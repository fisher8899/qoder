package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizPeerEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizPeerEvaluationMapper extends BaseMapper<BizPeerEvaluation> {

    @Select("SELECT * FROM biz_peer_evaluation WHERE exam_group_id = #{examGroupId} AND evaluator_org_id = #{evaluatorOrgId} AND target_org_id = #{targetOrgId} AND deleted = 0")
    List<BizPeerEvaluation> selectByExamGroupAndEvaluatorAndTarget(@Param("examGroupId") Long examGroupId, @Param("evaluatorOrgId") Long evaluatorOrgId, @Param("targetOrgId") Long targetOrgId);

    @Select("SELECT * FROM biz_peer_evaluation WHERE exam_group_id = #{examGroupId} AND evaluator_org_id = #{evaluatorOrgId} AND deleted = 0")
    List<BizPeerEvaluation> selectByExamGroupAndEvaluator(@Param("examGroupId") Long examGroupId, @Param("evaluatorOrgId") Long evaluatorOrgId);

    @Select("SELECT COUNT(*) FROM biz_peer_evaluation WHERE exam_group_id = #{examGroupId} AND evaluator_org_id = #{evaluatorOrgId} AND target_org_id = #{targetOrgId} AND status = 'SUBMITTED' AND deleted = 0")
    Long countSubmittedByTarget(@Param("examGroupId") Long examGroupId, @Param("evaluatorOrgId") Long evaluatorOrgId, @Param("targetOrgId") Long targetOrgId);
}
