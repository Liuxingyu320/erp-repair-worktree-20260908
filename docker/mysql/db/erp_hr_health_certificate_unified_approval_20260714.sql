-- 健康证审批切换统一引擎所需业务关联字段。
-- 普通幂等迁移：不修改存量 PENDING_REVIEW，旧待审核记录继续由 system 原审核接口处理。
-- MySQL 5.7 compatible.

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

-- 发布核对：PENDING_REVIEW 必须保持原值；新提交从 DRAFT/RETURNED 进入 APPROVAL_PENDING。
SELECT review_status,
       SUM(approval_instance_id IS NULL) AS without_unified_instance,
       SUM(approval_instance_id IS NOT NULL) AS with_unified_instance,
       COUNT(*) AS row_count
FROM hr_employee_health_certificate
WHERE del_flag = '0'
GROUP BY review_status
ORDER BY review_status;

