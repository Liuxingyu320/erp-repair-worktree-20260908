-- Draft revisions. Apply before deploying the version-aware API and clients.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_purchase_order' AND column_name='version'), 'SELECT 1', 'ALTER TABLE inv_purchase_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT "Draft revision"');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_purchase_return' AND column_name='version'), 'SELECT 1', 'ALTER TABLE inv_purchase_return ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT "Draft revision"');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_sales_return' AND column_name='version'), 'SELECT 1', 'ALTER TABLE inv_sales_return ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT "Draft revision"');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
