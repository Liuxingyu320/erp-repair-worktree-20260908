SET @schema_name = DATABASE();

SET @add_qc_status_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE inv_purchase_order ADD COLUMN qc_status varchar(16) DEFAULT NULL COMMENT ''quality check status: pending/passed/rejected/concession'' AFTER status',
        'SELECT ''inv_purchase_order.qc_status already exists'' AS info'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'inv_purchase_order'
      AND COLUMN_NAME = 'qc_status'
);

PREPARE add_qc_status_stmt FROM @add_qc_status_sql;
EXECUTE add_qc_status_stmt;
DEALLOCATE PREPARE add_qc_status_stmt;
