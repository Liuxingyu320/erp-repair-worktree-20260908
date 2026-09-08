-- Stock-check difference approval schema, permission, and legacy-state migration.
-- Repeat-safe for MySQL 8.0. Run with a client that supports DELIMITER.

SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS add_stock_check_column_if_missing;
DROP PROCEDURE IF EXISTS add_stock_check_index_if_missing;
DROP PROCEDURE IF EXISTS normalize_stock_check_table_collation_if_needed;

DELIMITER $$

CREATE PROCEDURE add_stock_check_column_if_missing(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @migration_ddl = p_ddl;
        PREPARE migration_stmt FROM @migration_ddl;
        EXECUTE migration_stmt;
        DEALLOCATE PREPARE migration_stmt;
    END IF;
END$$

CREATE PROCEDURE add_stock_check_index_if_missing(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @migration_ddl = p_ddl;
        PREPARE migration_stmt FROM @migration_ddl;
        EXECUTE migration_stmt;
        DEALLOCATE PREPARE migration_stmt;
    END IF;
END$$

CREATE PROCEDURE normalize_stock_check_table_collation_if_needed(
    IN p_table_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
          AND TABLE_COLLATION <> 'utf8mb4_general_ci'
    ) THEN
        SET @migration_ddl = p_ddl;
        PREPARE migration_stmt FROM @migration_ddl;
        EXECUTE migration_stmt;
        DEALLOCATE PREPARE migration_stmt;
    END IF;
END$$

DELIMITER ;

CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'approval_instance_id',
    'ALTER TABLE inv_stock_check ADD COLUMN approval_instance_id bigint DEFAULT NULL COMMENT ''当前审批实例'' AFTER status');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'approval_round',
    'ALTER TABLE inv_stock_check ADD COLUMN approval_round int NOT NULL DEFAULT 0 COMMENT ''当前或最近审批轮次'' AFTER approval_instance_id');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'submitted_user_id',
    'ALTER TABLE inv_stock_check ADD COLUMN submitted_user_id bigint DEFAULT NULL COMMENT ''最近提交人ID'' AFTER approval_round');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'submitted_by',
    'ALTER TABLE inv_stock_check ADD COLUMN submitted_by varchar(64) DEFAULT NULL COMMENT ''最近提交账号'' AFTER submitted_user_id');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'submitted_time',
    'ALTER TABLE inv_stock_check ADD COLUMN submitted_time datetime DEFAULT NULL COMMENT ''最近提交时间'' AFTER submitted_by');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'approved_user_id',
    'ALTER TABLE inv_stock_check ADD COLUMN approved_user_id bigint DEFAULT NULL COMMENT ''最近通过人ID'' AFTER submitted_time');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'approved_by',
    'ALTER TABLE inv_stock_check ADD COLUMN approved_by varchar(64) DEFAULT NULL COMMENT ''最近通过账号'' AFTER approved_user_id');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'approved_time',
    'ALTER TABLE inv_stock_check ADD COLUMN approved_time datetime DEFAULT NULL COMMENT ''最近通过时间'' AFTER approved_by');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'last_reject_reason',
    'ALTER TABLE inv_stock_check ADD COLUMN last_reject_reason varchar(500) DEFAULT NULL COMMENT ''最近驳回原因'' AFTER approved_time');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'last_rejected_user_id',
    'ALTER TABLE inv_stock_check ADD COLUMN last_rejected_user_id bigint DEFAULT NULL COMMENT ''最近驳回人ID'' AFTER last_reject_reason');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'last_rejected_by',
    'ALTER TABLE inv_stock_check ADD COLUMN last_rejected_by varchar(64) DEFAULT NULL COMMENT ''最近驳回账号'' AFTER last_rejected_user_id');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'last_rejected_time',
    'ALTER TABLE inv_stock_check ADD COLUMN last_rejected_time datetime DEFAULT NULL COMMENT ''最近驳回时间'' AFTER last_rejected_by');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'last_invalid_reason',
    'ALTER TABLE inv_stock_check ADD COLUMN last_invalid_reason varchar(500) DEFAULT NULL COMMENT ''最近库存失效原因'' AFTER last_rejected_time');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'last_invalid_detail_snapshot',
    'ALTER TABLE inv_stock_check ADD COLUMN last_invalid_detail_snapshot longtext DEFAULT NULL COMMENT ''最近库存失效明细'' AFTER last_invalid_reason');
