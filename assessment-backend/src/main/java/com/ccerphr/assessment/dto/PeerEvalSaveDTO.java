package com.ccerphr.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PeerEvalSaveDTO {
    private Long id;

    @NotNull(message = "考核组ID不能为空")
    private Long examGroupId;

    @NotNull(message = "评价单位ID不能为空")
    private Long evaluatorOrgId;

    @NotNull(message = "被评价单位ID不能为空")
    private Long targetOrgId;

    @NotNull(message = "指标ID不能为空")
    private Long indicatorId;

    @NotNull(message = "互评分数不能为空")
    @DecimalMin(value = "0", message = "分数不能小于0")
    @DecimalMax(value = "999.99", message = "分数不能超过999.99")
    private BigDecimal peerScore;

    @Size(max = 1000, message = "评分说明不能超过1000字符")
    private String scoreComment;
}
