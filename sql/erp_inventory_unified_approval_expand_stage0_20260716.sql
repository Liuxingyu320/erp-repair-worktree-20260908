-- Stage 0 inventory association expansion for unified approval.
-- MySQL 5.7 / 8.0 compatible and repeat-safe.
-- It adds only runtime association fields/indexes and preserves every existing
-- row on the LEGACY engine. It does not expose menus or enable native approval.

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
