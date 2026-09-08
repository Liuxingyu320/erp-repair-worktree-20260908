#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

mysql_exec() {
  mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" "$@"
}

echo "database=${DB}"
echo "time=$(date '+%F %T')"
echo

cat <<'SQL' | mysql_exec --batch --raw
select 'sys_dept' as table_name, count(*) as rows_count from sys_dept
union all select 'sys_post', count(*) from sys_post
union all select 'sys_role', count(*) from sys_role
union all select 'sys_user', count(*) from sys_user
union all select 'sys_user_post', count(*) from sys_user_post
union all select 'sys_user_role', count(*) from sys_user_role
union all select 'sys_user_shop', count(*) from sys_user_shop
union all select 'inv_product_category', count(*) from inv_product_category
union all select 'inv_supplier', count(*) from inv_supplier
union all select 'inv_product', count(*) from inv_product
union all select 'inv_stock', count(*) from inv_stock
union all select 'inv_stock_log', count(*) from inv_stock_log
union all select 'inv_purchase_detail', count(*) from inv_purchase_detail
union all select 'inv_sales_detail', count(*) from inv_sales_detail
union all select 'inv_inbound_record', count(*) from inv_inbound_record
union all select 'inv_outbound_record', count(*) from inv_outbound_record;
SQL

echo
echo "recent_import_marks"
cat <<'SQL' | mysql_exec --batch --raw
select 'employee_import_users' as mark, count(*) as rows_count from sys_user where create_by='employee_xls_import' or update_by='employee_xls_import'
union all select 'employee_import_depts', count(*) from sys_dept where create_by='employee_xls_import' or update_by='employee_xls_import'
union all select 'xlsx_reimport_products', count(*) from inv_product where create_by='xlsx_reimport' or update_by='xlsx_reimport'
union all select 'xlsx_reimport_categories', count(*) from inv_product_category where create_by='xlsx_reimport' or update_by='xlsx_reimport'
union all select 'xlsx_reimport_suppliers', count(*) from inv_supplier where create_by='xlsx_reimport' or update_by='xlsx_reimport';
SQL

echo
echo "inventory_detail_rows_by_product_reference"
cat <<'SQL' | mysql_exec --batch --raw
select 'inv_stock' as table_name, count(*) as rows_count, count(distinct product_id) as product_refs from inv_stock
union all select 'inv_stock_log', count(*), count(distinct product_id) from inv_stock_log
union all select 'inv_purchase_detail', count(*), count(distinct product_id) from inv_purchase_detail
union all select 'inv_sales_detail', count(*), count(distinct product_id) from inv_sales_detail
union all select 'inv_inbound_record', count(*), count(distinct product_id) from inv_inbound_record
union all select 'inv_outbound_record', count(*), count(distinct product_id) from inv_outbound_record;
SQL

echo
echo "python_xlrd"
python3 - <<'PY'
try:
    import xlrd
except Exception as exc:
    print("xlrd=missing " + repr(exc))
else:
    print("xlrd=ok " + getattr(xlrd, "__version__", "unknown"))
PY
