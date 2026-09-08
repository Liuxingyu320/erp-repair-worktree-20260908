-- Roll back structured gift cost and supplier fields.
USE `BossERP_NEW`;

SET @erp_db := DATABASE();

SET @ddl := IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @erp_db
          AND table_name = 'inv_gift_box'
          AND index_name = 'idx_inv_gift_box_supplier'
    ),
    'ALTER TABLE inv_gift_box DROP INDEX idx_inv_gift_box_supplier',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @erp_db
          AND table_name = 'inv_gift_box'
          AND column_name = 'supplier_name'
    ),
    'ALTER TABLE inv_gift_box DROP COLUMN supplier_name',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @erp_db
          AND table_name = 'inv_gift_box'
          AND column_name = 'cost_price'
    ),
    'ALTER TABLE inv_gift_box DROP COLUMN cost_price',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
