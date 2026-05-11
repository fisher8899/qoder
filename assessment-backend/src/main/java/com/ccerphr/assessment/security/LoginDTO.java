package com.ccerphr.assessment.security;

import lombok.Data;

@Data
public class LoginDTO {
    private String roleCode;
    private Long orgId;
    private String userName;
}
