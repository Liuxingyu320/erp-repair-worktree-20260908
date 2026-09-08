#!/usr/bin/env bash

# New-business migration release gate.
#
# Static contract (safe for every developer machine):
#   ./scripts/verify-new-business-migrations.sh
#
# Apply the ordered migration set to an explicitly supplied database:
#   ERP_NEW_BUSINESS_MYSQL_DATABASE=erp \
#   ERP_NEW_BUSINESS_MYSQL_HOST=127.0.0.1 \
#   ERP_NEW_BUSINESS_MYSQL_PORT=3306 \
#   ERP_NEW_BUSINESS_MYSQL_USER=root \
#   ERP_NEW_BUSINESS_MYSQL_PASSWORD=secret \
#   ./scripts/verify-new-business-migrations.sh --apply
#
# The apply mode deliberately has no implicit local/default database. It runs all
# scripts in one mysql connection while holding a global named lock.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIST_FILE="${ROOT_DIR}/scripts/new-business-migrations-20260713.list"
MANIFEST_FILE="${ROOT_DIR}/scripts/new-business-release-20260714.json"
SOURCE_DIR="${ROOT_DIR}/sql"
DEPLOY_DIR="${ROOT_DIR}/docker/mysql/db"
LOCK_NAME="erp:new-business:20260713"
EXPECTED_COUNT=0
MODE="${1:---static}"
MIGRATIONS=()

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
        'Usage: verify-new-business-migrations.sh [--static|--apply]' \
        '' \
        '  --static  Validate release list, source/deploy parity and safe defaults.' \
        '  --apply   Run the validated list against an explicitly configured MySQL database.'
}

