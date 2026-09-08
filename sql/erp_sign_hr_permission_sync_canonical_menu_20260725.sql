-- Keep duplicate permission records compatible with historical role data, while
-- selecting only the HR employee archive actions for signing-HR synchronization.

DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_transfer;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_transfer()
BEGIN
    DECLARE transfer_menu_count int DEFAULT 0;
    DECLARE transfer_menu_id bigint DEFAULT NULL;

    SELECT COUNT(*), MIN(action_menu.menu_id)
      INTO transfer_menu_count, transfer_menu_id
      FROM sys_menu action_menu
      JOIN sys_menu employee_menu
        ON employee_menu.menu_id = action_menu.parent_id
       AND BINARY employee_menu.perms = BINARY 'hr:employee:list'
       AND employee_menu.status = '0'
     WHERE BINARY action_menu.perms = BINARY 'hr:employee:transfer'
       AND action_menu.status = '0';
    IF transfer_menu_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected one active HR employee archive transfer permission menu';
    END IF;

    CALL sync_sign_hr_permissions_with_plan();

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
    SELECT state.managed_role_id, transfer_menu_id
      FROM sys_sign_hr_state state
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL;

    INSERT INTO sys_sign_hr_menu_grant (role_id, menu_id, hr_user_id, created_time)
    SELECT state.managed_role_id, transfer_menu_id, state.hr_user_id, NOW()
      FROM sys_sign_hr_state state
      JOIN sys_role_menu role_menu
        ON role_menu.role_id = state.managed_role_id
       AND role_menu.menu_id = transfer_menu_id
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL
    ON DUPLICATE KEY UPDATE
        hr_user_id = VALUES(hr_user_id),
        created_time = VALUES(created_time);
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_offboarding;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_offboarding()
BEGIN
    DECLARE offboard_menu_count int DEFAULT 0;
    DECLARE offboard_menu_id bigint DEFAULT NULL;

    SELECT COUNT(*), MIN(action_menu.menu_id)
      INTO offboard_menu_count, offboard_menu_id
      FROM sys_menu action_menu
      JOIN sys_menu employee_menu
        ON employee_menu.menu_id = action_menu.parent_id
       AND BINARY employee_menu.perms = BINARY 'hr:employee:list'
       AND employee_menu.status = '0'
     WHERE BINARY action_menu.perms = BINARY 'hr:employee:offboard'
       AND action_menu.status = '0';
    IF offboard_menu_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected one active HR employee archive offboarding permission menu';
    END IF;

    CALL sync_sign_hr_permissions_with_transfer();

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
    SELECT state.managed_role_id, offboard_menu_id
      FROM sys_sign_hr_state state
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL;

    INSERT INTO sys_sign_hr_menu_grant (role_id, menu_id, hr_user_id, created_time)
    SELECT state.managed_role_id, offboard_menu_id, state.hr_user_id, NOW()
      FROM sys_sign_hr_state state
      JOIN sys_role_menu role_menu
        ON role_menu.role_id = state.managed_role_id
       AND role_menu.menu_id = offboard_menu_id
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL
    ON DUPLICATE KEY UPDATE
        hr_user_id = VALUES(hr_user_id),
        created_time = VALUES(created_time);
END$$
DELIMITER ;
