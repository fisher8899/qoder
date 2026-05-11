package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class PeerEvalQueryDTO {
    private Long examGroupId;
    private Long evaluatorOrgId;
    private Long targetOrgId;
    private String status;
}
