package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_dict")
public class SysDict {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictType;
    private String dictCode;
    private String dictLabel;
    private Integer sortCode;
    private Integer isEnabled;
    private LocalDateTime createdTime;
    @TableLogic
    private Integer deleted;
}
