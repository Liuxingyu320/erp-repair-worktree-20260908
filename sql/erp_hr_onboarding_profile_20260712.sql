-- Persist stable onboarding data used by immutable HR signing snapshots.
-- Safe to rerun on databases created before phase 3 Task 4.

DROP PROCEDURE IF EXISTS migrate_hr_onboarding_profile_20260712;
DELIMITER $$
CREATE PROCEDURE migrate_hr_onboarding_profile_20260712()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
    ) THEN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'legal_entity_id'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN legal_entity_id bigint DEFAULT NULL COMMENT '稳定法律主体ID' AFTER legal_entity;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'legal_entity_code'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN legal_entity_code varchar(64) DEFAULT NULL COMMENT '稳定法律主体代码' AFTER legal_entity_id;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'probation_start_date'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN probation_start_date date DEFAULT NULL COMMENT '试用期开始日期' AFTER probation_period;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'probation_end_date'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN probation_end_date date DEFAULT NULL COMMENT '试用期结束日期' AFTER probation_start_date;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'base_salary'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN base_salary decimal(16,2) DEFAULT NULL COMMENT '基本工资' AFTER legal_entity_code;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'post_salary'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN post_salary decimal(16,2) DEFAULT NULL COMMENT '岗位工资' AFTER base_salary;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'field_allowance'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN field_allowance decimal(16,2) DEFAULT NULL COMMENT '外勤补贴' AFTER post_salary;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'performance_salary'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN performance_salary decimal(16,2) DEFAULT NULL COMMENT '绩效工资' AFTER field_allowance;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'salary_total'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN salary_total decimal(16,2) DEFAULT NULL COMMENT '薪资合计' AFTER performance_salary;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_profile'
          AND COLUMN_NAME = 'salary_version'
    ) THEN
        ALTER TABLE sys_user_profile
            ADD COLUMN salary_version varchar(32) DEFAULT NULL COMMENT '薪资版本' AFTER salary_total;
    END IF;
    END IF;
END$$
DELIMITER ;

CALL migrate_hr_onboarding_profile_20260712();
DROP PROCEDURE IF EXISTS migrate_hr_onboarding_profile_20260712;
