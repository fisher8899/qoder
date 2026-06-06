package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExamProgressDetailVO {
    private Long indicatorId;
    private Long categoryId;
    private String categoryName;
    private Integer sortCode;
    private String subCategory;
    private String content;
    private String targetDesc;
    private BigDecimal weightAnnual;
    private BigDecimal weightMonthly;
    private String evaluationStandard;

    private Long selfEvalId;
    private String actualCompletion;
    private BigDecimal selfScore;
    private BigDecimal selfResult;
    private String attachmentUrl;
    private String attachmentName;
    private String attachmentDownloadUrl;
    private String status;

    private BigDecimal peerResult;
    private String peerComment;
    private BigDecimal adminScore;
    private String adjustComment;
}
