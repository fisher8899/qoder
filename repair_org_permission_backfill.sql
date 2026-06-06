-- 组织角色权限补偿脚本（幂等）
-- 目标：补齐 sys_user_permission 中 ORG 范围的三类角色权限
-- 角色：
--   DEPT_ADMIN  -> sys_organization.dept_admin_id (sys_employee.id)
--   DEPT_LEADER -> sys_organization.dept_leader_id (sys_employee.id)
--   SUPERVISOR  -> sys_organization.supervisor_id (sys_leader.id -> sys_leader.employee_id)

START TRANSACTION;

-- 1) 部门绩效管理员
INSERT INTO sys_user_permission
(
    user_id, user_name, unit_scope, exam_type, role_code,
    data_scope, scope_id, scope_name, start_date, end_date,
    created_time, updated_time, deleted
)
SELECT
    u.id AS user_id,
    COALESCE(u.real_name, e.employee_name) AS user_name,
    '' AS unit_scope,
    'MONTHLY' AS exam_type,
    'DEPT_ADMIN' AS role_code,
    'ORG' AS data_scope,
    o.id AS scope_id,
    o.org_name AS scope_name,
    CURDATE() AS start_date,
    NULL AS end_date,
    NOW() AS created_time,
    NOW() AS updated_time,
    0 AS deleted
FROM sys_organization o
JOIN sys_employee e ON e.id = o.dept_admin_id AND e.deleted = 0
JOIN sys_user u ON u.employee_id = e.id AND u.deleted = 0
LEFT JOIN sys_user_permission p
       ON p.user_id = u.id
      AND p.role_code = 'DEPT_ADMIN'
      AND p.data_scope = 'ORG'
      AND p.scope_id = o.id
      AND p.deleted = 0
WHERE o.deleted = 0
  AND o.dept_admin_id IS NOT NULL
  AND p.id IS NULL;

-- 2) 部门负责人
INSERT INTO sys_user_permission
(
    user_id, user_name, unit_scope, exam_type, role_code,
    data_scope, scope_id, scope_name, start_date, end_date,
    created_time, updated_time, deleted
)
SELECT
    u.id AS user_id,
    COALESCE(u.real_name, e.employee_name) AS user_name,
    '' AS unit_scope,
    'MONTHLY' AS exam_type,
    'DEPT_LEADER' AS role_code,
    'ORG' AS data_scope,
    o.id AS scope_id,
    o.org_name AS scope_name,
    CURDATE() AS start_date,
    NULL AS end_date,
    NOW() AS created_time,
    NOW() AS updated_time,
    0 AS deleted
FROM sys_organization o
JOIN sys_employee e ON e.id = o.dept_leader_id AND e.deleted = 0
JOIN sys_user u ON u.employee_id = e.id AND u.deleted = 0
LEFT JOIN sys_user_permission p
       ON p.user_id = u.id
      AND p.role_code = 'DEPT_LEADER'
      AND p.data_scope = 'ORG'
      AND p.scope_id = o.id
      AND p.deleted = 0
WHERE o.deleted = 0
  AND o.dept_leader_id IS NOT NULL
  AND p.id IS NULL;

-- 3) 分管领导（注意 supervisor_id 指向 sys_leader.id）
INSERT INTO sys_user_permission
(
    user_id, user_name, unit_scope, exam_type, role_code,
    data_scope, scope_id, scope_name, start_date, end_date,
    created_time, updated_time, deleted
)
SELECT
    u.id AS user_id,
    COALESCE(u.real_name, e.employee_name) AS user_name,
    '' AS unit_scope,
    'MONTHLY' AS exam_type,
    'SUPERVISOR' AS role_code,
    'ORG' AS data_scope,
    o.id AS scope_id,
    o.org_name AS scope_name,
    CURDATE() AS start_date,
    NULL AS end_date,
    NOW() AS created_time,
    NOW() AS updated_time,
    0 AS deleted
FROM sys_organization o
JOIN sys_leader l ON l.id = o.supervisor_id AND l.deleted = 0
JOIN sys_employee e ON e.id = l.employee_id AND e.deleted = 0
JOIN sys_user u ON u.employee_id = e.id AND u.deleted = 0
LEFT JOIN sys_user_permission p
       ON p.user_id = u.id
      AND p.role_code = 'SUPERVISOR'
      AND p.data_scope = 'ORG'
      AND p.scope_id = o.id
      AND p.deleted = 0
WHERE o.deleted = 0
  AND o.supervisor_id IS NOT NULL
  AND p.id IS NULL;

COMMIT;

-- 验证：查 tuke01 是否已有“分管领导 + 党建工作部”
-- SELECT p.id, u.username, p.role_code, p.data_scope, p.scope_id, p.scope_name
-- FROM sys_user_permission p
-- JOIN sys_user u ON u.id = p.user_id
-- WHERE u.username = 'tuke01'
--   AND p.role_code = 'SUPERVISOR'
--   AND p.data_scope = 'ORG'
--   AND p.scope_name = '党建工作部'
--   AND p.deleted = 0;
