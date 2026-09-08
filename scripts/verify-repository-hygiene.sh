#!/usr/bin/env bash

set -Eeuo pipefail

ERP_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ERP_REPO_ROOT"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    printf '[FAIL] repository hygiene verification requires a Git worktree\n' >&2
    exit 1
fi

violations=()
while IFS= read -r -d '' tracked_path; do
    # A dirty worktree may already contain an intended deletion. CI sees the committed
    # state, while local verification should judge only files that still exist.
    [[ -e "$tracked_path" || -L "$tracked_path" ]] || continue

    case "$tracked_path" in
        target/*|*/target/*|dist/*|*/dist/*|node_modules/*|*/node_modules/*|\
        output/*|*/output/*|__pycache__/*|*/__pycache__/*|*.pyc|*/.DS_Store|.DS_Store|\
        docker/release/*|docker/mysql/releases/*|docker/erp/*/jar/*.jar|\
        erp-*.tar.gz|employee-data-import-*.tar.gz)
            violations+=("$tracked_path")
            continue
            ;;
    esac

    file_name="${tracked_path##*/}"
    if [[ "$file_name" =~ \ [2-9][0-9]*\.[^/]+$ ]]; then
        violations+=("$tracked_path")
    fi
done < <(git ls-files -z)

if (( ${#violations[@]} > 0 )); then
    printf '[FAIL] generated, deployment, or numbered-copy files are tracked:\n' >&2
    printf '  - %s\n' "${violations[@]}" >&2
    printf 'Move recoverable artifacts under output/ and remove them from version control.\n' >&2
    exit 1
fi

tracked_count="$(git ls-files | wc -l | tr -d ' ')"
printf '[PASS] repository hygiene: %s tracked paths contain no forbidden generated artifacts\n' \
    "$tracked_count"
