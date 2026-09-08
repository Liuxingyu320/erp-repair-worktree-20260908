-- 扩展库存流水业务类型，兼容 warehouse_replenishment 等稳定业务标识。
-- MySQL 5.7/8.0，可重复执行。

SET @erp_db := DATABASE();

SET @business_type_length := (
    SELECT character_maximum_length
    FROM information_schema.columns
    WHERE table_schema = @erp_db
      AND table_name = 'inv_stock_log'
      AND column_name = 'business_type'
    LIMIT 1
);

SET @sql := IF(
    COALESCE(@business_type_length, 0) < 40,
    'ALTER TABLE inv_stock_log MODIFY COLUMN business_type varchar(40) DEFAULT '''' COMMENT ''业务类型(purchase/sales/outbound/stock/stock_check/purchase_return/sales_return/warehouse_replenishment/store_return/cross_store_transfer/oe_replenishment)''',
    'SELECT ''inv_stock_log.business_type already supports 40 characters'' AS migration_info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT character_maximum_length AS business_type_length
FROM information_schema.columns
WHERE table_schema = @erp_db
  AND table_name = 'inv_stock_log'
  AND column_name = 'business_type';
