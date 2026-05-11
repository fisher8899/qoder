package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.IndicatorCategoryQueryDTO;
import com.ccerphr.assessment.entity.SysIndicatorCategory;
import com.ccerphr.assessment.service.SysIndicatorCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/indicator-category")
public class SysIndicatorCategoryController {

    private final SysIndicatorCategoryService sysIndicatorCategoryService;

    public SysIndicatorCategoryController(SysIndicatorCategoryService sysIndicatorCategoryService) {
        this.sysIndicatorCategoryService = sysIndicatorCategoryService;
    }

    @GetMapping("/list")
    public Result<PageResult<SysIndicatorCategory>> list(IndicatorCategoryQueryDTO query) {
        return Result.success(sysIndicatorCategoryService.queryPage(query));
    }

    @GetMapping("/all")
    public Result<List<SysIndicatorCategory>> all(@RequestParam(required = false) String applicableScope) {
        return Result.success(sysIndicatorCategoryService.getAll(applicableScope));
    }

    @GetMapping("/{id}")
    public Result<SysIndicatorCategory> detail(@PathVariable Long id) {
        return Result.success(sysIndicatorCategoryService.getDetail(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody @Validated SysIndicatorCategory category) {
        sysIndicatorCategoryService.addCategory(category);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Validated SysIndicatorCategory category) {
        sysIndicatorCategoryService.updateCategory(category);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysIndicatorCategoryService.deleteCategory(id);
        return Result.success();
    }
}
