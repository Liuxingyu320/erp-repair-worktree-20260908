#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" --batch --raw <<'SQL'
select 'counts' as section;
select 'current' as src, count(*) all_rows, sum(del_flag='0') active_rows,
       sum(del_flag='0' and dept_type='GROUP') active_groups,
       sum(del_flag='0' and dept_type='COMPANY') active_companies,
       sum(del_flag='0' and dept_type='STORE') active_stores,
       sum(del_flag='0' and dept_type='WAREHOUSE') active_warehouses
from sys_dept
union all
select 'backup_20260628_233900', count(*), sum(del_flag='0'),
       sum(del_flag='0' and dept_type='GROUP'), sum(del_flag='0' and dept_type='COMPANY'),
       sum(del_flag='0' and dept_type='STORE'), sum(del_flag='0' and dept_type='WAREHOUSE')
from backup_employee_import_sys_dept_20260628_233900
union all
select 'backup_store_reseed_193703', count(*), sum(del_flag='0'),
       sum(del_flag='0' and dept_type='GROUP'), sum(del_flag='0' and dept_type='COMPANY'),
       sum(del_flag='0' and dept_type='STORE'), sum(del_flag='0' and dept_type='WAREHOUSE')
from backup_store_reseed_sys_dept_20260628_193703
union all
select 'backup_missing_phone_220217', count(*), sum(del_flag='0'),
       sum(del_flag='0' and dept_type='GROUP'), sum(del_flag='0' and dept_type='COMPANY'),
       sum(del_flag='0' and dept_type='STORE'), sum(del_flag='0' and dept_type='WAREHOUSE')
from backup_missing_phone_reseed_sys_dept_20260628_220217
union all
select 'backup_pre_employee_place_190758', count(*), sum(del_flag='0'),
       sum(del_flag='0' and dept_type='GROUP'), sum(del_flag='0' and dept_type='COMPANY'),
       sum(del_flag='0' and dept_type='STORE'), sum(del_flag='0' and dept_type='WAREHOUSE')
from backup_pre_employee_place_sys_dept_20260628_190758
union all
select 'backup_july_pre_import_181729', count(*), sum(del_flag='0'),
       sum(del_flag='0' and dept_type='GROUP'), sum(del_flag='0' and dept_type='COMPANY'),
       sum(del_flag='0' and dept_type='STORE'), sum(del_flag='0' and dept_type='WAREHOUSE')
from backup_employee_import_sys_dept_20260701_181729;

select 'top_level_current' as section;
select dept_id, parent_id, dept_name, dept_type, order_num, del_flag
from sys_dept
where del_flag='0' and (parent_id=0 or parent_id=100 or dept_id=100)
order by parent_id, order_num, dept_id;

select 'top_level_backup_20260628_233900' as section;
select dept_id, parent_id, dept_name, dept_type, order_num, del_flag
from backup_employee_import_sys_dept_20260628_233900
where del_flag='0' and (parent_id=0 or parent_id=100 or dept_id=100)
order by parent_id, order_num, dept_id;

select 'top_level_backup_july_pre_import_181729' as section;
select dept_id, parent_id, dept_name, dept_type, order_num, del_flag
from backup_employee_import_sys_dept_20260701_181729
where del_flag='0' and (parent_id=0 or parent_id=100 or dept_id=100)
order by parent_id, order_num, dept_id;
SQL
