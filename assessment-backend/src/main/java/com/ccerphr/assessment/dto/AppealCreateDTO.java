package com.ccerphr.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppealCreateDTO {
    @NotNull(message = "考核组ID不能为空")
    private Long examGroupId;

    @NotNull(message = "申诉单位ID不能为空")
    private Long appealOrgId;

    @Size(max = 100, message = "申诉单位名称不能超过100字符")
    private String appealOrgName;

    @NotNull(message = "被申诉评分单位ID不能为空")
    private Long scorerOrgId;

    @Size(max = 100, message = "被申诉评分单位名称不能超过100字符")
    private String scorerOrgName;

    @NotNull(message = "指标ID不能为空")
    private Long indicatorId;

    @NotNull(message = "申诉原因不能为空")
    @Size(min = 10, max = 2000, message = "申诉原因长度应在10-2000字符之间")
    private String appealReason;

    @NotNull(message = "原评分不能为空")
    @DecimalMin(value = "0", message = "分数不能小于0")
    @DecimalMax(value = "999.99", message = "分数不能超过999.99")
    private BigDecimal originalScore;
}
