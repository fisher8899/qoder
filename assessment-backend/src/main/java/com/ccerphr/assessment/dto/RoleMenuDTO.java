package com.ccerphr.assessment.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleMenuDTO {
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private List<Long> menuIds;
}
