-- ERP-NEW_2 V2 调拨差异双方确认事件加法结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不写确认事件、不更新差异事项、不启用 Gate、不创建裁决结果。

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_confirmation_event (
    confirmation_event_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    case_version bigint NOT NULL,
    fact_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    party_role varchar(16) NOT NULL
        COMMENT 'source/target',
    party_dept_id bigint NOT NULL,
    decision varchar(16) NOT NULL
        COMMENT 'confirmed/disputed',
    confirmation_note varchar(500) DEFAULT NULL,
    operator_user_id bigint NOT NULL,
    operator_name varchar(64) NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (confirmation_event_id),
    UNIQUE KEY uk_transfer_discrepancy_confirmation_request (request_id),
    KEY idx_transfer_discrepancy_confirmation_latest
        (discrepancy_case_id, party_role, confirmation_event_id),
    KEY idx_transfer_discrepancy_confirmation_operator
        (operator_user_id, confirmation_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异双方事实确认追加事件';
