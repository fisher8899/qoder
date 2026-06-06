package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizIndicatorLeader;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BizIndicatorLeaderMapper extends BaseMapper<BizIndicatorLeader> {

    /**
     * 根据指标ID查询关联的分管领导列表
     */
    @Select("SELECT * FROM biz_indicator_leader WHERE indicator_id = #{indicatorId} AND deleted = 0 ORDER BY id")
    List<BizIndicatorLeader> selectByIndicatorId(@Param("indicatorId") Long indicatorId);

    /**
     * 根据分管领导ID查询关联的指标ID列表
     */
    @Select("SELECT indicator_id FROM biz_indicator_leader WHERE leader_id = #{leaderId} AND deleted = 0")
    List<Long> selectIndicatorIdsByLeaderId(@Param("leaderId") Long leaderId);

    /**
     * 根据指标ID软删除关联数据
     */
    @Update("UPDATE biz_indicator_leader SET deleted = 1 WHERE indicator_id = #{indicatorId}")
    void deleteByIndicatorId(@Param("indicatorId") Long indicatorId);
}