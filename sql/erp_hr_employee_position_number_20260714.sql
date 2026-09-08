-- HR fixed employee number + mutable position number.
-- Compatible with MySQL 5.7 and 8.0.
-- Employee number: configurable alpha prefix + five-digit global sequence (E00001).
-- Position number: uppercase post code + '-' + employee number (CYS-E00001).

SET @hr_position_migration_lock_name =
    CONCAT('erp_hr_pos_20260714:', MD5(COALESCE(DATABASE(), 'NO_DATABASE')));
SET @hr_position_migration_lock_acquired = GET_LOCK(@hr_position_migration_lock_name, 60);
SET @hr_position_lock_guard_sql = IF(
    COALESCE(@hr_position_migration_lock_acquired, 0) = 1,
    'DO 0',
    'SELECT * FROM information_schema.erp_hr_position_migration_lock_not_acquired');
PREPARE hr_position_lock_guard FROM @hr_position_lock_guard_sql;
EXECUTE hr_position_lock_guard;
DEALLOCATE PREPARE hr_position_lock_guard;

SET @erp_db = DATABASE();

CREATE TABLE IF NOT EXISTS hr_employee_no_sequence (
    sequence_key varchar(32) NOT NULL COMMENT '流水键，当前固定为GLOBAL',
    current_value int unsigned NOT NULL DEFAULT 0 COMMENT '已分配最大五位流水',
    max_value int unsigned NOT NULL DEFAULT 99999 COMMENT '流水上限',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR员工号全局流水';

CREATE TABLE IF NOT EXISTS hr_employee_position_no_history (
    history_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    user_id bigint(20) NOT NULL COMMENT '用户ID',
    employee_no varchar(64) NOT NULL COMMENT '固定员工号',
    old_position_no varchar(96) DEFAULT NULL COMMENT '变更前岗位工号',
    new_position_no varchar(96) NOT NULL COMMENT '变更后岗位工号',
    old_post_id bigint(20) DEFAULT NULL COMMENT '变更前岗位ID',
    new_post_id bigint(20) DEFAULT NULL COMMENT '变更后岗位ID',
    old_post_code varchar(64) DEFAULT NULL COMMENT '变更前岗位编码',
    new_post_code varchar(64) NOT NULL COMMENT '变更后岗位编码',
    effective_date date DEFAULT NULL COMMENT '岗位工号生效日期',
    change_source varchar(32) NOT NULL COMMENT '来源：MIGRATION_BACKFILL/PROFILE_INSERT/POSITION_CHANGE',
    change_by varchar(64) NOT NULL DEFAULT 'system' COMMENT '变更人',
    changed_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (history_id),
    KEY idx_hr_position_history_user (user_id, changed_time),
    KEY idx_hr_position_history_employee (employee_no, changed_time),
    KEY idx_hr_position_history_new_no (new_position_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工岗位工号变更历史';

CREATE TABLE IF NOT EXISTS hr_employee_no_resequence_batch (
    batch_code varchar(32) NOT NULL COMMENT '重排批次编码',
    employee_prefix varchar(4) NOT NULL COMMENT '员工号前缀',
    number_width tinyint unsigned NOT NULL COMMENT '数字位数',
    employee_count int unsigned NOT NULL COMMENT '本批重排人数',
    ordering_rule varchar(500) NOT NULL COMMENT '确定性排序规则',
    applied_by varchar(64) NOT NULL DEFAULT 'migration' COMMENT '执行人',
    applied_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    remark varchar(500) DEFAULT NULL COMMENT '说明',
    PRIMARY KEY (batch_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工号层级重排批次';

CREATE TABLE IF NOT EXISTS hr_employee_no_resequence_history (
    batch_code varchar(32) NOT NULL COMMENT '重排批次编码',
    user_id bigint(20) NOT NULL COMMENT '用户ID',
    sequence_no int unsigned NOT NULL COMMENT '层级顺序',
    old_employee_no varchar(64) DEFAULT NULL COMMENT '重排前员工号',
    new_employee_no varchar(64) NOT NULL COMMENT '重排后员工号',
    old_position_no varchar(96) DEFAULT NULL COMMENT '重排前岗位工号',
    new_position_no varchar(96) NOT NULL COMMENT '重排后岗位工号',
    post_id bigint(20) NOT NULL COMMENT '重排时岗位ID',
    post_code varchar(64) NOT NULL COMMENT '重排时岗位编码',
    post_sort int NOT NULL COMMENT '重排时岗位排序',
    dept_id bigint(20) DEFAULT NULL COMMENT '重排时部门ID',
    dept_type varchar(20) DEFAULT NULL COMMENT '重排时部门类型',
    dept_order int DEFAULT NULL COMMENT '重排时部门排序',
    entry_date date DEFAULT NULL COMMENT '重排时入职日期',
    login_name_changed tinyint(1) NOT NULL DEFAULT 0 COMMENT '登录名是否随员工号调整',
    changed_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '重排时间',
    PRIMARY KEY (batch_code, user_id),
    UNIQUE KEY uk_hr_employee_resequence_new_no (batch_code, new_employee_no),
    KEY idx_hr_employee_resequence_user (user_id, changed_time),
    KEY idx_hr_employee_resequence_old_no (old_employee_no),
    KEY idx_hr_employee_resequence_new_no (new_employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工号层级重排映射';

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile'
       AND COLUMN_NAME = 'position_no') = 0,
    'ALTER TABLE sys_user_profile ADD COLUMN position_no varchar(96) DEFAULT NULL COMMENT ''当前岗位工号（岗位编码-固定员工号）'' AFTER employee_no',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop our guards while reconciling legacy data; they are recreated below.
DROP TRIGGER IF EXISTS trg_sys_user_profile_employee_no_bu;
DROP TRIGGER IF EXISTS trg_sys_user_profile_position_no_ai;
DROP TRIGGER IF EXISTS trg_sys_user_profile_position_no_au;
DROP TRIGGER IF EXISTS trg_sys_post_code_bu;

-- Replace three imported placeholder codes with stable business codes.
UPDATE sys_post target
LEFT JOIN sys_post existing ON existing.post_code = 'cpzl'
    AND existing.post_id <> target.post_id
SET target.post_code = 'cpzl'
WHERE target.post_code = 'imp_a495ddb0e5'
  AND existing.post_id IS NULL;

UPDATE sys_post target
LEFT JOIN sys_post existing ON existing.post_code = 'xtwh'
    AND existing.post_id <> target.post_id
SET target.post_code = 'xtwh'
WHERE target.post_code = 'imp_e58e33693d'
  AND existing.post_id IS NULL;

UPDATE sys_post target
LEFT JOIN sys_post existing ON existing.post_code = 'cgzy'
    AND existing.post_id <> target.post_id
SET target.post_code = 'cgzy'
WHERE target.post_code = 'imp_302599fc00'
  AND existing.post_id IS NULL;

DROP PROCEDURE IF EXISTS erp_hr_employee_position_number_apply;
DELIMITER $$
CREATE PROCEDURE erp_hr_employee_position_number_apply()
BEGIN
    DECLARE v_prefix varchar(4);
    DECLARE v_sequence_value int unsigned DEFAULT 0;
    DECLARE v_existing_max int unsigned DEFAULT 0;
    DECLARE v_base_value int unsigned DEFAULT 0;
    DECLARE v_backfill_count int unsigned DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        DO RELEASE_LOCK(@hr_position_migration_lock_name);
        RESIGNAL;
    END;

    SELECT UPPER(COALESCE(NULLIF(TRIM((
        SELECT config_value
        FROM sys_config
        WHERE config_key = 'hr.employee.no.prefix'
        LIMIT 1
    )), ''), 'E')) INTO v_prefix;

    IF v_prefix COLLATE utf8mb4_general_ci
            NOT REGEXP _utf8mb4'^[A-Z]{1,4}$' COLLATE utf8mb4_general_ci THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'hr.employee.no.prefix must contain 1-4 ASCII letters';
    END IF;

    IF EXISTS (
        SELECT 1 FROM sys_post
        WHERE status = '0'
          AND (post_code IS NULL OR TRIM(post_code) COLLATE utf8mb4_general_ci
              NOT REGEXP _utf8mb4'^[A-Za-z0-9]{2,16}$' COLLATE utf8mb4_general_ci)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active sys_post contains an invalid position-number post_code';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM sys_post
        GROUP BY UPPER(TRIM(post_code))
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'sys_post.post_code contains duplicates';
    END IF;

    INSERT INTO hr_employee_no_sequence (
        sequence_key, current_value, max_value, create_time, update_time
    ) VALUES ('GLOBAL', 0, 99999, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON DUPLICATE KEY UPDATE max_value = 99999;

    SELECT current_value INTO v_sequence_value
    FROM hr_employee_no_sequence
    WHERE sequence_key = 'GLOBAL'
    FOR UPDATE;

    SELECT GREATEST(
        COALESCE((
            SELECT MAX(CAST(SUBSTRING(employee_no, CHAR_LENGTH(v_prefix) + 1) AS UNSIGNED))
            FROM sys_user_profile
            WHERE employee_no COLLATE utf8mb4_general_ci
                REGEXP CONCAT('^', v_prefix, '[0-9]{5}$') COLLATE utf8mb4_general_ci
        ), 0),
        COALESCE((
            SELECT MAX(CAST(SUBSTRING(user_name, CHAR_LENGTH(v_prefix) + 1) AS UNSIGNED))
            FROM sys_user
            WHERE user_name COLLATE utf8mb4_general_ci
                REGEXP CONCAT('^', v_prefix, '[0-9]{5}$') COLLATE utf8mb4_general_ci
        ), 0)
    ) INTO v_existing_max;

    SET v_base_value = GREATEST(v_sequence_value, v_existing_max);

    DROP TEMPORARY TABLE IF EXISTS tmp_hr_employee_no_backfill;
    CREATE TEMPORARY TABLE tmp_hr_employee_no_backfill (
        sequence_offset int unsigned NOT NULL AUTO_INCREMENT,
        user_id bigint(20) NOT NULL,
        PRIMARY KEY (sequence_offset),
        UNIQUE KEY uk_tmp_hr_employee_user (user_id)
    ) ENGINE=InnoDB;

    INSERT INTO tmp_hr_employee_no_backfill (user_id)
    SELECT p.user_id
    FROM sys_user_profile p
    WHERE p.employee_no IS NULL OR TRIM(p.employee_no) = ''
    ORDER BY p.user_id;

    SELECT COUNT(*) INTO v_backfill_count FROM tmp_hr_employee_no_backfill;
    IF v_base_value + v_backfill_count > 99999 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'five-digit employee number sequence exhausted during backfill';
    END IF;

    UPDATE sys_user_profile p
    INNER JOIN tmp_hr_employee_no_backfill pending ON pending.user_id = p.user_id
    SET p.employee_no = CONCAT(v_prefix,
            LPAD(v_base_value + pending.sequence_offset, 5, '0')),
        p.update_by = COALESCE(NULLIF(p.update_by, ''), 'migration'),
        p.update_time = CURRENT_TIMESTAMP;

    UPDATE hr_employee_no_sequence
    SET current_value = v_base_value + v_backfill_count,
        max_value = 99999,
        update_time = CURRENT_TIMESTAMP
    WHERE sequence_key = 'GLOBAL';

    IF EXISTS (
        SELECT 1
        FROM sys_user u
        INNER JOIN sys_user_profile p ON p.user_id = u.user_id
        WHERE u.del_flag = '0'
          AND COALESCE(p.employee_status, '') <> '离职'
          AND (
              SELECT COUNT(DISTINCT sup.post_id)
              FROM sys_user_post sup
              INNER JOIN sys_post sp ON sp.post_id = sup.post_id AND sp.status = '0'
              WHERE sup.user_id = u.user_id
          ) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'an active employee must have exactly one enabled post';
    END IF;

    UPDATE sys_user_profile p
    INNER JOIN (
        SELECT sup.user_id, MIN(sp.post_id) AS post_id, MIN(sp.post_code) AS post_code
        FROM sys_user_post sup
        INNER JOIN sys_post sp ON sp.post_id = sup.post_id AND sp.status = '0'
        GROUP BY sup.user_id
        HAVING COUNT(DISTINCT sup.post_id) = 1
    ) current_post ON current_post.user_id = p.user_id
    SET p.position_no = CONCAT(UPPER(TRIM(current_post.post_code)), '-',
            UPPER(TRIM(p.employee_no))),
        p.update_by = COALESCE(NULLIF(p.update_by, ''), 'migration'),
        p.update_time = CURRENT_TIMESTAMP
    WHERE p.employee_no IS NOT NULL
      AND TRIM(p.employee_no) <> ''
      AND NOT (p.position_no COLLATE utf8mb4_general_ci
              <=> CONCAT(UPPER(TRIM(current_post.post_code)), '-',
                  UPPER(TRIM(p.employee_no))) COLLATE utf8mb4_general_ci);

    IF EXISTS (
        SELECT 1
        FROM sys_user_profile
        WHERE position_no IS NOT NULL AND TRIM(position_no) <> ''
        GROUP BY position_no
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'generated sys_user_profile.position_no contains duplicates';
    END IF;

    INSERT INTO hr_employee_position_no_history (
        user_id, employee_no, old_position_no, new_position_no,
        old_post_id, new_post_id, old_post_code, new_post_code,
        effective_date, change_source, change_by, changed_time, remark
    )
    SELECT p.user_id, p.employee_no, NULL, p.position_no,
           NULL, sp.post_id, NULL, UPPER(TRIM(sp.post_code)),
           COALESCE(p.current_position_start_date, p.entry_date, DATE(p.create_time)),
           'MIGRATION_BACKFILL', 'migration', CURRENT_TIMESTAMP,
           '岗位工号上线时建立的基线记录'
    FROM sys_user_profile p
    INNER JOIN sys_user_post sup ON sup.user_id = p.user_id
    INNER JOIN sys_post sp ON sp.post_id = sup.post_id AND sp.status = '0'
    WHERE p.position_no IS NOT NULL
      AND TRIM(p.position_no) <> ''
      AND NOT EXISTS (
          SELECT 1
          FROM hr_employee_position_no_history history
          WHERE history.user_id = p.user_id
      );

    DROP TEMPORARY TABLE IF EXISTS tmp_hr_employee_no_backfill;
END$$
DELIMITER ;

CALL erp_hr_employee_position_number_apply();
DROP PROCEDURE IF EXISTS erp_hr_employee_position_number_apply;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_user_profile'
       AND INDEX_NAME = 'uk_user_profile_position_no') = 0,
    'ALTER TABLE sys_user_profile ADD UNIQUE KEY uk_user_profile_position_no (position_no)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'sys_post'
       AND INDEX_NAME = 'uk_sys_post_post_code') = 0,
    'ALTER TABLE sys_post ADD UNIQUE KEY uk_sys_post_post_code (post_code)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DELIMITER $$
CREATE TRIGGER trg_sys_user_profile_employee_no_bu
BEFORE UPDATE ON sys_user_profile
FOR EACH ROW
BEGIN
    IF COALESCE(@hr_employee_no_resequence_bypass, 0) <> 1
       AND NULLIF(TRIM(OLD.employee_no), '') IS NOT NULL
       AND NOT (OLD.employee_no <=> NEW.employee_no) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'employee_no is immutable after assignment';
    END IF;
END$$

CREATE TRIGGER trg_sys_post_code_bu
BEFORE UPDATE ON sys_post
FOR EACH ROW
BEGIN
    IF NOT (OLD.post_code <=> NEW.post_code)
       AND EXISTS (SELECT 1 FROM sys_user_post WHERE post_id = OLD.post_id LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'assigned post_code is immutable; create a new post instead';
    END IF;
END$$

CREATE TRIGGER trg_sys_user_profile_position_no_ai
AFTER INSERT ON sys_user_profile
FOR EACH ROW
BEGIN
    DECLARE v_post_code varchar(64);
    DECLARE v_post_id bigint(20);
    IF NULLIF(TRIM(NEW.position_no), '') IS NOT NULL THEN
        SET v_post_code = SUBSTRING_INDEX(NEW.position_no, '-', 1);
        SELECT MIN(post_id) INTO v_post_id
        FROM sys_post
        WHERE CAST(UPPER(TRIM(post_code)) AS BINARY) = CAST(UPPER(TRIM(v_post_code)) AS BINARY);
        INSERT INTO hr_employee_position_no_history (
            user_id, employee_no, old_position_no, new_position_no,
            old_post_id, new_post_id, old_post_code, new_post_code,
            effective_date, change_source, change_by, changed_time, remark
        ) VALUES (
            NEW.user_id, NEW.employee_no, NULL, NEW.position_no,
            NULL, v_post_id, NULL, UPPER(TRIM(v_post_code)),
            COALESCE(NEW.current_position_start_date, NEW.entry_date, CURRENT_DATE),
            'PROFILE_INSERT', COALESCE(NULLIF(NEW.create_by, ''), 'system'),
            CURRENT_TIMESTAMP, '新员工首次生成岗位工号'
        );
    END IF;
END$$

CREATE TRIGGER trg_sys_user_profile_position_no_au
AFTER UPDATE ON sys_user_profile
FOR EACH ROW
BEGIN
    DECLARE v_old_post_code varchar(64);
    DECLARE v_new_post_code varchar(64);
    DECLARE v_old_post_id bigint(20);
    DECLARE v_new_post_id bigint(20);
    IF COALESCE(@hr_employee_no_resequence_bypass, 0) <> 1
       AND NOT (OLD.position_no <=> NEW.position_no)
       AND NULLIF(TRIM(NEW.position_no), '') IS NOT NULL THEN
        SET v_old_post_code = CASE
            WHEN NULLIF(TRIM(OLD.position_no), '') IS NULL THEN NULL
            ELSE SUBSTRING_INDEX(OLD.position_no, '-', 1)
        END;
        SET v_new_post_code = SUBSTRING_INDEX(NEW.position_no, '-', 1);
        SELECT MIN(post_id) INTO v_old_post_id
        FROM sys_post
        WHERE CAST(UPPER(TRIM(post_code)) AS BINARY) = CAST(UPPER(TRIM(v_old_post_code)) AS BINARY);
        SELECT MIN(post_id) INTO v_new_post_id
        FROM sys_post
        WHERE CAST(UPPER(TRIM(post_code)) AS BINARY) = CAST(UPPER(TRIM(v_new_post_code)) AS BINARY);
        INSERT INTO hr_employee_position_no_history (
            user_id, employee_no, old_position_no, new_position_no,
            old_post_id, new_post_id, old_post_code, new_post_code,
            effective_date, change_source, change_by, changed_time, remark
        ) VALUES (
            NEW.user_id, NEW.employee_no, OLD.position_no, NEW.position_no,
            v_old_post_id, v_new_post_id, UPPER(TRIM(v_old_post_code)),
            UPPER(TRIM(v_new_post_code)),
            COALESCE(
                CASE
                    WHEN NOT (OLD.actual_regularization_date <=> NEW.actual_regularization_date)
                    THEN NEW.actual_regularization_date
                END,
                NEW.current_position_start_date, NEW.entry_date, CURRENT_DATE),
            'POSITION_CHANGE', COALESCE(NULLIF(NEW.update_by, ''), 'system'),
            CURRENT_TIMESTAMP, '岗位变化自动更新岗位工号'
        );
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS erp_hr_employee_number_hierarchy_resequence_apply;
DELIMITER $$
CREATE PROCEDURE erp_hr_employee_number_hierarchy_resequence_apply()
main: BEGIN
    DECLARE v_batch_code varchar(32) DEFAULT 'HIERARCHY_V1';
    DECLARE v_prefix varchar(4);
    DECLARE v_batch_exists int unsigned DEFAULT 0;
    DECLARE v_profile_count int unsigned DEFAULT 0;
    DECLARE v_general_manager_count int unsigned DEFAULT 0;
    DECLARE v_current_value int unsigned DEFAULT 0;
    DECLARE v_existing_max int unsigned DEFAULT 0;
    DECLARE v_first_post_code varchar(64);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @hr_employee_no_resequence_bypass = 0;
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS tmp_hr_employee_no_resequence;
        DO RELEASE_LOCK(@hr_position_migration_lock_name);
        RESIGNAL;
    END;

    SET @hr_employee_no_resequence_bypass = 0;

    SELECT UPPER(COALESCE(NULLIF(TRIM((
        SELECT config_value
        FROM sys_config
        WHERE config_key = 'hr.employee.no.prefix'
        LIMIT 1
    )), ''), 'E')) INTO v_prefix;

    IF v_prefix COLLATE utf8mb4_general_ci
            NOT REGEXP _utf8mb4'^[A-Z]{1,4}$' COLLATE utf8mb4_general_ci THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'hr.employee.no.prefix must contain 1-4 ASCII letters';
    END IF;

    INSERT INTO hr_employee_no_sequence (
        sequence_key, current_value, max_value, create_time, update_time
    ) VALUES ('GLOBAL', 0, 99999, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON DUPLICATE KEY UPDATE max_value = 99999;

    START TRANSACTION;

    SELECT current_value INTO v_current_value
    FROM hr_employee_no_sequence
    WHERE sequence_key = 'GLOBAL'
    FOR UPDATE;

    SELECT COUNT(*) INTO v_batch_exists
    FROM hr_employee_no_resequence_batch
    WHERE batch_code COLLATE utf8mb4_general_ci
        = v_batch_code COLLATE utf8mb4_general_ci;

    IF v_batch_exists > 0 THEN
        IF EXISTS (
            SELECT 1
            FROM hr_employee_no_resequence_history history
            LEFT JOIN sys_user_profile profile ON profile.user_id = history.user_id
            WHERE history.batch_code COLLATE utf8mb4_general_ci
                    = v_batch_code COLLATE utf8mb4_general_ci
              AND (
                  profile.user_id IS NULL
                  OR NOT (profile.employee_no COLLATE utf8mb4_general_ci
                      <=> history.new_employee_no COLLATE utf8mb4_general_ci)
              )
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'employee number resequence audit differs from current profiles';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM sys_user_profile
            WHERE employee_no IS NULL
               OR TRIM(employee_no) = ''
               OR employee_no COLLATE utf8mb4_general_ci
                    NOT REGEXP CONCAT('^', v_prefix, '[0-9]{5}$') COLLATE utf8mb4_general_ci
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'current employee number is not a canonical five-digit sequence';
        END IF;

        SELECT COALESCE(MAX(CAST(SUBSTRING(
            employee_no, CHAR_LENGTH(v_prefix) + 1) AS UNSIGNED)), 0)
        INTO v_existing_max
        FROM sys_user_profile
        WHERE employee_no COLLATE utf8mb4_general_ci
            REGEXP CONCAT('^', v_prefix, '[0-9]{5}$') COLLATE utf8mb4_general_ci;

        UPDATE hr_employee_no_sequence
        SET current_value = GREATEST(v_current_value, v_existing_max),
            max_value = 99999,
            update_time = CURRENT_TIMESTAMP
        WHERE sequence_key = 'GLOBAL';

        COMMIT;
        LEAVE main;
    END IF;

    SELECT COUNT(*) INTO v_profile_count FROM sys_user_profile;

    IF v_profile_count = 0 THEN
        UPDATE hr_employee_no_sequence
        SET max_value = 99999,
            update_time = CURRENT_TIMESTAMP
        WHERE sequence_key = 'GLOBAL';
        COMMIT;
        LEAVE main;
    END IF;

    IF v_profile_count > 99999 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'five-digit employee number sequence cannot hold all profiles';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM sys_user_profile profile
        LEFT JOIN sys_user user_account ON user_account.user_id = profile.user_id
        WHERE user_account.user_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'employee profile without a user account cannot be resequenced';
    END IF;

    IF EXISTS (
        SELECT profile.user_id
        FROM sys_user_profile profile
        LEFT JOIN sys_user_post user_post ON user_post.user_id = profile.user_id
        LEFT JOIN sys_post post
          ON post.post_id = user_post.post_id AND post.status = '0'
        GROUP BY profile.user_id
        HAVING COUNT(DISTINCT post.post_id) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'every employee profile must have exactly one enabled post before resequencing';
    END IF;

    SELECT COUNT(DISTINCT profile.user_id) INTO v_general_manager_count
    FROM sys_user_profile profile
    INNER JOIN sys_user_post user_post ON user_post.user_id = profile.user_id
    INNER JOIN sys_post post ON post.post_id = user_post.post_id AND post.status = '0'
    WHERE LOWER(TRIM(post.post_code)) = 'zjl';

    IF v_general_manager_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'more than one general manager is assigned; E00001 would be ambiguous';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_hr_employee_no_resequence;
    CREATE TEMPORARY TABLE tmp_hr_employee_no_resequence (
        sequence_no int unsigned NOT NULL AUTO_INCREMENT,
        user_id bigint(20) NOT NULL,
        old_employee_no varchar(64) DEFAULT NULL,
        new_employee_no varchar(64) DEFAULT NULL,
        old_position_no varchar(96) DEFAULT NULL,
        new_position_no varchar(96) DEFAULT NULL,
        post_id bigint(20) NOT NULL,
        post_code varchar(64) NOT NULL,
        post_sort int NOT NULL,
        dept_id bigint(20) DEFAULT NULL,
        dept_type varchar(20) DEFAULT NULL,
        dept_order int DEFAULT NULL,
        entry_date date DEFAULT NULL,
        login_name_changed tinyint(1) NOT NULL DEFAULT 0,
        PRIMARY KEY (sequence_no),
        UNIQUE KEY uk_tmp_hr_employee_resequence_user (user_id),
        UNIQUE KEY uk_tmp_hr_employee_resequence_new_no (new_employee_no)
    ) ENGINE=InnoDB;

    INSERT INTO tmp_hr_employee_no_resequence (
        user_id, old_employee_no, old_position_no,
        post_id, post_code, post_sort,
        dept_id, dept_type, dept_order, entry_date, login_name_changed
    )
    SELECT profile.user_id, profile.employee_no, profile.position_no,
           post.post_id, UPPER(TRIM(post.post_code)), post.post_sort,
           dept.dept_id, UPPER(TRIM(dept.dept_type)), dept.order_num,
           profile.entry_date,
           CASE
               WHEN CAST(user_account.user_name AS BINARY)
                    = CAST(profile.employee_no AS BINARY) THEN 1
               ELSE 0
           END
    FROM sys_user_profile profile
    INNER JOIN sys_user user_account ON user_account.user_id = profile.user_id
    INNER JOIN sys_user_post user_post ON user_post.user_id = profile.user_id
    INNER JOIN sys_post post ON post.post_id = user_post.post_id AND post.status = '0'
    LEFT JOIN sys_dept dept ON dept.dept_id = user_account.dept_id
    ORDER BY
        CASE WHEN LOWER(TRIM(post.post_code)) = 'zjl' THEN 0 ELSE 1 END,
        post.post_sort,
        post.post_id,
        CASE UPPER(COALESCE(dept.dept_type, ''))
            WHEN 'COMPANY' THEN 0
            WHEN 'GROUP' THEN 1
            WHEN 'WAREHOUSE' THEN 2
            WHEN 'STORE' THEN 3
            ELSE 9
        END,
        COALESCE(dept.order_num, 2147483647),
        COALESCE(dept.dept_id, 9223372036854775807),
        COALESCE(profile.entry_date, '9999-12-31'),
        profile.user_id;

    UPDATE tmp_hr_employee_no_resequence
    SET new_employee_no = CONCAT(v_prefix, LPAD(sequence_no, 5, '0')),
        new_position_no = CONCAT(post_code, '-', v_prefix,
            LPAD(sequence_no, 5, '0'));

    IF v_general_manager_count = 1 THEN
        SELECT post_code INTO v_first_post_code
        FROM tmp_hr_employee_no_resequence
        WHERE sequence_no = 1;
        IF LOWER(TRIM(v_first_post_code)) <> 'zjl' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'general manager must receive the first employee number';
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM sys_user user_account
        INNER JOIN tmp_hr_employee_no_resequence mapping
          ON user_account.user_name COLLATE utf8mb4_general_ci
             = mapping.new_employee_no COLLATE utf8mb4_general_ci
        WHERE user_account.user_id <> mapping.user_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'new employee number conflicts with an existing login name';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM sys_user user_account
        INNER JOIN tmp_hr_employee_no_resequence mapping
          ON user_account.user_name COLLATE utf8mb4_general_ci
             = CONCAT('RESEQ_', mapping.user_id) COLLATE utf8mb4_general_ci
        WHERE user_account.user_id <> mapping.user_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'temporary resequence login name conflicts with an existing account';
    END IF;

    INSERT INTO hr_employee_no_resequence_history (
        batch_code, user_id, sequence_no,
        old_employee_no, new_employee_no, old_position_no, new_position_no,
        post_id, post_code, post_sort,
        dept_id, dept_type, dept_order, entry_date,
        login_name_changed, changed_time
    )
    SELECT v_batch_code, user_id, sequence_no,
           old_employee_no, new_employee_no, old_position_no, new_position_no,
           post_id, post_code, post_sort,
           dept_id, dept_type, dept_order, entry_date,
           login_name_changed, CURRENT_TIMESTAMP
    FROM tmp_hr_employee_no_resequence;

    SET @hr_employee_no_resequence_bypass = 1;

    UPDATE sys_user user_account
    INNER JOIN tmp_hr_employee_no_resequence mapping
      ON mapping.user_id = user_account.user_id
    SET user_account.user_name = CONCAT('RESEQ_', mapping.user_id),
        user_account.update_by = 'migration',
        user_account.update_time = CURRENT_TIMESTAMP
    WHERE mapping.login_name_changed = 1;

    UPDATE hr_onboarding onboarding
    INNER JOIN tmp_hr_employee_no_resequence mapping
      ON onboarding.linked_user_id = mapping.user_id
      OR onboarding.employee_no COLLATE utf8mb4_general_ci
         = mapping.old_employee_no COLLATE utf8mb4_general_ci
    SET onboarding.employee_no = mapping.new_employee_no,
        onboarding.update_by = 'migration',
        onboarding.update_time = CURRENT_TIMESTAMP
    WHERE onboarding.employee_no IS NOT NULL
      AND TRIM(onboarding.employee_no) <> '';

    UPDATE hr_employee_position_no_history history
    INNER JOIN tmp_hr_employee_no_resequence mapping
      ON mapping.user_id = history.user_id
    SET history.employee_no = mapping.new_employee_no,
        history.old_position_no = CASE
            WHEN NULLIF(TRIM(history.old_position_no), '') IS NULL THEN NULL
            ELSE CONCAT(SUBSTRING_INDEX(history.old_position_no, '-', 1),
                '-', mapping.new_employee_no)
        END,
        history.new_position_no = CONCAT(
            SUBSTRING_INDEX(history.new_position_no, '-', 1),
            '-', mapping.new_employee_no);

    UPDATE sys_user_profile profile
    INNER JOIN tmp_hr_employee_no_resequence mapping
      ON mapping.user_id = profile.user_id
    SET profile.employee_no = CONCAT('TMP', LPAD(mapping.user_id, 20, '0')),
        profile.position_no = CONCAT('TMP-', LPAD(mapping.user_id, 20, '0')),
        profile.update_by = 'migration',
        profile.update_time = CURRENT_TIMESTAMP;

    UPDATE sys_user_profile profile
    INNER JOIN tmp_hr_employee_no_resequence mapping
      ON mapping.user_id = profile.user_id
    SET profile.employee_no = mapping.new_employee_no,
        profile.position_no = mapping.new_position_no,
        profile.update_by = 'migration',
        profile.update_time = CURRENT_TIMESTAMP;

    UPDATE sys_user user_account
    INNER JOIN tmp_hr_employee_no_resequence mapping
      ON mapping.user_id = user_account.user_id
    SET user_account.user_name = mapping.new_employee_no,
        user_account.update_by = 'migration',
        user_account.update_time = CURRENT_TIMESTAMP
    WHERE mapping.login_name_changed = 1;

    SET @hr_employee_no_resequence_bypass = 0;

    UPDATE hr_employee_no_sequence
    SET current_value = GREATEST(v_current_value, v_profile_count),
        max_value = 99999,
        update_time = CURRENT_TIMESTAMP
    WHERE sequence_key = 'GLOBAL';

    IF EXISTS (
        SELECT 1
        FROM sys_user_profile profile
        INNER JOIN tmp_hr_employee_no_resequence mapping
          ON mapping.user_id = profile.user_id
        WHERE NOT (profile.employee_no COLLATE utf8mb4_general_ci
                <=> mapping.new_employee_no COLLATE utf8mb4_general_ci)
           OR NOT (profile.position_no COLLATE utf8mb4_general_ci
                <=> mapping.new_position_no COLLATE utf8mb4_general_ci)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'employee number resequence verification failed';
    END IF;

    IF (SELECT COUNT(*) FROM sys_user_profile) <>
       (SELECT COUNT(DISTINCT employee_no) FROM sys_user_profile) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'employee number resequence generated duplicates';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM sys_user_profile
        WHERE employee_no COLLATE utf8mb4_general_ci
            NOT REGEXP CONCAT('^', v_prefix, '[0-9]{5}$') COLLATE utf8mb4_general_ci
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'employee number resequence generated a non-canonical value';
    END IF;

    INSERT INTO hr_employee_no_resequence_batch (
        batch_code, employee_prefix, number_width, employee_count,
        ordering_rule, applied_by, applied_time, remark
    ) VALUES (
        v_batch_code, v_prefix, 5, v_profile_count,
        'GENERAL_MANAGER_FIRST,POST_SORT,POST_ID,DEPT_TYPE,DEPT_ORDER,DEPT_ID,ENTRY_DATE,USER_ID',
        'migration', CURRENT_TIMESTAMP,
        '现有员工一次性按岗位与组织层级重排；后续员工只递增，不插队重排'
    );

    COMMIT;
    DROP TEMPORARY TABLE IF EXISTS tmp_hr_employee_no_resequence;
END$$
DELIMITER ;

CALL erp_hr_employee_number_hierarchy_resequence_apply();
DROP PROCEDURE IF EXISTS erp_hr_employee_number_hierarchy_resequence_apply;

DO RELEASE_LOCK(@hr_position_migration_lock_name);
