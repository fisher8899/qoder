package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.MenuTreeDTO;
import com.ccerphr.assessment.entity.SysMenu;
import com.ccerphr.assessment.service.SysMenuService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    @GetMapping("/by-role/{roleCode}")
    public Result<List<Map<String, Object>>> getMenusByRole(@PathVariable String roleCode) {
        return Result.success(sysMenuService.getMenuTreeByRole(roleCode));
    }

    @GetMapping("/tree")
    public Result<List<MenuTreeDTO>> tree() {
        return Result.success(sysMenuService.getMenuTree());
    }

    @GetMapping("/{id}")
    public Result<SysMenu> detail(@PathVariable Long id) {
        return Result.success(sysMenuService.getDetail(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody @Validated SysMenu menu) {
        sysMenuService.addMenu(menu);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Validated SysMenu menu) {
        sysMenuService.updateMenu(menu);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.deleteMenu(id);
        return Result.success();
    }
}
