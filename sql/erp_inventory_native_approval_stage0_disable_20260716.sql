-- Stage 0 fail-closed roll-forward for inventory native approval.
-- This migration is repeat-safe and changes only the switches for new submissions.
-- Existing NATIVE instances remain routable through their recorded approval_engine.

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT '盘点统一审批开关',
       'feature.inventory.stock-check-native-approval.enabled',
       'false', 'Y', 'system', NOW(),
       '阶段0向前修复：保持关闭，独立验收后由管理员手工开启'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.inventory.stock-check-native-approval.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT '调拨统一审批开关',
       'feature.inventory.transfer-native-approval.enabled',
       'false', 'Y', 'system', NOW(),
       '阶段0向前修复：保持关闭，独立验收后由管理员手工开启'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.inventory.transfer-native-approval.enabled'
);

UPDATE sys_config
SET config_value = 'false',
    update_by = 'system',
    update_time = NOW(),
    remark = '阶段0向前修复：保持关闭，独立验收后由管理员手工开启'
WHERE config_key IN (
    'feature.inventory.stock-check-native-approval.enabled',
    'feature.inventory.transfer-native-approval.enabled'
);

SELECT config_key, config_value
FROM sys_config
WHERE config_key IN (
    'feature.inventory.stock-check-native-approval.enabled',
    'feature.inventory.transfer-native-approval.enabled'
)
ORDER BY config_key;
