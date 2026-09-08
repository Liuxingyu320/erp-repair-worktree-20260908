#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FILE_LIST="$ROOT_DIR/scripts/customer-service-card-release-files-20260719.list"
MODE="${1:---static}"

usage()
{
    cat <<'EOF'
Usage:
  bash scripts/verify-customer-service-card-release.sh --static
  bash scripts/verify-customer-service-card-release.sh --candidate BASE_REF CANDIDATE_REF
  bash scripts/verify-customer-service-card-release.sh --archive PACKAGE.tar.gz

  --static      Validate the checked-in code-only release contract.
  --candidate   Require the complete Git diff to equal the exact source allowlist.
  --archive     Require CUSTOMER_CARD_EXPECTED_COMMIT and a complete direct-stage
                runtime package whose frontend and all 10 JARs match that commit.
EOF
}

command -v python3 >/dev/null 2>&1 || {
    echo '[FAIL] python3 is required' >&2
    exit 3
}

run_static()
{
    if [[ "${CUSTOMER_CARD_CONTRACT_TEST_ACTIVE:-0}" != '1' ]]; then
        python3 "$ROOT_DIR/scripts/test_customer_service_card_release_contract.py"
    fi
    bash -n "$ROOT_DIR/scripts/verify-customer-service-card-release.sh"
    bash -n "$ROOT_DIR/scripts/remote_customer_service_card_preflight_20260719.sh"
    bash -n "$ROOT_DIR/scripts/remote_customer_service_card_postcheck_20260719.sh"
    echo 'CUSTOMER_SERVICE_CARD_RELEASE_STATIC_OK deploymentMode=code-only migrations=0 featureFlags=0'
}

case "$MODE" in
    --static)
        [[ "$#" -eq 1 ]] || { usage >&2; exit 2; }
        run_static
        ;;
    --candidate)
        [[ "$#" -eq 3 ]] || { usage >&2; exit 2; }
        run_static
        BASE_REF="$2"
        CANDIDATE_REF="$3"
        git -C "$ROOT_DIR" rev-parse --verify "${BASE_REF}^{commit}" >/dev/null
        git -C "$ROOT_DIR" rev-parse --verify "${CANDIDATE_REF}^{commit}" >/dev/null
        ACTUAL_FILES="$(git -C "$ROOT_DIR" diff --name-only --diff-filter=ACDMRTUXB \
            "$BASE_REF" "$CANDIDATE_REF")"
        python3 - "$FILE_LIST" "$ACTUAL_FILES" <<'PY'
import sys
from pathlib import Path

