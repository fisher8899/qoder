package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HistoryExamVO {
    private Long examGroupId;
    private String groupName;
    private String examCategory;
    private String examType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String currentStep;
    private BigDecimal totalScore;
}
