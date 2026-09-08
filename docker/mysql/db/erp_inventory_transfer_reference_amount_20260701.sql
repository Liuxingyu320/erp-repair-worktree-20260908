-- Persist transfer request reference costs and totals for stable mobile and desktop detail display.

SET @schema_name = DATABASE();

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_order ADD COLUMN total_amount decimal(16,2) NOT NULL DEFAULT ''0.00'' COMMENT ''参考总价'' AFTER total_quantity',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_transfer_order'
      AND COLUMN_NAME = 'total_amount'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_detail ADD COLUMN cost_price decimal(16,2) NOT NULL DEFAULT ''0.00'' COMMENT ''参考成本价'' AFTER received_quantity',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_transfer_detail'
      AND COLUMN_NAME = 'cost_price'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_transfer_detail ADD COLUMN amount decimal(16,2) NOT NULL DEFAULT ''0.00'' COMMENT ''参考金额小计'' AFTER cost_price',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_transfer_detail'
      AND COLUMN_NAME = 'amount'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE inv_transfer_detail d
JOIN inv_transfer_order o ON o.transfer_id = d.transfer_id
LEFT JOIN inv_stock s
       ON s.product_id = d.product_id
      AND s.shop_dept_id = COALESCE(NULLIF(o.from_warehouse_id, 0), o.from_dept_id)
      AND s.warehouse_id = COALESCE(NULLIF(o.from_warehouse_id, 0), o.from_dept_id)
SET d.cost_price = COALESCE(s.cost_price, d.cost_price, 0),
    d.amount = ROUND(COALESCE(d.quantity, 0) * COALESCE(s.cost_price, d.cost_price, 0), 2)
WHERE COALESCE(d.cost_price, 0) = 0
  AND COALESCE(d.amount, 0) = 0;

UPDATE inv_transfer_order o
JOIN (
    SELECT transfer_id,
           SUM(COALESCE(quantity, 0)) AS total_quantity,
           SUM(COALESCE(amount, 0)) AS total_amount
    FROM inv_transfer_detail
    GROUP BY transfer_id
) d ON d.transfer_id = o.transfer_id
SET o.total_quantity = d.total_quantity,
    o.total_amount = d.total_amount;
