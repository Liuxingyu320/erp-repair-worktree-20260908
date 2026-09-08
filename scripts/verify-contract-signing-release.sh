#!/usr/bin/env bash

# Release A source gate. Static mode proves repository contracts but deliberately
# does not claim that the external native-MySQL-5.7 or signed UAT gates passed.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---static}"

usage()
{
    cat <<'EOF'
Usage: bash scripts/verify-contract-signing-release.sh [--static|--readiness]

  --static     Validate source allowlist, ordered/hash-pinned migration pairs,
               legacy role boundaries, fail-closed flags, Docker bootstrap
               integrity, menu ownership, and focused frontend contracts.
  --readiness  Run the static gate, then require a finalized ready manifest and
               externally completed native-MySQL-5.7 gate. The checked-in
               development manifest intentionally blocks this mode today.
EOF
}

case "$MODE" in
    --static|--readiness) ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac
[[ "$#" -le 1 ]] || { usage >&2; exit 2; }

command -v python3 >/dev/null 2>&1 || {
    echo '[FAIL] python3 is required' >&2
    exit 3
}

python3 "$ROOT_DIR/scripts/test_contract_signing_release_contract.py"
python3 "$ROOT_DIR/scripts/test_contract_signing_uat.py"
python3 "$ROOT_DIR/scripts/contract/verify_contract_signing_scope.py" \
    --root "$ROOT_DIR" \
    --scope "$ROOT_DIR/docs/releases/20260716-contract-signing-release-scope.md" \
    --mode static
python3 "$ROOT_DIR/scripts/contract/verify_menu_id_ownership.py" \
    --mode release \
    --allowlist "$ROOT_DIR/scripts/contract/menu_id_conflicts_20260716.json" \
    "$ROOT_DIR/sql" "$ROOT_DIR/docker/mysql/db" >/dev/null
echo '[PASS] menu ID ownership conflicts match the exact reviewed allowlist'
bash "$ROOT_DIR/scripts/verify-docker-mysql-bootstrap.sh"
bash -n "$ROOT_DIR/scripts/verify-contract-signing-release.sh"

NODE_BIN="${NODE_BIN:-$(command -v node || true)}"
[[ -n "$NODE_BIN" ]] || {
    echo '[FAIL] node is required for focused frontend release contracts' >&2
    exit 4
}

for test_file in \
    signTaskCenter.test.js \
    signPackageModule.test.js \
    signDeadlineUx.test.js \
    signPackageRefusal.test.js \
    signTaskExceptionResolution.test.js \
    signManualClosure.test.js \
    signCompanySealManagement.test.js \
    unifiedTodoMutationRefresh.test.js
do
    "$NODE_BIN" "$ROOT_DIR/erp-ui/test/$test_file"
done

if [[ "$MODE" == '--readiness' ]]; then
    python3 - "$ROOT_DIR/scripts/contract-signing-release-20260716.json" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
blockers = []
if manifest.get("status") != "ready":
    blockers.append("release manifest status is not ready")
mysql57 = manifest.get("externalGates", {}).get("nativeMySql57", {})
if mysql57.get("status") not in {"passed", "not-applicable-version-verified"}:
    blockers.append("native MySQL 5.7 external gate is not passed")
signed_uat = manifest.get("externalGates", {}).get("signedUat", {})
if signed_uat.get("status") != "passed":
    blockers.append("signed UAT/legal/business approval gate is not passed")
if blockers:
    for blocker in blockers:
        print(f"[BLOCKED] {blocker}", file=sys.stderr)
    print(
        "[BLOCKED] static verification cannot substitute for external native-MySQL-5.7/UAT approval",
        file=sys.stderr,
    )
    raise SystemExit(5)
print("CONTRACT_SIGNING_RELEASE_READINESS_OK")
PY
fi

echo "CONTRACT_SIGNING_RELEASE_STATIC_OK migrations=4 legacy_package=preserved package_only_9650=forbidden native_mysql57=external-pending"
