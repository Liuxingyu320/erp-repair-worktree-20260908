-- OA 采购测试数据与 Flowable 物理清理（不可逆、只允许人工执行一次）。
-- 执行前必须停止全部 erp-oa 实例。
-- 本脚本只适用于当前测试数据库，不得加入 Docker bootstrap 或普通 release runner。
-- 兼容两种受控前置结构：纯 Flowable 旧结构，或统一审批四个字段已提前加入的过渡结构。

SET @expected_database := 'BossERP_NEW';

DROP TEMPORARY TABLE IF EXISTS tmp_flowable_purge_tables;
DROP TEMPORARY TABLE IF EXISTS tmp_flowable_purge_parent_tables;
CREATE TEMPORARY TABLE tmp_flowable_purge_tables (
    table_name varchar(64) NOT NULL PRIMARY KEY
);
INSERT INTO tmp_flowable_purge_tables (table_name) VALUES
    ('ACT_EVT_LOG'),
    ('ACT_GE_BYTEARRAY'), ('ACT_GE_PROPERTY'),
    ('ACT_HI_ACTINST'), ('ACT_HI_ATTACHMENT'), ('ACT_HI_COMMENT'),
    ('ACT_HI_DETAIL'), ('ACT_HI_ENTITYLINK'), ('ACT_HI_IDENTITYLINK'),
    ('ACT_HI_PROCINST'), ('ACT_HI_TASKINST'), ('ACT_HI_TSK_LOG'),
    ('ACT_HI_VARINST'),
    ('ACT_ID_BYTEARRAY'), ('ACT_ID_GROUP'), ('ACT_ID_INFO'),
    ('ACT_ID_MEMBERSHIP'), ('ACT_ID_PRIV'), ('ACT_ID_PRIV_MAPPING'),
    ('ACT_ID_PROPERTY'), ('ACT_ID_TOKEN'), ('ACT_ID_USER'),
    ('ACT_PROCDEF_INFO'),
    ('ACT_RE_DEPLOYMENT'), ('ACT_RE_MODEL'), ('ACT_RE_PROCDEF'),
    ('ACT_RU_ACTINST'), ('ACT_RU_DEADLETTER_JOB'),
    ('ACT_RU_ENTITYLINK'), ('ACT_RU_EVENT_SUBSCR'),
    ('ACT_RU_EXECUTION'), ('ACT_RU_EXTERNAL_JOB'),
    ('ACT_RU_HISTORY_JOB'), ('ACT_RU_IDENTITYLINK'), ('ACT_RU_JOB'),
    ('ACT_RU_SUSPENDED_JOB'), ('ACT_RU_TASK'),
    ('ACT_RU_TIMER_JOB'), ('ACT_RU_VARIABLE'),
    ('FLW_RU_BATCH'), ('FLW_RU_BATCH_PART');

-- MySQL 8 does not allow the same temporary table to be opened twice in one
-- statement. Keep a second identical lookup table for the parent side of the
-- boundary-FK check while retaining MySQL 5.7 compatibility.
CREATE TEMPORARY TABLE tmp_flowable_purge_parent_tables (
    table_name varchar(64) NOT NULL PRIMARY KEY
);
INSERT INTO tmp_flowable_purge_parent_tables (table_name)
SELECT table_name FROM tmp_flowable_purge_tables;

DROP PROCEDURE IF EXISTS assert_oa_flowable_purge_preconditions;
DELIMITER $$

