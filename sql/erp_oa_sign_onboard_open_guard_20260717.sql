-- One open ONBOARD signing task per employee, independent of source dedupe keys.
-- MySQL 5.7/8.x compatible and safe to run repeatedly.
--
-- Existing duplicates are never guessed away: operators must resolve them from
-- approved business evidence before this fail-closed migration can continue.

SET @erp_db = DATABASE();

DROP PROCEDURE IF EXISTS assert_oa_onboard_open_guard_prerequisites_20260717;
DELIMITER $$
CREATE PROCEDURE assert_oa_onboard_open_guard_prerequisites_20260717()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ONBOARD open-task guard requires a selected database';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_task';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_task is required for ONBOARD open-task guard';
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
            SET MESSAGE_TEXT = 'duplicate open ONBOARD tasks must be resolved before guard installation';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_onboard_open_guard_prerequisites_20260717();
DROP PROCEDURE IF EXISTS assert_oa_onboard_open_guard_prerequisites_20260717;

SET @sql = IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'oa_sign_task'
        AND COLUMN_NAME = 'open_onboard_employee_id') = 0,
    'ALTER TABLE oa_sign_task ADD COLUMN open_onboard_employee_id bigint GENERATED ALWAYS AS (CASE WHEN UPPER(TRIM(scenario)) = ''ONBOARD'' AND status NOT IN (''SIGNED'', ''REFUSED'', ''EXPIRED'', ''CANCELLED'', ''NO_ACTION'') THEN employee_id ELSE NULL END) STORED COMMENT ''非终态ONBOARD员工唯一占位'' AFTER employee_id',
    'DO 0');
PREPARE onboard_guard_stmt FROM @sql;
EXECUTE onboard_guard_stmt;
DEALLOCATE PREPARE onboard_guard_stmt;

DROP PROCEDURE IF EXISTS assert_oa_onboard_open_guard_column_20260717;
DELIMITER $$
CREATE PROCEDURE assert_oa_onboard_open_guard_column_20260717()
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

CALL assert_oa_onboard_open_guard_column_20260717();
DROP PROCEDURE IF EXISTS assert_oa_onboard_open_guard_column_20260717;

SET @sql = IF(
    (SELECT COUNT(DISTINCT INDEX_NAME)
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'oa_sign_task'
        AND INDEX_NAME = 'uk_oa_sign_task_open_onboard_employee') = 0,
    'ALTER TABLE oa_sign_task ADD UNIQUE INDEX uk_oa_sign_task_open_onboard_employee (open_onboard_employee_id)',
    'DO 0');
PREPARE onboard_guard_stmt FROM @sql;
EXECUTE onboard_guard_stmt;
DEALLOCATE PREPARE onboard_guard_stmt;

DROP PROCEDURE IF EXISTS assert_oa_onboard_open_guard_index_20260717;
DELIMITER $$
CREATE PROCEDURE assert_oa_onboard_open_guard_index_20260717()
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

CALL assert_oa_onboard_open_guard_index_20260717();
DROP PROCEDURE IF EXISTS assert_oa_onboard_open_guard_index_20260717;
