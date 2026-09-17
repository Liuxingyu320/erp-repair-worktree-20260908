#!/usr/bin/env python3
import argparse
import base64
import datetime as dt
import getpass
import gzip
import hashlib
import hmac
import json
import os
import re
import subprocess
import sys
import time
import uuid
from pathlib import Path, PurePosixPath
from urllib.parse import quote

import oss2
import requests

from release_migration_contract import (
    prepare_migration_plan,
    render_remote_migration_script,
)


OSS_REQUEST_TIMEOUT = (10, 60)
RELEASE_VERIFY_TOOL = Path(__file__).resolve().parent / "release" / "release_tool.py"

HOST_SERVICE_PORTS = {
    "gateway": 8080,
    "auth": 9200,
    "monitor": 9100,
    "system": 9201,
    "job": 9203,
    "oa": 9204,
    "inventory": 9205,
    "file": 9300,
    "approval": 9206,
}

HOST_SERVICE_ARTIFACTS = {
    "gateway": "erp/gateway/jar/erp-gateway.jar",
    "auth": "erp/auth/jar/erp-auth.jar",
    "monitor": "erp/visual/monitor/jar/erp-visual-monitor.jar",
    "system": "erp/modules/system/jar/erp-modules-system.jar",
    "job": "erp/modules/job/jar/erp-modules-job.jar",
    "oa": "erp/modules/oa/jar/erp-modules-oa.jar",
    "inventory": "erp/modules/inventory/jar/erp-modules-inventory.jar",
    "file": "erp/modules/file/jar/erp-modules-file.jar",
    "approval": "erp/modules/approval/jar/erp-modules-approval.jar",
}

HOST_SERVICE_START_ORDER = (
    "system",
    "job",
    "oa",
    "inventory",
    "file",
    "approval",
    "monitor",
    "auth",
    "gateway",
)

HOST_REQUIRED_BASELINE_SERVICES = (
    "gateway",
    "auth",
    "system",
    "oa",
    "inventory",
    "file",
    "approval",
)

HOST_SHARED_UPLOAD_ROOT = "/data/erp-new-data/uploadPath"
HOST_FILE_PATH = HOST_SHARED_UPLOAD_ROOT
HOST_SIGN_PACKAGE_STORAGE_ROOT = f"{HOST_SHARED_UPLOAD_ROOT}/private/sign-package"
HOST_ATTENDANCE_STORAGE_ROOT = f"{HOST_SHARED_UPLOAD_ROOT}/private/attendance"
HOST_REIMBURSEMENT_STORAGE_ROOT = f"{HOST_SHARED_UPLOAD_ROOT}/private/reimbursement"
HOST_DRIVE_LOCAL_PATH = f"{HOST_SHARED_UPLOAD_ROOT}/private/drive"


