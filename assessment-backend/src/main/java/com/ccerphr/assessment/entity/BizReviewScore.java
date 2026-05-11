package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_review_score")
public class BizReviewScore {
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
    private BigDecimal deptScore;
    private BigDecimal adminScore;
    private BigDecimal finalScore;
    private String scoreComment;
    private String reviewer;
    private LocalDateTime reviewTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