CALL add_stock_check_column_if_missing(
    'inv_stock_check', 'last_invalidated_time',
    'ALTER TABLE inv_stock_check ADD COLUMN last_invalidated_time datetime DEFAULT NULL COMMENT ''最近库存失效时间'' AFTER last_invalid_detail_snapshot');

CREATE TABLE IF NOT EXISTS inv_stock_check_approval_instance (
    instance_id bigint NOT NULL AUTO_INCREMENT COMMENT '审批实例ID',
    check_id bigint NOT NULL COMMENT '盘点单ID',
    round_no int NOT NULL COMMENT '审批轮次',
    status varchar(32) NOT NULL COMMENT 'running/approved/rejected/invalidated/cancelled',
    shop_dept_id bigint NOT NULL COMMENT '盘点组织ID',
    check_no varchar(64) NOT NULL COMMENT '盘点单号快照',
    profit_item_count int NOT NULL DEFAULT 0 COMMENT '盘盈项数',
    loss_item_count int NOT NULL DEFAULT 0 COMMENT '盘亏项数',
    total_abs_diff_quantity decimal(18,4) NOT NULL DEFAULT 0 COMMENT '差异绝对数量合计',
    detail_snapshot longtext NOT NULL COMMENT '提交明细快照',
    invalid_reason varchar(500) DEFAULT NULL COMMENT '库存快照失效原因',
    invalid_detail_snapshot longtext DEFAULT NULL COMMENT '库存快照变化明细',
    adjustment_result_snapshot longtext DEFAULT NULL COMMENT '库存调整前后结果',
    submitted_user_id bigint DEFAULT NULL COMMENT '提交人ID',
    submitted_by varchar(64) DEFAULT NULL COMMENT '提交账号',
    submitted_time datetime DEFAULT NULL COMMENT '提交时间',
    finished_user_id bigint DEFAULT NULL COMMENT '完成人ID',
    finished_by varchar(64) DEFAULT NULL COMMENT '完成账号',
    finished_time datetime DEFAULT NULL COMMENT '完成时间',
    finish_comment varchar(500) DEFAULT NULL COMMENT '完成意见',
    create_by varchar(64) DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) DEFAULT NULL,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (instance_id),
    UNIQUE KEY uk_inv_stock_check_approval_round (check_id, round_no),
    KEY idx_inv_stock_check_approval_status (status, shop_dept_id, submitted_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存盘点审批实例';

CREATE TABLE IF NOT EXISTS inv_stock_check_approval_task (
    task_id bigint NOT NULL AUTO_INCREMENT COMMENT '审批任务ID',
    instance_id bigint NOT NULL COMMENT '审批实例ID',
    check_id bigint NOT NULL COMMENT '盘点单ID',
    round_no int NOT NULL COMMENT '审批轮次',
    candidate_user_ids text NOT NULL COMMENT '候选审批人ID快照',
    candidate_user_names text NOT NULL COMMENT '候选审批人姓名快照',
    status varchar(32) NOT NULL COMMENT 'pending/approved/rejected/invalidated/cancelled',
    action varchar(32) DEFAULT NULL COMMENT '实际动作',
    approver_user_id bigint DEFAULT NULL COMMENT '实际审批人ID',
    approver_name varchar(64) DEFAULT NULL COMMENT '实际审批账号',
    approval_comment varchar(500) DEFAULT NULL COMMENT '审批意见',
    approval_time datetime DEFAULT NULL COMMENT '审批时间',
    self_approved char(1) NOT NULL DEFAULT '0' COMMENT '是否自审',
    create_by varchar(64) DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) DEFAULT NULL,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id),
    KEY idx_inv_stock_check_approval_task_instance (instance_id, task_id),
    KEY idx_inv_stock_check_approval_task_status (status, check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存盘点审批任务';

CALL normalize_stock_check_table_collation_if_needed(
    'inv_stock_check_approval_instance',
    'ALTER TABLE inv_stock_check_approval_instance CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci');
CALL normalize_stock_check_table_collation_if_needed(
    'inv_stock_check_approval_task',
    'ALTER TABLE inv_stock_check_approval_task CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci');

CALL add_stock_check_column_if_missing(
    'inv_stock_check_approval_instance', 'adjustment_result_snapshot',
    'ALTER TABLE inv_stock_check_approval_instance ADD COLUMN adjustment_result_snapshot longtext DEFAULT NULL COMMENT ''库存调整前后结果'' AFTER invalid_detail_snapshot');

CALL add_stock_check_index_if_missing(
    'inv_stock_check_approval_instance', 'uk_inv_stock_check_approval_round',
    'ALTER TABLE inv_stock_check_approval_instance ADD UNIQUE KEY uk_inv_stock_check_approval_round (check_id, round_no)');
CALL add_stock_check_index_if_missing(
    'inv_stock_check_approval_task', 'idx_inv_stock_check_approval_task_instance',
    'ALTER TABLE inv_stock_check_approval_task ADD KEY idx_inv_stock_check_approval_task_instance (instance_id, task_id)');

DROP PROCEDURE IF EXISTS add_stock_check_column_if_missing;
DROP PROCEDURE IF EXISTS add_stock_check_index_if_missing;
DROP PROCEDURE IF EXISTS normalize_stock_check_table_collation_if_needed;

-- Preserve the complete pre-migration source rows before classifying legacy checking data.
CREATE TABLE IF NOT EXISTS inv_stock_check_backup_20260710 LIKE inv_stock_check;
INSERT IGNORE INTO inv_stock_check_backup_20260710 SELECT * FROM inv_stock_check;
CREATE TABLE IF NOT EXISTS inv_stock_check_detail_backup_20260710 LIKE inv_stock_check_detail;
INSERT IGNORE INTO inv_stock_check_detail_backup_20260710 SELECT * FROM inv_stock_check_detail;

-- Incomplete actual quantities return to draft.
UPDATE inv_stock_check s
SET s.status = 'draft',
    s.remark = concat_ws('；', nullif(s.remark, ''), '历史待确认数据实盘不完整，已退回草稿'),
    s.update_by = 'migration'
WHERE s.status = 'checking'
  AND EXISTS (
      SELECT 1 FROM inv_stock_check_detail d
      WHERE d.check_id = s.check_id AND d.actual_qty IS NULL
  );

-- Complete actual quantities with a stale inventory snapshot require a fresh stock check.
UPDATE inv_stock_check s
SET s.status = 'invalidated',
    s.last_invalid_reason = '历史盘点库存快照已变化，需要重新盘点',
    s.last_invalid_detail_snapshot = (
        SELECT json_arrayagg(json_object(
            'detailId', d.detail_id,
            'productId', d.product_id,
            'productName', d.product_name,
            'bookQuantity', d.book_qty,
            'currentQuantity', coalesce((
                SELECT max(st.current_quantity)
                FROM inv_stock st
                WHERE st.product_id = d.product_id
                  AND st.shop_dept_id = s.shop_dept_id
                  AND st.warehouse_id = coalesce(s.warehouse_id, s.shop_dept_id)
            ), 0)
        ))
        FROM inv_stock_check_detail d
        WHERE d.check_id = s.check_id
          AND coalesce(d.book_qty, 0) <> coalesce((
              SELECT max(st.current_quantity)
              FROM inv_stock st
              WHERE st.product_id = d.product_id
                AND st.shop_dept_id = s.shop_dept_id
                AND st.warehouse_id = coalesce(s.warehouse_id, s.shop_dept_id)
          ), 0)
    ),
    s.last_invalidated_time = CURRENT_TIMESTAMP,
    s.submitted_by = coalesce(nullif(s.update_by, ''), nullif(s.create_by, ''), 'migration'),
    s.submitted_time = CURRENT_TIMESTAMP,
    s.update_by = 'migration'
WHERE s.status = 'checking'
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_check_detail d
      WHERE d.check_id = s.check_id AND d.actual_qty IS NULL
  )
  AND EXISTS (
      SELECT 1
      FROM inv_stock_check_detail d
      WHERE d.check_id = s.check_id
        AND coalesce(d.book_qty, 0) <> coalesce((
            SELECT max(st.current_quantity)
            FROM inv_stock st
            WHERE st.product_id = d.product_id
              AND st.shop_dept_id = s.shop_dept_id
              AND st.warehouse_id = coalesce(s.warehouse_id, s.shop_dept_id)
        ), 0)
  );