CREATE PROCEDURE assert_oa_flowable_purge_preconditions()
BEGIN
    DECLARE v_oa_table_count int DEFAULT 0;
    DECLARE v_comment_table_count int DEFAULT 0;
    DECLARE v_old_column_count int DEFAULT 0;
    DECLARE v_new_column_count int DEFAULT 0;
    DECLARE v_act_total_count int DEFAULT 0;
    DECLARE v_act_expected_count int DEFAULT 0;
    DECLARE v_flw_total_count int DEFAULT 0;
    DECLARE v_flw_expected_count int DEFAULT 0;
    DECLARE v_unknown_oa_fk_count int DEFAULT 0;
    DECLARE v_flowable_boundary_fk_count int DEFAULT 0;

    IF DATABASE() IS NULL OR DATABASE() <> @expected_database THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '拒绝执行：当前数据库名与脚本目标数据库不一致';
    END IF;

    SELECT COUNT(*) INTO v_oa_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase';

    SELECT COUNT(*) INTO v_comment_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase_comment';

    SELECT COUNT(*) INTO v_old_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase'
      AND COLUMN_NAME IN ('process_instance_id', 'current_task_id');

    SELECT COUNT(*) INTO v_new_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase'
      AND COLUMN_NAME IN (
          'approval_instance_id', 'approval_round',
          'row_version', 'last_approval_event_key'
      );

    -- oa_purchase_comment -> oa_purchase is the only expected inbound OA
    -- relationship because the comment table is dropped first. Any other
    -- table referencing either destructive target must be reviewed manually.
    SELECT COUNT(*) INTO v_unknown_oa_fk_count
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE REFERENCED_TABLE_SCHEMA = DATABASE()
      AND REFERENCED_TABLE_NAME IN
          ('oa_purchase', 'oa_purchase_comment')
      AND NOT (
          TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'oa_purchase_comment'
          AND REFERENCED_TABLE_NAME = 'oa_purchase'
      );

    -- Refuse any FK crossing the exact 41-table engine boundary in either
    -- direction. FOREIGN_KEY_CHECKS=0 must never leave a surviving table
    -- constrained to a table removed by this script.
    SELECT COUNT(*) INTO v_flowable_boundary_fk_count
    FROM information_schema.KEY_COLUMN_USAGE k
    LEFT JOIN tmp_flowable_purge_tables child_target
      ON k.TABLE_SCHEMA = DATABASE()
     AND child_target.table_name = k.TABLE_NAME
    LEFT JOIN tmp_flowable_purge_parent_tables parent_target
      ON k.REFERENCED_TABLE_SCHEMA = DATABASE()
     AND parent_target.table_name = k.REFERENCED_TABLE_NAME
    WHERE k.REFERENCED_TABLE_NAME IS NOT NULL
      AND (
          (child_target.table_name IS NOT NULL
              AND parent_target.table_name IS NULL)
          OR
          (child_target.table_name IS NULL
              AND parent_target.table_name IS NOT NULL)
      );

    SELECT COUNT(*) INTO v_act_total_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND LEFT(TABLE_NAME, 4) = 'ACT_';

    SELECT COUNT(*) INTO v_act_expected_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'ACT_EVT_LOG',
          'ACT_GE_BYTEARRAY', 'ACT_GE_PROPERTY',
          'ACT_HI_ACTINST', 'ACT_HI_ATTACHMENT', 'ACT_HI_COMMENT',
          'ACT_HI_DETAIL', 'ACT_HI_ENTITYLINK', 'ACT_HI_IDENTITYLINK',
          'ACT_HI_PROCINST', 'ACT_HI_TASKINST', 'ACT_HI_TSK_LOG',
          'ACT_HI_VARINST',
          'ACT_ID_BYTEARRAY', 'ACT_ID_GROUP', 'ACT_ID_INFO',
          'ACT_ID_MEMBERSHIP', 'ACT_ID_PRIV', 'ACT_ID_PRIV_MAPPING',
          'ACT_ID_PROPERTY', 'ACT_ID_TOKEN', 'ACT_ID_USER',
          'ACT_PROCDEF_INFO',
          'ACT_RE_DEPLOYMENT', 'ACT_RE_MODEL', 'ACT_RE_PROCDEF',
          'ACT_RU_ACTINST', 'ACT_RU_DEADLETTER_JOB',
          'ACT_RU_ENTITYLINK', 'ACT_RU_EVENT_SUBSCR',
          'ACT_RU_EXECUTION', 'ACT_RU_EXTERNAL_JOB',
          'ACT_RU_HISTORY_JOB', 'ACT_RU_IDENTITYLINK', 'ACT_RU_JOB',
          'ACT_RU_SUSPENDED_JOB', 'ACT_RU_TASK',
          'ACT_RU_TIMER_JOB', 'ACT_RU_VARIABLE'
      );

    SELECT COUNT(*) INTO v_flw_total_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND LEFT(TABLE_NAME, 4) = 'FLW_';

    SELECT COUNT(*) INTO v_flw_expected_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('FLW_RU_BATCH', 'FLW_RU_BATCH_PART');

    IF v_oa_table_count <> 1 OR v_comment_table_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '拒绝执行：OA 采购表或意见表不符合旧结构预期';
    END IF;
    IF v_old_column_count <> 2 OR v_new_column_count NOT IN (0, 4) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '拒绝执行：oa_purchase 不是受支持的 Flowable 旧结构或统一审批过渡结构';
    END IF;
    IF v_unknown_oa_fk_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '拒绝执行：OA 采购表存在未列入白名单的外键引用';
    END IF;
    IF v_flowable_boundary_fk_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '拒绝执行：Flowable 表与非清理表之间存在跨边界外键';
    END IF;
    IF v_act_total_count <> 39 OR v_act_expected_count <> 39 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '拒绝执行：ACT 表必须与当前明确的 39 张清单完全一致';
    END IF;
    IF v_flw_total_count <> 2 OR v_flw_expected_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '拒绝执行：FLW 表必须与当前明确的 2 张清单完全一致';
    END IF;
