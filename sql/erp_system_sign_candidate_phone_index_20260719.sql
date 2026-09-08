-- Exact-phone lookup used by Excel-only onboarding contract matching.
-- Non-unique by design: the matching service detects duplicate active profiles and fails closed.
SET @erp_db := DATABASE();
SET @sign_candidate_phone_index_sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'sys_user') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @erp_db
        AND TABLE_NAME = 'sys_user'
        AND INDEX_NAME = 'idx_sys_user_sign_phone') = 0,
    'ALTER TABLE sys_user ADD INDEX idx_sys_user_sign_phone (phonenumber, del_flag, status, user_id)',
    'SELECT 1'
);
PREPARE sign_candidate_phone_index_stmt FROM @sign_candidate_phone_index_sql;
EXECUTE sign_candidate_phone_index_stmt;
DEALLOCATE PREPARE sign_candidate_phone_index_stmt;
