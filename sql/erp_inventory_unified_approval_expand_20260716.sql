-- Inventory unified-approval expand migration.
-- MySQL 5.7 / 8.0 compatible and repeat-safe.
-- Historical inventory approval instances/tasks/actions are never updated or deleted.
-- This automatic migration adds compatibility fields and fail-closed flags only.
-- It does not hide the legacy rule UI or enable the native approval engine.

SET @erp_schema := DATABASE();

DROP PROCEDURE IF EXISTS add_inventory_approval_column_if_missing;
DROP PROCEDURE IF EXISTS add_inventory_approval_index_if_missing;
DELIMITER $$

CREATE PROCEDURE add_inventory_approval_column_if_missing(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @erp_schema
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @inventory_approval_ddl := p_ddl;
        PREPARE inventory_approval_stmt FROM @inventory_approval_ddl;
        EXECUTE inventory_approval_stmt;
        DEALLOCATE PREPARE inventory_approval_stmt;
    END IF;
END$$

CREATE PROCEDURE add_inventory_approval_index_if_missing(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @erp_schema
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @inventory_approval_ddl := p_ddl;
        PREPARE inventory_approval_stmt FROM @inventory_approval_ddl;
        EXECUTE inventory_approval_stmt;
        DEALLOCATE PREPARE inventory_approval_stmt;
    END IF;
END$$

DELIMITER ;

CALL add_inventory_approval_column_if_missing(
    'inv_stock_check', 'approval_engine',
    'ALTER TABLE inv_stock_check ADD COLUMN approval_engine varchar(16) NOT NULL DEFAULT ''LEGACY'' COMMENT ''LEGACY/NATIVE审批引擎'' AFTER approval_round');
CALL add_inventory_approval_column_if_missing(
    'inv_stock_check', 'last_approval_event_key',
    'ALTER TABLE inv_stock_check ADD COLUMN last_approval_event_key varchar(128) DEFAULT NULL COMMENT ''最近已处理统一审批事件键'' AFTER approval_engine');
CALL add_inventory_approval_column_if_missing(
    'inv_stock_check', 'row_version',
    'ALTER TABLE inv_stock_check ADD COLUMN row_version bigint NOT NULL DEFAULT 0 COMMENT ''业务行版本'' AFTER last_approval_event_key');

CALL add_inventory_approval_column_if_missing(
    'inv_transfer_order', 'approval_round',
    'ALTER TABLE inv_transfer_order ADD COLUMN approval_round int NOT NULL DEFAULT 0 COMMENT ''当前统一或旧审批轮次'' AFTER approval_instance_id');
CALL add_inventory_approval_column_if_missing(
    'inv_transfer_order', 'approval_engine',
    'ALTER TABLE inv_transfer_order ADD COLUMN approval_engine varchar(16) NOT NULL DEFAULT ''LEGACY'' COMMENT ''LEGACY/NATIVE审批引擎'' AFTER approval_round');
CALL add_inventory_approval_column_if_missing(
    'inv_transfer_order', 'last_approval_event_key',
    'ALTER TABLE inv_transfer_order ADD COLUMN last_approval_event_key varchar(128) DEFAULT NULL COMMENT ''最近已处理统一审批事件键'' AFTER approval_engine');

CALL add_inventory_approval_index_if_missing(
    'inv_stock_check', 'idx_inv_stock_check_approval_engine_status',
    'ALTER TABLE inv_stock_check ADD KEY idx_inv_stock_check_approval_engine_status (approval_engine, status, submitted_time)');
CALL add_inventory_approval_index_if_missing(
    'inv_transfer_order', 'idx_inv_transfer_approval_engine_status',
    'ALTER TABLE inv_transfer_order ADD KEY idx_inv_transfer_approval_engine_status (approval_engine, status, submitted_time)');

DROP PROCEDURE IF EXISTS add_inventory_approval_column_if_missing;
DROP PROCEDURE IF EXISTS add_inventory_approval_index_if_missing;

-- Existing rows retain their old instance links and are explicitly routed to
-- the legacy engines. This includes pending rows whose old instance link is
-- NULL; the compatibility adapter resolves those by business ID.
UPDATE inv_stock_check
SET approval_engine = 'LEGACY'
WHERE approval_engine IS NULL OR approval_engine = '';

UPDATE inv_transfer_order
SET approval_engine = 'LEGACY'
WHERE approval_engine IS NULL OR approval_engine = '';

-- Fail-closed until the unified approval SQL has published and validated the
-- corresponding native templates. That later migration enables these flags.
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT '盘点统一审批开关',
       'feature.inventory.stock-check-native-approval.enabled',
       'false', 'Y', 'system', NOW(),
       '只控制新提交；NATIVE在途实例始终继续走统一审批'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.inventory.stock-check-native-approval.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT '调拨统一审批开关',
       'feature.inventory.transfer-native-approval.enabled',
       'false', 'Y', 'system', NOW(),
       '只控制新提交；NATIVE在途实例始终继续走统一审批'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.inventory.transfer-native-approval.enabled'
);

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = @erp_schema
  AND (
      (table_name = 'inv_stock_check'
       AND column_name IN ('approval_engine', 'last_approval_event_key', 'row_version'))
      OR
      (table_name = 'inv_transfer_order'
       AND column_name IN ('approval_round', 'approval_engine', 'last_approval_event_key'))
  )
ORDER BY table_name, ordinal_position;
