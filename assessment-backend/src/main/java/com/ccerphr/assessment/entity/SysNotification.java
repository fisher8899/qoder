package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_notification")
public class SysNotification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recipientUserId;
    private String title;
    private String content;
    private String linkUrl;
    private String linkText;
    private String notifType;
    private Long relatedId;
    private Integer isRead;
    private Long unitId;
    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}
