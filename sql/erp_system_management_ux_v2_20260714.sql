-- System-management UX v2 rollout switch (MySQL 5.7/8.0, idempotent).
-- The new backend safety contracts are always active; this switch only controls
-- the richer user-management and organization-authorization presentation.

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '系统管理易用性 V2', 'feature.system.management-ux-v2.enabled', 'false', 'Y',
       'system', NOW(), 'Release B 灰度开关；关闭后仍保留安全 DTO、预览和并发冲突保护'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.system.management-ux-v2.enabled'
);

SELECT config_key, config_value
FROM sys_config
WHERE config_key = 'feature.system.management-ux-v2.enabled';
