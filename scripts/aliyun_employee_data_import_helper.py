#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shutil
import tarfile
import tempfile
import time
from pathlib import Path

import oss2

import aliyun_ecs_deploy_helper as ecs


DEFAULT_EXCEL_PATH = (
    "/Users/liuxingyu/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_el0e9x2hyy9022_b7f6/temp/RWTemp/2026-06/"
    "559f94eccc121c4784e667d4eef125da/"
    "北京金英灵韵茶业有限公司在职员工20260619(3).xls"
)


def find_xlrd_package() -> Path:
    import xlrd

    package_dir = Path(xlrd.__file__).resolve().parent
    if not package_dir.is_dir():
        raise SystemExit(f"xlrd package not found: {package_dir}")
    return package_dir


def find_dataclasses_backport() -> Path:
    candidates = []
    env_path = os.environ.get("DATACLASSES_BACKPORT")
    if env_path:
        candidates.append(Path(env_path))
    candidates.extend([
        Path("/tmp/dataclasses-backport-extract/dataclasses.py"),
        Path("/private/tmp/dataclasses-backport-extract/dataclasses.py"),
    ])
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(
        "dataclasses backport not found; download it with "
        "`python3 -m pip download dataclasses==0.8 --no-deps --ignore-requires-python`"
    )


def build_import_package(excel: Path) -> Path:
    excel = excel.resolve()
    if not excel.is_file():
        raise SystemExit(f"employee excel not found: {excel}")

    script = Path(__file__).resolve().parent / "erp_employee_importer.py"
    if not script.is_file():
        raise SystemExit(f"employee importer not found: {script}")

    xlrd_dir = find_xlrd_package()
    dataclasses_backport = find_dataclasses_backport()
    package_path = Path.cwd() / f"employee-data-import-{time.strftime('%Y%m%d%H%M%S')}.tar.gz"
    with tempfile.TemporaryDirectory(prefix="erp-employee-import-") as tmp:
        root = Path(tmp) / "payload"
        (root / "vendor").mkdir(parents=True)
        script_text = script.read_text(encoding="utf-8")
        script_text = script_text.replace("from __future__ import annotations\n\n", "")
        (root / "erp_employee_importer.py").write_text(script_text, encoding="utf-8")
        shutil.copy2(excel, root / "employee.xls")
        shutil.copytree(xlrd_dir, root / "vendor" / "xlrd")
        shutil.copy2(dataclasses_backport, root / "vendor" / "dataclasses.py")
        with tarfile.open(package_path, "w:gz") as tar:
            tar.add(root, arcname=".")
    return package_path


def employee_import(args: argparse.Namespace) -> None:
    ak, secret = ecs.read_credentials()
    package = build_import_package(Path(args.excel))
    package_sha = ecs.sha256_file(package)

    auth = oss2.Auth(ak, secret)
    endpoint = f"{args.oss_scheme}://oss-{args.region}.aliyuncs.com"
    bucket_name = args.bucket or ecs.make_bucket_name("erp-data-import")
    bucket = oss2.Bucket(auth, endpoint, bucket_name, connect_timeout=30, proxies={})
    created_bucket = False
    object_key = f"erp-data-import/{package.name}"
    try:
        try:
            bucket.create_bucket(oss2.models.BUCKET_ACL_PRIVATE)
            created_bucket = True
        except oss2.exceptions.BucketAlreadyExists:
            raise SystemExit(f"bucket already exists globally: {bucket_name}")
        except oss2.exceptions.BucketAlreadyOwnedByYou:
            pass

        print(f"Uploading employee import package to OSS bucket {bucket_name} ...", flush=True)
        with package.open("rb") as fh:
            bucket.put_object(object_key, fh)
        signed_url = bucket.sign_url("GET", object_key, args.url_ttl)

        client = ecs.ecs_client(ak, secret)
        stamp = time.strftime("%Y%m%d%H%M%S")
        command = f"""#!/usr/bin/env bash
set -euo pipefail
umask 077
PACKAGE={json.dumps(package.name)}
SIGNED_URL={json.dumps(signed_url)}
EXPECTED_SHA={json.dumps(package_sha)}
IMPORT_DIR=/opt/erp-new-data-imports/employee-{stamp}
BACKUP_DIR=/opt/erp-new-data-backups/employee-{stamp}
DB=BossERP_NEW
mkdir -p "$IMPORT_DIR" "$BACKUP_DIR"
cd "$IMPORT_DIR"
if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 --connect-timeout 15 "$SIGNED_URL" -o "$PACKAGE"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$PACKAGE" "$SIGNED_URL"
else
  echo "curl/wget not found" >&2
  exit 20
fi
ACTUAL_SHA="$(sha256sum "$PACKAGE" | awk '{{print $1}}')"
if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  echo "sha256 mismatch: $ACTUAL_SHA" >&2
  exit 21
fi
tar -xzf "$PACKAGE"
export PYTHONPATH="$PWD/vendor:${{PYTHONPATH:-}}"
echo "-- preflight plan --"
python3 erp_employee_importer.py --excel employee.xls
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
echo "-- external backup --"
mysqldump --single-transaction --quick -uroot -p"$MYSQL_ROOT_PASS" "$DB" \\
  sys_dept sys_post sys_role sys_user sys_user_post sys_user_role sys_user_shop \\
  | gzip > "$BACKUP_DIR/sys_employee_tables_before.sql.gz"
ls -lh "$BACKUP_DIR/sys_employee_tables_before.sql.gz"
echo "-- apply import --"
python3 erp_employee_importer.py --excel employee.xls --password "$MYSQL_ROOT_PASS" --apply
echo "-- post counts --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select 'sys_dept' as table_name, count(*) as rows_count from sys_dept
union all select 'sys_post', count(*) from sys_post
union all select 'sys_role', count(*) from sys_role
union all select 'sys_user', count(*) from sys_user
union all select 'sys_user_post', count(*) from sys_user_post
union all select 'sys_user_role', count(*) from sys_user_role
union all select 'sys_user_shop', count(*) from sys_user_shop
union all select 'active_users', count(*) from sys_user where del_flag='0' and status='0'
union all select 'employee_import_users', count(*) from sys_user where create_by='employee_xls_import' or update_by='employee_xls_import'
union all select 'employee_import_depts', count(*) from sys_dept where create_by='employee_xls_import' or update_by='employee_xls_import';
SQL
echo "IMPORT_OK import_dir=$IMPORT_DIR backup_dir=$BACKUP_DIR"
"""

        print("Running employee data import through ECS Cloud Assistant ...", flush=True)
        result = ecs.run_command(client, args.region, args.instance_id, command, "erp-employee-data-import", args.timeout)
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
                if created_bucket or bucket_name.startswith("erp-data-import-"):
                    bucket.delete_bucket()
            except Exception as exc:
                print(f"warning: OSS cleanup failed: {exc}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", required=True)
    parser.add_argument("--instance-id", required=True)
    parser.add_argument("--excel", default=DEFAULT_EXCEL_PATH)
    parser.add_argument("--bucket")
    parser.add_argument("--url-ttl", type=int, default=7200)
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--oss-scheme", choices=["https", "http"], default="https")
    parser.add_argument("--cleanup-oss", action="store_true")
    args = parser.parse_args()
    employee_import(args)


if __name__ == "__main__":
    main()
