#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/scripts/sign-original-placement-export-release-20260809.json"
SOURCE_LIST="$ROOT_DIR/scripts/sign-original-placement-export-release-files-20260809.list"
MIGRATION_LIST="$ROOT_DIR/scripts/sign-original-placement-export-migrations-20260809.list"
WRAPPER="$ROOT_DIR/scripts/release-sign-original-placement-export-20260809.sh"

usage() {
  echo "Usage: bash scripts/verify-sign-original-placement-export-release.sh --source|--approved" >&2
}

[[ "$#" -eq 1 && ( "${1:-}" == "--source" || "${1:-}" == "--approved" ) ]] \
  || { usage; exit 2; }
VERIFY_MODE="${1#--}"
command -v python3 >/dev/null 2>&1 || { echo "[FAIL] python3 is required" >&2; exit 3; }
bash -n "$WRAPPER"
bash -n "$0"

python3 - "$ROOT_DIR" "$MANIFEST" "$SOURCE_LIST" "$MIGRATION_LIST" "$WRAPPER" \
  "$VERIFY_MODE" <<'PY'
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
manifest_path = Path(sys.argv[2]).resolve()
source_list_path = Path(sys.argv[3]).resolve()
migration_list_path = Path(sys.argv[4]).resolve()
wrapper_path = Path(sys.argv[5]).resolve()
verify_mode = sys.argv[6]

RELEASE_ID = "sign-original-placement-export-20260809"
BASELINE_COMMIT = "b408b7054a481fac692878fcdc24e7ea5bc9cc68"
MIGRATION = "erp_oa_sign_labor_contract_placement_v7_20260809.sql"
MIGRATION_SHA = "829ae042311b6b4bc14f5969062951f4991fdc84340a60272a9e103bf35f1584"
V7_SHA = "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558"
EXPECTED_GATES = [
    "VERIFY_SOURCE_MANIFEST_AND_CODE_COMPATIBILITY",
    "VERIFY_EXACT_V7_SOURCE_DOCX_SHA256",
    "BUILD_AND_RECORD_CANDIDATE_OA_ARTIFACT_SHA256",
    "STOP_PACKAGE_WRITES_AND_OA_SERVICE",
    "INSTALL_AND_ATTEST_OA_ARTIFACT_WHILE_STOPPED",
    "RUN_READ_ONLY_PREFLIGHT_AND_JAVA_DB_GATE_PRE_SKIP_ZERO",
    "APPLY_IMMUTABLE_MIGRATION_ONCE",
    "RUN_JAVA_DB_GATE_POST_SKIP_ZERO",
    "START_OA_SERVICE",
    "VERIFY_OA_HEALTH_UP",
    "VERIFY_READ_ONLY_SIGNING_ROUTES",
]
EXPECTED_SOURCE_PATHS = set("""
.gitignore
docker/mysql/db/erp_oa_sign_labor_contract_placement_v7_20260809.sql
docs/releases/evidence/sign-original-placement-export-20260809-scratch.json
erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java
erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersionTemplate.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTemplate.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignPackageFile.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/SignedPdfResult.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignLaborAnchorPlacementResolver.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignLaborPlacementProfileRegistry.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaPdfPageNumberService.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlacementPolicyService.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionFingerprint.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java
erp-modules/erp-oa/src/main/resources/db/migration/erp_oa_sign_labor_contract_placement_v7_20260809.sql
erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageDocumentMapper.xml
erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml
erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml
erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTemplateMapper.xml
erp-modules/erp-oa/src/main/resources/oa/sign/evidence/legal-entity-4-representative-20260719-v1.json
erp-modules/erp-oa/src/main/resources/oa/sign/labor-contract-placement-profiles-v1.json
erp-modules/erp-oa/src/main/resources/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx
erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignTemplateTypeTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignLaborPlacementReleaseMigrationTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignFinalFileAndIdempotencyTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignGoldenFixture.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignLaborPlacementDatabaseVerifierTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackagePreflightValidatorTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaPdfPageNumberServiceTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlacementPolicyServiceTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanVersionFingerprintTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImplTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignedPdfServiceTest.java
erp-modules/erp-oa/src/test/resources/oa/sign/release/labor-placement-plan-snapshots-v1.json
erp-ui/src/api/oa/signPackage.js
erp-ui/src/views/mobile/signPackage/index.vue
erp-ui/src/views/oa/signPackage/index.vue
erp-ui/test/signOriginalPlacementExportRelease.test.js
erp-ui/test/signPackageDesktopExport.test.js
erp-ui/test/signPackageMobilePreview.test.js
erp-ui/test/signPackageModule.test.js
scripts/release-sign-original-placement-export-20260809.sh
scripts/sign-original-placement-export-migrations-20260809.list
scripts/sign-original-placement-export-preflight-20260809.sql
scripts/sign-original-placement-export-release-20260809.json
scripts/sign-original-placement-export-release-files-20260809.list
scripts/sign-original-placement-export-rollback-20260809.sql
scripts/verify-sign-original-placement-export-release.sh
sql/erp_oa_sign_labor_contract_placement_v7_20260809.sql
""".strip().splitlines())
EXPECTED_BUILD_INPUT_PATHS = [
    "pom.xml",
    "erp-common/pom.xml",
    "erp-common/erp-common-core/pom.xml",
    "erp-api/pom.xml",
    "erp-api/erp-api-system/pom.xml",
    "erp-common/erp-common-redis/pom.xml",
    "erp-common/erp-common-security/pom.xml",
    "erp-common/erp-common-log/pom.xml",
    "erp-common/erp-common-swagger/pom.xml",
    "erp-common/erp-common-datascope/pom.xml",
    "erp-common/erp-common-datasource/pom.xml",
    "erp-api/erp-api-oa/pom.xml",
    "erp-api/erp-api-approval/pom.xml",
    "erp-modules/pom.xml",
    "erp-modules/erp-oa/pom.xml",
]


