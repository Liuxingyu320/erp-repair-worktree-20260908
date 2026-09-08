#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
select 'inv_product_category_upserted' as mark, count(*) as rows_count from inv_product_category where update_by='xlsx_reimport_upsert'
union all select 'inv_supplier_upserted', count(*) from inv_supplier where update_by='xlsx_reimport_upsert'
union all select 'inv_product_upserted', count(*) from inv_product where update_by='xlsx_reimport_upsert';
SQL
