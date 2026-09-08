-- 新增业务功能每日只读对账（MySQL 5.7/8.0）。
-- 返回的异常明细均应为空；状态摘要用于监控趋势，不会修改任何数据。

-- 1. 成功上报必须且只能有一条可靠发件箱事实。
SELECT 'OA_SUBMITTED_OUTBOX_CARDINALITY' AS issue_code,
       r.repair_id, r.shop_dept_id, r.status AS repair_status,
       COALESCE(o.outbox_count, 0) AS outbox_count,
       o.outbox_ids
FROM oa_fixed_asset_repair r
LEFT JOIN (
    SELECT repair_id, COUNT(*) AS outbox_count,
           GROUP_CONCAT(outbox_id ORDER BY outbox_id) AS outbox_ids
    FROM oa_fixed_asset_replenishment_outbox
    GROUP BY repair_id
) o ON o.repair_id = r.repair_id
WHERE r.status = 'submitted'
  AND COALESCE(o.outbox_count, 0) <> 1
ORDER BY r.repair_id;

-- 2. 发件箱状态、源业务和投递目标必须一致。
SELECT 'OA_OUTBOX_SOURCE_OR_STATE_INVALID' AS issue_code,
       o.outbox_id, o.repair_id, o.status AS outbox_status,
       r.status AS repair_status, o.retry_count, o.target_demand_id,
       o.sent_time, o.last_error_code
FROM oa_fixed_asset_replenishment_outbox o
LEFT JOIN oa_fixed_asset_repair r ON r.repair_id = o.repair_id
WHERE r.repair_id IS NULL
   OR r.status <> 'submitted'
   OR o.status NOT IN ('PENDING', 'SENDING', 'RETRY', 'SENT', 'DEAD')
   OR o.retry_count < 0
   OR (o.status = 'SENT' AND (o.target_demand_id IS NULL OR o.sent_time IS NULL))
   OR (o.status <> 'SENT' AND o.sent_time IS NOT NULL)
ORDER BY o.outbox_id;

SELECT 'OA_SENT_TARGET_DEMAND_MISMATCH' AS issue_code,
       o.outbox_id, o.repair_id, o.event_version,
       o.target_demand_id, d.demand_id, d.source_service,
       d.repair_id AS demand_repair_id,
       d.event_version AS demand_event_version, d.status AS demand_status
FROM oa_fixed_asset_replenishment_outbox o
LEFT JOIN inv_oe_replenishment_demand d
       ON d.demand_id = o.target_demand_id
WHERE o.status = 'SENT'
  AND (d.demand_id IS NULL
       OR d.source_service <> 'erp-oa'
       OR d.repair_id <> o.repair_id
       OR d.event_version <> o.event_version)
ORDER BY o.outbox_id;

-- 超时任务是运维告警：SENDING 5 分钟，到期 PENDING/RETRY 15 分钟，DEAD 立即告警。
SELECT 'OA_OUTBOX_STALLED_OR_DEAD' AS issue_code,
       outbox_id, repair_id, status, retry_count, next_retry_time,
       TIMESTAMPDIFF(SECOND, create_time, NOW()) AS age_seconds,
       TIMESTAMPDIFF(SECOND, update_time, NOW()) AS state_age_seconds,
       last_http_status, last_error_code
