package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_organization")
public class SysOrganization {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orgName;
    private String orgCode;
    private Long unitId;
    private String orgType;
    private Integer sortCode;
    private Long deptAdminId;
    private String deptAdminName;
    private Long deptLeaderId;
    private String deptLeaderName;
    private Long supervisorId;
    private String supervisorName;
    private Long assessorId;
    private String assessorName;
    private Integer isEnabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
