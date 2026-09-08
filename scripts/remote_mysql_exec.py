#!/usr/bin/env python3
"""Run a MySQL statement on an ERP service host without printing credentials."""

import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path


DEFAULT_JAR = Path("/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar")


def process_environment():
    try:
        output = subprocess.check_output(
            ["systemctl", "show", "-p", "MainPID", "--value", "erp-new@oa.service"],
            universal_newlines=True,
        ).strip()
        pid = int(output)
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
    sql = sys.stdin.read()
    if not sql.strip():
        print("SQL input is empty", file=sys.stderr)
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
        "mysql",
        "--batch",
        "--raw",
        "--skip-column-names",
        "--user",
        username,
        database,
        "--execute",
        sql,
    ]
    completed = subprocess.run(command, env=env, universal_newlines=True)
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
