-- 单位初始化
INSERT IGNORE INTO sys_unit (unit_name, unit_code, unit_type, is_enabled, created_by) VALUES
('中煤鄂能化能源化工有限公司', 'EM-001', '公司', 1, '管理员'),
('图克分公司', 'TK-002', '分公司', 1, '管理员'),
('乌审召分公司', 'WSZ-003', '分公司', 1, '管理员'),
('水务分公司', 'SW-004', '分公司', 1, '管理员');

-- 考核组织初始化 - 14个机关部门 + 3个分公司
INSERT IGNORE INTO sys_organization (org_name, org_code, unit_id, org_type, sort_code) VALUES
('安全健康环保部', 'AQJKHBB', 1, '职能部门', 1),
('党建工作部', 'DJGZB', 1, '职能部门', 2),
('法律及合规部', 'FLJHGB', 1, '职能部门', 3),
('工程管理中心', 'GCGLZX', 1, '职能部门', 4),
('供销管理中心', 'GXGLZX', 1, '职能部门', 5),
('规划发展部', 'GHFZB', 1, '职能部门', 6),
('后勤服务中心', 'HQFWZX', 1, '职能部门', 7),
('机械动力部', 'JXDLB', 1, '职能部门', 8),
('计划财务部', 'JHCWB', 1, '职能部门', 9),
('纪委机关', 'JWJG', 1, '职能部门', 10),
('人力资源部', 'RLZYB', 1, '职能部门', 11),
('生产技术部', 'SCJSB', 1, '职能部门', 12),
('消气防管理中心', 'XQFGLZX', 1, '职能部门', 13),
('综合办公室', 'ZHBGS', 1, '职能部门', 14),
('图克分公司', 'TKFGS', 2, '分公司', 15),
('乌审召分公司', 'WSZFGS', 3, '分公司', 16),
('水务分公司', 'SWFGS', 4, '分公司', 17);

-- 9个指标大类初始化
INSERT IGNORE INTO sys_indicator_category (category_name, category_code, sort_code, applicable_scope) VALUES
('经营指标', 'BUSINESS', 1, '分公司'),
('重点任务', 'KEY_TASK', 2, '分公司'),
('重点工作', 'KEY_WORK', 1, '职能部门'),
('基础工作', 'BASIC_WORK', 2, '职能部门'),
('动态督办事项', 'DYNAMIC', 3, '通用'),
('党建考核', 'PARTY', 4, '通用'),
('控制指标', 'CONTROL', 5, '通用'),
('特殊贡献指标', 'SPECIAL', 6, '通用'),
('否决项目', 'VETO', 7, '通用');

-- 角色初始化
INSERT IGNORE INTO sys_role (role_name, role_code, description) VALUES
('系统管理员', 'ADMIN', '系统全局管理'),
('计划财务处业绩考核管理员', 'EXAM_ADMIN', '考核全流程管理'),
('部门绩效管理员', 'DEPT_ADMIN', '部门指标和自评管理'),
('部门负责人', 'DEPT_LEADER', '指标审批'),
('分管领导/考核员', 'SUPERVISOR', '他评打分和申诉处理');

