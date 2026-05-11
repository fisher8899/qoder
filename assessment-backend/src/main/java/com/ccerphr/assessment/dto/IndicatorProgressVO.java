package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class IndicatorProgressVO {
    private Long examGroupId;
    private String groupName;
    private Long orgId;
    private String orgName;
    private String approvalStatus;
    private Integer totalCount;
    private Integer approvedCount;
}
