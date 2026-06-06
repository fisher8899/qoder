package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.IndicatorSubCategoryDTO;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizIndicatorSubCategory;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizIndicatorSubCategoryMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.service.BizIndicatorSubCategoryService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BizIndicatorSubCategoryServiceImpl extends ServiceImpl<BizIndicatorSubCategoryMapper, BizIndicatorSubCategory> implements BizIndicatorSubCategoryService {

    private final SysOrganizationMapper organizationMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;

    public BizIndicatorSubCategoryServiceImpl(SysOrganizationMapper organizationMapper,
                                              BizIndicatorDefinitionMapper indicatorDefinitionMapper) {
        this.organizationMapper = organizationMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
    }

    @Override
    public List<BizIndicatorSubCategory> listByOwner(Long examGroupId, Long orgId, Long categoryId, String categoryName) {
        LambdaQueryWrapper<BizIndicatorSubCategory> wrapper = new LambdaQueryWrapper<>();
        if (examGroupId != null) {
            wrapper.eq(BizIndicatorSubCategory::getExamGroupId, examGroupId);
        }
        if (orgId != null) {
            wrapper.eq(BizIndicatorSubCategory::getOrgId, orgId);
        }
        if (categoryId != null) {
            wrapper.eq(BizIndicatorSubCategory::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(categoryName)) {
            wrapper.eq(BizIndicatorSubCategory::getCategoryName, categoryName);
        }
        DataScopeFilter.applyFilter(wrapper, BizIndicatorSubCategory::getUnitId, BizIndicatorSubCategory::getOrgId);
        wrapper.orderByAsc(BizIndicatorSubCategory::getSortCode).orderByAsc(BizIndicatorSubCategory::getId);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSubCategory(IndicatorSubCategoryDTO dto) {
        BizIndicatorSubCategory entity = new BizIndicatorSubCategory();
        BeanUtils.copyProperties(dto, entity);
        normalize(entity);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSubCategory(IndicatorSubCategoryDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("小类ID不能为空");
        }
        BizIndicatorSubCategory entity = getById(dto.getId());
        if (entity == null) {
            throw new BusinessException("小类不存在");
        }
        String oldName = entity.getSubCategoryName();
        BeanUtils.copyProperties(dto, entity);
        normalize(entity);
        entity.setUpdatedTime(LocalDateTime.now());
        updateById(entity);
        syncIndicatorDefinitions(entity, oldName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSubCategory(Long id) {
        long indicatorCount = indicatorDefinitionMapper.selectCount(new LambdaQueryWrapper<BizIndicatorDefinition>()
                .eq(BizIndicatorDefinition::getSubCategoryId, id));
        if (indicatorCount > 0) {
            throw new BusinessException("该小类下还有考核内容，不能删除");
        }
        if (!removeById(id)) {
            throw new BusinessException("小类不存在或已删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizIndicatorSubCategory ensureSubCategory(IndicatorSubCategoryDTO dto) {
        if (dto.getId() != null) {
            BizIndicatorSubCategory existing = getById(dto.getId());
            if (existing != null) {
                return existing;
            }
        }
        BizIndicatorSubCategory entity = new BizIndicatorSubCategory();
        BeanUtils.copyProperties(dto, entity);
        normalize(entity);

        LambdaQueryWrapper<BizIndicatorSubCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizIndicatorSubCategory::getExamGroupId, entity.getExamGroupId())
                .eq(BizIndicatorSubCategory::getOrgId, entity.getOrgId())
                .eq(entity.getCategoryId() != null, BizIndicatorSubCategory::getCategoryId, entity.getCategoryId())
                .eq(BizIndicatorSubCategory::getCategoryName, entity.getCategoryName())
                .eq(BizIndicatorSubCategory::getSubCategoryName, entity.getSubCategoryName());
        BizIndicatorSubCategory existing = getOne(wrapper, false);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        save(entity);
        return entity;
    }

    private void normalize(BizIndicatorSubCategory entity) {
        if (entity.getExamGroupId() == null) {
            throw new BusinessException("考核组不能为空");
        }
        if (entity.getOrgId() == null) {
            throw new BusinessException("归属部门不能为空");
        }
        if (!StringUtils.hasText(entity.getCategoryName())) {
            throw new BusinessException("指标大类不能为空");
        }
        if (!StringUtils.hasText(entity.getSubCategoryName())) {
            throw new BusinessException("小类名称不能为空");
        }

        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        if ("ORG".equals(dataScope) && scopeId != null && scopeId != 0L && !scopeId.equals(entity.getOrgId())) {
            throw new BusinessException("小类归属部门必须与当前数据范围一致");
        }

        SysOrganization org = organizationMapper.selectById(entity.getOrgId());
        if (org == null || (org.getDeleted() != null && org.getDeleted() != 0)) {
            throw new BusinessException("归属部门不存在");
        }
        if (org.getUnitId() == null) {
            throw new BusinessException("归属部门未配置所属单位");
        }
        if ("UNIT".equals(dataScope) && scopeId != null && scopeId != 0L && !scopeId.equals(org.getUnitId())) {
            throw new BusinessException("归属部门不在当前单位数据范围内");
        }

        entity.setOrgId(org.getId());
        entity.setOrgName(org.getOrgName());
        entity.setUnitId(org.getUnitId());
        entity.setCategoryName(entity.getCategoryName().trim());
        entity.setSubCategoryName(entity.getSubCategoryName().trim());
        if (entity.getEvaluationStandard() != null) {
            entity.setEvaluationStandard(entity.getEvaluationStandard().trim());
        }
        if (entity.getSortCode() == null) {
            entity.setSortCode(0);
        }
    }

    private void syncIndicatorDefinitions(BizIndicatorSubCategory subCategory, String oldName) {
        LambdaUpdateWrapper<BizIndicatorDefinition> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BizIndicatorDefinition::getSubCategoryId, subCategory.getId())
                .or(wrapper -> wrapper
                        .eq(BizIndicatorDefinition::getExamGroupId, subCategory.getExamGroupId())
                        .eq(BizIndicatorDefinition::getOrgId, subCategory.getOrgId())
                        .eq(BizIndicatorDefinition::getCategoryName, subCategory.getCategoryName())
                        .eq(BizIndicatorDefinition::getSubCategory, oldName));
        BizIndicatorDefinition updateEntity = new BizIndicatorDefinition();
        updateEntity.setSubCategoryId(subCategory.getId());
        updateEntity.setSubCategory(subCategory.getSubCategoryName());
        if (StringUtils.hasText(subCategory.getEvaluationStandard())) {
            updateEntity.setEvaluationStandard(subCategory.getEvaluationStandard());
        }
        indicatorDefinitionMapper.update(updateEntity, updateWrapper);
    }
}
