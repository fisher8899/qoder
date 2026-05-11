package com.ccerphr.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class IndicatorTreeDTO {
    private String categoryName;
    private List<IndicatorSubCategoryDTO> subCategories;

    @Data
    public static class IndicatorSubCategoryDTO {
        private String subCategory;
        private List<IndicatorItemDTO> items;
    }

    @Data
    public static class IndicatorItemDTO {
        private Long id;
        private String content;
        private String targetDesc;
        private BigDecimal weightAnnual;
        private BigDecimal weightMonthly;
        private String evaluationStandard;
    }
}
