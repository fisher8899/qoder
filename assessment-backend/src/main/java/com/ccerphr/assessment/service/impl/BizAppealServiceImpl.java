package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.AppealCreateDTO;
import com.ccerphr.assessment.dto.AppealHandleDTO;
import com.ccerphr.assessment.dto.AppealQueryDTO;
import com.ccerphr.assessment.entity.BizAppeal;
import com.ccerphr.assessment.entity.BizAppealAttachment;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizReviewScore;
import com.ccerphr.assessment.mapper.BizAppealAttachmentMapper;
import com.ccerphr.assessment.mapper.BizAppealMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizReviewScoreMapper;
import com.ccerphr.assessment.service.BizAppealService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BizAppealServiceImpl extends ServiceImpl<BizAppealMapper, BizAppeal> implements BizAppealService {

    private final BizAppealAttachmentMapper attachmentMapper;
    private final BizReviewScoreMapper reviewScoreMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;

    public BizAppealServiceImpl(BizAppealAttachmentMapper attachmentMapper,
                                BizReviewScoreMapper reviewScoreMapper,
                                BizIndicatorDefinitionMapper indicatorDefinitionMapper) {
        this.attachmentMapper = attachmentMapper;
        this.reviewScoreMapper = reviewScoreMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
    }

    @Override
    public List<BizAppeal> getAppealList(AppealQueryDTO queryDTO) {
        LambdaQueryWrapper<BizAppeal> wrapper = buildAppealQueryWrapper(queryDTO);
        wrapper.orderByDesc(BizAppeal::getCreatedTime);
        Page<BizAppeal> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        return baseMapper.selectPage(page, wrapper).getRecords();
    }

    @Override
    public Long countAppealList(AppealQueryDTO queryDTO) {
        LambdaQueryWrapper<BizAppeal> wrapper = buildAppealQueryWrapper(queryDTO);
        return baseMapper.selectCount(wrapper);
    }

    private LambdaQueryWrapper<BizAppeal> buildAppealQueryWrapper(AppealQueryDTO queryDTO) {
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
        DataScopeFilter.applyUnitFilter(wrapper, BizAppeal::getUnitId);
        return wrapper;
    }

    @Override
    public Map<String, Object> getAppealDetail(Long id) {
        BizAppeal appeal = getAccessibleAppeal(id);
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
        entity.setUnitId(DataScopeFilter.getAutoFillUnitId());
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
        BizAppeal appeal = getAccessibleAppeal(id);
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
        BizAppeal appeal = getAccessibleAppeal(id);
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
        processAppeal(dto.getAppealId(), dto.getNewScore(), handledBy, "只有待重新评估状态的申诉可以处理");
    }

    @Override
    public List<BizAppeal> getPendingReevalList(Long scorerOrgId) {
        if (scorerOrgId == null) {
            return List.of();
        }
        List<BizAppeal> appeals = getBaseMapper().selectPendingReevalByScorer(scorerOrgId);
        return appeals.stream()
                .filter(this::isAppealAccessible)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reScoreAppeal(Long id, AppealHandleDTO dto, String handledBy) {
        processAppeal(id, dto.getNewScore(), handledBy, "只有待重新评估状态的申诉可以重新打分");
    }

    private void processAppeal(Long appealId, BigDecimal newScore, String handledBy, String statusErrorMessage) {
        BizAppeal appeal = getAccessibleAppeal(appealId);
        if (!"PENDING_REEVAL".equals(appeal.getStatus())) {
            throw new BusinessException(statusErrorMessage);
        }
        appeal.setNewScore(newScore);
        appeal.setStatus("HANDLED");
        appeal.setHandledBy(handledBy);
        appeal.setHandledTime(LocalDateTime.now());
        appeal.setUpdatedTime(LocalDateTime.now());
        updateById(appeal);
        updateReviewScore(appeal, newScore);
    }

    @Override
    public BizAppeal getAccessibleAppeal(Long id) {
        BizAppeal appeal = getById(id);
        if (appeal == null) {
            throw new BusinessException(404, "申诉记录不存在");
        }
        assertAppealAccessible(appeal);
        return appeal;
    }

    @Override
    public BizAppealAttachment getAccessibleAttachment(Long attachmentId) {
        BizAppealAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(404, "附件不存在");
        }
        getAccessibleAppeal(attachment.getAppealId());
        return attachment;
    }

    private void updateReviewScore(BizAppeal appeal, BigDecimal newScore) {
        if (newScore == null || appeal.getIndicatorId() == null || appeal.getExamGroupId() == null) {
            return;
        }

        LambdaQueryWrapper<BizReviewScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizReviewScore::getExamGroupId, appeal.getExamGroupId());
        wrapper.eq(BizReviewScore::getOrgId, appeal.getAppealOrgId());
        wrapper.eq(BizReviewScore::getIndicatorId, appeal.getIndicatorId());
        BizReviewScore reviewScore = reviewScoreMapper.selectOne(wrapper);

        if (reviewScore != null) {
            reviewScore.setAdminScore(newScore);
            reviewScore.setFinalScore(newScore);
            reviewScore.setUpdatedTime(LocalDateTime.now());
            reviewScoreMapper.updateById(reviewScore);
            return;
        }

        BizIndicatorDefinition indicator = indicatorDefinitionMapper.selectById(appeal.getIndicatorId());
        reviewScore = new BizReviewScore();
        reviewScore.setExamGroupId(appeal.getExamGroupId());
        reviewScore.setOrgId(appeal.getAppealOrgId());
        reviewScore.setOrgName(appeal.getAppealOrgName());
        reviewScore.setIndicatorId(appeal.getIndicatorId());
        reviewScore.setAdminScore(newScore);
        reviewScore.setFinalScore(newScore);
        reviewScore.setCreatedTime(LocalDateTime.now());
        reviewScore.setUpdatedTime(LocalDateTime.now());
        if (indicator != null) {
            // indicator exists but finalScore already set above, no additional action needed
        }
        reviewScoreMapper.insert(reviewScore);
    }

    private void assertAppealAccessible(BizAppeal appeal) {
        if (!isAppealAccessible(appeal)) {
            throw new BusinessException(403, "无权访问该申诉记录");
        }
    }

    private boolean isAppealAccessible(BizAppeal appeal) {
        if (appeal == null) {
            return false;
        }
        String dataScope = DataScopeContext.getDataScope();
        if ("ALL".equals(dataScope)) {
            return true;
        }

        Long scopeId = DataScopeContext.getScopeId();
        if (scopeId == null || scopeId == 0L) {
            return false;
        }

        if ("ORG".equals(dataScope)) {
            return scopeId.equals(appeal.getAppealOrgId());
        }

        if ("UNIT".equals(dataScope)) {
            return DataScopeContext.getVisibleOrgIds().contains(appeal.getAppealOrgId());
        }

        return false;
    }
}
