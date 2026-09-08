#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
MANIFEST="$ROOT_DIR/scripts/mobile-frontend-optimization-release-20260728.json"
FILE_LIST="$ROOT_DIR/scripts/mobile-frontend-optimization-release-files-20260728.list"
CONFIG_VALIDATOR="$ROOT_DIR/scripts/verify-mobile-session-canary-config.sh"
HEAD_COMMIT=$(git -C "$ROOT_DIR" rev-parse HEAD)

python3 - "$MANIFEST" "$FILE_LIST" "$ROOT_DIR" "$HEAD_COMMIT" <<'PY'
import json
import sys
from pathlib import Path

manifest_path = Path(sys.argv[1]).resolve()
file_list_path = Path(sys.argv[2]).resolve()
root = Path(sys.argv[3]).resolve()
head_commit = sys.argv[4]

manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
if manifest.get("releaseId") != "mobile-frontend-optimization-20260728":
    raise SystemExit("unexpected releaseId")
if manifest.get("status") != "conditional-ready":
    raise SystemExit("release status must remain conditional-ready before environment gates")
if manifest.get("executionPolicy") != "manual-phased-canary-no-auto-deploy":
    raise SystemExit("unsafe execution policy")
if manifest.get("sourceCommit") != head_commit:
    raise SystemExit("manifest sourceCommit does not match HEAD")

artifact_policy = manifest.get("artifactPolicy", {})
if artifact_policy.get("candidateArtifactRequiredBeforeDeploy") is not True:
    raise SystemExit("candidate artifact must be required before deployment")
if artifact_policy.get("candidateArtifactSha256") is not None:
    raise SystemExit("source-only manifest must not claim an artifact SHA-256")
if artifact_policy.get("dirtyWorktreeMayBePackaged") is not False:
    raise SystemExit("dirty worktree packaging must remain forbidden")
if artifact_policy.get("productionDeploymentAuthorized") is not False:
    raise SystemExit("source manifest must not authorize production deployment")

packages = manifest.get("packages", [])
if [item.get("id") for item in packages] != ["A", "B", "C", "D", "E"]:
    raise SystemExit("package order must be A through E")
if [item.get("order") for item in packages] != [1, 2, 3, 4, 5]:
    raise SystemExit("package numeric order is invalid")

