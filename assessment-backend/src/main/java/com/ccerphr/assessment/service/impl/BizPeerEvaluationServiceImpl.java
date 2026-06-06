package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.PeerEvalQueryDTO;
import com.ccerphr.assessment.dto.PeerEvalSaveDTO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizPeerEvaluation;
import com.ccerphr.assessment.entity.BizSelfEvaluation;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.mapper.BizExamGroupMapper;
import com.ccerphr.assessment.mapper.BizExamGroupMemberMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizIndicatorOrgMapper;
import com.ccerphr.assessment.mapper.BizPeerEvaluationMapper;
import com.ccerphr.assessment.mapper.BizSelfEvaluationMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.service.BizPeerEvaluationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import com.ccerphr.assessment.util.ScoreCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BizPeerEvaluationServiceImpl extends ServiceImpl<BizPeerEvaluationMapper, BizPeerEvaluation> implements BizPeerEvaluationService {

    private static final String STATUS_PUBLISHED = "已发布";

    private final BizExamGroupMapper examGroupMapper;
    private final BizExamGroupMemberMapper memberMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final BizIndicatorOrgMapper indicatorOrgMapper;
    private final BizSelfEvaluationMapper selfEvaluationMapper;
    private final SysOrganizationMapper organizationMapper;

    public BizPeerEvaluationServiceImpl(BizExamGroupMapper examGroupMapper,
                                        BizExamGroupMemberMapper memberMapper,
                                        BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                        BizIndicatorOrgMapper indicatorOrgMapper,
                                        BizSelfEvaluationMapper selfEvaluationMapper,
                                        SysOrganizationMapper organizationMapper) {
        this.examGroupMapper = examGroupMapper;
        this.memberMapper = memberMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.indicatorOrgMapper = indicatorOrgMapper;
        this.selfEvaluationMapper = selfEvaluationMapper;
        this.organizationMapper = organizationMapper;
    }

    @Override
    public List<Map<String, Object>> getTaskList(PeerEvalQueryDTO queryDTO) {
        Long evaluatorOrgId = queryDTO.getEvaluatorOrgId();
        validateEvaluatorOrgAccess(evaluatorOrgId);
        List<BizExamGroup> groups;
        if (queryDTO.getExamGroupId() != null) {
            BizExamGroup group = examGroupMapper.selectById(queryDTO.getExamGroupId());
            groups = group != null ? List.of(group) : List.of();
        } else {
            LambdaQueryWrapper<BizExamGroup> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizExamGroup::getExamType, "MONTHLY");
            // 数据范围过滤
            DataScopeFilter.applyUnitFilter(wrapper, BizExamGroup::getUnitId);
            wrapper.orderByDesc(BizExamGroup::getCreatedTime);
            groups = examGroupMapper.selectList(wrapper);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroup group : groups) {
            Map<String, Object> map = new HashMap<>();
            map.put("examGroupId", group.getId());
            map.put("groupName", group.getGroupName());
            map.put("examType", group.getExamType());
            map.put("groupStatus", group.getStatus());
            map.put("currentStep", group.getCurrentStep());

            Map<Long, Integer> approvedCountByOrg = loadAssignedApprovedIndicatorCountByTarget(group.getId(), evaluatorOrgId);
            Map<Long, Integer> submittedCountByTarget = loadPeerCountByTarget(group.getId(), evaluatorOrgId, "SUBMITTED");

            int totalIndicatorsAll = 0;
            int evaluatedAll = 0;
            for (Map.Entry<Long, Integer> entry : approvedCountByOrg.entrySet()) {
                int total = entry.getValue();
                int evaluated = submittedCountByTarget.getOrDefault(entry.getKey(), 0);

                totalIndicatorsAll += total;
                evaluatedAll += evaluated;
            }

            String status = evaluatedAll >= totalIndicatorsAll && totalIndicatorsAll > 0 ? "COMPLETED" : "PENDING";
            // Status is computed from aggregated counts across multiple tables, so it cannot be filtered in SQL.
            // The filter must be applied in Java after computing the derived status value.
            if (StringUtils.hasText(queryDTO.getStatus()) && !queryDTO.getStatus().equals(status)) {
                continue;
            }

            map.put("status", status);
            map.put("totalIndicators", totalIndicatorsAll);
            map.put("evaluatedCount", evaluatedAll);
            int progress = totalIndicatorsAll > 0 ? (evaluatedAll * 100 / totalIndicatorsAll) : 0;
            map.put("progress", progress);
            result.add(map);
        }

        // 按优先级排序：进行中/他评中/考核中 优先
        result.sort(Comparator.comparingInt((Map<String, Object> m) -> {
            String gs = (String) m.get("groupStatus");
            String cs = (String) m.get("currentStep");
            if ("进行中".equals(gs) || "他评中".equals(cs) || "考核中".equals(cs)) return 0;
            return 1;
        }));

        return result;
    }

    @Override
    public List<Map<String, Object>> getTargetDepts(Long examGroupId, Long evaluatorOrgId) {
        validateEvaluatorOrgAccess(evaluatorOrgId);

        List<BizIndicatorDefinition> assignedIndicators = loadAssignedApprovedIndicators(examGroupId, evaluatorOrgId);
        Map<Long, Integer> approvedCountByOrg = countIndicatorsByTarget(assignedIndicators);
        Map<Long, Integer> submittedCountByTarget = loadPeerCountByTarget(examGroupId, evaluatorOrgId, "SUBMITTED");
        Map<Long, Integer> savedCountByTarget = loadPeerCountByTarget(examGroupId, evaluatorOrgId, null);
        Map<Long, String> targetOrgNames = new HashMap<>();
        for (BizIndicatorDefinition indicator : assignedIndicators) {
            if (indicator.getOrgId() != null) {
                targetOrgNames.putIfAbsent(indicator.getOrgId(), indicator.getOrgName());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : approvedCountByOrg.entrySet()) {
            Long targetOrgId = entry.getKey();
            Map<String, Object> map = new HashMap<>();
            map.put("targetOrgId", targetOrgId);
            map.put("targetOrgName", targetOrgNames.get(targetOrgId));

            int total = entry.getValue();
            int evaluated = submittedCountByTarget.getOrDefault(targetOrgId, 0);
            int saved = savedCountByTarget.getOrDefault(targetOrgId, 0);

            map.put("totalIndicators", total);
            map.put("evaluatedCount", saved);
            int progress = total > 0 ? (saved * 100 / total) : 0;
            map.put("progress", progress);
            map.put("status", evaluated >= total && total > 0 ? "COMPLETED" : "PENDING");
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getPeerEvalByDept(Long examGroupId, Long evaluatorOrgId, Long targetOrgId) {
        validateEvaluatorOrgAccess(evaluatorOrgId);
        validateTargetOrgInExamGroup(examGroupId, evaluatorOrgId, targetOrgId);
        List<BizIndicatorDefinition> indicators = loadAssignedApprovedIndicators(examGroupId, evaluatorOrgId).stream()
                .filter(indicator -> targetOrgId.equals(indicator.getOrgId()))
                .collect(Collectors.toList());
        List<BizPeerEvaluation> peerEvals = getBaseMapper().selectByExamGroupAndEvaluatorAndTarget(examGroupId, evaluatorOrgId, targetOrgId);
        Map<Long, BizPeerEvaluation> evalMap = new HashMap<>();
        for (BizPeerEvaluation eval : peerEvals) {
            evalMap.put(eval.getIndicatorId(), eval);
        }

        // 查询目标部门的自评数据
        List<BizSelfEvaluation> selfEvals = selfEvaluationMapper.selectByExamGroupAndOrg(examGroupId, targetOrgId);
        Map<Long, BizSelfEvaluation> selfEvalMap = new HashMap<>();
        for (BizSelfEvaluation se : selfEvals) {
            selfEvalMap.put(se.getIndicatorId(), se);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizIndicatorDefinition ind : indicators) {
            Map<String, Object> map = new HashMap<>();
            map.put("indicatorId", ind.getId());
            map.put("categoryId", ind.getCategoryId());
            map.put("categoryName", ind.getCategoryName());
            map.put("sortCode", ind.getSortCode());
            map.put("subCategory", ind.getSubCategory());
            map.put("content", ind.getContent());
            map.put("targetDesc", ind.getTargetDesc());
            map.put("weightAnnual", ind.getWeightAnnual());
            map.put("weightMonthly", ind.getWeightMonthly());
            map.put("evaluationStandard", ind.getEvaluationStandard());

            // 合并自评数据
            BizSelfEvaluation selfEval = selfEvalMap.get(ind.getId());
            if (selfEval != null) {
                map.put("actualCompletion", selfEval.getActualCompletion());
                map.put("selfScore", selfEval.getSelfScore());
                map.put("attachmentUrl", selfEval.getAttachmentUrl() != null ? selfEval.getAttachmentUrl() : "");
                map.put("attachmentName", selfEval.getAttachmentName() != null ? selfEval.getAttachmentName() : "");
            } else {
                map.put("actualCompletion", "");
                map.put("selfScore", null);
                map.put("attachmentUrl", "");
                map.put("attachmentName", "");
            }

            BizPeerEvaluation eval = evalMap.get(ind.getId());
            if (eval != null) {
                map.put("peerEvalId", eval.getId());
                map.put("peerScore", eval.getPeerScore());
                map.put("scoreComment", eval.getScoreComment());
                map.put("status", eval.getStatus());
            } else {
                map.put("peerEvalId", null);
                map.put("peerScore", null);
                map.put("scoreComment", "");
                map.put("status", "PENDING");
            }
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getPeerEvalByIndicator(Long examGroupId, Long evaluatorOrgId, Long categoryId) {
        validateEvaluatorOrgAccess(evaluatorOrgId);
        List<BizExamGroupMember> members = getMembersExcept(examGroupId, evaluatorOrgId);
        List<Map<String, Object>> result = new ArrayList<>();

        // 先获取所有该考核组下的指标（按categoryId过滤）
        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
        if (categoryId != null) {
            indWrapper.eq(BizIndicatorDefinition::getCategoryId, categoryId);
        }
        List<BizIndicatorDefinition> allIndicators = indicatorDefinitionMapper.selectList(indWrapper);

        // 按指标分组
        Map<Long, List<BizIndicatorDefinition>> indicatorById = new HashMap<>();
        for (BizIndicatorDefinition ind : allIndicators) {
            indicatorById.computeIfAbsent(ind.getId(), k -> new ArrayList<>()).add(ind);
        }

        List<BizPeerEvaluation> allPeerEvals = getBaseMapper().selectByExamGroupAndEvaluator(examGroupId, evaluatorOrgId);
        Map<String, BizPeerEvaluation> evalMap = new HashMap<>();
        for (BizPeerEvaluation eval : allPeerEvals) {
            String key = eval.getTargetOrgId() + "-" + eval.getIndicatorId();
            evalMap.put(key, eval);
        }

        for (List<BizIndicatorDefinition> indList : indicatorById.values()) {
            if (indList.isEmpty()) continue;
            BizIndicatorDefinition ind = indList.get(0);
            Map<String, Object> map = new HashMap<>();
            map.put("indicatorId", ind.getId());
            map.put("categoryId", ind.getCategoryId());
            map.put("categoryName", ind.getCategoryName());
            map.put("sortCode", ind.getSortCode());
            map.put("subCategory", ind.getSubCategory());
            map.put("content", ind.getContent());

            int totalDepts = members.size();
            int scoredCount = 0;
            List<Map<String, Object>> deptScores = new ArrayList<>();
            for (BizExamGroupMember member : members) {
                String key = member.getOrgId() + "-" + ind.getId();
                BizPeerEvaluation eval = evalMap.get(key);
                Map<String, Object> deptMap = new HashMap<>();
                deptMap.put("targetOrgId", member.getOrgId());
                deptMap.put("targetOrgName", member.getOrgName());
                if (eval != null) {
                    deptMap.put("peerScore", eval.getPeerScore());
                    deptMap.put("scoreComment", eval.getScoreComment());
                    deptMap.put("status", eval.getStatus());
                    if (eval.getPeerScore() != null) {
                        scoredCount++;
                    }
                } else {
                    deptMap.put("peerScore", null);
                    deptMap.put("scoreComment", "");
                    deptMap.put("status", "PENDING");
                }
                deptScores.add(deptMap);
            }
            map.put("totalDepts", totalDepts);
            map.put("scoredCount", scoredCount);
            map.put("deptScores", deptScores);
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePeerEval(PeerEvalSaveDTO dto) {
        // 发布状态校验
        BizExamGroup group = examGroupMapper.selectById(dto.getExamGroupId());
        if (group != null && STATUS_PUBLISHED.equals(group.getStatus())) {
            throw new BusinessException(403, "考核结果已发布，无法修改他评数据");
        }

        BizPeerEvaluation entity;
        if (dto.getId() != null) {
            entity = getById(dto.getId());
            if (entity == null) {
                throw new BusinessException("他评记录不存在");
            }
            validateEvaluatorOrgAccess(entity.getEvaluatorOrgId());
            ensureSamePeerEvalTarget(entity, dto);
            validateIndicatorAssignedToEvaluator(dto.getIndicatorId(), dto.getExamGroupId(), dto.getEvaluatorOrgId(), dto.getTargetOrgId());
        } else {
            validateEvaluatorOrgAccess(dto.getEvaluatorOrgId());
            validateTargetOrgInExamGroup(dto.getExamGroupId(), dto.getEvaluatorOrgId(), dto.getTargetOrgId());
            validateIndicatorAssignedToEvaluator(dto.getIndicatorId(), dto.getExamGroupId(), dto.getEvaluatorOrgId(), dto.getTargetOrgId());
            LambdaQueryWrapper<BizPeerEvaluation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizPeerEvaluation::getExamGroupId, dto.getExamGroupId());
            wrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, dto.getEvaluatorOrgId());
            wrapper.eq(BizPeerEvaluation::getTargetOrgId, dto.getTargetOrgId());
            wrapper.eq(BizPeerEvaluation::getIndicatorId, dto.getIndicatorId());
            entity = getOne(wrapper);
            if (entity == null) {
                entity = new BizPeerEvaluation();
                entity.setExamGroupId(dto.getExamGroupId());
                entity.setEvaluatorOrgId(dto.getEvaluatorOrgId());
                entity.setTargetOrgId(dto.getTargetOrgId());
                entity.setIndicatorId(dto.getIndicatorId());

                SysOrganization evalOrg = organizationMapper.selectById(dto.getEvaluatorOrgId());
                if (evalOrg != null) {
                    entity.setEvaluatorOrgName(evalOrg.getOrgName());
                }
                SysOrganization targetOrg = organizationMapper.selectById(dto.getTargetOrgId());
                if (targetOrg != null) {
                    entity.setTargetOrgName(targetOrg.getOrgName());
                }
                entity.setStatus("DRAFT");
                entity.setCreatedTime(LocalDateTime.now());
            }
        }

        entity.setPeerScore(dto.getPeerScore());
        entity.setScoreComment(dto.getScoreComment());

        // 调用通用得分计算规则，计算他评结果
        BizIndicatorDefinition indicator = indicatorDefinitionMapper.selectById(dto.getIndicatorId());
        entity.setPeerResult(ScoreCalculator.calculateResult(dto.getPeerScore(), indicator));

        entity.setUpdatedTime(LocalDateTime.now());
        saveOrUpdate(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPeerEval(Long examGroupId, Long evaluatorOrgId, Long targetOrgId, String submittedBy) {
        // 发布状态校验
        BizExamGroup group = examGroupMapper.selectById(examGroupId);
        if (group != null && STATUS_PUBLISHED.equals(group.getStatus())) {
            throw new BusinessException(403, "考核结果已发布，无法修改他评数据");
        }

        validateEvaluatorOrgAccess(evaluatorOrgId);
        validateTargetOrgInExamGroup(examGroupId, evaluatorOrgId, targetOrgId);
        Set<Long> assignedIndicatorIds = loadAssignedApprovedIndicators(examGroupId, evaluatorOrgId).stream()
                .filter(indicator -> targetOrgId.equals(indicator.getOrgId()))
                .map(BizIndicatorDefinition::getId)
                .collect(Collectors.toSet());
        long totalIndicators = assignedIndicatorIds.size();
        if (totalIndicators == 0) {
            throw new BusinessException(403, "当前部门没有需要评价的指标");
        }

        LambdaQueryWrapper<BizPeerEvaluation> peWrapper = new LambdaQueryWrapper<>();
        peWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
        peWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, evaluatorOrgId);
        peWrapper.eq(BizPeerEvaluation::getTargetOrgId, targetOrgId);
        List<BizPeerEvaluation> list = list(peWrapper).stream()
                .filter(evaluation -> assignedIndicatorIds.contains(evaluation.getIndicatorId()))
                .collect(Collectors.toList());

        if (list.size() < totalIndicators) {
            throw new BusinessException("还有指标未完成打分，无法提交");
        }

        LocalDateTime now = LocalDateTime.now();
        List<BizPeerEvaluation> toUpdate = new ArrayList<>();
        for (BizPeerEvaluation eval : list) {
            if (eval.getPeerScore() == null) {
                throw new BusinessException("还有指标未填写得分，无法提交");
            }
            eval.setStatus("SUBMITTED");
            eval.setSubmittedBy(submittedBy);
            eval.setSubmittedTime(now);
            eval.setUpdatedTime(now);
            toUpdate.add(eval);
        }
        updateBatchById(toUpdate);
    }

    @Override
    public List<Map<String, Object>> getStatistics(Long examGroupId) {
        List<BizExamGroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<BizExamGroupMember>().eq(BizExamGroupMember::getExamGroupId, examGroupId)
        );
        Map<Long, Integer> approvedCountByOrg = loadApprovedIndicatorCountByOrg(examGroupId);
        Map<String, Integer> submittedCountByPair = loadSubmittedPeerCountByPair(examGroupId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroupMember evaluator : members) {
            for (BizExamGroupMember target : members) {
                if (evaluator.getOrgId().equals(target.getOrgId())) {
                    continue;
                }
                int total = approvedCountByOrg.getOrDefault(target.getOrgId(), 0);
                int submitted = submittedCountByPair.getOrDefault(
                        evaluator.getOrgId() + "-" + target.getOrgId(), 0);

                Map<String, Object> map = new HashMap<>();
                map.put("evaluatorOrgId", evaluator.getOrgId());
                map.put("evaluatorOrgName", evaluator.getOrgName());
                map.put("targetOrgId", target.getOrgId());
                map.put("targetOrgName", target.getOrgName());
                map.put("totalIndicators", total);
                map.put("submittedCount", submitted);
                map.put("status", submitted >= total && total > 0 ? "COMPLETED" : "PENDING");
                result.add(map);
            }
        }
        return result;
    }

    private Map<Long, Integer> loadApprovedIndicatorCountByOrg(Long examGroupId) {
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(
                new LambdaQueryWrapper<BizIndicatorDefinition>()
                        .eq(BizIndicatorDefinition::getExamGroupId, examGroupId)
                        .eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED")
        );
        Map<Long, Integer> result = new HashMap<>();
        for (BizIndicatorDefinition indicator : indicators) {
            if (indicator.getOrgId() != null) {
                result.merge(indicator.getOrgId(), 1, Integer::sum);
            }
        }
        return result;
    }

    private Map<Long, Integer> loadAssignedApprovedIndicatorCountByTarget(Long examGroupId, Long evaluatorOrgId) {
        return countIndicatorsByTarget(loadAssignedApprovedIndicators(examGroupId, evaluatorOrgId));
    }

    private Map<Long, Integer> countIndicatorsByTarget(List<BizIndicatorDefinition> indicators) {
        Map<Long, Integer> result = new HashMap<>();
        for (BizIndicatorDefinition indicator : indicators) {
            if (indicator.getOrgId() != null) {
                result.merge(indicator.getOrgId(), 1, Integer::sum);
            }
        }
        return result;
    }

    private List<BizIndicatorDefinition> loadAssignedApprovedIndicators(Long examGroupId, Long evaluatorOrgId) {
        List<Long> indicatorIds = indicatorOrgMapper.selectIndicatorIdsByOrgId(evaluatorOrgId);
        List<Long> sourceGroupIds = resolveIndicatorSourceGroupIds(examGroupId);
        if (indicatorIds.isEmpty() || sourceGroupIds.isEmpty()) {
            return new ArrayList<>();
        }
        return indicatorDefinitionMapper.selectList(
                new LambdaQueryWrapper<BizIndicatorDefinition>()
                        .in(BizIndicatorDefinition::getId, indicatorIds)
                        .in(BizIndicatorDefinition::getExamGroupId, sourceGroupIds)
                        .eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED")
                        .eq(BizIndicatorDefinition::getExamTargetType, "DEPARTMENT")
                        .ne(BizIndicatorDefinition::getOrgId, evaluatorOrgId)
                        .orderByAsc(BizIndicatorDefinition::getSortCode)
                        .orderByAsc(BizIndicatorDefinition::getId)
        );
    }

    private List<Long> resolveIndicatorSourceGroupIds(Long examGroupId) {
        BizExamGroup group = examGroupMapper.selectById(examGroupId);
        if (group == null) {
            throw new BusinessException(404, "考核组不存在");
        }
        if ("INDICATOR_SET".equals(group.getExamCategory())) {
            return List.of(group.getId());
        }
        if (group.getStartDate() == null || group.getEndDate() == null) {
            return List.of();
        }

        int year = group.getStartDate().getYear();
        java.time.LocalDate yearStart = java.time.LocalDate.of(year, 1, 1);
        java.time.LocalDate yearEnd = java.time.LocalDate.of(year, 12, 31);
        return examGroupMapper.selectList(
                new LambdaQueryWrapper<BizExamGroup>()
                        .eq(BizExamGroup::getUnitId, group.getUnitId())
                        .eq(BizExamGroup::getExamCategory, "INDICATOR_SET")
                        .le(BizExamGroup::getStartDate, yearEnd)
                        .ge(BizExamGroup::getEndDate, yearStart)
                        .orderByDesc(BizExamGroup::getStartDate)
        ).stream().map(BizExamGroup::getId).collect(Collectors.toList());
    }

    private Map<Long, Integer> loadPeerCountByTarget(Long examGroupId, Long evaluatorOrgId, String status) {
        Set<Long> assignedIndicatorIds = loadAssignedApprovedIndicators(examGroupId, evaluatorOrgId).stream()
                .map(BizIndicatorDefinition::getId)
                .collect(Collectors.toSet());
        LambdaQueryWrapper<BizPeerEvaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
        wrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, evaluatorOrgId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(BizPeerEvaluation::getStatus, status);
        }
        List<BizPeerEvaluation> evaluations = list(wrapper);
        Map<Long, Set<Long>> indicatorIdsByTarget = new HashMap<>();
        for (BizPeerEvaluation evaluation : evaluations) {
            if (evaluation.getTargetOrgId() == null
                    || evaluation.getIndicatorId() == null
                    || !assignedIndicatorIds.contains(evaluation.getIndicatorId())) {
                continue;
            }
            indicatorIdsByTarget
                    .computeIfAbsent(evaluation.getTargetOrgId(), key -> new HashSet<>())
                    .add(evaluation.getIndicatorId());
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : indicatorIdsByTarget.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    private Map<String, Integer> loadSubmittedPeerCountByPair(Long examGroupId) {
        List<BizPeerEvaluation> evaluations = list(
                new LambdaQueryWrapper<BizPeerEvaluation>()
                        .eq(BizPeerEvaluation::getExamGroupId, examGroupId)
                        .eq(BizPeerEvaluation::getStatus, "SUBMITTED")
        );
        Map<String, Set<Long>> indicatorIdsByPair = new HashMap<>();
        for (BizPeerEvaluation evaluation : evaluations) {
            if (evaluation.getEvaluatorOrgId() == null
                    || evaluation.getTargetOrgId() == null
                    || evaluation.getIndicatorId() == null) {
                continue;
            }
            String key = evaluation.getEvaluatorOrgId() + "-" + evaluation.getTargetOrgId();
            indicatorIdsByPair.computeIfAbsent(key, item -> new HashSet<>()).add(evaluation.getIndicatorId());
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : indicatorIdsByPair.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    private List<BizExamGroupMember> getMembersExcept(Long examGroupId, Long excludeOrgId) {
        LambdaQueryWrapper<BizExamGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        wrapper.ne(BizExamGroupMember::getOrgId, excludeOrgId);
        return memberMapper.selectList(wrapper);
    }

    private void validateEvaluatorOrgAccess(Long evaluatorOrgId) {
        if (evaluatorOrgId == null || evaluatorOrgId == 0L) {
            // evaluatorOrgId 为空或0表示前端未传递有效的互评人部门，跳过校验
            return;
        }
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        log.debug("[validateEvaluatorOrgAccess] evaluatorOrgId={}, dataScope={}, scopeId={}", evaluatorOrgId, dataScope, scopeId);
        if ("ALL".equals(dataScope)) {
            return;
        }
        if ("ORG".equals(dataScope)) {
            if (!evaluatorOrgId.equals(scopeId)) {
                log.debug("[validateEvaluatorOrgAccess] BLOCKED: evaluatorOrgId({}) != scopeId({})", evaluatorOrgId, scopeId);
                throw new BusinessException(403, "无权限以该单位进行互评");
            }
            return;
        }
        if ("UNIT".equals(dataScope)) {
            SysOrganization evaluatorOrg = organizationMapper.selectById(evaluatorOrgId);
            if (evaluatorOrg == null || !DataScopeContext.getVisibleUnitIds().contains(evaluatorOrg.getUnitId())) {
                throw new BusinessException(403, "评价单位超出当前数据范围");
            }
            return;
        }
        throw new BusinessException(403, "无权限以该单位进行互评");
    }

    private void validateTargetOrgInExamGroup(Long examGroupId, Long evaluatorOrgId, Long targetOrgId) {
        if (examGroupId == null || targetOrgId == null) {
            throw new BusinessException("考核组和被评价单位不能为空");
        }
        if (targetOrgId.equals(evaluatorOrgId)) {
            throw new BusinessException("不能对本单位进行互评");
        }
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<BizExamGroupMember>()
                        .eq(BizExamGroupMember::getExamGroupId, examGroupId)
                        .eq(BizExamGroupMember::getOrgId, targetOrgId)
        );
        if (count == null || count == 0) {
            throw new BusinessException(403, "被评价单位不属于当前考核组");
        }
    }

    private void validateIndicatorAssignedToEvaluator(Long indicatorId, Long examGroupId, Long evaluatorOrgId, Long targetOrgId) {
        BizIndicatorDefinition indicator = indicatorDefinitionMapper.selectById(indicatorId);
        if (indicator == null) {
            throw new BusinessException(403, "无权限评价该指标");
        }
        List<Long> sourceGroupIds = resolveIndicatorSourceGroupIds(examGroupId);
        boolean assigned = indicatorOrgMapper.selectByIndicatorId(indicatorId).stream()
                .anyMatch(org -> evaluatorOrgId.equals(org.getOrgId()));
        if (!sourceGroupIds.contains(indicator.getExamGroupId())
                || !targetOrgId.equals(indicator.getOrgId())
                || !"APPROVED".equals(indicator.getApprovalStatus())
                || !"DEPARTMENT".equals(indicator.getExamTargetType())
                || !assigned) {
            throw new BusinessException(403, "无权限评价该指标");
        }
    }

    private void ensureSamePeerEvalTarget(BizPeerEvaluation entity, PeerEvalSaveDTO dto) {
        if (!entity.getExamGroupId().equals(dto.getExamGroupId())
                || !entity.getEvaluatorOrgId().equals(dto.getEvaluatorOrgId())
                || !entity.getTargetOrgId().equals(dto.getTargetOrgId())
                || !entity.getIndicatorId().equals(dto.getIndicatorId())) {
            throw new BusinessException(403, "无权限修改该互评记录");
        }
    }
}
