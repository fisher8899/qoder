-- 菜单类别改造：SYSTEM=全部/系统管理员，UNIT=单位类，DEPT=部门类。
-- menu_category 支持逗号分隔多选，用于职责定义时控制可分配菜单范围。

DROP PROCEDURE IF EXISTS safe_add_column;
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

CALL safe_add_column('sys_menu', 'menu_category', 'VARCHAR(50) DEFAULT "SYSTEM" COMMENT "菜单类别：SYSTEM=全部/系统, UNIT=单位类, DEPT=部门类，可逗号分隔多选" AFTER menu_code');

INSERT INTO sys_menu (menu_name, menu_code, menu_category, parent_id, menu_path, sort_code, is_enabled)
SELECT '系统用户管理', 'USER_MGMT', 'UNIT',
       COALESCE((SELECT parent.id FROM sys_menu parent WHERE parent.menu_code = 'SYS_SETTING' AND parent.deleted = 0 LIMIT 1), 0),
       '/admin/user', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'USER_MGMT' AND existing.deleted = 0);

INSERT INTO sys_menu (menu_name, menu_code, menu_category, parent_id, menu_path, sort_code, is_enabled)
SELECT '人员管理', 'EMPLOYEE_MGMT', 'UNIT',
       COALESCE((SELECT parent.id FROM sys_menu parent WHERE parent.menu_code = 'EXAM_CONFIG' AND parent.deleted = 0 LIMIT 1), 0),
       '/admin/employee', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'EMPLOYEE_MGMT' AND existing.deleted = 0);

INSERT INTO sys_menu (menu_name, menu_code, menu_category, parent_id, menu_path, sort_code, is_enabled)
SELECT '指标设定进度查询', 'INDICATOR_PROGRESS', 'UNIT',
       COALESCE((SELECT parent.id FROM sys_menu parent WHERE parent.menu_code = 'RESULT_MGMT' AND parent.deleted = 0 LIMIT 1), 0),
       '/exam/indicator-progress', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'INDICATOR_PROGRESS' AND existing.deleted = 0);

UPDATE sys_menu SET menu_category = 'SYSTEM,UNIT' WHERE menu_code = 'SYS_SETTING' AND deleted = 0;
UPDATE sys_menu SET menu_category = 'SYSTEM' WHERE menu_code IN ('UNIT_MGMT', 'SYS_OPS', 'MENU_MGMT', 'ROLE_MGMT', 'DATA_SYNC') AND deleted = 0;
UPDATE sys_menu SET menu_category = 'UNIT' WHERE menu_code IN (
    'USER_MGMT',
    'LEADER_MGMT',
    'PERM_MGMT',
    'EXAM_CONFIG',
    'ORG_MGMT',
    'EMPLOYEE_MGMT',
    'CATEGORY_MGMT',
    'EXAM_MGMT',
    'GROUP_MGMT',
    'MONTHLY_EXAM',
    'REVIEW',
    'RESULT_MGMT',
    'APPEAL_MGMT',
    'INDICATOR_PROGRESS',
    'PROGRESS_QUERY',
    'RESULT_QUERY'
) AND deleted = 0;
UPDATE sys_menu SET menu_category = 'DEPT' WHERE menu_code IN (
    'DEPT_EXAM',
    'INDICATOR_SET',
    'SELF_EVAL',
    'PEER_EVAL',
    'DEPT_FEEDBACK',
    'APPEAL_FEEDBACK',
    'DEPT_RESULT',
    'APPROVAL_MGMT',
    'INDICATOR_APPROVAL',
    'INDICATOR_APPROVE',
    'LEADER_RESULT',
    'LEADER_RESULT_VIEW',
    'SUPERVISOR_EVAL',
    'EVAL_SCORE',
    'SUPERVISOR_PROGRESS',
    'SUPERVISOR_APPEAL',
    'APPEAL_REEVAL',
    'SUPERVISOR_STAT',
    'HISTORY_QUERY'
) AND deleted = 0;

UPDATE sys_menu SET sort_code = 1 WHERE menu_code = 'UNIT_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 2 WHERE menu_code = 'USER_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 3 WHERE menu_code = 'LEADER_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 4 WHERE menu_code = 'PERM_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 1 WHERE menu_code = 'ORG_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 2 WHERE menu_code = 'EMPLOYEE_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 3 WHERE menu_code = 'CATEGORY_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 1 WHERE menu_code = 'APPEAL_MGMT' AND deleted = 0;
UPDATE sys_menu SET sort_code = 2 WHERE menu_code = 'INDICATOR_PROGRESS' AND deleted = 0;
UPDATE sys_menu SET sort_code = 3 WHERE menu_code = 'PROGRESS_QUERY' AND deleted = 0;
UPDATE sys_menu SET sort_code = 4 WHERE menu_code = 'RESULT_QUERY' AND deleted = 0;

DROP PROCEDURE IF EXISTS safe_add_column;
