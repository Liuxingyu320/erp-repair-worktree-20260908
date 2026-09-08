-- Safe Release B presentation rollback.
-- Never remove a switch that an administrator has enabled or otherwise edited.

DELETE FROM sys_config
WHERE config_key = 'feature.system.management-ux-v2.enabled'
  AND config_value = 'false'
  AND create_by = 'system';

SELECT COUNT(*) AS remaining_system_management_ux_v2_config
FROM sys_config
WHERE config_key = 'feature.system.management-ux-v2.enabled';
