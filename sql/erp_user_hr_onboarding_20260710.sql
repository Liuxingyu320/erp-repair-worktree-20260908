-- HR onboarding aggregate, import staging, sensitive-access audit, menus, and parameters.
-- Compatible with MySQL 5.7 and 8.0. Apply after the core system tables exist.
-- Execution contract: run this file in a dedicated client connection and
-- close the connection on any error. MySQL releases connection-scoped named
-- and table locks when that connection closes. Top-level DDL, procedure DDL,
-- LOCK TABLES, and CALL failures cannot be caught by SQL handlers in this file.

-- The guard is a session-local prepared statement and runs before the first
-- shared object is read or written. A caller may set the timeout to 0 for probes.
SET @hr_onboarding_migration_lock_name =
    CONCAT('erp_hr_onboarding_20260710:', MD5(COALESCE(DATABASE(), 'NO_DATABASE')));
SET @hr_onboarding_migration_lock_timeout_seconds =
    COALESCE(@hr_onboarding_migration_lock_timeout_seconds, 60);
SET @hr_onboarding_migration_lock_acquired =
    GET_LOCK(@hr_onboarding_migration_lock_name,
             @hr_onboarding_migration_lock_timeout_seconds);
SET @hr_onboarding_lock_guard_sql = IF(
    COALESCE(@hr_onboarding_migration_lock_acquired, 0) = 1,
    'DO 0',
    'SELECT * FROM information_schema.erp_hr_onboarding_lock_not_acquired');
PREPARE hr_onboarding_lock_guard FROM @hr_onboarding_lock_guard_sql;
EXECUTE hr_onboarding_lock_guard;
DEALLOCATE PREPARE hr_onboarding_lock_guard;
SET @hr_menu_apply_error = NULL;

