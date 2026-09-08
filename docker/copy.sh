#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd "$BASE_DIR/.." && pwd)
MANIFEST_FILE="$ROOT_DIR/scripts/new-business-release-20260714.json"
SQL_ONLY=false
RELEASE_ID=${RELEASE_ID:-}
GIT_COMMIT=${GIT_COMMIT:-}
BUILD_TIME=${BUILD_TIME:-}

usage() {
	echo "Usage: sh copy.sh [--manifest PATH] [--sql-only] [--release-id ID] [--git-commit SHA] [--build-time UTC]"
}

while [ "$#" -gt 0 ]; do
	case "$1" in
		--manifest)
			[ "$#" -ge 2 ] || { usage >&2; exit 2; }
			case "$2" in
				/*) MANIFEST_FILE=$2 ;;
				*) MANIFEST_FILE="$ROOT_DIR/$2" ;;
			esac
			shift 2
			;;
		--sql-only)
			SQL_ONLY=true
			shift
			;;
		--release-id)
			[ "$#" -ge 2 ] || { usage >&2; exit 2; }
			RELEASE_ID=$2
			shift 2
			;;
		--git-commit)
			[ "$#" -ge 2 ] || { usage >&2; exit 2; }
			GIT_COMMIT=$2
			shift 2
			;;
		--build-time)
			[ "$#" -ge 2 ] || { usage >&2; exit 2; }
			BUILD_TIME=$2
			shift 2
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			usage >&2
			exit 2
			;;
	esac
done

copy_jar() {
	src_file=$1
	dst_dir=$2
	if [ ! -f "$src_file" ]; then
		echo "missing file: $src_file" >&2
		exit 1
	fi
	case "$src_file" in
		*.jar) ;;
		*) echo "refusing non-JAR artifact: $src_file" >&2; exit 1 ;;
	esac
	mkdir -p "$dst_dir"
	canonical_name=$(basename "$src_file")
	for existing in "$dst_dir"/*.jar; do
		[ -e "$existing" ] || continue
		if [ "$(basename "$existing")" != "$canonical_name" ]; then
			echo "unexpected JAR blocks clean deployment copy: $existing" >&2
			exit 1
		fi
	done
	rm -f "$dst_dir/$canonical_name"
	cp "$src_file" "$dst_dir/$canonical_name"
	jar_count=$(find "$dst_dir" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')
	if [ "$jar_count" != 1 ]; then
		echo "JAR directory is not canonical after copy: $dst_dir" >&2
		exit 1
	fi
}

copy_dist() {
	src_dir="$ROOT_DIR/erp-ui/dist"
	dst_dir="$BASE_DIR/nginx/html/dist"
	if [ ! -d "$src_dir" ]; then
		echo "missing directory: $src_dir" >&2
		exit 1
	fi
	if find "$src_dir" -type l | grep -q .; then
		echo "frontend dist must not contain symlinks" >&2
		exit 1
	fi
	if find "$src_dir" -type f \( -name '* [0-9].*' -o -name '* copy.*' -o -name '*副本.*' -o -name '*.bak' -o -name '*.old' \) | grep -q .; then
		echo "frontend dist contains a duplicate/backup-style filename" >&2
		exit 1
	fi
	rm -rf "$dst_dir"
	mkdir -p "$dst_dir"
	cp -R "$src_dir/." "$dst_dir/"
}

copy_sql_files() {
	python3 - "$ROOT_DIR" "$MANIFEST_FILE" <<'PY'
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
manifest_path = Path(sys.argv[2]).resolve()
try:
    manifest_path.relative_to(root)
except ValueError as exc:
    raise SystemExit("manifest must be inside the repository") from exc
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
release_id = manifest.get("releaseId")
if not isinstance(release_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9-]+", release_id):
    raise SystemExit("unsafe releaseId")

def repo_path(value, label):
    path = (root / str(value)).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise SystemExit(f"unsafe {label} path") from exc
    return path

source_dir = repo_path(manifest.get("sourceDirectory"), "sourceDirectory")
deploy_dir = repo_path(manifest.get("deployDirectory"), "deployDirectory")
expected_deploy = (root / "docker/mysql/releases" / release_id).resolve()
if deploy_dir != expected_deploy:
    raise SystemExit(f"deployDirectory must be docker/mysql/releases/{release_id}")
list_path = repo_path(manifest.get("migrationList"), "migrationList")
names = [line.strip() for line in list_path.read_text(encoding="utf-8").splitlines()
         if line.strip() and not line.lstrip().startswith("#")]
if not names or len(names) != len(set(names)):
    raise SystemExit("migration list must be non-empty and unique")
if any(not re.fullmatch(r"[A-Za-z0-9._-]+\.sql", name) for name in names):
    raise SystemExit("unsafe migration filename")
migrations = manifest.get("migrations")
if not isinstance(migrations, list) or [item.get("file") for item in migrations] != names:
    raise SystemExit("manifest migrations must exactly match the ordered migration list")
for item in migrations:
    source = source_dir / item["file"]
    expected = item.get("sha256")
    if not source.is_file() or not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit("migration source or hash is invalid: " + item["file"])
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
        raise SystemExit("migration source hash mismatch: " + item["file"])

rollback_name = None
rollback = manifest.get("rollback")
if rollback is not None:
    if not isinstance(rollback, dict):
        raise SystemExit("rollback metadata must be an object")
    rollback_name = rollback.get("file")
    rollback_hash = rollback.get("sha256")
    if not isinstance(rollback_name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+\.sql", rollback_name):
        raise SystemExit("unsafe rollback filename")
    if rollback_name in names:
        raise SystemExit("rollback file must not be part of the forward migration order")
    rollback_source = source_dir / rollback_name
    if not rollback_source.is_file() or not isinstance(rollback_hash, str) or not re.fullmatch(r"[0-9a-f]{64}", rollback_hash):
        raise SystemExit("rollback source or hash is invalid: " + rollback_name)
    if hashlib.sha256(rollback_source.read_bytes()).hexdigest() != rollback_hash:
        raise SystemExit("rollback source hash mismatch: " + rollback_name)

deploy_dir.mkdir(parents=True, exist_ok=True)
for existing in deploy_dir.glob("*.sql"):
    existing.unlink()
for name in names:
    shutil.copy2(source_dir / name, deploy_dir / name)
if rollback_name:
    shutil.copy2(source_dir / rollback_name, deploy_dir / rollback_name)
metadata_dir = root / "docker/release"
metadata_dir.mkdir(parents=True, exist_ok=True)
shutil.copy2(manifest_path, metadata_dir / manifest_path.name)
shutil.copy2(list_path, metadata_dir / list_path.name)
print(f"RELEASE_SQL_COPIED release={release_id} files={len(names)} rollback={int(bool(rollback_name))} destination={deploy_dir}")
PY
}

if [ "$SQL_ONLY" != true ]; then
	echo "verify explicit mysql bootstrap"
	"$ROOT_DIR/scripts/verify-docker-mysql-bootstrap.sh"
fi

echo "copy versioned release sql and metadata"
copy_sql_files

if [ "$SQL_ONLY" = true ]; then
	echo "sql-only packaging complete"
	exit 0
fi

[ -n "$RELEASE_ID" ] || { echo "RELEASE_ID is required for a full release copy" >&2; exit 1; }
[ -n "$GIT_COMMIT" ] || { echo "GIT_COMMIT is required for a full release copy" >&2; exit 1; }
[ -n "$BUILD_TIME" ] || { echo "BUILD_TIME is required for a full release copy" >&2; exit 1; }

echo "begin copy html"
copy_dist

echo "begin copy erp-gateway"
copy_jar "$ROOT_DIR/erp-gateway/target/erp-gateway.jar" "$BASE_DIR/erp/gateway/jar"

echo "begin copy erp-auth"
copy_jar "$ROOT_DIR/erp-auth/target/erp-auth.jar" "$BASE_DIR/erp/auth/jar"

echo "begin copy erp-visual"
copy_jar "$ROOT_DIR/erp-visual/erp-monitor/target/erp-visual-monitor.jar" "$BASE_DIR/erp/visual/monitor/jar"

echo "begin copy erp-modules-system"
copy_jar "$ROOT_DIR/erp-modules/erp-system/target/erp-modules-system.jar" "$BASE_DIR/erp/modules/system/jar"

echo "begin copy erp-modules-file"
copy_jar "$ROOT_DIR/erp-modules/erp-file/target/erp-modules-file.jar" "$BASE_DIR/erp/modules/file/jar"

echo "begin copy erp-modules-job"
copy_jar "$ROOT_DIR/erp-modules/erp-job/target/erp-modules-job.jar" "$BASE_DIR/erp/modules/job/jar"

echo "begin copy erp-modules-oa"
copy_jar "$ROOT_DIR/erp-modules/erp-oa/target/erp-modules-oa.jar" "$BASE_DIR/erp/modules/oa/jar"

echo "begin copy erp-modules-inventory"
copy_jar "$ROOT_DIR/erp-modules/erp-inventory/target/erp-modules-inventory.jar" "$BASE_DIR/erp/modules/inventory/jar"

echo "begin copy erp-modules-approval"
copy_jar "$ROOT_DIR/erp-modules/erp-approval/target/erp-modules-approval.jar" "$BASE_DIR/erp/modules/approval/jar"

echo "stamp and verify unified release identity"
python3 "$ROOT_DIR/scripts/release/release_tool.py" stamp-deploy-tree \
	--docker-dir "$BASE_DIR" \
	--release-id "$RELEASE_ID" \
	--git-commit "$GIT_COMMIT" \
	--build-time "$BUILD_TIME"
