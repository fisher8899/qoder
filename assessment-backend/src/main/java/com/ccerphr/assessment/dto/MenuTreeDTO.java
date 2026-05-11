package com.ccerphr.assessment.dto;

import lombok.Data;

import java.util.List;

@Data
public class MenuTreeDTO {
    private Long id;
    private String menuName;
    private String menuCode;
    private Long parentId;
    private String menuPath;
    private String menuIcon;
    private Integer sortCode;
    private Integer isEnabled;
    private List<MenuTreeDTO> children;
}
