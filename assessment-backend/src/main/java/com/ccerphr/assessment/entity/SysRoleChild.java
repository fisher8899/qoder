package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_child")
public class SysRoleChild {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentRoleId;
    private Long childRoleId;

    private LocalDateTime createdTime;
}
