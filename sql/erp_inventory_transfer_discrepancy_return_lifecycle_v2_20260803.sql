-- ERP-NEW_2 V2 差异退回隔离预留生命周期证据（MySQL 5.7）。
-- 只供未来 ERP-NEW_2 专属数据库按顺序 roll-forward；本文件不连接
-- 数据库、不消费或释放库存、不修改任何既有运行环境。

CREATE TABLE IF NOT EXISTS
    inv_transfer_quarantine_reservation_consumption (
    consumption_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quarantine_reservation_id bigint NOT NULL,
    adjudication_action_id bigint NOT NULL,
    child_transfer_id bigint NOT NULL,
    child_transfer_detail_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    shipment_detail_id bigint NOT NULL,
    consumed_quantity decimal(18,4) NOT NULL,
    consumed_amount decimal(18,6) NOT NULL,
    stock_version_before bigint NOT NULL,
    stock_version_after bigint NOT NULL,
    balance_version_before bigint NOT NULL,
    balance_version_after bigint NOT NULL,
    operator_name varchar(64) NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumption_id),
    UNIQUE KEY uk_transfer_quarantine_consumption_request (request_id),
    UNIQUE KEY uk_transfer_quarantine_consumption_shipment_detail
        (quarantine_reservation_id, shipment_detail_id),
    KEY idx_transfer_quarantine_consumption_shipment
        (shipment_id, consumption_id),
    CONSTRAINT chk_transfer_quarantine_consumption CHECK (
        consumed_quantity > 0
        AND consumed_amount >= 0
        AND stock_version_after = stock_version_before + 1
        AND balance_version_after = balance_version_before + 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2差异退回隔离预留不可变消费事件';

CREATE TABLE IF NOT EXISTS inv_transfer_quarantine_reservation_release (
    release_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quarantine_reservation_id bigint NOT NULL,
    adjudication_action_id bigint NOT NULL,
    child_transfer_id bigint NOT NULL,
    child_transfer_detail_id bigint NOT NULL,
    reason_code varchar(32) NOT NULL,
    reason_reference varchar(128) NOT NULL,
    released_quantity decimal(18,4) NOT NULL,
    stock_version_before bigint NOT NULL,
    stock_version_after bigint NOT NULL,
    balance_version_before bigint NOT NULL,
    balance_version_after bigint NOT NULL,
    operator_name varchar(64) NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (release_id),
    UNIQUE KEY uk_transfer_quarantine_release_request (request_id),
    UNIQUE KEY uk_transfer_quarantine_release_reservation
        (quarantine_reservation_id),
    KEY idx_transfer_quarantine_release_action
        (adjudication_action_id, release_id),
    CONSTRAINT chk_transfer_quarantine_release CHECK (
        released_quantity > 0
        AND stock_version_after = stock_version_before + 1
        AND balance_version_after = balance_version_before + 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2差异退回隔离预留不可变释放事件';

ALTER TABLE inv_transfer_quarantine_reservation_serial
    ADD COLUMN lifecycle_status varchar(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/CONSUMED/RELEASED' AFTER status_after,
    ADD COLUMN consumption_id bigint DEFAULT NULL
        AFTER lifecycle_status,
    ADD COLUMN release_id bigint DEFAULT NULL AFTER consumption_id,
    ADD COLUMN consumed_shipment_id bigint DEFAULT NULL AFTER release_id,
    ADD COLUMN version bigint NOT NULL DEFAULT 0
        AFTER consumed_shipment_id,
    ADD COLUMN update_by varchar(64) NOT NULL DEFAULT '' AFTER version,
    ADD COLUMN update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER update_by,
    ADD UNIQUE KEY uk_transfer_quarantine_serial_consumption
        (consumption_id, serial_id),
    ADD UNIQUE KEY uk_transfer_quarantine_serial_release
        (release_id, serial_id),
    ADD KEY idx_transfer_quarantine_serial_lifecycle
        (quarantine_reservation_id, lifecycle_status,
         receipt_serial_id, serial_id);
