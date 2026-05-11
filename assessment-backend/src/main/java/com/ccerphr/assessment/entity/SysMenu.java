package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String menuName;
    private String menuCode;
    private Long parentId;
    private String menuPath;
    private String menuIcon;
    private Integer sortCode;
    private Integer isEnabled;
    private LocalDateTime createdTime;
    @TableLogic
    private Integer deleted;
}
