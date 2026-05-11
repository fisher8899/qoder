-- 1. 单位表
CREATE TABLE IF NOT EXISTS sys_unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_name VARCHAR(100) NOT NULL COMMENT '单位名称',
    unit_code VARCHAR(50) NOT NULL UNIQUE COMMENT '单位编码',
    unit_type VARCHAR(20) NOT NULL COMMENT '单位类型：公司/分公司',
    is_enabled TINYINT DEFAULT 1 COMMENT '是否启用：1启用 0禁用',
    created_by VARCHAR(50) COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expire_date DATE COMMENT '失效日期',
    deleted TINYINT DEFAULT 0
) COMMENT '单位表';

-- 2. 分管领导表
CREATE TABLE IF NOT EXISTS sys_leader (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL COMMENT '所属单位ID',
    employee_id BIGINT COMMENT '关联人员ID',
    leader_name VARCHAR(50) NOT NULL COMMENT '领导姓名',
    leader_level VARCHAR(20) COMMENT '职级',
    effective_date DATE COMMENT '生效日期',
    expire_date DATE COMMENT '失效日期',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '分管领导表';

-- 3. 用户权限表
CREATE TABLE IF NOT EXISTS sys_user_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    user_name VARCHAR(50) COMMENT '用户姓名',
    unit_scope VARCHAR(500) COMMENT '单位权限范围(JSON数组)',
    exam_type VARCHAR(100) COMMENT '考核类型',
    role_code VARCHAR(50) COMMENT '分配角色编码',
    data_scope VARCHAR(500) COMMENT '数据范围: ALL=全部, 或逗号分隔的组织名称',
    start_date DATE DEFAULT (CURDATE()) COMMENT '生效日期',
    end_date DATE DEFAULT NULL COMMENT '失效日期',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '用户权限表';

-- 4. 考核组织表
CREATE TABLE IF NOT EXISTS sys_organization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    org_name VARCHAR(100) NOT NULL COMMENT '组织名称',
    org_code VARCHAR(50) NOT NULL COMMENT '组织编码',
    unit_id BIGINT COMMENT '所属单位ID',
    org_type VARCHAR(20) COMMENT '组织类别：职能部门/分公司',
    sort_code INT DEFAULT 0 COMMENT '排序',
    dept_admin_id BIGINT COMMENT '部门绩效管理员用户ID',
    dept_admin_name VARCHAR(50) COMMENT '部门绩效管理员姓名',
    dept_leader_id BIGINT COMMENT '部门负责人用户ID',
    dept_leader_name VARCHAR(50) COMMENT '部门负责人姓名',
    supervisor_id BIGINT COMMENT '分管领导ID',
    supervisor_name VARCHAR(50) COMMENT '分管领导姓名',
    assessor_id BIGINT COMMENT '考核员ID',
    assessor_name VARCHAR(50) COMMENT '考核员姓名',
    is_enabled TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '考核组织表';

-- 5. 指标大类表
CREATE TABLE IF NOT EXISTS sys_indicator_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL COMMENT '大类名称',
    category_code VARCHAR(50) NOT NULL COMMENT '大类编码',
    sort_code INT DEFAULT 0 COMMENT '排序编码',
    applicable_scope VARCHAR(50) COMMENT '适用范围：职能部门/分公司/通用',
    weight DECIMAL(5,2) COMMENT '权重(%)',
    evaluation_standard TEXT COMMENT '评价标准',
    is_enabled TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '指标大类表';

-- 6. 菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    menu_code VARCHAR(50) COMMENT '菜单编码',
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
    menu_path VARCHAR(200) COMMENT '路由路径',
    menu_icon VARCHAR(50) COMMENT '图标',
    sort_code INT DEFAULT 0,
    is_enabled TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '菜单表';

-- 7. 职责表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL COMMENT '职责名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '职责编码',
    description VARCHAR(200) COMMENT '描述',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '职责表';

-- 8. 职责-菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL
) COMMENT '职责菜单关联表';

-- 9. 数据同步日志表
CREATE TABLE IF NOT EXISTS sys_data_sync_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type VARCHAR(50) COMMENT '同步类型',
    total_count INT DEFAULT 0 COMMENT '总条数',
    add_count INT DEFAULT 0 COMMENT '新增条数',
    update_count INT DEFAULT 0 COMMENT '更新条数',
    fail_count INT DEFAULT 0 COMMENT '失败条数',
    status VARCHAR(20) COMMENT '状态：进行中/成功/失败',
    start_time DATETIME,
    end_time DATETIME,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '数据同步日志表';

-- 10. 考核组表
CREATE TABLE IF NOT EXISTS biz_exam_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL COMMENT '考核组名称',
    exam_category VARCHAR(20) COMMENT '考核类别：业绩指标设定/绩效考核',
    exam_type VARCHAR(20) COMMENT '考核类型：月度考核/年度考核',
    start_date DATE COMMENT '考核开始日期',
    end_date DATE COMMENT '考核结束日期',
    progress INT DEFAULT 0 COMMENT '完成进度(%)',
    status VARCHAR(20) DEFAULT '待启动' COMMENT '状态：待启动/进行中/已完成/预发布/已发布',
    current_step VARCHAR(30) COMMENT '当前步骤：成员维护/已启动/指标已发布/考核中/他评中/预发布/已发布',
    created_by VARCHAR(50),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '考核组表';

