-- P0-B: freeze sales-delivery warehouse/cost snapshots and deduplicate
-- generated cross-store transfers. MySQL 5.7/8.0, repeat-safe.

SET @p0b_schema := DATABASE();

-- The cross-store history preflight below reads target_dept_id. Some existing
-- installations predate that sales-order snapshot column, so create it before
-- any static SQL references it. Current same-store rows intentionally remain
-- NULL; the application treats NULL as no cross-store destination.
SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_sales_order ADD COLUMN target_dept_id bigint DEFAULT NULL COMMENT ''跨店销售目标门店'' AFTER shop_dept_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_sales_order'
      AND column_name = 'target_dept_id'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

-- Add nullable columns first. Historical rows are validated and backfilled
-- before warehouse_id is tightened to NOT NULL.
SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_delivery_notice_detail ADD COLUMN warehouse_id bigint DEFAULT NULL COMMENT ''通知创建时冻结的发货仓库'' AFTER sales_detail_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_delivery_notice_detail'
      AND column_name = 'warehouse_id'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_delivery_notice_detail ADD COLUMN delivered_cost_amount decimal(20,2) DEFAULT NULL COMMENT ''累计实际出库成本额'' AFTER delivered_qty',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_delivery_notice_detail'
      AND column_name = 'delivered_cost_amount'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_outbound_record ADD COLUMN notice_id bigint DEFAULT NULL COMMENT ''发货通知ID''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_outbound_record'
      AND column_name = 'notice_id'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_outbound_record ADD COLUMN notice_detail_id bigint DEFAULT NULL COMMENT ''发货通知明细ID''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_outbound_record'
      AND column_name = 'notice_detail_id'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_outbound_record ADD COLUMN cost_price decimal(16,2) DEFAULT NULL COMMENT ''出库时冻结成本单价''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_outbound_record'
      AND column_name = 'cost_price'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_outbound_record ADD COLUMN cost_amount decimal(20,2) DEFAULT NULL COMMENT ''本次出库成本额''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_outbound_record'
      AND column_name = 'cost_amount'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

-- Warehouse backfill is deterministic because a notice detail points to one
-- sales detail. Rows without a trustworthy warehouse remain NULL and block.
UPDATE inv_delivery_notice_detail notice_detail
JOIN inv_sales_detail sales_detail
  ON sales_detail.detail_id = notice_detail.sales_detail_id
SET notice_detail.warehouse_id = sales_detail.warehouse_id
WHERE (notice_detail.warehouse_id IS NULL
       OR notice_detail.warehouse_id = 0)
  AND sales_detail.warehouse_id IS NOT NULL
  AND sales_detail.warehouse_id <> 0;

-- Zero is valid only for a row that has never shipped. Shipped historical
-- rows deliberately retain NULL until operations provide a trustworthy cost.
UPDATE inv_delivery_notice_detail
SET delivered_cost_amount = 0.00
WHERE delivered_cost_amount IS NULL
  AND COALESCE(delivered_qty, 0) = 0;

-- Legacy sales_delivery rows used a sales id in purchase_id. Clear only rows
-- whose source id proves that misuse; preserve uncertain historical links.
UPDATE inv_transfer_order
SET purchase_id = NULL
WHERE source_business_type = 'sales_delivery'
  AND source_business_id IS NOT NULL
  AND purchase_id = source_business_id;

-- Visible release preflight lists.
SELECT notice_detail.detail_id, notice_detail.notice_id,
       notice_detail.sales_detail_id, notice_detail.delivered_qty
FROM inv_delivery_notice_detail notice_detail
WHERE notice_detail.warehouse_id IS NULL
   OR notice_detail.warehouse_id = 0
ORDER BY notice_detail.detail_id
LIMIT 500;

SELECT notice_detail.detail_id, notice_detail.notice_id,
       notice_detail.warehouse_id, notice_detail.delivered_qty
FROM inv_delivery_notice_detail notice_detail
JOIN inv_delivery_notice notice
  ON notice.notice_id = notice_detail.notice_id
JOIN inv_sales_order sales_order
  ON sales_order.order_id = notice.sales_order_id
WHERE notice.status = 'delivering'
  AND sales_order.target_dept_id IS NOT NULL
  AND sales_order.target_dept_id <> notice.shop_dept_id
  AND COALESCE(notice_detail.delivered_qty, 0) > 0
  AND notice_detail.delivered_cost_amount IS NULL
ORDER BY notice_detail.detail_id
LIMIT 500;

SELECT source_business_id, from_warehouse_id, COUNT(*) AS duplicate_count
FROM inv_transfer_order
WHERE source_business_type = 'sales_delivery_notice'
GROUP BY source_business_id, from_warehouse_id
HAVING COUNT(*) > 1
ORDER BY source_business_id, from_warehouse_id
LIMIT 500;