allowlist = [
    line.strip()
    for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
actual = [line for line in sys.argv[2].splitlines() if line]
if actual != allowlist:
    missing = sorted(set(allowlist) - set(actual))
    extra = sorted(set(actual) - set(allowlist))
    if missing:
        print("[FAIL] candidate is missing allowlisted files:", *missing, sep="\n  ", file=sys.stderr)
    if extra:
        print("[FAIL] candidate contains out-of-scope files:", *extra, sep="\n  ", file=sys.stderr)
    if not missing and not extra:
        print("[FAIL] candidate file order differs from the canonical allowlist", file=sys.stderr)
    raise SystemExit(4)
print(f"CUSTOMER_SERVICE_CARD_CANDIDATE_SCOPE_OK files={len(actual)}")
PY
        MOBILE_PAYLOAD_DIFF="$(git -C "$ROOT_DIR" diff --unified=0 \
            "$BASE_REF" "$CANDIDATE_REF" -- \
            erp-ui/src/views/mobile/feature/mobileFormPayloads.js)"
        python3 - "$MOBILE_PAYLOAD_DIFF" <<'PY'
import sys

diff = sys.argv[1]
if diff.count("\n@@") != 1:
    raise SystemExit("[FAIL] mobileFormPayloads.js must contain exactly the isolated customer requestKey hunk")
if "source.requestKey" not in diff or 'createRequestKey("customer-card")' not in diff:
    raise SystemExit("[FAIL] customer requestKey reuse hunk is missing")
for forbidden in ("normalizeLineItems(value).filter", "isAllowedLineItemType(itemType, settings)"):
    if forbidden in diff:
        raise SystemExit("[FAIL] unrelated OE/line-item hunk entered the customer-card candidate")
print("CUSTOMER_SERVICE_CARD_MOBILE_PAYLOAD_HUNK_OK")
PY
        WORKBENCH_DIFF="$(git -C "$ROOT_DIR" diff --unified=0 \
            "$BASE_REF" "$CANDIDATE_REF" -- \
            erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue)"
        python3 - "$WORKBENCH_DIFF" <<'PY'
import sys

diff = sys.argv[1]
if diff.count("\n@@") != 1:
    raise SystemExit("[FAIL] MobileWorkbenchShell.vue must contain exactly one customer-permission hunk")
changed = [
    line
    for line in diff.splitlines()
    if (line.startswith(("+", "-")) and not line.startswith(("+++", "---")))
]
if len(changed) != 2:
    raise SystemExit("[FAIL] MobileWorkbenchShell.vue contains changes beyond the customer permission replacement")
removed, added = changed
if not (
    removed.startswith("-")
    and added.startswith("+")
    and '"客户资料"' in removed
    and 'permissions: ["inv:customer:list"]' in removed
    and added[1:] == removed[1:].replace(
        'permissions: ["inv:customer:list"]',
        'permissions: ["inv:customerCard:list"]',
    )
):
    raise SystemExit("[FAIL] MobileWorkbenchShell.vue customer permission replacement drift")
if "Progressive mobile redesign" in diff:
    raise SystemExit("[FAIL] unrelated MobileWorkbenchShell.vue CSS entered the customer-card candidate")
print("CUSTOMER_SERVICE_CARD_WORKBENCH_HUNK_OK")
PY
        python3 - "$ROOT_DIR" "$CANDIDATE_REF" <<'PY'
import re
import subprocess
import sys

root, candidate = sys.argv[1:]

def source(path: str) -> str:
    return subprocess.check_output(
        ["git", "-C", root, "show", f"{candidate}:{path}"], text=True
    )

def method_body(java_source: str, signature: str) -> str:
    match = re.search(signature, java_source)
    if not match:
        raise SystemExit("[FAIL] Java method signature is missing: " + signature)
    opening = java_source.find("{", match.end())
    if opening < 0:
        raise SystemExit("[FAIL] Java method body is missing: " + signature)
    depth = 0
    for index in range(opening, len(java_source)):
        character = java_source[index]
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return java_source[opening + 1:index]
    raise SystemExit("[FAIL] unbalanced Java method body: " + signature)

def ordered(body: str, markers: tuple[str, ...], label: str) -> None:
    cursor = -1
    for marker in markers:
        position = body.find(marker, cursor + 1)
        if position < 0:
            raise SystemExit(f"[FAIL] {label} is missing or misorders: {marker}")
        cursor = position

annotation = source("erp-common/erp-common-security/src/main/java/com/erp/common/security/annotation/IdempotentSubmit.java")
aspect = source("erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java")
aspect_test = source("erp-common/erp-common-security/src/test/java/com/erp/common/security/aspect/IdempotentSubmitAspectTest.java")
controller = source("erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java")
service = source("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImpl.java")
feature_gate = source("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/BusinessFeatureGate.java")
feature_gate_test = source("erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/BusinessFeatureGateTest.java")
mobile_picker = source("erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue")
mobile_quick_test = source("erp-ui/test/mobileQuickCustomer.test.js")

if not re.search(r"boolean\s+releaseOnSuccess\s*\(\s*\)\s*default\s+false\s*;", annotation):
    raise SystemExit("[FAIL] IdempotentSubmit.releaseOnSuccess must default to false")
if "idempotentSubmit.releaseOnSuccess()" not in aspect or "releaseQuietly(key)" not in aspect:
    raise SystemExit("[FAIL] the aspect does not release the successful in-flight guard")
for forbidden in ("SignScopeHeaderUtils", "isSigningRequest(", "isSameOrChildPath("):
    if forbidden in aspect:
        raise SystemExit("[FAIL] unrelated signing-scope code entered IdempotentSubmitAspect: " + forbidden)
for forbidden in (
    "SignScopeHeaderUtils",
    "shouldUseDedicatedSignScopeInIdempotencyKey",
    "shouldIgnoreSigningHeaderForInventoryIdempotencyKey",
    "shouldNotTreatSimilarNonSigningPathAsSigningEndpoint",
):
    if forbidden in aspect_test:
        raise SystemExit("[FAIL] unrelated signing-scope test entered the customer-card candidate: " + forbidden)
if "shouldReleaseInFlightGuardAfterDurablyIdempotentSuccess" not in aspect_test:
    raise SystemExit("[FAIL] successful releaseOnSuccess coverage is missing")
if controller.count("@IdempotentSubmit(timeout = 30, releaseOnSuccess = true)") != 4:
    raise SystemExit("[FAIL] exactly four customer-card write endpoints must opt into releaseOnSuccess")
required_service_markers = (
    "请求标识不能为空",
    "normalized.length() > 128",
    "请求标识长度不能超过128",
    "selectCreatedCustomerIdByRequestKey",
    "selectChangeLogIdByRequestKey",
    "selectRecordIdByRequestKey",
)
for marker in required_service_markers:
    if marker not in service:
        raise SystemExit("[FAIL] durable requestKey contract is incomplete: " + marker)
if "UUID.randomUUID" in service:
    raise SystemExit("[FAIL] the service must not synthesize a durable requestKey")
open_match = re.search(
    r"openQuickCustomerForm\(\)\s*\{([\s\S]*?)\n\s*\},\n\s*closeQuickCustomerForm",
    mobile_picker,
)
create_match = re.search(
    r"createQuickCustomer\(\)\s*\{([\s\S]*?)\n\s*\},\n\s*focusSearchInputAfterQuickCreate",
    mobile_picker,
)
if not open_match or not create_match:
    raise SystemExit("[FAIL] mobile quick-customer method boundaries are missing")
if mobile_picker.count("Date.now()") != 1 or "Date.now()" not in open_match.group(1):
    raise SystemExit("[FAIL] mobile quick-customer key must be generated exactly once when the form opens")
if "requestKey: this.quickCustomerRequestKey" not in create_match.group(1):
    raise SystemExit("[FAIL] mobile quick-customer create must reuse the stored requestKey")
if "Date.now()" in create_match.group(1):
    raise SystemExit("[FAIL] mobile quick-customer retry must not generate a new requestKey")
if "quick-customer retries should reuse the key" not in mobile_quick_test:
    raise SystemExit("[FAIL] mobile quick-customer retry coverage is missing")
required_allowlist_code = (
    'normalized.split(",", -1)',
    'candidate.matches("[1-9][0-9]*")',
    "Long.parseLong(candidate)",
    "catch (NumberFormatException ex)",
    "shopDeptId <= 0",
)
for marker in required_allowlist_code:
    if marker not in feature_gate:
        raise SystemExit("[FAIL] strict shop allowlist parser is incomplete: " + marker)
if '"*".equals(normalized)' in feature_gate or '"*".equals(value' in feature_gate:
    raise SystemExit("[FAIL] wildcard shop allowlists must not be accepted")
required_allowlist_tests = (
    "blankWildcardAndMalformedAllowlistsDenyEveryShop",
    'R.ok("*")',
    '"10,garbage"',
    '"10,"',
    '"10,,12"',
    '"0,10"',
    '"-1,10"',
    '"10,9223372036854775808"',
)
for marker in required_allowlist_tests:
    if marker not in feature_gate_test:
        raise SystemExit("[FAIL] malformed shop allowlist coverage is incomplete: " + marker)

if "PageHelper" in controller:
    raise SystemExit("[FAIL] InvCustomerController must not import or call PageHelper")
controller_card_list = method_body(controller, r"public\s+TableDataInfo\s+serviceCardList\s*\(")
controller_audit_list = method_body(controller, r"public\s+TableDataInfo\s+serviceCardAudit\s*\(")
controller_page = method_body(controller, r"private\s+CustomerCardPage\s+customerCardPage\s*\(")
for label, body, service_call in (
    ("controller card list", controller_card_list, "customerServiceCardService.selectList"),
    ("controller audit list", controller_audit_list, "customerServiceCardService.selectAuditList"),
):
    ordered(body, ("CustomerCardPage page = customerCardPage(request);", service_call), label)
    if "startPage(" in body or "PageHelper" in body:
        raise SystemExit(f"[FAIL] {label} must only parse and forward CustomerCardPage")
for marker in ("Math.max(1", "Math.min(100, Math.max(1", "new CustomerCardPage(pageNum, pageSize)"):
    if marker not in controller_page:
        raise SystemExit("[FAIL] controller CustomerCardPage clamp is incomplete: " + marker)

service_card_list = method_body(service, r"public\s+List<InvCustomerServiceCardVo>\s+selectList\s*\(")
service_audit_list = method_body(service, r"public\s+List<InvCustomerServiceAuditVo>\s+selectAuditList\s*\(")
service_start_page = method_body(service, r"private\s+void\s+startPage\s*\(")
ordered(
    service_card_list,
    ("requireStoreContext", "safeQuery.setShopDeptId(shopDeptId);", "startPage(pageNum, pageSize);", "mapper.selectCardList(safeQuery)"),
    "service card list pagination",
)
ordered(
    service_audit_list,
    ("requireStoreContext", "safeQuery.setShopDeptId(shopDeptId);", "assertScopedCard", "startPage(pageNum, pageSize);", "mapper.selectAuditList(safeQuery)"),
    "service audit list pagination",
)
for body, target, label in (
    (service_card_list, "return mapper.selectCardList(safeQuery);", "service card list"),
    (service_audit_list, "return mapper.selectAuditList(safeQuery);", "service audit list"),
):
    lines = [line.strip() for line in body.splitlines() if line.strip()]
    target_index = lines.index(target)
    if target_index == 0 or lines[target_index - 1] != "startPage(pageNum, pageSize);":
        raise SystemExit(f"[FAIL] {label} must start local pagination immediately before the target mapper")
normalized_start_page = re.sub(r"\s+", " ", service_start_page)
for marker in ("PageHelper.startPage(Math.max(1, pageNum),", "Math.min(100, Math.max(1, pageSize))"):
    if marker not in normalized_start_page:
        raise SystemExit("[FAIL] service pagination clamp is incomplete: " + marker)

service_test_source = source("erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImplTest.java")
list_page_test = method_body(
    service_test_source,
    r"void\s+cardListStartsBoundedPaginationAfterStoreScopeChecks\s*\(",
)
audit_page_test = method_body(
    service_test_source,
    r"void\s+auditListOverwritesCallerScopeAndReturnsOnlySafeProjection\s*\(",
)
ordered(
    list_page_test,
    ("countUserShopScope", "PageHelper.getLocalPage()).isNull()", "selectDeptTypeById", "PageHelper.getLocalPage()).isNull()", "mapper.selectCardList", "PageHelper.getLocalPage()", ".isNotNull()", "getPageNum()).isEqualTo(1)", "getPageSize()).isEqualTo(100)"),
    "card pagination ordering test",
)
ordered(
    audit_page_test,
    ("mapper.selectCardById", "PageHelper.getLocalPage()).isNull()", "mapper.selectAuditList", "PageHelper.getLocalPage()", ".isNotNull()"),
    "audit pagination ordering test",
)
print("CUSTOMER_SERVICE_CARD_IDEMPOTENCY_HUNK_OK endpoints=4 maxKeyLength=128")
print("CUSTOMER_SERVICE_CARD_PAGINATION_ORDER_OK maxPageSize=100")
PY
        ;;
    --archive)
        [[ "$#" -eq 2 ]] || { usage >&2; exit 2; }
        run_static
        ARCHIVE="$2"
        [[ -s "$ARCHIVE" ]] || { echo "[FAIL] archive not found: $ARCHIVE" >&2; exit 5; }
        EXPECTED_COMMIT="${CUSTOMER_CARD_EXPECTED_COMMIT:-}"
        [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]] || {
            echo '[FAIL] CUSTOMER_CARD_EXPECTED_COMMIT must be the candidate 40-character lowercase Git SHA' >&2
            exit 5
        }
        python3 - "$ARCHIVE" "$EXPECTED_COMMIT" <<'PY'
