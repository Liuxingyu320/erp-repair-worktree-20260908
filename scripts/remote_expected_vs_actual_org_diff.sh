#!/usr/bin/env bash
set -euo pipefail

DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
IMPORT_DIR="$(ls -dt /opt/erp-new-data-imports/employee-* | head -n 1)"
WORK_DIR="/tmp/erp-org-diff-$$"
mkdir -p "$WORK_DIR"

cd "$IMPORT_DIR"
export PYTHONPATH="$PWD/vendor:${PYTHONPATH:-}"
python3 - <<'PY' > "$WORK_DIR/expected_paths.txt"
from erp_employee_importer import build_plan_from_workbook

plan = build_plan_from_workbook("employee.xls")
expected = {"金英灵韵集团", "金英灵韵集团 / 仓库后勤部门 / 主仓库"}
for path in plan.dept_paths:
    for index in range(1, len(path) + 1):
        expected.add("金英灵韵集团 / " + " / ".join(path[:index]))
for item in sorted(expected):
    print(item)
PY

mysql --batch --raw --skip-column-names -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<'SQL' > "$WORK_DIR/actual_paths.tsv"
with recursive tree as (
  select dept_id, parent_id, dept_name, dept_type, ancestors, order_num,
         cast(dept_name as char(2000)) as full_path,
         0 as depth
  from sys_dept
  where parent_id = 0 and del_flag='0'
  union all
  select d.dept_id, d.parent_id, d.dept_name, d.dept_type, d.ancestors, d.order_num,
         concat(tree.full_path, ' / ', d.dept_name) as full_path,
         tree.depth + 1 as depth
  from sys_dept d
  join tree on tree.dept_id = d.parent_id
  where d.del_flag='0'
)
select full_path, dept_id, parent_id, dept_name, dept_type
from tree
where dept_type in ('GROUP','COMPANY','STORE','WAREHOUSE')
order by full_path, dept_id;
SQL

cut -f1 "$WORK_DIR/actual_paths.tsv" > "$WORK_DIR/actual_paths.txt"

echo "import_dir=$IMPORT_DIR"
echo "expected_count=$(wc -l < "$WORK_DIR/expected_paths.txt")"
echo "actual_count=$(wc -l < "$WORK_DIR/actual_paths.txt")"

echo "-- missing_in_actual --"
comm -23 "$WORK_DIR/expected_paths.txt" "$WORK_DIR/actual_paths.txt" || true

echo "-- extra_in_actual --"
comm -13 "$WORK_DIR/expected_paths.txt" "$WORK_DIR/actual_paths.txt" > "$WORK_DIR/extra_paths.txt" || true
cat "$WORK_DIR/extra_paths.txt"

echo "-- extra_path_references --"
python3 - "$WORK_DIR/actual_paths.tsv" "$WORK_DIR/extra_paths.txt" > "$WORK_DIR/extra_ids.txt" <<'PY'
import sys
actual_file, extra_file = sys.argv[1:3]
actual = {}
with open(actual_file, encoding="utf-8") as fh:
    for line in fh:
        full_path, dept_id, parent_id, dept_name, dept_type = line.rstrip("\n").split("\t")
        actual[full_path] = (dept_id, parent_id, dept_name, dept_type)
with open(extra_file, encoding="utf-8") as fh:
    for line in fh:
        path = line.rstrip("\n")
        if not path:
            continue
        dept_id, parent_id, dept_name, dept_type = actual[path]
        print("\t".join([dept_id, path, parent_id, dept_name, dept_type]))
PY

if [ -s "$WORK_DIR/extra_ids.txt" ]; then
  ids="$(cut -f1 "$WORK_DIR/extra_ids.txt" | paste -sd, -)"
  mysql --batch --raw -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" <<SQL
select e.dept_id, e.dept_name, e.dept_type,
       (select count(*) from sys_dept c where c.del_flag='0' and c.parent_id=e.dept_id) as active_children,
       (select count(*) from sys_user u where u.del_flag='0' and u.dept_id=e.dept_id) as active_users,
       (select count(*) from sys_user_shop us join sys_user u on u.user_id=us.user_id and u.del_flag='0' where us.dept_id=e.dept_id) as active_user_shop_links
from sys_dept e
where e.dept_id in (${ids})
order by e.dept_id;
SQL
fi

rm -rf "$WORK_DIR"
