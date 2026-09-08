#!/usr/bin/env bash

# Static, non-mutating gate for contract-signing-consistency-20260720.
# This script never applies migrations, registers templates, signs contracts,
# deletes tasks, or claims external approvals.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---static}"

usage()
{
    cat <<'EOF'
Usage: bash scripts/verify-contract-signing-consistency-release.sh --static

Validates the new 20260720 source contract, eight ordered migration hashes and
three-way mirrors, portable disabled-v6 manifest, pinned render-QA evidence,
and hash-pinned isolated synthetic-browser UAT evidence.
It performs no database, template, signing, deletion, or production mutation.
EOF
}

case "$MODE" in
    --static) ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac
[[ "$#" -le 1 ]] || { usage >&2; exit 2; }

command -v python3 >/dev/null 2>&1 || {
    echo '[FAIL] python3 is required' >&2
    exit 3
}

python3 -m py_compile \
    "$ROOT_DIR/scripts/contract/build_beijing_sign_safe_templates_v6_20260720.py" \
    "$ROOT_DIR/scripts/contract/stamp_pdf_page_numbers_20260720.py" \
    "$ROOT_DIR/scripts/contract/verify_beijing_sign_v6_render_qa_20260720.py" \
    "$ROOT_DIR/scripts/test_contract_signing_consistency_release_20260720.py"
python3 "$ROOT_DIR/scripts/test_contract_signing_consistency_release_20260720.py"
python3 "$ROOT_DIR/scripts/test_prepare_onboard_templates_20260718.py"
python3 \
    "$ROOT_DIR/scripts/contract/verify_beijing_sign_v6_render_qa_20260720.py" \
    --check
bash "$ROOT_DIR/scripts/verify-docker-mysql-bootstrap.sh"
bash -n "$ROOT_DIR/scripts/verify-contract-signing-consistency-release.sh"

echo 'CONTRACT_SIGNING_CONSISTENCY_RELEASE_STATIC_OK migrations=8 sql_mirrors=3 pdfs=18 pages=112 synthetic_browser_uat=passed real_dual_flow_signed_uat=pending mysql57=not-applicable-version-verified local_mysql80_replay=passed production_mutation=false external_gates=pending'
