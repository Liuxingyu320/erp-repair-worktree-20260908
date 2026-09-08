#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
IDS="1129,1130,1246,1254,1255,1256,1257,1258,1259,1260,1264"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<SQL
select o.transfer_id, o.order_no, o.from_dept_id, fd.dept_name as from_dept_name,
       o.to_dept_id, td.dept_name as to_dept_name, o.status, o.create_time
from inv_transfer_order o
left join sys_dept fd on fd.dept_id=o.from_dept_id
left join sys_dept td on td.dept_id=o.to_dept_id
where o.to_dept_id in (${IDS}) or o.from_dept_id in (${IDS})
order by o.transfer_id;
SQL
