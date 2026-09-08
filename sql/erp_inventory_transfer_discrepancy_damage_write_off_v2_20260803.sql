-- ERP-NEW_2 only: additive damaged-quarantine write-off ledgers.
-- This file defines schema only and never mutates business rows.

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_damage_loss_ledger (
    damage_loss_ledger_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stock_ledger_request_id varchar(100)
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
    target_warehouse_id bigint NOT NULL,
    item_type varchar(20) NOT NULL,
    item_id bigint NOT NULL,
    product_id bigint DEFAULT NULL,
    tracking_policy varchar(16) NOT NULL,
    damaged_target_lot_id bigint NOT NULL,
    quarantine_location_id bigint NOT NULL,
    damaged_target_balance_id bigint NOT NULL,
    discrepancy_type varchar(16) NOT NULL,
    action_type varchar(32) NOT NULL,
    effect_kind varchar(64) NOT NULL,
    loss_type varchar(32) NOT NULL,
    responsible_party varchar(16) NOT NULL,
    loss_quantity decimal(18,4) NOT NULL,
    source_cost_price decimal(18,6) NOT NULL,
    loss_amount decimal(18,6) NOT NULL,
    allocation_damaged_quantity decimal(18,4) NOT NULL,
    allocation_written_off_before decimal(18,4) NOT NULL,
    allocation_written_off_after decimal(18,4) NOT NULL,
    stock_id bigint NOT NULL,
    stock_version_before bigint NOT NULL,
    stock_version_after bigint NOT NULL,
    stock_current_before decimal(18,4) NOT NULL,
    stock_current_after decimal(18,4) NOT NULL,
    stock_quarantine_before decimal(18,4) NOT NULL,
    stock_quarantine_after decimal(18,4) NOT NULL,
    stock_total_cost_before decimal(18,6) NOT NULL,
    stock_total_cost_after decimal(18,6) NOT NULL,
    balance_version_before bigint NOT NULL,
    balance_version_after bigint NOT NULL,
    balance_current_before decimal(18,4) NOT NULL,
    balance_current_after decimal(18,4) NOT NULL,
    balance_quarantine_before decimal(18,4) NOT NULL,
    balance_quarantine_after decimal(18,4) NOT NULL,
    balance_total_cost_before decimal(18,6) NOT NULL,
    balance_total_cost_after decimal(18,6) NOT NULL,
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
    create_by varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (damage_loss_ledger_id),
    UNIQUE KEY uk_transfer_discrepancy_damage_loss_request (request_id),
    UNIQUE KEY uk_transfer_discrepancy_damage_stock_request
        (stock_ledger_request_id),
    UNIQUE KEY uk_transfer_discrepancy_damage_loss_action_version
        (adjudication_action_id, action_version_before),
    KEY idx_transfer_discrepancy_damage_loss_case
        (discrepancy_case_id, damage_loss_ledger_id),
    KEY idx_transfer_discrepancy_damage_loss_allocation
        (receipt_allocation_id, damage_loss_ledger_id),
    KEY idx_transfer_discrepancy_damage_loss_balance
        (damaged_target_balance_id, damage_loss_ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨受损隔离库存不可变写销损失台账';

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_damage_loss_serial (
    damage_loss_serial_id bigint NOT NULL AUTO_INCREMENT,
    damage_loss_ledger_id bigint NOT NULL,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    adjudication_action_id bigint NOT NULL,
    receipt_serial_id bigint NOT NULL,
    serial_id bigint NOT NULL,
    serial_no_snapshot varchar(128) NOT NULL,
    warehouse_id bigint NOT NULL,
    balance_id bigint NOT NULL,
    lot_id bigint NOT NULL,
    location_id bigint NOT NULL,
    status_before varchar(30) NOT NULL,
    status_after varchar(30) NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (damage_loss_serial_id),
    UNIQUE KEY uk_transfer_discrepancy_damage_serial_receipt
        (receipt_serial_id),
    UNIQUE KEY uk_transfer_discrepancy_damage_serial_inventory (serial_id),
    KEY idx_transfer_discrepancy_damage_serial_ledger
        (damage_loss_ledger_id, damage_loss_serial_id),
    KEY idx_transfer_discrepancy_damage_serial_action
        (adjudication_action_id, damage_loss_serial_id),
    KEY idx_transfer_discrepancy_damage_serial_request
        (request_id, damage_loss_serial_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨受损写销不可变序列号消费台账';
