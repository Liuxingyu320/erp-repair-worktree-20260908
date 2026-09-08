#!/usr/bin/env bash

# Fail-closed gate for the 2026-07-14 signing final-confirmation cutover.
# Static mode is repository-only. Preflight is read-only and must be run against
# every deployment target before code without the legacy HR-confirm endpoint is
# released.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION="erp_oa_sign_final_confirmation_20260714.sql"
SOURCE_SQL="$ROOT_DIR/sql/$MIGRATION"
DEPLOY_SQL="$ROOT_DIR/docker/mysql/db/$MIGRATION"
VERSIONED_SQL="$ROOT_DIR/docker/mysql/releases/sign-final-confirmation-20260714/$MIGRATION"
SOURCE_MANIFEST="$ROOT_DIR/scripts/sign-final-confirmation-release-20260714.json"
DEPLOY_MANIFEST="$ROOT_DIR/docker/release/sign-final-confirmation-release-20260714.json"
SOURCE_LIST="$ROOT_DIR/scripts/sign-final-confirmation-migrations-20260714.list"
DEPLOY_LIST="$ROOT_DIR/docker/release/sign-final-confirmation-migrations-20260714.list"
BOOTSTRAP_LIST="$ROOT_DIR/docker/mysql/bootstrap-files.list"
MODE="${1:---static}"
MYSQL_COMMAND=()
MYSQL_ARGS=()

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    printf '[PASS] %s\n' "$*"
}

usage()
{
    printf '%s\n' \
        'Usage: verify-sign-final-confirmation-cutover.sh [--source-only-static|--static|--preflight]' \
        '' \
        '  --source-only-static  Validate source artifacts without Docker release mirrors.' \
        '  --static      Validate migration mirrors and the removed HR-confirm contract.' \
        '  --preflight   Also verify the target schema and require WAITING_HR_CONFIRM=0.'
}

