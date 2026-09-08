#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" --batch --raw <<'SQL'
select 'target_users' as section;
select u.user_id, u.user_name, u.nick_name, u.phonenumber, u.create_by, u.update_by, u.dept_id, d.dept_name,
       coalesce(r.role_name, '<none>') as role_name,
       coalesce(p.post_name, '<none>') as post_name,
       count(us.dept_id) as shop_count,
       sum(us.is_default='Y') as default_count
from sys_user u
left join sys_dept d on d.dept_id=u.dept_id
left join sys_user_role ur on ur.user_id=u.user_id
left join sys_role r on r.role_id=ur.role_id
left join sys_user_post up on up.user_id=u.user_id
left join sys_post p on p.post_id=up.post_id
left join sys_user_shop us on us.user_id=u.user_id
where u.del_flag='0'
  and (u.nick_name in ('金英','杨沛','叶韦芳','李曼','叶素红') or u.phonenumber in ('18210107086','19193889337'))
group by u.user_id, u.user_name, u.nick_name, u.phonenumber, u.create_by, u.update_by, u.dept_id, d.dept_name, r.role_name, p.post_name
order by u.nick_name, u.user_id;

select 'jinying_default_shop' as section;
select u.user_id, u.nick_name, us.dept_id, d.dept_name, us.is_default
from sys_user u
join sys_user_shop us on us.user_id=u.user_id
join sys_dept d on d.dept_id=us.dept_id
where u.del_flag='0' and u.nick_name='金英' and us.is_default='Y';
SQL
