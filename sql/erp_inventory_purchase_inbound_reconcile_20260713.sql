-- 采购收货、质检与已收量只读对账（MySQL 8）
-- 本文件不写数据。结果由业务人员区分真实异常、历史迁移和测试数据后再单独制定修复单。

-- 1. 采购明细已收量必须等于未拒收的有效入库记录合计。
WITH inbound_quantity AS (
    SELECT purchase_detail_id,
           SUM(CASE WHEN COALESCE(qc_result, 'pending') <> 'rejected' THEN quantity ELSE 0 END) AS active_quantity,
           SUM(CASE WHEN qc_result = 'rejected' THEN quantity ELSE 0 END) AS rejected_quantity,
           SUM(quantity) AS historical_received_quantity,
           COUNT(*) AS inbound_count
    FROM inv_inbound_record
    WHERE purchase_detail_id IS NOT NULL
    GROUP BY purchase_detail_id
)
SELECT o.order_id, o.order_no, o.status, o.qc_status,
       d.detail_id, d.product_id, d.product_name,
       d.quantity AS ordered_quantity,
       COALESCE(d.received_quantity, 0) AS detail_received_quantity,
       COALESCE(iq.active_quantity, 0) AS active_inbound_quantity,
       COALESCE(iq.rejected_quantity, 0) AS rejected_inbound_quantity,
       COALESCE(iq.inbound_count, 0) AS inbound_count,
       COALESCE(d.received_quantity, 0) - COALESCE(iq.active_quantity, 0) AS quantity_difference
FROM inv_purchase_detail d
JOIN inv_purchase_order o ON o.order_id = d.order_id
LEFT JOIN inbound_quantity iq ON iq.purchase_detail_id = d.detail_id
WHERE COALESCE(d.received_quantity, 0) <> COALESCE(iq.active_quantity, 0)
ORDER BY o.order_id, d.detail_id;

-- 2. 主单标记待质检时，必须存在待检入库记录。
SELECT o.order_id, o.order_no, o.status, o.qc_status, o.shop_dept_id,
       COUNT(i.inbound_id) AS pending_inbound_count,
       COALESCE(SUM(i.quantity), 0) AS pending_quantity
FROM inv_purchase_order o
LEFT JOIN inv_inbound_record i
  ON i.purchase_order_id = o.order_id
 AND COALESCE(i.qc_result, 'pending') = 'pending'
WHERE o.qc_status = 'pending'
GROUP BY o.order_id, o.order_no, o.status, o.qc_status, o.shop_dept_id
HAVING COUNT(i.inbound_id) = 0
ORDER BY o.order_id;

-- 3. 已完成采购单不得存在未收数量或待检记录。
WITH detail_summary AS (
    SELECT order_id,
           SUM(quantity) AS ordered_quantity,
           SUM(COALESCE(received_quantity, 0)) AS received_quantity
    FROM inv_purchase_detail
    GROUP BY order_id
), pending_summary AS (
    SELECT purchase_order_id, COUNT(*) AS pending_count, SUM(quantity) AS pending_quantity
    FROM inv_inbound_record
    WHERE COALESCE(qc_result, 'pending') = 'pending'
    GROUP BY purchase_order_id
)
SELECT o.order_id, o.order_no, o.status, o.qc_status,
       COALESCE(ds.ordered_quantity, 0) AS ordered_quantity,
       COALESCE(ds.received_quantity, 0) AS received_quantity,
       COALESCE(ps.pending_count, 0) AS pending_inbound_count,
       COALESCE(ps.pending_quantity, 0) AS pending_quantity
FROM inv_purchase_order o
LEFT JOIN detail_summary ds ON ds.order_id = o.order_id
LEFT JOIN pending_summary ps ON ps.purchase_order_id = o.order_id
WHERE o.status = 'received'
  AND (COALESCE(ds.received_quantity, 0) <> COALESCE(ds.ordered_quantity, 0)
       OR COALESCE(ps.pending_count, 0) > 0)
ORDER BY o.order_id;

-- 4. 入库记录仓库必须与采购执行仓库及明细仓库一致。
SELECT i.inbound_id, i.purchase_order_id, o.order_no, i.purchase_detail_id,
       o.shop_dept_id AS order_warehouse_id,
       d.warehouse_id AS detail_warehouse_id,
       i.shop_dept_id AS inbound_shop_dept_id,
       i.warehouse_id AS inbound_warehouse_id,
       i.quantity, i.qc_result
FROM inv_inbound_record i
LEFT JOIN inv_purchase_order o ON o.order_id = i.purchase_order_id
LEFT JOIN inv_purchase_detail d ON d.detail_id = i.purchase_detail_id
WHERE o.order_id IS NULL
   OR d.detail_id IS NULL
   OR i.shop_dept_id <> o.shop_dept_id
   OR COALESCE(i.warehouse_id, i.shop_dept_id) <> o.shop_dept_id
   OR (d.warehouse_id IS NOT NULL AND d.warehouse_id <> COALESCE(i.warehouse_id, i.shop_dept_id))
ORDER BY i.inbound_id;

-- 5. 拒收记录必须已经从明细已收量中回退；以下结果与规则1相同但突出拒收历史。
WITH rejected AS (
    SELECT purchase_detail_id, SUM(quantity) AS rejected_quantity
    FROM inv_inbound_record
    WHERE qc_result = 'rejected' AND purchase_detail_id IS NOT NULL
    GROUP BY purchase_detail_id
), active AS (
    SELECT purchase_detail_id, SUM(quantity) AS active_quantity
    FROM inv_inbound_record
    WHERE COALESCE(qc_result, 'pending') <> 'rejected' AND purchase_detail_id IS NOT NULL
    GROUP BY purchase_detail_id
)
SELECT o.order_id, o.order_no, d.detail_id, d.product_name,
       COALESCE(d.received_quantity, 0) AS detail_received_quantity,
       COALESCE(a.active_quantity, 0) AS active_inbound_quantity,
       r.rejected_quantity,
       COALESCE(d.received_quantity, 0) - COALESCE(a.active_quantity, 0) AS rollback_difference
FROM rejected r
JOIN inv_purchase_detail d ON d.detail_id = r.purchase_detail_id
JOIN inv_purchase_order o ON o.order_id = d.order_id
LEFT JOIN active a ON a.purchase_detail_id = d.detail_id
WHERE COALESCE(d.received_quantity, 0) <> COALESCE(a.active_quantity, 0)
ORDER BY o.order_id, d.detail_id;

-- 6. 汇总计数，全部异常计数应为 0 后方可发布。
SELECT
    (SELECT COUNT(*) FROM inv_purchase_order WHERE qc_status = 'pending') AS pending_qc_order_count,
    (SELECT COUNT(*) FROM inv_inbound_record WHERE COALESCE(qc_result, 'pending') = 'pending') AS pending_inbound_count,
    (SELECT COUNT(*) FROM inv_inbound_record WHERE purchase_detail_id IS NULL) AS inbound_without_detail_count,
    (SELECT COUNT(*) FROM inv_inbound_record WHERE quantity <= 0) AS non_positive_inbound_count;