def fail(message: str) -> None:
    raise SystemExit("[FAIL] " + message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_list(path: Path) -> list[str]:
    if not path.is_file():
        fail(f"missing list: {path.relative_to(root)}")
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def git_status(relative: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), "status", "--porcelain=v1", "--untracked-files=all", "--", relative],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout[:2] if result.stdout else ".."


def strip_sql_comments(value: str) -> str:
    return "\n".join(
        line for line in value.splitlines() if not line.lstrip().startswith("--")
    )


try:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    fail(f"invalid manifest: {exc}")

if manifest.get("schemaVersion") != 1 or manifest.get("releaseId") != RELEASE_ID:
    fail("manifest schema or releaseId drift")
if verify_mode == "source":
    if manifest.get("status") != "development":
        fail("source review expects development status")
    if manifest.get("deployable") is not False or manifest.get(
            "deploymentAuthorized") is not False:
        fail("development release must not be deployable or authorized")
    if manifest.get("approvalRecord") is not None:
        fail("development release must not contain an approval record")
elif verify_mode == "approved":
    if manifest.get("status") != "approved" or manifest.get(
            "approvalState") != "project-manager-approved":
        fail("approved gate requires project-manager-approved manifest status")
    if manifest.get("deployable") is not True or manifest.get(
            "deploymentAuthorized") is not True:
        fail("approved gate requires deployable and deploymentAuthorized")
    approval = manifest.get("approvalRecord")
    if not isinstance(approval, dict) or approval.get(
            "projectManagerApproved") is not True:
        fail("approved gate requires a project-manager approval record")
    for key in ("approvedAtUtc", "approvalReference", "authorizationScope"):
        if not isinstance(approval.get(key), str) or not approval[key].strip():
            fail(f"approved gate approval record is incomplete: {key}")
else:
    fail("unknown verifier mode")
