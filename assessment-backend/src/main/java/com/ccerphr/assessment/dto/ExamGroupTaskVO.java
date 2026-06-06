package com.ccerphr.assessment.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 考核组任务视图对象 —— 用于部门绩效管理员查看所属考核组任务
 */
@Data
public class ExamGroupTaskVO {
    /** 考核组ID */
    private Long examGroupId;
    /** 考核组名称 */
    private String examGroupName;
    /** 考核类型 */
    private String examType;
    /** 考核模版 */
    private String examCategory;
    /** 考核开始日期 */
    private LocalDate startDate;
    /** 考核结束日期 */
    private LocalDate endDate;
    /** 考核组状态 */
    private String status;
    /** 部门ID */
    private Long orgId;
    /** 部门名称 */
    private String orgName;
    /** 所属单位ID */
    private Long unitId;
    /** 指标审批状态（从指标数据聚合；无指标时为 DRAFT） */
    private String approvalStatus;
}
