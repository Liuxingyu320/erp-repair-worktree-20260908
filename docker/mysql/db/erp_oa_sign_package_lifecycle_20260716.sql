-- Signing package deadline, terminal-state, resolution, and reissue persistence.
-- MySQL 5.7/8.x compatible and safe to run repeatedly.
--
-- Deliberate fail-closed rule: this migration never invents a deadline policy for
-- an already-active signing record. DDL is idempotent and may commit before the
-- final assertion raises; operators can then repair the explicit policy fields
-- from approved evidence and rerun this same migration.

SET @erp_db = DATABASE();

DROP PROCEDURE IF EXISTS assert_oa_sign_package_lifecycle_prerequisites_20260716;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_package_lifecycle_prerequisites_20260716()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing lifecycle migration requires a selected database';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('oa_sign_package', 'oa_sign_task');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing lifecycle migration prerequisite tables are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_package'
       AND COLUMN_NAME IN ('package_id', 'status', 'sent_time', 'sign_deadline',
                           'version', 'task_id', 'employee_id', 'shop_dept_id',
                           'plan_version_id');
    IF v_count <> 9 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_package lifecycle prerequisite columns are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_task'
       AND COLUMN_NAME IN ('task_id', 'status', 'sent_time', 'sign_deadline',
                           'version', 'package_id', 'assigned_hr_user_id', 'created_time',
                           'cancelled_time', 'employee_id', 'shop_dept_id',
                           'plan_version_id');
    IF v_count <> 12 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_task lifecycle prerequisite columns are missing';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_package_lifecycle_prerequisites_20260716();
DROP PROCEDURE IF EXISTS assert_oa_sign_package_lifecycle_prerequisites_20260716;

-- The existing value pending_final_confirm is 21 characters. Both known local
-- databases still have varchar(20), which fails under STRICT_TRANS_TABLES.
SET @sql = IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'oa_sign_package'
        AND COLUMN_NAME = 'status'
        AND (DATA_TYPE <> 'varchar' OR CHARACTER_MAXIMUM_LENGTH < 32)) > 0,
    'ALTER TABLE oa_sign_package MODIFY COLUMN status varchar(32) NOT NULL DEFAULT ''draft'' COMMENT ''draft/pending_sign/part_viewed/pending_company/pending_final_confirm/signed/refused/expired/voided/failed''',
    'DO 0');
PREPARE lifecycle_stmt FROM @sql;
EXECUTE lifecycle_stmt;
DEALLOCATE PREPARE lifecycle_stmt;

DROP PROCEDURE IF EXISTS apply_oa_sign_package_lifecycle_schema_20260716;
DELIMITER $$
CREATE PROCEDURE apply_oa_sign_package_lifecycle_schema_20260716(
    IN p_table_name varchar(64),
    IN p_object_kind varchar(16),
    IN p_object_name varchar(64),
    IN p_alter_clause text,
    IN p_expected_columns varchar(500),
    IN p_expected_non_unique int)
BEGIN
    DECLARE v_count bigint DEFAULT 0;
    DECLARE v_matches bigint DEFAULT 0;
    DECLARE v_message varchar(128);

    IF p_object_kind = 'COLUMN' THEN
        SELECT COUNT(*) INTO v_count
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND BINARY TABLE_NAME = BINARY p_table_name
           AND BINARY COLUMN_NAME = BINARY p_object_name;
    ELSEIF p_object_kind = 'INDEX' THEN
        SELECT COUNT(DISTINCT INDEX_NAME) INTO v_count
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND BINARY TABLE_NAME = BINARY p_table_name
           AND BINARY INDEX_NAME = BINARY p_object_name;

        IF v_count > 0 THEN
            SELECT COUNT(*) INTO v_matches
              FROM (
                    SELECT INDEX_NAME,
                           GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS index_columns,
                           MIN(NON_UNIQUE) AS non_unique
                      FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND BINARY TABLE_NAME = BINARY p_table_name
                       AND BINARY INDEX_NAME = BINARY p_object_name
                     GROUP BY INDEX_NAME
                   ) existing_index
             WHERE BINARY existing_index.index_columns = BINARY p_expected_columns
               AND existing_index.non_unique = p_expected_non_unique;
            IF v_matches <> 1 THEN
                SET v_message = CONCAT('incompatible lifecycle index: ', p_object_name);
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
            END IF;
        END IF;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'unsupported lifecycle schema object kind';
    END IF;

    IF v_count = 0 THEN
        SET @lifecycle_ddl = CONCAT('ALTER TABLE `', p_table_name, '` ', p_alter_clause);
        PREPARE lifecycle_ddl_stmt FROM @lifecycle_ddl;
        EXECUTE lifecycle_ddl_stmt;
        DEALLOCATE PREPARE lifecycle_ddl_stmt;
    END IF;