if manifest.get("baselineCommit") != BASELINE_COMMIT:
    fail("baseline commit drift")
if manifest.get("baselineCommitSemantics") != "build-baseline-only-dirty-worktree-not-reproducible-by-commit":
    fail("dirty baseline semantics are missing")
if manifest.get("workingTreeReproducible") is not False:
    fail("dirty worktree must not be described as commit-reproducible")
pinned_build_inputs = manifest.get("pinnedBuildInputs", {})
if pinned_build_inputs.get("scope") != (
        "cross-release-reactor-build-inputs-not-rev06-business-source"):
    fail("pinned build inputs must remain separate from REV-06 business source")
if pinned_build_inputs.get("reactor") != (
        "./mvnw -pl erp-modules/erp-oa -am clean package"):
    fail("pinned build reactor command drift")
build_input_items = pinned_build_inputs.get("files", [])
if pinned_build_inputs.get("fileCount") != len(EXPECTED_BUILD_INPUT_PATHS) or len(
        build_input_items) != len(EXPECTED_BUILD_INPUT_PATHS):
    fail("pinned build input count drift")
if [item.get("file") for item in build_input_items] != EXPECTED_BUILD_INPUT_PATHS:
    fail("pinned build input path/order drift")
for item in build_input_items:
    relative = item["file"]
    recorded = item.get("sha256")
    if not isinstance(recorded, str) or not re.fullmatch(r"[0-9a-f]{64}", recorded):
        fail(f"invalid pinned build-input SHA-256: {relative}")
    path = root / relative
    if not path.is_file() or sha256(path) != recorded:
        fail(f"pinned build input drift: {relative}")
    if item.get("gitStatus") != git_status(relative):
        fail(f"pinned build-input git status drift: {relative}")
business_source_paths = {
    item.get("file") for item in manifest.get("sourceFiles", [])
    if isinstance(item, dict)
}
if business_source_paths.intersection(EXPECTED_BUILD_INPUT_PATHS):
    fail("cross-release build inputs must not be presented as REV-06 business source")
root_pom = (root / "pom.xml").read_text(encoding="utf-8")
for required in (
    "<build.releaseId>UNSET</build.releaseId>",
    "<build.approvedPatchSha256>UNSET</build.approvedPatchSha256>",
    "<build.approvedSourceManifestSha256>UNSET</build.approvedSourceManifestSha256>",
    "<releaseId>${build.releaseId}</releaseId>",
    "<approvedPatchSha256>${build.approvedPatchSha256}</approvedPatchSha256>",
    "<approvedSourceManifestSha256>${build.approvedSourceManifestSha256}</approvedSourceManifestSha256>",
):
    if required not in root_pom:
        fail(f"cross-release build-info contract missing: {required}")
if manifest.get("gateOrder") != EXPECTED_GATES:
    fail("release gate order drift")
if manifest.get("rollbackPolicy", {}).get("candidatePackageCountMustEqual") != 0:
    fail("rollback must be prohibited after any candidate package exists")
if manifest.get("historicalCoverage", {}).get("zeroSamplesClaim") != "not-verified":
    fail("zero historical samples must not be called verified coverage")
