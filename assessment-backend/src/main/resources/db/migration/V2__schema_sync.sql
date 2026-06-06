-- 将历史手工 schema 修正纳入 Flyway 受控迁移。
-- 说明：
-- 1. 既有环境通过 baseline 接管后执行本脚本。
-- 2. 该脚本兼容已手工执行过部分变更的环境。

DROP PROCEDURE IF EXISTS safe_add_column;
DROP PROCEDURE IF EXISTS safe_add_index;
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

DELIMITER //
CREATE PROCEDURE safe_add_index(
    IN table_name_param VARCHAR(100),
    IN index_name_param VARCHAR(100),
    IN index_definition VARCHAR(500)
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND INDEX_NAME = index_name_param;

    IF index_exists = 0 THEN
        SET @index_sql = CONCAT('CREATE INDEX ', index_name_param, ' ON ', table_name_param, ' ', index_definition);
        PREPARE stmt FROM @index_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL safe_add_column('biz_exam_group', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_exam_group_member', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_indicator_definition', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_indicator_definition', 'sub_category_id', 'BIGINT COMMENT "指标小类ID" AFTER category_name');
CALL safe_add_column('biz_self_evaluation', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_peer_evaluation', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_review_score', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_monthly_score', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('biz_appeal', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER id');
CALL safe_add_column('sys_role', 'role_type', 'VARCHAR(20) COMMENT "职责类型：SYSTEM/UNIT/DEPT" AFTER description');
CALL safe_add_column('sys_indicator_category', 'unit_id', 'BIGINT COMMENT "所属单位ID" AFTER evaluation_standard');
CALL safe_add_column('sys_user_permission', 'scope_id', 'BIGINT COMMENT "范围ID" AFTER data_scope');
CALL safe_add_column('sys_user_permission', 'scope_name', 'VARCHAR(100) COMMENT "范围名称" AFTER scope_id');
CALL safe_add_column('sys_role_menu', 'sort_code', 'INT DEFAULT 0 COMMENT "排序编码" AFTER menu_id');
CALL safe_add_column('sys_notification', 'role_code', 'VARCHAR(50) COMMENT "目标职责编码" AFTER unit_id');
CALL safe_add_column('sys_notification', 'org_id', 'BIGINT COMMENT "目标部门ID" AFTER role_code');

CREATE TABLE IF NOT EXISTS sys_role_child (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_role_id BIGINT NOT NULL COMMENT '父职责ID',
    child_role_id BIGINT NOT NULL COMMENT '子职责ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent_role (parent_role_id),
    INDEX idx_child_role (child_role_id)
) COMMENT '职责子角色关联表';

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
    sort_code INT DEFAULT 0 COMMENT '排序编码',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_indicator_sub_owner (exam_group_id, org_id, category_id, deleted),
    INDEX idx_indicator_sub_name (exam_group_id, org_id, category_name, sub_category_name, deleted)
) COMMENT '指标小类表';

CALL safe_add_index('sys_notification', 'idx_notification_role_org', '(role_code, org_id)');

UPDATE sys_role SET role_type = 'SYSTEM' WHERE role_code = 'ADMIN' AND (role_type IS NULL OR role_type = '');
UPDATE sys_role SET role_type = 'UNIT' WHERE role_code IN ('FIN_ADMIN', 'EXAM_ADMIN') AND (role_type IS NULL OR role_type = '');
UPDATE sys_role SET role_type = 'DEPT' WHERE role_code IN ('DEPT_ADMIN', 'DEPT_LEADER', 'SUPERVISOR') AND (role_type IS NULL OR role_type = '');

UPDATE biz_exam_group eg
SET unit_id = (
    SELECT o.unit_id
    FROM sys_organization o
    INNER JOIN biz_exam_group_member m ON m.org_id = o.id AND m.exam_group_id = eg.id
    LIMIT 1
)
WHERE unit_id IS NULL
  AND id IN (SELECT exam_group_id FROM biz_exam_group_member);

UPDATE biz_indicator_definition ind
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = ind.org_id)
WHERE unit_id IS NULL
  AND org_id IS NOT NULL;

INSERT INTO biz_indicator_sub_category (
    unit_id, exam_group_id, org_id, org_name, category_id, category_name,
    sub_category_name, evaluation_standard, sort_code, created_time, updated_time, deleted
)
SELECT
    ind.unit_id,
    ind.exam_group_id,
    ind.org_id,
    MAX(ind.org_name),
    ind.category_id,
    ind.category_name,
    ind.sub_category,
    MAX(ind.evaluation_standard),
    MIN(COALESCE(ind.sort_code, 0)),
    NOW(),
    NOW(),
    0
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

UPDATE biz_self_evaluation se
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = se.org_id)
WHERE unit_id IS NULL
  AND org_id IS NOT NULL;

UPDATE biz_peer_evaluation pe
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = pe.evaluator_org_id)
WHERE unit_id IS NULL
  AND evaluator_org_id IS NOT NULL;

UPDATE biz_review_score rs
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = rs.org_id)
WHERE unit_id IS NULL
  AND org_id IS NOT NULL;

UPDATE biz_monthly_score ms
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = ms.org_id)
WHERE unit_id IS NULL
  AND org_id IS NOT NULL;

UPDATE biz_appeal a
SET unit_id = (SELECT unit_id FROM sys_organization WHERE id = a.appeal_org_id)
WHERE unit_id IS NULL
  AND appeal_org_id IS NOT NULL;

DROP PROCEDURE IF EXISTS safe_add_column;
DROP PROCEDURE IF EXISTS safe_add_index;
