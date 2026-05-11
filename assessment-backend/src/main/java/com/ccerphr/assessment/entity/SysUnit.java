package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_unit")
public class SysUnit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String unitName;
    private String unitCode;
    private String unitType;
    private Integer isEnabled;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDate expireDate;
    @TableLogic
    private Integer deleted;
}
