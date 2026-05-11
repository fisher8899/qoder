package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizAppeal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizAppealMapper extends BaseMapper<BizAppeal> {

    @Select("SELECT * FROM biz_appeal WHERE scorer_org_id = #{scorerOrgId} AND status = 'PENDING_REEVAL' AND deleted = 0 ORDER BY created_time DESC")
    List<BizAppeal> selectPendingReevalByScorer(@Param("scorerOrgId") Long scorerOrgId);

    @Select("SELECT COUNT(*) FROM biz_appeal WHERE scorer_org_id = #{scorerOrgId} AND status = 'PENDING_REEVAL' AND deleted = 0")
    Long countPendingReevalByScorer(@Param("scorerOrgId") Long scorerOrgId);
}
