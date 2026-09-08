-- Link sales and purchase orders to customer/supplier master data while preserving name snapshots.
-- Columns remain nullable so historical rows that cannot be matched safely stay readable.

SET @add_sales_customer_id_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_order ADD COLUMN customer_id bigint NULL COMMENT ''客户档案ID'' AFTER order_title',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_order'
      AND COLUMN_NAME = 'customer_id'
);
PREPARE add_sales_customer_id_stmt FROM @add_sales_customer_id_sql;
EXECUTE add_sales_customer_id_stmt;
DEALLOCATE PREPARE add_sales_customer_id_stmt;

SET @add_purchase_supplier_id_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_order ADD COLUMN supplier_id bigint NULL COMMENT ''供应商档案ID'' AFTER order_title',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_purchase_order'
      AND COLUMN_NAME = 'supplier_id'
);
PREPARE add_purchase_supplier_id_stmt FROM @add_purchase_supplier_id_sql;
EXECUTE add_purchase_supplier_id_stmt;
DEALLOCATE PREPARE add_purchase_supplier_id_stmt;

SET @add_sales_customer_idx_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_order ADD INDEX idx_inv_sales_order_customer (customer_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_order'
      AND INDEX_NAME = 'idx_inv_sales_order_customer'
);
PREPARE add_sales_customer_idx_stmt FROM @add_sales_customer_idx_sql;
EXECUTE add_sales_customer_idx_stmt;
DEALLOCATE PREPARE add_sales_customer_idx_stmt;

SET @add_purchase_supplier_idx_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_order ADD INDEX idx_inv_purchase_order_supplier (supplier_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_purchase_order'
      AND INDEX_NAME = 'idx_inv_purchase_order_supplier'
);
PREPARE add_purchase_supplier_idx_stmt FROM @add_purchase_supplier_idx_sql;
EXECUTE add_purchase_supplier_idx_stmt;
DEALLOCATE PREPARE add_purchase_supplier_idx_stmt;

-- Sales customers are store-owned, so only an unambiguous same-organization match is backfilled.
UPDATE inv_sales_order o
JOIN (
    SELECT customer_name, shop_dept_id, MIN(customer_id) AS customer_id
    FROM inv_customer
    WHERE status = '0'
    GROUP BY customer_name, shop_dept_id
    HAVING COUNT(*) = 1
) c ON c.customer_name = o.customer_name AND c.shop_dept_id = o.shop_dept_id
SET o.customer_id = c.customer_id
WHERE o.customer_id IS NULL
  AND NULLIF(TRIM(o.customer_name), '') IS NOT NULL;

-- Suppliers may live on a business ancestor of the warehouse. Backfill only globally unique active names.
UPDATE inv_purchase_order o
JOIN (
    SELECT supplier_name, MIN(supplier_id) AS supplier_id
    FROM inv_supplier
    WHERE status = '0' AND cooperation_status = '0'
    GROUP BY supplier_name
    HAVING COUNT(*) = 1
) s ON s.supplier_name = o.supplier_name
SET o.supplier_id = s.supplier_id
WHERE o.supplier_id IS NULL
  AND NULLIF(TRIM(o.supplier_name), '') IS NOT NULL;
SET @add_sales_customer_fk_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_sales_order ADD CONSTRAINT fk_inv_sales_order_customer FOREIGN KEY (customer_id) REFERENCES inv_customer(customer_id) ON DELETE SET NULL ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_order'
      AND CONSTRAINT_NAME = 'fk_inv_sales_order_customer'
);
PREPARE add_sales_customer_fk_stmt FROM @add_sales_customer_fk_sql;
EXECUTE add_sales_customer_fk_stmt;
DEALLOCATE PREPARE add_sales_customer_fk_stmt;

SET @add_purchase_supplier_fk_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_order ADD CONSTRAINT fk_inv_purchase_order_supplier FOREIGN KEY (supplier_id) REFERENCES inv_supplier(supplier_id) ON DELETE SET NULL ON UPDATE RESTRICT',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_purchase_order'
      AND CONSTRAINT_NAME = 'fk_inv_purchase_order_supplier'
);
PREPARE add_purchase_supplier_fk_stmt FROM @add_purchase_supplier_fk_sql;
EXECUTE add_purchase_supplier_fk_stmt;
DEALLOCATE PREPARE add_purchase_supplier_fk_stmt;

-- Rows returned here need manual master-data cleanup before they can be linked.
SELECT o.order_id, o.order_no, o.customer_name, o.shop_dept_id
FROM inv_sales_order o
WHERE o.customer_id IS NULL
  AND NULLIF(TRIM(o.customer_name), '') IS NOT NULL;

SELECT o.order_id, o.order_no, o.supplier_name, o.shop_dept_id
FROM inv_purchase_order o
WHERE o.supplier_id IS NULL
  AND NULLIF(TRIM(o.supplier_name), '') IS NOT NULL;
