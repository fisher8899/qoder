package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_permission")
public class SysUserPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    /**
     * 系统登录用户名（非持久化，从sys_user关联查询）
     */
    @TableField(exist = false)
    private String username;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    private String unitScope;
    private String examType;
    private String roleCode;
    private String dataScope;
    /**
     * 数据范围对应的ID（单位ID或组织ID，ALL时为0）
     */
    private Long scopeId;
    /**
     * 数据范围名称（冗余展示用，如"图克分公司"、"安全健康环保部"）
     */
    private String scopeName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endDate;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
