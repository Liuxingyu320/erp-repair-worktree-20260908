-- Inventory generic item upgrade: product / OE / gift box.
-- Existing rows remain product rows; product_id is retained for backward compatibility.

DROP PROCEDURE IF EXISTS inv_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE inv_add_column_if_missing(
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
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL inv_add_column_if_missing('inv_stock', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/oe/gift'' AFTER `stock_id`');
CALL inv_add_column_if_missing('inv_stock', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');
CALL inv_add_column_if_missing('inv_stock_log', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/oe/gift'' AFTER `log_id`');
CALL inv_add_column_if_missing('inv_stock_log', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');

CALL inv_add_column_if_missing('inv_purchase_detail', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/oe/gift'' AFTER `order_id`');
CALL inv_add_column_if_missing('inv_purchase_detail', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');
CALL inv_add_column_if_missing('inv_purchase_detail', 'item_code',
    'varchar(64) DEFAULT '''' COMMENT ''物料编码快照'' AFTER `item_id`');
CALL inv_add_column_if_missing('inv_purchase_detail', 'item_name',
    'varchar(128) DEFAULT '''' COMMENT ''物料名称快照'' AFTER `item_code`');

CALL inv_add_column_if_missing('inv_sales_detail', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/gift'' AFTER `order_id`');
CALL inv_add_column_if_missing('inv_sales_detail', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');
CALL inv_add_column_if_missing('inv_sales_detail', 'item_code',
    'varchar(64) DEFAULT '''' COMMENT ''物料编码快照'' AFTER `item_id`');
CALL inv_add_column_if_missing('inv_sales_detail', 'item_name',
    'varchar(128) DEFAULT '''' COMMENT ''物料名称快照'' AFTER `item_code`');

CALL inv_add_column_if_missing('inv_delivery_notice_detail', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/gift'' AFTER `sales_detail_id`');
CALL inv_add_column_if_missing('inv_delivery_notice_detail', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');
CALL inv_add_column_if_missing('inv_delivery_notice_detail', 'item_code',
    'varchar(64) DEFAULT '''' COMMENT ''物料编码快照'' AFTER `item_id`');
CALL inv_add_column_if_missing('inv_delivery_notice_detail', 'item_name',
    'varchar(128) DEFAULT '''' COMMENT ''物料名称快照'' AFTER `item_code`');

CALL inv_add_column_if_missing('inv_transfer_detail', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/gift'' AFTER `transfer_id`');
CALL inv_add_column_if_missing('inv_transfer_detail', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');
CALL inv_add_column_if_missing('inv_transfer_detail', 'item_code',
    'varchar(64) DEFAULT '''' COMMENT ''物料编码快照'' AFTER `item_id`');
CALL inv_add_column_if_missing('inv_transfer_detail', 'item_name',
    'varchar(128) DEFAULT '''' COMMENT ''物料名称快照'' AFTER `item_code`');

CALL inv_add_column_if_missing('inv_transfer_shipment_detail', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/gift'' AFTER `transfer_detail_id`');
CALL inv_add_column_if_missing('inv_transfer_shipment_detail', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');
CALL inv_add_column_if_missing('inv_transfer_shipment_detail', 'item_code',
    'varchar(64) DEFAULT '''' COMMENT ''物料编码快照'' AFTER `item_id`');
CALL inv_add_column_if_missing('inv_transfer_shipment_detail', 'item_name',
    'varchar(128) DEFAULT '''' COMMENT ''物料名称快照'' AFTER `item_code`');

CALL inv_add_column_if_missing('inv_inbound_record', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/oe/gift'' AFTER `order_no`');
CALL inv_add_column_if_missing('inv_inbound_record', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');
CALL inv_add_column_if_missing('inv_outbound_record', 'item_type',
    'varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型：product/gift'' AFTER `order_no`');
CALL inv_add_column_if_missing('inv_outbound_record', 'item_id',
    'bigint DEFAULT NULL COMMENT ''物料ID'' AFTER `item_type`');

DROP PROCEDURE inv_add_column_if_missing;

UPDATE inv_stock SET item_type = 'product', item_id = product_id WHERE item_id IS NULL;
UPDATE inv_stock_log SET item_type = 'product', item_id = product_id WHERE item_id IS NULL;
UPDATE inv_purchase_detail
SET item_type = 'product', item_id = product_id,
    item_code = COALESCE(NULLIF(item_code, ''), sku),
    item_name = COALESCE(NULLIF(item_name, ''), product_name)
WHERE item_id IS NULL;
UPDATE inv_sales_detail
SET item_type = 'product', item_id = product_id,
    item_code = COALESCE(NULLIF(item_code, ''), sku),
    item_name = COALESCE(NULLIF(item_name, ''), product_name)
WHERE item_id IS NULL;
UPDATE inv_delivery_notice_detail d
LEFT JOIN inv_sales_detail sd ON sd.detail_id = d.sales_detail_id
SET d.item_type = COALESCE(NULLIF(sd.item_type, ''), 'product'),
    d.item_id = COALESCE(sd.item_id, d.product_id),
    d.item_code = COALESCE(NULLIF(d.item_code, ''), sd.item_code, sd.sku, ''),
    d.item_name = COALESCE(NULLIF(d.item_name, ''), sd.item_name, d.product_name, '')
WHERE d.item_id IS NULL;
UPDATE inv_transfer_detail
SET item_type = 'product', item_id = product_id,
    item_code = COALESCE(NULLIF(item_code, ''), product_code),
    item_name = COALESCE(NULLIF(item_name, ''), product_name)
WHERE item_id IS NULL;
UPDATE inv_transfer_shipment_detail
SET item_type = 'product', item_id = product_id,
    item_name = COALESCE(NULLIF(item_name, ''), product_name)
WHERE item_id IS NULL;
UPDATE inv_inbound_record SET item_type = 'product', item_id = product_id WHERE item_id IS NULL;
UPDATE inv_outbound_record SET item_type = 'product', item_id = product_id WHERE item_id IS NULL;

ALTER TABLE inv_stock MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_stock_log MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_purchase_detail MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_sales_detail MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_delivery_notice_detail MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_transfer_detail MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_transfer_shipment_detail MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_inbound_record MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';
ALTER TABLE inv_outbound_record MODIFY COLUMN product_id bigint NULL COMMENT '兼容商品ID，非商品物料为空';

ALTER TABLE inv_stock MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_stock_log MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_purchase_detail MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_sales_detail MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_delivery_notice_detail MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_transfer_detail MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_transfer_shipment_detail MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_inbound_record MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';
ALTER TABLE inv_outbound_record MODIFY COLUMN item_id bigint NOT NULL COMMENT '物料ID';

DROP PROCEDURE IF EXISTS inv_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE inv_add_index_if_missing(
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
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_index_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL inv_add_index_if_missing('inv_stock', 'uk_inv_stock_item_shop_wh',
    'UNIQUE KEY `uk_inv_stock_item_shop_wh` (`item_type`, `item_id`, `shop_dept_id`, `warehouse_id`)');
CALL inv_add_index_if_missing('inv_stock_log', 'idx_inv_stock_log_item',
    'INDEX `idx_inv_stock_log_item` (`item_type`, `item_id`)');
CALL inv_add_index_if_missing('inv_purchase_detail', 'idx_inv_purchase_detail_item',
    'INDEX `idx_inv_purchase_detail_item` (`item_type`, `item_id`)');
CALL inv_add_index_if_missing('inv_sales_detail', 'idx_inv_sales_detail_item',
    'INDEX `idx_inv_sales_detail_item` (`item_type`, `item_id`)');
CALL inv_add_index_if_missing('inv_delivery_notice_detail', 'idx_inv_delivery_notice_item',
    'INDEX `idx_inv_delivery_notice_item` (`item_type`, `item_id`)');
CALL inv_add_index_if_missing('inv_transfer_detail', 'idx_inv_transfer_detail_item',
    'INDEX `idx_inv_transfer_detail_item` (`item_type`, `item_id`)');
CALL inv_add_index_if_missing('inv_transfer_shipment_detail', 'idx_inv_transfer_shipment_item',
    'INDEX `idx_inv_transfer_shipment_item` (`item_type`, `item_id`)');
CALL inv_add_index_if_missing('inv_inbound_record', 'idx_inv_inbound_item',
    'INDEX `idx_inv_inbound_item` (`item_type`, `item_id`)');
CALL inv_add_index_if_missing('inv_outbound_record', 'idx_inv_outbound_item',
    'INDEX `idx_inv_outbound_item` (`item_type`, `item_id`)');

DROP PROCEDURE inv_add_index_if_missing;
