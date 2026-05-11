package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class AppealQueryDTO {
    private Long examGroupId;
    private Long appealOrgId;
    private String status;
    private Integer current = 1;
    private Integer size = 10;
}
