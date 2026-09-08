-- 仓库管理每日只读健康检查
-- 要求：使用只读账号调度，结果保存到监控/工单系统；本脚本不修复数据。
-- 所有 issue_count 应为 0；备份表和过期草稿为治理指标，按阈值告警。

SET @stale_draft_days = COALESCE(@stale_draft_days, 7);

-- 1. 可发货调拨的审批完整性。
SELECT o.transfer_id, o.order_no, o.status, o.approval_instance_id,
       i.transfer_id AS instance_transfer_id, i.status AS instance_status,
       COUNT(t.task_id) AS task_count,
       SUM(CASE WHEN t.status = 'approved' THEN 1 ELSE 0 END) AS approved_task_count,
       SUM(CASE WHEN t.status = 'pending' THEN 1 ELSE 0 END) AS pending_task_count,
       SUM(CASE WHEN t.status = 'rejected' THEN 1 ELSE 0 END) AS rejected_task_count,
       SUM(CASE WHEN t.task_id IS NOT NULL AND t.transfer_id <> o.transfer_id THEN 1 ELSE 0 END) AS mismatched_task_count,
       COALESCE(MAX(auto_approve.auto_approve_count), 0) AS auto_approve_count
FROM inv_transfer_order o
LEFT JOIN inv_transfer_approval_instance i ON i.instance_id = o.approval_instance_id
LEFT JOIN inv_transfer_approval_task t ON t.instance_id = i.instance_id
LEFT JOIN (
    SELECT transfer_id, COUNT(*) AS auto_approve_count
    FROM inv_transfer_status_log
    WHERE action = 'auto_approve'
    GROUP BY transfer_id
) auto_approve ON auto_approve.transfer_id = o.transfer_id
WHERE o.status IN ('approved', 'reserved', 'partial_delivered')
GROUP BY o.transfer_id, o.order_no, o.status, o.approval_instance_id,
         i.instance_id, i.transfer_id, i.status, i.approval_mode, i.required_count
HAVING o.approval_instance_id IS NULL
    OR i.instance_id IS NULL
    OR i.transfer_id <> o.transfer_id
    OR i.status <> 'approved'
    OR mismatched_task_count > 0
    OR rejected_task_count > 0
    OR (task_count = 0 AND auto_approve_count = 0)
    OR (i.approval_mode = 'all_nodes' AND pending_task_count > 0)
    OR (i.approval_mode = 'any_one' AND approved_task_count = 0)
    OR (i.approval_mode = 'quorum'
        AND approved_task_count < GREATEST(COALESCE(i.required_count, 0), 1));

-- 2. 采购待检状态断裂。
SELECT o.order_id, o.order_no, o.shop_dept_id, o.status, o.qc_status,
       COUNT(i.inbound_id) AS pending_inbound_count
FROM inv_purchase_order o
LEFT JOIN inv_inbound_record i
  ON i.purchase_order_id = o.order_id
 AND COALESCE(i.qc_result, 'pending') = 'pending'
WHERE o.qc_status = 'pending'
GROUP BY o.order_id, o.order_no, o.shop_dept_id, o.status, o.qc_status
HAVING COUNT(i.inbound_id) = 0;

-- 3. 采购明细已收数与有效入库事实。
SELECT d.order_id, d.detail_id, COALESCE(d.item_type, 'product') AS item_type,
       COALESCE(d.item_id, d.product_id) AS item_id,
       COALESCE(d.received_quantity, 0) AS detail_received_quantity,
       COALESCE(SUM(CASE
           WHEN COALESCE(i.qc_result, 'pending') IN ('pending', 'passed', 'concession')
             THEN i.quantity ELSE 0 END), 0) AS effective_inbound_quantity
FROM inv_purchase_detail d
LEFT JOIN inv_inbound_record i
  ON i.purchase_order_id = d.order_id AND i.purchase_detail_id = d.detail_id
GROUP BY d.order_id, d.detail_id, COALESCE(d.item_type, 'product'),
         COALESCE(d.item_id, d.product_id), d.received_quantity
HAVING ABS(detail_received_quantity - effective_inbound_quantity) > 0.0001;

