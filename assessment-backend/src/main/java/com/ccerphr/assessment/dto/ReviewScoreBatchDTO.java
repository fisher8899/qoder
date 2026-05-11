package com.ccerphr.assessment.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReviewScoreBatchDTO {
    private Long examGroupId;
    private List<ReviewScoreSaveDTO> items;
}
