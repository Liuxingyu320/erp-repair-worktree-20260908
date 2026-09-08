-- ERP-NEW_2 V2 差异裁决退回隔离明细预留（MySQL 5.7）。
-- 只供未来 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不创建子调拨、不冻结库存、不修改任何既有运行环境。

CREATE TABLE IF NOT EXISTS inv_transfer_quarantine_reservation (
    quarantine_reservation_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    adjudication_id bigint NOT NULL,
    adjudication_action_id bigint NOT NULL,
    action_version_before bigint NOT NULL,
    child_transfer_id bigint NOT NULL,
    child_transfer_detail_id bigint NOT NULL,
    reservation_round int NOT NULL,
    receipt_id bigint NOT NULL,
    receipt_allocation_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    shipment_allocation_id bigint NOT NULL,
    stock_id bigint NOT NULL,
    quarantine_balance_id bigint NOT NULL,
    quarantine_lot_id bigint NOT NULL,
    quarantine_location_id bigint NOT NULL,
    item_type varchar(32) NOT NULL,
    item_id bigint NOT NULL,
    product_id bigint DEFAULT NULL,
    tracking_policy varchar(16) NOT NULL COMMENT 'lot/serial',
    reserved_quantity decimal(18,4) NOT NULL,
    consumed_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    released_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    source_cost_price decimal(18,6) NOT NULL,
    reserved_amount decimal(18,6) NOT NULL,
    disposed_quantity_before decimal(18,4) NOT NULL,
    disposed_quantity_after decimal(18,4) NOT NULL,
    stock_version_before bigint NOT NULL,
    stock_version_after bigint NOT NULL,
    balance_version_before bigint NOT NULL,
    balance_version_after bigint NOT NULL,
    decision_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/PARTIAL/CONSUMED/RELEASED/CLOSED',
    version bigint NOT NULL DEFAULT 0,
    operator_user_id bigint NOT NULL,
    operator_name varchar(64) NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) NOT NULL DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (quarantine_reservation_id),
    UNIQUE KEY uk_transfer_quarantine_reservation_request (request_id),
    UNIQUE KEY uk_transfer_quarantine_reservation_action
        (adjudication_action_id),
    UNIQUE KEY uk_transfer_quarantine_reservation_child
        (child_transfer_id, child_transfer_detail_id, reservation_round),
    KEY idx_transfer_quarantine_reservation_receipt
        (receipt_allocation_id, quarantine_reservation_id),
    KEY idx_transfer_quarantine_reservation_balance
        (quarantine_balance_id, status, quarantine_reservation_id),
    CONSTRAINT chk_transfer_quarantine_reservation_quantity CHECK (
        reserved_quantity > 0
        AND consumed_quantity >= 0
        AND released_quantity >= 0
        AND consumed_quantity + released_quantity <= reserved_quantity
        AND disposed_quantity_before >= 0
        AND disposed_quantity_after =
            disposed_quantity_before + reserved_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2差异退回子调拨隔离库存专属预留';

CREATE TABLE IF NOT EXISTS inv_transfer_quarantine_reservation_serial (
    quarantine_reservation_serial_id bigint NOT NULL AUTO_INCREMENT,
    quarantine_reservation_id bigint NOT NULL,
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
    PRIMARY KEY (quarantine_reservation_serial_id),
    UNIQUE KEY uk_transfer_quarantine_reservation_receipt_serial
        (receipt_serial_id),
    UNIQUE KEY uk_transfer_quarantine_reservation_serial (serial_id),
    KEY idx_transfer_quarantine_reservation_serial_owner
        (quarantine_reservation_id,
         quarantine_reservation_serial_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2差异退回隔离序列号唯一预留';
