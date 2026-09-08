#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
show columns from inv_product_category;
show columns from inv_supplier;
show columns from inv_product;
show index from inv_product_category;
show index from inv_supplier;
show index from inv_product;
SQL
