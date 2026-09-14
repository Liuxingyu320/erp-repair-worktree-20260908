-- Isolated fixture: original production plan/template/version DDL + current mapped added fields; no business seeds.
CREATE TABLE IF NOT EXISTS oa_sign_plan (
    plan_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签约方案ID',
    plan_name varchar(120) NOT NULL COMMENT '方案名称',
    scenario varchar(32) NOT NULL DEFAULT 'onboard' COMMENT '签约场景',
    post_name varchar(64) DEFAULT NULL COMMENT '适用岗位',
    employment_type varchar(32) DEFAULT NULL COMMENT '用工类型',
    social_type varchar(20) DEFAULT NULL COMMENT '社保口径',
    service_person_type varchar(32) DEFAULT NULL COMMENT '劳务人员类型',
    insurance_type varchar(64) DEFAULT NULL COMMENT '保险类型',
    post_level_snapshot varchar(32) DEFAULT NULL COMMENT '岗位等级快照',
    salary_version varchar(20) DEFAULT NULL COMMENT '薪酬版本',
    entry_date varchar(10) DEFAULT NULL COMMENT '入职日期',
    contract_start_date varchar(10) DEFAULT NULL COMMENT '合同开始日期',
    contract_end_date varchar(10) DEFAULT NULL COMMENT '合同结束日期',
    probation_start_date varchar(10) DEFAULT NULL COMMENT '试用期开始日期',
    probation_end_date varchar(10) DEFAULT NULL COMMENT '试用期结束日期',
    base_salary decimal(16,2) DEFAULT NULL COMMENT '基本工资',
    post_salary decimal(16,2) DEFAULT NULL COMMENT '岗位工资',
    field_allowance decimal(16,2) DEFAULT NULL COMMENT '综合驻外补贴',
    salary_total decimal(16,2) DEFAULT NULL COMMENT '工资合计',
    shop_dept_id bigint(20) DEFAULT NULL COMMENT '所属店铺/组织ID',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
    sort_order int(11) DEFAULT 100 COMMENT '排序',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (plan_id),
    KEY idx_oa_sign_plan_shop (shop_dept_id, status),
    KEY idx_oa_sign_plan_post (post_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA员工签约方案';
CREATE TABLE IF NOT EXISTS oa_sign_plan_template (
    id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_id bigint(20) NOT NULL COMMENT '签约方案ID',
    template_id bigint(20) NOT NULL COMMENT '签约模板ID',
    template_type varchar(64) NOT NULL COMMENT '模板类型',
    sort_order int(11) DEFAULT 100 COMMENT '排序',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_oa_sign_plan_template_plan (plan_id, sort_order),
    KEY idx_oa_sign_plan_template_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA签约方案模板';
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
CREATE TABLE IF NOT EXISTS oa_sign_plan_version (
    version_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签约方案版本ID',
    plan_id bigint(20) NOT NULL COMMENT '来源签约方案ID',
    plan_name varchar(120) NOT NULL COMMENT '方案名称快照',
    version_no int(11) NOT NULL COMMENT '方案内版本号',
    scenario varchar(32) NOT NULL COMMENT '签约场景快照',
    shop_dept_id bigint(20) NOT NULL COMMENT '门店ID快照',
    legal_entity_id bigint(20) NOT NULL COMMENT '稳定法律主体ID快照',
    legal_entity_name varchar(160) NOT NULL COMMENT '法律主体名称快照',
    rule_json longtext NOT NULL COMMENT '规范化规则JSON快照',
    default_values_json longtext NOT NULL COMMENT '规范化默认值JSON快照',
    sign_deadline_days int(11) NOT NULL COMMENT '签署期限天数快照',
    reminder_policy_json longtext NOT NULL COMMENT '提醒策略JSON快照',
    auto_send_condition_json longtext NOT NULL COMMENT '自动发送条件JSON快照',
    publish_status varchar(20) NOT NULL COMMENT '发布状态',
    matching_status varchar(20) NOT NULL DEFAULT 'ENABLED' COMMENT '新任务匹配状态',
    published_by_user_id bigint(20) NOT NULL COMMENT '发布人用户ID',
    published_by varchar(64) NOT NULL COMMENT '发布人账号',
    published_time datetime NOT NULL COMMENT '发布时间',
    version_hash char(64) NOT NULL COMMENT '规范化版本SHA-256',
    create_time datetime NOT NULL COMMENT '创建时间',
    PRIMARY KEY (version_id),
    UNIQUE KEY uk_oa_sign_plan_version_no (plan_id, version_no),
    UNIQUE KEY uk_oa_sign_plan_version_hash (plan_id, version_hash),
    KEY idx_oa_sign_plan_version_match
        (scenario, shop_dept_id, legal_entity_id, publish_status, matching_status),
    CONSTRAINT fk_oa_sign_plan_version_plan
        FOREIGN KEY (plan_id) REFERENCES oa_sign_plan (plan_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=1000000000 DEFAULT CHARSET=utf8mb4 COMMENT='不可变签约方案发布版本';
CREATE TABLE IF NOT EXISTS oa_sign_plan_version_template (
    id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_version_id bigint(20) NOT NULL COMMENT '签约方案版本ID',
    template_id bigint(20) NOT NULL COMMENT '来源模板ID',
    template_version varchar(32) DEFAULT NULL COMMENT '模板版本快照',
    template_type varchar(64) NOT NULL COMMENT '模板类型快照',
    template_name varchar(120) DEFAULT NULL COMMENT '模板名称快照',
    source_file_url varchar(500) NOT NULL COMMENT '源模板文件地址快照',
    source_file_hash varchar(128) NOT NULL COMMENT '源模板文件SHA-256快照',
    required_placeholders varchar(1000) DEFAULT NULL COMMENT '必需占位符快照',
    sort_order int(11) NOT NULL COMMENT '模板排序快照',
    employee_sign_required char(1) NOT NULL COMMENT '是否要求员工签署',
    signature_position_json longtext DEFAULT NULL COMMENT '员工签名定位策略JSON快照',
    company_seal_position_json longtext DEFAULT NULL COMMENT '企业章定位策略JSON快照',
    match_condition_json longtext NOT NULL COMMENT '模板匹配条件JSON快照',
    create_time datetime NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_sign_plan_version_template_type (plan_version_id, template_type),
    KEY idx_oa_sign_plan_version_template_order (plan_version_id, sort_order, id),
    CONSTRAINT fk_oa_sign_plan_version_template_version
        FOREIGN KEY (plan_version_id) REFERENCES oa_sign_plan_version (version_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变签约方案模板快照';
ALTER TABLE oa_sign_plan ADD legal_entity_id bigint, ADD legal_entity_name varchar(160), ADD rule_json longtext, ADD default_values_json longtext, ADD sign_deadline_days int, ADD reminder_policy_json longtext, ADD auto_send_condition_json longtext;
ALTER TABLE oa_sign_template ADD employee_visible char(1) DEFAULT 'Y', ADD read_confirmation_required char(1) DEFAULT 'Y', ADD company_seal_required char(1) DEFAULT 'Y', ADD signature_position_json longtext, ADD company_seal_position_json longtext, ADD match_condition_json longtext;
ALTER TABLE oa_sign_plan_version_template ADD employee_visible char(1) DEFAULT 'Y', ADD read_confirmation_required char(1) DEFAULT 'Y', ADD company_seal_required char(1) DEFAULT 'Y';
CREATE TABLE oa_sign_task(task_id bigint primary key,plan_version_id bigint NOT NULL, FOREIGN KEY(plan_version_id) REFERENCES oa_sign_plan_version(version_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Existing 20260714 final-confirmation migration: new plan versions no longer prebind company.
ALTER TABLE oa_sign_plan_version MODIFY COLUMN legal_entity_id bigint(20) DEFAULT NULL, MODIFY COLUMN legal_entity_name varchar(160) DEFAULT NULL;
