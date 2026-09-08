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

SET @erp_db = DATABASE();
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_id') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN source_plan_id bigint(20) DEFAULT NULL COMMENT ''来源签约方案ID'' AFTER shop_dept_name',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_name') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN source_plan_name varchar(120) DEFAULT NULL COMMENT ''来源签约方案名称快照'' AFTER source_plan_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND INDEX_NAME = 'idx_oa_sign_package_employee_plan_status') = 0,
    'ALTER TABLE oa_sign_package ADD INDEX idx_oa_sign_package_employee_plan_status (employee_id, source_plan_id, status, package_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
