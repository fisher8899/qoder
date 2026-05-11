package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_self_evaluation")
public class BizSelfEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属单位ID
     */
    private Long unitId;
    private Long examGroupId;
    private Long orgId;
    private Long indicatorId;
    private String actualCompletion;
    private BigDecimal selfScore;
    private BigDecimal selfResult;
    private String attachmentUrl;
    private String attachmentName;
    private String status;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
