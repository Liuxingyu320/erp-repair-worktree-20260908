-- Hotfix for deployments that already have sys_user_profile but missed the
-- login-query profile columns introduced after the table was first created.

SET @erp_db = DATABASE();

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile' AND COLUMN_NAME = 'contract_type') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN contract_type varchar(32) DEFAULT NULL COMMENT ''合同类型'' AFTER contract_end_date',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile' AND COLUMN_NAME = 'social_type') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN social_type varchar(32) DEFAULT NULL COMMENT ''社保类型'' AFTER household_type',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
