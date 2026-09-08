-- Multi-account/multi-store desktop audit remediation.
-- Adds the cost-view permission, grants it to admin/warehouse roles, removes
-- store-manager sensitive permissions, and renames the shop scope menu.

SET @product_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:product:list' AND menu_type IN ('C', 'F')
    ORDER BY menu_type, menu_id
    LIMIT 1
);

SET @cost_view_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:cost:view'
    LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 4510, '成本查看', COALESCE(@product_menu_id, 4001), 20, '', NULL, NULL,
       '', 1, 0, 'F', '0', '0', 'inv:cost:view', '#', 'system', NOW(),
       '商品、库存、报表等成本敏感字段查看权限'
WHERE @cost_view_menu_id IS NULL
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    update_time = NOW();

SET @cost_view_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:cost:view'
    LIMIT 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @cost_view_menu_id
FROM sys_role
WHERE @cost_view_menu_id IS NOT NULL
  AND del_flag = '0'
  AND (role_id = 1 OR role_key IN ('admin', 'ck'));

DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_role r ON r.role_id = rm.role_id
INNER JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key = 'dz'
	  AND m.perms IN (
	      'inv:cost:view',
	      'inv:stock:adjust',
	      'inv:transfer:deliver',
	      'inv:transfer:receive',
	      'inv:deliveryNotice:deliver',
	      'oa:laborContract:template:list',
	      'oa:laborContract:template:add',
	      'oa:laborContract:list',
	      'oa:laborContract:query',
	      'oa:laborContract:add',
	      'oa:laborContract:send',
	      'oa:laborContract:void',
	      'system:salary:list',
	      'system:salary:query',
	      'system:salary:role',
	      'system:salary:export',
	      'oa:salary:list',
	      'oa:salary:export',
	      'oa:salary:calculate',
	      'oa:salary:config'
	  );

UPDATE sys_menu
SET menu_name = '店铺授权',
    update_time = NOW()
WHERE menu_type = 'C'
  AND (
      component = 'system/shop/index'
      OR path = 'shop'
      OR menu_name = '店铺配置'
  );
