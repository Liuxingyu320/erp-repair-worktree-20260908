-- 人事员工档案编辑权限
-- 说明：仅创建按钮权限；是否授权给具体角色由系统管理员在角色授权中配置。

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT seq.next_id,
       '员工档案编辑',
       parent.menu_id,
       4,
       '#',
       '',
       NULL,
       '',
       '1',
       '0',
       'F',
       '0',
       '0',
       'hr:employee:edit',
       '#',
       'system',
       NOW(),
       '人事员工档案编辑按钮权限'
FROM (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'hr:employee:list'
    ORDER BY menu_id
    LIMIT 1
) parent
CROSS JOIN (
    SELECT COALESCE(MAX(menu_id), 0) + 1 AS next_id
    FROM sys_menu
) seq
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu existing
    WHERE existing.perms = 'hr:employee:edit'
);
