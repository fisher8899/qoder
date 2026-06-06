package com.ccerphr.assessment.service;

import java.util.List;
import java.util.Map;

public interface BizLeaderEvaluationService {

    /**
     * 获取分管领导的评估任务列表（按考核组）
     */
    List<Map<String, Object>> getTaskList(Long employeeId);

    /**
     * 获取某考核组下，该领导负责评估的各部门指标
     */
    List<Map<String, Object>> getIndicatorsByDept(Long examGroupId, Long employeeId);

    /**
     * 保存领导打分（单条）
     */
    void save(Map<String, Object> data);

    /**
     * 根据员工ID查找分管领导ID
     */
    Long findLeaderId(Long employeeId);

    /**
     * 根据员工ID和指标查找用于保存评价记录的分管领导ID
     */
    Long findLeaderIdForIndicator(Long employeeId, Long indicatorId);

    /**
     * 提交领导评估（整个考核组 + 目标部门）
     */
    void submit(Long examGroupId, Long leaderId, Long targetOrgId, String submittedBy);
}
