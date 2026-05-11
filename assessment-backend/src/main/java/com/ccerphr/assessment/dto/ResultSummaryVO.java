package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ResultSummaryVO {
    private Long orgId;
    private String orgName;
    private Map<String, BigDecimal> categoryScores;
    private BigDecimal totalScore;
}
