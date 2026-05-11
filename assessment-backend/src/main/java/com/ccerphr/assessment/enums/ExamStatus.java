package com.ccerphr.assessment.enums;

import lombok.Getter;

@Getter
public enum ExamStatus {
    NOT_STARTED("NOT_STARTED", "待启动"),
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    COMPLETED("COMPLETED", "已完成"),
    PRE_PUBLISHED("PRE_PUBLISHED", "预发布"),
    PUBLISHED("PUBLISHED", "已发布");

    private final String code;
    private final String label;

    ExamStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
