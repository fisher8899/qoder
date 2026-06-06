package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.entity.*;
import com.ccerphr.assessment.mapper.*;
import com.ccerphr.assessment.service.BizLeaderEvaluationService;
import com.ccerphr.assessment.util.ScoreCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BizLeaderEvaluationServiceImpl extends ServiceImpl<BizLeaderEvaluationMapper, BizLeaderEvaluation>
        implements BizLeaderEvaluationService {

    private final SysLeaderMapper leaderMapper;
    private final BizIndicatorLeaderMapper indicatorLeaderMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final BizSelfEvaluationMapper selfEvaluationMapper;
    private final SysOrganizationMapper organizationMapper;
    private final BizExamGroupMapper examGroupMapper;

    public BizLeaderEvaluationServiceImpl(SysLeaderMapper leaderMapper,
                                          BizIndicatorLeaderMapper indicatorLeaderMapper,
                                          BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                          BizSelfEvaluationMapper selfEvaluationMapper,
                                          SysOrganizationMapper organizationMapper,
                                          BizExamGroupMapper examGroupMapper) {
        this.leaderMapper = leaderMapper;
        this.indicatorLeaderMapper = indicatorLeaderMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.selfEvaluationMapper = selfEvaluationMapper;
        this.organizationMapper = organizationMapper;
        this.examGroupMapper = examGroupMapper;
    }

    @Override
    public List<Map<String, Object>> getTaskList(Long employeeId) {
        SysLeader leader = findLeaderByEmployeeId(employeeId);
        if (leader == null) {
            return Collections.emptyList();
        }

        // 查询该领导关联的所有指标ID
        List<Long> leaderIds = findLeaderIdsByEmployeeId(employeeId);
        List<Long> indicatorIds = findIndicatorIdsByLeaderIds(leaderIds);
        if (leaderIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<BizExamGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.eq(BizExamGroup::getExamType, "MONTHLY");
        groupWrapper.orderByDesc(BizExamGroup::getCreatedTime);
        List<BizExamGroup> groups = examGroupMapper.selectList(groupWrapper);

        // 查询已有的评估记录
        LambdaQueryWrapper<BizLeaderEvaluation> evalWrapper = new LambdaQueryWrapper<>();
        evalWrapper.in(BizLeaderEvaluation::getLeaderId, leaderIds);
        List<BizLeaderEvaluation> existingEvals = list(evalWrapper);
        Map<Long, List<BizLeaderEvaluation>> evalByGroup = existingEvals.stream()
                .collect(Collectors.groupingBy(BizLeaderEvaluation::getExamGroupId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroup examGroup : groups) {
            Long examGroupId = examGroup.getId();
            List<Long> sourceGroupIds = resolveIndicatorSourceGroupIds(examGroupId);
            if (sourceGroupIds.isEmpty()) continue;

        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.in(BizIndicatorDefinition::getExamGroupId, sourceGroupIds);
        indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        indWrapper.eq(BizIndicatorDefinition::getExamTargetType, "LEADER");
        indWrapper.and(wrapper -> {
            if (!indicatorIds.isEmpty()) {
                wrapper.in(BizIndicatorDefinition::getId, indicatorIds).or();
            }
            wrapper.in(BizIndicatorDefinition::getLeaderId, leaderIds)
                    .or()
                    .like(BizIndicatorDefinition::getLeaderName, leader.getLeaderName());
        });
        List<BizIndicatorDefinition> groupIndicators = indicatorDefinitionMapper.selectList(indWrapper);
            if (groupIndicators.isEmpty()) continue;

            int totalIndicators = groupIndicators.size();
            List<BizLeaderEvaluation> groupEvals = evalByGroup.getOrDefault(examGroupId, Collections.emptyList());
            int evaluatedCount = (int) groupEvals.stream()
                    .filter(e -> groupIndicators.stream().anyMatch(ind -> ind.getId().equals(e.getIndicatorId())))
                    .filter(e -> e.getLeaderScore() != null)
                    .count();

            Map<String, Object> task = new HashMap<>();
            task.put("examGroupId", examGroupId);
            task.put("groupName", examGroup.getGroupName());
            task.put("examType", examGroup.getExamType());
            task.put("status", evaluatedCount >= totalIndicators && totalIndicators > 0 ? "COMPLETED" : "PENDING");
            task.put("totalIndicators", totalIndicators);
            task.put("evaluatedCount", evaluatedCount);
            task.put("progress", totalIndicators > 0 ? (evaluatedCount * 100 / totalIndicators) : 0);
            task.put("startDate", examGroup.getStartDate());
            task.put("endDate", examGroup.getEndDate());
            result.add(task);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getIndicatorsByDept(Long examGroupId, Long employeeId) {
        SysLeader leader = findLeaderByEmployeeId(employeeId);
        if (leader == null) {
            return Collections.emptyList();
        }

        List<Long> leaderIds = findLeaderIdsByEmployeeId(employeeId);
        List<Long> indicatorIds = findIndicatorIdsByLeaderIds(leaderIds);
        if (leaderIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> sourceGroupIds = resolveIndicatorSourceGroupIds(examGroupId);
        if (sourceGroupIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.in(BizIndicatorDefinition::getExamGroupId, sourceGroupIds);
        indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        indWrapper.eq(BizIndicatorDefinition::getExamTargetType, "LEADER");
        indWrapper.and(wrapper -> {
            if (!indicatorIds.isEmpty()) {
                wrapper.in(BizIndicatorDefinition::getId, indicatorIds).or();
            }
            wrapper.in(BizIndicatorDefinition::getLeaderId, leaderIds)
                    .or()
                    .like(BizIndicatorDefinition::getLeaderName, leader.getLeaderName());
        });
        indWrapper.orderByAsc(BizIndicatorDefinition::getOrgId);
        indWrapper.orderByAsc(BizIndicatorDefinition::getSortCode);
        indWrapper.orderByAsc(BizIndicatorDefinition::getId);
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper);

        // 查询已有评估记录（批量查询替代N+1）
        LambdaQueryWrapper<BizLeaderEvaluation> evalWrapper = new LambdaQueryWrapper<>();
        evalWrapper.eq(BizLeaderEvaluation::getExamGroupId, examGroupId);
        evalWrapper.in(BizLeaderEvaluation::getLeaderId, leaderIds);
        List<BizLeaderEvaluation> existingEvals = list(evalWrapper);
        Map<String, BizLeaderEvaluation> evalMap = new HashMap<>();
        for (BizLeaderEvaluation eval : existingEvals) {
            evalMap.put(eval.getIndicatorId().toString(), eval);
        }

        // 按部门分组，构建返回数据
        // 返回扁平列表，前端按部门分组展示
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
            map.put("targetOrgId", ind.getOrgId());
            map.put("targetOrgName", ind.getOrgName());

            BizSelfEvaluation selfEval = findSelfEvaluation(examGroupId, ind.getOrgId(), ind.getId());
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

            BizLeaderEvaluation eval = evalMap.get(ind.getId().toString());
            if (eval != null) {
                map.put("evalId", eval.getId());
                map.put("leaderScore", eval.getLeaderScore());
                map.put("scoreComment", eval.getScoreComment());
                map.put("status", eval.getStatus());
            } else {
                map.put("evalId", null);
                map.put("leaderScore", null);
                map.put("scoreComment", "");
                map.put("status", "PENDING");
            }
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void save(Map<String, Object> data) {
        Long examGroupId = toLong(data.get("examGroupId"));
        Long leaderId = toLong(data.get("leaderId"));
        Long targetOrgId = toLong(data.get("targetOrgId"));
        Long indicatorId = toLong(data.get("indicatorId"));
        BigDecimal leaderScore = toBigDecimal(data.get("leaderScore"));
        String scoreComment = data.get("scoreComment") == null ? "" : data.get("scoreComment").toString();

        if (examGroupId == null || leaderId == null || targetOrgId == null || indicatorId == null) {
            return;
        }
        validateIndicatorAssignedToLeader(indicatorId, examGroupId, leaderId, targetOrgId);

        // 查找已有记录
        LambdaQueryWrapper<BizLeaderEvaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizLeaderEvaluation::getExamGroupId, examGroupId);
        wrapper.eq(BizLeaderEvaluation::getLeaderId, leaderId);
        wrapper.eq(BizLeaderEvaluation::getTargetOrgId, targetOrgId);
        wrapper.eq(BizLeaderEvaluation::getIndicatorId, indicatorId);
        BizLeaderEvaluation entity = getOne(wrapper);

        if (entity == null) {
            entity = new BizLeaderEvaluation();
            entity.setExamGroupId(examGroupId);
            entity.setLeaderId(leaderId);
            entity.setTargetOrgId(targetOrgId);
            entity.setIndicatorId(indicatorId);
            entity.setStatus("DRAFT");
            entity.setCreatedTime(LocalDateTime.now());
        } else if ("SUBMITTED".equals(entity.getStatus())) {
            return; // 已提交不可修改
        }

        // 获取目标部门名称
        SysOrganization org = organizationMapper.selectById(targetOrgId);
        if (org != null) {
            entity.setTargetOrgName(org.getOrgName());
        }

        entity.setLeaderScore(leaderScore);
        entity.setScoreComment(scoreComment);

        // 计算加权结果
        BizIndicatorDefinition ind = indicatorDefinitionMapper.selectById(indicatorId);
        if (ind != null) {
            entity.setLeaderResult(ScoreCalculator.calculateResult(leaderScore, ind));
            if (entity.getUnitId() == null) {
                entity.setUnitId(ind.getUnitId());
            }
        }

        entity.setUpdatedTime(LocalDateTime.now());
        saveOrUpdate(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long examGroupId, Long leaderId, Long targetOrgId, String submittedBy) {
        List<BizLeaderEvaluation> evals = getBaseMapper().selectByExamGroupLeaderAndTarget(examGroupId, leaderId, targetOrgId);
        Set<Long> assignedIndicatorIds = loadAssignedApprovedIndicators(examGroupId, leaderId).stream()
                .filter(indicator -> targetOrgId.equals(indicator.getOrgId()))
                .map(BizIndicatorDefinition::getId)
                .collect(Collectors.toSet());
        if (assignedIndicatorIds.isEmpty()) {
            throw new BusinessException(403, "当前部门没有需要评价的指标");
        }

        Map<Long, BizLeaderEvaluation> evalMap = evals.stream()
                .filter(eval -> eval.getIndicatorId() != null)
                .collect(Collectors.toMap(BizLeaderEvaluation::getIndicatorId, item -> item, (first, second) -> first));
        for (Long indicatorId : assignedIndicatorIds) {
            BizLeaderEvaluation eval = evalMap.get(indicatorId);
            if (eval == null || eval.getLeaderScore() == null) {
                throw new BusinessException("还有指标未填写得分，无法提交");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<BizLeaderEvaluation> toUpdate = new ArrayList<>();
        for (BizLeaderEvaluation eval : evals) {
            if (assignedIndicatorIds.contains(eval.getIndicatorId()) && !"SUBMITTED".equals(eval.getStatus())) {
                eval.setStatus("SUBMITTED");
                eval.setSubmittedBy(submittedBy);
                eval.setSubmittedTime(now);
                eval.setUpdatedTime(now);
                toUpdate.add(eval);
            }
        }
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate);
        }
    }

    private SysLeader findLeaderByEmployeeId(Long employeeId) {
        if (employeeId == null) return null;
        List<SysLeader> leaders = findLeadersByEmployeeId(employeeId);
        if (leaders.isEmpty()) return null;
        Long currentUnitId = DataScopeContext.getCurrentUnitId();
        if (currentUnitId != null) {
            Optional<SysLeader> scopedLeader = leaders.stream()
                    .filter(leader -> currentUnitId.equals(leader.getUnitId()))
                    .findFirst();
            if (scopedLeader.isPresent()) {
                return scopedLeader.get();
            }
        }
        return leaders.get(0);
    }

    private List<SysLeader> findLeadersByEmployeeId(Long employeeId) {
        if (employeeId == null) return Collections.emptyList();
        LambdaQueryWrapper<SysLeader> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLeader::getEmployeeId, employeeId);
        return leaderMapper.selectList(wrapper);
    }

    private List<Long> findLeaderIdsByEmployeeId(Long employeeId) {
        return findLeadersByEmployeeId(employeeId).stream()
                .map(SysLeader::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Long findLeaderId(Long employeeId) {
        SysLeader leader = findLeaderByEmployeeId(employeeId);
        return leader != null ? leader.getId() : null;
    }

    @Override
    public Long findLeaderIdForIndicator(Long employeeId, Long indicatorId) {
        List<SysLeader> leaders = findLeadersByEmployeeId(employeeId);
        if (leaders.isEmpty()) {
            return null;
        }
        if (indicatorId != null) {
            List<Long> relatedLeaderIds = indicatorLeaderMapper.selectByIndicatorId(indicatorId).stream()
                    .map(BizIndicatorLeader::getLeaderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            for (SysLeader leader : leaders) {
                if (relatedLeaderIds.contains(leader.getId())) {
                    return leader.getId();
                }
            }

            BizIndicatorDefinition indicator = indicatorDefinitionMapper.selectById(indicatorId);
            if (indicator != null) {
                for (SysLeader leader : leaders) {
                    boolean idMatched = leader.getId() != null && leader.getId().equals(indicator.getLeaderId());
                    boolean nameMatched = leader.getLeaderName() != null
                            && indicator.getLeaderName() != null
                            && indicator.getLeaderName().contains(leader.getLeaderName());
                    if (idMatched || nameMatched) {
                        return leader.getId();
                    }
                }
            }
        }
        return findLeaderId(employeeId);
    }

    private List<Long> resolveIndicatorSourceGroupIds(Long examGroupId) {
        BizExamGroup group = examGroupMapper.selectById(examGroupId);
        if (group == null) {
            return Collections.emptyList();
        }
        if ("INDICATOR_SET".equals(group.getExamCategory())) {
            return List.of(group.getId());
        }
        if (group.getStartDate() == null || group.getEndDate() == null) {
            return Collections.emptyList();
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

    private BizSelfEvaluation findSelfEvaluation(Long examGroupId, Long orgId, Long indicatorId) {
        if (examGroupId == null || orgId == null || indicatorId == null) {
            return null;
        }
        return selfEvaluationMapper.selectByExamGroupAndOrg(examGroupId, orgId).stream()
                .filter(item -> indicatorId.equals(item.getIndicatorId()))
                .findFirst()
                .orElse(null);
    }

    private List<BizIndicatorDefinition> loadAssignedApprovedIndicators(Long examGroupId, Long leaderId) {
        List<Long> indicatorIds = findIndicatorIdsByLeaderIds(List.of(leaderId));
        List<Long> sourceGroupIds = resolveIndicatorSourceGroupIds(examGroupId);
        if (sourceGroupIds.isEmpty()) {
            return Collections.emptyList();
        }
        SysLeader leader = leaderMapper.selectById(leaderId);
        LambdaQueryWrapper<BizIndicatorDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizIndicatorDefinition::getExamGroupId, sourceGroupIds);
        wrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        wrapper.eq(BizIndicatorDefinition::getExamTargetType, "LEADER");
        wrapper.and(nested -> {
            if (!indicatorIds.isEmpty()) {
                nested.in(BizIndicatorDefinition::getId, indicatorIds).or();
            }
            nested.eq(BizIndicatorDefinition::getLeaderId, leaderId);
            if (leader != null && leader.getLeaderName() != null) {
                nested.or().like(BizIndicatorDefinition::getLeaderName, leader.getLeaderName());
            }
        });
        wrapper.orderByAsc(BizIndicatorDefinition::getOrgId);
        wrapper.orderByAsc(BizIndicatorDefinition::getSortCode);
        wrapper.orderByAsc(BizIndicatorDefinition::getId);
        return indicatorDefinitionMapper.selectList(wrapper);
    }

    private void validateIndicatorAssignedToLeader(Long indicatorId, Long examGroupId, Long leaderId, Long targetOrgId) {
        BizIndicatorDefinition indicator = indicatorDefinitionMapper.selectById(indicatorId);
        if (indicator == null) {
            throw new BusinessException(403, "无权限评价该指标");
        }
        List<Long> sourceGroupIds = resolveIndicatorSourceGroupIds(examGroupId);
        SysLeader leader = leaderMapper.selectById(leaderId);
        boolean assigned = findIndicatorIdsByLeaderIds(List.of(leaderId)).contains(indicatorId)
                || leaderId.equals(indicator.getLeaderId())
                || (leader != null
                    && leader.getLeaderName() != null
                    && indicator.getLeaderName() != null
                    && indicator.getLeaderName().contains(leader.getLeaderName()));
        if (!sourceGroupIds.contains(indicator.getExamGroupId())
                || !targetOrgId.equals(indicator.getOrgId())
                || !"APPROVED".equals(indicator.getApprovalStatus())
                || !"LEADER".equals(indicator.getExamTargetType())
                || !assigned) {
            throw new BusinessException(403, "无权限评价该指标");
        }
    }

    private List<Long> findIndicatorIdsByLeaderIds(List<Long> leaderIds) {
        if (leaderIds == null || leaderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return leaderIds.stream()
                .flatMap(leaderId -> indicatorLeaderMapper.selectIndicatorIdsByLeaderId(leaderId).stream())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long) return (Long) val;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return new BigDecimal(val.toString());
        try { return new BigDecimal(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