-- 菜单初始化
INSERT IGNORE INTO sys_menu (menu_name, menu_code, parent_id, menu_path, sort_code) VALUES
-- 系统管理员菜单
('系统设置', 'SYS_SETTING', 0, '', 1),
('单位管理', 'UNIT_MGMT', 1, '/admin/unit', 1),
('分管领导维护', 'LEADER_MGMT', 1, '/admin/leader', 2),
('权限分配管理', 'PERM_MGMT', 1, '/admin/permission', 3),
('考核配置', 'EXAM_CONFIG', 0, '', 2),
('考核组织管理', 'ORG_MGMT', 5, '/admin/organization', 1),
('指标大类管理', 'CATEGORY_MGMT', 5, '/admin/indicator-category', 2),
('系统运维', 'SYS_OPS', 0, '', 3),
('功能/菜单定义', 'MENU_MGMT', 8, '/admin/menu', 1),
('职责定义', 'ROLE_MGMT', 8, '/admin/role', 2),
('数据同步', 'DATA_SYNC', 8, '/admin/data-sync', 3),
-- 考核管理员菜单
('考核管理', 'EXAM_MGMT', 0, '', 10),
('考核组管理', 'GROUP_MGMT', 12, '/exam/group', 1),
('业绩指标审批', 'INDICATOR_APPROVAL', 12, '/exam/indicator-approval', 2),
('月度考核管理', 'MONTHLY_EXAM', 12, '/exam/monthly', 3),
('复核评估', 'REVIEW', 12, '/exam/review', 4),
('结果管理', 'RESULT_MGMT', 0, '', 11),
('申诉管理', 'APPEAL_MGMT', 17, '/exam/appeal', 1),
('考核进度查询', 'PROGRESS_QUERY', 17, '/exam/progress', 2),
('考核结果查询', 'RESULT_QUERY', 17, '/exam/result', 3),
-- 部门绩效管理员菜单
('考核管理', 'DEPT_EXAM', 0, '', 20),
('业绩指标设定', 'INDICATOR_SET', 21, '/dept/indicator-set', 1),
('月度考核自评', 'SELF_EVAL', 21, '/dept/self-eval', 2),
('部门他评打分', 'PEER_EVAL', 21, '/dept/peer-eval', 3),
('反馈查询', 'DEPT_FEEDBACK', 0, '', 21),
('申诉反馈', 'APPEAL_FEEDBACK', 25, '/dept/appeal-feedback', 1),
('考核结果查询', 'DEPT_RESULT', 25, '/dept/result', 2),
-- 部门负责人菜单
('审批管理', 'APPROVAL_MGMT', 0, '', 30),
('指标审批', 'INDICATOR_APPROVE', 28, '/leader/indicator-approve', 1),
('考核结果', 'LEADER_RESULT', 0, '', 31),
('考核结果查看', 'LEADER_RESULT_VIEW', 30, '/leader/result', 1),
-- 分管领导菜单
('考核评估', 'SUPERVISOR_EVAL', 0, '', 40),
('评估打分', 'EVAL_SCORE', 32, '/supervisor/eval-score', 1),
('考核进度查询', 'SUPERVISOR_PROGRESS', 32, '/supervisor/progress', 2),
('申诉处理', 'SUPERVISOR_APPEAL', 0, '', 41),
('申诉重新评估', 'APPEAL_REEVAL', 35, '/supervisor/appeal-reeval', 1),
('查询统计', 'SUPERVISOR_STAT', 0, '', 42),
('历史考核查询', 'HISTORY_QUERY', 37, '/supervisor/history', 1);

-- 字典数据
INSERT IGNORE INTO sys_dict (dict_type, dict_code, dict_label, sort_code) VALUES
('exam_category', 'INDICATOR_SET', '业绩指标设定', 1),
('exam_category', 'PERFORMANCE', '绩效考核', 2),
('exam_type', 'MONTHLY', '月度考核', 1),
('exam_type', 'ANNUAL', '年度考核', 2),
('approval_status', 'DRAFT', '草稿', 1),
('approval_status', 'PENDING_DEPT_LEADER', '待部门负责人审批', 2),
('approval_status', 'PENDING_SUPERVISOR', '待分管领导审批', 3),
('approval_status', 'PENDING_FINANCE', '待财务处审批', 4),
('approval_status', 'APPROVED', '审批通过', 5),
('approval_status', 'REJECTED', '被退回', 6),
('exam_status', 'NOT_STARTED', '待启动', 1),
('exam_status', 'IN_PROGRESS', '进行中', 2),
('exam_status', 'COMPLETED', '已完成', 3),
('exam_status', 'PRE_PUBLISHED', '预发布', 4),
('exam_status', 'PUBLISHED', '已发布', 5),
('appeal_status', 'DRAFT', '草稿', 1),
('appeal_status', 'PENDING_REEVAL', '待重新评估', 2),
('appeal_status', 'PROCESSED', '已处理', 3),
('unit_type', 'COMPANY', '公司', 1),
('unit_type', 'BRANCH', '分公司', 2),
('org_type', 'FUNCTIONAL', '职能部门', 1),
('org_type', 'BRANCH', '分公司', 2);

