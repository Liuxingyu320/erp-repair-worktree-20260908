-- Security-preserving rollback.
-- The two orphan permissions intentionally stay disabled and their role mappings are not restored.
-- New high-risk permissions had no automatic grants, so there is no grant to infer or restore here.

DROP PROCEDURE IF EXISTS sp_system_management_permissions_rollback_20260714;
DELIMITER $$
CREATE PROCEDURE sp_system_management_permissions_rollback_20260714()
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.menu_id = rm.menu_id
    WHERE m.perms IN ('system:logininfor:query', 'system:salary:import');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback refused: orphan permission mappings must remain removed';
    END IF;

    UPDATE sys_menu
    SET status = '1', visible = '1', update_by = 'system', update_time = NOW()
    WHERE perms IN ('system:logininfor:query', 'system:salary:import');
END$$
DELIMITER ;

CALL sp_system_management_permissions_rollback_20260714();
DROP PROCEDURE sp_system_management_permissions_rollback_20260714;

