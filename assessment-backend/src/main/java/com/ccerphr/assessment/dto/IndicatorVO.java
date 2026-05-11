package com.ccerphr.assessment.dto;

import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class IndicatorVO extends BizIndicatorDefinition {
    private String examGroupName;
    private String orgName;
    private String examType;
    private String examCategory;
    private String startDate;
    private String endDate;

    /**
     * 多选支持：考核部门ID列表
     */
    private List<Long> orgIdList;

    /**
     * 多选支持：考核部门名称列表
     */
    private List<String> orgNameList;

    /**
     * 多选支持：分管领导ID列表
     */
    private List<Long> leaderIdList;

    /**
     * 多选支持：分管领导名称列表
     */
    private List<String> leaderNameList;
}
