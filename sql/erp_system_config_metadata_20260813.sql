-- Expand the production schema required by the current system-service contract.
-- MySQL 5.7/8.0 compatible, safe to re-run, and backward compatible with the
-- previous application release. This migration never rewrites business data.

DROP PROCEDURE IF EXISTS sp_erp_system_config_metadata_20260813;
DELIMITER $$
CREATE PROCEDURE sp_erp_system_config_metadata_20260813()
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('sys_config', 'sys_user');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'system config metadata migration requires sys_config and sys_user';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'sys_config' AND column_name IN ('config_id', 'config_type'))
        OR (table_name = 'sys_user' AND column_name IN (
            'user_id', 'pwd_update_date', 'credential_state', 'temporary_password_expires_at'
        )));
    IF v_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'system config metadata migration base columns are incomplete';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_config'
      AND column_name = 'group_code';
    IF v_count = 0 THEN
        ALTER TABLE sys_config
            ADD COLUMN group_code VARCHAR(32) NOT NULL DEFAULT 'custom'
                COMMENT '配置分组' AFTER config_type;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_config'
      AND column_name = 'value_type';
    IF v_count = 0 THEN
        ALTER TABLE sys_config
            ADD COLUMN value_type VARCHAR(20) NOT NULL DEFAULT 'string'
                COMMENT '值类型' AFTER group_code;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_config'
      AND column_name = 'sensitive_flag';
    IF v_count = 0 THEN
        ALTER TABLE sys_config
            ADD COLUMN sensitive_flag CHAR(1) NOT NULL DEFAULT 'N'
                COMMENT '是否敏感（Y是 N否）' AFTER value_type;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_config'
      AND column_name = 'validation_rule';
    IF v_count = 0 THEN
        ALTER TABLE sys_config
            ADD COLUMN validation_rule VARCHAR(500) NULL DEFAULT NULL
                COMMENT '服务端校验规则' AFTER sensitive_flag;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_config'
      AND column_name = 'display_order';
    IF v_count = 0 THEN
        ALTER TABLE sys_config
            ADD COLUMN display_order INT NOT NULL DEFAULT 100
                COMMENT '分组内显示顺序' AFTER validation_rule;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_config'
      AND column_name = 'version';
    IF v_count = 0 THEN
        ALTER TABLE sys_config
            ADD COLUMN version INT NOT NULL DEFAULT 1
                COMMENT '乐观锁版本' AFTER display_order;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'must_change_password';
    IF v_count = 0 THEN
        ALTER TABLE sys_user
            ADD COLUMN must_change_password CHAR(1) NOT NULL DEFAULT '0'
                COMMENT '是否必须修改密码（0否 1是）' AFTER pwd_update_date;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_config'
      AND index_name = 'idx_sys_config_group_order';
    IF v_count = 0 THEN
        ALTER TABLE sys_config
            ADD INDEX idx_sys_config_group_order (group_code, display_order, config_id);
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND (
        (table_name = 'sys_config' AND column_name = 'group_code'
            AND data_type = 'varchar' AND character_maximum_length = 32
            AND is_nullable = 'NO' AND column_default = 'custom')
        OR (table_name = 'sys_config' AND column_name = 'value_type'
            AND data_type = 'varchar' AND character_maximum_length = 20
            AND is_nullable = 'NO' AND column_default = 'string')
        OR (table_name = 'sys_config' AND column_name = 'sensitive_flag'
            AND data_type = 'char' AND character_maximum_length = 1
            AND is_nullable = 'NO' AND column_default = 'N')
        OR (table_name = 'sys_config' AND column_name = 'validation_rule'
            AND data_type = 'varchar' AND character_maximum_length = 500
            AND is_nullable = 'YES' AND column_default IS NULL)
        OR (table_name = 'sys_config' AND column_name = 'display_order'
            AND data_type = 'int' AND is_nullable = 'NO' AND column_default = '100')
        OR (table_name = 'sys_config' AND column_name = 'version'
            AND data_type = 'int' AND is_nullable = 'NO' AND column_default = '1')
        OR (table_name = 'sys_user' AND column_name = 'must_change_password'
            AND data_type = 'char' AND character_maximum_length = 1
            AND is_nullable = 'NO' AND column_default = '0')
      );
    IF v_count <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'system config metadata column fingerprint verification failed';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM (
        SELECT table_name, index_name, non_unique,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_config'
          AND index_name = 'idx_sys_config_group_order'
        GROUP BY table_name, index_name, non_unique
    ) actual_index
    WHERE non_unique = 1
      AND columns_csv = 'group_code,display_order,config_id';
    IF v_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'system config metadata index fingerprint verification failed';
    END IF;
END$$
DELIMITER ;

CALL sp_erp_system_config_metadata_20260813();
DROP PROCEDURE sp_erp_system_config_metadata_20260813;

SELECT CONCAT(table_name, '.', column_name) AS installed_column
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'sys_config' AND column_name IN (
        'group_code', 'value_type', 'sensitive_flag',
        'validation_rule', 'display_order', 'version'
      ))
    OR (table_name = 'sys_user' AND column_name = 'must_change_password'))
ORDER BY table_name, ordinal_position;
