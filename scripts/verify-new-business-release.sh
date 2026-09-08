#!/usr/bin/env bash

# Unified, fail-closed release gate for the 2026-07-14 new-business release.
# No argument means the complete candidate gate. Developers must explicitly
# choose --static or --local when Docker/clean-candidate checks are not desired.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/scripts/new-business-release-20260714.json"
MAVEN="$ROOT_DIR/mvnw"
MODE="${1:---full}"
STARTED_EPOCH="$(date +%s)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="${ERP_NEW_BUSINESS_EVIDENCE_DIR:-$ROOT_DIR/output/new-business-release/$STAMP}"
STATUS="failed"
CURRENT_STEP="initialization"
BUILD_COMMIT=""
BUILD_TIME=""

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

step()
{
    CURRENT_STEP="$1"
    printf '\n[STEP] %s\n' "$CURRENT_STEP"
}

write_summary()
{
    local exit_code="$1"
    local finished_epoch
    finished_epoch="$(date +%s)"
    mkdir -p "$EVIDENCE_DIR"
    RELEASE_ROOT="$ROOT_DIR" \
    RELEASE_MANIFEST="$MANIFEST" \
    RELEASE_EVIDENCE_DIR="$EVIDENCE_DIR" \
    RELEASE_MODE="$MODE" \
    RELEASE_STATUS="$STATUS" \
    RELEASE_STEP="$CURRENT_STEP" \
    RELEASE_STARTED_EPOCH="$STARTED_EPOCH" \
    RELEASE_FINISHED_EPOCH="$finished_epoch" \
    RELEASE_EXIT_CODE="$exit_code" \
    RELEASE_BUILD_COMMIT="$BUILD_COMMIT" \
    RELEASE_BUILD_TIME="$BUILD_TIME" \
    python3 - <<'PY'
import hashlib
import json
import os
import subprocess
from pathlib import Path

root = Path(os.environ["RELEASE_ROOT"])
manifest = json.loads(Path(os.environ["RELEASE_MANIFEST"]).read_text(encoding="utf-8"))
evidence = Path(os.environ["RELEASE_EVIDENCE_DIR"])

def git(*args):
    try:
        return subprocess.check_output(
            ["git", "-C", str(root), *args], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return ""

def file_sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

artifacts = {}
for relative in (
    "erp-gateway/target/erp-gateway.jar",
    "erp-auth/target/erp-auth.jar",
    "erp-visual/erp-monitor/target/erp-visual-monitor.jar",
    "erp-modules/erp-system/target/erp-modules-system.jar",
    "erp-modules/erp-file/target/erp-modules-file.jar",
    "erp-modules/erp-job/target/erp-modules-job.jar",
    "erp-modules/erp-oa/target/erp-modules-oa.jar",
    "erp-modules/erp-inventory/target/erp-modules-inventory.jar",
    "erp-modules/erp-approval/target/erp-modules-approval.jar",
):
    path = root / relative
    if path.is_file():
        artifacts[relative] = file_sha(path)

dist = root / "erp-ui/dist"
if dist.is_dir():
    digest = hashlib.sha256()
    files = sorted(path for path in dist.rglob("*") if path.is_file())
    for path in files:
        relative = path.relative_to(dist).as_posix().encode("utf-8")
        digest.update(len(relative).to_bytes(4, "big"))
        digest.update(relative)
        digest.update(bytes.fromhex(file_sha(path)))
    artifacts["erp-ui/dist"] = digest.hexdigest()

summary = {
    "schemaVersion": 1,
    "releaseId": manifest["releaseId"],
    "mode": os.environ["RELEASE_MODE"],
    "status": os.environ["RELEASE_STATUS"],
    "failedOrLastStep": os.environ["RELEASE_STEP"],
    "exitCode": int(os.environ["RELEASE_EXIT_CODE"]),
    "startedEpoch": int(os.environ["RELEASE_STARTED_EPOCH"]),
    "finishedEpoch": int(os.environ["RELEASE_FINISHED_EPOCH"]),
    "git": {
        "branch": git("branch", "--show-current"),
        "commit": git("rev-parse", "HEAD"),
        "dirty": bool(git("status", "--porcelain=v1")),
    },
    "build": {
        "commit": os.environ.get("RELEASE_BUILD_COMMIT") or None,
        "time": os.environ.get("RELEASE_BUILD_TIME") or None,
    },
    "artifacts": artifacts,
}
(evidence / "summary.json").write_text(
    json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
print(f"[INFO] release evidence: {evidence / 'summary.json'}")
PY
}

on_exit()
{
    local exit_code=$?
    trap - EXIT
    write_summary "$exit_code" || true
    exit "$exit_code"
}

trap on_exit EXIT

case "$MODE" in
    --static|--local|--full) ;;
    *) fail 'usage: verify-new-business-release.sh [--static|--local|--full]' ;;
