-- 人工异店调货：调入店发起、调出店逐行确认，未确认数量回到调入店重选。
-- MySQL 5.7，可重复执行。

SET @erp_db := DATABASE();

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='source_confirm_status'),
    'ALTER TABLE inv_transfer_order ADD COLUMN source_confirm_status varchar(32) NOT NULL DEFAULT ''NOT_REQUIRED'' COMMENT ''调出店确认状态'' AFTER source_business_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='source_confirmed_user_id'),
    'ALTER TABLE inv_transfer_order ADD COLUMN source_confirmed_user_id bigint(20) DEFAULT NULL COMMENT ''调出店确认人用户ID'' AFTER source_confirm_status',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='source_confirmed_by'),
    'ALTER TABLE inv_transfer_order ADD COLUMN source_confirmed_by varchar(64) DEFAULT NULL COMMENT ''调出店确认人'' AFTER source_confirmed_user_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='source_confirmed_time'),
    'ALTER TABLE inv_transfer_order ADD COLUMN source_confirmed_time datetime DEFAULT NULL COMMENT ''调出店确认时间'' AFTER source_confirmed_by',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='source_confirm_remark'),
    'ALTER TABLE inv_transfer_order ADD COLUMN source_confirm_remark varchar(500) DEFAULT NULL COMMENT ''调出店确认说明'' AFTER source_confirmed_time',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND column_name='reselection_from_transfer_id'),
    'ALTER TABLE inv_transfer_order ADD COLUMN reselection_from_transfer_id bigint(20) DEFAULT NULL COMMENT ''未确认余量来源调拨单ID'' AFTER source_confirm_remark',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE inv_transfer_order
SET source_confirm_status = CASE
    WHEN transfer_type <> 'cross_store'
         OR COALESCE(source_business_type, '') = 'sales_delivery'
        THEN 'NOT_REQUIRED'
    WHEN status IN ('approved', 'reserved')
        THEN 'PENDING'
    WHEN status IN ('partial_delivered', 'delivered', 'partial_received',
                    'received', 'completed', 'discrepancy')
        THEN 'CONFIRMED'
    ELSE 'NOT_STARTED'
END
WHERE source_confirm_status IS NULL
   OR source_confirm_status = ''
   OR (source_confirm_status = 'NOT_REQUIRED'
       AND transfer_type = 'cross_store'
       AND COALESCE(source_business_type, '') <> 'sales_delivery');

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND index_name='idx_inv_transfer_source_confirm'),
    'ALTER TABLE inv_transfer_order ADD KEY idx_inv_transfer_source_confirm (source_confirm_status, status, from_dept_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=@erp_db AND table_name='inv_transfer_order'
      AND index_name='idx_inv_transfer_reselection'),
    'ALTER TABLE inv_transfer_order ADD KEY idx_inv_transfer_reselection (reselection_from_transfer_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
