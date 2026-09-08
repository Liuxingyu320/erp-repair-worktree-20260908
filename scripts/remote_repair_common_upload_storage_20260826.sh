#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# One-time, fail-closed repair for the 2026-08-26 common upload-root split.
#
# The default mode is a read-only audit.  Production writes require BOTH
# --apply and the exact confirmation token printed by --help.  A completed
# apply writes a self-contained rollback bundle.  This script never copies,
# moves, links, removes, or rewrites private/sign-package.

readonly SCRIPT_ID="common-upload-storage-20260826"
readonly APPLY_TOKEN="APPLY_COMMON_UPLOAD_STORAGE_20260826"
readonly ROLLBACK_TOKEN="ROLLBACK_COMMON_UPLOAD_STORAGE_20260826"
readonly PINNED_SEAL_RELATIVE="public/2026/06/14/图片1_20260614140927A001.png"
readonly PINNED_SEAL_SIZE="55761"
readonly PINNED_SEAL_SHA256="4fa824798d039fabf04382f547a3d867f645343065d1cb87bfde7e35e4d4de14"

MODE="audit"
MODE_SET=0
CONFIRM_TOKEN=""
ROLLBACK_DIR=""
OUTPUT_DIR=""
declare -a EXTRA_CANDIDATES=()
EXTRA_CANDIDATE_COUNT=0

usage() {
  cat <<'EOF'
Usage:
  remote_repair_common_upload_storage_20260826.sh [--audit] [--output-dir DIR]
  remote_repair_common_upload_storage_20260826.sh --apply \
    --confirm APPLY_COMMON_UPLOAD_STORAGE_20260826 [--candidate DIR ...]
  remote_repair_common_upload_storage_20260826.sh --rollback BACKUP_DIR \
    --confirm ROLLBACK_COMMON_UPLOAD_STORAGE_20260826

Modes:
  --audit       Read-only database/filesystem audit (default).  It writes only
                its report under /tmp unless --output-dir is supplied.
  --apply       Restore hash-verified public/sign-template files, switch the
                common root, and restart only erp-new@file and erp-new@oa.
  --rollback    Restore the sealed pre-apply environment and systemd drop-ins.
                TARGET_ROOT files/directories and every legacy compatibility
                path are deliberately preserved; rollback never deletes or
                moves them.  Sealed incomplete applies can be recovered too.

Safety:
  * Production target is fixed at /data/erp-new-data/uploadPath.
  * private/sign-package is read and fingerprinted, never mutated.
  * Database access is read-only.  The extracted password is never printed,
    passed in argv, or persisted separately; the required exact .env backup
    remains inside the root-only rollback bundle.
  * ERP_REPAIR_OA_BEARER_TOKEN is optional.  When a safe token is present,
    authenticated OA business and template-file postchecks run.  Without one,
    that browser-only check is recorded as DEFERRED_TO_SAFARI_SAME_ORIGIN;
    physical-file, public HTTP, health, and runtime checks remain mandatory.
  * A target-path hash/size conflict, missing verified source, path traversal,
    or post-apply drift aborts closed.
  * Legacy compatibility symlinks/directories are audited but never replaced;
    service storage is switched only through .env and systemd guards.
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '%s | %s\n' "$(date '+%F %T %z')" "$*"
}