def render_host_runtime_contract():
    """Render fail-closed service/readiness and persistent-upload helpers."""
    return r'''
collect_previous_active_services() {
  PREVIOUS_ACTIVE_SERVICES=""
  local missing=""
  local required
  for svc in $SERVICES; do
    if systemctl is-active --quiet "erp-new@$svc.service"; then
      PREVIOUS_ACTIVE_SERVICES="$PREVIOUS_ACTIVE_SERVICES $svc"
    fi
  done
  PREVIOUS_ACTIVE_SERVICES="${PREVIOUS_ACTIVE_SERVICES# }"
  if [ -z "$PREVIOUS_ACTIVE_SERVICES" ]; then
    echo "no active previous ERP services found; refusing an unverified deployment" >&2
    return 1
  fi
  for required in $REQUIRED_PREVIOUS_SERVICES; do
    if [[ " $PREVIOUS_ACTIVE_SERVICES " != *" $required "* ]]; then
      missing="$missing $required"
    fi
  done
  missing="${missing# }"
  if [ -n "$missing" ]; then
    echo "required previous ERP services are not active: $missing" >&2
    return 1
  fi
  echo "previous active services: $PREVIOUS_ACTIVE_SERVICES"
}

change_service_state() {
  local action="$1"
  shift
  local failed=0
  local svc
  for svc in "$@"; do
    if ! systemctl "$action" "erp-new@$svc.service"; then
      echo "failed to $action erp-new@$svc.service" >&2
      failed=1
    fi
  done
  return "$failed"
}

start_services() {
  local requested=" $* "
  local failed=0
  local svc
  for svc in $START_SERVICES; do
    if [[ "$requested" == *" $svc "* ]]; then
      if ! systemctl restart "erp-new@$svc.service"; then
        echo "failed to restart erp-new@$svc.service" >&2
        failed=1
      fi
    fi
  done
  return "$failed"
}

service_port() {
  local wanted="$1"
  local entry
  for entry in $SERVICE_PORTS; do
    if [ "${entry%%:*}" = "$wanted" ]; then
      printf '%s\n' "${entry#*:}"
      return 0
    fi
  done
  echo "missing port contract for service: $wanted" >&2
  return 1
}

service_ready() {
  local svc="$1"
  local port
  local body
  port="$(service_port "$svc")"
  systemctl is-active --quiet "erp-new@$svc.service" || return 1
  body="$(curl -fsS --max-time 4 "http://127.0.0.1:$port/actuator/health")" || return 1
  printf '%s' "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

wait_for_host_readiness() {
  local required_services="${1:-$SERVICES}"
  local deadline=$((SECONDS + ${ERP_SERVICE_READY_TIMEOUT:-180}))
  local pending
  local svc
  while (( SECONDS < deadline )); do
    pending=""
    for svc in $required_services; do
      if ! service_ready "$svc"; then
        pending="$pending $svc"
      fi
    done
    pending="${pending# }"
    if [ -z "$pending" ]; then
      echo "service readiness passed: $required_services"
      return 0
    fi
    sleep 3
  done
  echo "service readiness timed out; not ready:${pending:- unknown}" >&2
  return 1
}

reload_nginx() {
  local nginx_master_pid
  nginx -t || return 1
  if systemctl is-active --quiet nginx.service; then
    if systemctl reload nginx.service; then
      return 0
    fi
  fi
  if [ -x /etc/init.d/nginx ] && /etc/init.d/nginx reload; then
    return 0
  fi
  nginx_master_pid="$(pgrep -o -x nginx || true)"
  [ -n "$nginx_master_pid" ] || {
    echo "nginx master process is not running" >&2
    return 1
  }
  kill -HUP "$nginx_master_pid"
}

unit_property_has_word() {
  local unit="$1"
  local property="$2"
  local expected="$3"
  local value
  local word
  value="$(systemctl show "$unit" --property="$property" --value)" || return 1
  for word in $value; do
    if [ "$word" = "$expected" ]; then
      return 0
    fi
  done
  return 1
}

unit_exec_start_pre_has() {
  local unit="$1"
  local mode="$2"
  local path="$3"
  local expected="argv[]=/usr/bin/test $mode $path"
  local value
  value="$(systemctl show "$unit" --property=ExecStartPre --value)" || return 1
  printf '%s\n' "$value" | awk -v expected="$expected" '
    {
      count = split($0, fields, ";")
      for (field_index = 1; field_index <= count; field_index++) {
        field = fields[field_index]
        gsub(/^[[:space:]]+/, "", field)
        gsub(/[[:space:]]+$/, "", field)
        gsub(/[[:space:]]+/, " ", field)
        if (field == expected) found = 1
      }
    }
    END { exit(found ? 0 : 1) }
  '
}

atomic_release_link() {
  local target="$1" link="$2"
  python3 - "$target" "$link" <<'PYLINK'
import os, sys, uuid
target, link = sys.argv[1:]
candidate = link + ".next-" + uuid.uuid4().hex
try:
    os.symlink(target, candidate)
    os.replace(candidate, link)
finally:
    if os.path.lexists(candidate): os.unlink(candidate)
PYLINK
}

verify_storage_unit_contract() {
  local svc="$1"
  local root="$2"
  local unit="erp-new@$svc.service"
  unit_property_has_word "$unit" RequiresMountsFor "$root" || {
    echo "$svc systemd unit does not effectively require the common-upload mount" >&2
    return 1
  }
  unit_exec_start_pre_has "$unit" -d "$root" || {
    echo "$svc systemd unit does not effectively guard the common-upload directory" >&2
    return 1
  }
  unit_exec_start_pre_has "$unit" -w "$root" || {
    echo "$svc systemd unit does not effectively guard common-upload writability" >&2
    return 1
  }
}

verify_host_service_contract() {
  local release_root="$1"
  local entry
  local svc
  local artifact
  local command
  local exec_start
  local load_state
  local common_mount
  local storage_svc
  test -r "$release_root/.env" || {
    echo "missing host environment file: $release_root/.env" >&2
    return 1
  }
  if [ "$(grep -Ec '^MYSQL_DATABASE=[A-Za-z0-9_]+$' "$release_root/.env")" -ne 1 ]; then
    echo "host .env must contain exactly one safe MYSQL_DATABASE value" >&2
    return 1
  fi
  for command in curl diff findmnt pgrep python3 ss sync tar; do
    command -v "$command" >/dev/null 2>&1 || {
      echo "$command is required for deployment safety checks" >&2
      return 1
    }
  done
  while IFS='=' read -r name expected; do
    if [ "$(grep -Ec "^${name}=" "$release_root/.env")" -ne 1 ] ||
       [ "$(grep -Fxc "${name}=${expected}" "$release_root/.env")" -ne 1 ]; then
      echo "host .env must pin $name to $expected" >&2
      return 1
    fi
  done <<EOF
ERP_UPLOAD_ROOT=$SHARED_UPLOAD_ROOT
FILE_PATH=$FILE_PATH
SIGN_PACKAGE_STORAGE_ROOT=$SIGN_PACKAGE_STORAGE_ROOT
OA_ATTENDANCE_STORAGE_ROOT=$OA_ATTENDANCE_STORAGE_ROOT
OA_REIMBURSEMENT_STORAGE_ROOT=$OA_REIMBURSEMENT_STORAGE_ROOT
DRIVE_LOCAL_PATH=$DRIVE_LOCAL_PATH
OA_SIGN_EXCEL_IMPORT_ENABLED=true
EOF
  for entry in \
    "$SHARED_UPLOAD_ROOT" \
    "$SIGN_PACKAGE_STORAGE_ROOT" \
    "$OA_ATTENDANCE_STORAGE_ROOT" \
    "$OA_REIMBURSEMENT_STORAGE_ROOT" \
    "$DRIVE_LOCAL_PATH"; do
    test -d "$entry" && test -w "$entry" || {
      echo "persistent common-upload path is missing or not writable: $entry" >&2
      return 1
    }
  done
  common_mount="$(findmnt -n -o TARGET -T "$SHARED_UPLOAD_ROOT")"
  if [ "$common_mount" != /data ]; then
    echo "persistent common-upload root must be mounted on /data, got: $common_mount" >&2
    return 1
  fi
  for entry in "$release_root" "$SIGN_PACKAGE_STORAGE_ROOT" "$OA_ATTENDANCE_STORAGE_ROOT" \
      "$OA_REIMBURSEMENT_STORAGE_ROOT" "$DRIVE_LOCAL_PATH" \
      /data/erp-new-data/uploadPath/private/sign-package-temp \
      /data/erp-new-data/logs /data/erp-new-data/tmp /data/erp-new-data/cache; do
    test -d "$entry" && test -w "$entry" &&
      [ "$(findmnt -n -o TARGET -T "$entry")" = /data ] || return 1
  done
  test -x /data/erp-new-runtime/image-codecs/bin/python || return 1
  [ "$(findmnt -n -o TARGET -T /data/erp-new-runtime/image-codecs/bin/python)" = /data ] || return 1
  /data/erp-new-runtime/image-codecs/bin/python -I -c 'from PIL import features; import pillow_heif; assert features.check("webp"); assert pillow_heif.libheif_info()["HEIF"]' || return 1
  for storage_svc in $SERVICES; do
    verify_storage_unit_contract "$storage_svc" "$SHARED_UPLOAD_ROOT" || return 1
  done
  test -x "$release_root/run-erp-service.sh" || {
    echo "missing executable host service runner: $release_root/run-erp-service.sh" >&2
    return 1
  }
  test -x "$release_root/run-erp-service-legacy.sh" || {
    echo "missing executable legacy host service runner: $release_root/run-erp-service-legacy.sh" >&2
    return 1
  }
  for svc in $SERVICES; do
    load_state="$(systemctl show "erp-new@$svc.service" --property=LoadState --value)"
    if [ "$load_state" != loaded ]; then
      echo "missing systemd service contract: erp-new@$svc.service" >&2
      return 1
    fi
    exec_start="$(systemctl show "erp-new@$svc.service" --property=ExecStart --value)"
    if [[ "$exec_start" != *"run-erp-service.sh $svc"* ]]; then
      echo "systemd service does not pass '$svc' to run-erp-service.sh" >&2
      return 1
    fi
  done
  for entry in $SERVICE_ARTIFACTS; do
    svc="${entry%%:*}"
    artifact="${entry#*:}"
    test -s "$release_root/$artifact" || {
      echo "missing target artifact for $svc: $release_root/$artifact" >&2
      return 1
    }
    if [ "$svc" = monitor ] || [ "$svc" = approval ]; then
      grep -F "$artifact" "$release_root/run-erp-service.sh" >/dev/null || {
        echo "host service runner does not map $svc to $artifact" >&2
        return 1
      }
    fi
  done
}

ensure_shared_upload_source() {
  local legacy="$PREV_TARGET/erp/uploadPath"
  local data_root
  local marker
  local release_name
  local backup
  local link_candidate
  local resolved
  data_root="$(dirname "$SHARED_UPLOAD_ROOT")"
  marker="$data_root/.uploadPath-initialized"
  mkdir -p "$data_root"

  if [ -L "$legacy" ]; then
    resolved="$(readlink -f "$legacy")"
    if [ "$resolved" != "$SHARED_UPLOAD_ROOT" ]; then
      echo "legacy uploadPath points to unexpected location: $resolved" >&2
      return 1
    fi
    test -d "$SHARED_UPLOAD_ROOT"
    return 0
  fi

  if [ -d "$legacy" ]; then
    if [ -e "$marker" ]; then
      echo "shared upload marker exists but previous release still has a local directory" >&2
      return 1
    fi
    mkdir -p "$SHARED_UPLOAD_ROOT"
    if [ -n "$(find "$SHARED_UPLOAD_ROOT" -mindepth 1 -print -quit)" ]; then
      echo "shared upload root is non-empty without a migration marker; reconcile it manually" >&2
      return 1
    fi
    change_service_state stop $PREVIOUS_ACTIVE_SERVICES
    rmdir "$SHARED_UPLOAD_ROOT"
    cp -a "$legacy" "$SHARED_UPLOAD_ROOT"
    diff -qr "$legacy" "$SHARED_UPLOAD_ROOT" >/dev/null
    sync
    release_name="$(basename "$(dirname "$PREV_TARGET")")"
    backup="$data_root/uploadPath-migration-backup-$release_name"
    test ! -e "$backup" || {
      echo "uploadPath migration backup already exists: $backup" >&2
      return 1
    }
    link_candidate="$legacy.shared-link-$$"
    test ! -e "$link_candidate" || {
      echo "uploadPath migration link candidate already exists: $link_candidate" >&2
      return 1
    }
    ln -s "$SHARED_UPLOAD_ROOT" "$link_candidate"
    UPLOAD_MIGRATION_BACKUP="$backup"
    UPLOAD_MIGRATION_LINK_CANDIDATE="$link_candidate"
    mv "$legacy" "$backup"
    mv "$link_candidate" "$legacy"
    UPLOAD_MIGRATION_LINK_CANDIDATE=""
    printf 'source=%s\nbackup=%s\n' "$PREV_TARGET" "$backup" > "$marker.tmp"
    mv "$marker.tmp" "$marker"
    echo "UPLOAD_PATH_MIGRATED shared=$SHARED_UPLOAD_ROOT backup=$backup"
    return 0
  fi

  if [ -e "$legacy" ]; then
    echo "legacy uploadPath is neither a directory nor a symlink: $legacy" >&2
    return 1
  fi
  mkdir -p "$SHARED_UPLOAD_ROOT"
  printf 'source=%s\nbackup=none\n' "$PREV_TARGET" > "$marker.tmp"
  mv "$marker.tmp" "$marker"
}

link_shared_upload_target() {
  local release_root="$1"
  local relative
  local target
  local resolved
  for relative in uploadPath erp/uploadPath; do
    target="$release_root/$relative"
    mkdir -p "$(dirname "$target")"
    if [ -L "$target" ]; then
      resolved="$(readlink -f "$target")"
      if [ "$resolved" != "$SHARED_UPLOAD_ROOT" ]; then
        echo "target $relative points to unexpected location: $resolved" >&2
        return 1
      fi
      continue
    fi
    if [ -d "$target" ]; then
      if [ -n "$(find "$target" -mindepth 1 -print -quit)" ]; then
        echo "release contains data outside the common upload root: $target" >&2
        return 1
      fi
      rmdir "$target"
    elif [ -e "$target" ]; then
      echo "target $relative is not a directory or symlink: $target" >&2
      return 1
    fi
    ln -s "$SHARED_UPLOAD_ROOT" "$target"
  done
}

verify_release_upload_links() {
  local release_root="$1"
  local relative
  local target
  for relative in uploadPath erp/uploadPath; do
    target="$release_root/$relative"
    if [ ! -L "$target" ] || [ "$(readlink -f "$target")" != "$SHARED_UPLOAD_ROOT" ]; then
      echo "release $relative does not resolve to $SHARED_UPLOAD_ROOT" >&2
      return 1
    fi
  done
}

process_has_environment() {
  local svc="$1"
  local expected="$2"
  local pid
  pid="$(systemctl show "erp-new@$svc.service" --property=MainPID --value)"
  if [ -z "$pid" ] || [ "$pid" = 0 ] || [ ! -r "/proc/$pid/environ" ]; then
    echo "cannot inspect runtime environment for $svc" >&2
    return 1
  fi
  tr '\0' '\n' < "/proc/$pid/environ" | grep -Fqx "$expected" || {
    echo "$svc runtime environment differs from the storage contract: ${expected%%=*}" >&2
    return 1
  }
}

verify_host_storage_runtime() {
  local release_root="$1"
  local svc
  local pid
  local cwd
  local assignment
  verify_release_upload_links "$release_root"
  for svc in ${PREVIOUS_ACTIVE_SERVICES:-oa file}; do
    process_has_environment "$svc" "ERP_LOG_ROOT=/data/erp-new-data/logs" || return 1
    process_has_environment "$svc" "ERP_TEMP_ROOT=/data/erp-new-data/tmp" || return 1
    process_has_environment "$svc" "ERP_CACHE_ROOT=/data/erp-new-data/cache" || return 1
    process_has_environment "$svc" "TMPDIR=/data/erp-new-data/tmp/$svc" || return 1
    local storage_pid
    storage_pid="$(systemctl show "erp-new@$svc.service" --property=MainPID --value)"
    tr '\0' '\n' < "/proc/$storage_pid/environ" |
      grep -E '^JDK_JAVA_OPTIONS=.*-Djava.io.tmpdir=/data/erp-new-data/tmp/' >/dev/null || return 1
  done
  for svc in oa file; do
    systemctl is-active --quiet "erp-new@$svc.service" || {
      echo "storage service is not active: $svc" >&2
      return 1
    }
    pid="$(systemctl show "erp-new@$svc.service" --property=MainPID --value)"
    cwd="$(readlink -f "/proc/$pid/cwd")"
    if [ "$cwd" != "$(readlink -f "$release_root")" ]; then
      echo "$svc runtime working directory differs from the active release: $cwd" >&2
      return 1
    fi
    for assignment in \
      "ERP_UPLOAD_ROOT=$SHARED_UPLOAD_ROOT" \
      "FILE_PATH=$FILE_PATH" \
      "SIGN_PACKAGE_STORAGE_ROOT=$SIGN_PACKAGE_STORAGE_ROOT" \
      "OA_ATTENDANCE_STORAGE_ROOT=$OA_ATTENDANCE_STORAGE_ROOT" \
      "OA_REIMBURSEMENT_STORAGE_ROOT=$OA_REIMBURSEMENT_STORAGE_ROOT" \
      "DRIVE_LOCAL_PATH=$DRIVE_LOCAL_PATH" \
      "OA_SIGN_EXCEL_IMPORT_ENABLED=true"; do
      process_has_environment "$svc" "$assignment" || return 1
    done
  done
}

business_json_ready() {
  local url="$1"
  local host_header="$2"
  local body
  body="$(curl -fsS --max-time 10 -H "Host: $host_header" "$url")" || return 1
  printf '%s' "$body" | python3 -c '
import json
import sys
try:
    payload = json.load(sys.stdin)
except Exception:
    raise SystemExit(1)
code = payload.get("code") if isinstance(payload, dict) else None
raise SystemExit(0 if code == 200 else 1)
'
}

repair_previous_upload_link() {
  local legacy="$PREV_TARGET/erp/uploadPath"
  local backup="${UPLOAD_MIGRATION_BACKUP:-}"
  local link_candidate="${UPLOAD_MIGRATION_LINK_CANDIDATE:-}"
  local resolved

  if [ -L "$legacy" ]; then
    resolved="$(readlink -f "$legacy")"
    if [ "$resolved" != "$SHARED_UPLOAD_ROOT" ]; then
      echo "rollback found an unexpected previous uploadPath target: $resolved" >&2
      return 1
    fi
  elif [ -d "$legacy" ]; then
    # The physical directory was never moved, so the original release is intact.
    :
  elif [ -z "$backup" ] && [ ! -e "$legacy" ]; then
    # The previous release did not have an upload directory before migration.
    :
  elif [ -n "$backup" ] && [ -d "$backup" ] && [ -d "$SHARED_UPLOAD_ROOT" ]; then
    # The directory copy is durable; restore visibility through the shared path
    # instead of moving the backup back and potentially hiding later writes.
    ln -s "$SHARED_UPLOAD_ROOT" "$legacy" || return 1
  else
    echo "rollback cannot restore previous uploadPath visibility" >&2
    return 1
  fi

  if [ -n "$link_candidate" ] && [ -L "$link_candidate" ]; then
    rm -f "$link_candidate"
  fi
}

rollback() {
  local code="${1:-$?}"
  local stop_status=0
  local link_status=0
  local daemon_status=0
  local restart_status=0
  local readiness_status=0
  local upload_status=0
  local nginx_status=0
  local healthcheck_host="${ERP_HEALTHCHECK_HOST:-8.152.199.39}"
  trap - ERR EXIT
  set +e
  echo "deployment failed, rolling back to $PREV_TARGET" >&2
  change_service_state stop $SERVICES
  stop_status=$?
  repair_previous_upload_link
  upload_status=$?
  if [ "$upload_status" -eq 0 ]; then
    link_shared_upload_target "$PREV_TARGET"
    upload_status=$?
  fi
  atomic_release_link "$PREV_TARGET" "$CURRENT_LINK"
  link_status=$?
  systemctl daemon-reload
  daemon_status=$?
  if [ -n "$PREVIOUS_ACTIVE_SERVICES" ]; then
    start_services $PREVIOUS_ACTIVE_SERVICES
    restart_status=$?
    wait_for_host_readiness "$PREVIOUS_ACTIVE_SERVICES"
    readiness_status=$?
    if [ "$readiness_status" -eq 0 ]; then
      verify_host_storage_runtime "$PREV_TARGET"
      readiness_status=$?
    fi
  fi
  reload_nginx
  nginx_status=$?
  if (( nginx_status == 0 )); then
    curl -fsS --max-time 10 -H "Host: $healthcheck_host" http://127.0.0.1/ >/dev/null && \
      business_json_ready http://127.0.0.1/prod-api/code "$healthcheck_host"
    nginx_status=$?
  fi
  if (( stop_status || upload_status || link_status || daemon_status || restart_status || readiness_status || nginx_status )); then
    echo "ROLLBACK_INCOMPLETE stop=$stop_status upload=$upload_status link=$link_status daemon=$daemon_status restart=$restart_status readiness=$readiness_status nginx=$nginx_status" >&2
  else
    echo "ROLLBACK_OK current=$(readlink -f "$CURRENT_LINK")" >&2
  fi
  exit "$code"
}

rollback_on_exit() {
  local code="$1"
  if [ "$code" -ne 0 ]; then
    rollback "$code"
  fi
}
'''


