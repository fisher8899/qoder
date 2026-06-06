package com.ccerphr.assessment.interceptor;

import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import com.ccerphr.assessment.security.JwtAuthenticationFilter.JwtAuthenticationDetails;
import com.ccerphr.assessment.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class DataScopeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataScopeInterceptor.class);

    private final DataSource dataSource;
    private final SysUserPermissionMapper userPermissionMapper;

    public DataScopeInterceptor(DataSource dataSource, SysUserPermissionMapper userPermissionMapper) {
        this.dataSource = dataSource;
        this.userPermissionMapper = userPermissionMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            log.debug("No authenticated user, skipping data scope setup");
            return true;
        }

        List<SysUserPermission> permissions = userPermissionMapper.selectActiveByUserId(userId);
        JwtAuthenticationDetails details = getJwtDetails();

        // 优先从请求头读取前端主动切换的角色/范围，其次使用 JWT token 中的默认值
        String headerRole = request.getHeader("X-Role-Code");
        String headerScopeIdStr = request.getHeader("X-Scope-Id");
        Long headerScopeId = null;
        try {
            if (StringUtils.hasText(headerScopeIdStr)) {
                headerScopeId = Long.parseLong(headerScopeIdStr);
            }
        } catch (NumberFormatException ignored) {}

        String activeRoleCode = StringUtils.hasText(headerRole) ? headerRole : (details != null ? details.getActiveRoleCode() : null);
        Long activeScopeId = headerScopeId != null ? headerScopeId : (details != null ? details.getActiveScopeId() : null);

        SysUserPermission matchedPermission = findMatchingPermission(permissions, activeRoleCode, activeScopeId);
        if (matchedPermission == null) {
            if (StringUtils.hasText(activeRoleCode) || activeScopeId != null) {
                log.warn("No exact permission matched for userId={}, token role={}, scopeId={}",
                        userId, activeRoleCode, activeScopeId);
                writeForbidden(response, "当前职责或数据范围无效，请重新选择后再试");
                return false;
            }
            if (permissions.isEmpty()) {
                log.warn("User {} has no active permissions", userId);
                writeForbidden(response, "当前用户未配置有效职责和数据范围，请先在用户权限分配中维护");
                return false;
            }
            if (permissions.size() > 1) {
                log.warn("User {} has multiple active permissions but token missing role/scope context", userId);
                writeForbidden(response, "当前请求缺少职责上下文，请重新选择职责后再试");
                return false;
            }
            matchedPermission = permissions.get(0);
        }

        DataScopeContext.DataScopeInfo info = new DataScopeContext.DataScopeInfo();
        info.setUserId(userId);
        info.setRoleCode(matchedPermission.getRoleCode());
        info.setDataScope(matchedPermission.getDataScope());
        info.setScopeId(matchedPermission.getScopeId() != null ? matchedPermission.getScopeId() : 0L);
        info.setScopeName(matchedPermission.getScopeName());

        if ("UNIT".equals(matchedPermission.getDataScope())) {
            Long scopeUnitId = matchedPermission.getScopeId();
            info.setUnitId(scopeUnitId);
            info.setVisibleUnitIds(scopeUnitId == null ? Collections.emptyList() : Collections.singletonList(scopeUnitId));
            info.setVisibleOrgIds(getVisibleOrgIdsForUnit(scopeUnitId));
        } else if ("ORG".equals(matchedPermission.getDataScope()) && matchedPermission.getScopeId() != null) {
            Long orgId = matchedPermission.getScopeId();
            Long orgUnitId = getOrgUnitId(orgId);
            info.setUnitId(orgUnitId);
            if (orgUnitId != null) {
                info.setVisibleUnitIds(Collections.singletonList(orgUnitId));
            }
            info.setVisibleOrgIds(Collections.singletonList(orgId));
        }

        DataScopeContext.set(info);
        log.debug("DataScope set: userId={}, roleCode={}, dataScope={}, scopeId={}",
                userId, matchedPermission.getRoleCode(), matchedPermission.getDataScope(), matchedPermission.getScopeId());
        log.info("[DataScope] userId={}, roleCode={}, dataScope={}, scopeId={}, visibleOrgIds={}",
                userId, info.getRoleCode(), info.getDataScope(), info.getScopeId(), info.getVisibleOrgIds());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DataScopeContext.clear();
    }

    private SysUserPermission findMatchingPermission(List<SysUserPermission> permissions,
                                                     String activeRoleCode,
                                                     Long activeScopeId) {
        log.info("[findMatchingPermission] permissions count={}, activeRoleCode={}, activeScopeId={}",
                permissions.size(), activeRoleCode, activeScopeId);
        if (!StringUtils.hasText(activeRoleCode) && activeScopeId == null) {
            return null;
        }
        for (SysUserPermission permission : permissions) {
            Long permissionScopeId = permission.getScopeId() != null ? permission.getScopeId() : 0L;
            boolean roleMatched = StringUtils.hasText(activeRoleCode) && activeRoleCode.equals(permission.getRoleCode());
            boolean scopeMatched = activeScopeId != null && activeScopeId.equals(permissionScopeId);
            log.info("[findMatchingPermission] checking permission: id={}, roleCode={}, scopeId={}, roleMatched={}, scopeMatched={}",
                    permission.getId(), permission.getRoleCode(), permission.getScopeId(), roleMatched, scopeMatched);
            if (roleMatched && scopeMatched) {
                log.info("[findMatchingPermission] MATCHED: permission id={}", permission.getId());
                return permission;
            }
        }
        log.warn("[findMatchingPermission] No matching permission found");
        return null;
    }

    private JwtAuthenticationDetails getJwtDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtAuthenticationDetails jwtDetails) {
            return jwtDetails;
        }
        return null;
    }

    private void writeForbidden(HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\",\"data\":null}");
            response.getWriter().flush();
        } catch (Exception ignored) {
        }
    }

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
            log.warn("Failed to get unit_id for org {}: {}", orgId, e.getMessage());
        }
        return null;
    }

    private List<Long> getVisibleOrgIdsForUnit(Long scopeUnitId) {
        List<Long> orgIds = new ArrayList<>();
        if (scopeUnitId == null || scopeUnitId == 0L) {
            return orgIds;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM sys_organization WHERE unit_id = ? AND deleted = 0")) {
            ps.setLong(1, scopeUnitId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orgIds.add(rs.getLong("id"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve visible organizations for unit {}: {}", scopeUnitId, e.getMessage());
        }
        return orgIds;
    }
}
