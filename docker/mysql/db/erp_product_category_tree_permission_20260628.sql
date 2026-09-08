-- Split product-page category tree read permission from category management.
-- Safe to re-run.

SET @product_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'inv:product:list'
      AND menu_type = 'C'
    ORDER BY menu_id
    LIMIT 1
);

SET @category_tree_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'inv:category:tree'
    ORDER BY menu_id
    LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT
    4511, '商品分类树查询', COALESCE(@product_menu_id, 4001), 8, '', NULL, NULL,
    '', 1, 0, 'F', '0', '0', 'inv:category:tree', '#', 'system', NOW(), '商品管理页左侧分类树只读权限'
WHERE @category_tree_menu_id IS NULL
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
    remark = VALUES(remark),
    update_time = NOW();

SET @category_tree_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'inv:category:tree'
    ORDER BY menu_id
    LIMIT 1
);

UPDATE sys_menu
SET menu_name = '商品分类树查询',
    parent_id = COALESCE(@product_menu_id, parent_id),
    order_num = 8,
    path = '',
    component = NULL,
    query = NULL,
    route_name = '',
    is_frame = 1,
    is_cache = 0,
    menu_type = 'F',
    visible = '0',
    status = '0',
    icon = '#',
    remark = '商品管理页左侧分类树只读权限',
    update_time = NOW()
WHERE menu_id = @category_tree_menu_id;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @category_tree_menu_id
FROM sys_role_menu rm
JOIN sys_menu owned ON owned.menu_id = rm.menu_id
WHERE owned.perms = 'inv:product:list'
  AND @category_tree_menu_id IS NOT NULL;
