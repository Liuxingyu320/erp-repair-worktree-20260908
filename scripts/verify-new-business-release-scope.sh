#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/scripts/new-business-release-20260714.json"
MODE="${1:---static}"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    printf '[PASS] %s\n' "$*"
}

case "$MODE" in
    --static|--candidate) ;;
    *) fail "usage: verify-new-business-release-scope.sh [--static|--candidate]" ;;
esac

command -v git >/dev/null 2>&1 || fail 'git is required'
command -v python3 >/dev/null 2>&1 || fail 'python3 is required'
git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || fail 'release scope verification requires a Git worktree'
[[ -r "$MANIFEST" ]] || fail "missing release manifest: $MANIFEST"

python3 - "$ROOT_DIR" "$MANIFEST" <<'PY'
import hashlib
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
manifest_path = Path(sys.argv[2]).resolve()
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

def fail(message):
    raise SystemExit(f"[FAIL] {message}")

if manifest.get("schemaVersion") != 1:
    fail("unsupported release manifest schemaVersion")
if not re.fullmatch(r"[a-z0-9][a-z0-9-]+", str(manifest.get("releaseId", ""))):
    fail("invalid releaseId")

list_path = root / str(manifest.get("migrationList", ""))
source_dir = root / str(manifest.get("sourceDirectory", ""))
# The committed deploy mirror is the ordered Docker bootstrap source. The
# manifest deployDirectory is a generated release output and must stay absent
# from immutable repository inputs.
deploy_dir = root / "docker/mysql/db"
if not list_path.is_file():
    fail(f"migration list is missing: {list_path}")

listed = [
    line.strip()
    for line in list_path.read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
migrations = manifest.get("migrations")
expected_migration_count = manifest.get("migrationCount")
if (not isinstance(expected_migration_count, int)
        or isinstance(expected_migration_count, bool)
        or expected_migration_count < 1):
    fail("release manifest migrationCount must be a positive integer")
if not isinstance(migrations, list) or len(migrations) != expected_migration_count:
    fail("release manifest migration count differs from migrationCount")
manifest_files = [item.get("file") for item in migrations]
if manifest_files != listed:
    fail("release manifest migration order differs from the ordered list")

table_name = re.compile(r"[a-z][a-z0-9_]*")
for item in migrations:
    name = item.get("file")
    expected = item.get("sha256")
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+\.sql", name):
        fail(f"unsafe migration filename: {name!r}")
    if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
        fail(f"invalid sha256 for {name}")
    source = source_dir / name
    deploy = deploy_dir / name
    if not source.is_file() or not deploy.is_file():
        fail(f"source/deploy migration pair is incomplete: {name}")
    source_bytes = source.read_bytes()
    if source_bytes != deploy.read_bytes():
        fail(f"source/deploy migration mismatch: {name}")
    actual = hashlib.sha256(source_bytes).hexdigest()
    if actual != expected:
        fail(f"release manifest sha256 mismatch: {name}")
    for key in ("creates", "backupTables"):
        values = item.get(key)
        if not isinstance(values, list) or any(
            not isinstance(value, str) or not table_name.fullmatch(value)
            for value in values
        ):
            fail(f"invalid {key} list for {name}")

flags = manifest.get("featureFlags")
expected_flags = {
    "feature.hr.health-certificate.enabled",
    "feature.inventory.store-return.enabled",
    "feature.inventory.transfer-discrepancy.enabled",
    "feature.inventory.customer-service-card.enabled",
    "feature.oa.purchase.enabled",
    "feature.inventory.stock-check-native-approval.enabled",
    "feature.inventory.transfer-native-approval.enabled",
}
if (not isinstance(flags, list) or len(flags) != len(set(flags))
        or set(flags) != expected_flags):
    fail("release manifest feature flags differ from the explicit 7-key contract")
for flag in flags:
    if not isinstance(flag, str) or not flag.endswith(".enabled"):
        fail(f"invalid feature flag: {flag!r}")
    matches = sum(
        (source_dir / item["file"]).read_text(encoding="utf-8").count(flag)
        for item in migrations
    )
    if matches == 0:
        fail(f"feature flag is absent from declared migration SQL: {flag}")

tests = manifest.get("integrationTests")
if not isinstance(tests, list) or len(tests) != 6 or len(set(tests)) != 6:
    fail("release manifest must declare exactly 6 unique integration tests")
for relative in tests:
    path = root / relative
    if not path.is_file() or not path.name.endswith("IT.java"):
        fail(f"integration test is missing or misnamed: {relative}")

print("[PASS] release manifest order, hashes, backups, flags and IT inventory are valid")
PY

git -C "$ROOT_DIR" diff --check \
    || fail 'Git whitespace validation failed'

git -C "$ROOT_DIR" check-ignore -q --no-index .codex-runs/local-result.json \
    || fail '.codex-runs output is not ignored'
git -C "$ROOT_DIR" check-ignore -q --no-index .playwright-cli/page-2099-01-01T00-00-00-000Z.yml \
    || fail 'temporary Playwright page snapshots are not ignored'

if [[ -L "$ROOT_DIR/erp-ui/node_modules" ]]; then
    fail 'erp-ui/node_modules must not be a repository or filesystem symlink'
fi
git -C "$ROOT_DIR" check-ignore -q --no-index erp-ui/node_modules \
    || fail 'erp-ui/node_modules must remain ignored'

if git -C "$ROOT_DIR" ls-files --error-unmatch erp-ui/node_modules >/dev/null 2>&1; then
    git -C "$ROOT_DIR" diff --name-only --diff-filter=D -- erp-ui/node_modules \
        | grep -qx 'erp-ui/node_modules' \
        || fail 'tracked erp-ui/node_modules symlink is not scheduled for removal'
    pass 'tracked node_modules symlink is scheduled for removal'
else
    pass 'node_modules is not tracked'
fi

if [[ "$MODE" == '--candidate' ]]; then
    [[ -z "$(git -C "$ROOT_DIR" status --porcelain=v1)" ]] \
        || fail 'release candidate worktree is not clean'
    [[ -z "$(git -C "$ROOT_DIR" ls-files -s erp-ui/node_modules)" ]] \
        || fail 'release candidate still tracks erp-ui/node_modules'
    pass 'release candidate worktree is clean and reproducible'
else
    pass 'static release scope contract is valid; use --candidate on the clean committed candidate'
fi
