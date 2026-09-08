-- Freeze the actual regularization date used to render immutable employee signing documents.

SET @erp_db = DATABASE();
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'actual_regularization_date') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN actual_regularization_date varchar(10) DEFAULT NULL COMMENT ''实际转正日期'' AFTER probation_end_date',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