esac

command -v python3 >/dev/null 2>&1 || fail 'python3 is required'
[[ -x "$MAVEN" ]] || [[ "$MODE" == '--static' ]] \
    || fail 'repository Maven wrapper is required for local/full verification'
mkdir -p "$EVIDENCE_DIR"

step 'static release scope'
if [[ "$MODE" == '--full' ]]; then
    "$ROOT_DIR/scripts/verify-new-business-release-scope.sh" --candidate
else
    "$ROOT_DIR/scripts/verify-new-business-release-scope.sh" --static
fi

step 'migration, integration-test, readiness and reconciliation contracts'
"$ROOT_DIR/scripts/verify-new-business-migrations.sh"
"$ROOT_DIR/scripts/verify-sign-final-confirmation-cutover.sh" --static
"$ROOT_DIR/scripts/verify-unified-approval-cutover.sh"
"$ROOT_DIR/scripts/verify-docker-mysql-bootstrap.sh"
"$ROOT_DIR/scripts/verify-new-business-it-contract.sh"
"$ROOT_DIR/scripts/verify-new-business-readiness.sh"
"$ROOT_DIR/scripts/verify-new-business-reconciliation.sh"
"$ROOT_DIR/scripts/verify-new-business-monitoring.sh"
python3 "$ROOT_DIR/scripts/test_verify_new_business_failsafe_results.py"
python3 "$ROOT_DIR/scripts/test_release_migration_contract.py"
python3 "$ROOT_DIR/scripts/test_verify_new_business_release_archive.py"
python3 "$ROOT_DIR/scripts/test_new_business_release_build_contract.py"
python3 "$ROOT_DIR/scripts/test_approval_start_outbox_contract.py"
python3 "$ROOT_DIR/scripts/test_aliyun_deployment_contract.py"
python3 "$ROOT_DIR/scripts/test_new_business_uat.py"
python3 "$ROOT_DIR/scripts/test_parse_new_business_reconciliation.py"
python3 "$ROOT_DIR/scripts/test_new_business_gray_evidence.py"
python3 "$ROOT_DIR/scripts/test_new_business_performance.py"
PYTHONPATH="$ROOT_DIR/scripts${PYTHONPATH:+:$PYTHONPATH}" \
    python3 "$ROOT_DIR/scripts/test_stage0_db_evidence.py"

if [[ "$MODE" == '--static' ]]; then
    STATUS="passed"
    CURRENT_STEP="static gate complete"
    printf '\n[PASS] static new-business release gate completed\n'
    exit 0
fi

BUILD_COMMIT="$(git -C "$ROOT_DIR" rev-parse HEAD)"
BUILD_TIME="${ERP_NEW_BUSINESS_BUILD_TIME:-}"
if [[ -z "$BUILD_TIME" && "$MODE" == '--local' ]]; then
    BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
fi
[[ -n "$BUILD_TIME" ]] \
    || fail 'ERP_NEW_BUSINESS_BUILD_TIME is required for the full release gate'
python3 - "$BUILD_TIME" <<'PY'
import datetime
import sys

try:
    datetime.datetime.fromisoformat(sys.argv[1].replace("Z", "+00:00"))
except ValueError as error:
    raise SystemExit(f"invalid ERP_NEW_BUSINESS_BUILD_TIME: {error}")
PY

