package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.IndicatorApprovalDTO;
import com.ccerphr.assessment.dto.IndicatorProgressVO;
import com.ccerphr.assessment.dto.IndicatorQueryDTO;
import com.ccerphr.assessment.dto.IndicatorSetDTO;
import com.ccerphr.assessment.dto.IndicatorTreeDTO;
import com.ccerphr.assessment.dto.IndicatorVO;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.security.UnitScopeAccess;
import com.ccerphr.assessment.service.BizIndicatorDefinitionService;
import com.ccerphr.assessment.service.WorkflowIntegrationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/indicator")
public class BizIndicatorDefinitionController {

    private final BizIndicatorDefinitionService indicatorService;
    private final WorkflowIntegrationService workflowService;

    public BizIndicatorDefinitionController(
            BizIndicatorDefinitionService indicatorService,
            WorkflowIntegrationService workflowService) {
        this.indicatorService = indicatorService;
        this.workflowService = workflowService;
    }

    @GetMapping("/list")
    public Result<PageResult<IndicatorVO>> list(IndicatorQueryDTO queryDTO) {
        return Result.success(indicatorService.queryPage(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<BizIndicatorDefinition> detail(@PathVariable Long id) {
        return Result.success(indicatorService.getDetail(id));
    }

    @GetMapping("/tree/{examGroupId}/{orgId}")
    public Result<List<IndicatorTreeDTO>> tree(@PathVariable Long examGroupId, @PathVariable Long orgId) {
        return Result.success(indicatorService.getIndicatorTree(examGroupId, orgId));
    }

    @PostMapping
    public Result<Long> create(@RequestBody IndicatorSetDTO dto) {
        ensureIndicatorEditAccess();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("Current data scope is read-only");
        }
        Long id = indicatorService.createIndicator(dto);
        return Result.success(id);
    }

    @PutMapping
    public Result<Void> update(@RequestBody IndicatorSetDTO dto) {
        ensureIndicatorEditAccess();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("Current data scope is read-only");
        }
        indicatorService.updateIndicator(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ensureIndicatorEditAccess();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("Current data scope is read-only");
        }
        indicatorService.deleteIndicator(id);
        return Result.success();
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody List<Long> indicatorIds) {
        ensureIndicatorEditAccess();

        // 1. 更新 QODER 指标状态
        indicatorService.submitForApproval(indicatorIds);

        // 2. 同步启动工作流平台审批流程（不阻断主流程）
        if (indicatorIds != null && !indicatorIds.isEmpty()) {
            try {
                BizIndicatorDefinition first = indicatorService.getDetail(indicatorIds.get(0));
                if (first != null && first.getExamGroupId() != null && first.getOrgId() != null) {
                    workflowService.startIndicatorWorkflow(
                        first.getExamGroupId(),
                        first.getOrgId(),
                        java.util.Map.of("indicatorIds", indicatorIds)
                    );
                }
            } catch (Exception e) {
                // 工作流启动失败不阻断提交，仅记录日志
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("启动工作流失败（不阻断提交）: {}", e.getMessage());
            }
        }

        return Result.success();
    }

    @GetMapping("/approval/list")
    public Result<PageResult<IndicatorVO>> approvalList(IndicatorQueryDTO queryDTO) {
        String roleCode = DataScopeContext.getRoleCode();
        return Result.success(indicatorService.getApprovalList(queryDTO, roleCode));
    }

    @PostMapping("/approve")
    @RequireRole({"DEPT_LEADER", "SUPERVISOR", "FIN_ADMIN", "ADMIN"})
    public Result<Void> approve(@RequestBody IndicatorApprovalDTO dto) {
        dto.setRoleCode(DataScopeContext.getRoleCode());
        indicatorService.approve(dto);
        return Result.success();
    }

    @PostMapping("/reject")
    @RequireRole({"DEPT_LEADER", "SUPERVISOR", "FIN_ADMIN", "ADMIN"})
    public Result<Void> reject(@RequestBody IndicatorApprovalDTO dto) {
        dto.setRoleCode(DataScopeContext.getRoleCode());
        indicatorService.reject(dto);
        return Result.success();
    }

    @GetMapping("/progress/list")
    public Result<List<IndicatorProgressVO>> queryProgress(
            @RequestParam(required = false) Long examGroupId,
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false) String approvalStatus) {
        List<IndicatorProgressVO> list = indicatorService.queryProgress(examGroupId, orgName, approvalStatus);
        return Result.success(list);
    }

    private void ensureIndicatorEditAccess() {
        UnitScopeAccess.requireIndicatorEditor();
    }
}
