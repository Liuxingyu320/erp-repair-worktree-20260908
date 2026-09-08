#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
select user_id, user_name, nick_name, phonenumber, dept_id, create_by, update_by
from sys_user
where del_flag='0' and create_by='missing_phone_reseed'
order by nick_name, user_id;
SQL
