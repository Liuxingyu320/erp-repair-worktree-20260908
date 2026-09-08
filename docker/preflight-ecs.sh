#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$BASE_DIR/.env"
COMPOSE_FILE="$BASE_DIR/docker-compose.ecs-host.yml"
NACOS_PREFLIGHT="$BASE_DIR/nacos-production-preflight.py"
CONFIG_ONLY=false

usage() {
  cat <<'USAGE'
Usage: ./preflight-ecs.sh [--config-only] [--env-file PATH] [--compose-file PATH]

Runs a read-only production deployment preflight. It never builds, starts,
stops, restarts, or pulls containers. --config-only skips release artifact
presence checks and is intended for CI configuration validation.
USAGE
}

absolute_from_base() {
  local path="$1"
  if [[ "$path" == /* ]]; then
    printf '%s\n' "$path"
  else
    printf '%s/%s\n' "$BASE_DIR" "$path"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config-only)
      CONFIG_ONLY=true
      shift
      ;;
    --env-file)
      [[ $# -ge 2 ]] || {
        echo "--env-file requires a path" >&2
        exit 64
      }
      ENV_FILE="$(absolute_from_base "$2")"
      shift 2
      ;;
    --compose-file)
      [[ $# -ge 2 ]] || {
        echo "--compose-file requires a path" >&2
        exit 64
      }
      COMPOSE_FILE="$(absolute_from_base "$2")"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unsupported preflight option: $1" >&2
      usage >&2
      exit 64
      ;;
  esac
done

[[ -r "$ENV_FILE" && -f "$ENV_FILE" ]] || {
  echo "production environment file is missing or unreadable" >&2
  exit 65
}
[[ -r "$COMPOSE_FILE" && -f "$COMPOSE_FILE" ]] || {
  echo "production Compose file is missing or unreadable" >&2
  exit 66
}
[[ -r "$NACOS_PREFLIGHT" && -f "$NACOS_PREFLIGHT" ]] || {
  echo "Nacos production preflight is missing or unreadable" >&2
  exit 66
}

env_mode=""
if env_mode="$(stat -c '%a' "$ENV_FILE" 2>/dev/null)"; then
  :
elif env_mode="$(stat -f '%Lp' "$ENV_FILE" 2>/dev/null)"; then
  :
fi
if [[ -n "$env_mode" ]] && (( (8#$env_mode & 077) != 0 )); then
  echo "production environment file must not be accessible by group or others" >&2
  exit 65
fi

command -v docker >/dev/null 2>&1 || {
  echo "Docker is required for production preflight" >&2
  exit 69
}
docker compose version >/dev/null 2>&1 || {
  echo "Docker Compose plugin is required for production preflight" >&2
  exit 69
}
command -v python3 >/dev/null 2>&1 || {
  echo "python3 is required for semantic Compose validation" >&2
  exit 69
}

upload_root="$(awk -F= '$1 == "ERP_UPLOAD_ROOT" { value=substr($0, index($0, "=") + 1) } END { print value }' "$ENV_FILE")"
expected_upload_root=/data/erp-new-data/uploadPath
if [[ "$upload_root" != "$expected_upload_root" ]]; then
  echo "ERP_UPLOAD_ROOT must occur once and equal $expected_upload_root" >&2
  exit 65
fi
if [[ "$(grep -Ec '^ERP_UPLOAD_ROOT=' "$ENV_FILE" || true)" -ne 1 ]]; then
  echo "ERP_UPLOAD_ROOT must occur exactly once" >&2
  exit 65
fi

while IFS='=' read -r name expected; do
  count="$(grep -Ec "^${name}=" "$ENV_FILE" || true)"
  actual="$(awk -F= -v wanted="$name" '$1 == wanted { print substr($0, index($0, "=") + 1) }' "$ENV_FILE")"
  if [[ "$count" -ne 1 || "$actual" != "$expected" ]]; then
    echo "$name must occur once and equal $expected" >&2
    exit 65
  fi
done <<EOF
FILE_PATH=$expected_upload_root
SIGN_PACKAGE_STORAGE_ROOT=$expected_upload_root/private/sign-package
OA_ATTENDANCE_STORAGE_ROOT=$expected_upload_root/private/attendance
OA_REIMBURSEMENT_STORAGE_ROOT=$expected_upload_root/private/reimbursement
DRIVE_LOCAL_PATH=$expected_upload_root/private/drive
OA_SIGN_EXCEL_IMPORT_ENABLED=true
EOF

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
"${compose[@]}" config --quiet

expected_services=(
  erp-auth
  erp-gateway
  erp-modules-approval
  erp-modules-file
  erp-modules-inventory
  erp-modules-job
  erp-modules-oa
  erp-modules-system
  erp-mysql
  erp-nginx
  erp-redis
  erp-visual-monitor
)
expected_list="$(printf '%s\n' "${expected_services[@]}" | LC_ALL=C sort)"
actual_list="$("${compose[@]}" config --services | LC_ALL=C sort)"
if [[ "$actual_list" != "$expected_list" ]]; then
  echo "production Compose service set differs from the approved 12-service contract" >&2
  exit 67
fi

"${compose[@]}" config --format json | python3 -c '
import json
import sys

expected = {
    "erp-auth",
    "erp-gateway",
    "erp-modules-approval",
    "erp-modules-file",
    "erp-modules-inventory",
    "erp-modules-job",
    "erp-modules-oa",
    "erp-modules-system",
    "erp-mysql",
    "erp-nginx",
    "erp-redis",
    "erp-visual-monitor",
}
required_healthy_dependencies = {
    "erp-auth": {"erp-redis", "erp-modules-system"},
    "erp-gateway": {"erp-redis"},
    "erp-modules-approval": {"erp-mysql", "erp-redis"},
    "erp-modules-file": {"erp-mysql", "erp-redis"},
    "erp-modules-inventory": {"erp-mysql", "erp-redis"},
    "erp-modules-job": {"erp-mysql", "erp-redis"},
    "erp-modules-oa": {"erp-mysql", "erp-redis"},
    "erp-modules-system": {"erp-mysql", "erp-redis"},
    "erp-nginx": {"erp-gateway"},
}
expected_container_root = "/data/erp-new-data/uploadPath"
expected_storage_environment = {
    "ERP_UPLOAD_ROOT": expected_container_root,
    "FILE_PATH": expected_container_root,
    "SIGN_PACKAGE_STORAGE_ROOT": expected_container_root + "/private/sign-package",
    "OA_ATTENDANCE_STORAGE_ROOT": expected_container_root + "/private/attendance",
    "OA_REIMBURSEMENT_STORAGE_ROOT": expected_container_root + "/private/reimbursement",
    "DRIVE_LOCAL_PATH": expected_container_root + "/private/drive",
}

document = json.load(sys.stdin)
services = document.get("services", {})
issues = []
if set(services) != expected:
    issues.append("service set")
for name in sorted(expected):
    service = services.get(name, {})
    if service.get("restart") != "unless-stopped":
        issues.append(f"{name}: restart")
    if not service.get("stop_grace_period"):
        issues.append(f"{name}: stop_grace_period")
    healthcheck = service.get("healthcheck") or {}
    if healthcheck.get("disable") or not healthcheck.get("test"):
        issues.append(f"{name}: healthcheck")
    dependencies = service.get("depends_on") or {}
    for dependency in sorted(required_healthy_dependencies.get(name, set())):
        condition = (dependencies.get(dependency) or {}).get("condition")
        if condition != "service_healthy":
            issues.append(f"{name}: depends_on {dependency}")
for storage_service in ("erp-modules-oa", "erp-modules-file"):
    service = services.get(storage_service, {})
    environment = service.get("environment") or {}
    for name, expected_value in expected_storage_environment.items():
        if environment.get(name) != expected_value:
            issues.append(f"{storage_service}: {name}")
    upload_mounts = [
        volume
        for volume in service.get("volumes") or []
        if volume.get("target") == expected_container_root
    ]
    if len(upload_mounts) != 1 or upload_mounts[0].get("source") != "/data/erp-new-data/uploadPath":
        issues.append(f"{storage_service}: common-upload mount")
if (services.get("erp-modules-oa", {}).get("environment") or {}).get("OA_SIGN_EXCEL_IMPORT_ENABLED") != "true":
    issues.append("erp-modules-oa: Excel signing import disabled")
if issues:
    print("production Compose policy validation failed: " + ", ".join(issues), file=sys.stderr)
    raise SystemExit(68)
'

NACOS_STATUS=skipped-config-only
if [[ "$CONFIG_ONLY" == false ]]; then
  command -v findmnt >/dev/null 2>&1 || {
    echo "findmnt is required for common-upload storage verification" >&2
    exit 69
  }
  for storage_path in \
    "$expected_upload_root" \
    "$expected_upload_root/private/sign-package" \
    "$expected_upload_root/private/attendance" \
    "$expected_upload_root/private/reimbursement" \
    "$expected_upload_root/private/drive"; do
    [[ -d "$storage_path" && -w "$storage_path" ]] || {
      echo "common-upload path is missing or not writable: $storage_path" >&2
      exit 73
    }
  done
  [[ "$(findmnt -n -o TARGET -T "$expected_upload_root")" == /data ]] || {
    echo "ERP_UPLOAD_ROOT must be mounted on /data" >&2
    exit 73
  }
  python3 "$NACOS_PREFLIGHT" --env-file "$ENV_FILE"
  NACOS_STATUS=verified
  command -v curl >/dev/null 2>&1 || {
    echo "curl is required for post-deployment readiness checks" >&2
    exit 69
  }
  required_artifacts=(
    erp/auth/jar/erp-auth.jar
    erp/gateway/jar/erp-gateway.jar
    erp/modules/approval/jar/erp-modules-approval.jar
    erp/modules/file/jar/erp-modules-file.jar
    erp/modules/inventory/jar/erp-modules-inventory.jar
    erp/modules/job/jar/erp-modules-job.jar
    erp/modules/oa/jar/erp-modules-oa.jar
    erp/modules/system/jar/erp-modules-system.jar
    erp/visual/monitor/jar/erp-visual-monitor.jar
    nginx/html/dist/index.html
    nginx/conf/nginx.host.conf
    redis/conf/redis.conf
  )
  missing=()
  for relative_path in "${required_artifacts[@]}"; do
    [[ -s "$BASE_DIR/$relative_path" ]] || missing+=("$relative_path")
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    printf 'release artifact is missing or empty: %s\n' "${missing[@]}" >&2
    exit 66
  fi
fi

echo "production preflight passed: services=12 restart=12 healthcheck=12 nacos=$NACOS_STATUS config_only=$CONFIG_ONLY"
