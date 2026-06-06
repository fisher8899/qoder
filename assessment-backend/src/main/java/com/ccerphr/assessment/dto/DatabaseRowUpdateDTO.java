package com.ccerphr.assessment.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DatabaseRowUpdateDTO {
    private String tableName;
    private Long id;
    private Map<String, Object> values;
}
