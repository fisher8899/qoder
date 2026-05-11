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
import com.ccerphr.assessment.service.SysRoleChildService;
import com.ccerphr.assessment.service.SysRoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/role")
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final SysRoleChildMapper roleChildMapper;
    private final SysRoleChildService roleChildService;

    public SysRoleController(SysRoleService sysRoleService, SysRoleChildMapper roleChildMapper, SysRoleChildService roleChildService) {
        this.sysRoleService = sysRoleService;
        this.roleChildMapper = roleChildMapper;
        this.roleChildService = roleChildService;
    }

    @GetMapping("/list")
    public Result<PageResult<SysRole>> list(@RequestParam(required = false) String roleName,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.success(sysRoleService.queryPage(roleName, current, size));
    }

    @GetMapping("/all")
    public Result<List<SysRole>> all() {
        List<SysRole> list = sysRoleService.list();
        return Result.success(list);
    }

    @GetMapping("/list-by-type")
    public Result<List<SysRole>> listByType(@RequestParam String type) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getRoleType, type);
        List<SysRole> roles = sysRoleService.list(wrapper);
        return Result.success(roles);
    }

    @GetMapping("/{id}")
    public Result<RoleMenuDTO> detail(@PathVariable Long id) {
        return Result.success(sysRoleService.getDetail(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody @Validated SysRole role) {
        sysRoleService.addRole(role);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Validated SysRole role) {
        sysRoleService.updateRole(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysRole role = sysRoleService.getById(id);
        if (role != null && "ADMIN".equals(role.getRoleCode())) {
            return Result.error("系统管理员职责不可删除");
        }
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    @PostMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<RoleMenuItemDTO> roleMenuItems) {
        sysRoleService.assignMenus(id, roleMenuItems);
        return Result.success();
    }

    @GetMapping("/{id}/menus")
    public Result<List<RoleMenuItemDTO>> getRoleMenus(@PathVariable Long id) {
        return Result.success(sysRoleService.getRoleMenuDetails(id));
    }

    @GetMapping("/{id}/children")
    public Result<List<Map<String, Object>>> getChildren(@PathVariable Long id) {
        LambdaQueryWrapper<SysRoleChild> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleChild::getParentRoleId, id);
        List<SysRoleChild> children = roleChildMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysRoleChild child : children) {
            SysRole childRole = sysRoleService.getById(child.getChildRoleId());
            if (childRole != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", child.getId());
                map.put("childRoleId", child.getChildRoleId());
                map.put("roleName", childRole.getRoleName());
                map.put("roleCode", childRole.getRoleCode());
                result.add(map);
            }
        }
        return Result.success(result);
    }

    @PostMapping("/{id}/children")
    public Result<String> addChild(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long childRoleId = body.get("childRoleId");
        if (childRoleId == null) {
            return Result.error("请选择部门职责");
        }
        // 检查是否已存在
        LambdaQueryWrapper<SysRoleChild> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleChild::getParentRoleId, id);
        wrapper.eq(SysRoleChild::getChildRoleId, childRoleId);
        if (roleChildMapper.selectCount(wrapper) > 0) {
            return Result.error("该职责已添加");
        }

        SysRoleChild roleChild = new SysRoleChild();
        roleChild.setParentRoleId(id);
        roleChild.setChildRoleId(childRoleId);
        roleChild.setCreatedTime(LocalDateTime.now());
        roleChildMapper.insert(roleChild);
        return Result.success("添加成功");
    }

    @DeleteMapping("/{id}/children/{childId}")
    public Result<Void> removeChild(@PathVariable Long id, @PathVariable Long childId) {
        roleChildMapper.deleteById(childId);
        return Result.success();
    }

    /**
     * 获取当前用户可分配的角色列表
     * - SYSTEM类型角色：返回全部角色
     * - UNIT类型角色：返回 sys_role_child 中配置的子角色
     * - DEPT类型角色：返回空列表
     */
    @GetMapping("/available")
    public Result<List<SysRole>> getAvailableRoles() {
        String roleCode = DataScopeContext.getRoleCode();
        if (roleCode == null) {
            // 无上下文，返回全部（兼容）
            return Result.success(sysRoleService.list());
        }

        // 查找当前角色的类型
        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysRole::getRoleCode, roleCode);
        SysRole currentRole = sysRoleService.getOne(roleWrapper);

        if (currentRole == null || "SYSTEM".equals(currentRole.getRoleType())) {
            // 系统管理员返回全部角色
            return Result.success(sysRoleService.list());
        }

        if ("UNIT".equals(currentRole.getRoleType())) {
            // 单位管理员：返回 sys_role_child 中配置的子角色
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

            List<SysRole> childRoles = sysRoleService.listByIds(childRoleIds);
            return Result.success(childRoles);
        }

        // DEPT 类型返回空
        return Result.success(new ArrayList<>());
    }
}
