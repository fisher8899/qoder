-- Schema同步迁移脚本 - 补充实体类定义但Schema缺失的字段
-- 执行时间：2026-05-14
-- 注意：MySQL的ALTER TABLE ADD COLUMN不支持IF NOT EXISTS，使用存储过程实现幂等

-- 创建临时存储过程用于安全添加列
DELIMITER //
CREATE PROCEDURE safe_add_column(
    IN table_name_param VARCHAR(100),
    IN column_name_param VARCHAR(100),
    IN column_definition VARCHAR(500)
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND COLUMN_NAME = column_name_param;

    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', table_name_param, ' ADD COLUMN ', column_name_param, ' ', column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 1. biz_exam_group 添加 unit_id
CALL safe_add_column('biz_exam_group', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');

-- 2. biz_exam_group_member 添加 unit_id
CALL safe_add_column('biz_exam_group_member', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');

-- 3. biz_indicator_definition 添加 unit_id
CALL safe_add_column('biz_indicator_definition', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_indicator_definition', 'sub_category_id', 'BIGINT COMMENT "指标小类ID" AFTER category_name');

-- 4. biz_self_evaluation 添加 unit_id
CALL safe_add_column('biz_self_evaluation', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');

-- 5. biz_peer_evaluation 添加 unit_id
CALL safe_add_column('biz_peer_evaluation', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');

-- 6. biz_review_score 添加 unit_id
CALL safe_add_column('biz_review_score', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');

-- 7. biz_monthly_score 添加 unit_id
CALL safe_add_column('biz_monthly_score', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');

-- 8. biz_appeal 添加 unit_id
CALL safe_add_column('biz_appeal', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');

-- 9. sys_role 添加 role_type
CALL safe_add_column('sys_role', 'role_type', 'VARCHAR(20) COMMENT "职责类型：SYSTEM-系统,UNIT-单位,DEPT-部门" AFTER description');

-- 10. sys_indicator_category 添加 unit_id
CALL safe_add_column('sys_indicator_category', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER evaluation_standard');

-- 11. sys_user_permission 添加 scope_id
CALL safe_add_column('sys_user_permission', 'scope_id', 'BIGINT COMMENT "范围ID" AFTER data_scope');

-- 12. sys_user_permission 添加 scope_name
CALL safe_add_column('sys_user_permission', 'scope_name', 'VARCHAR(100) COMMENT "范围名称" AFTER scope_id');

-- 13. sys_role_menu 添加 sort_code
CALL safe_add_column('sys_role_menu', 'sort_code', 'INT DEFAULT 0 COMMENT "排序编码" AFTER menu_id');

-- 14. sys_notification 添加 role_code 和 org_id（如果尚未执行migration_add_notification_fields.sql）
CALL safe_add_column('sys_notification', 'role_code', 'VARCHAR(50) COMMENT "目标职责编码" AFTER unit_id');
CALL safe_add_column('sys_notification', 'org_id', 'BIGINT COMMENT "目标部门ID" AFTER role_code');

-- 15. 创建 sys_role_child 表（如果不存在）
CREATE TABLE IF NOT EXISTS sys_role_child (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_role_id BIGINT NOT NULL COMMENT '父角色ID',
    child_role_id BIGINT NOT NULL COMMENT '子角色ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent_role (parent_role_id),
    INDEX idx_child_role (child_role_id)
) COMMENT '职责子角色关联表';

-- 16. 更新 sys_role 的 role_type 字段
UPDATE sys_role SET role_type = 'SYSTEM' WHERE role_code = 'ADMIN' AND role_type IS NULL;
UPDATE sys_role SET role_type = 'UNIT' WHERE role_code IN ('FIN_ADMIN', 'EXAM_ADMIN') AND role_type IS NULL;
UPDATE sys_role SET role_type = 'DEPT' WHERE role_code IN ('DEPT_ADMIN', 'DEPT_LEADER', 'SUPERVISOR') AND role_type IS NULL;

-- 17. 回填 biz_* 表的 unit_id（根据 org_id 查询 sys_organization.unit_id）
-- biz_exam_group: 从成员关联获取
UPDATE biz_exam_group eg
SET unit_id = (
    SELECT o.unit_id FROM sys_organization o
    INNER JOIN biz_exam_group_member m ON m.org_id = o.id AND m.exam_group_id = eg.id
    LIMIT 1
)
WHERE unit_id IS NULL AND id IN (SELECT exam_group_id FROM biz_exam_group_member);

-- biz_indicator_definition: 直接关联
UPDATE biz_indicator_definition ind
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = ind.org_id)
WHERE unit_id IS NULL AND org_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS biz_indicator_sub_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL COMMENT '所属单位ID',
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    org_id BIGINT NOT NULL COMMENT '归属部门ID',
    org_name VARCHAR(100) COMMENT '部门名称',
    category_id BIGINT COMMENT '指标大类ID',
    category_name VARCHAR(100) NOT NULL COMMENT '指标大类名称',
    sub_category_name VARCHAR(100) NOT NULL COMMENT '指标小类名称',
    evaluation_standard TEXT COMMENT '考核标准',
    sort_code INT DEFAULT 0 COMMENT '排序码',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_indicator_sub_owner (exam_group_id, org_id, category_id, deleted),
    INDEX idx_indicator_sub_name (exam_group_id, org_id, category_name, sub_category_name, deleted)
) COMMENT '指标小类表';

INSERT INTO biz_indicator_sub_category (
    unit_id, exam_group_id, org_id, org_name, category_id, category_name,
    sub_category_name, evaluation_standard, sort_code, created_time, updated_time, deleted
)
SELECT
    ind.unit_id, ind.exam_group_id, ind.org_id, MAX(ind.org_name), ind.category_id, ind.category_name,
    ind.sub_category, MAX(ind.evaluation_standard), MIN(COALESCE(ind.sort_code, 0)), NOW(), NOW(), 0
FROM biz_indicator_definition ind
LEFT JOIN biz_indicator_sub_category sub
  ON sub.exam_group_id = ind.exam_group_id
 AND sub.org_id = ind.org_id
 AND (sub.category_id = ind.category_id OR (sub.category_id IS NULL AND ind.category_id IS NULL))
 AND sub.category_name = ind.category_name
 AND sub.sub_category_name = ind.sub_category
 AND sub.deleted = 0
WHERE ind.deleted = 0
  AND ind.unit_id IS NOT NULL
  AND ind.exam_group_id IS NOT NULL
  AND ind.org_id IS NOT NULL
  AND ind.category_name IS NOT NULL
  AND ind.sub_category IS NOT NULL
  AND sub.id IS NULL
GROUP BY ind.unit_id, ind.exam_group_id, ind.org_id, ind.category_id, ind.category_name, ind.sub_category;

UPDATE biz_indicator_definition ind
JOIN biz_indicator_sub_category sub
  ON sub.exam_group_id = ind.exam_group_id
 AND sub.org_id = ind.org_id
 AND (sub.category_id = ind.category_id OR (sub.category_id IS NULL AND ind.category_id IS NULL))
 AND sub.category_name = ind.category_name
 AND sub.sub_category_name = ind.sub_category
 AND sub.deleted = 0
SET ind.sub_category_id = sub.id
WHERE ind.deleted = 0
  AND ind.sub_category_id IS NULL;

-- biz_self_evaluation: 直接关联
UPDATE biz_self_evaluation se
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = se.org_id)
WHERE unit_id IS NULL AND org_id IS NOT NULL;

-- biz_peer_evaluation: 直接关联
UPDATE biz_peer_evaluation pe
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = pe.evaluator_org_id)
WHERE unit_id IS NULL AND evaluator_org_id IS NOT NULL;

-- biz_review_score: 直接关联
UPDATE biz_review_score rs
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = rs.org_id)
WHERE unit_id IS NULL AND org_id IS NOT NULL;

-- biz_monthly_score: 直接关联
UPDATE biz_monthly_score ms
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = ms.org_id)
WHERE unit_id IS NULL AND org_id IS NOT NULL;

-- biz_appeal: 直接关联
UPDATE biz_appeal a
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = a.appeal_org_id)
WHERE unit_id IS NULL AND appeal_org_id IS NOT NULL;

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS safe_add_column;
