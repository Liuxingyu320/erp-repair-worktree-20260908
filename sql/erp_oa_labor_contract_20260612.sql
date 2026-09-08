-- Labor contract online signing module for erp-oa.

CREATE TABLE IF NOT EXISTS oa_labor_contract_template (
    template_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    template_name varchar(100) NOT NULL COMMENT '模板名称',
    social_type varchar(20) NOT NULL COMMENT '社保口径：有社保/无社保',
    template_version varchar(32) DEFAULT NULL COMMENT '模板版本',
    template_file_url varchar(500) NOT NULL COMMENT '模板文件URL',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
    built_in char(1) NOT NULL DEFAULT 'N' COMMENT '是否内置（Y是 N否）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (template_id),
    KEY idx_oa_labor_contract_template_social (social_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA劳动合同模板';

CREATE TABLE IF NOT EXISTS oa_labor_contract (
    contract_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '合同ID',
    template_id bigint(20) NOT NULL COMMENT '模板ID',
    employee_id bigint(20) NOT NULL COMMENT '员工用户ID',
    employee_dept_id bigint(20) DEFAULT NULL COMMENT '员工部门ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '所属店铺ID',
    scheme_id bigint(20) DEFAULT NULL COMMENT '薪资方案ID快照',
    salary_item_id bigint(20) DEFAULT NULL COMMENT '薪资档位ID快照',
    contract_no varchar(64) DEFAULT NULL COMMENT '合同编号',
    contract_title varchar(120) DEFAULT NULL COMMENT '合同标题',
    employee_name varchar(64) NOT NULL COMMENT '员工姓名快照',
    employee_id_card varchar(32) NOT NULL COMMENT '身份证号快照',
    employee_phone varchar(32) NOT NULL COMMENT '手机号快照',
    post_name varchar(64) NOT NULL COMMENT '岗位快照',
    social_type varchar(20) NOT NULL COMMENT '社保口径快照',
    contract_start_date varchar(10) DEFAULT NULL COMMENT '合同开始日期',
    contract_end_date varchar(10) DEFAULT NULL COMMENT '合同结束日期',
    probation_start_date varchar(10) DEFAULT NULL COMMENT '试用期开始日期',
    probation_end_date varchar(10) DEFAULT NULL COMMENT '试用期结束日期',
    base_salary decimal(16,2) DEFAULT 0.00 COMMENT '基本工资',
    management_allowance decimal(16,2) DEFAULT 0.00 COMMENT '管理津贴',
    overtime_pay decimal(16,2) DEFAULT 0.00 COMMENT '加班费',
    reward_allowance decimal(16,2) DEFAULT 0.00 COMMENT '奖励津贴',
    full_attendance_bonus decimal(16,2) DEFAULT 0.00 COMMENT '全勤奖',
    social_subsidy decimal(16,2) DEFAULT 0.00 COMMENT '社保补贴',
    commute_subsidy decimal(16,2) DEFAULT 0.00 COMMENT '通勤补贴',
    total_salary decimal(16,2) DEFAULT 0.00 COMMENT '工资合计',
    accommodation_text varchar(500) DEFAULT NULL COMMENT '住宿约定快照',
    commute_text varchar(500) DEFAULT NULL COMMENT '通勤约定快照',
    status varchar(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft/pending_sign/signed/voided/expired',
    preview_file_url varchar(500) DEFAULT NULL COMMENT '预览DOCX URL',
    archive_file_url varchar(500) DEFAULT NULL COMMENT '归档DOCX URL',
    pdf_file_url varchar(500) DEFAULT NULL COMMENT '归档PDF URL',
    signature_file_url varchar(500) DEFAULT NULL COMMENT '员工签名图片URL',
    contract_file_hash varchar(128) DEFAULT NULL COMMENT '合同文件SHA-256',
    signer_ip varchar(64) DEFAULT NULL COMMENT '签署IP',
    signer_user_agent varchar(500) DEFAULT NULL COMMENT '签署User-Agent',
    sent_time datetime DEFAULT NULL COMMENT '发送时间',
    signed_time datetime DEFAULT NULL COMMENT '签署时间',
    voided_time datetime DEFAULT NULL COMMENT '作废时间',
    sign_provider varchar(32) DEFAULT 'internal' COMMENT '签署服务商',
    provider_contract_id varchar(128) DEFAULT NULL COMMENT '第三方合同ID',
    provider_sign_url varchar(500) DEFAULT NULL COMMENT '第三方签署URL',
    provider_status varchar(64) DEFAULT NULL COMMENT '第三方状态',
    provider_callback_payload text COMMENT '第三方回调原文',
    ca_cert_no varchar(128) DEFAULT NULL COMMENT 'CA证书编号',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (contract_id),
    KEY idx_oa_labor_contract_employee (employee_id, status),
    KEY idx_oa_labor_contract_shop (shop_dept_id, status),
    KEY idx_oa_labor_contract_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA劳动合同签约单';

CREATE TABLE IF NOT EXISTS oa_labor_contract_event (
    event_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    contract_id bigint(20) NOT NULL COMMENT '合同ID',
    event_type varchar(32) NOT NULL COMMENT '事件类型',
    event_summary varchar(500) DEFAULT NULL COMMENT '事件摘要',
    operator_id bigint(20) DEFAULT NULL COMMENT '操作者ID',
    operator_name varchar(64) DEFAULT NULL COMMENT '操作者账号',
    client_ip varchar(64) DEFAULT NULL COMMENT '客户端IP',
    user_agent varchar(500) DEFAULT NULL COMMENT 'User-Agent',
    file_hash varchar(128) DEFAULT NULL COMMENT '文件SHA-256',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (event_id),
    KEY idx_oa_labor_contract_event_contract (contract_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA劳动合同签约事件';

CREATE TABLE IF NOT EXISTS oa_company_seal_config (
    seal_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '印章ID',
    seal_name varchar(100) NOT NULL COMMENT '印章名称',
    seal_image_url varchar(500) NOT NULL COMMENT '印章图片URL',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (seal_id),
    KEY idx_oa_company_seal_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA企业合同印章配置';

INSERT INTO oa_labor_contract_template
    (template_id, template_name, social_type, template_version, template_file_url, status, built_in, create_by, create_time, remark)
VALUES
    (1, '杭州劳动合同有社保版名田', '有社保', '2026-06', 'classpath:/templates/labor-contract/social.docx', '0', 'Y', 'system', NOW(), '内置模板'),
    (2, '杭州劳动合同无社保版名田', '无社保', '2026-06', 'classpath:/templates/labor-contract/no-social.docx', '0', 'Y', 'system', NOW(), '内置模板')
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    social_type = VALUES(social_type),
    template_version = VALUES(template_version),
    template_file_url = VALUES(template_file_url),
    status = VALUES(status),
    built_in = VALUES(built_in),
    update_by = 'system',
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (3300, '劳动合同', 3000, 9, 'labor-contract', 'oa/laborContract/index', NULL,
     'OaLaborContract', 1, 0, 'C', '0', '0', 'oa:laborContract:list', 'document', 'system', NOW(), '劳动合同线上签约')
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
    (3301, '合同查询', 3300, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:laborContract:query', '#', 'system', NOW(), ''),
    (3302, '合同新增', 3300, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:laborContract:add', '#', 'system', NOW(), ''),
    (3303, '合同发送', 3300, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:laborContract:send', '#', 'system', NOW(), ''),
    (3304, '合同作废', 3300, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:laborContract:void', '#', 'system', NOW(), ''),
    (3305, '模板查询', 3300, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:laborContract:template:list', '#', 'system', NOW(), ''),
    (3306, '模板维护', 3300, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'oa:laborContract:template:add', '#', 'system', NOW(), '')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    perms = VALUES(perms),
    update_time = NOW();

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES
    (1, 3300),
    (1, 3301),
    (1, 3302),
    (1, 3303),
    (1, 3304),
    (1, 3305),
    (1, 3306);
