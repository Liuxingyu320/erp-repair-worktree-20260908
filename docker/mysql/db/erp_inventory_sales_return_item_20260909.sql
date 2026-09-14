-- 销售退货通用物料；本地 MySQL 8.0.45 核对旧表 product_id 为 bigint NOT NULL。
-- 部署前仍需核对目标环境 DDL。只回填确定的商品身份，不猜礼盒编号。
-- 新旧程序并行写入不受支持；产生礼盒退货后不可直接回退旧程序写入。

SET @sales_return_ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE inv_sales_return_detail ADD COLUMN item_type varchar(32) NULL COMMENT ''物料类型'' AFTER sales_detail_id', 'SELECT 1')
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail' AND COLUMN_NAME = 'item_type');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

SET @sales_return_ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE inv_sales_return_detail ADD COLUMN item_id bigint NULL COMMENT ''物料ID'' AFTER item_type', 'SELECT 1')
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail' AND COLUMN_NAME = 'item_id');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

SET @sales_return_ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE inv_sales_return_detail ADD COLUMN item_code varchar(128) NULL COMMENT ''物料编码快照'' AFTER item_id', 'SELECT 1')
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail' AND COLUMN_NAME = 'item_code');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

SET @sales_return_ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE inv_sales_return_detail ADD COLUMN item_name varchar(255) NULL COMMENT ''物料名称快照'' AFTER item_code', 'SELECT 1')
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail' AND COLUMN_NAME = 'item_name');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

SET @sales_return_ddl := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE inv_sales_return_detail MODIFY COLUMN product_id bigint NULL COMMENT ''兼容商品ID，礼盒为空''', 'SELECT 1')
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail' AND COLUMN_NAME = 'product_id' AND IS_NULLABLE = 'NO');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

SET @sales_return_ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE inv_sales_return_detail ADD INDEX idx_sales_return_item (item_type, item_id)', 'SELECT 1')
    FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail' AND INDEX_NAME = 'idx_sales_return_item');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

-- 不把历史已退成本填为 0；缺失原明细成本事实必须核对后才能继续确认退货。
SET @sales_return_ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE inv_sales_return_detail ADD COLUMN returned_cost_amount decimal(20,2) NULL COMMENT ''本明细实际已返成本，历史未知为空'' AFTER returned_quantity', 'SELECT 1')
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_sales_return_detail' AND COLUMN_NAME = 'returned_cost_amount');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

SET @sales_return_ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE inv_stock_log ADD COLUMN cost_amount decimal(20,2) NULL COMMENT ''实际变动成本绝对值，方向由数量确定，历史未知为空'' AFTER cost_price', 'SELECT 1')
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_stock_log' AND COLUMN_NAME = 'cost_amount');
PREPARE sales_return_stmt FROM @sales_return_ddl;
EXECUTE sales_return_stmt;
DEALLOCATE PREPARE sales_return_stmt;

-- 原模型 product_id 明确表示商品；先保留该事实，不能因同号礼盒改写身份。
UPDATE inv_sales_return_detail
SET item_type = 'product', item_id = product_id,
    item_code = sku, item_name = product_name
WHERE item_type IS NULL AND item_id IS NULL AND product_id IS NOT NULL;

-- 只在所属原单中有唯一相同类型/编号明细时补关联；重复不同价格行保留待核对。
UPDATE inv_sales_return_detail rd
JOIN inv_sales_return r ON r.return_id = rd.return_id
JOIN (
    SELECT order_id, COALESCE(NULLIF(item_type, ''), 'product') AS source_type,
           COALESCE(item_id, product_id) AS source_id, MIN(detail_id) AS detail_id
    FROM inv_sales_detail
    GROUP BY order_id, COALESCE(NULLIF(item_type, ''), 'product'), COALESCE(item_id, product_id)
    HAVING COUNT(*) = 1
) source ON source.order_id = r.sales_order_id AND source.source_type = rd.item_type AND source.source_id = rd.item_id
SET rd.sales_detail_id = source.detail_id
WHERE rd.sales_detail_id IS NULL;

-- 已有精确关联仍校验原单和身份；仅给缺失身份的非商品历史候选补可信信息。
UPDATE inv_sales_return_detail rd
JOIN inv_sales_return r ON r.return_id = rd.return_id
JOIN inv_sales_detail sd ON sd.detail_id = rd.sales_detail_id AND sd.order_id = r.sales_order_id
SET rd.item_type = sd.item_type, rd.item_id = sd.item_id,
    rd.item_code = COALESCE(NULLIF(rd.sku, ''), sd.item_code),
    rd.item_name = COALESCE(NULLIF(rd.product_name, ''), sd.item_name)
WHERE rd.item_type IS NULL AND rd.item_id IS NULL AND rd.product_id IS NULL
  AND sd.item_type IN ('product', 'gift') AND sd.item_id IS NOT NULL;

-- 阻断发布前人工核验清单：不自动重写歧义、跨原单、类型冲突。
SELECT rd.detail_id, r.return_no, rd.sales_detail_id, rd.item_type, rd.item_id
FROM inv_sales_return_detail rd
JOIN inv_sales_return r ON r.return_id = rd.return_id
LEFT JOIN inv_sales_detail sd ON sd.detail_id = rd.sales_detail_id AND sd.order_id = r.sales_order_id
WHERE rd.item_type IS NULL OR rd.item_id IS NULL OR sd.detail_id IS NULL
   OR rd.item_type <> COALESCE(NULLIF(sd.item_type, ''), 'product')
   OR rd.item_id <> COALESCE(sd.item_id, sd.product_id);
