#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_ID="sign-original-placement-export-20260809"
MIGRATION="$ROOT_DIR/sql/erp_oa_sign_labor_contract_placement_v7_20260809.sql"
PREFLIGHT="$ROOT_DIR/scripts/sign-original-placement-export-preflight-20260809.sql"
VERIFIER="$ROOT_DIR/scripts/verify-sign-original-placement-export-release.sh"
MANIFEST="$ROOT_DIR/scripts/sign-original-placement-export-release-20260809.json"
SOURCE_LIST="$ROOT_DIR/scripts/sign-original-placement-export-release-files-20260809.list"
V7_TEMPLATE="$ROOT_DIR/erp-modules/erp-oa/src/main/resources/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"
EXPECTED_V7_SHA="1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558"
BASELINE_COMMIT="b408b7054a481fac692878fcdc24e7ea5bc9cc68"
CANDIDATE_JAR="$ROOT_DIR/erp-modules/erp-oa/target/erp-modules-oa.jar"

usage() {
  cat >&2 <<'EOF'
Usage:
  bash scripts/release-sign-original-placement-export-20260809.sh --source
  bash scripts/release-sign-original-placement-export-20260809.sh --print-gates
  bash scripts/release-sign-original-placement-export-20260809.sh --record-candidate-artifact
  bash scripts/release-sign-original-placement-export-20260809.sh --attest-installed-artifact
  bash scripts/release-sign-original-placement-export-20260809.sh --execute-migration
  bash scripts/release-sign-original-placement-export-20260809.sh --verify-health
  bash scripts/release-sign-original-placement-export-20260809.sh --self-test-route-policy

--execute-migration is fail-closed and requires an approved maintenance window. It never
stops or starts a service itself. Candidate and installed JAR evidence must be generated
by the two artifact modes; the installed JAR and stopped OA endpoint are rechecked before PRE.
EOF
}

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "required environment variable is missing: $name"
}

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

source_snapshot_sha() {
  python3 - "$MANIFEST" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
lines = []
for item in sorted(manifest.get("sourceFiles", []), key=lambda value: value["file"]):
    lines.append(f"{item['file']}\t{item['sha256']}\t{item['gitStatus']}\n")
if len(lines) + len(manifest.get("sourceHashExclusions", [])) != manifest.get(
        "sourceFileCount"):
    raise SystemExit("manifest source snapshot count drift")
print(hashlib.sha256("".join(lines).encode("utf-8")).hexdigest())
PY
}

