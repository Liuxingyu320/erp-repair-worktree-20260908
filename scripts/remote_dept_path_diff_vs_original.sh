#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" --batch --raw <<'SQL'
select 'diff_vs_backup_employee_import_20260701_181729' as section;

WITH RECURSIVE current_tree AS (
    SELECT dept_id, parent_id, dept_name, dept_type, del_flag, CAST(dept_name AS CHAR(1000)) full_path
    FROM sys_dept
    WHERE dept_id=100 AND del_flag='0'
    UNION ALL
    SELECT d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.del_flag, CONCAT(t.full_path, ' / ', d.dept_name)
    FROM sys_dept d
    JOIN current_tree t ON t.dept_id=d.parent_id
    WHERE d.del_flag='0'
),
backup_tree AS (
    SELECT dept_id, parent_id, dept_name, dept_type, del_flag, CAST(dept_name AS CHAR(1000)) full_path
    FROM backup_employee_import_sys_dept_20260701_181729
    WHERE dept_id=100 AND del_flag='0'
    UNION ALL
    SELECT d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.del_flag, CONCAT(t.full_path, ' / ', d.dept_name)
    FROM backup_employee_import_sys_dept_20260701_181729 d
    JOIN backup_tree t ON t.dept_id=d.parent_id
    WHERE d.del_flag='0'
)
select 'current_only' as diff_type, c.dept_id, c.dept_type, c.full_path
from current_tree c
left join backup_tree b on b.full_path=c.full_path and b.dept_type=c.dept_type
where b.dept_id is null
order by c.full_path;

WITH RECURSIVE current_tree AS (
    SELECT dept_id, parent_id, dept_name, dept_type, del_flag, CAST(dept_name AS CHAR(1000)) full_path
    FROM sys_dept
    WHERE dept_id=100 AND del_flag='0'
    UNION ALL
    SELECT d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.del_flag, CONCAT(t.full_path, ' / ', d.dept_name)
    FROM sys_dept d
    JOIN current_tree t ON t.dept_id=d.parent_id
    WHERE d.del_flag='0'
),
backup_tree AS (
    SELECT dept_id, parent_id, dept_name, dept_type, del_flag, CAST(dept_name AS CHAR(1000)) full_path
    FROM backup_employee_import_sys_dept_20260701_181729
    WHERE dept_id=100 AND del_flag='0'
    UNION ALL
    SELECT d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.del_flag, CONCAT(t.full_path, ' / ', d.dept_name)
    FROM backup_employee_import_sys_dept_20260701_181729 d
    JOIN backup_tree t ON t.dept_id=d.parent_id
    WHERE d.del_flag='0'
)
select 'backup_only' as diff_type, b.dept_id, b.dept_type, b.full_path
from backup_tree b
left join current_tree c on c.full_path=b.full_path and c.dept_type=b.dept_type
where c.dept_id is null
order by b.full_path;
SQL
