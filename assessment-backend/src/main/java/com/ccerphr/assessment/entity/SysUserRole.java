package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_role")
public class SysUserRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String roleCode;
    private String roleName;
    private LocalDateTime createdTime;
}
