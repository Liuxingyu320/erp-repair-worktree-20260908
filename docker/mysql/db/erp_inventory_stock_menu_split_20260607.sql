-- Split store inventory query and warehouse inventory management menu entries.
-- Store-side entry:
--   进销存管理 -> 库存管理 -> /inventory/stock?stockEntry=store
-- Warehouse-side entry:
--   仓库管理 -> 库存管理 -> /cangku/stock?stockEntry=warehouse

UPDATE sys_menu
SET menu_name = '库存管理',
    parent_id = 4000,
    order_num = 29,
    path = 'stock',
    component = 'inventory/stock/index',
    query = '{"stockEntry":"store"}',
    route_name = 'InventoryStock',
    perms = 'inv:stock:list',
    visible = '0',
    status = '0',
    update_time = NOW()
WHERE menu_id = 4040;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4450, '库存管理', 4308, 5, 'stock', 'inventory/stock/index', '{"stockEntry":"warehouse"}',
     'WarehouseStock', 1, 0, 'C', '0', '0', 'inv:stock:list', '#', 'system', NOW(), '仓库管理库存入口')
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
    (4451, '库存管理查询', 4450, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:stock:query', '#', 'system', NOW(), ''),
    (4452, '库存调整', 4450, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:stock:adjust', '#', 'system', NOW(), ''),
    (4453, '库存日志', 4450, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:stock:log', '#', 'system', NOW(), ''),
    (4454, '库存导出', 4450, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:stock:export', '#', 'system', NOW(), '')
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
    (1, 4450),
    (1, 4451),
    (1, 4452),
    (1, 4453),
    (1, 4454);
