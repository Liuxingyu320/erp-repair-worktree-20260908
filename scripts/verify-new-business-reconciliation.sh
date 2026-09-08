#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_SQL="$ROOT_DIR/scripts/new-business-daily-reconciliation.sql"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

[[ -f "$REPORT_SQL" ]] || fail "missing reconciliation report: $REPORT_SQL"

if grep -Eiq '^[[:space:]]*(insert|update|delete|replace|alter|create|drop|truncate|call|set|prepare|execute|deallocate|lock|unlock)[[:space:]]' "$REPORT_SQL"; then
  fail "reconciliation SQL must remain strictly read-only"
fi

for required_token in \
  STORE_RETURN_DIRECTION_OR_APPROVAL_INVALID \
  STORE_RETURN_SHIPMENT_QUANTITY_CONSERVATION \
  TRANSFER_DISCREPANCY_MASTER_DETAIL_MISMATCH \
  TRANSFER_DISCREPANCY_OVERDUE_24H \
  CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH \
  CUSTOMER_SERVICE_RECORD_AUDIT_MISSING \
  HEALTH_CERTIFICATE_CURRENT_INVALID \
  NEW_BUSINESS_RECONCILIATION_SUMMARY; do
  grep -q "$required_token" "$REPORT_SQL" \
    || fail "reconciliation SQL is missing section: $required_token"
done

grep -q 'sd.shipped_quantity - sd.received_quantity' "$REPORT_SQL" \
  || fail "store-return shipment conservation check is missing"

if grep -Eq 'OA_OUTBOX|OE_DEMAND|oa_fixed_asset_replenishment_outbox|inv_oe_replenishment_demand' "$REPORT_SQL"; then
  fail "reconciliation SQL must not monitor retired OE replenishment runtime tables"
fi

if [[ "${1:-}" != "--run" ]]; then
  echo "[PASS] new-business daily reconciliation is present and strictly read-only"
  exit 0
fi

exec "$ROOT_DIR/scripts/run-new-business-reconciliation.sh"
