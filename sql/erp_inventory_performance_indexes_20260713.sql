-- 仓库高频查询索引（MySQL 8）
-- 只增加索引，不改写业务数据。大表执行前必须在本机同规模数据上 EXPLAIN/EXPLAIN ANALYZE。
-- 如线上表结构不支持 LOCK=NONE，停止发布，改用经评审的在线 DDL 方案。

DROP PROCEDURE IF EXISTS inv_add_warehouse_performance_index;
DELIMITER $$
CREATE PROCEDURE inv_add_warehouse_performance_index(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_columns varchar(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD INDEX `', p_index_name,
            '` (', p_columns, '), ALGORITHM=INPLACE, LOCK=NONE'
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL inv_add_warehouse_performance_index(
    'inv_inbound_record', 'idx_inbound_qc_order_warehouse_time',
    'qc_result, purchase_order_id, warehouse_id, create_time'
);
CALL inv_add_warehouse_performance_index(
    'inv_stock_log', 'idx_stock_log_warehouse_time',
    'warehouse_id, create_time'
);
CALL inv_add_warehouse_performance_index(
    'inv_stock_log', 'idx_stock_log_business_movement',
    'business_type, movement_type, business_id'
);
CALL inv_add_warehouse_performance_index(
    'inv_purchase_order', 'idx_purchase_warehouse_stage_time',
    'shop_dept_id, status, qc_status, create_time'
);
CALL inv_add_warehouse_performance_index(
    'inv_stock_check', 'idx_stock_check_warehouse_status_due',
    'shop_dept_id, status, deadline'
);

DROP PROCEDURE IF EXISTS inv_add_warehouse_performance_index;

-- 验收：五个索引都应返回列序。
SELECT table_name, index_name, seq_in_index, column_name, cardinality
FROM information_schema.STATISTICS
WHERE table_schema = DATABASE()
  AND index_name IN (
      'idx_inbound_qc_order_warehouse_time',
      'idx_stock_log_warehouse_time',
      'idx_stock_log_business_movement',
      'idx_purchase_warehouse_stage_time',
      'idx_stock_check_warehouse_status_due'
  )
ORDER BY table_name, index_name, seq_in_index;
