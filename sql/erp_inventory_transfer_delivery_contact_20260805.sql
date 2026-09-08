-- 调拨单收件与配送信息：支持从粘贴清单解析并持久化。
-- MySQL 5.7，可重复执行；历史调拨单保持 NULL。

SET @erp_db := DATABASE();

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @erp_db
      AND table_name = 'inv_transfer_order'
      AND column_name = 'recipient_name'),
    'ALTER TABLE inv_transfer_order ADD COLUMN recipient_name varchar(64) DEFAULT NULL COMMENT ''收件人姓名'' AFTER attachment_node_ids',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @erp_db
      AND table_name = 'inv_transfer_order'
      AND column_name = 'recipient_phone'),
    'ALTER TABLE inv_transfer_order ADD COLUMN recipient_phone varchar(32) DEFAULT NULL COMMENT ''收件人联系电话'' AFTER recipient_name',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @erp_db
      AND table_name = 'inv_transfer_order'
      AND column_name = 'shipping_address'),
    'ALTER TABLE inv_transfer_order ADD COLUMN shipping_address varchar(500) DEFAULT NULL COMMENT ''详细收货地址'' AFTER recipient_phone',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
