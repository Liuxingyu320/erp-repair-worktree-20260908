#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

echo "INSTALL_PREFLIGHT_BEGIN"
echo "host=$(hostname)"
if [[ -r /etc/os-release ]]; then
  . /etc/os-release
  echo "os_id=${ID:-unknown}"
  echo "os_version=${VERSION_ID:-unknown}"
  echo "os_pretty=${PRETTY_NAME:-unknown}"
fi

echo "PACKAGE_MANAGER_BEGIN"
for tool in dnf yum rpm; do
  path="$(command -v "$tool" 2>/dev/null || true)"
  echo "tool=$tool path=${path:-MISSING}"
done
if command -v dnf >/dev/null 2>&1; then
  dnf --version 2>/dev/null | head -n 2 | sed 's/^/dnf_version=/' || true
  dnf -q repolist --enabled 2>/dev/null | sed 's/^/repo=/' || true
fi
echo "PACKAGE_MANAGER_END"

echo "RESOURCE_BEGIN"
free -b | sed 's/^/memory=/' || true
if command -v swapon >/dev/null 2>&1; then
  swapon --show --bytes --noheadings 2>/dev/null | sed 's/^/swap=/' || true
fi
df -B1 --output=source,fstype,size,used,avail,pcent,target / /tmp /opt/erp-new-data 2>/dev/null \
  | awk '!seen[$0]++ {print "disk=" $0}' || true
df -i --output=source,itotal,iused,iavail,ipcent,target / /tmp /opt/erp-new-data 2>/dev/null \
  | awk '!seen[$0]++ {print "inode=" $0}' || true
echo "tmp_mode=$(stat -c '%a:%U:%G' /tmp)"
echo "selinux=$(getenforce 2>/dev/null || echo UNKNOWN)"
oa_pid="$(systemctl show erp-new@oa.service -p MainPID --value 2>/dev/null || true)"
if [[ "$oa_pid" =~ ^[1-9][0-9]*$ ]]; then
  ps -o pid=,rss=,vsz=,etimes=,comm= -p "$oa_pid" | sed 's/^/oa_process=/' || true
  tr '\0' '\n' < "/proc/$oa_pid/cmdline" 2>/dev/null \
    | sed -n '1,12p' | sed 's/^/oa_argv=/' || true
fi
echo "RESOURCE_END"

echo "AVAILABLE_PACKAGE_QUERY_BEGIN"
if ! command -v dnf >/dev/null 2>&1; then
  echo "DNF_MISSING"
  exit 10
fi

query_pattern() {
  local pattern="$1"
  echo "QUERY_PATTERN|$pattern"
  dnf -q --cacheonly repoquery --available --latest-limit 1 \
    --qf 'PKG|%{name}|%{arch}|%{evr}|repo=%{reponame}|download=%{downloadsize}|install=%{installsize}' \
    "$pattern" 2>&1 || true
}

query_pattern 'libreoffice*'
query_pattern '*noto*cjk*'
query_pattern 'poppler*'
echo "AVAILABLE_PACKAGE_QUERY_END"

echo "INSTALLED_RELEVANT_BEGIN"
rpm -qa --qf 'INSTALLED|%{name}|%{arch}|%{evr}|install=%{size}\n' \
  | grep -Ei '^(INSTALLED\|)(fontconfig|libreoffice|poppler|google-noto|noto-)' \
  | sort || true
echo "INSTALLED_RELEVANT_END"

echo "CACHE_SIZE_BEGIN"
du -sh /var/cache/dnf 2>/dev/null | sed 's/^/dnf_cache=/' || true
echo "CACHE_SIZE_END"
echo "INSTALL_PREFLIGHT_OK"
