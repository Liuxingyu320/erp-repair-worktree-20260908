-- ERP-NEW_2 V2 调拨发货审计与批次库位扣减结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、不切换仓库模式、不写业务数据。

ALTER TABLE inv_transfer_shipment_detail
    MODIFY COLUMN planned_quantity decimal(18,4) DEFAULT '0.0000' COMMENT '审批/计划数量',
    MODIFY COLUMN shipped_quantity decimal(18,4) DEFAULT '0.0000' COMMENT '本批发货数量',
    MODIFY COLUMN received_quantity decimal(18,4) DEFAULT '0.0000' COMMENT '累计收货数量',
    MODIFY COLUMN cost_price decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '发货加权单位成本';

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_shipment'
      AND column_name = 'inventory_write_version'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE inv_transfer_shipment ADD COLUMN inventory_write_version varchar(16) NOT NULL DEFAULT ''LEGACY'' COMMENT ''LEGACY/V2_DETAIL'' AFTER source_location_dept_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_shipment'
      AND column_name = 'command_request_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE inv_transfer_shipment ADD COLUMN command_request_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT ''V2根幂等请求ID'' AFTER inventory_write_version',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_shipment'
      AND column_name = 'plan_version'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE inv_transfer_shipment ADD COLUMN plan_version char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT ''事务内重算规划SHA-256'' AFTER command_request_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_shipment'
      AND column_name = 'sealed_revision_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE inv_transfer_shipment ADD COLUMN sealed_revision_id bigint(20) DEFAULT NULL COMMENT ''已审批封存修订ID'' AFTER plan_version',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_shipment'
      AND column_name = 'reconcile_batch'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE inv_transfer_shipment ADD COLUMN reconcile_batch varchar(64) DEFAULT NULL COMMENT ''来源仓权威对账批次快照'' AFTER sealed_revision_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_shipment'
      AND index_name = 'uk_inv_transfer_shipment_command_request'
);
SET @ddl := IF(@index_exists = 0,
    'ALTER TABLE inv_transfer_shipment ADD UNIQUE KEY uk_inv_transfer_shipment_command_request (command_request_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_transfer_shipment'
      AND index_name = 'idx_inv_transfer_shipment_v2_plan'
);
SET @ddl := IF(@index_exists = 0,
    'ALTER TABLE inv_transfer_shipment ADD KEY idx_inv_transfer_shipment_v2_plan (inventory_write_version, plan_version, sealed_revision_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS inv_transfer_shipment_allocation (
    allocation_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '发货分配ID',
    shipment_id bigint(20) NOT NULL,
    shipment_detail_id bigint(20) NOT NULL,
    transfer_id bigint(20) NOT NULL,
    transfer_detail_id bigint(20) NOT NULL,
    balance_id bigint(20) NOT NULL,
    lot_id bigint(20) NOT NULL,
    location_id bigint(20) NOT NULL,
    policy_rank int NOT NULL,
    allocation_policy varchar(16) NOT NULL COMMENT 'FEFO/FIFO',
    tracking_policy varchar(16) NOT NULL COMMENT 'lot/serial',
    allocated_quantity decimal(18,4) NOT NULL,
    balance_version_before bigint(20) NOT NULL,
    balance_version_after bigint(20) NOT NULL,
    before_quantity decimal(18,4) NOT NULL,
    after_quantity decimal(18,4) NOT NULL,
    cost_price decimal(18,6) NOT NULL,
    total_cost decimal(18,6) NOT NULL,
    summary_stock_log_id bigint(20) NOT NULL,
    command_request_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_id),
    UNIQUE KEY uk_inv_transfer_shipment_allocation_balance (shipment_detail_id, balance_id),
    UNIQUE KEY uk_inv_transfer_shipment_allocation_rank (shipment_detail_id, policy_rank),
    KEY idx_inv_transfer_shipment_allocation_transfer (transfer_id, transfer_detail_id),
    KEY idx_inv_transfer_shipment_allocation_lot_location (lot_id, location_id),
    KEY idx_inv_transfer_shipment_allocation_request (command_request_id, allocation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V2调拨发货批次库位不可变分配';

CREATE TABLE IF NOT EXISTS inv_transfer_shipment_serial (
    shipment_serial_id bigint(20) NOT NULL AUTO_INCREMENT,
    allocation_id bigint(20) NOT NULL,
    shipment_id bigint(20) NOT NULL,
    serial_id bigint(20) NOT NULL,
    serial_no_snapshot varchar(128) NOT NULL,
    item_type varchar(20) NOT NULL,
    item_id bigint(20) NOT NULL,
    source_balance_id bigint(20) NOT NULL,
    source_lot_id bigint(20) NOT NULL,
    source_location_id bigint(20) NOT NULL,
    status_before varchar(30) NOT NULL,
    status_after varchar(30) NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (shipment_serial_id),
    UNIQUE KEY uk_inv_transfer_shipment_serial (shipment_id, serial_id),
    KEY idx_inv_transfer_shipment_serial_allocation (allocation_id, shipment_serial_id),
    KEY idx_inv_transfer_shipment_serial_source (source_balance_id, source_lot_id, source_location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V2调拨发货序列号不可变关联';

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_stock_ledger_detail'
      AND column_name = 'command_request_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE inv_stock_ledger_detail ADD COLUMN command_request_id varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT ''根幂等请求ID'' AFTER request_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_stock_ledger_detail'
      AND column_name = 'shipment_allocation_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE inv_stock_ledger_detail ADD COLUMN shipment_allocation_id bigint(20) DEFAULT NULL COMMENT ''V2发货分配ID'' AFTER command_request_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_stock_ledger_detail'
      AND index_name = 'uk_inv_stock_ledger_shipment_allocation'
);
SET @ddl := IF(@index_exists = 0,
    'ALTER TABLE inv_stock_ledger_detail ADD UNIQUE KEY uk_inv_stock_ledger_shipment_allocation (shipment_allocation_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inv_stock_ledger_detail'
      AND index_name = 'idx_inv_stock_ledger_command_request'
);
SET @ddl := IF(@index_exists = 0,
    'ALTER TABLE inv_stock_ledger_detail ADD KEY idx_inv_stock_ledger_command_request (command_request_id, ledger_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