inject_artifact_provenance() {
  local artifact="$1"
  local manifest_sha snapshot_sha source_list_sha
  manifest_sha="$(sha256_file "$MANIFEST")"
  snapshot_sha="$(source_snapshot_sha)"
  source_list_sha="$(sha256_file "$SOURCE_LIST")"
  python3 - "$artifact" "$RELEASE_ID" "$BASELINE_COMMIT" "$manifest_sha" \
    "$snapshot_sha" "$source_list_sha" <<'PY'
import sys
import zipfile
from pathlib import Path

(artifact_raw, release_id, baseline_commit, manifest_sha, snapshot_sha,
 source_list_sha) = sys.argv[1:]
artifact = Path(artifact_raw).resolve(strict=True)
entry = "META-INF/erp-sign-placement-release.properties"
payload = "\n".join([
    f"releaseId={release_id}",
    f"baselineCommit={baseline_commit}",
    f"sourceManifestSha256={manifest_sha}",
    f"sourceSnapshotSha256={snapshot_sha}",
    f"sourceListSha256={source_list_sha}",
    "",
]).encode("ascii")
with zipfile.ZipFile(artifact, "a") as archive:
    if entry in archive.namelist():
        raise SystemExit("artifact provenance entry already exists")
    info = zipfile.ZipInfo(entry, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    archive.writestr(info, payload)
PY
}

assert_oa_stopped() {
  require_env ERP_SIGN_OA_HEALTH_URL
  if curl --silent --show-error --max-time 3 --output /dev/null \
      "$ERP_SIGN_OA_HEALTH_URL"; then
    fail "OA endpoint still responds; stopped-service gate is not satisfied"
  fi
}

write_artifact_attestation() {
  local stage="$1"
  local artifact="$2"
  local evidence="$3"
  local expected_sha="${4:-}"
  local manifest_sha snapshot_sha
  manifest_sha="$(sha256_file "$MANIFEST")"
  snapshot_sha="$(source_snapshot_sha)"
  python3 - "$stage" "$artifact" "$evidence" "$expected_sha" "$EXPECTED_V7_SHA" \
    "$RELEASE_ID" "$BASELINE_COMMIT" "$manifest_sha" "$snapshot_sha" <<'PY'
import hashlib
import json
import os
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

(stage, artifact_raw, evidence_raw, expected_sha, expected_v7_sha, release_id,
 baseline_commit, manifest_sha, snapshot_sha) = sys.argv[1:]
artifact = Path(artifact_raw).resolve(strict=True)
evidence = Path(evidence_raw).resolve()
if not artifact.is_file():
    raise SystemExit("artifact is not a regular file")

def digest_bytes(value):
    return hashlib.sha256(value).hexdigest()

def digest_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

required = [
    "com/erp/oa/service/impl/OaSignDocumentService.class",
    "com/erp/oa/service/impl/OaSignLaborAnchorPlacementResolver.class",
    "com/erp/oa/service/impl/OaSignPackageServiceImpl.class",
    "com/erp/oa/service/impl/OaPdfPageNumberService.class",
    "com/erp/oa/service/impl/OaSignLaborPlacementProfileRegistry.class",
    "com/erp/oa/service/impl/OaSignPlacementPolicyService.class",
    "com/erp/oa/service/impl/OaSignPlanVersionFingerprint.class",
    "com/erp/oa/service/impl/OaSignedPdfService.class",
    "oa/sign/labor-contract-placement-profiles-v1.json",
    "oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx",
    "META-INF/build-info.properties",
    "META-INF/erp-sign-placement-release.properties",
]
artifact_sha = digest_file(artifact)
if expected_sha and artifact_sha != expected_sha:
    raise SystemExit("installed artifact SHA-256 differs from recorded candidate")
with zipfile.ZipFile(artifact) as archive:
    names = archive.namelist()
    resolved = {}
    for suffix in required:
        matches = [name for name in names if name == suffix or name.endswith("/" + suffix)]
        if len(matches) != 1:
            raise SystemExit(f"artifact entry must resolve exactly once: {suffix} ({len(matches)})")
        resolved[suffix] = matches[0]
    v7_bytes = archive.read(resolved[
        "oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"])
    if digest_bytes(v7_bytes) != expected_v7_sha:
        raise SystemExit("exact-v7 bytes embedded in artifact do not match the reviewed SHA-256")
    build_info = archive.read(resolved["META-INF/build-info.properties"]).decode(
        "utf-8", errors="strict")
    if "UNSET" in build_info:
        raise SystemExit("artifact build metadata contains UNSET")
    properties = {}
    for line in build_info.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    expected_properties = {
        "build.commit": baseline_commit,
        "build.releaseId": release_id,
        "build.approvedPatchSha256": snapshot_sha,
        "build.approvedSourceManifestSha256": manifest_sha,
    }
    for key, value in expected_properties.items():
        if properties.get(key) != value:
            raise SystemExit(f"artifact build metadata drift: {key}")
    provenance = archive.read(resolved[
        "META-INF/erp-sign-placement-release.properties"]).decode(
            "ascii", errors="strict")
    provenance_properties = {}
    for line in provenance.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            provenance_properties[key] = value
    expected_provenance = {
        "releaseId": release_id,
        "baselineCommit": baseline_commit,
        "sourceManifestSha256": manifest_sha,
        "sourceSnapshotSha256": snapshot_sha,
    }
    for key, value in expected_provenance.items():
        if provenance_properties.get(key) != value:
            raise SystemExit(f"artifact provenance drift: {key}")

payload = {
    "schemaVersion": 1,
    "releaseId": release_id,
    "stage": stage,
    "recordedAtUtc": datetime.now(timezone.utc).isoformat(),
    "artifactPath": str(artifact),
    "artifactSize": artifact.stat().st_size,
    "artifactSha256": artifact_sha,
    "exactV7EmbeddedSha256": expected_v7_sha,
    "baselineCommit": baseline_commit,
    "approvedSourceManifestSha256": manifest_sha,
    "approvedSourceSnapshotSha256": snapshot_sha,
    "buildInfoProperties": expected_properties,
    "requiredEntries": required,
    "resolvedEntries": resolved,
    "buildMetadataUnset": False,
}
evidence.parent.mkdir(parents=True, exist_ok=True)
temporary = evidence.with_name(evidence.name + ".tmp")
temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                     encoding="utf-8")
os.replace(temporary, evidence)
PY
}

