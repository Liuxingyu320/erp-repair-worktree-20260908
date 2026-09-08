#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import oss2

import aliyun_ecs_deploy_helper as ecs


def apply_sql(args: argparse.Namespace) -> None:
    ak, secret = ecs.read_credentials()
    sql_file = Path(args.sql).resolve()
    if not sql_file.is_file():
        raise SystemExit(f"sql file not found: {sql_file}")
    sql_sha = ecs.sha256_file(sql_file)

    auth = oss2.Auth(ak, secret)
    endpoint = f"{args.oss_scheme}://oss-{args.region}.aliyuncs.com"
    bucket_name = args.bucket or ecs.make_bucket_name("erp-sql-apply")
    bucket = oss2.Bucket(auth, endpoint, bucket_name, connect_timeout=ecs.OSS_REQUEST_TIMEOUT, proxies={})
    created_bucket = False
    object_key = f"erp-sql-apply/{sql_file.name}"
    try:
        try:
            bucket.create_bucket(oss2.models.BUCKET_ACL_PRIVATE)
            created_bucket = True
        except oss2.exceptions.BucketAlreadyExists:
            raise SystemExit(f"bucket already exists globally: {bucket_name}")
        except oss2.exceptions.BucketAlreadyOwnedByYou:
            pass

        print(f"Uploading SQL to OSS bucket {bucket_name} ...", flush=True)
        ecs.put_object_with_retry(bucket, object_key, sql_file.read_bytes())
        signed_url = bucket.sign_url("GET", object_key, args.url_ttl)

        stamp = time.strftime("%Y%m%d%H%M%S")
        backup_tables = " ".join(args.backup_table or [])
        command = f"""#!/usr/bin/env bash
set -euo pipefail
umask 077
SQL_FILE={json.dumps(sql_file.name)}
SIGNED_URL={json.dumps(signed_url)}
EXPECTED_SHA={json.dumps(sql_sha)}
WORK_DIR=/opt/erp-new-data-imports/sql-{stamp}
BACKUP_DIR=/opt/erp-new-data-backups/sql-{stamp}
DB={json.dumps(args.database)}
BACKUP_TABLES={json.dumps(backup_tables)}
mkdir -p "$WORK_DIR" "$BACKUP_DIR"
cd "$WORK_DIR"
if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 --connect-timeout 15 "$SIGNED_URL" -o "$SQL_FILE"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$SQL_FILE" "$SIGNED_URL"
else
  echo "curl/wget not found" >&2
  exit 20
fi
ACTUAL_SHA="$(sha256sum "$SQL_FILE" | awk '{{print $1}}')"
if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  echo "sha256 mismatch: $ACTUAL_SHA" >&2
  exit 21
fi
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
if [ -n "$BACKUP_TABLES" ]; then
  echo "-- external backup --"
  mysqldump --single-transaction --quick -uroot -p"$MYSQL_ROOT_PASS" "$DB" $BACKUP_TABLES \\
    | gzip > "$BACKUP_DIR/tables_before.sql.gz"
  ls -lh "$BACKUP_DIR/tables_before.sql.gz"
fi
echo "-- apply sql --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" < "$SQL_FILE"
echo "SQL_APPLY_OK work_dir=$WORK_DIR backup_dir=$BACKUP_DIR"
"""
        client = ecs.ecs_client(ak, secret)
        print("Running SQL through ECS Cloud Assistant ...", flush=True)
        result = ecs.run_command(client, args.region, args.instance_id, command, args.name, args.timeout)
        output = ecs.decode_output(result)
        print(json.dumps({
            "invocationStatus": result.get("InvocationStatus"),
            "exitCode": result.get("ExitCode"),
            "output": output[-20000:],
        }, ensure_ascii=False, indent=2))
        if result.get("ExitCode") not in (0, "0"):
            raise SystemExit(1)
    finally:
        if args.cleanup_oss:
            try:
                bucket.delete_object(object_key)
                if created_bucket or bucket_name.startswith("erp-sql-apply-"):
                    bucket.delete_bucket()
            except Exception as exc:
                print(f"warning: OSS cleanup failed: {exc}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", required=True)
    parser.add_argument("--instance-id", required=True)
    parser.add_argument("--sql", required=True)
    parser.add_argument("--database", default="BossERP_NEW")
    parser.add_argument("--backup-table", action="append")
    parser.add_argument("--bucket")
    parser.add_argument("--url-ttl", type=int, default=7200)
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--oss-scheme", choices=["https", "http"], default="https")
    parser.add_argument("--cleanup-oss", action="store_true")
    parser.add_argument("--name", default="erp-apply-sql")
    args = parser.parse_args()
    apply_sql(args)


if __name__ == "__main__":
    main()