-- 11. 考核组成员表
CREATE TABLE IF NOT EXISTS biz_exam_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    org_id BIGINT NOT NULL COMMENT '考核组织ID',
    org_name VARCHAR(100) COMMENT '组织名称',
    member_type VARCHAR(20) COMMENT '成员类型',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '考核组成员表';

-- 12. 指标设定表（三级树结构）
CREATE TABLE IF NOT EXISTS biz_indicator_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    org_id BIGINT NOT NULL COMMENT '所属组织/部门ID',
    org_name VARCHAR(100) COMMENT '部门名称',
    category_id BIGINT COMMENT '指标大类ID',
    category_name VARCHAR(100) COMMENT '指标大类名称',
    sub_category VARCHAR(100) COMMENT '指标小类',
    content VARCHAR(500) COMMENT '考核内容',
    target_desc VARCHAR(500) COMMENT '指标/目标描述',
    weight_annual DECIMAL(5,2) COMMENT '权重-年度(%)',
    weight_monthly DECIMAL(5,2) COMMENT '权重-月度(%)',
    evaluation_standard TEXT COMMENT '考核标准',
    sort_code INT DEFAULT 0,
    approval_status VARCHAR(20) DEFAULT '草稿' COMMENT '审批状态：草稿/待部门负责人审批/待分管领导审批/待财务处审批/审批通过/被退回',
    reject_reason VARCHAR(500) COMMENT '退回说明',
    submitted_by VARCHAR(50) COMMENT '提交人',
    submitted_time DATETIME COMMENT '提交时间',
    approved_by VARCHAR(50) COMMENT '审批人',
    approved_time DATETIME COMMENT '审批时间',
    exam_target_type VARCHAR(20) DEFAULT 'DEPARTMENT' COMMENT '考核目标类型：DEPARTMENT-部门 LEADER-分管领导',
    leader_id BIGINT COMMENT '分管领导ID（当exam_target_type=LEADER时）',
    leader_name VARCHAR(50) COMMENT '分管领导名称',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '业绩指标设定表';

-- 13. 自评表
CREATE TABLE IF NOT EXISTS biz_self_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    org_id BIGINT NOT NULL COMMENT '部门ID',
    indicator_id BIGINT NOT NULL COMMENT '指标项ID',
    actual_completion VARCHAR(500) COMMENT '实际完成情况',
    self_score DECIMAL(5,2) COMMENT '自评得分(0-100)',
    self_result DECIMAL(8,4) COMMENT '自评结果(得分×月度权重)',
    attachment_url VARCHAR(500) COMMENT '附件URL',
    attachment_name VARCHAR(200) COMMENT '附件名称',
    status VARCHAR(20) DEFAULT '待提交' COMMENT '状态：待提交/已提交',
    submitted_by VARCHAR(50),
    submitted_time DATETIME,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '月度自评表';

-- 14. 他评表
CREATE TABLE IF NOT EXISTS biz_peer_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    evaluator_org_id BIGINT NOT NULL COMMENT '评估部门ID',
    evaluator_org_name VARCHAR(100) COMMENT '评估部门名称',
    target_org_id BIGINT NOT NULL COMMENT '被评估部门ID',
    target_org_name VARCHAR(100) COMMENT '被评估部门名称',
    indicator_id BIGINT NOT NULL COMMENT '指标项ID',
    peer_score DECIMAL(5,2) COMMENT '他评得分',
    score_comment VARCHAR(500) COMMENT '打分说明',
    status VARCHAR(20) DEFAULT '待打分' COMMENT '状态：待打分/已完成',
    submitted_by VARCHAR(50),
    submitted_time DATETIME,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '他评表';

-- 15. 复核打分表
CREATE TABLE IF NOT EXISTS biz_review_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    org_id BIGINT NOT NULL COMMENT '部门ID',
    org_name VARCHAR(100) COMMENT '部门名称',
    indicator_id BIGINT NOT NULL COMMENT '指标项ID',
    dept_score DECIMAL(5,2) COMMENT '部门打分',
    admin_score DECIMAL(5,2) COMMENT '管理员打分',
    final_score DECIMAL(8,4) COMMENT '最终得分 = max(admin,dept) × 月度权重',
    score_comment VARCHAR(500) COMMENT '打分说明',
    reviewer VARCHAR(50) COMMENT '复核人',
    review_time DATETIME,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '复核打分表';