while (($#)); do
  case "$1" in
    --audit)
      [[ "$MODE_SET" == "0" ]] || die "only one mode may be selected"
      MODE="audit"
      MODE_SET=1
      shift
      ;;
    --apply)
      [[ "$MODE_SET" == "0" ]] || die "only one mode may be selected"
      MODE="apply"
      MODE_SET=1
      shift
      ;;
    --rollback)
      [[ "$MODE_SET" == "0" ]] || die "only one mode may be selected"
      (($# >= 2)) || die "--rollback requires BACKUP_DIR"
      MODE="rollback"
      MODE_SET=1
      ROLLBACK_DIR="$2"
      shift 2
      ;;
    --confirm)
      (($# >= 2)) || die "--confirm requires a token"
      CONFIRM_TOKEN="$2"
      shift 2
      ;;
    --candidate)
      (($# >= 2)) || die "--candidate requires a directory"
      EXTRA_CANDIDATES+=("$2")
      ((EXTRA_CANDIDATE_COUNT += 1))
      shift 2
      ;;
    --output-dir)
      (($# >= 2)) || die "--output-dir requires a directory"
      OUTPUT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

if [[ "$MODE" == "audit" && -n "$CONFIRM_TOKEN" ]]; then
  die "--confirm is not accepted in audit mode"
fi
if [[ "$MODE" != "audit" && -n "$OUTPUT_DIR" ]]; then
  die "--output-dir is only valid in audit mode"
fi
if [[ "$MODE" == "apply" && "$CONFIRM_TOKEN" != "$APPLY_TOKEN" ]]; then
  die "--apply requires --confirm $APPLY_TOKEN"
fi
if [[ "$MODE" == "rollback" && "$CONFIRM_TOKEN" != "$ROLLBACK_TOKEN" ]]; then
  die "--rollback requires --confirm $ROLLBACK_TOKEN"
fi

# Test overrides are intentionally impossible unless the explicit test-mode
# latch is set.  The normal production path remains a constant.
TEST_MODE="${ERP_COMMON_UPLOAD_REPAIR_TEST_MODE:-0}"
if [[ "$TEST_MODE" == "1" ]]; then
  TARGET_ROOT="${ERP_COMMON_UPLOAD_REPAIR_TARGET_ROOT:?test target is required}"
  CURRENT_LINK="${ERP_COMMON_UPLOAD_REPAIR_CURRENT_LINK:?test current link is required}"
  BACKUP_BASE="${ERP_COMMON_UPLOAD_REPAIR_BACKUP_BASE:?test backup base is required}"
  SYSTEMD_ROOT="${ERP_COMMON_UPLOAD_REPAIR_SYSTEMD_ROOT:?test systemd root is required}"
  LOCK_FILE="${ERP_COMMON_UPLOAD_REPAIR_LOCK_FILE:?test lock file is required}"
  FILE_HEALTH_URL="${ERP_COMMON_UPLOAD_REPAIR_FILE_HEALTH_URL:-http://127.0.0.1:9300/actuator/health}"
  OA_HEALTH_URL="${ERP_COMMON_UPLOAD_REPAIR_OA_HEALTH_URL:-http://127.0.0.1:9204/actuator/health}"
  OA_BUSINESS_CHECK_URL="${ERP_COMMON_UPLOAD_REPAIR_OA_BUSINESS_CHECK_URL:-http://127.0.0.1:9204/signTask/capabilities}"
  OA_TEMPLATE_BASE_URL="${ERP_COMMON_UPLOAD_REPAIR_OA_TEMPLATE_BASE_URL:-http://127.0.0.1:9204/signPackage/template}"
  FILE_PUBLIC_BASE_URL="${ERP_COMMON_UPLOAD_REPAIR_FILE_PUBLIC_BASE_URL:-http://127.0.0.1:9300/file/public}"
  PINNED_SEAL_SOURCE="${ERP_COMMON_UPLOAD_REPAIR_PINNED_SEAL_SOURCE:-/opt/erp-new-data/uploadPath/$PINNED_SEAL_RELATIVE}"
else
  readonly TARGET_ROOT="/data/erp-new-data/uploadPath"
  readonly CURRENT_LINK="/opt/erp-new"
  readonly BACKUP_BASE="/data/erp-new-data-backups"
  readonly SYSTEMD_ROOT="/etc/systemd/system"
  readonly LOCK_FILE="/run/lock/${SCRIPT_ID}.lock"
  readonly FILE_HEALTH_URL="http://127.0.0.1:9300/actuator/health"
  readonly OA_HEALTH_URL="http://127.0.0.1:9204/actuator/health"
  readonly OA_BUSINESS_CHECK_URL="http://127.0.0.1:9204/signTask/capabilities"
  readonly OA_TEMPLATE_BASE_URL="http://127.0.0.1:9204/signPackage/template"
  readonly FILE_PUBLIC_BASE_URL="http://127.0.0.1:9300/file/public"
  readonly PINNED_SEAL_SOURCE="/opt/erp-new-data/uploadPath/$PINNED_SEAL_RELATIVE"
fi

readonly SIGN_PACKAGE_ROOT="$TARGET_ROOT/private/sign-package"
readonly FILE_SERVICE="erp-new@file.service"
readonly OA_SERVICE="erp-new@oa.service"
OA_BEARER_TOKEN="${ERP_REPAIR_OA_BEARER_TOKEN:-}"
unset ERP_REPAIR_OA_BEARER_TOKEN
DYNAMIC_SEAL_ENABLED=0
readonly -a MANAGED_STORAGE_RELATIVES=(
  "public"
  "sign-template"
  "private/attendance"
  "private/reimbursement"
  "private/drive"
)

test_failpoint() {
  local name="$1"
  [[ "$TEST_MODE" == "1" ]] || return 0
  [[ "${ERP_COMMON_UPLOAD_REPAIR_TEST_FAILPOINT:-}" != "$name" ]] \
    || die "test failpoint: $name"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command is missing: $1"
}

for command_name in python3 mysql sha256sum stat find readlink awk grep sed sort; do
  require_command "$command_name"
done
if [[ "$MODE" != "audit" ]]; then
  for command_name in systemctl flock curl cp mv sync mysqldump gzip ln mkdir rmdir chmod chown cmp mktemp; do
    require_command "$command_name"
  done
  mv --help 2>&1 | grep -Fq -- '--no-target-directory' \
    || die "GNU mv with atomic -T replacement support is required"
fi

[[ -d "$CURRENT_LINK" || -L "$CURRENT_LINK" ]] || die "current release link is missing: $CURRENT_LINK"
RELEASE_ROOT="$(readlink -f "$CURRENT_LINK")"
[[ -n "$RELEASE_ROOT" && -d "$RELEASE_ROOT" ]] || die "cannot resolve active release: $CURRENT_LINK"
ENV_FILE="$RELEASE_ROOT/.env"
[[ -f "$ENV_FILE" && ! -L "$ENV_FILE" ]] || die "active release .env is missing or is a symlink: $ENV_FILE"

if [[ "$TEST_MODE" != "1" ]]; then
  [[ "$TARGET_ROOT" == "/data/erp-new-data/uploadPath" ]] || die "production target changed unexpectedly"
  [[ "$RELEASE_ROOT" == /opt/* || "$RELEASE_ROOT" == /data/opt/* ]] || die "unexpected active release root: $RELEASE_ROOT"
fi

runtime_env_value() {
  local service="$1"
  local key="$2"
  local pid=""
  if command -v systemctl >/dev/null 2>&1; then
    pid="$(systemctl show "$service" -p MainPID --value 2>/dev/null || true)"
  fi
  [[ "$pid" =~ ^[1-9][0-9]*$ && -r "/proc/$pid/environ" ]] || return 0
  python3 - "$pid" "$key" <<'PY'
import pathlib
import sys

pid, wanted = sys.argv[1:]
for item in pathlib.Path(f"/proc/{pid}/environ").read_bytes().split(b"\0"):
    if b"=" not in item:
        continue
    key, value = item.split(b"=", 1)
    if key.decode(errors="ignore") == wanted:
        sys.stdout.write(value.decode(errors="ignore"))
        break
PY
}

dotenv_value() {
  local key="$1"
  python3 - "$ENV_FILE" "$key" <<'PY'
import pathlib
import re
import sys

path, wanted = sys.argv[1:]
found = []
for raw in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
    line = raw.rstrip("\r")
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    if key == wanted:
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        found.append(value)
if len(found) > 1:
    raise SystemExit(f"duplicate dotenv key: {wanted}")
if found:
    sys.stdout.write(found[0])
PY
}

load_db_credentials() {
  DB_USER="${ERP_REPAIR_DB_USER:-}"
  DB_PASSWORD="${ERP_REPAIR_DB_PASSWORD:-}"
  DB_NAME="${ERP_REPAIR_DB_NAME:-}"

  [[ -n "$DB_USER" ]] || DB_USER="$(runtime_env_value "$OA_SERVICE" MYSQL_USERNAME)"
  [[ -n "$DB_PASSWORD" ]] || DB_PASSWORD="$(runtime_env_value "$OA_SERVICE" MYSQL_PASSWORD)"
  [[ -n "$DB_NAME" ]] || DB_NAME="$(runtime_env_value "$OA_SERVICE" MYSQL_DATABASE)"
  [[ -n "$DB_USER" ]] || DB_USER="$(dotenv_value MYSQL_USERNAME)"
  [[ -n "$DB_PASSWORD" ]] || DB_PASSWORD="$(dotenv_value MYSQL_PASSWORD)"
  [[ -n "$DB_NAME" ]] || DB_NAME="$(dotenv_value MYSQL_DATABASE)"

  [[ -n "$DB_USER" ]] || die "cannot determine read-only MySQL username"
  [[ -n "$DB_NAME" && "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || die "cannot determine a safe MySQL database name"
  # An empty password is permitted only when it is the actual configured value.
  export MYSQL_PWD="$DB_PASSWORD"
  MYSQL=(mysql --batch --raw --skip-column-names --default-character-set=utf8mb4 --user "$DB_USER" "$DB_NAME")
}

mysql_to_file() {
  local sql="$1"
  local output="$2"
  "${MYSQL[@]}" --execute "$sql" >"$output"
}

has_column() {
  local table="$1"
  local column="$2"
  grep -Fqx "$table"$'\t'"$column" "$SCHEMA_FILE"
}

append_select() {
  DB_SELECTS+=("$1")
  ((DB_SELECT_COUNT += 1))
}

build_db_reference_file() {
  local output="$1"
  local query_file="$2"

  SCHEMA_FILE="${output}.schema"
  mysql_to_file \
    "SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('oa_sign_template','oa_sign_plan_version_template','oa_company_seal_config','oa_sign_package','oa_sign_package_document','oa_sign_file_evidence','oa_labor_contract_template','oa_labor_contract') ORDER BY TABLE_NAME,ORDINAL_POSITION" \
    "$SCHEMA_FILE"

  DB_SELECTS=()
  DB_SELECT_COUNT=0
  if has_column oa_sign_template template_id && has_column oa_sign_template file_url \
      && has_column oa_sign_template file_size && has_column oa_sign_template file_hash; then
    append_select "SELECT 'oa_sign_template',CAST(template_id AS CHAR),HEX(file_url),COALESCE(CAST(file_size AS CHAR),''),LOWER(COALESCE(file_hash,'')) FROM oa_sign_template WHERE file_url IS NOT NULL AND file_url<>''"
  fi
  if has_column oa_sign_plan_version_template id && has_column oa_sign_plan_version_template source_file_url \
      && has_column oa_sign_plan_version_template source_file_hash; then
    append_select "SELECT 'oa_sign_plan_version_template',CAST(id AS CHAR),HEX(source_file_url),'',LOWER(COALESCE(source_file_hash,'')) FROM oa_sign_plan_version_template WHERE source_file_url IS NOT NULL AND source_file_url<>''"
  fi
  if has_column oa_company_seal_config seal_id && has_column oa_company_seal_config seal_image_url \
      && has_column oa_company_seal_config seal_image_hash; then
    append_select "SELECT 'oa_company_seal_config',CAST(seal_id AS CHAR),HEX(seal_image_url),'',LOWER(COALESCE(seal_image_hash,'')) FROM oa_company_seal_config WHERE seal_image_url IS NOT NULL AND seal_image_url<>''"
  fi
  if has_column oa_sign_package package_id && has_column oa_sign_package seal_image_url_snapshot \
      && has_column oa_sign_package seal_image_hash_snapshot; then
    append_select "SELECT 'oa_sign_package_seal_snapshot',CAST(package_id AS CHAR),HEX(seal_image_url_snapshot),'',LOWER(COALESCE(seal_image_hash_snapshot,'')) FROM oa_sign_package WHERE seal_image_url_snapshot IS NOT NULL AND seal_image_url_snapshot<>''"
  fi
  if has_column oa_sign_package_document document_id && has_column oa_sign_package_document source_file_url_snapshot; then
    append_select "SELECT 'oa_sign_package_document_source',CAST(document_id AS CHAR),HEX(source_file_url_snapshot),'','' FROM oa_sign_package_document WHERE source_file_url_snapshot IS NOT NULL AND source_file_url_snapshot<>''"
  fi
  if has_column oa_sign_package_document document_id && has_column oa_sign_package_document generated_file_url \
      && has_column oa_sign_package_document file_hash_before_sign; then
    append_select "SELECT 'oa_sign_package_document_generated',CAST(document_id AS CHAR),HEX(generated_file_url),'',LOWER(COALESCE(file_hash_before_sign,'')) FROM oa_sign_package_document WHERE generated_file_url IS NOT NULL AND generated_file_url<>''"
  fi
  if has_column oa_sign_package_document document_id && has_column oa_sign_package_document signed_file_url \
      && has_column oa_sign_package_document file_hash_after_sign; then
    append_select "SELECT 'oa_sign_package_document_signed',CAST(document_id AS CHAR),HEX(signed_file_url),'',LOWER(COALESCE(file_hash_after_sign,'')) FROM oa_sign_package_document WHERE signed_file_url IS NOT NULL AND signed_file_url<>''"
  fi
  if has_column oa_sign_package_document document_id && has_column oa_sign_package_document review_pdf_url \
      && has_column oa_sign_package_document review_pdf_hash; then
    append_select "SELECT 'oa_sign_package_document_review_pdf',CAST(document_id AS CHAR),HEX(review_pdf_url),'',LOWER(COALESCE(review_pdf_hash,'')) FROM oa_sign_package_document WHERE review_pdf_url IS NOT NULL AND review_pdf_url<>''"
  fi
  if has_column oa_sign_package_document document_id && has_column oa_sign_package_document signed_pdf_url \
      && has_column oa_sign_package_document signed_pdf_hash; then
    append_select "SELECT 'oa_sign_package_document_signed_pdf',CAST(document_id AS CHAR),HEX(signed_pdf_url),'',LOWER(COALESCE(signed_pdf_hash,'')) FROM oa_sign_package_document WHERE signed_pdf_url IS NOT NULL AND signed_pdf_url<>''"
  fi
  if has_column oa_sign_package_document document_id && has_column oa_sign_package_document signature_file_url \
      && has_column oa_sign_package_document signature_hash; then
    append_select "SELECT 'oa_sign_package_document_signature',CAST(document_id AS CHAR),HEX(signature_file_url),'',LOWER(COALESCE(signature_hash,'')) FROM oa_sign_package_document WHERE signature_file_url IS NOT NULL AND signature_file_url<>''"
  fi
  if has_column oa_sign_package_document document_id && has_column oa_sign_package_document certificate_file_url \
      && has_column oa_sign_package_document certificate_hash; then
    append_select "SELECT 'oa_sign_package_document_certificate',CAST(document_id AS CHAR),HEX(certificate_file_url),'',LOWER(COALESCE(certificate_hash,'')) FROM oa_sign_package_document WHERE certificate_file_url IS NOT NULL AND certificate_file_url<>''"
  fi
  if has_column oa_sign_file_evidence evidence_id && has_column oa_sign_file_evidence file_url \
      && has_column oa_sign_file_evidence file_size && has_column oa_sign_file_evidence file_hash; then
    append_select "SELECT 'oa_sign_file_evidence',CAST(evidence_id AS CHAR),HEX(file_url),COALESCE(CAST(file_size AS CHAR),''),LOWER(COALESCE(file_hash,'')) FROM oa_sign_file_evidence WHERE file_url IS NOT NULL AND file_url<>''"
  fi
  if has_column oa_labor_contract_template template_id && has_column oa_labor_contract_template template_file_url; then
    append_select "SELECT 'oa_labor_contract_template',CAST(template_id AS CHAR),HEX(template_file_url),'','' FROM oa_labor_contract_template WHERE template_file_url IS NOT NULL AND template_file_url<>''"
  fi
  if has_column oa_labor_contract contract_id && has_column oa_labor_contract archive_file_url \
      && has_column oa_labor_contract archive_file_hash; then
    append_select "SELECT 'oa_labor_contract_archive',CAST(contract_id AS CHAR),HEX(archive_file_url),'',LOWER(COALESCE(archive_file_hash,'')) FROM oa_labor_contract WHERE archive_file_url IS NOT NULL AND archive_file_url<>''"
  fi
  if has_column oa_labor_contract contract_id && has_column oa_labor_contract preview_file_url \
      && has_column oa_labor_contract preview_file_hash; then
    append_select "SELECT 'oa_labor_contract_preview',CAST(contract_id AS CHAR),HEX(preview_file_url),'',LOWER(COALESCE(preview_file_hash,'')) FROM oa_labor_contract WHERE preview_file_url IS NOT NULL AND preview_file_url<>''"
  fi
  if has_column oa_labor_contract contract_id && has_column oa_labor_contract certificate_file_url \
      && has_column oa_labor_contract certificate_file_hash; then
    append_select "SELECT 'oa_labor_contract_certificate',CAST(contract_id AS CHAR),HEX(certificate_file_url),'',LOWER(COALESCE(certificate_file_hash,'')) FROM oa_labor_contract WHERE certificate_file_url IS NOT NULL AND certificate_file_url<>''"
  fi
  if has_column oa_labor_contract contract_id && has_column oa_labor_contract pdf_file_url; then
    append_select "SELECT 'oa_labor_contract_pdf',CAST(contract_id AS CHAR),HEX(pdf_file_url),'','' FROM oa_labor_contract WHERE pdf_file_url IS NOT NULL AND pdf_file_url<>''"
  fi
  if has_column oa_labor_contract contract_id && has_column oa_labor_contract signature_file_url; then
    append_select "SELECT 'oa_labor_contract_signature',CAST(contract_id AS CHAR),HEX(signature_file_url),'','' FROM oa_labor_contract WHERE signature_file_url IS NOT NULL AND signature_file_url<>''"
  fi
  ((DB_SELECT_COUNT > 0)) || die "none of the expected signing file-reference schemas exists"

  {
    printf '%s\n' 'SET SESSION TRANSACTION READ ONLY;' 'START TRANSACTION READ ONLY;'
    local index
    for index in "${!DB_SELECTS[@]}"; do
      ((index == 0)) || printf '%s\n' 'UNION ALL'
      printf '%s\n' "${DB_SELECTS[$index]}"
    done
    printf '%s\n' ';' 'COMMIT;'
  } >"$query_file"
  "${MYSQL[@]}" <"$query_file" >"$output"
}

check_no_inflight_loaded() {
  local output="$1"
  local schema_output="${output}.schema"
  local table
  mysql_to_file \
    "SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('oa_sign_onboard_import_batch','oa_sign_onboard_import_row','oa_sign_file_cleanup','oa_sign_notification_outbox','oa_sign_task_hard_delete_operation') AND COLUMN_NAME='status' ORDER BY TABLE_NAME" \
    "$schema_output"
  for table in \
    oa_sign_onboard_import_batch \
    oa_sign_onboard_import_row \
    oa_sign_file_cleanup \
    oa_sign_notification_outbox \
    oa_sign_task_hard_delete_operation; do
    grep -Fqx "$table"$'\tstatus' "$schema_output" \
      || die "required in-flight status column is missing: $table.status"
  done

  mysql_to_file \
    "SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY;
SELECT 'oa_sign_onboard_import_batch.GENERATING',COUNT(*) FROM oa_sign_onboard_import_batch WHERE UPPER(status)='GENERATING'
UNION ALL SELECT 'oa_sign_onboard_import_row.GENERATING',COUNT(*) FROM oa_sign_onboard_import_row WHERE UPPER(status)='GENERATING'
UNION ALL SELECT 'oa_sign_file_cleanup.PROCESSING',COUNT(*) FROM oa_sign_file_cleanup WHERE UPPER(status)='PROCESSING'
UNION ALL SELECT 'oa_sign_notification_outbox.PROCESSING_OR_SENDING',COUNT(*) FROM oa_sign_notification_outbox WHERE UPPER(status) IN ('PROCESSING','SENDING')
UNION ALL SELECT 'oa_sign_task_hard_delete_operation.PROCESSING',COUNT(*) FROM oa_sign_task_hard_delete_operation WHERE UPPER(status)='PROCESSING';
COMMIT;" \
    "$output"

  python3 - "$output" <<'PY'
import pathlib
import sys

expected = {
    "oa_sign_onboard_import_batch.GENERATING",
    "oa_sign_onboard_import_row.GENERATING",
    "oa_sign_file_cleanup.PROCESSING",
    "oa_sign_notification_outbox.PROCESSING_OR_SENDING",
    "oa_sign_task_hard_delete_operation.PROCESSING",
}
values = {}
for raw in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    fields = raw.split("\t")
    if len(fields) != 2 or fields[0] in values:
        raise SystemExit("malformed or duplicate in-flight result")
    try:
        values[fields[0]] = int(fields[1])
    except ValueError as exc:
        raise SystemExit("non-numeric in-flight result") from exc
if set(values) != expected:
    raise SystemExit(f"incomplete in-flight result: {sorted(set(expected) - set(values))}")
busy = {key: value for key, value in values.items() if value != 0}
if busy:
    raise SystemExit("in-flight signing work blocks storage repair: " + repr(busy))
PY
}

check_no_inflight() {
  local output="$1"
  load_db_credentials
  check_no_inflight_loaded "$output"
  unset MYSQL_PWD DB_PASSWORD
}

discover_candidates() {
  local output="$1"
  local candidate resolved
  declare -a seeds=(
    "$TARGET_ROOT"
    "/data/opt/erp-new-data/uploadPath"
    "/opt/erp-new-data/uploadPath"
    "$RELEASE_ROOT/uploadPath"
    "$RELEASE_ROOT/erp/uploadPath"
  )
  if ((EXTRA_CANDIDATE_COUNT > 0)); then
    seeds+=("${EXTRA_CANDIDATES[@]}")
  fi

  for base in /data/opt /data/rollback /opt; do
    [[ -d "$base" ]] || continue
    while IFS= read -r candidate; do
      seeds+=("$candidate")
    done < <(find "$base" -maxdepth 8 -type d -name uploadPath -print 2>/dev/null || true)
  done

  : >"$output"
  for candidate in "${seeds[@]}"; do
    [[ -e "$candidate" ]] || continue
    resolved="$(readlink -f "$candidate" 2>/dev/null || true)"
    [[ -n "$resolved" && -d "$resolved" ]] || continue
    [[ "$resolved" != "$SIGN_PACKAGE_ROOT" && "$resolved" != "$SIGN_PACKAGE_ROOT"/* ]] \
      || die "candidate unexpectedly points inside private/sign-package: $candidate"
    printf '%s\n' "$resolved" >>"$output"
  done
  sort -u -o "$output" "$output"
}

resolve_manifest() {
  local db_refs="$1"
  local candidates="$2"
  local report_dir="$3"
  mkdir -p "$report_dir"
  python3 - "$db_refs" "$candidates" "$TARGET_ROOT" "$report_dir" \
    "$PINNED_SEAL_RELATIVE" "$PINNED_SEAL_SOURCE" "$PINNED_SEAL_SIZE" "$PINNED_SEAL_SHA256" <<'PY'
import hashlib
import json
import os
import pathlib
import re
import sys
import urllib.parse
from collections import defaultdict

(
    refs_path,
    candidates_path,
    target_text,
    out_text,
    pinned_relative,
    pinned_source_text,
    pinned_size_text,
    pinned_hash,
) = sys.argv[1:]
target_root = pathlib.Path(target_text)
pinned_source = pathlib.Path(pinned_source_text)
pinned_size = int(pinned_size_text)
out_dir = pathlib.Path(out_text)
manifest_path = out_dir / "manifest.tsv"
unresolved_path = out_dir / "unresolved.tsv"
observations_path = out_dir / "candidate-observations.tsv"
summary_path = out_dir / "summary.json"

def decode_url(value):
    try:
        return bytes.fromhex(value).decode("utf-8")
    except (ValueError, UnicodeDecodeError):
        return None

def relative_from_url(url):
    if url is None or any(ord(ch) < 32 for ch in url):
        return None, "MALFORMED_URL"
    parsed = urllib.parse.urlsplit(url)
    path = urllib.parse.unquote(parsed.path).replace("\\", "/")
    prefixes = (
        ("/prod-api/file/public/", "public/"),
        ("/file/public/", "public/"),
        ("/public/", "public/"),
        ("/profile/", ""),
    )
    relative = None
    for prefix, replacement in prefixes:
        if path.startswith(prefix):
            relative = replacement + path[len(prefix):]
            break
    if relative is None:
        return None, "UNSUPPORTED_URL"
    parts = pathlib.PurePosixPath(relative).parts
    if not parts or any(part in ("", ".", "..") for part in parts):
        return None, "UNSAFE_PATH"
    if any(any(ord(ch) < 32 for ch in part) for part in parts):
        return None, "UNSAFE_PATH"
    return "/".join(parts), None

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def unsafe_ancestor_in_target(relative):
    current = target_root
    for part in pathlib.PurePosixPath(relative).parts[:-1]:
        current = current / part
        if os.path.lexists(current) and (os.path.islink(current) or not current.is_dir()):
            return True
    return False

raw_refs = []
with open(refs_path, encoding="utf-8") as handle:
    for number, raw in enumerate(handle, 1):
        fields = raw.rstrip("\n").split("\t")
        if len(fields) != 5:
            raw_refs.append(("db", str(number), "", "", "", None, "MALFORMED_DB_ROW"))
            continue
        kind, source_id, url_hex, size_text, hash_text = fields
        url = decode_url(url_hex)
        relative, error = relative_from_url(url)
        raw_refs.append((kind, source_id, url or "<invalid-utf8>", size_text, hash_text, relative, error))

groups = defaultdict(list)
unresolved = []
summary = {
    "db_references": len(raw_refs),
    "present": 0,
    "restore": 0,
    "missing_source": 0,
    "target_conflict": 0,
    "metadata_conflict": 0,
    "unverified": 0,
    "outside_scope": 0,
    "excluded_sign_package": 0,
    "db_hash_pass": 0,
    "pinned_no_db_hash_cross_verified": 0,
    "known_legacy_unverified_residual": 0,
    "blocking": 0,
}

for item in raw_refs:
    kind, source_id, url, size_text, hash_text, relative, error = item
    if error:
        unresolved.append((kind, source_id, url, error))
        summary["outside_scope"] += 1
        continue
    if relative == "private/sign-package" or relative.startswith("private/sign-package/"):
        unresolved.append((kind, source_id, url, "EXCLUDED_SIGN_PACKAGE"))
        summary["excluded_sign_package"] += 1
        continue
    if not (relative == "public" or relative.startswith("public/")
            or relative == "sign-template" or relative.startswith("sign-template/")):
        unresolved.append((kind, source_id, url, "OUTSIDE_REPAIR_SCOPE"))
        summary["outside_scope"] += 1
        continue
    groups[relative].append(item)

candidates = []
for raw in pathlib.Path(candidates_path).read_text(encoding="utf-8").splitlines():
    if not raw:
        continue
    root = pathlib.Path(raw)
    if root.is_dir() and not root.is_symlink():
        candidates.append(root)

manifest_rows = []
observations = []
hash_pattern = re.compile(r"[0-9a-f]{64}")
seal_cross_hashes = {
    item[4].lower()
    for item in raw_refs
    if item[0] == "oa_company_seal_config"
    and item[1] == "4"
    and item[6] is None
    and item[5] is not None
    and (item[5].startswith("public/") or item[5].startswith("sign-template/"))
    and hash_pattern.fullmatch(item[4].lower())
}

for relative in sorted(groups):
    items = groups[relative]
    valid_hashes = {item[4].lower() for item in items if hash_pattern.fullmatch(item[4].lower())}
    invalid_nonempty_hash = any(item[4] and not hash_pattern.fullmatch(item[4].lower()) for item in items)
    sizes = set()
    invalid_size = False
    for item in items:
        if not item[3]:
            continue
        try:
            parsed_size = int(item[3])
            if parsed_size < 0:
                raise ValueError
            sizes.add(parsed_size)
        except ValueError:
            invalid_size = True
    refs = ";".join(sorted({f"{item[0]}:{item[1]}" for item in items}))
    urls = ";".join(sorted({item[2] for item in items}))

    if len(valid_hashes) > 1 or len(sizes) > 1 or invalid_size or invalid_nonempty_hash:
        reason = "DB_METADATA_CONFLICT"
        unresolved.append((refs, "", urls, reason))
        summary["metadata_conflict"] += 1
        summary["blocking"] += 1
        continue
    pinned = False
    if not valid_hashes:
        known_legacy_residuals = {
            ("1", "public/2026/06/14/杭州劳动合同有社保版名田_20260614142819A005.docx"),
            ("2", "public/2026/06/14/杭州劳动合同(无社保版)名田_20260614142810A004.docx"),
        }
        is_known_legacy_residual = (
            len(items) == 1
            and items[0][0] == "oa_labor_contract_template"
            and (items[0][1], relative) in known_legacy_residuals
        )
        if is_known_legacy_residual:
            unresolved.append((items[0][0], items[0][1], items[0][2], "EXTERNAL_BACKUP_REQUIRED"))
            summary["unverified"] += 1
            summary["known_legacy_unverified_residual"] += 1
            continue
        is_pinned_seal = (
            relative == pinned_relative
            and any(item[0] == "oa_company_seal_config" and item[1] == "1" for item in items)
        )
        if not is_pinned_seal:
            unresolved.append((refs, "", urls, "UNVERIFIED_IN_SCOPE_NO_HASH"))
            summary["unverified"] += 1
            summary["blocking"] += 1
            continue
        if pinned_hash not in seal_cross_hashes:
            unresolved.append((refs, "", urls, "PINNED_CROSS_DB_HASH_MISSING"))
            summary["unverified"] += 1
            summary["blocking"] += 1
            continue
        if not pinned_source.is_file() or pinned_source.is_symlink():
            unresolved.append((refs, "", str(pinned_source), "PINNED_SOURCE_MISSING_OR_UNSAFE"))
            summary["missing_source"] += 1
            summary["blocking"] += 1
            continue
        if pinned_source.stat().st_size != pinned_size or digest(pinned_source) != pinned_hash:
            unresolved.append((refs, "", str(pinned_source), "PINNED_SOURCE_CONTENT_CONFLICT"))
            summary["metadata_conflict"] += 1
            summary["blocking"] += 1
            continue
        expected_hash = pinned_hash
        expected_size = pinned_size
        pinned = True
    else:
        expected_hash = next(iter(valid_hashes))
        expected_size = next(iter(sizes)) if sizes else None

    selected = None
    candidate_conflicts = []
    for root in candidates:
        candidate = root.joinpath(*pathlib.PurePosixPath(relative).parts)
        if not os.path.lexists(candidate):
            continue
        if not candidate.is_file() or candidate.is_symlink():
            observations.append((str(root), relative, "", "", "REJECTED_TYPE"))
            candidate_conflicts.append(str(candidate))
            continue
        actual_size = candidate.stat().st_size
        actual_hash = digest(candidate)
        result = "MATCH" if actual_hash == expected_hash else "REJECTED_HASH"
        if expected_size is not None and actual_size != expected_size:
            result = "REJECTED_SIZE"
        observations.append((str(root), relative, actual_size, actual_hash, result))
        if result == "MATCH" and selected is None:
            selected = (candidate, actual_size)
        elif result != "MATCH":
            candidate_conflicts.append(str(candidate))
    if candidate_conflicts:
        unresolved.append((refs, "", ";".join(candidate_conflicts), "CANDIDATE_CONTENT_CONFLICT"))
        summary["metadata_conflict"] += 1
        summary["blocking"] += 1
        continue

    target_path = target_root.joinpath(*pathlib.PurePosixPath(relative).parts)
    if unsafe_ancestor_in_target(relative):
        unresolved.append((refs, "", str(target_path), "TARGET_ANCESTOR_TYPE_CONFLICT"))
        summary["target_conflict"] += 1
        summary["blocking"] += 1
        continue
    if os.path.lexists(target_path):
        if not target_path.is_file() or target_path.is_symlink():
            unresolved.append((refs, "", str(target_path), "TARGET_TYPE_CONFLICT"))
            summary["target_conflict"] += 1
            summary["blocking"] += 1
            continue
        actual_size = target_path.stat().st_size
        actual_hash = digest(target_path)
        observations.append((str(target_root), relative, actual_size, actual_hash, "TARGET"))
        if actual_hash != expected_hash or (expected_size is not None and actual_size != expected_size):
            unresolved.append((refs, "", str(target_path), "TARGET_CONTENT_CONFLICT"))
            summary["target_conflict"] += 1
            summary["blocking"] += 1
            continue
        status = "PRESENT_PINNED_NO_DB_HASH_CROSS_VERIFIED" if pinned else "PRESENT"
        manifest_rows.append((relative, actual_size, expected_hash, str(target_path), status, refs))
        summary["present"] += 1
        if pinned:
            summary["pinned_no_db_hash_cross_verified"] += 1
        else:
            summary["db_hash_pass"] += 1
        continue

    if pinned:
        selected = (pinned_source, pinned_size)
    if selected is None:
        unresolved.append((refs, "", relative, "MISSING_VERIFIED_SOURCE"))
        summary["missing_source"] += 1
        summary["blocking"] += 1
        continue
    source, actual_size = selected
    status = "RESTORE_PINNED_NO_DB_HASH_CROSS_VERIFIED" if pinned else "RESTORE"
    manifest_rows.append((relative, actual_size, expected_hash, str(source), status, refs))
    summary["restore"] += 1
    if pinned:
        summary["pinned_no_db_hash_cross_verified"] += 1
    else:
        summary["db_hash_pass"] += 1

with manifest_path.open("w", encoding="utf-8") as handle:
    handle.write("relative_path\tfile_size\tsha256\tsource_path\tstatus\tdb_references\n")
    for row in manifest_rows:
        handle.write("\t".join(map(str, row)) + "\n")
with unresolved_path.open("w", encoding="utf-8") as handle:
    handle.write("source_kind\tsource_id\tvalue\treason\n")
    for row in unresolved:
        handle.write("\t".join(str(value).replace("\t", " ").replace("\n", " ") for value in row) + "\n")
with observations_path.open("w", encoding="utf-8") as handle:
    handle.write("candidate_root\trelative_path\tfile_size\tsha256\tresult\n")
    for row in observations:
        handle.write("\t".join(map(str, row)) + "\n")
summary_path.write_text(json.dumps(summary, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
PY
}

summary_value() {
  local summary_file="$1"
  local key="$2"
  python3 - "$summary_file" "$key" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8"))[sys.argv[2]])
PY
}

print_summary() {
  local report_dir="$1"
  python3 - "$report_dir/summary.json" <<'PY'
import json
import sys
s = json.load(open(sys.argv[1], encoding="utf-8"))
print("MANIFEST_SUMMARY " + " ".join(f"{key}={s[key]}" for key in (
    "db_references", "present", "restore", "missing_source", "target_conflict",
    "metadata_conflict", "unverified", "outside_scope", "excluded_sign_package",
    "db_hash_pass", "pinned_no_db_hash_cross_verified",
    "known_legacy_unverified_residual", "blocking")))
PY
}

private_fingerprint() {
  local output="$1"
  if [[ ! -d "$SIGN_PACKAGE_ROOT" || -L "$SIGN_PACKAGE_ROOT" ]]; then
    printf 'ABSENT_OR_UNSAFE\n' >"$output"
    return
  fi
  python3 - "$SIGN_PACKAGE_ROOT" "$output" <<'PY'
import hashlib
import os
import pathlib
import stat
import sys

root = pathlib.Path(sys.argv[1])
output = pathlib.Path(sys.argv[2])
aggregate = hashlib.sha256()
for current, directories, files in os.walk(root, topdown=True, followlinks=False):
    directories.sort()
    files.sort()
    for name in directories + files:
        path = pathlib.Path(current, name)
        relative = path.relative_to(root).as_posix()
        metadata = path.lstat()
        aggregate.update(relative.encode("utf-8") + b"\0")
        aggregate.update(oct(stat.S_IFMT(metadata.st_mode)).encode("ascii") + b"\0")
        if path.is_symlink():
            aggregate.update(b"LINK\0" + os.readlink(path).encode("utf-8") + b"\0")
        elif path.is_file():
            content = hashlib.sha256()
            with path.open("rb") as handle:
                for block in iter(lambda: handle.read(1024 * 1024), b""):
                    content.update(block)
            aggregate.update(str(metadata.st_size).encode("ascii") + b"\0" + content.digest())
        elif path.is_dir():
            aggregate.update(b"DIR")
        else:
            aggregate.update(b"OTHER")
        aggregate.update(b"\0")
output.write_text(aggregate.hexdigest() + "\n", encoding="ascii")
PY
}

scope_fingerprint() {
  local output="$1"
  python3 - "$TARGET_ROOT" "$output" <<'PY'
import hashlib
import os
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])
digest = hashlib.sha256()
for scope in ("public", "sign-template", "private/attendance", "private/reimbursement", "private/drive"):
    base = root / scope
    if not base.exists():
        digest.update(f"{scope}\0ABSENT\0".encode())
        continue
    if base.is_symlink() or not base.is_dir():
        raise SystemExit(f"unsafe managed storage scope: {base}")
    for path in sorted(base.rglob("*")):
        if path.is_symlink():
            raise SystemExit(f"symlink in managed storage scope: {path}")
        relative = path.relative_to(root).as_posix()
        digest.update(relative.encode("utf-8") + b"\0")
        if path.is_file():
            file_hash = hashlib.sha256()
            with path.open("rb") as handle:
                for block in iter(lambda: handle.read(1024 * 1024), b""):
                    file_hash.update(block)
            digest.update(str(path.stat().st_size).encode() + b"\0" + file_hash.digest())
        else:
            digest.update(b"DIR")
        digest.update(b"\0")
out.write_text(digest.hexdigest() + "\n", encoding="ascii")
PY
}

run_audit() {
  local report_dir="$1"
  mkdir -p "$report_dir"
  chmod 700 "$report_dir"
  load_db_credentials
  build_db_reference_file "$report_dir/db-references.tsv" "$report_dir/db-query.sql"
  check_no_inflight_loaded "$report_dir/inflight.tsv"
  discover_candidates "$report_dir/candidate-roots.txt"
  resolve_manifest "$report_dir/db-references.tsv" "$report_dir/candidate-roots.txt" "$report_dir"
  private_fingerprint "$report_dir/private-sign-package.fingerprint"
  unset MYSQL_PWD DB_PASSWORD
  print_summary "$report_dir"
  log "audit report=$report_dir"
}

ensure_apply_preconditions() {
  [[ "$TEST_MODE" == "1" || "$(id -u)" == "0" ]] || die "apply/rollback must run as root"
  [[ -d "$TARGET_ROOT" && ! -L "$TARGET_ROOT" ]] || die "target root must already be a real directory: $TARGET_ROOT"
  [[ -d "$TARGET_ROOT/private" && ! -L "$TARGET_ROOT/private" ]] \
    || die "target private root is missing or unsafe: $TARGET_ROOT/private"
  [[ -d "$SIGN_PACKAGE_ROOT" && ! -L "$SIGN_PACKAGE_ROOT" ]] || die "protected sign-package root is missing or unsafe: $SIGN_PACKAGE_ROOT"
  local relative path
  for relative in "${MANAGED_STORAGE_RELATIVES[@]}"; do
    path="$TARGET_ROOT/$relative"
    [[ ! -L "$path" ]] || die "managed storage path may not be a symlink: $path"
    [[ ! -e "$path" || -d "$path" ]] || die "managed storage path has the wrong type: $path"
  done
  if [[ -n "$OA_BEARER_TOKEN" && ! "$OA_BEARER_TOKEN" =~ ^[A-Za-z0-9._~-]+$ ]]; then
    die "ERP_REPAIR_OA_BEARER_TOKEN is present but contains unsafe characters"
  fi
  systemctl is-active --quiet "$FILE_SERVICE" || die "$FILE_SERVICE must be active before apply"
  systemctl is-active --quiet "$OA_SERVICE" || die "$OA_SERVICE must be active before apply"
}

ensure_rollback_preconditions() {
  [[ "$TEST_MODE" == "1" || "$(id -u)" == "0" ]] || die "apply/rollback must run as root"
  [[ -d "$TARGET_ROOT" && ! -L "$TARGET_ROOT" ]] || die "rollback target root is missing or unsafe: $TARGET_ROOT"
  [[ -d "$TARGET_ROOT/private" && ! -L "$TARGET_ROOT/private" ]] \
    || die "rollback private root is missing or unsafe: $TARGET_ROOT/private"
  [[ -d "$SIGN_PACKAGE_ROOT" && ! -L "$SIGN_PACKAGE_ROOT" ]] \
    || die "protected sign-package root is missing or unsafe: $SIGN_PACKAGE_ROOT"
}

render_desired_env() {
  local source="$1"
  local target="$2"
  python3 - "$source" "$target" "$TARGET_ROOT" <<'PY'
import pathlib
import sys

source, target, root = map(pathlib.Path, sys.argv[1:])
root_text = str(root)
desired = {
    "ERP_UPLOAD_ROOT": root_text,
    "FILE_PATH": root_text,
    "SIGN_PACKAGE_STORAGE_ROOT": root_text + "/private/sign-package",
    "OA_ATTENDANCE_STORAGE_ROOT": root_text + "/private/attendance",
    "OA_REIMBURSEMENT_STORAGE_ROOT": root_text + "/private/reimbursement",
    "DRIVE_LOCAL_PATH": root_text + "/private/drive",
    "OA_SIGN_EXCEL_IMPORT_ENABLED": "true",
    "VUE_APP_SIGN_EXCEL_IMPORT_ENABLED": "true",
}
lines = source.read_text(encoding="utf-8").splitlines()
seen = set()
output = []
for line in lines:
    key = line.split("=", 1)[0] if "=" in line and not line.startswith("#") else None
    if key in desired:
        if key in seen:
            raise SystemExit(f"duplicate dotenv key: {key}")
        output.append(f"{key}={desired[key]}")
        seen.add(key)
    else:
        output.append(line)
for key, value in desired.items():
    if key not in seen:
        output.append(f"{key}={value}")
target.write_text("\n".join(output) + "\n", encoding="utf-8")
PY
}

render_systemd_guard() {
  local service="$1"
  local output="$2"
  {
    printf '%s\n' '[Unit]' "RequiresMountsFor=$TARGET_ROOT" "ConditionPathIsDirectory=$TARGET_ROOT" '[Service]'
    printf 'ExecStartPre=/usr/bin/test -d %s\n' "$TARGET_ROOT"
    printf 'ExecStartPre=/usr/bin/test -w %s\n' "$TARGET_ROOT"
    if [[ "$service" == "$OA_SERVICE" ]]; then
      printf 'ExecStartPre=/usr/bin/test -d %s\n' "$SIGN_PACKAGE_ROOT"
    fi
  } >"$output"
}

backup_config_and_directories() {
  local backup="$1"
  mkdir -p "$backup/config" "$backup/systemd" "$backup/target-scope" "$backup/compat"
  chmod 700 "$backup"
  cp -a "$ENV_FILE" "$backup/config/env.before"
  sha256sum "$backup/config/env.before" >"$backup/config/env.before.sha256"
  render_desired_env "$backup/config/env.before" "$backup/config/env.planned"
  chown --reference="$backup/config/env.before" "$backup/config/env.planned"
  chmod --reference="$backup/config/env.before" "$backup/config/env.planned"
  printf '%s\n' "$RELEASE_ROOT" >"$backup/config/release-root"
  printf '%s\n' "$TARGET_ROOT" >"$backup/config/target-root"

  local service unit_name dropin copy_name state parent_state
  : >"$backup/systemd/dropins.tsv"
  for service in "$FILE_SERVICE" "$OA_SERVICE"; do
    unit_name="${service%.service}"
    systemctl cat "$service" >"$backup/systemd/${unit_name}.cat.before"
    systemctl show "$service" -p FragmentPath -p DropInPaths -p ExecStart -p WorkingDirectory \
      >"$backup/systemd/${unit_name}.show.before"
    dropin="$SYSTEMD_ROOT/${service}.d/20-common-upload-root.conf"
    if [[ -d "$(dirname "$dropin")" && ! -L "$(dirname "$dropin")" ]]; then
      parent_state="DIRECTORY"
    elif [[ ! -e "$(dirname "$dropin")" && ! -L "$(dirname "$dropin")" ]]; then
      parent_state="ABSENT"
    else
      die "unsafe systemd drop-in directory: $(dirname "$dropin")"
    fi
    copy_name="$backup/systemd/${unit_name}.dropin.before"
    if [[ -e "$dropin" ]]; then
      [[ -f "$dropin" && ! -L "$dropin" ]] || die "unsafe existing systemd drop-in: $dropin"
      cp -a "$dropin" "$copy_name"
      state="FILE"
    else
      state="ABSENT"
      copy_name="-"
    fi
    printf '%s\t%s\t%s\t%s\t%s\n' \
      "$service" "$dropin" "$state" "$copy_name" "$parent_state" >>"$backup/systemd/dropins.tsv"
    render_systemd_guard "$service" "$backup/systemd/${unit_name}.dropin.planned"
    chmod 0644 "$backup/systemd/${unit_name}.dropin.planned"
  done

  : >"$backup/target-scope/state.tsv"
  : >"$backup/created-directories.tsv"
  : >"$backup/temporary-paths.tsv"
  local relative path destination safe_name
  for relative in "${MANAGED_STORAGE_RELATIVES[@]}"; do
    path="$TARGET_ROOT/$relative"
    safe_name="${relative//\//__}"
    destination="$backup/target-scope/$safe_name.before"
    if [[ -d "$path" && ! -L "$path" ]]; then
      if [[ "$relative" == "public" || "$relative" == "sign-template" ]]; then
        cp -a --reflink=auto "$path" "$destination"
      else
        destination="-"
      fi
      printf '%s\tDIRECTORY\t%s\n' "$relative" "$destination" >>"$backup/target-scope/state.tsv"
    else
      [[ ! -e "$path" && ! -L "$path" ]] || die "unsafe managed storage path during backup: $path"
      printf '%s\tABSENT\t-\n' "$relative" >>"$backup/target-scope/state.tsv"
    fi
  done
}

record_release_state() {
  local backup="$1"
  local kind literal resolved inode
  mkdir -p "$backup/config"
  if [[ -L "$CURRENT_LINK" ]]; then
    kind="SYMLINK"
    literal="$(readlink "$CURRENT_LINK")"
    [[ -n "$literal" && "$literal" != *$'\t'* && "$literal" != *$'\n'* ]] \
      || die "active release link has an unsafe literal target"
  elif [[ -d "$CURRENT_LINK" ]]; then
    kind="DIRECTORY"
    literal="-"
  else
    die "active release link changed to an unsafe type"
  fi
  resolved="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
  [[ "$resolved" == "$RELEASE_ROOT" && -d "$resolved" && ! -L "$resolved" ]] \
    || die "active release changed while its state was being recorded"
  inode="$(path_device_inode "$resolved")"
  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$CURRENT_LINK" "$kind" "$literal" "$resolved" "$inode" \
    >"$backup/config/release-state.tsv"
}

assert_release_unchanged() {
  local backup="$1"
  local recorded_link kind literal resolved inode extra=""
  local actual_kind actual_literal="-" actual_resolved actual_inode reason=""
  [[ -f "$backup/config/release-state.tsv" \
      && ! -L "$backup/config/release-state.tsv" ]] \
    || die "sealed active-release state is missing or unsafe"
  IFS=$'\t' read -r recorded_link kind literal resolved inode \
    <"$backup/config/release-state.tsv" \
    || die "cannot read sealed active-release state"
  IFS= read -r extra < <(sed -n '2p' "$backup/config/release-state.tsv") || true
  [[ -z "$extra" && "$recorded_link" == "$CURRENT_LINK" \
      && "$resolved" == "$RELEASE_ROOT" && "$inode" =~ ^[0-9]+:[0-9]+$ ]] \
    || reason="sealed_release_state_invalid"
  if [[ -z "$reason" ]]; then
    if [[ -L "$CURRENT_LINK" ]]; then
      actual_kind="SYMLINK"
      actual_literal="$(readlink "$CURRENT_LINK")"
    elif [[ -d "$CURRENT_LINK" ]]; then
      actual_kind="DIRECTORY"
    else
      actual_kind="UNSAFE_OR_ABSENT"
    fi
    actual_resolved="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
    if [[ -n "$actual_resolved" && -d "$actual_resolved" ]]; then
      actual_inode="$(path_device_inode "$actual_resolved")"
    else
      actual_inode="-"
    fi
    [[ "$actual_kind" == "$kind" && "$actual_literal" == "$literal" \
        && "$actual_resolved" == "$resolved" && "$actual_inode" == "$inode" ]] \
      || reason="active_release_link_or_inode_drift"
  fi
  if [[ -n "$reason" ]]; then
    mark_manual_intervention "$backup" "$reason"
    die "active release changed; preserving the unified root for manual intervention"
  fi
}

write_phase() {
  local backup="$1"
  local phase="$2"
  local current=""
  local temp="$backup/PHASE.tmp.$$"
  if [[ -e "$backup/PHASE" || -L "$backup/PHASE" ]]; then
    [[ -f "$backup/PHASE" && ! -L "$backup/PHASE" ]] \
      || die "phase marker became unsafe"
    current="$(<"$backup/PHASE")"
  fi
  case "$current:$phase" in
    :BACKUP_SEALED|BACKUP_SEALED:STOPPING_SERVICES|STOPPING_SERVICES:SERVICES_STOPPED|\
    SERVICES_STOPPED:DB_BACKUP_SEALED|DB_BACKUP_SEALED:MUTATION_STARTED|\
    MUTATION_STARTED:MUTATION_APPLIED|MUTATION_APPLIED:SERVICES_STARTING|\
    SERVICES_STARTING:APPLY_COMPLETE|BACKUP_SEALED:ROLLED_BACK|\
    STOPPING_SERVICES:ROLLED_BACK|SERVICES_STOPPED:ROLLED_BACK|\
    DB_BACKUP_SEALED:ROLLED_BACK|MUTATION_STARTED:ROLLED_BACK|\
    MUTATION_APPLIED:ROLLED_BACK|SERVICES_STARTING:ROLLED_BACK|\
    APPLY_COMPLETE:ROLLED_BACK) ;;
    *) die "non-monotonic repair phase transition: ${current:-ABSENT} -> $phase" ;;
  esac
  printf '%s\n' "$phase" >"$temp"
  chmod 0600 "$temp"
  mv -f -- "$temp" "$backup/PHASE"
  sync
  if [[ "$DYNAMIC_SEAL_ENABLED" == "1" ]]; then
    seal_rollback_state "$backup" "DYNAMIC_ROLLBACK_STATE.sha256" dynamic
  fi
}

seal_pre_stop_bundle() {
  local backup="$1"
  python3 - "$backup" <<'PY'
import base64
import hashlib
import os
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
manifest = root / "PRE_STOP_BUNDLE.sha256"
roots = [root / name for name in ("config", "systemd", "target-scope", "compat", "preflight")]
files = [root / name for name in ("REPAIR_ID", "RELEASE_ROOT", "TARGET_ROOT", "APPLY_STARTED")]
for directory in roots:
    if not directory.is_dir() or directory.is_symlink():
        raise SystemExit(f"unsafe pre-stop backup directory: {directory}")
    for path in directory.rglob("*"):
        if path.is_symlink():
            raise SystemExit(f"symlink is not allowed in pre-stop backup: {path}")
        if path.is_file():
            files.append(path)
for path in files:
    if not path.is_file() or path.is_symlink():
        raise SystemExit(f"missing pre-stop backup file: {path}")
rows = []
for path in sorted(set(files), key=lambda item: item.relative_to(root).as_posix()):
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    encoded = base64.b64encode(path.relative_to(root).as_posix().encode("utf-8")).decode("ascii")
    rows.append(f"{digest}\t{encoded}")
temporary = root / f"PRE_STOP_BUNDLE.sha256.tmp.{os.getpid()}"
with temporary.open("w", encoding="ascii") as handle:
    handle.write("\n".join(rows) + "\n")
    handle.flush()
    os.fsync(handle.fileno())
os.replace(temporary, manifest)
directory_fd = os.open(root, os.O_RDONLY)
try:
    os.fsync(directory_fd)
finally:
    os.close(directory_fd)
PY
  chmod 0600 "$backup/PRE_STOP_BUNDLE.sha256"
}

validate_backup_bundle() {
  local backup="$1"
  [[ -f "$backup/PRE_STOP_BUNDLE.sha256" && ! -L "$backup/PRE_STOP_BUNDLE.sha256" ]] \
    || { mark_manual_intervention "$backup" "pre_stop_bundle_missing_or_unsafe"; \
         die "pre-stop backup checksum manifest is missing"; }
  if ! python3 - "$backup" <<'PY'
import base64
import hashlib
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
manifest = root / "PRE_STOP_BUNDLE.sha256"
seen = set()
for number, raw in enumerate(manifest.read_text(encoding="ascii").splitlines(), 1):
    fields = raw.split("\t")
    if len(fields) != 2 or len(fields[0]) != 64:
        raise SystemExit(f"malformed pre-stop checksum row {number}")
    try:
        relative = base64.b64decode(fields[1], validate=True).decode("utf-8")
    except Exception as exc:
        raise SystemExit(f"malformed pre-stop checksum path at row {number}") from exc
    candidate = pathlib.PurePosixPath(relative)
    if candidate.is_absolute() or not candidate.parts or any(part in ("", ".", "..") for part in candidate.parts):
        raise SystemExit(f"unsafe pre-stop checksum path at row {number}")
    if relative in seen:
        raise SystemExit(f"duplicate pre-stop checksum path: {relative}")
    seen.add(relative)
    path = root.joinpath(*candidate.parts)
    if not path.is_file() or path.is_symlink():
        raise SystemExit(f"pre-stop backup file missing or unsafe: {relative}")
    if hashlib.sha256(path.read_bytes()).hexdigest() != fields[0]:
        raise SystemExit(f"pre-stop backup checksum mismatch: {relative}")
required = {
    "config/env.before",
    "config/env.before.sha256",
    "config/env.planned",
    "config/release-state.tsv",
    "systemd/dropins.tsv",
    "systemd/erp-new@file.dropin.planned",
    "systemd/erp-new@oa.dropin.planned",
    "target-scope/state.tsv",
    "compat/links.tsv",
    "preflight/manifest.tsv",
    "preflight/summary.json",
    "REPAIR_ID",
    "RELEASE_ROOT",
    "TARGET_ROOT",
    "APPLY_STARTED",
}
missing = required - seen
if missing:
    raise SystemExit(f"pre-stop backup checksum coverage is incomplete: {sorted(missing)}")
PY
  then
    mark_manual_intervention "$backup" "pre_stop_bundle_validation_failed"
    die "pre-stop backup checksum validation failed"
  fi
  sha256sum -c "$backup/config/env.before.sha256" >/dev/null \
    || { mark_manual_intervention "$backup" "environment_backup_checksum_mismatch"; \
         die "environment backup checksum mismatch"; }
}

seal_rollback_state() {
  local backup="$1"
  local manifest_name="$2"
  local mode="$3"
  if ! python3 - "$backup" "$manifest_name" "$mode" <<'PY'
import base64
import hashlib
import os
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
manifest_name = sys.argv[2]
mode = sys.argv[3]
manifest = root / manifest_name

dynamic_names = (
    "PHASE", "created-directories.tsv", "temporary-paths.tsv",
    "file-intents.tsv", "inflight.after-stop.tsv",
    "private-sign-package.before-apply.fingerprint",
    "private-sign-package.after-mutation.fingerprint",
    "scope.before-service-start.fingerprint", "OA_BUSINESS_POSTCHECK",
)
if mode == "dynamic":
    files = [root / name for name in dynamic_names if (root / name).is_file()]
    database = root / "database"
    if database.is_dir() and not database.is_symlink():
        files.extend(path for path in database.iterdir() if path.is_file() and not path.is_symlink())
    required = {"PHASE", "created-directories.tsv", "temporary-paths.tsv"}
elif mode == "database":
    names = (
        "database/tables.txt", "database/signing-before.sql.gz",
        "database/signing-before.sql.gz.sha256",
    )
    files = [root / name for name in names]
    required = set(names)
elif mode == "mutation":
    names = (
        "created-directories.tsv", "temporary-paths.tsv", "file-intents.tsv",
        "applied-files.tsv", "private-sign-package.before-apply.fingerprint",
        "private-sign-package.after-mutation.fingerprint",
        "scope.before-service-start.fingerprint", "MUTATION_EXPECTED_PHASE",
        "config/env.after", "systemd/erp-new@file.dropin.after",
        "systemd/erp-new@oa.dropin.after",
        "database/tables.txt",
        "database/signing-before.sql.gz", "database/signing-before.sql.gz.sha256",
        "DB_BACKUP.sha256",
    )
    files = [root / name for name in names]
    required = set(names)
elif mode == "complete":
    names = (
        "PHASE", "DYNAMIC_ROLLBACK_STATE.sha256",
        "APPLY_STARTED", "APPLY_COMPLETE", "POST_APPLY_EXPECTED_PHASE",
        "MUTATION_ROLLBACK.sha256",
        "created-directories.tsv", "temporary-paths.tsv", "file-intents.tsv",
        "applied-files.tsv", "scope.before-service-start.fingerprint",
        "scope.after-apply.fingerprint", "private-sign-package.before-apply.fingerprint",
        "private-sign-package.after-mutation.fingerprint", "OA_BUSINESS_POSTCHECK",
        "config/env.after", "systemd/erp-new@file.dropin.after",
        "systemd/erp-new@oa.dropin.after",
        "postcheck/manifest.tsv", "postcheck/summary.json",
        "database/tables.txt", "database/signing-before.sql.gz",
        "database/signing-before.sql.gz.sha256", "DB_BACKUP.sha256",
        "config/release-state.tsv",
    )
    files = [root / name for name in names]
    required = set(names)
else:
    raise SystemExit(f"unknown rollback-state manifest mode: {mode}")

rows = []
seen = set()
for path in files:
    if not path.is_file() or path.is_symlink():
        raise SystemExit(f"missing or unsafe rollback-state file: {path.relative_to(root)}")
    relative = path.relative_to(root).as_posix()
    seen.add(relative)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    encoded = base64.b64encode(relative.encode("utf-8")).decode("ascii")
    rows.append(f"{digest}\t{encoded}")
missing = required - seen
if missing:
    raise SystemExit(f"rollback-state checksum coverage is incomplete: {sorted(missing)}")
payload = "\n".join(sorted(rows)) + "\n"
temporary = root / f"{manifest_name}.tmp.{os.getpid()}"
with temporary.open("w", encoding="ascii") as handle:
    handle.write(payload)
    handle.flush()
    os.fsync(handle.fileno())
os.replace(temporary, manifest)
os.chmod(manifest, 0o600)
directory_fd = os.open(root, os.O_RDONLY)
try:
    os.fsync(directory_fd)
finally:
    os.close(directory_fd)
PY
  then
    mark_manual_intervention "$backup" "${manifest_name}_validation_failed"
    die "$manifest_name validation failed"
  fi
}

validate_rollback_state_manifest() {
  local backup="$1"
  local manifest_name="$2"
  local mode="$3"
  [[ -f "$backup/$manifest_name" && ! -L "$backup/$manifest_name" ]] \
    || { mark_manual_intervention "$backup" "${manifest_name}_missing_or_unsafe"; \
         die "$manifest_name is missing or unsafe"; }
  if ! python3 - "$backup" "$manifest_name" "$mode" <<'PY'
import base64
import hashlib
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
manifest_name = sys.argv[2]
mode = sys.argv[3]
manifest = root / manifest_name
required_by_mode = {
    "dynamic": {"PHASE", "created-directories.tsv", "temporary-paths.tsv"},
    "database": {
        "database/tables.txt", "database/signing-before.sql.gz",
        "database/signing-before.sql.gz.sha256",
    },
    "mutation": {
        "created-directories.tsv", "temporary-paths.tsv", "file-intents.tsv",
        "applied-files.tsv", "private-sign-package.before-apply.fingerprint",
        "private-sign-package.after-mutation.fingerprint",
        "scope.before-service-start.fingerprint", "MUTATION_EXPECTED_PHASE",
        "config/env.after", "systemd/erp-new@file.dropin.after",
        "systemd/erp-new@oa.dropin.after",
        "database/tables.txt",
        "database/signing-before.sql.gz", "database/signing-before.sql.gz.sha256",
        "DB_BACKUP.sha256",
    },
    "complete": {
        "PHASE", "DYNAMIC_ROLLBACK_STATE.sha256",
        "APPLY_STARTED", "APPLY_COMPLETE", "POST_APPLY_EXPECTED_PHASE",
        "MUTATION_ROLLBACK.sha256",
        "created-directories.tsv", "temporary-paths.tsv", "file-intents.tsv",
        "applied-files.tsv", "scope.before-service-start.fingerprint",
        "scope.after-apply.fingerprint", "private-sign-package.before-apply.fingerprint",
        "private-sign-package.after-mutation.fingerprint", "OA_BUSINESS_POSTCHECK",
        "config/env.after", "systemd/erp-new@file.dropin.after",
        "systemd/erp-new@oa.dropin.after",
        "postcheck/manifest.tsv", "postcheck/summary.json", "database/tables.txt",
        "database/signing-before.sql.gz", "database/signing-before.sql.gz.sha256",
        "DB_BACKUP.sha256", "config/release-state.tsv",
    },
}
required = required_by_mode.get(mode)
if required is None:
    raise SystemExit(f"unknown rollback-state validation mode: {mode}")
seen = set()
for number, raw in enumerate(manifest.read_text(encoding="ascii").splitlines(), 1):
    fields = raw.split("\t")
    if len(fields) != 2 or len(fields[0]) != 64:
        raise SystemExit(f"malformed {manifest_name} row {number}")
    try:
        relative = base64.b64decode(fields[1], validate=True).decode("utf-8")
    except Exception as exc:
        raise SystemExit(f"malformed {manifest_name} path row {number}") from exc
    pure = pathlib.PurePosixPath(relative)
    if pure.is_absolute() or not pure.parts or any(part in ("", ".", "..") for part in pure.parts):
        raise SystemExit(f"unsafe {manifest_name} path row {number}")
    if relative in seen:
        raise SystemExit(f"duplicate {manifest_name} path: {relative}")
    seen.add(relative)
    path = root.joinpath(*pure.parts)
    if not path.is_file() or path.is_symlink():
        raise SystemExit(f"sealed rollback-state file missing or unsafe: {relative}")
    if hashlib.sha256(path.read_bytes()).hexdigest() != fields[0]:
        raise SystemExit(f"sealed rollback-state checksum mismatch: {relative}")
missing = required - seen
if missing:
    raise SystemExit(f"sealed rollback-state coverage is incomplete: {sorted(missing)}")
PY
  then
    mark_manual_intervention "$backup" "${manifest_name}_validation_failed"
    die "$manifest_name validation failed"
  fi
  case "$mode" in
    mutation)
      [[ "$(<"$backup/MUTATION_EXPECTED_PHASE")" == "MUTATION_APPLIED" ]] \
        || die "mutation rollback seal has the wrong expected phase"
      ;;
    complete)
      [[ "$(<"$backup/POST_APPLY_EXPECTED_PHASE")" == "APPLY_COMPLETE" ]] \
        || die "post-apply rollback seal has the wrong expected phase"
      ;;
  esac
  if [[ "$mode" != "dynamic" && -f "$backup/database/signing-before.sql.gz" ]]; then
    sha256sum -c "$backup/database/signing-before.sql.gz.sha256" >/dev/null \
      || { mark_manual_intervention "$backup" "database_dump_checksum_mismatch"; \
          die "signing database backup checksum mismatch in rollback-state seal"; }
    gzip -t "$backup/database/signing-before.sql.gz" \
      || { mark_manual_intervention "$backup" "database_dump_gzip_invalid"; \
          die "signing database backup gzip is corrupt in rollback-state seal"; }
  fi
}

assert_checkpoint_seal_consistency() {
  local backup="$1"
  local phase="$2"
  local reason=""
  local db="$backup/DB_BACKUP.sha256"
  local mutation="$backup/MUTATION_ROLLBACK.sha256"
  local complete="$backup/POST_APPLY_ROLLBACK_STATE.sha256"
  local complete_marker="$backup/APPLY_COMPLETE"
  local complete_expected="$backup/POST_APPLY_EXPECTED_PHASE"
  local dynamic="$backup/DYNAMIC_ROLLBACK_STATE.sha256"

  [[ -f "$dynamic" && ! -L "$dynamic" ]] \
    || reason="dynamic_phase_seal_missing_or_unsafe"
  if [[ -z "$reason" ]]; then
    case "$phase" in
      BACKUP_SEALED|STOPPING_SERVICES|SERVICES_STOPPED)
        if [[ -e "$db" || -L "$db" || -e "$mutation" || -L "$mutation" \
            || -e "$complete" || -L "$complete" \
            || -e "$complete_marker" || -L "$complete_marker" \
            || -e "$complete_expected" || -L "$complete_expected" ]]; then
          reason="later_checkpoint_exists_for_pre_database_phase"
        fi
        ;;
      DB_BACKUP_SEALED|MUTATION_STARTED)
        if [[ ! -f "$db" || -L "$db" ]]; then
          reason="database_checkpoint_missing_for_phase"
        elif [[ -e "$mutation" || -L "$mutation" \
            || -e "$complete" || -L "$complete" \
            || -e "$complete_marker" || -L "$complete_marker" \
            || -e "$complete_expected" || -L "$complete_expected" ]]; then
          reason="later_checkpoint_exists_for_pre_mutation_phase"
        fi
        ;;
      MUTATION_APPLIED|SERVICES_STARTING)
        if [[ ! -f "$db" || -L "$db" || ! -f "$mutation" || -L "$mutation" ]]; then
          reason="mutation_checkpoint_missing_for_phase"
        elif [[ -e "$complete" || -L "$complete" \
            || -e "$complete_marker" || -L "$complete_marker" \
            || -e "$complete_expected" || -L "$complete_expected" ]]; then
          reason="final_checkpoint_exists_before_apply_complete_phase"
        fi
        ;;
      APPLY_COMPLETE)
        if [[ ! -f "$db" || -L "$db" || ! -f "$mutation" || -L "$mutation" \
            || ! -f "$complete" || -L "$complete" \
            || ! -f "$complete_marker" || -L "$complete_marker" \
            || ! -f "$complete_expected" || -L "$complete_expected" ]]; then
          reason="complete_checkpoint_missing_or_unsafe"
        fi
        ;;
      *) reason="unknown_phase_for_checkpoint_validation" ;;
    esac
  fi
  if [[ -n "$reason" ]]; then
    mark_manual_intervention "$backup" "$reason"
    die "repair phase and sealed checkpoints are inconsistent"
  fi
}

append_journal_event() {
  local backup="$1"
  local journal="$2"
  shift 2
  local value row
  for value in "$@"; do
    [[ "$value" != *$'\t'* && "$value" != *$'\n'* ]] \
      || die "unsafe character in rollback journal event"
  done
  local IFS=$'\t'
  row="$*"
  printf '%s\n' "$row" >>"$journal"
  sync
  if [[ "$DYNAMIC_SEAL_ENABLED" == "1" ]]; then
    seal_rollback_state "$backup" "DYNAMIC_ROLLBACK_STATE.sha256" dynamic
  fi
}

backup_signing_database() {
  local backup="$1"
  local table_file="$backup/database/tables.txt"
  local dump_file="$backup/database/signing-before.sql.gz"
  local dump_partial="$backup/database/signing-before.sql.gz.partial.$$"
  local table
  local dump_help dump_version
  local table_count=0
  local -a tables=()
  local -a dump_options=(
    --single-transaction
    --quick
    --hex-blob
    --skip-lock-tables
    --default-character-set=utf8mb4
  )
  mkdir -p "$backup/database"
  load_db_credentials
  mysql_to_file \
    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND (LEFT(TABLE_NAME,8)='oa_sign_' OR TABLE_NAME IN ('oa_company_seal_config','oa_labor_contract','oa_labor_contract_template','oa_labor_contract_event')) ORDER BY TABLE_NAME" \
    "$table_file"
  while IFS= read -r table; do
    [[ "$table" =~ ^[a-z0-9_]+$ ]] || die "unsafe table name returned for backup"
    tables+=("$table")
    ((table_count += 1))
  done <"$table_file"
  ((table_count > 0)) || die "no signing tables were discovered for backup"
  for table in \
    oa_sign_task oa_sign_package oa_sign_package_document oa_sign_file_evidence \
    oa_sign_template oa_sign_plan oa_sign_plan_template oa_sign_plan_version \
    oa_sign_plan_version_template oa_sign_final_confirmation \
    oa_sign_final_confirmation_document oa_sign_event oa_sign_task_event \
    oa_company_seal_config oa_sign_onboard_import_batch oa_sign_onboard_import_row \
    oa_sign_file_cleanup oa_sign_notification_outbox oa_sign_task_hard_delete_operation; do
    grep -Fqx "$table" "$table_file" || die "required signing backup table is missing: $table"
  done

  dump_version="$(mysqldump --version 2>&1)" || die "cannot identify mysqldump client"
  dump_help="$(mysqldump --help 2>&1)" || die "cannot inspect mysqldump capabilities"
  if grep -Fq -- '--no-tablespaces' <<<"$dump_help"; then
    dump_options+=(--no-tablespaces)
  elif ! grep -qi 'mariadb' <<<"$dump_version"; then
    die "mysqldump lacks --no-tablespaces and is not an identified MariaDB client"
  fi
  if grep -Fq -- '--set-gtid-purged' <<<"$dump_help"; then
    dump_options+=(--set-gtid-purged=OFF)
  elif grep -qi 'mariadb' <<<"$dump_version"; then
    # MariaDB's client does not emit MySQL SET @@GLOBAL.GTID_PURGED unless its
    # separate --gtid option is requested.  Omitting --gtid is the safe
    # equivalent of MySQL --set-gtid-purged=OFF.
    :
  else
    die "mysqldump cannot safely disable GTID_PURGED output"
  fi

  # MYSQL_PWD is inherited only by the two client processes.  It is never
  # included in argv, stdout, the SQL dump, or the backup manifest.
  mysqldump "${dump_options[@]}" \
    --user "$DB_USER" "$DB_NAME" "${tables[@]}" | gzip -1 >"$dump_partial"
  chmod 0600 "$dump_partial"
  gzip -t "$dump_partial"
  [[ -s "$dump_partial" ]] || die "signing database backup is empty"
  mv -f -- "$dump_partial" "$dump_file"
  chmod 0600 "$dump_file"
  test_failpoint db_after_dump_before_checksum
  sha256sum "$dump_file" >"$dump_file.sha256"
  sync
  unset MYSQL_PWD DB_PASSWORD
}

record_compatibility_state() {
  local backup="$1"
  local stamp="$2"
  local state_file="$backup/compat/links.tsv"
  local index=0 path state link_target sidecar copy
  declare -a paths=(
    "$RELEASE_ROOT/uploadPath"
    "$RELEASE_ROOT/erp/uploadPath"
    "/opt/erp-new-data/uploadPath"
  )
  if [[ "$TEST_MODE" == "1" ]]; then
    paths=(
      "$RELEASE_ROOT/uploadPath"
      "$RELEASE_ROOT/erp/uploadPath"
      "${ERP_COMMON_UPLOAD_REPAIR_LEGACY_ROOT:?test legacy root is required}"
    )
  fi
  : >"$state_file"
  for path in "${paths[@]}"; do
    ((index += 1))
    sidecar="${path}.pre-${SCRIPT_ID}-${stamp}"
    copy="$backup/compat/path-${index}.before"
    [[ ! -e "$sidecar" && ! -L "$sidecar" ]] || die "compatibility sidecar already exists: $sidecar"
    if [[ -L "$path" ]]; then
      # Preserve the literal link text, including a relative target.  The
      # production baseline legitimately points erp/uploadPath at the old
      # /opt root, so a non-TARGET_ROOT symlink is migration input, not an
      # automatic conflict.
      link_target="$(readlink "$path")"
      [[ -n "$link_target" && "$link_target" != *$'\t'* && "$link_target" != *$'\n'* ]] \
        || die "unsafe compatibility symlink target: $path"
      state="SYMLINK"
      sidecar="-"
      copy="-"
    elif [[ -d "$path" ]]; then
      state="DIRECTORY"
      link_target="-"
      cp -a --reflink=auto "$path" "$copy"
    elif [[ ! -e "$path" ]]; then
      state="ABSENT"
      link_target="-"
      sidecar="-"
      copy="-"
    else
      die "unsupported compatibility path type: $path"
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$index" "$path" "$state" "$link_target" "$sidecar" "$copy" >>"$state_file"
  done
}

path_device_inode() {
  stat -c '%d:%i' -- "$1"
}

create_tracked_directory() {
  local path="$1"
  local journal="$2"
  local backup inode
  if [[ -e "$path" || -L "$path" ]]; then
    [[ -d "$path" && ! -L "$path" ]] || die "managed directory has an unsafe type: $path"
    return 0
  fi
  backup="$(dirname "$journal")"
  append_journal_event "$backup" "$journal" PENDING "$path" -
  test_failpoint created_directory_after_intent
  if mkdir -- "$path"; then
    inode="$(path_device_inode "$path")"
    append_journal_event "$backup" "$journal" APPLIED "$path" "$inode"
  elif [[ -d "$path" && ! -L "$path" ]]; then
    # A concurrent creator is not attributed to this script and therefore is
    # never removed by rollback.
    append_journal_event "$backup" "$journal" SKIPPED "$path" -
    return 0
  else
    die "failed to create managed directory: $path"
  fi
}

ensure_target_parent_directories() {
  local target="$1"
  local journal="$2"
  local relative scope_root current index missing_count=0
  local -a missing=()
  relative="${target#"$TARGET_ROOT/"}"
  case "$relative" in
    public/*) scope_root="$TARGET_ROOT/public" ;;
    sign-template/*) scope_root="$TARGET_ROOT/sign-template" ;;
    *) die "target parent request escaped repair scope: $target" ;;
  esac
  current="$(dirname "$target")"
  while :; do
    [[ "$current" == "$scope_root" || "$current" == "$scope_root"/* ]] \
      || die "target parent escaped managed scope: $current"
    if [[ -e "$current" || -L "$current" ]]; then
      [[ -d "$current" && ! -L "$current" ]] || die "unsafe target parent: $current"
      break
    fi
    missing+=("$current")
    ((missing_count += 1))
    [[ "$current" != "$scope_root" ]] || break
    current="$(dirname "$current")"
  done
  for ((index=missing_count - 1; index >= 0; index--)); do
    create_tracked_directory "${missing[$index]}" "$journal"
  done
}

ensure_required_storage_directories() {
  local backup="$1"
  local relative path
  for relative in private/attendance private/reimbursement private/drive; do
    path="$TARGET_ROOT/$relative"
    create_tracked_directory "$path" "$backup/created-directories.tsv"
    [[ -d "$path" && ! -L "$path" ]] || die "required storage directory is unsafe: $path"
  done
  test_failpoint storage_directories
}

verify_required_storage_directories() {
  local relative path
  for relative in private/attendance private/reimbursement private/drive; do
    path="$TARGET_ROOT/$relative"
    [[ -d "$path" && ! -L "$path" ]] || die "required storage directory is missing or unsafe: $path"
  done
}

create_tracked_file_temp() {
  local target="$1"
  local journal="$2"
  local backup temp inode attempt=0
  backup="$(dirname "$journal")"
  while :; do
    ((attempt += 1))
    ((attempt <= 32)) || die "cannot allocate a unique tracked file temporary: $target"
    temp="${target}.tmp.${SCRIPT_ID}.$$.${RANDOM}${RANDOM}"
    [[ ! -e "$temp" && ! -L "$temp" ]] && break
  done
  append_journal_event "$backup" "$journal" PENDING FILE "$target" "$temp" - -
  test_failpoint temporary_file_after_intent
  ( set -o noclobber; : >"$temp" ) \
    || die "failed to exclusively create tracked file temporary: $target"
  [[ -f "$temp" && ! -L "$temp" ]] || die "failed to create tracked file temporary: $target"
  inode="$(path_device_inode "$temp")"
  append_journal_event "$backup" "$journal" APPLIED FILE "$target" "$temp" "$inode" -
  printf '%s\n' "$temp"
}

create_tracked_symlink_temp() {
  local target="$1"
  local link_target="$2"
  local journal="$3"
  local backup temp inode attempt=0
  backup="$(dirname "$journal")"
  while :; do
    ((attempt += 1))
    ((attempt <= 32)) || die "cannot allocate a unique tracked symlink temporary: $target"
    temp="${target}.tmp.${SCRIPT_ID}.$$.${RANDOM}${RANDOM}"
    [[ ! -e "$temp" && ! -L "$temp" ]] && break
  done
  append_journal_event "$backup" "$journal" PENDING SYMLINK "$target" "$temp" - "$link_target"
  test_failpoint temporary_symlink_after_intent
  ln -s -- "$link_target" "$temp"
  inode="$(path_device_inode "$temp")"
  append_journal_event "$backup" "$journal" APPLIED SYMLINK "$target" "$temp" "$inode" "$link_target"
  printf '%s\n' "$temp"
}

replace_symlink() {
  local path="$1"
  local target="$2"
  local journal="$3"
  local parent temp
  parent="$(dirname "$path")"
  [[ -d "$parent" && ! -L "$parent" ]] || die "compatibility parent is missing or unsafe: $parent"
  if [[ -e "$path" || -L "$path" ]]; then
    [[ -L "$path" ]] || die "refusing to replace a non-symlink: $path"
  fi
  temp="$(create_tracked_symlink_temp "$path" "$target" "$journal")"
  test_failpoint compat_before_atomic_rename
  # GNU mv -T performs one rename over an existing symlink.  There is no
  # rm-then-mv window in which the original link disappears.
  mv -Tf -- "$temp" "$path"
}

restore_manifest_files() {
  local manifest="$1"
  local applied_file="$2"
  local backup="$3"
  local intent_file="$backup/file-intents.tsv"
  local relative size expected_hash source status refs target temp actual_hash actual_size
  printf 'relative_path\tfile_size\tsha256\n' >"$applied_file"
  printf 'relative_path\tfile_size\tsha256\toriginal_state\n' >"$intent_file"
  while IFS=$'\t' read -r relative size expected_hash source status refs; do
    [[ "$relative" != "relative_path" ]] || continue
    [[ "$status" == "RESTORE" \
        || "$status" == "RESTORE_PINNED_NO_DB_HASH_CROSS_VERIFIED" ]] || continue
    case "$relative" in
      public/*|sign-template/*) ;;
      *) die "manifest attempted to leave repair scope: $relative" ;;
    esac
    [[ "$relative" != *"/../"* && "$relative" != ../* && "$relative" != */.. ]] \
      || die "unsafe manifest path: $relative"
    [[ -f "$source" && ! -L "$source" ]] || die "verified source disappeared: $source"
    actual_size="$(stat -c %s "$source")"
    actual_hash="$(sha256sum "$source" | awk '{print $1}')"
    [[ "$actual_size" == "$size" && "$actual_hash" == "$expected_hash" ]] \
      || die "verified source drifted: $source"
    target="$TARGET_ROOT/$relative"
    if [[ -e "$target" || -L "$target" ]]; then
      [[ -f "$target" && ! -L "$target" ]] || die "target appeared with unsafe type: $target"
      [[ "$(stat -c %s "$target")" == "$size" \
          && "$(sha256sum "$target" | awk '{print $1}')" == "$expected_hash" ]] \
        || die "target appeared with conflicting content: $target"
      continue
    fi
    append_journal_event "$backup" "$intent_file" \
      "$relative" "$size" "$expected_hash" ABSENT
    test_failpoint file_intent_persisted
    ensure_target_parent_directories "$target" "$backup/created-directories.tsv"
    temp="$(create_tracked_file_temp "$target" "$backup/temporary-paths.tsv")"
    cp -- "$source" "$temp"
    chmod 0640 "$temp"
    chown --reference="$TARGET_ROOT" "$temp"
    [[ "$(stat -c %s "$temp")" == "$size" \
        && "$(sha256sum "$temp" | awk '{print $1}')" == "$expected_hash" ]] \
      || die "temporary restore verification failed: $relative"
    test_failpoint hash_verified_before_atomic_rename
    mv -Tf -- "$temp" "$target"
    test_failpoint file_after_atomic_rename_before_applied_record
    printf '%s\t%s\t%s\n' "$relative" "$size" "$expected_hash" >>"$applied_file"
  done <"$manifest"
  sync
}

directory_contains_only_path() {
  local directory="$1"
  local allowed="$2"
  local entry
  [[ -d "$directory" && ! -L "$directory" ]] || return 1
  while IFS= read -r -d '' entry; do
    [[ "$entry" == "$allowed" ]] || return 1
  done < <(find "$directory" -mindepth 1 -maxdepth 1 -print0)
}

fail_apply_config_state() {
  local backup="$1"
  local stage="$2"
  local reason="$3"
  mark_manual_intervention "$backup" "apply_config_${stage}_${reason}"
  die "active environment/systemd state drifted from the sealed apply stage"
}

assert_config_apply_stage() {
  local backup="$1"
  local stage="$2"
  local env_expected="$3"
  local file_expected="$4"
  local oa_expected="$5"
  local service dropin state copy parent_state planned expected parent
  local seen_file=0 seen_oa=0

  case "$env_expected" in
    PRE)
      files_exact_with_metadata "$ENV_FILE" "$backup/config/env.before" \
        || fail_apply_config_state "$backup" "$stage" "env_not_pre"
      ;;
    APPLIED)
      files_exact_with_metadata "$ENV_FILE" "$backup/config/env.planned" \
        || fail_apply_config_state "$backup" "$stage" "env_not_applied"
      ;;
    *) die "unknown expected environment apply state: $env_expected" ;;
  esac

  while IFS=$'\t' read -r service dropin state copy parent_state; do
    case "$service" in
      "$FILE_SERVICE") expected="$file_expected"; seen_file=$((seen_file + 1)) ;;
      "$OA_SERVICE") expected="$oa_expected"; seen_oa=$((seen_oa + 1)) ;;
      *) fail_apply_config_state "$backup" "$stage" "unknown_service" ;;
    esac
    planned="$backup/systemd/${service%.service}.dropin.planned"
    parent="$(dirname "$dropin")"
    [[ -f "$planned" && ! -L "$planned" ]] \
      || fail_apply_config_state "$backup" "$stage" "planned_dropin_missing"
    case "$expected:$state" in
      PRE:FILE)
        [[ -d "$parent" && ! -L "$parent" ]] \
          || fail_apply_config_state "$backup" "$stage" "pre_parent_changed"
        files_exact_with_metadata "$dropin" "$copy" \
          || fail_apply_config_state "$backup" "$stage" "dropin_not_pre"
        ;;
      PRE:ABSENT)
        [[ ! -e "$dropin" && ! -L "$dropin" ]] \
          || fail_apply_config_state "$backup" "$stage" "absent_dropin_appeared"
        case "$parent_state" in
          DIRECTORY)
            [[ -d "$parent" && ! -L "$parent" ]] \
              || fail_apply_config_state "$backup" "$stage" "pre_parent_changed"
            ;;
          ABSENT)
            [[ ! -e "$parent" && ! -L "$parent" ]] \
              || fail_apply_config_state "$backup" "$stage" "absent_parent_appeared"
            ;;
          *) fail_apply_config_state "$backup" "$stage" "unknown_parent_state" ;;
        esac
        ;;
      APPLIED:FILE|APPLIED:ABSENT)
        [[ -d "$parent" && ! -L "$parent" ]] \
          || fail_apply_config_state "$backup" "$stage" "applied_parent_changed"
        files_exact_with_metadata "$dropin" "$planned" \
          || fail_apply_config_state "$backup" "$stage" "dropin_not_applied"
        if [[ "$parent_state" == "ABSENT" ]]; then
          directory_contains_only_path "$parent" "$dropin" \
            || fail_apply_config_state "$backup" "$stage" "created_parent_contains_foreign_entry"
        elif [[ "$parent_state" != "DIRECTORY" ]]; then
          fail_apply_config_state "$backup" "$stage" "unknown_parent_state"
        fi
        ;;
      *) fail_apply_config_state "$backup" "$stage" "unknown_dropin_state" ;;
    esac
  done <"$backup/systemd/dropins.tsv"
  [[ "$seen_file" == "1" && "$seen_oa" == "1" ]] \
    || fail_apply_config_state "$backup" "$stage" "dropin_inventory_incomplete"
}

