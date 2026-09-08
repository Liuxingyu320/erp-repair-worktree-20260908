-- Irreversible security finalization. This script does not restore a shared initial password.

DROP PROCEDURE IF EXISTS sp_system_management_finalize_20260714;
DELIMITER $$
CREATE PROCEDURE sp_system_management_finalize_20260714()
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM sys_credential_migration_audit
    WHERE phase = 'FINAL_READINESS'
      AND status = 'CONFIRMED'
      AND shared_match_count = 0
      AND active_shared_match_count = 0
      AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR);
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'finalize refused: recent confirmed zero-match credential audit is required';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM sys_user
    WHERE del_flag = '0'
      AND (credential_state NOT IN ('ACTIVE', 'TEMPORARY', 'CHANGE_REQUIRED')
       OR (credential_state = 'TEMPORARY' AND temporary_password_expires_at IS NULL)
       OR (credential_state <> 'TEMPORARY' AND temporary_password_expires_at IS NOT NULL));
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'finalize refused: credential-state invariant failed';
    END IF;

    DELETE FROM sys_config
    WHERE config_key IN ('sys.user.initPassword', 'sys.account.initPasswordModify');

    SELECT COUNT(*) INTO v_count
    FROM sys_config
    WHERE config_key IN ('sys.user.initPassword', 'sys.account.initPasswordModify');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'legacy credential configuration still exists';
    END IF;
END$$
DELIMITER ;

CALL sp_system_management_finalize_20260714();
DROP PROCEDURE sp_system_management_finalize_20260714;

SELECT config_key
FROM sys_config
WHERE config_key IN ('sys.user.initPassword', 'sys.account.initPasswordModify');

