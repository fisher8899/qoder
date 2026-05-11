package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_leader")
public class SysLeader {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long unitId;
    private Long employeeId;
    private String leaderName;
    private String leaderLevel;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