verify_artifact_evidence() {
  local artifact="$1"
  local evidence="$2"
  local expected_stage="$3"
  local manifest_sha snapshot_sha
  manifest_sha="$(sha256_file "$MANIFEST")"
  snapshot_sha="$(source_snapshot_sha)"
  python3 - "$artifact" "$evidence" "$expected_stage" "$RELEASE_ID" \
    "$BASELINE_COMMIT" "$manifest_sha" "$snapshot_sha" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

artifact = Path(sys.argv[1]).resolve(strict=True)
evidence = Path(sys.argv[2]).resolve(strict=True)
(expected_stage, release_id, baseline_commit, manifest_sha,
 snapshot_sha) = sys.argv[3:]
data = json.loads(evidence.read_text(encoding="utf-8"))
digest = hashlib.sha256()
with artifact.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
if data.get("stage") != expected_stage:
    raise SystemExit("artifact evidence stage drift")
if data.get("artifactPath") != str(artifact):
    raise SystemExit("artifact evidence path drift")
if data.get("artifactSha256") != digest.hexdigest():
    raise SystemExit("artifact bytes drifted after attestation")
if data.get("buildMetadataUnset") is not False:
    raise SystemExit("artifact build metadata was not approved")
if data.get("releaseId") != release_id or data.get("baselineCommit") != baseline_commit:
    raise SystemExit("artifact release identity drift")
if data.get("approvedSourceManifestSha256") != manifest_sha:
    raise SystemExit("artifact source-manifest digest drift")
if data.get("approvedSourceSnapshotSha256") != snapshot_sha:
    raise SystemExit("artifact approved source snapshot drift")
if data.get("buildInfoProperties") != {
        "build.commit": baseline_commit,
        "build.releaseId": release_id,
        "build.approvedPatchSha256": snapshot_sha,
        "build.approvedSourceManifestSha256": manifest_sha,
}:
    raise SystemExit("artifact build-info provenance drift")
PY
}

candidate_artifact_sha() {
  python3 - "$1" <<'PY'
import json
import sys
from pathlib import Path
data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
value = data.get("artifactSha256")
if not isinstance(value, str) or len(value) != 64:
    raise SystemExit("candidate artifact SHA-256 evidence is invalid")
print(value)
PY
}

