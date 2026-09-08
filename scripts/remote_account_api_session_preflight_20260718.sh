#!/usr/bin/env bash
set -euo pipefail
umask 077

ENV_FILE="/opt/erp-new/.env"
if [[ ! -r "$ENV_FILE" ]]; then
  echo "ENV_FILE_UNAVAILABLE" >&2
  exit 2
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${ERP_JWT_SECRET:?ERP_JWT_SECRET is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"

if [[ "$(redis-cli --raw PING 2>/dev/null || true)" == "PONG" ]]; then
  redis=(redis-cli --raw)
else
  redis=(redis-cli --no-auth-warning -a "$REDIS_PASSWORD" --raw)
fi
[[ "$("${redis[@]}" PING)" == "PONG" ]]

admin_tokens=( $("${redis[@]}" SMEMBERS user_login_tokens:1) )
live_admin=0
for token_id in "${admin_tokens[@]}"; do
  token_id="${token_id#\"}"
  token_id="${token_id%\"}"
  if [[ -n "$token_id" ]] && [[ "$("${redis[@]}" EXISTS "login_tokens:${token_id}")" == "1" ]]; then
    live_admin=$((live_admin + 1))
  fi
done

hr_tokens=( $("${redis[@]}" SMEMBERS user_login_tokens:940) )
live_hr=0
for token_id in "${hr_tokens[@]}"; do
  token_id="${token_id#\"}"
  token_id="${token_id%\"}"
  if [[ -n "$token_id" ]] && [[ "$("${redis[@]}" EXISTS "login_tokens:${token_id}")" == "1" ]]; then
    live_hr=$((live_hr + 1))
  fi
done

all_live="$("${redis[@]}" --scan --pattern 'login_tokens:*' | wc -l | tr -d ' ')"

printf 'env_file=ready jwt_secret=ready redis=ready live_sessions=%s admin_indexed=%s admin_live=%s hr_indexed=%s hr_live=%s\n' \
  "$all_live" "${#admin_tokens[@]}" "$live_admin" "${#hr_tokens[@]}" "$live_hr"

if [[ "$live_admin" -lt 1 ]]; then
  echo "ADMIN_SESSION_REQUIRED" >&2
  exit 3
fi

http_code="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --max-time 5 http://127.0.0.1:8080/system/user/list || true)"
printf 'gateway_without_token_http=%s\n' "$http_code"
echo "ACCOUNT_API_SESSION_PREFLIGHT_OK"
