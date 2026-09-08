-- Add the offboarding-only permission and frozen OA package fields on MySQL 5.7.
SET @erp_db = DATABASE();

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_menu') > 0,
    'INSERT INTO sys_menu
        (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
         is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
     SELECT sequence.next_id, ''确认离职'', parent.menu_id, 21, ''#'', '''', NULL, '''',
            ''1'', ''0'', ''F'', ''0'', ''0'', ''hr:employee:offboard'', ''#'',
            ''system'', NOW(), ''唯一HR确认员工离职''
       FROM (SELECT menu_id FROM sys_menu WHERE perms = ''hr:employee:list'' ORDER BY menu_id LIMIT 1) parent
       CROSS JOIN (SELECT COALESCE(MAX(menu_id), 0) + 1 AS next_id FROM sys_menu) sequence
      WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = ''hr:employee:offboard'')',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_offboarding;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_offboarding()
BEGIN
    CALL sync_sign_hr_permissions_with_transfer();

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
    SELECT state.managed_role_id, offboard_menu.menu_id
      FROM sys_sign_hr_state state
      JOIN sys_menu offboard_menu
        ON offboard_menu.perms = 'hr:employee:offboard'
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL;

    INSERT INTO sys_sign_hr_menu_grant
        (role_id, menu_id, hr_user_id, created_time)
    SELECT state.managed_role_id, offboard_menu.menu_id, state.hr_user_id, NOW()
      FROM sys_sign_hr_state state
      JOIN sys_menu offboard_menu
        ON offboard_menu.perms = 'hr:employee:offboard'
      JOIN sys_role_menu role_menu
        ON role_menu.role_id = state.managed_role_id
       AND role_menu.menu_id = offboard_menu.menu_id
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL
    ON DUPLICATE KEY UPDATE
        hr_user_id = VALUES(hr_user_id),
        created_time = VALUES(created_time);
END$$
DELIMITER ;

CALL sync_sign_hr_permissions_with_offboarding();

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'offboarding_type') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN offboarding_type varchar(32) DEFAULT NULL COMMENT ''离职类型'' AFTER leave_reason',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'salary_settlement_status') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN salary_settlement_status varchar(16) DEFAULT NULL COMMENT ''工资结算状态'' AFTER offboarding_type',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'asset_handover_status') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN asset_handover_status varchar(16) DEFAULT NULL COMMENT ''资产交接状态'' AFTER salary_settlement_status',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'non_compete_decision') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN non_compete_decision varchar(32) DEFAULT NULL COMMENT ''竞业决定'' AFTER asset_handover_status',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'compensation_amount') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN compensation_amount decimal(16,2) DEFAULT NULL COMMENT ''补偿金额'' AFTER non_compete_decision',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'compensation_note') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN compensation_note varchar(500) DEFAULT NULL COMMENT ''补偿说明'' AFTER compensation_amount',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
