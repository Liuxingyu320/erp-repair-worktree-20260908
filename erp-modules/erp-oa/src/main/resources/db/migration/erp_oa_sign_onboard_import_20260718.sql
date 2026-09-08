-- HR-selected employee + 签约数据.xlsx import batches.
-- MySQL 5.7/8.x compatible and safe to execute repeatedly.
-- This migration never updates or deletes historical signing facts.

-- Fresh installations must receive the same database-level concurrency guard as
-- the standalone 20260717 release migration. Existing installations that already
-- applied that migration are validated and left unchanged. Historical duplicates
-- are never guessed away: the migration fails closed before any guard DDL runs.
SET @erp_db = DATABASE();

DROP PROCEDURE IF EXISTS assert_oa_onboard_import_guard_prerequisites_20260718;
DELIMITER $$
CREATE PROCEDURE assert_oa_onboard_import_guard_prerequisites_20260718()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ONBOARD import guard requires a selected database';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_task';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_task is required for ONBOARD import guard';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_task'
       AND COLUMN_NAME IN ('task_id', 'employee_id', 'scenario', 'status');
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_task guard prerequisite columns are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM (
            SELECT employee_id
              FROM oa_sign_task
             WHERE UPPER(TRIM(scenario)) = 'ONBOARD'
               AND status NOT IN ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION')
             GROUP BY employee_id
            HAVING COUNT(*) > 1
           ) duplicate_open_onboard;
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate open ONBOARD tasks must be resolved before import guard installation';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_onboard_import_guard_prerequisites_20260718();
DROP PROCEDURE IF EXISTS assert_oa_onboard_import_guard_prerequisites_20260718;

SET @sql = IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'oa_sign_task'
        AND COLUMN_NAME = 'open_onboard_employee_id') = 0,
    'ALTER TABLE oa_sign_task ADD COLUMN open_onboard_employee_id bigint GENERATED ALWAYS AS (CASE WHEN UPPER(TRIM(scenario)) = ''ONBOARD'' AND status NOT IN (''SIGNED'', ''REFUSED'', ''EXPIRED'', ''CANCELLED'', ''NO_ACTION'') THEN employee_id ELSE NULL END) STORED COMMENT ''非终态ONBOARD员工唯一占位'' AFTER employee_id',
    'DO 0');
PREPARE onboard_import_guard_stmt FROM @sql;
EXECUTE onboard_import_guard_stmt;
DEALLOCATE PREPARE onboard_import_guard_stmt;

DROP PROCEDURE IF EXISTS assert_oa_onboard_import_guard_column_20260718;
DELIMITER $$
CREATE PROCEDURE assert_oa_onboard_import_guard_column_20260718()
BEGIN
    DECLARE v_count bigint DEFAULT 0;
    DECLARE v_expression text;

    SELECT COUNT(*), MAX(LOWER(GENERATION_EXPRESSION))
      INTO v_count, v_expression
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_task'
       AND COLUMN_NAME = 'open_onboard_employee_id'
       AND DATA_TYPE = 'bigint'
       AND EXTRA = 'STORED GENERATED';
    IF v_count <> 1
       OR LOCATE('scenario', v_expression) = 0
       OR LOCATE('onboard', v_expression) = 0
       OR LOCATE('status', v_expression) = 0
       OR LOCATE('signed', v_expression) = 0
       OR LOCATE('refused', v_expression) = 0
       OR LOCATE('expired', v_expression) = 0
       OR LOCATE('cancelled', v_expression) = 0
       OR LOCATE('no_action', v_expression) = 0
       OR LOCATE('employee_id', v_expression) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'incompatible oa_sign_task.open_onboard_employee_id definition';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_onboard_import_guard_column_20260718();
DROP PROCEDURE IF EXISTS assert_oa_onboard_import_guard_column_20260718;

SET @sql = IF(
    (SELECT COUNT(DISTINCT INDEX_NAME)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'oa_sign_task'
        AND INDEX_NAME = 'uk_oa_sign_task_open_onboard_employee') = 0,
    'ALTER TABLE oa_sign_task ADD UNIQUE INDEX uk_oa_sign_task_open_onboard_employee (open_onboard_employee_id)',
    'DO 0');
PREPARE onboard_import_guard_stmt FROM @sql;
EXECUTE onboard_import_guard_stmt;
DEALLOCATE PREPARE onboard_import_guard_stmt;

