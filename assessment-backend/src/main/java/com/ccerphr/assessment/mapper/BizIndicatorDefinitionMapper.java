package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.dto.IndicatorProgressVO;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizIndicatorDefinitionMapper extends BaseMapper<BizIndicatorDefinition> {

    @Select("SELECT * FROM biz_indicator_definition WHERE exam_group_id = #{examGroupId} AND org_id = #{orgId} AND deleted = 0 ORDER BY sort_code")
    List<BizIndicatorDefinition> selectByExamGroupAndOrg(@Param("examGroupId") Long examGroupId, @Param("orgId") Long orgId);

    @Select("<script>" +
        "SELECT bid.exam_group_id AS examGroupId, beg.group_name AS groupName, bid.org_id AS orgId, bid.org_name AS orgName, " +
        "COUNT(*) AS totalCount, " +
        "SUM(CASE WHEN bid.approval_status = 'APPROVED' THEN 1 ELSE 0 END) AS approvedCount, " +
        "CASE " +
        "  WHEN SUM(CASE WHEN bid.approval_status = 'REJECTED' THEN 1 ELSE 0 END) &gt; 0 THEN 'REJECTED' " +
        "  WHEN SUM(CASE WHEN bid.approval_status = 'DRAFT' THEN 1 ELSE 0 END) &gt; 0 THEN 'DRAFT' " +
        "  WHEN SUM(CASE WHEN bid.approval_status = 'PENDING_DEPT_LEADER' THEN 1 ELSE 0 END) &gt; 0 THEN 'PENDING_DEPT_LEADER' " +
        "  WHEN SUM(CASE WHEN bid.approval_status = 'PENDING_SUPERVISOR' THEN 1 ELSE 0 END) &gt; 0 THEN 'PENDING_SUPERVISOR' " +
        "  WHEN SUM(CASE WHEN bid.approval_status = 'PENDING_FINANCE' THEN 1 ELSE 0 END) &gt; 0 THEN 'PENDING_FINANCE' " +
        "  ELSE 'APPROVED' " +
        "END AS approvalStatus " +
        "FROM biz_indicator_definition bid " +
        "LEFT JOIN biz_exam_group beg ON bid.exam_group_id = beg.id " +
        "WHERE bid.deleted = 0 " +
        "<if test='examGroupId != null'> AND bid.exam_group_id = #{examGroupId}</if>" +
        "<if test='orgName != null and orgName != \"\"'> AND bid.org_name LIKE CONCAT('%', #{orgName}, '%')</if>" +
        "<if test='unitId != null'> AND bid.unit_id = #{unitId}</if>" +
        "<if test='orgId != null'> AND bid.org_id = #{orgId}</if>" +
        "GROUP BY bid.exam_group_id, beg.group_name, bid.org_id, bid.org_name " +
        "<if test='approvalStatus != null and approvalStatus != \"\"'>" +
        "HAVING approvalStatus = #{approvalStatus} " +
        "</if>" +
        "ORDER BY bid.exam_group_id DESC, bid.org_name" +
        "</script>")
    List<IndicatorProgressVO> queryProgress(@Param("examGroupId") Long examGroupId,
                                            @Param("orgName") String orgName,
                                            @Param("approvalStatus") String approvalStatus,
                                            @Param("unitId") Long unitId,
                                            @Param("orgId") Long orgId);
}
