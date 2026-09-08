#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql_exec() {
  mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" "$@"
}

cat <<'SQL' | mysql_exec --batch --raw
select 'role_perm_summary' as section;
select r.role_id, r.role_name, r.role_key, r.role_sort,
       count(distinct m.menu_id) as menu_count,
       count(distinct case when m.menu_type='M' then m.menu_id end) as dir_count,
       count(distinct case when m.menu_type='C' then m.menu_id end) as page_count,
       count(distinct case when m.menu_type='F' then m.menu_id end) as button_count,
       group_concat(distinct case when m.menu_type in ('M','C') then concat(m.menu_id, ':', m.menu_name, ':', coalesce(m.perms,'')) end order by m.parent_id, m.order_num separator ' | ') as pages
from sys_role r
left join sys_role_menu rm on rm.role_id = r.role_id
left join sys_menu m on m.menu_id = rm.menu_id and m.status='0'
where r.role_name in ('运营总监','区域运营总监','运营经理','驻店经理','店长','店长助理','茶艺师','实习生','普通角色','仓库管理员')
  and r.del_flag='0'
group by r.role_id, r.role_name, r.role_key, r.role_sort
order by r.role_sort, r.role_id;

select 'store_role_inventory_perms' as section;
select r.role_name,
       group_concat(distinct m.perms order by m.perms separator ' | ') as inv_perms
from sys_role r
join sys_role_menu rm on rm.role_id = r.role_id
join sys_menu m on m.menu_id = rm.menu_id
where r.role_name in ('运营经理','驻店经理','店长','店长助理','茶艺师','实习生')
  and r.del_flag='0'
  and m.status='0'
  and m.perms like 'inv:%'
group by r.role_name, r.role_sort
order by r.role_sort;

select 'multi_post_users' as section;
select u.user_id, u.nick_name, u.user_name, u.create_by,
       group_concat(concat(p.post_id, ':', p.post_name) order by p.post_sort, p.post_id separator ' | ') as posts,
       group_concat(distinct r.role_name order by r.role_sort separator ' | ') as roles
from sys_user u
join sys_user_post up on up.user_id=u.user_id
join sys_post p on p.post_id=up.post_id
left join sys_user_role ur on ur.user_id=u.user_id
left join sys_role r on r.role_id=ur.role_id
where u.del_flag='0'
group by u.user_id, u.nick_name, u.user_name, u.create_by
having count(up.post_id) <> 1
order by u.user_id;
SQL
