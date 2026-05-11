package com.ccerphr.assessment.enums;

import lombok.Getter;

@Getter
public enum ApprovalStatus {
    DRAFT("DRAFT", "草稿"),
    PENDING_DEPT_LEADER("PENDING_DEPT_LEADER", "待部门负责人审批"),
    PENDING_SUPERVISOR("PENDING_SUPERVISOR", "待分管领导审批"),
    PENDING_FINANCE("PENDING_FINANCE", "待财务处审批"),
    APPROVED("APPROVED", "审批通过"),
    REJECTED("REJECTED", "被退回");

    private final String code;
    private final String label;

    ApprovalStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
