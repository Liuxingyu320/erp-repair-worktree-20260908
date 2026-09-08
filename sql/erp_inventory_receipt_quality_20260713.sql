-- 仓库管理优化：采购收货批次 + 逐行质检事实
-- 执行日期：2026-07-13
-- 部署顺序：先执行本脚本，再发布后端/前端。全部为增量结构，不改写历史业务数据。

CREATE TABLE IF NOT EXISTS inv_receipt_batch (
    batch_id            bigint       NOT NULL AUTO_INCREMENT COMMENT '收货批次ID',
    batch_no            varchar(40)  NOT NULL COMMENT '收货批次号',
    purchase_order_id   bigint       NOT NULL COMMENT '采购单ID',
    order_no            varchar(64)  DEFAULT NULL COMMENT '采购单号快照',
    warehouse_id        bigint       NOT NULL COMMENT '收货仓库ID',
    supplier_batch_no   varchar(100) DEFAULT NULL COMMENT '供应商批次号',
    delivery_note_no    varchar(100) DEFAULT NULL COMMENT '送货单号',
    arrived_time        datetime     NOT NULL COMMENT '到货时间',
    status              varchar(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/partial/completed',
    received_user_id    bigint       DEFAULT NULL COMMENT '收货人用户ID',
    received_by         varchar(64)  DEFAULT NULL COMMENT '收货人',
    total_quantity      decimal(18,4) NOT NULL DEFAULT 0 COMMENT '本批收货数量',
    pending_quantity    decimal(18,4) NOT NULL DEFAULT 0 COMMENT '本批待检数量',
    create_by           varchar(64)  DEFAULT NULL,
    create_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           varchar(64)  DEFAULT NULL,
    update_time         datetime     DEFAULT NULL,
    remark              varchar(500) DEFAULT NULL,
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_receipt_batch_no (batch_no),
    KEY idx_receipt_batch_order_status (purchase_order_id, status),
    KEY idx_receipt_batch_warehouse_time (warehouse_id, arrived_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购收货批次事实';

CREATE TABLE IF NOT EXISTS inv_receipt_batch_detail (
    batch_detail_id     bigint       NOT NULL AUTO_INCREMENT COMMENT '批次明细ID',
    batch_id            bigint       NOT NULL COMMENT '收货批次ID',
    purchase_order_id   bigint       NOT NULL COMMENT '采购单ID',
    purchase_detail_id  bigint       NOT NULL COMMENT '采购明细ID',
    item_type           varchar(20)  NOT NULL DEFAULT 'product' COMMENT '物料类型',
    item_id             bigint       NOT NULL COMMENT '物料ID',
    product_id          bigint       DEFAULT NULL COMMENT '兼容商品ID',
    item_code           varchar(100) DEFAULT NULL COMMENT '物料编码快照',
    item_name           varchar(200) DEFAULT NULL COMMENT '物料名称快照',
    unit                varchar(30)  DEFAULT NULL COMMENT '单位快照',
    received_quantity   decimal(18,4) NOT NULL DEFAULT 0 COMMENT '收货数量',
    pending_quantity    decimal(18,4) NOT NULL DEFAULT 0 COMMENT '待检数量',
    inspected_quantity  decimal(18,4) NOT NULL DEFAULT 0 COMMENT '累计已检数量',
    accepted_quantity   decimal(18,4) NOT NULL DEFAULT 0 COMMENT '累计合格数量',
    rejected_quantity   decimal(18,4) NOT NULL DEFAULT 0 COMMENT '累计拒收数量',
    concession_quantity decimal(18,4) NOT NULL DEFAULT 0 COMMENT '累计让步数量',
    PRIMARY KEY (batch_detail_id),
    UNIQUE KEY uk_receipt_batch_purchase_detail (batch_id, purchase_detail_id),
    KEY idx_receipt_detail_order (purchase_order_id, purchase_detail_id),
    KEY idx_receipt_detail_item (item_type, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购收货批次明细';

CREATE TABLE IF NOT EXISTS inv_quality_inspection (
    inspection_id       bigint       NOT NULL AUTO_INCREMENT COMMENT '质检记录ID',
    inspection_no       varchar(40)  NOT NULL COMMENT '质检单号',
    receipt_batch_id    bigint       NOT NULL COMMENT '收货批次ID',
    batch_detail_id     bigint       NOT NULL COMMENT '收货批次明细ID',
    purchase_order_id   bigint       NOT NULL COMMENT '采购单ID',
    purchase_detail_id  bigint       NOT NULL COMMENT '采购明细ID',
    inspected_quantity  decimal(18,4) NOT NULL COMMENT '本次检验数量',
    accepted_quantity   decimal(18,4) NOT NULL DEFAULT 0 COMMENT '本次合格数量',
    rejected_quantity   decimal(18,4) NOT NULL DEFAULT 0 COMMENT '本次拒收数量',
    concession_quantity decimal(18,4) NOT NULL DEFAULT 0 COMMENT '本次让步数量',
    conclusion          varchar(20)  NOT NULL COMMENT 'passed/rejected/concession',
    defect_level        varchar(20)  DEFAULT NULL COMMENT '缺陷等级',
    defect_reason       varchar(500) DEFAULT NULL COMMENT '缺陷/让步原因',
    inspector_user_id   bigint       DEFAULT NULL COMMENT '质检人用户ID',
    inspector_name      varchar(64)  DEFAULT NULL COMMENT '质检人',
    inspection_time     datetime     NOT NULL COMMENT '质检时间',
    create_by           varchar(64)  DEFAULT NULL,
    create_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark              varchar(500) DEFAULT NULL,
    PRIMARY KEY (inspection_id),
    UNIQUE KEY uk_quality_inspection_no (inspection_no),
    KEY idx_quality_batch_detail (receipt_batch_id, batch_detail_id),
    KEY idx_quality_order_time (purchase_order_id, inspection_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可覆盖的逐行质检事实';

CREATE TABLE IF NOT EXISTS inv_quality_inspection_attachment (
    attachment_id       bigint       NOT NULL AUTO_INCREMENT,
    inspection_id       bigint       NOT NULL COMMENT '质检记录ID',
    file_name           varchar(255) DEFAULT NULL,
    file_url            varchar(1000) NOT NULL,
    file_type           varchar(30)  DEFAULT NULL,
    create_by           varchar(64)  DEFAULT NULL,
    create_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attachment_id),
    KEY idx_quality_attachment_inspection (inspection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检附件';

DROP PROCEDURE IF EXISTS inv_add_receipt_quality_column;
DELIMITER $$
CREATE PROCEDURE inv_add_receipt_quality_column(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_definition varchar(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `',
                          p_column_name, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL inv_add_receipt_quality_column('inv_inbound_record', 'receipt_batch_id',
    'bigint DEFAULT NULL COMMENT ''收货批次ID'' AFTER warehouse_id');
CALL inv_add_receipt_quality_column('inv_inbound_record', 'receipt_batch_detail_id',
    'bigint DEFAULT NULL COMMENT ''收货批次明细ID'' AFTER receipt_batch_id');
CALL inv_add_receipt_quality_column('inv_inbound_record', 'quality_inspection_id',
    'bigint DEFAULT NULL COMMENT ''最后一次质检记录ID'' AFTER receipt_batch_detail_id');
CALL inv_add_receipt_quality_column('inv_inbound_record', 'inspected_quantity',
    'decimal(18,4) NOT NULL DEFAULT 0 COMMENT ''累计已检数量'' AFTER quantity');
CALL inv_add_receipt_quality_column('inv_inbound_record', 'accepted_quantity',
    'decimal(18,4) NOT NULL DEFAULT 0 COMMENT ''累计合格数量'' AFTER inspected_quantity');
CALL inv_add_receipt_quality_column('inv_inbound_record', 'rejected_quantity',
    'decimal(18,4) NOT NULL DEFAULT 0 COMMENT ''累计拒收数量'' AFTER accepted_quantity');
CALL inv_add_receipt_quality_column('inv_inbound_record', 'concession_quantity',
    'decimal(18,4) NOT NULL DEFAULT 0 COMMENT ''累计让步数量'' AFTER rejected_quantity');

DROP PROCEDURE IF EXISTS inv_add_receipt_quality_column;

DROP PROCEDURE IF EXISTS inv_add_receipt_quality_index;
DELIMITER $$
CREATE PROCEDURE inv_add_receipt_quality_index(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_columns varchar(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD INDEX `',
                          p_index_name, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL inv_add_receipt_quality_index('inv_inbound_record', 'idx_inbound_receipt_batch',
    'receipt_batch_id, receipt_batch_detail_id');
DROP PROCEDURE IF EXISTS inv_add_receipt_quality_index;

-- 发布后校验：应返回 0。0 表示没有批次数量分类不平。
SELECT COUNT(*) AS invalid_receipt_quality_quantity_count
FROM inv_receipt_batch_detail
WHERE received_quantity <> pending_quantity + inspected_quantity
   OR inspected_quantity <> accepted_quantity + rejected_quantity + concession_quantity;
