package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizSelfEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizSelfEvaluationMapper extends BaseMapper<BizSelfEvaluation> {

    @Select("SELECT * FROM biz_self_evaluation WHERE exam_group_id = #{examGroupId} AND org_id = #{orgId} AND deleted = 0")
    List<BizSelfEvaluation> selectByExamGroupAndOrg(@Param("examGroupId") Long examGroupId, @Param("orgId") Long orgId);
}
