-- 批次/库位库存紧急读写模式回退
-- 安全边界：只把指定仓库切回 legacy/legacy，不删除任何余额、批次、序列号或流水事实。
-- 默认 dry-run。启用示例：
--   SET @target_warehouse_id = 1245;
--   SET @rollback_reason = '灰度对账出现差异';
--   SET @enable_lot_location_rollback = 1;
--   SOURCE erp_inventory_lot_location_rollback_20260713.sql;

SET @target_warehouse_id = NULLIF(@target_warehouse_id, 0);
SET @enable_lot_location_rollback = COALESCE(@enable_lot_location_rollback, 0);
SET @rollback_reason = COALESCE(NULLIF(@rollback_reason, ''), '批次库位模式紧急回退');
SET @rollback_operator = COALESCE(NULLIF(@rollback_operator, ''), CURRENT_USER());
SET @rollback_batch_no = COALESCE(
    NULLIF(@rollback_batch_no, ''),
    CONCAT('LOT-ROLLBACK-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'))
);
SET @rollback_can_write = IF(
    @enable_lot_location_rollback = 1 AND @target_warehouse_id IS NOT NULL,
    1,
    0
);

SELECT @enable_lot_location_rollback AS write_enabled,
       @target_warehouse_id AS target_warehouse_id,
       @rollback_batch_no AS rollback_batch_no,
       @rollback_reason AS rollback_reason;

SELECT m.*
FROM inv_warehouse_stock_mode m
WHERE m.warehouse_id = @target_warehouse_id;

START TRANSACTION;

INSERT INTO inv_warehouse_stock_mode_history (
    change_batch_no, warehouse_id, old_write_mode, old_read_mode,
    new_write_mode, new_read_mode, change_reason, changed_by, changed_time
)
SELECT @rollback_batch_no, m.warehouse_id, m.write_mode, m.read_mode,
       'legacy', 'legacy', @rollback_reason, @rollback_operator, NOW()
FROM inv_warehouse_stock_mode m
WHERE @rollback_can_write = 1
  AND m.warehouse_id = @target_warehouse_id
  AND (m.write_mode <> 'legacy' OR m.read_mode <> 'legacy')
  AND NOT EXISTS (
      SELECT 1 FROM inv_warehouse_stock_mode_history h
      WHERE h.change_batch_no = @rollback_batch_no
  );

UPDATE inv_warehouse_stock_mode
SET write_mode = 'legacy',
    read_mode = 'legacy',
    reconcile_status = 'not_run',
    approved_by = NULL,
    approved_time = NULL,
    update_by = @rollback_operator,
    update_time = NOW(),
    remark = CONCAT('已回退：', @rollback_reason, '；批次事实保留')
WHERE @rollback_can_write = 1
  AND warehouse_id = @target_warehouse_id;

INSERT INTO inv_lot_location_migration_run (
    run_batch_no, target_warehouse_id, run_type, run_status, blocker_count,
    source_stock_count, detail_balance_count, executed_by, executed_time, remark
)
SELECT @rollback_batch_no, @target_warehouse_id, 'rollback', 'completed', 0,
       (SELECT COUNT(*) FROM inv_stock WHERE warehouse_id = @target_warehouse_id),
       (SELECT COUNT(*) FROM inv_stock_balance_detail WHERE warehouse_id = @target_warehouse_id),
       @rollback_operator, NOW(),
       CONCAT('回到 legacy/legacy；原因：', @rollback_reason)
WHERE @rollback_can_write = 1
ON DUPLICATE KEY UPDATE run_id = run_id;

COMMIT;

SELECT @rollback_can_write AS writes_executed,
       CASE
         WHEN @enable_lot_location_rollback <> 1 THEN 'DRY_RUN_ONLY'
         WHEN @target_warehouse_id IS NULL THEN 'BLOCKED_TARGET_WAREHOUSE_REQUIRED'
         ELSE 'ROLLED_BACK_TO_LEGACY_KEEP_FACTS'
       END AS result;

SELECT m.*
FROM inv_warehouse_stock_mode m
WHERE m.warehouse_id = @target_warehouse_id;
