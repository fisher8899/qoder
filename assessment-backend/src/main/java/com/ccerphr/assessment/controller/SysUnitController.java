package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.UnitQueryDTO;
import com.ccerphr.assessment.entity.SysUnit;
import com.ccerphr.assessment.service.SysUnitService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/unit")
public class SysUnitController {

    private final SysUnitService sysUnitService;

    public SysUnitController(SysUnitService sysUnitService) {
        this.sysUnitService = sysUnitService;
    }

    @GetMapping("/list")
    public Result<PageResult<SysUnit>> list(UnitQueryDTO query) {
        return Result.success(sysUnitService.queryPage(query));
    }

    @GetMapping("/{id}")
    public Result<SysUnit> detail(@PathVariable Long id) {
        return Result.success(sysUnitService.getDetail(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody @Validated SysUnit unit) {
        sysUnitService.addUnit(unit);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Validated SysUnit unit) {
        sysUnitService.updateUnit(unit);
        return Result.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        sysUnitService.toggleStatus(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUnitService.deleteUnit(id);
        return Result.success();
    }
}
