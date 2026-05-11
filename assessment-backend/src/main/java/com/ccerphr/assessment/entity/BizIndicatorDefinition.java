package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_indicator_definition")
public class BizIndicatorDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属单位ID（多选时存储逗号分隔的ID）
     */
    private Long unitId;
    private Long examGroupId;
    /**
     * 组织ID（多选时存储第一个ID，用于兼容单选查询）
     */
    private Long orgId;
    /**
     * 组织名称（多选时存储逗号分隔的名称）
     */
    private String orgName;
    private Long categoryId;
    private String categoryName;
    private String subCategory;
    private String content;
    private String targetDesc;
    private BigDecimal weightAnnual;
    private BigDecimal weightMonthly;
    private String evaluationStandard;
    private Integer sortCode;
    private String approvalStatus;
    private String rejectReason;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private String approvedBy;
    private LocalDateTime approvedTime;
    private String examTargetType;
    /**
     * 分管领导ID（多选时存储第一个ID，用于兼容单选查询）
     */
    private Long leaderId;
    /**
     * 分管领导名称（多选时存储逗号分隔的名称）
     */
    private String leaderName;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