-- 16. 月度得分汇总表
CREATE TABLE IF NOT EXISTS biz_monthly_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    org_id BIGINT NOT NULL COMMENT '部门ID',
    org_name VARCHAR(100) COMMENT '部门名称',
    indicator_id BIGINT COMMENT '指标项ID',
    category_name VARCHAR(100) COMMENT '指标大类',
    score_value DECIMAL(8,4) COMMENT '得分值',
    weight_monthly DECIMAL(5,2) COMMENT '月度权重',
    weighted_score DECIMAL(8,4) COMMENT '加权得分',
    total_score DECIMAL(8,4) COMMENT '汇总得分',
    score_month VARCHAR(7) COMMENT '考核月份(yyyy-MM)',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '月度得分汇总表';

-- 17. 申诉表
CREATE TABLE IF NOT EXISTS biz_appeal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_group_id BIGINT NOT NULL COMMENT '考核组ID',
    appeal_org_id BIGINT NOT NULL COMMENT '申诉部门ID',
    appeal_org_name VARCHAR(100) COMMENT '申诉部门名称',
    scorer_org_id BIGINT COMMENT '打分部门ID',
    scorer_org_name VARCHAR(100) COMMENT '打分部门名称',
    indicator_id BIGINT COMMENT '指标项ID',
    appeal_reason VARCHAR(1000) COMMENT '申诉说明',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态：草稿/待重新评估/已处理',
    original_score DECIMAL(5,2) COMMENT '原始得分',
    new_score DECIMAL(5,2) COMMENT '重新评估得分',
    handled_by VARCHAR(50) COMMENT '处理人',
    handled_time DATETIME COMMENT '处理时间',
    created_by VARCHAR(50),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '申诉表';

-- 18. 申诉附件表
CREATE TABLE IF NOT EXISTS biz_appeal_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appeal_id BIGINT NOT NULL COMMENT '申诉ID',
    file_name VARCHAR(200) COMMENT '文件名',
    file_url VARCHAR(500) COMMENT '文件URL',
    file_size BIGINT COMMENT '文件大小(字节)',
    file_type VARCHAR(50) COMMENT '文件类型',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '申诉附件表';

-- 19. 数据字典表
CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type VARCHAR(50) NOT NULL COMMENT '字典类型',
    dict_code VARCHAR(50) NOT NULL COMMENT '字典编码',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典标签',
    sort_code INT DEFAULT 0,
    is_enabled TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '数据字典表';

-- 20. 操作日志表
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '操作用户ID',
    user_name VARCHAR(50) COMMENT '操作用户',
    operation VARCHAR(100) COMMENT '操作描述',
    method VARCHAR(200) COMMENT '方法名',
    params TEXT COMMENT '请求参数',
    ip VARCHAR(50) COMMENT 'IP地址',
    duration BIGINT COMMENT '耗时(ms)',
    status VARCHAR(20) COMMENT '状态：成功/失败',
    error_msg TEXT COMMENT '错误信息',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '操作日志表';

-- 21. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(200) NOT NULL COMMENT '密码(BCrypt加密)',
    real_name VARCHAR(50) COMMENT '真实姓名',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(50) COMMENT '角色名称',
    org_id BIGINT COMMENT '所属组织ID',
    org_name VARCHAR(100) COMMENT '所属组织名称',
    unit_id BIGINT COMMENT '所属单位ID',
    employee_id BIGINT COMMENT '关联人员ID',
    is_enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    last_login_time DATETIME COMMENT '最后登录时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '系统用户表';

-- 22. 人员表（数据来源：人事系统同步）
CREATE TABLE IF NOT EXISTS sys_employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_no VARCHAR(50) NOT NULL UNIQUE COMMENT '人员编号',
    employee_name VARCHAR(50) NOT NULL COMMENT '人员姓名',
    dept_id BIGINT COMMENT '部门/组织ID',
    dept_name VARCHAR(100) COMMENT '部门名称',
    unit_id BIGINT COMMENT '所属单位ID',
    position VARCHAR(100) COMMENT '岗位',
    level VARCHAR(50) COMMENT '级别',
    is_active TINYINT DEFAULT 1 COMMENT '是否在职(1在职/0离职)',
    is_invalid TINYINT DEFAULT 0 COMMENT '是否失效(0有效/1失效)',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) COMMENT '人员表';

-- 23. 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_code VARCHAR(30) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_code)
) COMMENT '用户角色关联表';

-- 24. 指标-考核部门关联表（支持多选）
CREATE TABLE IF NOT EXISTS biz_indicator_org (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    indicator_id BIGINT NOT NULL COMMENT '指标ID',
    org_id BIGINT NOT NULL COMMENT '考核部门ID',
    org_name VARCHAR(100) COMMENT '考核部门名称',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_indicator_id (indicator_id),
    INDEX idx_org_id (org_id)
) COMMENT '指标-考核部门关联表';

-- 25. 指标-分管领导关联表（支持多选）
CREATE TABLE IF NOT EXISTS biz_indicator_leader (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    indicator_id BIGINT NOT NULL COMMENT '指标ID',
    leader_id BIGINT NOT NULL COMMENT '分管领导ID',
    leader_name VARCHAR(50) COMMENT '分管领导名称',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_indicator_id (indicator_id),
    INDEX idx_leader_id (leader_id)
) COMMENT '指标-分管领导关联表';
