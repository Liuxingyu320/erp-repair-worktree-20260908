SET @schema_name = DATABASE();

SET @add_purchase_detail_id_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_inbound_record ADD COLUMN purchase_detail_id bigint(20) DEFAULT NULL COMMENT ''采购明细ID'' AFTER purchase_order_id',
        'SELECT ''inv_inbound_record.purchase_detail_id already exists'' AS info'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_inbound_record'
      AND COLUMN_NAME = 'purchase_detail_id'
);

PREPARE add_purchase_detail_id_stmt FROM @add_purchase_detail_id_sql;
EXECUTE add_purchase_detail_id_stmt;
DEALLOCATE PREPARE add_purchase_detail_id_stmt;

SET @add_purchase_detail_id_index_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_inbound_record ADD INDEX idx_inbound_purchase_detail (purchase_detail_id)',
        'SELECT ''idx_inbound_purchase_detail already exists'' AS info'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_inbound_record'
      AND INDEX_NAME = 'idx_inbound_purchase_detail'
);

PREPARE add_purchase_detail_id_index_stmt FROM @add_purchase_detail_id_index_sql;
EXECUTE add_purchase_detail_id_index_stmt;
DEALLOCATE PREPARE add_purchase_detail_id_index_stmt;

UPDATE inv_inbound_record r
JOIN (
    SELECT order_id, product_id, MIN(detail_id) AS detail_id, COUNT(*) AS detail_count
    FROM inv_purchase_detail
    GROUP BY order_id, product_id
    HAVING detail_count = 1
) d ON d.order_id = r.purchase_order_id AND d.product_id = r.product_id
SET r.purchase_detail_id = d.detail_id
WHERE r.purchase_detail_id IS NULL;
