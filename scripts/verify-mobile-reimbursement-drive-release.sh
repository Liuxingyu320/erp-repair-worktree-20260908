#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/scripts/mobile-reimbursement-drive-release-20260731.json"
FILE_LIST="$ROOT_DIR/scripts/mobile-reimbursement-drive-release-files-20260731.list"
MODE="${1:---source}"

case "$MODE" in
  --source|--candidate)
    ;;
  *)
    echo "Usage: $0 [--source|--candidate]" >&2
    exit 2
    ;;
esac

python3 - "$ROOT_DIR" "$MANIFEST" "$FILE_LIST" "$MODE" <<'PY'
import json
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
manifest_path = Path(sys.argv[2]).resolve()
file_list_path = Path(sys.argv[3]).resolve()
mode = sys.argv[4]
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

if manifest.get("releaseId") != "mobile-reimbursement-drive-20260731":
    raise SystemExit("[FAIL] unexpected releaseId")
if manifest.get("status") != "development":
    raise SystemExit("[FAIL] source contract must remain development until external gates pass")
if manifest.get("executionPolicy") != "manual-clean-candidate-no-auto-deploy":
    raise SystemExit("[FAIL] unsafe execution policy")
if manifest.get("productionDeploymentAuthorized") is not False:
    raise SystemExit("[FAIL] development manifest must not authorize production deployment")

