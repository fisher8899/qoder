package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.MenuTreeDTO;
import com.ccerphr.assessment.entity.SysMenu;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.service.SysMenuService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    @GetMapping("/current")
    public Result<List<Map<String, Object>>> current() {
        return Result.success(sysMenuService.getMenuTreeByContext(
                DataScopeContext.getRoleCode(),
                DataScopeContext.getDataScope(),
                DataScopeContext.getScopeId()
        ));
    }

    @GetMapping("/by-role/{roleCode}")
    @RequireRole("ADMIN")
    public Result<List<Map<String, Object>>> getMenusByRole(@PathVariable String roleCode) {
        return Result.success(sysMenuService.getMenuTreeByRole(roleCode));
    }

    @GetMapping("/tree")
    @RequireRole("ADMIN")
    public Result<List<MenuTreeDTO>> tree(@RequestParam(required = false) String roleType) {
        if (roleType == null || roleType.isBlank()) {
            return Result.success(sysMenuService.getMenuTree());
        }
        return Result.success(sysMenuService.getAssignableMenuTree(roleType));
    }

    @GetMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<SysMenu> detail(@PathVariable Long id) {
        return Result.success(sysMenuService.getDetail(id));
    }

    @PostMapping
    @RequireRole("ADMIN")
    public Result<Void> add(@RequestBody @Validated SysMenu menu) {
        sysMenuService.addMenu(menu);
        return Result.success();
    }

    @PutMapping
    @RequireRole("ADMIN")
    public Result<Void> update(@RequestBody @Validated SysMenu menu) {
        sysMenuService.updateMenu(menu);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.deleteMenu(id);
        return Result.success();
    }
}
