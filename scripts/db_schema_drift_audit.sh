#!/usr/bin/env bash
set -euo pipefail

DB="${DB:-BossERP_NEW}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mysql_args=(-u"${MYSQL_USER}" --batch --raw --skip-column-names)
if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  mysql_args+=("-p${MYSQL_PASSWORD}")
fi

mysql_db() {
  "${MYSQL_BIN}" "${mysql_args[@]}" "${DB}" "$@"
}

echo "database=${DB}"
mysql_db -e "select concat('server=', @@version), concat('lower_case_table_names=', @@lower_case_table_names), concat('current_database=', database());"
echo

echo "[critical_schema_drift]"
mysql_db <<'SQL'
SELECT 'missing_table' AS issue, e.table_name AS detail
FROM (
    SELECT 'inv_oe_category' AS table_name UNION ALL
    SELECT 'inv_oe_item' UNION ALL
    SELECT 'inv_gift_category' UNION ALL
    SELECT 'inv_gift_box' UNION ALL
    SELECT 'oa_sign_plan' UNION ALL
    SELECT 'oa_sign_plan_template'
) e
LEFT JOIN information_schema.TABLES t
       ON t.TABLE_SCHEMA = DATABASE()
      AND t.TABLE_NAME = e.table_name
WHERE t.TABLE_NAME IS NULL
UNION ALL
SELECT 'missing_column', CONCAT(e.table_name, '.', e.column_name)
FROM (
    SELECT 'oa_fixed_asset_config' AS table_name, 'oe_item_id' AS column_name UNION ALL
    SELECT 'oa_fixed_asset_repair', 'oe_item_id' UNION ALL
    SELECT 'oa_fixed_asset_repair', 'oe_item_name' UNION ALL
    SELECT 'oa_sign_package', 'source_plan_id' UNION ALL
    SELECT 'oa_sign_package', 'source_plan_name'
) e
LEFT JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA = DATABASE()
      AND c.TABLE_NAME = e.table_name
      AND c.COLUMN_NAME = e.column_name
WHERE c.COLUMN_NAME IS NULL
UNION ALL
SELECT 'legacy_column_still_present', CONCAT(e.table_name, '.', e.column_name)
FROM (
    SELECT 'oa_fixed_asset_config' AS table_name, 'product_id' AS column_name UNION ALL
    SELECT 'oa_fixed_asset_repair', 'product_id' UNION ALL
    SELECT 'oa_fixed_asset_repair', 'product_name'
) e
JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA = DATABASE()
      AND c.TABLE_NAME = e.table_name
      AND c.COLUMN_NAME = e.column_name
UNION ALL
SELECT 'missing_index', 'oa_fixed_asset_config.idx_oa_fixed_asset_config_oe'
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'oa_fixed_asset_config'
      AND INDEX_NAME = 'idx_oa_fixed_asset_config_oe'
)
UNION ALL
SELECT 'missing_index', 'oa_fixed_asset_repair.idx_oa_fixed_asset_repair_oe'
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'oa_fixed_asset_repair'
      AND INDEX_NAME = 'idx_oa_fixed_asset_repair_oe'
)
UNION ALL
SELECT 'missing_index', 'oa_sign_package.idx_oa_sign_package_employee_plan_status'
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'oa_sign_package'
      AND INDEX_NAME = 'idx_oa_sign_package_employee_plan_status'
)
ORDER BY issue, detail;
SQL

echo
echo "[mapper_missing_tables]"
ROOT_DIR="${ROOT_DIR}" DB="${DB}" MYSQL_BIN="${MYSQL_BIN}" MYSQL_USER="${MYSQL_USER}" MYSQL_PASSWORD="${MYSQL_PASSWORD:-}" python3 - <<'PY'
from pathlib import Path
import os
import re
import subprocess

root = Path(os.environ["ROOT_DIR"])
try:
    paths = subprocess.check_output(
        ["rg", "--files", "-g", "*Mapper.xml", "erp-modules", "erp-auth", "erp-gateway", "erp-visual", "erp-api"],
        cwd=root,
        text=True,
    ).splitlines()
except Exception:
    paths = [str(p.relative_to(root)) for p in root.glob("erp-modules/**/src/main/resources/mapper/**/*Mapper.xml")]

pattern = re.compile(r"\b(?:from|join|update|into)\s+`?([a-zA-Z_][\w]*)`?", re.I)
ignore = {"current_seq", "id", "information_schema", "select", "where", "set", "values", "dual"}
refs = {}
for rel in paths:
    text = (root / rel).read_text(errors="ignore")
    text = re.sub(r"<!--.*?-->", " ", text, flags=re.S)
    for match in pattern.finditer(text):
        table = match.group(1)
        if table.lower() in ignore:
            continue
        refs.setdefault(table, set()).add(rel)

cmd = [
    os.environ.get("MYSQL_BIN", "mysql"),
    "-u" + os.environ.get("MYSQL_USER", "root"),
    "--batch",
    "--raw",
    "--skip-column-names",
    os.environ["DB"],
    "-e",
    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()",
]
env = os.environ.copy()
if env.get("MYSQL_PASSWORD"):
    env["MYSQL_PWD"] = env["MYSQL_PASSWORD"]
tables = set(subprocess.check_output(cmd, text=True, env=env).splitlines())
missing = sorted(table for table in refs if table not in tables)
if not missing:
    print("ok")
else:
    for table in missing:
        print(f"{table}\t{';'.join(sorted(refs[table])[:6])}")
PY

echo
echo "[row_counts]"
mysql_db <<'SQL'
SELECT 'oa_fixed_asset_config', COUNT(*) FROM oa_fixed_asset_config
UNION ALL SELECT 'oa_fixed_asset_repair', COUNT(*) FROM oa_fixed_asset_repair
UNION ALL SELECT 'oa_fixed_asset_quota', COUNT(*) FROM oa_fixed_asset_quota
UNION ALL SELECT 'oa_fixed_asset_quota_ledger', COUNT(*) FROM oa_fixed_asset_quota_ledger
UNION ALL SELECT 'oa_sign_package', COUNT(*) FROM oa_sign_package
UNION ALL SELECT 'sys_menu', COUNT(*) FROM sys_menu;
SQL
