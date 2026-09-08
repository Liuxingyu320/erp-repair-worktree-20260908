#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
MODE=pre-build
MANIFEST=

usage() {
    echo "Usage: sh scripts/verify-build-tree-clean.sh [--root PATH] [--manifest PATH] [--pre-build|--artifacts-only]"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --root)
            [ "$#" -ge 2 ] || { usage >&2; exit 2; }
            ROOT_DIR=$(CDPATH= cd "$2" && pwd)
            shift 2
            ;;
        --pre-build)
            MODE=pre-build
            shift
            ;;
        --manifest)
            [ "$#" -ge 2 ] || { usage >&2; exit 2; }
            case "$2" in
                /*) MANIFEST=$2 ;;
                *) MANIFEST="$ROOT_DIR/$2" ;;
            esac
            shift 2
            ;;
        --artifacts-only)
            MODE=artifacts-only
            shift
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

TMP_FILE=$(mktemp "${TMPDIR:-/tmp}/erp-build-tree.XXXXXX")
trap 'rm -f "$TMP_FILE"' EXIT HUP INT TERM

find "$ROOT_DIR" \
    \( -path "$ROOT_DIR/.git" -o -path '*/node_modules' \) -prune -o \
    -type f \( -name '* [0-9]*.*' -o -name '._*' -o -name '.DS_Store' \) -print \
    > "$TMP_FILE"

if [ -s "$TMP_FILE" ]; then
    echo "conflict or metadata artifacts found:" >&2
    sed -n '1,80p' "$TMP_FILE" >&2
    exit 41
fi

python3 - "$ROOT_DIR" <<'PY'
import re
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

root = Path(sys.argv[1]).resolve()
ignored = {".git", "node_modules"}
fqcn_sources = defaultdict(list)

for source in root.rglob("*.java"):
    if any(part in ignored for part in source.parts) or "/src/main/java/" not in source.as_posix():
        continue
    try:
        text = source.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    match = re.search(r"(?m)^\s*package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;", text)
    if match:
        fqcn_sources[f"{match.group(1)}.{source.stem}"].append(source)

duplicates = {name: paths for name, paths in fqcn_sources.items() if len(paths) > 1}
if duplicates:
    rows = [f"{name}: " + ", ".join(str(path.relative_to(root)) for path in paths)
            for name, paths in sorted(duplicates.items())]
    raise SystemExit("duplicate main-source FQCNs found:\n" + "\n".join(rows[:40]))

bad_entry = re.compile(r"(?:^|/)(?:\._|[^/]+ [0-9]+(?:\.[^/]*)?$)")
for jar in root.rglob("*.jar"):
    if any(part in ignored for part in jar.parts):
        continue
    try:
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
    except (OSError, zipfile.BadZipFile):
        continue
    repeated = sorted(name for name, count in Counter(names).items() if count > 1)
    conflicts = sorted(name for name in names if bad_entry.search(name) or "/.DS_Store" in name)
    if repeated or conflicts:
        details = repeated[:10] + conflicts[:10]
        raise SystemExit(f"duplicate/conflict entries found in {jar}: " + ", ".join(details))

for jar_dir in root.glob("docker/**/jar"):
    packaged = sorted(path for path in jar_dir.glob("*.jar") if path.is_file())
    if len(packaged) > 1:
        raise SystemExit(
            "packaged service directory owns more than one JAR: "
            + str(jar_dir.relative_to(root))
        )
PY

if [ -n "$MANIFEST" ]; then
    [ -f "$MANIFEST" ] || { echo "release manifest is missing: $MANIFEST" >&2; exit 44; }
    case "$(basename "$MANIFEST")" in
        system-management-release-20260714.json)
            python3 "$ROOT_DIR/scripts/verify_system_management_release_contract.py" \
                --root "$ROOT_DIR" --manifest "$MANIFEST"
            ;;
        *)
            echo "unsupported build-tree manifest: $MANIFEST" >&2
            exit 45
            ;;
    esac
fi

if [ "$MODE" = "pre-build" ]; then
    find "$ROOT_DIR" \
        \( -path "$ROOT_DIR/.git" -o -path '*/node_modules' \) -prune -o \
        -type d \( -name target -o -name dist \) -print > "$TMP_FILE"
    if [ -s "$TMP_FILE" ]; then
        echo "pre-build tree contains target/dist directories:" >&2
        sed -n '1,80p' "$TMP_FILE" >&2
        exit 42
    fi
    if [ -d "$ROOT_DIR/.git" ] && [ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]; then
        echo "pre-build checkout is not clean" >&2
        exit 43
    fi
fi

echo "BUILD_TREE_CLEAN mode=$MODE root=$ROOT_DIR"
