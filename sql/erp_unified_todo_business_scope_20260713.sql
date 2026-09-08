-- 历史脚本：已被 2026-07-14 统一审批中心切换方案取代。
-- 本文件仅保留已执行逻辑供审计，不再进入 Docker bootstrap，不得在移除 Flowable 后执行。
-- 统一待办业务口径收口（MySQL 5.7 / 8.0，可重复执行）。
-- 发布批次：UNIFIED_TODO_SCOPE_20260713_V1
--
-- 作用：
--   1. OA 采购写功能默认关闭，并停用旧采购申请/OA待办/OA已办菜单；
--   2. 增加盘点临期阈值配置；
--   3. 校验盘点责任字段与索引已由前置迁移建好；
--   4. 输出需要人工治理的活动盘点，不伪造责任人或截止时间。
--
-- 安全边界：
--   * OA 采购存在 submitted 单据、运行任务或运行实例时立即阻断；
--   * feature.oa.purchase.enabled 已存在且不是 false 时立即阻断，脚本不覆盖管理员配置；
--   * 不删除 oa_purchase、Flowable 历史或任何库存采购数据；
--   * 不创建、回填或清空盘点业务责任字段。

SET @unified_todo_release_batch :=
    _utf8mb4'UNIFIED_TODO_SCOPE_20260713_V1' COLLATE utf8mb4_general_ci;

DROP PROCEDURE IF EXISTS verify_unified_todo_business_scope_20260713;

DELIMITER $$

CREATE PROCEDURE verify_unified_todo_business_scope_20260713()
BEGIN
    DECLARE v_missing_tables int DEFAULT 0;
    DECLARE v_submitted_purchase int DEFAULT 0;
    DECLARE v_runtime_tasks int DEFAULT 0;
    DECLARE v_runtime_instances int DEFAULT 0;
    DECLARE v_duplicate_configs int DEFAULT 0;
    DECLARE v_purchase_gate_not_closed int DEFAULT 0;
    DECLARE v_required_columns int DEFAULT 0;
    DECLARE v_due_index_ok int DEFAULT 0;
    DECLARE v_error_message varchar(500);

    SELECT COUNT(*) INTO v_missing_tables
    FROM (
        SELECT 'oa_purchase' AS table_name
        UNION ALL SELECT 'ACT_RU_TASK'
        UNION ALL SELECT 'ACT_RU_EXECUTION'
        UNION ALL SELECT 'inv_stock_check'
        UNION ALL SELECT 'sys_config'
        UNION ALL SELECT 'sys_menu'
        UNION ALL SELECT 'sys_role_menu'
    ) required_table
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLES actual_table
        WHERE actual_table.TABLE_SCHEMA = DATABASE()
          AND LOWER(actual_table.TABLE_NAME) = LOWER(required_table.table_name)
    );

    IF v_missing_tables > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '统一待办发布阻断：缺少 OA/Flowable/盘点/菜单前置表，请先完成基础迁移';
    END IF;

    SELECT COUNT(*) INTO v_duplicate_configs
    FROM (
        SELECT config_key
        FROM sys_config
        WHERE config_key IN (
            'feature.oa.purchase.enabled',
            'todo.stock-check.due-soon.hours'
        )
        GROUP BY config_key
        HAVING COUNT(*) > 1
    ) duplicate_config;

    IF v_duplicate_configs > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '统一待办发布阻断：目标配置键存在重复记录，请先人工去重';
    END IF;

    SELECT COUNT(*) INTO v_purchase_gate_not_closed
    FROM sys_config
    WHERE config_key = 'feature.oa.purchase.enabled'
      AND LOWER(TRIM(COALESCE(config_value, ''))) <> 'false';

    IF v_purchase_gate_not_closed > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '统一待办发布阻断：feature.oa.purchase.enabled 必须先由管理员确认为 false';
    END IF;

    SELECT COUNT(*) INTO v_submitted_purchase
    FROM oa_purchase
    WHERE LOWER(status) = 'submitted';

    SELECT COUNT(DISTINCT task_row.ID_) INTO v_runtime_tasks
    FROM oa_purchase purchase_row
    INNER JOIN ACT_RU_TASK task_row ON task_row.ID_ = purchase_row.current_task_id
    WHERE LOWER(purchase_row.status) = 'submitted';

    SELECT COUNT(DISTINCT execution_row.PROC_INST_ID_) INTO v_runtime_instances
    FROM ACT_RU_EXECUTION execution_row
    WHERE execution_row.PROC_DEF_ID_ LIKE 'oaPurchaseApproval:%';

    IF v_submitted_purchase > 0 OR v_runtime_tasks > 0 OR v_runtime_instances > 0 THEN
        SET v_error_message = CONCAT(
            '统一待办发布阻断：OA采购仍有活动事项 submitted=',
            v_submitted_purchase,
            ', tasks=',
            v_runtime_tasks,
            ', instances=',
            v_runtime_instances
        );
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error_message;
    END IF;

    SELECT COUNT(DISTINCT COLUMN_NAME) INTO v_required_columns
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inv_stock_check'
      AND COLUMN_NAME IN ('counter_user_id', 'counter_name', 'deadline');

    IF v_required_columns <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '统一待办发布阻断：inv_stock_check 缺少 counter_user_id/counter_name/deadline 前置字段';
    END IF;

    SELECT COUNT(*) INTO v_due_index_ok
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'inv_stock_check'
          AND INDEX_NAME = 'idx_stock_check_counter_due'
        GROUP BY INDEX_NAME
        HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') =
               'counter_user_id,status,deadline'
    ) valid_due_index;

    IF v_due_index_ok <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '统一待办发布阻断：缺少 idx_stock_check_counter_due(counter_user_id,status,deadline)';
    END IF;