END$$

DELIMITER ;

CALL assert_oa_flowable_purge_preconditions();

-- 删除前发布审计计数。
SELECT DATABASE() AS target_database,
       (SELECT COUNT(*) FROM oa_purchase) AS oa_purchase_rows,
       (SELECT COUNT(*) FROM oa_purchase_comment) AS oa_purchase_comment_rows,
       (SELECT COUNT(*) FROM ACT_RU_TASK) AS flowable_runtime_task_rows,
       (SELECT COUNT(*) FROM ACT_RU_EXECUTION) AS flowable_runtime_execution_rows,
       (SELECT COUNT(*) FROM ACT_HI_PROCINST) AS flowable_history_instance_rows,
       (SELECT COUNT(*) FROM ACT_RE_PROCDEF) AS flowable_process_definition_rows,
       (SELECT COUNT(*) FROM FLW_RU_BATCH) AS flowable_batch_rows,
       (SELECT COUNT(*) FROM FLW_RU_BATCH_PART) AS flowable_batch_part_rows,
       39 AS expected_act_table_count,
       2 AS expected_flw_table_count;

DROP PROCEDURE IF EXISTS purge_oa_flowable_test_data;
DELIMITER $$

CREATE PROCEDURE purge_oa_flowable_test_data()
BEGIN
    DECLARE v_old_foreign_key_checks int DEFAULT 1;
    DECLARE v_new_column_count int DEFAULT 0;
    DECLARE v_new_index_count int DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET FOREIGN_KEY_CHECKS = v_old_foreign_key_checks;
        RESIGNAL;
    END;

    SET v_old_foreign_key_checks = @@FOREIGN_KEY_CHECKS;

    DROP TABLE oa_purchase_comment;
    TRUNCATE TABLE oa_purchase;

    SELECT COUNT(*) INTO v_new_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase'
      AND COLUMN_NAME IN (
          'approval_instance_id', 'approval_round',
          'row_version', 'last_approval_event_key'
      );

    SELECT COUNT(*) INTO v_new_index_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase'
      AND INDEX_NAME = 'idx_oa_purchase_approval_instance';

    IF v_new_column_count = 0 THEN
        ALTER TABLE oa_purchase
            DROP INDEX idx_oa_purchase_proc,
            DROP COLUMN process_instance_id,
            DROP COLUMN current_task_id,
            ADD COLUMN approval_instance_id bigint(20) DEFAULT NULL
                COMMENT '当前或最近统一审批实例ID' AFTER status,
            ADD COLUMN approval_round int NOT NULL DEFAULT 0
                COMMENT '当前或最近统一审批轮次' AFTER approval_instance_id,
            ADD COLUMN row_version bigint(20) NOT NULL DEFAULT 0
                COMMENT '业务行乐观锁版本' AFTER approval_round,
            ADD COLUMN last_approval_event_key varchar(128) DEFAULT NULL
                COMMENT '最近成功消费的统一审批事件键' AFTER row_version,
            ADD KEY idx_oa_purchase_approval_instance
                (approval_instance_id, approval_round);
    ELSE
        ALTER TABLE oa_purchase
            DROP INDEX idx_oa_purchase_proc,
            DROP COLUMN process_instance_id,
            DROP COLUMN current_task_id;

        IF v_new_index_count = 0 THEN
            ALTER TABLE oa_purchase
                ADD KEY idx_oa_purchase_approval_instance
                    (approval_instance_id, approval_round);
        END IF;
    END IF;

    SET FOREIGN_KEY_CHECKS = 0;

    -- 必须显式列出当前实际存在的 39 张 ACT 表和 2 张 FLW 表，禁止模糊动态 DROP。
    DROP TABLE
        ACT_EVT_LOG,
        ACT_GE_BYTEARRAY,
        ACT_GE_PROPERTY,
        ACT_HI_ACTINST,
        ACT_HI_ATTACHMENT,
        ACT_HI_COMMENT,
        ACT_HI_DETAIL,
        ACT_HI_ENTITYLINK,
        ACT_HI_IDENTITYLINK,
        ACT_HI_PROCINST,
        ACT_HI_TASKINST,
        ACT_HI_TSK_LOG,
        ACT_HI_VARINST,
        ACT_ID_BYTEARRAY,
        ACT_ID_GROUP,
        ACT_ID_INFO,
        ACT_ID_MEMBERSHIP,
        ACT_ID_PRIV,
        ACT_ID_PRIV_MAPPING,
        ACT_ID_PROPERTY,
        ACT_ID_TOKEN,
        ACT_ID_USER,
        ACT_PROCDEF_INFO,
        ACT_RE_DEPLOYMENT,
        ACT_RE_MODEL,
        ACT_RE_PROCDEF,
        ACT_RU_ACTINST,
        ACT_RU_DEADLETTER_JOB,
        ACT_RU_ENTITYLINK,
        ACT_RU_EVENT_SUBSCR,
        ACT_RU_EXECUTION,
        ACT_RU_EXTERNAL_JOB,
        ACT_RU_HISTORY_JOB,
        ACT_RU_IDENTITYLINK,
        ACT_RU_JOB,
        ACT_RU_SUSPENDED_JOB,
        ACT_RU_TASK,
        ACT_RU_TIMER_JOB,
        ACT_RU_VARIABLE,
        FLW_RU_BATCH_PART,
        FLW_RU_BATCH;

    SET FOREIGN_KEY_CHECKS = v_old_foreign_key_checks;
