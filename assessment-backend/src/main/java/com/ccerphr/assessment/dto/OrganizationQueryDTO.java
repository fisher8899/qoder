package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationQueryDTO extends PageRequest {
    private String orgName;
    private String orgType;
    private Long unitId;
}
