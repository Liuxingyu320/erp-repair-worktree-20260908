-- Immutable signing/seal placement policy snapshots for generated package documents.
-- Apply after erp_oa_sign_package_lifecycle_20260716.sql.
-- MySQL 5.7/8.x compatible and safe to execute repeatedly.
--
-- Existing rows were rendered before package-document policy snapshots existed. They
-- are labelled LEGACY_APPEND_ONLY without inventing placement coordinates or a seal
-- requirement. New rows have no database default for document_policy_mode and must
-- explicitly provide a complete SNAPSHOT_V1 policy.

SET @erp_db = DATABASE();

DROP PROCEDURE IF EXISTS assert_oa_sign_document_policy_prerequisites_20260716;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_document_policy_prerequisites_20260716()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing document policy migration requires a selected database';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('oa_sign_template', 'oa_sign_plan_version_template',
                          'oa_sign_package_document');
    IF v_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing document policy prerequisite tables are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND BINARY TABLE_NAME = BINARY 'oa_sign_package_document'
       AND BINARY COLUMN_NAME IN (BINARY 'document_id', BINARY 'package_id',
                                  BINARY 'employee_sign_required');
    IF v_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing document policy prerequisite columns are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_template'
       AND COLUMN_NAME IN ('template_id', 'company_seal_position_json');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing template seal-policy prerequisite columns are missing';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_plan_version_template'
       AND COLUMN_NAME IN ('id', 'company_seal_position_json');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'plan-version seal-policy prerequisite columns are missing';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_document_policy_prerequisites_20260716();
DROP PROCEDURE IF EXISTS assert_oa_sign_document_policy_prerequisites_20260716;

DROP PROCEDURE IF EXISTS add_oa_sign_document_policy_column_20260716;
DELIMITER $$
CREATE PROCEDURE add_oa_sign_document_policy_column_20260716(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_alter_clause text)
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND BINARY TABLE_NAME = BINARY p_table_name
       AND BINARY COLUMN_NAME = BINARY p_column_name;

    IF v_count = 0 THEN
        SET @document_policy_ddl = CONCAT(
                'ALTER TABLE `', p_table_name, '` ', p_alter_clause);
        PREPARE document_policy_ddl_stmt FROM @document_policy_ddl;
        EXECUTE document_policy_ddl_stmt;
        DEALLOCATE PREPARE document_policy_ddl_stmt;
    END IF;
END$$
DELIMITER ;

CALL add_oa_sign_document_policy_column_20260716(
    'oa_sign_template',
    'company_seal_required',
    'ADD COLUMN company_seal_required char(1) DEFAULT NULL COMMENT ''是否需要公司印章（Y是 N否；NULL未审批）'' AFTER company_seal_position_json');
CALL add_oa_sign_document_policy_column_20260716(
    'oa_sign_plan_version_template',
    'company_seal_required',
    'ADD COLUMN company_seal_required char(1) DEFAULT NULL COMMENT ''是否需要公司印章快照（Y是 N否；历史版本为NULL）'' AFTER company_seal_position_json');
CALL add_oa_sign_document_policy_column_20260716(
    'oa_sign_package_document',
    'signature_position_json',
    'ADD COLUMN signature_position_json longtext DEFAULT NULL COMMENT ''员工签名定位策略JSON快照'' AFTER employee_sign_required');
CALL add_oa_sign_document_policy_column_20260716(
    'oa_sign_package_document',
    'company_seal_position_json',
    'ADD COLUMN company_seal_position_json longtext DEFAULT NULL COMMENT ''公司印章定位策略JSON快照'' AFTER signature_position_json');
CALL add_oa_sign_document_policy_column_20260716(
    'oa_sign_package_document',
    'company_seal_required',
    'ADD COLUMN company_seal_required char(1) DEFAULT NULL COMMENT ''是否需要公司印章快照（Y是 N否；历史旧文件为NULL）'' AFTER company_seal_position_json');
