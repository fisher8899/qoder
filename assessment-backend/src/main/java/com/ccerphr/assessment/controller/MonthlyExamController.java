package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizPeerEvaluation;
import com.ccerphr.assessment.entity.BizSelfEvaluation;
import com.ccerphr.assessment.mapper.BizExamGroupMemberMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizPeerEvaluationMapper;
import com.ccerphr.assessment.mapper.BizSelfEvaluationMapper;
import com.ccerphr.assessment.service.BizExamGroupService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monthly-exam")
public class MonthlyExamController {

    private final BizExamGroupService examGroupService;
    private final BizExamGroupMemberMapper memberMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final BizSelfEvaluationMapper selfEvaluationMapper;
    private final BizPeerEvaluationMapper peerEvaluationMapper;

    public MonthlyExamController(BizExamGroupService examGroupService,
                                 BizExamGroupMemberMapper memberMapper,
                                 BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                 BizSelfEvaluationMapper selfEvaluationMapper,
                                 BizPeerEvaluationMapper peerEvaluationMapper) {
        this.examGroupService = examGroupService;
        this.memberMapper = memberMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.selfEvaluationMapper = selfEvaluationMapper;
        this.peerEvaluationMapper = peerEvaluationMapper;
    }

    @GetMapping("/list")
    public Result<List<BizExamGroup>> list() {
        LambdaQueryWrapper<BizExamGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroup::getExamType, "MONTHLY");
        DataScopeFilter.applyUnitFilter(wrapper, BizExamGroup::getUnitId);
        wrapper.orderByDesc(BizExamGroup::getCreatedTime);
        return Result.success(examGroupService.list(wrapper));
    }

    @GetMapping("/{examGroupId}/status")
    public Result<Map<String, Object>> status(@PathVariable Long examGroupId) {
        ProgressAggregate agg = buildProgressAggregate(examGroupId);

        int totalSelfIndicators = 0;
        int completedSelfIndicators = 0;
        int totalPeerIndicators = 0;
        int completedPeerIndicators = 0;
        List<Map<String, Object>> deptDetails = new ArrayList<>();

        int memberCount = agg.members.size();
        for (BizExamGroupMember member : agg.members) {
            int deptIndCount = agg.indicatorCountByOrg.getOrDefault(member.getOrgId(), 0);
            int selfSubmitted = agg.selfSubmittedByOrg.getOrDefault(member.getOrgId(), 0);

            totalSelfIndicators += deptIndCount;
            completedSelfIndicators += selfSubmitted;

            // 他评：所有其他部门对该部门的评估总数
            int deptPeerTotal = deptIndCount * Math.max(0, memberCount - 1);
            int deptPeerCompleted = agg.peerSubmittedByTargetOrg.getOrDefault(member.getOrgId(), 0);

            totalPeerIndicators += deptPeerTotal;
            completedPeerIndicators += deptPeerCompleted;

            Map<String, Object> deptMap = new HashMap<>();
            deptMap.put("orgId", member.getOrgId());
            deptMap.put("orgName", member.getOrgName());
            deptMap.put("selfEvalStatus", selfSubmitted >= deptIndCount && deptIndCount > 0 ? "已完成" : "待提交");
            deptMap.put("peerEvalCompleted", deptPeerCompleted);
            deptMap.put("peerEvalTotal", deptPeerTotal);
            deptMap.put("peerEvalProgress", percent(deptPeerCompleted, deptPeerTotal));
            deptDetails.add(deptMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("selfEvalRate", percent(completedSelfIndicators, totalSelfIndicators));
        result.put("peerEvalRate", percent(completedPeerIndicators, totalPeerIndicators));
        result.put("deptDetails", deptDetails);
        return Result.success(result);
    }

    @GetMapping("/{examGroupId}/dept-progress")
    public Result<List<Map<String, Object>>> deptProgress(@PathVariable Long examGroupId) {
        ProgressAggregate agg = buildProgressAggregate(examGroupId);

        List<Map<String, Object>> result = new ArrayList<>();
        int memberCount = agg.members.size();
        for (BizExamGroupMember member : agg.members) {
            int indCount = agg.indicatorCountByOrg.getOrDefault(member.getOrgId(), 0);
            int selfCount = agg.selfSubmittedByOrg.getOrDefault(member.getOrgId(), 0);
            int peerTotal = indCount * Math.max(0, memberCount - 1);
            int peerCompleted = agg.peerSubmittedByTargetOrg.getOrDefault(member.getOrgId(), 0);

            Map<String, Object> map = new HashMap<>();
            map.put("orgId", member.getOrgId());
            map.put("orgName", member.getOrgName());
            map.put("selfEvalStatus", selfCount >= indCount && indCount > 0 ? "已完成" : "待提交");
            map.put("selfEvalProgress", percent(selfCount, indCount));
            map.put("peerEvalProgress", percent(peerCompleted, peerTotal));
            map.put("overallStatus", (selfCount >= indCount && peerCompleted >= peerTotal && indCount > 0) ? "已完成" : "进行中");
            result.add(map);
        }
        return Result.success(result);
    }

    /**
     * 三次批量聚合查询，替换原先 O(N^2) 的循环单条查询。
     */
    private ProgressAggregate buildProgressAggregate(Long examGroupId) {
        ProgressAggregate agg = new ProgressAggregate();

        agg.members = memberMapper.selectList(
                new LambdaQueryWrapper<BizExamGroupMember>().eq(BizExamGroupMember::getExamGroupId, examGroupId)
        );

        List<BizIndicatorDefinition> approvedIndicators = indicatorDefinitionMapper.selectList(
                new LambdaQueryWrapper<BizIndicatorDefinition>()
                        .eq(BizIndicatorDefinition::getExamGroupId, examGroupId)
                        .eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED")
        );
        for (BizIndicatorDefinition ind : approvedIndicators) {
            agg.indicatorCountByOrg.merge(ind.getOrgId(), 1, Integer::sum);
        }

        List<BizSelfEvaluation> selfSubmitted = selfEvaluationMapper.selectList(
                new LambdaQueryWrapper<BizSelfEvaluation>()
                        .eq(BizSelfEvaluation::getExamGroupId, examGroupId)
                        .eq(BizSelfEvaluation::getStatus, "SUBMITTED")
        );
        for (BizSelfEvaluation se : selfSubmitted) {
            agg.selfSubmittedByOrg.merge(se.getOrgId(), 1, Integer::sum);
        }

        List<BizPeerEvaluation> peerSubmitted = peerEvaluationMapper.selectList(
                new LambdaQueryWrapper<BizPeerEvaluation>()
                        .eq(BizPeerEvaluation::getExamGroupId, examGroupId)
                        .eq(BizPeerEvaluation::getStatus, "SUBMITTED")
        );
        for (BizPeerEvaluation pe : peerSubmitted) {
            agg.peerSubmittedByTargetOrg.merge(pe.getTargetOrgId(), 1, Integer::sum);
        }

        return agg;
    }

    /**
     * 百分比计算，保留两位小数（避免整数除法精度丢失）。
     */
    private static double percent(int numerator, int denominator) {
        if (denominator <= 0) return 0d;
        return Math.round((double) numerator * 10000d / denominator) / 100d;
    }

    private static class ProgressAggregate {
        List<BizExamGroupMember> members;
        final Map<Long, Integer> indicatorCountByOrg = new HashMap<>();
        final Map<Long, Integer> selfSubmittedByOrg = new HashMap<>();
        final Map<Long, Integer> peerSubmittedByTargetOrg = new HashMap<>();
    }
}
