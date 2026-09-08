-- ERP-NEW_2 immutable transfer business-content revisions.
-- Roll forward only. The caller selects the isolated application database.
-- This file never selects, creates, or migrates a shared/original database.

CREATE TABLE IF NOT EXISTS inv_transfer_revision (
    revision_id bigint NOT NULL AUTO_INCREMENT,
    transfer_id bigint NOT NULL,
    revision_no int NOT NULL,
    parent_revision_id bigint DEFAULT NULL,
    approval_round int DEFAULT NULL,
    approval_instance_id bigint DEFAULT NULL,
    status varchar(16) NOT NULL,
    header_snapshot longtext NOT NULL COMMENT 'Canonical immutable business header JSON',
    detail_snapshot longtext NOT NULL COMMENT 'Canonical immutable requested-detail JSON',
    snapshot_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256(header + details)',
    decision_action varchar(32) DEFAULT NULL,
    decision_reason varchar(500) DEFAULT NULL,
    decision_user_id bigint DEFAULT NULL,
    decision_username varchar(64) DEFAULT NULL,
    decision_time datetime DEFAULT NULL,
    sealed_time datetime DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) NOT NULL,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (revision_id),
    UNIQUE KEY uk_inv_transfer_revision_no (transfer_id, revision_no),
    UNIQUE KEY uk_inv_transfer_revision_round (transfer_id, approval_round),
    KEY idx_inv_transfer_revision_parent (parent_revision_id),
    KEY idx_inv_transfer_revision_status (transfer_id, status, revision_id),
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED',
        'RETURNED', 'WITHDRAWN', 'CLOSED', 'CANCELLED', 'DELIVERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable transfer business revision ledger';
