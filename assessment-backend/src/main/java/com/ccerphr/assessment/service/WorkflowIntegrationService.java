package com.ccerphr.assessment.service;

import com.ccerphr.assessment.config.WorkflowConfig;
import com.ccerphr.assessment.context.DataScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.*;

/**
 * 工作流平台集成服务
 *
 * 职责：
 * 1. 查询当前用户在工作流平台中的用户 UUID（基于角色）
 * 2. 启动业绩指标审核工作流实例
 * 3. 代理查询工作流待办任务
 * 4. 代理审批操作
 */
@Service
public class WorkflowIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowIntegrationService.class);

    private final WorkflowConfig workflowConfig;
    private final RestTemplate restTemplate;

    public WorkflowIntegrationService(WorkflowConfig workflowConfig, RestTemplateBuilder restTemplateBuilder) {
        this.workflowConfig = workflowConfig;
        this.restTemplate = restTemplateBuilder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * 创建带有认证头的 HttpHeaders
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = workflowConfig.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    // ===== 1. 当前用户的工作流 UUID =====

    /**
     * 获取当前登录用户的工作流平台用户 UUID
     * 基于 DataScopeContext 中的角色代码查询
     */
    public String getCurrentUserWorkflowId() {
        String roleCode = DataScopeContext.getRoleCode();
        String userId = workflowConfig.getWorkflowUserId(roleCode);
        if (userId == null) {
            log.warn("未找到角色 {} 对应的工作流用户 UUID，请检查 WorkflowConfig.roleUserIdMap 配置", roleCode);
        }
        return userId;
    }

    /**
     * 根据角色代码获取工作流用户 UUID
     */
    public String getWorkflowUserIdByRole(String roleCode) {
        return workflowConfig.getWorkflowUserId(roleCode);
    }

    // ===== 2. 启动工作流实例 =====

    /**
     * 启动业绩指标审核工作流
     *
     * @param examGroupId  考核组 ID（工作流变量）
     * @param orgId        部门 ID（工作流变量）
     * @param variables    其他变量（如 indicatorIds 等）
     * @return 工作流实例响应（JSON）
     */
    public Map<String, Object> startIndicatorWorkflow(Long examGroupId, Long orgId, Map<String, Object> variables) {
        String workflowId = workflowConfig.getIndicatorWorkflowId();
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalStateException(
                "工作流 ID 未配置，请设置 app.workflow.indicator-workflow-id " +
                "或调用 /api/workflow/init 初始化工作流"
            );
        }

        String url = UriComponentsBuilder.fromHttpUrl(workflowConfig.getBaseUrl())
            .path("/api/instances/start")
            .build()
            .toUriString();

        Map<String, Object> body = new HashMap<>();
        body.put("workflow_id", workflowId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("examGroupId", examGroupId);
        vars.put("orgId", orgId);
        if (variables != null) {
            vars.putAll(variables);
        }
        body.put("variables", vars);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> result = response.getBody();
            log.info("启动工作流成功: workflowId={}, instanceId={}", workflowId,
                result != null ? result.get("id") : "null");
            return result != null ? result : Collections.emptyMap();
        } catch (RestClientException e) {
            log.error("启动工作流失败: {}", e.getMessage(), e);
            throw new RuntimeException("启动工作流失败: " + e.getMessage(), e);
        }
    }

    // ===== 3. 查询待办任务 =====

    /**
     * 查询指定用户（UUID）的待办审批任务
     *
     * @param workflowUserId 工作流平台用户 UUID
     * @return 待办任务列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPendingTasks(String workflowUserId) {
        if (workflowUserId == null || workflowUserId.isBlank()) {
            return Collections.emptyList();
        }

        String url = UriComponentsBuilder.fromHttpUrl(workflowConfig.getBaseUrl())
            .path("/api/tasks")
            .queryParam("approver_id", workflowUserId)
            .build()
            .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("查询待办任务失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ===== 4. 执行审批 =====

    /**
     * 执行审批操作
     *
     * @param nodeInstanceId 节点实例 UUID（来自待办任务）
     * @param action         操作类型：approve / reject
     * @param comment        审批意见
     * @return 审批响应
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeApproval(String nodeInstanceId, String action, String comment) {
        if (nodeInstanceId == null) {
            throw new IllegalArgumentException("nodeInstanceId 不能为空");
        }

        String url = UriComponentsBuilder.fromHttpUrl(workflowConfig.getBaseUrl())
            .path("/api/approvals/{nodeInstanceId}")
            .buildAndExpand(nodeInstanceId)
            .toUriString();

        Map<String, Object> body = new HashMap<>();
        // 使用当前用户的工作流 UUID 作为审批人
        String approverId = getCurrentUserWorkflowId();
        if (approverId == null) {
            throw new IllegalStateException("当前用户未配置工作流用户 UUID，无法执行审批");
        }
        body.put("approver_id", approverId);
        body.put("action", action);
        body.put("comment", comment != null ? comment : "");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> result = response.getBody();
            log.info("审批完成: nodeInstanceId={}, action={}, success={}", nodeInstanceId, action,
                result != null ? result.get("success") : "null");
            return result != null ? result : Collections.emptyMap();
        } catch (RestClientException e) {
            log.error("审批失败: {}", e.getMessage(), e);
            throw new RuntimeException("审批失败: " + e.getMessage(), e);
        }
    }

    // ===== 5. 查询审批历史 =====

    /**
     * 查询工作流实例的审批历史
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getInstanceHistory(String instanceId) {
        if (instanceId == null) return Collections.emptyList();

        String url = UriComponentsBuilder.fromHttpUrl(workflowConfig.getBaseUrl())
            .path("/api/instances/{instanceId}/history")
            .buildAndExpand(instanceId)
            .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("查询审批历史失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ===== 6. 查询工作流定义列表 =====

    /**
     * 获取已发布的工作流列表（用于查找业绩指标审核工作流 ID）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPublishedWorkflows() {
        String url = UriComponentsBuilder.fromHttpUrl(workflowConfig.getBaseUrl())
            .path("/api/workflows")
            .queryParam("status", "published")
            .build()
            .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (RestClientException e) {
            log.error("查询工作流列表失败: {}", e.getMessage());
            throw new RuntimeException("查询工作流列表失败: " + e.getMessage(), e);
        }
    }
}
