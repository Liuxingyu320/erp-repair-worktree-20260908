-- ERP-NEW_2 V2 调拨差异裁决动作执行事件加法结构（MySQL 5.7，可重复执行）。
-- 只供未来在 ERP-NEW_2 专属数据库 roll-forward；本文件不连接数据库、
-- 不执行库存/物流/财务效果、不写业务数据、不启用 Gate。

DROP PROCEDURE IF EXISTS erp_new_2_adjudication_execution_add_column;
DELIMITER $$
CREATE PROCEDURE erp_new_2_adjudication_execution_add_column(
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
        SET @erp_new_2_adjudication_execution_ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD COLUMN `',
            p_column_name, '` ', p_definition);
        PREPARE erp_new_2_adjudication_execution_stmt
            FROM @erp_new_2_adjudication_execution_ddl;
        EXECUTE erp_new_2_adjudication_execution_stmt;
        DEALLOCATE PREPARE erp_new_2_adjudication_execution_stmt;
    END IF;
END$$
DELIMITER ;

CALL erp_new_2_adjudication_execution_add_column(
    'inv_transfer_receipt_discrepancy_adjudication_action',
    'execution_version',
    'bigint NOT NULL DEFAULT 0 COMMENT ''动作执行乐观锁版本'' AFTER `execution_status`');
CALL erp_new_2_adjudication_execution_add_column(
    'inv_transfer_receipt_discrepancy_adjudication_action',
    'execution_effect_reference',
    'varchar(128) DEFAULT NULL COMMENT ''补发/退回权威子调拨引用'' AFTER `execution_version`');

DROP PROCEDURE IF EXISTS erp_new_2_adjudication_execution_add_index;
DELIMITER $$
CREATE PROCEDURE erp_new_2_adjudication_execution_add_index(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_definition text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @erp_new_2_adjudication_execution_index_ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD ', p_definition);
        PREPARE erp_new_2_adjudication_execution_index_stmt
            FROM @erp_new_2_adjudication_execution_index_ddl;
        EXECUTE erp_new_2_adjudication_execution_index_stmt;
        DEALLOCATE PREPARE erp_new_2_adjudication_execution_index_stmt;
    END IF;
END$$
DELIMITER ;

CALL erp_new_2_adjudication_execution_add_index(
    'inv_transfer_receipt_discrepancy_adjudication_action',
    'uk_transfer_discrepancy_action_effect_reference',
    'UNIQUE KEY `uk_transfer_discrepancy_action_effect_reference` (`execution_effect_reference`)');

DROP PROCEDURE erp_new_2_adjudication_execution_add_index;

CREATE TABLE IF NOT EXISTS
    inv_transfer_receipt_discrepancy_adjudication_execution_event (
    execution_event_id bigint NOT NULL AUTO_INCREMENT,
    request_id varchar(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discrepancy_case_id bigint NOT NULL,
    case_version_before bigint NOT NULL,
    case_version_after bigint NOT NULL,
    case_status_before varchar(32) NOT NULL,
    case_status_after varchar(32) NOT NULL,
    adjudication_id bigint NOT NULL,
    decision_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_status_before varchar(32) NOT NULL,
    plan_status_after varchar(32) NOT NULL,
    adjudication_action_id bigint NOT NULL,
    action_sequence int NOT NULL,
    action_type varchar(32) NOT NULL,
    coverage_kind varchar(16) NOT NULL
        COMMENT 'resolution/responsibility服务端覆盖维度',
    discrepancy_type varchar(16) NOT NULL,
    execution_command varchar(16) NOT NULL
        COMMENT 'dispatch/complete',
    effect_kind varchar(64) NOT NULL,
    effect_reference varchar(128) DEFAULT NULL,
    action_status_before varchar(32) NOT NULL,
    action_status_after varchar(32) NOT NULL,
    action_version_before bigint NOT NULL,
    action_version_after bigint NOT NULL,
    action_quantity decimal(18,4) NOT NULL,
    source_cost_price decimal(18,6) NOT NULL,
    action_amount decimal(18,6) NOT NULL,
    responsible_party varchar(16) NOT NULL,
    required_permission varchar(128) NOT NULL,
    executor_user_id bigint NOT NULL,
    executor_name varchar(64) NOT NULL,
    event_fingerprint char(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_by varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (execution_event_id),
    UNIQUE KEY uk_transfer_discrepancy_execution_request (request_id),
    UNIQUE KEY uk_transfer_discrepancy_execution_action_version
        (adjudication_action_id, action_version_before),
    KEY idx_transfer_discrepancy_execution_effect_reference
        (effect_kind, effect_reference, execution_event_id),
    KEY idx_transfer_discrepancy_execution_case
        (discrepancy_case_id, execution_event_id),
    KEY idx_transfer_discrepancy_execution_plan
        (adjudication_id, execution_event_id),
    KEY idx_transfer_discrepancy_execution_effect
        (effect_kind, action_status_after, execution_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V2调拨差异裁决动作追加式执行事件';

-- 执行入口始终硬关闭，因此已存在的事件表按契约必须为空；此调用只负责
-- 兼容同一滚动脚本先前已创建表、随后再次执行的结构升级场景。
CALL erp_new_2_adjudication_execution_add_column(
    'inv_transfer_receipt_discrepancy_adjudication_execution_event',
    'source_cost_price',
    'decimal(18,6) NOT NULL COMMENT ''裁决冻结源成本价'' AFTER `action_quantity`');

DROP PROCEDURE erp_new_2_adjudication_execution_add_column;
