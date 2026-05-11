package com.ccerphr.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppealHandleDTO {
    private Long appealId;

    @NotNull(message = "新评分不能为空")
    @DecimalMin(value = "0", message = "分数不能小于0")
    @DecimalMax(value = "999.99", message = "分数不能超过999.99")
    private BigDecimal newScore;

    @Size(max = 1000, message = "处理说明不能超过1000字符")
    private String handleComment;
}
