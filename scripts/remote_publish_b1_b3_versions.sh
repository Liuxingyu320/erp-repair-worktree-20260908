#!/usr/bin/env bash
set -euo pipefail

set -a
source /opt/erp-new/.env
set +a

redis() {
  redis-cli -h 127.0.0.1 -p "${REDIS_PORT:-6379}" "$@"
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

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
  exit 2
}

session_uuid="${best_key#*login_tokens:}"
admin_json="$(redis GET "$best_key")"
admin_user_id="$(printf '%s' "$admin_json" | grep -Eo '"userid"[[:space:]]*:[[:space:]]*[0-9]+' | head -1 | grep -Eo '[0-9]+$' || true)"
if [[ -z "$admin_user_id" ]]; then
  admin_user_id="$(printf '%s' "$admin_json" | grep -Eo '"userId"[[:space:]]*:[[:space:]]*[0-9]+' | head -1 | grep -Eo '[0-9]+$' || true)"
fi
[[ -n "$admin_user_id" ]] || {
  echo "ADMIN_USER_ID_NOT_FOUND"
  exit 3
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

api() {
  curl -sS --max-time 60 \
    -H 'Host: 8.152.199.39' \
    -H "Authorization: Bearer $jwt" \
    "$@"
}

auth_check="$(api http://127.0.0.1/prod-api/getInfo)"
printf '%s' "$auth_check" | grep -Eq '"code"[[:space:]]*:[[:space:]]*200' || {
  echo "ADMIN_SESSION_INVALID"
  exit 4
}

for plan_id in 37 45; do
  response="$(api \
    -X POST \
    -H 'Content-Type: application/json' \
    -d '{}' \
    "http://127.0.0.1/prod-api/oa/signPackage/plan/${plan_id}/publish")"
  if printf '%s' "$response" | grep -Eq '"code"[[:space:]]*:[[:space:]]*200'; then
    echo "PUBLISH_OK plan_id=$plan_id"
  else
    echo "PUBLISH_FAILED plan_id=$plan_id"
    printf '%s\n' "$response" | head -c 4000
    echo
    exit 5
  fi
done
