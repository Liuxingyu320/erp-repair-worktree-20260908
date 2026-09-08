-- 历史 inv_stock -> 批次/库位明细余额回填
-- 默认 dry-run：不写新业务事实。
-- 启用示例（只能先在本机独立 MySQL 测试库演练）：
--   SET @target_warehouse_id = 1245;
--   SET @enable_lot_location_backfill = 1;
--   SOURCE erp_inventory_lot_location_backfill_20260713.sql;

SET @target_warehouse_id = NULLIF(@target_warehouse_id, 0);
SET @enable_lot_location_backfill = COALESCE(@enable_lot_location_backfill, 0);
SET @backfill_batch_no = COALESCE(
    NULLIF(@backfill_batch_no, ''),
    CONCAT('LOT-BACKFILL-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'))
);
SET @backfill_operator = COALESCE(NULLIF(@backfill_operator, ''), CURRENT_USER());

-- 1. dry-run 范围与阻断项。
SELECT
    @enable_lot_location_backfill AS write_enabled,
    @target_warehouse_id AS target_warehouse_id,
    @backfill_batch_no AS backfill_batch_no,
    COUNT(*) AS source_stock_count,
    COUNT(DISTINCT warehouse_id) AS warehouse_count,
    SUM(COALESCE(current_quantity, 0)) AS source_current_quantity,
    SUM(COALESCE(total_cost, 0)) AS source_total_cost
FROM inv_stock
WHERE (@target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id);

