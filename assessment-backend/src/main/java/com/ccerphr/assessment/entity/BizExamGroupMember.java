package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_exam_group_member")
public class BizExamGroupMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属单位ID
     */
    private Long unitId;
    private Long examGroupId;
    private Long orgId;
    private String orgName;
    private String memberType;
    private LocalDateTime createdTime;
    @TableLogic
    private Integer deleted;
}
