#!/usr/bin/env bash
set -euo pipefail

DB="${DB:-BossERP_NEW}"
MYSQL_ROOT_PASS_FILE="${MYSQL_ROOT_PASS_FILE:-/root/.erp-mysql-root-pass}"
MYSQL_ROOT_PASS="${MYSQL_ROOT_PASS:-$(cat "${MYSQL_ROOT_PASS_FILE}")}"
ROW_LIMIT="${ROW_LIMIT:-200}"
MATCH_REGEX="测试|test|demo|mock|sample|示例|演示|临时|云岫|青炉|so-mobile|po-mobile|mobile-ready|codex|qa_|ux_"
FRONTEND_REMOVED_MARKERS="SO-MOBILE PO-MOBILE MOBILE-READY"

mysql_base=(mysql --protocol=tcp --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}")

mysql_exec() {
  "${mysql_base[@]}" "$@"
}

echo "database=${DB}"
echo "time=$(date '+%F %T')"
echo "match_regex=${MATCH_REGEX}"
echo "frontend_removed_markers=${FRONTEND_REMOVED_MARKERS}"
echo

echo "candidate_summary"
cat <<SQL | mysql_exec
set @test_data_regex = '${MATCH_REGEX}';
select 'candidate_sys_dept' as bucket, count(*) as rows_count
from sys_dept
where lower(concat_ws('|', coalesce(dept_name, ''), coalesce(leader, ''), coalesce(phone, ''), coalesce(email, ''), coalesce(dept_type, ''), coalesce(create_by, ''), coalesce(update_by, ''))) regexp @test_data_regex
union all
select 'candidate_sys_user', count(*)
from sys_user
where lower(concat_ws('|', coalesce(user_name, ''), coalesce(nick_name, ''), coalesce(email, ''), coalesce(phonenumber, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp @test_data_regex
union all
select 'candidate_sys_user_shop', count(*)
from sys_user_shop us
left join sys_user u on u.user_id = us.user_id
left join sys_dept d on d.dept_id = us.dept_id
where lower(concat_ws('|', coalesce(us.create_by, ''), coalesce(u.user_name, ''), coalesce(u.nick_name, ''), coalesce(d.dept_name, ''))) regexp @test_data_regex;
SQL

echo
echo "candidate_sys_dept"
cat <<SQL | mysql_exec
set @test_data_regex = '${MATCH_REGEX}';
select dept_id, parent_id, dept_name, dept_type, status, del_flag, create_by, create_time, update_by, update_time
from sys_dept
where lower(concat_ws('|', coalesce(dept_name, ''), coalesce(leader, ''), coalesce(phone, ''), coalesce(email, ''), coalesce(dept_type, ''), coalesce(create_by, ''), coalesce(update_by, ''))) regexp @test_data_regex
order by del_flag, status, dept_id
limit ${ROW_LIMIT};
SQL

echo
echo "candidate_sys_user"
cat <<SQL | mysql_exec
set @test_data_regex = '${MATCH_REGEX}';
select u.user_id, u.user_name, u.nick_name, u.status, u.del_flag, u.dept_id,
       d.dept_name as main_dept_name, u.create_by, u.create_time, u.update_by, u.update_time, u.remark,
       group_concat(concat(us.dept_id, ':', coalesce(sd.dept_name, ''), ':', us.is_default) order by us.dept_id separator ' | ') as shops
from sys_user u
left join sys_dept d on d.dept_id = u.dept_id
left join sys_user_shop us on us.user_id = u.user_id
left join sys_dept sd on sd.dept_id = us.dept_id
where lower(concat_ws('|', coalesce(u.user_name, ''), coalesce(u.nick_name, ''), coalesce(u.email, ''), coalesce(u.phonenumber, ''), coalesce(u.create_by, ''), coalesce(u.update_by, ''), coalesce(u.remark, ''))) regexp @test_data_regex
group by u.user_id, u.user_name, u.nick_name, u.status, u.del_flag, u.dept_id, d.dept_name, u.create_by, u.create_time, u.update_by, u.update_time, u.remark
order by u.del_flag, u.status, u.user_id
limit ${ROW_LIMIT};
SQL

echo
echo "candidate_sys_user_shop"
cat <<SQL | mysql_exec
set @test_data_regex = '${MATCH_REGEX}';
select us.user_id, u.user_name, u.nick_name, us.dept_id, d.dept_name, us.is_default, us.create_by, us.create_time
from sys_user_shop us
left join sys_user u on u.user_id = us.user_id
left join sys_dept d on d.dept_id = us.dept_id
where lower(concat_ws('|', coalesce(us.create_by, ''), coalesce(u.user_name, ''), coalesce(u.nick_name, ''), coalesce(d.dept_name, ''))) regexp @test_data_regex
order by us.create_by, us.user_id, us.dept_id
limit ${ROW_LIMIT};
SQL

echo
echo "candidate_inventory_rows"
cat <<SQL | mysql_exec
set @test_data_regex = '${MATCH_REGEX}';
select *
from (
  select 'inv_product' as table_name, product_id as record_id, product_code as record_code, product_name as record_name, shop_dept_id,
         concat_ws('|', product_name, product_code, sku, supplier_name, supplier_phone, internal_tea_name, product_description, barcode, create_by, update_by, remark) as matched_text
  from inv_product
  where lower(concat_ws('|', coalesce(product_name, ''), coalesce(product_code, ''), coalesce(sku, ''), coalesce(supplier_name, ''), coalesce(supplier_phone, ''), coalesce(internal_tea_name, ''), coalesce(product_description, ''), coalesce(barcode, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp @test_data_regex
  union all
  select 'inv_product_category', category_id, category_code, category_name, shop_dept_id,
         concat_ws('|', category_name, category_code, create_by, update_by, remark)
  from inv_product_category
  where lower(concat_ws('|', coalesce(category_name, ''), coalesce(category_code, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp @test_data_regex
  union all
  select 'inv_supplier', supplier_id, supplier_code, supplier_name, shop_dept_id,
         concat_ws('|', supplier_name, supplier_code, contact_person, contact_phone, contact_email, address, create_by, update_by, remark)
  from inv_supplier
  where lower(concat_ws('|', coalesce(supplier_name, ''), coalesce(supplier_code, ''), coalesce(contact_person, ''), coalesce(contact_phone, ''), coalesce(contact_email, ''), coalesce(address, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp @test_data_regex
  union all
  select 'inv_purchase_order', order_id, order_no, order_title, shop_dept_id,
         concat_ws('|', order_no, order_title, supplier_name, applicant_name, create_by, update_by, remark)
  from inv_purchase_order
  where lower(concat_ws('|', coalesce(order_no, ''), coalesce(order_title, ''), coalesce(supplier_name, ''), coalesce(applicant_name, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp @test_data_regex
  union all
  select 'inv_sales_order', order_id, order_no, order_title, shop_dept_id,
         concat_ws('|', order_no, order_title, customer_name, applicant_name, create_by, update_by, remark)
  from inv_sales_order
  where lower(concat_ws('|', coalesce(order_no, ''), coalesce(order_title, ''), coalesce(customer_name, ''), coalesce(applicant_name, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp @test_data_regex
  union all
  select 'inv_stock', stock_id, coalesce(batch_no, serial_no, location_code, ''), coalesce(location_name, ''), shop_dept_id,
         concat_ws('|', batch_no, serial_no, location_code, location_name, create_by, update_by, remark)
  from inv_stock
  where lower(concat_ws('|', coalesce(batch_no, ''), coalesce(serial_no, ''), coalesce(location_code, ''), coalesce(location_name, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp @test_data_regex
  union all
  select 'inv_stock_log', log_id, coalesce(business_no, batch_no, serial_no, location_code, ''), coalesce(location_name, movement_type, ''), shop_dept_id,
         concat_ws('|', business_no, batch_no, serial_no, location_code, location_name, movement_type, business_type, create_by, remark)
  from inv_stock_log
  where lower(concat_ws('|', coalesce(business_no, ''), coalesce(batch_no, ''), coalesce(serial_no, ''), coalesce(location_code, ''), coalesce(location_name, ''), coalesce(movement_type, ''), coalesce(business_type, ''), coalesce(create_by, ''), coalesce(remark, ''))) regexp @test_data_regex
) candidate_inventory_rows
order by table_name, record_id
limit ${ROW_LIMIT};
SQL

echo
echo "backup_table_inventory"
cat <<SQL | mysql_exec
select table_name, table_rows, round(data_length / 1024 / 1024, 2) as data_mb, create_time
from information_schema.tables
where table_schema = database()
  and lower(table_name) regexp '(^backup_|_backup_|clear_backup|cleanup_backup)'
order by table_name;
SQL

echo
echo "business_reference_counts"

escape_ident() {
  printf '%s' "$1" | sed 's/`/``/g'
}

candidate_dept_sql="select dept_id from sys_dept where lower(concat_ws('|', coalesce(dept_name, ''), coalesce(leader, ''), coalesce(phone, ''), coalesce(email, ''), coalesce(dept_type, ''), coalesce(create_by, ''), coalesce(update_by, ''))) regexp '${MATCH_REGEX}'"
candidate_user_sql="select user_id from sys_user where lower(concat_ws('|', coalesce(user_name, ''), coalesce(nick_name, ''), coalesce(email, ''), coalesce(phonenumber, ''), coalesce(create_by, ''), coalesce(update_by, ''), coalesce(remark, ''))) regexp '${MATCH_REGEX}'"

mysql_exec --skip-column-names -e "
select table_name, column_name
from information_schema.columns
where table_schema = '${DB}'
  and table_name not like 'backup_%'
  and table_name not like '%_backup_%'
  and table_name not in ('sys_dept', 'sys_user', 'sys_user_shop')
  and column_name in ('dept_id', 'shop_dept_id', 'warehouse_id', 'from_dept_id', 'to_dept_id', 'source_dept_id', 'target_dept_id', 'applicant_dept_id')
order by table_name, column_name;
" | while IFS=$'\t' read -r table_name column_name; do
  quoted_table="$(escape_ident "${table_name}")"
  quoted_column="$(escape_ident "${column_name}")"
  count="$(mysql_exec --skip-column-names -e "select count(*) from \`${quoted_table}\` t join (${candidate_dept_sql}) c on t.\`${quoted_column}\` = c.dept_id" 2>/dev/null || printf '0')"
  if [ "${count}" != "0" ]; then
    echo "candidate_sys_dept ${table_name}.${column_name}=${count}"
  fi
done

mysql_exec --skip-column-names -e "
select table_name, column_name
from information_schema.columns
where table_schema = '${DB}'
  and table_name not like 'backup_%'
  and table_name not like '%_backup_%'
  and table_name not in ('sys_dept', 'sys_user', 'sys_user_shop')
  and column_name in ('user_id', 'applicant_id', 'operator_id', 'owner_id', 'approver_id', 'assignee_id')
order by table_name, column_name;
" | while IFS=$'\t' read -r table_name column_name; do
  quoted_table="$(escape_ident "${table_name}")"
  quoted_column="$(escape_ident "${column_name}")"
  count="$(mysql_exec --skip-column-names -e "select count(*) from \`${quoted_table}\` t join (${candidate_user_sql}) c on t.\`${quoted_column}\` = c.user_id" 2>/dev/null || printf '0')"
  if [ "${count}" != "0" ]; then
    echo "candidate_sys_user ${table_name}.${column_name}=${count}"
  fi
done
