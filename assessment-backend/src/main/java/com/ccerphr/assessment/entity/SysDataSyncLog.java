package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_data_sync_log")
public class SysDataSyncLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String syncType;
    private Integer totalCount;
    private Integer addCount;
    private Integer updateCount;
    private Integer failCount;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdTime;
}
