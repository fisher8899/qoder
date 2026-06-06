package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.IndicatorApprovalDTO;
import com.ccerphr.assessment.dto.IndicatorProgressVO;
import com.ccerphr.assessment.dto.IndicatorQueryDTO;
import com.ccerphr.assessment.dto.IndicatorSetDTO;
import com.ccerphr.assessment.dto.IndicatorSubCategoryDTO;
import com.ccerphr.assessment.dto.IndicatorTreeDTO;
import com.ccerphr.assessment.dto.IndicatorVO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizIndicatorLeader;
import com.ccerphr.assessment.entity.BizIndicatorOrg;
import com.ccerphr.assessment.entity.BizIndicatorSubCategory;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.enums.ApprovalStatus;
import com.ccerphr.assessment.mapper.BizExamGroupMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizIndicatorLeaderMapper;
import com.ccerphr.assessment.mapper.BizIndicatorOrgMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.service.BizIndicatorDefinitionService;
import com.ccerphr.assessment.service.BizIndicatorSubCategoryService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BizIndicatorDefinitionServiceImpl extends ServiceImpl<BizIndicatorDefinitionMapper, BizIndicatorDefinition> implements BizIndicatorDefinitionService {

    private final BizExamGroupMapper examGroupMapper;
    private final BizIndicatorOrgMapper indicatorOrgMapper;
    private final BizIndicatorLeaderMapper indicatorLeaderMapper;
    private final SysOrganizationMapper organizationMapper;
    private final BizIndicatorSubCategoryService subCategoryService;

    public BizIndicatorDefinitionServiceImpl(
            BizExamGroupMapper examGroupMapper,
            BizIndicatorOrgMapper indicatorOrgMapper,
            BizIndicatorLeaderMapper indicatorLeaderMapper,
            SysOrganizationMapper organizationMapper,
            BizIndicatorSubCategoryService subCategoryService) {
        this.examGroupMapper = examGroupMapper;
        this.indicatorOrgMapper = indicatorOrgMapper;
        this.indicatorLeaderMapper = indicatorLeaderMapper;
        this.organizationMapper = organizationMapper;
        this.subCategoryService = subCategoryService;
    }

    @Override
    public PageResult<IndicatorVO> queryPage(IndicatorQueryDTO queryDTO) {
        LambdaQueryWrapper<BizIndicatorDefinition> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getExamGroupId() != null) {
            wrapper.eq(BizIndicatorDefinition::getExamGroupId, queryDTO.getExamGroupId());
        }
        if (queryDTO.getOrgId() != null) {
            wrapper.eq(BizIndicatorDefinition::getOrgId, queryDTO.getOrgId());
        }
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(BizIndicatorDefinition::getCategoryId, queryDTO.getCategoryId());
        }
        if (StringUtils.hasText(queryDTO.getApprovalStatus())) {
            wrapper.eq(BizIndicatorDefinition::getApprovalStatus, queryDTO.getApprovalStatus());
        }
        // 应用数据范围权限过滤
        DataScopeFilter.applyFilter(wrapper, BizIndicatorDefinition::getUnitId, BizIndicatorDefinition::getOrgId);
        wrapper.orderByAsc(BizIndicatorDefinition::getSortCode);
        Page<BizIndicatorDefinition> page = page(new Page<>(queryDTO.getCurrent(), queryDTO.getSize()), wrapper);
        List<IndicatorVO> voList = convertToVOList(page.getRecords());
        PageResult<IndicatorVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public BizIndicatorDefinition getDetail(Long id) {
        BizIndicatorDefinition indicator = requireAccessibleIndicator(id);
        if (indicator == null) {
            throw new BusinessException("Indicator not found");
        }
        return indicator;
    }

    @Override
    public List<IndicatorTreeDTO> getIndicatorTree(Long examGroupId, Long orgId) {
        List<BizIndicatorDefinition> list = baseMapper.selectByExamGroupAndOrg(examGroupId, orgId);

        Map<String, Map<String, List<BizIndicatorDefinition>>> grouped = list.stream()
                .collect(Collectors.groupingBy(
                        BizIndicatorDefinition::getCategoryName,
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                i -> i.getSubCategory() != null ? i.getSubCategory() : "",
                                LinkedHashMap::new,
                                Collectors.toList()
                        )
                ));

        List<IndicatorTreeDTO> tree = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<BizIndicatorDefinition>>> catEntry : grouped.entrySet()) {
            IndicatorTreeDTO catDto = new IndicatorTreeDTO();
            catDto.setCategoryName(catEntry.getKey());
            List<IndicatorTreeDTO.IndicatorSubCategoryDTO> subList = new ArrayList<>();
            for (Map.Entry<String, List<BizIndicatorDefinition>> subEntry : catEntry.getValue().entrySet()) {
                IndicatorTreeDTO.IndicatorSubCategoryDTO subDto = new IndicatorTreeDTO.IndicatorSubCategoryDTO();
                subDto.setSubCategory(subEntry.getKey());
                List<IndicatorTreeDTO.IndicatorItemDTO> itemList = new ArrayList<>();
                for (BizIndicatorDefinition ind : subEntry.getValue()) {
                    IndicatorTreeDTO.IndicatorItemDTO item = new IndicatorTreeDTO.IndicatorItemDTO();
                    item.setId(ind.getId());
                    item.setContent(ind.getContent());
                    item.setTargetDesc(ind.getTargetDesc());
                    item.setWeightAnnual(ind.getWeightAnnual());
                    item.setWeightMonthly(ind.getWeightMonthly());
                    item.setEvaluationStandard(ind.getEvaluationStandard());
                    itemList.add(item);
                }
                subDto.setItems(itemList);
                subList.add(subDto);
            }
            catDto.setSubCategories(subList);
            tree.add(catDto);
        }
        return tree;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createIndicator(IndicatorSetDTO dto) {
        BizIndicatorDefinition indicator = new BizIndicatorDefinition();
        BeanUtils.copyProperties(dto, indicator);
        indicator.setApprovalStatus(ApprovalStatus.DRAFT.getCode());
        indicator.setCreatedTime(LocalDateTime.now());
        indicator.setUpdatedTime(LocalDateTime.now());
        // 校验并规范化指标归属信息
        normalizeOwnerFields(indicator, dto);
        validateExamTarget(indicator, dto);
        normalizeSubCategoryFields(indicator, dto);
        // 保存指标基础信息
        save(indicator);

        // 保存指标关联的部门和负责人信息
        saveIndicatorRelations(indicator.getId(), dto);

        return indicator.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIndicator(IndicatorSetDTO dto) {
        BizIndicatorDefinition indicator = requireAccessibleIndicator(dto.getId());
        if (indicator == null) {
            throw new BusinessException("Indicator not found");
        }
        BeanUtils.copyProperties(dto, indicator);
        indicator.setUpdatedTime(LocalDateTime.now());
        // 校验并规范化指标归属信息
        normalizeOwnerFields(indicator, dto);
        validateExamTarget(indicator, dto);
        normalizeSubCategoryFields(indicator, dto);
        // 更新指标基础信息
        updateById(indicator);

        // 更新指标关联数据 (先删后增)
        indicatorOrgMapper.deleteByIndicatorId(indicator.getId());
        indicatorLeaderMapper.deleteByIndicatorId(indicator.getId());
        saveIndicatorRelations(indicator.getId(), dto);
    }

    private void normalizeOwnerFields(BizIndicatorDefinition indicator, IndicatorSetDTO dto) {
        Long ownerOrgId = dto.getOrgId();
        if (ownerOrgId == null) {
            throw new BusinessException("指标归属部门不能为空");
        }

        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        if ("ORG".equals(dataScope) && scopeId != null && scopeId != 0L && !scopeId.equals(ownerOrgId)) {
            throw new BusinessException("指标归属部门必须与当前数据范围一致");
        }

        SysOrganization ownerOrg = organizationMapper.selectById(ownerOrgId);
        if (ownerOrg == null || (ownerOrg.getDeleted() != null && ownerOrg.getDeleted() != 0)) {
            throw new BusinessException("指标归属部门不存在");
        }
        if (ownerOrg.getUnitId() == null) {
            throw new BusinessException("指标归属部门未配置所属单位");
        }
        if ("UNIT".equals(dataScope) && scopeId != null && scopeId != 0L && !scopeId.equals(ownerOrg.getUnitId())) {
            throw new BusinessException("指标归属部门不在当前单位数据范围内");
        }

        indicator.setOrgId(ownerOrg.getId());
        indicator.setOrgName(ownerOrg.getOrgName());
        indicator.setUnitId(ownerOrg.getUnitId());

        if (!CollectionUtils.isEmpty(dto.getLeaderIds())) {
            indicator.setLeaderId(dto.getLeaderIds().get(0));
        } else {
            indicator.setLeaderId(dto.getLeaderId());
        }
    }

    private void validateExamTarget(BizIndicatorDefinition indicator, IndicatorSetDTO dto) {
        // 允许自己部门给自己打分，不再限制
        return;
    }

    private void normalizeSubCategoryFields(BizIndicatorDefinition indicator, IndicatorSetDTO dto) {
        if (!StringUtils.hasText(dto.getSubCategory())) {
            throw new BusinessException("指标小类不能为空");
        }
        IndicatorSubCategoryDTO subDto = new IndicatorSubCategoryDTO();
        subDto.setId(dto.getSubCategoryId());
        subDto.setExamGroupId(indicator.getExamGroupId());
        subDto.setOrgId(indicator.getOrgId());
        subDto.setCategoryId(indicator.getCategoryId());
        subDto.setCategoryName(indicator.getCategoryName());
        subDto.setSubCategoryName(dto.getSubCategory());
        subDto.setEvaluationStandard(dto.getEvaluationStandard());
        subDto.setSortCode(0);
        BizIndicatorSubCategory subCategory = subCategoryService.ensureSubCategory(subDto);
        indicator.setSubCategoryId(subCategory.getId());
        indicator.setSubCategory(subCategory.getSubCategoryName());
        if (!StringUtils.hasText(indicator.getEvaluationStandard())) {
            indicator.setEvaluationStandard(subCategory.getEvaluationStandard());
        }
    }

    /**
     * 保存指标关联的部门和负责人
     */
    private void saveIndicatorRelations(Long indicatorId, IndicatorSetDTO dto) {
        LocalDateTime now = LocalDateTime.now();

        // 处理关联部门
        if (!CollectionUtils.isEmpty(dto.getOrgIds())) {
            for (int i = 0; i < dto.getOrgIds().size(); i++) {
                BizIndicatorOrg org = new BizIndicatorOrg();
                org.setIndicatorId(indicatorId);
                org.setOrgId(dto.getOrgIds().get(i));
                if (dto.getOrgNames() != null && i < dto.getOrgNames().size()) {
                    org.setOrgName(dto.getOrgNames().get(i));
                }
                org.setCreatedTime(now);
                indicatorOrgMapper.insert(org);
            }
        }

        // 处理分管领导
        if (!CollectionUtils.isEmpty(dto.getLeaderIds())) {
            for (int i = 0; i < dto.getLeaderIds().size(); i++) {
                BizIndicatorLeader leader = new BizIndicatorLeader();
                leader.setIndicatorId(indicatorId);
                leader.setLeaderId(dto.getLeaderIds().get(i));
                if (dto.getLeaderNames() != null && i < dto.getLeaderNames().size()) {
                    leader.setLeaderName(dto.getLeaderNames().get(i));
                }
                leader.setCreatedTime(now);
                indicatorLeaderMapper.insert(leader);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteIndicator(Long id) {
        BizIndicatorDefinition indicator = requireAccessibleIndicator(id);
        if (indicator == null) {
            throw new BusinessException("Indicator not found");
        }
        if (!ApprovalStatus.DRAFT.getCode().equals(indicator.getApprovalStatus())) {
            throw new BusinessException("Only draft indicators can be deleted");
        }
        // 删除关联的部门和领导数据
        indicatorOrgMapper.deleteByIndicatorId(id);
        indicatorLeaderMapper.deleteByIndicatorId(id);
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(List<Long> indicatorIds) {
        if (indicatorIds == null || indicatorIds.isEmpty()) {
            throw new BusinessException("Please select indicators to submit");
        }
        for (Long id : indicatorIds) {
            BizIndicatorDefinition indicator = requireAccessibleIndicator(id);
            if (indicator == null) {
                continue;
            }
            if (!ApprovalStatus.DRAFT.getCode().equals(indicator.getApprovalStatus())
                    && !ApprovalStatus.REJECTED.getCode().equals(indicator.getApprovalStatus())) {
                continue;
            }
            indicator.setApprovalStatus(ApprovalStatus.PENDING_DEPT_LEADER.getCode());
            indicator.setSubmittedTime(LocalDateTime.now());
            indicator.setUpdatedTime(LocalDateTime.now());
            updateById(indicator);
        }
    }

    @Override
    public PageResult<IndicatorVO> getApprovalList(IndicatorQueryDTO queryDTO, String roleCode) {
        LambdaQueryWrapper<BizIndicatorDefinition> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getExamGroupId() != null) {
            wrapper.eq(BizIndicatorDefinition::getExamGroupId, queryDTO.getExamGroupId());
        }
        if (queryDTO.getOrgId() != null) {
            wrapper.eq(BizIndicatorDefinition::getOrgId, queryDTO.getOrgId());
        }
        if (StringUtils.hasText(queryDTO.getApprovalStatus())) {
            wrapper.eq(BizIndicatorDefinition::getApprovalStatus, queryDTO.getApprovalStatus());
        }
        DataScopeFilter.applyFilter(wrapper, BizIndicatorDefinition::getUnitId, BizIndicatorDefinition::getOrgId);
        wrapper.orderByDesc(BizIndicatorDefinition::getSubmittedTime);
        Page<BizIndicatorDefinition> page = page(new Page<>(queryDTO.getCurrent(), queryDTO.getSize()), wrapper);
        List<IndicatorVO> voList = convertToVOList(page.getRecords());
        PageResult<IndicatorVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    private List<IndicatorVO> convertToVOList(List<BizIndicatorDefinition> list) {
        if (org.springframework.util.CollectionUtils.isEmpty(list)) {
            return new java.util.ArrayList<>();
        }

        // 1. 批量收集 ID
        List<Long> indicatorIds = list.stream().map(BizIndicatorDefinition::getId).collect(Collectors.toList());
        List<Long> groupIds = list.stream()
                .map(BizIndicatorDefinition::getExamGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 2. 批量查询关联数据 (避免 N+1)
        Map<Long, BizExamGroup> groupMap = new java.util.HashMap<>();
        if (!groupIds.isEmpty()) {
            groupMap = examGroupMapper.selectBatchIds(groupIds).stream()
                    .collect(Collectors.toMap(BizExamGroup::getId, g -> g));
        }

        Map<Long, List<BizIndicatorOrg>> orgMap = indicatorOrgMapper.selectList(
                new LambdaQueryWrapper<BizIndicatorOrg>().in(BizIndicatorOrg::getIndicatorId, indicatorIds)
        ).stream().collect(Collectors.groupingBy(BizIndicatorOrg::getIndicatorId));

        Map<Long, List<BizIndicatorLeader>> leaderMap = indicatorLeaderMapper.selectList(
                new LambdaQueryWrapper<BizIndicatorLeader>().in(BizIndicatorLeader::getIndicatorId, indicatorIds)
        ).stream().collect(Collectors.groupingBy(BizIndicatorLeader::getIndicatorId));

        // 3. 组装结果
        List<IndicatorVO> voList = new java.util.ArrayList<>();
        for (BizIndicatorDefinition indicator : list) {
            IndicatorVO vo = new IndicatorVO();
            BeanUtils.copyProperties(indicator, vo);

            // 填充考核组信息
            BizExamGroup group = groupMap.get(indicator.getExamGroupId());
            if (group != null) {
                vo.setExamGroupName(group.getGroupName());
                vo.setExamType(group.getExamType());
                vo.setExamCategory(group.getExamCategory());
                vo.setStartDate(group.getStartDate() != null ? group.getStartDate().toString() : null);
                vo.setEndDate(group.getEndDate() != null ? group.getEndDate().toString() : null);
            }

            // 填充归属部门列表
            List<BizIndicatorOrg> orgs = orgMap.getOrDefault(indicator.getId(), new java.util.ArrayList<>());
            if (!orgs.isEmpty()) {
                vo.setOrgIdList(orgs.stream().map(BizIndicatorOrg::getOrgId).collect(Collectors.toList()));
                vo.setOrgNameList(orgs.stream().map(BizIndicatorOrg::getOrgName).collect(Collectors.toList()));
            }

            // 填充负责人列表
            List<BizIndicatorLeader> leaders = leaderMap.getOrDefault(indicator.getId(), new java.util.ArrayList<>());
            if (!leaders.isEmpty()) {
                vo.setLeaderIdList(leaders.stream().map(BizIndicatorLeader::getLeaderId).collect(Collectors.toList()));
                vo.setLeaderNameList(leaders.stream().map(BizIndicatorLeader::getLeaderName).collect(Collectors.toList()));
            }

            voList.add(vo);
        }
        return voList;
    }

    private IndicatorVO convertToVO(BizIndicatorDefinition indicator) {
        IndicatorVO vo = new IndicatorVO();
        BeanUtils.copyProperties(indicator, vo);
        if (indicator.getExamGroupId() != null) {
            BizExamGroup group = examGroupMapper.selectById(indicator.getExamGroupId());
            if (group != null) {
                vo.setExamGroupName(group.getGroupName());
                vo.setExamType(group.getExamType());
                vo.setExamCategory(group.getExamCategory());
                vo.setStartDate(group.getStartDate() != null ? group.getStartDate().toString() : null);
                vo.setEndDate(group.getEndDate() != null ? group.getEndDate().toString() : null);
            }
        }

        // 婵犵數鍋涢顓熸叏鐎涙﹩娈介柟闂寸閻鎲搁弮鍫濈疇闁哄洢鍨圭猾宥夋煕閵夛絽鍔楅柛瀣崌瀹曟﹢顢旈崨顓犲酱闂佽崵濮村ú锕併亹閸愵亜绶ら柛顭戝櫘閻斿棝鏌ら幖浣规锭闁告繃妞介弻宥堫檨闁告挻鐟╄棟濞村吋娼欏Ч鏌ユ煟閺冨倸甯剁紒?
        List<BizIndicatorOrg> orgList = indicatorOrgMapper.selectByIndicatorId(indicator.getId());
        if (!orgList.isEmpty()) {
            vo.setOrgIdList(orgList.stream().map(BizIndicatorOrg::getOrgId).collect(Collectors.toList()));
            vo.setOrgNameList(orgList.stream().map(BizIndicatorOrg::getOrgName).collect(Collectors.toList()));
        }

        List<BizIndicatorLeader> leaderList = indicatorLeaderMapper.selectByIndicatorId(indicator.getId());
        if (!leaderList.isEmpty()) {
            vo.setLeaderIdList(leaderList.stream().map(BizIndicatorLeader::getLeaderId).collect(Collectors.toList()));
            vo.setLeaderNameList(leaderList.stream().map(BizIndicatorLeader::getLeaderName).collect(Collectors.toList()));
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(IndicatorApprovalDTO dto) {
        if (dto.getIndicatorIds() == null || dto.getIndicatorIds().isEmpty()) {
            throw new BusinessException("Please select indicators to approve");
        }
        String roleCode = dto.getRoleCode();
        for (Long id : dto.getIndicatorIds()) {
            BizIndicatorDefinition indicator = requireAccessibleIndicator(id);
            if (indicator == null) {
                continue;
            }
            String nextStatus = getNextApprovalStatus(indicator.getApprovalStatus(), roleCode);
            if (nextStatus == null) {
                continue;
            }
            indicator.setApprovalStatus(nextStatus);
            indicator.setApprovedTime(LocalDateTime.now());
            indicator.setUpdatedTime(LocalDateTime.now());
            updateById(indicator);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(IndicatorApprovalDTO dto) {
        if (dto.getIndicatorIds() == null || dto.getIndicatorIds().isEmpty()) {
            throw new BusinessException("Please select indicators to reject");
        }
        for (Long id : dto.getIndicatorIds()) {
            BizIndicatorDefinition indicator = requireAccessibleIndicator(id);
            if (indicator == null) {
                continue;
            }
            indicator.setApprovalStatus(ApprovalStatus.REJECTED.getCode());
            indicator.setRejectReason(dto.getRejectReason());
            indicator.setUpdatedTime(LocalDateTime.now());
            updateById(indicator);
        }
    }

    private String getNextApprovalStatus(String currentStatus, String roleCode) {
        if (ApprovalStatus.PENDING_DEPT_LEADER.getCode().equals(currentStatus) && "DEPT_LEADER".equals(roleCode)) {
            return ApprovalStatus.PENDING_SUPERVISOR.getCode();
        }
        if (ApprovalStatus.PENDING_SUPERVISOR.getCode().equals(currentStatus) && "SUPERVISOR".equals(roleCode)) {
            return ApprovalStatus.PENDING_FINANCE.getCode();
        }
        if (ApprovalStatus.PENDING_FINANCE.getCode().equals(currentStatus) && "FIN_ADMIN".equals(roleCode)) {
            return ApprovalStatus.APPROVED.getCode();
        }
        return null;
    }

    private BizIndicatorDefinition requireAccessibleIndicator(Long id) {
        BizIndicatorDefinition indicator = getById(id);
        if (indicator == null) {
            throw new BusinessException(404, "Indicator not found");
        }
        assertIndicatorAccessible(indicator);
        return indicator;
    }

    private void assertIndicatorAccessible(BizIndicatorDefinition indicator) {
        String dataScope = DataScopeContext.getDataScope();
        if ("ALL".equals(dataScope)) {
            return;
        }
        Long scopeId = DataScopeContext.getScopeId();
        if (scopeId == null || scopeId == 0L) {
            throw new BusinessException(403, "No permission to access this indicator");
        }
        if ("UNIT".equals(dataScope)) {
            if (scopeId.equals(indicator.getUnitId())) {
                return;
            }
            throw new BusinessException(403, "Indicator is outside current unit scope");
        }
        if ("ORG".equals(dataScope)) {
            if (scopeId.equals(indicator.getOrgId())) {
                return;
            }
            throw new BusinessException(403, "Indicator is outside current organization scope");
        }
        throw new BusinessException(403, "No permission to access this indicator");
    }

    @Override
    public List<IndicatorProgressVO> queryProgress(Long examGroupId, String orgName, String approvalStatus) {
        Long unitId = null;
        Long orgId = null;
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        if ("UNIT".equals(dataScope) && scopeId != null && scopeId != 0L) {
            unitId = DataScopeContext.getCurrentUnitId();
        } else if ("ORG".equals(dataScope) && scopeId != null && scopeId != 0L) {
            orgId = scopeId;  // 如果是部门权限，则限定在该部门 ID
        }
        return baseMapper.queryProgress(examGroupId, orgName, approvalStatus, unitId, orgId);
    }
}


