package com.ccerphr.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_indicator_sub_category")
public class BizIndicatorSubCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long unitId;
    private Long examGroupId;
    private Long orgId;
    private String orgName;
    private Long categoryId;
    private String categoryName;
    private String subCategoryName;
    private String evaluationStandard;
    private Integer sortCode;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}
