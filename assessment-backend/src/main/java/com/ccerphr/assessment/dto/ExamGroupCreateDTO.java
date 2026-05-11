package com.ccerphr.assessment.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamGroupCreateDTO {
    private Long id;
    private String groupName;
    private String examCategory;
    private String examType;
    private LocalDate startDate;
    private LocalDate endDate;
}
