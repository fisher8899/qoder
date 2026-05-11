package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    /**
     * 职责类型：SYSTEM-系统, UNIT-单位, DEPT-部门
     */
    private String roleType;
    private LocalDateTime createdTime;
    @TableLogic
    private Integer deleted;
}
