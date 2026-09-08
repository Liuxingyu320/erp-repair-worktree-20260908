-- Employee signing package module for onboarding/offboarding/transfer documents.

CREATE TABLE IF NOT EXISTS oa_sign_template (
    template_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    template_type varchar(64) NOT NULL COMMENT '模板类型编码',
    template_name varchar(120) NOT NULL COMMENT '模板名称',
    template_version varchar(32) DEFAULT NULL COMMENT '模板版本',
    scenario varchar(32) DEFAULT NULL COMMENT '签约场景：onboard/offboard/transfer/regularize',
    employment_type varchar(32) DEFAULT NULL COMMENT '用工类型',
    social_type varchar(20) DEFAULT NULL COMMENT '社保口径',
    post_level_scope varchar(64) DEFAULT NULL COMMENT '岗位等级适用范围',
    salary_version varchar(20) DEFAULT NULL COMMENT '薪酬结构版本',
    file_url varchar(500) NOT NULL COMMENT '模板文件URL',
    file_name varchar(200) DEFAULT NULL COMMENT '模板文件名',
    file_size bigint(20) DEFAULT NULL COMMENT '模板文件大小',
    file_hash varchar(128) DEFAULT NULL COMMENT '模板文件SHA-256',
    required_placeholders varchar(1000) DEFAULT NULL COMMENT '必填占位符',
    optional_placeholders varchar(1000) DEFAULT NULL COMMENT '可选占位符',
    employee_sign_required char(1) NOT NULL DEFAULT 'Y' COMMENT '是否员工签署（Y是 N否）',
    sort_order int(11) DEFAULT 100 COMMENT '签约包内排序',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (template_id),
    KEY idx_oa_sign_template_type (template_type, status),
    KEY idx_oa_sign_template_match (scenario, employment_type, social_type, salary_version, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA员工签约模板';

CREATE TABLE IF NOT EXISTS oa_sign_package (
    package_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签约包ID',
    package_no varchar(64) DEFAULT NULL COMMENT '签约包编号',
    employee_id bigint(20) NOT NULL COMMENT '员工用户ID',
    employee_name_snapshot varchar(64) NOT NULL COMMENT '员工姓名快照',
    employee_phone_snapshot varchar(32) NOT NULL COMMENT '手机号快照',
    employee_id_card_snapshot varchar(32) NOT NULL COMMENT '身份证号快照',
    employee_address_snapshot varchar(200) DEFAULT NULL COMMENT '住址快照',
    dept_id_snapshot bigint(20) DEFAULT NULL COMMENT '员工部门ID快照',
    dept_name_snapshot varchar(100) DEFAULT NULL COMMENT '员工部门名称快照',
    shop_dept_id bigint(20) NOT NULL COMMENT '所属店铺/组织ID',
    shop_dept_name varchar(100) DEFAULT NULL COMMENT '所属店铺/组织名称',
    scenario varchar(32) NOT NULL COMMENT '签约场景',
    employment_type varchar(32) DEFAULT NULL COMMENT '用工类型',
    social_type varchar(20) DEFAULT NULL COMMENT '社保口径',
    service_person_type varchar(32) DEFAULT NULL COMMENT '劳务人员类型',
    insurance_type varchar(64) DEFAULT NULL COMMENT '保险类型',
    post_name_snapshot varchar(64) DEFAULT NULL COMMENT '岗位快照',
    post_level_snapshot varchar(32) DEFAULT NULL COMMENT '岗位等级快照',
    salary_version varchar(20) DEFAULT NULL COMMENT '薪酬版本',
    entry_date varchar(10) DEFAULT NULL COMMENT '入职日期',
    contract_start_date varchar(10) DEFAULT NULL COMMENT '合同开始日期',
    contract_end_date varchar(10) DEFAULT NULL COMMENT '合同结束日期',
    probation_start_date varchar(10) DEFAULT NULL COMMENT '试用期开始日期',
    probation_end_date varchar(10) DEFAULT NULL COMMENT '试用期结束日期',
    work_start_date varchar(10) DEFAULT NULL COMMENT '任职开始日期',
    work_end_date varchar(10) DEFAULT NULL COMMENT '任职结束日期',
    leave_date varchar(10) DEFAULT NULL COMMENT '离职日期',
    leave_reason varchar(200) DEFAULT NULL COMMENT '离职原因',
    base_salary decimal(16,2) DEFAULT NULL COMMENT '基本工资',
    post_salary decimal(16,2) DEFAULT NULL COMMENT '岗位工资',
    field_allowance decimal(16,2) DEFAULT NULL COMMENT '综合驻外补贴',
    performance_salary decimal(16,2) DEFAULT NULL COMMENT '绩效工资',
    salary_total decimal(16,2) DEFAULT NULL COMMENT '工资合计',
    status varchar(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft/pending_sign/part_viewed/signed/voided/failed',
    void_reason varchar(500) DEFAULT NULL COMMENT '撤回原因',
    sent_time datetime DEFAULT NULL COMMENT '发送时间',
    viewed_time datetime DEFAULT NULL COMMENT '查看时间',
    signed_time datetime DEFAULT NULL COMMENT '签署时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (package_id),
    KEY idx_oa_sign_package_employee (employee_id, status),
    KEY idx_oa_sign_package_shop (shop_dept_id, status),
    KEY idx_oa_sign_package_scenario (scenario, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA员工签约包';

SET @erp_db = DATABASE();
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'employee_address_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN employee_address_snapshot varchar(200) DEFAULT NULL COMMENT ''住址快照'' AFTER employee_id_card_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'service_person_type') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN service_person_type varchar(32) DEFAULT NULL COMMENT ''劳务人员类型'' AFTER social_type',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'insurance_type') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN insurance_type varchar(64) DEFAULT NULL COMMENT ''保险类型'' AFTER service_person_type',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'probation_start_date') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN probation_start_date varchar(10) DEFAULT NULL COMMENT ''试用期开始日期'' AFTER contract_end_date',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'probation_end_date') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN probation_end_date varchar(10) DEFAULT NULL COMMENT ''试用期结束日期'' AFTER probation_start_date',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'field_allowance') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN field_allowance decimal(16,2) DEFAULT NULL COMMENT ''综合驻外补贴'' AFTER post_salary',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS oa_sign_package_document (
    document_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签约包文件ID',
    package_id bigint(20) NOT NULL COMMENT '签约包ID',
    template_id bigint(20) DEFAULT NULL COMMENT '模板ID',
    template_type varchar(64) NOT NULL COMMENT '模板类型',
    document_name varchar(120) NOT NULL COMMENT '文件名称',
    template_version_snapshot varchar(32) DEFAULT NULL COMMENT '模板版本快照',
    source_file_url_snapshot varchar(500) DEFAULT NULL COMMENT '源模板URL快照',
    generated_file_url varchar(500) DEFAULT NULL COMMENT '生成文件URL',
    generated_pdf_url varchar(500) DEFAULT NULL COMMENT '生成PDF URL',
    signed_file_url varchar(500) DEFAULT NULL COMMENT '签署归档文件URL',
    certificate_file_url varchar(500) DEFAULT NULL COMMENT '签署证明URL',
    file_hash_before_sign varchar(128) DEFAULT NULL COMMENT '签署前文件SHA-256',
    file_hash_after_sign varchar(128) DEFAULT NULL COMMENT '签署后文件SHA-256',
    employee_sign_required char(1) NOT NULL DEFAULT 'Y' COMMENT '是否员工签署',
    read_confirmed char(1) NOT NULL DEFAULT 'N' COMMENT '是否已阅读确认',
    signed char(1) NOT NULL DEFAULT 'N' COMMENT '是否已签署',
    sort_order int(11) DEFAULT 100 COMMENT '排序',
    status varchar(20) NOT NULL DEFAULT 'pending_sign' COMMENT '状态',
    error_message varchar(500) DEFAULT NULL COMMENT '生成错误',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (document_id),
    KEY idx_oa_sign_package_document_package (package_id, sort_order),
    KEY idx_oa_sign_package_document_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA员工签约包文件';

CREATE TABLE IF NOT EXISTS oa_sign_event (
    event_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签署事件ID',
    package_id bigint(20) NOT NULL COMMENT '签约包ID',
    document_id bigint(20) DEFAULT NULL COMMENT '签约包文件ID',
    event_type varchar(64) NOT NULL COMMENT '事件类型',
    operator_user_id bigint(20) DEFAULT NULL COMMENT '操作者用户ID',
    operator_name varchar(64) DEFAULT NULL COMMENT '操作者账号',
    operator_role varchar(32) DEFAULT NULL COMMENT '操作者角色',
    ip_address varchar(64) DEFAULT NULL COMMENT '客户端IP',
    user_agent varchar(500) DEFAULT NULL COMMENT 'User-Agent',
    event_payload varchar(1000) DEFAULT NULL COMMENT '事件内容',
    document_hash varchar(128) DEFAULT NULL COMMENT '文件SHA-256',
    prev_event_hash varchar(128) DEFAULT NULL COMMENT '上一事件hash',
    event_hash varchar(128) DEFAULT NULL COMMENT '当前事件hash',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (event_id),
    KEY idx_oa_sign_event_package (package_id, event_id),
    KEY idx_oa_sign_event_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA员工签约事件';

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4520, '员工签约', 3000, 10, 'sign-package', 'oa/signPackage/index', NULL,
     'OaSignPackage', 1, 0, 'C', '0', '0', 'oa:signPackage:list', 'document', 'system', NOW(), '入离调转签约包')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    route_name = VALUES(route_name),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4521, '签约包查询', 4520, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:query', '#', 'system', NOW(), ''),
    (4522, '签约包新增', 4520, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:add', '#', 'system', NOW(), ''),
    (4523, '签约包发送', 4520, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:send', '#', 'system', NOW(), ''),
    (4524, '签约包撤回', 4520, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:void', '#', 'system', NOW(), ''),
    (4525, '签约模板维护', 4520, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:template', '#', 'system', NOW(), ''),
    (4526, '员工签约', 4520, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:signPackage:sign', '#', 'system', NOW(), '')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    perms = VALUES(perms),
    update_time = NOW();

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES
    (1, 4520),
    (1, 4521),
    (1, 4522),
    (1, 4523),
    (1, 4524),
    (1, 4525),
    (1, 4526);
