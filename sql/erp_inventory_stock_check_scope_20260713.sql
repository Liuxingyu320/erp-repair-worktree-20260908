-- 库存盘点范围、盲盘与复盘字段增量迁移（MySQL 8）
-- 仅增加字段、索引并执行可确定的兼容回填；不删除旧字段，不调整历史盘点数量。
-- 请先在本机独立测试库执行，再按发布流程应用到目标库。

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN check_scope varchar(32) NOT NULL DEFAULT ''all'' COMMENT ''盘点范围(all/category/selected/sample)'' AFTER warehouse_id',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'check_scope'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN category_id bigint NULL COMMENT ''分类盘点根分类'' AFTER check_scope',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'category_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN sample_size int NULL COMMENT ''抽盘商品数'' AFTER category_id',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'sample_size'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN blind_check char(1) NOT NULL DEFAULT ''0'' COMMENT ''是否盲盘(0否1是)'' AFTER sample_size',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'blind_check'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN counter_user_id bigint NULL COMMENT ''盘点人用户ID'' AFTER blind_check',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'counter_user_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN counter_name varchar(64) NULL COMMENT ''盘点人姓名快照'' AFTER counter_user_id',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'counter_name'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN deadline datetime NULL COMMENT ''盘点截止时间'' AFTER counter_name',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'deadline'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD COLUMN recount_threshold decimal(16,2) NOT NULL DEFAULT 0 COMMENT ''触发复盘的差异绝对数量，0表示关闭'' AFTER deadline',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND COLUMN_NAME = 'recount_threshold'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check_detail ADD COLUMN recount_required char(1) NOT NULL DEFAULT ''0'' COMMENT ''是否必须复盘(0否1是)'' AFTER actual_qty',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND COLUMN_NAME = 'recount_required'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check_detail ADD COLUMN recount_qty decimal(16,2) NULL COMMENT ''复盘数量'' AFTER recount_required',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND COLUMN_NAME = 'recount_qty'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check_detail ADD COLUMN recount_by varchar(64) NULL COMMENT ''复盘录入人'' AFTER recount_qty',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND COLUMN_NAME = 'recount_by'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check_detail ADD COLUMN recount_time datetime NULL COMMENT ''复盘录入时间'' AFTER recount_by',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND COLUMN_NAME = 'recount_time'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check ADD INDEX idx_stock_check_counter_due (counter_user_id, status, deadline)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check' AND INDEX_NAME = 'idx_stock_check_counter_due'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE inv_stock_check_detail ADD INDEX idx_stock_check_recount (check_id, recount_required)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_stock_check_detail' AND INDEX_NAME = 'idx_stock_check_recount'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史盘点默认视为全仓、非盲盘且不强制复盘；盘点人姓名只使用已有创建人确定回填。
UPDATE inv_stock_check
SET check_scope = COALESCE(NULLIF(check_scope, ''), 'all'),
    blind_check = COALESCE(NULLIF(blind_check, ''), '0'),
    counter_name = COALESCE(NULLIF(counter_name, ''), NULLIF(create_by, '')),
    recount_threshold = COALESCE(recount_threshold, 0);

-- 验收查询：结果应全部为 0；非 0 时停止发布并人工核对，不自动修正盘点数量。
SELECT
    SUM(CASE WHEN check_scope NOT IN ('all', 'category', 'selected', 'sample') THEN 1 ELSE 0 END) AS invalid_scope_count,
    SUM(CASE WHEN blind_check NOT IN ('0', '1') THEN 1 ELSE 0 END) AS invalid_blind_count,
    SUM(CASE WHEN recount_threshold < 0 THEN 1 ELSE 0 END) AS invalid_threshold_count
FROM inv_stock_check;

SELECT COUNT(*) AS invalid_recount_quantity_count
FROM inv_stock_check_detail
WHERE recount_qty < 0;
