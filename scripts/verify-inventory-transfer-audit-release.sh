#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST_PATH="$ROOT_DIR/scripts/inventory-transfer-audit-release-20260807.json"
SOURCE_LIST_PATH="$ROOT_DIR/scripts/inventory-transfer-audit-release-files-20260807.list"
MIGRATION_LIST_PATH="$ROOT_DIR/scripts/inventory-transfer-audit-migrations-20260807.list"

usage() {
  echo "Usage: bash scripts/verify-inventory-transfer-audit-release.sh --source" >&2
}

[[ "${1:-}" == "--source" && "$#" -eq 1 ]] || { usage; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "[FAIL] python3 is required" >&2; exit 3; }

bash -n "$ROOT_DIR/scripts/verify-docker-mysql-bootstrap.sh"
if [[ -f "$ROOT_DIR/scripts/verify-new-business-migrations.sh" ]]; then
  bash -n "$ROOT_DIR/scripts/verify-new-business-migrations.sh"
fi
if [[ -f "$ROOT_DIR/scripts/verify-new-business-release.sh" ]]; then
  bash -n "$ROOT_DIR/scripts/verify-new-business-release.sh"
fi

python3 - "$ROOT_DIR" "$MANIFEST_PATH" "$SOURCE_LIST_PATH" "$MIGRATION_LIST_PATH" <<'PY'
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

EXPECTED_RELEASE_ID = "inventory-transfer-audit-20260807"
EXPECTED_BASELINE = "b408b7054a481fac692878fcdc24e7ea5bc9cc68"
EXPECTED_MIGRATION = "erp_inventory_transfer_audit_identity_20260807.sql"
EXPECTED_MIGRATION_SHA = "666c2c7bf388839eab3d13534a4c25b758206c085e01e1feab9ede44b6f45151"
EXPECTED_ARTIFACTS = {
    "inventoryJarSha256": "841c8df5d05f68868b1608fcd5c730efa96a07ca0d1699891a13599c60f252fd",
    "uiDistManifestSha256": "4226a55100f625176fbed8c7f3c6bb328ab2a7f9e4aca7465c4ae580ad060f6e",
    "uiIndexSha256": "873c69f15119e1343324aaca853c13534419db26882c20ef8119752a3a499cb0",
    "releaseInfoSha256": "a5607cc219afb41ab0de1d394fc97bb8cb30f091dc80ea37477f6e799cda3bf3",
}
EXPECTED_SOURCE_PATHS = {
    "docker/mysql/bootstrap-files.list",
    "docker/mysql/db/erp_inventory_transfer_audit_identity_20260807.sql",
    "docker/mysql/releases/inventory-transfer-audit-20260807/erp_inventory_transfer_audit_identity_20260807.sql",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java",
    "erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventorySchemaMigrationTest.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/NewBusinessOpsMapperBindingTest.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java",
    "erp-ui/src/views/inventory/transfer/index.vue",
    "erp-ui/src/views/inventory/transfer/transferOrgHierarchy.js",
    "erp-ui/test/inventoryTransferAuditRelease.test.js",
    "erp-ui/test/transferOrgHierarchy.test.js",
    "erp-ui/test/transferWarehouseProcessingVisibility.test.js",
    "scripts/inventory-transfer-audit-migrations-20260807.list",
    "scripts/inventory-transfer-audit-release-20260807.json",
    "scripts/inventory-transfer-audit-release-files-20260807.list",
    "scripts/verify-inventory-transfer-audit-release.sh",
    "sql/erp_inventory_transfer_audit_identity_20260807.sql",
}


def fail(message: str) -> None:
    raise SystemExit("[FAIL] " + message)


def read_lines(path: Path) -> list[str]:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(root)}")
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git_status(relative: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), "status", "--porcelain=v1", "--untracked-files=all", "--", relative],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout[:2] if result.stdout else ".."


try:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    fail(f"invalid release manifest: {exc}")

if manifest.get("schemaVersion") != 1:
    fail("schemaVersion must be 1")
if manifest.get("releaseId") != EXPECTED_RELEASE_ID:
    fail("releaseId drift")
if manifest.get("baselineCommit") != EXPECTED_BASELINE:
    fail("baselineCommit must be the full approved HEAD baseline")
if manifest.get("baselineCommitSemantics") != "build-baseline-only-dirty-worktree-not-reproducible-by-commit":
    fail("baseline commit semantics must state that HEAD alone cannot reproduce the dirty candidate")
if manifest.get("workingTreeReproducible") is not False:
    fail("workingTreeReproducible must be false")
if manifest.get("executionPolicy") != "automatic":
    fail("migration executionPolicy must be automatic")
if manifest.get("sourceFileList") != "scripts/inventory-transfer-audit-release-files-20260807.list":
    fail("sourceFileList drift")
if manifest.get("migrationList") != "scripts/inventory-transfer-audit-migrations-20260807.list":
    fail("migrationList drift")

source_entries = read_lines(source_list_path)
if len(source_entries) != len(set(source_entries)):
    fail("source allowlist contains duplicates")
