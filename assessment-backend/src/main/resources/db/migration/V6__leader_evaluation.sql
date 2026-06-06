-- V6: 分管领导评估打分表
CREATE TABLE IF NOT EXISTS biz_leader_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT COMMENT '所属单位ID',
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    leader_id BIGINT NOT NULL COMMENT '分管领导ID（sys_leader.id）',
    target_org_id BIGINT NOT NULL COMMENT '被考核部门ID',
    target_org_name VARCHAR(100) COMMENT '被考核部门名称',
    indicator_id BIGINT NOT NULL COMMENT '指标项ID',
    leader_score DECIMAL(5,2) COMMENT '领导打分',
    leader_result DECIMAL(8,4) COMMENT '领导打分结果（加权后）',
    score_comment VARCHAR(500) COMMENT '打分说明',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿/SUBMITTED-已提交',
    submitted_by VARCHAR(50) COMMENT '提交人',
    submitted_time DATETIME COMMENT '提交时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_leader_eval (exam_group_id, leader_id, target_org_id, indicator_id)
) COMMENT '分管领导评估打分表';
