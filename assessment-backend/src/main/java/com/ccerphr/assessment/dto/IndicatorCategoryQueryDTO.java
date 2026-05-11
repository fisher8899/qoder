package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IndicatorCategoryQueryDTO extends PageRequest {
    private String categoryName;
    private String categoryCode;
    private Integer isEnabled;
    private String applicableScope;
}
