#!/usr/bin/env bash
set -Euo pipefail
umask 077

echo "TRANSACTION_SIMULATION_BEGIN"
echo "root_disk=$(df -B1 --output=size,used,avail,pcent,target / | tail -n 1 | xargs)"
echo "root_inode=$(df -i / | tail -n 1 | awk '{print "total=" $2 ",used=" $3 ",avail=" $4 ",use=" $5 ",mount=" $6}')"
echo "memory_available_bytes=$(awk '/^MemAvailable:/ {print $2 * 1024}' /proc/meminfo)"

oa_pid="$(systemctl show erp-new@oa.service -p MainPID --value 2>/dev/null || true)"
if [[ "$oa_pid" =~ ^[1-9][0-9]*$ && -r "/proc/$oa_pid/environ" ]]; then
  python3 - "$oa_pid" <<'PY'
import sys
from pathlib import Path

pid = sys.argv[1]
for item in Path(f"/proc/{pid}/environ").read_bytes().split(b"\0"):
    if item.startswith(b"PATH="):
        print("oa_path=" + item.partition(b"=")[2].decode("utf-8", "replace"))
        break
else:
    print("oa_path=UNSET")
PY
fi

simulate() {
  local label="$1"
  shift
  echo "SIMULATION_BEGIN|$label|requested=$*"
  set +e
  LC_ALL=C dnf --cacheonly --setopt=install_weak_deps=False \
    install --assumeno "$@" 2>&1 \
    | sed "s/^/DNF|$label|/"
  pipeline_rc=("${PIPESTATUS[@]}")
  set -e
  echo "SIMULATION_END|$label|dnf_rc=${pipeline_rc[0]}|sed_rc=${pipeline_rc[1]}"
}

simulate docx_minimal \
  libreoffice-writer \
  google-noto-sans-cjk-ttc-fonts \
  poppler-utils

simulate dockerfile_parity \
  libreoffice-writer \
  libreoffice-calc \
  google-noto-sans-cjk-ttc-fonts \
  poppler-utils

echo "PACKAGE_FILE_HINTS_BEGIN"
dnf -q --cacheonly repoquery --available --latest-limit 1 --list google-noto-sans-cjk-ttc-fonts 2>/dev/null \
  | grep -E '/NotoSans.*\.(ttc|otf)$' | head -n 30 | sed 's/^/FONT_FILE|/' || true
dnf -q --cacheonly repoquery --available --latest-limit 1 --list libreoffice-core 2>/dev/null \
  | grep -E '/(libreoffice|soffice)$' | head -n 30 | sed 's/^/OFFICE_FILE|/' || true
dnf -q --cacheonly repoquery --available --latest-limit 1 --list poppler-utils 2>/dev/null \
  | grep -E '/(pdfinfo|pdffonts|pdftotext|pdftoppm)$' | sort | sed 's/^/PDF_TOOL_FILE|/' || true
echo "PACKAGE_FILE_HINTS_END"

echo "TRANSACTION_SIMULATION_OK"
