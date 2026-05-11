package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_monthly_score")
public class BizMonthlyScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属单位ID
     */
    private Long unitId;
    private Long examGroupId;
    private Long orgId;
    private String orgName;
    private Long indicatorId;
    private String categoryName;
    private BigDecimal scoreValue;
    private BigDecimal weightMonthly;
    private BigDecimal weightedScore;
    private BigDecimal totalScore;
    private String scoreMonth;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
