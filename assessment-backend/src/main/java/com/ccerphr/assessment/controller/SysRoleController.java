package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.RoleMenuDTO;
import com.ccerphr.assessment.dto.RoleMenuItemDTO;
import com.ccerphr.assessment.entity.SysRole;
import com.ccerphr.assessment.entity.SysRoleChild;
import com.ccerphr.assessment.mapper.SysRoleChildMapper;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.service.SysRoleChildService;
import com.ccerphr.assessment.service.SysRoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/role")
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final SysRoleChildMapper roleChildMapper;
    private final SysRoleChildService roleChildService;

    public SysRoleController(SysRoleService sysRoleService,
                             SysRoleChildMapper roleChildMapper,
                             SysRoleChildService roleChildService) {
        this.sysRoleService = sysRoleService;
        this.roleChildMapper = roleChildMapper;
        this.roleChildService = roleChildService;
    }

    @GetMapping("/list")
    @RequireRole("ADMIN")
    public Result<PageResult<SysRole>> list(@RequestParam(required = false) String roleName,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.success(sysRoleService.queryPage(roleName, current, size));
    }

    @GetMapping("/all")
    @RequireRole("ADMIN")
    public Result<List<SysRole>> all() {
        return Result.success(sysRoleService.list());
    }

    @GetMapping("/list-by-type")
    @RequireRole("ADMIN")
    public Result<List<SysRole>> listByType(@RequestParam String type) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getRoleType, type);
        return Result.success(sysRoleService.list(wrapper));
    }

    @GetMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<RoleMenuDTO> detail(@PathVariable Long id) {
        return Result.success(sysRoleService.getDetail(id));
    }

    @PostMapping
    @RequireRole("ADMIN")
    public Result<Void> add(@RequestBody @Validated SysRole role) {
        sysRoleService.addRole(role);
        return Result.success();
    }

    @PutMapping
    @RequireRole("ADMIN")
    public Result<Void> update(@RequestBody @Validated SysRole role) {
        sysRoleService.updateRole(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        SysRole role = sysRoleService.getById(id);
        if (role != null && "ADMIN".equals(role.getRoleCode())) {
            return Result.error("系统管理员角色不可删除");
        }
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    @PostMapping("/{id}/menus")
    @RequireRole("ADMIN")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<RoleMenuItemDTO> roleMenuItems) {
        sysRoleService.assignMenus(id, roleMenuItems);
        return Result.success();
    }

    @GetMapping("/{id}/menus")
    @RequireRole("ADMIN")
    public Result<List<RoleMenuItemDTO>> getRoleMenus(@PathVariable Long id) {
        return Result.success(sysRoleService.getRoleMenuDetails(id));
    }

    @GetMapping("/{id}/children")
    @RequireRole("ADMIN")
    public Result<List<Map<String, Object>>> getChildren(@PathVariable Long id) {
        LambdaQueryWrapper<SysRoleChild> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleChild::getParentRoleId, id);
        List<SysRoleChild> children = roleChildMapper.selectList(wrapper);
        if (children.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> childRoleIds = children.stream()
                .map(SysRoleChild::getChildRoleId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, SysRole> childRoleMap = sysRoleService.listByIds(childRoleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>(children.size());
        for (SysRoleChild child : children) {
            SysRole childRole = childRoleMap.get(child.getChildRoleId());
            if (childRole == null) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", child.getId());
            map.put("childRoleId", child.getChildRoleId());
            map.put("roleName", childRole.getRoleName());
            map.put("roleCode", childRole.getRoleCode());
            result.add(map);
        }
        return Result.success(result);
    }

    @PostMapping("/{id}/children")
    @RequireRole("ADMIN")
    public Result<String> addChild(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long childRoleId = body.get("childRoleId");
        if (childRoleId == null) {
            return Result.error("请选择子角色");
        }

        LambdaQueryWrapper<SysRoleChild> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleChild::getParentRoleId, id);
        wrapper.eq(SysRoleChild::getChildRoleId, childRoleId);
        if (roleChildMapper.selectCount(wrapper) > 0) {
            return Result.error("该角色已添加");
        }

        SysRoleChild roleChild = new SysRoleChild();
        roleChild.setParentRoleId(id);
        roleChild.setChildRoleId(childRoleId);
        roleChild.setCreatedTime(LocalDateTime.now());
        roleChildMapper.insert(roleChild);
        return Result.success("添加成功");
    }

    @DeleteMapping("/{id}/children/{childId}")
    @RequireRole("ADMIN")
    public Result<Void> removeChild(@PathVariable Long id, @PathVariable Long childId) {
        roleChildMapper.deleteById(childId);
        return Result.success();
    }

    @GetMapping("/available")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<List<SysRole>> getAvailableRoles() {
        String roleCode = DataScopeContext.getRoleCode();
        if (roleCode == null || roleCode.isBlank()) {
            return Result.error(403, "缺少当前角色上下文");
        }

        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysRole::getRoleCode, roleCode);
        SysRole currentRole = sysRoleService.getOne(roleWrapper);
        if (currentRole == null) {
            return Result.error(403, "当前角色无效");
        }

        if ("SYSTEM".equals(currentRole.getRoleType())) {
            return Result.success(sysRoleService.list());
        }

        if ("UNIT".equals(currentRole.getRoleType())) {
            LambdaQueryWrapper<SysRoleChild> childWrapper = new LambdaQueryWrapper<>();
            childWrapper.eq(SysRoleChild::getParentRoleId, currentRole.getId());
            List<SysRoleChild> children = roleChildService.list(childWrapper);
            if (children.isEmpty()) {
                return Result.success(new ArrayList<>());
            }

            List<Long> childRoleIds = new ArrayList<>();
            for (SysRoleChild child : children) {
                childRoleIds.add(child.getChildRoleId());
            }
            return Result.success(sysRoleService.listByIds(childRoleIds));
        }

        return Result.success(new ArrayList<>());
    }
}
