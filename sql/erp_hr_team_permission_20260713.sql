-- “我的团队”精简接口菜单及店长能力授权（MySQL 5.7，可重复执行）。
-- 本功能不新增员工表字段，手机号仅由团队专用DTO完整返回。

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '我的团队功能开关', 'feature.hr.team.enabled', 'false', 'Y',
       'system', NOW(), '关闭时仅隐藏入口，不改变员工档案和手机号脱敏规则'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'feature.hr.team.enabled');

SET @hr_parent_id := (
    SELECT parent_id FROM sys_menu
    WHERE menu_type = 'C' AND perms = 'hr:employee:list'
    ORDER BY menu_id LIMIT 1
);
SET @hr_parent_id := COALESCE(@hr_parent_id, 0);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '我的团队', @hr_parent_id, 19, 'team', 'hr/team/index', NULL,
       'HrMyTeam', '1', '0', 'C', '0', '0', 'hr:team:list', 'peoples',
       'system', NOW(), '仅返回当前门店/直属员工的团队精简字段，手机号不脱敏'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:team:list');

SET @team_page_id := (
    SELECT menu_id FROM sys_menu WHERE perms = 'hr:team:list'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '查看当前门店团队', @team_page_id, 1, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:team:store', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @team_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:team:store');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '查看直属团队', @team_page_id, 2, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:team:direct', '#', 'system', NOW(), ''
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @team_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:team:direct');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT next_id, '查看团队健康证', @team_page_id, 3, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:team:healthCertificate', '#',
       'system', NOW(), '查看办理日、到期日及附件是否存在，不返回附件公开地址'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 next_id FROM sys_menu) sequence
WHERE @team_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:team:healthCertificate');

-- 管理员、人事沿用员工档案能力；店长角色模板获得门店团队能力。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, team_menu.menu_id
FROM sys_role_menu rm
JOIN sys_menu old_menu ON old_menu.menu_id = rm.menu_id
JOIN sys_menu team_menu ON team_menu.perms IN (
    'hr:team:list', 'hr:team:store', 'hr:team:direct', 'hr:team:healthCertificate'
)
WHERE old_menu.perms = 'hr:employee:list';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, team_menu.menu_id
FROM sys_role r
JOIN sys_menu team_menu ON team_menu.perms IN (
    'hr:team:list', 'hr:team:store', 'hr:team:healthCertificate'
)
WHERE r.status = '0' AND r.del_flag = '0'
  AND (
      lower(r.role_key) IN ('dz', 'store_manager', 'shop_manager')
      OR r.role_name LIKE '%店长%'
  );

-- 直属主管角色由现有员工主管关系配合此能力使用；不在代码中识别角色名。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, team_menu.menu_id
FROM sys_role r
JOIN sys_menu team_menu ON team_menu.perms IN ('hr:team:list', 'hr:team:direct')
WHERE r.status = '0' AND r.del_flag = '0'
  AND (r.role_name LIKE '%主管%' OR lower(r.role_key) LIKE '%supervisor%');

SELECT r.role_id, r.role_name, r.role_key,
       GROUP_CONCAT(m.perms ORDER BY m.perms SEPARATOR ',') team_permissions
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id AND m.perms LIKE 'hr:team:%'
GROUP BY r.role_id, r.role_name, r.role_key
ORDER BY r.role_id;
