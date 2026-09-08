-- Frozen contract-term selection for onboarding labor-contract rendering.
-- MySQL 5.7/8.x compatible and safe to execute repeatedly.
--
-- Existing signed packages remain immutable. Unsigned onboarding packages are backfilled
-- only from their own immutable import-row snapshot when that source fact is available.

SET @erp_db = DATABASE();

DROP PROCEDURE IF EXISTS assert_oa_sign_contract_term_prerequisites_20260721;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_contract_term_prerequisites_20260721()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'contract-term snapshot migration requires a selected database';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('oa_sign_package', 'oa_sign_onboard_import_row');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'contract-term snapshot prerequisite tables are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_package'
       AND COLUMN_NAME IN ('package_id', 'scenario', 'employment_type', 'status');
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_package contract-term prerequisites are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_onboard_import_row'
       AND COLUMN_NAME IN ('package_id', 'snapshot_json');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'onboarding import contract-term prerequisites are missing';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_contract_term_prerequisites_20260721();
DROP PROCEDURE IF EXISTS assert_oa_sign_contract_term_prerequisites_20260721;

DROP PROCEDURE IF EXISTS add_oa_sign_contract_term_column_20260721;
DELIMITER $$
CREATE PROCEDURE add_oa_sign_contract_term_column_20260721()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND BINARY TABLE_NAME = BINARY 'oa_sign_package'
       AND BINARY COLUMN_NAME = BINARY 'contract_term_code_snapshot';

    IF v_count = 0 THEN
        ALTER TABLE oa_sign_package
            ADD COLUMN contract_term_code_snapshot varchar(32) DEFAULT NULL
                COMMENT '合同期限类型快照 FIXED_TERM/OPEN_ENDED'
                AFTER employment_type;
    END IF;
END$$
DELIMITER ;

CALL add_oa_sign_contract_term_column_20260721();
DROP PROCEDURE IF EXISTS add_oa_sign_contract_term_column_20260721;

DROP PROCEDURE IF EXISTS assert_oa_sign_contract_term_column_20260721;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_contract_term_column_20260721()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_package'
       AND COLUMN_NAME = 'contract_term_code_snapshot'
       AND DATA_TYPE = 'varchar'
       AND CHARACTER_MAXIMUM_LENGTH = 32
       AND IS_NULLABLE = 'YES';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_package contract-term snapshot definition mismatch';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_contract_term_column_20260721();
DROP PROCEDURE IF EXISTS assert_oa_sign_contract_term_column_20260721;

UPDATE oa_sign_package package_row
INNER JOIN oa_sign_onboard_import_row import_row
        ON import_row.package_id = package_row.package_id
SET package_row.contract_term_code_snapshot = UPPER(TRIM(
        JSON_UNQUOTE(JSON_EXTRACT(import_row.snapshot_json, '$.contractTermCode'))))
WHERE package_row.contract_term_code_snapshot IS NULL
  AND UPPER(TRIM(package_row.scenario)) = 'ONBOARD'
  AND LOWER(TRIM(package_row.status)) <> 'signed'
  AND JSON_VALID(import_row.snapshot_json)
  AND UPPER(TRIM(JSON_UNQUOTE(
        JSON_EXTRACT(import_row.snapshot_json, '$.contractTermCode'))))
      IN ('FIXED_TERM', 'OPEN_ENDED');

DROP PROCEDURE IF EXISTS assert_oa_sign_contract_term_values_20260721;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_contract_term_values_20260721()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package
     WHERE contract_term_code_snapshot IS NOT NULL
       AND BINARY contract_term_code_snapshot NOT IN (
               BINARY 'FIXED_TERM', BINARY 'OPEN_ENDED');
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_sign_package contains an unsupported contract-term snapshot';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_contract_term_values_20260721();
DROP PROCEDURE IF EXISTS assert_oa_sign_contract_term_values_20260721;
