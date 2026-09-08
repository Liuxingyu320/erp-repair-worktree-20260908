#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
MANIFEST="$ROOT_DIR/scripts/system-management-release-20260714.json"
FILE_LIST="$ROOT_DIR/scripts/system-management-release-files-20260714.list"
MODE=source

usage() {
    echo "Usage: sh scripts/verify-system-management-release.sh [--source|--native-full]"
}

case "${1:---source}" in
    --source) MODE=source ;;
    --native-full) MODE=native-full ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac
[ "$#" -le 1 ] || { usage >&2; exit 2; }

python3 - "$MANIFEST" "$FILE_LIST" "$ROOT_DIR" <<'PY'
import json
import sys
from pathlib import Path

manifest_path = Path(sys.argv[1]).resolve()
file_list = Path(sys.argv[2]).resolve()
root = Path(sys.argv[3]).resolve()
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
if manifest.get("releaseId") != "system-management-20260714":
    raise SystemExit("unexpected system-management releaseId")
entries = [
    line.strip() for line in file_list.read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
if not entries or len(entries) != len(set(entries)):
    raise SystemExit("release file list must be non-empty and unique")
unsafe = [entry for entry in entries if Path(entry).is_absolute() or ".." in Path(entry).parts]
if unsafe:
    raise SystemExit("unsafe release file path: " + ", ".join(unsafe))
missing = [entry for entry in entries if not (root / entry).is_file()]
if missing:
    raise SystemExit("release file list contains missing files: " + ", ".join(missing))
required = {
    "scripts/system-management-release-20260714.json",
    "scripts/system-management-migrations-20260714.list",
    "scripts/finalize_system_management_release_manifest.py",
    "scripts/verify_system_management_release_contract.py",
    "scripts/verify-system-management-native-mysql.sh",
    "sql/erp_system_management_expand_20260714.sql",
    "sql/erp_system_management_permissions_20260714.sql",
    "sql/erp_system_management_finalize_20260714.sql",
}
missing_required = sorted(required - set(entries))
if missing_required:
    raise SystemExit("release file list misses required contract files: " + ", ".join(missing_required))
print(f"SYSTEM_MANAGEMENT_SOURCE_LIST_OK files={len(entries)}")
PY

python3 "$ROOT_DIR/scripts/verify_system_management_release_contract.py" --root "$ROOT_DIR"
PYTHONPATH="$ROOT_DIR/scripts" python3 "$ROOT_DIR/scripts/test_system_management_release_contract.py"
PYTHONPATH="$ROOT_DIR/scripts" python3 "$ROOT_DIR/scripts/test_system_management_native_mysql_contract.py"
PYTHONPATH="$ROOT_DIR/scripts" python3 "$ROOT_DIR/scripts/test_system_management_browser_contract.py"
PYTHONPATH="$ROOT_DIR/scripts" python3 "$ROOT_DIR/scripts/test_remote_system_management_release_contract.py"
PYTHONPATH="$ROOT_DIR/scripts" python3 "$ROOT_DIR/scripts/test_finalize_system_management_release_manifest.py"
python3 "$ROOT_DIR/scripts/test_release_migration_contract.py"
python3 "$ROOT_DIR/scripts/verify-audit-log-policy.py" --root "$ROOT_DIR"
python3 "$ROOT_DIR/scripts/test_verify_audit_log_policy.py"
python3 "$ROOT_DIR/scripts/test_classify_system_oper_log.py"
sh "$ROOT_DIR/scripts/verify-system-permission-alignment.sh"
python3 "$ROOT_DIR/scripts/test_verify_system_permission_alignment.py"
sh -n "$ROOT_DIR/docker/copy.sh"
bash -n "$ROOT_DIR/scripts/verify-system-management-native-mysql.sh"

NODE_BIN=${NODE_BIN:-$(command -v node || true)}
if [ -n "$NODE_BIN" ]; then
    "$NODE_BIN" "$ROOT_DIR/erp-ui/test/authSecurityHardening.test.js"
    "$NODE_BIN" "$ROOT_DIR/erp-ui/test/systemManagementUx.test.js"
    "$NODE_BIN" "$ROOT_DIR/erp-ui/test/systemBuildInfo.test.js"
else
    echo "node is required for system-management source verification" >&2
    exit 46
fi

if [ "$MODE" = "source" ]; then
    echo "SYSTEM_MANAGEMENT_RELEASE_SOURCE_OK native_only=true"
    exit 0
fi

for variable in DOCKER_HOST TESTCONTAINERS_HOST_OVERRIDE TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE; do
    eval "present=\${$variable+x}"
    if [ "$present" = x ]; then
        echo "$variable must be unset for the native-only release gate" >&2
        exit 47
    fi
done

if rg -l 'org\.testcontainers' "$ROOT_DIR" \
        --glob '*Test.java' --glob '!**/*IT.java' --glob '!**/target/**' \
        | grep -q .; then
    echo "a default-lifecycle Test class imports Testcontainers; native-only verification refuses to continue" >&2
    exit 48
fi

sh "$ROOT_DIR/scripts/verify-build-tree-clean.sh" --root "$ROOT_DIR" --pre-build

BUILD_COMMIT=$(git -C "$ROOT_DIR" rev-parse HEAD)
BUILD_TIME=${ERP_SYSTEM_BUILD_TIME:-}
[ -n "$BUILD_TIME" ] || {
    echo "ERP_SYSTEM_BUILD_TIME must be an explicit ISO-8601 timestamp" >&2
    exit 49
}
CANDIDATE_MANIFEST=${ERP_SYSTEM_RELEASE_MANIFEST:-$ROOT_DIR/output/release/system-management-release-20260714.json}

NPM_CLI_JS=${NPM_CLI_JS:-}
if [ -z "$NPM_CLI_JS" ]; then
    for candidate in /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js /usr/local/lib/node_modules/npm/bin/npm-cli.js; do
        if [ -f "$candidate" ]; then
            NPM_CLI_JS=$candidate
            break
        fi
    done
fi
run_npm() {
    if [ -n "$NPM_CLI_JS" ]; then
        PATH="$(dirname "$NODE_BIN"):$PATH" "$NODE_BIN" "$NPM_CLI_JS" "$@"
    else
        npm "$@"
    fi
}
run_npm --version >/dev/null

echo "SYSTEM_MANAGEMENT_NATIVE_GATE_START commit=$BUILD_COMMIT"
(
    cd "$ROOT_DIR"
    mvn -T 1C clean test -DskipTests=false -Dbuild.commit="$BUILD_COMMIT"
    mvn -T 1C package -DskipTests -Dbuild.commit="$BUILD_COMMIT"
)

(
    cd "$ROOT_DIR/erp-ui"
    run_npm ci
    run_npm test
    VUE_APP_BUILD_COMMIT="$BUILD_COMMIT" VUE_APP_BUILD_TIME="$BUILD_TIME" run_npm run build:prod
)

bash "$ROOT_DIR/scripts/verify-system-management-native-mysql.sh"

ERP_UI_PREVIEW_PORT=19528 "$NODE_BIN" "$ROOT_DIR/erp-ui/scripts/serve-dist.cjs" \
    >"${TMPDIR:-/tmp}/erp-ui-system-management-preview.log" 2>&1 &
PREVIEW_PID=$!
cleanup_preview() {
    kill "$PREVIEW_PID" >/dev/null 2>&1 || true
    wait "$PREVIEW_PID" >/dev/null 2>&1 || true
}
trap cleanup_preview EXIT HUP INT TERM
preview_ready=false
attempt=0
while [ "$attempt" -lt 30 ]; do
    if curl -fsS --max-time 2 http://127.0.0.1:19528/ >/dev/null 2>&1; then
        preview_ready=true
        break
    fi
    attempt=$((attempt + 1))
    sleep 1
done
[ "$preview_ready" = true ] || {
    echo "native frontend preview did not become ready" >&2
    exit 50
}
BROWSER_OUTPUT=$(NODE_BIN="$NODE_BIN" bash "$ROOT_DIR/scripts/verify-system-management-browser.sh")
printf '%s\n' "$BROWSER_OUTPUT"
BROWSER_VERSION=$(printf '%s\n' "$BROWSER_OUTPUT" \
    | sed -n 's/.*Chrome\/\([0-9][0-9.]*\).*/\1/p' | head -1)
[ -n "$BROWSER_VERSION" ] || {
    echo "unable to record native Chrome version" >&2
    exit 51
}
cleanup_preview
trap - EXIT HUP INT TERM

sh "$ROOT_DIR/docker/copy.sh" --manifest scripts/system-management-release-20260714.json
PYTHONPATH="$ROOT_DIR/scripts" python3 "$ROOT_DIR/scripts/finalize_system_management_release_manifest.py" \
    --root "$ROOT_DIR" --template "$MANIFEST" --output "$CANDIDATE_MANIFEST" \
    --commit "$BUILD_COMMIT" --build-time "$BUILD_TIME" --browser "$BROWSER_VERSION"
sh "$ROOT_DIR/docker/copy.sh" --manifest "$CANDIDATE_MANIFEST" --sql-only
sh "$ROOT_DIR/scripts/verify-build-tree-clean.sh" --root "$ROOT_DIR" \
    --manifest "$CANDIDATE_MANIFEST" --artifacts-only
python3 "$ROOT_DIR/scripts/verify_system_management_release_contract.py" --root "$ROOT_DIR" \
    --manifest "$CANDIDATE_MANIFEST" --candidate

echo "SYSTEM_MANAGEMENT_RELEASE_NATIVE_FULL_OK commit=$BUILD_COMMIT native_only=true"