-- Valid, complete, zero-difference legacy checks complete without inventory movement.
UPDATE inv_stock_check s
SET s.status = 'completed',
    s.remark = concat_ws('；', nullif(s.remark, ''), '历史无差异盘点自动完成'),
    s.submitted_by = coalesce(nullif(s.update_by, ''), nullif(s.create_by, ''), 'migration'),
    s.submitted_time = CURRENT_TIMESTAMP,
    s.update_by = 'migration'
WHERE s.status = 'checking'
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_check_detail d
      WHERE d.check_id = s.check_id AND d.actual_qty IS NULL
  )
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_check_detail d
      WHERE d.check_id = s.check_id
        AND coalesce(d.actual_qty, 0) <> coalesce(d.book_qty, 0)
  );

-- Remaining valid differences are rejected for an explicit new approval submission.
UPDATE inv_stock_check s
SET s.status = 'rejected',
    s.last_reject_reason = '审批制度升级，需重新提交',
    s.last_rejected_by = 'migration',
    s.last_rejected_time = CURRENT_TIMESTAMP,
    s.submitted_by = coalesce(nullif(s.update_by, ''), nullif(s.create_by, ''), 'migration'),
    s.submitted_time = CURRENT_TIMESTAMP,
    s.update_by = 'migration'
WHERE s.status = 'checking'
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_check_detail d
      WHERE d.check_id = s.check_id AND d.actual_qty IS NULL
  );