FROM oa_fixed_asset_replenishment_outbox
WHERE status = 'DEAD'
   OR (status = 'SENDING' AND update_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
   OR (status IN ('PENDING', 'RETRY')
       AND COALESCE(next_retry_time, create_time) <= DATE_SUB(NOW(), INTERVAL 15 MINUTE))
ORDER BY CASE status WHEN 'DEAD' THEN 0 WHEN 'SENDING' THEN 1 ELSE 2 END,
         COALESCE(next_retry_time, create_time), outbox_id;

SELECT 'OA_OUTBOX_STATUS_SUMMARY' AS summary_code,
       status, COUNT(*) AS row_count,
       MIN(COALESCE(next_retry_time, create_time)) AS oldest_waiting_time,
       MAX(retry_count) AS max_retry_count
FROM oa_fixed_asset_replenishment_outbox
GROUP BY status
ORDER BY status;

-- 3. OE需求数量守恒：计划 >= 已关联计划 >= 实际已发 >= 实际已收。
--    同时校验需求表的已发/已收汇总与发货事实一致。
SELECT 'OE_DEMAND_QUANTITY_CONSERVATION' AS issue_code,
       d.demand_id, d.repair_id, d.status,
       d.reported_quantity, d.planned_quantity,
       COALESCE(l.linked_planned_quantity, 0) AS linked_planned_quantity,
       d.shipped_quantity AS demand_shipped_quantity,
       COALESCE(s.actual_shipped_quantity, 0) AS actual_shipped_quantity,
       d.received_quantity AS demand_received_quantity,
       COALESCE(s.actual_received_quantity, 0) AS actual_received_quantity
FROM inv_oe_replenishment_demand d
LEFT JOIN (
    SELECT demand_id, SUM(planned_quantity) AS linked_planned_quantity
    FROM inv_oe_replenishment_transfer
    GROUP BY demand_id
) l ON l.demand_id = d.demand_id
LEFT JOIN (
    SELECT link.demand_id,
           COALESCE(SUM(sd.shipped_quantity), 0) AS actual_shipped_quantity,
           COALESCE(SUM(sd.received_quantity), 0) AS actual_received_quantity
    FROM inv_oe_replenishment_transfer link
    LEFT JOIN inv_transfer_shipment_detail sd
           ON sd.transfer_id = link.transfer_id
    GROUP BY link.demand_id
) s ON s.demand_id = d.demand_id
WHERE d.reported_quantity < 0 OR d.planned_quantity < 0
   OR d.shipped_quantity < 0 OR d.received_quantity < 0
   OR d.planned_quantity + 0.0001 < COALESCE(l.linked_planned_quantity, 0)
   OR COALESCE(l.linked_planned_quantity, 0) + 0.0001
      < COALESCE(s.actual_shipped_quantity, 0)
   OR COALESCE(s.actual_shipped_quantity, 0) + 0.0001
      < COALESCE(s.actual_received_quantity, 0)
   OR ABS(d.shipped_quantity - COALESCE(s.actual_shipped_quantity, 0)) > 0.0001
   OR ABS(d.received_quantity - COALESCE(s.actual_received_quantity, 0)) > 0.0001
ORDER BY d.demand_id;

SELECT 'OE_DEMAND_SOURCE_OUTBOX_MISMATCH' AS issue_code,
       d.demand_id, d.source_service, d.repair_id, d.event_version,
       o.outbox_id, o.status AS outbox_status, o.target_demand_id
FROM inv_oe_replenishment_demand d
LEFT JOIN oa_fixed_asset_replenishment_outbox o
       ON o.target_demand_id = d.demand_id AND o.status = 'SENT'
WHERE d.source_service = 'erp-oa'
  AND (o.outbox_id IS NULL OR o.repair_id <> d.repair_id
       OR o.event_version <> d.event_version)
ORDER BY d.demand_id;

-- 非终态需求超过24小时没有任何更新，需要仓库负责人介入。
SELECT 'OE_DEMAND_STALLED_24H' AS issue_code,
       demand_id, repair_id, shop_dept_id, source_warehouse_dept_id,
       status, reported_quantity, planned_quantity, shipped_quantity,
       received_quantity, create_time, update_time,
       TIMESTAMPDIFF(HOUR, COALESCE(update_time, create_time), NOW())
           AS idle_hours
FROM inv_oe_replenishment_demand
WHERE status IN ('PENDING', 'TRANSFER_CREATED', 'PARTIAL_SHIPPED',
                 'SHIPPED', 'PARTIAL_RECEIVED', 'DISCREPANCY')
  AND COALESCE(update_time, create_time)
      < DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY COALESCE(update_time, create_time), demand_id;

SELECT 'OE_DEMAND_STATUS_SUMMARY' AS summary_code,
       status, COUNT(*) AS row_count, MIN(create_time) AS oldest_create_time,
       SUM(planned_quantity) AS planned_quantity,
       SUM(shipped_quantity) AS shipped_quantity,
       SUM(received_quantity) AS received_quantity
FROM inv_oe_replenishment_demand
GROUP BY status
ORDER BY status;

-- 4. 门店返仓方向和原因必须合法，并与现有调拨审批实例一致。
SELECT 'STORE_RETURN_DIRECTION_OR_APPROVAL_INVALID' AS issue_code,
       o.transfer_id, o.order_no, o.status, o.from_dept_id,
       f.dept_type AS from_dept_type, o.to_dept_id,
       t.dept_type AS to_dept_type, o.return_reason_code,
       o.approval_instance_id, a.status AS approval_status
FROM inv_transfer_order o
LEFT JOIN sys_dept f ON f.dept_id = o.from_dept_id
LEFT JOIN sys_dept t ON t.dept_id = o.to_dept_id
LEFT JOIN inv_transfer_approval_instance a
       ON a.instance_id = o.approval_instance_id
WHERE o.transfer_type = 'store_return'
  AND (UPPER(COALESCE(f.dept_type, '')) <> 'STORE'
       OR UPPER(COALESCE(t.dept_type, '')) <> 'WAREHOUSE'
       OR NULLIF(TRIM(o.return_reason_code), '') IS NULL
       OR (o.status NOT IN ('draft', 'cancelled')
           AND (a.instance_id IS NULL OR a.transfer_id <> o.transfer_id)))
ORDER BY o.transfer_id;

-- 发货数 = 已验收入库数 + 所有已留痕差异数（OPEN/PENDING_QC/RESOLVED均保留）。
SELECT 'STORE_RETURN_SHIPMENT_QUANTITY_CONSERVATION' AS issue_code,
       o.transfer_id, o.order_no, sd.shipment_id, sh.status AS shipment_status,
       sd.shipment_detail_id,
       sd.item_type, sd.item_id, sd.item_name,
       sd.shipped_quantity, sd.received_quantity,
       COALESCE(x.discrepancy_quantity, 0) AS discrepancy_quantity,
       sd.shipped_quantity - sd.received_quantity
           - COALESCE(x.discrepancy_quantity, 0) AS formula_diff
FROM inv_transfer_order o
JOIN inv_transfer_shipment_detail sd ON sd.transfer_id = o.transfer_id
JOIN inv_transfer_shipment sh ON sh.shipment_id = sd.shipment_id
LEFT JOIN (
    SELECT shipment_detail_id,
           SUM(shortage_quantity + rejected_quantity + damaged_quantity)
               AS discrepancy_quantity
    FROM inv_transfer_discrepancy_detail
    GROUP BY shipment_detail_id
) x ON x.shipment_detail_id = sd.shipment_detail_id
WHERE o.transfer_type = 'store_return'
  AND sh.status IN ('received', 'discrepancy', 'abnormal')
  AND (sd.shipped_quantity < 0 OR sd.received_quantity < 0
       OR ABS(sd.shipped_quantity - sd.received_quantity
              - COALESCE(x.discrepancy_quantity, 0)) > 0.0001)
ORDER BY o.transfer_id, sd.shipment_id, sd.shipment_detail_id;

-- 5. 差异主从表及每条明细的数量公式必须守恒。
SELECT 'TRANSFER_DISCREPANCY_DETAIL_INVALID' AS issue_code,
       d.discrepancy_id, d.discrepancy_detail_id, d.shipment_detail_id,
       d.shipped_quantity, d.accepted_quantity, d.shortage_quantity,
       d.rejected_quantity, d.damaged_quantity, d.resolution_quantity,
       d.shipped_quantity - d.accepted_quantity - d.shortage_quantity
           - d.rejected_quantity - d.damaged_quantity AS formula_diff
FROM inv_transfer_discrepancy_detail d
WHERE d.shipped_quantity < 0 OR d.accepted_quantity < 0
   OR d.shortage_quantity < 0 OR d.rejected_quantity < 0
   OR d.damaged_quantity < 0 OR d.resolution_quantity < 0
   OR ABS(d.shipped_quantity - d.accepted_quantity - d.shortage_quantity
          - d.rejected_quantity - d.damaged_quantity) > 0.0001
ORDER BY d.discrepancy_id, d.discrepancy_detail_id;

SELECT 'TRANSFER_DISCREPANCY_MASTER_DETAIL_MISMATCH' AS issue_code,
       m.discrepancy_id, m.discrepancy_no, m.status, m.discrepancy_type,
       m.shipped_quantity, COALESCE(d.shipped_quantity, 0) AS detail_shipped,
       m.accepted_quantity, COALESCE(d.accepted_quantity, 0) AS detail_accepted,
       m.shortage_quantity, COALESCE(d.shortage_quantity, 0) AS detail_shortage,
       m.rejected_quantity, COALESCE(d.rejected_quantity, 0) AS detail_rejected,
       m.damaged_quantity, COALESCE(d.damaged_quantity, 0) AS detail_damaged,
       COALESCE(d.detail_count, 0) AS detail_count
FROM inv_transfer_discrepancy m
LEFT JOIN (
    SELECT discrepancy_id, COUNT(*) AS detail_count,
           SUM(shipped_quantity) AS shipped_quantity,
           SUM(accepted_quantity) AS accepted_quantity,
           SUM(shortage_quantity) AS shortage_quantity,
           SUM(rejected_quantity) AS rejected_quantity,
           SUM(damaged_quantity) AS damaged_quantity
    FROM inv_transfer_discrepancy_detail
    GROUP BY discrepancy_id
) d ON d.discrepancy_id = m.discrepancy_id
WHERE m.status NOT IN ('OPEN', 'PENDING_QC', 'RESOLVED')
   OR COALESCE(d.detail_count, 0) = 0
   OR ABS(m.shipped_quantity - COALESCE(d.shipped_quantity, 0)) > 0.0001
   OR ABS(m.accepted_quantity - COALESCE(d.accepted_quantity, 0)) > 0.0001
   OR ABS(m.shortage_quantity - COALESCE(d.shortage_quantity, 0)) > 0.0001
   OR ABS(m.rejected_quantity - COALESCE(d.rejected_quantity, 0)) > 0.0001
   OR ABS(m.damaged_quantity - COALESCE(d.damaged_quantity, 0)) > 0.0001
ORDER BY m.discrepancy_id;

-- OPEN/PENDING_QC从创建起超过24小时未结，必须进入运维工单。
SELECT 'TRANSFER_DISCREPANCY_OVERDUE_24H' AS issue_code,
       discrepancy_id, discrepancy_no, transfer_id, shipment_id,
       status, discrepancy_type, create_time, update_time,
       TIMESTAMPDIFF(HOUR, create_time, NOW()) AS open_hours
FROM inv_transfer_discrepancy
WHERE status IN ('OPEN', 'PENDING_QC')
  AND create_time < DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY create_time, discrepancy_id;

SELECT 'TRANSFER_DISCREPANCY_STATUS_SUMMARY' AS summary_code,
       status, discrepancy_type, COUNT(*) AS row_count,
       MIN(create_time) AS oldest_create_time,
       SUM(shortage_quantity + rejected_quantity + damaged_quantity)
           AS affected_quantity
FROM inv_transfer_discrepancy
GROUP BY status, discrepancy_type
ORDER BY status, discrepancy_type;

-- 6. 客户服务卡、服务记录和审计日志必须与客户归属同店。
SELECT 'CUSTOMER_SERVICE_PROFILE_CARDINALITY' AS issue_code,
       c.customer_id, c.customer_name, c.shop_dept_id,
       COUNT(p.customer_id) AS profile_count
FROM inv_customer c
LEFT JOIN inv_customer_service_profile p ON p.customer_id = c.customer_id
GROUP BY c.customer_id, c.customer_name, c.shop_dept_id
HAVING COUNT(p.customer_id) <> 1
ORDER BY c.customer_id;

SELECT 'CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH' AS issue_code,
       r.record_id, r.customer_id, c.customer_name,
       r.shop_dept_id AS record_shop_dept_id,
       c.shop_dept_id AS customer_shop_dept_id,
       r.service_user_id, r.service_date, r.source_client
FROM inv_customer_service_record r
LEFT JOIN inv_customer c ON c.customer_id = r.customer_id
WHERE c.customer_id IS NULL OR r.shop_dept_id <> c.shop_dept_id
   OR r.source_client NOT IN ('WEB', 'MOBILE', 'IMPORT')
ORDER BY r.record_id;

SELECT 'CUSTOMER_SERVICE_AUDIT_SCOPE_MISMATCH' AS issue_code,
       l.log_id, l.customer_id, c.customer_name,
       l.shop_dept_id AS audit_shop_dept_id,
       c.shop_dept_id AS customer_shop_dept_id,
       l.change_type, l.operator_user_id, l.source_client, l.create_time
FROM inv_customer_service_change_log l
LEFT JOIN inv_customer c ON c.customer_id = l.customer_id
WHERE c.customer_id IS NULL OR l.shop_dept_id <> c.shop_dept_id
   OR l.source_client NOT IN ('WEB', 'MOBILE', 'IMPORT')
   OR l.change_type NOT IN ('CREATE', 'UPDATE', 'ADD_RECORD', 'ARCHIVE')
ORDER BY l.log_id;

SELECT 'CUSTOMER_SERVICE_RECORD_AUDIT_MISSING' AS issue_code,
       r.record_id, r.customer_id, r.shop_dept_id, r.request_key,
       r.service_user_id, r.service_date, r.create_time
FROM inv_customer_service_record r
LEFT JOIN inv_customer_service_change_log l
       ON l.customer_id = r.customer_id
      AND l.shop_dept_id = r.shop_dept_id
      AND l.request_key = r.request_key
      AND l.change_type = 'ADD_RECORD'
WHERE l.log_id IS NULL
ORDER BY r.record_id;

SELECT 'CUSTOMER_SERVICE_ACTIVITY_SUMMARY' AS summary_code,
       (SELECT COUNT(*) FROM inv_customer WHERE status = '0') AS active_card_count,
       (SELECT COUNT(*) FROM inv_customer_service_record
        WHERE create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)) AS records_last_24h,
       (SELECT COUNT(*) FROM inv_customer_service_change_log
        WHERE create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)) AS audits_last_24h,
       (SELECT COUNT(*) FROM inv_customer_service_change_log
        WHERE source_client = 'MOBILE'
          AND create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)) AS mobile_writes_last_24h;

