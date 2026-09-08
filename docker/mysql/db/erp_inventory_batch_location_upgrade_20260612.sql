-- Add additive stock trace/location visibility fields.

SET @schema_name = DATABASE();

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD COLUMN batch_no varchar(64) DEFAULT NULL COMMENT ''批次号'' AFTER warehouse_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND COLUMN_NAME = 'batch_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD COLUMN expiry_date date DEFAULT NULL COMMENT ''效期'' AFTER batch_no',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND COLUMN_NAME = 'expiry_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD COLUMN serial_no varchar(64) DEFAULT NULL COMMENT ''序列号'' AFTER expiry_date',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND COLUMN_NAME = 'serial_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD COLUMN location_code varchar(64) DEFAULT NULL COMMENT ''库位编码'' AFTER serial_no',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND COLUMN_NAME = 'location_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD COLUMN location_name varchar(128) DEFAULT NULL COMMENT ''库位名称'' AFTER location_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND COLUMN_NAME = 'location_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD COLUMN batch_no varchar(64) DEFAULT NULL COMMENT ''批次号'' AFTER warehouse_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND COLUMN_NAME = 'batch_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD COLUMN expiry_date date DEFAULT NULL COMMENT ''效期'' AFTER batch_no',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND COLUMN_NAME = 'expiry_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD COLUMN serial_no varchar(64) DEFAULT NULL COMMENT ''序列号'' AFTER expiry_date',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND COLUMN_NAME = 'serial_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD COLUMN location_code varchar(64) DEFAULT NULL COMMENT ''库位编码'' AFTER serial_no',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND COLUMN_NAME = 'location_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD COLUMN location_name varchar(128) DEFAULT NULL COMMENT ''库位名称'' AFTER location_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND COLUMN_NAME = 'location_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD INDEX idx_inv_stock_batch_no (batch_no)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND INDEX_NAME = 'idx_inv_stock_batch_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD INDEX idx_inv_stock_serial_no (serial_no)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND INDEX_NAME = 'idx_inv_stock_serial_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD INDEX idx_inv_stock_location_code (location_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND INDEX_NAME = 'idx_inv_stock_location_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD INDEX idx_inv_stock_log_batch_no (batch_no)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND INDEX_NAME = 'idx_inv_stock_log_batch_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD INDEX idx_inv_stock_log_serial_no (serial_no)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND INDEX_NAME = 'idx_inv_stock_log_serial_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock_log ADD INDEX idx_inv_stock_log_location_code (location_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock_log'
      AND INDEX_NAME = 'idx_inv_stock_log_location_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
