-- Persist authoritative transfer audit details separately from the business effective date.
SET @erp_db = DATABASE();

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_hr_lifecycle_action') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_hr_lifecycle_action'
            AND COLUMN_NAME = 'actual_confirm_time') = 0,
    'ALTER TABLE sys_hr_lifecycle_action ADD COLUMN actual_confirm_time datetime DEFAULT NULL COMMENT ''实际确认时间'' AFTER effective_date',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- Keep transfer confirmation on the feature-owned single-HR role. The permission
-- row uses a runtime ID because historical HR/OA migrations share old numeric IDs.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_menu') > 0,
    'INSERT INTO sys_menu
        (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
         is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
     SELECT sequence.next_id, ''确认调岗'', parent.menu_id, 20, ''#'', '''', NULL, '''',
            ''1'', ''0'', ''F'', ''0'', ''0'', ''hr:employee:transfer'', ''#'',
            ''system'', NOW(), ''唯一HR确认员工调岗''
       FROM (SELECT menu_id FROM sys_menu WHERE perms = ''hr:employee:list'' ORDER BY menu_id LIMIT 1) parent
       CROSS JOIN (SELECT COALESCE(MAX(menu_id), 0) + 1 AS next_id FROM sys_menu) sequence
      WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = ''hr:employee:transfer'')',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Extend, rather than duplicate, the existing task-center and plan permission sync.
DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_transfer;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_transfer()
BEGIN
    CALL sync_sign_hr_permissions_with_plan();

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
    SELECT state.managed_role_id, transfer_menu.menu_id
      FROM sys_sign_hr_state state
      JOIN sys_menu transfer_menu
        ON transfer_menu.perms = 'hr:employee:transfer'
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL;

    INSERT INTO sys_sign_hr_menu_grant
        (role_id, menu_id, hr_user_id, created_time)
    SELECT state.managed_role_id, transfer_menu.menu_id, state.hr_user_id, NOW()
      FROM sys_sign_hr_state state
      JOIN sys_menu transfer_menu
        ON transfer_menu.perms = 'hr:employee:transfer'
      JOIN sys_role_menu role_menu
        ON role_menu.role_id = state.managed_role_id
       AND role_menu.menu_id = transfer_menu.menu_id
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL
    ON DUPLICATE KEY UPDATE
        hr_user_id = VALUES(hr_user_id),
        created_time = VALUES(created_time);
END$$
DELIMITER ;

-- Incremental deployment: grant the permission to the currently managed role.
-- Future configuration writes call the wrapper above through SysConfigMapper.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_state') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_role_menu') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_menu') > 0,
    'INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
       SELECT state.managed_role_id, transfer_menu.menu_id
         FROM sys_sign_hr_state state
         JOIN sys_menu transfer_menu ON transfer_menu.perms = ''hr:employee:transfer''
        WHERE state.state_id = 1
          AND state.managed_role_id IS NOT NULL
          AND state.hr_user_id IS NOT NULL',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_state') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_role_menu') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_menu') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_sign_hr_menu_grant') > 0,
    'INSERT INTO sys_sign_hr_menu_grant (role_id, menu_id, hr_user_id, created_time)
       SELECT state.managed_role_id, transfer_menu.menu_id, state.hr_user_id, NOW()
         FROM sys_sign_hr_state state
         JOIN sys_menu transfer_menu ON transfer_menu.perms = ''hr:employee:transfer''
         JOIN sys_role_menu role_menu
           ON role_menu.role_id = state.managed_role_id
          AND role_menu.menu_id = transfer_menu.menu_id
        WHERE state.state_id = 1
          AND state.managed_role_id IS NOT NULL
          AND state.hr_user_id IS NOT NULL
       ON DUPLICATE KEY UPDATE hr_user_id = VALUES(hr_user_id), created_time = VALUES(created_time)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_hr_lifecycle_action') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_hr_lifecycle_action'
            AND COLUMN_NAME = 'risk_confirmation_json') = 0,
    'ALTER TABLE sys_hr_lifecycle_action ADD COLUMN risk_confirmation_json json DEFAULT NULL COMMENT ''历史补录结构化二次确认'' AFTER risk_detail',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_hr_lifecycle_action') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_hr_lifecycle_action'
            AND COLUMN_NAME = 'historical_reason') = 0,
    'ALTER TABLE sys_hr_lifecycle_action ADD COLUMN historical_reason varchar(500) DEFAULT NULL COMMENT ''历史调岗补录原因'' AFTER risk_confirmation_json',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- Freeze the transfer event context on the OA task and generated signing package.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND COLUMN_NAME = 'before_snapshot_json') = 0,
    'ALTER TABLE oa_sign_task ADD COLUMN before_snapshot_json json DEFAULT NULL COMMENT ''调岗前签约快照'' AFTER dedupe_key',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND COLUMN_NAME = 'after_snapshot_json') = 0,
    'ALTER TABLE oa_sign_task ADD COLUMN after_snapshot_json json DEFAULT NULL COMMENT ''调岗后签约快照'' AFTER before_snapshot_json',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND COLUMN_NAME = 'business_effective_date') = 0,
    'ALTER TABLE oa_sign_task ADD COLUMN business_effective_date date DEFAULT NULL COMMENT ''业务生效日期'' AFTER after_snapshot_json',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_task'
            AND COLUMN_NAME = 'historical_supplement') = 0,
    'ALTER TABLE oa_sign_task ADD COLUMN historical_supplement tinyint(1) DEFAULT NULL COMMENT ''是否历史调岗补录'' AFTER business_effective_date',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'transfer_effective_date') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN transfer_effective_date varchar(10) DEFAULT NULL COMMENT ''调岗业务生效日期'' AFTER actual_regularization_date',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'historical_supplement') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN historical_supplement tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否历史调岗补录'' AFTER transfer_effective_date',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'before_dept_name_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN before_dept_name_snapshot varchar(100) DEFAULT NULL COMMENT ''调岗前组织名称快照'' AFTER historical_supplement',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package'
            AND COLUMN_NAME = 'before_post_name_snapshot') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN before_post_name_snapshot varchar(64) DEFAULT NULL COMMENT ''调岗前岗位名称快照'' AFTER before_dept_name_snapshot',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
