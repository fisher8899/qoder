package com.ccerphr.assessment.security;

import com.ccerphr.assessment.common.BusinessException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class RoleCheckAspect {

    @Before("@annotation(requireRole)")
    public void checkRole(RequireRole requireRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(403, "未登录或登录已过期");
        }

        String currentRole = authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("");

        boolean hasRole = Arrays.stream(requireRole.value()).anyMatch(role -> role.equals(currentRole));
        if (!hasRole) {
            throw new BusinessException(403, "无权限访问该资源");
        }
    }
}
