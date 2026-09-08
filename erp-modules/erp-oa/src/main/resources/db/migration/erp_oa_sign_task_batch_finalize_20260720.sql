-- Dedicated permission for batch legal-entity selection and contract sealing.
-- Forward-only, idempotent, and compatible with MySQL 5.7/8.x.

DROP PROCEDURE IF EXISTS assert_sign_batch_finalize_permission_20260720;
DELIMITER $$
CREATE PROCEDURE assert_sign_batch_finalize_permission_20260720()
BEGIN
    DECLARE sign_role_count int DEFAULT 0;

    IF NOT EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE menu_id = 9650
           AND parent_id = 3000
           AND BINARY path = BINARY 'sign-task'
           AND BINARY component = BINARY 'oa/signTask/index'
           AND BINARY route_name = BINARY 'OaSignTask'
           AND menu_type = 'C'
           AND BINARY perms = BINARY 'oa:signTask:list'
           AND status = '0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Batch finalize requires the repaired signing task center menu';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_sign_hr_menu_grant'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_sign_hr_state'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Batch finalize requires the dedicated signing HR grant tables';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE menu_id = 9670
           AND NOT COALESCE((
                parent_id = 9650
                AND COALESCE(path, '') = ''
                AND component IS NULL
                AND COALESCE(route_name, '') = ''
                AND menu_type = 'F'
                AND BINARY perms = BINARY 'oa:signTask:batchFinalize'
           ), 0)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Signing batch-finalize menu ID 9670 belongs to another feature';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE BINARY perms = BINARY 'oa:signTask:batchFinalize'
           AND menu_id <> 9670
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate signing batch-finalize permission menu exists';
    END IF;

    SELECT COUNT(*) INTO sign_role_count
      FROM sys_role
     WHERE BINARY role_key = BINARY 'sign_single_hr';
    IF sign_role_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Multiple sign_single_hr roles exist';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM sys_sign_hr_state state
          JOIN sys_role managed_role ON managed_role.role_id = state.managed_role_id
         WHERE state.state_id = 1
           AND NOT COALESCE(BINARY managed_role.role_key = BINARY 'sign_single_hr', 0)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Managed signing role ID belongs to another role';
    END IF;
END$$
DELIMITER ;

CALL assert_sign_batch_finalize_permission_20260720();
DROP PROCEDURE IF EXISTS assert_sign_batch_finalize_permission_20260720;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
VALUES
    (9670, '批量选公司盖章', 9650, 20, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:batchFinalize', '#',
     'system', NOW(), 'system', NOW(), '仅唯一合同经办人可批量选择合同公司并生成盖章文件')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_by = VALUES(update_by),
    update_time = VALUES(update_time),
    remark = VALUES(remark);

-- The dedicated role survives HR reassignment; granting the role keeps the permission
-- attached when sys_user_role is moved to the newly configured HR.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, 9670
  FROM sys_role role
 WHERE BINARY role.role_key = BINARY 'sign_single_hr'
   AND role.status = '0'
   AND role.del_flag = '0';

INSERT INTO sys_sign_hr_menu_grant (role_id, menu_id, hr_user_id, created_time)
SELECT state.managed_role_id, 9670, state.hr_user_id, NOW()
  FROM sys_sign_hr_state state
  JOIN sys_role managed_role
    ON managed_role.role_id = state.managed_role_id
   AND BINARY managed_role.role_key = BINARY 'sign_single_hr'
  JOIN sys_role_menu role_menu
    ON role_menu.role_id = state.managed_role_id
   AND role_menu.menu_id = 9670
 WHERE state.state_id = 1
   AND state.hr_user_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    hr_user_id = VALUES(hr_user_id),
    created_time = VALUES(created_time);
