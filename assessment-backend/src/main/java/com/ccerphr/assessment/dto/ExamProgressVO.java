package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExamProgressVO {
    private Long orgId;
    private String orgName;
    private BigDecimal selfEvalRate;
    private BigDecimal peerEvalRate;
    private String reviewStatus;
    private BigDecimal overallProgress;
}
