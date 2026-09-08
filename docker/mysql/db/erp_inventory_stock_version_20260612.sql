-- Add an optimistic-lock version guard to inventory stock mutations.

SET @schema_name = DATABASE();

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_stock ADD COLUMN version bigint NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER total_cost',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_stock'
      AND COLUMN_NAME = 'version'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
