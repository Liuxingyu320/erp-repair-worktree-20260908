#!/usr/bin/env bash

# Runs the four database transaction/concurrency suites against an explicitly configured
# native non-production MySQL. No Docker, Testcontainers, VM, FLUSH, or shared schema is used.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="${ROOT_DIR}/erp-modules/erp-system"
REPORT_DIR="${MODULE_DIR}/target/failsafe-reports"

HOST="${ERP_IT_MYSQL_HOST:-}"
PORT="${ERP_IT_MYSQL_PORT:-3306}"
USER="${ERP_IT_MYSQL_USER:-}"
PASSWORD="${ERP_IT_MYSQL_PASSWORD:-}"
EXPECTED_VERSION="${ERP_IT_EXPECTED_MYSQL_VERSION_PREFIX:-}"
ALLOW_REMOTE="${ERP_IT_ALLOW_REMOTE_NONPROD:-0}"
RUN_ID="${ERP_IT_RUN_ID:-r$(date +%m%d%H%M%S)_$$}"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    printf '[PASS] %s\n' "$*"
}

[[ -n "${HOST}" ]] || fail "ERP_IT_MYSQL_HOST is required"
[[ -n "${USER}" ]] || fail "ERP_IT_MYSQL_USER is required"
[[ "${PORT}" =~ ^[0-9]+$ ]] && (( PORT >= 1 && PORT <= 65535 )) \
    || fail "ERP_IT_MYSQL_PORT must be between 1 and 65535"
[[ "${HOST}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "ERP_IT_MYSQL_HOST has an invalid format"
[[ "${RUN_ID}" =~ ^[a-z0-9_]+$ ]] || fail "ERP_IT_RUN_ID must contain only lowercase letters, digits, and underscores"
(( ${#RUN_ID} <= 20 )) || fail "ERP_IT_RUN_ID must not exceed 20 characters"

if [[ "${HOST}" != "127.0.0.1" && "${HOST}" != "localhost" && "${HOST}" != "::1" \
      && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote MySQL is denied by default; set ERP_IT_ALLOW_REMOTE_NONPROD=1 only for a confirmed non-production instance"
fi

command -v mysql >/dev/null 2>&1 || fail "mysql CLI is required"
command -v mvn >/dev/null 2>&1 || fail "mvn is required"

export ERP_IT_RUN_ID="${RUN_ID}"
DATABASE_PREFIX="erp_it_${RUN_ID}_"

mysql_server_query()
{
    local statement="$1"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" --batch --skip-column-names \
        -e "${statement}" 2>/dev/null | tr -d '\r'
}

list_run_databases()
{
    mysql_server_query "SELECT schema_name FROM information_schema.schemata
        WHERE LEFT(schema_name, ${#DATABASE_PREFIX})='${DATABASE_PREFIX}'
        ORDER BY schema_name"
}

cleanup()
{
    local original_status=$?
    local cleanup_status=0
    local database
    local databases=""
    trap - EXIT INT TERM
    set +e
    databases="$(list_run_databases)"
    if [[ $? -ne 0 ]]; then
        printf '[FAIL] unable to inspect native MySQL isolation databases during cleanup\n' >&2
        cleanup_status=1
    else
        while IFS= read -r database; do
            [[ -n "${database}" ]] || continue
            if [[ ! "${database}" =~ ^erp_it_[a-z0-9_]+$ || "${database}" != "${DATABASE_PREFIX}"* ]]; then
                printf '[FAIL] refused unsafe cleanup database: %s\n' "${database}" >&2
                cleanup_status=1
                continue
            fi
            mysql_server_query "DROP DATABASE IF EXISTS \`${database}\`" >/dev/null
            if [[ $? -ne 0 ]]; then
                printf '[FAIL] unable to clean isolation database: %s\n' "${database}" >&2
                cleanup_status=1
            else
                printf '[cleanup] dropped %s\n' "${database}"
            fi
        done <<< "${databases}"
    fi
    set -e
    if [[ "${original_status}" -ne 0 ]]; then
        exit "${original_status}"
    fi
    exit "${cleanup_status}"
}
trap cleanup EXIT INT TERM

VERSION="$(mysql_server_query 'SELECT VERSION()')" || fail "unable to connect to native MySQL"
[[ -n "${VERSION}" ]] || fail "native MySQL returned an empty version"
if [[ -n "${EXPECTED_VERSION}" && "${VERSION}" != "${EXPECTED_VERSION}"* ]]; then
    fail "MySQL version mismatch: expected prefix ${EXPECTED_VERSION}, actual ${VERSION}"
fi

EXISTING="$(list_run_databases)"
[[ -z "${EXISTING}" ]] || fail "current run id already has isolation databases: ${EXISTING}"
printf '[native-mysql-it] host=%s:%s version=%s run_id=%s\n' \
    "${HOST}" "${PORT}" "${VERSION}" "${RUN_ID}"

mkdir -p "${REPORT_DIR}"
EXPECTED_CLASSES=(
    HrEmployeeTransferTransactionIT
    HrOffboardingTransactionIT
    HrOnboardingTransactionIT
    HrSignEventOutboxNativeMySqlIT
)
for class_name in "${EXPECTED_CLASSES[@]}"; do
    rm -f "${REPORT_DIR}/TEST-com.erp.system.service.impl.${class_name}.xml"
done

(
    cd "${ROOT_DIR}"
    mvn -pl erp-modules/erp-system -am -Pnative-mysql-it verify
)

TOTAL_TESTS=0
for class_name in "${EXPECTED_CLASSES[@]}"; do
    report="${REPORT_DIR}/TEST-com.erp.system.service.impl.${class_name}.xml"
    [[ -s "${report}" ]] || fail "missing failsafe report for ${class_name}"
    suite_line="$(grep -m 1 '<testsuite ' "${report}" || true)"
    [[ "${suite_line}" =~ tests=\"([1-9][0-9]*)\" ]] \
        || fail "${class_name} did not execute any tests"
    tests="${BASH_REMATCH[1]}"
    [[ "${suite_line}" == *'errors="0"'* \
        && "${suite_line}" == *'failures="0"'* \
        && "${suite_line}" == *'skipped="0"'* ]] \
        || fail "${class_name} contains failed, errored, or skipped tests"
    TOTAL_TESTS=$((TOTAL_TESTS + tests))
    pass "${class_name}: ${tests} tests, 0 failed, 0 skipped"
done

REMAINING="$(list_run_databases)"
[[ -z "${REMAINING}" ]] || fail "integration tests left isolation databases behind: ${REMAINING}"
pass "native MySQL integration gate completed: version=${VERSION}, suites=4, tests=${TOTAL_TESTS}"
