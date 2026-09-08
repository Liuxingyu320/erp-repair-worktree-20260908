-- 批次/库位/序列号库存只读对账
-- 可选：SET @target_warehouse_id = 1245; NULL 表示全部仓库。
-- 本脚本不更新业务表。

SET @target_warehouse_id = NULLIF(@target_warehouse_id, 0);
SET @reconcile_quantity_tolerance = 0.0001;
SET @reconcile_cost_tolerance = 0.01;

-- 1. 新老余额数量/成本差异：必须返回 0 行。
WITH legacy_balance AS (
    SELECT warehouse_id,
           COALESCE(item_type, 'product') AS item_type,
           COALESCE(item_id, product_id) AS item_id,
           SUM(COALESCE(current_quantity, 0)) AS current_quantity,
           SUM(COALESCE(locked_quantity, 0)) AS locked_quantity,
           SUM(COALESCE(available_quantity, 0)) AS available_quantity,
           SUM(COALESCE(total_cost, 0)) AS total_cost
    FROM inv_stock
    WHERE @target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id
    GROUP BY warehouse_id, COALESCE(item_type, 'product'), COALESCE(item_id, product_id)
), detail_balance AS (
    SELECT warehouse_id, item_type, item_id,
           SUM(current_quantity) AS current_quantity,
           SUM(locked_quantity) AS locked_quantity,
           SUM(available_quantity) AS available_quantity,
           SUM(total_cost) AS total_cost
    FROM inv_stock_balance_detail
    WHERE @target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id
    GROUP BY warehouse_id, item_type, item_id
), balance_keys AS (
    SELECT warehouse_id, item_type, item_id FROM legacy_balance
    UNION
    SELECT warehouse_id, item_type, item_id FROM detail_balance
)
SELECT k.warehouse_id, k.item_type, k.item_id,
       l.current_quantity AS legacy_current, d.current_quantity AS detail_current,
       l.locked_quantity AS legacy_locked, d.locked_quantity AS detail_locked,
       l.available_quantity AS legacy_available, d.available_quantity AS detail_available,
       l.total_cost AS legacy_total_cost, d.total_cost AS detail_total_cost,
       COALESCE(d.current_quantity, 0) - COALESCE(l.current_quantity, 0) AS current_diff,
       COALESCE(d.total_cost, 0) - COALESCE(l.total_cost, 0) AS cost_diff
FROM balance_keys k
LEFT JOIN legacy_balance l
  ON l.warehouse_id = k.warehouse_id AND l.item_type = k.item_type AND l.item_id = k.item_id
LEFT JOIN detail_balance d
  ON d.warehouse_id = k.warehouse_id AND d.item_type = k.item_type AND d.item_id = k.item_id
WHERE ABS(COALESCE(d.current_quantity, 0) - COALESCE(l.current_quantity, 0)) > @reconcile_quantity_tolerance
   OR ABS(COALESCE(d.locked_quantity, 0) - COALESCE(l.locked_quantity, 0)) > @reconcile_quantity_tolerance
   OR ABS(COALESCE(d.available_quantity, 0) - COALESCE(l.available_quantity, 0)) > @reconcile_quantity_tolerance
   OR ABS(COALESCE(d.total_cost, 0) - COALESCE(l.total_cost, 0)) > @reconcile_cost_tolerance
ORDER BY k.warehouse_id, k.item_type, k.item_id;

-- 2. 明细余额内部不变式：必须返回 0 行。
SELECT b.*,
       b.current_quantity - b.locked_quantity - b.available_quantity AS formula_diff,
       b.current_quantity * b.cost_price - b.total_cost AS cost_formula_diff
FROM inv_stock_balance_detail b
WHERE (@target_warehouse_id IS NULL OR b.warehouse_id = @target_warehouse_id)
  AND (
      b.current_quantity < 0 OR b.locked_quantity < 0 OR b.available_quantity < 0
      OR ABS(b.current_quantity - b.locked_quantity - b.available_quantity) > @reconcile_quantity_tolerance
      OR ABS(b.current_quantity * b.cost_price - b.total_cost) > @reconcile_cost_tolerance
  )
ORDER BY b.warehouse_id, b.balance_id;

