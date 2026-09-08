#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ERP_ENV_FILE:-$BASE_DIR/.env}"
COMPOSE_FILE="${ERP_COMPOSE_FILE:-$BASE_DIR/docker-compose.ecs-host.yml}"
ATTEMPTS="${ERP_HEALTH_ATTEMPTS:-30}"
INTERVAL_SECONDS="${ERP_HEALTH_INTERVAL_SECONDS:-2}"
CURL_TIMEOUT_SECONDS="${ERP_HEALTH_CURL_TIMEOUT_SECONDS:-5}"

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

for setting in "$ATTEMPTS" "$INTERVAL_SECONDS" "$CURL_TIMEOUT_SECONDS"; do
  is_positive_integer "$setting" || {
    echo "health-check timing values must be positive integers" >&2
    exit 64
  }
done
if (( ATTEMPTS > 120 || INTERVAL_SECONDS > 30 || CURL_TIMEOUT_SECONDS > 30 )); then
  echo "health-check timing values exceed the approved safety bounds" >&2
  exit 64
fi

[[ -r "$ENV_FILE" && -r "$COMPOSE_FILE" ]] || {
  echo "production environment or Compose file is unreadable" >&2
  exit 65
}
command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 || {
  echo "Docker Compose plugin is required for health checks" >&2
  exit 69
}
command -v curl >/dev/null 2>&1 || {
  echo "curl is required for HTTP readiness checks" >&2
  exit 69
}
command -v python3 >/dev/null 2>&1 || {
  echo "python3 is required for business-response validation" >&2
  exit 69
}

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
services=(
  erp-mysql
  erp-redis
  erp-modules-system
  erp-auth
  erp-gateway
  erp-modules-job
  erp-modules-oa
  erp-modules-inventory
  erp-modules-approval
  erp-modules-file
  erp-visual-monitor
  erp-nginx
)

wait_for_container_health() {
  local service="$1"
  local attempt
  local container_id
  local status
  for ((attempt = 1; attempt <= ATTEMPTS; attempt++)); do
    container_id="$("${compose[@]}" ps -q "$service")"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      if [[ "$status" == healthy ]]; then
        return 0
      fi
    fi
    sleep "$INTERVAL_SECONDS"
  done
  echo "container health check failed: $service" >&2
  return 1
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local mode="${3:-http}"
  local attempt
  local body
  for ((attempt = 1; attempt <= ATTEMPTS; attempt++)); do
    if [[ "$mode" == actuator ]]; then
      if body="$(curl -fsS --max-time "$CURL_TIMEOUT_SECONDS" "$url")" &&
        grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$body"; then
        return 0
      fi
    elif [[ "$mode" == business-json ]]; then
      if body="$(curl -fsS --max-time "$CURL_TIMEOUT_SECONDS" "$url")" &&
        printf '%s' "$body" | python3 -c '
import json
import sys
try:
    payload = json.load(sys.stdin)
except Exception:
    raise SystemExit(1)
code = payload.get("code") if isinstance(payload, dict) else None
raise SystemExit(0 if code == 200 else 1)
'; then
        return 0
      fi
    elif curl -fsS --max-time "$CURL_TIMEOUT_SECONDS" "$url" >/dev/null; then
      return 0
    fi
    sleep "$INTERVAL_SECONDS"
  done
  echo "HTTP readiness check failed: $label" >&2
  return 1
}

for service in "${services[@]}"; do
  wait_for_container_health "$service"
done

expected_host_root=/data/erp-new-data/uploadPath
expected_container_root=/data/erp-new-data/uploadPath
for storage_service in erp-modules-oa erp-modules-file; do
  container_id="$("${compose[@]}" ps -q "$storage_service")"
  [[ -n "$container_id" ]]
  container_environment="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container_id")"
  for assignment in \
    "ERP_UPLOAD_ROOT=$expected_container_root" \
    "FILE_PATH=$expected_container_root" \
    "SIGN_PACKAGE_STORAGE_ROOT=$expected_container_root/private/sign-package" \
    "OA_ATTENDANCE_STORAGE_ROOT=$expected_container_root/private/attendance" \
    "OA_REIMBURSEMENT_STORAGE_ROOT=$expected_container_root/private/reimbursement" \
    "DRIVE_LOCAL_PATH=$expected_container_root/private/drive"; do
    grep -Fqx "$assignment" <<<"$container_environment" || {
      echo "$storage_service container environment differs: ${assignment%%=*}" >&2
      exit 1
    }
  done
  if [[ "$storage_service" == erp-modules-oa ]]; then
    grep -Fqx "OA_SIGN_EXCEL_IMPORT_ENABLED=true" <<<"$container_environment" || {
      echo "OA container disabled the required Excel signing import entry" >&2
      exit 1
    }
  fi
  upload_mount_source="$(docker inspect --format \
    '{{range .Mounts}}{{if eq .Destination "/data/erp-new-data/uploadPath"}}{{printf "%s" .Source}}{{end}}{{end}}' \
    "$container_id")"
  [[ "$upload_mount_source" == "$expected_host_root" ]] || {
    echo "$storage_service common-upload mount differs from $expected_host_root" >&2
    exit 1
  }
done

for entry in gateway:8080 auth:9200 system:9201 job:9203 oa:9204 inventory:9205 approval:9206 file:9300; do
  name="${entry%%:*}"
  port="${entry#*:}"
  wait_for_http "http://127.0.0.1:$port/actuator/health" "$name" actuator
done
wait_for_http "http://127.0.0.1:9100/" monitor
wait_for_http "http://127.0.0.1/" frontend
wait_for_http "http://127.0.0.1/prod-api/code" "gateway captcha endpoint" business-json

echo "production health check passed: containers=12 http_endpoints=11"
