package com.ccerphr.assessment.security;

import com.ccerphr.assessment.security.JwtAuthenticationFilter.JwtAuthenticationDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 安全工具类 - 从 Spring Security 上下文获取当前用户信息
 */
public class SecurityUtil {

    private SecurityUtil() {
        // 工具类不允许实例化
    }

    /**
     * 获取当前认证用户的ID
     * @return 用户ID，未认证返回null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            try {
                return Long.valueOf(username);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取当前认证用户的用户名（真实姓名，从JWT claims）
     * @return 用户名，未认证返回null
     */
    public static String getCurrentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        // 从自定义的 Details 中获取 userName
        Object details = authentication.getDetails();
        if (details instanceof JwtAuthenticationDetails) {
            return ((JwtAuthenticationDetails) details).getUserName();
        }
        // 兼容旧方案：从 principal 获取
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return authentication.getName();
    }

    /**
     * 获取当前认证用户的第一个角色编码
     * @return 角色编码，未认证或无角色返回null
     */
    public static String getCurrentRoleCode() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断当前用户是否已认证
     * @return true=已认证，false=未认证
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * 获取当前会话激活的职责编码（来自 JWT activeRoleCode 声明）
     */
    public static String getActiveRoleCode() {
        JwtAuthenticationDetails details = getJwtDetails();
        return details == null ? null : details.getActiveRoleCode();
    }

    /**
     * 获取当前会话激活的数据范围ID（来自 JWT activeScopeId 声明）
     */
    public static Long getActiveScopeId() {
        JwtAuthenticationDetails details = getJwtDetails();
        return details == null ? null : details.getActiveScopeId();
    }

    /**
     * 获取当前会话激活的数据范围类型（来自 JWT activeDataScope 声明）
     */
    public static String getActiveDataScope() {
        JwtAuthenticationDetails details = getJwtDetails();
        return details == null ? null : details.getActiveDataScope();
    }

    private static JwtAuthenticationDetails getJwtDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object details = authentication.getDetails();
        return details instanceof JwtAuthenticationDetails ? (JwtAuthenticationDetails) details : null;
    }
}