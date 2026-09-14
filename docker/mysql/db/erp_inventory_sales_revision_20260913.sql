-- INV-N04: additive sales mutation revision. Apply before deploying the matching API/UI.
-- No history quantities, warehouses or statuses are changed by this migration.
SET @inv_sales_version_exists = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'inv_sales_order' AND column_name = 'version');
SET @inv_sales_version_sql = IF(@inv_sales_version_exists = 0,
 'ALTER TABLE inv_sales_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''Sales mutation revision''', 'SELECT 1');
PREPARE inv_sales_version_stmt FROM @inv_sales_version_sql;
EXECUTE inv_sales_version_stmt;
DEALLOCATE PREPARE inv_sales_version_stmt;
-- Rollback application first; retain the additive column to preserve revision history.

CREATE TABLE IF NOT EXISTS inv_sales_warehouse_repair_audit (
  audit_id BIGINT NOT NULL AUTO_INCREMENT,
  sales_order_id BIGINT NOT NULL,
  sales_detail_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  expected_version BIGINT NOT NULL,
  notice_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_name VARCHAR(64) NOT NULL,
  shop_dept_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL,
  PRIMARY KEY (audit_id),
  UNIQUE KEY uk_sales_warehouse_repair (sales_order_id, sales_detail_id, expected_version),
  KEY idx_sales_warehouse_notice (notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Controlled missing sales warehouse assignments';