entries = [
    line.strip()
    for line in file_list_path.read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
if not entries:
    raise SystemExit("release file list is empty")
if len(entries) != len(set(entries)):
    duplicates = sorted({entry for entry in entries if entries.count(entry) > 1})
    raise SystemExit("duplicate release paths: " + ", ".join(duplicates))

unsafe = []
for entry in entries:
    path = Path(entry)
    if path.is_absolute() or ".." in path.parts:
        unsafe.append(entry)
if unsafe:
    raise SystemExit("unsafe release paths: " + ", ".join(unsafe))

missing = [entry for entry in entries if not (root / entry).is_file()]
if missing:
    raise SystemExit("missing release files: " + ", ".join(missing))

forbidden_parts = {"target", "node_modules", "dist-dev-check", "android", "ios"}
forbidden = [
    entry
    for entry in entries
    if forbidden_parts.intersection(Path(entry).parts)
    or entry.startswith("tmp/")
    or entry.startswith("erp-ui/dist/")
]
if forbidden:
    raise SystemExit("generated or excluded paths in release list: " + ", ".join(forbidden))

required = {
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvMobileServiceImpl.java",
    "erp-ui/package.json",
    "erp-ui/package-lock.json",
    "erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java",
    "erp-ui/src/views/mobile/hr/components/MobileOnboardingOwnerPicker.vue",
    "erp-ui/scripts/check-production-bundle.cjs",
    "erp-auth/src/main/java/com/erp/auth/controller/TokenController.java",
    "erp-common/erp-common-security/src/main/java/com/erp/common/security/config/WebMvcConfig.java",
    "erp-gateway/src/main/java/com/erp/gateway/filter/CsrfFilter.java",
    "erp-ui/src/utils/sessionMode.js",
    "docs/mobile-frontend-optimization-canary-runbook-20260728.md",
    "scripts/verify-mobile-session-canary-config.sh",
}
missing_required = sorted(required - set(entries))
if missing_required:
    raise SystemExit("release list misses required files: " + ", ".join(missing_required))

shared_files = set(manifest.get("sharedFiles", []))
if not shared_files or not shared_files.issubset(set(entries)):
    raise SystemExit("all shared files must be present in the explicit release list")

print(
    "MOBILE_OPTIMIZATION_SOURCE_MANIFEST_OK"
    f" files={len(entries)} packages={len(packages)} commit={head_commit}"
)
PY

test ! -e "$ROOT_DIR/erp-ui/public/session-cookie-e2e-probe.html"
rg -q 'globalcors:' "$ROOT_DIR/erp-gateway/src/main/resources/bootstrap.yml"
rg -q 'add-to-simple-url-handler-mapping: true' "$ROOT_DIR/erp-gateway/src/main/resources/bootstrap.yml"
rg -q 'headerExcludeUrls = \{ "/login" \}' \
    "$ROOT_DIR/erp-common/erp-common-security/src/main/java/com/erp/common/security/config/WebMvcConfig.java"
for env_file in \
    "$ROOT_DIR/erp-ui/.env.development" \
    "$ROOT_DIR/erp-ui/.env.staging" \
    "$ROOT_DIR/erp-ui/.env.production"
do
    rg -q 'VUE_APP_WEB_SESSION_MODE = bearer' "$env_file"
done

git -C "$ROOT_DIR" diff --check
sh -n "$CONFIG_VALIDATOR"

env \
    DEPLOY_ENV=staging \
    RELEASE_PHASE=web-cookie-canary \
    AUTH_SESSION_MODE=dual \
    GATEWAY_SESSION_MODE=dual \
    WEB_SESSION_MODE=cookie \
    AUTH_CONFIG_REVISION=release-20260728-r1 \
    GATEWAY_CONFIG_REVISION=release-20260728-r1 \
    COOKIE_NAME=ERP_SESSION \
    CSRF_COOKIE_NAME=XSRF-TOKEN \
    COOKIE_SECURE=true \
    COOKIE_HTTP_ONLY=true \
    CSRF_COOKIE_HTTP_ONLY=false \
    COOKIE_SAME_SITE=Lax \
    CSRF_ENABLED=true \
    REQUIRE_ORIGIN=true \
    PUBLIC_ORIGIN=https://erp-staging.example.com \
    ALLOWED_ORIGINS=https://erp-staging.example.com \
    NATIVE_BEARER_ENABLED=true \
    EFFECTIVE_CONFIG_SNAPSHOT_RECORDED=true \
    MULTI_ACCOUNT_UAT_PASSED=true \
    ROLLBACK_ARTIFACT_READY=true \
    MONITORING_READY=true \
    sh "$CONFIG_VALIDATOR"

if env \
    DEPLOY_ENV=staging \
    RELEASE_PHASE=web-cookie-canary \
    AUTH_SESSION_MODE=dual \
    GATEWAY_SESSION_MODE=bearer \
    WEB_SESSION_MODE=cookie \
    AUTH_CONFIG_REVISION=r1 \
    GATEWAY_CONFIG_REVISION=r1 \
    COOKIE_NAME=ERP_SESSION \
    CSRF_COOKIE_NAME=XSRF-TOKEN \
    COOKIE_SECURE=true \
    COOKIE_HTTP_ONLY=true \
    CSRF_COOKIE_HTTP_ONLY=false \
    COOKIE_SAME_SITE=Lax \
    CSRF_ENABLED=true \
    REQUIRE_ORIGIN=true \
    PUBLIC_ORIGIN=https://erp-staging.example.com \
    ALLOWED_ORIGINS=https://erp-staging.example.com \
    NATIVE_BEARER_ENABLED=true \
    EFFECTIVE_CONFIG_SNAPSHOT_RECORDED=true \
    MULTI_ACCOUNT_UAT_PASSED=true \
    ROLLBACK_ARTIFACT_READY=true \
    MONITORING_READY=true \
    sh "$CONFIG_VALIDATOR" >/dev/null 2>&1
then
    echo "mismatched Auth/Gateway mode was not rejected" >&2
    exit 1
fi

if env \
    DEPLOY_ENV=production \
    RELEASE_PHASE=web-cookie-full \
    AUTH_SESSION_MODE=dual \
    GATEWAY_SESSION_MODE=dual \
    WEB_SESSION_MODE=cookie \
    AUTH_CONFIG_REVISION=r2 \
    GATEWAY_CONFIG_REVISION=r2 \
    COOKIE_NAME=ERP_SESSION \
    CSRF_COOKIE_NAME=XSRF-TOKEN \
    COOKIE_SECURE=true \
    COOKIE_HTTP_ONLY=true \
    CSRF_COOKIE_HTTP_ONLY=false \
    COOKIE_SAME_SITE=Lax \
    CSRF_ENABLED=true \
    REQUIRE_ORIGIN=true \
    PUBLIC_ORIGIN=https://erp.example.com \
    ALLOWED_ORIGINS='https://*.example.com' \
    NATIVE_BEARER_ENABLED=true \
    EFFECTIVE_CONFIG_SNAPSHOT_RECORDED=true \
    MULTI_ACCOUNT_UAT_PASSED=true \
    ROLLBACK_ARTIFACT_READY=true \
    MONITORING_READY=true \
    sh "$CONFIG_VALIDATOR" >/dev/null 2>&1
then
    echo "wildcard Origin was not rejected" >&2
    exit 1
fi

if env \
    DEPLOY_ENV=production \
    RELEASE_PHASE=web-cookie-full \
    AUTH_SESSION_MODE=cookie \
    GATEWAY_SESSION_MODE=cookie \
    WEB_SESSION_MODE=cookie \
    AUTH_CONFIG_REVISION=r3 \
    GATEWAY_CONFIG_REVISION=r3 \
    COOKIE_NAME=ERP_SESSION \
    CSRF_COOKIE_NAME=XSRF-TOKEN \
    COOKIE_SECURE=true \
    COOKIE_HTTP_ONLY=true \
    CSRF_COOKIE_HTTP_ONLY=false \
    COOKIE_SAME_SITE=Lax \
    CSRF_ENABLED=true \
    REQUIRE_ORIGIN=true \
    PUBLIC_ORIGIN=https://erp.example.com \
    ALLOWED_ORIGINS=https://erp.example.com \
    NATIVE_BEARER_ENABLED=true \
    EFFECTIVE_CONFIG_SNAPSHOT_RECORDED=true \
    MULTI_ACCOUNT_UAT_PASSED=true \
    ROLLBACK_ARTIFACT_READY=true \
    MONITORING_READY=true \
    sh "$CONFIG_VALIDATOR" >/dev/null 2>&1
then
    echo "Cookie-only backend with Native Bearer was not rejected" >&2
    exit 1
fi

echo "MOBILE_OPTIMIZATION_RELEASE_SOURCE_OK negative_config_cases=3"
