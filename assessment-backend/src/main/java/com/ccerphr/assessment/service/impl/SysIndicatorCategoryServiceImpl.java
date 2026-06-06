package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.IndicatorCategoryQueryDTO;
import com.ccerphr.assessment.entity.SysIndicatorCategory;
import com.ccerphr.assessment.mapper.SysIndicatorCategoryMapper;
import com.ccerphr.assessment.service.SysIndicatorCategoryService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SysIndicatorCategoryServiceImpl extends ServiceImpl<SysIndicatorCategoryMapper, SysIndicatorCategory> implements SysIndicatorCategoryService {

    @Override
    public PageResult<SysIndicatorCategory> queryPage(IndicatorCategoryQueryDTO query) {
        LambdaQueryWrapper<SysIndicatorCategory> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getCategoryName())) {
            wrapper.like(SysIndicatorCategory::getCategoryName, query.getCategoryName());
        }
        if (StringUtils.hasText(query.getCategoryCode())) {
            wrapper.eq(SysIndicatorCategory::getCategoryCode, query.getCategoryCode());
        }
        if (query.getIsEnabled() != null) {
            wrapper.eq(SysIndicatorCategory::getIsEnabled, query.getIsEnabled());
        }
        if (StringUtils.hasText(query.getApplicableScope())) {
            wrapper.and(w -> w.eq(SysIndicatorCategory::getApplicableScope, query.getApplicableScope())
                    .or()
                    .eq(SysIndicatorCategory::getApplicableScope, "通用"));
        }
        // 数据范围过滤 - 按 unit_id 过滤
        DataScopeFilter.applyUnitFilter(wrapper, SysIndicatorCategory::getUnitId);
        wrapper.orderByAsc(SysIndicatorCategory::getSortCode);
        Page<SysIndicatorCategory> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        PageResult<SysIndicatorCategory> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public List<SysIndicatorCategory> getAll(String applicableScope, Long unitId) {
        LambdaQueryWrapper<SysIndicatorCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysIndicatorCategory::getIsEnabled, 1);
        if (StringUtils.hasText(applicableScope)) {
            // 将 org_type 枚举值映射为 applicable_scope 中文值
            String mappedScope = mapOrgTypeToScope(applicableScope);
            wrapper.and(w -> w.eq(SysIndicatorCategory::getApplicableScope, mappedScope)
                    .or()
                    .eq(SysIndicatorCategory::getApplicableScope, "通用"));
        }
        // 指标大类为全局配置数据，不按 unit_id 过滤，所有组织均可见
        if (unitId != null) {
            wrapper.eq(SysIndicatorCategory::getUnitId, unitId);
        } else {
            DataScopeFilter.applyUnitFilter(wrapper, SysIndicatorCategory::getUnitId);
        }
        wrapper.orderByAsc(SysIndicatorCategory::getSortCode);
        return this.list(wrapper);
    }

    /**
     * 将组织类型枚举值映射为指标大类的适用范围中文值
     */
    private String mapOrgTypeToScope(String orgType) {
        if (orgType == null) return orgType;
        switch (orgType) {
            case "FUNCTIONAL":
                return "职能部门";
            case "BRANCH":
                return "分公司";
            default:
                return orgType;
        }
    }

    @Override
    public SysIndicatorCategory getDetail(Long id) {
        SysIndicatorCategory category = this.getById(id);
        if (category == null) {
            throw new BusinessException("指标大类不存在");
        }
        return category;
    }

    @Override
    public void addCategory(SysIndicatorCategory category) {
        // 自动填入 unitId
        Long unitId = DataScopeFilter.getAutoFillUnitId();
        if (unitId != null && category.getUnitId() == null) {
            category.setUnitId(unitId);
        }
        LocalDateTime now = LocalDateTime.now();
        category.setCreatedTime(now);
        category.setUpdatedTime(now);
        this.save(category);
    }

    @Override
    public void updateCategory(SysIndicatorCategory category) {
        if (category.getId() == null) {
            throw new BusinessException("指标大类ID不能为空");
        }
        category.setUpdatedTime(LocalDateTime.now());
        this.updateById(category);
    }

    @Override
    public void deleteCategory(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("指标大类不存在或已删除");
        }
    }
}
