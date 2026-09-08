-- Reviewed onboarding-signing personal facts and privacy-safe idempotency audit.
-- MySQL 5.7-compatible, rerunnable forward migration.

SET @erp_sign_profile_schema = DATABASE();

DROP PROCEDURE IF EXISTS migrate_system_sign_profile_supplement_20260718;
DELIMITER $$
CREATE PROCEDURE migrate_system_sign_profile_supplement_20260718()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @erp_sign_profile_schema
          AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'student_status'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN student_status varchar(20) DEFAULT NULL
                COMMENT '当前在校客观事实 STUDENT/NON_STUDENT'
                AFTER current_address;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @erp_sign_profile_schema
          AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'school_name'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN school_name varchar(200) DEFAULT NULL
                COMMENT '当前在读学校'
                AFTER student_status;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @erp_sign_profile_schema
          AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'retirement_status'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN retirement_status varchar(20) DEFAULT NULL
                COMMENT '退休客观事实 RETIRED/NOT_RETIRED'
                AFTER school_name;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @erp_sign_profile_schema
          AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'income_start_year_month'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN income_start_year_month char(7) DEFAULT NULL
                COMMENT '主要劳动收入起始月 yyyy-MM'
                AFTER retirement_status;
    END IF;
END$$
DELIMITER ;

CALL migrate_system_sign_profile_supplement_20260718();
DROP PROCEDURE IF EXISTS migrate_system_sign_profile_supplement_20260718;

CREATE TABLE IF NOT EXISTS sys_sign_profile_supplement_audit (
    audit_id bigint NOT NULL AUTO_INCREMENT COMMENT '审计ID',
    request_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'OA跨服务幂等请求ID（大小写敏感）',
    employee_id bigint NOT NULL COMMENT '员工用户ID',
    request_hash char(64) NOT NULL COMMENT '规范化请求SHA-256',
    field_mask varchar(200) NOT NULL COMMENT '本次更新的白名单字段，不含值',
    before_hash char(64) DEFAULT NULL COMMENT '更新前个人事实SHA-256',
    after_hash char(64) DEFAULT NULL COMMENT '更新后个人事实SHA-256',
    status varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED',
    create_by varchar(64) NOT NULL COMMENT '固定内部调用方标识',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_sign_profile_supplement_request (request_id),
    KEY idx_sign_profile_supplement_employee (employee_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签约补资审核后档案同步幂等审计';

-- Repair an already-created development table so rerunning this migration also
-- enforces Java request-id equality at the database unique-key boundary.
ALTER TABLE sys_sign_profile_supplement_audit
    MODIFY COLUMN request_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'OA跨服务幂等请求ID（大小写敏感）';
