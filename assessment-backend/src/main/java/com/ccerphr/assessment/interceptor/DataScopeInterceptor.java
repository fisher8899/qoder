package com.ccerphr.assessment.interceptor;

import com.ccerphr.assessment.context.DataScopeContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 数据范围拦截器 - 从请求Header中提取权限信息存入ThreadLocal
 */
@Component
public class DataScopeInterceptor implements HandlerInterceptor {

    private final DataSource dataSource;

    public DataScopeInterceptor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String roleCode = request.getHeader("X-Role-Code");
        String dataScope = request.getHeader("X-Data-Scope");
        String scopeIdStr = request.getHeader("X-Scope-Id");
        String scopeName = request.getHeader("X-Scope-Name");
        String userIdStr = request.getHeader("X-User-Id");

        if (roleCode != null && dataScope != null) {
            DataScopeContext.DataScopeInfo info = new DataScopeContext.DataScopeInfo();
            info.setRoleCode(roleCode);
            info.setDataScope(dataScope);
            Long scopeId = scopeIdStr != null ? Long.parseLong(scopeIdStr) : 0L;
            info.setScopeId(scopeId);
            info.setScopeName(scopeName);
            info.setUserId(userIdStr != null ? Long.parseLong(userIdStr) : null);

            // 预解析 unitId
            if ("UNIT".equals(dataScope)) {
                info.setUnitId(scopeId);
            } else if ("ORG".equals(dataScope) && scopeId != null && scopeId != 0L) {
                Long orgUnitId = getOrgUnitId(scopeId);
                info.setUnitId(orgUnitId);
            }

            DataScopeContext.set(info);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DataScopeContext.clear();
    }

    /**
     * 根据组织ID查询其所属单位ID
     */
    private Long getOrgUnitId(Long orgId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT unit_id FROM sys_organization WHERE id = ? AND deleted = 0")) {
            ps.setLong(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long unitId = rs.getLong("unit_id");
                    return rs.wasNull() ? null : unitId;
                }
            }
        } catch (Exception e) {
            // 查询失败时不影响主流程
        }
        return null;
    }
}
