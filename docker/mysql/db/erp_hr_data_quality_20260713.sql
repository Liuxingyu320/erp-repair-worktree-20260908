-- HR data-quality phase 1: dual completeness policy and read-only master-data governance.
-- Deploy after erp_user_hr_onboarding_20260710.sql. No employee or organization data is modified.

-- Feature flags. Existing values are preserved; false is only the safe default for a missing enforcement key.
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '人事完整度双口径', 'hr.completeness.policy.v2.enabled', 'true', 'Y',
       'system', NOW(), '启用业务必填完成率与资料覆盖度双口径'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'hr.completeness.policy.v2.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '人事主数据只读检查', 'hr.master-data.readiness.enabled', 'true', 'Y',
       'system', NOW(), '启用组织、岗位、岗位配置和字典路由只读检查'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'hr.master-data.readiness.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '人事主数据拦截', 'hr.master-data.enforce-on-enable', 'false', 'Y',
       'system', NOW(), '观察期必须保持false；本期不自动修复、不阻断业务'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'hr.master-data.enforce-on-enable'
);

-- Add hidden button permissions below the existing completeness page.
-- MAX(menu_id)+1 statements must run in one migration session during a maintenance window.
SET @hr_completeness_page_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE menu_type = 'C' AND perms = 'hr:completeness:list'
    ORDER BY menu_id
    LIMIT 1
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT next_id, '主数据问题查询', @hr_completeness_page_id, 20, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:masterData:list', '#', 'system', NOW(),
       '只读查看人事主数据治理队列'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 AS next_id FROM sys_menu) sequence
WHERE @hr_completeness_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:masterData:list');

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT next_id, '主数据问题导出', @hr_completeness_page_id, 21, '', NULL, NULL, '',
       '1', '0', 'F', '0', '0', 'hr:masterData:export', '#', 'system', NOW(),
       '导出只读人事主数据检查结果'
FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 AS next_id FROM sys_menu) sequence
WHERE @hr_completeness_page_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'hr:masterData:export');

-- Observation access is granted to system administrators only in phase 1.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, menu.menu_id
FROM sys_role role
JOIN sys_menu menu ON menu.perms IN ('hr:masterData:list', 'hr:masterData:export')
WHERE role.del_flag = '0'
  AND (role.role_id = 1 OR lower(role.role_key) IN ('admin', 'super-admin'))
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = role.role_id AND existing.menu_id = menu.menu_id
  );

-- Deployment assertions.
SELECT config_key, config_value
FROM sys_config
WHERE config_key IN (
    'hr.completeness.policy.v2.enabled',
    'hr.master-data.readiness.enabled',
    'hr.master-data.enforce-on-enable'
)
ORDER BY config_key;

SELECT menu_id, menu_name, perms
FROM sys_menu
WHERE perms IN ('hr:masterData:list', 'hr:masterData:export')
ORDER BY menu_id;
