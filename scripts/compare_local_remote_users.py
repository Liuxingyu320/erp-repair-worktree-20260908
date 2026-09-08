#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import io
import json
import subprocess

from compare_local_remote_depts import decode_output, read_credentials, run_command


USER_SQL = """
select user_id,dept_id,user_name,nick_name,phonenumber,status,del_flag
from {database}.sys_user
order by user_id;
"""

REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select user_id,dept_id,user_name,nick_name,phonenumber,status,del_flag
from sys_user
order by user_id;
SQL
"""


def parse_users(text: str) -> dict[int, dict[str, str]]:
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("user_id\t"):
            text = "\n".join(lines[index:]) + "\n"
            break
    reader = csv.DictReader(io.StringIO(text), delimiter="\t")
    rows: dict[int, dict[str, str]] = {}
    for row in reader:
        if row and row.get("user_id"):
            rows[int(row["user_id"])] = row
    return rows


def local_users(database: str) -> dict[int, dict[str, str]]:
    result = subprocess.run(
        ["mysql", "--protocol=tcp", "-h127.0.0.1", "-P3306", "-uroot", "--batch", "--raw", "-e", USER_SQL.format(database=database)],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return parse_users(result.stdout)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--local-db", default="BossERP_NEW")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    local = local_users(args.local_db)
    client = read_credentials()
    result = run_command(client, args.region, args.instance_id, REMOTE_SCRIPT, "erp-read-users", args.timeout)
    if result.get("ExitCode") not in (0, "0"):
        print(decode_output(result))
        raise SystemExit(1)
    remote = parse_users(decode_output(result))

    only_local = sorted(set(local) - set(remote))
    only_remote = sorted(set(remote) - set(local))
    mismatches = []
    for user_id in sorted(set(local) & set(remote)):
        diffs = {}
        for field in ("dept_id", "user_name", "nick_name", "phonenumber", "status", "del_flag"):
            if (local[user_id].get(field) or "") != (remote[user_id].get(field) or ""):
                diffs[field] = {"local": local[user_id].get(field), "remote": remote[user_id].get(field)}
        if diffs:
            mismatches.append({"user_id": user_id, "diffs": diffs})

    print(json.dumps({
        "local_count": len(local),
        "remote_count": len(remote),
        "only_local": only_local,
        "only_remote": only_remote,
        "mismatch_count": len(mismatches),
        "mismatch_sample": mismatches[:120],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
