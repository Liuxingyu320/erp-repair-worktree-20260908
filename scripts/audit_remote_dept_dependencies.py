#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json

from compare_local_remote_depts import (
    decode_output,
    local_rows,
    read_credentials,
    remote_rows,
    run_command,
)


def sql_id_list(ids: list[int]) -> str:
    return ",".join(str(i) for i in ids) if ids else "NULL"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--local-db", default="BossERP_NEW")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    local = local_rows(args.local_db)
    remote = remote_rows(args.region, args.instance_id, args.timeout)
    local_ids = {row.dept_id for row in local}
    remote_ids = {row.dept_id for row in remote}
    remote_only = sorted(remote_ids - local_ids)
    local_only = sorted(local_ids - remote_ids)

    client = read_credentials()
    remote_only_sql = sql_id_list(remote_only)
    local_only_sql = sql_id_list(local_only)
    command = f"""#!/usr/bin/env bash
set -euo pipefail
DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
mysql_base=(mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB")

echo "-- id summary --"
echo "remote_only_ids={remote_only_sql}"
echo "local_only_ids={local_only_sql}"

echo "-- remote-only active dept nodes --"
"${{mysql_base[@]}}" <<'SQL'
select d.dept_id,d.parent_id,d.dept_name,d.dept_type,d.del_flag,
       (select count(*) from sys_user u where u.del_flag='0' and u.dept_id=d.dept_id) active_users,
       (select count(*) from sys_user_shop us join sys_user u on u.user_id=us.user_id and u.del_flag='0' where us.dept_id=d.dept_id) active_user_shop_links,
       (select count(*) from sys_dept c where c.del_flag='0' and c.parent_id=d.dept_id) active_children
from sys_dept d
where d.dept_id in ({remote_only_sql})
order by d.dept_id;
SQL

echo "-- users pointing at remote-only ids --"
"${{mysql_base[@]}}" <<'SQL'
select u.user_id,u.user_name,u.nick_name,u.phonenumber,u.dept_id,d.dept_name
from sys_user u
left join sys_dept d on d.dept_id=u.dept_id
where u.del_flag='0' and u.dept_id in ({remote_only_sql})
order by u.user_id
limit 200;
SQL

echo "-- user-shop links pointing at remote-only ids --"
"${{mysql_base[@]}}" <<'SQL'
select us.user_id,u.user_name,u.nick_name,u.phonenumber,us.dept_id,d.dept_name,us.is_default
from sys_user_shop us
join sys_user u on u.user_id=us.user_id and u.del_flag='0'
left join sys_dept d on d.dept_id=us.dept_id
where us.dept_id in ({remote_only_sql})
order by us.dept_id,us.user_id
limit 300;
SQL

echo "-- local-only ids current rows on remote --"
"${{mysql_base[@]}}" <<'SQL'
select dept_id,parent_id,dept_name,dept_type,status,del_flag
from sys_dept
where dept_id in ({local_only_sql})
order by dept_id;
SQL

echo "-- business references to remote-only ids --"
"${{mysql_base[@]}}" --skip-column-names -e "
select table_name, column_name
from information_schema.columns
where table_schema='${{DB}}'
  and table_name not in ('sys_dept','sys_user','sys_user_shop')
  and column_name in ('dept_id','shop_dept_id','target_dept_id','source_dept_id','from_dept_id','to_dept_id')
order by table_name, column_name;
" | while IFS=$'\\t' read -r table_name column_name; do
  count="$("${{mysql_base[@]}}" --skip-column-names -e "select count(*) from \`${{table_name}}\` where \`${{column_name}}\` in ({remote_only_sql})" 2>/dev/null || echo 0)"
  if [ "${{count}}" != "0" ]; then
    echo "${{table_name}}.${{column_name}}=${{count}}"
  fi
done
"""
    result = run_command(client, args.region, args.instance_id, command, "erp-audit-dept-refs", args.timeout)
    output = decode_output(result)
    print(json.dumps({
        "exitCode": result.get("ExitCode"),
        "invocationStatus": result.get("InvocationStatus"),
        "remote_only_count": len(remote_only),
        "local_only_count": len(local_only),
    }, ensure_ascii=False, indent=2))
    print(output)
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
