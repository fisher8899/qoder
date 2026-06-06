package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.config.WorkflowConfig;
import com.ccerphr.assessment.service.WorkflowIntegrationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作流平台集成接口
 *
 * 供 QODER 前端调用，用于：
 * 1. 获取当前用户的工作流 UUID
 * 2. 启动业绩指标审核工作流
 * 3. 查询待办审批任务
 * 4. 执行审批操作
 */
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowIntegrationService workflowService;
    private final WorkflowConfig workflowConfig;

    public WorkflowController(WorkflowIntegrationService workflowService, WorkflowConfig workflowConfig) {
        this.workflowService = workflowService;
        this.workflowConfig = workflowConfig;
    }

    /**
     * GET /api/workflow/my-id
     * 获取当前登录用户的工作流平台用户 UUID
     */
    @GetMapping("/my-id")
    public Result<String> getMyWorkflowUserId() {
        String userId = workflowService.getCurrentUserWorkflowId();
        return Result.success(userId);
    }

    /**
     * GET /api/workflow/approver-id?roleCode=xxx
     * 根据角色代码查询对应的工作流用户 UUID
     */
    @GetMapping("/approver-id")
    public Result<String> getApproverId(@RequestParam String roleCode) {
        String userId = workflowService.getWorkflowUserIdByRole(roleCode);
        return Result.success(userId);
    }

    /**
     * POST /api/workflow/start-indicator
     * 启动业绩指标审核工作流
     *
     * body: { examGroupId: Long, orgId: Long, variables?: Map }
     */
    @PostMapping("/start-indicator")
    public Result<Map<String, Object>> startIndicatorWorkflow(@RequestBody Map<String, Object> body) {
        Long examGroupId = body.get("examGroupId") != null
            ? Long.valueOf(body.get("examGroupId").toString()) : null;
        Long orgId = body.get("orgId") != null
            ? Long.valueOf(body.get("orgId").toString()) : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) body.get("variables");

        Map<String, Object> result = workflowService.startIndicatorWorkflow(examGroupId, orgId, variables);
        return Result.success(result);
    }

    /**
     * GET /api/workflow/tasks
     * 查询当前用户的待办审批任务
     */
    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> getPendingTasks() {
        String workflowUserId = workflowService.getCurrentUserWorkflowId();
        List<Map<String, Object>> tasks = workflowService.getPendingTasks(workflowUserId);
        return Result.success(tasks);
    }

    /**
     * POST /api/workflow/approve
     * 执行审批通过
     *
     * body: { nodeInstanceId: String, comment?: String }
     */
    @PostMapping("/approve")
    public Result<Map<String, Object>> approve(@RequestBody Map<String, Object> body) {
        String nodeInstanceId = (String) body.get("nodeInstanceId");
        String comment = body.get("comment") != null ? body.get("comment").toString() : "";
        Map<String, Object> result = workflowService.executeApproval(nodeInstanceId, "approve", comment);
        return Result.success(result);
    }

    /**
     * POST /api/workflow/reject
     * 执行审批驳回
     *
     * body: { nodeInstanceId: String, comment?: String }
     */
    @PostMapping("/reject")
    public Result<Map<String, Object>> reject(@RequestBody Map<String, Object> body) {
        String nodeInstanceId = (String) body.get("nodeInstanceId");
        String comment = body.get("comment") != null ? body.get("comment").toString() : "";
        Map<String, Object> result = workflowService.executeApproval(nodeInstanceId, "reject", comment);
        return Result.success(result);
    }

    /**
     * GET /api/workflow/history/{instanceId}
     * 查询工作流实例的审批历史
     */
    @GetMapping("/history/{instanceId}")
    public Result<List<Map<String, Object>>> getHistory(@PathVariable String instanceId) {
        List<Map<String, Object>> history = workflowService.getInstanceHistory(instanceId);
        return Result.success(history);
    }

    /**
     * POST /api/workflow/init
     * 初始化工作流：自动查找业绩指标审核工作流 ID 并回写到配置
     * 仅用于首次配置；配置好后不要再调用
     */
    @PostMapping("/init")
    public Result<String> initWorkflow() {
        Map<String, Object> workflows = workflowService.getPublishedWorkflows();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) workflows.get("items");

        String targetWorkflowId = null;
        if (items != null) {
            for (Map<String, Object> item : items) {
                String name = (String) item.get("name");
                if ("业绩指标审核".equals(name)) {
                    targetWorkflowId = (String) item.get("id");
                    break;
                }
            }
        }

        if (targetWorkflowId == null) {
            return Result.error("未找到已发布的「业绩指标审核」工作流，请先在工作流平台中配置并发布");
        }

        // 回写到 workflowConfig（实际生产中应持久化到数据库或配置文件）
        workflowConfig.setIndicatorWorkflowId(targetWorkflowId);
        return Result.success("工作流 ID 已设置为: " + targetWorkflowId);
    }
}
