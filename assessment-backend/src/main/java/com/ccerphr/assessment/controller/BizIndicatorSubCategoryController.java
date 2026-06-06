package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.IndicatorSubCategoryDTO;
import com.ccerphr.assessment.entity.BizIndicatorSubCategory;
import com.ccerphr.assessment.service.BizIndicatorSubCategoryService;
import com.ccerphr.assessment.util.DataScopeFilter;
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

@RestController
@RequestMapping("/api/indicator/sub-category")
public class BizIndicatorSubCategoryController {

    private final BizIndicatorSubCategoryService subCategoryService;

    public BizIndicatorSubCategoryController(BizIndicatorSubCategoryService subCategoryService) {
        this.subCategoryService = subCategoryService;
    }

    @GetMapping("/list")
    public Result<List<BizIndicatorSubCategory>> list(@RequestParam Long examGroupId,
                                                       @RequestParam Long orgId,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(required = false) String categoryName) {
        return Result.success(subCategoryService.listByOwner(examGroupId, orgId, categoryId, categoryName));
    }

    @PostMapping
    public Result<Long> create(@RequestBody IndicatorSubCategoryDTO dto) {
        ensureIndicatorEditAccess();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        return Result.success(subCategoryService.createSubCategory(dto));
    }

    @PutMapping
    public Result<Void> update(@RequestBody IndicatorSubCategoryDTO dto) {
        ensureIndicatorEditAccess();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        subCategoryService.updateSubCategory(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ensureIndicatorEditAccess();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        subCategoryService.deleteSubCategory(id);
        return Result.success();
    }

    private void ensureIndicatorEditAccess() {
        String roleCode = DataScopeContext.getRoleCode();
        String dataScope = DataScopeContext.getDataScope();
        if ("ADMIN".equals(roleCode) || "FIN_ADMIN".equals(roleCode) || "DEPT_ADMIN".equals(roleCode)) {
            return;
        }
        if ("UNIT".equals(dataScope) || "ORG".equals(dataScope)) {
            return;
        }
        throw new BusinessException(403, "当前职责无权维护指标分类");
    }
}