END$$
DELIMITER ;

CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'deadline_policy_source',
    'ADD COLUMN deadline_policy_source varchar(32) DEFAULT NULL COMMENT ''截止策略来源'' AFTER sign_deadline',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'deadline_days_snapshot',
    'ADD COLUMN deadline_days_snapshot int(11) DEFAULT NULL COMMENT ''截止天数快照'' AFTER deadline_policy_source',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'terminal_time',
    'ADD COLUMN terminal_time datetime DEFAULT NULL COMMENT ''业务终态时间'' AFTER cancelled_time',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'terminal_reason_code',
    'ADD COLUMN terminal_reason_code varchar(64) DEFAULT NULL COMMENT ''终态结构化原因码'' AFTER terminal_time',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'terminal_reason_detail',
    'ADD COLUMN terminal_reason_detail varchar(500) DEFAULT NULL COMMENT ''终态原因说明'' AFTER terminal_reason_code',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'resolution_status',
    'ADD COLUMN resolution_status varchar(20) DEFAULT NULL COMMENT ''异常处置状态 OPEN/CLOSED/REISSUED'' AFTER terminal_reason_detail',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'resolved_by',
    'ADD COLUMN resolved_by bigint(20) DEFAULT NULL COMMENT ''异常处置人用户ID'' AFTER resolution_status',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'resolved_time',
    'ADD COLUMN resolved_time datetime DEFAULT NULL COMMENT ''异常处置时间'' AFTER resolved_by',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'resolution_reason_code',
    'ADD COLUMN resolution_reason_code varchar(64) DEFAULT NULL COMMENT ''异常处置原因码'' AFTER resolved_time',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'resolution_reason_detail',
    'ADD COLUMN resolution_reason_detail varchar(500) DEFAULT NULL COMMENT ''异常处置原因说明'' AFTER resolution_reason_code',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'reissue_of_task_id',
    'ADD COLUMN reissue_of_task_id bigint(20) DEFAULT NULL COMMENT ''替代来源任务ID'' AFTER resolution_reason_detail',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'COLUMN', 'reissued_to_task_id',
    'ADD COLUMN reissued_to_task_id bigint(20) DEFAULT NULL COMMENT ''替代目标任务ID'' AFTER reissue_of_task_id',
    NULL, NULL);

CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'deadline_policy_source',
    'ADD COLUMN deadline_policy_source varchar(32) DEFAULT NULL COMMENT ''截止策略来源'' AFTER sign_deadline',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'deadline_days_snapshot',
    'ADD COLUMN deadline_days_snapshot int(11) DEFAULT NULL COMMENT ''截止天数快照'' AFTER deadline_policy_source',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'terminal_time',
    'ADD COLUMN terminal_time datetime DEFAULT NULL COMMENT ''业务终态时间'' AFTER version',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'terminal_reason_code',
    'ADD COLUMN terminal_reason_code varchar(64) DEFAULT NULL COMMENT ''终态结构化原因码'' AFTER terminal_time',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'terminal_reason_detail',
    'ADD COLUMN terminal_reason_detail varchar(500) DEFAULT NULL COMMENT ''终态原因说明'' AFTER terminal_reason_code',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'resolution_status',
    'ADD COLUMN resolution_status varchar(20) DEFAULT NULL COMMENT ''异常处置状态 OPEN/CLOSED/REISSUED'' AFTER terminal_reason_detail',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'resolved_by',
    'ADD COLUMN resolved_by bigint(20) DEFAULT NULL COMMENT ''异常处置人用户ID'' AFTER resolution_status',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'resolved_time',
    'ADD COLUMN resolved_time datetime DEFAULT NULL COMMENT ''异常处置时间'' AFTER resolved_by',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'resolution_reason_code',
    'ADD COLUMN resolution_reason_code varchar(64) DEFAULT NULL COMMENT ''异常处置原因码'' AFTER resolved_time',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'resolution_reason_detail',
    'ADD COLUMN resolution_reason_detail varchar(500) DEFAULT NULL COMMENT ''异常处置原因说明'' AFTER resolution_reason_code',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'reissue_of_package_id',
    'ADD COLUMN reissue_of_package_id bigint(20) DEFAULT NULL COMMENT ''替代来源签约包ID'' AFTER resolution_reason_detail',
    NULL, NULL);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'COLUMN', 'reissued_to_package_id',
    'ADD COLUMN reissued_to_package_id bigint(20) DEFAULT NULL COMMENT ''替代目标签约包ID'' AFTER reissue_of_package_id',
    NULL, NULL);

CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'INDEX', 'uk_oa_sign_task_reissue_of',
    'ADD UNIQUE INDEX uk_oa_sign_task_reissue_of (reissue_of_task_id)',
    'reissue_of_task_id', 0);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'INDEX', 'uk_oa_sign_task_reissued_to',
    'ADD UNIQUE INDEX uk_oa_sign_task_reissued_to (reissued_to_task_id)',
    'reissued_to_task_id', 0);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'INDEX', 'uk_oa_sign_package_reissue_of',
    'ADD UNIQUE INDEX uk_oa_sign_package_reissue_of (reissue_of_package_id)',
    'reissue_of_package_id', 0);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'INDEX', 'uk_oa_sign_package_reissued_to',
    'ADD UNIQUE INDEX uk_oa_sign_package_reissued_to (reissued_to_package_id)',
    'reissued_to_package_id', 0);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'INDEX', 'idx_oa_sign_package_lifecycle_due',
    'ADD INDEX idx_oa_sign_package_lifecycle_due (status, sign_deadline, package_id)',
    'status,sign_deadline,package_id', 1);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'INDEX', 'idx_oa_sign_task_lifecycle_due',
    'ADD INDEX idx_oa_sign_task_lifecycle_due (status, sign_deadline, task_id)',
    'status,sign_deadline,task_id', 1);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_task', 'INDEX', 'idx_oa_sign_task_exception_resolution',
    'ADD INDEX idx_oa_sign_task_exception_resolution (assigned_hr_user_id, status, resolution_status, created_time, task_id)',
    'assigned_hr_user_id,status,resolution_status,created_time,task_id', 1);
CALL apply_oa_sign_package_lifecycle_schema_20260716(
    'oa_sign_package', 'INDEX', 'idx_oa_sign_package_exception_resolution',
    'ADD INDEX idx_oa_sign_package_exception_resolution (status, resolution_status, package_id)',
    'status,resolution_status,package_id', 1);

DROP PROCEDURE IF EXISTS apply_oa_sign_package_lifecycle_schema_20260716;

-- Historical terminal rows remain legacy-closed (resolution_status IS NULL).
-- They must not be surfaced as actionable OPEN work without durable terminal
-- evidence and a verified task/package pair. New application transitions write
-- terminal_time and OPEN atomically.