assert_dropin_pre_replace() {
  local backup="$1"
  local expected_service="$2"
  local allowed_temp="$3"
  local service dropin state copy parent_state parent matched=0
  while IFS=$'\t' read -r service dropin state copy parent_state; do
    [[ "$service" == "$expected_service" ]] || continue
    matched=$((matched + 1))
    parent="$(dirname "$dropin")"
    [[ -d "$parent" && ! -L "$parent" ]] \
      || fail_apply_config_state "$backup" "before_${service}" "parent_changed"
    case "$state" in
      FILE)
        files_exact_with_metadata "$dropin" "$copy" \
          || fail_apply_config_state "$backup" "before_${service}" "dropin_not_pre"
        ;;
      ABSENT)
        [[ ! -e "$dropin" && ! -L "$dropin" ]] \
          || fail_apply_config_state "$backup" "before_${service}" "absent_dropin_appeared"
        if [[ "$parent_state" == "ABSENT" ]]; then
          directory_contains_only_path "$parent" "$allowed_temp" \
            || fail_apply_config_state "$backup" "before_${service}" "created_parent_contains_foreign_entry"
        elif [[ "$parent_state" != "DIRECTORY" ]]; then
          fail_apply_config_state "$backup" "before_${service}" "unknown_parent_state"
        fi
        ;;
      *) fail_apply_config_state "$backup" "before_${service}" "unknown_dropin_state" ;;
    esac
  done <"$backup/systemd/dropins.tsv"
  [[ "$matched" == "1" ]] \
    || fail_apply_config_state "$backup" "before_${expected_service}" "dropin_inventory_incomplete"
}

