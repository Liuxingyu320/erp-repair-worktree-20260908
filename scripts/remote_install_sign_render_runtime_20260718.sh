#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

LOCK_FILE="/var/lock/erp-sign-render-runtime.lock"
BACKUP_ROOT="/opt/erp-new-data-backups"
PACKAGES=(
  libreoffice-writer
  google-noto-sans-cjk-ttc-fonts
  poppler-utils
)
REPOS="alinux3-os,alinux3-updates,alinux3-plus,alinux3-powertools,alinux3-module"

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "SIGN_RENDER_INSTALL_LOCKED" >&2
  exit 2
fi

if [[ ! -r /etc/os-release ]]; then
  echo "OS_RELEASE_MISSING" >&2
  exit 3
fi
# shellcheck disable=SC1091
. /etc/os-release
if [[ "${ID:-}" != "alinux" || "${VERSION_ID:-}" != "3" ]]; then
  echo "UNSUPPORTED_OS id=${ID:-unknown} version=${VERSION_ID:-unknown}" >&2
  exit 4
fi

for command in dnf rpm fc-cache flock systemctl sha256sum; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "REQUIRED_COMMAND_MISSING command=$command" >&2
    exit 5
  }
done

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
record_dir="$BACKUP_ROOT/sign-render-runtime-$timestamp"
mkdir -m 700 "$record_dir"

rpm -qa --qf '%{NAME}|%{ARCH}|%{EPOCHNUM}:%{VERSION}-%{RELEASE}\n' \
  | sort > "$record_dir/rpm-before.txt"
dnf history list > "$record_dir/dnf-history-before.txt"
df -B1 / > "$record_dir/disk-before.txt"
free -b > "$record_dir/memory-before.txt"
systemctl is-active erp-new@oa.service > "$record_dir/oa-before.txt"

already_ready=true
for package in "${PACKAGES[@]}"; do
  if ! rpm -q "$package" >/dev/null 2>&1; then
    already_ready=false
  fi
done

if [[ "$already_ready" == "false" ]]; then
  set +e
  dnf --disablerepo='*' --enablerepo="$REPOS" \
    --setopt=install_weak_deps=False install -y "${PACKAGES[@]}" \
    > "$record_dir/dnf-install.log" 2>&1
  dnf_rc=$?
  set -e
  if [[ "$dnf_rc" -ne 0 ]]; then
    tail -n 120 "$record_dir/dnf-install.log" >&2 || true
    echo "SIGN_RENDER_DNF_INSTALL_FAILED rc=$dnf_rc record_dir=$record_dir" >&2
    exit "$dnf_rc"
  fi
  install_state="installed"
else
  printf '%s\n' "all requested packages were already installed" \
    > "$record_dir/dnf-install.log"
  install_state="already_present"
fi

fc-cache -f > "$record_dir/fc-cache.log" 2>&1

rpm -qa --qf '%{NAME}|%{ARCH}|%{EPOCHNUM}:%{VERSION}-%{RELEASE}\n' \
  | sort > "$record_dir/rpm-after.txt"
comm -13 "$record_dir/rpm-before.txt" "$record_dir/rpm-after.txt" \
  > "$record_dir/rpm-added.txt"
dnf history list > "$record_dir/dnf-history-after.txt"
dnf history info last > "$record_dir/dnf-history-last.txt"
df -B1 / > "$record_dir/disk-after.txt"
free -b > "$record_dir/memory-after.txt"

for package in "${PACKAGES[@]}"; do
  rpm -q "$package"
done
libreoffice --version | head -n 1
fc-match -f 'noto_cjk_family=%{family};file=%{file}\n' 'Noto Sans CJK SC' | head -n 1
pdfinfo -v 2>&1 | head -n 1

if ! fc-match -f '%{family}\n' 'Noto Sans CJK SC' | head -n 1 \
  | grep -Fq 'Noto Sans CJK SC'; then
  echo "NOTO_CJK_MATCH_FAILED record_dir=$record_dir" >&2
  exit 20
fi

if [[ "$(systemctl is-active erp-new@oa.service)" != "active" ]]; then
  echo "OA_SERVICE_NOT_ACTIVE record_dir=$record_dir" >&2
  exit 21
fi

transaction_id="$(awk '$1 ~ /^[0-9]+$/ {print $1; exit}' \
  "$record_dir/dnf-history-after.txt")"
printf 'install_state=%s\n' "$install_state"
printf 'record_dir=%s\n' "$record_dir"
printf 'latest_dnf_transaction_id=%s\n' "${transaction_id:-unknown}"
printf 'added_packages=%s\n' "$(wc -l < "$record_dir/rpm-added.txt" | tr -d ' ')"
printf 'root_available_bytes=%s\n' "$(df -B1 --output=avail / | tail -n 1 | tr -d ' ')"
echo "SIGN_RENDER_RUNTIME_INSTALL_OK"