-- 3. 孤儿/跨仓维度：必须返回 0 行。
SELECT b.balance_id, b.warehouse_id, b.item_type, b.item_id,
       b.lot_id, lot.warehouse_id AS lot_warehouse_id,
       b.location_id, loc.warehouse_id AS location_warehouse_id,
       CASE
         WHEN lot.lot_id IS NULL THEN 'missing_lot'
         WHEN loc.location_id IS NULL THEN 'missing_location'
         WHEN lot.warehouse_id <> b.warehouse_id THEN 'lot_cross_warehouse'
         WHEN loc.warehouse_id <> b.warehouse_id THEN 'location_cross_warehouse'
         WHEN lot.item_type <> b.item_type OR lot.item_id <> b.item_id THEN 'lot_item_mismatch'
       END AS issue
FROM inv_stock_balance_detail b
LEFT JOIN inv_inventory_lot lot ON lot.lot_id = b.lot_id
LEFT JOIN inv_warehouse_location loc ON loc.location_id = b.location_id
WHERE (@target_warehouse_id IS NULL OR b.warehouse_id = @target_warehouse_id)
  AND (
      lot.lot_id IS NULL OR loc.location_id IS NULL
      OR lot.warehouse_id <> b.warehouse_id
      OR loc.warehouse_id <> b.warehouse_id
      OR lot.item_type <> b.item_type OR lot.item_id <> b.item_id
  );

-- 4. 历史回填来源映射：每条 inv_stock 必须且只能对应一条历史明细余额。
SELECT s.stock_id, s.warehouse_id,
       COUNT(b.balance_id) AS detail_balance_count,
       SUM(COALESCE(b.current_quantity, 0)) AS detail_current_quantity,
       s.current_quantity AS legacy_current_quantity
FROM inv_stock s
LEFT JOIN inv_stock_balance_detail b ON b.source_stock_id = s.stock_id
WHERE @target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id
GROUP BY s.stock_id, s.warehouse_id, s.current_quantity
HAVING COUNT(b.balance_id) <> 1
    OR ABS(SUM(COALESCE(b.current_quantity, 0)) - COALESCE(s.current_quantity, 0)) > @reconcile_quantity_tolerance;

-- 5. 序列号唯一性、位置和余额：必须返回 0 行。
SELECT serial.serial_id, serial.serial_no, serial.serial_status,
       serial.warehouse_id, serial.lot_id, serial.location_id, serial.balance_id,
       balance.current_quantity, balance.warehouse_id AS balance_warehouse_id,
       balance.lot_id AS balance_lot_id, balance.location_id AS balance_location_id,
       CASE
         WHEN balance.balance_id IS NULL THEN 'missing_balance'
         WHEN serial.warehouse_id <> balance.warehouse_id THEN 'warehouse_mismatch'
         WHEN serial.lot_id <> balance.lot_id THEN 'lot_mismatch'
         WHEN serial.location_id <> balance.location_id THEN 'location_mismatch'
         WHEN serial.serial_status IN ('pending_qc', 'available', 'allocated', 'picked', 'quarantine')
              AND balance.current_quantity < 1 THEN 'serial_without_quantity'
       END AS issue
FROM inv_inventory_serial serial
LEFT JOIN inv_stock_balance_detail balance ON balance.balance_id = serial.balance_id
WHERE (@target_warehouse_id IS NULL OR serial.warehouse_id = @target_warehouse_id)
  AND (
      balance.balance_id IS NULL
      OR serial.warehouse_id <> balance.warehouse_id
      OR serial.lot_id <> balance.lot_id
      OR serial.location_id <> balance.location_id
      OR (serial.serial_status IN ('pending_qc', 'available', 'allocated', 'picked', 'quarantine')
          AND balance.current_quantity < 1)
  );

SELECT item_type, serial_no, COUNT(*) AS duplicate_count
FROM inv_inventory_serial
WHERE @target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id
GROUP BY item_type, serial_no
HAVING COUNT(*) > 1;

