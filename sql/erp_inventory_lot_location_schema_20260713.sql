-- 仓库批次/库位/序列号增量模型
-- 默认不切换任何仓库；所有仓库初始为 legacy/legacy。
-- 执行前置：先发布 erp_inventory_receipt_quality_20260713.sql。

CREATE TABLE IF NOT EXISTS inv_warehouse_zone (
    zone_id             bigint       NOT NULL AUTO_INCREMENT,
    warehouse_id        bigint       NOT NULL COMMENT '仓库组织ID',
    zone_code            varchar(64)  NOT NULL COMMENT '库区编码',
    zone_name            varchar(128) NOT NULL COMMENT '库区名称',
    zone_type            varchar(30)  NOT NULL DEFAULT 'storage' COMMENT 'receiving/storage/quarantine/shipping/virtual',
    sort_order           int          NOT NULL DEFAULT 0,
    status               char(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    is_virtual           char(1)      NOT NULL DEFAULT '0',
    create_by            varchar(64)  DEFAULT NULL,
    create_time          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            varchar(64)  DEFAULT NULL,
    update_time          datetime     DEFAULT NULL,
    remark               varchar(500) DEFAULT NULL,
    PRIMARY KEY (zone_id),
    UNIQUE KEY uk_warehouse_zone_code (warehouse_id, zone_code),
    KEY idx_warehouse_zone_status (warehouse_id, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库库区';

CREATE TABLE IF NOT EXISTS inv_warehouse_location (
    location_id          bigint       NOT NULL AUTO_INCREMENT,
    warehouse_id         bigint       NOT NULL COMMENT '仓库组织ID',
    zone_id              bigint       NOT NULL COMMENT '库区ID',
    location_code        varchar(64)  NOT NULL COMMENT '库位编码',
    location_name        varchar(128) NOT NULL COMMENT '库位名称',
    barcode              varchar(128) DEFAULT NULL COMMENT '扫码码值',
    location_type        varchar(30)  NOT NULL DEFAULT 'storage' COMMENT 'receiving/storage/quarantine/shipping/virtual',
    capacity_quantity    decimal(18,4) DEFAULT NULL,
    sort_order           int          NOT NULL DEFAULT 0,
    status               char(1)      NOT NULL DEFAULT '0',
    is_virtual           char(1)      NOT NULL DEFAULT '0',
    create_by            varchar(64)  DEFAULT NULL,
    create_time          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            varchar(64)  DEFAULT NULL,
    update_time          datetime     DEFAULT NULL,
    remark               varchar(500) DEFAULT NULL,
    PRIMARY KEY (location_id),
    UNIQUE KEY uk_warehouse_location_code (warehouse_id, location_code),
    UNIQUE KEY uk_warehouse_location_barcode (warehouse_id, barcode),
    KEY idx_warehouse_location_zone (zone_id, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库库位';

CREATE TABLE IF NOT EXISTS inv_inventory_lot (
    lot_id               bigint       NOT NULL AUTO_INCREMENT,
    lot_no               varchar(100) NOT NULL COMMENT '系统批次号',
    item_type            varchar(20)  NOT NULL DEFAULT 'product',
    item_id              bigint       NOT NULL,
    product_id           bigint       DEFAULT NULL COMMENT '兼容商品ID',
    warehouse_id         bigint       NOT NULL,
    receipt_batch_id     bigint       DEFAULT NULL COMMENT '收货批次ID',
    supplier_batch_no    varchar(100) DEFAULT NULL,
    production_date      date         DEFAULT NULL,
    expiry_date          date         DEFAULT NULL,
    qc_status            varchar(20)  NOT NULL DEFAULT 'passed' COMMENT 'pending/passed/concession/rejected/quarantine',
    lot_status           varchar(20)  NOT NULL DEFAULT 'active' COMMENT 'active/frozen/expired/closed',
    source_type          varchar(30)  DEFAULT NULL COMMENT 'purchase/transfer/return/history',
    source_business_id   bigint       DEFAULT NULL,
    source_stock_id      bigint       DEFAULT NULL COMMENT '历史回填来源 inv_stock.stock_id',
    create_by            varchar(64)  DEFAULT NULL,
    create_time          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            varchar(64)  DEFAULT NULL,
    update_time          datetime     DEFAULT NULL,
    remark               varchar(500) DEFAULT NULL,
    PRIMARY KEY (lot_id),
    UNIQUE KEY uk_inventory_lot_no (warehouse_id, item_type, item_id, lot_no),
    UNIQUE KEY uk_inventory_lot_source_stock (source_stock_id),
    KEY idx_inventory_lot_fefo (warehouse_id, item_type, item_id, qc_status, lot_status, expiry_date, create_time),
    KEY idx_inventory_lot_receipt (receipt_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存批次与效期';

CREATE TABLE IF NOT EXISTS inv_stock_balance_detail (
    balance_id           bigint        NOT NULL AUTO_INCREMENT,
    item_type            varchar(20)   NOT NULL DEFAULT 'product',
    item_id              bigint        NOT NULL,
    product_id           bigint        DEFAULT NULL,
    warehouse_id         bigint        NOT NULL,
    lot_id               bigint        NOT NULL,
    location_id          bigint        NOT NULL,
    current_quantity     decimal(18,4) NOT NULL DEFAULT 0,
    locked_quantity      decimal(18,4) NOT NULL DEFAULT 0,
    available_quantity   decimal(18,4) NOT NULL DEFAULT 0,
    cost_price           decimal(18,6) NOT NULL DEFAULT 0,
    total_cost           decimal(18,6) NOT NULL DEFAULT 0,
    version              bigint        NOT NULL DEFAULT 0,
    source_stock_id      bigint        DEFAULT NULL COMMENT '历史回填来源 inv_stock.stock_id',
    last_in_time         datetime      DEFAULT NULL,
    last_out_time        datetime      DEFAULT NULL,
    create_by            varchar(64)   DEFAULT NULL,
    create_time          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            varchar(64)   DEFAULT NULL,
    update_time          datetime      DEFAULT NULL,
    remark               varchar(500)  DEFAULT NULL,
    PRIMARY KEY (balance_id),
    UNIQUE KEY uk_stock_balance_dimension (warehouse_id, item_type, item_id, lot_id, location_id),
    UNIQUE KEY uk_stock_balance_source_stock (source_stock_id),
    KEY idx_stock_balance_allocation (warehouse_id, item_type, item_id, available_quantity, lot_id, location_id),
    KEY idx_stock_balance_location (warehouse_id, location_id, current_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次库位明细库存余额';

CREATE TABLE IF NOT EXISTS inv_inventory_serial (
    serial_id            bigint       NOT NULL AUTO_INCREMENT,
    serial_no            varchar(128) NOT NULL,
    item_type            varchar(20)  NOT NULL DEFAULT 'product',
    item_id              bigint       NOT NULL,
    product_id           bigint       DEFAULT NULL,
    warehouse_id         bigint       NOT NULL,
    lot_id               bigint       NOT NULL,
    location_id          bigint       NOT NULL,
    balance_id           bigint       NOT NULL,
    serial_status        varchar(30)  NOT NULL DEFAULT 'available' COMMENT 'pending_qc/available/allocated/picked/shipped/quarantine/scrapped',
    source_type          varchar(30)  DEFAULT NULL,
    source_business_id   bigint       DEFAULT NULL,
    create_by            varchar(64)  DEFAULT NULL,
    create_time          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            varchar(64)  DEFAULT NULL,
    update_time          datetime     DEFAULT NULL,
    remark               varchar(500) DEFAULT NULL,
    PRIMARY KEY (serial_id),
    UNIQUE KEY uk_inventory_serial (item_type, serial_no),
    KEY idx_inventory_serial_position (warehouse_id, location_id, serial_status),
    KEY idx_inventory_serial_lot (lot_id, serial_status),
    KEY idx_inventory_serial_balance (balance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存序列号单件事实';

CREATE TABLE IF NOT EXISTS inv_stock_ledger_detail (
    ledger_id            bigint        NOT NULL AUTO_INCREMENT,
    request_id           varchar(100)  NOT NULL COMMENT '幂等请求ID',
    summary_stock_log_id bigint        DEFAULT NULL COMMENT '兼容汇总流水ID',
    warehouse_id         bigint        NOT NULL,
    item_type            varchar(20)   NOT NULL DEFAULT 'product',
    item_id              bigint        NOT NULL,
    product_id           bigint        DEFAULT NULL,
    movement_type        varchar(40)   NOT NULL,
    business_type        varchar(40)   DEFAULT NULL,
    business_id          bigint        DEFAULT NULL,
    business_no          varchar(100)  DEFAULT NULL,
    from_balance_id      bigint        DEFAULT NULL,
    from_lot_id          bigint        DEFAULT NULL,
    from_location_id     bigint        DEFAULT NULL,
    to_balance_id        bigint        DEFAULT NULL,
    to_lot_id            bigint        DEFAULT NULL,
    to_location_id       bigint        DEFAULT NULL,
    change_quantity      decimal(18,4) NOT NULL,
    before_quantity      decimal(18,4) DEFAULT NULL,
    after_quantity       decimal(18,4) DEFAULT NULL,
    cost_price           decimal(18,6) DEFAULT NULL,
    total_cost           decimal(18,6) DEFAULT NULL,
    operator_user_id     bigint        DEFAULT NULL,
    operator_name        varchar(64)   DEFAULT NULL,
    occurred_time        datetime      NOT NULL,
    create_by            varchar(64)   DEFAULT NULL,
    create_time          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark               varchar(500)  DEFAULT NULL,
    PRIMARY KEY (ledger_id),
    UNIQUE KEY uk_stock_ledger_request (request_id),
    KEY idx_stock_ledger_item_time (warehouse_id, item_type, item_id, occurred_time),
    KEY idx_stock_ledger_business (business_type, business_id),
    KEY idx_stock_ledger_lot_location (from_lot_id, from_location_id, to_lot_id, to_location_id),
    KEY idx_stock_ledger_summary_log (summary_stock_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变明细库存流水';

CREATE TABLE IF NOT EXISTS inv_warehouse_task (
    task_id              bigint       NOT NULL AUTO_INCREMENT,
    task_no              varchar(40)  NOT NULL,
    warehouse_id         bigint       NOT NULL,
    task_type            varchar(30)  NOT NULL COMMENT 'putaway/move/pick/review/ship/count',
    task_status          varchar(30)  NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/completed/cancelled/exception',
    source_type          varchar(40)  DEFAULT NULL,
    source_business_id   bigint       DEFAULT NULL,
    source_business_no   varchar(100) DEFAULT NULL,
    idempotency_key      varchar(100) NOT NULL,
    assignee_user_id     bigint       DEFAULT NULL,
    assignee_name        varchar(64)  DEFAULT NULL,
    deadline             datetime     DEFAULT NULL,
    started_time         datetime     DEFAULT NULL,
    completed_time       datetime     DEFAULT NULL,
    create_by            varchar(64)  DEFAULT NULL,
    create_time          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            varchar(64)  DEFAULT NULL,
    update_time          datetime     DEFAULT NULL,
    remark               varchar(500) DEFAULT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_warehouse_task_no (task_no),
    UNIQUE KEY uk_warehouse_task_idempotency (warehouse_id, idempotency_key),
    KEY idx_warehouse_task_todo (warehouse_id, task_status, task_type, deadline),
    KEY idx_warehouse_task_source (source_type, source_business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库作业任务';

CREATE TABLE IF NOT EXISTS inv_warehouse_task_detail (
    task_detail_id       bigint        NOT NULL AUTO_INCREMENT,
    task_id              bigint        NOT NULL,
    item_type            varchar(20)   NOT NULL DEFAULT 'product',
    item_id              bigint        NOT NULL,
    product_id           bigint        DEFAULT NULL,
    lot_id               bigint        DEFAULT NULL,
    serial_id            bigint        DEFAULT NULL,
    from_location_id     bigint        DEFAULT NULL,
    to_location_id       bigint        DEFAULT NULL,
    planned_quantity     decimal(18,4) NOT NULL,
    processed_quantity   decimal(18,4) NOT NULL DEFAULT 0,
    exception_quantity   decimal(18,4) NOT NULL DEFAULT 0,
    detail_status        varchar(30)   NOT NULL DEFAULT 'pending',
    version              bigint        NOT NULL DEFAULT 0,
    create_time          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          datetime      DEFAULT NULL,
    remark               varchar(500)  DEFAULT NULL,
    PRIMARY KEY (task_detail_id),
    KEY idx_warehouse_task_detail_task (task_id, detail_status),
    KEY idx_warehouse_task_detail_item (item_type, item_id, lot_id),
    KEY idx_warehouse_task_detail_serial (serial_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库作业任务明细';

CREATE TABLE IF NOT EXISTS inv_warehouse_stock_mode (
    warehouse_id          bigint       NOT NULL,
    write_mode            varchar(20)  NOT NULL DEFAULT 'legacy' COMMENT 'legacy/dual',
    read_mode             varchar(20)  NOT NULL DEFAULT 'legacy' COMMENT 'legacy/shadow/detail',
    reconcile_status      varchar(20)  NOT NULL DEFAULT 'not_run' COMMENT 'not_run/passed/failed',
    last_reconcile_time   datetime     DEFAULT NULL,
    last_reconcile_batch  varchar(64)  DEFAULT NULL,
    approved_by           varchar(64)  DEFAULT NULL,
    approved_time         datetime     DEFAULT NULL,
    create_by             varchar(64)  DEFAULT NULL,
    create_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by             varchar(64)  DEFAULT NULL,
    update_time           datetime     DEFAULT NULL,
    remark                varchar(500) DEFAULT NULL,
    PRIMARY KEY (warehouse_id),
    KEY idx_warehouse_stock_mode (write_mode, read_mode, reconcile_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逐仓库批次库存灰度模式';

CREATE TABLE IF NOT EXISTS inv_warehouse_stock_mode_history (
    history_id           bigint       NOT NULL AUTO_INCREMENT,
    change_batch_no      varchar(64)  NOT NULL,
    warehouse_id         bigint       NOT NULL,
    old_write_mode       varchar(20)  DEFAULT NULL,
    old_read_mode        varchar(20)  DEFAULT NULL,
    new_write_mode       varchar(20)  NOT NULL,
    new_read_mode        varchar(20)  NOT NULL,
    change_reason        varchar(500) NOT NULL,
    changed_by           varchar(64)  NOT NULL,
    changed_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    KEY idx_stock_mode_history_warehouse (warehouse_id, changed_time),
    KEY idx_stock_mode_history_batch (change_batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库库存模式切换审计';

CREATE TABLE IF NOT EXISTS inv_lot_location_migration_run (
    run_id                bigint       NOT NULL AUTO_INCREMENT,
    run_batch_no          varchar(64)  NOT NULL,
    target_warehouse_id   bigint       DEFAULT NULL,
    run_type              varchar(30)  NOT NULL COMMENT 'backfill/rollback/mode_change',
    run_status            varchar(30)  NOT NULL COMMENT 'completed/blocked/failed',
    blocker_count         int          NOT NULL DEFAULT 0,
    source_stock_count    int          NOT NULL DEFAULT 0,
    detail_balance_count  int          NOT NULL DEFAULT 0,
    executed_by           varchar(64)  NOT NULL,
    executed_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark                varchar(1000) DEFAULT NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uk_lot_location_migration_run (run_batch_no),
    KEY idx_lot_location_migration_target (target_warehouse_id, executed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次库位迁移执行审计';

-- 只为已有库存的仓库初始化兼容模式，不启用双写或新读。
INSERT INTO inv_warehouse_stock_mode (
    warehouse_id, write_mode, read_mode, reconcile_status,
    create_by, create_time, remark
)
SELECT DISTINCT s.warehouse_id, 'legacy', 'legacy', 'not_run',
       'migration-20260713', NOW(), '增量模型初始化，未切换'
FROM inv_stock s
JOIN sys_dept d ON d.dept_id = s.warehouse_id AND d.dept_type = 'WAREHOUSE'
WHERE s.warehouse_id IS NOT NULL
ON DUPLICATE KEY UPDATE warehouse_id = VALUES(warehouse_id);

SELECT write_mode, read_mode, COUNT(*) AS warehouse_count
FROM inv_warehouse_stock_mode
GROUP BY write_mode, read_mode;
