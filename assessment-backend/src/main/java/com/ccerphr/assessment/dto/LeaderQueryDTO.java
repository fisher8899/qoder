package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeaderQueryDTO extends PageRequest {
    private Long unitId;
    private String leaderName;
}
