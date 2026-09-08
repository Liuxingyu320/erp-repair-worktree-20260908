-- Freeze legal-entity and renewal-safety data on every signing package.
-- MySQL 5.7/8.0 compatible and safe to execute repeatedly.

SET @erp_db = DATABASE();

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'legal_entity_id_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_id_snapshot bigint(20) DEFAULT NULL COMMENT ''法律主体ID快照'' AFTER shop_dept_name',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'legal_entity_code_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_code_snapshot varchar(64) DEFAULT NULL COMMENT ''法律主体编码快照'' AFTER legal_entity_id_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'legal_entity_name_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN legal_entity_name_snapshot varchar(160) DEFAULT NULL COMMENT ''法律主体名称快照'' AFTER legal_entity_code_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'previous_contract_end_date') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN previous_contract_end_date varchar(10) DEFAULT NULL COMMENT ''续签前合同结束日期快照'' AFTER contract_end_date',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'previous_employment_type') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN previous_employment_type varchar(32) DEFAULT NULL COMMENT ''续签前合同类型快照'' AFTER previous_contract_end_date',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'previous_legal_entity_id_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN previous_legal_entity_id_snapshot bigint(20) DEFAULT NULL COMMENT ''续签前法律主体ID快照'' AFTER previous_employment_type',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'previous_renewal_count') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN previous_renewal_count int(11) DEFAULT NULL COMMENT ''续签前续签次数快照'' AFTER previous_legal_entity_id_snapshot',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'renewal_count') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN renewal_count int(11) DEFAULT NULL COMMENT ''本次续签次数快照'' AFTER previous_renewal_count',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

