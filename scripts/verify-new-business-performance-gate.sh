#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

[[ -n "${ERP_NEW_BUSINESS_UAT_EVIDENCE:-}" ]] \
  || fail 'ERP_NEW_BUSINESS_UAT_EVIDENCE is required'
[[ -n "${ERP_NEW_BUSINESS_GRAY_EVIDENCE:-}" ]] \
  || fail 'ERP_NEW_BUSINESS_GRAY_EVIDENCE is required'
[[ -n "${ERP_NEW_BUSINESS_PERFORMANCE_EVIDENCE:-}" ]] \
  || fail 'ERP_NEW_BUSINESS_PERFORMANCE_EVIDENCE is required'

commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
"$ROOT_DIR/scripts/verify-new-business-gray-gate.sh"
python3 "$ROOT_DIR/scripts/verify_new_business_performance_evidence.py" \
  --root "$ROOT_DIR" \
  --candidate-commit "$commit" \
  --evidence "$ERP_NEW_BUSINESS_PERFORMANCE_EVIDENCE"

echo '[PASS] new-business post-gray performance gate completed'
