package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReviewScoreSaveDTO {
    private Long id;
    private Long examGroupId;
    private Long orgId;
    private Long indicatorId;
    private BigDecimal adminScore;
    private String scoreComment;
}