END$$

DELIMITER ;

CALL purge_oa_flowable_test_data();
DROP PROCEDURE purge_oa_flowable_test_data;

DROP PROCEDURE IF EXISTS assert_oa_flowable_purge_result;
DELIMITER $$

CREATE PROCEDURE assert_oa_flowable_purge_result()
BEGIN
    DECLARE v_purchase_rows bigint DEFAULT 0;
    DECLARE v_comment_tables int DEFAULT 0;
    DECLARE v_act_tables int DEFAULT 0;
    DECLARE v_flw_tables int DEFAULT 0;
    DECLARE v_old_columns int DEFAULT 0;
    DECLARE v_new_columns int DEFAULT 0;

    SELECT COUNT(*) INTO v_purchase_rows FROM oa_purchase;
    SELECT COUNT(*) INTO v_comment_tables
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase_comment';
    SELECT COUNT(*) INTO v_act_tables
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND LEFT(TABLE_NAME, 4) = 'ACT_';
    SELECT COUNT(*) INTO v_flw_tables
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND LEFT(TABLE_NAME, 4) = 'FLW_';
    SELECT COUNT(*) INTO v_old_columns
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase'
      AND COLUMN_NAME IN ('process_instance_id', 'current_task_id');
    SELECT COUNT(*) INTO v_new_columns
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase'
      AND COLUMN_NAME IN (
          'approval_instance_id', 'approval_round',
          'row_version', 'last_approval_event_key'
      );

    IF v_purchase_rows <> 0 OR v_comment_tables <> 0
       OR v_act_tables <> 0 OR v_flw_tables <> 0
       OR v_old_columns <> 0 OR v_new_columns <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '清理结果异常：请停止发布并人工核对，不得重复执行脚本';
    END IF;
END$$

DELIMITER ;

CALL assert_oa_flowable_purge_result();

SELECT COUNT(*) AS oa_purchase_rows FROM oa_purchase;
SELECT COUNT(*) AS oa_purchase_comment_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_purchase_comment';
SELECT COUNT(*) AS remaining_act_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND LEFT(TABLE_NAME, 4) = 'ACT_';
SELECT COUNT(*) AS remaining_flw_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND LEFT(TABLE_NAME, 4) = 'FLW_';

DROP PROCEDURE assert_oa_flowable_purge_result;
DROP PROCEDURE assert_oa_flowable_purge_preconditions;
DROP TEMPORARY TABLE tmp_flowable_purge_parent_tables;
DROP TEMPORARY TABLE tmp_flowable_purge_tables;
