-- ERP-NEW_2 V2 调拨多次部分收货加法结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不切换仓库模式、不启用 Gate、不写业务数据。

DROP PROCEDURE IF EXISTS erp_new_2_receipt_add_column;
DELIMITER $$
CREATE PROCEDURE erp_new_2_receipt_add_column(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @erp_new_2_receipt_ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD COLUMN `',
            p_column_name, '` ', p_definition);
        PREPARE erp_new_2_receipt_stmt FROM @erp_new_2_receipt_ddl;
        EXECUTE erp_new_2_receipt_stmt;
        DEALLOCATE PREPARE erp_new_2_receipt_stmt;
    END IF;
END$$
DELIMITER ;

CALL erp_new_2_receipt_add_column(
    'inv_transfer_shipment_allocation',
    'accepted_received_quantity',
    'decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''累计合格收货'' AFTER `allocated_quantity`');
CALL erp_new_2_receipt_add_column(
    'inv_transfer_shipment_allocation',
    'damaged_received_quantity',
    'decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''累计残损收货'' AFTER `accepted_received_quantity`');
CALL erp_new_2_receipt_add_column(
    'inv_transfer_shipment_allocation',
    'shortage_reported_quantity',
    'decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''累计显式短缺待裁决'' AFTER `damaged_received_quantity`');
CALL erp_new_2_receipt_add_column(
    'inv_transfer_shipment_allocation',
    'receipt_version',
    'bigint NOT NULL DEFAULT 0 COMMENT ''逐来源收货乐观锁版本'' AFTER `shortage_reported_quantity`');

CALL erp_new_2_receipt_add_column(
    'inv_inventory_lot',
    'source_lot_id',
    'bigint DEFAULT NULL COMMENT ''V2调拨来源批次'' AFTER `source_stock_id`');
CALL erp_new_2_receipt_add_column(
    'inv_inventory_lot',
    'receipt_disposition',
    'varchar(16) DEFAULT NULL COMMENT ''accepted/damaged'' AFTER `source_lot_id`');

CALL erp_new_2_receipt_add_column(
    'inv_stock',
    'quarantine_quantity',
    'decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''隔离库存数量'' AFTER `available_quantity`');
CALL erp_new_2_receipt_add_column(
    'inv_stock_balance_detail',
    'quarantine_quantity',
    'decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''隔离明细数量'' AFTER `available_quantity`');

CALL erp_new_2_receipt_add_column(
    'inv_stock_ledger_detail',
    'receipt_allocation_id',
    'bigint DEFAULT NULL COMMENT ''V2收货分配ID'' AFTER `shipment_allocation_id`');
CALL erp_new_2_receipt_add_column(
    'inv_stock_ledger_detail',
    'receipt_disposition',
    'varchar(16) DEFAULT NULL COMMENT ''accepted/damaged'' AFTER `receipt_allocation_id`');

DROP PROCEDURE erp_new_2_receipt_add_column;

CREATE TABLE IF NOT EXISTS inv_transfer_shipment_receipt (
    receipt_id bigint NOT NULL AUTO_INCREMENT,
    shipment_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    receipt_no varchar(64) NOT NULL,
    command_request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receipt_plan_version char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_shipment_plan_version char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_reconcile_batch varchar(64) NOT NULL,
    target_warehouse_id bigint NOT NULL,
    target_reconcile_batch varchar(64) NOT NULL,
    arrived_time datetime NOT NULL,
    finalize_shipment char(1) NOT NULL DEFAULT '0',
    accepted_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    damaged_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    shortage_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    remaining_quantity_after decimal(18,4) NOT NULL DEFAULT '0.0000',
    receipt_status varchar(24) NOT NULL
        COMMENT 'partial/completed/discrepancy',
    operator_user_id bigint DEFAULT NULL,
    operator_name varchar(64) NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (receipt_id),
    UNIQUE KEY uk_transfer_receipt_no (receipt_no),
    UNIQUE KEY uk_transfer_receipt_request (command_request_id),
    KEY idx_transfer_receipt_shipment (shipment_id, receipt_id),
    KEY idx_transfer_receipt_transfer (transfer_id, receipt_id),
    KEY idx_transfer_receipt_status (receipt_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨不可变收货批次';

CREATE TABLE IF NOT EXISTS inv_transfer_shipment_receipt_allocation (
    receipt_allocation_id bigint NOT NULL AUTO_INCREMENT,
    receipt_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    shipment_allocation_id bigint NOT NULL,
    shipment_detail_id bigint NOT NULL,
    transfer_detail_id bigint NOT NULL,
    item_type varchar(20) NOT NULL,
    item_id bigint NOT NULL,
    product_id bigint DEFAULT NULL,
    tracking_policy varchar(16) NOT NULL,
    source_balance_id bigint NOT NULL,
    source_lot_id bigint NOT NULL,
    source_location_id bigint NOT NULL,
    cost_price decimal(18,6) NOT NULL,
    accepted_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    damaged_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    shortage_quantity decimal(18,4) NOT NULL DEFAULT '0.0000',
    accepted_cost decimal(18,6) NOT NULL DEFAULT '0.000000',
    damaged_cost decimal(18,6) NOT NULL DEFAULT '0.000000',
    allocation_version_before bigint NOT NULL,
    allocation_version_after bigint NOT NULL,
    accepted_location_id bigint DEFAULT NULL,
    accepted_target_lot_id bigint DEFAULT NULL,
    accepted_target_balance_id bigint DEFAULT NULL,
    accepted_balance_version_before bigint DEFAULT NULL,
    accepted_balance_version_after bigint DEFAULT NULL,
    quarantine_location_id bigint DEFAULT NULL,
    damaged_target_lot_id bigint DEFAULT NULL,
    damaged_target_balance_id bigint DEFAULT NULL,
    damaged_balance_version_before bigint DEFAULT NULL,
    damaged_balance_version_after bigint DEFAULT NULL,
    discrepancy_note varchar(500) DEFAULT NULL,
    attachment_refs varchar(2000) DEFAULT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (receipt_allocation_id),
    UNIQUE KEY uk_transfer_receipt_allocation
        (receipt_id, shipment_allocation_id),
    KEY idx_transfer_receipt_alloc_source
        (shipment_allocation_id, receipt_id),
    KEY idx_transfer_receipt_alloc_accepted
        (accepted_target_balance_id, receipt_allocation_id),
    KEY idx_transfer_receipt_alloc_damaged
        (damaged_target_balance_id, receipt_allocation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨收货逐来源分配不可变审计';

CREATE TABLE IF NOT EXISTS inv_transfer_shipment_receipt_serial (
    receipt_serial_id bigint NOT NULL AUTO_INCREMENT,
    receipt_id bigint NOT NULL,
    receipt_allocation_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    shipment_allocation_id bigint NOT NULL,
    shipment_serial_id bigint NOT NULL,
    serial_id bigint NOT NULL,
    serial_no_snapshot varchar(128) NOT NULL,
    disposition varchar(16) NOT NULL
        COMMENT 'accepted/damaged/shortage',
    source_balance_id bigint NOT NULL,
    source_lot_id bigint NOT NULL,
    source_location_id bigint NOT NULL,
    target_balance_id bigint DEFAULT NULL,
    target_lot_id bigint DEFAULT NULL,
    target_location_id bigint DEFAULT NULL,
    status_before varchar(30) NOT NULL,
    status_after varchar(30) NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (receipt_serial_id),
    UNIQUE KEY uk_transfer_receipt_serial (receipt_id, serial_id),
    UNIQUE KEY uk_transfer_receipt_serial_once
        (shipment_id, shipment_serial_id),
    KEY idx_transfer_receipt_serial_allocation
        (receipt_allocation_id, receipt_serial_id),
    KEY idx_transfer_receipt_serial_shipment
        (shipment_id, serial_id, receipt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨收货序列号不可变处置审计';

DROP PROCEDURE IF EXISTS erp_new_2_receipt_add_index;
DELIMITER $$
CREATE PROCEDURE erp_new_2_receipt_add_index(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_index_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @erp_new_2_receipt_ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD ',
            p_index_definition);
        PREPARE erp_new_2_receipt_stmt FROM @erp_new_2_receipt_ddl;
        EXECUTE erp_new_2_receipt_stmt;
        DEALLOCATE PREPARE erp_new_2_receipt_stmt;
    END IF;
END$$
DELIMITER ;

CALL erp_new_2_receipt_add_index(
    'inv_transfer_shipment_allocation',
    'idx_transfer_shipment_allocation_receipt',
    'INDEX `idx_transfer_shipment_allocation_receipt` (`shipment_id`, `receipt_version`, `allocation_id`)');
CALL erp_new_2_receipt_add_index(
    'inv_inventory_lot',
    'uk_inventory_lot_transfer_receipt',
    'UNIQUE KEY `uk_inventory_lot_transfer_receipt` (`warehouse_id`, `item_type`, `item_id`, `source_lot_id`, `receipt_disposition`)');
CALL erp_new_2_receipt_add_index(
    'inv_stock_ledger_detail',
    'uk_stock_ledger_receipt_disposition',
    'UNIQUE KEY `uk_stock_ledger_receipt_disposition` (`receipt_allocation_id`, `receipt_disposition`)');
CALL erp_new_2_receipt_add_index(
    'inv_stock_ledger_detail',
    'idx_stock_ledger_receipt',
    'INDEX `idx_stock_ledger_receipt` (`business_id`, `receipt_allocation_id`)');
CALL erp_new_2_receipt_add_index(
    'inv_transfer_shipment_receipt_serial',
    'uk_transfer_receipt_serial_once',
    'UNIQUE KEY `uk_transfer_receipt_serial_once` (`shipment_id`, `shipment_serial_id`)');

DROP PROCEDURE erp_new_2_receipt_add_index;
