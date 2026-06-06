package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.service.BizLeaderEvaluationService;
import com.ccerphr.assessment.security.SecurityUtil;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation/leader")
public class BizLeaderEvaluationController {

    private final BizLeaderEvaluationService leaderEvaluationService;
    private final SysUserMapper userMapper;

    public BizLeaderEvaluationController(BizLeaderEvaluationService leaderEvaluationService,
                                          SysUserMapper userMapper) {
        this.leaderEvaluationService = leaderEvaluationService;
        this.userMapper = userMapper;
    }

    /**
     * 获取分管领导的评估任务列表
     */
    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> getTasks() {
        Long employeeId = getCurrentEmployeeId();
        if (employeeId == null) {
            return Result.error("当前用户未绑定员工信息");
        }
        return Result.success(leaderEvaluationService.getTaskList(employeeId));
    }

    /**
     * 获取某考核组下该领导需要评估的指标（按部门）
     */
    @GetMapping("/indicators")
    public Result<List<Map<String, Object>>> getIndicators(@RequestParam Long examGroupId) {
        Long employeeId = getCurrentEmployeeId();
        if (employeeId == null) {
            return Result.error("当前用户未绑定员工信息");
        }
        return Result.success(leaderEvaluationService.getIndicatorsByDept(examGroupId, employeeId));
    }

    /**
     * 保存领导打分
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody Map<String, Object> data) {
        Long employeeId = getCurrentEmployeeId();
        if (employeeId == null) {
            return Result.error("当前用户未绑定员工信息");
        }
        Long indicatorId = toLong(data.get("indicatorId"));
        Long leaderId = leaderEvaluationService.findLeaderIdForIndicator(employeeId, indicatorId);
        if (leaderId == null) {
            return Result.error("当前用户未关联分管领导");
        }
        data.put("leaderId", leaderId);
        leaderEvaluationService.save(data);
        return Result.success();
    }

    /**
     * 提交领导评估
     */
    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam Long examGroupId, @RequestParam Long targetOrgId) {
        Long employeeId = getCurrentEmployeeId();
        if (employeeId == null) {
            return Result.error("当前用户未绑定员工信息");
        }
        Long leaderId = leaderEvaluationService.findLeaderId(employeeId);
        if (leaderId == null) {
            return Result.error("当前用户未关联分管领导");
        }
        String submittedBy = SecurityUtil.getCurrentUserName();
        leaderEvaluationService.submit(examGroupId, leaderId, targetOrgId, submittedBy);
        return Result.success();
    }

    private Long getCurrentEmployeeId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) return null;
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getEmployeeId() : null;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long) return (Long) val;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
