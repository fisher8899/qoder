package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.IndicatorCategoryQueryDTO;
import com.ccerphr.assessment.entity.SysIndicatorCategory;

import java.util.List;

public interface SysIndicatorCategoryService extends IService<SysIndicatorCategory> {

    PageResult<SysIndicatorCategory> queryPage(IndicatorCategoryQueryDTO query);

    List<SysIndicatorCategory> getAll(String applicableScope, Long unitId);

    SysIndicatorCategory getDetail(Long id);

    void addCategory(SysIndicatorCategory category);

    void updateCategory(SysIndicatorCategory category);

    void deleteCategory(Long id);
}
