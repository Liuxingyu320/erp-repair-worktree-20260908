-- OA purchase unified-approval expand migration.
-- MySQL 5.7 / 8.0 compatible and repeat-safe.
-- This migration is non-destructive: it keeps legacy Flowable columns and data.

SET @erp_schema := DATABASE();

DROP PROCEDURE IF EXISTS add_oa_purchase_approval_column_if_missing;
DROP PROCEDURE IF EXISTS add_oa_purchase_approval_index_if_missing;
DELIMITER $$

CREATE PROCEDURE add_oa_purchase_approval_column_if_missing(
    IN p_column_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @erp_schema
          AND table_name = 'oa_purchase'
          AND column_name = p_column_name
    ) THEN
        SET @oa_purchase_approval_ddl := p_ddl;
        PREPARE oa_purchase_approval_stmt FROM @oa_purchase_approval_ddl;
        EXECUTE oa_purchase_approval_stmt;
        DEALLOCATE PREPARE oa_purchase_approval_stmt;
    END IF;
END$$

CREATE PROCEDURE add_oa_purchase_approval_index_if_missing(
    IN p_index_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @erp_schema
          AND table_name = 'oa_purchase'
          AND index_name = p_index_name
    ) THEN
        SET @oa_purchase_approval_ddl := p_ddl;
        PREPARE oa_purchase_approval_stmt FROM @oa_purchase_approval_ddl;
        EXECUTE oa_purchase_approval_stmt;
        DEALLOCATE PREPARE oa_purchase_approval_stmt;
    END IF;
END$$

DELIMITER ;

CALL add_oa_purchase_approval_column_if_missing(
    'approval_instance_id',
    'ALTER TABLE oa_purchase ADD COLUMN approval_instance_id bigint(20) DEFAULT NULL COMMENT ''当前或最近统一审批实例ID''');
CALL add_oa_purchase_approval_column_if_missing(
    'approval_round',
    'ALTER TABLE oa_purchase ADD COLUMN approval_round int NOT NULL DEFAULT 0 COMMENT ''当前或最近统一审批轮次''');
CALL add_oa_purchase_approval_column_if_missing(
    'row_version',
    'ALTER TABLE oa_purchase ADD COLUMN row_version bigint(20) NOT NULL DEFAULT 0 COMMENT ''业务行乐观锁版本''');
CALL add_oa_purchase_approval_column_if_missing(
    'last_approval_event_key',
    'ALTER TABLE oa_purchase ADD COLUMN last_approval_event_key varchar(128) DEFAULT NULL COMMENT ''最近成功消费的统一审批事件键''');

CALL add_oa_purchase_approval_index_if_missing(
    'idx_oa_purchase_approval_instance',
    'ALTER TABLE oa_purchase ADD KEY idx_oa_purchase_approval_instance (approval_instance_id, approval_round)');

DROP PROCEDURE IF EXISTS add_oa_purchase_approval_column_if_missing;
DROP PROCEDURE IF EXISTS add_oa_purchase_approval_index_if_missing;

SELECT column_name
FROM information_schema.columns
WHERE table_schema = @erp_schema
  AND table_name = 'oa_purchase'
  AND column_name IN (
      'approval_instance_id', 'approval_round',
      'row_version', 'last_approval_event_key'
  )
ORDER BY ordinal_position;
