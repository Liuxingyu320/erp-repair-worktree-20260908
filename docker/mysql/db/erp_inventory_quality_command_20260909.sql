-- ERP-NEW_2 purchase quality command idempotency ledger.
-- Roll forward only. The caller selects the isolated application database.

CREATE TABLE IF NOT EXISTS inv_quality_command (
    command_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Globally unique X-Request-Id',
    command_type varchar(64) NOT NULL COMMENT 'Stable purchase quality command type',
    request_fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical request fingerprint',
    actor_user_id bigint NOT NULL,
    actor_username varchar(64) NOT NULL,
    selected_dept_id bigint NOT NULL,
    resource_key varchar(128) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    result_type varchar(255) DEFAULT NULL,
    result_payload longtext,
    completed_time datetime DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (command_id),
    UNIQUE KEY uk_inv_quality_command_request (request_id),
    KEY idx_inv_quality_command_resource (resource_key, command_type, create_time),
    KEY idx_inv_quality_command_actor (actor_user_id, selected_dept_id, create_time),
    CHECK (status IN ('PENDING', 'SUCCEEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Purchase quality command idempotency ledger';
