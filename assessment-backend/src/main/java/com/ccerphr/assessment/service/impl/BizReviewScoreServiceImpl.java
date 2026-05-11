package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.dto.ReviewQueryDTO;
import com.ccerphr.assessment.dto.ReviewScoreBatchDTO;
import com.ccerphr.assessment.dto.ReviewScoreSaveDTO;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.entity.BizPeerEvaluation;
import com.ccerphr.assessment.entity.BizReviewScore;
import com.ccerphr.assessment.mapper.BizExamGroupMemberMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.BizPeerEvaluationMapper;
import com.ccerphr.assessment.mapper.BizReviewScoreMapper;
import com.ccerphr.assessment.service.BizReviewScoreService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BizReviewScoreServiceImpl extends ServiceImpl<BizReviewScoreMapper, BizReviewScore> implements BizReviewScoreService {

    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final BizPeerEvaluationMapper peerEvaluationMapper;
    private final BizExamGroupMemberMapper memberMapper;

    public BizReviewScoreServiceImpl(BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                      BizPeerEvaluationMapper peerEvaluationMapper,
                                      BizExamGroupMemberMapper memberMapper) {
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.peerEvaluationMapper = peerEvaluationMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public List<Map<String, Object>> getReviewList(ReviewQueryDTO queryDTO) {
        Long examGroupId = queryDTO.getExamGroupId();
        if (examGroupId == null) {
            return new ArrayList<>();
        }

        // 获取考核组成员
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);

        // 构建orgId过滤
        List<Long> orgIds = new ArrayList<>();
        if (queryDTO.getOrgId() != null) {
            orgIds.add(queryDTO.getOrgId());
        } else {
            for (BizExamGroupMember member : members) {
                orgIds.add(member.getOrgId());
            }
        }

        // 获取所有他评记录（该考核组下，状态为SUBMITTED）
        LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
        peerWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
        peerWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
        // 数据范围过滤
        DataScopeFilter.applyUnitFilter(peerWrapper, BizPeerEvaluation::getUnitId);
        List<BizPeerEvaluation> peerEvals = peerEvaluationMapper.selectList(peerWrapper);

        // 按orgId+indicatorId分组计算平均分
        Map<String, List<BigDecimal>> peerScoreMap = new HashMap<>();
        for (BizPeerEvaluation eval : peerEvals) {
            String key = eval.getTargetOrgId() + "-" + eval.getIndicatorId();
            peerScoreMap.computeIfAbsent(key, k -> new ArrayList<>()).add(eval.getPeerScore());
        }

        // 获取已有的复核记录
        List<BizReviewScore> reviewScores = getBaseMapper().selectByExamGroupId(examGroupId);
        Map<String, BizReviewScore> reviewMap = new HashMap<>();
        for (BizReviewScore rs : reviewScores) {
            String key = rs.getOrgId() + "-" + rs.getIndicatorId();
            reviewMap.put(key, rs);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long orgId : orgIds) {
            LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
            indWrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
            indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
            if (queryDTO.getCategoryId() != null) {
                indWrapper.eq(BizIndicatorDefinition::getCategoryId, queryDTO.getCategoryId());
            }
            indWrapper.orderByAsc(BizIndicatorDefinition::getSortCode);
            List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper);

            for (BizIndicatorDefinition ind : indicators) {
                Map<String, Object> map = new HashMap<>();
                map.put("examGroupId", examGroupId);
                map.put("orgId", orgId);
                map.put("orgName", ind.getOrgName());
                map.put("indicatorId", ind.getId());
                map.put("categoryName", ind.getCategoryName());
                map.put("subCategory", ind.getSubCategory());
                map.put("content", ind.getContent());
                map.put("weightMonthly", ind.getWeightMonthly());

                // 部门打分（他评平均分）
                String peerKey = orgId + "-" + ind.getId();
                List<BigDecimal> scores = peerScoreMap.get(peerKey);
                BigDecimal deptScore = null;
                if (scores != null && !scores.isEmpty()) {
                    BigDecimal sum = BigDecimal.ZERO;
                    int count = 0;
                    for (BigDecimal s : scores) {
                        if (s != null) {
                            sum = sum.add(s);
                            count++;
                        }
                    }
                    if (count > 0) {
                        deptScore = sum.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
                    }
                }
                map.put("deptScore", deptScore);

                // 管理员打分
                BizReviewScore review = reviewMap.get(peerKey);
                if (review != null) {
                    map.put("id", review.getId());
                    map.put("adminScore", review.getAdminScore());
                    map.put("scoreComment", review.getScoreComment());
                } else {
                    map.put("id", null);
                    map.put("adminScore", null);
                    map.put("scoreComment", "");
                }

                // 最终得分（未加权）
                map.put("finalScore", calcFinalScore(
                    (BigDecimal) map.get("adminScore"),
                    deptScore
                ));

                result.add(map);
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getReviewSummary(Long examGroupId) {
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (BizExamGroupMember member : members) {
            LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
            indWrapper.eq(BizIndicatorDefinition::getOrgId, member.getOrgId());
            indWrapper.eq(BizIndicatorDefinition::getApprovalStatus, "APPROVED");
            int total = indicatorDefinitionMapper.selectCount(indWrapper).intValue();

            LambdaQueryWrapper<BizReviewScore> reviewWrapper = new LambdaQueryWrapper<>();
            reviewWrapper.eq(BizReviewScore::getExamGroupId, examGroupId);
            reviewWrapper.eq(BizReviewScore::getOrgId, member.getOrgId());
            reviewWrapper.isNotNull(BizReviewScore::getAdminScore);
            int reviewed = (int) count(reviewWrapper);

            Map<String, Object> map = new HashMap<>();
            map.put("orgId", member.getOrgId());
            map.put("orgName", member.getOrgName());
            map.put("totalIndicators", total);
            map.put("reviewedCount", reviewed);
            map.put("progress", total > 0 ? (reviewed * 100 / total) : 0);
            map.put("status", reviewed >= total && total > 0 ? "COMPLETED" : "PENDING");
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReviewScore(ReviewScoreSaveDTO dto) {
        BizReviewScore entity;
        if (dto.getId() != null) {
            entity = getById(dto.getId());
            if (entity == null) {
                entity = new BizReviewScore();
                entity.setExamGroupId(dto.getExamGroupId());
                entity.setOrgId(dto.getOrgId());
                entity.setIndicatorId(dto.getIndicatorId());
            }
        } else {
            BizReviewScore existing = getBaseMapper().selectByUniqueKey(
                dto.getExamGroupId(), dto.getOrgId(), dto.getIndicatorId()
            );
            if (existing != null) {
                entity = existing;
            } else {
                entity = new BizReviewScore();
                entity.setExamGroupId(dto.getExamGroupId());
                entity.setOrgId(dto.getOrgId());
                entity.setIndicatorId(dto.getIndicatorId());
            }
        }

        // 获取部门打分（他评平均分）
        LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
        peerWrapper.eq(BizPeerEvaluation::getExamGroupId, dto.getExamGroupId());
        peerWrapper.eq(BizPeerEvaluation::getTargetOrgId, dto.getOrgId());
        peerWrapper.eq(BizPeerEvaluation::getIndicatorId, dto.getIndicatorId());
        peerWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
        List<BizPeerEvaluation> peerEvals = peerEvaluationMapper.selectList(peerWrapper);
        BigDecimal deptScore = null;
        if (!peerEvals.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            for (BizPeerEvaluation pe : peerEvals) {
                if (pe.getPeerScore() != null) {
                    sum = sum.add(pe.getPeerScore());
                    count++;
                }
            }
            if (count > 0) {
                deptScore = sum.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
            }
        }

        // 获取指标权重
        BigDecimal weightMonthly = BigDecimal.ZERO;
        BizIndicatorDefinition ind = indicatorDefinitionMapper.selectById(dto.getIndicatorId());
        if (ind != null) {
            weightMonthly = ind.getWeightMonthly();
            if (StringUtils.hasText(ind.getOrgName())) {
                entity.setOrgName(ind.getOrgName());
            }
        }

        entity.setDeptScore(deptScore);
        entity.setAdminScore(dto.getAdminScore());
        entity.setFinalScore(calcFinalScore(dto.getAdminScore(), deptScore));
        entity.setScoreComment(dto.getScoreComment());
        entity.setUpdatedTime(LocalDateTime.now());
        if (entity.getId() == null) {
            entity.setCreatedTime(LocalDateTime.now());
        }
        saveOrUpdate(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveReviewScore(ReviewScoreBatchDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return;
        }
        for (ReviewScoreSaveDTO item : dto.getItems()) {
            if (item.getExamGroupId() == null) {
                item.setExamGroupId(dto.getExamGroupId());
            }
            saveReviewScore(item);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long examGroupId, String reviewer) {
        LambdaQueryWrapper<BizReviewScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizReviewScore::getExamGroupId, examGroupId);
        List<BizReviewScore> list = list(wrapper);

        // 获取所有他评记录计算最新deptScore
        LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
        peerWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
        peerWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
        List<BizPeerEvaluation> peerEvals = peerEvaluationMapper.selectList(peerWrapper);
        Map<String, List<BigDecimal>> peerScoreMap = new HashMap<>();
        for (BizPeerEvaluation eval : peerEvals) {
            String key = eval.getTargetOrgId() + "-" + eval.getIndicatorId();
            peerScoreMap.computeIfAbsent(key, k -> new ArrayList<>()).add(eval.getPeerScore());
        }

        // 获取指标权重
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(
            new LambdaQueryWrapper<BizIndicatorDefinition>()
                .eq(BizIndicatorDefinition::getExamGroupId, examGroupId)
        );
        Map<Long, BigDecimal> weightMap = new HashMap<>();
        for (BizIndicatorDefinition ind : indicators) {
            weightMap.put(ind.getId(), ind.getWeightMonthly());
        }

        LocalDateTime now = LocalDateTime.now();
        for (BizReviewScore rs : list) {
            String key = rs.getOrgId() + "-" + rs.getIndicatorId();
            List<BigDecimal> scores = peerScoreMap.get(key);
            BigDecimal deptScore = null;
            if (scores != null && !scores.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                int count = 0;
                for (BigDecimal s : scores) {
                    if (s != null) {
                        sum = sum.add(s);
                        count++;
                    }
                }
                if (count > 0) {
                    deptScore = sum.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
                }
            }
            rs.setDeptScore(deptScore);
            rs.setFinalScore(calcFinalScore(rs.getAdminScore(), deptScore));
            rs.setReviewer(reviewer);
            rs.setReviewTime(now);
            rs.setUpdatedTime(now);
            updateById(rs);
        }
    }

    private BigDecimal calcFinalScore(BigDecimal adminScore, BigDecimal deptScore) {
        if (adminScore == null && deptScore == null) {
            return null;
        }
        BigDecimal baseScore = adminScore;
        if (baseScore == null) {
            baseScore = deptScore;
        } else if (deptScore != null && deptScore.compareTo(baseScore) > 0) {
            baseScore = deptScore;
        }
        return baseScore;
    }
}
