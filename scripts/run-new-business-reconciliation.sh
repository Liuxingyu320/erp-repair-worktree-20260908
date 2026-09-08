#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT_DIR/scripts/new-business-daily-reconciliation.sql"
MYSQL_BIN="${ERP_NEW_BUSINESS_MYSQL_BIN:-mysql}"
DEFAULTS_FILE="${ERP_NEW_BUSINESS_MYSQL_DEFAULTS_FILE:-}"
DATABASE="${ERP_NEW_BUSINESS_MYSQL_DATABASE:-}"

fail() {
  echo "[FAIL] $*" >&2
  exit 2
}

command -v "$MYSQL_BIN" >/dev/null 2>&1 \
  || fail "mysql client is unavailable: $MYSQL_BIN"
[[ "$DATABASE" =~ ^[A-Za-z0-9_]{1,64}$ ]] \
  || fail "ERP_NEW_BUSINESS_MYSQL_DATABASE must be an explicit safe database name"
[[ -n "$DEFAULTS_FILE" ]] \
  || fail "ERP_NEW_BUSINESS_MYSQL_DEFAULTS_FILE is required (owner-only MySQL client options)"

DEFAULTS_FILE="$(python3 - "$DEFAULTS_FILE" <<'PY'
import os
import stat
import sys
from pathlib import Path

path = Path(sys.argv[1]).expanduser()
if not path.is_absolute():
    raise SystemExit("[FAIL] MySQL defaults file must use an absolute path")
try:
    resolved = path.resolve(strict=True)
except FileNotFoundError:
    raise SystemExit("[FAIL] MySQL defaults file does not exist")
if not resolved.is_file() or resolved.is_symlink():
    raise SystemExit("[FAIL] MySQL defaults file must be a regular non-symlink file")
if stat.S_IMODE(resolved.stat().st_mode) & 0o077:
    raise SystemExit("[FAIL] MySQL defaults file must be owner-only (chmod 600)")
print(resolved)
PY
)"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_DIR="${ERP_NEW_BUSINESS_RECONCILIATION_OUTPUT_DIR:-$ROOT_DIR/output/new-business-reconciliation/$stamp}"
mkdir -p "$OUTPUT_DIR"
SUMMARY="$OUTPUT_DIR/summary.json"
RAW="$(mktemp)"
trap 'rm -f "$RAW"' EXIT

prometheus_args=()
if [[ -n "${ERP_NEW_BUSINESS_TEXTFILE_DIR:-}" ]]; then
  [[ -d "$ERP_NEW_BUSINESS_TEXTFILE_DIR" && -w "$ERP_NEW_BUSINESS_TEXTFILE_DIR" ]] \
    || fail "ERP_NEW_BUSINESS_TEXTFILE_DIR must be an existing writable directory"
  prometheus_args=(
    --prometheus-output
    "$ERP_NEW_BUSINESS_TEXTFILE_DIR/erp_new_business_reconciliation.prom"
  )
fi

echo "[INFO] running all reconciliation queries in a read-only MySQL session"
"$MYSQL_BIN" \
  "--defaults-extra-file=$DEFAULTS_FILE" \
  --batch --raw --skip-column-names --show-warnings --connect-timeout=10 \
  --init-command="SET SESSION TRANSACTION READ ONLY" \
  --database "$DATABASE" < "$SQL" > "$RAW"

set +e
python3 "$ROOT_DIR/scripts/parse_new_business_reconciliation.py" \
  --sql "$SQL" --raw "$RAW" --output "$SUMMARY" \
  "${prometheus_args[@]}"
result=$?
set -e
if (( result == 1 )); then
  echo "[INFO] raw rows were deleted; only issue counts were retained" >&2
fi
exit "$result"