update_env_file() {
  local backup="$1"
  local temp
  assert_release_unchanged "$backup"
  assert_config_apply_stage "$backup" before_env_replace PRE PRE PRE
  temp="$(create_tracked_file_temp "$ENV_FILE" "$backup/temporary-paths.tsv")"
  cp -- "$backup/config/env.planned" "$temp"
  chown --reference="$backup/config/env.planned" "$temp"
  chmod --reference="$backup/config/env.planned" "$temp"
  files_exact_with_metadata "$ENV_FILE" "$backup/config/env.before" \
    || fail_apply_config_state "$backup" before_env_atomic_rename env_not_pre
  test_failpoint env_before_atomic_rename
  mv -Tf -- "$temp" "$ENV_FILE"
  assert_config_apply_stage "$backup" after_env_replace APPLIED PRE PRE
  local key expected
  for key in \
    ERP_UPLOAD_ROOT FILE_PATH SIGN_PACKAGE_STORAGE_ROOT \
    OA_ATTENDANCE_STORAGE_ROOT OA_REIMBURSEMENT_STORAGE_ROOT DRIVE_LOCAL_PATH; do
    case "$key" in
      ERP_UPLOAD_ROOT|FILE_PATH) expected="$TARGET_ROOT" ;;
      SIGN_PACKAGE_STORAGE_ROOT) expected="$SIGN_PACKAGE_ROOT" ;;
      OA_ATTENDANCE_STORAGE_ROOT) expected="$TARGET_ROOT/private/attendance" ;;
      OA_REIMBURSEMENT_STORAGE_ROOT) expected="$TARGET_ROOT/private/reimbursement" ;;
      DRIVE_LOCAL_PATH) expected="$TARGET_ROOT/private/drive" ;;
      *) die "unknown persisted storage variable: $key" ;;
    esac
    [[ "$(dotenv_value "$key")" == "$expected" ]] || die "failed to persist $key"
  done
  [[ "$(dotenv_value OA_SIGN_EXCEL_IMPORT_ENABLED)" == "true" ]] || die "OA Excel import was not preserved"
  [[ "$(dotenv_value VUE_APP_SIGN_EXCEL_IMPORT_ENABLED)" == "true" ]] || die "frontend Excel import was not preserved"
  test_failpoint env_after_update
}

