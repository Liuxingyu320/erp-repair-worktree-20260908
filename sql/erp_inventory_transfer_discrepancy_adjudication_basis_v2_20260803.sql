-- ERP-NEW_2 V2 调拨差异裁决不透明依据令牌加法结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不签发或消费令牌、不写裁决、不更新事项、不执行库存/物流/财务处置。

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_adjudication_basis (
    adjudication_basis_id bigint NOT NULL AUTO_INCREMENT,
    token_hash char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    case_version bigint NOT NULL,
    fact_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_confirmation_event_id bigint NOT NULL,
    target_confirmation_event_id bigint NOT NULL,
    selected_shop_dept_id bigint NOT NULL,
    scope_digest char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    required_permission varchar(128) NOT NULL,
    adjudicator_user_id bigint NOT NULL,
    adjudicator_name varchar(64) NOT NULL,
    basis_status varchar(16) NOT NULL DEFAULT 'issued',
    issued_at datetime NOT NULL,
    expires_at datetime NOT NULL,
    consumed_at datetime NULL,
    consumed_request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    consumed_adjudication_id bigint NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) NOT NULL DEFAULT '',
    update_time datetime NULL,
    PRIMARY KEY (adjudication_basis_id),
    UNIQUE KEY uk_transfer_discrepancy_adjudication_basis_token
        (token_hash),
    KEY idx_transfer_discrepancy_adjudication_basis_case
        (discrepancy_case_id, case_version, basis_status),
    KEY idx_transfer_discrepancy_adjudication_basis_expiry
        (basis_status, expires_at, adjudication_basis_id),
    UNIQUE KEY uk_transfer_discrepancy_adjudication_basis_request
        (consumed_request_id),
    UNIQUE KEY uk_transfer_discrepancy_adjudication_basis_adjudication
        (consumed_adjudication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异裁决不透明依据令牌服务端绑定';
