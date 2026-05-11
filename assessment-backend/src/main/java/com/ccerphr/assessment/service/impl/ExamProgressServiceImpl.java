package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.ExamProgressVO;
import com.ccerphr.assessment.dto.ProgressQueryDTO;
import com.ccerphr.assessment.dto.UnfilledItemVO;
import com.ccerphr.assessment.entity.*;
import com.ccerphr.assessment.mapper.*;
import com.ccerphr.assessment.service.ExamProgressService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExamProgressServiceImpl implements ExamProgressService {

    private final BizExamGroupMemberMapper memberMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final BizSelfEvaluationMapper selfEvaluationMapper;
    private final BizPeerEvaluationMapper peerEvaluationMapper;
    private final BizReviewScoreMapper reviewScoreMapper;

    public ExamProgressServiceImpl(BizExamGroupMemberMapper memberMapper,
                                   BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                   BizSelfEvaluationMapper selfEvaluationMapper,
                                   BizPeerEvaluationMapper peerEvaluationMapper,
                                   BizReviewScoreMapper reviewScoreMapper) {
        this.memberMapper = memberMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.selfEvaluationMapper = selfEvaluationMapper;
        this.peerEvaluationMapper = peerEvaluationMapper;
        this.reviewScoreMapper = reviewScoreMapper;
    }

    @Override
    public List<ExamProgressVO> queryProgress(ProgressQueryDTO queryDTO) {
        Long examGroupId = queryDTO.getExamGroupId();
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

            // 指标总数
            LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
            indWrapper.eq(BizIndicatorDefinition::getOrgId, member.getOrgId());
            long totalIndicator = indicatorDefinitionMapper.selectCount(indWrapper);

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
    public List<UnfilledItemVO> queryUnfilledItems(Long examGroupId, Long orgId) {
        List<UnfilledItemVO> result = new ArrayList<>();

        // 查询该部门下所有指标
        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
        indWrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
        List<BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper);

        for (BizIndicatorDefinition ind : indicators) {
            // 检查自评是否完成
            LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
            selfWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
            selfWrapper.eq(BizSelfEvaluation::getIndicatorId, ind.getId());
            selfWrapper.eq(BizSelfEvaluation::getStatus, "SUBMITTED");
            Long selfCount = selfEvaluationMapper.selectCount(selfWrapper);
            if (selfCount == 0) {
                UnfilledItemVO vo = new UnfilledItemVO();
                vo.setIndicatorName(ind.getContent());
                vo.setStage("自评");
                vo.setOrgName(ind.getOrgName());
                result.add(vo);
                continue;
            }

            // 检查他评是否完成（任意一条他评记录）
            LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
            peerWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
            peerWrapper.eq(BizPeerEvaluation::getTargetOrgId, orgId);
            peerWrapper.eq(BizPeerEvaluation::getIndicatorId, ind.getId());
            peerWrapper.eq(BizPeerEvaluation::getStatus, "SUBMITTED");
            Long peerCount = peerEvaluationMapper.selectCount(peerWrapper);
            if (peerCount == 0) {
                UnfilledItemVO vo = new UnfilledItemVO();
                vo.setIndicatorName(ind.getContent());
                vo.setStage("他评");
                vo.setOrgName(ind.getOrgName());
                result.add(vo);
                continue;
            }

            // 检查复核是否完成
            LambdaQueryWrapper<BizReviewScore> reviewWrapper = new LambdaQueryWrapper<>();
            reviewWrapper.eq(BizReviewScore::getExamGroupId, examGroupId);
            reviewWrapper.eq(BizReviewScore::getOrgId, orgId);
            reviewWrapper.eq(BizReviewScore::getIndicatorId, ind.getId());
            Long reviewCount = reviewScoreMapper.selectCount(reviewWrapper);
            if (reviewCount == 0) {
                UnfilledItemVO vo = new UnfilledItemVO();
                vo.setIndicatorName(ind.getContent());
                vo.setStage("复核");
                vo.setOrgName(ind.getOrgName());
                result.add(vo);
            }
        }

        return result;
    }
}
