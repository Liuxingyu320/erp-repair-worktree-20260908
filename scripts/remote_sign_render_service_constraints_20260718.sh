#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

echo "SERVICE_CONSTRAINTS_BEGIN"
systemctl show erp-new@oa.service --no-pager \
  -p User -p Group -p MainPID -p ActiveState -p SubState \
  -p NoNewPrivileges -p PrivateTmp -p ProtectSystem -p ProtectHome \
  -p MemoryCurrent -p MemoryHigh -p MemoryMax -p TasksCurrent -p TasksMax \
  -p ReadOnlyPaths -p ReadWritePaths -p InaccessiblePaths

oa_pid="$(systemctl show erp-new@oa.service -p MainPID --value)"
[[ "$oa_pid" =~ ^[1-9][0-9]*$ && -r "/proc/$oa_pid/environ" ]]
python3 - "$oa_pid" <<'PY'
import sys
from pathlib import Path

pid = sys.argv[1]
wanted = (
    "HOME",
    "LANG",
    "PATH",
    "SIGN_PACKAGE_PDF_CONVERTER_COMMAND",
    "SIGN_PACKAGE_PDF_TIMEOUT_SECONDS",
    "SIGN_PACKAGE_TEMP_ROOT",
    "SIGN_PACKAGE_STORAGE_ROOT",
)
values = {}
for item in Path(f"/proc/{pid}/environ").read_bytes().split(b"\0"):
    key, separator, value = item.partition(b"=")
    if separator:
        decoded_key = key.decode("ascii", "ignore")
        if decoded_key in wanted:
            values[decoded_key] = value.decode("utf-8", "replace")
for key in wanted:
    print(f"oa_env|{key}={values.get(key, 'UNSET')}")
PY

echo "tmp_mount=$(findmnt -no SOURCE,FSTYPE,OPTIONS -T /tmp 2>/dev/null || true)"
echo "tmp_probe_begin"
probe="$(mktemp -d /tmp/sign-render-constraint-probe.XXXXXX)"
case "$probe" in /tmp/sign-render-constraint-probe.*) ;; *) exit 20;; esac
touch "$probe/write-ok"
echo "tmp_probe_path_pattern=/tmp/sign-render-constraint-probe.XXXXXX"
echo "tmp_probe_write=OK"
rm -f -- "$probe/write-ok"
rmdir -- "$probe"
echo "tmp_probe_end"
echo "SERVICE_CONSTRAINTS_OK"
