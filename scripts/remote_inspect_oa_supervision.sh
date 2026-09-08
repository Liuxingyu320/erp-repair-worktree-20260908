#!/usr/bin/env bash
set -euo pipefail

pid="$(
  for candidate in $(pgrep -x java || true); do
    command="$(tr '\0' ' ' < "/proc/$candidate/cmdline" 2>/dev/null || true)"
    if printf '%s' "$command" | grep -q '/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar'; then
      echo "$candidate"
      break
    fi
  done
)"
[[ -n "$pid" ]]

echo "PROCESS"
ps -o pid=,ppid=,lstart=,cmd= -p "$pid"
ppid="$(ps -o ppid= -p "$pid" | tr -d ' ')"
ps -o pid=,ppid=,lstart=,cmd= -p "$ppid" || true

echo "CGROUP"
cat "/proc/$pid/cgroup" || true

echo "SYSTEMD_CANDIDATES"
systemctl list-units --type=service --all --no-legend |
  grep -Ei 'erp|oa|java' | sed -n '1,80p' || true

echo "START_SCRIPTS"
find /opt/erp-new /www/server/panel -maxdepth 5 -type f \
  \( -name '*.service' -o -name '*.sh' -o -name '*.json' \) 2>/dev/null |
  xargs grep -Il '/opt/erp-new/erp/modules/oa/jar/erp-modules-oa.jar' 2>/dev/null |
  sed -n '1,40p' || true
