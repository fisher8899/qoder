package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.SelfEvalQueryDTO;
import com.ccerphr.assessment.dto.SelfEvalSaveDTO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizSelfEvaluation;
import com.ccerphr.assessment.entity.SysIndicatorCategory;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.mapper.BizExamGroupMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizSelfEvaluationMapper;
import com.ccerphr.assessment.mapper.SysIndicatorCategoryMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.service.BizSelfEvaluationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import com.ccerphr.assessment.util.ScoreCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BizSelfEvaluationServiceImpl extends ServiceImpl<BizSelfEvaluationMapper, BizSelfEvaluation>
        implements BizSelfEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(BizSelfEvaluationServiceImpl.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".doc", ".docx", ".wps", ".xls", ".xlsx", ".zip", ".rar", ".7z"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-works",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed"
    ));

    private final BizExamGroupMapper examGroupMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final SysIndicatorCategoryMapper categoryMapper;
    private final SysOrganizationMapper organizationMapper;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    public BizSelfEvaluationServiceImpl(BizExamGroupMapper examGroupMapper,
                                        BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                        SysIndicatorCategoryMapper categoryMapper,
                                        SysOrganizationMapper organizationMapper) {
        this.examGroupMapper = examGroupMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.categoryMapper = categoryMapper;
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

            List<Long> indicatorSourceGroupIds = resolveAnnualIndicatorSourceGroupIds(group, orgId);
            long totalIndicators = countApprovedIndicators(indicatorSourceGroupIds, orgId);

            LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(BizSelfEvaluation::getExamGroupId, group.getId());
            selfWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
            selfWrapper.ne(BizSelfEvaluation::getStatus, "DRAFT");
            long submittedCount = count(selfWrapper);

            LambdaQueryWrapper<BizSelfEvaluation> draftWrapper = new LambdaQueryWrapper<>();
            draftWrapper.eq(BizSelfEvaluation::getExamGroupId, group.getId());
            draftWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
            draftWrapper.eq(BizSelfEvaluation::getStatus, "DRAFT");
            long draftCount = count(draftWrapper);

            // 任务状态：只有当所有指标都有非DRAFT记录时才为SUBMITTED，否则为PENDING
            String status = submittedCount >= totalIndicators && totalIndicators > 0 ? "SUBMITTED" : "PENDING";
            // 进度显示：优先用已提交数，如果没有则用草稿数展示进度
            long actualEvaluated = submittedCount > 0 ? submittedCount : draftCount;
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
        BizExamGroup monthlyGroup = requireExamGroup(examGroupId);
        List<Long> indicatorSourceGroupIds = resolveAnnualIndicatorSourceGroupIds(monthlyGroup, orgId);

        List<BizIndicatorDefinition> indicators = loadApprovedIndicators(indicatorSourceGroupIds, orgId);
        List<BizSelfEvaluation> selfEvals = getBaseMapper().selectByExamGroupAndOrg(examGroupId, orgId);
        Map<Long, BizSelfEvaluation> evalMap = new HashMap<>();
        for (BizSelfEvaluation eval : selfEvals) {
            evalMap.put(eval.getIndicatorId(), eval);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizIndicatorDefinition ind : indicators) {
            Map<String, Object> map = new HashMap<>();
            map.put("indicatorId", ind.getId());
            map.put("categoryId", ind.getCategoryId());
            map.put("categoryName", ind.getCategoryName());
            map.put("sortCode", ind.getSortCode());
            map.put("subCategory", ind.getSubCategory());
            map.put("content", ind.getContent());
            map.put("targetDesc", ind.getTargetDesc());
            map.put("weightAnnual", ind.getWeightAnnual());
            map.put("weightMonthly", ind.getWeightMonthly());
            map.put("evaluationStandard", ind.getEvaluationStandard());

            BizSelfEvaluation eval = evalMap.get(ind.getId());
            if (eval != null) {
                String attachmentKey = normalizeStoredAttachmentKey(eval.getAttachmentUrl());
                map.put("selfEvalId", eval.getId());
                map.put("actualCompletion", eval.getActualCompletion());
                map.put("selfScore", eval.getSelfScore());
                map.put("selfResult", eval.getSelfResult());
                map.put("attachmentUrl", attachmentKey == null ? "" : attachmentKey);
                map.put("attachmentName", eval.getAttachmentName());
                map.put("attachmentDownloadUrl", buildDownloadUrlById(eval.getId()));
                map.put("status", eval.getStatus());
            } else {
                map.put("selfEvalId", null);
                map.put("actualCompletion", "");
                map.put("selfScore", null);
                map.put("selfResult", null);
                map.put("attachmentUrl", "");
                map.put("attachmentName", "");
                map.put("attachmentDownloadUrl", "");
                map.put("status", "PENDING");
            }
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSelfEval(SelfEvalSaveDTO dto) {
        BizSelfEvaluation entity;
        if (dto.getId() != null) {
            entity = getById(dto.getId());
            if (entity == null) {
                throw new BusinessException("Self evaluation record not found");
            }
            validateOrgAccess(entity.getOrgId());
            ensureSameSelfEvalTarget(entity, dto);
            if ("SUBMITTED".equals(entity.getStatus())) {
                throw new BusinessException("Submitted self evaluation cannot be modified");
            }
        } else {
            validateOrgAccess(dto.getOrgId());
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
                throw new BusinessException("Submitted self evaluation cannot be modified");
            }
        }

        entity.setActualCompletion(dto.getActualCompletion());
        entity.setSelfScore(dto.getSelfScore());

        // 优先使用前端传入的自评结果，否则通过通用计算规则自动计算
        if (dto.getSelfResult() != null) {
            entity.setSelfResult(dto.getSelfResult());
        } else {
            BizIndicatorDefinition indicator = indicatorDefinitionMapper.selectById(dto.getIndicatorId());
            entity.setSelfResult(ScoreCalculator.calculateResult(dto.getSelfScore(), indicator));
        }

        entity.setAttachmentUrl(StringUtils.hasText(dto.getAttachmentUrl())
                ? normalizeAttachmentKey(dto.getAttachmentUrl())
                : null);
        entity.setAttachmentName(StringUtils.hasText(dto.getAttachmentName()) ? dto.getAttachmentName() : null);
        entity.setUpdatedTime(LocalDateTime.now());
        saveOrUpdate(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitSelfEval(Long examGroupId, Long orgId, String submittedBy) {
        validateOrgAccess(orgId);

        BizExamGroup monthlyGroup = requireExamGroup(examGroupId);
        List<Long> indicatorSourceGroupIds = resolveAnnualIndicatorSourceGroupIds(monthlyGroup, orgId);
        long totalIndicators = countApprovedIndicators(indicatorSourceGroupIds, orgId);

        LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
        selfWrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
        selfWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
        List<BizSelfEvaluation> list = list(selfWrapper);

        if (list.size() < totalIndicators) {
            throw new BusinessException("There are unfinished indicators, submission is not allowed");
        }

        for (BizSelfEvaluation eval : list) {
            if (eval.getSelfScore() == null) {
                throw new BusinessException("There are indicators without self score, submission is not allowed");
            }
            eval.setStatus("SUBMITTED");
            eval.setSubmittedBy(submittedBy);
            eval.setSubmittedTime(LocalDateTime.now());
            eval.setUpdatedTime(LocalDateTime.now());
            updateById(eval);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawSelfEval(Long examGroupId, Long orgId) {
        log.info("withdrawSelfEval called: examGroupId={}, orgId={}", examGroupId, orgId);
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        log.info("Current DataScope: dataScope={}, scopeId={}", dataScope, scopeId);
        validateOrgAccess(orgId);

        LambdaQueryWrapper<BizSelfEvaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
        wrapper.eq(BizSelfEvaluation::getOrgId, orgId);
        wrapper.eq(BizSelfEvaluation::getStatus, "SUBMITTED");
        List<BizSelfEvaluation> list = list(wrapper);
        log.info("Found {} submitted self evaluations for withdraw", list.size());

        if (list.isEmpty()) {
            throw new BusinessException("当前自评未提交，无法撤回");
        }

        for (BizSelfEvaluation eval : list) {
            eval.setStatus("DRAFT");
            eval.setSubmittedBy(null);
            eval.setSubmittedTime(null);
            eval.setUpdatedTime(LocalDateTime.now());
            updateById(eval);
        }
    }

    @Override
    public String uploadAttachment(byte[] fileData, String originalFilename) {
        try {
            String extension = resolveExtension(originalFilename);
            validateAttachment(fileData, originalFilename, extension);

            Path dir = getAttachmentDirectory();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String fileName = UUID.randomUUID() + extension;
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, fileData);
            return fileName;
        } catch (IOException e) {
            throw new BusinessException("附件上传失败: " + e.getMessage());
        }
    }

    @Override
    public Path resolveAttachmentForDownloadById(Long id) {
        BizSelfEvaluation evaluation = getById(id);
        if (evaluation == null || !StringUtils.hasText(evaluation.getAttachmentUrl())) {
            throw new BusinessException(404, "附件不存在");
        }
        validateOrgAccess(evaluation.getOrgId());

        String normalizedKey = normalizeAttachmentKey(evaluation.getAttachmentUrl());
        Path directory = getAttachmentDirectory();
        Path filePath = directory.resolve(normalizedKey).normalize();
        if (!filePath.startsWith(directory)) {
            throw new BusinessException(400, "非法附件路径");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "附件不存在");
        }
        return filePath;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long id) {
        BizSelfEvaluation evaluation = getById(id);
        if (evaluation == null) {
            throw new BusinessException(404, "记录不存在");
        }
        validateOrgAccess(evaluation.getOrgId());

        String attachmentUrl = evaluation.getAttachmentUrl();
        if (StringUtils.hasText(attachmentUrl)) {
            try {
                String fileName = normalizeAttachmentKey(attachmentUrl);
                Path directory = getAttachmentDirectory();
                Path filePath = directory.resolve(fileName).normalize();
                if (filePath.startsWith(directory)) {
                    Files.deleteIfExists(filePath);
                }
            } catch (IOException ignored) {
                log.warn("Failed to delete self-evaluation attachment, id={}, file={}", id, attachmentUrl, ignored);
            }
        }

        evaluation.setAttachmentUrl(null);
        evaluation.setAttachmentName(null);
        evaluation.setUpdatedTime(LocalDateTime.now());
        updateById(evaluation);
    }

    private void validateOrgAccess(Long targetOrgId) {
        if (targetOrgId == null) {
            return;
        }
        String dataScope = DataScopeContext.getDataScope();
        if ("ALL".equals(dataScope)) {
            return;
        }
        Long scopeId = DataScopeContext.getScopeId();
        if (scopeId == null) {
            throw new BusinessException(403, "No permission to operate this organization data");
        }
        if ("ORG".equals(dataScope)) {
            if (!targetOrgId.equals(scopeId)) {
                throw new BusinessException(403,
                        "No permission to operate this organization data, allowed scope: "
                                + DataScopeContext.get().getScopeName());
            }
            return;
        }
        if ("UNIT".equals(dataScope)) {
            SysOrganization org = organizationMapper.selectById(targetOrgId);
            if (org == null || !DataScopeContext.getVisibleUnitIds().contains(org.getUnitId())) {
                throw new BusinessException(403, "Target organization is outside your unit scope");
            }
        }
    }

    private void validateAttachment(byte[] fileData, String originalFilename, String extension) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException("附件名称不能为空");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的附件类型: " + extension);
        }
        String detectedContentType = detectContentType(fileData);
        if (detectedContentType == null || !ALLOWED_CONTENT_TYPES.contains(detectedContentType)) {
            throw new BusinessException("不支持的附件内容类型");
        }
        if (!isExtensionCompatible(extension, detectedContentType)) {
            throw new BusinessException("附件扩展名与内容类型不匹配");
        }
        
        // 额外校验一些高危类型
        if (extension.equals(".pdf") && !startsWith(fileData, new byte[]{0x25, 0x50, 0x44, 0x46})) {
            throw new BusinessException("非法的 PDF 文件内容");
        }
    }

    private String detectContentType(byte[] fileData) {
        if (startsWith(fileData, new byte[]{0x25, 0x50, 0x44, 0x46})) {
            return "application/pdf";
        }
        if (startsWith(fileData, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "image/jpeg";
        }
        if (startsWith(fileData, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})) {
            return "image/png";
        }
        if (startsWith(fileData, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(fileData, new byte[]{0x50, 0x4B, 0x05, 0x06})
                || startsWith(fileData, new byte[]{0x50, 0x4B, 0x07, 0x08})) {
            return "application/zip";
        }
        if (startsWith(fileData, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0})) {
            return "application/msword";
        }
        if (startsWith(fileData, new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00})
                || startsWith(fileData, new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00})) {
            return "application/x-rar-compressed";
        }
        if (startsWith(fileData, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C})) {
            return "application/x-7z-compressed";
        }
        return null;
    }

    private boolean isExtensionCompatible(String extension, String contentType) {
        return switch (extension) {
            case ".pdf" -> "application/pdf".equals(contentType);
            case ".jpg", ".jpeg" -> "image/jpeg".equals(contentType);
            case ".png" -> "image/png".equals(contentType);
            case ".doc", ".wps" -> "application/msword".equals(contentType)
                    || "application/vnd.ms-works".equals(contentType);
            case ".docx" -> "application/zip".equals(contentType)
                    || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType);
            case ".xls" -> "application/msword".equals(contentType)
                    || "application/vnd.ms-excel".equals(contentType);
            case ".xlsx" -> "application/zip".equals(contentType)
                    || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType);
            case ".zip" -> "application/zip".equals(contentType)
                    || "application/x-zip-compressed".equals(contentType);
            case ".rar" -> "application/x-rar-compressed".equals(contentType)
                    || "application/vnd.rar".equals(contentType);
            case ".7z" -> "application/x-7z-compressed".equals(contentType);
            default -> false;
        };
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source == null || prefix == null || source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException("附件名称不能为空");
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0) {
            throw new BusinessException("附件缺少扩展名");
        }
        return originalFilename.substring(index).toLowerCase();
    }

    private Path getAttachmentDirectory() {
        return Paths.get(uploadPath, "self-eval").toAbsolutePath().normalize();
    }

    private String buildDownloadUrlById(Long id) {
        return id != null ? "/api/evaluation/self/download/" + id : "";
    }

    private String buildLegacyPublicPath(String attachmentKey) {
        return "/uploads/self-eval/" + attachmentKey;
    }

    private String normalizeStoredAttachmentKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return normalizeAttachmentKey(value);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private String normalizeAttachmentKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("附件标识不能为空");
        }
        String normalized = value.trim().replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        if (!StringUtils.hasText(fileName)
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || !fileName.matches("[A-Za-z0-9._-]{1,120}")) {
            throw new BusinessException("非法附件标识");
        }
        String extension = resolveExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的附件类型: " + extension);
        }
        return fileName;
    }

    private void ensureSameSelfEvalTarget(BizSelfEvaluation entity, SelfEvalSaveDTO dto) {
        if (!entity.getExamGroupId().equals(dto.getExamGroupId())
                || !entity.getOrgId().equals(dto.getOrgId())
                || !entity.getIndicatorId().equals(dto.getIndicatorId())) {
            throw new BusinessException(403, "无权限修改该自评记录");
        }
    }

    private BizExamGroup requireExamGroup(Long examGroupId) {
        BizExamGroup group = examGroupMapper.selectById(examGroupId);
        if (group == null || group.getDeleted() != null && group.getDeleted() != 0) {
            throw new BusinessException(404, "考核组不存在");
        }
        return group;
    }

    /**
     * 找到月度自评对应的"业绩指标设定"考核组：
     * 同单位下、examCategory=INDICATOR_SET、考核区间与月度组所在年度有重叠的考核组。
     * 优先返回本部门已有审批通过指标的考核组；若都没有指标，仍把候选组返回，便于后续统计。
     */
    private List<Long> resolveAnnualIndicatorSourceGroupIds(BizExamGroup monthlyGroup, Long orgId) {
        if (monthlyGroup == null || monthlyGroup.getStartDate() == null || monthlyGroup.getEndDate() == null) {
            return List.of();
        }

        int year = monthlyGroup.getStartDate().getYear();
        java.time.LocalDate yearStart = java.time.LocalDate.of(year, 1, 1);
        java.time.LocalDate yearEnd = java.time.LocalDate.of(year, 12, 31);

        LambdaQueryWrapper<BizExamGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroup::getUnitId, monthlyGroup.getUnitId());
        wrapper.eq(BizExamGroup::getExamCategory, "INDICATOR_SET");
        // 与目标年度有交集：startDate <= yearEnd AND endDate >= yearStart
        wrapper.le(BizExamGroup::getStartDate, yearEnd);
        wrapper.ge(BizExamGroup::getEndDate, yearStart);
        wrapper.orderByDesc(BizExamGroup::getStartDate);
        List<BizExamGroup> candidates = examGroupMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> withApproved = new ArrayList<>();
        List<Long> allIds = new ArrayList<>();
        for (BizExamGroup candidate : candidates) {
            allIds.add(candidate.getId());
            Long approvedCount = indicatorDefinitionMapper.selectCount(
                    new LambdaQueryWrapper<BizIndicatorDefinition>()
                            .eq(BizIndicatorDefinition::getExamGroupId, candidate.getId())
                            .eq(BizIndicatorDefinition::getOrgId, orgId)
                            .eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED")
            );
            if (approvedCount != null && approvedCount > 0) {
                withApproved.add(candidate.getId());
            }
        }
        return withApproved.isEmpty() ? allIds : withApproved;
    }

    private long countApprovedIndicators(List<Long> sourceGroupIds, Long orgId) {
        if (sourceGroupIds == null || sourceGroupIds.isEmpty()) {
            return 0L;
        }
        LambdaQueryWrapper<BizIndicatorDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizIndicatorDefinition::getExamGroupId, sourceGroupIds);
        wrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
        wrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        Long count = indicatorDefinitionMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private List<BizIndicatorDefinition> loadApprovedIndicators(List<Long> sourceGroupIds, Long orgId) {
        if (sourceGroupIds == null || sourceGroupIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<BizIndicatorDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizIndicatorDefinition::getExamGroupId, sourceGroupIds);
        wrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
        wrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(wrapper);

        // 按指标大类的 sortCode 排序，大类内再按指标自身的 sortCode 排序
        if (!indicators.isEmpty()) {
            Set<Long> categoryIds = new java.util.HashSet<>();
            for (BizIndicatorDefinition ind : indicators) {
                if (ind.getCategoryId() != null) {
                    categoryIds.add(ind.getCategoryId());
                }
            }
            Map<Long, Integer> categorySortMap = new HashMap<>();
            if (!categoryIds.isEmpty()) {
                List<SysIndicatorCategory> categories = categoryMapper.selectBatchIds(categoryIds);
                for (SysIndicatorCategory cat : categories) {
                    categorySortMap.put(cat.getId(), cat.getSortCode() != null ? cat.getSortCode() : Integer.MAX_VALUE);
                }
            }
            indicators.sort(Comparator
                    .comparingInt((BizIndicatorDefinition ind) ->
                            categorySortMap.getOrDefault(ind.getCategoryId(), Integer.MAX_VALUE))
                    .thenComparingInt(ind -> ind.getSortCode() != null ? ind.getSortCode() : Integer.MAX_VALUE));
        }
        return indicators;
    }
}
