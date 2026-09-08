-- 调拨审批完整性只读预检（MySQL 8）
-- 本文件不包含 DDL/DML，可直接用于 dry-run。任何异常必须先人工确认，再使用独立修复模板。

-- 1. 当前状态允许发货、但审批关联不完整的调拨单。
WITH task_summary AS (
    SELECT instance_id,
           COUNT(*) AS task_count,
           SUM(status = 'approved') AS approved_count,
           SUM(status = 'pending') AS pending_count,
           SUM(status = 'rejected') AS rejected_count,
           SUM(status NOT IN ('approved', 'pending', 'rejected', 'transferred', 'skipped')) AS unknown_count,
           SUM(transfer_id IS NULL) AS missing_transfer_count,
           COUNT(DISTINCT transfer_id) AS task_transfer_count,
           MIN(transfer_id) AS task_transfer_id
    FROM inv_transfer_approval_task
    GROUP BY instance_id
), auto_approve AS (
    SELECT transfer_id, COUNT(*) AS auto_approve_count
    FROM inv_transfer_status_log
    WHERE action = 'auto_approve'
    GROUP BY transfer_id
)
SELECT o.transfer_id, o.order_no, o.status, o.approval_instance_id,
       i.transfer_id AS instance_transfer_id, i.status AS instance_status,
       i.approval_mode, i.required_count,
       COALESCE(ts.task_count, 0) AS task_count,
       COALESCE(ts.approved_count, 0) AS approved_task_count,
       COALESCE(ts.pending_count, 0) AS pending_task_count,
       COALESCE(ts.rejected_count, 0) AS rejected_task_count,
       COALESCE(aa.auto_approve_count, 0) AS auto_approve_count,
       CASE
           WHEN o.approval_instance_id IS NULL AND COALESCE(aa.auto_approve_count, 0) = 0
               THEN '实例缺失且无自动通过记录'
           WHEN o.approval_instance_id IS NULL THEN '自动通过记录存在但主单未关联实例（需服务校验）'
           WHEN i.instance_id IS NULL THEN '主单关联的审批实例不存在'
           WHEN i.transfer_id <> o.transfer_id THEN '审批实例串单'
           WHEN i.status <> 'approved' THEN CONCAT('审批实例未通过:', i.status)
           WHEN COALESCE(ts.task_transfer_count, 0) > 1
                OR (COALESCE(ts.task_count, 0) > 0 AND ts.task_transfer_id <> o.transfer_id)
               THEN '审批任务与调拨单不匹配'
           WHEN COALESCE(ts.rejected_count, 0) > 0 THEN '已通过实例仍包含驳回任务'
           WHEN COALESCE(ts.unknown_count, 0) > 0 THEN '审批任务存在未知状态'
           WHEN COALESCE(ts.task_count, 0) = 0 AND COALESCE(aa.auto_approve_count, 0) = 0
               THEN '缺少审批任务及自动通过记录'
           WHEN i.approval_mode = 'all_nodes' AND COALESCE(ts.pending_count, 0) > 0
               THEN '全节点审批仍有待处理任务'
           WHEN i.approval_mode = 'any_one' AND COALESCE(ts.approved_count, 0) = 0
               THEN '任一通过模式没有已通过任务'
           WHEN i.approval_mode = 'quorum'
                AND COALESCE(ts.approved_count, 0) < GREATEST(COALESCE(i.required_count, 0), 1)
               THEN '会签通过人数不足'
           WHEN o.approved_time IS NOT NULL AND o.approved_time < i.create_time
               THEN '主单审批时间早于实例创建时间'
           ELSE NULL
       END AS anomaly_reason
FROM inv_transfer_order o
LEFT JOIN inv_transfer_approval_instance i ON i.instance_id = o.approval_instance_id
LEFT JOIN task_summary ts ON ts.instance_id = i.instance_id
LEFT JOIN auto_approve aa ON aa.transfer_id = o.transfer_id
WHERE o.status = 'approved'
  AND (
      (o.approval_instance_id IS NULL AND COALESCE(aa.auto_approve_count, 0) = 0)
      OR (o.approval_instance_id IS NOT NULL AND i.instance_id IS NULL)
      OR (i.instance_id IS NOT NULL AND i.transfer_id <> o.transfer_id)
      OR (i.instance_id IS NOT NULL AND i.status <> 'approved')
      OR COALESCE(ts.task_transfer_count, 0) > 1
      OR (COALESCE(ts.task_count, 0) > 0 AND ts.task_transfer_id <> o.transfer_id)
      OR COALESCE(ts.rejected_count, 0) > 0
      OR COALESCE(ts.unknown_count, 0) > 0
      OR (COALESCE(ts.task_count, 0) = 0 AND COALESCE(aa.auto_approve_count, 0) = 0)
      OR (i.approval_mode = 'all_nodes' AND COALESCE(ts.pending_count, 0) > 0)
      OR (i.approval_mode = 'any_one' AND COALESCE(ts.approved_count, 0) = 0)
      OR (i.approval_mode = 'quorum'
          AND COALESCE(ts.approved_count, 0) < GREATEST(COALESCE(i.required_count, 0), 1))
      OR (o.approved_time IS NOT NULL AND o.approved_time < i.create_time)
  )
