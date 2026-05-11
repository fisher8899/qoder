package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_employee")
public class SysEmployee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String employeeNo;
    private String employeeName;
    private Long deptId;
    private String deptName;
    private Long unitId;
    private String position;
    private String level;
    private Integer isActive;
    private Integer isInvalid;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
