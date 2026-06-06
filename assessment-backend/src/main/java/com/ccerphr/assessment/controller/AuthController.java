package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.entity.SysRole;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.mapper.SysRoleMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import com.ccerphr.assessment.security.JwtTokenProvider;
import com.ccerphr.assessment.security.LoginDTO;
import com.ccerphr.assessment.security.LoginRateLimiter;
import com.ccerphr.assessment.security.SecurityUtil;
import com.ccerphr.assessment.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String INVALID_CREDENTIALS = "用户名或密码错误";

    private final SysUserMapper userMapper;
    private final SysEmployeeMapper employeeMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysUserPermissionMapper sysUserPermissionMapper;
    private final SysRoleMapper sysRoleMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginRateLimiter loginRateLimiter;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(SysUserMapper userMapper,
                          SysEmployeeMapper employeeMapper,
                          SysOrganizationMapper organizationMapper,
                          SysUserPermissionMapper sysUserPermissionMapper,
                          SysRoleMapper sysRoleMapper,
                          JwtTokenProvider jwtTokenProvider,
                          LoginRateLimiter loginRateLimiter,
                          TokenBlacklistService tokenBlacklistService,
                          PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.employeeMapper = employeeMapper;
        this.organizationMapper = organizationMapper;
        this.sysUserPermissionMapper = sysUserPermissionMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginRateLimiter = loginRateLimiter;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginRequest,
                                             HttpServletRequest request) {
        String normalizedUsername = loginRequest.getUsername().trim();
        String normalizedPassword = loginRequest.getPassword().trim();

        String clientIp = resolveClientIp(request);
        loginRateLimiter.check("ip:" + clientIp);
        loginRateLimiter.check("user:" + normalizedUsername);

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, normalizedUsername)
                        .eq(SysUser::getDeleted, 0)
        );

        if (user == null) {
            log.warn("Login failed: username not found, username={}", normalizedUsername);
            throw new BusinessException(INVALID_CREDENTIALS);
        }
        if (user.getIsEnabled() != null && user.getIsEnabled() == 0) {
            throw new BusinessException("该账号已被禁用");
        }
        if (!passwordEncoder.matches(normalizedPassword, user.getPassword())) {
            log.warn("Login failed: password mismatch, userId={}, username={}", user.getId(), normalizedUsername);
            throw new BusinessException(INVALID_CREDENTIALS);
        }

        SysEmployee employee = resolveEmployeeAndBackfillUserOrg(user);
        List<Map<String, Object>> availableRoles = getAvailableRoles(user);
        if (availableRoles.isEmpty()) {
            throw new BusinessException("当前用户未配置有效职责和数据范围，请先在用户权限分配中维护");
        }

        user.setLastLoginTime(java.time.LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> activeRole = availableRoles.get(0);
        Map<String, Object> userInfo = buildUserInfo(user, employee, activeRole, availableRoles);

        Map<String, Object> result = new HashMap<>();
        result.put("token", issueToken(user, activeRole));
        result.put("userInfo", userInfo);

        loginRateLimiter.reset("ip:" + clientIp);
        loginRateLimiter.reset("user:" + normalizedUsername);
        return Result.success(result);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/switch-role")
    public Result<Map<String, Object>> switchRole(@RequestBody Map<String, Object> request) {
        String roleCode = request.get("roleCode") == null ? "" : request.get("roleCode").toString();
        Long scopeId = toLong(request.get("scopeId"));

        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        SysUserPermission permission = findActivePermission(userId, roleCode, scopeId);
        if (permission == null) {
            throw new BusinessException("无权切换到该职责或数据范围，请检查用户权限分配");
        }

        Map<String, Object> role = toRoleMap(permission);
        role.put("token", issueToken(user, role));
        log.info("User {} switched to role={}, scopeId={}", userId, roleCode, scopeId);
        return Result.success(role);
    }

    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录或token无效");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        List<Map<String, Object>> availableRoles = getAvailableRoles(user);
        if (availableRoles.isEmpty()) {
            throw new BusinessException("当前用户未配置有效职责和数据范围，请先在用户权限分配中维护");
        }

        Map<String, Object> activeRole = findRoleMap(availableRoles,
                SecurityUtil.getActiveRoleCode(), SecurityUtil.getActiveScopeId());
        SysEmployee employee = user.getEmployeeId() == null ? null : employeeMapper.selectById(user.getEmployeeId());
        return Result.success(buildUserInfo(user, employee, activeRole, availableRoles));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long ttl = jwtTokenProvider.getRemainingTtl(token);
            if (ttl > 0) {
                tokenBlacklistService.revoke(token, ttl);
            }
        }
        return Result.success();
    }

    private List<Map<String, Object>> getAvailableRoles(SysUser user) {
        return sysUserPermissionMapper.selectActiveByUserId(user.getId())
                .stream()
                .map(this::toRoleMap)
                .collect(Collectors.toList());
    }

