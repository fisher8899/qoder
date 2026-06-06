package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizLeaderEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizLeaderEvaluationMapper extends BaseMapper<BizLeaderEvaluation> {

    @Select("SELECT * FROM biz_leader_evaluation WHERE exam_group_id = #{examGroupId} AND leader_id = #{leaderId} AND deleted = 0")
    List<BizLeaderEvaluation> selectByExamGroupAndLeader(@Param("examGroupId") Long examGroupId, @Param("leaderId") Long leaderId);

    @Select("SELECT * FROM biz_leader_evaluation WHERE exam_group_id = #{examGroupId} AND leader_id = #{leaderId} AND target_org_id = #{targetOrgId} AND deleted = 0")
    List<BizLeaderEvaluation> selectByExamGroupLeaderAndTarget(@Param("examGroupId") Long examGroupId, @Param("leaderId") Long leaderId, @Param("targetOrgId") Long targetOrgId);
}
