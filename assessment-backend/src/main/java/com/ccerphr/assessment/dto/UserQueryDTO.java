package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageRequest {
    private String keyword;
    private String roleCode;
    private Integer isEnabled;
}
