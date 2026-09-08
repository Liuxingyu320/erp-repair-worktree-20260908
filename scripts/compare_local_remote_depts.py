#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import csv
import datetime as dt
import hashlib
import hmac
import io
import json
import os
import subprocess
import time
import uuid
from dataclasses import dataclass
from urllib.parse import quote, urlencode
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class DeptRow:
    dept_id: int
    parent_id: int
    dept_name: str
    dept_type: str
    order_num: int
    status: str
    del_flag: str
    path: str


LOCAL_PATH_SQL = r"""
with recursive t as (
  select dept_id,parent_id,dept_name,dept_type,order_num,status,del_flag,
         cast(dept_name as char(2000)) path
  from {database}.sys_dept
  where parent_id=0 and del_flag='0'
  union all
  select d.dept_id,d.parent_id,d.dept_name,d.dept_type,d.order_num,d.status,d.del_flag,
         concat(t.path,' / ',d.dept_name)
  from {database}.sys_dept d
  join t on d.parent_id=t.dept_id
  where d.del_flag='0'
)
select dept_id,parent_id,dept_name,dept_type,order_num,status,del_flag,path
from t
order by path;
"""

REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
with recursive t as (
  select dept_id,parent_id,dept_name,dept_type,order_num,status,del_flag,
         cast(dept_name as char(2000)) path
  from sys_dept
  where parent_id=0 and del_flag='0'
  union all
  select d.dept_id,d.parent_id,d.dept_name,d.dept_type,d.order_num,d.status,d.del_flag,
         concat(t.path,' / ',d.dept_name)
  from sys_dept d
  join t on d.parent_id=t.dept_id
  where d.del_flag='0'
)
select dept_id,parent_id,dept_name,dept_type,order_num,status,del_flag,path
from t
order by path;
SQL
"""


def percent_encode(value: object) -> str:
    return quote(str(value), safe="~")


def ecs_request(client: dict[str, str], action: str, region_id: str, params: dict[str, object]) -> dict:
    url = f"https://ecs.{region_id}.aliyuncs.com/"
    last_exc: Exception | None = None
    for _ in range(3):
        try:
            all_params: dict[str, object] = {
                "Format": "JSON",
                "Version": "2014-05-26",
                "AccessKeyId": client["access_key_id"],
                "SignatureMethod": "HMAC-SHA1",
                "Timestamp": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "SignatureVersion": "1.0",
                "SignatureNonce": uuid.uuid4().hex,
                "Action": action,
            }
            all_params.update(params)
            canonical = "&".join(
                f"{percent_encode(k)}={percent_encode(all_params[k])}" for k in sorted(all_params)
            )
            string_to_sign = "POST&%2F&" + percent_encode(canonical)
            digest = hmac.new(
                (client["access_key_secret"] + "&").encode("utf-8"),
                string_to_sign.encode("utf-8"),
                hashlib.sha1,
            ).digest()
            all_params["Signature"] = base64.b64encode(digest).decode("ascii")
            body = urlencode(all_params).encode("utf-8")
            request = Request(url, data=body, method="POST")
            with urlopen(request, timeout=60) as response:
                data = json.loads(response.read().decode("utf-8"))
            if "Code" in data and data.get("Code") != "200":
                raise RuntimeError(f"{action} failed: {data.get('Code')}: {data.get('Message')}")
            return data
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            last_exc = exc
            time.sleep(2)
    raise last_exc if last_exc is not None else RuntimeError(f"{action} failed")


def run_command(client: dict[str, str], region_id: str, instance_id: str, command: str, name: str, timeout: int) -> dict:
    data = ecs_request(
        client,
        "RunCommand",
        region_id,
        {
            "RegionId": region_id,
            "Type": "RunShellScript",
            "Name": name[:128],
            "CommandContent": command,
            "InstanceId.1": instance_id,
            "Timeout": timeout,
        },
    )
    invoke_id = data["InvokeId"]
    deadline = time.time() + timeout + 60
    last: dict | None = None
    while time.time() < deadline:
        result_data = ecs_request(
            client,
            "DescribeInvocationResults",
            region_id,
            {"RegionId": region_id, "InvokeId": invoke_id, "InstanceId": instance_id},
        )
        results = result_data.get("Invocation", {}).get("InvocationResults", {}).get("InvocationResult", [])
        if results:
            result = results[0]
            status = result.get("InvocationStatus")
            if status in {"Success", "Failed", "Stopped", "PartialFailed", "Timeout"}:
                return result
            last = result
        time.sleep(5)
    raise TimeoutError(f"command timed out: {invoke_id}, last={last}")


def decode_output(result: dict) -> str:
    raw = result.get("Output") or ""
    try:
        return base64.b64decode(raw).decode("utf-8", "replace")
    except Exception:
        return raw


def read_credentials() -> dict[str, str]:
    access_key_id = os.environ.get("ALIYUN_ACCESS_KEY_ID")
    access_key_secret = os.environ.get("ALIYUN_ACCESS_KEY_SECRET")
    if not access_key_id or not access_key_secret:
        raise SystemExit("missing ALIYUN_ACCESS_KEY_ID/ALIYUN_ACCESS_KEY_SECRET")
    return {"access_key_id": access_key_id, "access_key_secret": access_key_secret}


def parse_rows(text: str) -> list[DeptRow]:
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("dept_id\t"):
            text = "\n".join(lines[index:]) + "\n"
            break
    reader = csv.DictReader(io.StringIO(text), delimiter="\t")
    rows: list[DeptRow] = []
    for row in reader:
        if not row or not row.get("dept_id"):
            continue
        rows.append(
            DeptRow(
                dept_id=int(row["dept_id"]),
                parent_id=int(row["parent_id"] or 0),
                dept_name=row["dept_name"] or "",
                dept_type=row["dept_type"] or "",
                order_num=int(row["order_num"] or 0),
                status=row["status"] or "",
                del_flag=row["del_flag"] or "",
                path=row["path"] or "",
            )
        )
    return rows


def local_rows(database: str) -> list[DeptRow]:
    sql = LOCAL_PATH_SQL.format(database=database)
    result = subprocess.run(
        ["mysql", "--protocol=tcp", "-h127.0.0.1", "-P3306", "-uroot", "--batch", "--raw", "-e", sql],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return parse_rows(result.stdout)


def remote_rows(region: str, instance_id: str, timeout: int) -> list[DeptRow]:
    client = read_credentials()
    result = run_command(client, region, instance_id, REMOTE_SCRIPT, "erp-read-sys-dept", timeout)
    output = decode_output(result)
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(json.dumps(result, ensure_ascii=False, indent=2))
    return parse_rows(output)


def type_counts(rows: list[DeptRow]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        counts[row.dept_type] = counts.get(row.dept_type, 0) + 1
    return dict(sorted(counts.items()))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--local-db", default="BossERP_NEW")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    local = local_rows(args.local_db)
    remote = remote_rows(args.region, args.instance_id, args.timeout)

    local_by_id = {row.dept_id: row for row in local}
    remote_by_id = {row.dept_id: row for row in remote}
    local_paths = {row.path for row in local}
    remote_paths = {row.path for row in remote}

    print(json.dumps({
        "local_count": len(local),
        "remote_count": len(remote),
        "local_type_counts": type_counts(local),
        "remote_type_counts": type_counts(remote),
        "ids_only_local": sorted(set(local_by_id) - set(remote_by_id)),
        "ids_only_remote": sorted(set(remote_by_id) - set(local_by_id)),
        "paths_only_local_count": len(local_paths - remote_paths),
        "paths_only_remote_count": len(remote_paths - local_paths),
    }, ensure_ascii=False, indent=2))

    print("\n-- field mismatches by same dept_id --")
    mismatch_count = 0
    for dept_id in sorted(set(local_by_id) & set(remote_by_id)):
        lrow = local_by_id[dept_id]
        rrow = remote_by_id[dept_id]
        diffs = []
        for field in ("parent_id", "dept_name", "dept_type", "order_num", "status", "path"):
            if getattr(lrow, field) != getattr(rrow, field):
                diffs.append(f"{field}: local={getattr(lrow, field)!r} remote={getattr(rrow, field)!r}")
        if diffs:
            mismatch_count += 1
            print(f"{dept_id}\t" + " | ".join(diffs))
    print(f"mismatch_count={mismatch_count}")

    print("\n-- sample paths only in local --")
    for path in sorted(local_paths - remote_paths)[:80]:
        print(path)

    print("\n-- sample paths only in remote --")
    for path in sorted(remote_paths - local_paths)[:120]:
        print(path)


if __name__ == "__main__":
    main()
