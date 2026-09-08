-- Attendance draft creation idempotency for all supported clients.
-- Callers that do not send client_request_id retain their current behaviour.
SET @attendance_client_schema := DATABASE();

SET @attendance_client_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_leave_request ADD COLUMN client_request_id varchar(64) DEFAULT NULL COMMENT ''客户端创建请求键'' AFTER leave_request_no',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_client_schema
      AND table_name = 'oa_attendance_leave_request'
      AND column_name = 'client_request_id'
);
PREPARE attendance_client_stmt FROM @attendance_client_sql;
EXECUTE attendance_client_stmt;
DEALLOCATE PREPARE attendance_client_stmt;

SET @attendance_client_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_leave_request ADD COLUMN client_request_fingerprint char(64) DEFAULT NULL COMMENT ''创建请求规范载荷SHA-256'' AFTER client_request_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_client_schema
      AND table_name = 'oa_attendance_leave_request'
      AND column_name = 'client_request_fingerprint'
);
PREPARE attendance_client_stmt FROM @attendance_client_sql;
EXECUTE attendance_client_stmt;
DEALLOCATE PREPARE attendance_client_stmt;

SET @attendance_client_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_leave_request ADD UNIQUE KEY uk_oa_attendance_leave_client_request (user_id, shop_id, client_request_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @attendance_client_schema
      AND table_name = 'oa_attendance_leave_request'
      AND index_name = 'uk_oa_attendance_leave_client_request'
);
PREPARE attendance_client_stmt FROM @attendance_client_sql;
EXECUTE attendance_client_stmt;
DEALLOCATE PREPARE attendance_client_stmt;

SET @attendance_client_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_correction_request ADD COLUMN client_request_id varchar(64) DEFAULT NULL COMMENT ''客户端创建请求键'' AFTER correction_request_no',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_client_schema
      AND table_name = 'oa_attendance_correction_request'
      AND column_name = 'client_request_id'
);
PREPARE attendance_client_stmt FROM @attendance_client_sql;
EXECUTE attendance_client_stmt;
DEALLOCATE PREPARE attendance_client_stmt;

SET @attendance_client_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_correction_request ADD COLUMN client_request_fingerprint char(64) DEFAULT NULL COMMENT ''创建请求规范载荷SHA-256'' AFTER client_request_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_client_schema
      AND table_name = 'oa_attendance_correction_request'
      AND column_name = 'client_request_fingerprint'
);
PREPARE attendance_client_stmt FROM @attendance_client_sql;
EXECUTE attendance_client_stmt;
DEALLOCATE PREPARE attendance_client_stmt;

SET @attendance_client_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_correction_request ADD UNIQUE KEY uk_oa_attendance_correction_client_request (user_id, shop_id, client_request_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @attendance_client_schema
      AND table_name = 'oa_attendance_correction_request'
      AND index_name = 'uk_oa_attendance_correction_client_request'
);
PREPARE attendance_client_stmt FROM @attendance_client_sql;
EXECUTE attendance_client_stmt;
DEALLOCATE PREPARE attendance_client_stmt;
