package com.ccerphr.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SelfEvalSaveDTO {
    private Long id;

    @NotNull(message = "考核组ID不能为空")
    private Long examGroupId;

    @NotNull(message = "单位ID不能为空")
    private Long orgId;

    @NotNull(message = "指标ID不能为空")
    private Long indicatorId;

    @Size(max = 2000, message = "完成情况说明不能超过2000字符")
    private String actualCompletion;

    @DecimalMin(value = "0", message = "分数不能小于0")
    @DecimalMax(value = "999.99", message = "分数不能超过999.99")
    private BigDecimal selfScore;

    @DecimalMax(value = "999.9999", message = "自评结果不能超过999.9999")
    private BigDecimal selfResult;

    @Size(max = 500, message = "附件URL不能超过500字符")
    private String attachmentUrl;

    @Size(max = 200, message = "附件名称不能超过200字符")
    private String attachmentName;
}
