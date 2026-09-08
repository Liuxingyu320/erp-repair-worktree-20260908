-- OE同款购买参考字段（MySQL 5.7，可重复执行）。
-- 可信域名由应用配置 inventory.oe.purchase-reference.allowed-hosts 管理，空白时后端拒绝保存链接。

SET @erp_db := DATABASE();

SET @sql := IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @erp_db AND table_name = 'inv_oe_item'
                  AND column_name = 'purchase_reference_url'),
    'ALTER TABLE inv_oe_item ADD COLUMN purchase_reference_url varchar(1000) DEFAULT NULL COMMENT ''同款HTTPS购买页面'' AFTER image_url',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @erp_db AND table_name = 'inv_oe_item'
                  AND column_name = 'purchase_reference_note'),
    'ALTER TABLE inv_oe_item ADD COLUMN purchase_reference_note varchar(500) DEFAULT NULL COMMENT ''同款购买说明'' AFTER purchase_reference_url',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @erp_db AND table_name = 'inv_oe_item'
                  AND column_name = 'purchase_reference_updated_by'),
    'ALTER TABLE inv_oe_item ADD COLUMN purchase_reference_updated_by varchar(64) DEFAULT NULL COMMENT ''购买参考维护人'' AFTER purchase_reference_note',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @erp_db AND table_name = 'inv_oe_item'
                  AND column_name = 'purchase_reference_updated_time'),
    'ALTER TABLE inv_oe_item ADD COLUMN purchase_reference_updated_time datetime DEFAULT NULL COMMENT ''购买参考维护时间'' AFTER purchase_reference_updated_by',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 启用固定资产OE购买信息完整度预检；本脚本不自动填充外部链接。
SELECT i.oe_item_id, i.oe_item_code, i.oe_item_name, i.image_url,
       i.purchase_reference_url, i.purchase_reference_note,
       COUNT(c.config_id) active_config_count,
       CASE
           WHEN i.oe_item_code IS NULL OR TRIM(i.oe_item_code) = '' THEN 'MISSING_CODE'
           WHEN i.image_url IS NULL OR TRIM(i.image_url) = '' THEN 'MISSING_IMAGE'
           WHEN i.purchase_reference_url IS NULL OR TRIM(i.purchase_reference_url) = '' THEN 'MISSING_REFERENCE'
           WHEN LOWER(i.purchase_reference_url) NOT LIKE 'https://%' THEN 'INVALID_SCHEME'
           ELSE 'READY'
       END readiness
FROM inv_oe_item i
JOIN oa_fixed_asset_config c ON c.oe_item_id = i.oe_item_id AND c.status = '0'
WHERE i.del_flag = '0' AND i.status = '0'
GROUP BY i.oe_item_id, i.oe_item_code, i.oe_item_name, i.image_url,
         i.purchase_reference_url, i.purchase_reference_note
ORDER BY readiness, i.oe_item_id;

