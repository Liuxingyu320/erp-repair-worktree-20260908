#!/usr/bin/env bash

# Unified-todo / warehouse release gate.
#
# Safe default (no database access):
#   ./scripts/verify-unified-todo-release.sh --static
#
# Read-only target preflight:
#   ERP_UNIFIED_TODO_MYSQL_DATABASE=BossERP_NEW \
#   ERP_UNIFIED_TODO_MYSQL_HOST=127.0.0.1 \
#   ERP_UNIFIED_TODO_MYSQL_PORT=3306 \
#   ERP_UNIFIED_TODO_MYSQL_USER=root \
#   ./scripts/verify-unified-todo-release.sh --preflight
#
# Apply is deliberately explicit and fail-closed:
#   ERP_UNIFIED_TODO_APPLY_CONFIRMATION=APPLY_UNIFIED_TODO_20260714 \
#   ERP_UNIFIED_TODO_MYSQL_DATABASE=... \
#   ./scripts/verify-unified-todo-release.sh --apply

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIST_FILE="${ROOT_DIR}/scripts/unified-todo-release-20260714.list"
SOURCE_DIR="${ROOT_DIR}/sql"
DEPLOY_DIR="${ROOT_DIR}/docker/mysql/db"
LOCK_NAME="erp:unified-todo:20260714"
APPLY_CONFIRMATION="APPLY_UNIFIED_TODO_20260714"
MODE="${1:---static}"
MIGRATIONS=()
MYSQL_COMMAND=()
MYSQL_ARGS=()
DATABASE=""

EXPECTED_MIGRATIONS=(
    "erp_inventory_purchase_return_item_20260713.sql"
    "erp_inventory_stock_check_scope_20260713.sql"
    "erp_inventory_receipt_quality_20260713.sql"
    "erp_inventory_performance_indexes_20260713.sql"
    "erp_inventory_warehouse_navigation_20260713.sql"
    "erp_hr_health_certificate_20260713.sql"
)

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    printf '[PASS] %s\n' "$*"
}

info()
{
    printf '[INFO] %s\n' "$*"
}

usage()
{
    printf '%s\n' \
        'Usage: verify-unified-todo-release.sh [--static|--checksums|--preflight|--apply]' \
        '' \
        '  --static      Validate the ordered release contract without database access.' \
        '  --checksums   Print SHA-256 values for the validated migration set.' \
        '  --preflight   Run read-only MySQL 8, schema, menu, config, and OA activity gates.' \
        '  --apply       Run preflight and then apply the ordered set under a named lock.'
}

