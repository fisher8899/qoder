package com.ccerphr.assessment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.workflow")
public class WorkflowConfig {
    /** 工作流平台根地址（生产环境应为 HTTPS） */
    private String baseUrl = "https://localhost:5555";

    /** 工作流平台 API Key（用于 Authorization 请求头） */
    private String apiKey;

    /** 业绩指标审核工作流 ID（UUID 字符串） */
    private String indicatorWorkflowId;

    /**
     * 角色代码 -> 工作流平台用户 UUID 映射表
     * key: QODER 角色代码（DEPT_LEADER / SUPERVISOR / FIN_ADMIN 等）
     * value: 工作流平台中对应审批人的 UUID
     *
     * 配置说明：
     * - 该映射表需与工作流平台中节点配置的 approver_config.user_ids 一致
     * - 修改后需同步更新工作流平台的节点配置
     * - 通过 application.yml 中 app.workflow.role-user-ids 配置
     */
    private Map<String, String> roleUserIdMap;

    /**
     * 根据角色代码获取工作流平台用户 UUID
     */
    public String getWorkflowUserId(String roleCode) {
        if (roleCode == null) return null;
        return roleUserIdMap.get(roleCode);
    }
}