import io
import json
import re
import sys
import tarfile
import zipfile
from pathlib import PurePosixPath

archive, expected_commit = sys.argv[1:]
manifest_path = "docker/release/customer-service-card-release-20260719.json"
jar_paths = {
    "docker/erp/auth/jar/erp-auth.jar",
    "docker/erp/gateway/jar/erp-gateway.jar",
    "docker/erp/modules/approval/jar/erp-modules-approval.jar",
    "docker/erp/modules/file/jar/erp-modules-file.jar",
    "docker/erp/modules/inventory/jar/erp-modules-inventory.jar",
    "docker/erp/modules/job/jar/erp-modules-job.jar",
    "docker/erp/modules/oa/jar/erp-modules-oa.jar",
    "docker/erp/modules/system/jar/erp-modules-system.jar",
    "docker/erp/visual/monitor/jar/erp-visual-monitor.jar",
}
dockerfile_paths = {
    "docker/erp/auth/dockerfile",
    "docker/erp/gateway/dockerfile",
    "docker/erp/modules/approval/dockerfile",
    "docker/erp/modules/file/dockerfile",
    "docker/erp/modules/inventory/dockerfile",
    "docker/erp/modules/job/dockerfile",
    "docker/erp/modules/oa/dockerfile",
    "docker/erp/modules/system/dockerfile",
    "docker/erp/visual/monitor/dockerfile",
}
required = jar_paths | dockerfile_paths | {
    "docker/nginx/conf/nginx.conf",
    "docker/nginx/conf/nginx.host.conf",
    "docker/nginx/dockerfile",
    "docker/nginx/html/dist/index.html",
    "docker/nginx/html/dist/release-info.json",
    manifest_path,
    "docker/run-erp-service.sh",
}
with tarfile.open(archive, "r:*") as handle:
    members = {}
    for member in handle.getmembers():
        normalized = member.name.removeprefix("./").rstrip("/")
        if normalized:
            if normalized in members:
                raise SystemExit("[FAIL] archive contains duplicate entry: " + normalized)
            members[normalized] = member