END$$

DELIMITER ;

CALL verify_unified_todo_business_scope_20260713();
DROP PROCEDURE IF EXISTS verify_unified_todo_business_scope_20260713;

CREATE TABLE IF NOT EXISTS sys_unified_todo_config_backup_20260713 (
    release_batch varchar(64) NOT NULL COMMENT '发布批次',
    config_key varchar(100) NOT NULL COMMENT '配置键',
    existed tinyint(1) NOT NULL COMMENT '发布前是否存在',
    config_id int DEFAULT NULL,
    config_name varchar(100) DEFAULT NULL,
    config_value varchar(500) DEFAULT NULL,
    config_type char(1) DEFAULT NULL,
    create_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    backup_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (release_batch, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一待办配置发布前快照';

CREATE TABLE IF NOT EXISTS sys_unified_todo_menu_backup_20260713 (
    release_batch varchar(64) NOT NULL COMMENT '发布批次',
    menu_id bigint NOT NULL COMMENT '菜单ID',
    visible char(1) DEFAULT NULL,
    status char(1) DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    backup_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (release_batch, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一待办OA采购退役菜单快照';

CREATE TABLE IF NOT EXISTS sys_unified_todo_role_menu_backup_20260713 (
    release_batch varchar(64) NOT NULL COMMENT '发布批次',
    role_id bigint NOT NULL COMMENT '角色ID',
    menu_id bigint NOT NULL COMMENT '菜单ID',
    backup_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (release_batch, role_id, menu_id),
    KEY idx_unified_todo_role_menu_batch_menu (release_batch, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一待办OA采购退役角色菜单快照';

DROP TEMPORARY TABLE IF EXISTS tmp_unified_todo_oa_purchase_menu_20260713;
CREATE TEMPORARY TABLE tmp_unified_todo_oa_purchase_menu_20260713 (
    menu_id bigint NOT NULL,
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB;

-- 用组件路径和权限前缀识别，不依赖各环境完全相同的 menu_id。
INSERT IGNORE INTO tmp_unified_todo_oa_purchase_menu_20260713 (menu_id)
SELECT menu_id
FROM sys_menu
WHERE LOWER(COALESCE(component, '')) IN (
        'oa/purchase/index',
        'oa/todo/index',
        'oa/done/index',
        'inventory/purchase-request/index',
        'inventory/purchase-request/history'
    )
   OR LOWER(COALESCE(perms, '')) LIKE 'oa:purchase:%'
   OR LOWER(COALESCE(perms, '')) LIKE 'oa:todo:%'
   OR LOWER(COALESCE(perms, '')) LIKE 'oa:done:%'
   OR LOWER(COALESCE(perms, '')) LIKE 'inv:purchase-request:%';

-- 标准种子 ID 只用于核对，实际变更仍以组件/权限识别结果为准。
SELECT menu.menu_id,
       menu.menu_name,
       menu.component,
       menu.perms,
       CASE WHEN candidate.menu_id IS NULL THEN 'ID_PRESENT_BUT_METADATA_NOT_MATCHED'
            ELSE 'MATCHED_BY_COMPONENT_OR_PERMISSION' END AS migration_match
FROM sys_menu menu
LEFT JOIN tmp_unified_todo_oa_purchase_menu_20260713 candidate
       ON candidate.menu_id = menu.menu_id
WHERE menu.menu_id IN (
    3002, 3003, 3004, 3005, 3006, 3007, 3008, 3009, 3010,
    3101, 3102, 3103, 3104, 3105, 3106, 3108
)
ORDER BY menu.menu_id;

START TRANSACTION;

INSERT IGNORE INTO sys_unified_todo_config_backup_20260713 (
    release_batch, config_key, existed, config_id, config_name, config_value,
    config_type, create_by, create_time, update_by, update_time, remark
)
SELECT @unified_todo_release_batch,
       desired.config_key,
       CASE WHEN current_config.config_id IS NULL THEN 0 ELSE 1 END,
       current_config.config_id,
       current_config.config_name,
       current_config.config_value,
       current_config.config_type,
       current_config.create_by,
       current_config.create_time,
       current_config.update_by,
       current_config.update_time,
       current_config.remark
FROM (
    SELECT 'feature.oa.purchase.enabled' AS config_key
    UNION ALL
    SELECT 'todo.stock-check.due-soon.hours'
) desired
LEFT JOIN sys_config current_config ON current_config.config_key = desired.config_key;

INSERT IGNORE INTO sys_unified_todo_menu_backup_20260713 (
    release_batch, menu_id, visible, status, update_by, update_time
)
SELECT @unified_todo_release_batch,
       menu.menu_id,
       menu.visible,
       menu.status,
       menu.update_by,
       menu.update_time
FROM sys_menu menu
INNER JOIN tmp_unified_todo_oa_purchase_menu_20260713 candidate
        ON candidate.menu_id = menu.menu_id;

INSERT IGNORE INTO sys_unified_todo_role_menu_backup_20260713 (
    release_batch, role_id, menu_id
)
SELECT @unified_todo_release_batch,
       role_menu.role_id,
       role_menu.menu_id
FROM sys_role_menu role_menu
INNER JOIN tmp_unified_todo_oa_purchase_menu_20260713 candidate
        ON candidate.menu_id = role_menu.menu_id;

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'OA采购功能开关', 'feature.oa.purchase.enabled', 'false', 'Y', 'system', NOW(),
       'UNIFIED_TODO_SCOPE_20260713_V1：OA采购已退役，缺失时同样按关闭处理'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'feature.oa.purchase.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '盘点临期提醒阈值（小时）', 'todo.stock-check.due-soon.hours', '24', 'Y', 'system', NOW(),
       'UNIFIED_TODO_SCOPE_20260713_V1：允许1至168小时，非法值由应用回退24小时'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'todo.stock-check.due-soon.hours'
);

UPDATE sys_menu menu
INNER JOIN tmp_unified_todo_oa_purchase_menu_20260713 candidate
        ON candidate.menu_id = menu.menu_id
SET menu.visible = '1',
    menu.status = '1',
    menu.update_by = 'system',
    menu.update_time = NOW();

DELETE role_menu
FROM sys_role_menu role_menu
INNER JOIN tmp_unified_todo_oa_purchase_menu_20260713 candidate
        ON candidate.menu_id = role_menu.menu_id;

COMMIT;

-- 发布后核对：OA 旧入口应全部停用且不再有角色授权。
SELECT menu.menu_id,
       menu.menu_name,
       menu.component,
       menu.perms,
       menu.visible,
       menu.status,
       COUNT(role_menu.role_id) AS role_grant_count
FROM sys_menu menu
INNER JOIN tmp_unified_todo_oa_purchase_menu_20260713 candidate
        ON candidate.menu_id = menu.menu_id
LEFT JOIN sys_role_menu role_menu ON role_menu.menu_id = menu.menu_id
GROUP BY menu.menu_id, menu.menu_name, menu.component, menu.perms,
         menu.visible, menu.status
ORDER BY menu.menu_id;

-- 发布后核对：两个配置键各应只有一条；OA 开关必须为 false。
SELECT config_key, config_value, config_type, create_by, update_by
FROM sys_config
WHERE config_key IN (
    'feature.oa.purchase.enabled',
    'todo.stock-check.due-soon.hours'
)
ORDER BY config_key, config_id;

-- 活动盘点治理清单：只读输出，不自动设置负责人或截止时间。
SELECT status, COUNT(*) AS row_count
FROM inv_stock_check
GROUP BY status
ORDER BY status;

SELECT check_id,
       check_no,
       shop_dept_id,
       create_by,
       create_time,
       counter_user_id,
       counter_name,
       deadline,
       status
FROM inv_stock_check
WHERE status IN ('draft', 'rejected', 'invalidated')
  AND (counter_user_id IS NULL OR deadline IS NULL)
ORDER BY create_time, check_id;

SELECT COUNT(*) AS stale_draft_count
FROM inv_stock_check
WHERE status = 'draft'
  AND create_time < DATE_SUB(NOW(), INTERVAL 7 DAY);

DROP TEMPORARY TABLE IF EXISTS tmp_unified_todo_oa_purchase_menu_20260713;
