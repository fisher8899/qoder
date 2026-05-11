package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_appeal")
public class BizAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属单位ID
     */
    private Long unitId;
    private Long examGroupId;
    private Long appealOrgId;
    private String appealOrgName;
    private Long scorerOrgId;
    private String scorerOrgName;
    private Long indicatorId;
    private String appealReason;
    private String status;
    private BigDecimal originalScore;
    private BigDecimal newScore;
    private String handledBy;
    private LocalDateTime handledTime;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
