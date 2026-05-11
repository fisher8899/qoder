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
import com.ccerphr.assessment.dto.IndicatorTreeDTO;
import com.ccerphr.assessment.dto.IndicatorVO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizIndicatorLeader;
import com.ccerphr.assessment.entity.BizIndicatorOrg;
import com.ccerphr.assessment.enums.ApprovalStatus;
import com.ccerphr.assessment.mapper.BizExamGroupMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizIndicatorLeaderMapper;
import com.ccerphr.assessment.mapper.BizIndicatorOrgMapper;
import com.ccerphr.assessment.service.BizIndicatorDefinitionService;
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
import java.util.stream.Collectors;

@Service
public class BizIndicatorDefinitionServiceImpl extends ServiceImpl<BizIndicatorDefinitionMapper, BizIndicatorDefinition> implements BizIndicatorDefinitionService {

    private final BizExamGroupMapper examGroupMapper;
    private final BizIndicatorOrgMapper indicatorOrgMapper;
    private final BizIndicatorLeaderMapper indicatorLeaderMapper;

    public BizIndicatorDefinitionServiceImpl(
            BizExamGroupMapper examGroupMapper,
            BizIndicatorOrgMapper indicatorOrgMapper,
            BizIndicatorLeaderMapper indicatorLeaderMapper) {
        this.examGroupMapper = examGroupMapper;
        this.indicatorOrgMapper = indicatorOrgMapper;
        this.indicatorLeaderMapper = indicatorLeaderMapper;
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
        // 数据范围过滤
        DataScopeFilter.applyFilter(wrapper, BizIndicatorDefinition::getUnitId, BizIndicatorDefinition::getOrgId);
        wrapper.orderByAsc(BizIndicatorDefinition::getSortCode);
        Page<BizIndicatorDefinition> page = page(new Page<>(queryDTO.getCurrent(), queryDTO.getSize()), wrapper);
        List<IndicatorVO> voList = page.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
        PageResult<IndicatorVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public BizIndicatorDefinition getDetail(Long id) {
        BizIndicatorDefinition indicator = getById(id);
        if (indicator == null) {
            throw new BusinessException("指标不存在");
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
        // 自动填入 unitId
        Long unitId = DataScopeFilter.getAutoFillUnitId();
        if (unitId != null && indicator.getUnitId() == null) {
            indicator.setUnitId(unitId);
        }
        // 设置第一个部门/领导作为主键关联（兼容单选查询）
        if (!CollectionUtils.isEmpty(dto.getOrgIds())) {
            indicator.setOrgId(dto.getOrgIds().get(0));
        }
        if (!CollectionUtils.isEmpty(dto.getLeaderIds())) {
            indicator.setLeaderId(dto.getLeaderIds().get(0));
        }
        save(indicator);

        // 保存关联数据
        saveIndicatorRelations(indicator.getId(), dto);

        return indicator.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIndicator(IndicatorSetDTO dto) {
        BizIndicatorDefinition indicator = getById(dto.getId());
        if (indicator == null) {
            throw new BusinessException("指标不存在");
        }
        BeanUtils.copyProperties(dto, indicator);
        indicator.setUpdatedTime(LocalDateTime.now());
        // 设置第一个部门/领导作为主键关联
        if (!CollectionUtils.isEmpty(dto.getOrgIds())) {
            indicator.setOrgId(dto.getOrgIds().get(0));
        }
        if (!CollectionUtils.isEmpty(dto.getLeaderIds())) {
            indicator.setLeaderId(dto.getLeaderIds().get(0));
        }
        updateById(indicator);

        // 删除旧关联数据，保存新关联数据
        indicatorOrgMapper.deleteByIndicatorId(indicator.getId());
        indicatorLeaderMapper.deleteByIndicatorId(indicator.getId());
        saveIndicatorRelations(indicator.getId(), dto);
    }

    /**
     * 保存指标关联数据
     */
    private void saveIndicatorRelations(Long indicatorId, IndicatorSetDTO dto) {
        LocalDateTime now = LocalDateTime.now();

        // 保存考核部门关联
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

        // 保存分管领导关联
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
        BizIndicatorDefinition indicator = getById(id);
        if (indicator == null) {
            throw new BusinessException("指标不存在");
        }
        if (!ApprovalStatus.DRAFT.getCode().equals(indicator.getApprovalStatus())) {
            throw new BusinessException("仅草稿状态的指标可删除");
        }
        // 删除关联数据
        indicatorOrgMapper.deleteByIndicatorId(id);
        indicatorLeaderMapper.deleteByIndicatorId(id);
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(List<Long> indicatorIds) {
        if (indicatorIds == null || indicatorIds.isEmpty()) {
            throw new BusinessException("请选择要提交的指标");
        }
        for (Long id : indicatorIds) {
            BizIndicatorDefinition indicator = getById(id);
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
        // 根据角色筛选对应审批状态
        if ("DEPT_LEADER".equals(roleCode)) {
            wrapper.eq(BizIndicatorDefinition::getApprovalStatus, ApprovalStatus.PENDING_DEPT_LEADER.getCode());
        } else if ("SUPERVISOR".equals(roleCode)) {
            wrapper.eq(BizIndicatorDefinition::getApprovalStatus, ApprovalStatus.PENDING_SUPERVISOR.getCode());
        } else if ("FIN_ADMIN".equals(roleCode)) {
            wrapper.eq(BizIndicatorDefinition::getApprovalStatus, ApprovalStatus.PENDING_FINANCE.getCode());
        } else if (StringUtils.hasText(queryDTO.getApprovalStatus())) {
            wrapper.eq(BizIndicatorDefinition::getApprovalStatus, queryDTO.getApprovalStatus());
        }
        wrapper.orderByDesc(BizIndicatorDefinition::getSubmittedTime);
        Page<BizIndicatorDefinition> page = page(new Page<>(queryDTO.getCurrent(), queryDTO.getSize()), wrapper);
        List<IndicatorVO> voList = page.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
        PageResult<IndicatorVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
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

        // 从关联表读取多选数据
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
            throw new BusinessException("请选择要审批的指标");
        }
        String roleCode = dto.getRoleCode();
        for (Long id : dto.getIndicatorIds()) {
            BizIndicatorDefinition indicator = getById(id);
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
            throw new BusinessException("请选择要退回的指标");
        }
        for (Long id : dto.getIndicatorIds()) {
            BizIndicatorDefinition indicator = getById(id);
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

    @Override
    public List<IndicatorProgressVO> queryProgress(Long examGroupId, String orgName, String approvalStatus) {
        Long unitId = null;
        Long orgId = null;
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        if ("UNIT".equals(dataScope) && scopeId != null && scopeId != 0L) {
            unitId = scopeId;
        } else if ("ORG".equals(dataScope) && scopeId != null && scopeId != 0L) {
            orgId = scopeId;  // 精确到组织
        }
        return baseMapper.queryProgress(examGroupId, orgName, approvalStatus, unitId, orgId);
    }
}
