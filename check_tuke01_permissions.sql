SELECT p.id, p.role_code, p.data_scope, p.scope_id, p.scope_name 
FROM sys_user_permission p 
JOIN sys_user u ON p.user_id = u.id 
WHERE u.user_name = 'tuke01' AND p.deleted = 0;
