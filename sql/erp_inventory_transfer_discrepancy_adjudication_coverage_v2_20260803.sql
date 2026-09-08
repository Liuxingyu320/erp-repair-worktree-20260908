-- ERP-NEW_2 V2 调拨差异裁决双覆盖维度加法结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不执行库存效果、不推进裁决状态、不启用 Gate。
-- 前置条件：裁决计划表和执行事件表已由各自 V2 基础脚本创建。

DROP PROCEDURE IF EXISTS erp_new_2_adjudication_coverage_add_column;
DELIMITER $$
CREATE PROCEDURE erp_new_2_adjudication_coverage_add_column(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @erp_new_2_adjudication_coverage_ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD COLUMN `',
            p_column_name, '` ', p_definition);
        PREPARE erp_new_2_adjudication_coverage_stmt
            FROM @erp_new_2_adjudication_coverage_ddl;
        EXECUTE erp_new_2_adjudication_coverage_stmt;
        DEALLOCATE PREPARE erp_new_2_adjudication_coverage_stmt;
    END IF;
END$$
DELIMITER ;

CALL erp_new_2_adjudication_coverage_add_column(
    'inv_transfer_receipt_discrepancy_adjudication_action',
    'coverage_kind',
    'varchar(16) DEFAULT NULL COMMENT ''resolution/responsibility服务端覆盖维度'' AFTER `action_type`');

UPDATE inv_transfer_receipt_discrepancy_adjudication_action action
INNER JOIN inv_transfer_receipt_discrepancy_adjudication adjudication
        ON adjudication.adjudication_id = action.adjudication_id
       AND adjudication.discrepancy_case_id = action.discrepancy_case_id
SET action.coverage_kind = CASE
    WHEN adjudication.discrepancy_type = 'damaged'
         AND action.action_type = 'responsibility_adjustment'
        THEN 'responsibility'
    ELSE 'resolution'
END
WHERE action.coverage_kind IS NULL;

CALL erp_new_2_adjudication_coverage_add_column(
    'inv_transfer_receipt_discrepancy_adjudication_execution_event',
    'coverage_kind',
    'varchar(16) DEFAULT NULL COMMENT ''resolution/responsibility服务端覆盖维度'' AFTER `action_type`');

UPDATE inv_transfer_receipt_discrepancy_adjudication_execution_event event
INNER JOIN inv_transfer_receipt_discrepancy_adjudication_action action
        ON action.adjudication_action_id = event.adjudication_action_id
       AND action.adjudication_id = event.adjudication_id
       AND action.discrepancy_case_id = event.discrepancy_case_id
SET event.coverage_kind = action.coverage_kind
WHERE event.coverage_kind IS NULL;

DROP PROCEDURE IF EXISTS erp_new_2_adjudication_coverage_validate;
DELIMITER $$
CREATE PROCEDURE erp_new_2_adjudication_coverage_validate()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM inv_transfer_receipt_discrepancy_adjudication_action action
        LEFT JOIN inv_transfer_receipt_discrepancy_adjudication adjudication
               ON adjudication.adjudication_id = action.adjudication_id
              AND adjudication.discrepancy_case_id =
                  action.discrepancy_case_id
        WHERE adjudication.adjudication_id IS NULL
           OR action.coverage_kind IS NULL
           OR action.coverage_kind NOT IN ('resolution', 'responsibility')
           OR adjudication.discrepancy_type NOT IN ('shortage', 'damaged')
           OR action.action_type NOT IN (
               'reship', 'return_to_source', 'damage_write_off',
               'responsibility_adjustment',
               'transport_loss_write_off')
           OR (adjudication.discrepancy_type = 'shortage'
               AND action.action_type IN (
                   'return_to_source', 'damage_write_off'))
           OR (adjudication.discrepancy_type = 'damaged'
               AND action.action_type = 'reship')
           OR action.coverage_kind <> CASE
               WHEN adjudication.discrepancy_type = 'damaged'
                    AND action.action_type = 'responsibility_adjustment'
                   THEN 'responsibility'
               ELSE 'resolution'
           END
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ERP-NEW_2 adjudication action coverage cannot be verified';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM inv_transfer_receipt_discrepancy_adjudication adjudication
        LEFT JOIN inv_transfer_receipt_discrepancy_adjudication_action action
               ON action.adjudication_id = adjudication.adjudication_id
              AND action.discrepancy_case_id =
                  adjudication.discrepancy_case_id
        GROUP BY adjudication.adjudication_id,
                 adjudication.discrepancy_quantity,
                 adjudication.discrepancy_amount
        HAVING COALESCE(SUM(CASE
                   WHEN action.coverage_kind = 'resolution'
                       THEN action.action_quantity ELSE 0 END), 0)
                   <> adjudication.discrepancy_quantity
            OR COALESCE(SUM(CASE
                   WHEN action.coverage_kind = 'resolution'
                       THEN action.action_amount ELSE 0 END), 0)
                   <> adjudication.discrepancy_amount
            OR COALESCE(SUM(CASE
                   WHEN action.coverage_kind = 'responsibility'
                       THEN action.action_quantity ELSE 0 END), 0)
                   > adjudication.discrepancy_quantity
            OR COALESCE(SUM(CASE
                   WHEN action.coverage_kind = 'responsibility'
                       THEN action.action_amount ELSE 0 END), 0)
                   > adjudication.discrepancy_amount
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ERP-NEW_2 adjudication coverage conservation cannot be verified';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM inv_transfer_receipt_discrepancy_adjudication_execution_event event
        LEFT JOIN inv_transfer_receipt_discrepancy_adjudication_action action
               ON action.adjudication_action_id =
                  event.adjudication_action_id
              AND action.adjudication_id = event.adjudication_id
              AND action.discrepancy_case_id = event.discrepancy_case_id
        WHERE action.adjudication_action_id IS NULL
           OR event.coverage_kind IS NULL
           OR event.coverage_kind <> action.coverage_kind
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ERP-NEW_2 execution event coverage cannot be verified';
    END IF;
END$$
DELIMITER ;

CALL erp_new_2_adjudication_coverage_validate();
DROP PROCEDURE erp_new_2_adjudication_coverage_validate;

ALTER TABLE inv_transfer_receipt_discrepancy_adjudication_action
    MODIFY COLUMN coverage_kind varchar(16) NOT NULL
        COMMENT 'resolution/responsibility服务端覆盖维度';

ALTER TABLE inv_transfer_receipt_discrepancy_adjudication_execution_event
    MODIFY COLUMN coverage_kind varchar(16) NOT NULL
        COMMENT 'resolution/responsibility服务端覆盖维度';

DROP PROCEDURE erp_new_2_adjudication_coverage_add_column;
