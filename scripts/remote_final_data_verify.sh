#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL'
select 'sys_dept' as item, count(*) as value from sys_dept
union all select 'sys_user', count(*) from sys_user
union all select 'sys_user_shop', count(*) from sys_user_shop
union all select 'employee_import_users', count(*) from sys_user where create_by='employee_xls_import' or update_by='employee_xls_import'
union all select 'employee_import_depts', count(*) from sys_dept where create_by='employee_xls_import' or update_by='employee_xls_import'
union all select 'inv_product_category', count(*) from inv_product_category
union all select 'inv_supplier', count(*) from inv_supplier
union all select 'inv_product', count(*) from inv_product
union all select 'inv_stock', count(*) from inv_stock
union all select 'inv_stock_log', count(*) from inv_stock_log
union all select 'category_upserted', count(*) from inv_product_category where update_by='xlsx_reimport_upsert'
union all select 'supplier_upserted', count(*) from inv_supplier where update_by='xlsx_reimport_upsert'
union all select 'product_upserted', count(*) from inv_product where update_by='xlsx_reimport_upsert';
SQL

echo "-- backups --"
ls -lh /opt/erp-new-data-backups/employee-20260701181726/sys_employee_tables_before.sql.gz
ls -lh /opt/erp-new-data-backups/sql-20260701182034/tables_before.sql.gz