validate_static_contract()
{
    local require_deploy_mirrors="${1:-true}"

    [[ -r "$SOURCE_SQL" ]] || fail "missing source migration: $SOURCE_SQL"
    [[ -r "$SOURCE_MANIFEST" ]] \
        || fail 'sign final-confirmation source release manifest is missing'
    [[ -r "$SOURCE_LIST" ]] \
        || fail 'sign final-confirmation source release list is missing'

    if [[ "$require_deploy_mirrors" == 'true' ]]; then
        [[ -r "$DEPLOY_SQL" ]] || fail "missing deployment migration: $DEPLOY_SQL"
        [[ -r "$VERSIONED_SQL" ]] || fail "missing versioned migration: $VERSIONED_SQL"
        [[ -r "$DEPLOY_MANIFEST" ]] \
            || fail 'sign final-confirmation deployment manifest is missing'
        [[ -r "$DEPLOY_LIST" ]] \
            || fail 'sign final-confirmation deployment list is missing'
        [[ -r "$BOOTSTRAP_LIST" ]] || fail "missing bootstrap list: $BOOTSTRAP_LIST"
        cmp -s "$SOURCE_SQL" "$DEPLOY_SQL" \
            || fail 'sign final-confirmation source and Docker mirror differ'
        cmp -s "$SOURCE_SQL" "$VERSIONED_SQL" \
            || fail 'sign final-confirmation source and versioned release differ'
        cmp -s "$SOURCE_MANIFEST" "$DEPLOY_MANIFEST" \
            || fail 'sign final-confirmation manifest and Docker mirror differ'
        cmp -s "$SOURCE_LIST" "$DEPLOY_LIST" \
            || fail 'sign final-confirmation list and Docker mirror differ'
        grep -Fxq "$MIGRATION" "$BOOTSTRAP_LIST" \
            || fail 'sign final-confirmation migration is absent from the explicit bootstrap list'
    fi

    PYTHONPATH="$ROOT_DIR/scripts" python3 - \
        "$SOURCE_MANIFEST" "$MIGRATION" "$require_deploy_mirrors" <<'PY'
import sys
from pathlib import Path

from release_migration_contract import (
    load_and_validate_manifest,
    sign_column_fingerprint_contract,
)

manifest = load_and_validate_manifest(
    Path(sys.argv[1]), require_deploy_mirror=sys.argv[3] == "true"
)
if manifest.get("releaseId") != "sign-final-confirmation-20260714":
    raise SystemExit("[FAIL] unexpected sign final-confirmation releaseId")
if manifest.get("executionPolicy") != "manual-phased":
    raise SystemExit("[FAIL] sign final-confirmation cutover must remain manual-phased")
if [item.get("file") for item in manifest.get("migrations", [])] != [sys.argv[2]]:
    raise SystemExit("[FAIL] sign final-confirmation release must declare exactly its cutover migration")
conditions, count = sign_column_fingerprint_contract()
if count != 64 or "character_maximum_length=500" not in conditions:
    raise SystemExit("[FAIL] sign final-confirmation column fingerprint is incomplete")
PY

    python3 - "$SOURCE_SQL" <<'PY'
import re
import sys
from pathlib import Path

sql = Path(sys.argv[1]).read_text(encoding="utf-8")
package_cutover = re.search(
    r"UPDATE\s+oa_sign_package\s+p\s+JOIN\s+oa_sign_task\s+t"
    r"[\s\S]*?SET\s+p\.confirm_status\s*=\s*'NOT_REQUIRED'"
    r"[\s\S]*?WHERE\s+t\.status\s*=\s*'WAITING_HR_CONFIRM'",
    sql,
    re.IGNORECASE,
)
task_cutover = re.search(
    r"UPDATE\s+oa_sign_task\s+SET\s+status\s*=\s*'READY_TO_SEND'"
    r"[\s\S]*?WHERE\s+status\s*=\s*'WAITING_HR_CONFIRM'",
    sql,
    re.IGNORECASE,
)
if package_cutover is None or task_cutover is None:
    raise SystemExit(
        "[FAIL] migration must move legacy HR-confirm tasks to no-review READY_TO_SEND"
    )
PY

    local controller="$ROOT_DIR/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java"
    local api="$ROOT_DIR/erp-ui/src/api/oa/signTask.js"
    [[ -r "$controller" && -r "$api" ]] \
        || fail 'sign task controller/API sources are missing'
    python3 - "$controller" <<'PY'
import re
import sys
from pathlib import Path

source = Path(sys.argv[1]).read_text(encoding="utf-8")
if re.search(
    r"@PostMapping\s*\([\s\S]{0,240}?/\{taskId\}/confirm[\s\S]{0,80}?\)",
    source,
):
    raise SystemExit("[FAIL] legacy HR-confirm controller endpoint must remain retired")
PY
    if grep -Eq "/oa/signTask/.*confirm" "$api"; then
        fail 'legacy HR-confirm frontend API must remain retired'
    fi

    if [[ "$require_deploy_mirrors" == 'true' ]]; then
        pass 'sign final-confirmation versioned release, bootstrap mirror and metadata are byte-identical'
    else
        pass 'sign final-confirmation source artifacts are valid; Docker release mirrors were not checked'
    fi
    pass 'legacy WAITING_HR_CONFIRM rows are migrated and the retired endpoint stays absent'
}

configure_mysql()
{
    local mysql_bin="${ERP_SIGN_CUTOVER_MYSQL_BIN:-mysql}"
    local defaults_file="${ERP_SIGN_CUTOVER_MYSQL_DEFAULTS_FILE:-}"
    local database="${ERP_SIGN_CUTOVER_MYSQL_DATABASE:-}"
    local host="${ERP_SIGN_CUTOVER_MYSQL_HOST:-}"
    local port="${ERP_SIGN_CUTOVER_MYSQL_PORT:-}"
    local user="${ERP_SIGN_CUTOVER_MYSQL_USER:-}"
    local password="${ERP_SIGN_CUTOVER_MYSQL_PASSWORD:-}"

    command -v "$mysql_bin" >/dev/null 2>&1 \
        || fail "mysql client is required: $mysql_bin"
    [[ -n "$database" ]] || fail 'ERP_SIGN_CUTOVER_MYSQL_DATABASE is required'
    case "$database" in
        information_schema|mysql|performance_schema|sys)
            fail "system database is not an allowed target: $database"
            ;;
    esac

    MYSQL_COMMAND=("$mysql_bin")
    if [[ -n "$defaults_file" ]]; then
        [[ -r "$defaults_file" ]] \
            || fail "MySQL defaults file is not readable: $defaults_file"
        MYSQL_COMMAND+=("--defaults-extra-file=$defaults_file")
    fi
    MYSQL_ARGS=(--batch --raw --skip-column-names --default-character-set=utf8mb4)
    [[ -z "$host" ]] || MYSQL_ARGS+=(--protocol=TCP "--host=$host")
    [[ -z "$port" ]] || MYSQL_ARGS+=("--port=$port")
    [[ -z "$user" ]] || MYSQL_ARGS+=("--user=$user")
    MYSQL_ARGS+=("--database=$database")

    export MYSQL_PWD="$password"
    trap 'unset MYSQL_PWD' EXIT
}

