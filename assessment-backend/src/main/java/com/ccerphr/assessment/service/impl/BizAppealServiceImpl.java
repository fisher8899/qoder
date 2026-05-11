package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.dto.AppealCreateDTO;
import com.ccerphr.assessment.dto.AppealHandleDTO;
import com.ccerphr.assessment.dto.AppealQueryDTO;
import com.ccerphr.assessment.entity.BizAppeal;
import com.ccerphr.assessment.entity.BizAppealAttachment;
import com.ccerphr.assessment.mapper.BizAppealAttachmentMapper;
import com.ccerphr.assessment.mapper.BizAppealMapper;
import com.ccerphr.assessment.service.BizAppealService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BizAppealServiceImpl extends ServiceImpl<BizAppealMapper, BizAppeal> implements BizAppealService {

    private final BizAppealAttachmentMapper attachmentMapper;

    public BizAppealServiceImpl(BizAppealAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public List<BizAppeal> getAppealList(AppealQueryDTO queryDTO) {
        LambdaQueryWrapper<BizAppeal> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getExamGroupId() != null) {
            wrapper.eq(BizAppeal::getExamGroupId, queryDTO.getExamGroupId());
        }
        if (queryDTO.getAppealOrgId() != null) {
            wrapper.eq(BizAppeal::getAppealOrgId, queryDTO.getAppealOrgId());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(BizAppeal::getStatus, queryDTO.getStatus());
        }
        // 数据范围过滤
        DataScopeFilter.applyUnitFilter(wrapper, BizAppeal::getUnitId);
        wrapper.orderByDesc(BizAppeal::getCreatedTime);
        Page<BizAppeal> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        return baseMapper.selectPage(page, wrapper).getRecords();
    }

    @Override
    public Long countAppealList(AppealQueryDTO queryDTO) {
        LambdaQueryWrapper<BizAppeal> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getExamGroupId() != null) {
            wrapper.eq(BizAppeal::getExamGroupId, queryDTO.getExamGroupId());
        }
        if (queryDTO.getAppealOrgId() != null) {
            wrapper.eq(BizAppeal::getAppealOrgId, queryDTO.getAppealOrgId());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(BizAppeal::getStatus, queryDTO.getStatus());
        }
        return baseMapper.selectCount(wrapper);
    }

    @Override
    public Map<String, Object> getAppealDetail(Long id) {
        BizAppeal appeal = getById(id);
        if (appeal == null) {
            throw new BusinessException("申诉记录不存在");
        }
        List<BizAppealAttachment> attachments = attachmentMapper.selectByAppealId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("appeal", appeal);
        result.put("attachments", attachments);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizAppeal createAppeal(AppealCreateDTO dto, String createdBy) {
        BizAppeal entity = new BizAppeal();
        entity.setExamGroupId(dto.getExamGroupId());
        entity.setAppealOrgId(dto.getAppealOrgId());
        entity.setAppealOrgName(dto.getAppealOrgName());
        entity.setScorerOrgId(dto.getScorerOrgId());
        entity.setScorerOrgName(dto.getScorerOrgName());
        entity.setIndicatorId(dto.getIndicatorId());
        entity.setAppealReason(dto.getAppealReason());
        entity.setOriginalScore(dto.getOriginalScore());
        entity.setStatus("DRAFT");
        entity.setCreatedBy(createdBy);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        save(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAppeal(Long id) {
        BizAppeal appeal = getById(id);
        if (appeal == null) {
            throw new BusinessException("申诉记录不存在");
        }
        if (!"DRAFT".equals(appeal.getStatus())) {
            throw new BusinessException("只有草稿状态的申诉可以提交");
        }
        appeal.setStatus("PENDING_REEVAL");
        appeal.setUpdatedTime(LocalDateTime.now());
        updateById(appeal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reassignAppeal(Long id) {
        BizAppeal appeal = getById(id);
        if (appeal == null) {
            throw new BusinessException("申诉记录不存在");
        }
        if (!"PENDING_REEVAL".equals(appeal.getStatus())) {
            throw new BusinessException("只有待重新评估状态的申诉可以退回");
        }
        appeal.setStatus("DRAFT");
        appeal.setUpdatedTime(LocalDateTime.now());
        updateById(appeal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAppeal(AppealHandleDTO dto, String handledBy) {
        BizAppeal appeal = getById(dto.getAppealId());
        if (appeal == null) {
            throw new BusinessException("申诉记录不存在");
        }
        if (!"PENDING_REEVAL".equals(appeal.getStatus())) {
            throw new BusinessException("只有待重新评估状态的申诉可以处理");
        }
        appeal.setNewScore(dto.getNewScore());
        appeal.setStatus("HANDLED");
        appeal.setHandledBy(handledBy);
        appeal.setHandledTime(LocalDateTime.now());
        appeal.setUpdatedTime(LocalDateTime.now());
        updateById(appeal);
    }

    @Override
    public List<BizAppeal> getPendingReevalList(Long scorerOrgId) {
        if (scorerOrgId == null) {
            return List.of();
        }
        return getBaseMapper().selectPendingReevalByScorer(scorerOrgId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reScoreAppeal(Long id, AppealHandleDTO dto, String handledBy) {
        BizAppeal appeal = getById(id);
        if (appeal == null) {
            throw new BusinessException("申诉记录不存在");
        }
        if (!"PENDING_REEVAL".equals(appeal.getStatus())) {
            throw new BusinessException("只有待重新评估状态的申诉可以重新打分");
        }
        appeal.setNewScore(dto.getNewScore());
        appeal.setStatus("HANDLED");
        appeal.setHandledBy(handledBy);
        appeal.setHandledTime(LocalDateTime.now());
        appeal.setUpdatedTime(LocalDateTime.now());
        updateById(appeal);
    }
}