CALL add_oa_sign_document_policy_column_20260716(
    'oa_sign_package_document',
    'document_policy_mode',
    'ADD COLUMN document_policy_mode varchar(32) DEFAULT NULL COMMENT ''文件策略模式 LEGACY_APPEND_ONLY/SNAPSHOT_V1'' AFTER company_seal_required');

DROP PROCEDURE IF EXISTS add_oa_sign_document_policy_column_20260716;

DROP PROCEDURE IF EXISTS assert_oa_sign_document_policy_columns_20260716;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_document_policy_columns_20260716()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('oa_sign_template', 'oa_sign_plan_version_template')
       AND COLUMN_NAME = 'company_seal_required'
       AND DATA_TYPE = 'char'
       AND CHARACTER_MAXIMUM_LENGTH = 1
       AND IS_NULLABLE = 'YES';
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing source seal-requirement column definition mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_package_document'
       AND (
            (COLUMN_NAME = 'signature_position_json' AND DATA_TYPE = 'longtext')
         OR (COLUMN_NAME = 'company_seal_position_json' AND DATA_TYPE = 'longtext')
         OR (COLUMN_NAME = 'company_seal_required' AND DATA_TYPE = 'char'
             AND CHARACTER_MAXIMUM_LENGTH = 1 AND IS_NULLABLE = 'YES')
         OR (COLUMN_NAME = 'document_policy_mode' AND DATA_TYPE = 'varchar'
             AND CHARACTER_MAXIMUM_LENGTH >= 32)
       );
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing document policy column definition mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package_document
     WHERE document_policy_mode IS NULL
       AND (signature_position_json IS NOT NULL
         OR company_seal_position_json IS NOT NULL
         OR company_seal_required IS NOT NULL);
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'historical signing document policy contains fabricated snapshot fields';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package_document
     WHERE document_policy_mode IS NOT NULL
       AND BINARY document_policy_mode NOT IN (
               BINARY 'LEGACY_APPEND_ONLY', BINARY 'SNAPSHOT_V1');
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'unsupported signing document policy mode';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package_document
     WHERE BINARY document_policy_mode = BINARY 'LEGACY_APPEND_ONLY'
       AND (signature_position_json IS NOT NULL
         OR company_seal_position_json IS NOT NULL
         OR company_seal_required IS NOT NULL);
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'historical signing document policy contains fabricated snapshot fields';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_template
     WHERE company_seal_required IS NOT NULL
       AND BINARY company_seal_required NOT IN (BINARY 'Y', BINARY 'N');
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing template has invalid explicit seal requirement';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version_template
     WHERE company_seal_required IS NOT NULL
       AND BINARY company_seal_required NOT IN (BINARY 'Y', BINARY 'N');
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'plan-version template has invalid explicit seal requirement';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_document_policy_columns_20260716();
DROP PROCEDURE IF EXISTS assert_oa_sign_document_policy_columns_20260716;

-- The pre-migration renderer used appended evidence pages and never consumed a
-- persisted per-document placement. Preserve that fact only; do not copy current
-- template/plan coordinates into old documents and do not infer a seal requirement.
UPDATE oa_sign_package_document
   SET document_policy_mode = 'LEGACY_APPEND_ONLY'
 WHERE document_policy_mode IS NULL
   AND signature_position_json IS NULL
   AND company_seal_position_json IS NULL
   AND company_seal_required IS NULL;

SET @sql = IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'oa_sign_package_document'
        AND COLUMN_NAME = 'document_policy_mode'
        AND DATA_TYPE = 'varchar'
        AND CHARACTER_MAXIMUM_LENGTH = 32
        AND IS_NULLABLE = 'NO'
        AND COLUMN_DEFAULT IS NULL) = 1,
    'DO 0',
    'ALTER TABLE oa_sign_package_document MODIFY COLUMN document_policy_mode varchar(32) NOT NULL COMMENT ''文件策略模式 LEGACY_APPEND_ONLY/SNAPSHOT_V1''');
PREPARE document_policy_mode_stmt FROM @sql;
EXECUTE document_policy_mode_stmt;
DEALLOCATE PREPARE document_policy_mode_stmt;

