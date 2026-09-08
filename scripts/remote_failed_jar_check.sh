#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_ROOT="${ERP_REMOTE_ROOT:-/opt/erp-new}"
MANIFEST="${ERP_RELEASE_MANIFEST:-$REMOTE_ROOT/release/system-management-release-20260714.json}"

python3 "$SCRIPT_DIR/verify_remote_system_management_release.py" \
    --root "$REMOTE_ROOT" --manifest "$MANIFEST" --print-sha

printf 'REMOTE_EXECUTABLE_JARS_VERIFIED manifest=%s\n' \
    "system-management-release-20260714.json"
