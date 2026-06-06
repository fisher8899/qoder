package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.ExamProgressDetailVO;
import com.ccerphr.assessment.dto.ExamProgressVO;
import com.ccerphr.assessment.dto.ProgressQueryDTO;
import com.ccerphr.assessment.dto.UnfilledItemVO;
import com.ccerphr.assessment.entity.*;
import com.ccerphr.assessment.mapper.*;
import com.ccerphr.assessment.service.ExamProgressService;
import com.ccerphr.assessment.util.DataScopeFilter;
import com.ccerphr.assessment.util.ScoreCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ExamProgressServiceImpl implements ExamProgressService {

    private final BizExamGroupMapper examGroupMapper;
    private final BizExamGroupMemberMapper memberMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final BizSelfEvaluationMapper selfEvaluationMapper;
    private final BizPeerEvaluationMapper peerEvaluationMapper;
    private final BizReviewScoreMapper reviewScoreMapper;
    private final SysIndicatorCategoryMapper indicatorCategoryMapper;
    private final BizIndicatorOrgMapper indicatorOrgMapper;
    private final BizIndicatorLeaderMapper indicatorLeaderMapper;

    public ExamProgressServiceImpl(BizExamGroupMapper examGroupMapper,
                                   BizExamGroupMemberMapper memberMapper,
                                   BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                   BizSelfEvaluationMapper selfEvaluationMapper,
                                   BizPeerEvaluationMapper peerEvaluationMapper,
                                   BizReviewScoreMapper reviewScoreMapper,
                                   SysIndicatorCategoryMapper indicatorCategoryMapper,
                                   BizIndicatorOrgMapper indicatorOrgMapper,
                                   BizIndicatorLeaderMapper indicatorLeaderMapper) {
        this.examGroupMapper = examGroupMapper;
        this.memberMapper = memberMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.selfEvaluationMapper = selfEvaluationMapper;
        this.peerEvaluationMapper = peerEvaluationMapper;
        this.reviewScoreMapper = reviewScoreMapper;
        this.indicatorCategoryMapper = indicatorCategoryMapper;
        this.indicatorOrgMapper = indicatorOrgMapper;
        this.indicatorLeaderMapper = indicatorLeaderMapper;
    }

    @Override
    public List<ExamProgressVO> queryProgress(ProgressQueryDTO queryDTO) {
        Long examGroupId = queryDTO.getExamGroupId();

        // 查找月度考核组，用于解析关联的指标来源组
        BizExamGroup examGroup = examGroupMapper.selectById(examGroupId);

        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        if (queryDTO.getOrgId() != null) {
            memberWrapper.eq(BizExamGroupMember::getOrgId, queryDTO.getOrgId());
        }
        // 数据范围过滤：有 org_id 和 unit_id 字段，使用 applyFilter
        DataScopeFilter.applyFilter(memberWrapper, BizExamGroupMember::getUnitId, BizExamGroupMember::getOrgId);
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);

        List<ExamProgressVO> result = new ArrayList<>();
        for (BizExamGroupMember member : members) {
            ExamProgressVO vo = new ExamProgressVO();
            vo.setOrgId(member.getOrgId());
            vo.setOrgName(member.getOrgName());

            // 指标总数：从关联的 INDICATOR_SET 考核组中取已审批通过的指标
            List<Long> indicatorSourceGroupIds = resolveIndicatorSourceGroupIds(examGroup, member.getOrgId());
            long totalIndicator = 0;
            if (!indicatorSourceGroupIds.isEmpty()) {
                LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
                indWrapper.in(BizIndicatorDefinition::getExamGroupId, indicatorSourceGroupIds);
                indWrapper.eq(BizIndicatorDefinition::getOrgId, member.getOrgId());
                indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
                totalIndicator = indicatorDefinitionMapper.selectCount(indWrapper);
            }

            // 自评完成数
            LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
            selfWrapper.eq(BizSelfEvaluation::getOrgId, member.getOrgId());
            selfWrapper.eq(BizSelfEvaluation::getStatus, "SUBMITTED");
            long selfDone = selfEvaluationMapper.selectCount(selfWrapper);

            // 他评完成数（该部门作为打分方，已提交的记录数）
            LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
            peerWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
            peerWrapper.eq(BizPeerEvaluation::getEvaluatorOrgId, member.getOrgId());
            peerWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
            long peerDone = peerEvaluationMapper.selectCount(peerWrapper);

            // 复核记录数
            LambdaQueryWrapper<BizReviewScore> reviewWrapper = new LambdaQueryWrapper<>();
            reviewWrapper.eq(BizReviewScore::getExamGroupId, examGroupId);
            reviewWrapper.eq(BizReviewScore::getOrgId, member.getOrgId());
            long reviewDone = reviewScoreMapper.selectCount(reviewWrapper);

            BigDecimal selfRate = totalIndicator > 0
                    ? BigDecimal.valueOf(selfDone).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalIndicator), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal peerRate = totalIndicator > 0
                    ? BigDecimal.valueOf(peerDone).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalIndicator), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String reviewStatus = reviewDone > 0 ? "已复核" : "待复核";
            BigDecimal reviewProgress = reviewDone > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;

            BigDecimal overall = selfRate.add(peerRate).add(reviewProgress)
                    .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

            vo.setSelfEvalRate(selfRate);
            vo.setPeerEvalRate(peerRate);
            vo.setReviewStatus(reviewStatus);
            vo.setOverallProgress(overall);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<ExamProgressDetailVO> queryProgressDetail(Long examGroupId, Long orgId) {
        BizExamGroup examGroup = examGroupMapper.selectById(examGroupId);
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<BizExamGroupMember>()
                        .eq(BizExamGroupMember::getExamGroupId, examGroupId)
                        .eq(BizExamGroupMember::getOrgId, orgId)
        );
        if (memberCount == null || memberCount == 0) {
            throw new BusinessException(403, "考核部门不属于当前考核组");
        }

        LambdaQueryWrapper<BizExamGroupMember> scopeWrapper = new LambdaQueryWrapper<>();
        scopeWrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        scopeWrapper.eq(BizExamGroupMember::getOrgId, orgId);
        DataScopeFilter.applyFilter(scopeWrapper, BizExamGroupMember::getUnitId, BizExamGroupMember::getOrgId);
        Long visibleCount = memberMapper.selectCount(scopeWrapper);
        if (visibleCount == null || visibleCount == 0) {
            throw new BusinessException(403, "无权限查看该部门考核数据");
        }

        List<Long> indicatorSourceGroupIds = resolveIndicatorSourceGroupIds(examGroup, orgId);
        if (indicatorSourceGroupIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.in(BizIndicatorDefinition::getExamGroupId, indicatorSourceGroupIds);
        indWrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
        indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper).stream()
                .sorted(Comparator
                        .comparing((BizIndicatorDefinition item) -> item.getSortCode() == null ? Integer.MAX_VALUE : item.getSortCode())
                        .thenComparing(item -> item.getCategoryName() == null ? "" : item.getCategoryName())
                        .thenComparing(item -> item.getSubCategory() == null ? "" : item.getSubCategory())
                        .thenComparing(item -> item.getContent() == null ? "" : item.getContent()))
                .collect(Collectors.toList());
        if (indicators.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> categorySortMap = indicatorCategoryMapper.selectList(
                        new LambdaQueryWrapper<SysIndicatorCategory>()
                                .eq(SysIndicatorCategory::getIsEnabled, 1)
                )
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getCategoryName() == null ? "" : item.getCategoryName(),
                        item -> item.getSortCode() == null ? Integer.MAX_VALUE : item.getSortCode(),
                        (first, second) -> Math.min(first, second)
                ));
        indicators = indicators.stream()
                .sorted(Comparator
                        .comparing((BizIndicatorDefinition item) -> categorySortMap.getOrDefault(item.getCategoryName() == null ? "" : item.getCategoryName(), Integer.MAX_VALUE))
                        .thenComparing(item -> item.getCategoryName() == null ? "" : item.getCategoryName())
                        .thenComparing(item -> item.getSubCategory() == null ? "" : item.getSubCategory())
                        .thenComparing(item -> item.getContent() == null ? "" : item.getContent()))
                .collect(Collectors.toList());

        Map<Long, BizSelfEvaluation> selfEvalMap = selfEvaluationMapper
                .selectByExamGroupAndOrg(examGroupId, orgId)
                .stream()
                .collect(Collectors.toMap(BizSelfEvaluation::getIndicatorId, item -> item, (first, second) -> first));

        Map<Long, List<BizPeerEvaluation>> peerEvalMap = peerEvaluationMapper.selectList(
                        new LambdaQueryWrapper<BizPeerEvaluation>()
                                .select(
                                        BizPeerEvaluation::getIndicatorId,
                                        BizPeerEvaluation::getPeerScore,
                                        BizPeerEvaluation::getScoreComment
                                )
                                .eq(BizPeerEvaluation::getExamGroupId, examGroupId)
                                .eq(BizPeerEvaluation::getTargetOrgId, orgId)
                                .eq(BizPeerEvaluation::getStatus, "SUBMITTED")
                )
                .stream()
                .filter(item -> item.getIndicatorId() != null)
                .collect(Collectors.groupingBy(BizPeerEvaluation::getIndicatorId));

        Map<Long, BizReviewScore> reviewScoreMap = reviewScoreMapper.selectList(
                        new LambdaQueryWrapper<BizReviewScore>()
                                .eq(BizReviewScore::getExamGroupId, examGroupId)
                                .eq(BizReviewScore::getOrgId, orgId)
                )
                .stream()
                .filter(item -> item.getIndicatorId() != null)
                .collect(Collectors.toMap(BizReviewScore::getIndicatorId, item -> item, (first, second) -> first));

        List<ExamProgressDetailVO> result = new ArrayList<>();
        for (BizIndicatorDefinition indicator : indicators) {
            ExamProgressDetailVO vo = new ExamProgressDetailVO();
            vo.setIndicatorId(indicator.getId());
            vo.setCategoryId(indicator.getCategoryId());
            vo.setCategoryName(indicator.getCategoryName());
            vo.setSortCode(categorySortMap.getOrDefault(indicator.getCategoryName() == null ? "" : indicator.getCategoryName(), Integer.MAX_VALUE));
            vo.setSubCategory(indicator.getSubCategory());
            vo.setContent(indicator.getContent());
            vo.setTargetDesc(indicator.getTargetDesc());
            vo.setWeightAnnual(indicator.getWeightAnnual());
            vo.setWeightMonthly(indicator.getWeightMonthly());
            vo.setEvaluationStandard(indicator.getEvaluationStandard());

            BizSelfEvaluation selfEval = selfEvalMap.get(indicator.getId());
            if (selfEval != null) {
                vo.setSelfEvalId(selfEval.getId());
                vo.setActualCompletion(selfEval.getActualCompletion());
                vo.setSelfScore(selfEval.getSelfScore());
                vo.setSelfResult(selfEval.getSelfResult());
                vo.setAttachmentUrl(selfEval.getAttachmentUrl() == null ? "" : selfEval.getAttachmentUrl());
                vo.setAttachmentName(selfEval.getAttachmentName());
                vo.setAttachmentDownloadUrl("/api/evaluation/self/download/" + selfEval.getId());
                vo.setStatus(selfEval.getStatus());
            } else {
                vo.setActualCompletion("");
                vo.setAttachmentUrl("");
                vo.setAttachmentName("");
                vo.setAttachmentDownloadUrl("");
                vo.setStatus("PENDING");
            }

            List<BizPeerEvaluation> peers = peerEvalMap.getOrDefault(indicator.getId(), List.of());
            vo.setPeerResult(average(peers.stream()
                    .map(peer -> ScoreCalculator.calculateResult(peer.getPeerScore(), indicator))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())));
            vo.setPeerComment(peers.stream()
                    .map(BizPeerEvaluation::getScoreComment)
                    .filter(comment -> comment != null && !comment.isBlank())
                    .distinct()
                    .collect(Collectors.joining("；")));

            BizReviewScore reviewScore = reviewScoreMap.get(indicator.getId());
            if (reviewScore != null) {
                vo.setAdminScore(reviewScore.getAdminScore());
                vo.setAdjustComment(reviewScore.getScoreComment());
            } else {
                vo.setAdjustComment("");
            }

            result.add(vo);
        }
        return result;
    }

    @Override
    public List<UnfilledItemVO> queryUnfilledItems(Long examGroupId, Long orgId) {
        List<UnfilledItemVO> result = new ArrayList<>();

        // 查找月度考核组，用于解析关联的指标来源组
        BizExamGroup examGroup = examGroupMapper.selectById(examGroupId);

        // 从关联的 INDICATOR_SET 考核组中取已审批通过的指标
        List<Long> indicatorSourceGroupIds = resolveIndicatorSourceGroupIds(examGroup, orgId);
        if (indicatorSourceGroupIds.isEmpty()) {
            return result;
        }

        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.in(BizIndicatorDefinition::getExamGroupId, indicatorSourceGroupIds);
        indWrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
        indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper);

        if (indicators.isEmpty()) {
            return result;
        }

        // 批量查询所有指标的自评、他评、复核记录
        List<Long> indicatorIds = indicators.stream().map(BizIndicatorDefinition::getId).collect(Collectors.toList());

        Map<Long, Long> selfCountMap = selfEvaluationMapper.selectList(
                new LambdaQueryWrapper<BizSelfEvaluation>()
                        .in(BizSelfEvaluation::getIndicatorId, indicatorIds)
                        .eq(BizSelfEvaluation::getExamGroupId, examGroupId)
                        .eq(BizSelfEvaluation::getOrgId, orgId)
                        .eq(BizSelfEvaluation::getStatus, "SUBMITTED"))
                .stream()
                .collect(Collectors.groupingBy(BizSelfEvaluation::getIndicatorId, Collectors.counting()));

        Map<Long, Long> peerCountMap = peerEvaluationMapper.selectList(
                new LambdaQueryWrapper<BizPeerEvaluation>()
                        .in(BizPeerEvaluation::getIndicatorId, indicatorIds)
                        .eq(BizPeerEvaluation::getExamGroupId, examGroupId)
                        .eq(BizPeerEvaluation::getTargetOrgId, orgId)
                        .eq(BizPeerEvaluation::getStatus, "SUBMITTED"))
                .stream()
                .collect(Collectors.groupingBy(BizPeerEvaluation::getIndicatorId, Collectors.counting()));

        Map<Long, Long> reviewCountMap = reviewScoreMapper.selectList(
                new LambdaQueryWrapper<BizReviewScore>()
                        .in(BizReviewScore::getIndicatorId, indicatorIds)
                        .eq(BizReviewScore::getExamGroupId, examGroupId)
                        .eq(BizReviewScore::getOrgId, orgId))
                .stream()
                .collect(Collectors.groupingBy(BizReviewScore::getIndicatorId, Collectors.counting()));

        for (BizIndicatorDefinition ind : indicators) {
            // 检查自评是否完成
            Long selfCount = selfCountMap.getOrDefault(ind.getId(), 0L);
            if (selfCount == 0) {
                UnfilledItemVO vo = new UnfilledItemVO();
                vo.setIndicatorName(ind.getContent());
                vo.setStage("自评");
                fillEvaluateTargetName(vo, ind);
                result.add(vo);
                continue;
            }

            // 检查他评是否完成
            Long peerCount = peerCountMap.getOrDefault(ind.getId(), 0L);
            if (peerCount == 0) {
                UnfilledItemVO vo = new UnfilledItemVO();
                vo.setIndicatorName(ind.getContent());
                vo.setStage("他评");
                fillEvaluateTargetName(vo, ind);
                result.add(vo);
                continue;
            }

            // 检查复核是否完成
            Long reviewCount = reviewCountMap.getOrDefault(ind.getId(), 0L);
            if (reviewCount == 0) {
                UnfilledItemVO vo = new UnfilledItemVO();
                vo.setIndicatorName(ind.getContent());
                vo.setStage("复核");
                fillEvaluateTargetName(vo, ind);
                result.add(vo);
            }
        }

        return result;
    }

    private void fillEvaluateTargetName(UnfilledItemVO vo, BizIndicatorDefinition indicator) {
        String targetName;
        if ("LEADER".equals(indicator.getExamTargetType())) {
            targetName = indicatorLeaderMapper.selectByIndicatorId(indicator.getId()).stream()
                    .map(BizIndicatorLeader::getLeaderName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining("、"));
            if (targetName.isBlank()) {
                targetName = indicator.getLeaderName();
            }
        } else {
            targetName = indicatorOrgMapper.selectByIndicatorId(indicator.getId()).stream()
                    .map(BizIndicatorOrg::getOrgName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining("、"));
            if (targetName.isBlank()) {
                targetName = indicator.getOrgName();
            }
        }
        vo.setEvaluateTargetName(targetName == null || targetName.isBlank() ? "-" : targetName);
        vo.setOrgName(vo.getEvaluateTargetName());
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 根据月度考核组，找到同年同单位的 INDICATOR_SET（业绩指标设定）考核组。
     * 优先返回该部门已有审批通过指标的组；若都没有指标，返回所有候选组。
     */
    private List<Long> resolveIndicatorSourceGroupIds(BizExamGroup monthlyGroup, Long orgId) {
        if (monthlyGroup == null || monthlyGroup.getStartDate() == null || monthlyGroup.getEndDate() == null) {
            return List.of();
        }

        int year = monthlyGroup.getStartDate().getYear();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        LambdaQueryWrapper<BizExamGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroup::getUnitId, monthlyGroup.getUnitId());
        wrapper.eq(BizExamGroup::getExamCategory, "INDICATOR_SET");
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
}