CREATE TABLE IF NOT EXISTS hr_onboarding (
    onboarding_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '入职单ID',
    onboarding_no varchar(32) NOT NULL COMMENT '入职单编号',
    status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/READY/CONFIRMED/CANCELLED',
    version int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    employee_name varchar(64) NOT NULL COMMENT '姓名',
    phone_number varchar(32) NOT NULL COMMENT '手机号',
    employee_no varchar(64) DEFAULT NULL COMMENT '确认后工号',
    target_dept_id bigint(20) NOT NULL COMMENT '目标组织ID',
    target_store_id bigint(20) DEFAULT NULL COMMENT '目标门店ID',
    target_post_id bigint(20) NOT NULL COMMENT '目标岗位ID',
    direct_supervisor_user_id bigint(20) DEFAULT NULL COMMENT '直属主管用户ID',
    owner_user_id bigint(20) NOT NULL COMMENT '入职负责人用户ID',
    company_name varchar(100) DEFAULT NULL COMMENT '所属公司派生值',
    dept_level1_name varchar(100) DEFAULT NULL COMMENT '1级部门派生值',
    dept_level2_name varchar(100) DEFAULT NULL COMMENT '2级部门派生值',
    dept_level3_name varchar(100) DEFAULT NULL COMMENT '3级部门派生值',
    store_name varchar(100) DEFAULT NULL COMMENT '4级门店派生值',
    position_name varchar(100) DEFAULT NULL COMMENT '职位派生值',
    department_supervisor varchar(100) DEFAULT NULL COMMENT '部门主管派生值',
    job_grade varchar(64) DEFAULT NULL COMMENT '职级',
    employee_category varchar(64) NOT NULL COMMENT '人员类别',
    sex char(1) DEFAULT NULL COMMENT '性别',
    birth_date date DEFAULT NULL COMMENT '出生日期',
    id_type varchar(64) DEFAULT NULL COMMENT '证件类型',
    id_number varchar(64) DEFAULT NULL COMMENT '证件号码',
    registered_residence varchar(255) DEFAULT NULL COMMENT '户口所在地',
    current_address varchar(255) DEFAULT NULL COMMENT '现居住地址',
    marital_status varchar(32) DEFAULT NULL COMMENT '婚姻状况',
    ethnicity varchar(64) DEFAULT NULL COMMENT '民族',
    emergency_contact varchar(100) DEFAULT NULL COMMENT '紧急联系人',
    emergency_contact_relation varchar(64) DEFAULT NULL COMMENT '紧急联系人关系',
    emergency_contact_phone varchar(32) DEFAULT NULL COMMENT '紧急联系人电话',
    expected_entry_date date NOT NULL COMMENT '预计入职日期',
    actual_entry_date date DEFAULT NULL COMMENT '实际入职日期',
    work_location varchar(100) DEFAULT NULL COMMENT '工作所在地',
    work_city_level varchar(64) DEFAULT NULL COMMENT '工作城市级别',
    bank_name varchar(100) DEFAULT NULL COMMENT '开户银行',
    bank_account varchar(64) DEFAULT NULL COMMENT '银行卡号',
    contract_type varchar(32) DEFAULT NULL COMMENT '合同类型',
    social_type varchar(32) DEFAULT NULL COMMENT '社保类型',
    probation_period varchar(64) DEFAULT NULL COMMENT '试用期',
    legal_entity varchar(100) DEFAULT NULL COMMENT '法人单位',
    source_type varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL/IMPORT',
    linked_user_id bigint(20) DEFAULT NULL COMMENT '确认后关联用户ID',
    preferred_conflict_action varchar(32) DEFAULT NULL COMMENT '导入预选冲突处理',
    preferred_bind_user_id bigint(20) DEFAULT NULL COMMENT '导入预选绑定用户ID',
    account_configuration_status varchar(16) DEFAULT NULL COMMENT '账号配置状态',
    account_risk_code varchar(64) DEFAULT NULL COMMENT '账号配置风险码',
    cancel_reason varchar(255) DEFAULT NULL COMMENT '取消原因',
    cancelled_by_user_id bigint(20) DEFAULT NULL COMMENT '取消操作用户ID',
    cancelled_by varchar(64) DEFAULT NULL COMMENT '取消操作人',
    cancelled_time datetime DEFAULT NULL COMMENT '取消时间',
    confirmed_by_user_id bigint(20) DEFAULT NULL COMMENT '确认操作用户ID',
    confirmed_by varchar(64) DEFAULT NULL COMMENT '确认操作人',
    confirmed_time datetime DEFAULT NULL COMMENT '确认时间',
    confirm_idempotency_key varchar(64) DEFAULT NULL COMMENT '确认幂等键',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (onboarding_id),
    UNIQUE KEY uk_hr_onboarding_no (onboarding_no),
    UNIQUE KEY uk_hr_onboarding_confirm_key (confirm_idempotency_key),
    KEY idx_hr_onboarding_status_date (status, expected_entry_date),
    KEY idx_hr_onboarding_phone (phone_number),
    KEY idx_hr_onboarding_id_number (id_number),
    KEY idx_hr_onboarding_target_dept (target_dept_id),
    KEY idx_hr_onboarding_linked_user (linked_user_id),
    KEY idx_hr_onboarding_account_risk (account_configuration_status, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR入职单';

CREATE TABLE IF NOT EXISTS hr_onboarding_operation_log (
    log_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '操作日志ID',
    onboarding_id bigint(20) NOT NULL COMMENT '入职单ID',
    operation_type varchar(32) NOT NULL COMMENT '操作类型',
    from_status varchar(16) DEFAULT NULL COMMENT '原状态',
    to_status varchar(16) DEFAULT NULL COMMENT '新状态',
    operator_user_id bigint(20) NOT NULL COMMENT '操作用户ID',
    operator_name varchar(64) NOT NULL COMMENT '操作人',
    changed_field_keys varchar(1000) DEFAULT NULL COMMENT '变更字段键列表，不含字段值',
    decision_summary varchar(500) DEFAULT NULL COMMENT '冲突决策摘要',
    operation_summary varchar(1000) DEFAULT NULL COMMENT '脱敏操作摘要',
    operation_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (log_id),
    KEY idx_hr_onboarding_log_onboarding (onboarding_id, operation_time),
    KEY idx_hr_onboarding_log_operator (operator_user_id, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR入职单操作日志';

CREATE TABLE IF NOT EXISTS hr_onboarding_position_config (
    config_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '岗位入职配置ID',
    post_id bigint(20) NOT NULL COMMENT '岗位ID',
    employee_category varchar(64) NOT NULL COMMENT '人员类别',
    data_scope_strategy varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '数据范围：TARGET_STORE/TARGET_DEPT/NONE',
    contract_type_mode varchar(32) NOT NULL DEFAULT 'OPTIONAL' COMMENT '合同类型规则模式',
    default_contract_type varchar(32) DEFAULT NULL COMMENT '默认合同类型',
    social_type_mode varchar(32) NOT NULL DEFAULT 'OPTIONAL' COMMENT '社保类型规则模式',
    default_social_type varchar(32) DEFAULT NULL COMMENT '默认社保类型',
    probation_period_mode varchar(32) NOT NULL DEFAULT 'OPTIONAL' COMMENT '试用期规则模式',
    default_probation_period varchar(64) DEFAULT NULL COMMENT '默认试用期',
    job_grade varchar(64) DEFAULT NULL COMMENT '默认职级',
    account_enabled char(1) NOT NULL DEFAULT '1' COMMENT '是否启用账号：1是0否',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态：0正常1停用',
    version int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_hr_onboarding_position_category (post_id, employee_category),
    KEY idx_hr_onboarding_config_status (status, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR岗位入职配置';

CREATE TABLE IF NOT EXISTS hr_onboarding_position_config_role (
    config_role_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置角色关系ID',
    config_id bigint(20) NOT NULL COMMENT '岗位入职配置ID',
    role_id bigint(20) NOT NULL COMMENT '默认角色ID',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (config_role_id),
    UNIQUE KEY uk_hr_onboarding_config_role (config_id, role_id),
    KEY idx_hr_onboarding_config_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR岗位入职配置默认角色';

CREATE TABLE IF NOT EXISTS hr_onboarding_import_batch (
    batch_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导入批次ID',
    batch_no varchar(32) NOT NULL COMMENT '导入批次编号',
    file_name varchar(255) NOT NULL COMMENT '原文件名',
    file_size bigint(20) DEFAULT NULL COMMENT '文件大小',
    file_hash varchar(64) DEFAULT NULL COMMENT '文件摘要',
    status varchar(32) NOT NULL DEFAULT 'PREVIEWED' COMMENT '批次状态',
    version int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    total_rows int(11) NOT NULL DEFAULT 0 COMMENT '总行数',
    importable_rows int(11) NOT NULL DEFAULT 0 COMMENT '可导入行数',
    warning_rows int(11) NOT NULL DEFAULT 0 COMMENT '警告行数',
    invalid_rows int(11) NOT NULL DEFAULT 0 COMMENT '无效行数',
    duplicate_rows int(11) NOT NULL DEFAULT 0 COMMENT '疑似重复行数',
    bindable_rows int(11) NOT NULL DEFAULT 0 COMMENT '可绑定账号行数',
    success_rows int(11) NOT NULL DEFAULT 0 COMMENT '成功行数',
    failure_rows int(11) NOT NULL DEFAULT 0 COMMENT '失败行数',
    creator_user_id bigint(20) NOT NULL COMMENT '批次创建用户ID',
    creator_name varchar(64) NOT NULL COMMENT '批次创建人',
    confirmed_by_user_id bigint(20) DEFAULT NULL COMMENT '确认用户ID',
    confirmed_by varchar(64) DEFAULT NULL COMMENT '确认人',
    confirmed_time datetime DEFAULT NULL COMMENT '确认时间',
    expires_time datetime DEFAULT NULL COMMENT '未确认批次过期时间',
    result_summary varchar(1000) DEFAULT NULL COMMENT '批次结果摘要',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_hr_onboarding_import_batch_no (batch_no),
    KEY idx_hr_onboarding_import_batch_creator (creator_user_id, create_time),
    KEY idx_hr_onboarding_import_batch_status (status, expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR入职导入批次';

CREATE TABLE IF NOT EXISTS hr_onboarding_import_row (
    row_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导入行ID',
    batch_id bigint(20) NOT NULL COMMENT '导入批次ID',
    source_row_number int(11) NOT NULL COMMENT 'Excel源行号',
    category varchar(32) NOT NULL COMMENT '预检分类',
    row_status varchar(32) NOT NULL DEFAULT 'PREVIEWED' COMMENT '处理状态',
    version int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    payload_json longtext NOT NULL COMMENT '规范化导入暂存载荷，由应用解析',
    warning_codes varchar(2000) DEFAULT NULL COMMENT '警告码列表',
    error_codes varchar(2000) DEFAULT NULL COMMENT '错误码列表',
    candidate_summary varchar(1000) DEFAULT NULL COMMENT '脱敏候选摘要',
    candidate_type varchar(32) DEFAULT NULL COMMENT '候选类型',
    candidate_user_id bigint(20) DEFAULT NULL COMMENT '候选用户ID',
    candidate_onboarding_id bigint(20) DEFAULT NULL COMMENT '候选入职单ID',
    decision varchar(32) DEFAULT NULL COMMENT '逐行确认决策',
    bind_user_id bigint(20) DEFAULT NULL COMMENT '决策绑定用户ID',
    result_onboarding_id bigint(20) DEFAULT NULL COMMENT '创建的入职单ID',
    result_code varchar(64) DEFAULT NULL COMMENT '处理结果码',
    result_message varchar(1000) DEFAULT NULL COMMENT '脱敏结果说明',
    processed_by_user_id bigint(20) DEFAULT NULL COMMENT '处理用户ID',
    processed_by varchar(64) DEFAULT NULL COMMENT '处理人',
    processed_time datetime DEFAULT NULL COMMENT '处理时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (row_id),
    UNIQUE KEY uk_hr_onboarding_import_source_row (batch_id, source_row_number),
    KEY idx_hr_onboarding_import_row_batch (batch_id, row_status),
    KEY idx_hr_onboarding_import_row_category (batch_id, category),
    KEY idx_hr_onboarding_import_row_candidate (candidate_user_id, candidate_onboarding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR入职导入预检行';

CREATE TABLE IF NOT EXISTS hr_sensitive_access_log (
    access_log_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '敏感访问日志ID',
    operator_user_id bigint(20) NOT NULL COMMENT '操作用户ID',
    operator_name varchar(64) NOT NULL COMMENT '操作人',
    employee_user_id bigint(20) DEFAULT NULL COMMENT '被访问员工用户ID',
    field_key varchar(64) DEFAULT NULL COMMENT '敏感字段键',
    export_scope varchar(1000) DEFAULT NULL COMMENT '导出字段和筛选范围摘要',
    action_type varchar(32) NOT NULL COMMENT '动作：REVEAL/EXPORT',
    request_ip varchar(128) DEFAULT NULL COMMENT '请求IP',
    event_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    result varchar(32) NOT NULL COMMENT '访问结果',
    result_message varchar(500) DEFAULT NULL COMMENT '脱敏结果说明',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (access_log_id),
    KEY idx_hr_sensitive_access_employee (employee_user_id, event_time),
    KEY idx_hr_sensitive_access_operator (operator_user_id, event_time),
    KEY idx_hr_sensitive_access_action (action_type, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR敏感字段访问审计';

-- Existing deployments receive the supervisor relation without replacing any profile column.
SET @erp_db = DATABASE();
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile'
           AND COLUMN_NAME = 'direct_supervisor_user_id') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN direct_supervisor_user_id bigint(20) DEFAULT NULL COMMENT ''直属主管用户ID'' AFTER direct_supervisor',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Seed the complete HR route and permission tree without assuming fixed menu IDs.
DROP TEMPORARY TABLE IF EXISTS tmp_hr_onboarding_menu_seed;
CREATE TEMPORARY TABLE tmp_hr_onboarding_menu_seed (
    seed_key varchar(64) NOT NULL,
    parent_seed_key varchar(64) DEFAULT NULL,
    seed_order int(11) NOT NULL,
    menu_name varchar(64) NOT NULL,
    order_num int(11) NOT NULL,
    path varchar(200) NOT NULL DEFAULT '',
    component varchar(255) DEFAULT NULL,
    route_name varchar(64) NOT NULL DEFAULT '',
    menu_type char(1) NOT NULL,
    perms varchar(100) DEFAULT NULL,
    icon varchar(100) NOT NULL DEFAULT '#',
    remark varchar(500) DEFAULT NULL,
    menu_id bigint(20) DEFAULT NULL,
    parent_menu_id bigint(20) DEFAULT NULL,
    existing_flag char(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (seed_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_hr_onboarding_menu_seed
    (seed_key, parent_seed_key, seed_order, menu_name, order_num, path, component,
     route_name, menu_type, perms, icon, remark)
VALUES
    ('HR_ROOT', NULL, 1, '人事管理', 50, 'hr', NULL, 'Hr', 'M', NULL, 'peoples', '人事档案与独立入职管理'),
    ('EMPLOYEE_PAGE', 'HR_ROOT', 10, '员工档案', 1, 'employee', 'hr/employee/index', 'HrEmployee', 'C', 'hr:employee:list', 'user', '正式员工档案'),
    ('ONBOARDING_PAGE', 'HR_ROOT', 20, '入职管理', 2, 'onboarding', 'hr/onboarding/index', 'HrOnboarding', 'C', 'hr:onboarding:list', 'form', '独立入职单工作台'),
    ('COMPLETENESS_PAGE', 'HR_ROOT', 30, '资料完整度', 3, 'completeness', 'hr/completeness/index', 'HrCompleteness', 'C', 'hr:completeness:list', 'chart', '员工资料完整度与账号风险'),
    ('IMPORT_PAGE', 'HR_ROOT', 40, '人事导入导出', 4, 'import-export', 'hr/importExport/index', 'HrImportExport', 'C', 'hr:onboarding:import:preview', 'upload', '人事入职预检导入与员工导出'),
    ('POSITION_CONFIG_PAGE', 'HR_ROOT', 50, '岗位入职配置', 5, 'position-config', 'hr/positionConfig/index', 'HrPositionConfig', 'C', 'hr:onboarding:config', 'tree-table', '岗位人员类别入职规则配置'),
    ('EMPLOYEE_QUERY', 'EMPLOYEE_PAGE', 101, '员工档案查询', 1, '', NULL, '', 'F', 'hr:employee:query', '#', ''),
    ('EMPLOYEE_ADD', 'EMPLOYEE_PAGE', 102, '员工档案新增', 2, '', NULL, '', 'F', 'hr:employee:add', '#', ''),
    ('EMPLOYEE_EDIT', 'EMPLOYEE_PAGE', 103, '员工档案编辑', 3, '', NULL, '', 'F', 'hr:employee:edit', '#', ''),
    ('EMPLOYEE_EXPORT', 'EMPLOYEE_PAGE', 104, '员工档案导出', 4, '', NULL, '', 'F', 'hr:employee:export', '#', ''),
    ('EMPLOYEE_SENSITIVE_VIEW', 'EMPLOYEE_PAGE', 105, '员工敏感字段查看', 5, '', NULL, '', 'F', 'hr:employee:sensitive:view', '#', '敏感值查看必须记录审计'),
    ('EMPLOYEE_SENSITIVE_EXPORT', 'EMPLOYEE_PAGE', 106, '员工敏感字段导出', 6, '', NULL, '', 'F', 'hr:employee:export:sensitive', '#', '敏感导出必须记录审计'),
    ('ONBOARDING_WORKBENCH', 'ONBOARDING_PAGE', 201, '入职工作台汇总', 1, '', NULL, '', 'F', 'hr:onboarding:workbench', '#', ''),
    ('ONBOARDING_QUERY', 'ONBOARDING_PAGE', 202, '入职单查询', 2, '', NULL, '', 'F', 'hr:onboarding:query', '#', ''),
    ('ONBOARDING_ADD', 'ONBOARDING_PAGE', 203, '入职单新增', 3, '', NULL, '', 'F', 'hr:onboarding:add', '#', ''),
    ('ONBOARDING_EDIT', 'ONBOARDING_PAGE', 204, '入职单编辑', 4, '', NULL, '', 'F', 'hr:onboarding:edit', '#', ''),
    ('ONBOARDING_READY', 'ONBOARDING_PAGE', 205, '标记待到岗', 5, '', NULL, '', 'F', 'hr:onboarding:ready', '#', ''),
    ('ONBOARDING_RETURN', 'ONBOARDING_PAGE', 206, '退回待补资料', 6, '', NULL, '', 'F', 'hr:onboarding:return', '#', ''),
    ('ONBOARDING_CONFIRM', 'ONBOARDING_PAGE', 207, '确认入职', 7, '', NULL, '', 'F', 'hr:onboarding:confirm', '#', ''),
    ('ONBOARDING_CANCEL', 'ONBOARDING_PAGE', 208, '取消入职', 8, '', NULL, '', 'F', 'hr:onboarding:cancel', '#', ''),
    ('ONBOARDING_RESTORE', 'ONBOARDING_PAGE', 209, '恢复入职单', 9, '', NULL, '', 'F', 'hr:onboarding:restore', '#', ''),
    ('ONBOARDING_IMPORT_CONFIRM', 'IMPORT_PAGE', 401, '入职导入确认', 1, '', NULL, '', 'F', 'hr:onboarding:import:confirm', '#', ''),
    ('ONBOARDING_IMPORT_TEMPLATE', 'IMPORT_PAGE', 402, '入职导入模板', 2, '', NULL, '', 'F', 'hr:onboarding:import:template', '#', ''),
    ('LEGACY_IMPORT_PREVIEW', 'IMPORT_PAGE', 403, '员工档案导入预检', 3, '', NULL, '', 'F', 'hr:import:preview', '#', '兼容现有员工档案导入页面'),
    ('LEGACY_IMPORT_CONFIRM', 'IMPORT_PAGE', 404, '员工档案导入确认', 4, '', NULL, '', 'F', 'hr:import:confirm', '#', '兼容现有员工档案导入页面'),
    ('LEGACY_IMPORT_TEMPLATE', 'IMPORT_PAGE', 405, '员工档案导入模板', 5, '', NULL, '', 'F', 'hr:import:template', '#', '兼容现有员工档案导入页面');

DROP PROCEDURE IF EXISTS erp_hr_onboarding_raise_apply_error;
DELIMITER $$
CREATE PROCEDURE erp_hr_onboarding_raise_apply_error()
BEGIN
    DECLARE assertion_message varchar(128);
    IF @hr_menu_apply_error IS NOT NULL THEN
        SET assertion_message = LEFT(@hr_menu_apply_error, 128);
        SET @hr_onboarding_migration_lock_released =
            RELEASE_LOCK(@hr_onboarding_migration_lock_name);
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = assertion_message;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS erp_hr_onboarding_apply_menu_config;
DELIMITER $$
CREATE PROCEDURE erp_hr_onboarding_apply_menu_config()
migration_body: BEGIN
    DECLARE finished int DEFAULT 0;
    DECLARE current_seed_key varchar(64);
    DECLARE current_menu_name varchar(64);
    DECLARE current_menu_type char(1);
    DECLARE current_perms varchar(100);
    DECLARE current_path varchar(200);
    DECLARE current_component varchar(255);
    DECLARE matched_count int DEFAULT 0;
    DECLARE matched_menu_id bigint(20);
    DECLARE error_number int DEFAULT 0;
    DECLARE error_message text;
    DECLARE seed_cursor CURSOR FOR
        SELECT seed_key, menu_name, menu_type, perms, path, component
        FROM tmp_hr_onboarding_menu_seed
        ORDER BY seed_order;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1
            error_number = MYSQL_ERRNO,
            error_message = MESSAGE_TEXT;
        SET @hr_menu_apply_error = LEFT(
            CONCAT('HR menu migration SQL error ', error_number, ': ', error_message), 128);
    END;

    SET @hr_menu_apply_error = NULL;
    OPEN seed_cursor;

    seed_loop: LOOP
        FETCH seed_cursor
            INTO current_seed_key, current_menu_name, current_menu_type,
                 current_perms, current_path, current_component;
        IF finished = 1 THEN
            LEAVE seed_loop;
        END IF;

        SET matched_count = 0;
        SET matched_menu_id = NULL;

        IF current_menu_type = 'M' THEN
            SELECT COUNT(DISTINCT menu_id), MIN(menu_id)
            INTO matched_count, matched_menu_id
            FROM sys_menu
            WHERE menu_type = 'M'
              AND ((CONVERT(path USING utf8mb4) COLLATE utf8mb4_unicode_ci =
                    CONVERT(current_path USING utf8mb4) COLLATE utf8mb4_unicode_ci)
                   OR (CONVERT(menu_name USING utf8mb4) COLLATE utf8mb4_unicode_ci =
                       CONVERT(current_menu_name USING utf8mb4) COLLATE utf8mb4_unicode_ci));
        ELSEIF current_menu_type = 'C' THEN
            SELECT COUNT(DISTINCT menu_id), MIN(menu_id)
            INTO matched_count, matched_menu_id
            FROM sys_menu
            WHERE menu_type = 'C'
              AND ((current_perms IS NOT NULL
                    AND CONVERT(perms USING utf8mb4) COLLATE utf8mb4_unicode_ci =
                        CONVERT(current_perms USING utf8mb4) COLLATE utf8mb4_unicode_ci)
                   OR (current_component IS NOT NULL
                       AND CONVERT(component USING utf8mb4) COLLATE utf8mb4_unicode_ci =
                           CONVERT(current_component USING utf8mb4) COLLATE utf8mb4_unicode_ci));
        ELSE
            SELECT COUNT(DISTINCT menu_id), MIN(menu_id)
            INTO matched_count, matched_menu_id
            FROM sys_menu
            WHERE menu_type = 'F'
              AND CONVERT(perms USING utf8mb4) COLLATE utf8mb4_unicode_ci =
                  CONVERT(current_perms USING utf8mb4) COLLATE utf8mb4_unicode_ci;
        END IF;

        IF matched_count > 1 THEN
            SET @hr_menu_apply_error = LEFT(
                CONCAT('ambiguous HR menu seed ', current_seed_key,
                       ': natural keys match multiple menu_id'), 128);
            CLOSE seed_cursor;
            LEAVE migration_body;
        END IF;

        UPDATE tmp_hr_onboarding_menu_seed
        SET menu_id = matched_menu_id,
            existing_flag = IF(matched_menu_id IS NULL, '0', '1')
        WHERE CONVERT(seed_key USING utf8mb4) COLLATE utf8mb4_unicode_ci =
              CONVERT(current_seed_key USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    END LOOP;
    CLOSE seed_cursor;

    SELECT COUNT(*)
    INTO matched_count
    FROM (
        SELECT menu_id
        FROM tmp_hr_onboarding_menu_seed
        WHERE menu_id IS NOT NULL
        GROUP BY menu_id
        HAVING COUNT(*) > 1
    ) duplicate_seed_menu_ids;

    IF matched_count > 0 THEN
        SET @hr_menu_apply_error =
            'ambiguous HR menu seed assignments: seeds share one menu_id';
        LEAVE migration_body;
    END IF;

    -- Existing duplicate parameter keys are ambiguous when config_key has no
    -- unique constraint. Abort before any menu or parameter write.
    SELECT COUNT(*) INTO matched_count
    FROM sys_config
    WHERE config_key = 'hr.onboarding.post_entry_due_days';
    IF matched_count > 1 THEN
        SET @hr_menu_apply_error =
            'ambiguous HR config key hr.onboarding.post_entry_due_days';
        LEAVE migration_body;
    END IF;

    SELECT COUNT(*) INTO matched_count
    FROM sys_config
    WHERE config_key = 'hr.onboarding.import_retention_days';
    IF matched_count > 1 THEN
        SET @hr_menu_apply_error =
            'ambiguous HR config key hr.onboarding.import_retention_days';
        LEAVE migration_body;
    END IF;

    SELECT COUNT(*) INTO matched_count
    FROM sys_config
    WHERE config_key = 'hr.employee.no.prefix';
    IF matched_count > 1 THEN
        SET @hr_menu_apply_error =
            'ambiguous HR config key hr.employee.no.prefix';
        LEAVE migration_body;
    END IF;

    SET @next_hr_menu_id = (SELECT COALESCE(MAX(menu_id), 0) FROM sys_menu);
    UPDATE tmp_hr_onboarding_menu_seed
    SET menu_id = (@next_hr_menu_id := @next_hr_menu_id + 1)
    WHERE menu_id IS NULL
    ORDER BY seed_order;

    SET @hr_root_menu_id = (SELECT menu_id FROM tmp_hr_onboarding_menu_seed WHERE seed_key = 'HR_ROOT');
    SET @hr_employee_page_id = (SELECT menu_id FROM tmp_hr_onboarding_menu_seed WHERE seed_key = 'EMPLOYEE_PAGE');
    SET @hr_onboarding_page_id = (SELECT menu_id FROM tmp_hr_onboarding_menu_seed WHERE seed_key = 'ONBOARDING_PAGE');
    SET @hr_completeness_page_id = (SELECT menu_id FROM tmp_hr_onboarding_menu_seed WHERE seed_key = 'COMPLETENESS_PAGE');
    SET @hr_import_page_id = (SELECT menu_id FROM tmp_hr_onboarding_menu_seed WHERE seed_key = 'IMPORT_PAGE');
    SET @hr_position_config_page_id = (SELECT menu_id FROM tmp_hr_onboarding_menu_seed WHERE seed_key = 'POSITION_CONFIG_PAGE');

    UPDATE tmp_hr_onboarding_menu_seed
    SET parent_menu_id = CASE parent_seed_key
        WHEN 'HR_ROOT' THEN @hr_root_menu_id
        WHEN 'EMPLOYEE_PAGE' THEN @hr_employee_page_id
        WHEN 'ONBOARDING_PAGE' THEN @hr_onboarding_page_id
        WHEN 'COMPLETENESS_PAGE' THEN @hr_completeness_page_id
        WHEN 'IMPORT_PAGE' THEN @hr_import_page_id
        WHEN 'POSITION_CONFIG_PAGE' THEN @hr_position_config_page_id
        ELSE 0
    END;

    UPDATE sys_menu
    JOIN tmp_hr_onboarding_menu_seed seed
      ON seed.menu_id = sys_menu.menu_id
     AND seed.existing_flag = '1'
    SET sys_menu.menu_name = seed.menu_name,
        sys_menu.parent_id = seed.parent_menu_id,
        sys_menu.order_num = seed.order_num,
        sys_menu.path = seed.path,
        sys_menu.component = seed.component,
        sys_menu.query = NULL,
        sys_menu.route_name = seed.route_name,
        sys_menu.is_frame = 1,
        sys_menu.is_cache = 0,
        sys_menu.menu_type = seed.menu_type,
        sys_menu.visible = '0',
        sys_menu.status = '0',
        sys_menu.perms = seed.perms,
        sys_menu.icon = seed.icon,
        sys_menu.update_by = 'system',
        sys_menu.update_time = CURRENT_TIMESTAMP,
        sys_menu.remark = seed.remark;

    INSERT INTO sys_menu
        (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
         is_frame, is_cache, menu_type, visible, status, perms, icon,
         create_by, create_time, update_by, update_time, remark)
    SELECT
        menu_id, menu_name, parent_menu_id, order_num, path, component, NULL, route_name,
        1, 0, menu_type, '0', '0', perms, icon,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, remark
    FROM tmp_hr_onboarding_menu_seed
    WHERE existing_flag = '0'
    ORDER BY seed_order;

    -- sys_config shares the same WRITE lock, so count-then-insert is safe even
    -- when the deployed schema has no unique key on config_key.
    SELECT COUNT(*) INTO matched_count
    FROM sys_config
    WHERE config_key = 'hr.onboarding.post_entry_due_days';
    IF matched_count = 0 THEN
        INSERT INTO sys_config
            (config_name, config_key, config_value, config_type, create_by, create_time, remark)
        VALUES
            ('入职后资料补齐期限（天）', 'hr.onboarding.post_entry_due_days', '7', 'Y',
             'system', CURRENT_TIMESTAMP, '入职后补充资料的默认自然日期限');
    END IF;

    SELECT COUNT(*) INTO matched_count
    FROM sys_config
    WHERE config_key = 'hr.onboarding.import_retention_days';
    IF matched_count = 0 THEN
        INSERT INTO sys_config
            (config_name, config_key, config_value, config_type, create_by, create_time, remark)
        VALUES
            ('入职导入预检保留期限（天）', 'hr.onboarding.import_retention_days', '30', 'Y',
             'system', CURRENT_TIMESTAMP, '未确认入职导入批次的默认保留期限');
    END IF;

    SELECT COUNT(*) INTO matched_count
    FROM sys_config
    WHERE config_key = 'hr.employee.no.prefix';
    IF matched_count = 0 THEN
        INSERT INTO sys_config
            (config_name, config_key, config_value, config_type, create_by, create_time, remark)
        VALUES
            ('员工工号前缀', 'hr.employee.no.prefix', 'E', 'Y',
             'system', CURRENT_TIMESTAMP, '确认入职时生成员工工号的默认前缀');
    END IF;
END$$
DELIMITER ;

-- ID resolution, allocation, menu writes, and config-key checks are serialized
-- while both mutable system tables are WRITE locked.
LOCK TABLES sys_menu WRITE, sys_config WRITE;
CALL erp_hr_onboarding_apply_menu_config();
UNLOCK TABLES;

DROP TEMPORARY TABLE IF EXISTS tmp_hr_onboarding_menu_seed;
DROP PROCEDURE IF EXISTS erp_hr_onboarding_apply_menu_config;

-- The procedure records validation or SQL errors instead of raising them while
-- table locks are held. After UNLOCK, the assertion releases the named lock
-- immediately before SIGNAL so failures cannot leak locks.
CALL erp_hr_onboarding_raise_apply_error();
DROP PROCEDURE IF EXISTS erp_hr_onboarding_raise_apply_error;

SET @hr_onboarding_migration_lock_released =
    RELEASE_LOCK(@hr_onboarding_migration_lock_name);
