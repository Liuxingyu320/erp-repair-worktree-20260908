-- Restore OA purchase application permissions for mobile and desktop approval flows.
-- Safe to re-run.

UPDATE sys_menu
SET visible = '0',
    status = '0',
    component = CASE
        WHEN menu_type = 'C' AND (component IS NULL OR component = '') THEN 'oa/purchase/index'
        ELSE component
    END,
    update_time = NOW()
WHERE perms IN (
    'oa:purchase:list',
    'oa:purchase:query',
    'oa:purchase:add',
    'oa:purchase:export'
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT r.role_id, purchase_menu.menu_id
FROM sys_role r
JOIN sys_role_menu anchor_role_menu ON anchor_role_menu.role_id = r.role_id
JOIN sys_menu anchor_menu ON anchor_menu.menu_id = anchor_role_menu.menu_id
JOIN sys_menu purchase_menu ON purchase_menu.perms IN (
    'oa:purchase:list',
    'oa:purchase:query',
    'oa:purchase:add',
    'oa:purchase:export'
)
WHERE r.del_flag = '0'
  AND r.role_key IN ('admin', 'yyzj', 'zjl', 'qyyyzzj', 'yyjl', 'zdjl', 'dz')
  AND anchor_menu.perms IN (
      'oa:purchase:list',
      'oa:todo:list',
      'oa:done:list'
  );
