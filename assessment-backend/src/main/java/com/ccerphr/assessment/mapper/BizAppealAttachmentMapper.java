package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.BizAppealAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizAppealAttachmentMapper extends BaseMapper<BizAppealAttachment> {

    @Select("SELECT * FROM biz_appeal_attachment WHERE appeal_id = #{appealId} AND deleted = 0 ORDER BY created_time DESC")
    List<BizAppealAttachment> selectByAppealId(@Param("appealId") Long appealId);
}
