#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
IDS="1129,1130,1246,1254,1255,1256,1257,1258,1259,1260,1264"

mysql_base=(mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}")

echo "-- non-backup business table references --"
"${mysql_base[@]}" --skip-column-names -e "
select table_name, column_name
from information_schema.columns
where table_schema='${DB}'
  and table_name not like 'backup\\_%'
  and table_name not in ('sys_dept','sys_user','sys_user_shop')
  and column_name in ('dept_id','shop_dept_id','target_dept_id','source_dept_id','from_dept_id','to_dept_id')
order by table_name, column_name;
" | while IFS=$'\t' read -r table_name column_name; do
  count="$("${mysql_base[@]}" --skip-column-names -e "select count(*) from \`${table_name}\` where \`${column_name}\` in (${IDS})" 2>/dev/null || echo 0)"
  if [ "${count}" != "0" ]; then
    echo "${table_name}.${column_name}=${count}"
  fi
done
