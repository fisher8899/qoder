package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.dto.PeerEvalQueryDTO;
import com.ccerphr.assessment.dto.PeerEvalSaveDTO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizPeerEvaluation;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.mapper.BizExamGroupMapper;
import com.ccerphr.assessment.mapper.BizExamGroupMemberMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizPeerEvaluationMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.service.BizPeerEvaluationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BizPeerEvaluationServiceImpl extends ServiceImpl<BizPeerEvaluationMapper, BizPeerEvaluation> implements BizPeerEvaluationService {

    private final BizExamGroupMapper examGroupMapper;
    private final BizExamGroupMemberMapper memberMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final SysOrganizationMapper organizationMapper;

    public BizPeerEvaluationServiceImpl(BizExamGroupMapper examGroupMapper,
                                        BizExamGroupMemberMapper memberMapper,
                                        BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                        SysOrganizationMapper organizationMapper) {
        this.examGroupMapper = examGroupMapper;
        this.memberMapper = memberMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.organizationMapper = organizationMapper;
    }

    @Override
    public List<Map<String, Object>> getTaskList(PeerEvalQueryDTO queryDTO) {
        Long evaluatorOrgId = queryDTO.getEvaluatorOrgId();
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

            // 获取该考核组下该评估部门需要评估的目标部门
            List<BizExamGroupMember> members = getMembersExcept(group.getId(), evaluatorOrgId);
            int totalIndicatorsAll = 0;
            int evaluatedAll = 0;
            for (BizExamGroupMember member : members) {
                LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
                indWrapper.eq(BizIndicatorDefinition::getExamGroupId, group.getId());
                indWrapper.eq(BizIndicatorDefinition::getOrgId, member.getOrgId());
                indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
                int total = indicatorDefinitionMapper.selectCount(indWrapper).intValue();

                LambdaQueryWrapper<BizPeerEvaluation> peWrapper = new LambdaQueryWrapper<>();
                peWrapper.eq(BizPeerEvaluation::getExamGroupId, group.getId());
                peWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, evaluatorOrgId);
                peWrapper.eq(BizPeerEvaluation::getTargetOrgId, member.getOrgId());
                peWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
                int evaluated = (int) count(peWrapper);

                totalIndicatorsAll += total;
                evaluatedAll += evaluated;
            }

            String status = evaluatedAll >= totalIndicatorsAll && totalIndicatorsAll > 0 ? "COMPLETED" : "PENDING";
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
        return result;
    }

    @Override
    public List<Map<String, Object>> getTargetDepts(Long examGroupId, Long evaluatorOrgId) {
        List<BizExamGroupMember> members = getMembersExcept(examGroupId, evaluatorOrgId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroupMember member : members) {
            Map<String, Object> map = new HashMap<>();
            map.put("targetOrgId", member.getOrgId());
            map.put("targetOrgName", member.getOrgName());

            LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
            indWrapper.eq(BizIndicatorDefinition::getOrgId, member.getOrgId());
            indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
            int total = indicatorDefinitionMapper.selectCount(indWrapper).intValue();

            LambdaQueryWrapper<BizPeerEvaluation> peWrapper = new LambdaQueryWrapper<>();
            peWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
            peWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, evaluatorOrgId);
            peWrapper.eq(BizPeerEvaluation::getTargetOrgId, member.getOrgId());
            peWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
            int evaluated = (int) count(peWrapper);

            // 包含已保存但未提交的
            LambdaQueryWrapper<BizPeerEvaluation> allWrapper = new LambdaQueryWrapper<>();
            allWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
            allWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, evaluatorOrgId);
            allWrapper.eq(BizPeerEvaluation::getTargetOrgId, member.getOrgId());
            int saved = (int) count(allWrapper);

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
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectByExamGroupAndOrg(examGroupId, targetOrgId);
        List<BizPeerEvaluation> peerEvals = getBaseMapper().selectByExamGroupAndEvaluatorAndTarget(examGroupId, evaluatorOrgId, targetOrgId);
        Map<Long, BizPeerEvaluation> evalMap = new HashMap<>();
        for (BizPeerEvaluation eval : peerEvals) {
            evalMap.put(eval.getIndicatorId(), eval);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizIndicatorDefinition ind : indicators) {
            Map<String, Object> map = new HashMap<>();
            map.put("indicatorId", ind.getId());
            map.put("categoryName", ind.getCategoryName());
            map.put("subCategory", ind.getSubCategory());
            map.put("content", ind.getContent());
            map.put("targetDesc", ind.getTargetDesc());
            map.put("weightAnnual", ind.getWeightAnnual());
            map.put("weightMonthly", ind.getWeightMonthly());
            map.put("evaluationStandard", ind.getEvaluationStandard());

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
            map.put("categoryName", ind.getCategoryName());
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
        BizPeerEvaluation entity;
        if (dto.getId() != null) {
            entity = getById(dto.getId());
            if (entity == null) {
                throw new BusinessException("他评记录不存在");
            }
            if ("SUBMITTED".equals(entity.getStatus())) {
                throw new BusinessException("已提交的他评无法修改");
            }
        } else {
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
            } else if ("SUBMITTED".equals(entity.getStatus())) {
                throw new BusinessException("已提交的他评无法修改");
            }
        }

        entity.setPeerScore(dto.getPeerScore());
        entity.setScoreComment(dto.getScoreComment());
        entity.setUpdatedTime(LocalDateTime.now());
        saveOrUpdate(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPeerEval(Long examGroupId, Long evaluatorOrgId, Long targetOrgId, String submittedBy) {
        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
        indWrapper.eq(BizIndicatorDefinition::getOrgId, targetOrgId);
        indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        long totalIndicators = indicatorDefinitionMapper.selectCount(indWrapper);

        LambdaQueryWrapper<BizPeerEvaluation> peWrapper = new LambdaQueryWrapper<>();
        peWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
        peWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, evaluatorOrgId);
        peWrapper.eq(BizPeerEvaluation::getTargetOrgId, targetOrgId);
        List<BizPeerEvaluation> list = list(peWrapper);

        if (list.size() < totalIndicators) {
            throw new BusinessException("还有指标未完成打分，无法提交");
        }

        for (BizPeerEvaluation eval : list) {
            if (eval.getPeerScore() == null) {
                throw new BusinessException("还有指标未填写得分，无法提交");
            }
            eval.setStatus("SUBMITTED");
            eval.setSubmittedBy(submittedBy);
            eval.setSubmittedTime(LocalDateTime.now());
            eval.setUpdatedTime(LocalDateTime.now());
            updateById(eval);
        }
    }

    @Override
    public List<Map<String, Object>> getStatistics(Long examGroupId) {
        List<BizExamGroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<BizExamGroupMember>().eq(BizExamGroupMember::getExamGroupId, examGroupId)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroupMember evaluator : members) {
            for (BizExamGroupMember target : members) {
                if (evaluator.getOrgId().equals(target.getOrgId())) {
                    continue;
                }
                LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
                indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
                indWrapper.eq(BizIndicatorDefinition::getOrgId, target.getOrgId());
                indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
                int total = indicatorDefinitionMapper.selectCount(indWrapper).intValue();

                LambdaQueryWrapper<BizPeerEvaluation> peWrapper = new LambdaQueryWrapper<>();
                peWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
                peWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, evaluator.getOrgId());
                peWrapper.eq(BizPeerEvaluation::getTargetOrgId, target.getOrgId());
                peWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
                int submitted = (int) count(peWrapper);

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

    private List<BizExamGroupMember> getMembersExcept(Long examGroupId, Long excludeOrgId) {
        LambdaQueryWrapper<BizExamGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        wrapper.ne(BizExamGroupMember::getOrgId, excludeOrgId);
        return memberMapper.selectList(wrapper);
    }
}