command -v node >/dev/null 2>&1 || fail 'Node.js is required for frontend verification'
command -v npm >/dev/null 2>&1 || fail 'npm is required for frontend verification'
PINNED_NODE_VERSION="$(tr -d '[:space:]' < "$ROOT_DIR/erp-ui/.nvmrc")"
CURRENT_NODE_VERSION="$(node -p 'process.versions.node')"
[[ "$CURRENT_NODE_VERSION" == "$PINNED_NODE_VERSION" ]] \
    || fail "Node.js $PINNED_NODE_VERSION is required; found $CURRENT_NODE_VERSION"
PINNED_NPM_VERSION="$(node -p 'require(process.argv[1]).packageManager.replace(/^npm@/, "")' "$ROOT_DIR/erp-ui/package.json")"
CURRENT_NPM_VERSION="$(npm --version)"
[[ "$CURRENT_NPM_VERSION" == "$PINNED_NPM_VERSION" ]] \
    || fail "npm $PINNED_NPM_VERSION is required; found $CURRENT_NPM_VERSION"
MAVEN_VERSION="$($MAVEN --version | sed -n 's/^Apache Maven \([^ ]*\).*/\1/p' | head -1)"
[[ "$MAVEN_VERSION" == '3.9.16' ]] \
    || fail "repository Maven 3.9.16 is required; found ${MAVEN_VERSION:-unknown}"
MAVEN_JAVA_VERSION="$($MAVEN --version | sed -n 's/^Java version: \([^,]*\).*/\1/p' | head -1)"
[[ "$MAVEN_JAVA_VERSION" == 17 || "$MAVEN_JAVA_VERSION" == 17.* ]] \
    || fail "Maven must run on Java 17; found ${MAVEN_JAVA_VERSION:-unknown}"

step 'complete backend regression and candidate jar build'
"$MAVEN" -T 1C -f "$ROOT_DIR/pom.xml" clean verify \
    -Dbuild.commit="$BUILD_COMMIT"

if [[ "$MODE" == '--full' ]]; then
    step 'Docker availability'
    source "$ROOT_DIR/scripts/configure-testcontainers-docker.sh"
    erp_configure_testcontainers_docker \
        || fail 'Docker/Testcontainers environment is unavailable; full release gate cannot continue'

    step 'fresh MySQL 5.7/8.0 integration regression'
    IT_STARTED_EPOCH="$(date +%s)"
    "$MAVEN" -f "$ROOT_DIR/pom.xml" clean -Pnew-business-mysql-it \
        -Dbuild.commit="$BUILD_COMMIT" verify
    python3 "$ROOT_DIR/scripts/verify_new_business_failsafe_results.py" \
        --root "$ROOT_DIR" \
        --manifest "$MANIFEST" \
        --since-epoch "$IT_STARTED_EPOCH" \
        --output "$EVIDENCE_DIR/failsafe-results.json"
fi

step 'frontend contract regression and production build'
(
    cd "$ROOT_DIR/erp-ui"
    if [[ "$MODE" == '--full' ]]; then
        npm ci
    fi
    npm test
    VUE_APP_BUILD_COMMIT="$BUILD_COMMIT" \
        VUE_APP_BUILD_TIME="$BUILD_TIME" \
        npm run build:prod
)

if [[ "$MODE" == '--full' ]]; then
    step 'release payload assembly'
    sh "$ROOT_DIR/docker/copy.sh"
    "$ROOT_DIR/scripts/verify-new-business-release-payload.sh" \
        "$ROOT_DIR/docker" "$BUILD_COMMIT"

    step 'real-role API and browser UAT evidence'
    [[ -n "${ERP_NEW_BUSINESS_UAT_EVIDENCE:-}" ]] \
        || fail 'ERP_NEW_BUSINESS_UAT_EVIDENCE is required for the full release gate'
    python3 "$ROOT_DIR/scripts/verify_new_business_uat_evidence.py" \
        --root "$ROOT_DIR" \
        --candidate-commit "$(git -C "$ROOT_DIR" rev-parse HEAD)" \
        --evidence "$ERP_NEW_BUSINESS_UAT_EVIDENCE"
fi

STATUS="passed"
CURRENT_STEP="${MODE#--} gate complete"
printf '\n[PASS] %s new-business release gate completed\n' "${MODE#--}"
