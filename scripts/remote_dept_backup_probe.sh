#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" --batch --raw <<'SQL'
select 'backup_tables' as section;
select table_name, table_rows, create_time
from information_schema.tables
where table_schema = database()
  and table_name like 'backup%sys_dept%'
order by create_time desc, table_name;

select 'current_counts' as section;
select count(*) as all_rows,
       sum(del_flag='0') as active_rows,
       sum(del_flag='0' and dept_type='GROUP') as active_groups,
       sum(del_flag='0' and dept_type='COMPANY') as active_companies,
       sum(del_flag='0' and dept_type='STORE') as active_stores,
       sum(del_flag='0' and dept_type='WAREHOUSE') as active_warehouses
from sys_dept;

select 'current_top_tree' as section;
select dept_id, parent_id, dept_name, dept_type, status, del_flag, create_by, update_by
from sys_dept
where del_flag='0' and (parent_id=0 or parent_id=100 or dept_id in (100,101,200))
order by parent_id, order_num, dept_id;
SQL
