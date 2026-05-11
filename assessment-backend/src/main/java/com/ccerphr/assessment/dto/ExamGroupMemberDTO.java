package com.ccerphr.assessment.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExamGroupMemberDTO {
    private Long examGroupId;
    private List<Long> orgIds;
}
