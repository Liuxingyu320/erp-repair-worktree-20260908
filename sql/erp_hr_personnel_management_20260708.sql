-- 人事管理与入职档案一期

CREATE TABLE IF NOT EXISTS sys_user_profile (
    profile_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '档案ID',
    user_id bigint(20) DEFAULT NULL COMMENT '用户ID，待入职阶段可为空',
    dept_id bigint(20) DEFAULT NULL COMMENT '档案所属组织ID',
    employee_name varchar(64) DEFAULT NULL COMMENT '员工姓名',
    phone_number varchar(32) DEFAULT NULL COMMENT '手机号',
    sex char(1) DEFAULT NULL COMMENT '性别',
    employee_no varchar(64) DEFAULT NULL COMMENT '工号',
    company_name varchar(100) DEFAULT NULL COMMENT '所属公司',
    dept_level1_name varchar(100) DEFAULT NULL COMMENT '1级部门',
    dept_level2_name varchar(100) DEFAULT NULL COMMENT '2级部门',
    dept_level3_name varchar(100) DEFAULT NULL COMMENT '3级部门',
    store_name varchar(100) DEFAULT NULL COMMENT '4级门店',
    position_names varchar(200) DEFAULT NULL COMMENT '职位',
    job_grade varchar(64) DEFAULT NULL COMMENT '职级',
    department_supervisor varchar(100) DEFAULT NULL COMMENT '部门主管',
    direct_supervisor varchar(100) DEFAULT NULL COMMENT '直属主管',
    employee_status varchar(32) DEFAULT NULL COMMENT '员工状态',
    employee_category varchar(64) DEFAULT NULL COMMENT '人员类别',
    birth_date date DEFAULT NULL COMMENT '出生日期',
    id_type varchar(64) DEFAULT NULL COMMENT '证件类型',
    id_number varchar(64) DEFAULT NULL COMMENT '证件号码',
    blood_type varchar(16) DEFAULT NULL COMMENT '血型',
    registered_residence varchar(255) DEFAULT NULL COMMENT '户口所在地',
    current_address varchar(255) DEFAULT NULL COMMENT '现居住地址',
    first_education varchar(64) DEFAULT NULL COMMENT '第一学历',
    first_degree varchar(64) DEFAULT NULL COMMENT '第一学位',
    first_graduation_date date DEFAULT NULL COMMENT '毕业时间',
    first_graduation_school varchar(100) DEFAULT NULL COMMENT '第一学历毕业学校',
    first_major varchar(100) DEFAULT NULL COMMENT '第一学历所学专业',
    highest_education varchar(64) DEFAULT NULL COMMENT '最高学历',
    highest_degree varchar(64) DEFAULT NULL COMMENT '最高学位',
    highest_graduation_date date DEFAULT NULL COMMENT '最高学历毕业时间',
    highest_graduation_school varchar(100) DEFAULT NULL COMMENT '最高学历毕业学校',
    highest_major varchar(100) DEFAULT NULL COMMENT '最高学历所学专业',
    political_status varchar(64) DEFAULT NULL COMMENT '政治面貌',
    marital_status varchar(32) DEFAULT NULL COMMENT '婚姻状况',
    nationality varchar(64) DEFAULT NULL COMMENT '国籍',
    foreign_national_flag varchar(8) DEFAULT NULL COMMENT '是否外籍',
    ethnicity varchar(64) DEFAULT NULL COMMENT '民族',
    health_status varchar(64) DEFAULT NULL COMMENT '健康状况',
    emergency_contact varchar(100) DEFAULT NULL COMMENT '紧急联系人',
    emergency_contact_relation varchar(64) DEFAULT NULL COMMENT '与紧急联系人关系',
    emergency_contact_phone varchar(32) DEFAULT NULL COMMENT '紧急联系人电话',
    recruitment_channel varchar(100) DEFAULT NULL COMMENT '招聘渠道',
    office_phone varchar(32) DEFAULT NULL COMMENT '办公电话',
    work_start_date date DEFAULT NULL COMMENT '参加工作时间',
    work_years varchar(32) DEFAULT NULL COMMENT '工龄',
    entry_date date DEFAULT NULL COMMENT '入职时间',
    probation_period varchar(64) DEFAULT NULL COMMENT '试用期',
    planned_regularization_date date DEFAULT NULL COMMENT '计划转正日期',
    actual_regularization_date date DEFAULT NULL COMMENT '实际转正日期',
    company_years varchar(32) DEFAULT NULL COMMENT '司龄',
    current_position_start_date date DEFAULT NULL COMMENT '本岗位任职日期',
    contract_start_date date DEFAULT NULL COMMENT '现合同起始日',
    contract_end_date date DEFAULT NULL COMMENT '现合同到期日',
    contract_term varchar(64) DEFAULT NULL COMMENT '合同期限',
    renewal_count int(11) DEFAULT NULL COMMENT '续签次数',
    work_location varchar(100) DEFAULT NULL COMMENT '工作所在地',
    work_city_level varchar(64) DEFAULT NULL COMMENT '工作所在城市级别',
    attendance_method varchar(64) DEFAULT NULL COMMENT '考勤方式',
    household_type varchar(64) DEFAULT NULL COMMENT '户口性质',
    social_security_location varchar(100) DEFAULT NULL COMMENT '社保缴纳地',
    housing_fund_location varchar(100) DEFAULT NULL COMMENT '公积金缴纳地',
    leave_date date DEFAULT NULL COMMENT '离职时间',
    bank_name varchar(100) DEFAULT NULL COMMENT '开户银行',
    bank_account varchar(64) DEFAULT NULL COMMENT '银行卡号',
    legal_entity varchar(100) DEFAULT NULL COMMENT '法人单位',
    contract_type varchar(64) DEFAULT NULL COMMENT '合同类型',
    social_security_type varchar(64) DEFAULT NULL COMMENT '社保类型',
    onboarding_status varchar(32) DEFAULT NULL COMMENT '入职状态',
    expected_entry_date date DEFAULT NULL COMMENT '预计入职日期',
    onboarding_confirmed_by varchar(64) DEFAULT NULL COMMENT '确认入职人',
    onboarding_confirmed_time datetime DEFAULT NULL COMMENT '确认入职时间',
    onboarding_cancel_reason varchar(255) DEFAULT NULL COMMENT '取消入职原因',
    profile_source varchar(64) DEFAULT NULL COMMENT '资料来源',
    last_profile_update_by varchar(64) DEFAULT NULL COMMENT '资料最后更新人',
    last_profile_update_time datetime DEFAULT NULL COMMENT '资料最后更新时间',
    id_card_portrait_status varchar(32) DEFAULT NULL COMMENT '身份证人像面状态',
    id_card_emblem_status varchar(32) DEFAULT NULL COMMENT '身份证国徽面状态',
    education_certificate_status varchar(32) DEFAULT NULL COMMENT '学历证书状态',
    degree_certificate_status varchar(32) DEFAULT NULL COMMENT '学位证书状态',
    resignation_certificate_status varchar(32) DEFAULT NULL COMMENT '前公司离职证明状态',
    employee_photo_status varchar(32) DEFAULT NULL COMMENT '员工照片状态',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_user_profile_user_id (user_id),
    UNIQUE KEY uk_user_profile_employee_no (employee_no),
    KEY idx_user_profile_employee_status (employee_status),
    KEY idx_user_profile_dept (dept_id),
    KEY idx_user_profile_employee_category (employee_category),
    KEY idx_user_profile_entry_date (entry_date),
    KEY idx_user_profile_onboarding_status (onboarding_status),
    KEY idx_user_profile_expected_entry_date (expected_entry_date),
    KEY idx_user_profile_phone_number (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户员工档案表';

-- 兼容已经由早期员工档案脚本创建的精简表，逐列补齐人事一期扩展字段。
SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'employee_name') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN employee_name varchar(64) DEFAULT NULL COMMENT ''员工姓名'' AFTER user_id', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'phone_number') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN phone_number varchar(32) DEFAULT NULL COMMENT ''手机号'' AFTER employee_name', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'sex') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN sex char(1) DEFAULT NULL COMMENT ''性别'' AFTER phone_number', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'social_security_type') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN social_security_type varchar(64) DEFAULT NULL COMMENT ''社保类型'' AFTER legal_entity', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'onboarding_status') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN onboarding_status varchar(32) DEFAULT NULL COMMENT ''入职状态'' AFTER legal_entity', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'expected_entry_date') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN expected_entry_date date DEFAULT NULL COMMENT ''预计入职日期'' AFTER onboarding_status', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'onboarding_confirmed_by') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN onboarding_confirmed_by varchar(64) DEFAULT NULL COMMENT ''确认入职人'' AFTER expected_entry_date', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'onboarding_confirmed_time') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN onboarding_confirmed_time datetime DEFAULT NULL COMMENT ''确认入职时间'' AFTER onboarding_confirmed_by', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'onboarding_cancel_reason') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN onboarding_cancel_reason varchar(255) DEFAULT NULL COMMENT ''取消入职原因'' AFTER onboarding_confirmed_time', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'profile_source') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN profile_source varchar(64) DEFAULT NULL COMMENT ''资料来源'' AFTER onboarding_cancel_reason', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'last_profile_update_by') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN last_profile_update_by varchar(64) DEFAULT NULL COMMENT ''资料最后更新人'' AFTER profile_source', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'last_profile_update_time') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN last_profile_update_time datetime DEFAULT NULL COMMENT ''资料最后更新时间'' AFTER last_profile_update_by', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'id_card_portrait_status') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN id_card_portrait_status varchar(32) DEFAULT NULL COMMENT ''身份证人像面状态'' AFTER last_profile_update_time', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'id_card_emblem_status') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN id_card_emblem_status varchar(32) DEFAULT NULL COMMENT ''身份证国徽面状态'' AFTER id_card_portrait_status', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'education_certificate_status') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN education_certificate_status varchar(32) DEFAULT NULL COMMENT ''学历证书状态'' AFTER id_card_emblem_status', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'degree_certificate_status') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN degree_certificate_status varchar(32) DEFAULT NULL COMMENT ''学位证书状态'' AFTER education_certificate_status', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'resignation_certificate_status') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN resignation_certificate_status varchar(32) DEFAULT NULL COMMENT ''前公司离职证明状态'' AFTER degree_certificate_status', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

