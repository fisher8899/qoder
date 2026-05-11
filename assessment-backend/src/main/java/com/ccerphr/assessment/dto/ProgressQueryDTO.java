package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class ProgressQueryDTO {
    private Long examGroupId;
    private Long orgId;
    private String period;
}
