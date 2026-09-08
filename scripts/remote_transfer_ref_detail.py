#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, local_rows, read_credentials, remote_rows, run_command


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--local-db", default="BossERP_NEW")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    local_ids = {row.dept_id for row in local_rows(args.local_db)}
    remote_ids = {row.dept_id for row in remote_rows(args.region, args.instance_id, args.timeout)}
    remote_only = sorted(remote_ids - local_ids)
    ids = ",".join(str(i) for i in remote_only) or "NULL"
    command = f"""#!/usr/bin/env bash
set -euo pipefail
DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
echo "-- columns --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" -e "show columns from inv_transfer_order;"
echo "-- rows --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select o.*, fd.dept_name as from_name, td.dept_name as to_name
from inv_transfer_order o
left join sys_dept fd on fd.dept_id=o.from_dept_id
left join sys_dept td on td.dept_id=o.to_dept_id
where o.from_dept_id in ({ids}) or o.to_dept_id in ({ids});
SQL
"""
    result = run_command(read_credentials(), args.region, args.instance_id, command, "erp-transfer-ref-detail", args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