install_systemd_guards() {
  local backup="$1"
  local service dropin temp
  for service in "$FILE_SERVICE" "$OA_SERVICE"; do
    assert_release_unchanged "$backup"
    if [[ "$service" == "$FILE_SERVICE" ]]; then
      assert_config_apply_stage "$backup" before_file_dropin APPLIED PRE PRE
    else
      assert_config_apply_stage "$backup" before_oa_dropin APPLIED APPLIED PRE
    fi
    dropin="$SYSTEMD_ROOT/${service}.d/20-common-upload-root.conf"
    mkdir -p "$(dirname "$dropin")"
    temp="$(create_tracked_file_temp "$dropin" "$backup/temporary-paths.tsv")"
    cp -- "$backup/systemd/${service%.service}.dropin.planned" "$temp"
    chown --reference="$backup/systemd/${service%.service}.dropin.planned" "$temp"
    chmod --reference="$backup/systemd/${service%.service}.dropin.planned" "$temp"
    assert_dropin_pre_replace "$backup" "$service" "$temp"
    mv -Tf -- "$temp" "$dropin"
    if [[ "$service" == "$FILE_SERVICE" ]]; then
      assert_config_apply_stage "$backup" after_file_dropin APPLIED APPLIED PRE
    else
      assert_config_apply_stage "$backup" after_oa_dropin APPLIED APPLIED APPLIED
    fi
  done
  systemctl daemon-reload
}

