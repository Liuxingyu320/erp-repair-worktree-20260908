-- Add structured cost and supplier fields required by the gift launch workbook.
USE `BossERP_NEW`;

SET @erp_db := DATABASE();

SET @ddl := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @erp_db
          AND table_name = 'inv_gift_box'
          AND column_name = 'cost_price'
    ),
    'ALTER TABLE inv_gift_box ADD COLUMN cost_price decimal(16,2) DEFAULT NULL COMMENT ''参考成本价（元/盒）'' AFTER replenishment_unit',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @erp_db
          AND table_name = 'inv_gift_box'
          AND column_name = 'supplier_name'
    ),
    'ALTER TABLE inv_gift_box ADD COLUMN supplier_name varchar(128) DEFAULT NULL COMMENT ''供应商名称'' AFTER guide_price_2',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @erp_db
          AND table_name = 'inv_gift_box'
          AND index_name = 'idx_inv_gift_box_supplier'
    ),
    'ALTER TABLE inv_gift_box ADD KEY idx_inv_gift_box_supplier (supplier_name)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