-- 6. 批次状态/效期异常：必须返回 0 行（历史过期库存需人工确认）。
WITH lot_quantity AS (
    SELECT lot_id,
           SUM(current_quantity) AS current_quantity,
           SUM(available_quantity) AS available_quantity
    FROM inv_stock_balance_detail
    GROUP BY lot_id
)
SELECT lot.*, COALESCE(q.current_quantity, 0) AS current_quantity,
       CASE
         WHEN lot.production_date IS NOT NULL AND lot.expiry_date IS NOT NULL
              AND lot.production_date > lot.expiry_date THEN 'production_after_expiry'
         WHEN lot.expiry_date < CURRENT_DATE AND COALESCE(q.current_quantity, 0) > 0
              AND lot.lot_status <> 'expired' THEN 'expired_lot_still_active'
         WHEN lot.qc_status IN ('pending', 'rejected', 'quarantine')
              AND COALESCE(q.available_quantity, 0) > 0 THEN 'unavailable_lot_has_available_stock'
       END AS issue
FROM inv_inventory_lot lot
LEFT JOIN lot_quantity q ON q.lot_id = lot.lot_id
WHERE (@target_warehouse_id IS NULL OR lot.warehouse_id = @target_warehouse_id)
  AND (
      (lot.production_date IS NOT NULL AND lot.expiry_date IS NOT NULL
       AND lot.production_date > lot.expiry_date)
      OR (lot.expiry_date < CURRENT_DATE AND COALESCE(q.current_quantity, 0) > 0
          AND lot.lot_status <> 'expired')
      OR (lot.qc_status IN ('pending', 'rejected', 'quarantine')
          AND COALESCE(q.available_quantity, 0) > 0)
  );

-- 7. 明细流水算术和维度：必须返回 0 行。
SELECT ledger.*,
       CASE
         WHEN ledger.before_quantity IS NOT NULL AND ledger.after_quantity IS NOT NULL
              AND ABS(ledger.before_quantity + ledger.change_quantity - ledger.after_quantity)
                    > @reconcile_quantity_tolerance THEN 'ledger_arithmetic_error'
         WHEN from_location.location_id IS NOT NULL
              AND from_location.warehouse_id <> ledger.warehouse_id THEN 'from_location_cross_warehouse'
         WHEN to_location.location_id IS NOT NULL
              AND to_location.warehouse_id <> ledger.warehouse_id THEN 'to_location_cross_warehouse'
       END AS issue
FROM inv_stock_ledger_detail ledger
LEFT JOIN inv_warehouse_location from_location ON from_location.location_id = ledger.from_location_id
LEFT JOIN inv_warehouse_location to_location ON to_location.location_id = ledger.to_location_id
WHERE (@target_warehouse_id IS NULL OR ledger.warehouse_id = @target_warehouse_id)
  AND (
      (ledger.before_quantity IS NOT NULL AND ledger.after_quantity IS NOT NULL
       AND ABS(ledger.before_quantity + ledger.change_quantity - ledger.after_quantity)
             > @reconcile_quantity_tolerance)
      OR (from_location.location_id IS NOT NULL AND from_location.warehouse_id <> ledger.warehouse_id)
      OR (to_location.location_id IS NOT NULL AND to_location.warehouse_id <> ledger.warehouse_id)
  );

-- 8. 作业任务数量和孤儿明细：必须返回 0 行。
SELECT detail.*, task.warehouse_id, task.task_status,
       CASE
         WHEN task.task_id IS NULL THEN 'missing_task'
         WHEN detail.planned_quantity < 0 OR detail.processed_quantity < 0 OR detail.exception_quantity < 0
              THEN 'negative_task_quantity'
         WHEN detail.processed_quantity + detail.exception_quantity
              > detail.planned_quantity + @reconcile_quantity_tolerance THEN 'task_quantity_exceeded'
       END AS issue
FROM inv_warehouse_task_detail detail
LEFT JOIN inv_warehouse_task task ON task.task_id = detail.task_id
WHERE (@target_warehouse_id IS NULL OR task.warehouse_id = @target_warehouse_id)
  AND (
      task.task_id IS NULL
      OR detail.planned_quantity < 0 OR detail.processed_quantity < 0 OR detail.exception_quantity < 0
      OR detail.processed_quantity + detail.exception_quantity
           > detail.planned_quantity + @reconcile_quantity_tolerance
  );

