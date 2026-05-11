package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_exam_group")
public class BizExamGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属单位ID
     */
    private Long unitId;
    private String groupName;
    private String examCategory;
    private String examType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer progress;
    private String status;
    private String currentStep;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