-- Add the new approval function below the stock-check page without assuming a fixed menu ID.
SET @stock_check_parent_id = (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:stockCheck:list'
    ORDER BY menu_id LIMIT 1
);
SET @stock_check_approval_menu_id = (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'inv:stockCheck:approve'
    ORDER BY menu_id LIMIT 1
);
SET @stock_check_approval_menu_id = coalesce(
    @stock_check_approval_menu_id,
    (SELECT coalesce(max(menu_id), 0) + 1 FROM sys_menu)
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT @stock_check_approval_menu_id, '盘点审批', @stock_check_parent_id, 9,
       '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'inv:stockCheck:approve', '#',
       'system', CURRENT_TIMESTAMP, '库存盘点差异审批'
FROM DUAL
WHERE @stock_check_parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'inv:stockCheck:approve'
  );

UPDATE sys_menu
SET menu_name = '盘点审批', parent_id = @stock_check_parent_id,
    visible = '0', status = '0', update_by = 'system', update_time = CURRENT_TIMESTAMP
WHERE perms = 'inv:stockCheck:approve';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'inv:stockCheck:approve' AND m.status = '0'
WHERE r.role_key = 'yyzj'
  AND r.del_flag = '0'
  AND r.status = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- The removed direct-confirm endpoint must not remain assignable to any role.
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms = 'inv:stockCheck:confirm';

UPDATE sys_menu
SET status = '1', visible = '1', update_by = 'system', update_time = CURRENT_TIMESTAMP,
    remark = CASE
        WHEN locate('盘点审批升级后停用', coalesce(remark, '')) > 0 THEN remark
        ELSE concat_ws('；', nullif(remark, ''), '盘点审批升级后停用')
    END
WHERE perms = 'inv:stockCheck:confirm'
  AND (
      coalesce(status, '') <> '1'
      OR coalesce(visible, '') <> '1'
      OR coalesce(update_by, '') <> 'system'
      OR locate('盘点审批升级后停用', coalesce(remark, '')) = 0
  );
