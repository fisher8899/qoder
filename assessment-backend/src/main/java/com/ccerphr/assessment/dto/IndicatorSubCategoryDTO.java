package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class IndicatorSubCategoryDTO {
    private Long id;
    private Long unitId;
    private Long examGroupId;
    private Long orgId;
    private String orgName;
    private Long categoryId;
    private String categoryName;
    private String subCategoryName;
    private String evaluationStandard;
    private Integer sortCode;
}
