package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResultDetailVO {
    private Long orgId;
    private String orgName;
    private String categoryName;
    private String subCategory;
    private String content;
    private String targetDesc;
    private BigDecimal weightAnnual;
    private BigDecimal weightMonthly;
    private String evaluationStandard;
    private BigDecimal selfScore;
    private BigDecimal peerScore;
    private BigDecimal adminScore;
    private BigDecimal finalScore;
    private BigDecimal weightedScore;
}
