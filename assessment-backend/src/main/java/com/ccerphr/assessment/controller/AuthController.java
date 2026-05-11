package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.entity.SysRole;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.entity.SysUserRole;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.mapper.SysRoleMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import com.ccerphr.assessment.mapper.SysUserRoleMapper;
import com.ccerphr.assessment.security.JwtTokenProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserMapper userMapper;
    private final SysEmployeeMapper employeeMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserPermissionMapper sysUserPermissionMapper;
    private final SysRoleMapper sysRoleMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(SysUserMapper userMapper, SysEmployeeMapper employeeMapper, SysOrganizationMapper organizationMapper, SysUserRoleMapper sysUserRoleMapper, SysUserPermissionMapper sysUserPermissionMapper, SysRoleMapper sysRoleMapper, JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.employeeMapper = employeeMapper;
        this.organizationMapper = organizationMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysUserPermissionMapper = sysUserPermissionMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 查询用户的所有角色列表，优先从sys_user_permission查询当前生效权限，
     * 无记录则回退到sys_user_role，最终回退到sys_user表的单角色
     */
    private List<Map<String, Object>> getAvailableRoles(SysUser user) {
        // 从 sys_user_permission 查询当前生效的权限
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getUserId, user.getId())
               .eq(SysUserPermission::getDeleted, 0)
               .le(SysUserPermission::getStartDate, today)
               .and(w -> w.isNull(SysUserPermission::getEndDate)
                          .or().ge(SysUserPermission::getEndDate, today));
    
        List<SysUserPermission> permissions = sysUserPermissionMapper.selectList(wrapper);
    
        if (permissions != null && !permissions.isEmpty()) {
            return permissions.stream().map(p -> {
                Map<String, Object> role = new HashMap<>();
                role.put("roleCode", p.getRoleCode());
                role.put("roleName", getRoleName(p.getRoleCode()));
                role.put("dataScope", p.getDataScope());
                role.put("scopeId", p.getScopeId() != null ? p.getScopeId() : 0L);
                role.put("scopeName", p.getScopeName() != null ? p.getScopeName() : "全部");
                // 如果 scopeId 不为空且 dataScope 不是 ALL，查组织获取 orgType
                if (p.getScopeId() != null && !"ALL".equals(p.getDataScope())) {
                    SysOrganization org = organizationMapper.selectById(p.getScopeId());
                    if (org != null) {
                        role.put("orgType", org.getOrgType());
                    }
                }
                return role;
            }).collect(Collectors.toList());
        }
    
        // 兼容：如果 sys_user_permission 没有记录，回退到 sys_user_role
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId())
        );
        if (userRoles != null && !userRoles.isEmpty()) {
            return userRoles.stream().map(r -> {
                Map<String, Object> role = new HashMap<>();
                role.put("roleCode", r.getRoleCode());
                role.put("roleName", r.getRoleName());
                role.put("dataScope", "ALL");
                role.put("scopeId", 0L);
                role.put("scopeName", "全部");
                return role;
            }).collect(Collectors.toList());
        }
    
        // 最终兖底：sys_user 表单角色
        List<Map<String, Object>> fallback = new ArrayList<>();
        if (user.getRoleCode() != null) {
            Map<String, Object> role = new HashMap<>();
            role.put("roleCode", user.getRoleCode());
            role.put("roleName", user.getRoleName());
            role.put("dataScope", "ALL");
            role.put("scopeId", 0L);
            role.put("scopeName", "全部");
            fallback.add(role);
        }
        return fallback;
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

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        // 查询用户
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .eq(SysUser::getDeleted, 0)
        );

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getIsEnabled() != null && user.getIsEnabled() == 0) {
            throw new BusinessException("该账户已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 生成JWT Token
        String token = jwtTokenProvider.generateToken(
            user.getId(),
            user.getRealName(),
            user.getRoleCode(),
            user.getOrgId()
        );

        // 如果用户关联了人员，读取人员信息
        SysEmployee employee = null;
        if (user.getEmployeeId() != null) {
            employee = employeeMapper.selectById(user.getEmployeeId());
        }

        // 如果user的orgId为空但employee的deptId不为空，自动填充
        if (user.getOrgId() == null && employee != null && employee.getDeptId() != null) {
            user.setOrgId(employee.getDeptId());
            user.setOrgName(employee.getDeptName());
            userMapper.updateById(user);
        }

        // 构建返回信息
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("userName", user.getRealName());
        // 优先从 availableRoles 中取第一个角色作为默认 roleCode
        List<Map<String, Object>> availableRolesList = getAvailableRoles(user);
        if (availableRolesList != null && !availableRolesList.isEmpty()) {
            userInfo.put("roleCode", availableRolesList.get(0).get("roleCode"));
            userInfo.put("roleName", availableRolesList.get(0).get("roleName"));
        } else {
            userInfo.put("roleCode", user.getRoleCode());
            userInfo.put("roleName", user.getRoleName());
        }
        userInfo.put("orgId", user.getOrgId());
        userInfo.put("orgName", user.getOrgName());
        userInfo.put("unitId", user.getUnitId());
        // 查询组织类型
        if (user.getOrgId() != null) {
            SysOrganization org = organizationMapper.selectById(user.getOrgId());
            if (org != null) {
                userInfo.put("orgType", org.getOrgType());
            }
        }
        // 新增人员关联信息
        if (employee != null) {
            userInfo.put("employeeId", employee.getId());
            userInfo.put("employeeNo", employee.getEmployeeNo());
            userInfo.put("position", employee.getPosition());
            userInfo.put("level", employee.getLevel());
            userInfo.put("deptId", employee.getDeptId());
            userInfo.put("deptName", employee.getDeptName());
        }
        // 查询用户的所有角色列表
        userInfo.put("availableRoles", availableRolesList);
        result.put("userInfo", userInfo);

        return Result.success(result);
    }

    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException("未登录或token无效");
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BusinessException("token已过期");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 如果用户关联了人员，读取人员信息
        SysEmployee employee = null;
        if (user.getEmployeeId() != null) {
            employee = employeeMapper.selectById(user.getEmployeeId());
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("userName", user.getRealName());
        userInfo.put("roleCode", user.getRoleCode());
        userInfo.put("roleName", user.getRoleName());
        userInfo.put("orgId", user.getOrgId());
        userInfo.put("orgName", user.getOrgName());
        userInfo.put("unitId", user.getUnitId());
        // 查询组织类型
        if (user.getOrgId() != null) {
            SysOrganization org = organizationMapper.selectById(user.getOrgId());
            if (org != null) {
                userInfo.put("orgType", org.getOrgType());
            }
        }
        // 新增人员关联信息
        if (employee != null) {
            userInfo.put("employeeId", employee.getId());
            userInfo.put("employeeNo", employee.getEmployeeNo());
            userInfo.put("position", employee.getPosition());
            userInfo.put("level", employee.getLevel());
            userInfo.put("deptId", employee.getDeptId());
            userInfo.put("deptName", employee.getDeptName());
        }
        // 查询用户的所有角色列表
        userInfo.put("availableRoles", getAvailableRoles(user));

        return Result.success(userInfo);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