load_release_list()
{
    local raw
    local name

    command -v python3 >/dev/null 2>&1 || fail 'python3 is required'
    [[ -r "${MANIFEST_FILE}" ]] || fail "release manifest is not readable: ${MANIFEST_FILE}"
    EXPECTED_COUNT="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
count = manifest.get("migrationCount")
if isinstance(count, bool) or not isinstance(count, int) or count < 1:
    raise SystemExit("invalid migrationCount")
print(count)
PY
)" || fail 'release manifest migrationCount is invalid'
    [[ "${EXPECTED_COUNT}" =~ ^[1-9][0-9]*$ ]] \
        || fail 'release manifest migrationCount is invalid'
    [[ -r "${LIST_FILE}" ]] || fail "release list is not readable: ${LIST_FILE}"
    while IFS= read -r raw || [[ -n "${raw}" ]]; do
        name="${raw%$'\r'}"
        [[ -z "${name}" ]] && continue
        [[ "${name}" == \#* ]] && continue
        [[ "${name}" =~ ^[A-Za-z0-9._-]+\.sql$ ]] \
            || fail "unsafe migration filename in release list: ${name}"
        MIGRATIONS+=("${name}")
    done < "${LIST_FILE}"

    [[ "${#MIGRATIONS[@]}" -eq "${EXPECTED_COUNT}" ]] \
        || fail "release list must contain exactly ${EXPECTED_COUNT} migrations; found ${#MIGRATIONS[@]}"

    if [[ "$(printf '%s\n' "${MIGRATIONS[@]}" | sort | uniq -d | wc -l | tr -d ' ')" != '0' ]]; then
        fail 'release list contains duplicate migration filenames'
    fi
}

assert_feature_default_off()
{
    local key="$1"
    local file="$2"
    local line

    line="$(grep -F -A3 "'${key}'" "${file}" | head -4 | tr '\n' ' ' || true)"
    [[ -n "${line}" ]] || fail "feature flag insert is missing for ${key}: ${file}"
    [[ "${line}" == *"'false'"* ]] \
        || fail "feature flag must default to false for ${key}: ${file}"
    [[ "${line}" != *"'true'"* ]] \
        || fail "unsafe true default remains for ${key}: ${file}"
}

validate_static_contract()
{
    local name
    local source_file
    local deploy_file

    load_release_list
    for name in "${MIGRATIONS[@]}"; do
        source_file="${SOURCE_DIR}/${name}"
        deploy_file="${DEPLOY_DIR}/${name}"
        [[ -r "${source_file}" ]] || fail "source migration is not readable: ${source_file}"
        [[ -r "${deploy_file}" ]] || fail "deploy migration is not readable: ${deploy_file}"
        cmp -s "${source_file}" "${deploy_file}" \
            || fail "source/deploy migration mismatch: ${name}"
    done

    assert_feature_default_off 'feature.hr.health-certificate.enabled' \
        "${SOURCE_DIR}/erp_hr_health_certificate_20260713.sql"
    assert_feature_default_off 'feature.inventory.store-return.enabled' \
        "${SOURCE_DIR}/erp_inventory_store_return_20260713.sql"
    assert_feature_default_off 'feature.inventory.transfer-discrepancy.enabled' \
        "${SOURCE_DIR}/erp_inventory_transfer_discrepancy_20260713.sql"
    assert_feature_default_off 'feature.inventory.customer-service-card.enabled' \
        "${SOURCE_DIR}/erp_inventory_customer_service_card_20260713.sql"
    assert_feature_default_off 'feature.oa.purchase.enabled' \
        "${SOURCE_DIR}/erp_unified_approval_seed_20260716.sql"
    assert_feature_default_off 'feature.inventory.stock-check-native-approval.enabled' \
        "${SOURCE_DIR}/erp_inventory_unified_approval_expand_20260716.sql"
    assert_feature_default_off 'feature.inventory.transfer-native-approval.enabled' \
        "${SOURCE_DIR}/erp_inventory_unified_approval_expand_20260716.sql"

    for name in \
        erp_hr_health_certificate_unified_approval_20260714.sql \
        erp_inventory_unified_approval_expand_20260716.sql \
        erp_oa_purchase_unified_approval_expand_20260716.sql \
        erp_unified_approval_schema_20260716.sql \
        erp_unified_approval_seed_20260716.sql; do
        printf '%s\n' "${MIGRATIONS[@]}" | grep -Fxq "${name}" \
            || fail "approval dependency is absent from release list: ${name}"
    done
    for name in \
        erp_inventory_unified_approval_cutover_20260714.sql \
        erp_unified_approval_center_20260714.sql \
        erp_oa_flowable_test_data_purge_20260714.sql; do
        if printf '%s\n' "${MIGRATIONS[@]}" | grep -Fxq "${name}"; then
            fail "manual or destructive approval migration entered automatic release: ${name}"
        fi
    done

    pass "${EXPECTED_COUNT} migrations are ordered, mirrored byte-for-byte, and default off"
}

apply_release()
{
    local mysql_bin="${ERP_NEW_BUSINESS_MYSQL_BIN:-mysql}"
    local database="${ERP_NEW_BUSINESS_MYSQL_DATABASE:-}"
    local host="${ERP_NEW_BUSINESS_MYSQL_HOST:-}"
    local port="${ERP_NEW_BUSINESS_MYSQL_PORT:-}"
    local user="${ERP_NEW_BUSINESS_MYSQL_USER:-}"
    local password="${ERP_NEW_BUSINESS_MYSQL_PASSWORD:-}"
    local lock_timeout="${ERP_NEW_BUSINESS_MIGRATION_LOCK_TIMEOUT_SECONDS:-30}"
    local name
    local mysql_args=(--batch --show-warnings --default-character-set=utf8mb4)

    command -v "${mysql_bin}" >/dev/null 2>&1 || fail "mysql client is required: ${mysql_bin}"
    [[ -n "${database}" ]] || fail 'ERP_NEW_BUSINESS_MYSQL_DATABASE is required for --apply'
    [[ "${lock_timeout}" =~ ^[0-9]+$ ]] || fail 'lock timeout must be a non-negative integer'

    [[ -n "${host}" ]] && mysql_args+=(--host="${host}")
    [[ -n "${port}" ]] && mysql_args+=(--port="${port}")
    [[ -n "${user}" ]] && mysql_args+=(--user="${user}")
    mysql_args+=(--database="${database}")

    # MYSQL_PWD prevents the password from appearing in the process list. The
    # release environment should still prefer an option file/secret injection.
    export MYSQL_PWD="${password}"
    {
        printf "SET @erp_new_business_lock_status = GET_LOCK('%s', %s);\n" \
            "${LOCK_NAME}" "${lock_timeout}"
        printf '%s\n' \
            "SET @erp_new_business_lock_guard = IF(@erp_new_business_lock_status = 1, 'DO 0', 'SELECT * FROM __erp_new_business_migration_lock_not_acquired__');" \
            'PREPARE erp_new_business_lock_stmt FROM @erp_new_business_lock_guard;' \
            'EXECUTE erp_new_business_lock_stmt;' \
            'DEALLOCATE PREPARE erp_new_business_lock_stmt;'
        for name in "${MIGRATIONS[@]}"; do
            printf 'SOURCE %s;\n' "${SOURCE_DIR}/${name}"
        done
        printf "SELECT RELEASE_LOCK('%s');\n" "${LOCK_NAME}"
    } | "${mysql_bin}" "${mysql_args[@]}" \
        || fail 'migration apply failed; the connection close releases the named lock automatically'
    unset MYSQL_PWD

    pass "applied ${EXPECTED_COUNT} migrations to ${database} under named lock ${LOCK_NAME}"
}

case "${MODE}" in
    --static)
        validate_static_contract
        ;;
    --apply)
        validate_static_contract
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
