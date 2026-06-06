package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_peer_evaluation")
public class BizPeerEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属单位ID
     */
    private Long unitId;
    private Long examGroupId;
    private Long evaluatorOrgId;
    private String evaluatorOrgName;
    private Long targetOrgId;
    private String targetOrgName;
    private Long indicatorId;
    private BigDecimal peerScore;
    private BigDecimal peerResult;
    private String scoreComment;
    private String status;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
