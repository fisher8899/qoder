package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.PermissionQueryDTO;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.service.SysUserPermissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permission")
public class SysUserPermissionController {

    private final SysUserPermissionService sysUserPermissionService;
    private final SysUserMapper sysUserMapper;

    public SysUserPermissionController(SysUserPermissionService sysUserPermissionService, SysUserMapper sysUserMapper) {
        this.sysUserPermissionService = sysUserPermissionService;
        this.sysUserMapper = sysUserMapper;
    }

    @GetMapping("/list")
    public Result<PageResult<SysUserPermission>> list(PermissionQueryDTO query) {
        return Result.success(sysUserPermissionService.queryPage(query));
    }

    @GetMapping("/{userId}")
    public Result<SysUserPermission> getByUserId(@PathVariable Long userId) {
        return Result.success(sysUserPermissionService.getByUserId(userId));
    }

    @GetMapping("/user/{userId}")
    public Result<List<SysUserPermission>> getUserPermissions(@PathVariable Long userId) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getUserId, userId)
               .eq(SysUserPermission::getDeleted, 0)
               .orderByDesc(SysUserPermission::getCreatedTime);
        List<SysUserPermission> list = sysUserPermissionService.list(wrapper);
        return Result.success(list);
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysUserPermission permission) {
        // 设置默认值，避免前端未传时数据库约束报错
        if (permission.getUnitScope() == null) {
            permission.setUnitScope("");
        }
        if (permission.getExamType() == null || permission.getExamType().isEmpty()) {
            permission.setExamType("MONTHLY");
        }
        // 数据范围校验：ORG/UNIT 必须指定 scopeId 和 scopeName
        String dataScope = permission.getDataScope();
        if ("ORG".equals(dataScope) || "UNIT".equals(dataScope)) {
            if (permission.getScopeId() == null || permission.getScopeId() <= 0) {
                throw new com.ccerphr.assessment.common.BusinessException("数据范围为ORG/UNIT时必须指定scopeId");
            }
            if (permission.getScopeName() == null || permission.getScopeName().isEmpty()) {
                throw new com.ccerphr.assessment.common.BusinessException("数据范围为ORG/UNIT时必须指定scopeName");
            }
        }
        // ALL 时设默认值
        if ("ALL".equals(dataScope)) {
            if (permission.getScopeId() == null) {
                permission.setScopeId(0L);
            }
            if (permission.getScopeName() == null || permission.getScopeName().isEmpty()) {
                permission.setScopeName("全部");
            }
        }
        sysUserPermissionService.addPermission(permission);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Validated SysUserPermission permission) {
        sysUserPermissionService.updatePermission(permission);
        return Result.success();
    }

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> getUserList() {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getIsEnabled, 1);
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
    public Result<Void> delete(@PathVariable Long id) {
        sysUserPermissionService.deletePermission(id);
        return Result.success();
    }
}
