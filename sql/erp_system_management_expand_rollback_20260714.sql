-- Guarded rollback for the compatible expansion. Refuses to discard live security-state data.

DROP PROCEDURE IF EXISTS sp_system_management_expand_rollback_20260714;
DELIMITER $$
CREATE PROCEDURE sp_system_management_expand_rollback_20260714()
BEGIN
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_has_state INT DEFAULT 0;
    DECLARE v_has_expiry INT DEFAULT 0;

    SELECT COUNT(*) INTO v_has_state
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'credential_state';
    SELECT COUNT(*) INTO v_has_expiry
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user'
      AND column_name = 'temporary_password_expires_at';

    IF v_has_state = 1 THEN
        SELECT COUNT(*) INTO v_count FROM sys_user WHERE credential_state <> 'ACTIVE';
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback refused: non-ACTIVE credential state exists';
        END IF;
    END IF;
    IF v_has_expiry = 1 THEN
        SELECT COUNT(*) INTO v_count FROM sys_user WHERE temporary_password_expires_at IS NOT NULL;
        IF v_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback refused: temporary credential expiry data exists';
        END IF;
    END IF;

    SELECT COUNT(*) INTO v_count FROM sys_security_session_outbox;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback refused: security session outbox contains evidence';
    END IF;
    SELECT COUNT(*) INTO v_count FROM sys_credential_migration_audit;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback refused: credential migration audit exists';
    END IF;
    SELECT COUNT(*) INTO v_count FROM sys_user_pii_access_audit;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback refused: PII access audit evidence exists';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.menu_id = rm.menu_id
    WHERE m.perms IN ('system:config:refresh', 'system:user:pii:read',
                      'system:user:pii:edit', 'system:user:pii:export');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback refused: new permissions are assigned';
    END IF;

    DELETE m
    FROM sys_menu m
    LEFT JOIN sys_mgmt_menu_backup_20260714 b ON b.menu_id = m.menu_id
    WHERE m.perms IN ('system:config:refresh', 'system:user:pii:read',
                      'system:user:pii:edit', 'system:user:pii:export')
      AND b.menu_id IS NULL;

    IF v_has_expiry = 1 THEN
        ALTER TABLE sys_user DROP COLUMN temporary_password_expires_at;
    END IF;
    IF v_has_state = 1 THEN
        ALTER TABLE sys_user DROP COLUMN credential_state;
    END IF;

    DROP TABLE sys_security_session_outbox;
    DROP TABLE sys_user_pii_access_audit;
    DROP TABLE sys_credential_migration_audit;
    DROP TABLE sys_mgmt_role_menu_backup_20260714;
    DROP TABLE sys_mgmt_menu_backup_20260714;
    DROP TABLE sys_mgmt_config_backup_20260714;
END$$
DELIMITER ;

CALL sp_system_management_expand_rollback_20260714();
DROP PROCEDURE sp_system_management_expand_rollback_20260714;