-- 4. 收货批次与逐行质检数量守恒。
SELECT d.*,
       d.received_quantity - d.pending_quantity - d.inspected_quantity AS receipt_formula_diff,
       d.inspected_quantity - d.accepted_quantity - d.rejected_quantity
           - d.concession_quantity AS quality_formula_diff
FROM inv_receipt_batch_detail d
WHERE ABS(d.received_quantity - d.pending_quantity - d.inspected_quantity) > 0.0001
   OR ABS(d.inspected_quantity - d.accepted_quantity - d.rejected_quantity
          - d.concession_quantity) > 0.0001
   OR d.received_quantity < 0 OR d.pending_quantity < 0 OR d.inspected_quantity < 0
   OR d.accepted_quantity < 0 OR d.rejected_quantity < 0 OR d.concession_quantity < 0;

SELECT i.inbound_id, i.purchase_order_id, i.receipt_batch_id, i.receipt_batch_detail_id,
       b.purchase_order_id AS batch_order_id, d.purchase_detail_id AS batch_purchase_detail_id,
       i.purchase_detail_id AS inbound_purchase_detail_id
FROM inv_inbound_record i
LEFT JOIN inv_receipt_batch b ON b.batch_id = i.receipt_batch_id
LEFT JOIN inv_receipt_batch_detail d ON d.batch_detail_id = i.receipt_batch_detail_id
WHERE i.receipt_batch_id IS NOT NULL
  AND (b.batch_id IS NULL OR d.batch_detail_id IS NULL
       OR b.purchase_order_id <> i.purchase_order_id
       OR d.batch_id <> i.receipt_batch_id
       OR d.purchase_detail_id <> i.purchase_detail_id);

-- 5. 汇总库存公式、负数和流水算术。
SELECT s.*,
       COALESCE(s.current_quantity, 0) - COALESCE(s.locked_quantity, 0)
           - COALESCE(s.available_quantity, 0) AS formula_diff
FROM inv_stock s
WHERE COALESCE(s.current_quantity, 0) < 0
   OR COALESCE(s.locked_quantity, 0) < 0
   OR COALESCE(s.available_quantity, 0) < 0
   OR ABS(COALESCE(s.current_quantity, 0) - COALESCE(s.locked_quantity, 0)
          - COALESCE(s.available_quantity, 0)) > 0.0001;

SELECT l.log_id, l.business_type, l.business_id, l.movement_type,
       l.before_quantity, l.change_quantity, l.after_quantity,
       l.before_quantity + l.change_quantity - l.after_quantity AS formula_diff
FROM inv_stock_log l
WHERE l.before_quantity IS NOT NULL AND l.after_quantity IS NOT NULL
  AND ABS(l.before_quantity + l.change_quantity - l.after_quantity) > 0.0001;

-- 6. 盘点复盘和过期草稿。
SELECT d.check_id, d.detail_id, d.book_qty, d.actual_qty,
       d.recount_required, d.recount_qty
FROM inv_stock_check_detail d
WHERE d.recount_required = '1' AND d.recount_qty IS NULL;

SELECT c.check_id, c.check_no, c.shop_dept_id, c.create_by, c.create_time,
       TIMESTAMPDIFF(DAY, c.create_time, NOW()) AS stale_days
FROM inv_stock_check c
WHERE c.status = 'draft'
  AND c.create_time < DATE_SUB(NOW(), INTERVAL @stale_draft_days DAY)
ORDER BY c.create_time;

