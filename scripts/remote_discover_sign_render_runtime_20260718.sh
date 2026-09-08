#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

echo "RUNTIME_DISCOVERY_BEGIN"
echo "host=$(hostname)"
echo "release=$(readlink -f /opt/erp-new 2>/dev/null || true)"
if [[ -r /etc/os-release ]]; then
  . /etc/os-release
  echo "os=${ID:-unknown} ${VERSION_ID:-unknown}"
fi

echo "FONT_MATCH_BEGIN"
fc-match -f 'query=Noto Sans CJK SC;family=%{family};style=%{style};file=%{file}\n' 'Noto Sans CJK SC' || true
fc-match -f 'query=Noto Sans CJK SC Bold;family=%{family};style=%{style};file=%{file}\n' 'Noto Sans CJK SC:style=Bold' || true
fc-match -f 'query=sans-serif;family=%{family};style=%{style};file=%{file}\n' 'sans-serif' || true
echo "FONT_MATCH_END"

echo "PACKAGE_STATE_BEGIN"
if command -v dpkg-query >/dev/null 2>&1; then
  for package in fontconfig fonts-noto-cjk libreoffice libreoffice-writer libreoffice-calc poppler-utils; do
    if dpkg-query -W -f='${Status}|${Version}\n' "$package" 2>/dev/null | grep -q '^install ok installed|'; then
      dpkg-query -W -f="PACKAGE|$package|\${Status}|\${Version}\n" "$package" 2>/dev/null || true
    else
      echo "PACKAGE|$package|NOT_INSTALLED"
    fi
  done
fi
if command -v rpm >/dev/null 2>&1; then
  for package in fontconfig google-noto-sans-cjk-fonts google-noto-cjk-fonts libreoffice libreoffice-writer libreoffice-calc poppler-utils poppler; do
    if rpm -q "$package" >/dev/null 2>&1; then
      echo "PACKAGE|$package|$(rpm -q "$package")"
    else
      echo "PACKAGE|$package|NOT_INSTALLED"
    fi
  done
fi
echo "PACKAGE_STATE_END"

echo "NONSTANDARD_BINARY_BEGIN"
for candidate in \
  /usr/bin/libreoffice /usr/bin/soffice \
  /usr/local/bin/libreoffice /usr/local/bin/soffice \
  /usr/lib/libreoffice/program/soffice \
  /opt/libreoffice/program/soffice \
  /snap/bin/libreoffice; do
  if [[ -e "$candidate" ]]; then
    echo "BINARY|$candidate|type=$(stat -c '%F' "$candidate" 2>/dev/null || true)"
  fi
done
echo "NONSTANDARD_BINARY_END"

echo "SERVICE_RUNTIME_BEGIN"
systemctl show erp-new@oa.service \
  -p LoadState -p ActiveState -p SubState -p FragmentPath -p ExecStart --no-pager 2>/dev/null \
  | sed -E 's/(password|secret|token)=[^ ;]+/\1=REDACTED/Ig' || true
oa_pid="$(systemctl show erp-new@oa.service -p MainPID --value 2>/dev/null || true)"
if [[ "$oa_pid" =~ ^[1-9][0-9]*$ && -r "/proc/$oa_pid/exe" ]]; then
  echo "oa_pid=$oa_pid"
  echo "oa_exe=$(readlink -f "/proc/$oa_pid/exe" 2>/dev/null || true)"
  echo "oa_cgroup_begin"
  sed -n '1,40p' "/proc/$oa_pid/cgroup" 2>/dev/null || true
  echo "oa_cgroup_end"
  python3 - "$oa_pid" <<'PY'
import sys
from pathlib import Path

pid = sys.argv[1]
values = {}
for item in Path(f"/proc/{pid}/environ").read_bytes().split(b"\0"):
    key, separator, value = item.partition(b"=")
    if separator and key.decode("ascii", "ignore") in {
        "SIGN_PACKAGE_PDF_CONVERTER_COMMAND",
        "SIGN_PACKAGE_PDF_TIMEOUT_SECONDS",
    }:
        values[key.decode("ascii")] = value.decode("utf-8", "replace")
for key in ("SIGN_PACKAGE_PDF_CONVERTER_COMMAND", "SIGN_PACKAGE_PDF_TIMEOUT_SECONDS"):
    print(f"oa_env|{key}={values.get(key, 'UNSET')}")
PY
fi
echo "SERVICE_RUNTIME_END"

echo "CONTAINER_RUNTIME_BEGIN"
for runtime in docker podman nerdctl; do
  runtime_path="$(command -v "$runtime" 2>/dev/null || true)"
  echo "container_tool=$runtime path=${runtime_path:-MISSING}"
done
if command -v docker >/dev/null 2>&1; then
  docker ps --format 'CONTAINER|{{.ID}}|{{.Image}}|{{.Names}}|{{.Status}}' 2>/dev/null || true
  docker image ls --format 'IMAGE|{{.Repository}}:{{.Tag}}|{{.ID}}|{{.Size}}' 2>/dev/null \
    | grep -Ei 'erp|oa|bosserp' || true
fi
if command -v podman >/dev/null 2>&1; then
  podman ps --format 'CONTAINER|{{.ID}}|{{.Image}}|{{.Names}}|{{.Status}}' 2>/dev/null || true
fi
echo "CONTAINER_RUNTIME_END"

echo "RUNTIME_DISCOVERY_OK"
