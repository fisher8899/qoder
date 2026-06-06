package com.ccerphr.assessment.security;

import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserPermissionMapper userPermissionMapper;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   SysUserPermissionMapper userPermissionMapper,
                                   TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userPermissionMapper = userPermissionMapper;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        if (StringUtils.hasText(token)
                && !tokenBlacklistService.isBlacklisted(token)
                && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            String userName = jwtTokenProvider.getUserNameFromToken(token);
            String activeRoleCode = jwtTokenProvider.getActiveRoleCodeFromToken(token);
            Long activeScopeId = jwtTokenProvider.getActiveScopeIdFromToken(token);
            String activeDataScope = jwtTokenProvider.getActiveDataScopeFromToken(token);

            List<SimpleGrantedAuthority> authorities = getUserAuthorities(userId);
            if (!authorities.isEmpty()) {
                UserDetails userDetails = new User(
                    String.valueOf(userId),
                    "[PROTECTED]",
                    authorities
                );

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new JwtAuthenticationDetails(userName, activeRoleCode, activeScopeId, activeDataScope, request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("User {} authenticated with roles: {}", userId,
                    authorities.stream().map(SimpleGrantedAuthority::getAuthority).collect(Collectors.joining(", ")));
            }
        }
        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> getUserAuthorities(Long userId) {
        try {
            List<SysUserPermission> permissions = userPermissionMapper.selectActiveByUserId(userId);
            if (permissions != null && !permissions.isEmpty()) {
                return permissions.stream()
                    .map(SysUserPermission::getRoleCode)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to query user permissions for {}: {}", userId, e.getMessage());
        }
        return List.of();
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public static class JwtAuthenticationDetails {
        private final String userName;
        private final String activeRoleCode;
        private final Long activeScopeId;
        private final String activeDataScope;
        private final Object originalDetails;

        public JwtAuthenticationDetails(String userName,
                                        String activeRoleCode,
                                        Long activeScopeId,
                                        String activeDataScope,
                                        HttpServletRequest request) {
            this.userName = userName;
            this.activeRoleCode = activeRoleCode;
            this.activeScopeId = activeScopeId;
            this.activeDataScope = activeDataScope;
            this.originalDetails = new WebAuthenticationDetailsSource().buildDetails(request);
        }

        public String getUserName() {
            return userName;
        }

        public String getActiveRoleCode() {
            return activeRoleCode;
        }

        public Long getActiveScopeId() {
            return activeScopeId;
        }

        public String getActiveDataScope() {
            return activeDataScope;
        }

        public Object getOriginalDetails() {
            return originalDetails;
        }
    }
}