entries = [
    line.strip()
    for line in file_list_path.read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
if not entries:
    raise SystemExit("[FAIL] release file list is empty")
if len(entries) != len(set(entries)):
    duplicates = sorted({entry for entry in entries if entries.count(entry) > 1})
    raise SystemExit("[FAIL] duplicate release paths: " + ", ".join(duplicates))
if manifest.get("sourceFileCount") != len(entries):
    raise SystemExit(
        f"[FAIL] sourceFileCount={manifest.get('sourceFileCount')} but list has {len(entries)}"
    )

unsafe = [
    entry for entry in entries
    if Path(entry).is_absolute() or ".." in Path(entry).parts
]
if unsafe:
    raise SystemExit("[FAIL] unsafe release paths: " + ", ".join(unsafe))

missing = [entry for entry in entries if not (root / entry).is_file()]
if missing:
    raise SystemExit("[FAIL] missing release files: " + ", ".join(missing))

forbidden_parts = {"target", "node_modules", "dist", "android", "ios", "tmp"}
generated = [
    entry for entry in entries
    if forbidden_parts.intersection(Path(entry).parts)
]
if generated:
    raise SystemExit("[FAIL] generated paths entered release scope: " + ", ".join(generated))

tracked_output = subprocess.check_output(
    ["git", "-C", str(root), "ls-files", "--cached", "-z"]
)
tracked = {
    value.decode("utf-8", "surrogateescape")
    for value in tracked_output.split(b"\0")
    if value
}
untracked = [entry for entry in entries if entry not in tracked]
if mode == "--candidate":
    if untracked:
        raise SystemExit(
            "[FAIL] candidate release files are not tracked in the Git index: "
            + ", ".join(untracked)
        )
    dirty = subprocess.check_output(
        [
            "git", "-C", str(root), "status", "--porcelain=v1",
            "--untracked-files=all", "--",
        ],
        text=True,
    ).splitlines()
    if dirty:
        raise SystemExit(
            "[FAIL] candidate release requires a clean worktree; first dirty path: "
            + dirty[0]
        )

required = {
    "erp-ui/src/views/mobile/oa/reimbursement/index.vue",
    "erp-ui/src/views/mobile/drive/index.vue",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementApprovalStartOutboxController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaReimbursementApprovalStartOutbox.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaReimbursementApprovalStartOutboxMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementApprovalStartDispatcher.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementApprovalStartOutboxService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementServiceImpl.java",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaReimbursementApprovalStartOutboxMapper.xml",
    "erp-modules/erp-approval/src/main/java/com/erp/approval/callback/OaReimbursementApprovalBusinessCallback.java",
    "erp-modules/erp-approval/src/test/java/com/erp/approval/service/ApprovalTaskServiceReimbursementPermissionTest.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java",
    "erp-modules/erp-inventory/src/test/resources/new-business-it-baseline.sql",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaReimbursementControllerTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaReimbursementServiceImplDraftTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaReimbursementApprovalStartFlowTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaReimbursementApprovalStartOutboxServiceTest.java",
    "erp-ui/src/utils/reimbursementSmartFill.js",
    "erp-ui/test/reimbursementManagement.test.js",
    "erp-ui/test/reimbursementSmartFill.test.js",
    "erp-ui/test/mobileCloudDrive.test.js",
    "scripts/verify-new-business-it-contract.sh",
}
missing_required = sorted(required - set(entries))
if missing_required:
    raise SystemExit("[FAIL] release scope misses required files: " + ", ".join(missing_required))

shared_files = set(manifest.get("sharedFiles", []))
if not shared_files or not shared_files.issubset(set(entries)):
    raise SystemExit("[FAIL] every shared file must be explicit in the release list")

migration = manifest.get("migration", {})
canonical = root / migration.get("canonical", "")
copies = [root / value for value in migration.get("copies", [])]
if len(copies) != 2 or any(path.read_bytes() != canonical.read_bytes() for path in copies):
    raise SystemExit("[FAIL] reimbursement migration copies are not byte-identical")

migration_sql = canonical.read_text(encoding="utf-8")
for required_sql in (
    "CREATE TABLE IF NOT EXISTS oa_reimbursement_approval_start_outbox",
    "oa:reimbursement:approvalStartOutbox:list",
    "oa:reimbursement:approvalStartOutbox:replay",
):
    if required_sql not in migration_sql:
        raise SystemExit(
            "[FAIL] reimbursement migration misses approval-start outbox contract: "
            + required_sql
        )

bootstrap_entries = [
    line.strip()
    for line in (root / "docker/mysql/bootstrap-files.list")
    .read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
reimbursement_name = canonical.name
expected_prerequisites = [
    "sql/erp_hr_dept_leader_identity_20260713.sql",
    "sql/erp_unified_approval_schema_20260716.sql",
    "sql/erp_unified_approval_seed_20260716.sql",
]
if migration.get("prerequisites") != expected_prerequisites:
    raise SystemExit("[FAIL] reimbursement migration prerequisites changed")
if bootstrap_entries.count(reimbursement_name) != 1:
    raise SystemExit(
        "[FAIL] reimbursement migration must occur exactly once in bootstrap-files.list"
    )
for prerequisite in expected_prerequisites:
    prerequisite_name = Path(prerequisite).name
    if not (root / prerequisite).is_file():
        raise SystemExit("[FAIL] missing reimbursement prerequisite: " + prerequisite)
    if prerequisite_name not in bootstrap_entries:
        raise SystemExit(
            "[FAIL] reimbursement prerequisite is absent from bootstrap: "
            + prerequisite_name
        )
    if bootstrap_entries.index(prerequisite_name) >= bootstrap_entries.index(
            reimbursement_name):
        raise SystemExit(
            "[FAIL] reimbursement prerequisite must precede reimbursement: "
            + prerequisite_name
        )

secret_patterns = (
    "BAIDU_OCR_API_KEY=AK",
    "BAIDU_OCR_SECRET_KEY=SK",
)
for relative in ("docker/.env.example", "docker/docker-compose.yml",
                 "docker/docker-compose.ecs-host.yml"):
    source = (root / relative).read_text(encoding="utf-8")
    if any(pattern in source for pattern in secret_patterns):
        raise SystemExit(f"[FAIL] possible OCR credential committed in {relative}")

print(
    "MOBILE_REIMBURSEMENT_DRIVE_RELEASE_"
    + ("CANDIDATE_PRECHECK_OK" if mode == "--candidate" else "SOURCE_OK")
    + f" files={len(entries)} status={manifest['status']}"
    + (f" untracked={len(untracked)}" if mode == "--source" else "")
)
PY

git -C "$ROOT_DIR" diff --check
bash -n "$ROOT_DIR/scripts/verify-mobile-reimbursement-drive-release.sh"