-- MySQL 5.7 parses CHECK clauses without enforcing them and lacks the 8.x
-- check-constraint metadata table. BEFORE triggers provide the same
-- fail-closed invariant on both MySQL 5.7 and 8.x.
DROP TRIGGER IF EXISTS trg_oa_sign_template_policy_bi_20260716;
DROP TRIGGER IF EXISTS trg_oa_sign_template_policy_bu_20260716;
DELIMITER $$
CREATE TRIGGER trg_oa_sign_template_policy_bi_20260716
BEFORE INSERT ON oa_sign_template
FOR EACH ROW
BEGIN
    IF NEW.company_seal_required IS NOT NULL
       AND BINARY NEW.company_seal_required NOT IN (BINARY 'Y', BINARY 'N') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid signing template seal requirement';
    END IF;
END$$
CREATE TRIGGER trg_oa_sign_template_policy_bu_20260716
BEFORE UPDATE ON oa_sign_template
FOR EACH ROW
BEGIN
    IF NEW.company_seal_required IS NOT NULL
       AND BINARY NEW.company_seal_required NOT IN (BINARY 'Y', BINARY 'N') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid signing template seal requirement';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_oa_sign_plan_version_template_policy_bi_20260716;
DROP TRIGGER IF EXISTS trg_oa_sign_plan_version_template_policy_bu_20260716;
DELIMITER $$
CREATE TRIGGER trg_oa_sign_plan_version_template_policy_bi_20260716
BEFORE INSERT ON oa_sign_plan_version_template
FOR EACH ROW
BEGIN
    IF NEW.company_seal_required IS NOT NULL
       AND BINARY NEW.company_seal_required NOT IN (BINARY 'Y', BINARY 'N') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid plan-version seal requirement';
    END IF;
END$$
CREATE TRIGGER trg_oa_sign_plan_version_template_policy_bu_20260716
BEFORE UPDATE ON oa_sign_plan_version_template
FOR EACH ROW
BEGIN
    IF NEW.company_seal_required IS NOT NULL
       AND BINARY NEW.company_seal_required NOT IN (BINARY 'Y', BINARY 'N') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid plan-version seal requirement';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_oa_sign_package_document_policy_bi_20260716;
DROP TRIGGER IF EXISTS trg_oa_sign_package_document_policy_bu_20260716;
DELIMITER $$
CREATE TRIGGER trg_oa_sign_package_document_policy_bi_20260716
BEFORE INSERT ON oa_sign_package_document
FOR EACH ROW
BEGIN
    IF NEW.document_policy_mode IS NULL
       OR BINARY NEW.document_policy_mode NOT IN (
              BINARY 'LEGACY_APPEND_ONLY', BINARY 'SNAPSHOT_V1')
       OR (BINARY NEW.document_policy_mode = BINARY 'LEGACY_APPEND_ONLY'
           AND (NEW.signature_position_json IS NOT NULL
             OR NEW.company_seal_position_json IS NOT NULL
             OR NEW.company_seal_required IS NOT NULL))
       OR (BINARY NEW.document_policy_mode = BINARY 'SNAPSHOT_V1'
           AND (NEW.employee_sign_required IS NULL
             OR BINARY NEW.employee_sign_required NOT IN (BINARY 'Y', BINARY 'N')
             OR NEW.company_seal_required IS NULL
             OR BINARY NEW.company_seal_required NOT IN (BINARY 'Y', BINARY 'N')
             OR (BINARY NEW.employee_sign_required = BINARY 'Y'
                 AND (NEW.signature_position_json IS NULL
                   OR TRIM(NEW.signature_position_json) = ''
                   OR COALESCE(JSON_VALID(NEW.signature_position_json), 0) <> 1))
             OR (BINARY NEW.employee_sign_required = BINARY 'N'
                 AND NEW.signature_position_json IS NOT NULL)
             OR (BINARY NEW.company_seal_required = BINARY 'Y'
                 AND (NEW.company_seal_position_json IS NULL
                   OR TRIM(NEW.company_seal_position_json) = ''
                   OR COALESCE(JSON_VALID(NEW.company_seal_position_json), 0) <> 1))
             OR (BINARY NEW.company_seal_required = BINARY 'N'
                 AND NEW.company_seal_position_json IS NOT NULL))) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid signing document policy snapshot';
    END IF;
