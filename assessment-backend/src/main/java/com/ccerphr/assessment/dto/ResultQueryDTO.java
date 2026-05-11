package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class ResultQueryDTO {
    private Long examGroupId;
    private Long orgId;
    private Long categoryId;
    private String scoreMonth;
    private Long current = 1L;
    private Long size = 10L;
}
