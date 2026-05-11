package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UnitQueryDTO extends PageRequest {
    private String unitName;
    private String unitType;
    private Integer isEnabled;
}
