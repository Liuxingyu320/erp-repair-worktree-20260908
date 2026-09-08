#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RULES="$ROOT_DIR/ops/monitoring/new-business-alert-rules.yml"
RUNNER="$ROOT_DIR/scripts/run-new-business-reconciliation.sh"
REQUIRE_PROMTOOL=false

usage() {
  echo 'Usage: verify-new-business-monitoring.sh [--require-promtool]'
}

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

case "$#" in
  0)
    ;;
  1)
    case "$1" in
      --require-promtool)
        REQUIRE_PROMTOOL=true
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        usage >&2
        fail "unknown option: $1"
        ;;
    esac
    ;;
  *)
    usage >&2
    fail 'only one option is supported'
    ;;
esac

for file in "$RULES" "$RUNNER"; do
  [[ -f "$file" ]] || fail "missing monitoring contract file: $file"
done

for alert in \
  ErpNewBusinessCustomerScopeDeniedSpike \
  ErpNewBusinessCustomerOptimisticConflictSpike \
  ErpNewBusinessHealthReminderFailure \
  ErpNewBusinessReconciliationFailed \
  ErpNewBusinessReconciliationStale \
  ErpNewBusinessTransferDiscrepancyOverdue24h; do
  grep -Fq -- "- alert: $alert" "$RULES" \
    || fail "missing active alert: $alert"
done

alert_count="$(grep -c '^[[:space:]]*- alert:' "$RULES")"
[[ "$alert_count" == "6" ]] \
  || fail "active monitoring contract must contain 6 alerts"

if grep -Eiq 'oe[_-](outbox|demand|replenishment)|fixed.asset.replenishment' "$RULES"; then
  fail 'active monitoring rules still reference retired OE replenishment'
fi

grep -Fq 'ERP_NEW_BUSINESS_TEXTFILE_DIR' "$RUNNER" \
  || fail 'reconciliation runner does not publish textfile metrics'
grep -Fq -- '--defaults-extra-file=' "$RUNNER" \
  || fail 'reconciliation runner must use an owner-only MySQL options file'
grep -Fq 'SET SESSION TRANSACTION READ ONLY' "$RUNNER" \
  || fail 'reconciliation runner must start a read-only session'

if grep -Eiq '(password|passwd|access.?key|secret)[[:space:]]*:' "$RULES"; then
  fail 'monitoring rules must not contain credentials'
fi

if command -v promtool >/dev/null 2>&1; then
  promtool check rules "$RULES"
elif [[ "$REQUIRE_PROMTOOL" == true ]]; then
  fail 'promtool is required but unavailable'
else
  echo '[INFO] promtool is unavailable; install-time validation remains mandatory'
fi

echo '[PASS] active monitoring contract: 6 alerts'
