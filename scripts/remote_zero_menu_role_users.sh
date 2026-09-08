#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" --batch --raw <<'SQL'
select 'zero_menu_role_user_counts' as section;
select r.role_id, r.role_name, count(distinct u.user_id) as user_count,
       group_concat(distinct p.post_name order by p.post_sort, p.post_id separator '|') as posts
from sys_role r
left join sys_role_menu rm on rm.role_id=r.role_id
join sys_user_role ur on ur.role_id=r.role_id
join sys_user u on u.user_id=ur.user_id and u.del_flag='0'
left join sys_user_post up on up.user_id=u.user_id
left join sys_post p on p.post_id=up.post_id
where r.del_flag='0'
group by r.role_id, r.role_name
having count(rm.menu_id)=0
order by user_count desc, r.role_id;

select 'zero_menu_common_users' as section;
select u.user_id, u.nick_name, u.user_name, u.phonenumber, p.post_name, r.role_name
from sys_user u
join sys_user_role ur on ur.user_id=u.user_id
join sys_role r on r.role_id=ur.role_id
left join sys_user_post up on up.user_id=u.user_id
left join sys_post p on p.post_id=up.post_id
where u.del_flag='0'
  and r.role_name='普通角色'
order by p.post_sort, u.nick_name;
SQL
