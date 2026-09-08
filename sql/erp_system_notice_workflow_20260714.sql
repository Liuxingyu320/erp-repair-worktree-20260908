-- Release C: notice lifecycle, audience rules and immutable recipient snapshots.
-- MySQL 5.7/8.0 compatible and idempotent. Existing published-notice recipients
-- are a migration-time approximation: active users plus historical readers.

DROP PROCEDURE IF EXISTS sp_system_notice_workflow_expand_20260714;
DELIMITER //
CREATE PROCEDURE sp_system_notice_workflow_expand_20260714()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'lifecycle_status'
    ) THEN
        ALTER TABLE sys_notice ADD COLUMN lifecycle_status varchar(16) NULL COMMENT 'DRAFT/SCHEDULED/PUBLISHED/OFFLINE' AFTER status;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'audience_type'
    ) THEN
        ALTER TABLE sys_notice ADD COLUMN audience_type varchar(16) NULL COMMENT 'ALL/DEPT/ROLE/USER/MIXED' AFTER lifecycle_status;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'scheduled_publish_time'
    ) THEN
        ALTER TABLE sys_notice ADD COLUMN scheduled_publish_time datetime NULL AFTER audience_type;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'published_time'
    ) THEN
        ALTER TABLE sys_notice ADD COLUMN published_time datetime NULL AFTER scheduled_publish_time;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'expire_time'
    ) THEN
        ALTER TABLE sys_notice ADD COLUMN expire_time datetime NULL AFTER published_time;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'version'
    ) THEN
        ALTER TABLE sys_notice ADD COLUMN version bigint NOT NULL DEFAULT 1 AFTER expire_time;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND column_name = 'previous_notice_id'
    ) THEN
        ALTER TABLE sys_notice ADD COLUMN previous_notice_id bigint NULL AFTER version;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND index_name = 'idx_notice_lifecycle_schedule'
    ) THEN
        CREATE INDEX idx_notice_lifecycle_schedule ON sys_notice (lifecycle_status, scheduled_publish_time, notice_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'sys_notice' AND index_name = 'idx_notice_lifecycle_expire'
    ) THEN
        CREATE INDEX idx_notice_lifecycle_expire ON sys_notice (lifecycle_status, expire_time, notice_id);
    END IF;
END//
DELIMITER ;

CALL sp_system_notice_workflow_expand_20260714();
DROP PROCEDURE IF EXISTS sp_system_notice_workflow_expand_20260714;

CREATE TABLE IF NOT EXISTS sys_notice_audience (
    notice_id bigint NOT NULL,
    target_type varchar(16) NOT NULL,
    target_id bigint NOT NULL,
    include_children tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (notice_id, target_type, target_id),
    KEY idx_notice_audience_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公告受众规则';

CREATE TABLE IF NOT EXISTS sys_notice_recipient (
    notice_id bigint NOT NULL,
    user_id bigint NOT NULL,
    delivered_time datetime NOT NULL,
    recipient_source varchar(32) NOT NULL,
    PRIMARY KEY (notice_id, user_id),
    KEY idx_notice_recipient_user (user_id, notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公告发布接收人快照';

-- Only rows that have never been classified are legacy rows. Re-running this
-- migration therefore cannot reinterpret a new DRAFT as an old OFFLINE row.
UPDATE sys_notice
SET lifecycle_status = CASE WHEN status = '0' THEN 'PUBLISHED' ELSE 'OFFLINE' END,
    audience_type = 'ALL',
    published_time = CASE WHEN status = '0' THEN COALESCE(update_time, create_time, NOW()) ELSE NULL END,
    version = COALESCE(version, 1)
WHERE lifecycle_status IS NULL;

ALTER TABLE sys_notice
    MODIFY COLUMN lifecycle_status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SCHEDULED/PUBLISHED/OFFLINE',
    MODIFY COLUMN audience_type varchar(16) NOT NULL DEFAULT 'MIXED' COMMENT 'ALL/DEPT/ROLE/USER/MIXED';

INSERT IGNORE INTO sys_notice_audience (notice_id, target_type, target_id, include_children)
SELECT notice_id, 'ALL', 0, 0
FROM sys_notice
WHERE audience_type = 'ALL';

-- Historical status=0 meant all active users. Existing readers are unioned in
-- even if they are no longer active so prior read evidence remains attributable.
INSERT IGNORE INTO sys_notice_recipient (notice_id, user_id, delivered_time, recipient_source)
SELECT n.notice_id, u.user_id, NOW(), 'LEGACY_MIGRATION'
FROM sys_notice n
JOIN sys_user u ON u.del_flag = '0' AND u.status = '0'
WHERE n.lifecycle_status = 'PUBLISHED';

INSERT IGNORE INTO sys_notice_recipient (notice_id, user_id, delivered_time, recipient_source)
SELECT n.notice_id, r.user_id, NOW(), 'LEGACY_MIGRATION'
FROM sys_notice n
JOIN sys_notice_read r ON r.notice_id = n.notice_id
WHERE n.lifecycle_status = 'PUBLISHED';

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '公告发布工作流', 'feature.system.notice-workflow.enabled', 'false', 'Y',
       'system', NOW(), 'Release C 灰度开关；关闭时仍只能保存草稿，绝不恢复status=0隐式广播'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'feature.system.notice-workflow.enabled'
);

-- 新权限不从 edit/list 自动扩权；由上线审批后显式分配角色。
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
SELECT '公告发布', notice_menu.menu_id, 5, '#', NULL, NULL, '',
       1, 0, 'F', '0', '0', 'system:notice:publish', '#',
       'system', NOW(), '', NULL, '独立公告发布/计划发布权限，不从编辑权限自动继承'
FROM sys_menu notice_menu
WHERE notice_menu.perms = 'system:notice:list'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:notice:publish');

SELECT lifecycle_status, COUNT(*) AS notice_count
FROM sys_notice GROUP BY lifecycle_status ORDER BY lifecycle_status;
SELECT recipient_source, COUNT(*) AS recipient_count
FROM sys_notice_recipient GROUP BY recipient_source ORDER BY recipient_source;
SELECT config_key, config_value FROM sys_config
WHERE config_key = 'feature.system.notice-workflow.enabled';
SELECT menu_id, perms FROM sys_menu WHERE perms = 'system:notice:publish';
