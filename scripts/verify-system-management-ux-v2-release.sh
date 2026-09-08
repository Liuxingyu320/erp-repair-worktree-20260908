#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
FILE_LIST="$ROOT_DIR/scripts/system-management-ux-v2-release-files-20260714.list"
MANIFEST="$ROOT_DIR/scripts/system-management-ux-v2-release-20260714.json"
MODE=${1:---source}

case "$MODE" in
    --source|--native-mysql) ;;
    -h|--help)
        echo "Usage: sh scripts/verify-system-management-ux-v2-release.sh [--source|--native-mysql]"
        exit 0
        ;;
    *)
        echo "unsupported verification mode: $MODE" >&2
        exit 2
        ;;
esac
[ "$#" -le 1 ] || { echo "too many arguments" >&2; exit 2; }

python3 - "$ROOT_DIR" "$FILE_LIST" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
file_list = Path(sys.argv[2]).resolve()
entries = [
    line.strip() for line in file_list.read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
if not entries or len(entries) != len(set(entries)):
    raise SystemExit("Release B file list must be non-empty and unique")
if any(Path(entry).is_absolute() or ".." in Path(entry).parts for entry in entries):
    raise SystemExit("Release B file list contains an unsafe path")
missing = [entry for entry in entries if not (root / entry).is_file()]
if missing:
    raise SystemExit("Release B file list contains missing files: " + ", ".join(missing))
required = {
    "scripts/system-management-ux-v2-release-20260714.json",
    "scripts/system-management-ux-v2-migrations-20260714.list",
    "scripts/verify-system-management-ux-v2-native-mysql.sh",
    "sql/erp_system_management_ux_v2_20260714.sql",
    "sql/erp_system_management_ux_v2_rollback_20260714.sql",
    "erp-ui/src/views/system/user/index.vue",
    "erp-ui/src/views/system/shop/index.vue",
    "erp-ui/src/views/system/role/components/RoleWizard.vue",
    "erp-ui/src/mixins/pendingSortGuard.js",
    "erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/SysSortChangeRequest.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysSortChangeSupport.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysUserShopScopePreviewVo.java",
}
if not required.issubset(entries):
    raise SystemExit("Release B file list misses required files")
print(f"SYSTEM_MANAGEMENT_UX_V2_SOURCE_LIST_OK files={len(entries)}")
PY

PYTHONPATH="$ROOT_DIR/scripts" python3 - "$MANIFEST" <<'PY'
import sys
from pathlib import Path
from release_migration_contract import load_and_validate_manifest

manifest = load_and_validate_manifest(Path(sys.argv[1]))
if manifest.get("releaseId") != "system-management-ux-v2-20260714":
    raise SystemExit("unexpected Release B releaseId")
if manifest.get("executionPolicy") != "automatic":
    raise SystemExit("Release B feature-flag migration must be automatic")
print("SYSTEM_MANAGEMENT_UX_V2_MANIFEST_OK")
PY

python3 "$ROOT_DIR/scripts/test_system_management_ux_v2_release_contract.py"
python3 "$ROOT_DIR/scripts/test_system_management_browser_contract.py"
bash -n "$ROOT_DIR/scripts/verify-system-management-ux-v2-native-mysql.sh"
sh -n "$ROOT_DIR/docker/copy.sh"

NODE_BIN=${NODE_BIN:-$(command -v node || true)}
[ -n "$NODE_BIN" ] || { echo "node is required" >&2; exit 3; }
"$NODE_BIN" "$ROOT_DIR/erp-ui/test/systemManagementUxV2.test.js"
"$NODE_BIN" "$ROOT_DIR/erp-ui/test/userShopNavigationUx.test.js"
"$NODE_BIN" "$ROOT_DIR/erp-ui/test/userShopScopeUx.test.js"
"$NODE_BIN" "$ROOT_DIR/erp-ui/test/roleAndSortUx.test.js"

if [ "$MODE" = "--native-mysql" ]; then
    bash "$ROOT_DIR/scripts/verify-system-management-ux-v2-native-mysql.sh"
fi

echo "SYSTEM_MANAGEMENT_UX_V2_RELEASE_SOURCE_OK native_only=true mode=$MODE"