load_release_list()
{
    local raw
    local name
    local index

    [[ -r "${LIST_FILE}" ]] || fail "release list is not readable: ${LIST_FILE}"
    while IFS= read -r raw || [[ -n "${raw}" ]]; do
        name="${raw%$'\r'}"
        [[ -z "${name}" ]] && continue
        [[ "${name}" == \#* ]] && continue
        [[ "${name}" =~ ^[A-Za-z0-9._-]+\.sql$ ]] \
            || fail "unsafe migration filename in release list: ${name}"
        MIGRATIONS+=("${name}")
    done < "${LIST_FILE}"

    [[ "${#MIGRATIONS[@]}" -eq "${#EXPECTED_MIGRATIONS[@]}" ]] \
        || fail "release list must contain exactly ${#EXPECTED_MIGRATIONS[@]} migrations; found ${#MIGRATIONS[@]}"

    for index in "${!EXPECTED_MIGRATIONS[@]}"; do
        [[ "${MIGRATIONS[$index]}" == "${EXPECTED_MIGRATIONS[$index]}" ]] \
            || fail "migration order mismatch at position $((index + 1)): expected ${EXPECTED_MIGRATIONS[$index]}, found ${MIGRATIONS[$index]}"
    done

    if [[ "$(printf '%s\n' "${MIGRATIONS[@]}" | sort | uniq -d | wc -l | tr -d ' ')" != '0' ]]; then
        fail 'release list contains duplicate migration filenames'
    fi
}

validate_static_contract()
{
    local name
    local source_file

    load_release_list
    for name in "${MIGRATIONS[@]}"; do
        source_file="${SOURCE_DIR}/${name}"
        [[ -r "${source_file}" ]] || fail "source migration is not readable: ${source_file}"
    done

    cmp -s \
        "${SOURCE_DIR}/erp_hr_health_certificate_20260713.sql" \
        "${DEPLOY_DIR}/erp_hr_health_certificate_20260713.sql" \
        || fail 'source/deployment migration mismatch: erp_hr_health_certificate_20260713.sql'

    [[ -r "${SOURCE_DIR}/erp_inventory_warehouse_navigation_rollback_20260713.sql" ]] \
        || fail 'warehouse navigation rollback is missing'
    grep -Fq "idx_stock_check_counter_due (counter_user_id, status, deadline)" \
        "${SOURCE_DIR}/erp_inventory_stock_check_scope_20260713.sql" \
        || fail 'stock-check responsibility index contract is missing'
    grep -Fq "sys_menu_warehouse_nav_backup_20260713" \
        "${SOURCE_DIR}/erp_inventory_warehouse_navigation_20260713.sql" \
        || fail 'warehouse navigation menu snapshot contract is missing'
    grep -Fq "@warehouse_nav_snapshot_exists = 0" \
        "${SOURCE_DIR}/erp_inventory_warehouse_navigation_20260713.sql" \
        || fail 'warehouse navigation role snapshot must be frozen after its first transaction'
    grep -Fq "menu.icon = backup.icon" \
        "${SOURCE_DIR}/erp_inventory_warehouse_navigation_rollback_20260713.sql" \
        || fail 'warehouse navigation rollback must restore the exact menu snapshot'
    grep -Fq "shop_dept_id, status, deadline" \
        "${SOURCE_DIR}/erp_inventory_performance_indexes_20260713.sql" \
        || fail 'performance migration no longer depends on the stock-check deadline field'
    grep -Fq "feature.hr.health-certificate.enabled', 'false'" \
        "${SOURCE_DIR}/erp_hr_health_certificate_20260713.sql" \
        || fail 'health-certificate intake must default off'
    pass "${#EXPECTED_MIGRATIONS[@]} migrations are present in the only supported release order"
    pass 'health-certificate Docker copy is byte-identical'
    pass 'release defaults are fail-closed and rollback scripts are present'
    info 'OA purchase and Flowable cutover is superseded by verify-unified-approval-cutover.sh'
}

configure_mysql()
{
    local mysql_bin="${ERP_UNIFIED_TODO_MYSQL_BIN:-mysql}"
    local defaults_file="${ERP_UNIFIED_TODO_MYSQL_DEFAULTS_FILE:-}"
    local host="${ERP_UNIFIED_TODO_MYSQL_HOST:-}"
    local port="${ERP_UNIFIED_TODO_MYSQL_PORT:-}"
    local user="${ERP_UNIFIED_TODO_MYSQL_USER:-}"
    local password="${ERP_UNIFIED_TODO_MYSQL_PASSWORD:-}"

    DATABASE="${ERP_UNIFIED_TODO_MYSQL_DATABASE:-}"
    command -v "${mysql_bin}" >/dev/null 2>&1 || fail "mysql client is required: ${mysql_bin}"
    [[ -n "${DATABASE}" ]] || fail 'ERP_UNIFIED_TODO_MYSQL_DATABASE is required'
    case "${DATABASE}" in
        information_schema|mysql|performance_schema|sys)
            fail "system database is not an allowed release target: ${DATABASE}"
            ;;
    esac

    MYSQL_COMMAND=("${mysql_bin}")
    if [[ -n "${defaults_file}" ]]; then
        [[ -r "${defaults_file}" ]] || fail "MySQL defaults file is not readable: ${defaults_file}"
        MYSQL_COMMAND+=("--defaults-extra-file=${defaults_file}")
    fi

    MYSQL_ARGS=(--batch --raw --skip-column-names --default-character-set=utf8mb4)
    if [[ -n "${host}" ]]; then
        MYSQL_ARGS+=(--protocol=TCP --host="${host}")
    fi
    [[ -z "${port}" ]] || MYSQL_ARGS+=(--port="${port}")
    [[ -z "${user}" ]] || MYSQL_ARGS+=(--user="${user}")
    MYSQL_ARGS+=(--database="${DATABASE}")

    export MYSQL_PWD="${password}"
    trap 'unset MYSQL_PWD' EXIT
}

query_scalar()
{
    local sql="$1"
    "${MYSQL_COMMAND[@]}" "${MYSQL_ARGS[@]}" --execute="${sql}" | tr -d '\r' | tail -n 1
}

run_preflight()
{
    local failures=0
    local value
    local required_tables=14
    local required_menu_ids=9

    configure_mysql

    value="$(query_scalar "SELECT CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED)")"
    if [[ ! "${value}" =~ ^[0-9]+$ ]] || (( value < 8 )); then
        printf '[BLOCK] MySQL 8.0 or newer is required; server major=%s\n' "${value}" >&2
        failures=$((failures + 1))
    else
        pass "MySQL server major version is ${value}"
    fi

    value="$(query_scalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME) IN ('inv_purchase_return','inv_purchase_return_detail','inv_stock_check','inv_stock_check_detail','inv_inbound_record','inv_stock_log','inv_purchase_order','sys_menu','sys_role','sys_role_menu','sys_config','sys_user','sys_dept','sys_user_profile')")"
    if [[ "${value}" != "${required_tables}" ]]; then
        printf '[BLOCK] required base tables present=%s expected=%s\n' "${value}" "${required_tables}" >&2
        failures=$((failures + 1))
    else
        pass "all ${required_tables} required base tables exist"
    fi

    if (( failures > 0 )); then
        fail "preflight stopped before business queries; blockers=${failures}"
    fi

    value="$(query_scalar "SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (4040,4100,4300,4308,4400,4450,4460,4465,4470)")"
    if [[ "${value}" != "${required_menu_ids}" ]]; then
        printf '[BLOCK] warehouse navigation anchor menus present=%s expected=%s\n' "${value}" "${required_menu_ids}" >&2
        failures=$((failures + 1))
    else
        pass "all ${required_menu_ids} warehouse navigation anchor menus exist"
    fi

    value="$(query_scalar "SELECT COUNT(*) FROM sys_menu WHERE LOWER(COALESCE(component,''))='inventory/purchase/index' AND LOWER(COALESCE(perms,''))='inv:purchase:list' AND visible='0' AND status='0'")"
    if [[ "${value}" -lt 1 ]]; then
        printf '[BLOCK] active inventory purchase anchor is missing; OA retirement must not proceed\n' >&2
        failures=$((failures + 1))
    else
        pass 'active inventory purchase anchor is protected'
    fi

    value="$(query_scalar "SELECT COUNT(*) FROM (SELECT config_key FROM sys_config WHERE config_key IN ('todo.stock-check.due-soon.hours','feature.hr.health-certificate.enabled','todo.health-certificate.warning-days') GROUP BY config_key HAVING COUNT(*) > 1) duplicate_config")"
    if [[ "${value}" != "0" ]]; then
        printf '[BLOCK] duplicate release configuration keys=%s\n' "${value}" >&2
        failures=$((failures + 1))
    else
        pass 'release configuration keys are unique'
    fi

    value="$(query_scalar "SELECT COUNT(*) FROM inv_stock_check WHERE LOWER(status) IN ('draft','rejected','invalidated')")"
    info "active stock checks requiring post-schema governance=${value}"

    (( failures == 0 )) || fail "release preflight blockers=${failures}"
    pass "read-only preflight passed for ${DATABASE}"
}