def render_remote_release_verifier():
    """Render a Python 3.6-compatible verifier for the extracted release tree."""
    return r'''
import hashlib
import json
import os
import re
import sys
import zipfile

root = os.path.realpath(sys.argv[1])
expected = {
    "releaseId": sys.argv[2],
    "gitCommit": sys.argv[3],
    "buildTime": sys.argv[4],
}

def load_json(relative):
    with open(os.path.join(root, relative), encoding="utf-8") as handle:
        return json.load(handle)

checksum_relative = "provenance/SHA256SUMS"
checksum_path = os.path.join(root, checksum_relative)
listed = {}
with open(checksum_path, encoding="utf-8") as handle:
    for number, raw in enumerate(handle, 1):
        line = raw.rstrip("\n")
        if not line:
            continue
        match = re.match(r"^([0-9a-f]{64})  (.+)$", line)
        if not match:
            raise SystemExit("invalid SHA256SUMS line %s" % number)
        relative = match.group(2)
        normalized = os.path.normpath(relative)
        if (
            relative != normalized
            or os.path.isabs(relative)
            or relative == checksum_relative
            or relative.startswith("../")
            or relative in listed
        ):
            raise SystemExit("unsafe or duplicate SHA256SUMS entry: %s" % relative)
        listed[relative] = match.group(1)

actual = set()
for directory, dirnames, filenames in os.walk(root, followlinks=False):
    for name in dirnames + filenames:
        path = os.path.join(directory, name)
        if os.path.islink(path):
            raise SystemExit("release package contains a symlink")
    for name in filenames:
        path = os.path.join(directory, name)
        relative = os.path.relpath(path, root).replace(os.sep, "/")
        if relative != checksum_relative:
            actual.add(relative)
if actual != set(listed):
    raise SystemExit("SHA256SUMS does not cover the extracted release tree")
for relative, wanted in listed.items():
    digest = hashlib.sha256()
    with open(os.path.join(root, relative), "rb") as handle:
        while True:
            chunk = handle.read(8 * 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    if digest.hexdigest() != wanted:
        raise SystemExit("checksum mismatch: %s" % relative)

release_manifest = load_json("provenance/release-manifest.json")
if any(release_manifest.get(name) != wanted for name, wanted in expected.items()):
    raise SystemExit("remote release manifest identity differs from local verification")
contract = load_json("release-tools/release-contract.json")
frontend = contract["frontend"]
frontend_info = load_json(frontend["destination"] + "/" + frontend["releaseInfo"])
if (
    frontend_info.get("releaseId") != expected["releaseId"]
    or frontend_info.get("gitCommit", frontend_info.get("commit"))
    != expected["gitCommit"]
    or frontend_info.get("buildTime") != expected["buildTime"]
):
    raise SystemExit("remote frontend identity differs from local verification")

def parse_manifest(raw):
    values = {}
    current = None
    for line in raw.decode("utf-8").replace("\r\n", "\n").split("\n"):
        if line.startswith(" ") and current:
            values[current] += line[1:]
        elif ": " in line:
            current, value = line.split(": ", 1)
            values[current] = value
    return values

artifacts = contract.get("artifactMap")
if not isinstance(artifacts, list) or not artifacts:
    raise SystemExit("release contract has no artifacts")
for artifact in artifacts:
    relative = artifact["destination"]
    jar_path = os.path.join(root, relative)
    jar_dir = os.path.dirname(jar_path)
    siblings = sorted(name for name in os.listdir(jar_dir) if name.endswith(".jar"))
    if siblings != [os.path.basename(jar_path)]:
        raise SystemExit("non-canonical JAR directory: %s" % relative)
    with zipfile.ZipFile(jar_path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise SystemExit("duplicate JAR entries: %s" % relative)
        embedded = json.loads(archive.read("META-INF/erp-release.json").decode("utf-8"))
        manifest = parse_manifest(archive.read("META-INF/MANIFEST.MF"))
    if embedded != expected:
        raise SystemExit("embedded JAR identity mismatch: %s" % relative)
    for name, wanted in (
        ("X-ERP-Release-Id", expected["releaseId"]),
        ("X-ERP-Git-Commit", expected["gitCommit"]),
        ("X-ERP-Build-Time", expected["buildTime"]),
    ):
        if manifest.get(name) != wanted:
            raise SystemExit("JAR manifest identity mismatch: %s" % relative)

print("release identity and checksums verified: %s" % expected["releaseId"])
'''


