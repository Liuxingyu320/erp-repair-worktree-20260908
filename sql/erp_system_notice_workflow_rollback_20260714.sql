-- Guarded Release C rollback. It is allowed only before any new workflow data
-- exists. Otherwise keep the additive schema and disable the feature flag.

DROP PROCEDURE IF EXISTS sp_system_notice_workflow_rollback_20260714;
DELIMITER //
CREATE PROCEDURE sp_system_notice_workflow_rollback_20260714()
BEGIN
    DECLARE new_workflow_rows bigint DEFAULT 0;
    DECLARE publish_permission_id bigint DEFAULT NULL;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice_recipient'
    ) THEN
        SELECT COUNT(*) INTO new_workflow_rows
        FROM sys_notice_recipient
        WHERE recipient_source <> 'LEGACY_MIGRATION';
    END IF;

    IF new_workflow_rows = 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'lifecycle_status'
    ) THEN
        SELECT COUNT(*) INTO new_workflow_rows
        FROM sys_notice
        WHERE previous_notice_id IS NOT NULL
           OR lifecycle_status IN ('DRAFT', 'SCHEDULED');
    END IF;

    IF new_workflow_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'notice workflow rollback blocked: new workflow data exists; disable the feature flag instead';
    END IF;

    SELECT menu_id INTO publish_permission_id
    FROM sys_menu WHERE perms = 'system:notice:publish' LIMIT 1;
    IF publish_permission_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE menu_id = publish_permission_id) THEN
        DELETE FROM sys_menu
        WHERE menu_id = publish_permission_id AND create_by = 'system';
    END IF;

    DELETE FROM sys_config
    WHERE config_key = 'feature.system.notice-workflow.enabled'
      AND config_value = 'false' AND create_by = 'system';

    DROP TABLE IF EXISTS sys_notice_recipient;
    DROP TABLE IF EXISTS sys_notice_audience;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'lifecycle_status'
    ) THEN
        ALTER TABLE sys_notice
            DROP COLUMN previous_notice_id,
            DROP COLUMN version,
            DROP COLUMN expire_time,
            DROP COLUMN published_time,
            DROP COLUMN scheduled_publish_time,
            DROP COLUMN audience_type,
            DROP COLUMN lifecycle_status;
    END IF;
END//
DELIMITER ;

CALL sp_system_notice_workflow_rollback_20260714();
DROP PROCEDURE IF EXISTS sp_system_notice_workflow_rollback_20260714;
