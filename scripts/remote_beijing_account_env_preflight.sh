#!/usr/bin/env bash
set -euo pipefail

echo "host=$(hostname)"
echo "time=$(date -u +%Y-%m-%dT%H:%M:%SZ)"

for candidate in /root/.erp-mysql-root-pass /opt/erp /opt/erp/current /root/ERP-NEW /root/erp-new /tmp; do
  if [[ -e "$candidate" ]]; then
    printf 'exists=%s\n' "$candidate"
  fi
done

find /root /opt /tmp -maxdepth 5 -type f \
  \( -name '北京区域0716-2.xlsx' -o -name 'reconcile_beijing_0716_employees.py' \) \
  -printf 'file=%p\n' 2>/dev/null | head -n 20

if command -v python3 >/dev/null 2>&1; then
  python3 - <<'PY'
import importlib.util
print("python3=present")
for name in ("openpyxl", "bcrypt"):
    print(f"python_module_{name}={'present' if importlib.util.find_spec(name) else 'missing'}")
PY
fi

if command -v mysql >/dev/null 2>&1; then
  echo "mysql_client=present"
else
  echo "mysql_client=missing"
fi

for unit in erp-gateway erp-auth erp-system erp-oa; do
  if systemctl list-unit-files "${unit}.service" --no-legend 2>/dev/null | grep -q "${unit}.service"; then
    printf 'service=%s:%s\n' "$unit" "$(systemctl is-active "${unit}.service" 2>/dev/null || true)"
  fi
done
