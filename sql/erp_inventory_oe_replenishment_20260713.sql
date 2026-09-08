-- 库存OE补货需求、需求-调拨关联及仓库处理权限（MySQL 5.7，可重复执行）。

CREATE TABLE IF NOT EXISTS inv_oe_replenishment_demand (
    demand_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '补货需求ID',
    source_service varchar(32) NOT NULL COMMENT '来源服务',
    repair_id bigint(20) NOT NULL COMMENT 'OA维修上报ID',
    event_version bigint(20) NOT NULL DEFAULT 1 COMMENT '来源事件版本',
    shop_dept_id bigint(20) NOT NULL COMMENT '需求门店ID',
    shop_dept_name varchar(128) DEFAULT NULL COMMENT '门店名称快照',
    oe_item_id bigint(20) NOT NULL COMMENT 'OE物料ID',
    oe_item_code varchar(64) DEFAULT NULL COMMENT 'OE编码快照',
    oe_item_name varchar(128) NOT NULL COMMENT 'OE名称快照',
    item_description varchar(1000) DEFAULT NULL COMMENT '产品描述快照',
    order_unit varchar(32) DEFAULT NULL COMMENT '订货单位快照',
    image_url varchar(1000) DEFAULT NULL COMMENT '图片兼容快照',
    reported_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '上报数量',
    planned_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '计划补货数量',
    shipped_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '已发数量汇总',
    received_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '已收数量汇总',
    fault_description varchar(1000) DEFAULT NULL COMMENT '故障说明快照',
    attachment_refs varchar(2000) DEFAULT NULL COMMENT '受控附件引用',
    applicant_id bigint(20) DEFAULT NULL COMMENT '上报人用户ID',
    applicant_name varchar(64) DEFAULT NULL COMMENT '上报人姓名快照',
    reported_time datetime DEFAULT NULL COMMENT 'OA上报时间',
    source_warehouse_dept_id bigint(20) DEFAULT NULL COMMENT '最近处理来源仓（仅展示）',
    status varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/TRANSFER_CREATED/PARTIAL_SHIPPED/SHIPPED/PARTIAL_RECEIVED/DISCREPANCY/COMPLETED/OUT_OF_STOCK/CLOSED',
    close_reason varchar(500) DEFAULT NULL COMMENT '缺货或关闭说明',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (demand_id),
    UNIQUE KEY uk_inv_oe_demand_source_repair (source_service, repair_id),
    KEY idx_inv_oe_demand_status (status, create_time, demand_id),
    KEY idx_inv_oe_demand_shop (shop_dept_id, status),
    KEY idx_inv_oe_demand_warehouse (source_warehouse_dept_id, status),
    KEY idx_inv_oe_demand_item (oe_item_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OE上报补货需求';

CREATE TABLE IF NOT EXISTS inv_oe_replenishment_transfer (
    link_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    demand_id bigint(20) NOT NULL COMMENT '补货需求ID',
    transfer_id bigint(20) NOT NULL COMMENT '调拨单ID',
    source_warehouse_dept_id bigint(20) NOT NULL COMMENT '来源仓库ID',
    planned_quantity decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '本次计划数量',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (link_id),
    UNIQUE KEY uk_inv_oe_replenishment_transfer (transfer_id),
    KEY idx_inv_oe_replenishment_demand (demand_id, link_id),
    KEY idx_inv_oe_replenishment_warehouse (source_warehouse_dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OE补货需求调拨关联';

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '库存OE补货功能开关', 'feature.inventory.oe-replenishment.enabled', 'false', 'Y',
       'system', NOW(), '关闭新建入口时保留在途需求和调拨继续处理'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'feature.inventory.oe-replenishment.enabled');

SET @warehouse_parent_id := (
    SELECT parent_id FROM sys_menu
    WHERE menu_type = 'C' AND perms = 'inv:transfer:list' AND status = '0'
    ORDER BY CASE WHEN visible = '0' THEN 0 ELSE 1 END, menu_id DESC LIMIT 1
);
SET @warehouse_parent_id := COALESCE(@warehouse_parent_id, 0);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, 'OE补货需求', @warehouse_parent_id, 9, 'oeReplenishment',
       'inventory/oeReplenishment/index', NULL, 'InvOeReplenishment',
       '1', '0', 'C', '0', '0', 'inv:oeReplenishment:list', 'shopping',
       'system', NOW(), '成功上报后由仓库选来源仓并生成现有调拨单'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:oeReplenishment:list');

SET @oe_replenishment_page_id := (
    SELECT menu_id FROM sys_menu WHERE perms = 'inv:oeReplenishment:list'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, 'OE补货详情', @oe_replenishment_page_id, 1, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:oeReplenishment:query', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @oe_replenishment_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:oeReplenishment:query');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '处理OE补货', @oe_replenishment_page_id, 2, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:oeReplenishment:handle', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @oe_replenishment_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:oeReplenishment:handle');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '关闭OE补货', @oe_replenishment_page_id, 3, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'inv:oeReplenishment:close', '#', 'system', NOW(),
       '仅仓库负责人和管理员可关闭，应用层继续校验负责人身份'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @oe_replenishment_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inv:oeReplenishment:close');

-- 只有同时具备调拨列表和发货能力的仓库角色自动获得处理权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, target_menu.menu_id
FROM sys_role_menu rm
JOIN sys_menu source_menu ON source_menu.menu_id = rm.menu_id
JOIN sys_menu target_menu ON target_menu.perms IN (
    'inv:oeReplenishment:list', 'inv:oeReplenishment:query', 'inv:oeReplenishment:handle'
)
WHERE source_menu.perms = 'inv:transfer:deliver'
  AND EXISTS (
      SELECT 1 FROM sys_role_menu list_rm
      JOIN sys_menu list_menu ON list_menu.menu_id = list_rm.menu_id
      WHERE list_rm.role_id = rm.role_id AND list_menu.perms = 'inv:transfer:list'
  );

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE perms IN ('inv:oeReplenishment:list', 'inv:oeReplenishment:query',
                'inv:oeReplenishment:handle', 'inv:oeReplenishment:close');

-- 上线后端到端对账。
SELECT d.demand_id, d.repair_id, d.shop_dept_id, d.oe_item_id,
       d.planned_quantity, d.shipped_quantity, d.received_quantity, d.status,
       COUNT(l.link_id) transfer_count
FROM inv_oe_replenishment_demand d
LEFT JOIN inv_oe_replenishment_transfer l ON l.demand_id = d.demand_id
GROUP BY d.demand_id, d.repair_id, d.shop_dept_id, d.oe_item_id,
         d.planned_quantity, d.shipped_quantity, d.received_quantity, d.status
ORDER BY d.demand_id DESC LIMIT 200;
