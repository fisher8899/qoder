package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.PermissionQueryDTO;
import com.ccerphr.assessment.entity.SysRole;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysRoleMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.security.UnitScopeAccess;
import com.ccerphr.assessment.service.SysUserPermissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permission")
public class SysUserPermissionController {

    private final SysUserPermissionService sysUserPermissionService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public SysUserPermissionController(SysUserPermissionService sysUserPermissionService,
                                       SysUserMapper sysUserMapper,
                                       SysRoleMapper sysRoleMapper) {
        this.sysUserPermissionService = sysUserPermissionService;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    @GetMapping("/list")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<PageResult<SysUserPermission>> list(PermissionQueryDTO query) {
        UnitScopeAccess.requireAdminOrUnitScope();
        return Result.success(sysUserPermissionService.queryPage(query));
    }

    @GetMapping("/{userId}")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<SysUserPermission> getByUserId(@PathVariable Long userId) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserInScope(userId);
        return Result.success(sysUserPermissionService.getByUserId(userId));
    }

    @GetMapping("/user/{userId}")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<List<SysUserPermission>> getUserPermissions(@PathVariable Long userId) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserInScope(userId);
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getUserId, userId)
                .eq(SysUserPermission::getDeleted, 0)
                .orderByDesc(SysUserPermission::getCreatedTime);
        return Result.success(sysUserPermissionService.list(wrapper));
    }

    @PostMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> add(@RequestBody SysUserPermission permission) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserInScope(permission.getUserId());
        if (permission.getUnitScope() == null) {
            permission.setUnitScope("");
        }
        if (permission.getExamType() == null || permission.getExamType().isEmpty()) {
            permission.setExamType("MONTHLY");
        }

        normalizeDataScopeByRoleType(permission);
        sysUserPermissionService.addPermission(permission);
        return Result.success();
    }

    @PutMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> update(@RequestBody @Validated SysUserPermission permission) {
        UnitScopeAccess.requireAdminOrUnitScope();
        assertUserInScope(permission.getUserId());
        normalizeDataScopeByRoleType(permission);
        sysUserPermissionService.updatePermission(permission);
        return Result.success();
    }

    @GetMapping("/users")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<List<Map<String, Object>>> getUserList() {
        UnitScopeAccess.requireAdminOrUnitScope();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getIsEnabled, 1);
        wrapper.eq(SysUser::getDeleted, 0);
        applyUserScopeFilter(wrapper);
        wrapper.select(SysUser::getId, SysUser::getUsername, SysUser::getRealName);

        List<SysUser> users = sysUserMapper.selectList(wrapper);
        List<Map<String, Object>> result = users.stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("realName", user.getRealName());
            return map;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> delete(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        SysUserPermission permission = sysUserPermissionService.getById(id);
        if (permission == null) {
            throw new BusinessException(404, "权限记录不存在");
        }
        assertUserInScope(permission.getUserId());
        sysUserPermissionService.deletePermission(id);
        return Result.success();
    }

    private void normalizeDataScopeByRoleType(SysUserPermission permission) {
        if (permission.getRoleCode() == null || permission.getRoleCode().isBlank()) {
            throw new BusinessException("请选择职责");
        }

        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysRole::getRoleCode, permission.getRoleCode());
        SysRole role = sysRoleMapper.selectOne(roleWrapper);
        if (role == null) {
            throw new BusinessException("职责不存在");
        }

        String expectedDataScope;
        if ("SYSTEM".equals(role.getRoleType())) {
            expectedDataScope = "ALL";
            permission.setScopeId(0L);
            permission.setScopeName("全部");
        } else if ("UNIT".equals(role.getRoleType())) {
            expectedDataScope = "UNIT";
        } else if ("DEPT".equals(role.getRoleType())) {
            expectedDataScope = "ORG";
        } else {
            throw new BusinessException("职责类型无效");
        }

        if (permission.getDataScope() != null
                && !permission.getDataScope().isBlank()
                && !expectedDataScope.equals(permission.getDataScope())) {
            throw new BusinessException("职责类型为 " + role.getRoleType() + " 时，数据范围必须为 " + expectedDataScope);
        }
        permission.setDataScope(expectedDataScope);

        if ("UNIT".equals(expectedDataScope) || "ORG".equals(expectedDataScope)) {
            if (permission.getScopeId() == null || permission.getScopeId() <= 0) {
                throw new BusinessException("数据范围为 " + expectedDataScope + " 时必须指定 scopeId");
            }
            if (permission.getScopeName() == null || permission.getScopeName().isEmpty()) {
                throw new BusinessException("数据范围为 " + expectedDataScope + " 时必须指定 scopeName");
            }
        }
    }

    private void assertUserInScope(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!isUserVisible(user)) {
            throw new BusinessException(403, "无权访问该用户");
        }
    }

    private void applyUserScopeFilter(LambdaQueryWrapper<SysUser> wrapper) {
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        if ("ALL".equals(dataScope) || scopeId == null || scopeId == 0L) {
            return;
        }
        if ("UNIT".equals(dataScope)) {
            wrapper.eq(SysUser::getUnitId, scopeId);
        } else if ("ORG".equals(dataScope)) {
            wrapper.eq(SysUser::getOrgId, scopeId);
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
