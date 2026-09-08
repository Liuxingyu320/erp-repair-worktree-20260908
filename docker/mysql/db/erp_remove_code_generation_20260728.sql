-- Remove the retired code-generation menu and its role grants.
-- The legacy gen_table metadata is intentionally retained for rollback/audit safety.

DELIMITER $$

DROP PROCEDURE IF EXISTS remove_code_generation_feature_20260728$$
CREATE PROCEDURE remove_code_generation_feature_20260728()
BEGIN
    DECLARE conflicting_menu_count int DEFAULT 0;

    SELECT COUNT(*) INTO conflicting_menu_count
      FROM sys_menu
     WHERE menu_id = 115
       AND NOT (
            parent_id = 3
        AND BINARY COALESCE(path, '') = BINARY 'gen'
        AND BINARY COALESCE(component, '') = BINARY 'tool/gen/index'
        AND BINARY COALESCE(perms, '') = BINARY 'tool:gen:list'
       );

    IF conflicting_menu_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Menu 115 no longer belongs to code generation';
    END IF;

    DELETE role_menu
      FROM sys_role_menu role_menu
      LEFT JOIN sys_menu menu
        ON menu.menu_id = role_menu.menu_id
     WHERE role_menu.menu_id IN (115, 1055, 1056, 1057, 1058, 1059, 1060)
        OR menu.parent_id = 115
        OR menu.perms LIKE 'tool:gen:%'
        OR BINARY COALESCE(menu.component, '') = BINARY 'tool/gen/index';

    DELETE FROM sys_menu
     WHERE parent_id = 115
        OR perms LIKE 'tool:gen:%';

    DELETE FROM sys_menu
     WHERE menu_id = 115
       AND parent_id = 3
       AND BINARY COALESCE(path, '') = BINARY 'gen'
       AND BINARY COALESCE(component, '') = BINARY 'tool/gen/index'
       AND BINARY COALESCE(perms, '') = BINARY 'tool:gen:list';
END$$

DELIMITER ;

START TRANSACTION;
CALL remove_code_generation_feature_20260728();
COMMIT;

DROP PROCEDURE IF EXISTS remove_code_generation_feature_20260728;
