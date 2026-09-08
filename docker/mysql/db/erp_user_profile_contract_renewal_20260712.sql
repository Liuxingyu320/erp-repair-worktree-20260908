-- Index used by the daily contract-renewal decision range scan.
-- The user_profile prefix keeps fresh Docker initialization after the profile table migration.
SET @erp_db := DATABASE();
SET @renewal_scan_index_sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'sys_user_profile') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'sys_user_profile'
        AND INDEX_NAME = 'idx_sys_user_profile_renewal_scan') = 0,
    'ALTER TABLE sys_user_profile ADD INDEX idx_sys_user_profile_renewal_scan (contract_end_date, user_id, employee_status)',
    'SELECT 1'
);
PREPARE renewal_scan_index_stmt FROM @renewal_scan_index_sql;
EXECUTE renewal_scan_index_stmt;
DEALLOCATE PREPARE renewal_scan_index_stmt;
