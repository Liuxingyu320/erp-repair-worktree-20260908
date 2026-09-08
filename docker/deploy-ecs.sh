#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$BASE_DIR/.env"
COMPOSE_FILE="$BASE_DIR/docker-compose.ecs-host.yml"
PREFLIGHT="$BASE_DIR/preflight-ecs.sh"
HEALTHCHECK="$BASE_DIR/healthcheck-ecs.sh"
DRY_RUN=false

usage() {
  cat <<'USAGE'
Usage: ./deploy-ecs.sh [--dry-run]

--dry-run performs the complete read-only release preflight and exits without
building, pulling, creating directories, or changing container state.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unsupported deployment option: $1" >&2
      usage >&2
      exit 64
      ;;
  esac
done

[[ -x "$PREFLIGHT" && -x "$HEALTHCHECK" ]] || {
  echo "production preflight and health-check scripts must be executable" >&2
  exit 66
}

"$PREFLIGHT" --env-file "$ENV_FILE" --compose-file "$COMPOSE_FILE"
if [[ "$DRY_RUN" == true ]]; then
  echo "production deployment dry-run passed; no host or container state was changed"
  exit 0
fi

docker info >/dev/null 2>&1 || {
  echo "Docker daemon is unavailable; provision and start Docker before deployment" >&2
  exit 69
}

WAIT_TIMEOUT="${ERP_COMPOSE_WAIT_TIMEOUT:-300}"
if [[ ! "$WAIT_TIMEOUT" =~ ^[1-9][0-9]*$ ]] || (( WAIT_TIMEOUT > 1800 )); then
  echo "ERP_COMPOSE_WAIT_TIMEOUT must be an integer between 1 and 1800 seconds" >&2
  exit 64
fi

ERP_UPLOAD_ROOT="$(awk -F= '$1 == "ERP_UPLOAD_ROOT" { value=substr($0, index($0, "=") + 1) } END { print value }' "$ENV_FILE")"
[[ "$ERP_UPLOAD_ROOT" == /data/erp-new-data/uploadPath ]] || {
  echo "ERP_UPLOAD_ROOT differs from the approved /data path" >&2
  exit 73
}
mkdir -p "$ERP_UPLOAD_ROOT"
[[ -d "$ERP_UPLOAD_ROOT" && -w "$ERP_UPLOAD_ROOT" ]] || {
  echo "ERP_UPLOAD_ROOT is not a writable directory" >&2
  exit 73
}
[[ "$(findmnt -n -o TARGET -T "$ERP_UPLOAD_ROOT")" == /data ]] || {
  echo "ERP_UPLOAD_ROOT is not mounted on /data" >&2
  exit 73
}
while IFS='=' read -r name expected; do
  actual="$(awk -F= -v wanted="$name" '$1 == wanted { print substr($0, index($0, "=") + 1) }' "$ENV_FILE")"
  [[ "$actual" == "$expected" && -d "$actual" && -w "$actual" ]] || {
    echo "$name does not resolve to a writable common-upload subdirectory" >&2
    exit 73
  }
done <<EOF
SIGN_PACKAGE_STORAGE_ROOT=$ERP_UPLOAD_ROOT/private/sign-package
OA_ATTENDANCE_STORAGE_ROOT=$ERP_UPLOAD_ROOT/private/attendance
OA_REIMBURSEMENT_STORAGE_ROOT=$ERP_UPLOAD_ROOT/private/reimbursement
DRIVE_LOCAL_PATH=$ERP_UPLOAD_ROOT/private/drive
EOF

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
"${compose[@]}" config --quiet
"${compose[@]}" up -d --build --wait --wait-timeout "$WAIT_TIMEOUT"

# Canonical HTTP readiness contract:
# gateway:8080 auth:9200 monitor:9100 system:9201 job:9203
# oa:9204 inventory:9205 file:9300 approval:9206
# Java services use /actuator/health; the edge path includes /prod-api/code.
ERP_ENV_FILE="$ENV_FILE" ERP_COMPOSE_FILE="$COMPOSE_FILE" "$HEALTHCHECK"
"${compose[@]}" ps
