-- 门店返仓作为store_return调拨类型接入现有审批、发货、收货和流水。
-- MySQL 5.7，可重复执行；不修改历史warehouse/cross_store类型。

SET @erp_db := DATABASE();

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND column_name='return_reason_code'),
    'ALTER TABLE inv_transfer_order ADD COLUMN return_reason_code varchar(32) DEFAULT NULL COMMENT ''返仓原因编码''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND column_name='return_reason_text'),
    'ALTER TABLE inv_transfer_order ADD COLUMN return_reason_text varchar(300) DEFAULT NULL COMMENT ''返仓原因说明''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND column_name='attachment_node_ids'),
    'ALTER TABLE inv_transfer_order ADD COLUMN attachment_node_ids varchar(2000) DEFAULT NULL COMMENT ''受控附件节点ID集合''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND column_name='source_business_type'),
    'ALTER TABLE inv_transfer_order ADD COLUMN source_business_type varchar(32) DEFAULT NULL COMMENT ''来源业务类型''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND column_name='source_business_id'),
    'ALTER TABLE inv_transfer_order ADD COLUMN source_business_id bigint(20) DEFAULT NULL COMMENT ''来源业务ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND column_name='version'),
    'ALTER TABLE inv_transfer_order ADD COLUMN version bigint(20) NOT NULL DEFAULT 0 COMMENT ''乐观锁版本''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_detail' AND column_name='goods_condition'),
    'ALTER TABLE inv_transfer_detail ADD COLUMN goods_condition varchar(32) DEFAULT ''NORMAL'' COMMENT ''NORMAL/DAMAGED/PENDING_QC''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_detail' AND column_name='condition_note'),
    'ALTER TABLE inv_transfer_detail ADD COLUMN condition_note varchar(500) DEFAULT NULL COMMENT ''货况说明''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_detail' AND column_name='lot_id'),
    'ALTER TABLE inv_transfer_detail ADD COLUMN lot_id bigint(20) DEFAULT NULL COMMENT ''来源批次ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_detail' AND column_name='source_location_id'),
    'ALTER TABLE inv_transfer_detail ADD COLUMN source_location_id bigint(20) DEFAULT NULL COMMENT ''来源库位ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_shipment' AND column_name='source_location_dept_id'),
    'ALTER TABLE inv_transfer_shipment ADD COLUMN source_location_dept_id bigint(20) DEFAULT NULL COMMENT ''实际发货来源组织''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 返仓和OE依赖发货批次保存通用物料标识；兼容历史product_id。
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_shipment_detail' AND column_name='item_type'),
    'ALTER TABLE inv_transfer_shipment_detail ADD COLUMN item_type varchar(32) NOT NULL DEFAULT ''product'' COMMENT ''product/oe/gift''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_shipment_detail' AND column_name='item_id'),
    'ALTER TABLE inv_transfer_shipment_detail ADD COLUMN item_id bigint(20) DEFAULT NULL COMMENT ''通用物料ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_shipment_detail' AND column_name='item_code'),
    'ALTER TABLE inv_transfer_shipment_detail ADD COLUMN item_code varchar(64) DEFAULT NULL COMMENT ''物料编码快照''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@erp_db AND table_name='inv_transfer_shipment_detail' AND column_name='item_name'),
    'ALTER TABLE inv_transfer_shipment_detail ADD COLUMN item_name varchar(128) DEFAULT NULL COMMENT ''物料名称快照''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE inv_transfer_shipment
SET source_location_dept_id = COALESCE(source_location_dept_id, warehouse_dept_id, warehouse_id)
WHERE source_location_dept_id IS NULL;

UPDATE inv_transfer_shipment_detail
SET item_type = COALESCE(NULLIF(item_type, ''), 'product'),
    item_id = COALESCE(item_id, product_id),
    item_name = COALESCE(item_name, product_name)
WHERE item_id IS NULL OR item_name IS NULL OR item_type IS NULL OR item_type = '';

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND index_name='idx_inv_transfer_type_status'),
    'ALTER TABLE inv_transfer_order ADD KEY idx_inv_transfer_type_status (transfer_type, status, create_time)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=@erp_db AND table_name='inv_transfer_order' AND index_name='idx_inv_transfer_source_business'),
    'ALTER TABLE inv_transfer_order ADD KEY idx_inv_transfer_source_business (source_business_type, source_business_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=@erp_db AND table_name='inv_transfer_shipment' AND index_name='idx_inv_transfer_shipment_source'),
    'ALTER TABLE inv_transfer_shipment ADD KEY idx_inv_transfer_shipment_source (source_location_dept_id, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '门店返仓功能开关', 'feature.inventory.store-return.enabled', 'false', 'Y',
       'system', NOW(), '关闭时仅禁止新建store_return，在途返仓继续走完审批和库存对账'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'feature.inventory.store-return.enabled');

UPDATE sys_menu
SET menu_name = '调拨作业', update_by = 'system', update_time = NOW(),
    remark = '门店要货、门店返仓、审批、双向发货收货和差异处理统一入口'
WHERE menu_type = 'C' AND perms = 'inv:transfer:list' AND status = '0';

-- 仓库原先只负责调拨出库；启用返仓后也作为目标组织执行收货。
-- 复用现有receive权限菜单，应用层仍按调拨目标组织校验，不能接收其他仓/店货物。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT deliver_role.role_id, receive_menu.menu_id
FROM sys_role_menu deliver_role
JOIN sys_menu deliver_menu ON deliver_menu.menu_id = deliver_role.menu_id
JOIN sys_menu receive_menu ON receive_menu.perms = 'inv:transfer:receive'
WHERE deliver_menu.perms = 'inv:transfer:deliver'
  AND EXISTS (
      SELECT 1 FROM sys_role_menu list_role
      JOIN sys_menu list_menu ON list_menu.menu_id = list_role.menu_id
      WHERE list_role.role_id = deliver_role.role_id
        AND list_menu.perms = 'inv:transfer:list'
  );

-- 审批预检：store_return会复用transfer_type=all的现有规则和完整审批节点。
SELECT rule_id, rule_name, transfer_type, scope_type, priority, status
FROM inv_transfer_approval_rule
WHERE document_type = 'transfer' AND status = '0'
  AND transfer_type IN ('all', 'store_return')
ORDER BY priority, rule_id;

-- 上线后方向和在途数量对账。
SELECT transfer_id, order_no, transfer_type, from_dept_id, to_dept_id,
       return_reason_code, status, total_quantity, version
FROM inv_transfer_order
WHERE transfer_type = 'store_return'
ORDER BY transfer_id DESC LIMIT 200;