-- 9. 灰度模式安全性：detail 读不能绕过 dual 写和对账批准。
SELECT m.*,
       CASE
         WHEN m.read_mode IN ('shadow', 'detail') AND m.write_mode <> 'dual' THEN 'new_read_without_dual_write'
         WHEN m.read_mode = 'detail' AND m.reconcile_status <> 'passed' THEN 'detail_read_without_reconcile_pass'
         WHEN m.read_mode = 'detail' AND (m.approved_by IS NULL OR m.approved_time IS NULL) THEN 'detail_read_without_approval'
       END AS issue
FROM inv_warehouse_stock_mode m
WHERE (@target_warehouse_id IS NULL OR m.warehouse_id = @target_warehouse_id)
  AND (
      (m.read_mode IN ('shadow', 'detail') AND m.write_mode <> 'dual')
      OR (m.read_mode = 'detail' AND m.reconcile_status <> 'passed')
      OR (m.read_mode = 'detail' AND (m.approved_by IS NULL OR m.approved_time IS NULL))
  );

-- 10. GO/NO-GO 摘要：所有 *_issue_count 必须为 0。
WITH legacy_balance AS (
    SELECT warehouse_id, COALESCE(item_type, 'product') AS item_type,
           COALESCE(item_id, product_id) AS item_id,
           SUM(COALESCE(current_quantity, 0)) AS current_quantity,
           SUM(COALESCE(locked_quantity, 0)) AS locked_quantity,
           SUM(COALESCE(available_quantity, 0)) AS available_quantity,
           SUM(COALESCE(total_cost, 0)) AS total_cost
    FROM inv_stock
    WHERE @target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id
    GROUP BY warehouse_id, COALESCE(item_type, 'product'), COALESCE(item_id, product_id)
), detail_balance AS (
    SELECT warehouse_id, item_type, item_id,
           SUM(current_quantity) AS current_quantity,
           SUM(locked_quantity) AS locked_quantity,
           SUM(available_quantity) AS available_quantity,
           SUM(total_cost) AS total_cost
    FROM inv_stock_balance_detail
    WHERE @target_warehouse_id IS NULL OR warehouse_id = @target_warehouse_id
    GROUP BY warehouse_id, item_type, item_id
), balance_keys AS (
    SELECT warehouse_id, item_type, item_id FROM legacy_balance
    UNION SELECT warehouse_id, item_type, item_id FROM detail_balance
), balance_diff AS (
    SELECT k.warehouse_id, k.item_type, k.item_id
    FROM balance_keys k
    LEFT JOIN legacy_balance l
      ON l.warehouse_id = k.warehouse_id AND l.item_type = k.item_type AND l.item_id = k.item_id
    LEFT JOIN detail_balance d
      ON d.warehouse_id = k.warehouse_id AND d.item_type = k.item_type AND d.item_id = k.item_id
    WHERE ABS(COALESCE(d.current_quantity, 0) - COALESCE(l.current_quantity, 0)) > @reconcile_quantity_tolerance
       OR ABS(COALESCE(d.locked_quantity, 0) - COALESCE(l.locked_quantity, 0)) > @reconcile_quantity_tolerance
       OR ABS(COALESCE(d.available_quantity, 0) - COALESCE(l.available_quantity, 0)) > @reconcile_quantity_tolerance
       OR ABS(COALESCE(d.total_cost, 0) - COALESCE(l.total_cost, 0)) > @reconcile_cost_tolerance
)
SELECT @target_warehouse_id AS target_warehouse_id,
       (SELECT COUNT(*) FROM balance_diff) AS legacy_detail_diff_count,
       (SELECT COUNT(*) FROM inv_stock_balance_detail b
        WHERE (@target_warehouse_id IS NULL OR b.warehouse_id = @target_warehouse_id)
          AND (b.current_quantity < 0 OR b.locked_quantity < 0 OR b.available_quantity < 0
               OR ABS(b.current_quantity - b.locked_quantity - b.available_quantity) > @reconcile_quantity_tolerance
               OR ABS(b.current_quantity * b.cost_price - b.total_cost) > @reconcile_cost_tolerance)) AS detail_invariant_issue_count,
       (SELECT COUNT(*) FROM inv_stock_balance_detail b
        LEFT JOIN inv_inventory_lot lot ON lot.lot_id = b.lot_id
        LEFT JOIN inv_warehouse_location loc ON loc.location_id = b.location_id
        WHERE (@target_warehouse_id IS NULL OR b.warehouse_id = @target_warehouse_id)
          AND (lot.lot_id IS NULL OR loc.location_id IS NULL
               OR lot.warehouse_id <> b.warehouse_id OR loc.warehouse_id <> b.warehouse_id
               OR lot.item_type <> b.item_type OR lot.item_id <> b.item_id)) AS orphan_dimension_issue_count,
       (SELECT COUNT(*) FROM inv_stock_ledger_detail ledger
        WHERE (@target_warehouse_id IS NULL OR ledger.warehouse_id = @target_warehouse_id)
          AND ledger.before_quantity IS NOT NULL AND ledger.after_quantity IS NOT NULL
          AND ABS(ledger.before_quantity + ledger.change_quantity - ledger.after_quantity)
                > @reconcile_quantity_tolerance) AS ledger_issue_count,
       (SELECT COUNT(*) FROM (
            SELECT s.stock_id
            FROM inv_stock s
            LEFT JOIN inv_stock_balance_detail b ON b.source_stock_id = s.stock_id
            WHERE @target_warehouse_id IS NULL OR s.warehouse_id = @target_warehouse_id
            GROUP BY s.stock_id, s.current_quantity
            HAVING COUNT(b.balance_id) <> 1
                OR ABS(SUM(COALESCE(b.current_quantity, 0)) - COALESCE(s.current_quantity, 0))
                     > @reconcile_quantity_tolerance
        ) source_mapping_issue) AS source_mapping_issue_count,
       (SELECT COUNT(*) FROM inv_inventory_serial serial
        LEFT JOIN inv_stock_balance_detail balance ON balance.balance_id = serial.balance_id
        WHERE (@target_warehouse_id IS NULL OR serial.warehouse_id = @target_warehouse_id)
          AND (balance.balance_id IS NULL
               OR serial.warehouse_id <> balance.warehouse_id
               OR serial.lot_id <> balance.lot_id
               OR serial.location_id <> balance.location_id
               OR (serial.serial_status IN ('pending_qc', 'available', 'allocated', 'picked', 'quarantine')
                   AND balance.current_quantity < 1))) AS serial_issue_count,
       (SELECT COUNT(*)
        FROM inv_inventory_lot lot
        LEFT JOIN (
            SELECT lot_id, SUM(current_quantity) AS current_quantity,
                   SUM(available_quantity) AS available_quantity
            FROM inv_stock_balance_detail
            GROUP BY lot_id
        ) q ON q.lot_id = lot.lot_id
        WHERE (@target_warehouse_id IS NULL OR lot.warehouse_id = @target_warehouse_id)
          AND ((lot.production_date IS NOT NULL AND lot.expiry_date IS NOT NULL
                AND lot.production_date > lot.expiry_date)
               OR (lot.expiry_date < CURRENT_DATE AND COALESCE(q.current_quantity, 0) > 0
                   AND lot.lot_status <> 'expired')
               OR (lot.qc_status IN ('pending', 'rejected', 'quarantine')
                   AND COALESCE(q.available_quantity, 0) > 0))) AS lot_issue_count,
       (SELECT COUNT(*) FROM inv_warehouse_task_detail detail
        LEFT JOIN inv_warehouse_task task ON task.task_id = detail.task_id
        WHERE (@target_warehouse_id IS NULL OR task.warehouse_id = @target_warehouse_id)
          AND (task.task_id IS NULL
               OR detail.planned_quantity < 0 OR detail.processed_quantity < 0 OR detail.exception_quantity < 0
               OR detail.processed_quantity + detail.exception_quantity
                    > detail.planned_quantity + @reconcile_quantity_tolerance)) AS task_issue_count,
       (SELECT COUNT(*) FROM inv_warehouse_stock_mode m
        WHERE (@target_warehouse_id IS NULL OR m.warehouse_id = @target_warehouse_id)
          AND ((m.read_mode IN ('shadow', 'detail') AND m.write_mode <> 'dual')
               OR (m.read_mode = 'detail' AND m.reconcile_status <> 'passed')
               OR (m.read_mode = 'detail' AND (m.approved_by IS NULL OR m.approved_time IS NULL)))) AS unsafe_mode_issue_count;