SET @hr_profile_column_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_profile' AND column_name = 'employee_photo_status') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN employee_photo_status varchar(32) DEFAULT NULL COMMENT ''员工照片状态'' AFTER resignation_certificate_status', 'SELECT 1');
PREPARE hr_profile_column_stmt FROM @hr_profile_column_sql; EXECUTE hr_profile_column_stmt; DEALLOCATE PREPARE hr_profile_column_stmt;

-- 人事管理菜单与权限

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4600, '人事管理', 0, 60, 'hr', NULL, NULL,
     'Hr', 1, 0, 'M', '0', '0', '', 'peoples', 'system', NOW(), '人事员工档案与入职管理')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4601, '员工档案', 4600, 1, 'employee', 'hr/employee/index', NULL,
     'HrEmployee', 1, 0, 'C', '0', '0', 'hr:employee:list', 'user', 'system', NOW(), '人事员工档案'),
    (4602, '入职管理', 4600, 2, 'onboarding', 'hr/onboarding/index', NULL,
     'HrOnboarding', 1, 0, 'C', '0', '0', 'hr:onboarding:list', 'guide', 'system', NOW(), '待入职资料确认'),
    (4603, '资料完整度', 4600, 3, 'completeness', 'hr/completeness/index', NULL,
     'HrCompleteness', 1, 0, 'C', '0', '0', 'hr:completeness:list', 'chart', 'system', NOW(), '人事资料完整度检查'),
    (4604, '人事导入导出', 4600, 4, 'import-export', 'hr/importExport/index', NULL,
     'HrImportExport', 1, 0, 'C', '0', '0', 'hr:import:preview', 'upload', 'system', NOW(), '人事档案导入导出')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4611, '员工档案查询', 4601, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:employee:query', '#', 'system', NOW(), ''),
    (4612, '员工档案新增', 4601, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:employee:add', '#', 'system', NOW(), ''),
    (4613, '员工档案编辑', 4601, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:employee:edit', '#', 'system', NOW(), ''),
    (4614, '员工档案导出', 4601, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:employee:export', '#', 'system', NOW(), ''),
    (4615, '员工档案导出接口', 4601, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:export:profile', '#', 'system', NOW(), ''),
    (4621, '入职确认', 4602, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:onboarding:confirm', '#', 'system', NOW(), ''),
    (4622, '取消入职', 4602, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:onboarding:cancel', '#', 'system', NOW(), ''),
    (4631, '资料完整度查询', 4603, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:completeness:list', '#', 'system', NOW(), ''),
    (4641, '人事导入预检', 4604, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:import:preview', '#', 'system', NOW(), ''),
    (4642, '人事确认导入', 4604, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:import:confirm', '#', 'system', NOW(), ''),
    (4643, '人事导入模板', 4604, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:import:template', '#', 'system', NOW(), ''),
    (4644, '导出缺失资料', 4604, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'hr:export:missing', '#', 'system', NOW(), '')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    perms = VALUES(perms),
    update_time = NOW();

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES
    (1, 4600), (1, 4601), (1, 4602), (1, 4603), (1, 4604),
    (1, 4611), (1, 4612), (1, 4613), (1, 4614), (1, 4615),
    (1, 4621), (1, 4622), (1, 4631),
    (1, 4641), (1, 4642), (1, 4643), (1, 4644);

INSERT INTO sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
     status, del_flag, create_by, create_time, remark)
SELECT '人事管理员', 'hr_admin', 30, '1', 1, 1,
       '0', '0', 'system', NOW(), '人事员工档案、入职管理、资料完整度与导入导出权限'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE del_flag = '0'
      AND (role_key = 'hr_admin' OR role_name = '人事管理员')
);

SET @hr_admin_role_id := (
    SELECT role_id
    FROM sys_role
    WHERE del_flag = '0'
      AND (role_key = 'hr_admin' OR role_name = '人事管理员')
    ORDER BY role_id
    LIMIT 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @hr_admin_role_id, menu_id
FROM sys_menu
WHERE menu_id IN (
    4600, 4601, 4602, 4603, 4604,
    4611, 4612, 4613, 4614, 4615,
    4621, 4622, 4631,
    4641, 4642, 4643, 4644
);
