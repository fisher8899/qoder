package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeQueryDTO extends PageRequest {
    private String keyword;
    private Long deptId;
    private Integer isActive;
}