query_scalar()
{
    local sql="$1"
    "${MYSQL_COMMAND[@]}" "${MYSQL_ARGS[@]}" --execute="$sql" \
        | tr -d '\r' | tail -n 1
}

run_preflight()
{
    local value fingerprint_conditions
    configure_mysql

    value="$(query_scalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('sys_legal_entity','oa_sign_final_confirmation','oa_sign_final_confirmation_document')")"
    [[ "$value" == '3' ]] \
        || fail "sign final-confirmation schema evidence is incomplete: created_tables=$value expected=3"

    value="$(query_scalar "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='sys_legal_entity' AND COLUMN_NAME IN ('legal_entity_id','legal_entity_code','legal_entity_name','unified_social_credit_code','registered_address','legal_representative','contact_phone','status','version','create_by','create_time','update_by','update_time','remark')) OR (TABLE_NAME='oa_sign_final_confirmation' AND COLUMN_NAME IN ('confirmation_id','package_id','employee_id','final_document_version','document_root_hash','confirmation_text','identity_method','request_id','ip_address','user_agent','confirmed_time','create_time')) OR (TABLE_NAME='oa_sign_final_confirmation_document' AND COLUMN_NAME IN ('confirmation_document_id','confirmation_id','package_id','document_id','final_document_version','final_pdf_hash','create_time')))")"
    [[ "$value" == '33' ]] \
        || fail "sign final-confirmation created tables are incomplete: columns=$value expected=33"

    value="$(query_scalar "SELECT COUNT(*) FROM (SELECT TABLE_NAME,INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS COLUMNS_CSV FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND NON_UNIQUE=0 AND ((TABLE_NAME='sys_legal_entity' AND INDEX_NAME IN ('PRIMARY','uk_sys_legal_entity_code')) OR (TABLE_NAME='oa_sign_final_confirmation' AND INDEX_NAME IN ('PRIMARY','uk_oa_sign_final_confirmation_request','uk_oa_sign_final_confirmation_version')) OR (TABLE_NAME='oa_sign_final_confirmation_document' AND INDEX_NAME IN ('PRIMARY','uk_oa_sign_final_confirmation_document'))) GROUP BY TABLE_NAME,INDEX_NAME) actual_keys WHERE (TABLE_NAME='sys_legal_entity' AND INDEX_NAME='PRIMARY' AND COLUMNS_CSV='legal_entity_id') OR (TABLE_NAME='sys_legal_entity' AND INDEX_NAME='uk_sys_legal_entity_code' AND COLUMNS_CSV='legal_entity_code') OR (TABLE_NAME='oa_sign_final_confirmation' AND INDEX_NAME='PRIMARY' AND COLUMNS_CSV='confirmation_id') OR (TABLE_NAME='oa_sign_final_confirmation' AND INDEX_NAME='uk_oa_sign_final_confirmation_request' AND COLUMNS_CSV='request_id') OR (TABLE_NAME='oa_sign_final_confirmation' AND INDEX_NAME='uk_oa_sign_final_confirmation_version' AND COLUMNS_CSV='package_id,final_document_version') OR (TABLE_NAME='oa_sign_final_confirmation_document' AND INDEX_NAME='PRIMARY' AND COLUMNS_CSV='confirmation_document_id') OR (TABLE_NAME='oa_sign_final_confirmation_document' AND INDEX_NAME='uk_oa_sign_final_confirmation_document' AND COLUMNS_CSV='confirmation_id,document_id')")"
    [[ "$value" == '7' ]] \
        || fail "sign final-confirmation created-table keys are incomplete: keys=$value expected=7"

    value="$(query_scalar "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='sys_dept' AND COLUMN_NAME IN ('legal_entity_id')) OR (TABLE_NAME='oa_company_seal_config' AND COLUMN_NAME IN ('legal_entity_id','seal_code','seal_type','is_default','seal_image_hash','valid_from','valid_to')) OR (TABLE_NAME='oa_sign_package' AND COLUMN_NAME IN ('legal_entity_source_dept_id','legal_entity_resolve_mode','legal_entity_credit_code_snapshot','legal_entity_address_snapshot','legal_representative_snapshot','legal_entity_phone_snapshot','legal_entity_override_reason','seal_id_snapshot','seal_name_snapshot','seal_image_url_snapshot','seal_image_hash_snapshot','initial_signed_time','final_document_version','final_document_root_hash','final_generated_time','final_confirmed_time','final_confirmation_status')) OR (TABLE_NAME='oa_sign_package_document' AND COLUMN_NAME IN ('final_pdf_url','final_pdf_hash','final_document_version','final_read_confirmed')))")"
    [[ "$value" == '29' ]] \
        || fail "sign final-confirmation added columns are incomplete: columns=$value expected=29"

    fingerprint_conditions="$(PYTHONPATH="$ROOT_DIR/scripts" python3 - <<'PY'
