#!/usr/bin/env python3
"""Export selected ERP MySQL rows without printing database credentials."""

import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path


DEFAULT_JAR = Path("/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar")


def process_environment():
    try:
        pid = int(
            subprocess.check_output(
                ["systemctl", "show", "-p", "MainPID", "--value", "erp-new@oa.service"],
                universal_newlines=True,
            ).strip()
        )
        raw = Path(f"/proc/{pid}/environ").read_bytes()
    except (OSError, subprocess.SubprocessError, ValueError):
        return {}

    result = {}
    for item in raw.split(b"\0"):
        if b"=" not in item:
            continue
        key, value = item.split(b"=", 1)
        result[key.decode(errors="ignore")] = value.decode(errors="ignore")
    return result


def default_from_bootstrap(name):
    with zipfile.ZipFile(DEFAULT_JAR) as jar:
        text = jar.read("BOOT-INF/classes/bootstrap.yml").decode()
    match = re.search(rf"\$\{{{re.escape(name)}:([^}}]*)\}}", text)
    return match.group(1) if match else None


def main():
    if len(sys.argv) != 3:
        print("usage: remote_mysql_dump.py TABLE WHERE", file=sys.stderr)
        return 2

    table, where = sys.argv[1:]
    allowed_tables = {
        "oa_sign_template",
        "oa_sign_plan_template",
        "oa_sign_plan_version",
        "oa_sign_plan_version_template",
        "sys_user",
    }
    if table not in allowed_tables:
        print(f"table is not allowed: {table}", file=sys.stderr)
        return 2

    service_env = process_environment()
    username = service_env.get("MYSQL_USERNAME") or default_from_bootstrap("MYSQL_USERNAME") or "root"
    password = service_env.get("MYSQL_PASSWORD")
    if password is None:
        password = default_from_bootstrap("MYSQL_PASSWORD") or ""
    database = service_env.get("MYSQL_DATABASE") or default_from_bootstrap("MYSQL_DATABASE") or "BossERP_NEW"

    env = os.environ.copy()
    env["MYSQL_PWD"] = password
    command = [
        "mysqldump",
        "--compact",
        "--complete-insert",
        "--no-create-info",
        "--skip-lock-tables",
        "--skip-triggers",
        "--replace",
        "--user",
        username,
        database,
        table,
        f"--where={where}",
    ]
    return subprocess.run(command, env=env).returncode


if __name__ == "__main__":
    raise SystemExit(main())
