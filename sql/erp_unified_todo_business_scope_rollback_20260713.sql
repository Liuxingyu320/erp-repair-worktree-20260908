-- 统一待办业务口径收口回滚（MySQL 5.7 / 8.0，可重复执行）。
-- 仅恢复本批次停用的 OA 菜单状态与原角色授权，并安全清理本批次新建的配置。
-- 不恢复 OA 采购写开关为 true；应用层缺失配置时仍保持 fail-closed。
-- 不删除业务历史、Flowable 历史、盘点责任人、截止时间或审批记录。

SET @unified_todo_release_batch :=
    _utf8mb4'UNIFIED_TODO_SCOPE_20260713_V1' COLLATE utf8mb4_general_ci;

START TRANSACTION;

UPDATE sys_menu menu
INNER JOIN sys_unified_todo_menu_backup_20260713 backup
        ON backup.release_batch = @unified_todo_release_batch
       AND backup.menu_id = menu.menu_id
SET menu.visible = backup.visible,
    menu.status = backup.status,
    menu.update_by = backup.update_by,
    menu.update_time = backup.update_time;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT backup.role_id, backup.menu_id
FROM sys_unified_todo_role_menu_backup_20260713 backup
INNER JOIN sys_menu menu ON menu.menu_id = backup.menu_id
WHERE backup.release_batch = @unified_todo_release_batch;

-- 发布前不存在的配置，只有仍保持本批次默认值和标记时才删除；
-- 若管理员在发布后修改过，保留当前值并在末尾冲突查询中提示人工处理。
DELETE config_row
FROM sys_config config_row
INNER JOIN sys_unified_todo_config_backup_20260713 backup
        ON backup.release_batch = @unified_todo_release_batch
       AND backup.config_key COLLATE utf8mb4_general_ci =
           config_row.config_key COLLATE utf8mb4_general_ci
       AND backup.existed = 0
WHERE (
        config_row.config_key = 'feature.oa.purchase.enabled'
        AND config_row.config_name = 'OA采购功能开关'
        AND config_row.config_value = 'false'
        AND config_row.config_type = 'Y'
        AND config_row.create_by = 'system'
        AND config_row.remark =
            'UNIFIED_TODO_SCOPE_20260713_V1：OA采购已退役，缺失时同样按关闭处理'
      )
   OR (
        config_row.config_key = 'todo.stock-check.due-soon.hours'
        AND config_row.config_name = '盘点临期提醒阈值（小时）'
        AND config_row.config_value = '24'
        AND config_row.config_type = 'Y'
        AND config_row.create_by = 'system'
        AND config_row.remark =
            'UNIFIED_TODO_SCOPE_20260713_V1：允许1至168小时，非法值由应用回退24小时'
      );

COMMIT;

-- 回滚核对：菜单状态应等于快照，原角色映射应恢复。
SELECT backup.menu_id,
       menu.menu_name,
       backup.visible AS expected_visible,
       menu.visible AS actual_visible,
       backup.status AS expected_status,
       menu.status AS actual_status
FROM sys_unified_todo_menu_backup_20260713 backup
INNER JOIN sys_menu menu ON menu.menu_id = backup.menu_id
WHERE backup.release_batch = @unified_todo_release_batch
ORDER BY backup.menu_id;

SELECT COUNT(*) AS missing_restored_role_grant_count
FROM sys_unified_todo_role_menu_backup_20260713 backup
LEFT JOIN sys_role_menu current_grant
       ON current_grant.role_id = backup.role_id
      AND current_grant.menu_id = backup.menu_id
WHERE backup.release_batch = @unified_todo_release_batch
  AND current_grant.role_id IS NULL;

-- 非零表示发布期间配置被修改，回滚脚本已保留现值，需要管理员决定是否调整。
SELECT config_row.config_key,
       config_row.config_value,
       config_row.config_type,
       config_row.create_by,
       config_row.update_by,
       config_row.update_time,
       '配置已偏离本批次默认值，回滚未自动删除' AS rollback_action
FROM sys_config config_row
INNER JOIN sys_unified_todo_config_backup_20260713 backup
        ON backup.release_batch = @unified_todo_release_batch
       AND backup.config_key COLLATE utf8mb4_general_ci =
           config_row.config_key COLLATE utf8mb4_general_ci
       AND backup.existed = 0
WHERE NOT (
        config_row.config_key = 'feature.oa.purchase.enabled'
        AND config_row.config_name = 'OA采购功能开关'
        AND config_row.config_value = 'false'
        AND config_row.config_type = 'Y'
        AND config_row.create_by = 'system'
        AND config_row.remark =
            'UNIFIED_TODO_SCOPE_20260713_V1：OA采购已退役，缺失时同样按关闭处理'
      )
  AND NOT (
        config_row.config_key = 'todo.stock-check.due-soon.hours'
        AND config_row.config_name = '盘点临期提醒阈值（小时）'
        AND config_row.config_value = '24'
        AND config_row.config_type = 'Y'
        AND config_row.create_by = 'system'
        AND config_row.remark =
            'UNIFIED_TODO_SCOPE_20260713_V1：允许1至168小时，非法值由应用回退24小时'
      )
ORDER BY config_row.config_key;