name_set = set(members)
missing = sorted(required - name_set)
if missing:
    raise SystemExit("[FAIL] archive is missing runtime artifacts: " + ", ".join(missing))

unsafe = []
for name, member in members.items():
    path = PurePosixPath(name)
    lowered = name.lower()
    if path.is_absolute() or ".." in path.parts:
        unsafe.append(name)
    elif not (member.isfile() or member.isdir()):
        unsafe.append(name)
    elif path.suffix.lower() == ".sql":
        unsafe.append(name)
    elif any(part.lower() in {
        "mysql", "history", "histories", "upload", "uploads", "uploadpath",
        "log", "logs", "cache", "caches", "key", "keys", "secret", "secrets",
    } for part in path.parts):
        unsafe.append(name)
    elif any(part.lower() == ".env" or part.lower().startswith(".env.") for part in path.parts):
        unsafe.append(name)
    elif "new-business-release" in lowered or "new-business-manifest" in lowered:
        unsafe.append(name)
    elif member.isfile() and name not in required and not name.startswith("docker/nginx/html/dist/"):
        unsafe.append(name)
if unsafe:
    raise SystemExit("[FAIL] archive contains forbidden entries: " + ", ".join(sorted(unsafe)))

with tarfile.open(archive, "r:*") as handle:
    for name in required:
        member = members[name]
        if not member.isfile() or member.size <= 0:
            raise SystemExit("[FAIL] required archive file is empty or not regular: " + name)

    manifest = json.load(handle.extractfile(members[manifest_path]))
    expected_manifest_values = {
        "releaseId": "customer-service-card-code-only-20260719",
        "deploymentMode": "code-only",
        "migrationCount": 0,
        "migrations": [],
        "featureFlagMutationCount": 0,
        "featureFlags": [],
        "databaseMutationCount": 0,
        "databaseMutations": [],
    }
    for key, value in expected_manifest_values.items():
        if manifest.get(key) != value:
            raise SystemExit(f"[FAIL] packaged manifest violates code-only contract: {key}")
    if set(manifest.get("runtimeArtifacts", [])) != required:
        raise SystemExit("[FAIL] packaged manifest runtimeArtifacts differ from archive contract")

    release_info = json.load(handle.extractfile(members["docker/nginx/html/dist/release-info.json"]))
    if release_info.get("commit") != expected_commit:
        raise SystemExit("[FAIL] frontend release-info commit differs from candidate")

    for jar_path in sorted(jar_paths):
        jar_bytes = handle.extractfile(members[jar_path]).read()
        try:
            with zipfile.ZipFile(io.BytesIO(jar_bytes)) as jar:
                embedded_commit = None
                for build_info_path in (
                    "BOOT-INF/classes/META-INF/build-info.properties",
                    "META-INF/build-info.properties",
                ):
                    try:
                        build_info = jar.read(build_info_path).decode("utf-8")
                    except KeyError:
                        continue
                    match = re.search(r"(?m)^build\.commit=([^\r\n]+)$", build_info)
                    if match:
                        embedded_commit = match.group(1).strip()
                        break
        except (OSError, zipfile.BadZipFile, UnicodeDecodeError) as exc:
            raise SystemExit("[FAIL] invalid executable JAR: " + jar_path) from exc
        if embedded_commit != expected_commit:
            raise SystemExit("[FAIL] JAR build.commit differs from candidate: " + jar_path)

print(
    "CUSTOMER_SERVICE_CARD_ARCHIVE_OK "
    f"entries={len(members)} jars={len(jar_paths)} dockerfiles={len(dockerfile_paths)} "
    f"commit={expected_commit} migrations=0"
)
PY
        ;;
    -h|--help)
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