SELECT 'negative_or_invalid_quantity' AS blocker_type, s.*
FROM inv_stock s
WHERE (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
  AND (
      COALESCE(s.current_quantity, 0) < 0
      OR COALESCE(s.locked_quantity, 0) < 0
      OR COALESCE(s.available_quantity, 0) < 0
      OR ABS(COALESCE(s.current_quantity, 0) - COALESCE(s.locked_quantity, 0)
             - COALESCE(s.available_quantity, 0)) > 0.0001
  );

SELECT 'missing_dimension' AS blocker_type, s.*
FROM inv_stock s
WHERE (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
  AND (s.warehouse_id IS NULL OR COALESCE(s.item_id, s.product_id) IS NULL);

SELECT 'serial_quantity_not_one' AS blocker_type, s.*
FROM inv_stock s
WHERE (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
  AND NULLIF(TRIM(s.serial_no), '') IS NOT NULL
  AND COALESCE(s.current_quantity, 0) <> 1;

SELECT 'duplicate_serial' AS blocker_type,
       COALESCE(item_type, 'product') AS item_type,
       TRIM(serial_no) AS serial_no,
       COUNT(*) AS duplicate_count,
       GROUP_CONCAT(stock_id ORDER BY stock_id) AS stock_ids
FROM inv_stock
WHERE (@target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id)
  AND NULLIF(TRIM(serial_no), '') IS NOT NULL
GROUP BY COALESCE(item_type, 'product'), TRIM(serial_no)
HAVING COUNT(*) > 1;

SELECT
    (
        SELECT COUNT(*)
        FROM inv_stock s
        WHERE (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
          AND (
              COALESCE(s.current_quantity, 0) < 0
              OR COALESCE(s.locked_quantity, 0) < 0
              OR COALESCE(s.available_quantity, 0) < 0
              OR ABS(COALESCE(s.current_quantity, 0) - COALESCE(s.locked_quantity, 0)
                     - COALESCE(s.available_quantity, 0)) > 0.0001
              OR s.warehouse_id IS NULL
              OR COALESCE(s.item_id, s.product_id) IS NULL
              OR (NULLIF(TRIM(s.serial_no), '') IS NOT NULL
                  AND COALESCE(s.current_quantity, 0) <> 1)
          )
    )
    +
    (
        SELECT COUNT(*)
        FROM (
            SELECT COALESCE(item_type, 'product'), TRIM(serial_no)
            FROM inv_stock
            WHERE (@target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id)
              AND NULLIF(TRIM(serial_no), '') IS NOT NULL
            GROUP BY COALESCE(item_type, 'product'), TRIM(serial_no)
            HAVING COUNT(*) > 1
        ) duplicate_serial_groups
    )
INTO @backfill_blocker_count;

SET @backfill_can_write = IF(
    @enable_lot_location_backfill = 1 AND @backfill_blocker_count = 0,
    1,
    0
);

-- 2. 写入部分所有语句都受 @backfill_can_write 门禁保护。
START TRANSACTION;

INSERT INTO inv_warehouse_zone (
    warehouse_id, zone_code, zone_name, zone_type, sort_order, status, is_virtual,
    create_by, create_time, remark
)
SELECT DISTINCT s.warehouse_id, 'SYS-VIRTUAL', '系统虚拟库区', 'virtual', -100, '0', '1',
       @backfill_operator, NOW(), CONCAT('历史回填 ', @backfill_batch_no)
FROM inv_stock s
WHERE @backfill_can_write = 1
  AND (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
ON DUPLICATE KEY UPDATE zone_id = zone_id;

INSERT INTO inv_warehouse_location (
    warehouse_id, zone_id, location_code, location_name, location_type,
    sort_order, status, is_virtual, create_by, create_time, remark
)
SELECT z.warehouse_id, z.zone_id, virtual_location.location_code,
       virtual_location.location_name, virtual_location.location_type,
       virtual_location.sort_order, '0', '1', @backfill_operator, NOW(),
       CONCAT('系统虚拟库位 ', @backfill_batch_no)
FROM inv_warehouse_zone z
JOIN (
    SELECT 'UNALLOCATED' AS location_code, '未分配库位' AS location_name,
           'virtual' AS location_type, -100 AS sort_order
    UNION ALL
    SELECT 'PENDING-PUTAWAY', '待上架', 'receiving', -90
) virtual_location
WHERE @backfill_can_write = 1
  AND z.zone_code = 'SYS-VIRTUAL'
  AND (@target_warehouse_id IS NULL OR z.warehouse_id = @target_warehouse_id)
ON DUPLICATE KEY UPDATE location_id = location_id;

INSERT INTO inv_warehouse_location (
    warehouse_id, zone_id, location_code, location_name, location_type,
    sort_order, status, is_virtual, create_by, create_time, remark
)
SELECT s.warehouse_id, z.zone_id, TRIM(s.location_code),
       COALESCE(MAX(NULLIF(TRIM(s.location_name), '')), TRIM(s.location_code)),
       'storage', 0, '0', '0', @backfill_operator, NOW(),
       CONCAT('从 inv_stock 回填 ', @backfill_batch_no)
FROM inv_stock s
JOIN inv_warehouse_zone z
  ON z.warehouse_id = s.warehouse_id AND z.zone_code = 'SYS-VIRTUAL'
WHERE @backfill_can_write = 1
  AND (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
  AND NULLIF(TRIM(s.location_code), '') IS NOT NULL
GROUP BY s.warehouse_id, z.zone_id, TRIM(s.location_code)
ON DUPLICATE KEY UPDATE location_id = location_id;

INSERT INTO inv_inventory_lot (
    lot_no, item_type, item_id, product_id, warehouse_id,
    supplier_batch_no, expiry_date, qc_status, lot_status,
    source_type, source_stock_id, create_by, create_time, remark
)
SELECT
    CASE
      WHEN NULLIF(TRIM(s.batch_no), '') IS NOT NULL
        THEN CONCAT('HIST-', s.stock_id, '-', LEFT(TRIM(s.batch_no), 60))
      ELSE CONCAT('HIST-', s.stock_id)
    END,
    COALESCE(s.item_type, 'product'), COALESCE(s.item_id, s.product_id),
    s.product_id, s.warehouse_id, NULLIF(TRIM(s.batch_no), ''), s.expiry_date,
    'passed', 'active', 'history', s.stock_id, @backfill_operator, NOW(),
    CONCAT('从 inv_stock 历史回填 ', @backfill_batch_no)
FROM inv_stock s
WHERE @backfill_can_write = 1
  AND (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
ON DUPLICATE KEY UPDATE source_stock_id = inv_inventory_lot.source_stock_id;

INSERT INTO inv_stock_balance_detail (
    item_type, item_id, product_id, warehouse_id, lot_id, location_id,
    current_quantity, locked_quantity, available_quantity,
    cost_price, total_cost, version, source_stock_id,
    last_in_time, last_out_time, create_by, create_time, remark
)
SELECT COALESCE(s.item_type, 'product'), COALESCE(s.item_id, s.product_id), s.product_id,
       s.warehouse_id, lot.lot_id, location.location_id,
       COALESCE(s.current_quantity, 0), COALESCE(s.locked_quantity, 0),
       COALESCE(s.available_quantity, 0), COALESCE(s.cost_price, 0),
       COALESCE(s.total_cost, 0), 0, s.stock_id,
       s.last_in_time, s.last_out_time, @backfill_operator, NOW(),
       CONCAT('从 inv_stock 历史回填 ', @backfill_batch_no)
FROM inv_stock s
JOIN inv_inventory_lot lot ON lot.source_stock_id = s.stock_id
JOIN inv_warehouse_location location
  ON location.warehouse_id = s.warehouse_id
 AND location.location_code = COALESCE(NULLIF(TRIM(s.location_code), ''), 'UNALLOCATED')
WHERE @backfill_can_write = 1
  AND (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
ON DUPLICATE KEY UPDATE source_stock_id = inv_stock_balance_detail.source_stock_id;

INSERT INTO inv_inventory_serial (
    serial_no, item_type, item_id, product_id, warehouse_id,
    lot_id, location_id, balance_id, serial_status,
    source_type, source_business_id, create_by, create_time, remark
)
SELECT TRIM(s.serial_no), COALESCE(s.item_type, 'product'), COALESCE(s.item_id, s.product_id),
       s.product_id, s.warehouse_id, b.lot_id, b.location_id, b.balance_id,
       'available', 'history', s.stock_id, @backfill_operator, NOW(),
       CONCAT('从 inv_stock 历史回填 ', @backfill_batch_no)
FROM inv_stock s
JOIN inv_stock_balance_detail b ON b.source_stock_id = s.stock_id
WHERE @backfill_can_write = 1
  AND (@target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id)
  AND NULLIF(TRIM(s.serial_no), '') IS NOT NULL
  AND COALESCE(s.current_quantity, 0) = 1
ON DUPLICATE KEY UPDATE serial_id = serial_id;

UPDATE inv_warehouse_stock_mode m
JOIN (
    SELECT DISTINCT warehouse_id
    FROM inv_stock
    WHERE @backfill_can_write = 1
      AND (@target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id)
) target ON target.warehouse_id = m.warehouse_id
SET m.reconcile_status = 'not_run',
    m.last_reconcile_time = NULL,
    m.last_reconcile_batch = @backfill_batch_no,
    m.update_by = @backfill_operator,
    m.update_time = NOW(),
    m.remark = '回填完成，仍保持 legacy/legacy，等待对账';

INSERT INTO inv_lot_location_migration_run (
    run_batch_no, target_warehouse_id, run_type, run_status, blocker_count,
    source_stock_count, detail_balance_count, executed_by, executed_time, remark
)
SELECT @backfill_batch_no, @target_warehouse_id, 'backfill', 'completed',
       @backfill_blocker_count,
       (SELECT COUNT(*) FROM inv_stock
        WHERE @target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id),
       (SELECT COUNT(*) FROM inv_stock_balance_detail
        WHERE @target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id),
       @backfill_operator, NOW(),
       '写入完成；仓库模式仍为 legacy/legacy'
WHERE @backfill_can_write = 1
ON DUPLICATE KEY UPDATE run_id = run_id;

COMMIT;

-- 3. 结果。writes_executed=0 时本脚本未写业务事实。
SELECT
    @enable_lot_location_backfill AS write_enabled,
    @backfill_blocker_count AS blocker_count,
    @backfill_can_write AS writes_executed,
    CASE
      WHEN @enable_lot_location_backfill <> 1 THEN 'DRY_RUN_ONLY'
      WHEN @backfill_blocker_count > 0 THEN 'BLOCKED_BY_PRECHECK'
      ELSE 'BACKFILL_COMPLETED_KEEP_LEGACY_MODE'
    END AS result;

SELECT m.*
FROM inv_warehouse_stock_mode m
WHERE @target_warehouse_id IS NULL OR m.warehouse_id = @target_warehouse_id
ORDER BY m.warehouse_id;
