package com.ccerphr.assessment.dto;

import lombok.Data;

import java.util.List;

@Data
public class IndicatorApprovalDTO {
    private List<Long> indicatorIds;
    private String action;
    private String rejectReason;
    private String roleCode;
}