from release_migration_contract import sign_column_fingerprint_contract

conditions, count = sign_column_fingerprint_contract()
if count != 64:
    raise SystemExit("unexpected sign column fingerprint count")
print(conditions)
PY
)"
    [[ -n "$fingerprint_conditions" ]] \
        || fail 'sign final-confirmation column fingerprint contract is empty'
    value="$(query_scalar "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ($fingerprint_conditions)")"
    [[ "$value" == '64' ]] \
        || fail "sign final-confirmation column fingerprint is incomplete: columns=$value expected=64"

    value="$(query_scalar "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_plan_version' AND COLUMN_NAME IN ('legal_entity_id','legal_entity_name') AND IS_NULLABLE='YES'")"
    [[ "$value" == '2' ]] \
        || fail "sign final-confirmation plan entity columns are not nullable"

    value="$(query_scalar "SELECT COUNT(*) FROM (SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS COLUMNS_CSV FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='sys_dept' AND INDEX_NAME='idx_sys_dept_legal_entity') OR (TABLE_NAME='oa_company_seal_config' AND INDEX_NAME='idx_oa_company_seal_entity')) GROUP BY TABLE_NAME,INDEX_NAME,NON_UNIQUE) actual_indexes WHERE (TABLE_NAME='sys_dept' AND INDEX_NAME='idx_sys_dept_legal_entity' AND COLUMNS_CSV='legal_entity_id' AND NON_UNIQUE=1) OR (TABLE_NAME='oa_company_seal_config' AND INDEX_NAME='idx_oa_company_seal_entity' AND COLUMNS_CSV='legal_entity_id,status,is_default' AND NON_UNIQUE=1)")"
    [[ "$value" == '2' ]] \
        || fail "sign final-confirmation indexes are incomplete: indexes=$value expected=2"

    value="$(query_scalar "SELECT COUNT(*) FROM oa_sign_task WHERE status='WAITING_HR_CONFIRM'")"
    [[ "$value" == '0' ]] \
        || fail "legacy WAITING_HR_CONFIRM tasks remain without a UI handler: count=$value"

    pass 'target contains the complete final-confirmation schema and WAITING_HR_CONFIRM=0'
}

case "$MODE" in
    --source-only-static)
        validate_static_contract false
        ;;
    --static)
        validate_static_contract true
        ;;
    --preflight)
        validate_static_contract true
        run_preflight
        ;;
    --help|-h)
        usage
        ;;
    *)
        usage >&2
        fail "unknown mode: $MODE"
        ;;
esac
