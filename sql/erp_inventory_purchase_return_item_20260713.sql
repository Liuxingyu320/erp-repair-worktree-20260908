-- 采购退货通用物料与原因字段增量迁移（MySQL 8）
-- 请先在独立测试库执行；本脚本只增加字段并回填可确定的原明细，不删除旧字段。

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return_detail ADD COLUMN item_type varchar(32) NULL COMMENT ''物料类型'' AFTER purchase_detail_id',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return_detail' AND COLUMN_NAME = 'item_type'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return_detail ADD COLUMN item_id bigint NULL COMMENT ''物料ID'' AFTER item_type',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return_detail' AND COLUMN_NAME = 'item_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return_detail ADD COLUMN item_code varchar(128) NULL COMMENT ''物料编码快照'' AFTER item_id',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return_detail' AND COLUMN_NAME = 'item_code'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return_detail ADD COLUMN item_name varchar(255) NULL COMMENT ''物料名称快照'' AFTER item_code',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return_detail' AND COLUMN_NAME = 'item_name'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return ADD COLUMN return_reason varchar(500) NULL COMMENT ''退货原因'' AFTER status',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return' AND COLUMN_NAME = 'return_reason'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return ADD COLUMN responsibility varchar(32) NULL COMMENT ''责任归属'' AFTER return_reason',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return' AND COLUMN_NAME = 'responsibility'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return ADD COLUMN attachment_urls text NULL COMMENT ''图片及附件URL'' AFTER responsibility',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return' AND COLUMN_NAME = 'attachment_urls'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_purchase_return_detail ADD INDEX idx_purchase_return_item (item_type, item_id)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_return_detail' AND INDEX_NAME = 'idx_purchase_return_item'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 仅依据已关联的原采购明细回填，不做模糊匹配。
UPDATE inv_purchase_return_detail rd
JOIN inv_purchase_detail pd ON pd.detail_id = rd.purchase_detail_id
SET rd.item_type = COALESCE(NULLIF(pd.item_type, ''), 'product'),
    rd.item_id = COALESCE(pd.item_id, pd.product_id),
    rd.item_code = COALESCE(pd.item_code, pd.sku),
    rd.item_name = COALESCE(pd.item_name, pd.product_name)
WHERE rd.item_id IS NULL;

-- 验收查询：返回行数必须在业务人员确认后才能人工修复。
SELECT rd.detail_id, r.return_no, rd.purchase_detail_id, rd.product_id,
       rd.item_type, rd.item_id, rd.product_name
FROM inv_purchase_return_detail rd
JOIN inv_purchase_return r ON r.return_id = rd.return_id
WHERE rd.item_id IS NULL
ORDER BY rd.detail_id;
