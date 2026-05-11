package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionQueryDTO extends PageRequest {
    private Long userId;
    private String userName;
    private String roleCode;
}