validate_read_only_signing_route_policy() {
  local health_url="$1"
  local routes_file="$2"
  local auth_header_file="$3"
  [[ -r "$routes_file" ]] || { echo "read-only route URL file is unreadable" >&2; return 1; }
  [[ -r "$auth_header_file" ]] \
    || { echo "read-only authorization header file is unreadable" >&2; return 1; }
  python3 - "$health_url" "$routes_file" "$auth_header_file" <<'PY'
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

health_url, routes_raw, header_raw = sys.argv[1:]
header_bytes = Path(header_raw).read_bytes()
if b"\r" in header_bytes:
    raise SystemExit("authorization header file contains CR bytes")
try:
    header = header_bytes.decode("ascii")
except UnicodeDecodeError as exc:
    raise SystemExit("authorization header must be ASCII") from exc
lines = header.splitlines()
if len(lines) != 1 or not re.fullmatch(
        r"Authorization:[ \t]+Bearer[ \t]+[A-Za-z0-9._~+/=-]+", lines[0]):
    raise SystemExit("authorization header file must contain exactly one Bearer header")

health = urlsplit(health_url)
if health.scheme not in {"http", "https"} or not health.hostname:
    raise SystemExit("OA health URL is invalid")
health_origin = (health.scheme.lower(), health.hostname.lower(),
                 health.port or (443 if health.scheme.lower() == "https" else 80))
urls = [line.strip() for line in Path(routes_raw).read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")]
if len(urls) != 2 or len(set(urls)) != 2:
    raise SystemExit("route file must contain exactly two unique URLs")
approved_paths = {"/signPackage/scope/options", "/signPackage/template/types"}
actual_paths = set()
for value in urls:
    parts = urlsplit(value)
    origin = (parts.scheme.lower(), (parts.hostname or "").lower(),
              parts.port or (443 if parts.scheme.lower() == "https" else 80))
    if origin != health_origin:
        raise SystemExit("route origin differs from the OA health origin")
    if parts.username or parts.password or parts.query or parts.fragment:
        raise SystemExit("route URLs must not contain credentials, query, or fragment")
    actual_paths.add(parts.path)
if actual_paths != approved_paths:
    raise SystemExit(f"route paths must equal the approved read-only set: {approved_paths}")
print("\n".join(urls))
PY
}

self_test_read_only_signing_route_policy() {
  local test_root routes_file header_file health_url
  test_root="$(mktemp -d "${TMPDIR:-/tmp}/erp-sign-route-policy-test.XXXXXX")"
  routes_file="$test_root/routes.txt"
  header_file="$test_root/authorization.txt"
  health_url="https://oa.example.invalid/actuator/health"
  trap 'rm -rf -- "$test_root"' EXIT

  printf '%s\n' 'Authorization: Bearer synthetic.test-token' > "$header_file"
  printf '%s\n' \
    'https://oa.example.invalid/signPackage/scope/options' \
    'https://oa.example.invalid/signPackage/template/types' > "$routes_file"
  validate_read_only_signing_route_policy "$health_url" "$routes_file" "$header_file" \
    >/dev/null || fail "valid read-only route policy was rejected"

  expect_route_policy_failure() {
    if validate_read_only_signing_route_policy "$health_url" "$routes_file" "$header_file" \
      >/dev/null 2>&1
    then
      fail "invalid read-only route policy was accepted: $1"
    fi
  }

  printf '%s\n' 'https://oa.example.invalid/signPackage/scope/options' > "$routes_file"
  expect_route_policy_failure "missing route"
  printf '%s\n' \
    'https://oa.example.invalid/signPackage/scope/options' \
    'https://oa.example.invalid/signPackage/scope/options' > "$routes_file"
  expect_route_policy_failure "duplicate route"
  printf '%s\n' \
    'https://oa.example.invalid/signPackage/scope/options' \
    'https://other.example.invalid/signPackage/template/types' > "$routes_file"
  expect_route_policy_failure "cross-origin route"
  printf '%s\n' \
    'https://oa.example.invalid/signPackage/scope/options?scope=all' \
    'https://oa.example.invalid/signPackage/template/types' > "$routes_file"
  expect_route_policy_failure "query string"
  printf '%s\n' \
    'https://oa.example.invalid/signPackage/scope/options' \
    'https://oa.example.invalid/signPackage/list' > "$routes_file"
  expect_route_policy_failure "unapproved path"
  printf '%s\n' 'Cookie: forbidden=value' > "$header_file"
  printf '%s\n' \
    'https://oa.example.invalid/signPackage/scope/options' \
    'https://oa.example.invalid/signPackage/template/types' > "$routes_file"
  expect_route_policy_failure "invalid bearer header"
  rm -rf -- "$test_root"
  trap - EXIT
  echo "READ_ONLY_SIGNING_ROUTE_POLICY_SELF_TEST_PASSED cases=7 network=disabled"
}

verify_read_only_signing_routes() {
  require_env ERP_SIGN_READ_ONLY_ROUTE_URLS_FILE
  require_env ERP_SIGN_READ_ONLY_AUTH_HEADER_FILE
  local validated_routes
  validated_routes="$(validate_read_only_signing_route_policy \
    "$ERP_SIGN_OA_HEALTH_URL" "$ERP_SIGN_READ_ONLY_ROUTE_URLS_FILE" \
    "$ERP_SIGN_READ_ONLY_AUTH_HEADER_FILE")" \
    || fail "read-only route/header policy validation failed"

  local route_evidence="$ERP_SIGN_RELEASE_AUDIT_DIR/read-only-signing-routes.jsonl"
  : > "$route_evidence"
  local count=0
  while IFS= read -r route_url; do
    [[ -n "$route_url" ]] || continue
    count=$((count + 1))
    local body_file header_file http_status
    body_file="$(mktemp "${TMPDIR:-/tmp}/erp-sign-route-body.XXXXXX")"
    header_file="$(mktemp "${TMPDIR:-/tmp}/erp-sign-route-header.XXXXXX")"
    http_status="$(curl --silent --show-error --request GET \
      --connect-timeout 3 --max-time 15 \
      --header "@$ERP_SIGN_READ_ONLY_AUTH_HEADER_FILE" \
      --dump-header "$header_file" --output "$body_file" \
      --write-out '%{http_code}' "$route_url")" \
      || { rm -f -- "$body_file" "$header_file"; fail "read-only signing route request failed"; }
    if ! python3 - "$route_url" "$http_status" "$header_file" "$body_file" \
      "$route_evidence" <<'PY'
import json
import sys
from pathlib import Path
from urllib.parse import urlsplit

url, status_raw, header_raw, body_raw, evidence_raw = sys.argv[1:]
status = int(status_raw)
if status < 200 or status >= 300:
    raise SystemExit(f"read-only route returned HTTP {status}")
headers = Path(header_raw).read_text(encoding="iso-8859-1", errors="replace")
if "application/json" not in headers.lower():
    raise SystemExit("read-only route response is not JSON")
payload = json.loads(Path(body_raw).read_text(encoding="utf-8"))
if not isinstance(payload, (dict, list)):
    raise SystemExit("read-only route response contract is not an object or array")
business_code = payload.get("code") if isinstance(payload, dict) else None
if business_code not in (0, 200, "0", "200"):
    raise SystemExit(f"read-only route business code is not successful: {business_code}")
parts = urlsplit(url)
record = {
    "method": "GET",
    "origin": f"{parts.scheme}://{parts.netloc}",
    "path": parts.path,
    "httpStatus": status,
    "contentType": "application/json",
    "businessCode": business_code,
    "responseBodyPersisted": False,
}
with Path(evidence_raw).open("a", encoding="utf-8") as stream:
    stream.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")
PY
    then
      rm -f -- "$body_file" "$header_file"
      fail "read-only signing route response contract failed"
    fi
    rm -f -- "$body_file" "$header_file"
  done <<< "$validated_routes"
  [[ "$count" -eq 2 ]] || fail "exactly two read-only signing routes are required"
  [[ "$(wc -l < "$route_evidence" | tr -d ' ')" -eq "$count" ]] \
    || fail "route smoke evidence count drift"
}

assert_json_gate() {
  local evidence="$1"
  local expected_stage="$2"
  python3 - "$evidence" "$expected_stage" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
stage = sys.argv[2]
data = json.loads(path.read_text(encoding="utf-8"))
if data.get("databaseVerifierExecuted") is not True:
    raise SystemExit("database verifier did not execute")
if data.get("stage") != stage:
    raise SystemExit(f"database verifier stage drift: {data.get('stage')} != {stage}")
identity = data.get("databaseIdentity")
if not isinstance(identity, dict) or set(identity) != {
        "serverUuid", "serverHostname", "serverPort", "databaseName"}:
    raise SystemExit("Java database identity evidence is incomplete")
if data.get("unsupportedNonTerminalLaborGroupCount") != 0:
    raise SystemExit("unsupported non-terminal labor contracts exist")
if data.get("unsupportedGroupCount") != 0:
    raise SystemExit("unsupported signed labor-contract groups exist")
if data.get("historicalCoverageStatus") not in {
    "FORMAL_EXPORT_DRY_RUN_VERIFIED",
    "COVERAGE_ZERO_NO_HISTORICAL_EXPORT_CLAIM",
}:
    raise SystemExit("historical coverage state is not auditable")
if data.get("signedConfirmedLaborContractDocumentCount") == 0 and data.get(
        "historicalCoverageStatus") != "COVERAGE_ZERO_NO_HISTORICAL_EXPORT_CLAIM":
    raise SystemExit("zero historical samples must not be reported as verified coverage")
PY
}

run_database_verifier() {
  local stage="$1"
  local evidence="$2"
  ERP_SIGN_PLACEMENT_VERIFY_STAGE="$stage" \
  ERP_SIGN_PLACEMENT_EVIDENCE_OUTPUT="$evidence" \
  "$ROOT_DIR/mvnw" -pl erp-modules/erp-oa -am \
    '-Dtest=OaSignLaborPlacementDatabaseVerifierTest#shouldRecomputeCompleteSnapshotsAndFailClosedUnknownHistoricalGroups' \
    -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
  [[ -s "$evidence" ]] || fail "$stage database verifier evidence was not written"
  assert_json_gate "$evidence" "$stage"
}

record_cli_database_identity() {
  local evidence="$1"
  local identity_row
  identity_row="$(mysql --defaults-extra-file="$ERP_SIGN_MYSQL_DEFAULTS_FILE" \
    --database="$ERP_SIGN_MYSQL_DATABASE" --batch --raw --skip-column-names \
    --execute="SELECT @@server_uuid, @@hostname, @@port, DATABASE()")"
  python3 - "$identity_row" "$evidence" <<'PY'
import json
import os
import sys
from pathlib import Path

parts = sys.argv[1].split("\t")
if len(parts) != 4 or not all(parts):
    raise SystemExit("CLI database identity query did not return four complete fields")
payload = {
    "serverUuid": parts[0],
    "serverHostname": parts[1],
    "serverPort": int(parts[2]),
    "databaseName": parts[3],
}
path = Path(sys.argv[2]).resolve()
path.parent.mkdir(parents=True, exist_ok=True)
temporary = path.with_name(path.name + ".tmp")
temporary.write_text(json.dumps(payload, sort_keys=True, indent=2) + "\n",
                     encoding="utf-8")
os.replace(temporary, path)
PY
}

assert_same_database_identity() {
  local cli_evidence="$1"
  local java_evidence="$2"
  python3 - "$cli_evidence" "$java_evidence" <<'PY'
import json
import sys
from pathlib import Path

cli = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
java = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8")).get(
    "databaseIdentity")
if cli != java:
    raise SystemExit(f"CLI/JDBC database identity mismatch: cli={cli} jdbc={java}")
PY
}