record_applied_config_state() {
  local backup="$1"
  local service dropin planned after
  assert_release_unchanged "$backup"
  assert_config_apply_stage "$backup" record_applied APPLIED APPLIED APPLIED
  files_exact_with_metadata "$ENV_FILE" "$backup/config/env.planned" \
    || die "active environment differs from the sealed applied plan"
  cp -a "$ENV_FILE" "$backup/config/env.after"
  for service in "$FILE_SERVICE" "$OA_SERVICE"; do
    dropin="$SYSTEMD_ROOT/${service}.d/20-common-upload-root.conf"
    planned="$backup/systemd/${service%.service}.dropin.planned"
    after="$backup/systemd/${service%.service}.dropin.after"
    files_exact_with_metadata "$dropin" "$planned" \
      || die "active systemd drop-in differs from the sealed applied plan: $service"
    cp -a "$dropin" "$after"
  done
}

switch_compatibility_paths() {
  local state_file="$1"
  local backup="$2"
  local index path state link_target sidecar copy
  assert_release_unchanged "$backup"
  while IFS=$'\t' read -r index path state link_target sidecar copy; do
    [[ -n "$index" ]] || continue
    case "$state" in
      SYMLINK)
        [[ -L "$path" && "$(readlink "$path")" == "$link_target" ]] \
          || die "compatibility symlink drifted before apply: $path"
        log "LEGACY_SYMLINK_PRESERVED path=$path target=$link_target"
        ;;
      DIRECTORY)
        [[ -d "$path" && ! -L "$path" ]] || die "compatibility directory drifted: $path"
        [[ -d "$copy" && ! -L "$copy" \
            && "$(directory_tree_fingerprint "$path")" == "$(directory_tree_fingerprint "$copy")" ]] \
          || die "legacy compatibility directory changed after its sealed backup: $path"
        # A legacy upload root can contain unhashed and unreferenced templates.
        # Keep the whole directory reachable in place; the services themselves
        # are pinned to TARGET_ROOT by .env and systemd guards.
        log "LEGACY_DIRECTORY_PRESERVED path=$path"
        ;;
      ABSENT)
        [[ ! -e "$path" && ! -L "$path" ]] || die "compatibility path appeared: $path"
        log "LEGACY_ABSENT_PATH_PRESERVED path=$path"
        ;;
      *) die "unknown compatibility state: $state" ;;
    esac
  done <"$state_file"
  test_failpoint compat_after_switch
}

wait_for_health() {
  local service="$1"
  local url="$2"
  local work="$3"
  local body="$work/health.body"
  local meta="$work/health.meta"
  local attempt http_code content_type
  for attempt in $(seq 1 45); do
    if systemctl is-active --quiet "$service"; then
      if curl -sS --max-time 4 -o "$body" -w '%{http_code}\t%{content_type}\n' "$url" >"$meta" 2>/dev/null; then
        IFS=$'\t' read -r http_code content_type <"$meta"
        if [[ "$http_code" == "200" ]] && python3 - "$body" <<'PY' >/dev/null 2>&1
import json
import sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
raise SystemExit(0 if isinstance(body, dict) and body.get("status") == "UP" else 1)
PY
        then
          return 0
        fi
      fi
    fi
    sleep 2
  done
  die "service health check failed: $service $url"
}

verify_runtime_env() {
  local service="$1"
  local key expected actual
  for key in \
    ERP_UPLOAD_ROOT FILE_PATH SIGN_PACKAGE_STORAGE_ROOT \
    OA_ATTENDANCE_STORAGE_ROOT OA_REIMBURSEMENT_STORAGE_ROOT DRIVE_LOCAL_PATH; do
    case "$key" in
      ERP_UPLOAD_ROOT|FILE_PATH) expected="$TARGET_ROOT" ;;
      SIGN_PACKAGE_STORAGE_ROOT) expected="$SIGN_PACKAGE_ROOT" ;;
      OA_ATTENDANCE_STORAGE_ROOT) expected="$TARGET_ROOT/private/attendance" ;;
      OA_REIMBURSEMENT_STORAGE_ROOT) expected="$TARGET_ROOT/private/reimbursement" ;;
      DRIVE_LOCAL_PATH) expected="$TARGET_ROOT/private/drive" ;;
      *) die "unknown runtime storage variable: $key" ;;
    esac
    if [[ "$TEST_MODE" == "1" ]]; then
      actual="$(dotenv_value "$key")"
    else
      actual="$(runtime_env_value "$service" "$key")"
    fi
    [[ "$actual" == "$expected" ]] || die "$service runtime $key differs from $expected"
  done
  if [[ "$service" == "$OA_SERVICE" ]]; then
    if [[ "$TEST_MODE" == "1" ]]; then
      actual="$(dotenv_value OA_SIGN_EXCEL_IMPORT_ENABLED)"
    else
      actual="$(runtime_env_value "$service" OA_SIGN_EXCEL_IMPORT_ENABLED)"
    fi
    [[ "$actual" == "true" ]] || die "OA runtime Excel import is not enabled"
  fi
}

