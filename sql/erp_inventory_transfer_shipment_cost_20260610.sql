SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inv_transfer_shipment_detail'
    AND column_name = 'cost_price'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE inv_transfer_shipment_detail ADD COLUMN cost_price decimal(16,2) NOT NULL DEFAULT ''0.00'' COMMENT ''shipment cost price'' AFTER received_quantity',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
