package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExamGroupQueryDTO extends PageRequest {
    private String groupName;
    private String examType;
    private String examCategory;
    private String status;
}
