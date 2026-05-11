package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizReviewScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizReviewScoreMapper extends BaseMapper<BizReviewScore> {

    @Select("SELECT * FROM biz_review_score WHERE exam_group_id = #{examGroupId} AND deleted = 0")
    List<BizReviewScore> selectByExamGroupId(@Param("examGroupId") Long examGroupId);

    @Select("SELECT * FROM biz_review_score WHERE exam_group_id = #{examGroupId} AND org_id = #{orgId} AND deleted = 0")
    List<BizReviewScore> selectByExamGroupAndOrg(@Param("examGroupId") Long examGroupId, @Param("orgId") Long orgId);

    @Select("SELECT * FROM biz_review_score WHERE exam_group_id = #{examGroupId} AND org_id = #{orgId} AND indicator_id = #{indicatorId} AND deleted = 0")
    BizReviewScore selectByUniqueKey(@Param("examGroupId") Long examGroupId, @Param("orgId") Long orgId, @Param("indicatorId") Long indicatorId);
}
