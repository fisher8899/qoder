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
        // 数据范围过滤
        DataScopeFilter.applyUnitFilter(wrapper, BizExamGroup::getUnitId);
        wrapper.orderByDesc(BizExamGroup::getCreatedTime);
        return Result.success(examGroupService.list(wrapper));
    }

    @GetMapping("/{examGroupId}/status")
    public Result<Map<String, Object>> status(@PathVariable Long examGroupId) {
        Map<String, Object> result = new HashMap<>();

        List<BizExamGroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<BizExamGroupMember>().eq(BizExamGroupMember::getExamGroupId, examGroupId)
        );

        int totalSelfIndicators = 0;
        int completedSelfIndicators = 0;
        int totalPeerIndicators = 0;
        int completedPeerIndicators = 0;

        List<Map<String, Object>> deptDetails = new ArrayList<>();

        for (BizExamGroupMember member : members) {
            LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
            indWrapper.eq(BizIndicatorDefinition::getOrgId, member.getOrgId());
            indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
            int deptIndCount = indicatorDefinitionMapper.selectCount(indWrapper).intValue();

            LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
            selfWrapper.eq(BizSelfEvaluation::getOrgId, member.getOrgId());
            selfWrapper.eq(BizSelfEvaluation::getStatus, "SUBMITTED");
            int selfSubmitted = selfEvaluationMapper.selectCount(selfWrapper).intValue();

            totalSelfIndicators += deptIndCount;
            completedSelfIndicators += selfSubmitted;

            // 他评：该部门被其他部门评估的情况
            int deptPeerTotal = 0;
            int deptPeerCompleted = 0;
            for (BizExamGroupMember other : members) {
                if (other.getOrgId().equals(member.getOrgId())) continue;
                LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
                peerWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
                peerWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, other.getOrgId());
                peerWrapper.eq(BizPeerEvaluation::getTargetOrgId, member.getOrgId());
                peerWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
                int peerSubmitted = peerEvaluationMapper.selectCount(peerWrapper).intValue();
                deptPeerTotal += deptIndCount;
                deptPeerCompleted += peerSubmitted;
            }

            totalPeerIndicators += deptPeerTotal;
            completedPeerIndicators += deptPeerCompleted;

            Map<String, Object> deptMap = new HashMap<>();
            deptMap.put("orgId", member.getOrgId());
            deptMap.put("orgName", member.getOrgName());
            deptMap.put("selfEvalStatus", selfSubmitted >= deptIndCount && deptIndCount > 0 ? "已完成" : "待提交");
            deptMap.put("peerEvalCompleted", deptPeerCompleted);
            deptMap.put("peerEvalTotal", deptPeerTotal);
            deptMap.put("peerEvalProgress", deptPeerTotal > 0 ? (deptPeerCompleted * 100 / deptPeerTotal) : 0);
            deptDetails.add(deptMap);
        }

        int selfRate = totalSelfIndicators > 0 ? (completedSelfIndicators * 100 / totalSelfIndicators) : 0;
        int peerRate = totalPeerIndicators > 0 ? (completedPeerIndicators * 100 / totalPeerIndicators) : 0;

        result.put("selfEvalRate", selfRate);
        result.put("peerEvalRate", peerRate);
        result.put("deptDetails", deptDetails);
        return Result.success(result);
    }

    @GetMapping("/{examGroupId}/dept-progress")
    public Result<List<Map<String, Object>>> deptProgress(@PathVariable Long examGroupId) {
        List<BizExamGroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<BizExamGroupMember>().eq(BizExamGroupMember::getExamGroupId, examGroupId)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroupMember member : members) {
            LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
            indWrapper.eq(BizIndicatorDefinition::getOrgId, member.getOrgId());
            indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
            int indCount = indicatorDefinitionMapper.selectCount(indWrapper).intValue();

            LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
            selfWrapper.eq(BizSelfEvaluation::getOrgId, member.getOrgId());
            selfWrapper.eq(BizSelfEvaluation::getStatus, "SUBMITTED");
            int selfCount = selfEvaluationMapper.selectCount(selfWrapper).intValue();

            int peerTotal = 0;
            int peerCompleted = 0;
            for (BizExamGroupMember other : members) {
                if (other.getOrgId().equals(member.getOrgId())) continue;
                LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
                peerWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
                peerWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, other.getOrgId());
                peerWrapper.eq(BizPeerEvaluation::getTargetOrgId, member.getOrgId());
                peerWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
                int pCount = peerEvaluationMapper.selectCount(peerWrapper).intValue();
                peerTotal += indCount;
                peerCompleted += pCount;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("orgId", member.getOrgId());
            map.put("orgName", member.getOrgName());
            map.put("selfEvalStatus", selfCount >= indCount && indCount > 0 ? "已完成" : "待提交");
            map.put("selfEvalProgress", indCount > 0 ? (selfCount * 100 / indCount) : 0);
            map.put("peerEvalProgress", peerTotal > 0 ? (peerCompleted * 100 / peerTotal) : 0);
            map.put("overallStatus", (selfCount >= indCount && peerCompleted >= peerTotal && indCount > 0) ? "已完成" : "进行中");
            result.add(map);
        }
        return Result.success(result);
    }
}
