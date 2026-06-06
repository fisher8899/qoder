package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DatabaseTablePageDTO {
    private String tableName;
    private String tableComment;
    private List<DatabaseColumnDTO> columns;
    private PageResult<Map<String, Object>> page;
}
