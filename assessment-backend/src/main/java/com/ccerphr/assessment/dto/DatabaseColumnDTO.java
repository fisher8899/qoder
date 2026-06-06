package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class DatabaseColumnDTO {
    private String columnName;
    private String columnType;
    private String columnComment;
    private boolean primaryKey;
    private boolean editable;
}
