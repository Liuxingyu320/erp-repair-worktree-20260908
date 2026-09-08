-- ERP-NEW_2 V2 调拨差异独立裁决计划加法结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不写裁决、不更新事项、不执行库存/物流/财务处置、不启用 Gate。

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_adjudication (
    adjudication_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    case_version_before bigint NOT NULL,
    case_version_after bigint NOT NULL,
    fact_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_confirmation_event_id bigint NOT NULL,
    target_confirmation_event_id bigint NOT NULL,
    discrepancy_type varchar(16) NOT NULL,
    discrepancy_quantity decimal(18,4) NOT NULL,
    source_cost_price decimal(18,6) NOT NULL,
    discrepancy_amount decimal(18,6) NOT NULL,
    decision_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    adjudication_note varchar(500) NOT NULL,
    evidence_refs varchar(2000) NOT NULL,
    required_permission varchar(128) NOT NULL,
    adjudicator_user_id bigint NOT NULL,
    adjudicator_name varchar(64) NOT NULL,
    plan_status varchar(32) NOT NULL DEFAULT 'adjudication_planned',
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adjudication_id),
    UNIQUE KEY uk_transfer_discrepancy_adjudication_request (request_id),
    UNIQUE KEY uk_transfer_discrepancy_adjudication_case_version
        (discrepancy_case_id, case_version_before),
    KEY idx_transfer_discrepancy_adjudication_status
        (plan_status, adjudication_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异独立裁决计划';

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_adjudication_action (
    adjudication_action_id bigint NOT NULL AUTO_INCREMENT,
    adjudication_id bigint NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    action_sequence int NOT NULL,
    action_type varchar(32) NOT NULL
        COMMENT 'reship/return_to_source/damage_write_off/responsibility_adjustment/transport_loss_write_off',
    coverage_kind varchar(16) NOT NULL
        COMMENT 'resolution/responsibility，由服务端按事项和动作推导',
    action_quantity decimal(18,4) NOT NULL,
    action_amount decimal(18,6) NOT NULL,
    responsible_party varchar(16) NOT NULL
        COMMENT 'source/target/carrier/company',
    action_note varchar(500) NOT NULL,
    execution_status varchar(32) NOT NULL DEFAULT 'pending',
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adjudication_action_id),
    UNIQUE KEY uk_transfer_discrepancy_adjudication_action_sequence
        (adjudication_id, action_sequence),
    KEY idx_transfer_discrepancy_adjudication_action_case
        (discrepancy_case_id, adjudication_action_id),
    KEY idx_transfer_discrepancy_adjudication_action_status
        (execution_status, adjudication_action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异裁决待执行动作';
