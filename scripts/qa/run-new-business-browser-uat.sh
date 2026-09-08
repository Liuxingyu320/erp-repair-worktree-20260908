#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG="${1:-}"
if [[ -z "$CONFIG" ]]; then
  echo "usage: $0 /absolute/path/to/uat.local.json" >&2
  exit 2
fi

"$ROOT_DIR/scripts/qa/require-isolated-qa-env.sh" >/dev/null

PLAYWRIGHT_CLI="${ERP_PLAYWRIGHT_CLI:-$HOME/.codex/skills/playwright/scripts/playwright_cli.sh}"
if [[ ! -x "$PLAYWRIGHT_CLI" ]]; then
  echo "[FAIL] Playwright CLI wrapper is unavailable: $PLAYWRIGHT_CLI" >&2
  exit 2
fi

# A Homebrew Node upgrade may temporarily leave the default binary unusable.
# Callers can provide ERP_NODE_BIN_DIR; otherwise select a working Node 22.
if [[ -n "${ERP_NODE_BIN_DIR:-}" ]]; then
  export PATH="$ERP_NODE_BIN_DIR:$PATH"
elif ! node --version >/dev/null 2>&1; then
  node22="$(find /opt/homebrew/Cellar/node@22 -maxdepth 2 -type d -name bin 2>/dev/null | sort -V | tail -n 1)"
  if [[ -z "$node22" || ! -x "$node22/node" ]]; then
    echo "[FAIL] a working Node.js runtime is required" >&2
    exit 2
  fi
  export PATH="$node22:$PATH"
fi

ROWS_FILE="$(mktemp)"
trap 'rm -f "$ROWS_FILE"' EXIT
python3 "$ROOT_DIR/scripts/qa/prepare_new_business_browser_uat.py" \
  --config "$CONFIG" > "$ROWS_FILE"

base_url="$(python3 - "$CONFIG" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["baseUrl"].rstrip("/"))
PY
)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
artifact_dir="${ERP_UAT_BROWSER_OUTPUT_DIR:-$ROOT_DIR/output/playwright/new-business-$stamp}"
mkdir -p "$artifact_dir"
report="$artifact_dir/browser-results.tsv"
printf 'id\tactor\tcontextDeptId\tpath\tstatus\tscreenshotSha256\n' > "$report"

failed=0
while IFS=$'\t' read -r page_id actor state_file dept_id dept_name dept_type path expected_text; do
  session="nb-${ERP_QA_RUN_ID//[^A-Za-z0-9_-]/-}-${page_id}"
  snapshot="$artifact_dir/$page_id.snapshot.txt"
  find_result="$artifact_dir/$page_id.find.txt"
  screenshot="$artifact_dir/$page_id.png"
  status=passed
  {
    "$PLAYWRIGHT_CLI" -s="$session" open "$base_url"
    "$PLAYWRIGHT_CLI" -s="$session" state-load "$state_file"
    "$PLAYWRIGHT_CLI" -s="$session" goto "$base_url/"
    "$PLAYWRIGHT_CLI" -s="$session" sessionstorage-set selected_dept_id "$dept_id"
    "$PLAYWRIGHT_CLI" -s="$session" sessionstorage-set selected_dept_name "$dept_name"
    "$PLAYWRIGHT_CLI" -s="$session" sessionstorage-set selected_dept_type "$dept_type"
    "$PLAYWRIGHT_CLI" -s="$session" sessionstorage-set selected_dept_validated 1
    "$PLAYWRIGHT_CLI" -s="$session" goto "$base_url$path"
    # Snapshot first: subsequent text matching is based on the current page state.
    "$PLAYWRIGHT_CLI" -s="$session" snapshot > "$snapshot"
    "$PLAYWRIGHT_CLI" -s="$session" find "$expected_text" > "$find_result"
    grep -Fq "$expected_text" "$find_result"
    "$PLAYWRIGHT_CLI" -s="$session" screenshot \
      --filename "$screenshot" --full-page
  } || status=failed
  "$PLAYWRIGHT_CLI" -s="$session" close >/dev/null 2>&1 || true
  if [[ "$status" == passed && -s "$screenshot" ]]; then
    sha="$(shasum -a 256 "$screenshot" | awk '{print $1}')"
  else
    sha=""
    failed=$((failed + 1))
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$page_id" "$actor" "$dept_id" "$path" "$status" "$sha" >> "$report"
done < "$ROWS_FILE"

echo "[INFO] browser UAT artifacts: $artifact_dir"
if (( failed > 0 )); then
  echo "[FAIL] $failed browser UAT page(s) failed" >&2
  exit 1
fi
echo "[PASS] browser UAT route, role and store-context smoke passed"