-- 7. 当前健康证必须是未删除的已审核记录，且每个员工最多一条。
SELECT 'HEALTH_CERTIFICATE_CURRENT_INVALID' AS issue_code,
       certificate_id, user_id, review_status, current_flag, del_flag,
       issued_date, valid_from, expires_on, reviewed_by_user_id, reviewed_time
FROM hr_employee_health_certificate
WHERE (current_flag = 'Y'
       AND (review_status <> 'APPROVED' OR del_flag <> '0'
            OR reviewed_by_user_id IS NULL OR reviewed_time IS NULL))
   OR issued_date > expires_on
   OR (valid_from IS NOT NULL AND valid_from > expires_on)
   OR review_status NOT IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')
ORDER BY user_id, certificate_id;

SELECT 'HEALTH_CERTIFICATE_CURRENT_DUPLICATE' AS issue_code,
       user_id, COUNT(*) AS current_count,
       GROUP_CONCAT(certificate_id ORDER BY certificate_id) AS certificate_ids
FROM hr_employee_health_certificate
WHERE current_flag = 'Y'
GROUP BY user_id
HAVING COUNT(*) > 1
ORDER BY user_id;

SELECT 'HEALTH_CERTIFICATE_PENDING_STALE' AS issue_code,
       certificate_id, user_id, dept_id_snapshot, create_time,
       TIMESTAMPDIFF(HOUR, create_time, NOW()) AS waiting_hours
