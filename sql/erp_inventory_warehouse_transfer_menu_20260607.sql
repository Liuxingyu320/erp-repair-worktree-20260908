-- Add warehouse-side transfer menu entries.
-- Warehouse managers should handle shipment from:
--   仓库管理 -> 调拨管理
-- without being granted the whole 进销存管理 menu.

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4460, '调拨管理', 4308, 6, 'transfer', 'inventory/transfer/index', NULL,
     'WarehouseTransfer', 1, 0, 'C', '0', '0', 'inv:transfer:list', '#', 'system', NOW(), '仓库管理调拨处理中入口'),
    (4465, '调拨记录', 4308, 9, 'transfer-records', 'inventory/transfer/records', NULL,
     'WarehouseTransferRecords', 1, 0, 'C', '0', '0', 'inv:transfer:records', '#', 'system', NOW(), '仓库管理调拨记录入口')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4461, '调拨查询', 4460, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:transfer:query', '#', 'system', NOW(), ''),
    (4462, '调拨出库', 4460, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:transfer:deliver', '#', 'system', NOW(), ''),
    (4463, '调拨导出', 4460, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:transfer:export', '#', 'system', NOW(), ''),
    (4466, '调拨记录查询', 4465, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:transfer:records:query', '#', 'system', NOW(), ''),
    (4467, '调拨记录导出', 4465, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:transfer:records:export', '#', 'system', NOW(), '')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_time = NOW();

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES
    (1, 4308),
    (1, 4460),
    (1, 4461),
    (1, 4462),
    (1, 4463),
    (1, 4465),
    (1, 4466),
    (1, 4467);
