-- 盘点明细物料身份增量迁移（MySQL 8）。先迁移后发布；不修改历史盘点数量或流水。
-- 新版本产生 OE/礼盒盘点后，禁止回退到只识别 product_id 的旧应用。
SET @stock_check_identity_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check_detail ADD COLUMN item_type varchar(20) NOT NULL DEFAULT ''product'' COMMENT ''物料类型product/oe/gift'' AFTER check_id',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND COLUMN_NAME = 'item_type'
);
PREPARE stock_check_identity_stmt FROM @stock_check_identity_ddl;
EXECUTE stock_check_identity_stmt;
DEALLOCATE PREPARE stock_check_identity_stmt;

SET @stock_check_identity_ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check_detail ADD COLUMN item_id bigint NULL COMMENT ''物料ID，旧商品数据兼容product_id'' AFTER item_type',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND COLUMN_NAME = 'item_id'
);
PREPARE stock_check_identity_stmt FROM @stock_check_identity_ddl;
EXECUTE stock_check_identity_stmt;
DEALLOCATE PREPARE stock_check_identity_stmt;

SET @stock_check_identity_ddl := (
    SELECT IF(IS_NULLABLE = 'NO',
        'ALTER TABLE inv_stock_check_detail MODIFY COLUMN product_id bigint NULL COMMENT ''商品ID，OE/礼盒为空''',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND COLUMN_NAME = 'product_id'
);
PREPARE stock_check_identity_stmt FROM @stock_check_identity_ddl;
EXECUTE stock_check_identity_stmt;
DEALLOCATE PREPARE stock_check_identity_stmt;

UPDATE inv_stock_check_detail SET item_id = product_id
WHERE item_type = 'product' AND item_id IS NULL AND product_id IS NOT NULL;
