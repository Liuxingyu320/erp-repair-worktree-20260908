#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
select 'sys_dept_active' as item, count(*) as value from sys_dept where del_flag='0'
union all select 'business_dept_active', count(*) from sys_dept where del_flag='0' and dept_type in ('GROUP','COMPANY','STORE','WAREHOUSE')
union all select 'duplicate_sibling_active', count(*) from (
  select parent_id, dept_name, dept_type
  from sys_dept
  where del_flag='0'
  group by parent_id, dept_name, dept_type
  having count(*) > 1
) dup
union all select 'orphan_active_depts', count(*) from sys_dept d left join sys_dept p on p.dept_id=d.parent_id and p.del_flag='0' where d.del_flag='0' and d.parent_id<>0 and p.dept_id is null
union all select 'invalid_active_user_depts', count(*) from sys_user u left join sys_dept d on d.dept_id=u.dept_id and d.del_flag='0' where u.del_flag='0' and d.dept_id is null
union all select 'invalid_active_user_shop_depts', count(*) from sys_user_shop us join sys_user u on u.user_id=us.user_id and u.del_flag='0' left join sys_dept d on d.dept_id=us.dept_id and d.del_flag='0' where d.dept_id is null
union all select 'old_extra_active', count(*) from sys_dept where del_flag='0' and dept_id in (1129,1130,1246,1254,1255,1256,1257,1258,1259,1260,1264)
union all select 'kept_fee_active', count(*) from sys_dept where del_flag='0' and dept_id=1263
union all select 'old_transfer_refs', count(*) from inv_transfer_order where to_dept_id=1259 or from_dept_id=1259
union all select 'new_transfer_refs', count(*) from inv_transfer_order o join sys_dept d on d.dept_id=o.to_dept_id where d.dept_name='长沙芸熙' and d.del_flag='0';

select 'missing_phone_assignments' as section;
select u.nick_name, u.dept_id, d.dept_name as main_dept,
       group_concat(concat(us.dept_id, ':', sd.dept_name, ':', us.is_default) order by us.is_default desc, us.dept_id separator ' | ') as shops
from sys_user u
left join sys_dept d on d.dept_id=u.dept_id
left join sys_user_shop us on us.user_id=u.user_id
left join sys_dept sd on sd.dept_id=us.dept_id
where u.del_flag='0' and u.create_by='missing_phone_reseed'
group by u.user_id, u.nick_name, u.dept_id, d.dept_name
order by u.nick_name;

select 'selected_tree_paths' as section;
with recursive tree as (
  select dept_id, parent_id, dept_name, dept_type, cast(dept_name as char(2000)) as full_path
  from sys_dept
  where parent_id=0 and del_flag='0'
  union all
  select d.dept_id, d.parent_id, d.dept_name, d.dept_type, concat(tree.full_path, ' / ', d.dept_name)
  from sys_dept d
  join tree on tree.dept_id=d.parent_id
  where d.del_flag='0'
)
select dept_id, dept_type, full_path
from tree
where full_path like '%名田三区%'
   or full_path like '%长沙茗记%'
   or full_path like '%外滩瑞吉%'
   or full_path like '%费尔蒙%'
order by full_path;
SQL