DROP PROCEDURE IF EXISTS assert_oa_sign_package_lifecycle_ready_20260716;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_package_lifecycle_ready_20260716()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_package'
       AND COLUMN_NAME = 'status'
       AND DATA_TYPE = 'varchar'
       AND CHARACTER_MAXIMUM_LENGTH >= 32
       AND IS_NULLABLE = 'NO';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_package status must be non-null varchar(32+)';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_task'
       AND (
            (COLUMN_NAME = 'deadline_policy_source' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 32)
         OR (COLUMN_NAME = 'deadline_days_snapshot' AND DATA_TYPE = 'int')
         OR (COLUMN_NAME = 'terminal_time' AND DATA_TYPE = 'datetime')
         OR (COLUMN_NAME = 'terminal_reason_code' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 64)
         OR (COLUMN_NAME = 'terminal_reason_detail' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 500)
         OR (COLUMN_NAME = 'resolution_status' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 20)
         OR (COLUMN_NAME = 'resolved_by' AND DATA_TYPE = 'bigint')
         OR (COLUMN_NAME = 'resolved_time' AND DATA_TYPE = 'datetime')
         OR (COLUMN_NAME = 'resolution_reason_code' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 64)
         OR (COLUMN_NAME = 'resolution_reason_detail' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 500)
         OR (COLUMN_NAME = 'reissue_of_task_id' AND DATA_TYPE = 'bigint')
         OR (COLUMN_NAME = 'reissued_to_task_id' AND DATA_TYPE = 'bigint')
       );
    IF v_count <> 12 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_task lifecycle column definition mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_package'
       AND (
            (COLUMN_NAME = 'deadline_policy_source' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 32)
         OR (COLUMN_NAME = 'deadline_days_snapshot' AND DATA_TYPE = 'int')
         OR (COLUMN_NAME = 'terminal_time' AND DATA_TYPE = 'datetime')
         OR (COLUMN_NAME = 'terminal_reason_code' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 64)
         OR (COLUMN_NAME = 'terminal_reason_detail' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 500)
         OR (COLUMN_NAME = 'resolution_status' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 20)
         OR (COLUMN_NAME = 'resolved_by' AND DATA_TYPE = 'bigint')
         OR (COLUMN_NAME = 'resolved_time' AND DATA_TYPE = 'datetime')
         OR (COLUMN_NAME = 'resolution_reason_code' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 64)
         OR (COLUMN_NAME = 'resolution_reason_detail' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 500)
         OR (COLUMN_NAME = 'reissue_of_package_id' AND DATA_TYPE = 'bigint')
         OR (COLUMN_NAME = 'reissued_to_package_id' AND DATA_TYPE = 'bigint')
       );
    IF v_count <> 12 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_package lifecycle column definition mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package p
     WHERE p.status IN ('pending_sign', 'part_viewed', 'pending_company', 'pending_final_confirm')
       AND (p.sent_time IS NULL
         OR p.sign_deadline IS NULL
         OR p.sign_deadline <= p.sent_time
         OR p.deadline_policy_source IS NULL
         OR TRIM(p.deadline_policy_source) = ''
         OR p.deadline_days_snapshot IS NULL
         OR p.deadline_days_snapshot <= 0);
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active signing package lacks explicit deadline lifecycle policy';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_task t
     WHERE t.status IN ('PENDING_SIGN', 'VIEWED', 'PENDING_COMPANY', 'PENDING_FINAL_CONFIRM')
       AND (t.sent_time IS NULL
         OR t.sign_deadline IS NULL
         OR t.sign_deadline <= t.sent_time
         OR t.deadline_policy_source IS NULL
         OR TRIM(t.deadline_policy_source) = ''
         OR t.deadline_days_snapshot IS NULL
         OR t.deadline_days_snapshot <= 0);
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active signing task lacks explicit deadline lifecycle policy';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package p
     WHERE p.status IN ('pending_sign', 'part_viewed', 'pending_company', 'pending_final_confirm')
       AND p.task_id IS NULL;
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active signing package must bind a managed task before lifecycle rollout';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package p
      LEFT JOIN oa_sign_task t ON t.task_id = p.task_id
     WHERE p.status IN ('pending_sign', 'part_viewed', 'pending_company', 'pending_final_confirm')
       AND p.task_id IS NOT NULL
       AND (t.task_id IS NULL
         OR t.package_id IS NULL
         OR t.package_id <> p.package_id
         OR NOT (p.employee_id <=> t.employee_id)
         OR NOT (p.shop_dept_id <=> t.shop_dept_id)
         OR p.plan_version_id IS NULL
         OR t.plan_version_id IS NULL
         OR p.plan_version_id <> t.plan_version_id
         OR NOT ((p.status = 'pending_sign' AND t.status = 'PENDING_SIGN')
              OR (p.status = 'part_viewed' AND t.status = 'VIEWED')
              OR (p.status = 'pending_company' AND t.status = 'PENDING_COMPANY')
              OR (p.status = 'pending_final_confirm' AND t.status = 'PENDING_FINAL_CONFIRM'))
         OR NOT (p.sent_time <=> t.sent_time)
         OR NOT (p.sign_deadline <=> t.sign_deadline)
         OR NOT (p.deadline_days_snapshot <=> t.deadline_days_snapshot)
         OR COALESCE(BINARY p.deadline_policy_source <> BINARY t.deadline_policy_source, 1));
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'task/package signing deadline lifecycle policy mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_task t
      LEFT JOIN oa_sign_package p ON p.package_id = t.package_id
     WHERE t.status IN ('PENDING_SIGN', 'VIEWED', 'PENDING_COMPANY', 'PENDING_FINAL_CONFIRM')
       AND (p.package_id IS NULL
         OR p.task_id IS NULL
         OR p.task_id <> t.task_id
         OR NOT (p.employee_id <=> t.employee_id)
         OR NOT (p.shop_dept_id <=> t.shop_dept_id)
         OR p.plan_version_id IS NULL
         OR t.plan_version_id IS NULL
         OR p.plan_version_id <> t.plan_version_id
         OR NOT ((t.status = 'PENDING_SIGN' AND p.status = 'pending_sign')
              OR (t.status = 'VIEWED' AND p.status = 'part_viewed')
              OR (t.status = 'PENDING_COMPANY' AND p.status = 'pending_company')
              OR (t.status = 'PENDING_FINAL_CONFIRM' AND p.status = 'pending_final_confirm'))
         OR NOT (p.sent_time <=> t.sent_time)
         OR NOT (p.sign_deadline <=> t.sign_deadline)
         OR NOT (p.deadline_days_snapshot <=> t.deadline_days_snapshot)
         OR COALESCE(BINARY p.deadline_policy_source <> BINARY t.deadline_policy_source, 1));
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active signing task/package binding mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package p
      LEFT JOIN oa_sign_task t ON t.task_id = p.task_id
     WHERE p.status IN ('refused', 'expired')
       AND p.resolution_status = 'OPEN'
       AND (p.terminal_time IS NULL
         OR p.task_id IS NULL
         OR t.task_id IS NULL
         OR t.package_id <> p.package_id
         OR NOT (p.employee_id <=> t.employee_id)
         OR NOT (p.shop_dept_id <=> t.shop_dept_id)
         OR p.plan_version_id IS NULL
         OR t.plan_version_id IS NULL
         OR p.plan_version_id <> t.plan_version_id
         OR NOT (t.status <=> UPPER(p.status))
         OR NOT (t.resolution_status <=> 'OPEN')
         OR t.terminal_time IS NULL
         OR NOT (t.terminal_time <=> p.terminal_time));
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'open terminal package lacks resolvable task evidence';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_task t
      LEFT JOIN oa_sign_package p ON p.package_id = t.package_id
     WHERE t.status IN ('REFUSED', 'EXPIRED')
       AND t.resolution_status = 'OPEN'
       AND (t.terminal_time IS NULL
         OR p.package_id IS NULL
         OR p.task_id <> t.task_id
         OR NOT (p.employee_id <=> t.employee_id)
         OR NOT (p.shop_dept_id <=> t.shop_dept_id)
         OR p.plan_version_id IS NULL
         OR t.plan_version_id IS NULL
         OR p.plan_version_id <> t.plan_version_id
         OR NOT (p.status <=> LOWER(t.status))
         OR NOT (p.resolution_status <=> 'OPEN')
         OR p.terminal_time IS NULL
         OR NOT (p.terminal_time <=> t.terminal_time));
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'open terminal task lacks resolvable package evidence';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_package_lifecycle_ready_20260716();
DROP PROCEDURE IF EXISTS assert_oa_sign_package_lifecycle_ready_20260716;
