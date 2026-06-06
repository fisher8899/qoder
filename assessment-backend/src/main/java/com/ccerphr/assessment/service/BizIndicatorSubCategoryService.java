package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.dto.IndicatorSubCategoryDTO;
import com.ccerphr.assessment.entity.BizIndicatorSubCategory;

import java.util.List;

public interface BizIndicatorSubCategoryService extends IService<BizIndicatorSubCategory> {
    List<BizIndicatorSubCategory> listByOwner(Long examGroupId, Long orgId, Long categoryId, String categoryName);

    Long createSubCategory(IndicatorSubCategoryDTO dto);

    void updateSubCategory(IndicatorSubCategoryDTO dto);

    void deleteSubCategory(Long id);

    BizIndicatorSubCategory ensureSubCategory(IndicatorSubCategoryDTO dto);
}
