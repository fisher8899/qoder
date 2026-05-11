package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizExamGroupMemberMapper extends BaseMapper<BizExamGroupMember> {

    @Select("SELECT org_id FROM biz_exam_group_member WHERE exam_group_id = #{examGroupId} AND deleted = 0")
    List<Long> selectOrgIdsByExamGroupId(@Param("examGroupId") Long examGroupId);
}