artifact_policy = manifest.get("artifactAttestation", {})
required_artifact_entries = [
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
if artifact_policy.get("candidateEvidenceRequired") is not True:
    fail("candidate artifact evidence must be required before stopping OA")
if artifact_policy.get("installedEvidenceRequiredWhileStopped") is not True:
    fail("installed artifact evidence must be required before PRE")
if artifact_policy.get("requiredJarEntries") != required_artifact_entries:
    fail("artifact class/resource attestation scope drift")
if artifact_policy.get("buildInfoProperties") != {
    "build.commit": "baselineCommit",
    "build.releaseId": "releaseId",
    "build.approvedPatchSha256": "sourceSnapshotSha256",
    "build.approvedSourceManifestSha256": "manifestSha256",
}:
    fail("artifact build-info provenance contract drift")
route_policy = manifest.get("readOnlyRouteSmoke", {})
if route_policy.get("minimumRouteCount") != 2:
    fail("at least two authenticated read-only signing routes are required")
if route_policy.get("requiredHttpMethod") != "GET":
    fail("signing route smoke must remain read-only GET")
if route_policy.get("responseBodyPersisted") is not False:
    fail("route smoke must not persist response bodies")
if route_policy.get("exactPaths") != [
        "/signPackage/scope/options", "/signPackage/template/types"]:
    fail("read-only signing route allowlist drift")
if route_policy.get("sameOriginAsOaHealthUrl") is not True:
    fail("route credentials must remain bound to the OA health origin")
database_identity_policy = manifest.get("databaseIdentityAttestation", {})
if database_identity_policy != {
    "requiredBeforeMigration": True,
    "requiredAfterMigration": True,
    "cliAndJdbcMustMatchExactly": True,
    "nonCredentialFields": [
        "serverUuid", "serverHostname", "serverPort", "databaseName"],
}:
    fail("CLI/JDBC database identity attestation policy drift")

entries = read_list(source_list_path)
if len(entries) != len(set(entries)):
    fail("source allowlist contains duplicates")
if set(entries) != EXPECTED_SOURCE_PATHS:
    missing = sorted(EXPECTED_SOURCE_PATHS - set(entries))
    extra = sorted(set(entries) - EXPECTED_SOURCE_PATHS)
    fail(f"source allowlist scope drift; missing={missing} extra={extra}")
if manifest.get("sourceFileCount") != len(entries):
    fail("sourceFileCount drift")

for relative in entries:
    lower = relative.lower()
    if re.search(r"(^|/)(output|target|dist|node_modules)(/|$)", lower):
        fail(f"generated artifact is forbidden in source allowlist: {relative}")
    if "tmp/pdfs/sign-export-audit" in lower:
        fail("controlled golden fixture must never enter the release list")
    if re.search(r"(^|/)(drive|cloud-drive|pds)(/|$)", lower):
        fail(f"drive scope is forbidden: {relative}")
    if re.search(r"(^|/)(\.env|secrets?)(/|$)", lower):
        fail(f"secret-bearing path is forbidden: {relative}")
    if not (root / relative).is_file():
        fail(f"source file is missing: {relative}")

exclusions = manifest.get("sourceHashExclusions")
expected_exclusion = [{
    "file": "scripts/sign-original-placement-export-release-20260809.json",
    "reason": "self-referential manifest; schema, exact source scope and every other file hash are verified fail-closed",
}]
if exclusions != expected_exclusion:
    fail("manifest self-reference must be the only source-hash exclusion")
excluded = {item["file"] for item in exclusions}
ledger = manifest.get("sourceFiles", [])
ledger_by_file: dict[str, dict] = {}
for item in ledger:
    relative = item.get("file") if isinstance(item, dict) else None
    if not isinstance(relative, str) or relative in ledger_by_file:
        fail("invalid or duplicate source ledger entry")
    ledger_by_file[relative] = item
if set(ledger_by_file) | excluded != set(entries) or set(ledger_by_file) & excluded:
    fail("source ledger and allowlist do not align")
for relative, item in ledger_by_file.items():
    recorded = item.get("sha256")
    if not isinstance(recorded, str) or not re.fullmatch(r"[0-9a-f]{64}", recorded):
        fail(f"invalid recorded SHA-256: {relative}")
    if sha256(root / relative) != recorded:
        fail(f"source hash drift: {relative}")
    if item.get("gitStatus") != git_status(relative):
        fail(f"source git status drift: {relative}")

migrations = read_list(migration_list_path)
if migrations != [MIGRATION]:
    fail("migration list must contain the one reviewed migration exactly once")
if manifest.get("migrationCount") != 1 or len(manifest.get("migrations", [])) != 1:
    fail("manifest migration count drift")
migration = manifest["migrations"][0]
if migration.get("file") != MIGRATION or migration.get("sha256") != MIGRATION_SHA:
    fail("migration identity or SHA drift")
copies = [
    root / "sql" / MIGRATION,
    root / "docker/mysql/db" / MIGRATION,
    root / "erp-modules/erp-oa/src/main/resources/db/migration" / MIGRATION,
]
if any(not path.is_file() or sha256(path) != MIGRATION_SHA for path in copies):
    fail("one or more migration copies are missing or drifted")
if not all(path.read_bytes() == copies[0].read_bytes() for path in copies[1:]):
    fail("three migration copies are not byte-identical")
migration_sql = strip_sql_comments(copies[0].read_text(encoding="utf-8"))
for forbidden in (
    r"\bUPDATE\s+oa_sign_package\b",
    r"\bUPDATE\s+oa_sign_package_document\b",
    r"\bDELETE\s+FROM\b",
    r"\bDROP\s+TABLE\b",
    r"\bTRUNCATE\b",
):
    if re.search(forbidden, migration_sql, flags=re.IGNORECASE):
        fail(f"migration contains forbidden business mutation: {forbidden}")

preflight = strip_sql_comments((root / "scripts/sign-original-placement-export-preflight-20260809.sql").read_text(encoding="utf-8"))
if re.search(r"\b(INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|TRUNCATE)\b", preflight, re.IGNORECASE):
    fail("preflight must remain read-only")
rollback = strip_sql_comments((root / "scripts/sign-original-placement-export-rollback-20260809.sql").read_text(encoding="utf-8"))
if re.search(r"\b(DELETE\s+FROM|DROP\s+TABLE|TRUNCATE)\b", rollback, re.IGNORECASE):
    fail("rollback contains a destructive operation")
for required in (
    "rollback blocked because candidate packages exist",
    "v_source_enabled = 0 AND v_candidate_enabled = 2",
    "v_source_enabled = 2 AND v_candidate_enabled = 0",
    "v_other_enabled <> 0",
):
    if required not in rollback:
        fail(f"rollback fail-closed contract missing: {required}")

resources = manifest.get("pinnedResources", {})
v7_path = root / "erp-modules/erp-oa/src/main/resources/oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"
if resources.get("exactV7TemplateSha256") != V7_SHA or sha256(v7_path) != V7_SHA:
    fail("exact-v7 DOCX source SHA drift")
for key in ("placementProfileSha256", "representativeEvidenceSha256"):
    if not re.fullmatch(r"[0-9a-f]{64}", str(resources.get(key, ""))):
        fail(f"missing pinned resource SHA: {key}")
resource_paths = {
    "placementProfileSha256": root / "erp-modules/erp-oa/src/main/resources/oa/sign/labor-contract-placement-profiles-v1.json",
    "representativeEvidenceSha256": root / "erp-modules/erp-oa/src/main/resources/oa/sign/evidence/legal-entity-4-representative-20260719-v1.json",
}
for key, path in resource_paths.items():
    if sha256(path) != resources[key]:
        fail(f"pinned resource drift: {key}")

profiles = json.loads(resource_paths["placementProfileSha256"].read_text(encoding="utf-8"))
golden = next((item for item in profiles.get("profiles", []) if item.get("profileId") == "labor-v7-layout-SP1785202186549261523"), None)
if golden is None:
    fail("golden audited placement profile is missing")
expected_anchors = [
    "primary-signing-row",
    "attachment-signature-row",
    "dormitory-signature-row",
    "position-confirmation-row",
]
actual_anchor_names = [item.get("name") for item in golden.get("anchors", [])]
if any(actual_anchor_names.count(name) != 1 for name in expected_anchors):
    fail("golden named signature anchors are missing or ambiguous")
if [actual_anchor_names.index(name) for name in expected_anchors] != sorted(
        actual_anchor_names.index(name) for name in expected_anchors):
    fail("golden named signature anchor order drifted")
if len(golden.get("signaturePlacements", [])) != 4 or len(golden.get("sealPlacements", [])) != 1:
    fail("golden profile must contain four signatures and one seal")

scratch = json.loads((root / "docs/releases/evidence/sign-original-placement-export-20260809-scratch.json").read_text(encoding="utf-8"))
if scratch.get("migration", {}).get("forwardExecutions") != 2:
    fail("scratch evidence must record two forward executions")
if scratch.get("migration", {}).get("rollbackExecutions") != 2:
    fail("scratch evidence must record two rollback executions")
if scratch.get("javaDatabaseVerifier", {}).get("pre", {}).get("skipped") != 0:
    fail("scratch PRE Java gate was skipped")
if scratch.get("javaDatabaseVerifier", {}).get("post", {}).get("skipped") != 0:
    fail("scratch POST Java gate was skipped")
if any(item.get("beforeSha256") != item.get("afterSha256") for item in scratch.get("negativeScenarios", [])):
    fail("a scratch negative scenario mutated state before failing")
if scratch.get("cleanup", {}).get("scratchSchemasDroppedAfterEvidenceCapture") is not True:
    fail("scratch cleanup evidence is missing")
if scratch.get("privacy", {}).get("containsEmployeeIdentity") is not False:
    fail("scratch evidence contains employee identity")

gitignore = (root / ".gitignore").read_text(encoding="utf-8")
if "/tmp/pdfs/sign-export-audit/" not in gitignore or "output/" not in gitignore:
    fail("controlled golden fixture and output directories must remain ignored")

for relative in entries:
    path = root / relative
    if path.suffix.lower() in {".docx", ".pdf", ".png", ".jpg", ".jpeg"}:
        continue
    text = path.read_text(encoding="utf-8", errors="strict")
    if re.search(r"(?<!\d)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[0-9Xx](?!\d)", text):
        fail(f"possible full identity-document number in release source: {relative}")

gate_output = subprocess.run(
    ["bash", str(wrapper_path), "--print-gates"],
    cwd=root,
    check=True,
    capture_output=True,
    text=True,
).stdout
positions = [gate_output.find(f"{index}. {gate}") for index, gate in enumerate(EXPECTED_GATES, 1)]
if any(position < 0 for position in positions) or positions != sorted(positions):
    fail("release wrapper gate order is incomplete or reordered")
wrapper_text = wrapper_path.read_text(encoding="utf-8")
for required in (
    "--record-candidate-artifact",
    "--attest-installed-artifact",
    "ERP_SIGN_OA_INSTALLED_JAR",
    "candidate-artifact.json",
    "installed-artifact.json",
    "ERP_SIGN_READ_ONLY_ROUTE_URLS_FILE",
    "ERP_SIGN_READ_ONLY_AUTH_HEADER_FILE",
    "read-only-signing-routes.jsonl",
    "META-INF/erp-sign-placement-release.properties",
    "sourceManifestSha256",
    "sourceSnapshotSha256",
):
    if required not in wrapper_text:
        fail(f"release wrapper evidence gate missing: {required}")

head = subprocess.run(
    ["git", "-C", str(root), "rev-parse", "HEAD"],
    check=True,
    capture_output=True,
    text=True,
).stdout.strip()
if head != BASELINE_COMMIT:
    fail("working HEAD drifted from recorded build baseline")

print(
    "SIGN_ORIGINAL_PLACEMENT_RELEASE_SOURCE_OK "
    f"files={len(entries)} migrationSha256={MIGRATION_SHA} "
    f"exactV7Sha256={V7_SHA} status={manifest.get('status')} "
    f"deployable={str(manifest.get('deployable')).lower()} mode={verify_mode}"
)
PY

echo "SIGN_ORIGINAL_PLACEMENT_RELEASE_STATIC_OK"
