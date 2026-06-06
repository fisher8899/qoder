-- 数据修复脚本：填充 biz_indicator_definition.org_name 为空的记录
-- 根因：旧版本代码未在保存时填充 orgName，现已修复
-- 执行时机：后端重启前执行一次
-- 执行后可验证：SELECT COUNT(*) FROM biz_indicator_definition WHERE org_name IS NULL OR org_name = '';

UPDATE biz_indicator_definition bid
INNER JOIN sys_organization so ON bid.org_id = so.id
SET bid.org_name = so.org_name
WHERE (bid.org_name IS NULL OR bid.org_name = '')
AND bid.deleted = 0;
