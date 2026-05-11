package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizMonthlyScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizMonthlyScoreMapper extends BaseMapper<BizMonthlyScore> {

    @Select("SELECT * FROM biz_monthly_score WHERE exam_group_id = #{examGroupId} AND deleted = 0 ORDER BY org_id, category_name")
    List<BizMonthlyScore> selectByExamGroup(@Param("examGroupId") Long examGroupId);

    @Select("SELECT * FROM biz_monthly_score WHERE exam_group_id = #{examGroupId} AND org_id = #{orgId} AND deleted = 0 ORDER BY category_name")
    List<BizMonthlyScore> selectByExamGroupAndOrg(@Param("examGroupId") Long examGroupId, @Param("orgId") Long orgId);
}
