#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
with recursive tree as (
  select dept_id, parent_id, dept_name, dept_type, ancestors, del_flag, create_by, update_by,
         cast(dept_name as char(2000)) as full_path
  from sys_dept
  where parent_id = 0 and del_flag = '0'
  union all
  select d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.ancestors, d.del_flag, d.create_by, d.update_by,
         concat(tree.full_path, ' / ', d.dept_name) as full_path
  from sys_dept d
  join tree on tree.dept_id = d.parent_id
  where d.del_flag = '0'
)
select dept_id, parent_id, dept_name, dept_type, full_path
from tree
where full_path like '%杭州名田%'
  and (full_path like '%名田三区%' or full_path like '%富阳%' or full_path like '%小青山%' or full_path like '%牛头馆%')
order by full_path, dept_id;

select parent.dept_id as parent_id, parent.dept_name as parent_name, count(child.dept_id) as active_children,
       group_concat(concat(child.dept_name, ':', child.dept_type, '#', child.dept_id) order by child.order_num, child.dept_id separator ' | ') as children
from sys_dept parent
left join sys_dept child on child.parent_id = parent.dept_id and child.del_flag = '0'
where parent.del_flag = '0'
  and parent.dept_name in ('名田三区', '富阳区域')
group by parent.dept_id, parent.dept_name
order by parent.dept_id;

select dept_id, parent_id, dept_name, dept_type, del_flag, status, update_by
from sys_dept
where dept_id in (1288, 1274, 1275, 1137, 1138, 1248)
order by dept_id;

select u.nick_name, u.phonenumber, u.dept_id, d.dept_name as main_dept_name,
       group_concat(concat(us.dept_id, ':', sd.dept_name, ':', us.is_default) order by us.dept_id separator ' | ') as shops
from sys_user u
left join sys_dept d on d.dept_id = u.dept_id
left join sys_user_shop us on us.user_id = u.user_id
left join sys_dept sd on sd.dept_id = us.dept_id
where u.del_flag='0'
  and u.phonenumber in ('15267457673','15672636806','15070541861','18390281095','15868163414','18996165827')
group by u.user_id, u.nick_name, u.phonenumber, u.dept_id, d.dept_name
order by u.nick_name;
SQL