-- 7. 批次库位已回填仓库的新老余额差异。
WITH legacy_balance AS (
    SELECT s.warehouse_id, COALESCE(s.item_type, 'product') AS item_type,
           COALESCE(s.item_id, s.product_id) AS item_id,
           SUM(s.current_quantity) AS current_quantity,
           SUM(s.locked_quantity) AS locked_quantity,
           SUM(s.available_quantity) AS available_quantity,
           SUM(s.total_cost) AS total_cost
    FROM inv_stock s
    WHERE EXISTS (
        SELECT 1 FROM inv_stock_balance_detail marker
        WHERE marker.warehouse_id = s.warehouse_id
    )
    GROUP BY s.warehouse_id, COALESCE(s.item_type, 'product'), COALESCE(s.item_id, s.product_id)
), detail_balance AS (
    SELECT warehouse_id, item_type, item_id,
           SUM(current_quantity) AS current_quantity,
           SUM(locked_quantity) AS locked_quantity,
           SUM(available_quantity) AS available_quantity,
           SUM(total_cost) AS total_cost
    FROM inv_stock_balance_detail
    GROUP BY warehouse_id, item_type, item_id
)
SELECT l.warehouse_id, l.item_type, l.item_id,
       l.current_quantity AS legacy_current, d.current_quantity AS detail_current,
       l.total_cost AS legacy_cost, d.total_cost AS detail_cost
FROM legacy_balance l
LEFT JOIN detail_balance d
  ON d.warehouse_id = l.warehouse_id AND d.item_type = l.item_type AND d.item_id = l.item_id
WHERE d.item_id IS NULL
   OR ABS(l.current_quantity - d.current_quantity) > 0.0001
   OR ABS(l.locked_quantity - d.locked_quantity) > 0.0001
   OR ABS(l.available_quantity - d.available_quantity) > 0.0001
   OR ABS(l.total_cost - d.total_cost) > 0.01;

-- 8. 治理信息：业务库中的库存备份表数和疑似 QA 单据数。
SELECT COUNT(*) AS inventory_backup_table_count,
       GROUP_CONCAT(table_name ORDER BY table_name SEPARATOR ',') AS table_names
FROM information_schema.TABLES
WHERE table_schema = DATABASE()
  AND table_name LIKE 'inv%backup%';

SELECT
    (SELECT COUNT(*) FROM inv_purchase_order
     WHERE order_no LIKE 'QA-%' OR order_no LIKE 'MOBILE-QA%') AS qa_purchase_count,
    (SELECT COUNT(*) FROM inv_transfer_order
     WHERE transfer_no LIKE 'QA-%' OR transfer_no LIKE 'MOBILE-QA%') AS qa_transfer_count,
    (SELECT COUNT(*) FROM inv_stock_check
     WHERE check_no LIKE 'QA-%' OR check_no LIKE 'MOBILE-QA%') AS qa_stock_check_count;

-- 9. 一行摘要，便于监控采集。
SELECT
    (SELECT COUNT(*) FROM inv_purchase_order o
     WHERE o.qc_status = 'pending'
       AND NOT EXISTS (SELECT 1 FROM inv_inbound_record i
                       WHERE i.purchase_order_id = o.order_id
                         AND COALESCE(i.qc_result, 'pending') = 'pending')) AS purchase_qc_broken_count,
    (SELECT COUNT(*) FROM inv_receipt_batch_detail d
     WHERE ABS(d.received_quantity - d.pending_quantity - d.inspected_quantity) > 0.0001
        OR ABS(d.inspected_quantity - d.accepted_quantity - d.rejected_quantity
               - d.concession_quantity) > 0.0001) AS receipt_quality_broken_count,
    (SELECT COUNT(*) FROM inv_stock s
     WHERE COALESCE(s.current_quantity, 0) < 0
        OR COALESCE(s.locked_quantity, 0) < 0
        OR COALESCE(s.available_quantity, 0) < 0
        OR ABS(COALESCE(s.current_quantity, 0) - COALESCE(s.locked_quantity, 0)
               - COALESCE(s.available_quantity, 0)) > 0.0001) AS stock_broken_count,
    (SELECT COUNT(*) FROM inv_stock_log l
     WHERE l.before_quantity IS NOT NULL AND l.after_quantity IS NOT NULL
       AND ABS(l.before_quantity + l.change_quantity - l.after_quantity) > 0.0001) AS stock_log_broken_count,
    (SELECT COUNT(*) FROM inv_stock_check c
     WHERE c.status = 'draft'
       AND c.create_time < DATE_SUB(NOW(), INTERVAL @stale_draft_days DAY)) AS stale_stock_check_draft_count,
    NOW() AS checked_time;
