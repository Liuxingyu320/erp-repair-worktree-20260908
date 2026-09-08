-- System management Release A: compatible expansion (MySQL 5.7 / 8.0).
-- This migration never grants a new high-risk permission and never deletes legacy configuration.

CREATE TABLE IF NOT EXISTS sys_mgmt_config_backup_20260714 (
    config_id INT NOT NULL,
    config_name VARCHAR(100) NOT NULL DEFAULT '',
    config_key VARCHAR(100) NOT NULL,
    value_configured TINYINT(1) NOT NULL DEFAULT 0,
    config_value_sha256 CHAR(64) NOT NULL,
    config_type CHAR(1) NOT NULL DEFAULT 'N',
    remark VARCHAR(500) DEFAULT NULL,
    backed_up_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_sys_mgmt_config_backup_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统管理整改配置元数据备份（不保存敏感原值）';

INSERT IGNORE INTO sys_mgmt_config_backup_20260714
    (config_id, config_name, config_key, value_configured, config_value_sha256, config_type, remark)
SELECT config_id, config_name, config_key,
       IF(config_value IS NULL OR config_value = '', 0, 1),
       SHA2(COALESCE(config_value, ''), 256), config_type, remark
FROM sys_config
WHERE config_key IN ('sys.user.initPassword', 'sys.account.initPasswordModify');

CREATE TABLE IF NOT EXISTS sys_mgmt_menu_backup_20260714 LIKE sys_menu;
INSERT IGNORE INTO sys_mgmt_menu_backup_20260714
SELECT m.*
FROM sys_menu m
WHERE m.perms IN (
    'system:user:resetPwd', 'system:config:query', 'system:operlog:query',
    'system:logininfor:query', 'system:salary:import', 'system:config:refresh',
    'system:user:pii:read', 'system:user:pii:edit', 'system:user:pii:export'
);

CREATE TABLE IF NOT EXISTS sys_mgmt_role_menu_backup_20260714 LIKE sys_role_menu;
INSERT IGNORE INTO sys_mgmt_role_menu_backup_20260714
SELECT rm.*
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms IN (
    'system:user:resetPwd', 'system:config:query', 'system:operlog:query',
    'system:logininfor:query', 'system:salary:import', 'system:config:refresh',
    'system:user:pii:read', 'system:user:pii:edit', 'system:user:pii:export'
);

CREATE TABLE IF NOT EXISTS sys_credential_migration_audit (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    target_database VARCHAR(64) NOT NULL,
    total_user_count INT NOT NULL DEFAULT 0,
    shared_match_count INT NOT NULL DEFAULT 0,
    active_shared_match_count INT NOT NULL DEFAULT 0,
    change_required_count INT NOT NULL DEFAULT 0,
    temporary_count INT NOT NULL DEFAULT 0,
    operator_user_id BIGINT DEFAULT NULL,
    candidate_digest CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_credential_migration_batch_phase (batch_id, phase),
    KEY idx_credential_migration_readiness (phase, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旧共享凭据迁移数量审计（无账号名、哈希或凭据）';

CREATE TABLE IF NOT EXISTS sys_security_session_outbox (
    outbox_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at DATETIME DEFAULT NULL,
    lock_owner VARCHAR(64) DEFAULT NULL,
    locked_at DATETIME DEFAULT NULL,
    last_error_code VARCHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME DEFAULT NULL,
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_security_session_event (event_id),
    KEY idx_security_session_dispatch (status, available_at, next_attempt_at),
    KEY idx_security_session_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全状态变化后的会话失效补偿队列';

CREATE TABLE IF NOT EXISTS sys_user_pii_access_audit (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    viewer_user_id BIGINT NOT NULL,
    target_user_id BIGINT DEFAULT NULL,
    reason_code VARCHAR(32) NOT NULL,
    action_code VARCHAR(16) NOT NULL,
    result_code VARCHAR(16) NOT NULL,
    changed_fields VARCHAR(1000) DEFAULT NULL,
    row_count INT NOT NULL DEFAULT 0,
    dataset_digest CHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY idx_user_pii_audit_viewer (viewer_user_id, created_at),
    KEY idx_user_pii_audit_target (target_user_id, created_at),
    KEY idx_user_pii_audit_action (action_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人敏感信息访问元数据审计（不保存字段值）';

DROP PROCEDURE IF EXISTS sp_system_management_expand_20260714;
DELIMITER $$
CREATE PROCEDURE sp_system_management_expand_20260714()
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'credential_state';
    IF v_count = 0 THEN
        ALTER TABLE sys_user
            ADD COLUMN credential_state VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
                COMMENT '凭据状态 ACTIVE/TEMPORARY/CHANGE_REQUIRED' AFTER pwd_update_date;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user'
      AND column_name = 'temporary_password_expires_at';
    IF v_count = 0 THEN
        ALTER TABLE sys_user
            ADD COLUMN temporary_password_expires_at DATETIME NULL
                COMMENT '管理员签发临时密码的失效时间' AFTER credential_state;
    END IF;

    SELECT COUNT(*) INTO v_count FROM sys_menu WHERE perms = 'system:config:list';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'expected exactly one system:config:list parent menu';
    END IF;
    INSERT INTO sys_menu
        (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
         menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
    SELECT '参数缓存刷新', parent.menu_id, 6, '#', NULL, NULL, '', 1, 0,
           'F', '0', '0', 'system:config:refresh', '#', 'system', NOW(), '', NULL,
           '独立高风险权限；不从参数删除权限自动继承'
    FROM sys_menu parent
    WHERE parent.perms = 'system:config:list'
      AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.perms = 'system:config:refresh');

    SELECT COUNT(*) INTO v_count FROM sys_menu WHERE perms = 'system:user:list';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'expected exactly one system:user:list parent menu';
    END IF;
    INSERT INTO sys_menu
        (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
         menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
    SELECT permission_name, parent.menu_id, permission_order, '#', NULL, NULL, '', 1, 0,
           'F', '0', '0', permission_code, '#', 'system', NOW(), '', NULL,
           '个人敏感信息专用权限；默认不向普通角色授权'
    FROM sys_menu parent
    CROSS JOIN (
        SELECT '查看完整个人敏感信息' permission_name, 8 permission_order,
               'system:user:pii:read' permission_code
        UNION ALL SELECT '修改个人敏感信息', 9, 'system:user:pii:edit'
        UNION ALL SELECT '导出个人敏感信息', 10, 'system:user:pii:export'
    ) permission_seed
    WHERE parent.perms = 'system:user:list'
      AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.perms = permission_seed.permission_code);

    SELECT COUNT(*) INTO v_count
    FROM sys_user
    WHERE credential_state NOT IN ('ACTIVE', 'TEMPORARY', 'CHANGE_REQUIRED')
       OR (credential_state = 'TEMPORARY' AND temporary_password_expires_at IS NULL)
       OR (credential_state <> 'TEMPORARY' AND temporary_password_expires_at IS NOT NULL);
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'credential-state invariant failed after expansion';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM sys_menu
    WHERE perms IN ('system:config:refresh', 'system:user:pii:read',
                    'system:user:pii:edit', 'system:user:pii:export');
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'new permission menu cardinality failed';
    END IF;
END$$
DELIMITER ;

CALL sp_system_management_expand_20260714();
DROP PROCEDURE sp_system_management_expand_20260714;

SELECT credential_state, COUNT(*) AS user_count
FROM sys_user
WHERE del_flag = '0'
GROUP BY credential_state
ORDER BY credential_state;
SELECT perms, status
FROM sys_menu
WHERE perms IN ('system:config:refresh', 'system:user:pii:read',
                'system:user:pii:edit', 'system:user:pii:export')
ORDER BY perms;