verify_all_manifest_targets() {
  local manifest="$1"
  local relative size expected_hash source status refs target count=0
  while IFS=$'\t' read -r relative size expected_hash source status refs; do
    [[ "$relative" != "relative_path" ]] || continue
    case "$status" in
      PRESENT|PRESENT_PINNED_NO_DB_HASH_CROSS_VERIFIED) ;;
      *) die "postcheck manifest contains a non-present row: $relative $status" ;;
    esac
    case "$relative" in
      public/*|sign-template/*) ;;
      *) die "postcheck manifest escaped repair scope: $relative" ;;
    esac
    target="$TARGET_ROOT/$relative"
    [[ -f "$target" && ! -L "$target" \
        && "$(stat -c %s "$target")" == "$size" \
        && "$(sha256sum "$target" | awk '{print $1}')" == "$expected_hash" ]] \
      || die "postcheck manifest target verification failed: $relative"
    ((count += 1))
  done <"$manifest"
  ((count > 0)) || die "postcheck manifest has no hash-verified files"
}

reject_json_binary_body() {
  local body="$1"
  local content_type="$2"
  python3 - "$body" "$content_type" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
content_type = sys.argv[2].lower()
sample = path.read_bytes()[:1024 * 1024]
is_json = "json" in content_type
if not is_json:
    try:
        json.loads(sample.decode("utf-8"))
        is_json = True
    except (UnicodeDecodeError, json.JSONDecodeError):
        pass
if is_json:
    raise SystemExit("expected binary file but received a JSON body (HTTP 200 error envelope rejected)")
PY
}

verify_public_binary_responses() {
  local manifest="$1"
  local work="$2"
  local relative size expected_hash source status refs encoded url body meta http_code content_type count=0
  while IFS=$'\t' read -r relative size expected_hash source status refs; do
    [[ "$relative" != "relative_path" ]] || continue
    [[ "$relative" == public/* ]] || continue
    encoded="$(python3 - "$relative" <<'PY'
import sys
import urllib.parse
print(urllib.parse.quote(sys.argv[1][len("public/"):], safe="/"))
PY
)"
    url="$FILE_PUBLIC_BASE_URL/$encoded"
    body="$work/public.$count.body"
    meta="$work/public.$count.meta"
    curl -sS --max-time 20 -o "$body" -w '%{http_code}\t%{content_type}\n' "$url" >"$meta" \
      || die "public-file request failed: $relative"
    IFS=$'\t' read -r http_code content_type <"$meta"
    [[ "$http_code" == "200" ]] || die "public-file HTTP status is $http_code: $relative"
    reject_json_binary_body "$body" "$content_type"
    [[ "$(stat -c %s "$body")" == "$size" ]] || die "HTTP body size mismatch: $relative"
    [[ "$(sha256sum "$body" | awk '{print $1}')" == "$expected_hash" ]] \
      || die "HTTP body SHA-256 mismatch: $relative"
    ((count += 1))
  done <"$manifest"
  ((count > 0)) || die "no hash-verified public file is available for HTTP verification"
}

oa_authenticated_curl() {
  local url="$1"
  local body="$2"
  local meta="$3"
  printf 'header = "Authorization: Bearer %s"\n' "$OA_BEARER_TOKEN" \
    | curl --config - -sS --max-time 20 -o "$body" \
        -w '%{http_code}\t%{content_type}\n' "$url" >"$meta"
}

verify_oa_business_json() {
  local work="$1"
  local body="$work/oa-business.body"
  local meta="$work/oa-business.meta"
  local http_code content_type
  oa_authenticated_curl "$OA_BUSINESS_CHECK_URL" "$body" "$meta" \
    || die "OA business capabilities request failed"
  IFS=$'\t' read -r http_code content_type <"$meta"
  [[ "$http_code" == "200" ]] || die "OA business capabilities HTTP status is $http_code"
  python3 - "$body" "$content_type" <<'PY'
import json
import sys
content_type = sys.argv[2].lower()
if "json" not in content_type:
    raise SystemExit("OA business endpoint did not return JSON")
payload = json.load(open(sys.argv[1], encoding="utf-8"))
data = payload.get("data") if isinstance(payload, dict) else None
if payload.get("code") != 200 or not isinstance(data, dict):
    raise SystemExit("OA business JSON is not a successful AjaxResult")
if data.get("coreEnabled") is not True or data.get("excelImportEnabled") is not True:
    raise SystemExit("OA signing capabilities are not enabled")
PY
}

verify_template_business_reads() {
  local manifest="$1"
  local work="$2"
  local rows="$work/template-business-reads.tsv"
  local template_id relative size expected_hash url body meta http_code content_type count=0
  python3 - "$manifest" "$rows" <<'PY'
import pathlib
import re
import sys
source, target = map(pathlib.Path, sys.argv[1:])
items = {}
for raw in source.read_text(encoding="utf-8").splitlines()[1:]:
    fields = raw.split("\t")
    if len(fields) != 6:
        raise SystemExit("malformed postcheck manifest")
    relative, size, digest, _source, status, refs = fields
    for ref in refs.split(";"):
        match = re.fullmatch(r"oa_sign_template:([0-9]+)", ref)
        if match:
            current = (relative, size, digest)
            if match.group(1) in items and items[match.group(1)] != current:
                raise SystemExit(f"template ID maps to conflicting files: {match.group(1)}")
            items[match.group(1)] = current
lines = []
for key, value in sorted(items.items(), key=lambda item: int(item[0])):
    lines.append(key + "\t" + "\t".join(value) + "\n")
target.write_text("".join(lines), encoding="utf-8")
PY
  while IFS=$'\t' read -r template_id relative size expected_hash; do
    [[ "$template_id" =~ ^[0-9]+$ ]] || die "unsafe template ID in business-read list"
    url="$OA_TEMPLATE_BASE_URL/$template_id/file"
    body="$work/template.$template_id.body"
    meta="$work/template.$template_id.meta"
    oa_authenticated_curl "$url" "$body" "$meta" || die "OA template-file request failed: $template_id"
    IFS=$'\t' read -r http_code content_type <"$meta"
    [[ "$http_code" == "200" ]] || die "OA template-file HTTP status is $http_code: $template_id"
    reject_json_binary_body "$body" "$content_type"
    [[ "$(stat -c %s "$body")" == "$size" \
        && "$(sha256sum "$body" | awk '{print $1}')" == "$expected_hash" ]] \
      || die "OA template-file content mismatch: $template_id"
    ((count += 1))
  done <"$rows"
  ((count > 0)) || die "no oa_sign_template rows were available for business file reads"
}

verify_compatibility_paths() {
  local state_file="$1"
  local index path state link_target sidecar copy
  while IFS=$'\t' read -r index path state link_target sidecar copy; do
    case "$state" in
      SYMLINK)
        [[ -L "$path" && "$(readlink "$path")" == "$link_target" ]] \
          || die "preserved compatibility symlink drifted: $path"
        ;;
      ABSENT)
        [[ ! -e "$path" && ! -L "$path" ]] \
          || die "preserved absent compatibility path appeared: $path"
        ;;
      DIRECTORY)
        [[ -d "$path" && ! -L "$path" && -d "$copy" && ! -L "$copy" \
            && "$(directory_tree_fingerprint "$path")" == "$(directory_tree_fingerprint "$copy")" ]] \
          || die "preserved legacy compatibility directory drifted: $path"
        ;;
      *) die "unknown compatibility state during verification: $state" ;;
    esac
  done <"$state_file"
}

restore_env_and_systemd() {
  local backup="$1"
  local service dropin state copy parent_state temp parent planned
  local rollback_journal="$backup/rollback-temporary-paths.tsv"
  preflight_config_rollback "$backup"
  sha256sum -c "$backup/config/env.before.sha256" >/dev/null \
    || die "environment backup checksum changed before restore"
  assert_release_unchanged "$backup"
  [[ -f "$ENV_FILE" && ! -L "$ENV_FILE" ]] || die "active environment file became unsafe before rollback"
  temp="$(create_tracked_file_temp "$ENV_FILE" "$rollback_journal")"
  cp -- "$backup/config/env.before" "$temp"
  chown --reference="$backup/config/env.before" "$temp"
  chmod --reference="$backup/config/env.before" "$temp"
  mv -Tf -- "$temp" "$ENV_FILE"
  while IFS=$'\t' read -r service dropin state copy parent_state; do
    assert_release_unchanged "$backup"
    parent="$(dirname "$dropin")"
    planned="$backup/systemd/${service%.service}.dropin.planned"
    if [[ "$parent_state" == "ABSENT" && ! -e "$parent" && ! -L "$parent" ]]; then
      [[ "$state" == "ABSENT" && ! -e "$dropin" && ! -L "$dropin" ]] \
        || die "originally absent systemd drop-in parent has inconsistent state"
      continue
    fi
    [[ -d "$parent" && ! -L "$parent" ]] \
      || die "systemd drop-in parent became unsafe during rollback"
    case "$state" in
      FILE)
        files_exact_with_metadata "$dropin" "$copy" \
          || files_exact_with_metadata "$dropin" "$planned" \
          || die "systemd drop-in changed after rollback preflight: $dropin"
        temp="$(create_tracked_file_temp "$dropin" "$rollback_journal")"
        cp -- "$copy" "$temp"
        chown --reference="$copy" "$temp"
        chmod --reference="$copy" "$temp"
        mv -Tf -- "$temp" "$dropin"
        ;;
      ABSENT)
        if [[ -e "$dropin" || -L "$dropin" ]]; then
          files_exact_with_metadata "$dropin" "$planned" \
            || die "systemd drop-in changed after rollback preflight: $dropin"
          rm -f -- "$dropin"
        fi
        ;;
      *) die "unknown systemd backup state: $state" ;;
    esac
    case "$parent_state" in
      DIRECTORY) ;;
      ABSENT)
        if [[ ! -e "$(dirname "$dropin")" && ! -L "$(dirname "$dropin")" ]]; then
          :
        else
          [[ -d "$(dirname "$dropin")" && ! -L "$(dirname "$dropin")" ]] \
            || die "systemd drop-in directory changed to an unsafe type"
          rmdir -- "$(dirname "$dropin")" \
            || die "systemd drop-in directory was originally absent but is now non-empty"
        fi
        ;;
      *) die "unknown systemd drop-in parent state: $parent_state" ;;
    esac
  done <"$backup/systemd/dropins.tsv"
  systemctl daemon-reload
}

preflight_config_rollback() {
  local backup="$1"
  local service dropin state copy parent_state planned parent reason=""
  local seen_file=0 seen_oa=0
  if [[ ! -f "$ENV_FILE" || -L "$ENV_FILE" ]]; then
    reason="active_env_type_unknown"
  elif ! files_exact_with_metadata "$ENV_FILE" "$backup/config/env.before" \
      && ! files_exact_with_metadata "$ENV_FILE" "$backup/config/env.planned"; then
    reason="active_env_content_unknown"
  fi
  while [[ -z "$reason" ]] \
      && IFS=$'\t' read -r service dropin state copy parent_state; do
    case "$service" in
      "$FILE_SERVICE") seen_file=$((seen_file + 1)) ;;
      "$OA_SERVICE") seen_oa=$((seen_oa + 1)) ;;
      *) reason="sealed_dropin_service_unknown"; break ;;
    esac
    planned="$backup/systemd/${service%.service}.dropin.planned"
    parent="$(dirname "$dropin")"
    [[ -f "$planned" && ! -L "$planned" ]] || { reason="planned_dropin_missing"; break; }
    case "$parent_state" in
      DIRECTORY)
        if [[ ! -d "$parent" || -L "$parent" ]]; then
          reason="active_dropin_parent_unknown"
          break
        fi
        ;;
      ABSENT)
        if [[ -e "$parent" || -L "$parent" ]]; then
          if [[ ! -d "$parent" || -L "$parent" ]] \
              || ! directory_contains_only_path "$parent" "$dropin"; then
            reason="active_created_dropin_parent_unknown"
            break
          fi
        fi
        ;;
      *) reason="sealed_dropin_parent_state_unknown"; break ;;
    esac
    case "$state" in
      FILE)
        if [[ ! -f "$dropin" || -L "$dropin" ]] \
            || { ! files_exact_with_metadata "$dropin" "$copy" \
                && ! files_exact_with_metadata "$dropin" "$planned"; }; then
          reason="active_dropin_content_unknown"
        fi
        ;;
      ABSENT)
        if [[ -e "$dropin" || -L "$dropin" ]]; then
          if [[ ! -f "$dropin" || -L "$dropin" ]] \
              || ! files_exact_with_metadata "$dropin" "$planned"; then
            reason="active_dropin_content_unknown"
          fi
        fi
        ;;
      *) reason="sealed_dropin_state_unknown" ;;
    esac
  done <"$backup/systemd/dropins.tsv"
  if [[ -z "$reason" && ( "$seen_file" != "1" || "$seen_oa" != "1" ) ]]; then
    reason="sealed_dropin_inventory_incomplete"
  fi
  if [[ -n "$reason" ]]; then
    mark_manual_intervention "$backup" "$reason"
    die "active environment/systemd state is neither sealed PRE nor sealed APPLIED"
  fi
}

files_exact_with_metadata() {
  local first="$1"
  local second="$2"
  python3 - "$first" "$second" <<'PY'
import pathlib
import stat
import sys

first, second = map(pathlib.Path, sys.argv[1:])
if not first.is_file() or first.is_symlink() or not second.is_file() or second.is_symlink():
    raise SystemExit(1)
a = first.stat()
b = second.stat()
same_metadata = (a.st_uid, a.st_gid, stat.S_IMODE(a.st_mode)) == (
    b.st_uid, b.st_gid, stat.S_IMODE(b.st_mode)
)
raise SystemExit(0 if same_metadata and first.read_bytes() == second.read_bytes() else 1)
PY
}

restore_compatibility_paths() {
  die "destructive compatibility rollback is disabled; legacy paths are preserved"
  local state_file="$1"
  local backup="$2"
  local index path state link_target sidecar copy
  while IFS=$'\t' read -r index path state link_target sidecar copy; do
    case "$state" in
      SYMLINK)
        if [[ -L "$path" && "$(readlink "$path")" == "$link_target" ]]; then
          # Apply failed before this link was changed, or the original literal
          # target already matched the desired target.
          :
        elif [[ -L "$path" && "$(readlink -f "$path" 2>/dev/null || true)" == "$TARGET_ROOT" ]]; then
          replace_symlink "$path" "$link_target" "$backup/temporary-paths.tsv"
        elif [[ ! -e "$path" && ! -L "$path" ]]; then
          # Controlled recovery also covers a legacy rm-then-mv interruption.
          replace_symlink "$path" "$link_target" "$backup/temporary-paths.tsv"
        else
          die "cannot restore original compatibility symlink: $path"
        fi
        ;;
      DIRECTORY)
        [[ -d "$path" && ! -L "$path" && ! -e "$sidecar" && ! -L "$sidecar" \
            && -d "$copy" && ! -L "$copy" \
            && "$(directory_tree_fingerprint "$path")" == "$(directory_tree_fingerprint "$copy")" ]] \
          || die "preserved legacy compatibility directory drifted: $path"
        log "LEGACY_DIRECTORY_PRESERVED rollback path=$path"
        ;;
      ABSENT)
        if [[ -L "$path" && "$(readlink -f "$path")" == "$TARGET_ROOT" ]]; then
          rm -f -- "$path"
        elif [[ ! -e "$path" && ! -L "$path" ]]; then
          # Apply failed before creating this compatibility link.
          :
        else
          die "rollback compatibility path drifted: $path"
        fi
        ;;
      *) die "unknown compatibility state: $state" ;;
    esac
  done <"$state_file"
}

cleanup_tracked_temporary_paths() {
  die "destructive temporary cleanup is disabled; tracked artifacts are preserved"
  local backup="$1"
  local journal="$backup/temporary-paths.tsv"
  local type target temp recorded_inode expected actual_inode
  [[ -f "$journal" && ! -L "$journal" ]] || die "temporary-path journal is missing or unsafe"
  while IFS=$'\t' read -r type target temp recorded_inode expected; do
    [[ -n "$type" ]] || continue
    case "$target" in
      "$TARGET_ROOT/public/"*|"$TARGET_ROOT/sign-template/"*|"$ENV_FILE"|\
      "$SYSTEMD_ROOT/$FILE_SERVICE.d/20-common-upload-root.conf"|\
      "$SYSTEMD_ROOT/$OA_SERVICE.d/20-common-upload-root.conf") ;;
      *)
        awk -F '\t' -v wanted="$target" '$2 == wanted { found=1 } END { exit !found }' \
          "$backup/compat/links.tsv" \
          || die "temporary-path target is outside the recorded repair set: $target"
        ;;
    esac
    [[ "$temp" == "$target.tmp.$SCRIPT_ID."* ]] || die "unsafe temporary-path journal entry: $temp"
    if [[ ! -e "$temp" && ! -L "$temp" ]]; then
      continue
    fi
    actual_inode="$(path_device_inode "$temp")"
    [[ "$actual_inode" == "$recorded_inode" ]] \
      || die "temporary path inode changed; refusing removal: $temp"
    case "$type" in
      FILE)
        [[ -f "$temp" && ! -L "$temp" ]] || die "tracked file temporary changed type: $temp"
        ;;
      SYMLINK)
        [[ -L "$temp" && "$(readlink "$temp")" == "$expected" ]] \
          || die "tracked symlink temporary changed: $temp"
        ;;
      *) die "unknown temporary-path type: $type" ;;
    esac
    rm -f -- "$temp"
  done <"$journal"
}

directory_tree_fingerprint() {
  local directory="$1"
  python3 - "$directory" <<'PY'
import hashlib
import os
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
digest = hashlib.sha256()
for current, directories, files in os.walk(root, topdown=True, followlinks=False):
    directories.sort()
    files.sort()
    for name in directories + files:
        path = pathlib.Path(current, name)
        relative = path.relative_to(root).as_posix()
        digest.update(relative.encode("utf-8") + b"\0")
        if path.is_symlink():
            digest.update(b"LINK\0" + os.readlink(path).encode("utf-8") + b"\0")
        elif path.is_file():
            content = hashlib.sha256(path.read_bytes()).digest()
            digest.update(b"FILE\0" + str(path.stat().st_size).encode("ascii") + b"\0" + content)
        elif path.is_dir():
            digest.update(b"DIR")
        else:
            digest.update(b"OTHER")
        digest.update(b"\0")
print(digest.hexdigest())
PY
}

restore_managed_directory_state() {
  die "destructive managed-directory rollback is disabled; TARGET_ROOT is preserved"
  local backup="$1"
  local created="$backup/created-directories.tsv"
  local reversed="$backup/created-directories.reverse.tsv"
  local path recorded_inode actual_inode relative state copy
  [[ -f "$created" && ! -L "$created" ]] || die "created-directory journal is missing or unsafe"
  python3 - "$created" "$reversed" <<'PY'
import pathlib
import sys
source, target = map(pathlib.Path, sys.argv[1:])
lines = source.read_text(encoding="utf-8").splitlines()
target.write_text("\n".join(reversed(lines)) + ("\n" if lines else ""), encoding="utf-8")
PY
  while IFS=$'\t' read -r path recorded_inode; do
    [[ -n "$path" ]] || continue
    case "$path" in
      "$TARGET_ROOT/public"|"$TARGET_ROOT/public/"*|\
      "$TARGET_ROOT/sign-template"|"$TARGET_ROOT/sign-template/"*|\
      "$TARGET_ROOT/private/attendance"|"$TARGET_ROOT/private/reimbursement"|"$TARGET_ROOT/private/drive") ;;
      *) die "created-directory journal escaped managed storage: $path" ;;
    esac
    if [[ ! -e "$path" && ! -L "$path" ]]; then
      continue
    fi
    [[ -d "$path" && ! -L "$path" ]] || die "created managed directory changed type: $path"
    actual_inode="$(path_device_inode "$path")"
    [[ "$actual_inode" == "$recorded_inode" ]] \
      || die "created managed directory inode changed; refusing removal: $path"
    rmdir -- "$path" || die "created managed directory is not empty; refusing removal: $path"
  done <"$reversed"

  while IFS=$'\t' read -r relative state copy; do
    [[ -n "$relative" ]] || continue
    path="$TARGET_ROOT/$relative"
    case "$state" in
      ABSENT)
        [[ ! -e "$path" && ! -L "$path" ]] \
          || die "rollback did not restore originally absent managed path: $path"
        ;;
      DIRECTORY)
        [[ -d "$path" && ! -L "$path" ]] \
          || die "rollback lost original managed directory: $path"
        if [[ "$copy" != "-" ]]; then
          [[ -d "$copy" && ! -L "$copy" ]] || die "managed directory backup is missing: $copy"
          [[ "$(directory_tree_fingerprint "$path")" == "$(directory_tree_fingerprint "$copy")" ]] \
            || die "rollback managed directory differs from its backup: $path"
        fi
        ;;
      *) die "unknown managed directory state: $state" ;;
    esac
  done <"$backup/target-scope/state.tsv"
}

quarantine_applied_files() {
  die "destructive applied-file rollback is disabled; TARGET_ROOT is preserved"
  local backup="$1"
  local intents="$backup/file-intents.tsv"
  local relative size expected_hash original_state target quarantine
  [[ -f "$intents" ]] || return 0
  while IFS=$'\t' read -r relative size expected_hash original_state; do
    [[ "$relative" != "relative_path" ]] || continue
    case "$relative" in
      public/*|sign-template/*) ;;
      *) die "rollback file escaped scope: $relative" ;;
    esac
    [[ "$original_state" == "ABSENT" ]] || die "unsupported file intent state: $original_state"
    target="$TARGET_ROOT/$relative"
    quarantine="$backup/rollback-quarantine/$relative"
    if [[ ! -e "$target" && ! -L "$target" ]]; then
      if [[ -e "$quarantine" || -L "$quarantine" ]]; then
        [[ -f "$quarantine" && ! -L "$quarantine" \
            && "$(stat -c %s "$quarantine")" == "$size" \
            && "$(sha256sum "$quarantine" | awk '{print $1}')" == "$expected_hash" ]] \
          || die "rollback quarantine content is unsafe: $relative"
      fi
      continue
    fi
    [[ -f "$target" && ! -L "$target" ]] || die "rollback target has an unsafe type: $relative"
    [[ "$(stat -c %s "$target")" == "$size" \
        && "$(sha256sum "$target" | awk '{print $1}')" == "$expected_hash" ]] \
      || die "rollback target changed after apply: $relative"
    [[ ! -e "$quarantine" && ! -L "$quarantine" ]] \
      || die "rollback quarantine target already exists: $relative"
    mkdir -p "$(dirname "$quarantine")"
    mv "$target" "$quarantine"
  done <"$intents"
}

ROLLBACK_RUNNING=0
PROCESS_STATE="IDLE"
ACTIVE_BACKUP=""
EXIT_HANDLER_RUNNING=0

mark_manual_intervention() {
  local backup="$1"
  local reason="$2"
  printf '%s\t%s\n' "$(date --iso-8601=seconds)" "$reason" >"$backup/MANUAL_INTERVENTION"
  printf 'MANUAL_INTERVENTION backup=%s reason=%s\n' "$backup" "$reason" >&2
}

rollback_internal() {
  local backup="$1"
  local invocation="${2:-manual}"
  local phase baseline="" pre_stop_fingerprint after_stop_fingerprint
  [[ "$ROLLBACK_RUNNING" == "0" ]] || return 1
  ROLLBACK_RUNNING=1
  validate_backup_bundle "$backup"
  [[ -f "$backup/PHASE" && ! -L "$backup/PHASE" ]] || die "rollback phase marker is missing or unsafe"
  phase="$(<"$backup/PHASE")"
  case "$phase" in
    BACKUP_SEALED|STOPPING_SERVICES|SERVICES_STOPPED|DB_BACKUP_SEALED|MUTATION_STARTED|MUTATION_APPLIED|SERVICES_STARTING|APPLY_COMPLETE) ;;
    *) die "rollback bundle has an unknown phase: $phase" ;;
  esac
  assert_checkpoint_seal_consistency "$backup" "$phase"
  [[ "$(<"$backup/TARGET_ROOT")" == "$TARGET_ROOT" ]] \
    || die "rollback target differs from the sealed production target"

  validate_rollback_state_manifest "$backup" "DYNAMIC_ROLLBACK_STATE.sha256" dynamic
  if [[ "$phase" == "APPLY_COMPLETE" ]]; then
    validate_rollback_state_manifest "$backup" "DB_BACKUP.sha256" database
    validate_rollback_state_manifest "$backup" "MUTATION_ROLLBACK.sha256" mutation
    validate_rollback_state_manifest "$backup" "POST_APPLY_ROLLBACK_STATE.sha256" complete
  else
    if [[ "$phase" == "DB_BACKUP_SEALED" || "$phase" == "MUTATION_STARTED" \
        || "$phase" == "MUTATION_APPLIED" || "$phase" == "SERVICES_STARTING" ]]; then
      validate_rollback_state_manifest "$backup" "DB_BACKUP.sha256" database
    fi
    if [[ "$phase" == "MUTATION_APPLIED" || "$phase" == "SERVICES_STARTING" ]]; then
      validate_rollback_state_manifest "$backup" "MUTATION_ROLLBACK.sha256" mutation
    fi
  fi
  DYNAMIC_SEAL_ENABLED=0
  local journal
  for journal in created-directories.tsv temporary-paths.tsv file-intents.tsv applied-files.tsv; do
    [[ ! -f "$backup/$journal" ]] || chmod 0400 "$backup/$journal"
  done
  assert_release_unchanged "$backup"
  preflight_config_rollback "$backup"

  if [[ "$phase" == "APPLY_COMPLETE" ]]; then
    baseline="$backup/scope.after-apply.fingerprint"
  elif [[ "$phase" == "SERVICES_STARTING" ]]; then
    baseline="$backup/scope.before-service-start.fingerprint"
  fi
  if [[ -n "$baseline" ]]; then
    pre_stop_fingerprint="$backup/scope.pre-stop-rollback.fingerprint"
    scope_fingerprint "$pre_stop_fingerprint"
    if ! cmp -s "$baseline" "$pre_stop_fingerprint"; then
      mark_manual_intervention "$backup" "public_or_template_write_detected_before_rollback_stop"
      die "new public/sign-template writes block rollback; unified root was preserved"
    fi
  fi

  systemctl stop "$OA_SERVICE"
  systemctl stop "$FILE_SERVICE"
  assert_release_unchanged "$backup"
  if [[ -n "$baseline" ]]; then
    after_stop_fingerprint="$backup/scope.after-stop-rollback.fingerprint"
    scope_fingerprint "$after_stop_fingerprint"
    if ! cmp -s "$baseline" "$after_stop_fingerprint"; then
      mark_manual_intervention "$backup" "public_or_template_write_detected_while_stopping"
      systemctl start "$FILE_SERVICE" || true
      systemctl start "$OA_SERVICE" || true
      die "new public/sign-template writes appeared while stopping; unified root was preserved"
    fi
  fi
  private_fingerprint "$backup/private-sign-package.before-rollback.fingerprint"
  if [[ -f "$backup/private-sign-package.before-apply.fingerprint" ]] \
      && ! cmp -s "$backup/private-sign-package.before-apply.fingerprint" \
        "$backup/private-sign-package.before-rollback.fingerprint"; then
    mark_manual_intervention "$backup" "private_sign_package_changed_since_apply_started"
    systemctl start "$FILE_SERVICE" || true
    systemctl start "$OA_SERVICE" || true
    die "private/sign-package changed; unified root was preserved"
  fi
  # Rollback is intentionally non-destructive for TARGET_ROOT.  Verified
  # restored files, required directories, and any interrupted .tmp artifacts
  # remain visible for a later audited retry; no journal entry authorizes a
  # delete or move.  Compatibility paths were preserved during apply.
  verify_compatibility_paths "$backup/compat/links.tsv"
  assert_release_unchanged "$backup"
  preflight_config_rollback "$backup"
  restore_env_and_systemd "$backup"
  private_fingerprint "$backup/private-sign-package.after-rollback.fingerprint"
  cmp -s "$backup/private-sign-package.before-rollback.fingerprint" "$backup/private-sign-package.after-rollback.fingerprint" \
    || die "private/sign-package changed during rollback"

  assert_release_unchanged "$backup"
  systemctl start "$FILE_SERVICE"
  wait_for_health "$FILE_SERVICE" "$FILE_HEALTH_URL" "$backup"
  assert_release_unchanged "$backup"
  systemctl start "$OA_SERVICE"
  wait_for_health "$OA_SERVICE" "$OA_HEALTH_URL" "$backup"
  printf '%s\n' "$(date --iso-8601=seconds)" >"$backup/ROLLED_BACK"
  write_phase "$backup" ROLLED_BACK
  log "ROLLBACK_OK invocation=$invocation backup=$backup target_artifacts_preserved=true"
}

on_process_exit() {
  local original_rc=$?
  local rollback_rc=0
  trap - EXIT
  if [[ "$EXIT_HANDLER_RUNNING" == "1" ]]; then
    exit "$original_rc"
  fi
  EXIT_HANDLER_RUNNING=1
  if [[ "$original_rc" != "0" && "$PROCESS_STATE" == APPLY_* \
      && -n "$ACTIVE_BACKUP" && -f "$ACTIVE_BACKUP/APPLY_STARTED" \
      && ! -f "$ACTIVE_BACKUP/ROLLED_BACK" ]]; then
    printf 'apply failed; attempting one controlled rollback from %s\n' "$ACTIVE_BACKUP" >&2
    set +e
    (
      trap - EXIT
      set -Eeuo pipefail
      rollback_internal "$ACTIVE_BACKUP" automatic
    )
    rollback_rc=$?
    set -e
    if [[ "$rollback_rc" != "0" ]]; then
      printf 'AUTOMATIC_ROLLBACK_FAILED backup=%s rollback_rc=%s original_rc=%s\n' \
        "$ACTIVE_BACKUP" "$rollback_rc" "$original_rc" >&2
    fi
  fi
  exit "$original_rc"
}

run_apply() {
  ensure_apply_preconditions
  mkdir -p "$(dirname "$LOCK_FILE")"
  exec 9>"$LOCK_FILE"
  flock -n 9 || die "another common-upload repair is running"

  local stamp backup preflight_report post_report blocking after_scope_tmp oa_check_tmp
  stamp="$(date +%Y%m%dT%H%M%S)"
  preflight_report="$(mktemp -d "/tmp/${SCRIPT_ID}.preflight.XXXXXX")"
  run_audit "$preflight_report"
  blocking="$(summary_value "$preflight_report/summary.json" blocking)"
  [[ "$blocking" == "0" ]] || die "preflight manifest has $blocking blocking issue(s): $preflight_report"

  backup="$BACKUP_BASE/${SCRIPT_ID}-${stamp}"
  [[ ! -e "$backup" ]] || die "backup directory already exists: $backup"
  mkdir -p "$backup"
  chmod 700 "$backup"
  cp -a "$preflight_report/." "$backup/preflight"
  record_release_state "$backup"
  backup_config_and_directories "$backup"
  record_compatibility_state "$backup" "$stamp"
  printf '%s\n' "$SCRIPT_ID" >"$backup/REPAIR_ID"
  printf '%s\n' "$RELEASE_ROOT" >"$backup/RELEASE_ROOT"
  printf '%s\n' "$TARGET_ROOT" >"$backup/TARGET_ROOT"
  printf '%s\n' "$(date --iso-8601=seconds)" >"$backup/APPLY_STARTED"
  write_phase "$backup" BACKUP_SEALED
  sync
  seal_pre_stop_bundle "$backup"
  validate_backup_bundle "$backup"
  ACTIVE_BACKUP="$backup"
  PROCESS_STATE="APPLY_ARMED"
  DYNAMIC_SEAL_ENABLED=1
  seal_rollback_state "$backup" "DYNAMIC_ROLLBACK_STATE.sha256" dynamic

  assert_release_unchanged "$backup"
  write_phase "$backup" STOPPING_SERVICES
  systemctl stop "$OA_SERVICE"
  systemctl stop "$FILE_SERVICE"
  write_phase "$backup" SERVICES_STOPPED
  assert_release_unchanged "$backup"
  assert_config_apply_stage "$backup" after_services_stopped PRE PRE PRE
  private_fingerprint "$backup/private-sign-package.before-apply.fingerprint"
  cmp -s "$backup/preflight/private-sign-package.fingerprint" \
    "$backup/private-sign-package.before-apply.fingerprint" \
    || die "private/sign-package changed between preflight and service stop"
  check_no_inflight "$backup/inflight.after-stop.tsv"
  backup_signing_database "$backup"
  seal_rollback_state "$backup" "DB_BACKUP.sha256" database
  validate_rollback_state_manifest "$backup" "DB_BACKUP.sha256" database
  write_phase "$backup" DB_BACKUP_SEALED

  assert_release_unchanged "$backup"
  write_phase "$backup" MUTATION_STARTED
  PROCESS_STATE="APPLY_MUTATING"
  ensure_required_storage_directories "$backup"
  verify_required_storage_directories
  restore_manifest_files "$backup/preflight/manifest.tsv" "$backup/applied-files.tsv" "$backup"
  update_env_file "$backup"
  install_systemd_guards "$backup"
  switch_compatibility_paths "$backup/compat/links.tsv" "$backup"
  verify_compatibility_paths "$backup/compat/links.tsv"
  record_applied_config_state "$backup"

  private_fingerprint "$backup/private-sign-package.after-mutation.fingerprint"
  cmp -s "$backup/private-sign-package.before-apply.fingerprint" "$backup/private-sign-package.after-mutation.fingerprint" \
    || die "private/sign-package changed during apply"

  scope_fingerprint "$backup/scope.before-service-start.fingerprint"
  printf 'MUTATION_APPLIED\n' >"$backup/MUTATION_EXPECTED_PHASE"
  seal_rollback_state "$backup" "MUTATION_ROLLBACK.sha256" mutation
  validate_rollback_state_manifest "$backup" "MUTATION_ROLLBACK.sha256" mutation
  chmod 0400 "$backup/created-directories.tsv" "$backup/temporary-paths.tsv" \
    "$backup/file-intents.tsv" "$backup/applied-files.tsv"
  write_phase "$backup" MUTATION_APPLIED
  write_phase "$backup" SERVICES_STARTING
  PROCESS_STATE="APPLY_SERVICES_STARTING"
  assert_release_unchanged "$backup"
  systemctl start "$FILE_SERVICE"
  wait_for_health "$FILE_SERVICE" "$FILE_HEALTH_URL" "$backup"
  assert_release_unchanged "$backup"
  systemctl start "$OA_SERVICE"
  wait_for_health "$OA_SERVICE" "$OA_HEALTH_URL" "$backup"
  verify_runtime_env "$FILE_SERVICE"
  verify_runtime_env "$OA_SERVICE"
  verify_required_storage_directories
  test_failpoint runtime_after_services

  post_report="$backup/postcheck"
  run_audit "$post_report"
  [[ "$(summary_value "$post_report/summary.json" blocking)" == "0" ]] || die "postcheck has blocking issues"
  [[ "$(summary_value "$post_report/summary.json" restore)" == "0" ]] || die "postcheck still requires file restoration"
  cmp -s "$backup/private-sign-package.before-apply.fingerprint" "$post_report/private-sign-package.fingerprint" \
    || die "postcheck detected a private/sign-package fingerprint change"
  verify_all_manifest_targets "$post_report/manifest.tsv"
  verify_required_storage_directories
  verify_public_binary_responses "$post_report/manifest.tsv" "$backup"
  oa_check_tmp="$backup/OA_BUSINESS_POSTCHECK.tmp.$$"
  if [[ -n "$OA_BEARER_TOKEN" ]]; then
    verify_oa_business_json "$backup"
    verify_template_business_reads "$post_report/manifest.tsv" "$backup"
    printf 'VERIFIED_WITH_TOKEN\n' >"$oa_check_tmp"
    log "OA_BUSINESS_POSTCHECK VERIFIED_WITH_TOKEN"
  else
    printf 'DEFERRED_TO_SAFARI_SAME_ORIGIN\n' >"$oa_check_tmp"
    log "OA_BUSINESS_POSTCHECK DEFERRED_TO_SAFARI_SAME_ORIGIN backup=$backup"
  fi
  mv -f -- "$oa_check_tmp" "$backup/OA_BUSINESS_POSTCHECK"
  verify_runtime_env "$FILE_SERVICE"
  verify_runtime_env "$OA_SERVICE"
  after_scope_tmp="$backup/scope.after-apply.fingerprint.tmp.$$"
  scope_fingerprint "$after_scope_tmp"
  cmp -s "$backup/scope.before-service-start.fingerprint" "$after_scope_tmp" \
    || die "public/sign-template changed while postchecks were running"
  mv -f -- "$after_scope_tmp" "$backup/scope.after-apply.fingerprint"

  assert_release_unchanged "$backup"
  assert_config_apply_stage "$backup" before_apply_complete APPLIED APPLIED APPLIED
  printf '%s\n' "$(date --iso-8601=seconds)" >"$backup/APPLY_COMPLETE"
  printf 'APPLY_COMPLETE\n' >"$backup/POST_APPLY_EXPECTED_PHASE"
  validate_rollback_state_manifest "$backup" "MUTATION_ROLLBACK.sha256" mutation
  assert_release_unchanged "$backup"
  write_phase "$backup" APPLY_COMPLETE
  seal_rollback_state "$backup" "POST_APPLY_ROLLBACK_STATE.sha256" complete
  validate_rollback_state_manifest "$backup" "DYNAMIC_ROLLBACK_STATE.sha256" dynamic
  validate_rollback_state_manifest "$backup" "DB_BACKUP.sha256" database
  validate_rollback_state_manifest "$backup" "MUTATION_ROLLBACK.sha256" mutation
  validate_rollback_state_manifest "$backup" "POST_APPLY_ROLLBACK_STATE.sha256" complete
  assert_checkpoint_seal_consistency "$backup" APPLY_COMPLETE
  DYNAMIC_SEAL_ENABLED=0
  PROCESS_STATE="APPLY_COMPLETE"
  unset OA_BEARER_TOKEN
  log "APPLY_OK target=$TARGET_ROOT backup=$backup rollback_token=$ROLLBACK_TOKEN"
}

run_manual_rollback() {
  ensure_rollback_preconditions
  mkdir -p "$(dirname "$LOCK_FILE")"
  exec 9>"$LOCK_FILE"
  flock -n 9 || die "another common-upload repair is running"

  local backup resolved_backup resolved_base
  [[ -d "$ROLLBACK_DIR" && ! -L "$ROLLBACK_DIR" ]] || die "rollback bundle is missing or unsafe: $ROLLBACK_DIR"
  resolved_backup="$(readlink -f "$ROLLBACK_DIR")"
  resolved_base="$(readlink -f "$BACKUP_BASE")"
  [[ "$resolved_backup" == "$resolved_base"/* ]] || die "rollback bundle must be under $BACKUP_BASE"
  backup="$resolved_backup"
  [[ "$(<"$backup/REPAIR_ID")" == "$SCRIPT_ID" ]] || die "rollback bundle has the wrong repair ID"
  [[ -f "$backup/APPLY_STARTED" && ! -f "$backup/ROLLED_BACK" ]] \
    || die "rollback bundle is not in a rollback-ready or recovery-ready state"
  PROCESS_STATE="MANUAL_ROLLBACK"
  rollback_internal "$backup" manual
}

trap on_process_exit EXIT

case "$MODE" in
  audit)
    if [[ -z "$OUTPUT_DIR" ]]; then
      OUTPUT_DIR="$(mktemp -d "/tmp/${SCRIPT_ID}.audit.XXXXXX")"
    else
      [[ ! -e "$OUTPUT_DIR" ]] || die "audit output already exists: $OUTPUT_DIR"
    fi
    run_audit "$OUTPUT_DIR"
    [[ "$(summary_value "$OUTPUT_DIR/summary.json" blocking)" == "0" ]] \
      || die "audit found blocking issues; inspect $OUTPUT_DIR"
    ;;
  apply)
    run_apply
    ;;
  rollback)
    run_manual_rollback
    ;;
esac
