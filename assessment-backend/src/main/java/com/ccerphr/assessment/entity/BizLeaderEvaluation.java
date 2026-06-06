package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_leader_evaluation")
public class BizLeaderEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long unitId;
    private Long examGroupId;
    private Long leaderId;
    private Long targetOrgId;
    private String targetOrgName;
    private Long indicatorId;
    private BigDecimal leaderScore;
    private BigDecimal leaderResult;
    private String scoreComment;
    private String status;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
