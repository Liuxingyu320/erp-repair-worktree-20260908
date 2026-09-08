#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import zlib
from pathlib import Path

import erp_employee_importer as importer
from generate_remote_user_shop_expected_audit import build_expected_rows


REMOTE_PYTHON = r'''
import base64
import json
import subprocess
import zlib

payload = "__PAYLOAD__"
rows = json.loads(zlib.decompress(base64.b64decode(payload)).decode("utf-8"))


def q(value):
    value = "" if value is None else str(value)
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


values = ",\n".join("(" + ", ".join(q(item) for item in row) + ")" for row in rows)
sql = f"""
CREATE TEMPORARY TABLE expected_user_shop_scope (
  match_type varchar(32) not null,
  match_key varchar(128) not null,
  expected_name varchar(128) not null,
  expected_full_path varchar(1000) not null,
  expected_default char(1) not null,
  source_name varchar(128) not null
);
INSERT INTO expected_user_shop_scope(match_type, match_key, expected_name, expected_full_path, expected_default, source_name) VALUES
{values};
CREATE TEMPORARY TABLE expected_user_shop_scope_lookup AS
SELECT * FROM expected_user_shop_scope;

CREATE TEMPORARY TABLE active_dept_paths AS
WITH RECURSIVE dept_tree AS (
    SELECT dept_id, parent_id, dept_name, CAST(dept_name AS CHAR(1000)) AS full_path
    FROM sys_dept
    WHERE dept_id = 100 AND del_flag = '0'
    UNION ALL
    SELECT d.dept_id, d.parent_id, d.dept_name, CONCAT(t.full_path, ' / ', d.dept_name) AS full_path
    FROM sys_dept d
    JOIN dept_tree t ON t.dept_id = d.parent_id
    WHERE d.del_flag = '0'
)
SELECT dept_id, full_path
FROM dept_tree;

CREATE TEMPORARY TABLE actual_user_shop_scope AS
SELECT
    CASE WHEN u.create_by = 'missing_phone_reseed' THEN 'missing_name' ELSE 'phone' END AS match_type,
    CASE WHEN u.create_by = 'missing_phone_reseed' THEN u.nick_name ELSE COALESCE(NULLIF(u.phonenumber, ''), u.user_name) END AS match_key,
    u.user_id,
    u.nick_name,
    p.full_path,
    us.is_default,
    u.create_by
FROM sys_user u
JOIN sys_user_shop us ON us.user_id = u.user_id
JOIN active_dept_paths p ON p.dept_id = us.dept_id
WHERE u.del_flag = '0'
  AND u.create_by IN ('employee_xls_import', 'missing_phone_reseed');

select 'shop_scope_counts' as section;
select count(*) as expected_rows,
       sum(source_name='员工Excel') as expected_employee_rows,
       sum(source_name='无手机号员工明细_含负责人.xlsx') as expected_missing_phone_rows
from expected_user_shop_scope;

select 'actual_counts' as section;
select count(*) as actual_rows,
       sum(create_by='employee_xls_import') as actual_employee_rows,
       sum(create_by='missing_phone_reseed') as actual_missing_phone_rows
from actual_user_shop_scope;

select 'missing_expected_shop_scope' as section;
select e.source_name, e.match_type, e.match_key, e.expected_name, e.expected_full_path, e.expected_default
from expected_user_shop_scope e
where not exists (
    select 1
    from actual_user_shop_scope a
    where a.match_type = e.match_type
      and a.match_key = e.match_key
      and a.full_path = e.expected_full_path
      and a.is_default = e.expected_default
)
order by e.source_name, e.expected_name, e.expected_full_path;

select 'extra_actual_shop_scope' as section;
select a.create_by, a.match_type, a.match_key, a.user_id, a.nick_name, a.full_path, a.is_default
from actual_user_shop_scope a
where not exists (
    select 1
    from expected_user_shop_scope_lookup e
    where e.match_type = a.match_type
      and e.match_key = a.match_key
      and e.expected_full_path = a.full_path
      and e.expected_default = a.is_default
)
order by a.create_by, a.nick_name, a.full_path;

select 'default_count_errors' as section;
select a.create_by, a.match_type, a.match_key, max(a.nick_name) as nick_name,
       count(*) as shop_count,
       sum(a.is_default='Y') as default_count,
       group_concat(concat(a.full_path, ':', a.is_default) order by a.is_default desc, a.full_path separator ' | ') as shops
from actual_user_shop_scope a
group by a.create_by, a.match_type, a.match_key
having sum(a.is_default='Y') <> 1;

select 'invalid_active_user_shop_depts' as section;
select count(*) as invalid_count
from sys_user_shop us
join sys_user u on u.user_id = us.user_id and u.del_flag = '0'
left join sys_dept d on d.dept_id = us.dept_id and d.del_flag = '0'
where d.dept_id is null;
"""
password = open("/root/.erp-mysql-root-pass").read().strip()
cmd = ["mysql", "--protocol=tcp", "-uroot", f"-p{password}", "BossERP_NEW", "--batch", "--raw"]
subprocess.run(cmd, input=sql, universal_newlines=True, check=True)
'''


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--employee-excel", default=importer.DEFAULT_EXCEL_PATH)
    parser.add_argument("--missing-excel", default="无手机号员工明细_含负责人.xlsx")
    args = parser.parse_args()

    rows = build_expected_rows(Path(args.employee_excel), Path(args.missing_excel))
    payload = base64.b64encode(
        zlib.compress(json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode("utf-8"), level=9)
    ).decode("ascii")
    remote_python = REMOTE_PYTHON.replace("__PAYLOAD__", payload)
    print("#!/usr/bin/env bash")
    print("set -euo pipefail")
    print("python3 - <<'PY'")
    print(remote_python.rstrip())
    print("PY")


if __name__ == "__main__":
    main()
