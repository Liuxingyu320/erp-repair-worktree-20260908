-- Fixed asset monthly quota snapshots and damaged quantity reporting.
-- Idempotent for existing databases that may still have product_* legacy columns.

SET @erp_db = DATABASE();

CREATE TABLE IF NOT EXISTS oa_fixed_asset_quota_month (
    month_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '月度额度ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺部门ID',
    quota_year int NOT NULL COMMENT '额度年份',
    quota_month int NOT NULL COMMENT '额度月份（1-12）',
    annual_repair_ratio decimal(8,2) NOT NULL DEFAULT 20.00 COMMENT '年度申报比例快照',
    asset_total_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '固定资产总金额快照',
    monthly_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '月度释放额度快照',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (month_id),
    UNIQUE KEY uk_oa_fixed_asset_quota_month (shop_dept_id, quota_year, quota_month),
    KEY idx_oa_fixed_asset_quota_month_shop_year (shop_dept_id, quota_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA固定资产月度额度快照';

SET @repair_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = @erp_db
      AND TABLE_NAME = 'oa_fixed_asset_repair'
);

SET @repair_quantity_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @erp_db
      AND TABLE_NAME = 'oa_fixed_asset_repair'
      AND COLUMN_NAME = 'repair_quantity'
);

SET @has_oe_item_name = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @erp_db
      AND TABLE_NAME = 'oa_fixed_asset_repair'
      AND COLUMN_NAME = 'oe_item_name'
);

SET @has_product_name = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @erp_db
      AND TABLE_NAME = 'oa_fixed_asset_repair'
      AND COLUMN_NAME = 'product_name'
);

SET @add_repair_quantity_sql = IF(
    @repair_table_exists = 0 OR @repair_quantity_exists > 0,
    'SELECT ''repair_quantity unchanged''',
    IF(
        @has_oe_item_name > 0,
        'ALTER TABLE oa_fixed_asset_repair ADD COLUMN repair_quantity decimal(16,2) NOT NULL DEFAULT 1.00 COMMENT ''坏掉数量'' AFTER oe_item_name',
        IF(
            @has_product_name > 0,
            'ALTER TABLE oa_fixed_asset_repair ADD COLUMN repair_quantity decimal(16,2) NOT NULL DEFAULT 1.00 COMMENT ''坏掉数量'' AFTER product_name',
            'ALTER TABLE oa_fixed_asset_repair ADD COLUMN repair_quantity decimal(16,2) NOT NULL DEFAULT 1.00 COMMENT ''坏掉数量'''
        )
    )
);
PREPARE add_repair_quantity_stmt FROM @add_repair_quantity_sql;
EXECUTE add_repair_quantity_stmt;
DEALLOCATE PREPARE add_repair_quantity_stmt;

SET @has_estimated_repair_amount = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @erp_db
      AND TABLE_NAME = 'oa_fixed_asset_repair'
      AND COLUMN_NAME = 'estimated_repair_amount'
);

SET @update_repair_amount_comment_sql = IF(
    @repair_table_exists = 0 OR @has_estimated_repair_amount = 0,
    'SELECT ''estimated_repair_amount unchanged''',
    'ALTER TABLE oa_fixed_asset_repair MODIFY COLUMN estimated_repair_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT ''系统计算占用额度'''
);
PREPARE update_repair_amount_comment_stmt FROM @update_repair_amount_comment_sql;
EXECUTE update_repair_amount_comment_stmt;
DEALLOCATE PREPARE update_repair_amount_comment_stmt;
