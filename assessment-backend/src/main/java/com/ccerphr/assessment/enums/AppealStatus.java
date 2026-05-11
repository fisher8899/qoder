package com.ccerphr.assessment.enums;

import lombok.Getter;

@Getter
public enum AppealStatus {
    DRAFT("DRAFT", "草稿"),
    PENDING_REEVAL("PENDING_REEVAL", "待重新评估"),
    PROCESSED("PROCESSED", "已处理");

    private final String code;
    private final String label;

    AppealStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
