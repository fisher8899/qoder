package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标-分管领导关联表
 */
@Data
@TableName("biz_indicator_leader")
public class BizIndicatorLeader {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 指标ID
     */
    private Long indicatorId;

    /**
     * 分管领导ID
     */
    private Long leaderId;

    /**
     * 分管领导名称（冗余，便于展示）
     */
    private String leaderName;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}