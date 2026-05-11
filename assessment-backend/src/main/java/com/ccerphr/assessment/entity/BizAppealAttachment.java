package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_appeal_attachment")
public class BizAppealAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appealId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private LocalDateTime createdTime;
    @TableLogic
    private Integer deleted;
}