FROM hr_employee_health_certificate
WHERE del_flag = '0' AND review_status = 'PENDING_REVIEW'
  AND create_time < DATE_SUB(NOW(), INTERVAL 72 HOUR)
ORDER BY create_time, certificate_id;

SELECT 'HEALTH_CERTIFICATE_EXPIRY_SUMMARY' AS summary_code,
       SUM(CASE WHEN expires_on < CURDATE() THEN 1 ELSE 0 END) AS expired_count,
       SUM(CASE WHEN expires_on BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)
                THEN 1 ELSE 0 END) AS expires_in_7_days,
       SUM(CASE WHEN expires_on > DATE_ADD(CURDATE(), INTERVAL 7 DAY)
                 AND expires_on <= DATE_ADD(CURDATE(), INTERVAL 15 DAY)
                THEN 1 ELSE 0 END) AS expires_in_8_to_15_days,
       SUM(CASE WHEN expires_on > DATE_ADD(CURDATE(), INTERVAL 15 DAY)
                 AND expires_on <= DATE_ADD(CURDATE(), INTERVAL 30 DAY)
                THEN 1 ELSE 0 END) AS expires_in_16_to_30_days,
       MIN(expires_on) AS earliest_expiry
FROM hr_employee_health_certificate
WHERE current_flag = 'Y' AND review_status = 'APPROVED' AND del_flag = '0';

