#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql_exec() {
  mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" "$@"
}

cat <<'SQL' | mysql_exec --batch --raw
select 'standard_roles' as section;
select r.role_id, r.role_name, r.role_key, r.role_sort, r.data_scope, r.status, r.del_flag,
       count(rm.menu_id) as menu_count
from sys_role r
left join sys_role_menu rm on rm.role_id = r.role_id
where r.role_name in ('运营总监','区域运营总监','运营经理','驻店经理','店长','店长助理','茶艺师','实习生','普通角色','仓库管理员','超级管理员')
group by r.role_id, r.role_name, r.role_key, r.role_sort, r.data_scope, r.status, r.del_flag
order by r.role_sort, r.role_id;

select 'standard_posts' as section;
select post_id, post_name, post_code, post_sort, status
from sys_post
where post_name in ('AM驻店店长','AM驻店经理','GM','产品专员','产品经理','初级运营经理','区域运营总监','区域运营经理','实习生','店长','店长助理','总经理','物料采购','茶艺师','行政助理','行政经理','运营助理','运营总监','运营经理','预备店长','驻店经理')
order by post_sort, post_id;

select 'binding_counts' as section;
select 'active_import_users' item, count(*) value
from sys_user
where del_flag='0' and create_by in ('employee_xls_import','missing_phone_reseed')
union all
select 'users_without_role', count(*)
from sys_user u
where u.del_flag='0' and u.create_by in ('employee_xls_import','missing_phone_reseed')
  and not exists (select 1 from sys_user_role ur where ur.user_id=u.user_id)
union all
select 'users_without_post', count(*)
from sys_user u
where u.del_flag='0' and u.create_by in ('employee_xls_import','missing_phone_reseed')
  and not exists (select 1 from sys_user_post up where up.user_id=u.user_id)
union all
select 'users_with_multi_roles', count(*)
from (
  select u.user_id
  from sys_user u join sys_user_role ur on ur.user_id=u.user_id
  where u.del_flag='0' and u.create_by in ('employee_xls_import','missing_phone_reseed')
  group by u.user_id
  having count(*) <> 1
) x
union all
select 'users_with_multi_posts', count(*)
from (
  select u.user_id
  from sys_user u join sys_user_post up on up.user_id=u.user_id
  where u.del_flag='0' and u.create_by in ('employee_xls_import','missing_phone_reseed')
  group by u.user_id
  having count(*) <> 1
) x
union all
select 'invalid_user_role_refs', count(*)
from sys_user_role ur
left join sys_user u on u.user_id=ur.user_id and u.del_flag='0'
left join sys_role r on r.role_id=ur.role_id and r.del_flag='0'
where u.user_id is null or r.role_id is null
union all
select 'invalid_user_post_refs', count(*)
from sys_user_post up
left join sys_user u on u.user_id=up.user_id and u.del_flag='0'
left join sys_post p on p.post_id=up.post_id
where u.user_id is null or p.post_id is null;

select 'import_user_role_post_summary' as section;
select coalesce(r.role_name, '<none>') as role_name, coalesce(p.post_name, '<none>') as post_name, count(*) as user_count
from sys_user u
left join sys_user_role ur on ur.user_id=u.user_id
left join sys_role r on r.role_id=ur.role_id
left join sys_user_post up on up.user_id=u.user_id
left join sys_post p on p.post_id=up.post_id
where u.del_flag='0' and u.create_by in ('employee_xls_import','missing_phone_reseed')
group by coalesce(r.role_name, '<none>'), coalesce(p.post_name, '<none>')
order by role_name, post_name;

select 'missing_phone_current' as section;
select u.user_id, u.nick_name, coalesce(p.post_name,'<none>') as post_name, coalesce(r.role_name,'<none>') as role_name
from sys_user u
left join sys_user_post up on up.user_id=u.user_id
left join sys_post p on p.post_id=up.post_id
left join sys_user_role ur on ur.user_id=u.user_id
left join sys_role r on r.role_id=ur.role_id
where u.del_flag='0' and u.create_by='missing_phone_reseed'
order by u.nick_name, u.user_id;
SQL
