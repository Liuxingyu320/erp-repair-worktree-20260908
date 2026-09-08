-- Link return detail rows to their source purchase/sales order detail rows.
-- Safe backfill only updates products that appear once in the original order.

SET @add_purchase_return_detail_source_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return_detail ADD COLUMN purchase_detail_id bigint NULL COMMENT ''原采购明细ID'' AFTER return_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_purchase_return_detail'
      AND COLUMN_NAME = 'purchase_detail_id'
);
PREPARE add_purchase_return_detail_source_stmt FROM @add_purchase_return_detail_source_sql;
EXECUTE add_purchase_return_detail_source_stmt;
DEALLOCATE PREPARE add_purchase_return_detail_source_stmt;

SET @add_purchase_return_detail_source_idx_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return_detail ADD INDEX idx_purchase_return_detail_source (purchase_detail_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_purchase_return_detail'
      AND INDEX_NAME = 'idx_purchase_return_detail_source'
);
PREPARE add_purchase_return_detail_source_idx_stmt FROM @add_purchase_return_detail_source_idx_sql;
EXECUTE add_purchase_return_detail_source_idx_stmt;
DEALLOCATE PREPARE add_purchase_return_detail_source_idx_stmt;

SET @add_sales_return_detail_source_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_return_detail ADD COLUMN sales_detail_id bigint NULL COMMENT ''原销售明细ID'' AFTER return_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail'
      AND COLUMN_NAME = 'sales_detail_id'
);
PREPARE add_sales_return_detail_source_stmt FROM @add_sales_return_detail_source_sql;
EXECUTE add_sales_return_detail_source_stmt;
DEALLOCATE PREPARE add_sales_return_detail_source_stmt;

SET @add_sales_return_detail_source_idx_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_return_detail ADD INDEX idx_sales_return_detail_source (sales_detail_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail'
      AND INDEX_NAME = 'idx_sales_return_detail_source'
);
PREPARE add_sales_return_detail_source_idx_stmt FROM @add_sales_return_detail_source_idx_sql;
EXECUTE add_sales_return_detail_source_idx_stmt;
DEALLOCATE PREPARE add_sales_return_detail_source_idx_stmt;

UPDATE inv_purchase_return_detail d
JOIN inv_purchase_return r ON r.return_id = d.return_id
JOIN (
    SELECT order_id, product_id, MIN(detail_id) AS detail_id, COUNT(*) AS detail_count
    FROM inv_purchase_detail
    GROUP BY order_id, product_id
    HAVING detail_count = 1
) pd ON pd.order_id = r.purchase_order_id AND pd.product_id = d.product_id
SET d.purchase_detail_id = pd.detail_id
WHERE d.purchase_detail_id IS NULL;

UPDATE inv_sales_return_detail d
JOIN inv_sales_return r ON r.return_id = d.return_id
JOIN (
    SELECT order_id, product_id, MIN(detail_id) AS detail_id, COUNT(*) AS detail_count
    FROM inv_sales_detail
    GROUP BY order_id, product_id
    HAVING detail_count = 1
) sd ON sd.order_id = r.sales_order_id AND sd.product_id = d.product_id
SET d.sales_detail_id = sd.detail_id
WHERE d.sales_detail_id IS NULL;

-- Manual remediation report: these rows are ambiguous because the source order has
-- the same product on multiple detail lines.
SELECT r.return_no, r.purchase_order_no, d.detail_id AS return_detail_id, d.product_id, COUNT(pd.detail_id) AS source_detail_count
FROM inv_purchase_return_detail d
JOIN inv_purchase_return r ON r.return_id = d.return_id
JOIN inv_purchase_detail pd ON pd.order_id = r.purchase_order_id AND pd.product_id = d.product_id
WHERE d.purchase_detail_id IS NULL
GROUP BY r.return_no, r.purchase_order_no, d.detail_id, d.product_id
HAVING source_detail_count > 1;

SELECT r.return_no, r.sales_order_no, d.detail_id AS return_detail_id, d.product_id, COUNT(sd.detail_id) AS source_detail_count
FROM inv_sales_return_detail d
JOIN inv_sales_return r ON r.return_id = d.return_id
JOIN inv_sales_detail sd ON sd.order_id = r.sales_order_id AND sd.product_id = d.product_id
WHERE d.sales_detail_id IS NULL
GROUP BY r.return_no, r.sales_order_no, d.detail_id, d.product_id
HAVING source_detail_count > 1;
