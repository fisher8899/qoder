package com.ccerphr.assessment.dto;

import lombok.Data;

@Data
public class DatabaseQueryDTO {
    private String tableName;
    private long current = 1;
    private long size = 10;
}