DROP PROCEDURE IF EXISTS assert_oa_onboard_import_guard_index_20260718;
DELIMITER $$
CREATE PROCEDURE assert_oa_onboard_import_guard_index_20260718()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM (
            SELECT INDEX_NAME,
                   GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS index_columns,
                   MIN(NON_UNIQUE) AS non_unique
              FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'oa_sign_task'
               AND INDEX_NAME = 'uk_oa_sign_task_open_onboard_employee'
             GROUP BY INDEX_NAME
           ) guard_index
     WHERE guard_index.index_columns = 'open_onboard_employee_id'
       AND guard_index.non_unique = 0;
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'incompatible ONBOARD open-task unique index';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_onboard_import_guard_index_20260718();
DROP PROCEDURE IF EXISTS assert_oa_onboard_import_guard_index_20260718;

CREATE TABLE IF NOT EXISTS oa_sign_onboard_import_batch (
    batch_id bigint NOT NULL AUTO_INCREMENT COMMENT '导入批次ID',
    batch_no varchar(64) NOT NULL COMMENT '公开批次编号',
    shop_dept_id bigint NOT NULL COMMENT '签约范围部门ID',
    shop_dept_name varchar(128) DEFAULT NULL COMMENT '签约范围名称快照',
    selected_employee_ids_json json NOT NULL COMMENT 'HR选人白名单ID快照',
    selection_hash char(64) NOT NULL COMMENT '排序后选人集合SHA-256',
    selected_count int NOT NULL COMMENT '选中员工数',
    original_file_name varchar(255) NOT NULL COMMENT '原文件名',
    file_size bigint NOT NULL COMMENT '文件字节数',
    file_sha256 char(64) NOT NULL COMMENT '原文件SHA-256',
    sheet_name varchar(64) NOT NULL COMMENT '固定为签约数据',
    status varchar(32) NOT NULL COMMENT 'PREVIEW_READY/GENERATING/PARTIAL_GENERATED/GENERATED',
    total_row_count int NOT NULL DEFAULT 0,
    matched_count int NOT NULL DEFAULT 0,
    excluded_count int NOT NULL DEFAULT 0,
    error_count int NOT NULL DEFAULT 0,
    warning_count int NOT NULL DEFAULT 0,
    generated_count int NOT NULL DEFAULT 0,
    created_by_user_id bigint NOT NULL COMMENT '创建HR用户ID',
    created_by_name varchar(64) DEFAULT NULL COMMENT '创建HR名称快照',
    expires_time datetime NOT NULL COMMENT '未完成预览过期时间',
    version bigint NOT NULL DEFAULT 1,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_oa_sign_onboard_import_batch_no (batch_no),
    KEY idx_oa_sign_onboard_import_reuse
        (created_by_user_id, shop_dept_id, selection_hash, file_sha256, expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入职签约Excel导入批次';

CREATE TABLE IF NOT EXISTS oa_sign_onboard_import_row (
    row_id bigint NOT NULL AUTO_INCREMENT COMMENT '导入行ID',
    batch_id bigint NOT NULL COMMENT '导入批次ID',
    source_row_number int NOT NULL COMMENT 'Excel源行号；缺行占位从501开始',
    row_hash char(64) NOT NULL COMMENT '规范化源行SHA-256',
    employee_id bigint DEFAULT NULL COMMENT '唯一匹配后的白名单员工ID',
    match_type varchar(32) NOT NULL COMMENT 'ID_NUMBER/PHONE_AND_NAME/EXTRA/CONFLICT/MISSING',
    employee_name_masked varchar(128) DEFAULT NULL,
    phone_masked varchar(32) DEFAULT NULL,
    id_number_masked varchar(32) DEFAULT NULL,
    address_masked varchar(255) DEFAULT NULL,
    snapshot_json json DEFAULT NULL COMMENT '规范化合同快照；仅服务端读取',
    status varchar(32) NOT NULL COMMENT '导入行状态',
    error_codes_json json NOT NULL COMMENT '硬错误编码数组',
    warning_codes_json json NOT NULL COMMENT '可确认警告编码数组',
    missing_fields_json json NOT NULL COMMENT '可补充字段编码数组',
    warning_confirmed tinyint(1) NOT NULL DEFAULT 0,
    warning_reason varchar(500) DEFAULT NULL,
    route_code varchar(16) DEFAULT NULL,
    plan_version_id bigint DEFAULT NULL,
    plan_version_hash char(64) DEFAULT NULL,
    data_request_id bigint DEFAULT NULL,
    task_id bigint DEFAULT NULL,
    package_id bigint DEFAULT NULL,
    historical_supplement tinyint(1) NOT NULL DEFAULT 0,
    historical_reason varchar(500) DEFAULT NULL,
    no_external_contract_confirmed tinyint(1) NOT NULL DEFAULT 0,
    generation_request_id varchar(64) DEFAULT NULL,
    source_event_version bigint DEFAULT NULL COMMENT '首次生成时冻结，不随重试变化',
    version bigint NOT NULL DEFAULT 1,
    generated_time datetime DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (row_id),
    UNIQUE KEY uk_oa_sign_onboard_import_source_row (batch_id, source_row_number),
    UNIQUE KEY uk_oa_sign_onboard_import_employee (batch_id, employee_id),
    KEY idx_oa_sign_onboard_import_row_status (batch_id, status),
    KEY idx_oa_sign_onboard_import_task (task_id),
    CONSTRAINT fk_oa_sign_onboard_import_row_batch FOREIGN KEY (batch_id)
        REFERENCES oa_sign_onboard_import_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入职签约Excel规范化行';

CREATE TABLE IF NOT EXISTS oa_sign_onboard_data_request (
    request_id bigint NOT NULL AUTO_INCREMENT COMMENT '补资料任务ID',
    request_no varchar(64) NOT NULL COMMENT '员工可见任务编号',
    batch_id bigint NOT NULL,
    row_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    allowed_fields_json json NOT NULL COMMENT '服务端员工字段白名单',
    submitted_values_json json DEFAULT NULL COMMENT '员工提交的客观事实',
    approved_hr_values_json json DEFAULT NULL COMMENT 'HR首次审批冻结的专属事实，不对员工返回',
    status varchar(32) NOT NULL COMMENT 'PENDING_EMPLOYEE/SUBMITTED/APPROVED/REJECTED/PROFILE_SYNC_FAILED/COMPLETED',
    submitted_time datetime DEFAULT NULL,
    reviewed_by_user_id bigint DEFAULT NULL,
    review_reason varchar(500) DEFAULT NULL,
    reviewed_time datetime DEFAULT NULL,
    profile_sync_status varchar(32) NOT NULL DEFAULT 'NOT_STARTED',
    profile_sync_request_id varchar(64) DEFAULT NULL COMMENT 'System幂等请求ID',
    profile_before_hash char(64) DEFAULT NULL,
    profile_after_hash char(64) DEFAULT NULL,
    version bigint NOT NULL DEFAULT 1,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id),
    UNIQUE KEY uk_oa_sign_onboard_data_request_no (request_no),
    KEY idx_oa_sign_onboard_data_request_row_history (row_id, request_id),
    KEY idx_oa_sign_onboard_data_request_mine (employee_id, status, request_id),
    CONSTRAINT fk_oa_sign_onboard_data_request_batch FOREIGN KEY (batch_id)
        REFERENCES oa_sign_onboard_import_batch (batch_id),
    CONSTRAINT fk_oa_sign_onboard_data_request_row FOREIGN KEY (row_id)
        REFERENCES oa_sign_onboard_import_row (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入职签约员工资料补充任务';

-- Older pre-release drafts allowed only one request per import row. Keep every completed
-- approval as audit history and let the row point at the newest request round. Add the
-- replacement FK-capable index first, then remove the legacy uniqueness constraint.
DROP PROCEDURE IF EXISTS migrate_oa_sign_onboard_data_request_history_20260719;
DELIMITER $$
CREATE PROCEDURE migrate_oa_sign_onboard_data_request_history_20260719()
BEGIN
    DECLARE v_history_index bigint DEFAULT 0;
    DECLARE v_legacy_unique bigint DEFAULT 0;
    SELECT COUNT(*) INTO v_history_index
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND BINARY TABLE_NAME = BINARY 'oa_sign_onboard_data_request'
       AND BINARY INDEX_NAME = BINARY 'idx_oa_sign_onboard_data_request_row_history';
    IF v_history_index = 0 THEN
        ALTER TABLE oa_sign_onboard_data_request
            ADD INDEX idx_oa_sign_onboard_data_request_row_history (row_id, request_id);
    END IF;
    SELECT COUNT(*) INTO v_legacy_unique
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND BINARY TABLE_NAME = BINARY 'oa_sign_onboard_data_request'
       AND BINARY INDEX_NAME = BINARY 'uk_oa_sign_onboard_data_request_row'
       AND NON_UNIQUE = 0;
    IF v_legacy_unique > 0 THEN
        ALTER TABLE oa_sign_onboard_data_request
            DROP INDEX uk_oa_sign_onboard_data_request_row;
    END IF;
END$$
DELIMITER ;

CALL migrate_oa_sign_onboard_data_request_history_20260719();
DROP PROCEDURE IF EXISTS migrate_oa_sign_onboard_data_request_history_20260719;

-- Appended personal-fact snapshots are nullable for all historical packages. They are
-- populated only from HR-reviewed facts before initial documents/signatures exist.
DROP PROCEDURE IF EXISTS add_oa_sign_onboard_import_column_20260718;
DELIMITER $$
CREATE PROCEDURE add_oa_sign_onboard_import_column_20260718(
    IN p_table_name varchar(64), IN p_column_name varchar(64), IN p_clause text)
BEGIN
    DECLARE v_count bigint DEFAULT 0;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND BINARY TABLE_NAME = BINARY p_table_name
       AND BINARY COLUMN_NAME = BINARY p_column_name;
    IF v_count = 0 THEN
        SET @oa_sign_onboard_import_ddl = CONCAT('ALTER TABLE `', p_table_name, '` ', p_clause);
        PREPARE oa_sign_onboard_import_stmt FROM @oa_sign_onboard_import_ddl;
        EXECUTE oa_sign_onboard_import_stmt;
        DEALLOCATE PREPARE oa_sign_onboard_import_stmt;
    END IF;
END$$
DELIMITER ;

CALL add_oa_sign_onboard_import_column_20260718(
    'oa_sign_onboard_data_request', 'approved_hr_values_json',
    'ADD COLUMN approved_hr_values_json json DEFAULT NULL COMMENT ''HR首次审批冻结的专属事实，不对员工返回'' AFTER submitted_values_json');
CALL add_oa_sign_onboard_import_column_20260718(
    'oa_sign_package', 'student_status_snapshot',
    'ADD COLUMN student_status_snapshot varchar(32) DEFAULT NULL COMMENT ''在校事实快照 STUDENT/NON_STUDENT'' AFTER employee_address_snapshot');
CALL add_oa_sign_onboard_import_column_20260718(
    'oa_sign_package', 'retirement_status_snapshot',
    'ADD COLUMN retirement_status_snapshot varchar(32) DEFAULT NULL COMMENT ''退休事实快照 RETIRED/NOT_RETIRED'' AFTER student_status_snapshot');
CALL add_oa_sign_onboard_import_column_20260718(
    'oa_sign_package', 'income_start_year_month',
    'ADD COLUMN income_start_year_month varchar(7) DEFAULT NULL COMMENT ''个人劳动收入主要生活来源起始年月 yyyy-MM'' AFTER retirement_status_snapshot');
CALL add_oa_sign_onboard_import_column_20260718(
    'oa_sign_package', 'recommended_company_snapshot',
    'ADD COLUMN recommended_company_snapshot varchar(255) DEFAULT NULL COMMENT ''Excel推荐签约公司，非已选法律主体'' AFTER source_plan_name');
CALL add_oa_sign_onboard_import_column_20260718(
    'oa_sign_package', 'recommended_legal_representative_snapshot',
    'ADD COLUMN recommended_legal_representative_snapshot varchar(128) DEFAULT NULL COMMENT ''Excel推荐法定代表人'' AFTER recommended_company_snapshot');
CALL add_oa_sign_onboard_import_column_20260718(
    'oa_sign_package', 'recommended_registered_address_snapshot',
    'ADD COLUMN recommended_registered_address_snapshot varchar(500) DEFAULT NULL COMMENT ''Excel推荐注册地'' AFTER recommended_legal_representative_snapshot');

-- System and OA completion flows both accept 255 characters. Repeating MODIFY is safe and
-- prevents a valid reviewed address from failing only when the immutable package is inserted.
ALTER TABLE oa_sign_package
    MODIFY COLUMN employee_address_snapshot varchar(255) DEFAULT NULL COMMENT '员工现住址快照';

DROP PROCEDURE IF EXISTS add_oa_sign_onboard_import_column_20260718;
