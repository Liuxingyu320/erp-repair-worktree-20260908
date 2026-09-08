#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
IDS="1129,1130,1246,1252,1254,1255,1256,1257,1258,1259,1260,1263,1264"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<SQL
select d.dept_id, d.dept_name, d.dept_type, d.parent_id,
       p.dept_name as parent_name,
       d.ancestors, d.create_by, d.update_by
from sys_dept d
left join sys_dept p on p.dept_id=d.parent_id
where d.dept_id in (${IDS})
order by d.dept_id;

select u.user_id, u.user_name, u.nick_name, u.phonenumber, u.dept_id, md.dept_name as main_dept,
       u.create_by, u.update_by,
       group_concat(concat(us.dept_id, ':', sd.dept_name, ':', us.is_default) order by us.dept_id separator ' | ') as shops
from sys_user u
left join sys_dept md on md.dept_id=u.dept_id
left join sys_user_shop us on us.user_id=u.user_id
left join sys_dept sd on sd.dept_id=us.dept_id
where u.del_flag='0'
  and (u.dept_id in (${IDS}) or us.dept_id in (${IDS}))
group by u.user_id, u.user_name, u.nick_name, u.phonenumber, u.dept_id, md.dept_name, u.create_by, u.update_by
order by u.nick_name, u.user_id;
SQL
