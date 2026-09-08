#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PAYLOAD_ROOT="${1:-$ROOT_DIR/docker}"
SOURCE_MANIFEST="$ROOT_DIR/scripts/new-business-release-20260714.json"
PAYLOAD_MANIFEST="$PAYLOAD_ROOT/release/new-business-release-20260714.json"
SOURCE_LIST="$ROOT_DIR/scripts/new-business-migrations-20260713.list"
PAYLOAD_LIST="$PAYLOAD_ROOT/release/new-business-migrations-20260713.list"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

[[ -d "$PAYLOAD_ROOT" ]] || fail "payload root is missing: $PAYLOAD_ROOT"
cmp -s "$SOURCE_MANIFEST" "$PAYLOAD_MANIFEST" \
    || fail 'payload migration manifest is missing or changed'
cmp -s "$SOURCE_LIST" "$PAYLOAD_LIST" \
    || fail 'payload migration list is missing or changed'

python3 - "$PAYLOAD_ROOT" "$PAYLOAD_MANIFEST" <<'PY'
import hashlib
import json
import re
import sys
from pathlib import Path

payload = Path(sys.argv[1]).resolve()
manifest = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
release_id = manifest.get("releaseId")
if not isinstance(release_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9-]+", release_id):
    raise SystemExit("[FAIL] invalid packaged releaseId")
sql_dir = payload / "mysql/releases" / release_id
if not sql_dir.is_dir():
    raise SystemExit("[FAIL] packaged release SQL directory is missing")

expected = []
migrations = manifest.get("migrations")
expected_count = manifest.get("migrationCount")
if (not isinstance(expected_count, int) or isinstance(expected_count, bool)
        or expected_count < 1 or not isinstance(migrations, list)
        or len(migrations) != expected_count):
    raise SystemExit("[FAIL] packaged migration count differs from migrationCount")
for migration in migrations:
    name = migration.get("file")
    digest = migration.get("sha256")
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+\.sql", name):
        raise SystemExit("[FAIL] unsafe packaged migration filename")
    path = sql_dir / name
    if not path.is_file():
        raise SystemExit("[FAIL] packaged migration is missing: " + name)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != digest:
        raise SystemExit("[FAIL] packaged migration hash mismatch: " + name)
    expected.append(name)

actual_files = sorted(path.name for path in sql_dir.glob("*.sql") if path.is_file())
if actual_files != sorted(expected):
    raise SystemExit("[FAIL] packaged release SQL directory contains undeclared files")
print(f"[PASS] payload contains exactly {len(expected)} manifest-verified migrations")
PY

if find "$PAYLOAD_ROOT/release" "$PAYLOAD_ROOT/mysql/releases" -type f \
    \( -name '.env' -o -name '*.pem' -o -name '*.key' \) -print -quit \
    | grep -q .; then
    fail 'versioned release metadata contains an environment or private-key file'
fi

printf '[PASS] versioned release payload metadata is complete\n'
