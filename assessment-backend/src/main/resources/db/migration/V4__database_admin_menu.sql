INSERT INTO sys_menu (menu_name, menu_code, menu_category, parent_id, menu_path, sort_code, is_enabled)
SELECT '数据库查询', 'DB_BROWSER', 'SYSTEM',
       COALESCE((SELECT parent.id FROM sys_menu parent WHERE parent.menu_code = 'SYS_OPS' AND parent.deleted = 0 LIMIT 1), 0),
       '/admin/db-browser', 2, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'DB_BROWSER' AND existing.deleted = 0
);

INSERT INTO sys_role_menu (role_id, menu_id, sort_code)
SELECT r.id, m.id, 5
FROM sys_role r
JOIN sys_menu m ON m.menu_code = 'DB_BROWSER' AND m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu existing
      WHERE existing.role_id = r.id
        AND existing.menu_id = m.id
  );
