package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标-考核部门关联表
 */
@Data
@TableName("biz_indicator_org")
public class BizIndicatorOrg {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 指标ID
     */
    private Long indicatorId;

    /**
     * 组织ID（考核部门）
     */
    private Long orgId;

    /**
     * 组织名称（冗余，便于展示）
     */
    private String orgName;

    private LocalDateTime createdTime;

    @TableLogic
    private Integer deleted;
}