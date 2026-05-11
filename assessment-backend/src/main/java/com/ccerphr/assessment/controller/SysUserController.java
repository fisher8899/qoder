package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.UserQueryDTO;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserRole;
import com.ccerphr.assessment.mapper.SysUserRoleMapper;
import com.ccerphr.assessment.service.SysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public Result<PageResult<SysUser>> list(UserQueryDTO query) {
        return Result.success(sysUserService.listByPage(query));
    }

    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable Long id) {
        return Result.success(sysUserService.getDetail(id));
    }

    @PostMapping
    public Result<Long> add(@RequestBody @Validated SysUser user) {
        sysUserService.add(user);
        return Result.success(user.getId());
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Validated SysUser user) {
        sysUserService.update(user);
        return Result.success();
    }

    @PutMapping("/{id}/toggle-enabled")
    public Result<Void> toggleEnabled(@PathVariable Long id) {
        sysUserService.toggleEnabled(id);
        return Result.success();
    }

    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        sysUserService.resetPassword(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return Result.success();
    }

    /**
     * 获取用户角色列表
     * GET /api/sys-user/{userId}/roles
     */
    @GetMapping("/{userId}/roles")
    public Result<List<String>> getRoles(@PathVariable Long userId) {
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

    /**
     * 保存用户的多角色配置
     * POST /api/sys-user/{userId}/roles
     * Body: ["DEPT_ADMIN", "FIN_ADMIN", ...]
     */
    @PostMapping("/{userId}/roles")
    public Result<Void> saveRoles(@PathVariable Long userId, @RequestBody List<String> roleCodes) {
        // 先删除该用户的旧角色记录
        sysUserRoleMapper.delete(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        );

        if (roleCodes != null && !roleCodes.isEmpty()) {
            // 角色编码到名称映射
            Map<String, String> roleNameMap = Map.of(
                "ADMIN", "系统管理员",
                "FIN_ADMIN", "计划财务处业绩考核管理员",
                "DEPT_ADMIN", "部门绩效管理员",
                "DEPT_LEADER", "部门负责人",
                "SUPERVISOR", "分管领导"
            );

            for (String code : roleCodes) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleCode(code);
                userRole.setRoleName(roleNameMap.getOrDefault(code, code));
                userRole.setCreatedTime(LocalDateTime.now());
                sysUserRoleMapper.insert(userRole);
            }

            // 第一个角色同步到sys_user表保持兼容
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
}
