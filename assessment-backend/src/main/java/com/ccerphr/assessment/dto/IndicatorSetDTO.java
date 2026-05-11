package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class IndicatorSetDTO {
    private Long id;
    private Long examGroupId;
    private Long orgId;
    private Long categoryId;
    private String categoryName;
    private String subCategory;
    private String content;
    private String targetDesc;
    private BigDecimal weightAnnual;
    private BigDecimal weightMonthly;
    private String evaluationStandard;
    private Integer sortCode;
    private String examTargetType;
    private Long leaderId;
    private String leaderName;

    /**
     * 多选支持：考核部门ID列表
     */
    private List<Long> orgIds;

    /**
     * 多选支持：考核部门名称列表
     */
    private List<String> orgNames;

    /**
     * 多选支持：分管领导ID列表
     */
    private List<Long> leaderIds;

    /**
     * 多选支持：分管领导名称列表
     */
    private List<String> leaderNames;
}
