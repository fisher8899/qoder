package com.ccerphr.assessment.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过64字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码不能超过128字符")
    private String password;

    private String roleCode;
    private Long orgId;
    private String userName;
}
