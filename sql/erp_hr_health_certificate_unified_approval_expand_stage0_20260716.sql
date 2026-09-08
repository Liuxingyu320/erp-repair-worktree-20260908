-- Stage 0 health-certificate association expansion for unified approval.
-- MySQL 5.7 / 8.0 compatible and repeat-safe for the approved baseline.
-- Existing PENDING_REVIEW records remain on the legacy review path.

SET @erp_db = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db
                  AND TABLE_NAME = 'hr_employee_health_certificate'
                  AND COLUMN_NAME = 'approval_instance_id') = 0,
    'ALTER TABLE hr_employee_health_certificate ADD COLUMN approval_instance_id bigint(20) DEFAULT NULL COMMENT ''当前或最近统一审批实例ID'' AFTER rejection_reason',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db
                  AND TABLE_NAME = 'hr_employee_health_certificate'
                  AND COLUMN_NAME = 'approval_round') = 0,
    'ALTER TABLE hr_employee_health_certificate ADD COLUMN approval_round int NOT NULL DEFAULT 0 COMMENT ''当前或最近统一审批轮次'' AFTER approval_instance_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @erp_db
                  AND TABLE_NAME = 'hr_employee_health_certificate'
                  AND COLUMN_NAME = 'last_approval_event_key') = 0,
    'ALTER TABLE hr_employee_health_certificate ADD COLUMN last_approval_event_key varchar(128) DEFAULT NULL COMMENT ''最近成功消费的统一审批事件键'' AFTER approval_round',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @erp_db
                  AND TABLE_NAME = 'hr_employee_health_certificate'
                  AND INDEX_NAME = 'idx_hr_health_approval_instance') = 0,
    'ALTER TABLE hr_employee_health_certificate ADD KEY idx_hr_health_approval_instance (approval_instance_id, approval_round)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE hr_employee_health_certificate
    MODIFY COLUMN review_status varchar(32) NOT NULL DEFAULT 'DRAFT'
    COMMENT 'DRAFT/PENDING_REVIEW(旧)/APPROVAL_SUBMITTING/APPROVAL_PENDING/APPROVED/RETURNED/REJECTED/WITHDRAWN/TERMINATED';
