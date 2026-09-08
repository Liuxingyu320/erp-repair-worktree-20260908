#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
with recursive tree as (
  select dept_id, parent_id, dept_name, dept_type, ancestors, create_by, update_by,
         cast(dept_name as char(2000)) as full_path
  from sys_dept
  where parent_id = 0 and del_flag = '0'
  union all
  select d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.ancestors, d.create_by, d.update_by,
         concat(tree.full_path, ' / ', d.dept_name) as full_path
  from sys_dept d
  join tree on tree.dept_id = d.parent_id
  where d.del_flag = '0'
)
select dept_id, parent_id, dept_name, dept_type, ancestors, create_by, update_by, full_path
from tree
where full_path like '%杭州名田%'
  and (full_path like '%名田三区%' or full_path like '%富阳%' or full_path like '%小青山%' or full_path like '%牛头馆%')
order by full_path, dept_id;

select parent_id, parent.dept_name as parent_name, count(*) as active_children,
       group_concat(concat(child.dept_name, ':', child.dept_type, '#', child.dept_id) order by child.order_num, child.dept_id separator ' | ') as children
from sys_dept parent
join sys_dept child on child.parent_id = parent.dept_id and child.del_flag = '0'
where parent.del_flag = '0'
  and parent.dept_name in ('名田三区', '富阳区域')
group by parent_id, parent.dept_name, parent.dept_id
order by parent.dept_id;

select dept_name, parent_id, dept_type, count(*) as c,
       group_concat(dept_id order by dept_id separator ',') as ids
from sys_dept
where del_flag = '0'
  and dept_name in ('名田三区', '富阳区域', '小青山', '牛头馆')
group by dept_name, parent_id, dept_type
having count(*) > 1
order by dept_name, parent_id, dept_type;
SQL
