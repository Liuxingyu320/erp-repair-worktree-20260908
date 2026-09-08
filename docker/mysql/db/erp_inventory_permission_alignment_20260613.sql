-- Align inventory frontend/backend permissions with assignable sys_menu rows.
-- Existing roles that already own the corresponding page receive the missing
-- button permissions automatically.

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (4470, '报表中心', 4000, 95, 'report', 'inventory/report/index', NULL,
     'InventoryReport', 1, 0, 'C', '0', '0', 'inv:report:list', 'chart', 'system', NOW(), '进销存经营报表入口'),
    (4471, '报表查询', 4470, 1, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:report:list', '#', 'system', NOW(), ''),
    (4481, '商品导入', 4001, 6, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:product:import', '#', 'system', NOW(), ''),
    (4482, '采购提交', 4020, 6, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:purchase:submit', '#', 'system', NOW(), ''),
    (4483, '采购质检', 4020, 7, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:purchase:qc', '#', 'system', NOW(), ''),
    (4484, '采购导出', 4020, 8, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:purchase:export', '#', 'system', NOW(), ''),
    (4485, '销售提交', 4030, 6, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:sales:submit', '#', 'system', NOW(), ''),
    (4486, '销售导出', 4030, 7, '', NULL, NULL,
     '', 1, 0, 'F', '0', '0', 'inv:sales:export', '#', 'system', NOW(), '')
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
    (1, 4470),
    (1, 4471),
    (1, 4481),
    (1, 4482),
    (1, 4483),
    (1, 4484),
    (1, 4485),
    (1, 4486);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4470 FROM sys_role_menu WHERE menu_id = 4000;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4471 FROM sys_role_menu WHERE menu_id = 4000;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4481 FROM sys_role_menu WHERE menu_id = 4001;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4482 FROM sys_role_menu WHERE menu_id = 4020;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4483 FROM sys_role_menu WHERE menu_id = 4020;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4484 FROM sys_role_menu WHERE menu_id = 4020;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4485 FROM sys_role_menu WHERE menu_id = 4030;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 4486 FROM sys_role_menu WHERE menu_id = 4030;
