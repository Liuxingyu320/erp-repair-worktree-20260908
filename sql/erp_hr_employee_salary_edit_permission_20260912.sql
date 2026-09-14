-- 07B: register one independent salary-write action under the employee archive.
-- This file does not grant permissions to any role or user. Assign through the
-- existing role editor after reviewing the target role. Do not revive HR auto-grants.
-- Execute during the normal serialized migration window; no data migration is needed.
-- Function actions need no route name; omit route_name for older menu-table compatibility.
DROP PROCEDURE IF EXISTS migrate_hr_salary_edit_menu_20260912;
DELIMITER $$
CREATE PROCEDURE migrate_hr_salary_edit_menu_20260912()
BEGIN
    DECLARE parent_count INT DEFAULT 0;
    DECLARE parent_menu_id BIGINT DEFAULT NULL;
    DECLARE permission_count INT DEFAULT 0;
    DECLARE canonical_count INT DEFAULT 0;

    SELECT COUNT(*), MIN(menu_id) INTO parent_count, parent_menu_id
      FROM sys_menu
     WHERE BINARY perms = BINARY 'hr:employee:list'
       AND menu_type = 'C' AND status = '0';
    IF parent_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected one active employee archive menu';
    END IF;

    SELECT COUNT(*) INTO permission_count FROM sys_menu
     WHERE BINARY perms = BINARY 'hr:employee:salary:edit';
    SELECT COUNT(*) INTO canonical_count FROM sys_menu
     WHERE BINARY perms = BINARY 'hr:employee:salary:edit'
       AND parent_id = parent_menu_id AND menu_type = 'F' AND status = '0';
    IF permission_count > 0 AND (permission_count <> 1 OR canonical_count <> 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Salary edit permission menu needs manual reconciliation';
    END IF;

    IF permission_count = 0 THEN
        INSERT INTO sys_menu
            (menu_id, menu_name, parent_id, order_num, path, component, `query`,
             is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        SELECT next_id, '调整员工工资', parent_menu_id, 25, '#', '', NULL,
               '1', '0', 'F', '0', '0', 'hr:employee:salary:edit', '#', 'migration', NOW(),
               '07B_20260912:独立调薪写权限；通过角色管理明确授权'
          FROM (SELECT COALESCE(MAX(menu_id), 0) + 1 AS next_id FROM sys_menu) ids;
    END IF;
END$$
DELIMITER ;
CALL migrate_hr_salary_edit_menu_20260912();
DROP PROCEDURE migrate_hr_salary_edit_menu_20260912;

-- Targeted rollback, only for the row created by this migration and after checking
-- it has no role bindings. Do not remove a pre-existing permission row or role grants.
-- DELETE menu FROM sys_menu menu
-- WHERE BINARY menu.perms = BINARY 'hr:employee:salary:edit'
--   AND menu.remark = '07B_20260912:独立调薪写权限；通过角色管理明确授权'
--   AND NOT EXISTS (SELECT 1 FROM sys_role_menu binding WHERE binding.menu_id = menu.menu_id);
