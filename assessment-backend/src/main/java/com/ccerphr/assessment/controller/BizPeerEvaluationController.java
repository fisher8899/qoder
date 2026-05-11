package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.PeerEvalQueryDTO;
import com.ccerphr.assessment.dto.PeerEvalSaveDTO;
import com.ccerphr.assessment.service.BizPeerEvaluationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation/peer")
public class BizPeerEvaluationController {

    private final BizPeerEvaluationService peerEvaluationService;

    public BizPeerEvaluationController(BizPeerEvaluationService peerEvaluationService) {
        this.peerEvaluationService = peerEvaluationService;
    }

    @GetMapping("/task")
    public Result<List<Map<String, Object>>> taskList(PeerEvalQueryDTO queryDTO) {
        return Result.success(peerEvaluationService.getTaskList(queryDTO));
    }

    @GetMapping("/targets")
    public Result<List<Map<String, Object>>> targets(@RequestParam Long examGroupId, @RequestParam Long evaluatorOrgId) {
        return Result.success(peerEvaluationService.getTargetDepts(examGroupId, evaluatorOrgId));
    }

    @GetMapping("/by-dept")
    public Result<List<Map<String, Object>>> byDept(@RequestParam Long examGroupId, @RequestParam Long evaluatorOrgId, @RequestParam Long targetOrgId) {
        return Result.success(peerEvaluationService.getPeerEvalByDept(examGroupId, evaluatorOrgId, targetOrgId));
    }

    @GetMapping("/by-indicator")
    public Result<List<Map<String, Object>>> byIndicator(@RequestParam Long examGroupId, @RequestParam Long evaluatorOrgId, @RequestParam(required = false) Long categoryId) {
        return Result.success(peerEvaluationService.getPeerEvalByIndicator(examGroupId, evaluatorOrgId, categoryId));
    }

    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody PeerEvalSaveDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        peerEvaluationService.savePeerEval(dto);
        return Result.success();
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam Long examGroupId, @RequestParam Long evaluatorOrgId, @RequestParam Long targetOrgId, HttpServletRequest request) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        String submittedBy = getCurrentUser(request);
        peerEvaluationService.submitPeerEval(examGroupId, evaluatorOrgId, targetOrgId, submittedBy);
        return Result.success();
    }

    @GetMapping("/statistics")
    public Result<List<Map<String, Object>>> statistics(@RequestParam Long examGroupId) {
        return Result.success(peerEvaluationService.getStatistics(examGroupId));
    }

    private String getCurrentUser(HttpServletRequest request) {
        String user = request.getHeader("X-Current-User");
        if (user == null || user.isEmpty()) {
            user = "system";
        }
        return user;
    }
}
