SELECT 'admin_role_users' AS check_name,
       u.user_id,
       u.user_name,
       r.role_id,
       r.role_key
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.del_flag = '0'
  AND r.role_key = 'admin';

SELECT 'wildcard_permission_roles' AS check_name,
       r.role_id,
       r.role_name,
       r.role_key,
       m.menu_id,
       m.perms
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms = '*:*:*';