DROP PROCEDURE IF EXISTS sp_p0b_assert_sales_delivery_history;
DELIMITER $$
CREATE PROCEDURE sp_p0b_assert_sales_delivery_history()
BEGIN
    DECLARE blocked_rows bigint DEFAULT 0;

    SELECT COUNT(*) INTO blocked_rows
    FROM inv_delivery_notice_detail
    WHERE warehouse_id IS NULL OR warehouse_id = 0;
    IF blocked_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'P0-B blocked: unresolved delivery notice warehouse';
    END IF;

    SELECT COUNT(*) INTO blocked_rows
    FROM inv_delivery_notice_detail notice_detail
    JOIN inv_delivery_notice notice
      ON notice.notice_id = notice_detail.notice_id
    JOIN inv_sales_order sales_order
      ON sales_order.order_id = notice.sales_order_id
    WHERE notice.status = 'delivering'
      AND sales_order.target_dept_id IS NOT NULL
      AND sales_order.target_dept_id <> notice.shop_dept_id
      AND COALESCE(notice_detail.delivered_qty, 0) > 0
      AND notice_detail.delivered_cost_amount IS NULL;
    IF blocked_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'P0-B blocked: shipped delivery notice cost is unknown';
    END IF;

    SELECT COUNT(*) INTO blocked_rows
    FROM inv_transfer_order
    WHERE source_business_type = 'sales_delivery_notice'
      AND (source_business_id IS NULL OR source_business_id <= 0
           OR from_warehouse_id IS NULL OR from_warehouse_id <= 0);
    IF blocked_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'P0-B blocked: invalid sales delivery notice source';
    END IF;

    SELECT COUNT(*) INTO blocked_rows
    FROM (
        SELECT source_business_id, from_warehouse_id
        FROM inv_transfer_order
        WHERE source_business_type = 'sales_delivery_notice'
        GROUP BY source_business_id, from_warehouse_id
        HAVING COUNT(*) > 1
    ) duplicate_sources;
    IF blocked_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'P0-B blocked: duplicate sales delivery notice transfer';
    END IF;
END$$
DELIMITER ;

CALL sp_p0b_assert_sales_delivery_history();
DROP PROCEDURE sp_p0b_assert_sales_delivery_history;

SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 1 AND MAX(is_nullable) = 'YES',
        'ALTER TABLE inv_delivery_notice_detail MODIFY COLUMN warehouse_id bigint NOT NULL COMMENT ''通知创建时冻结的发货仓库''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_delivery_notice_detail'
      AND column_name = 'warehouse_id'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

-- Only sales_delivery_notice rows produce a non-NULL generated value. MySQL
-- unique indexes permit multiple NULLs, so other business sources are untouched.
SET @p0b_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_transfer_order ADD COLUMN p0b_sales_delivery_notice_source_id bigint GENERATED ALWAYS AS (CASE WHEN source_business_type = ''sales_delivery_notice'' THEN source_business_id ELSE NULL END) STORED',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @p0b_schema
      AND table_name = 'inv_transfer_order'
      AND column_name = 'p0b_sales_delivery_notice_source_id'
);
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := IF(NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @p0b_schema
          AND table_name = 'inv_transfer_order'
          AND index_name = 'idx_inv_transfer_sales_notice_source'),
    'ALTER TABLE inv_transfer_order ADD KEY idx_inv_transfer_sales_notice_source (source_business_type, source_business_id, from_warehouse_id)',
    'SELECT 1');
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := IF(NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @p0b_schema
          AND table_name = 'inv_transfer_order'
          AND index_name = 'uk_inv_transfer_sales_delivery_notice'),
    'ALTER TABLE inv_transfer_order ADD UNIQUE KEY uk_inv_transfer_sales_delivery_notice (p0b_sales_delivery_notice_source_id, from_warehouse_id)',
    'SELECT 1');
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := IF(NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @p0b_schema
          AND table_name = 'inv_delivery_notice_detail'
          AND index_name = 'idx_inv_delivery_notice_warehouse'),
    'ALTER TABLE inv_delivery_notice_detail ADD KEY idx_inv_delivery_notice_warehouse (notice_id, warehouse_id)',
    'SELECT 1');
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;

SET @p0b_ddl := IF(NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @p0b_schema
          AND table_name = 'inv_outbound_record'
          AND index_name = 'idx_inv_outbound_notice_detail'),
    'ALTER TABLE inv_outbound_record ADD KEY idx_inv_outbound_notice_detail (notice_id, notice_detail_id)',
    'SELECT 1');
PREPARE p0b_stmt FROM @p0b_ddl;
EXECUTE p0b_stmt;
DEALLOCATE PREPARE p0b_stmt;
