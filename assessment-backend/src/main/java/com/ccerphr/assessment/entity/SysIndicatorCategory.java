package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_indicator_category")
public class SysIndicatorCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryName;
    private String categoryCode;
    private Integer sortCode;
    private String applicableScope;
    private BigDecimal weight;
    private String evaluationStandard;
    private Long unitId;
    private Integer isEnabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