def render_remote_release_env_stamper():
    """Render a Python 3.6-compatible atomic updater for public release metadata."""
    return r'''
import os
import sys

path = sys.argv[1]
expected = {
    "RELEASE_ID": sys.argv[2],
    "GIT_COMMIT": sys.argv[3],
    "BUILD_TIME": sys.argv[4],
}
with open(path, encoding="utf-8") as handle:
    lines = handle.readlines()
seen = set()
updated = []
for line in lines:
    key = line.split("=", 1)[0] if "=" in line else ""
    if key in expected:
        if key in seen:
            raise SystemExit("duplicate release identity key in host .env: %s" % key)
        seen.add(key)
        updated.append("%s=%s\n" % (key, expected[key]))
    else:
        updated.append(line)
for key in ("RELEASE_ID", "GIT_COMMIT", "BUILD_TIME"):
    if key not in seen:
        if updated and not updated[-1].endswith("\n"):
            updated[-1] += "\n"
        updated.append("%s=%s\n" % (key, expected[key]))
temporary = path + ".release-identity.tmp"
descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
try:
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.writelines(updated)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)
except BaseException:
    try:
        os.unlink(temporary)
    except OSError:
        pass
    raise
'''


def read_credentials():
    access_key_id = os.environ.get("ALIYUN_ACCESS_KEY_ID")
    access_key_secret = os.environ.get("ALIYUN_ACCESS_KEY_SECRET")
    if not access_key_id:
        access_key_id = input("AccessKey ID: ").strip()
    if not access_key_secret:
        access_key_secret = getpass.getpass("AccessKey Secret: ").strip()
    if not access_key_id or not access_key_secret:
        raise SystemExit("missing AccessKey credentials")
    return access_key_id, access_key_secret


def percent_encode(value):
    return quote(str(value), safe="~")


def ecs_client(ak, secret):
    return {"access_key_id": ak, "access_key_secret": secret}