if set(source_entries) != EXPECTED_SOURCE_PATHS:
    fail("source allowlist is outside the approved exact scope")
if manifest.get("sourceFileCount") != len(source_entries):
    fail("sourceFileCount does not match allowlist")

migration_entries = read_lines(migration_list_path)
if migration_entries != [EXPECTED_MIGRATION]:
    fail("migration list must contain exactly the approved migration once")
if manifest.get("migrationCount") != 1 or len(manifest.get("migrations", [])) != 1:
    fail("manifest migration count must be exactly one")

exclusions = manifest.get("sourceHashExclusions", [])
if exclusions != [{"file": "scripts/inventory-transfer-audit-release-20260807.json", "reason": "self-referential metadata; schema and cross-file relations are verified fail-closed"}]:
    fail("source hash exclusion must be the documented manifest self-reference only")
ledger = manifest.get("sourceFiles", [])
ledger_by_file = {}
for item in ledger:
    if not isinstance(item, dict) or not isinstance(item.get("file"), str):
        fail("invalid source hash ledger entry")
    if item["file"] in ledger_by_file:
        fail("source hash ledger contains duplicates")
    ledger_by_file[item["file"]] = item
excluded = {item["file"] for item in exclusions}
if set(ledger_by_file) | excluded != set(source_entries) or set(ledger_by_file) & excluded:
    fail("source hash ledger does not align with allowlist and exclusions")
for relative, item in ledger_by_file.items():
    path = root / relative
    if not path.is_file():
        fail(f"source file missing: {relative}")
    if not re.fullmatch(r"[0-9a-f]{64}", str(item.get("sha256", ""))):
        fail(f"invalid SHA-256 in source ledger: {relative}")
    if sha256(path) != item["sha256"]:
        fail(f"source hash drift: {relative}")
    expected_status = item.get("gitStatus")
    if expected_status not in {"..", " M", "M ", "MM", "A ", "AM", "??"}:
        fail(f"invalid recorded git status: {relative}")
    if git_status(relative) != expected_status:
        fail(f"git status drift: {relative}")

migration = manifest["migrations"][0]
if migration.get("file") != EXPECTED_MIGRATION or migration.get("sha256") != EXPECTED_MIGRATION_SHA:
    fail("manifest migration identity or SHA drift")
if migration.get("creates") != []:
    fail("creates must remain empty")
if "inv_transfer_order" not in migration.get("backupTables", []):
    fail("backupTables must include inv_transfer_order")
copy_paths = {
    "root": root / "sql" / EXPECTED_MIGRATION,
    "dockerDb": root / "docker/mysql/db" / EXPECTED_MIGRATION,
    "release": root / "docker/mysql/releases/inventory-transfer-audit-20260807" / EXPECTED_MIGRATION,
}
for label, path in copy_paths.items():
    if not path.is_file() or sha256(path) != EXPECTED_MIGRATION_SHA:
        fail(f"{label} migration copy is missing or has a wrong SHA")
for left, right in (("root", "dockerDb"), ("root", "release")):
    if copy_paths[left].read_bytes() != copy_paths[right].read_bytes():
        fail(f"migration copies differ: {left} vs {right}")

preconditions = manifest.get("preconditions", {})
if "inv_transfer_order" not in preconditions.get("tables", []):
    fail("preconditions must bind inv_transfer_order")
required_columns = {
    (item.get("table"), name)
    for item in preconditions.get("columns", [])
    for name in item.get("names", [])
}
for required in {
    ("inv_transfer_order", "transfer_id"),
    ("inv_transfer_order", "create_by"),
    ("inv_transfer_order", "submitted_time"),
    ("inv_transfer_order", "from_dept_id"),
    ("inv_transfer_order", "to_dept_id"),
}:
    if required not in required_columns:
        fail(f"missing migration prerequisite column: {required[1]}")

bootstrap_entries = read_lines(root / "docker/mysql/bootstrap-files.list")
if bootstrap_entries.count(EXPECTED_MIGRATION) != 1:
    fail("bootstrap migration occurrence must be exactly one")
bootstrap_index = bootstrap_entries.index(EXPECTED_MIGRATION)
if bootstrap_index == 0 or bootstrap_entries[bootstrap_index - 1] != "erp_inventory_stock_log_business_type_20260804.sql":
    fail("bootstrap migration must immediately follow the stock-log business-type migration")

artifacts = manifest.get("deployedArtifactEvidence", {})
for key, expected in EXPECTED_ARTIFACTS.items():
    if artifacts.get(key) != expected:
        fail(f"deployed artifact evidence drift: {key}")
if artifacts.get("backupDirectory") != "output/deploy/transfer-audit-20260807-071306":
    fail("deployment backup directory evidence drift")

print(
    "INVENTORY_TRANSFER_AUDIT_RELEASE_SOURCE_OK "
    f"files={len(source_entries)} migrations=1 migrationSha256={EXPECTED_MIGRATION_SHA} "
    f"baselineCommit={EXPECTED_BASELINE} dirtyCandidate=true"
)
PY

echo "INVENTORY_TRANSFER_AUDIT_RELEASE_STATIC_OK"
