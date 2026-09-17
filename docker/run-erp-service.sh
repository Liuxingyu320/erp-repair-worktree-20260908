#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE="${1:-}"
LEGACY_RUNNER="$BASE_DIR/run-erp-service-legacy.sh"
ENV_FILE="$BASE_DIR/.env"
DELEGATE_TO_LEGACY=false

case "$SERVICE" in
  monitor)
    JAR_PATH="$BASE_DIR/erp/visual/monitor/jar/erp-visual-monitor.jar"
    EXTRA_ARGS=(
      --spring.cloud.nacos.discovery.enabled=false
      --spring.cloud.nacos.config.enabled=false
      --spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
    )
    ;;
  approval)
    JAR_PATH="$BASE_DIR/erp/modules/approval/jar/erp-modules-approval.jar"
    EXTRA_ARGS=()
    ;;
  gateway|auth|system|job|oa|inventory|file)
    DELEGATE_TO_LEGACY=true
    ;;
  *)
    echo "unsupported ERP service: ${SERVICE:-<empty>}" >&2
    exit 64
    ;;
esac

test -r "$ENV_FILE" || {
  echo "missing host environment file: $ENV_FILE" >&2
  exit 65
}
# Parse dotenv assignments without evaluating shell syntax. This preserves the
# host-specific secret/config file while preventing command substitution.
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "$line" || "$line" == \#* ]] && continue
  key="${line%%=*}"
  value="${line#*=}"
  if [[ "$line" != *=* || ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "invalid dotenv assignment for host runner: $key" >&2
    exit 65
  fi
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"
    last="${value: -1}"
    if [[ ( "$first" == '"' && "$last" == '"' ) || ( "$first" == "'" && "$last" == "'" ) ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  export "$key=$value"
done < "$ENV_FILE"

require_env_value() {
  local name="$1"
  local expected="$2"
  local actual="${!name:-}"
  if [[ "$actual" != "$expected" ]]; then
    echo "$name must equal $expected for host runtime" >&2
    exit 65
  fi
}

if [[ "$SERVICE" == oa || "$SERVICE" == file ]]; then
  COMMON_UPLOAD_ROOT=/data/erp-new-data/uploadPath
  require_env_value ERP_UPLOAD_ROOT "$COMMON_UPLOAD_ROOT"
  require_env_value FILE_PATH "$COMMON_UPLOAD_ROOT"
  require_env_value SIGN_PACKAGE_STORAGE_ROOT "$COMMON_UPLOAD_ROOT/private/sign-package"
  require_env_value OA_ATTENDANCE_STORAGE_ROOT "$COMMON_UPLOAD_ROOT/private/attendance"
  require_env_value OA_REIMBURSEMENT_STORAGE_ROOT "$COMMON_UPLOAD_ROOT/private/reimbursement"
  require_env_value DRIVE_LOCAL_PATH "$COMMON_UPLOAD_ROOT/private/drive"
  require_env_value OA_SIGN_EXCEL_IMPORT_ENABLED true

  for storage_path in \
    "$ERP_UPLOAD_ROOT" \
    "$SIGN_PACKAGE_STORAGE_ROOT" \
    "$OA_ATTENDANCE_STORAGE_ROOT" \
    "$OA_REIMBURSEMENT_STORAGE_ROOT" \
    "$DRIVE_LOCAL_PATH"; do
    [[ -d "$storage_path" && -w "$storage_path" ]] || {
      echo "required persistent storage path is missing or not writable: $storage_path" >&2
      exit 73
    }
  done
  for release_upload in "$BASE_DIR/uploadPath" "$BASE_DIR/erp/uploadPath"; do
    [[ -L "$release_upload" ]] || {
      echo "release uploadPath must be a symlink: $release_upload" >&2
      exit 73
    }
    [[ "$(readlink -f "$release_upload")" == "$COMMON_UPLOAD_ROOT" ]] || {
      echo "release uploadPath points outside the common persistent root: $release_upload" >&2
      exit 73
    }
  done
fi

# All host processes, including legacy delegates, share the same data-disk contract.
# Do not mkdir before validating the mount: that would silently create system-disk storage.
require_data_path() {
  local path="$1"
  [[ -d "$path" && -w "$path" ]] &&
    [[ "$(readlink -f "$path")" == /data/* ]] &&
    [[ "$(findmnt -n -o TARGET -T "$path")" == /data ]] || {
      echo "required project path must be writable on the /data mount: $path" >&2
      exit 73
    }
}
require_data_path "$BASE_DIR"
export ERP_LOG_ROOT="${ERP_LOG_ROOT:-/data/erp-new-data/logs}"
export ERP_TEMP_ROOT="${ERP_TEMP_ROOT:-/data/erp-new-data/tmp}"
export ERP_CACHE_ROOT="${ERP_CACHE_ROOT:-/data/erp-new-data/cache}"
require_env_value ERP_LOG_ROOT /data/erp-new-data/logs
require_env_value ERP_TEMP_ROOT /data/erp-new-data/tmp
require_env_value ERP_CACHE_ROOT /data/erp-new-data/cache
for project_path in "$ERP_LOG_ROOT" "$ERP_TEMP_ROOT" "$ERP_CACHE_ROOT" \
    /data/erp-new-data/uploadPath /data/erp-new/releases /data/erp-new/packages /data/erp-new/backups; do
  require_data_path "$project_path"
done
for storage_path in /data/erp-new-data/uploadPath/private/{sign-package,sign-package-temp,attendance,reimbursement,drive}; do
  require_data_path "$storage_path"
done
[[ -L "$BASE_DIR/logs" && "$(readlink -f "$BASE_DIR/logs")" == "$ERP_LOG_ROOT" ]] || {
  echo "release logs must link to $ERP_LOG_ROOT" >&2
  exit 73
}
export SIGN_PACKAGE_TEMP_ROOT=/data/erp-new-data/uploadPath/private/sign-package-temp
export OA_ATTENDANCE_TEMP_ROOT="$ERP_TEMP_ROOT/attendance"
export OA_REIMBURSEMENT_TEMP_ROOT="$ERP_TEMP_ROOT/reimbursement"
for project_path in "$ERP_LOG_ROOT/$SERVICE" "$ERP_TEMP_ROOT/$SERVICE" "$ERP_CACHE_ROOT/$SERVICE" \
    "$OA_ATTENDANCE_TEMP_ROOT" "$OA_REIMBURSEMENT_TEMP_ROOT"; do
  require_data_path "$project_path"
done
export ERP_IMAGE_PYTHON="${ERP_IMAGE_PYTHON:-/data/erp-new-runtime/image-codecs/bin/python}"
require_env_value ERP_IMAGE_PYTHON /data/erp-new-runtime/image-codecs/bin/python
if [[ "$SERVICE" == file ]]; then
  require_data_path /data/erp-new-runtime/image-codecs
  [[ -x "$ERP_IMAGE_PYTHON" && "$(findmnt -n -o TARGET -T "$ERP_IMAGE_PYTHON")" == /data ]] || exit 73
  "$ERP_IMAGE_PYTHON" -I -c 'from PIL import features; import pillow_heif; assert features.check("webp"); assert pillow_heif.libheif_info()["HEIF"]'
fi
export TMPDIR="$ERP_TEMP_ROOT/$SERVICE"
export XDG_CACHE_HOME="$ERP_CACHE_ROOT/$SERVICE"
export XDG_CONFIG_HOME="$ERP_CACHE_ROOT/$SERVICE/config"
# Legacy runners may source .env again. Reserve the launcher variable so that
# reloading JAVA_TOOL_OPTIONS cannot erase the enforced storage locations.
if grep -Eq '^JDK_JAVA_OPTIONS=' "$ENV_FILE"; then
  echo "JDK_JAVA_OPTIONS is reserved by the host storage contract" >&2
  exit 65
fi
export JDK_JAVA_OPTIONS="${JDK_JAVA_OPTIONS:-} -Djava.io.tmpdir=$TMPDIR -Dcsp.sentinel.log.dir=$ERP_LOG_ROOT/$SERVICE/sentinel -Duser.home=$ERP_CACHE_ROOT/$SERVICE"
# Covers console-only services on hosts whose systemd lacks StandardOutput=append.
exec >>"$ERP_LOG_ROOT/$SERVICE/stdout.log" 2>>"$ERP_LOG_ROOT/$SERVICE/stderr.log"

if [[ "$DELEGATE_TO_LEGACY" == true ]]; then
  test -x "$LEGACY_RUNNER" || {
    echo "missing legacy ERP service runner: $LEGACY_RUNNER" >&2
    exit 64
  }
  exec "$LEGACY_RUNNER" "$@"
fi

test -s "$JAR_PATH" || {
  echo "missing service artifact: $JAR_PATH" >&2
  exit 66
}

ERP_JAVA_BIN="${ERP_JAVA_BIN:-/www/server/java/jdk-17.0.8/bin/java}"
if [[ ! -x "$ERP_JAVA_BIN" ]]; then
  ERP_JAVA_BIN="$(command -v java || true)"
fi
[[ -n "$ERP_JAVA_BIN" && -x "$ERP_JAVA_BIN" ]] || {
  echo "java is required to start $SERVICE" >&2
  exit 69
}

PROFILE="${SPRING_PROFILE:-${SPRING_PROFILES_ACTIVE:-local}}"
JAVA_OPTIONS=(-Xms128m -Xmx768m -XX:+ExitOnOutOfMemoryError)
if [[ -n "${JAVA_OPTS:-}" ]]; then
  read -r -a JAVA_OPTIONS <<<"$JAVA_OPTS"
fi

cd "$BASE_DIR"
COMMAND=("$ERP_JAVA_BIN")
if [[ ${#JAVA_OPTIONS[@]} -gt 0 ]]; then
  COMMAND+=("${JAVA_OPTIONS[@]}")
fi
COMMAND+=(-jar "$JAR_PATH" "--spring.profiles.active=$PROFILE")
if [[ "$SERVICE" == monitor ]]; then
  COMMAND+=("${EXTRA_ARGS[@]}")
fi
exec "${COMMAND[@]}"
