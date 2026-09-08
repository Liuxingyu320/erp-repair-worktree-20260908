#!/usr/bin/env bash
set -euo pipefail

preview_file=/tmp/codex-onboard-preview-20260724.xlsx
[[ -s "$preview_file" ]] || {
  echo "PREVIEW_FILE_MISSING"
  exit 2
}

set -a
source /opt/erp-new/.env
set +a

redis() {
  redis-cli -h 127.0.0.1 -p "${REDIS_PORT:-6379}" "$@"
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"; rm -f "$preview_file"' EXIT

redis --scan --pattern '*login_tokens*' > "$tmp_dir/keys"
best_key=''
best_ttl=-1
while IFS= read -r key; do
  [[ -n "$key" ]] || continue
  value="$(redis GET "$key")"
  if printf '%s' "$value" | grep -Eq '"username"[[:space:]]*:[[:space:]]*"admin"|"userName"[[:space:]]*:[[:space:]]*"admin"'; then
    ttl="$(redis TTL "$key")"
    if [[ "$ttl" -gt "$best_ttl" ]]; then
      best_ttl="$ttl"
      best_key="$key"
    fi
  fi
done < "$tmp_dir/keys"
[[ -n "$best_key" ]] || {
  echo "ADMIN_SESSION_NOT_FOUND"
  exit 3
}

session_uuid="${best_key#*login_tokens:}"
admin_json="$(redis GET "$best_key")"
admin_user_id="$(printf '%s' "$admin_json" | grep -Eo '"userid"[[:space:]]*:[[:space:]]*[0-9]+' | head -1 | grep -Eo '[0-9]+$' || true)"
if [[ -z "$admin_user_id" ]]; then
  admin_user_id="$(printf '%s' "$admin_json" | grep -Eo '"userId"[[:space:]]*:[[:space:]]*[0-9]+' | head -1 | grep -Eo '[0-9]+$' || true)"
fi
[[ -n "$admin_user_id" ]] || {
  echo "ADMIN_USER_ID_NOT_FOUND"
  exit 4
}

jwt="$(
  ADMIN_USER_ID="$admin_user_id" SESSION_UUID="$session_uuid" JWT_SECRET="$ERP_JWT_SECRET" python3 - <<'PY'
import base64
import hashlib
import hmac
import json
import os

header = {"alg": "HS512"}
payload = {
    "user_key": os.environ["SESSION_UUID"],
    "user_id": int(os.environ["ADMIN_USER_ID"]),
    "username": "admin",
}

def enc(value):
    raw = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).rstrip(b"=")

body = enc(header) + b"." + enc(payload)
secret = os.environ["JWT_SECRET"]
secret += "=" * (-len(secret) % 4)
key = base64.b64decode(secret)
signature = hmac.new(key, body, hashlib.sha512).digest()
print((body + b"." + base64.urlsafe_b64encode(signature).rstrip(b"=")).decode())
PY
)"

http_code="$(
  curl -sS --max-time 180 \
    -o "$tmp_dir/response.json" \
    -w '%{http_code}' \
    -H 'Host: 8.152.199.39' \
    -H "Authorization: Bearer $jwt" \
    -H 'Sign-Scope-Dept-Id: 101' \
    -F "file=@${preview_file};type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
    -F 'employeeIds=[]' \
    -F 'matchMode=EXCEL_PHONE_NAME' \
    http://127.0.0.1/prod-api/oa/signTask/onboard/import/preview
)"
[[ "$http_code" = 200 ]] || {
  echo "PREVIEW_HTTP_FAILED status=$http_code"
  head -c 3000 "$tmp_dir/response.json"
  echo
  exit 5
}

RESPONSE_FILE="$tmp_dir/response.json" python3 - <<'PY'
import json
import os

with open(os.environ["RESPONSE_FILE"], "r", encoding="utf-8") as handle:
    response = json.load(handle)
if response.get("code") != 200:
    raise SystemExit("PREVIEW_API_FAILED code={} msg={}".format(
        response.get("code"), response.get("msg")))

data = response.get("data") or {}
rows = data.get("rows") or []
b3_rows = [row for row in rows if row.get("routeCode") == "B3"]
print("PREVIEW_OK batch_no={} rows={} matched={} errors={} warnings={}".format(
    data.get("batchNo"), data.get("totalRowCount"), data.get("matchedCount"),
    data.get("errorCount"), data.get("warningCount")))
print("B3_ROWS count={}".format(len(b3_rows)))
for row in b3_rows:
    templates = row.get("templateNames") or []
    errors = row.get("errorCodes") or []
    missing = row.get("missingFields") or []
    print("B3_RESULT source_row={} plan_version_id={} plan_name={} commitment={} "
          "legacy_missing_commitment_error={} status={} errors={} missing={}".format(
              row.get("sourceRowNumber"),
              row.get("planVersionId"),
              row.get("planName"),
              "入职承诺书" in templates,
              any("ONBOARD_COMMITMENT" in str(item) or "入职承诺书" in str(item)
                  for item in errors),
              row.get("status"),
              json.dumps(errors, ensure_ascii=False, separators=(",", ":")),
              json.dumps(missing, ensure_ascii=False, separators=(",", ":"))))

if not b3_rows:
    raise SystemExit("B3_ROW_NOT_FOUND")
if not all("入职承诺书" in (row.get("templateNames") or []) for row in b3_rows):
    raise SystemExit("B3_COMMITMENT_TEMPLATE_NOT_INCLUDED")
if any(any("ONBOARD_COMMITMENT" in str(item) or "入职承诺书" in str(item)
           for item in (row.get("errorCodes") or [])) for row in b3_rows):
    raise SystemExit("B3_STILL_HAS_COMMITMENT_ERROR")
PY