-- 测试用户（密码均为123456）
INSERT IGNORE INTO sys_user (username, password, real_name, role_code, role_name, org_id, org_name, unit_id) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'ADMIN', '系统管理员', NULL, NULL, 1),
('wangfang', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王芳', 'EXAM_ADMIN', '计划财务处业绩考核管理员', 9, '计划财务部', 1),
('zhaogang', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵刚', 'DEPT_ADMIN', '部门绩效管理员', 12, '生产技术部', 1),
('zhangjg', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张建国', 'DEPT_LEADER', '部门负责人', 12, '生产技术部', 1),
('wangjg', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王建国', 'SUPERVISOR', '分管领导/考核员', 14, '综合办公室', 1);

-- 人员数据初始化（28人）
INSERT IGNORE INTO sys_employee (employee_no, employee_name, dept_id, dept_name, unit_id, position, level) VALUES
-- 14个总公司部门
('EMP-001-01', '李安全', 1, '安全健康环保部', 1, '安全主管', '中层'),
('EMP-001-02', '周环保', 1, '安全健康环保部', 1, '环保专员', '一般'),
('EMP-002-01', '陈党建', 2, '党建工作部', 1, '党务主任', '中层'),
('EMP-003-01', '刘法务', 3, '法律及合规部', 1, '法务经理', '中层'),
('EMP-004-01', '孙工程', 4, '工程管理中心', 1, '工程总监', '高层'),
('EMP-005-01', '杨工会', 5, '供销管理中心', 1, '工会主席', '中层'),
('EMP-006-01', '吴技术', 6, '规划发展部', 1, '技术主管', '中层'),
('EMP-007-01', '郑纪检', 7, '后勤服务中心', 1, '纪检专员', '一般'),
('EMP-008-01', '钱经营', 8, '机械动力部', 1, '经营主管', '中层'),
('EMP-009-01', '王芳', 9, '计划财务部', 1, '财务主管', '中层'),
('EMP-009-02', '林财务', 9, '计划财务部', 1, '会计', '一般'),
('EMP-010-01', '何科技', 10, '纪委机关', 1, '科技主任', '中层'),
('EMP-011-01', '马人资', 11, '人力资源部', 1, '人资经理', '中层'),
('EMP-012-01', '赵刚', 12, '生产技术部', 1, '生产主管', '中层'),
('EMP-012-02', '张建国', 12, '生产技术部', 1, '技术总监', '高层'),
('EMP-013-01', '黄审计', 13, '消气防管理中心', 1, '审计主管', '中层'),
('EMP-014-01', '王建国', 14, '综合办公室', 1, '分管领导', '高层'),
('EMP-014-02', '许综合', 14, '综合办公室', 1, '行政专员', '一般'),
('EMP-014-03', '系统管理员', 14, '综合办公室', 1, '系统管理岗', '中层'),
-- 3个分公司各3人
('EMP-015-01', '田图克', 15, '图克分公司', 2, '分公司经理', '高层'),
('EMP-015-02', '冯运营', 15, '图克分公司', 2, '运营主管', '中层'),
('EMP-015-03', '蒋安全', 15, '图克分公司', 2, '安全员', '一般'),
('EMP-016-01', '韩乌审', 16, '乌审召分公司', 3, '分公司经理', '高层'),
('EMP-016-02', '曹生产', 16, '乌审召分公司', 3, '生产主管', '中层'),
('EMP-016-03', '邓技术', 16, '乌审召分公司', 3, '技术员', '一般'),
('EMP-017-01', '谢水务', 17, '水务分公司', 4, '分公司经理', '高层'),
('EMP-017-02', '梁运维', 17, '水务分公司', 4, '运维主管', '中层'),
('EMP-017-03', '丁水处理', 17, '水务分公司', 4, '水处理工程师', '一般');

-- 更新 sys_user 关联 employee_id
UPDATE sys_user SET employee_id = 19 WHERE username = 'admin';
UPDATE sys_user SET employee_id = 10 WHERE username = 'wangfang';
UPDATE sys_user SET employee_id = 14 WHERE username = 'zhaogang';
UPDATE sys_user SET employee_id = 15 WHERE username = 'zhangjg';
UPDATE sys_user SET employee_id = 17 WHERE username = 'wangjg';

-- ========================================
-- 考核组及指标测试数据
-- ========================================

-- 考核组初始化（关联现有数据）
-- 注意：考核组 id=4 和 id=5 已通过系统界面创建，此处仅确保状态正确
UPDATE biz_exam_group SET status = 'IN_PROGRESS', current_step = '已启动', updated_time = NOW() WHERE id = 4 AND deleted = 0;
UPDATE biz_exam_group SET status = 'IN_PROGRESS', current_step = '已启动', updated_time = NOW() WHERE id = 5 AND deleted = 0;

-- 生产技术部(orgId=12)业绩指标 - 关联考核组4（INDICATOR_SET）
INSERT IGNORE INTO biz_indicator_definition (exam_group_id, org_id, org_name, category_id, category_name, sub_category, content, target_desc, weight_annual, weight_monthly, evaluation_standard, sort_code, approval_status, exam_target_type, created_time, updated_time, deleted) VALUES
(4, 12, '生产技术部', 3, '重点工作', '产量管理', '完成月度生产计划', '按月完成公司下达的生产计划任务，产量达标率100%', 20.00, 20.00, '月度生产计划完成率100%得满分，每低1%扣1分，低于90%不得分', 1, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0),
(4, 12, '生产技术部', 3, '重点工作', '质量管理', '控制产品不合格率', '产品一次合格率≥98%，不合格品返工率≤2%', 15.00, 15.00, '合格率≥98%得满分，每低0.5%扣1分，低于95%不得分', 2, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0),
(4, 12, '生产技术部', 7, '控制指标', '安全生产', '确保安全生产零事故', '实现全年安全生产零事故，安全培训覆盖率100%', 20.00, 20.00, '零事故得满分，发生一般事故扣10分，发生较大事故不得分', 3, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0),
(4, 12, '生产技术部', 4, '基础工作', '技术创新', '完成技术改进项目', '按计划完成年度技术改进项目，通过验收', 25.00, 25.00, '按时完成并通过验收得满分，延期1项扣5分，未完成不得分', 4, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0),
(4, 12, '生产技术部', 4, '基础工作', '团队建设', '完成部门培训计划', '按计划完成部门年度培训任务，培训覆盖率100%', 20.00, 20.00, '培训完成率100%得满分，每低5%扣2分，低于80%不得分', 5, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0);

-- 计划财务部(orgId=9)业绩指标 - 关联考核组4（INDICATOR_SET）
INSERT IGNORE INTO biz_indicator_definition (exam_group_id, org_id, org_name, category_id, category_name, sub_category, content, target_desc, weight_annual, weight_monthly, evaluation_standard, sort_code, approval_status, exam_target_type, created_time, updated_time, deleted) VALUES
(4, 9, '计划财务部', 3, '重点工作', '预算执行', '完成年度预算执行率', '年度预算执行率≥95%，无超预算支出', 25.00, 25.00, '执行率≥95%得满分，每低1%扣1分，低于85%不得分', 1, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0),
(4, 9, '计划财务部', 3, '重点工作', '成本控制', '降低运营成本', '运营成本较上年下降5%以上', 20.00, 20.00, '成本下降≥5%得满分，每少1%扣2分，未下降不得分', 2, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0),
(4, 9, '计划财务部', 4, '基础工作', '考核组织', '按时完成月度考核组织工作', '每月5日前完成上月考核组织及汇总工作', 30.00, 30.00, '按时完成得满分，每延期1天扣2分，延期5天以上不得分', 3, 'DRAFT', 'DEPARTMENT', NOW(), NOW(), 0);

-- 用户角色关联初始化
INSERT IGNORE INTO sys_user_role (user_id, role_code, role_name) VALUES
(1, 'ADMIN', '系统管理员'),
(2, 'EXAM_ADMIN', '计划财务处业绩考核管理员'),
(3, 'DEPT_ADMIN', '部门绩效管理员'),
(3, 'DEPT_LEADER', '部门负责人'),
(4, 'DEPT_LEADER', '部门负责人'),
(5, 'SUPERVISOR', '分管领导/考核员');
