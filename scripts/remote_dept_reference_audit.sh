#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
IDS="1127,1248,1288,1137,1138,1274,1275"

mysql_base=(mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}")

"${mysql_base[@]}" <<SQL
select d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.ancestors, d.create_by, d.update_by,
       (select count(*) from sys_user u where u.del_flag='0' and u.dept_id=d.dept_id) as active_users,
       (select count(*) from sys_user_shop us join sys_user u on u.user_id=us.user_id and u.del_flag='0' where us.dept_id=d.dept_id) as active_user_shop_links,
       (select count(*) from sys_dept c where c.del_flag='0' and c.parent_id=d.dept_id) as active_children
from sys_dept d
where d.dept_id in (${IDS})
order by d.dept_id;

select u.user_id, u.user_name, u.nick_name, u.dept_id, d.dept_name as main_dept_name,
       group_concat(concat(us.dept_id, ':', sd.dept_name, ':', us.is_default) order by us.dept_id separator ' | ') as shops
from sys_user u
left join sys_dept d on d.dept_id = u.dept_id
left join sys_user_shop us on us.user_id = u.user_id
left join sys_dept sd on sd.dept_id = us.dept_id
where u.del_flag='0'
  and (u.phonenumber in ('15267457673','15672636806','15070541861','18390281095','15868163414','18996165827')
       or u.dept_id in (${IDS})
       or us.dept_id in (${IDS}))
group by u.user_id, u.user_name, u.nick_name, u.dept_id, d.dept_name
order by u.nick_name;
SQL

echo "-- business table references --"
"${mysql_base[@]}" --skip-column-names -e "
select table_name, column_name
from information_schema.columns
where table_schema='${DB}'
  and table_name not in ('sys_dept','sys_user','sys_user_shop')
  and column_name in ('dept_id','shop_dept_id','target_dept_id','source_dept_id','from_dept_id','to_dept_id')
order by table_name, column_name;
" | while IFS=$'\t' read -r table_name column_name; do
  count="$("${mysql_base[@]}" --skip-column-names -e "select count(*) from \`${table_name}\` where \`${column_name}\` in (${IDS})" 2>/dev/null || echo 0)"
  if [ "${count}" != "0" ]; then
    echo "${table_name}.${column_name}=${count}"
  fi
done
