-- 调拨收货差异台账、处理权限和待办开关（MySQL 5.7，可重复执行）。
-- 短少、拒收、残损或待质检均保留逐行快照，处理完成后才允许调拨闭环。

CREATE TABLE IF NOT EXISTS inv_transfer_discrepancy (
    discrepancy_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '差异单ID',
    transfer_id bigint(20) NOT NULL COMMENT '调拨单ID',
    shipment_id bigint(20) NOT NULL COMMENT '发货批次ID',
    discrepancy_no varchar(64) NOT NULL COMMENT '差异单号',
    status varchar(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PENDING_QC/RESOLVED',
    discrepancy_type varchar(64) NOT NULL COMMENT 'SHORTAGE/REJECTED/DAMAGED/MIXED/PENDING_QC',
    shipped_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '发货数量',
    accepted_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '实际接收数量',
    shortage_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '短少数量',
    rejected_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '拒收数量',
    damaged_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '残损数量',
    description varchar(1000) DEFAULT NULL COMMENT '收货差异说明',
    attachment_refs varchar(2000) DEFAULT NULL COMMENT '受控附件引用',
    responsible_party varchar(32) DEFAULT NULL COMMENT 'WAREHOUSE/STORE/CARRIER/OTHER',
    resolution_decision varchar(32) DEFAULT NULL COMMENT 'ACCEPT_LOSS/RESHIP/RETURN_TO_SOURCE/QC_RELEASE/QC_REJECT',
    resolution_note varchar(1000) DEFAULT NULL COMMENT '处理说明',
    handled_by_user_id bigint(20) DEFAULT NULL COMMENT '处理人用户ID',
    handled_by_name varchar(64) DEFAULT NULL COMMENT '处理人姓名快照',
    handled_time datetime DEFAULT NULL COMMENT '处理完成时间',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (discrepancy_id),
    UNIQUE KEY uk_inv_transfer_discrepancy_no (discrepancy_no),
    KEY idx_inv_transfer_discrepancy_transfer (transfer_id, status, discrepancy_id),
    KEY idx_inv_transfer_discrepancy_shipment (shipment_id, discrepancy_id),
    KEY idx_inv_transfer_discrepancy_status (status, create_time, discrepancy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨收货差异主表';

CREATE TABLE IF NOT EXISTS inv_transfer_discrepancy_detail (
    discrepancy_detail_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '差异明细ID',
    discrepancy_id bigint(20) NOT NULL COMMENT '差异单ID',
    transfer_detail_id bigint(20) NOT NULL COMMENT '调拨明细ID',
    shipment_detail_id bigint(20) NOT NULL COMMENT '发货批次明细ID',
    item_type varchar(32) NOT NULL COMMENT 'product/oe/gift',
    item_id bigint(20) NOT NULL COMMENT '物料ID',
    item_code varchar(64) DEFAULT NULL COMMENT '物料编码快照',
    item_name varchar(128) NOT NULL COMMENT '物料名称快照',
    shipped_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '发货数量',
    accepted_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '实际接收数量',
    shortage_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '短少数量',
    rejected_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '拒收数量',
    damaged_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '残损数量',
    resolution_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '处理数量',
    note varchar(1000) DEFAULT NULL COMMENT '明细说明',
    attachment_refs varchar(2000) DEFAULT NULL COMMENT '明细受控附件引用',
    PRIMARY KEY (discrepancy_detail_id),
    KEY idx_inv_transfer_discrepancy_detail (discrepancy_id, discrepancy_detail_id),
    KEY idx_inv_transfer_discrepancy_transfer_detail (transfer_detail_id),
    KEY idx_inv_transfer_discrepancy_shipment_detail (shipment_detail_id),
    KEY idx_inv_transfer_discrepancy_item (item_type, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨收货差异明细';

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '调拨差异闭环功能开关', 'feature.inventory.transfer-discrepancy.enabled', 'false', 'Y',
       'system', NOW(), '关闭新差异入口时仍须处理已经生成的OPEN/PENDING_QC差异'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'feature.inventory.transfer-discrepancy.enabled'
);

SET @transfer_page_id := (
    SELECT menu_id FROM sys_menu
    WHERE menu_type = 'C' AND perms = 'inv:transfer:list' AND status = '0'
    ORDER BY CASE WHEN component = 'inventory/transfer/index' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '处理调拨差异', @transfer_page_id, 20, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:transfer:discrepancy:handle', '#',
       'system', NOW(), '仅调拨目标组织收货职责人员可处理短少、拒收、残损和待质检'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @transfer_page_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'inv:transfer:discrepancy:handle'
  );

-- 调拨目标组织的收货角色获得差异处理权限；应用层继续校验目标组织边界。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT receive_role.role_id, discrepancy_menu.menu_id
FROM sys_role_menu receive_role
JOIN sys_menu receive_menu ON receive_menu.menu_id = receive_role.menu_id
JOIN sys_menu discrepancy_menu
  ON discrepancy_menu.perms = 'inv:transfer:discrepancy:handle'
WHERE receive_menu.perms = 'inv:transfer:receive';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE perms = 'inv:transfer:discrepancy:handle';

-- 上线后差异数量、处理状态和明细汇总对账。
SELECT d.discrepancy_id, d.discrepancy_no, d.transfer_id, d.shipment_id,
       d.status, d.discrepancy_type, d.shipped_quantity, d.accepted_quantity,
       d.shortage_quantity, d.rejected_quantity, d.damaged_quantity,
       COALESCE(SUM(dd.shipped_quantity), 0) detail_shipped_quantity,
       COALESCE(SUM(dd.accepted_quantity), 0) detail_accepted_quantity,
       COUNT(dd.discrepancy_detail_id) detail_count
FROM inv_transfer_discrepancy d
LEFT JOIN inv_transfer_discrepancy_detail dd ON dd.discrepancy_id = d.discrepancy_id
GROUP BY d.discrepancy_id, d.discrepancy_no, d.transfer_id, d.shipment_id,
         d.status, d.discrepancy_type, d.shipped_quantity, d.accepted_quantity,
         d.shortage_quantity, d.rejected_quantity, d.damaged_quantity
ORDER BY d.discrepancy_id DESC LIMIT 200;

SELECT transfer_id, COUNT(*) open_discrepancy_count
FROM inv_transfer_discrepancy
WHERE status IN ('OPEN', 'PENDING_QC')
GROUP BY transfer_id
ORDER BY transfer_id DESC;
