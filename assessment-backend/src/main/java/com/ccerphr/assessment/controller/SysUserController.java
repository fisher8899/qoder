package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.UserQueryDTO;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserRole;
import com.ccerphr.assessment.mapper.SysUserRoleMapper;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.security.UnitScopeAccess;
import com.ccerphr.assessment.service.SysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sys-user")
public class SysUserController {

    private final SysUserService sysUserService;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserController(SysUserService sysUserService, SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserService = sysUserService;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @GetMapping("/list")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<PageResult<SysUser>> list(UserQueryDTO query) {
        UnitScopeAccess.requireAdminOrUnitScope();
        return Result.success(sysUserService.listByPage(query));
    }

    @GetMapping("/{id}")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<SysUser> detail(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserVisible(id);
        return Result.success(sysUserService.getDetail(id));
    }

    @PostMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Long> add(@RequestBody @Validated SysUser user) {
        UnitScopeAccess.requireAdminOrUnitScope();
        sysUserService.add(user);
        return Result.success(user.getId());
    }

    @PutMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> update(@RequestBody @Validated SysUser user) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserVisible(user.getId());
        sysUserService.update(user);
        return Result.success();
    }

    @PutMapping("/{id}/toggle-enabled")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> toggleEnabled(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserVisible(id);
        sysUserService.toggleEnabled(id);
        return Result.success();
    }

    @PutMapping("/{id}/reset-password")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<String> resetPassword(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserVisible(id);
        return Result.success(sysUserService.resetPassword(id));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> delete(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserVisible(id);
        sysUserService.delete(id);
        return Result.success();
    }

    @GetMapping("/{userId}/roles")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<List<String>> getRoles(@PathVariable Long userId) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserVisible(userId);
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .orderByAsc(SysUserRole::getId)
        );
        List<String> roleCodes = userRoles.stream()
                .map(SysUserRole::getRoleCode)
                .collect(Collectors.toList());
        return Result.success(roleCodes);
    }

    @PostMapping("/{userId}/roles")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> saveRoles(@PathVariable Long userId, @RequestBody List<String> roleCodes) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserVisible(userId);

        sysUserRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        );

        if (roleCodes != null && !roleCodes.isEmpty()) {
            Map<String, String> roleNameMap = Map.of(
                    "ADMIN", "ADMIN",
                    "FIN_ADMIN", "FIN_ADMIN",
                    "DEPT_ADMIN", "DEPT_ADMIN",
                    "DEPT_LEADER", "DEPT_LEADER",
                    "SUPERVISOR", "SUPERVISOR"
            );

            for (String code : roleCodes) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleCode(code);
                userRole.setRoleName(roleNameMap.getOrDefault(code, code));
                userRole.setCreatedTime(LocalDateTime.now());
                sysUserRoleMapper.insert(userRole);
            }

            SysUser user = sysUserService.getById(userId);
            if (user != null) {
                user.setRoleCode(roleCodes.get(0));
                user.setRoleName(roleNameMap.getOrDefault(roleCodes.get(0), roleCodes.get(0)));
                user.setUpdatedTime(LocalDateTime.now());
                sysUserService.update(user);
            }
        }

        return Result.success();
    }

    private void assertUserVisible(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!isUserVisible(user)) {
            throw new BusinessException(403, "无权访问该用户");
        }
    }

    private boolean isUserVisible(SysUser user) {
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        if ("ALL".equals(dataScope)) {
            return true;
        }
        if (scopeId == null || scopeId == 0L) {
            return false;
        }
        if ("UNIT".equals(dataScope)) {
            return scopeId.equals(user.getUnitId());
        }
        if ("ORG".equals(dataScope)) {
            return scopeId.equals(user.getOrgId());
        }
        return false;
    }
}
