#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" --batch --raw <<'SQL'
SET NAMES utf8mb4;

SET @target_role_id := (
  SELECT role_id
  FROM sys_role
  WHERE del_flag = '0'
    AND (role_key = 'qyyyzzj' OR role_name = '区域运营总监')
  ORDER BY role_id
  LIMIT 1
);

SET @source_role_id := (
  SELECT role_id
  FROM sys_role
  WHERE del_flag = '0'
    AND (role_key = 'yyjl' OR role_name = '运营经理')
  ORDER BY role_id
  LIMIT 1
);

CREATE TABLE IF NOT EXISTS backup_add_qyyyzzj_permissions_sys_role_menu_20260701 LIKE sys_role_menu;

INSERT IGNORE INTO backup_add_qyyyzzj_permissions_sys_role_menu_20260701(role_id, menu_id)
SELECT role_id, menu_id
FROM sys_role_menu
WHERE role_id = @target_role_id;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @target_role_id, menu_id
FROM sys_role_menu
WHERE role_id = @source_role_id;

UPDATE sys_role
SET status = '0',
    role_sort = 2,
    data_scope = '4',
    update_by = 'codex_permission_fix',
    update_time = NOW()
WHERE role_id = @target_role_id;

SELECT 'target_role_id' AS item, @target_role_id AS value
UNION ALL SELECT 'source_role_id', @source_role_id
UNION ALL SELECT 'target_menu_count', COUNT(*) FROM sys_role_menu WHERE role_id = @target_role_id
UNION ALL SELECT 'source_menu_count', COUNT(*) FROM sys_role_menu WHERE role_id = @source_role_id;
SQL
