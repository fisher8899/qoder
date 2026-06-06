-- 清理“图克分公司”误落到旧组织ID的数据
-- 当前有效组织：
--   sys_organization.id = 24, org_name = '图克分公司', unit_id = 1, deleted = 0
-- 历史错误数据：
--   biz_indicator_definition / biz_indicator_sub_category 中存在 org_id = 9 且 org_name = '图克分公司'
--   org_id = 9 实际是“计划财务部”，属于错误归属数据

START TRANSACTION;

DELETE FROM biz_indicator_definition
WHERE exam_group_id = 9
  AND org_id = 9
  AND org_name = '图克分公司';

DELETE FROM biz_indicator_sub_category
WHERE exam_group_id = 9
  AND org_id = 9
  AND org_name = '图克分公司';

COMMIT;
