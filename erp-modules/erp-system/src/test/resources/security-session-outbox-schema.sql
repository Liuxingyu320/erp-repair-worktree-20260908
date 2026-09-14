-- Copied existing production outbox schema, before additive retention migration.
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