-- 8. 一行告警摘要；所有 issue_count 都应为 0。
SELECT 'NEW_BUSINESS_RECONCILIATION_SUMMARY' AS summary_code,
       (SELECT COUNT(*)
        FROM oa_fixed_asset_repair r
        LEFT JOIN (SELECT repair_id, COUNT(*) AS row_count
                   FROM oa_fixed_asset_replenishment_outbox GROUP BY repair_id) o
               ON o.repair_id = r.repair_id
        WHERE r.status = 'submitted' AND COALESCE(o.row_count, 0) <> 1)
           AS oa_outbox_cardinality_issue_count,
       (SELECT COUNT(*) FROM oa_fixed_asset_replenishment_outbox
        WHERE status = 'DEAD'
           OR (status = 'SENDING' AND update_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
           OR (status IN ('PENDING', 'RETRY')
               AND COALESCE(next_retry_time, create_time)
                   <= DATE_SUB(NOW(), INTERVAL 15 MINUTE)))
           AS oa_outbox_ops_issue_count,
       (SELECT COUNT(*) FROM inv_oe_replenishment_demand
        WHERE reported_quantity < 0 OR planned_quantity < 0
           OR shipped_quantity < 0 OR received_quantity < 0
           OR planned_quantity + 0.0001 < shipped_quantity
           OR shipped_quantity + 0.0001 < received_quantity)
           AS oe_quantity_issue_count,
       (SELECT COUNT(*) FROM inv_transfer_discrepancy
        WHERE status IN ('OPEN', 'PENDING_QC')
          AND create_time < DATE_SUB(NOW(), INTERVAL 24 HOUR))
           AS overdue_transfer_discrepancy_count,
       (SELECT COUNT(*) FROM inv_oe_replenishment_demand
        WHERE status IN ('PENDING', 'TRANSFER_CREATED', 'PARTIAL_SHIPPED',
                         'SHIPPED', 'PARTIAL_RECEIVED', 'DISCREPANCY')
          AND COALESCE(update_time, create_time)
              < DATE_SUB(NOW(), INTERVAL 24 HOUR))
           AS stalled_oe_demand_count,
       (SELECT COUNT(*)
        FROM inv_customer_service_record r
        LEFT JOIN inv_customer c ON c.customer_id = r.customer_id
        WHERE c.customer_id IS NULL OR r.shop_dept_id <> c.shop_dept_id)
           AS customer_scope_issue_count,
       (SELECT COUNT(*) FROM hr_employee_health_certificate
        WHERE (current_flag = 'Y'
               AND (review_status <> 'APPROVED' OR del_flag <> '0'))
           OR issued_date > expires_on
           OR (valid_from IS NOT NULL AND valid_from > expires_on))
           AS health_certificate_issue_count,
       NOW() AS checked_time;
