#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
show columns from inv_transfer_order;
SQL