print_checksums()
{
    local checksum_bin
    local name

    validate_static_contract
    if command -v sha256sum >/dev/null 2>&1; then
        checksum_bin=(sha256sum)
    elif command -v shasum >/dev/null 2>&1; then
        checksum_bin=(shasum -a 256)
    else
        fail 'sha256sum or shasum is required'
    fi

    for name in "${MIGRATIONS[@]}"; do
        "${checksum_bin[@]}" "${SOURCE_DIR}/${name}"
    done
}

apply_release()
{
    local lock_timeout="${ERP_UNIFIED_TODO_MIGRATION_LOCK_TIMEOUT_SECONDS:-30}"
    local name

    [[ "${ERP_UNIFIED_TODO_APPLY_CONFIRMATION:-}" == "${APPLY_CONFIRMATION}" ]] \
        || fail "--apply requires ERP_UNIFIED_TODO_APPLY_CONFIRMATION=${APPLY_CONFIRMATION}"
    [[ "${lock_timeout}" =~ ^[0-9]+$ ]] || fail 'lock timeout must be a non-negative integer'

    validate_static_contract
    run_preflight

    {
        printf "SET @erp_unified_todo_lock_status = GET_LOCK('%s', %s);\n" \
            "${LOCK_NAME}" "${lock_timeout}"
        printf '%s\n' \
            "SET @erp_unified_todo_lock_guard = IF(@erp_unified_todo_lock_status = 1, 'DO 0', 'SELECT * FROM __erp_unified_todo_migration_lock_not_acquired__');" \
            'PREPARE erp_unified_todo_lock_stmt FROM @erp_unified_todo_lock_guard;' \
            'EXECUTE erp_unified_todo_lock_stmt;' \
            'DEALLOCATE PREPARE erp_unified_todo_lock_stmt;'
        for name in "${MIGRATIONS[@]}"; do
            printf '\n-- BEGIN %s\n' "${name}"
            cat "${SOURCE_DIR}/${name}"
            printf '\n-- END %s\n' "${name}"
        done
        printf "SELECT RELEASE_LOCK('%s');\n" "${LOCK_NAME}"
    } | "${MYSQL_COMMAND[@]}" "${MYSQL_ARGS[@]}" --show-warnings \
        || fail 'ordered migration apply failed; connection close releases the named lock'

    pass "applied ${#MIGRATIONS[@]} ordered migrations to ${DATABASE} under ${LOCK_NAME}"
}

case "${MODE}" in
    --static)
        validate_static_contract
        ;;
    --checksums)
        print_checksums
        ;;
    --preflight)
        validate_static_contract
        run_preflight
        ;;
    --apply)
        apply_release
        ;;
    --help|-h)
        usage
        ;;
    *)
        usage >&2
        fail "unknown mode: ${MODE}"
        ;;
esac
