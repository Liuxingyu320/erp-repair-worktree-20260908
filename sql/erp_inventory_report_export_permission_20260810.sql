-- ERP-NEW_2 additive report export permission.
-- This migration deliberately does not grant the permission to any role.

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT
    4472, '库存预警导出', report_menu.menu_id, 2, '', NULL, NULL, '',
    1, 0, 'F', '0', '0', 'inv:report:export', '#', 'system', NOW(),
    '库存报表预警安全导出；按角色单独授权'
FROM sys_menu report_menu
WHERE report_menu.perms = 'inv:report:list'
  AND report_menu.menu_type = 'C'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing_permission
      WHERE existing_permission.perms = 'inv:report:export'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing_id
      WHERE existing_id.menu_id = 4472
  )
LIMIT 1;
