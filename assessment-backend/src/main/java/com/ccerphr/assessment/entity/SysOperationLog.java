package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    private String operation;
    private String method;
    private String params;
    private String ip;
    private Long duration;
    private String status;
    private String errorMsg;
    private LocalDateTime createdTime;
}