def ecs_request(client, action, region_id, params=None):
    url = f"https://ecs.{region_id}.aliyuncs.com/"
    last_exc = None
    for attempt in range(1, 6):
        try:
            all_params = {
                "Format": "JSON",
                "Version": "2014-05-26",
                "AccessKeyId": client["access_key_id"],
                "SignatureMethod": "HMAC-SHA1",
                "Timestamp": dt.datetime.now(dt.UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "SignatureVersion": "1.0",
                "SignatureNonce": uuid.uuid4().hex,
                "Action": action,
            }
            all_params.update(params or {})
            canonical = "&".join(
                f"{percent_encode(k)}={percent_encode(all_params[k])}" for k in sorted(all_params)
            )
            string_to_sign = "POST&%2F&" + percent_encode(canonical)
            digest = hmac.new(
                (client["access_key_secret"] + "&").encode("utf-8"),
                string_to_sign.encode("utf-8"),
                hashlib.sha1,
            ).digest()
            all_params["Signature"] = base64.b64encode(digest).decode("ascii")
            response = requests.post(url, data=all_params, timeout=(10, 60))
            data = response.json()
            if response.status_code >= 400 or "Code" in data and data.get("Code") != "200":
                code = data.get("Code", response.status_code)
                message = data.get("Message", response.text[:500])
                raise RuntimeError(f"{action} failed in {region_id}: {code}: {message}")
            return data
        except Exception as exc:
            last_exc = exc
            time.sleep(min(2 * attempt, 10))
    raise last_exc


def describe_regions(ak, secret):
    client = ecs_client(ak, secret)
    data = ecs_request(client, "DescribeRegions", "cn-hangzhou", {"AcceptLanguage": "zh-CN"})
    regions = data.get("Regions", {}).get("Region", [])
    return [r["RegionId"] for r in regions if r.get("RegionId")]


def describe_instances(ak, secret, regions):
    instances = []
    for region_id in regions:
        client = ecs_client(ak, secret)
        page = 1
        while True:
            try:
                data = ecs_request(
                    client,
                    "DescribeInstances",
                    region_id,
                    {"RegionId": region_id, "PageNumber": page, "PageSize": 100},
                )
            except Exception as exc:
                print(f"warning: skip region {region_id}: {type(exc).__name__}", file=sys.stderr)
                break
            items = data.get("Instances", {}).get("Instance", [])
            for item in items:
                public_ips = item.get("PublicIpAddress", {}).get("IpAddress", []) or []
                eip = item.get("EipAddress", {}) or {}
                eip_ip = eip.get("IpAddress")
                if eip_ip and eip_ip not in public_ips:
                    public_ips.append(eip_ip)
                private_ips = item.get("VpcAttributes", {}).get("PrivateIpAddress", {}).get("IpAddress", []) or []
                instances.append(
                    {
                        "regionId": region_id,
                        "zoneId": item.get("ZoneId"),
                        "instanceId": item.get("InstanceId"),
                        "securityGroupIds": item.get("SecurityGroupIds", {}).get("SecurityGroupId", []) or [],
                        "instanceName": item.get("InstanceName"),
                        "hostName": item.get("HostName"),
                        "status": item.get("Status"),
                        "osName": item.get("OSName"),
                        "publicIp": public_ips,
                        "privateIp": private_ips,
                        "creationTime": item.get("CreationTime"),
                        "expiredTime": item.get("ExpiredTime"),
                    }
                )
            total = data.get("TotalCount", 0)
            if page * 100 >= total:
                break
            page += 1
    return instances


def print_instances(args):
    ak, secret = read_credentials()
    if args.regions:
        regions = [r.strip() for r in args.regions.split(",") if r.strip()]
    else:
        regions = describe_regions(ak, secret)
    instances = describe_instances(ak, secret, regions)
    print(json.dumps(instances, ensure_ascii=False, indent=2))


def wait_invocation(client, region_id, invoke_id, instance_id, timeout=900):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        data = ecs_request(
            client,
            "DescribeInvocationResults",
            region_id,
            {"RegionId": region_id, "InvokeId": invoke_id, "InstanceId": instance_id},
        )
        results = data.get("Invocation", {}).get("InvocationResults", {}).get("InvocationResult", [])
        if results:
            result = results[0]
            status = result.get("InvocationStatus")
            if status in {"Success", "Failed", "Stopped", "PartialFailed", "Timeout"}:
                return result
            last = result
        time.sleep(5)
    raise TimeoutError(f"command timed out: {invoke_id}, last={last}")


def run_command(client, region_id, instance_id, command, name, timeout=900):
    command = compact_shell_command(command, name)
    data = ecs_request(
        client,
        "RunCommand",
        region_id,
        {
            "RegionId": region_id,
            "Type": "RunShellScript",
            "Name": name[:128],
            "CommandContent": command,
            "InstanceId.1": instance_id,
            "Timeout": timeout,
        },
    )
    invoke_id = data["InvokeId"]
    return wait_invocation(client, region_id, invoke_id, instance_id, timeout=timeout + 60)


def compact_shell_command(command, name, threshold=16000):
    """Compress oversized Cloud Assistant scripts without changing their behavior."""

    raw = command.encode("utf-8")
    if len(raw) <= threshold:
        return command
    payload = base64.b64encode(gzip.compress(raw, compresslevel=9, mtime=0)).decode("ascii")
    safe_name = "".join(character if character.isalnum() else "-" for character in name)[:48]
    remote_script = f"/tmp/{safe_name or 'erp-command'}-{uuid.uuid4().hex[:12]}.sh"
    wrapper = f'''#!/usr/bin/env bash
set -euo pipefail
SCRIPT={json.dumps(remote_script)}
trap 'rm -f "$SCRIPT"' EXIT
python3 - <<'PY'
import base64
import gzip
from pathlib import Path

Path({json.dumps(remote_script)}).write_bytes(
    gzip.decompress(base64.b64decode({json.dumps(payload)}))
)
PY
chmod 700 "$SCRIPT"
echo "COMPACT_COMMAND_READY original_bytes={len(raw)} compressed_bytes={len(payload)}"
bash "$SCRIPT"
'''
    if len(wrapper.encode("utf-8")) > threshold:
        raise ValueError("compressed Cloud Assistant command still exceeds the safe limit")
    return wrapper


def decode_output(result):
    raw = result.get("Output") or ""
    try:
        return base64.b64decode(raw).decode("utf-8", "replace")
    except Exception:
        return raw


def make_bucket_name(account_hint="erp-deploy"):
    return f"{account_hint}-{int(time.time())}-{uuid.uuid4().hex[:8]}".lower()


def upload_package_resumable(bucket, object_key, package):
    package = Path(package)
    total = package.stat().st_size
    part_size = 1 * 1024 * 1024
    upload_id = None
    parts = []
    consumed = 0
    last_step = -1
    try:
        upload_id = bucket.init_multipart_upload(object_key).upload_id
        with package.open("rb") as fh:
            part_number = 1
            while True:
                data = fh.read(part_size)
                if not data:
                    break
                for attempt in range(1, 6):
                    try:
                        result = bucket.upload_part(object_key, upload_id, part_number, data)
                        parts.append(oss2.models.PartInfo(part_number, result.etag))
                        break
                    except Exception:
                        if attempt == 5:
                            raise
                        time.sleep(2 * attempt)
                consumed += len(data)
                step = consumed // (10 * 1024 * 1024)
                if step != last_step or consumed == total:
                    last_step = step
                    pct = consumed * 100 / total if total else 0
                    print(f"upload progress: {consumed / 1024 / 1024:.0f}/{total / 1024 / 1024:.0f} MiB ({pct:.1f}%)", flush=True)
                part_number += 1
        bucket.complete_multipart_upload(object_key, upload_id, parts)
    except Exception:
        if upload_id:
            try:
                bucket.abort_multipart_upload(object_key, upload_id)
            except Exception:
                pass
        raise


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as fh:
        for chunk in iter(lambda: fh.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_release_archive(package):
    """Fail closed unless *package* is a coherent canonical release archive."""
    result = subprocess.run(
        [
            sys.executable,
            str(RELEASE_VERIFY_TOOL),
            "verify",
            "--archive",
            str(Path(package).resolve()),
        ],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip().splitlines()
        reason = detail[-1] if detail else "release verifier returned no diagnostic"
        raise SystemExit(f"release archive verification failed: {reason}")
    try:
        verified = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise SystemExit("release archive verifier returned invalid JSON") from exc
    required = ("releaseId", "gitCommit", "buildTime")
    if verified.get("status") != "verified" or any(
        not isinstance(verified.get(name), str) or not verified[name]
        for name in required
    ):
        raise SystemExit("release archive verifier returned an incomplete identity")
    migration_release_id = verified.get("migrationReleaseId")
    migration_manifest_sha = verified.get("migrationManifestSha256")
    if migration_release_id is not None and (
        not isinstance(migration_release_id, str) or not migration_release_id
    ):
        raise SystemExit("release archive verifier returned an invalid migration identity")
    if migration_manifest_sha is not None and (
        not isinstance(migration_manifest_sha, str)
        or re.fullmatch(r"[0-9a-f]{64}", migration_manifest_sha) is None
    ):
        raise SystemExit("release archive verifier returned an invalid migration hash")
    if (migration_release_id is None) != (migration_manifest_sha is None):
        raise SystemExit("release archive verifier returned a partial migration identity")
    identity = {name: verified[name] for name in required}
    identity["migrationReleaseId"] = migration_release_id
    identity["migrationManifestSha256"] = migration_manifest_sha
    return identity


def put_object_with_retry(bucket, key, data, attempts=12):
    for attempt in range(1, attempts + 1):
        try:
            bucket.put_object(key, data)
            return
        except Exception as exc:
            if attempt == attempts:
                raise
            print(
                f"OSS chunk retry {attempt}/{attempts} "
                f"error={type(exc).__name__}",
                file=sys.stderr,
                flush=True,
            )
            time.sleep(min(2 * attempt, 20))


def upload_package_as_chunk_objects(bucket, package, url_ttl):
    package = Path(package)
    package_sha = sha256_file(package)
    # Small independent objects tolerate unstable outbound links better than a
    # single multipart stream, and existing keys make a retry resumable.
    part_size = 1 * 1024 * 1024
    total_size = package.stat().st_size
    part_count = (total_size + part_size - 1) // part_size
    prefix = f"erp-deploy/chunks/{package.name}.{package_sha[:16]}"
    uploaded_keys = []
    urls = []
    consumed = 0
    last_step = -1
    print(f"chunk object upload: {part_count} parts, part_size={part_size // 1024 // 1024} MiB", flush=True)
    with package.open("rb") as fh:
        for idx in range(part_count):
            data = fh.read(part_size)
            key = f"{prefix}/part-{idx:05d}"
            expected_size = len(data)
            should_upload = True
            try:
                meta = bucket.head_object(key)
                if int(meta.content_length) == expected_size:
                    should_upload = False
            except Exception:
                pass
            if should_upload:
                put_object_with_retry(bucket, key, data)
            uploaded_keys.append(key)
            urls.append(bucket.sign_url("GET", key, url_ttl))
            consumed += expected_size
            step = consumed // (10 * 1024 * 1024)
            if step != last_step or consumed == total_size:
                last_step = step
                pct = consumed * 100 / total_size if total_size else 0
                print(f"upload progress: {consumed / 1024 / 1024:.0f}/{total_size / 1024 / 1024:.0f} MiB ({pct:.1f}%)", flush=True)

    manifest = {
        "package": package.name,
        "sha256": package_sha,
        "size": total_size,
        "partSize": part_size,
        "parts": part_count,
        "urls": urls,
    }
    manifest_key = f"{prefix}/manifest.json"
    put_object_with_retry(bucket, manifest_key, json.dumps(manifest, ensure_ascii=False).encode("utf-8"))
    uploaded_keys.append(manifest_key)
    return {
        "manifest_key": manifest_key,
        "manifest_url": bucket.sign_url("GET", manifest_key, url_ttl),
        "cleanup_keys": uploaded_keys,
        "sha256": package_sha,
    }


def deploy(args):
    ak, secret = read_credentials()
    package = Path(args.package).resolve()
    if not package.is_file():
        raise SystemExit(f"package not found: {package}")

    client = ecs_client(ak, secret)
    auth = oss2.Auth(ak, secret)
    endpoint = f"{args.oss_scheme}://oss-{args.region}.aliyuncs.com"
    bucket_name = args.bucket or make_bucket_name()
    bucket = oss2.Bucket(auth, endpoint, bucket_name, connect_timeout=OSS_REQUEST_TIMEOUT, proxies={})
    created_bucket = False
    try:
        bucket.create_bucket(oss2.models.BUCKET_ACL_PRIVATE)
        created_bucket = True
    except oss2.exceptions.BucketAlreadyExists:
        raise SystemExit(f"bucket already exists globally: {bucket_name}")
    except oss2.exceptions.BucketAlreadyOwnedByYou:
        pass

    object_key = f"erp-deploy/{package.name}"
    print(f"Uploading package to OSS bucket {bucket_name} ...", flush=True)
    upload_package_resumable(bucket, object_key, package)
    signed_url = bucket.sign_url("GET", object_key, args.url_ttl)

    remote_root = args.remote_root.rstrip("/")
    release_dir = f"{remote_root}/releases/{time.strftime('%Y%m%d%H%M%S')}"
    package_sha = hashlib.sha256(package.read_bytes()).hexdigest()
    command = f"""#!/usr/bin/env bash
set -euo pipefail
umask 077
REMOTE_ROOT={json.dumps(remote_root)}
RELEASE_DIR={json.dumps(release_dir)}
PACKAGE={json.dumps(package.name)}
SIGNED_URL={json.dumps(signed_url)}
EXPECTED_SHA={json.dumps(package_sha)}
mkdir -p "$RELEASE_DIR" "$REMOTE_ROOT/backups"
cd "$RELEASE_DIR"
if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 --connect-timeout 15 "$SIGNED_URL" -o "$PACKAGE"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$PACKAGE" "$SIGNED_URL"
else
  echo "curl/wget not found" >&2
  exit 20
fi
ACTUAL_SHA="$(sha256sum "$PACKAGE" | awk '{{print $1}}')"
if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  echo "sha256 mismatch: $ACTUAL_SHA" >&2
  exit 21
fi
tar -xzf "$PACKAGE"
cd docker
chmod +x deploy-ecs.sh
if [ -d "$REMOTE_ROOT/current/docker/mysql/data" ] && [ ! -e mysql/data ]; then
  mkdir -p mysql
  cp -a "$REMOTE_ROOT/current/docker/mysql/data" mysql/data
fi
if [ -d "$REMOTE_ROOT/current/docker/redis/data" ] && [ ! -e redis/data ]; then
  mkdir -p redis
  cp -a "$REMOTE_ROOT/current/docker/redis/data" redis/data
fi
if [ -d "$REMOTE_ROOT/current/docker/mysql/data" ]; then
  echo "Using copied existing mysql data directory; seed SQL will not reinitialize existing DB."
fi
ln -sfn "$RELEASE_DIR" "$REMOTE_ROOT/current"
./deploy-ecs.sh
docker compose --env-file .env -f docker-compose.ecs-host.yml ps
"""

    print("Running remote deployment command through ECS Cloud Assistant ...", flush=True)
    try:
        result = run_command(client, args.region, args.instance_id, command, "erp-deploy-ecs-host", args.timeout)
        output = decode_output(result)
        print(json.dumps({
            "invocationStatus": result.get("InvocationStatus"),
            "exitCode": result.get("ExitCode"),
            "startTime": result.get("StartTime"),
            "finishedTime": result.get("FinishedTime"),
            "output": output[-12000:],
        }, ensure_ascii=False, indent=2))
        if result.get("ExitCode") not in (0, "0"):
            raise SystemExit(1)
    finally:
        if args.cleanup_oss and bucket is not None:
            try:
                bucket.delete_object(object_key)
                if created_bucket or bucket_name.startswith("erp-deploy-"):
                    bucket.delete_bucket()
            except Exception as exc:
                print(f"warning: OSS cleanup failed: {exc}", file=sys.stderr)


def deploy_host(args):
    package = Path(args.package).resolve()
    if not package.is_file():
        raise SystemExit(f"package not found: {package}")
    verified_release = verify_release_archive(package)

    try:
        migration_plan = prepare_migration_plan(
            apply_migrations=args.apply_migrations,
            manifest_path=args.migration_manifest,
            approval=args.approve_migrations,
            database_name=args.database_name,
            mysql_user=args.mysql_user,
            mysql_password_file=args.mysql_password_file,
        )
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc
    packaged_migration_release = verified_release.get("migrationReleaseId")
    packaged_migration_manifest_sha = verified_release.get(
        "migrationManifestSha256"
    )
    approved_migration_release = (
        migration_plan.release_id if migration_plan.enabled else None
    )
    approved_migration_manifest_sha = (
        migration_plan.manifest_sha256 if migration_plan.enabled else None
    )
    if (
        packaged_migration_release != approved_migration_release
        or packaged_migration_manifest_sha != approved_migration_manifest_sha
    ):
        raise SystemExit(
            "candidate migration identity differs from the explicitly approved "
            f"deployment plan: packaged={packaged_migration_release!r} "
            f"approved={approved_migration_release!r} manifest_sha_match=false"
        )
    migration_script = render_remote_migration_script(migration_plan)

    ak, secret = read_credentials()
    client = ecs_client(ak, secret)
    bucket = None
    bucket_name = ""
    created_bucket = False
    cleanup_keys = []
    remote_package = args.remote_package
    if remote_package:
        remote_path = PurePosixPath(remote_package)
        allowed_parent = PurePosixPath("/data/erp-new/packages")
        if (
            not remote_path.is_absolute()
            or remote_path.parent != allowed_parent
            or remote_path.name != package.name
        ):
            raise SystemExit(
                "--remote-package must be the same-named package directly under "
                "/data/erp-new/packages"
            )
        package_sha = sha256_file(package)
        manifest_url = ""
        acquire_package_script = f'''REMOTE_PACKAGE={json.dumps(str(remote_path))}
test -s "$REMOTE_PACKAGE"
ACTUAL_REMOTE_SHA="$(sha256sum "$REMOTE_PACKAGE" | awk '{{print $1}}')"
if [ "$ACTUAL_REMOTE_SHA" != "$EXPECTED_SHA" ]; then
  echo "remote package sha256 mismatch: $ACTUAL_REMOTE_SHA" >&2
  exit 32
fi
echo "REMOTE_PACKAGE_REUSED path=$REMOTE_PACKAGE sha256=$ACTUAL_REMOTE_SHA"
'''
    else:
        auth = oss2.Auth(ak, secret)
        endpoint = f"{args.oss_scheme}://oss-{args.region}.aliyuncs.com"
        bucket_name = args.bucket or make_bucket_name()
        bucket = oss2.Bucket(
            auth,
            endpoint,
            bucket_name,
            connect_timeout=OSS_REQUEST_TIMEOUT,
            proxies={},
        )
        try:
            bucket.create_bucket(oss2.models.BUCKET_ACL_PRIVATE)
            created_bucket = True
        except oss2.exceptions.BucketAlreadyExists:
            raise SystemExit(f"bucket already exists globally: {bucket_name}")
        except oss2.exceptions.BucketAlreadyOwnedByYou:
            pass

        print(f"Uploading package to OSS bucket {bucket_name} ...", flush=True)
        upload_info = upload_package_as_chunk_objects(bucket, package, args.url_ttl)
        cleanup_keys = upload_info["cleanup_keys"]
        manifest_url = upload_info["manifest_url"]
        package_sha = upload_info["sha256"]
        acquire_package_script = '''export MANIFEST_URL PACKAGE
python3 - <<'PY'
import hashlib
import json
import os
import time
import urllib.request

manifest_url = os.environ["MANIFEST_URL"]
package = os.environ["PACKAGE"]
with urllib.request.urlopen(manifest_url, timeout=120) as response:
    manifest = json.load(response)
tmp = package + ".part"
digest = hashlib.sha256()
downloaded = 0
total = int(manifest["size"])
with open(tmp, "wb") as out:
    for index, url in enumerate(manifest["urls"], 1):
        for attempt in range(1, 6):
            try:
                with urllib.request.urlopen(url, timeout=180) as response:
                    data = response.read()
                break
            except Exception:
                if attempt == 5:
                    raise
                time.sleep(2 * attempt)
        out.write(data)
        digest.update(data)
        downloaded += len(data)
        if index == 1 or index == len(manifest["urls"]) or downloaded // (50 * 1024 * 1024) != (downloaded - len(data)) // (50 * 1024 * 1024):
            print(f"download progress: {downloaded / 1024 / 1024:.0f}/{total / 1024 / 1024:.0f} MiB ({downloaded * 100 / total:.1f}%)", flush=True)
if downloaded != total:
    raise SystemExit(f"downloaded size mismatch: {downloaded} != {total}")
if digest.hexdigest() != manifest["sha256"]:
    raise SystemExit(f"downloaded sha mismatch: {digest.hexdigest()} != {manifest['sha256']}")
os.replace(tmp, package)
PY
'''

    release_name = verified_release["releaseId"]
    release_parent = "/data/erp-new/releases"
    release_dir = f"{release_parent}/{release_name}"
    services = " ".join(HOST_SERVICE_PORTS)
    start_services = " ".join(HOST_SERVICE_START_ORDER)
    required_previous_services = " ".join(HOST_REQUIRED_BASELINE_SERVICES)
    service_ports = " ".join(
        f"{service}:{port}" for service, port in HOST_SERVICE_PORTS.items()
    )
    service_artifacts = " ".join(
        f"{service}:{artifact}" for service, artifact in HOST_SERVICE_ARTIFACTS.items()
    )
    runtime_contract = render_host_runtime_contract()
    remote_release_verifier = render_remote_release_verifier()
    remote_release_env_stamper = render_remote_release_env_stamper()
    command = f"""#!/usr/bin/env bash
set -euo pipefail
umask 077
PACKAGE={json.dumps(package.name)}
MANIFEST_URL={json.dumps(manifest_url)}
EXPECTED_SHA={json.dumps(package_sha)}
RELEASE_DIR={json.dumps(release_dir)}
RELEASE_ID={json.dumps(verified_release["releaseId"])}
EXPECTED_GIT_COMMIT={json.dumps(verified_release["gitCommit"])}
EXPECTED_BUILD_TIME={json.dumps(verified_release["buildTime"])}
SERVICES={json.dumps(services)}
START_SERVICES={json.dumps(start_services)}
REQUIRED_PREVIOUS_SERVICES={json.dumps(required_previous_services)}
SERVICE_PORTS={json.dumps(service_ports)}
SERVICE_ARTIFACTS={json.dumps(service_artifacts)}
SHARED_UPLOAD_ROOT={json.dumps(HOST_SHARED_UPLOAD_ROOT)}
FILE_PATH={json.dumps(HOST_FILE_PATH)}
SIGN_PACKAGE_STORAGE_ROOT={json.dumps(HOST_SIGN_PACKAGE_STORAGE_ROOT)}
OA_ATTENDANCE_STORAGE_ROOT={json.dumps(HOST_ATTENDANCE_STORAGE_ROOT)}
OA_REIMBURSEMENT_STORAGE_ROOT={json.dumps(HOST_REIMBURSEMENT_STORAGE_ROOT)}
DRIVE_LOCAL_PATH={json.dumps(HOST_DRIVE_LOCAL_PATH)}
CURRENT_LINK=/data/erp-new/current
PREV_TARGET="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
if [ -z "$PREV_TARGET" ] || [ ! -d "$PREV_TARGET" ]; then
  echo "previous /opt/erp-new target not found" >&2
  exit 30
fi

{runtime_contract}
collect_previous_active_services

if [ -e "$RELEASE_DIR" ]; then
  echo "release directory already exists: $RELEASE_DIR" >&2
  exit 31
fi
# Fail before creating or downloading anything if /data is absent or redirected.
for project_path in /data/erp-new/releases /data/erp-new/packages /data/erp-new/backups \
    /data/erp-new-data/logs /data/erp-new-data/tmp /data/erp-new-data/cache; do
  test -d "$project_path" && test -w "$project_path"
  test "$(findmnt -n -o TARGET -T "$project_path")" = /data
done
test -L /opt/erp-new
test "$(readlink /opt/erp-new)" = /data/erp-new/current
mkdir "$RELEASE_DIR"
chmod 755 "$RELEASE_DIR"
cd /data/erp-new/packages
{acquire_package_script}
ACTUAL_SHA="$(sha256sum "$PACKAGE" | awk '{{print $1}}')"
if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  echo "sha256 mismatch: $ACTUAL_SHA" >&2
  exit 32
fi
tar -xzf "$PACKAGE" -C "$RELEASE_DIR"
PACKAGE_ROOT="$RELEASE_DIR/$RELEASE_ID"
test -d "$PACKAGE_ROOT"
python3 - "$PACKAGE_ROOT" "$RELEASE_ID" "$EXPECTED_GIT_COMMIT" "$EXPECTED_BUILD_TIME" <<'PY'
{remote_release_verifier}
PY
NEW_DOCKER="$PACKAGE_ROOT/docker"
test -d "$NEW_DOCKER"

# Preserve host-specific runtime config. The packaged wrapper adds monitor and
# approval while delegating the original seven mappings to the legacy runner.
cp -a "$PREV_TARGET/.env" "$NEW_DOCKER/.env"
python3 - "$NEW_DOCKER/.env" "$RELEASE_ID" "$EXPECTED_GIT_COMMIT" "$EXPECTED_BUILD_TIME" <<'PY'
{remote_release_env_stamper}
PY
test "$(grep -Fxc "RELEASE_ID=$RELEASE_ID" "$NEW_DOCKER/.env")" -eq 1
test "$(grep -Fxc "GIT_COMMIT=$EXPECTED_GIT_COMMIT" "$NEW_DOCKER/.env")" -eq 1
test "$(grep -Fxc "BUILD_TIME=$EXPECTED_BUILD_TIME" "$NEW_DOCKER/.env")" -eq 1
test -s "$NEW_DOCKER/run-erp-service.sh"
LEGACY_RUNNER_SOURCE="$PREV_TARGET/run-erp-service-legacy.sh"
if [ ! -f "$LEGACY_RUNNER_SOURCE" ]; then
  LEGACY_RUNNER_SOURCE="$PREV_TARGET/run-erp-service.sh"
fi
test -f "$LEGACY_RUNNER_SOURCE"
cp -pL "$LEGACY_RUNNER_SOURCE" "$NEW_DOCKER/run-erp-service-legacy.sh"
chmod 700 "$NEW_DOCKER/run-erp-service.sh" "$NEW_DOCKER/run-erp-service-legacy.sh"
test ! -e "$NEW_DOCKER/logs"
ln -s /data/erp-new-data/logs "$NEW_DOCKER/logs"
verify_host_service_contract "$NEW_DOCKER"
trap 'rollback "$?"' ERR
trap 'rollback_on_exit "$?"' EXIT
ensure_shared_upload_source
link_shared_upload_target "$PREV_TARGET"
link_shared_upload_target "$NEW_DOCKER"
verify_release_upload_links "$PREV_TARGET"
verify_release_upload_links "$NEW_DOCKER"

{migration_script}

atomic_release_link "$NEW_DOCKER" "$CURRENT_LINK"
systemctl daemon-reload
# Preserve the production service-state boundary: restart only services that
# were active before the release. Optional job/monitor services must not be
# enabled as a side effect of an unrelated deployment.
start_services $PREVIOUS_ACTIVE_SERVICES
wait_for_host_readiness "$PREVIOUS_ACTIVE_SERVICES"
verify_host_storage_runtime "$NEW_DOCKER"
reload_nginx
HEALTHCHECK_HOST="${{ERP_HEALTHCHECK_HOST:-8.152.199.39}}"
curl -fsS --max-time 10 -H "Host: $HEALTHCHECK_HOST" http://127.0.0.1/ >/dev/null
business_json_ready http://127.0.0.1/prod-api/code "$HEALTHCHECK_HOST"
for svc in $SERVICES; do
  printf '%s=%s\n' "$svc" "$(systemctl is-active "erp-new@$svc.service")"
done
ss -ltnp | grep -E ':(80|8080|9100|9200|9201|9203|9204|9205|9206|9300)\\b'
rm -f "/data/erp-new/packages/$PACKAGE"
atomic_release_link "$PREV_TARGET" /data/erp-new/previous
trap - ERR EXIT
echo "DEPLOY_OK release=$RELEASE_DIR previous=$PREV_TARGET previous_services=$PREVIOUS_ACTIVE_SERVICES"
"""

    print("Running host-style deployment through ECS Cloud Assistant ...", flush=True)
    try:
        result = run_command(client, args.region, args.instance_id, command, "erp-deploy-host", args.timeout)
        output = decode_output(result)
        print(json.dumps({
            "invocationStatus": result.get("InvocationStatus"),
            "exitCode": result.get("ExitCode"),
            "startTime": result.get("StartTime"),
            "finishedTime": result.get("FinishedTime"),
            "output": output[-20000:],
        }, ensure_ascii=False, indent=2))
        if result.get("ExitCode") not in (0, "0"):
            raise SystemExit(1)
    finally:
        if args.cleanup_oss and bucket is not None:
            try:
                for key in cleanup_keys:
                    try:
                        bucket.delete_object(key)
                    except Exception:
                        pass
                if created_bucket or bucket_name.startswith("erp-deploy-"):
                    bucket.delete_bucket()
            except Exception as exc:
                print(f"warning: OSS cleanup failed: {exc}", file=sys.stderr)


def deploy_host_patch(args):
    raise SystemExit(
        "deploy-host-patch is retired because raw overlays cannot prove one coherent "
        "frontend/JAR release identity; build a canonical release archive and use deploy-host"
    )


def inspect(args):
    ak, secret = read_credentials()
    client = ecs_client(ak, secret)
    command = r"""#!/usr/bin/env bash
set -euo pipefail
echo "HOST=$(hostname)"
echo "DATE=$(date '+%F %T %z')"
echo "OS=$(cat /etc/os-release 2>/dev/null | sed -n 's/^PRETTY_NAME=//p' | tr -d '"' | head -1)"
echo "WHOAMI=$(whoami)"
echo "PWD=$(pwd)"
echo "DOCKER=$(command -v docker || true)"
docker --version 2>/dev/null || true
docker compose version 2>/dev/null || true
echo "-- containers --"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || true
echo "-- candidate dirs --"
for d in /opt/erp /home/erp /root/erp /data/erp /www/wwwroot/erp; do
  if [ -e "$d" ]; then
    ls -ld "$d"
  fi
done
echo "-- listening ports --"
ss -ltnp 2>/dev/null | sed -n '1,80p' || true
echo "-- disk --"
df -h / /opt 2>/dev/null || df -h /
echo "-- memory --"
free -h 2>/dev/null || true
echo "-- java processes --"
ps -eo pid,ppid,lstart,cmd | grep '[j]ava' | sed -n '1,120p'
echo "-- java process cwd/cmdline --"
for pid in $(pgrep -f 'java' || true); do
  echo "PID=$pid"
  readlink "/proc/$pid/cwd" 2>/dev/null || true
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true
  echo
done
echo "-- service units matching erp/java/nginx/mysql/redis --"
systemctl list-units --type=service --all --no-pager 2>/dev/null | grep -Ei 'erp|java|nginx|mysql|redis|nacos|sentinel' || true
echo "-- jar locations --"
find / -xdev -type f \( -name 'erp-*.jar' -o -name 'ruoyi-*.jar' \) 2>/dev/null | sed -n '1,160p'
echo "-- nginx config refs --"
nginx -T 2>/dev/null | grep -En 'server_name|root |proxy_pass|listen ' | sed -n '1,160p' || true
echo "-- opt erp layout --"
ls -lah /opt 2>/dev/null | grep 'erp-new' || true
ls -lah /opt/erp-new /opt/erp-new-20260629124340 /opt/erp-new-20260629124340/docker 2>/dev/null || true
echo "-- erp-new service template --"
systemctl cat 'erp-new@gateway.service' 2>/dev/null | sed -n '1,220p' || true
echo "-- redacted docker env keys --"
if [ -f /opt/erp-new/.env ]; then
  awk -F= 'NF && $1 !~ /^#/ {print $1"=<redacted>"}' /opt/erp-new/.env
elif [ -f /opt/erp-new-20260629124340/docker/.env ]; then
  awk -F= 'NF && $1 !~ /^#/ {print $1"=<redacted>"}' /opt/erp-new-20260629124340/docker/.env
fi
echo "-- run-erp-service.sh --"
sed -n '1,220p' /opt/erp-new/run-erp-service.sh 2>/dev/null || true
echo "-- erp service overrides --"
for svc in gateway auth monitor system job oa inventory file approval; do
  echo "### $svc"
  systemctl cat "erp-new@$svc.service" 2>/dev/null | grep -E 'Environment=JAR_PATH|Environment=SPRING_PROFILE|ExecStart|WorkingDirectory' || true
done
"""
    result = run_command(client, args.region, args.instance_id, command, "erp-inspect", 300)
    print(json.dumps({
        "invocationStatus": result.get("InvocationStatus"),
        "exitCode": result.get("ExitCode"),
        "output": decode_output(result),
    }, ensure_ascii=False, indent=2))


def authorize_ssh(args):
    ak, secret = read_credentials()
    public_key = Path(args.public_key).read_text().strip()
    if not public_key:
        raise SystemExit(f"empty public key: {args.public_key}")
    client = ecs_client(ak, secret)
    marker = " codex-temp-erp-deploy"
    key_line = public_key
    if marker not in key_line:
        key_line = key_line + marker
    if args.remove:
        command = f"""#!/usr/bin/env bash
set -euo pipefail
AUTH=/root/.ssh/authorized_keys
if [ -f "$AUTH" ]; then
  grep -vxF {json.dumps(key_line)} "$AUTH" > "$AUTH.tmp" || true
  cat "$AUTH.tmp" > "$AUTH"
  rm -f "$AUTH.tmp"
  chmod 600 "$AUTH"
fi
echo SSH_KEY_REMOVED
"""
    else:
        command = f"""#!/usr/bin/env bash
set -euo pipefail
mkdir -p /root/.ssh
chmod 700 /root/.ssh
AUTH=/root/.ssh/authorized_keys
touch "$AUTH"
grep -qxF {json.dumps(key_line)} "$AUTH" || echo {json.dumps(key_line)} >> "$AUTH"
chmod 600 "$AUTH"
echo SSH_KEY_AUTHORIZED
"""
    result = run_command(client, args.region, args.instance_id, command, "erp-authorize-ssh", 120)
    print(json.dumps({
        "invocationStatus": result.get("InvocationStatus"),
        "exitCode": result.get("ExitCode"),
        "output": decode_output(result),
    }, ensure_ascii=False, indent=2))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


def security_group_ssh(args):
    ak, secret = read_credentials()
    client = ecs_client(ak, secret)
    params = {
        "RegionId": args.region,
        "SecurityGroupId": args.security_group_id,
        "IpProtocol": "tcp",
        "PortRange": "22/22",
        "SourceCidrIp": args.cidr,
        "Policy": "accept",
        "Priority": str(args.priority),
    }
    action = "RevokeSecurityGroup" if args.remove else "AuthorizeSecurityGroup"
    try:
        data = ecs_request(client, action, args.region, params)
        status = "removed" if args.remove else "authorized"
        print(json.dumps({"status": status, "requestId": data.get("RequestId")}, ensure_ascii=False, indent=2))
    except RuntimeError as exc:
        message = str(exc)
        if not args.remove and "InvalidPermission.Duplicate" in message:
            print(json.dumps({"status": "already_exists"}, ensure_ascii=False, indent=2))
            return
        if args.remove and ("InvalidPermission.NotFound" in message or "Forbidden.RulesNotFound" in message):
            print(json.dumps({"status": "already_removed"}, ensure_ascii=False, indent=2))
            return
        raise


def ssh_debug(args):
    ak, secret = read_credentials()
    client = ecs_client(ak, secret)
    command = r"""#!/usr/bin/env bash
set -u
echo "-- sshd status --"
systemctl status sshd --no-pager | sed -n '1,80p' || true
echo "-- sshd config active --"
sshd -T 2>/dev/null | grep -Ei 'port|listenaddress|permitrootlogin|pubkeyauthentication|maxstartups|allowusers|denyusers|passwordauthentication|usepam' || true
echo "-- sshd config files --"
grep -RniE '^(Port|ListenAddress|PermitRootLogin|PubkeyAuthentication|MaxStartups|AllowUsers|DenyUsers|PasswordAuthentication|UsePAM)' /etc/ssh/sshd_config /etc/ssh/sshd_config.d 2>/dev/null || true
echo "-- hosts allow deny --"
cat /etc/hosts.allow 2>/dev/null || true
cat /etc/hosts.deny 2>/dev/null || true
echo "-- recent secure logs --"
(tail -n 120 /var/log/secure 2>/dev/null || journalctl -u sshd --no-pager -n 120 2>/dev/null) | sed -n '1,160p'
"""
    result = run_command(client, args.region, args.instance_id, command, "erp-ssh-debug", 120)
    print(json.dumps({
        "invocationStatus": result.get("InvocationStatus"),
        "exitCode": result.get("ExitCode"),
        "output": decode_output(result),
    }, ensure_ascii=False, indent=2))


def run_shell(args):
    ak, secret = read_credentials()
    client = ecs_client(ak, secret)
    if args.script_file == "-":
        command = sys.stdin.read()
    else:
        command = Path(args.script_file).read_text()
    if args.legacy_preflight:
        if args.script_file == "-" or Path(args.script_file).name != "remote_deploy_verify.sh":
            raise SystemExit("--legacy-preflight is restricted to remote_deploy_verify.sh")
        if "predeploy" not in args.name.lower():
            raise SystemExit("--legacy-preflight requires a command name containing 'predeploy'")
        command = (
            "export ERP_ALLOW_LEGACY_UPLOAD_PATH=true\n"
            "export ERP_ALLOW_LEGACY_SERVICE_SET=true\n"
            "export ERP_SKIP_FINALIZED_RELEASE_MANIFEST=true\n"
            + command
        )
    result = run_command(client, args.region, args.instance_id, command, args.name, args.timeout)
    print(json.dumps({
        "invocationStatus": result.get("InvocationStatus"),
        "exitCode": result.get("ExitCode"),
        "output": decode_output(result),
    }, ensure_ascii=False, indent=2))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


def add_release_migration_arguments(parser):
    parser.add_argument(
        "--migration-manifest",
        help="local versioned migration manifest to validate and expect in the package",
    )
    parser.add_argument(
        "--apply-migrations",
        action="store_true",
        help="explicitly back up and apply only the supplied migration manifest",
    )
    parser.add_argument(
        "--approve-migrations",
        help="must exactly equal the manifest releaseId when applying migrations",
    )
    parser.add_argument(
        "--database-name",
        help="explicit remote database name; valid only with --apply-migrations",
    )
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument(
        "--mysql-password-file",
        default="/root/.erp-mysql-root-pass",
        help="absolute password-file path on the remote host",
    )


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_instances = sub.add_parser("instances")
    p_instances.add_argument("--regions", help="comma-separated region ids; omit to query all ECS regions")
    p_instances.set_defaults(func=print_instances)

    p_inspect = sub.add_parser("inspect")
    p_inspect.add_argument("--region", required=True)
    p_inspect.add_argument("--instance-id", required=True)
    p_inspect.set_defaults(func=inspect)

    p_ssh = sub.add_parser("authorize-ssh")
    p_ssh.add_argument("--region", required=True)
    p_ssh.add_argument("--instance-id", required=True)
    p_ssh.add_argument("--public-key", default=str(Path.home() / ".ssh/id_ed25519.pub"))
    p_ssh.add_argument("--remove", action="store_true")
    p_ssh.set_defaults(func=authorize_ssh)

    p_sg = sub.add_parser("security-group-ssh")
    p_sg.add_argument("--region", required=True)
    p_sg.add_argument("--security-group-id", required=True)
    p_sg.add_argument("--cidr", required=True)
    p_sg.add_argument("--priority", type=int, default=1)
    p_sg.add_argument("--remove", action="store_true")
    p_sg.set_defaults(func=security_group_ssh)

    p_ssh_debug = sub.add_parser("ssh-debug")
    p_ssh_debug.add_argument("--region", required=True)
    p_ssh_debug.add_argument("--instance-id", required=True)
    p_ssh_debug.set_defaults(func=ssh_debug)

    p_run = sub.add_parser("run-shell")
    p_run.add_argument("--region", required=True)
    p_run.add_argument("--instance-id", required=True)
    p_run.add_argument("--script-file", required=True)
    p_run.add_argument("--name", default="erp-run-shell")
    p_run.add_argument("--timeout", type=int, default=300)
    p_run.add_argument(
        "--legacy-preflight",
        action="store_true",
        help="one-time read-only preflight before monitor/approval and persistent upload are first managed",
    )
    p_run.set_defaults(func=run_shell)

    p_deploy = sub.add_parser("deploy")
    p_deploy.add_argument("--region", required=True)
    p_deploy.add_argument("--instance-id", required=True)
    p_deploy.add_argument("--package", required=True)
    p_deploy.add_argument("--remote-root", default="/opt/erp")
    p_deploy.add_argument("--bucket")
    p_deploy.add_argument("--url-ttl", type=int, default=7200)
    p_deploy.add_argument("--timeout", type=int, default=1200)
    p_deploy.add_argument("--oss-scheme", choices=["https", "http"], default="https")
    p_deploy.add_argument("--cleanup-oss", action="store_true")
    p_deploy.set_defaults(func=deploy)

    p_deploy_host = sub.add_parser("deploy-host")
    p_deploy_host.add_argument("--region", required=True)
    p_deploy_host.add_argument("--instance-id", required=True)
    p_deploy_host.add_argument("--package", required=True)
    p_deploy_host.add_argument(
        "--remote-package",
        help="reuse a same-named, pre-uploaded package directly under /data/erp-new/packages",
    )
    p_deploy_host.add_argument("--bucket")
    p_deploy_host.add_argument("--url-ttl", type=int, default=7200)
    p_deploy_host.add_argument("--timeout", type=int, default=1800)
    p_deploy_host.add_argument("--oss-scheme", choices=["https", "http"], default="https")
    p_deploy_host.add_argument("--cleanup-oss", action="store_true")
    add_release_migration_arguments(p_deploy_host)
    p_deploy_host.set_defaults(func=deploy_host)

    p_deploy_host_patch = sub.add_parser(
        "deploy-host-patch",
        help="retired: raw overlays cannot prove a coherent frontend/JAR release",
    )
    p_deploy_host_patch.set_defaults(func=deploy_host_patch)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