ORDER BY o.create_time, o.transfer_id;

-- 2. 已经发货/收货的历史单据中，存在同类审批异常的记录。
WITH task_summary AS (
    SELECT instance_id, COUNT(*) AS task_count,
           SUM(status = 'approved') AS approved_count,
           SUM(status = 'pending') AS pending_count,
           SUM(status = 'rejected') AS rejected_count,
           COUNT(DISTINCT transfer_id) AS task_transfer_count,
           MIN(transfer_id) AS task_transfer_id
    FROM inv_transfer_approval_task
    GROUP BY instance_id
), auto_approve AS (
    SELECT transfer_id, COUNT(*) AS auto_approve_count
    FROM inv_transfer_status_log
    WHERE action = 'auto_approve'
    GROUP BY transfer_id
)
SELECT o.transfer_id, o.order_no, o.status, o.delivered_time, o.received_time,
       o.approval_instance_id, i.transfer_id AS instance_transfer_id,
       i.status AS instance_status, COALESCE(ts.task_count, 0) AS task_count,
       COALESCE(ts.approved_count, 0) AS approved_task_count,
       COALESCE(ts.pending_count, 0) AS pending_task_count,
       COALESCE(ts.rejected_count, 0) AS rejected_task_count,
       COALESCE(aa.auto_approve_count, 0) AS auto_approve_count
FROM inv_transfer_order o
LEFT JOIN inv_transfer_approval_instance i ON i.instance_id = o.approval_instance_id
LEFT JOIN task_summary ts ON ts.instance_id = i.instance_id
LEFT JOIN auto_approve aa ON aa.transfer_id = o.transfer_id
WHERE (o.delivered_time IS NOT NULL
       OR o.status IN ('partial_delivered', 'delivered', 'partial_received', 'received', 'closed'))
  AND (
      (o.approval_instance_id IS NULL AND COALESCE(aa.auto_approve_count, 0) = 0)
      OR (o.approval_instance_id IS NOT NULL AND i.instance_id IS NULL)
      OR (i.instance_id IS NOT NULL AND (i.transfer_id <> o.transfer_id OR i.status <> 'approved'))
      OR COALESCE(ts.task_transfer_count, 0) > 1
      OR (COALESCE(ts.task_count, 0) > 0 AND ts.task_transfer_id <> o.transfer_id)
      OR COALESCE(ts.rejected_count, 0) > 0
      OR (COALESCE(ts.task_count, 0) = 0 AND COALESCE(aa.auto_approve_count, 0) = 0)
  )
ORDER BY COALESCE(o.delivered_time, o.create_time), o.transfer_id;

-- 3. 审批实例串单与审批任务串单；结果必须为 0 行。
SELECT o.transfer_id, o.order_no, o.approval_instance_id,
       i.transfer_id AS instance_transfer_id,
       t.task_id, t.transfer_id AS task_transfer_id
FROM inv_transfer_order o
LEFT JOIN inv_transfer_approval_instance i ON i.instance_id = o.approval_instance_id
LEFT JOIN inv_transfer_approval_task t ON t.instance_id = i.instance_id
WHERE o.approval_instance_id IS NOT NULL
  AND (i.instance_id IS NULL OR i.transfer_id <> o.transfer_id OR t.transfer_id <> o.transfer_id)
ORDER BY o.transfer_id, t.task_id;

-- 4. 汇总计数，便于发布审批记录。
SELECT
    SUM(o.status = 'approved') AS currently_shippable_count,
    SUM(o.delivered_time IS NOT NULL) AS historically_delivered_count,
    SUM(o.approval_instance_id IS NULL) AS missing_instance_link_count,
    SUM(o.approval_instance_id IS NOT NULL AND i.instance_id IS NULL) AS dangling_instance_link_count,
    SUM(i.instance_id IS NOT NULL AND i.transfer_id <> o.transfer_id) AS cross_linked_instance_count
FROM inv_transfer_order o
LEFT JOIN inv_transfer_approval_instance i ON i.instance_id = o.approval_instance_id;