private Map<String, Object> toRoleMap(SysUserPermission permission) {
        Map<String, Object> role = new HashMap<>();
        role.put("roleCode", permission.getRoleCode());
        role.put("roleName", getRoleName(permission.getRoleCode()));
        role.put("dataScope", permission.getDataScope());
        // scopeId 为 null 时使用 0，前端会用 orgId 作为回退
role.put("scopeId", permission.getScopeId() != null ? permission.getScopeId() : 0L);
        role.put("scopeName", permission.getScopeName() != null ? permission.getScopeName() : "全部");
        if (permission.getScopeId() != null && !"ALL".equals(permission.getDataScope())) {
            SysOrganization org = organizationMapper.selectById(permission.getScopeId());
            if (org != null) {
                role.put("orgType", org.getOrgType());
            }
        }
        return role;
    }

    private SysUserPermission findActivePermission(Long userId, String roleCode, Long scopeId) {
        return sysUserPermissionMapper.selectActiveByUserId(userId)
                .stream()
                .filter(permission -> Objects.equals(permission.getRoleCode(), roleCode))
                .filter(permission -> Objects.equals(permission.getScopeId() != null ? permission.getScopeId() : 0L, scopeId))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> findRoleMap(List<Map<String, Object>> roles, String roleCode, Long scopeId) {
        if (roleCode != null && scopeId != null) {
            return roles.stream()
                    .filter(role -> Objects.equals(role.get("roleCode"), roleCode))
                    .filter(role -> Objects.equals(toLong(role.get("scopeId")), scopeId))
                    .findFirst()
                    .orElse(roles.get(0));
        }
        return roles.get(0);
    }

    private String issueToken(SysUser user, Map<String, Object> activeRole) {
        return jwtTokenProvider.generateToken(
                user.getId(),
                user.getRealName(),
                user.getRoleCode(),
                user.getOrgId(),
                (String) activeRole.get("roleCode"),
                toLong(activeRole.get("scopeId")),
                (String) activeRole.get("dataScope")
        );
    }

    private Map<String, Object> buildUserInfo(SysUser user,
                                              SysEmployee employee,
                                              Map<String, Object> activeRole,
                                              List<Map<String, Object>> availableRoles) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("userName", user.getRealName());
        userInfo.put("roleCode", activeRole.get("roleCode"));
        userInfo.put("roleName", activeRole.get("roleName"));
        userInfo.put("orgId", user.getOrgId());
        userInfo.put("orgName", user.getOrgName());
        userInfo.put("unitId", user.getUnitId());
        if (activeRole.get("orgType") != null) {
            userInfo.put("orgType", activeRole.get("orgType"));
        } else if (user.getOrgId() != null) {
            SysOrganization org = organizationMapper.selectById(user.getOrgId());
            if (org != null) {
                userInfo.put("orgType", org.getOrgType());
            }
        }
        if (employee != null) {
            userInfo.put("employeeId", employee.getId());
            userInfo.put("employeeNo", employee.getEmployeeNo());
            userInfo.put("position", employee.getPosition());
            userInfo.put("level", employee.getLevel());
            userInfo.put("deptId", employee.getDeptId());
            userInfo.put("deptName", employee.getDeptName());
        }
        userInfo.put("availableRoles", availableRoles);
        return userInfo;
    }

    private SysEmployee resolveEmployeeAndBackfillUserOrg(SysUser user) {
        SysEmployee employee = null;
        if (user.getEmployeeId() != null) {
            employee = employeeMapper.selectById(user.getEmployeeId());
        }
        if (user.getOrgId() == null && employee != null && employee.getDeptId() != null) {
            user.setOrgId(employee.getDeptId());
            user.setOrgName(employee.getDeptName());
        }
        return employee;
    }

    private String getRoleName(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode)
        );
        return role != null ? role.getRoleName() : roleCode;
    }

    private Long toLong(Object value) {
        if (value == null || value.toString().isBlank()) {
            return 0L;
        }
        return Long.valueOf(value.toString());
    }
}
