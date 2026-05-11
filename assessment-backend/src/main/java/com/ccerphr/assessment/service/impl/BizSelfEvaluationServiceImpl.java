package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.dto.SelfEvalQueryDTO;
import com.ccerphr.assessment.dto.SelfEvalSaveDTO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizSelfEvaluation;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.mapper.BizExamGroupMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizSelfEvaluationMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.service.BizSelfEvaluationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BizSelfEvaluationServiceImpl extends ServiceImpl<BizSelfEvaluationMapper, BizSelfEvaluation> implements BizSelfEvaluationService {

    private final BizExamGroupMapper examGroupMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final SysOrganizationMapper organizationMapper;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    public BizSelfEvaluationServiceImpl(BizExamGroupMapper examGroupMapper,
                                        BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                        SysOrganizationMapper organizationMapper) {
        this.examGroupMapper = examGroupMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.organizationMapper = organizationMapper;
    }

    @Override
    public List<Map<String, Object>> getTaskList(SelfEvalQueryDTO queryDTO) {
        Long orgId = queryDTO.getOrgId();
        List<BizExamGroup> groups;
        if (queryDTO.getExamGroupId() != null) {
            BizExamGroup group = examGroupMapper.selectById(queryDTO.getExamGroupId());
            groups = group != null ? List.of(group) : List.of();
        } else {
            LambdaQueryWrapper<BizExamGroup> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizExamGroup::getExamType, "MONTHLY");
            // 数据范围过滤
            DataScopeFilter.applyUnitFilter(wrapper, BizExamGroup::getUnitId);
            wrapper.orderByDesc(BizExamGroup::getCreatedTime);
            groups = examGroupMapper.selectList(wrapper);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroup group : groups) {
            Map<String, Object> map = new HashMap<>();
            map.put("examGroupId", group.getId());
            map.put("groupName", group.getGroupName());
            map.put("examType", group.getExamType());
            map.put("startDate", group.getStartDate());
            map.put("endDate", group.getEndDate());

            // 统计指标数和自评情况
            LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(BizIndicatorDefinition::getExamGroupId, group.getId());
            indWrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
            indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
            long totalIndicators = indicatorDefinitionMapper.selectCount(indWrapper);

            LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(BizSelfEvaluation::getExamGroupId, group.getId());
            selfWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
            selfWrapper.ne(BizSelfEvaluation::getStatus, "DRAFT");
            long evaluatedCount = count(selfWrapper);

            // 如果数据库中没有自评记录但有指标，说明都未评
            long actualEvaluated = evaluatedCount;
            if (actualEvaluated == 0 && totalIndicators > 0) {
                // 检查是否有草稿
                LambdaQueryWrapper<BizSelfEvaluation> draftWrapper = new LambdaQueryWrapper<>();
                draftWrapper.eq(BizSelfEvaluation::getExamGroupId, group.getId());
                draftWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
                draftWrapper.eq(BizSelfEvaluation::getStatus, "DRAFT");
                long draftCount = count(draftWrapper);
                if (draftCount > 0) {
                    actualEvaluated = draftCount;
                }
            }

            String status = actualEvaluated >= totalIndicators && totalIndicators > 0 ? "SUBMITTED" : "PENDING";
            if (StringUtils.hasText(queryDTO.getStatus()) && !queryDTO.getStatus().equals(status)) {
                continue;
            }

            map.put("status", status);
            map.put("totalIndicators", (int) totalIndicators);
            map.put("evaluatedCount", (int) actualEvaluated);
            int progress = totalIndicators > 0 ? (int) ((actualEvaluated * 100) / totalIndicators) : 0;
            map.put("progress", progress);
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getIndicators(Long examGroupId, Long orgId) {
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectByExamGroupAndOrg(examGroupId, orgId);
        List<BizSelfEvaluation> selfEvals = getBaseMapper().selectByExamGroupAndOrg(examGroupId, orgId);
        Map<Long, BizSelfEvaluation> evalMap = new HashMap<>();
        for (BizSelfEvaluation eval : selfEvals) {
            evalMap.put(eval.getIndicatorId(), eval);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizIndicatorDefinition ind : indicators) {
            Map<String, Object> map = new HashMap<>();
            map.put("indicatorId", ind.getId());
            map.put("categoryName", ind.getCategoryName());
            map.put("subCategory", ind.getSubCategory());
            map.put("content", ind.getContent());
            map.put("targetDesc", ind.getTargetDesc());
            map.put("weightAnnual", ind.getWeightAnnual());
            map.put("weightMonthly", ind.getWeightMonthly());
            map.put("evaluationStandard", ind.getEvaluationStandard());

            BizSelfEvaluation eval = evalMap.get(ind.getId());
            if (eval != null) {
                map.put("selfEvalId", eval.getId());
                map.put("actualCompletion", eval.getActualCompletion());
                map.put("selfScore", eval.getSelfScore());
                map.put("selfResult", eval.getSelfResult());
                map.put("attachmentUrl", eval.getAttachmentUrl());
                map.put("attachmentName", eval.getAttachmentName());
                map.put("status", eval.getStatus());
            } else {
                map.put("selfEvalId", null);
                map.put("actualCompletion", "");
                map.put("selfScore", null);
                map.put("selfResult", null);
                map.put("attachmentUrl", "");
                map.put("attachmentName", "");
                map.put("status", "PENDING");
            }
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSelfEval(SelfEvalSaveDTO dto) {
        BizSelfEvaluation entity;
        if (dto.getId() != null) {
            entity = getById(dto.getId());
            if (entity == null) {
                throw new BusinessException("自评记录不存在");
            }
            if ("SUBMITTED".equals(entity.getStatus())) {
                throw new BusinessException("已提交的自评无法修改");
            }
        } else {
            // 检查是否已存在
            LambdaQueryWrapper<BizSelfEvaluation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizSelfEvaluation::getExamGroupId, dto.getExamGroupId());
            wrapper.eq(BizSelfEvaluation::getOrgId, dto.getOrgId());
            wrapper.eq(BizSelfEvaluation::getIndicatorId, dto.getIndicatorId());
            entity = getOne(wrapper);
            if (entity == null) {
                entity = new BizSelfEvaluation();
                entity.setExamGroupId(dto.getExamGroupId());
                entity.setOrgId(dto.getOrgId());
                entity.setIndicatorId(dto.getIndicatorId());
                entity.setStatus("DRAFT");
                entity.setCreatedTime(LocalDateTime.now());
            } else if ("SUBMITTED".equals(entity.getStatus())) {
                throw new BusinessException("已提交的自评无法修改");
            }
        }

        entity.setActualCompletion(dto.getActualCompletion());
        entity.setSelfScore(dto.getSelfScore());

        // 自动计算自评结果
        if (dto.getSelfScore() != null) {
            BizIndicatorDefinition indicator = indicatorDefinitionMapper.selectById(dto.getIndicatorId());
            if (indicator != null && indicator.getWeightMonthly() != null) {
                BigDecimal result = dto.getSelfScore()
                        .multiply(indicator.getWeightMonthly())
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                entity.setSelfResult(result);
            } else {
                entity.setSelfResult(dto.getSelfScore());
            }
        } else {
            entity.setSelfResult(null);
        }

        if (StringUtils.hasText(dto.getAttachmentUrl())) {
            entity.setAttachmentUrl(dto.getAttachmentUrl());
        }
        if (StringUtils.hasText(dto.getAttachmentName())) {
            entity.setAttachmentName(dto.getAttachmentName());
        }

        entity.setUpdatedTime(LocalDateTime.now());
        saveOrUpdate(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitSelfEval(Long examGroupId, Long orgId, String submittedBy) {
        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
        indWrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
        indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        long totalIndicators = indicatorDefinitionMapper.selectCount(indWrapper);

        LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
        selfWrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
        selfWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
        List<BizSelfEvaluation> list = list(selfWrapper);

        if (list.size() < totalIndicators) {
            throw new BusinessException("还有指标未完成自评，无法提交");
        }

        for (BizSelfEvaluation eval : list) {
            if (eval.getSelfScore() == null) {
                throw new BusinessException("还有指标未填写自评得分，无法提交");
            }
            eval.setStatus("SUBMITTED");
            eval.setSubmittedBy(submittedBy);
            eval.setSubmittedTime(LocalDateTime.now());
            eval.setUpdatedTime(LocalDateTime.now());
            updateById(eval);
        }
    }

    @Override
    public String uploadAttachment(byte[] fileData, String originalFilename) {
        try {
            Path dir = Paths.get(uploadPath, "self-eval");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String ext = "";
            int idx = originalFilename.lastIndexOf('.');
            if (idx > 0) {
                ext = originalFilename.substring(idx);
            }
            String fileName = UUID.randomUUID() + ext;
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, fileData);
            return "/uploads/self-eval/" + fileName;
        } catch (IOException e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }
}
