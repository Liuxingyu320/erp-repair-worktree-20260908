-- P0 UX/permission hardening for inventory routes and read-only dependencies.
-- Safe to re-run.

SET @product_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:product:list' AND menu_type IN ('C', 'F')
    ORDER BY menu_type = 'C' DESC, menu_id
    LIMIT 1
);

SET @category_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:category:list' AND menu_type IN ('C', 'F')
    ORDER BY menu_type = 'C' DESC, menu_id
    LIMIT 1
);

SET @product_query_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:product:query'
    ORDER BY menu_id
    LIMIT 1
);

SET @category_query_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:category:query'
    ORDER BY menu_id
    LIMIT 1
);

DELETE rm FROM sys_role_menu rm
WHERE rm.menu_id = 4492
  AND EXISTS (
      SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE perms = 'inv:product:query' AND menu_id <> 4492 LIMIT 1) existing_product_query
  );

DELETE FROM sys_menu
WHERE menu_id = 4492
  AND EXISTS (
      SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE perms = 'inv:product:query' AND menu_id <> 4492 LIMIT 1) existing_product_query
  );

DELETE rm FROM sys_role_menu rm
WHERE rm.menu_id = 4493
  AND EXISTS (
      SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE perms = 'inv:category:query' AND menu_id <> 4493 LIMIT 1) existing_category_query
  );

DELETE FROM sys_menu
WHERE menu_id = 4493
  AND EXISTS (
      SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE perms = 'inv:category:query' AND menu_id <> 4493 LIMIT 1) existing_category_query
  );

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4490, '库存变动日志', 4000, 31, 'stock-log', 'inventory/stock/log', NULL,
     'InventoryStockLog', 1, 0, 'C', '1', '0', 'inv:stock:log', '#', 'system', NOW(), '库存管理页隐藏跳转路由')
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
    (4491, '状态审计查询', 4470, 20, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:audit:list', '#', 'system', NOW(), '单据状态审计查询权限')
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
SELECT 4492, '商品详情查询', COALESCE(@product_menu_id, 4001), 7, '', NULL, NULL,
       '', 1, 0, 'F', '0', '0', 'inv:product:query', '#', 'system', NOW(), '业务页面选择商品依赖的只读权限'
WHERE @product_query_menu_id IS NULL
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
SELECT 4493, '分类详情查询', COALESCE(@category_menu_id, 4000), 7, '', NULL, NULL,
       '', 1, 0, 'F', '0', '0', 'inv:category:query', '#', 'system', NOW(), '库存分类筛选依赖的只读权限'
WHERE @category_query_menu_id IS NULL
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
    (1, 4490),
    (1, 4491);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4490
FROM sys_role_menu
WHERE menu_id IN (
    SELECT menu_id FROM sys_menu WHERE perms = 'inv:stock:log'
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4491
FROM sys_role_menu
WHERE menu_id IN (
    SELECT menu_id FROM sys_menu WHERE perms IN ('inv:report:list', 'inv:stock:list', 'inv:transfer:list')
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.menu_id
FROM sys_role_menu rm
JOIN sys_menu owned ON owned.menu_id = rm.menu_id
JOIN sys_menu m ON m.perms IN (
    'inv:product:list',
    'inv:product:query',
    'inv:category:list',
    'inv:category:query'
)
WHERE owned.perms IN (
    'inv:sales:list',
    'inv:purchase:list',
    'inv:transfer:list',
    'inv:stock:list',
    'inv:report:list'
);
