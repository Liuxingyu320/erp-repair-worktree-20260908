-- ERP-NEW_2 V2 调拨差异责任与短缺损失不可变台账（MySQL 5.7）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不写业务数据、不执行库存/财务总账效果、不启用 Gate。

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_responsibility_ledger (
    responsibility_ledger_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    adjudication_id bigint NOT NULL,
    adjudication_action_id bigint NOT NULL,
    action_version_before bigint NOT NULL,
    action_version_after bigint NOT NULL,
    case_version_before bigint NOT NULL,
    case_version_after bigint NOT NULL,
    case_status_before varchar(32) NOT NULL,
    case_status_after varchar(32) NOT NULL,
    plan_status_before varchar(32) NOT NULL,
    plan_status_after varchar(32) NOT NULL,
    action_status_before varchar(32) NOT NULL,
    action_status_after varchar(32) NOT NULL,
    action_sequence int NOT NULL,
    receipt_id bigint NOT NULL,
    receipt_allocation_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    shipment_allocation_id bigint NOT NULL,
    item_type varchar(20) NOT NULL,
    item_id bigint NOT NULL,
    product_id bigint DEFAULT NULL,
    discrepancy_type varchar(16) NOT NULL,
    action_type varchar(32) NOT NULL,
    effect_kind varchar(64) NOT NULL,
    responsible_party varchar(16) NOT NULL,
    action_quantity decimal(18,4) NOT NULL,
    source_cost_price decimal(18,6) NOT NULL,
    action_amount decimal(18,6) NOT NULL,
    decision_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    adjudication_note varchar(500) NOT NULL,
    action_note varchar(500) NOT NULL,
    evidence_refs varchar(2000) NOT NULL,
    required_permission varchar(128) NOT NULL,
    operator_user_id bigint NOT NULL,
    operator_name varchar(64) NOT NULL,
    occurred_time datetime NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (responsibility_ledger_id),
    UNIQUE KEY uk_transfer_discrepancy_responsibility_request
        (request_id),
    UNIQUE KEY uk_transfer_discrepancy_responsibility_action_version
        (adjudication_action_id, action_version_before),
    KEY idx_transfer_discrepancy_responsibility_case
        (discrepancy_case_id, responsibility_ledger_id),
    KEY idx_transfer_discrepancy_responsibility_transfer
        (transfer_id, responsibility_ledger_id),
    KEY idx_transfer_discrepancy_responsibility_party
        (responsible_party, occurred_time, responsibility_ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异不可变责任调整台账';

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_shortage_loss_ledger (
    shortage_loss_ledger_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    adjudication_id bigint NOT NULL,
    adjudication_action_id bigint NOT NULL,
    action_version_before bigint NOT NULL,
    action_version_after bigint NOT NULL,
    case_version_before bigint NOT NULL,
    case_version_after bigint NOT NULL,
    case_status_before varchar(32) NOT NULL,
    case_status_after varchar(32) NOT NULL,
    plan_status_before varchar(32) NOT NULL,
    plan_status_after varchar(32) NOT NULL,
    action_status_before varchar(32) NOT NULL,
    action_status_after varchar(32) NOT NULL,
    action_sequence int NOT NULL,
    receipt_id bigint NOT NULL,
    receipt_allocation_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    shipment_allocation_id bigint NOT NULL,
    item_type varchar(20) NOT NULL,
    item_id bigint NOT NULL,
    product_id bigint DEFAULT NULL,
    discrepancy_type varchar(16) NOT NULL,
    action_type varchar(32) NOT NULL,
    effect_kind varchar(64) NOT NULL,
    loss_type varchar(32) NOT NULL,
    responsible_party varchar(16) NOT NULL,
    loss_quantity decimal(18,4) NOT NULL,
    source_cost_price decimal(18,6) NOT NULL,
    loss_amount decimal(18,6) NOT NULL,
    decision_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    adjudication_note varchar(500) NOT NULL,
    action_note varchar(500) NOT NULL,
    evidence_refs varchar(2000) NOT NULL,
    required_permission varchar(128) NOT NULL,
    operator_user_id bigint NOT NULL,
    operator_name varchar(64) NOT NULL,
    occurred_time datetime NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (shortage_loss_ledger_id),
    UNIQUE KEY uk_transfer_discrepancy_shortage_loss_request
        (request_id),
    UNIQUE KEY uk_transfer_discrepancy_shortage_loss_action_version
        (adjudication_action_id, action_version_before),
    KEY idx_transfer_discrepancy_shortage_loss_case
        (discrepancy_case_id, shortage_loss_ledger_id),
    KEY idx_transfer_discrepancy_shortage_loss_transfer
        (transfer_id, shortage_loss_ledger_id),
    KEY idx_transfer_discrepancy_shortage_loss_party
        (responsible_party, occurred_time, shortage_loss_ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异不可变短缺运输损失台账';
