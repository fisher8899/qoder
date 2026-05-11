package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizIndicatorOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizIndicatorOrgMapper extends BaseMapper<BizIndicatorOrg> {

    /**
     * 根据指标ID查询关联的部门列表
     */
    @Select("SELECT * FROM biz_indicator_org WHERE indicator_id = #{indicatorId} AND deleted = 0 ORDER BY id")
    List<BizIndicatorOrg> selectByIndicatorId(@Param("indicatorId") Long indicatorId);

    /**
     * 根据部门ID查询关联的指标ID列表
     */
    @Select("SELECT indicator_id FROM biz_indicator_org WHERE org_id = #{orgId} AND deleted = 0")
    List<Long> selectIndicatorIdsByOrgId(@Param("orgId") Long orgId);

    /**
     * 根据指标ID删除关联数据
     */
    @Select("UPDATE biz_indicator_org SET deleted = 1 WHERE indicator_id = #{indicatorId}")
    void deleteByIndicatorId(@Param("indicatorId") Long indicatorId);
}