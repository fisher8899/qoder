-- 迁移脚本：为 sys_notification 表增加 role_code 和 org_id 字段
-- 执行时间：2026-05-12

ALTER TABLE sys_notification
    ADD COLUMN role_code VARCHAR(50) COMMENT '目标职责编码(如DEPT_LEADER)' AFTER unit_id,
    ADD COLUMN org_id BIGINT COMMENT '目标部门ID(职责对应的部门)' AFTER role_code;

-- 创建索引
CREATE INDEX idx_notification_role_org ON sys_notification(role_code, org_id);
