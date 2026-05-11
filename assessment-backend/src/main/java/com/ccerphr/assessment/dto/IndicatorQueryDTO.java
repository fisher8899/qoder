package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IndicatorQueryDTO extends PageRequest {
    private Long examGroupId;
    private Long orgId;
    private Long categoryId;
    private String approvalStatus;
}