print_gates() {
  cat <<'EOF'
1. VERIFY_SOURCE_MANIFEST_AND_CODE_COMPATIBILITY
2. VERIFY_EXACT_V7_SOURCE_DOCX_SHA256
3. BUILD_AND_RECORD_CANDIDATE_OA_ARTIFACT_SHA256
4. STOP_PACKAGE_WRITES_AND_OA_SERVICE
5. INSTALL_AND_ATTEST_OA_ARTIFACT_WHILE_STOPPED
6. RUN_READ_ONLY_PREFLIGHT_AND_JAVA_DB_GATE_PRE_SKIP_ZERO
7. APPLY_IMMUTABLE_MIGRATION_ONCE
8. RUN_JAVA_DB_GATE_POST_SKIP_ZERO
9. START_OA_SERVICE
10. VERIFY_OA_HEALTH_UP
11. VERIFY_READ_ONLY_SIGNING_ROUTES

Rollback is allowed only before any package references a candidate plan version. Once a
candidate package exists, matching rollback is prohibited and a new reviewed decision is needed.
EOF
}

case "${1:-}" in
  --self-test-route-policy)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    self_test_read_only_signing_route_policy
    ;;
  --source)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    bash "$VERIFIER" --source
    ;;
  --print-gates)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    print_gates
    ;;
  --record-candidate-artifact)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    bash "$VERIFIER" --approved
    require_env ERP_SIGN_RELEASE_AUDIT_DIR
    mkdir -p "$ERP_SIGN_RELEASE_AUDIT_DIR"
    umask 077
    [[ "$(sha256_file "$V7_TEMPLATE")" == "$EXPECTED_V7_SHA" ]] \
      || fail "exact-v7 source DOCX SHA-256 drifted"
    approved_manifest_sha="$(sha256_file "$MANIFEST")"
    approved_snapshot_sha="$(source_snapshot_sha)"
    "$ROOT_DIR/mvnw" -pl erp-modules/erp-oa -am clean package -DskipTests \
      "-Dbuild.commit=$BASELINE_COMMIT" \
      "-Dbuild.releaseId=$RELEASE_ID" \
      "-Dbuild.approvedPatchSha256=$approved_snapshot_sha" \
      "-Dbuild.approvedSourceManifestSha256=$approved_manifest_sha"
    [[ -r "$CANDIDATE_JAR" ]] || fail "fixed candidate OA JAR was not built"
    inject_artifact_provenance "$CANDIDATE_JAR"
    write_artifact_attestation candidate "$CANDIDATE_JAR" \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/candidate-artifact.json"
    echo "CANDIDATE_ARTIFACT_RECORDED"
    ;;
  --attest-installed-artifact)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    bash "$VERIFIER" --approved
    require_env ERP_SIGN_OA_INSTALLED_JAR
    require_env ERP_SIGN_RELEASE_AUDIT_DIR
    [[ "${ERP_SIGN_PACKAGE_WRITES_BLOCKED:-false}" == "true" ]] \
      || fail "new package writes are not proven blocked"
    [[ "${ERP_SIGN_OA_SERVICE_STOPPED:-false}" == "true" ]] \
      || fail "OA service is not declared stopped"
    assert_oa_stopped
    local_candidate_evidence="$ERP_SIGN_RELEASE_AUDIT_DIR/candidate-artifact.json"
    [[ -s "$local_candidate_evidence" ]] || fail "candidate artifact evidence is missing"
    verify_artifact_evidence "$CANDIDATE_JAR" \
      "$local_candidate_evidence" candidate
    expected_candidate_sha="$(candidate_artifact_sha "$local_candidate_evidence")"
    write_artifact_attestation installed "$ERP_SIGN_OA_INSTALLED_JAR" \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/installed-artifact.json" "$expected_candidate_sha"
    echo "INSTALLED_ARTIFACT_ATTESTED_WHILE_OA_STOPPED"
    ;;
  --execute-migration)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    bash "$VERIFIER" --approved
    [[ "${ERP_SIGN_RELEASE_AUTHORIZED:-false}" == "true" ]] \
      || fail "release authorization is not present"
    [[ "${ERP_SIGN_PACKAGE_WRITES_BLOCKED:-false}" == "true" ]] \
      || fail "new package writes are not proven blocked"
    [[ "${ERP_SIGN_OA_SERVICE_STOPPED:-false}" == "true" ]] \
      || fail "OA service is not proven stopped"
    [[ "${ERP_SIGN_PLACEMENT_DB_VERIFY:-false}" == "true" ]] \
      || fail "Java database verifier is not explicitly enabled"
    require_env ERP_SIGN_MYSQL_DEFAULTS_FILE
    require_env ERP_SIGN_MYSQL_DATABASE
    require_env ERP_SIGN_PLACEMENT_JDBC_URL
    require_env ERP_SIGN_PLACEMENT_JDBC_USER
    require_env ERP_SIGN_PLACEMENT_FILE_ROOT
    require_env ERP_SIGN_PLACEMENT_STORAGE_ROOT
    require_env ERP_SIGN_PLACEMENT_DRY_RUN_TEMP
    require_env ERP_SIGN_RELEASE_AUDIT_DIR
    require_env ERP_SIGN_OA_INSTALLED_JAR
    require_env ERP_SIGN_OA_HEALTH_URL
    [[ -r "$ERP_SIGN_MYSQL_DEFAULTS_FILE" ]] || fail "MySQL defaults file is unreadable"
    mkdir -p "$ERP_SIGN_RELEASE_AUDIT_DIR"
    umask 077

    assert_oa_stopped
    candidate_evidence="$ERP_SIGN_RELEASE_AUDIT_DIR/candidate-artifact.json"
    installed_evidence="$ERP_SIGN_RELEASE_AUDIT_DIR/installed-artifact.json"
    [[ -s "$candidate_evidence" && -s "$installed_evidence" ]] \
      || fail "candidate/installed artifact evidence is incomplete"
    verify_artifact_evidence "$CANDIDATE_JAR" \
      "$candidate_evidence" candidate
    verify_artifact_evidence "$ERP_SIGN_OA_INSTALLED_JAR" \
      "$installed_evidence" installed
    [[ "$(candidate_artifact_sha "$candidate_evidence")" == \
       "$(candidate_artifact_sha "$installed_evidence")" ]] \
      || fail "installed OA artifact is not the recorded candidate"

    [[ "$(sha256_file "$V7_TEMPLATE")" == "$EXPECTED_V7_SHA" ]] \
      || fail "exact-v7 source DOCX SHA-256 drifted"

    "$ROOT_DIR/mvnw" -pl erp-modules/erp-oa -am \
      '-Dtest=OaPdfPageNumberServiceTest,OaSignDocumentServiceTest,OaSignPlacementPolicyServiceTest,OaSignedPdfServiceTest,OaSignFinalFileAndIdempotencyTest,OaSignLaborPlacementReleaseMigrationTest,OaSignPlanVersionFingerprintTest' \
      -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test

    record_cli_database_identity \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-identity-cli-pre.json"
    mysql --defaults-extra-file="$ERP_SIGN_MYSQL_DEFAULTS_FILE" \
      --database="$ERP_SIGN_MYSQL_DATABASE" --batch --raw \
      < "$PREFLIGHT" > "$ERP_SIGN_RELEASE_AUDIT_DIR/preflight-pre.tsv"
    run_database_verifier PRE "$ERP_SIGN_RELEASE_AUDIT_DIR/database-verifier-pre.json"
    assert_same_database_identity \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-identity-cli-pre.json" \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-verifier-pre.json"

    mysql --defaults-extra-file="$ERP_SIGN_MYSQL_DEFAULTS_FILE" \
      --database="$ERP_SIGN_MYSQL_DATABASE" < "$MIGRATION" \
      > "$ERP_SIGN_RELEASE_AUDIT_DIR/migration.stdout"
    record_cli_database_identity \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-identity-cli-post.json"
    run_database_verifier POST "$ERP_SIGN_RELEASE_AUDIT_DIR/database-verifier-post.json"
    assert_same_database_identity \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-identity-cli-post.json" \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-verifier-post.json"
    assert_same_database_identity \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-identity-cli-pre.json" \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/database-verifier-post.json"
    mysql --defaults-extra-file="$ERP_SIGN_MYSQL_DEFAULTS_FILE" \
      --database="$ERP_SIGN_MYSQL_DATABASE" --batch --raw \
      < "$PREFLIGHT" > "$ERP_SIGN_RELEASE_AUDIT_DIR/preflight-post.tsv"

    printf '%s\n' "$RELEASE_ID" > "$ERP_SIGN_RELEASE_AUDIT_DIR/MIGRATION_APPLIED_OA_STILL_STOPPED.marker"
    echo "MIGRATION_GATE_PASSED: start OA externally, then run --verify-health"
    ;;
  --verify-health)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    bash "$VERIFIER" --approved
    require_env ERP_SIGN_RELEASE_AUDIT_DIR
    require_env ERP_SIGN_OA_HEALTH_URL
    require_env ERP_SIGN_OA_INSTALLED_JAR
    [[ -f "$ERP_SIGN_RELEASE_AUDIT_DIR/MIGRATION_APPLIED_OA_STILL_STOPPED.marker" ]] \
      || fail "migration completion marker is missing"
    verify_artifact_evidence "$ERP_SIGN_OA_INSTALLED_JAR" \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/installed-artifact.json" installed
    curl --fail --silent --show-error "$ERP_SIGN_OA_HEALTH_URL" \
      > "$ERP_SIGN_RELEASE_AUDIT_DIR/oa-health.json"
    grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' \
      "$ERP_SIGN_RELEASE_AUDIT_DIR/oa-health.json" || fail "OA health is not UP"
    verify_read_only_signing_routes
    echo "OA_HEALTH_AND_READ_ONLY_SIGNING_ROUTE_GATES_PASSED"
    ;;
  *)
    usage
    exit 2
    ;;
esac
