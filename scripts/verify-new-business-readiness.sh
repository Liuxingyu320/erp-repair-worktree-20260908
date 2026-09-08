#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_SQL="$ROOT_DIR/scripts/new-business-permission-readiness.sql"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

[[ -f "$REPORT_SQL" ]] || fail "missing readiness report: $REPORT_SQL"

if grep -Eiq '^[[:space:]]*(insert|update|delete|replace|alter|create|drop|truncate|call|set)[[:space:]]' "$REPORT_SQL"; then
  fail "readiness SQL must remain strictly read-only"
fi

for required_token in \
  STORE_EMPLOYEE_MISSING_PERMISSION \
  STORE_LEADER_MISSING_PERMISSION \
  WAREHOUSE_EMPLOYEE_MISSING_PERMISSION \
  HIGH_RISK_ROLE_GRANT_REVIEW \
  OE_MASTER_DATA_NOT_READY \
  LEADER_IDENTITY_NOT_READY; do
  grep -q "$required_token" "$REPORT_SQL" \
    || fail "readiness SQL is missing section: $required_token"
done

if grep -Eq 'hr:team|oeReplenishment|fixedAsset:outbox' "$REPORT_SQL"; then
  fail "readiness SQL still references retired permissions"
fi

if [[ "${1:-}" != "--run" ]]; then
  echo "[PASS] permission and OE readiness report is present and read-only"
  exit 0
fi

allowed_hosts="${INVENTORY_OE_PURCHASE_REFERENCE_ALLOWED_HOSTS:-}"
[[ -n "$allowed_hosts" ]] \
  || fail "INVENTORY_OE_PURCHASE_REFERENCE_ALLOWED_HOSTS is required"

IFS=',' read -r -a host_values <<< "$allowed_hosts"
for raw_host in "${host_values[@]}"; do
  host="${raw_host//[[:space:]]/}"
  [[ -n "$host" ]] || fail "trusted host list contains a blank entry"
  [[ "$host" != *"://"* && "$host" != */* && "$host" != *:* ]] \
    || fail "trusted host must be a hostname without scheme, path or port: $raw_host"
  [[ "$host" =~ ^([A-Za-z0-9-]+\.)*[A-Za-z0-9-]+$ ]] \
    || fail "invalid trusted hostname: $raw_host"
done

mysql_bin="${ERP_NEW_BUSINESS_MYSQL_BIN:-mysql}"
command -v "$mysql_bin" >/dev/null 2>&1 \
  || fail "mysql client is unavailable: $mysql_bin"

database="${ERP_NEW_BUSINESS_MYSQL_DATABASE:-}"
[[ -n "$database" ]] || fail "ERP_NEW_BUSINESS_MYSQL_DATABASE is required"

mysql_args=(--show-warnings --table --database "$database")
[[ -z "${ERP_NEW_BUSINESS_MYSQL_HOST:-}" ]] \
  || mysql_args+=(--host "$ERP_NEW_BUSINESS_MYSQL_HOST")
[[ -z "${ERP_NEW_BUSINESS_MYSQL_PORT:-}" ]] \
  || mysql_args+=(--port "$ERP_NEW_BUSINESS_MYSQL_PORT")
[[ -z "${ERP_NEW_BUSINESS_MYSQL_USER:-}" ]] \
  || mysql_args+=(--user "$ERP_NEW_BUSINESS_MYSQL_USER")

echo "[INFO] trusted OE purchase-reference hosts: $allowed_hosts"
echo "[INFO] every returned readiness row requires remediation or explicit approval"
"$mysql_bin" "${mysql_args[@]}" < "$REPORT_SQL"