END$$
CREATE TRIGGER trg_oa_sign_package_document_policy_bu_20260716
BEFORE UPDATE ON oa_sign_package_document
FOR EACH ROW
BEGIN
    IF NOT (BINARY NEW.employee_visible <=> BINARY OLD.employee_visible)
       OR NOT (BINARY NEW.read_confirmation_required <=> BINARY OLD.read_confirmation_required)
       OR NOT (BINARY NEW.employee_sign_required <=> BINARY OLD.employee_sign_required)
       OR NOT (BINARY NEW.signature_position_json <=> BINARY OLD.signature_position_json)
       OR NOT (BINARY NEW.company_seal_position_json <=> BINARY OLD.company_seal_position_json)
       OR NOT (BINARY NEW.company_seal_required <=> BINARY OLD.company_seal_required)
       OR NOT (BINARY NEW.document_policy_mode <=> BINARY OLD.document_policy_mode) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'immutable signing document policy snapshot';
    END IF;
    IF NEW.document_policy_mode IS NULL
       OR BINARY NEW.document_policy_mode NOT IN (
              BINARY 'LEGACY_APPEND_ONLY', BINARY 'SNAPSHOT_V1')
       OR (BINARY NEW.document_policy_mode = BINARY 'LEGACY_APPEND_ONLY'
           AND (NEW.signature_position_json IS NOT NULL
             OR NEW.company_seal_position_json IS NOT NULL
             OR NEW.company_seal_required IS NOT NULL))
       OR (BINARY NEW.document_policy_mode = BINARY 'SNAPSHOT_V1'
           AND (NEW.employee_sign_required IS NULL
             OR BINARY NEW.employee_sign_required NOT IN (BINARY 'Y', BINARY 'N')
             OR NEW.company_seal_required IS NULL
             OR BINARY NEW.company_seal_required NOT IN (BINARY 'Y', BINARY 'N')
             OR (BINARY NEW.employee_sign_required = BINARY 'Y'
                 AND (NEW.signature_position_json IS NULL
                   OR TRIM(NEW.signature_position_json) = ''
                   OR COALESCE(JSON_VALID(NEW.signature_position_json), 0) <> 1))
             OR (BINARY NEW.employee_sign_required = BINARY 'N'
                 AND NEW.signature_position_json IS NOT NULL)
             OR (BINARY NEW.company_seal_required = BINARY 'Y'
                 AND (NEW.company_seal_position_json IS NULL
                   OR TRIM(NEW.company_seal_position_json) = ''
                   OR COALESCE(JSON_VALID(NEW.company_seal_position_json), 0) <> 1))
             OR (BINARY NEW.company_seal_required = BINARY 'N'
                 AND NEW.company_seal_position_json IS NOT NULL))) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid signing document policy snapshot';
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS assert_oa_sign_document_policy_ready_20260716;
DELIMITER $$
CREATE PROCEDURE assert_oa_sign_document_policy_ready_20260716()
BEGIN
    DECLARE v_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('oa_sign_template', 'oa_sign_plan_version_template')
       AND COLUMN_NAME = 'company_seal_required'
       AND DATA_TYPE = 'char'
       AND CHARACTER_MAXIMUM_LENGTH = 1
       AND IS_NULLABLE = 'YES'
       AND COLUMN_DEFAULT IS NULL;
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing source seal-requirement final definition mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_package_document'
       AND (
            (COLUMN_NAME = 'signature_position_json' AND DATA_TYPE = 'longtext'
             AND IS_NULLABLE = 'YES')
         OR (COLUMN_NAME = 'company_seal_position_json' AND DATA_TYPE = 'longtext'
             AND IS_NULLABLE = 'YES')
         OR (COLUMN_NAME = 'company_seal_required' AND DATA_TYPE = 'char'
             AND CHARACTER_MAXIMUM_LENGTH = 1 AND IS_NULLABLE = 'YES')
         OR (COLUMN_NAME = 'document_policy_mode' AND DATA_TYPE = 'varchar'
             AND CHARACTER_MAXIMUM_LENGTH = 32 AND IS_NULLABLE = 'NO'
             AND COLUMN_DEFAULT IS NULL)
       );
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing document policy final column definition mismatch';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_template
     WHERE company_seal_required IS NOT NULL
       AND BINARY company_seal_required NOT IN (BINARY 'Y', BINARY 'N');
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing template has invalid explicit seal requirement';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version_template
     WHERE company_seal_required IS NOT NULL
       AND BINARY company_seal_required NOT IN (BINARY 'Y', BINARY 'N');
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'plan-version template has invalid explicit seal requirement';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package_document
     WHERE BINARY document_policy_mode NOT IN (
               BINARY 'LEGACY_APPEND_ONLY', BINARY 'SNAPSHOT_V1');
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'unsupported signing document policy mode';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package_document
     WHERE BINARY document_policy_mode = BINARY 'LEGACY_APPEND_ONLY'
       AND (signature_position_json IS NOT NULL
         OR company_seal_position_json IS NOT NULL
         OR company_seal_required IS NOT NULL);
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'historical signing document policy contains fabricated snapshot fields';
    END IF;

    SELECT COUNT(*) INTO v_count
     FROM oa_sign_package_document
     WHERE BINARY document_policy_mode = BINARY 'SNAPSHOT_V1'
       AND (
            BINARY employee_sign_required NOT IN (BINARY 'Y', BINARY 'N')
         OR BINARY company_seal_required NOT IN (BINARY 'Y', BINARY 'N')
         OR (BINARY employee_sign_required = BINARY 'Y'
             AND (signature_position_json IS NULL
                  OR TRIM(signature_position_json) = ''
                  OR JSON_VALID(signature_position_json) = 0))
         OR (BINARY employee_sign_required = BINARY 'N' AND signature_position_json IS NOT NULL)
         OR (BINARY company_seal_required = BINARY 'Y'
             AND (company_seal_position_json IS NULL
                  OR TRIM(company_seal_position_json) = ''
                  OR JSON_VALID(company_seal_position_json) = 0))
         OR (BINARY company_seal_required = BINARY 'N'
             AND company_seal_position_json IS NOT NULL)
       );
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing document snapshot policy is incomplete';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM information_schema.TRIGGERS
     WHERE TRIGGER_SCHEMA = DATABASE()
       AND ACTION_TIMING = 'BEFORE'
       AND (
            (TRIGGER_NAME IN ('trg_oa_sign_template_policy_bi_20260716',
                              'trg_oa_sign_template_policy_bu_20260716')
             AND EVENT_OBJECT_TABLE = 'oa_sign_template'
             AND ACTION_STATEMENT LIKE '%company_seal_required%')
         OR (TRIGGER_NAME IN ('trg_oa_sign_plan_version_template_policy_bi_20260716',
                              'trg_oa_sign_plan_version_template_policy_bu_20260716')
             AND EVENT_OBJECT_TABLE = 'oa_sign_plan_version_template'
             AND ACTION_STATEMENT LIKE '%company_seal_required%')
         OR (TRIGGER_NAME IN ('trg_oa_sign_package_document_policy_bi_20260716',
                              'trg_oa_sign_package_document_policy_bu_20260716')
             AND EVENT_OBJECT_TABLE = 'oa_sign_package_document'
             AND ACTION_STATEMENT LIKE '%LEGACY_APPEND_ONLY%'
             AND ACTION_STATEMENT LIKE '%SNAPSHOT_V1%'
             AND ACTION_STATEMENT LIKE '%JSON_VALID%')
       );
    IF v_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'signing document policy enforcement triggers are missing';
    END IF;
END$$
DELIMITER ;

CALL assert_oa_sign_document_policy_ready_20260716();
DROP PROCEDURE IF EXISTS assert_oa_sign_document_policy_ready_20260716;

-- Rollback guidance: retain these evidence-policy columns. Application rollback may
-- read LEGACY_APPEND_ONLY rows, but dropping the columns would destroy snapshot facts.
