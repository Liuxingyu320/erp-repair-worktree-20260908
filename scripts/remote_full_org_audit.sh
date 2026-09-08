#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql_base=(mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}")

"${mysql_base[@]}" <<'SQL'
select 'sys_dept_all' as item, count(*) as value from sys_dept
union all select 'sys_dept_active', count(*) from sys_dept where del_flag='0'
union all select 'business_dept_active', count(*) from sys_dept where del_flag='0' and dept_type in ('GROUP','COMPANY','STORE','WAREHOUSE')
union all select 'store_active', count(*) from sys_dept where del_flag='0' and dept_type='STORE'
union all select 'warehouse_active', count(*) from sys_dept where del_flag='0' and dept_type='WAREHOUSE'
union all select 'company_active', count(*) from sys_dept where del_flag='0' and dept_type='COMPANY'
union all select 'group_active', count(*) from sys_dept where del_flag='0' and dept_type='GROUP'
union all select 'employee_import_active', count(*) from sys_dept where del_flag='0' and (create_by='employee_xls_import' or update_by='employee_xls_import')
union all select 'active_users', count(*) from sys_user where del_flag='0'
union all select 'active_user_shop_links', count(*) from sys_user_shop us join sys_user u on u.user_id=us.user_id where u.del_flag='0';

select 'duplicate_sibling_active' as section;
select parent_id, dept_name, dept_type, count(*) as c,
       group_concat(dept_id order by dept_id separator ',') as ids
from sys_dept
where del_flag='0'
group by parent_id, dept_name, dept_type
having count(*) > 1
order by c desc, parent_id, dept_name, dept_type
limit 100;

select 'active_business_tree' as section;
with recursive tree as (
  select dept_id, parent_id, dept_name, dept_type, ancestors, order_num,
         cast(dept_name as char(2000)) as full_path,
         0 as depth
  from sys_dept
  where parent_id = 0 and del_flag='0'
  union all
  select d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.ancestors, d.order_num,
         concat(tree.full_path, ' / ', d.dept_name) as full_path,
         tree.depth + 1 as depth
  from sys_dept d
  join tree on tree.dept_id = d.parent_id
  where d.del_flag='0'
)
select dept_id, parent_id, dept_name, dept_type, depth, full_path
from tree
where dept_type in ('GROUP','COMPANY','STORE','WAREHOUSE')
order by full_path, dept_id;

select 'orphan_active_depts' as section;
select d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.ancestors
from sys_dept d
left join sys_dept p on p.dept_id=d.parent_id and p.del_flag='0'
where d.del_flag='0' and d.parent_id <> 0 and p.dept_id is null
order by d.dept_id;

select 'invalid_active_user_depts' as section;
select u.user_id, u.user_name, u.nick_name, u.dept_id
from sys_user u
left join sys_dept d on d.dept_id=u.dept_id and d.del_flag='0'
where u.del_flag='0' and d.dept_id is null
order by u.user_id;

select 'invalid_active_user_shop_depts' as section;
select us.user_id, u.nick_name, us.dept_id
from sys_user_shop us
join sys_user u on u.user_id=us.user_id and u.del_flag='0'
left join sys_dept d on d.dept_id=us.dept_id and d.del_flag='0'
where d.dept_id is null
order by us.user_id, us.dept_id;
SQL
