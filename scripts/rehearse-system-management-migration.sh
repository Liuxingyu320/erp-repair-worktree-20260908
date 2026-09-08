#!/usr/bin/env bash

# Clones one non-production schema into a unique rehearsal database, then proves
# apply -> idempotent apply -> declared rollback -> reapply without containers.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FORWARD_SQL="${ROOT_DIR}/sql/erp_system_management_hardening_20260713.sql"
ROLLBACK_SQL="${ROOT_DIR}/sql/erp_system_management_hardening_rollback_20260713.sql"
AUDIT_SCRIPT="${ROOT_DIR}/scripts/audit-system-management-migration.sh"

HOST="${ERP_IT_MYSQL_HOST:-}"
PORT="${ERP_IT_MYSQL_PORT:-3306}"
USER="${ERP_IT_MYSQL_USER:-}"
PASSWORD="${ERP_IT_MYSQL_PASSWORD:-}"
SOURCE_DATABASE="${ERP_REHEARSAL_SOURCE_DATABASE:-}"
EXPECTED_VERSION="${EXPECTED_MYSQL_VERSION_PREFIX:-${ERP_IT_EXPECTED_MYSQL_VERSION_PREFIX:-}}"
ALLOW_REMOTE="${ERP_IT_ALLOW_REMOTE_NONPROD:-0}"
KEEP_DATABASE="${ERP_REHEARSAL_KEEP_DATABASE:-0}"
RUN_ID="${ERP_IT_RUN_ID:-r$(date +%m%d%H%M%S)_$$}"
TARGET_DATABASE="erp_system_release_rehearsal_${RUN_ID}"
TARGET_CREATED=0

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    printf '[PASS] %s\n' "$*"
}

assert_equals()
{
    local expected="$1"
    local actual="$2"
    local label="$3"
    if [[ "${actual}" != "${expected}" ]]; then
        fail "${label}: expected '${expected}', got '${actual}'"
    fi
}

server_query()
{
    local statement="$1"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" \
        --batch --skip-column-names --raw -e "${statement}" 2>/dev/null | tr -d '\r'
}

database_query()
{
    local database="$1"
    local statement="$2"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" \
        --batch --skip-column-names --raw "${database}" \
        -e "SET SESSION TRANSACTION READ ONLY; ${statement}" 2>/dev/null | tr -d '\r'
}

apply_sql()
{
    local script="$1"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" \
        "${TARGET_DATABASE}" < "${script}" >/dev/null
}

business_signature()
{
    local database="$1"
    database_query "${database}" "SELECT CONCAT_WS('|',
        (SELECT COUNT(*) FROM sys_user),
        (SELECT COUNT(*) FROM sys_role),
        (SELECT COUNT(*) FROM sys_salary_scheme),
        (SELECT COUNT(*) FROM sys_salary_scheme_item),
        (SELECT COUNT(*) FROM inv_transfer_approval_rule),
        (SELECT COUNT(*) FROM sys_oper_log),
        (SELECT COUNT(*) FROM sys_logininfor))"
}

migration_signature()
{
    database_query "${TARGET_DATABASE}" "SELECT CONCAT_WS('|',
        (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='sys_user'
            AND column_name='must_change_password'),
        (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='sys_config'
            AND column_name IN ('group_code','value_type','sensitive_flag',
                                'validation_rule','display_order','version')),
        (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='sys_salary_scheme'
            AND column_name='version'),
        (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='inv_transfer_approval_rule'
            AND column_name='version'),
        (SELECT COUNT(*) FROM information_schema.tables
          WHERE table_schema=DATABASE()
            AND table_name IN ('sys_audit_archive_batch','sys_salary_scheme_revision')),
        (SELECT COUNT(*) FROM sys_menu
          WHERE perms IN ('system:role:dataScope','system:operlog:detail',
                          'system:salary:emergency','system:user:authRole',
                          'system:role:authUser','system:config:refresh',
                          'system:dict:refresh','hr:employee:renewal',
                          'hr:employee:regularize')),
        (SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
          WHERE m.perms IN ('system:role:dataScope','system:operlog:detail',
                            'system:salary:emergency','system:user:authRole',
                            'system:role:authUser','system:config:refresh',
                            'system:dict:refresh','hr:employee:renewal',
                            'hr:employee:regularize')),
        (SELECT COUNT(*) FROM sys_salary_scheme_revision),
        (SELECT COUNT(*) FROM sys_config))"
}

cleanup()
{
    local original_status=$?
    local cleanup_status=0
    trap - EXIT INT TERM
    if [[ "${TARGET_CREATED}" -eq 1 ]]; then
        if [[ "${KEEP_DATABASE}" == "1" && "${original_status}" -eq 0 ]]; then
            printf '[KEEP] rehearsal database retained by explicit request: %s\n' \
                "${TARGET_DATABASE}"
        else
            set +e
            server_query "DROP DATABASE IF EXISTS \`${TARGET_DATABASE}\`" >/dev/null
            cleanup_status=$?
            set -e
            if [[ "${cleanup_status}" -eq 0 ]]; then
                printf '[cleanup] dropped %s\n' "${TARGET_DATABASE}"
            else
                printf '[FAIL] unable to clean rehearsal database: %s\n' \
                    "${TARGET_DATABASE}" >&2
            fi
        fi
    fi
    if [[ "${original_status}" -ne 0 ]]; then
        exit "${original_status}"
    fi
    exit "${cleanup_status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

command -v mysql >/dev/null 2>&1 || fail "mysql CLI is required"
command -v mysqldump >/dev/null 2>&1 || fail "mysqldump is required"
command -v shasum >/dev/null 2>&1 || fail "shasum is required"
[[ -r "${FORWARD_SQL}" && -r "${ROLLBACK_SQL}" ]] \
    || fail "migration scripts are not readable"
[[ -x "${AUDIT_SCRIPT}" ]] || fail "migration audit script must be executable"
[[ -n "${HOST}" ]] || fail "ERP_IT_MYSQL_HOST is required"
[[ -n "${USER}" ]] || fail "ERP_IT_MYSQL_USER is required"
[[ -n "${SOURCE_DATABASE}" ]] || fail "ERP_REHEARSAL_SOURCE_DATABASE is required"
[[ -n "${EXPECTED_VERSION}" ]] || fail "EXPECTED_MYSQL_VERSION_PREFIX is required"
[[ "${PORT}" =~ ^[0-9]+$ ]] && (( PORT >= 1 && PORT <= 65535 )) \
    || fail "ERP_IT_MYSQL_PORT must be between 1 and 65535"
[[ "${HOST}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "ERP_IT_MYSQL_HOST has an invalid format"
[[ "${SOURCE_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || fail "source database name is unsafe"
[[ "${RUN_ID}" =~ ^[a-z0-9_]+$ ]] \
    || fail "ERP_IT_RUN_ID must contain only lowercase letters, digits, and underscores"
(( ${#RUN_ID} <= 20 )) || fail "ERP_IT_RUN_ID must not exceed 20 characters"
[[ "${TARGET_DATABASE}" =~ ^erp_system_release_rehearsal_[a-z0-9_]+$ ]] \
    || fail "generated rehearsal database name is unsafe"
(( ${#TARGET_DATABASE} <= 64 )) || fail "generated rehearsal database name is too long"
[[ "${SOURCE_DATABASE}" != "${TARGET_DATABASE}" ]] || fail "source and target database must differ"
[[ "${KEEP_DATABASE}" == "0" || "${KEEP_DATABASE}" == "1" ]] \
    || fail "ERP_REHEARSAL_KEEP_DATABASE must be 0 or 1"

if [[ "${HOST}" != "127.0.0.1" && "${HOST}" != "localhost" && "${HOST}" != "::1" \
      && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote MySQL is denied by default; explicitly confirm a non-production target"
fi

VERSION="$(server_query 'SELECT VERSION()')" || fail "unable to connect to native MySQL"
[[ "${VERSION}" == "${EXPECTED_VERSION}"* ]] \
    || fail "MySQL version mismatch: expected prefix ${EXPECTED_VERSION}, actual ${VERSION}"
assert_equals "1" "$(server_query "SELECT COUNT(*) FROM information_schema.schemata
    WHERE schema_name='${SOURCE_DATABASE}'")" "source database existence"
assert_equals "0" "$(server_query "SELECT COUNT(*) FROM information_schema.schemata
    WHERE schema_name='${TARGET_DATABASE}'")" "target database collision check"

FORWARD_SHA="$(shasum -a 256 "${FORWARD_SQL}" | awk '{print $1}')"
ROLLBACK_SHA="$(shasum -a 256 "${ROLLBACK_SQL}" | awk '{print $1}')"

SOURCE_BUSINESS_BEFORE="$(business_signature "${SOURCE_DATABASE}")"
SOURCE_REQUIRED_TABLES="$(database_query "${SOURCE_DATABASE}" "SELECT COUNT(*)
    FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('sys_user','sys_config','sys_menu','sys_role_menu','sys_role',
        'sys_salary_scheme','sys_salary_scheme_item','sys_oper_log','sys_logininfor',
        'hr_onboarding','sys_hr_lifecycle_action','inv_transfer_approval_rule')")"
assert_equals "12" "${SOURCE_REQUIRED_TABLES}" "source full-schema coverage"
assert_equals "0" "$(database_query "${SOURCE_DATABASE}" "SELECT COUNT(*)
    FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='sys_user' AND column_name='must_change_password'")" \
    "source pre-migration password column"
assert_equals "0" "$(database_query "${SOURCE_DATABASE}" "SELECT COUNT(*)
    FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='inv_transfer_approval_rule' AND column_name='version'")" \
    "source pre-migration transfer rule version column"
assert_equals "0" "$(database_query "${SOURCE_DATABASE}" "SELECT COUNT(*)
    FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('sys_audit_archive_batch','sys_salary_scheme_revision')")" \
    "source pre-migration governance tables"
assert_equals "0" "$(database_query "${SOURCE_DATABASE}" "SELECT COUNT(*) FROM sys_menu
    WHERE perms IN ('system:role:dataScope','system:operlog:detail',
                    'system:salary:emergency','system:user:authRole',
                    'system:role:authUser','system:config:refresh',
                    'system:dict:refresh','hr:employee:renewal',
                    'hr:employee:regularize')")" "source pre-migration permission rows"

printf '[REHEARSAL] version=%s source=%s target=%s forward_sha256=%s rollback_sha256=%s\n' \
    "${VERSION}" "${SOURCE_DATABASE}" "${TARGET_DATABASE}" \
    "${FORWARD_SHA}" "${ROLLBACK_SHA}"

server_query "CREATE DATABASE \`${TARGET_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" >/dev/null
TARGET_CREATED=1

CLONE_STARTED="$(date +%s)"
MYSQL_PWD="${PASSWORD}" mysqldump --protocol=TCP --host="${HOST}" --port="${PORT}" \
    --user="${USER}" --single-transaction --quick --routines --triggers --events \
    --hex-blob --default-character-set=utf8mb4 "${SOURCE_DATABASE}" \
    | MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --host="${HOST}" --port="${PORT}" \
        --user="${USER}" "${TARGET_DATABASE}"
CLONE_SECONDS=$(( $(date +%s) - CLONE_STARTED ))

TARGET_BUSINESS_BEFORE="$(business_signature "${TARGET_DATABASE}")"
assert_equals "${SOURCE_BUSINESS_BEFORE}" "${TARGET_BUSINESS_BEFORE}" \
    "full clone business signature"
TARGET_TABLES="$(database_query "${TARGET_DATABASE}" "SELECT COUNT(*)
    FROM information_schema.tables WHERE table_schema=DATABASE()")"
printf '[REHEARSAL] clone_seconds=%s tables=%s business_signature=%s\n' \
    "${CLONE_SECONDS}" "${TARGET_TABLES}" "${TARGET_BUSINESS_BEFORE}"

APPLY_STARTED="$(date +%s)"
apply_sql "${FORWARD_SQL}"
APPLY_SECONDS=$(( $(date +%s) - APPLY_STARTED ))
FIRST_SIGNATURE="$(migration_signature)"
assert_equals "${TARGET_BUSINESS_BEFORE}" "$(business_signature "${TARGET_DATABASE}")" \
    "business rows after first apply"

ERP_AUDIT_MYSQL_HOST="${HOST}" \
ERP_AUDIT_MYSQL_PORT="${PORT}" \
ERP_AUDIT_MYSQL_USER="${USER}" \
ERP_AUDIT_MYSQL_PASSWORD="${PASSWORD}" \
ERP_AUDIT_MYSQL_DATABASE="${TARGET_DATABASE}" \
ERP_AUDIT_STAGE="first-apply" \
ERP_IT_ALLOW_REMOTE_NONPROD="${ALLOW_REMOTE}" \
    "${AUDIT_SCRIPT}"
pass "SQL_APPLY_OK seconds=${APPLY_SECONDS} signature=${FIRST_SIGNATURE}"

SECOND_APPLY_STARTED="$(date +%s)"
apply_sql "${FORWARD_SQL}"
SECOND_APPLY_SECONDS=$(( $(date +%s) - SECOND_APPLY_STARTED ))
SECOND_SIGNATURE="$(migration_signature)"
assert_equals "${FIRST_SIGNATURE}" "${SECOND_SIGNATURE}" "idempotent migration signature"
assert_equals "${TARGET_BUSINESS_BEFORE}" "$(business_signature "${TARGET_DATABASE}")" \
    "business rows after idempotent apply"
pass "SQL_IDEMPOTENT_OK seconds=${SECOND_APPLY_SECONDS} signature=${SECOND_SIGNATURE}"

ROLLBACK_STARTED="$(date +%s)"
apply_sql "${ROLLBACK_SQL}"
ROLLBACK_SECONDS=$(( $(date +%s) - ROLLBACK_STARTED ))
assert_equals "0" "$(database_query "${TARGET_DATABASE}" "SELECT COUNT(*) FROM sys_menu
    WHERE perms IN ('system:role:dataScope','system:operlog:detail',
                    'system:salary:emergency','system:user:authRole',
                    'system:role:authUser','system:config:refresh',
                    'system:dict:refresh','hr:employee:renewal',
                    'hr:employee:regularize')
      AND create_by='system_hardening_20260713'")" "rollback-created permission rows"
assert_equals "0" "$(database_query "${TARGET_DATABASE}" "SELECT COUNT(*)
    FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
    WHERE m.perms IN ('system:role:dataScope','system:operlog:detail',
                      'system:salary:emergency','system:user:authRole',
                      'system:role:authUser','system:config:refresh',
                      'system:dict:refresh','hr:employee:renewal',
                      'hr:employee:regularize')
      AND m.create_by='system_hardening_20260713'")" "rollback-created permission grants"
assert_equals "${TARGET_BUSINESS_BEFORE}" "$(business_signature "${TARGET_DATABASE}")" \
    "business rows after rollback"
pass "SQL_ROLLBACK_OK seconds=${ROLLBACK_SECONDS}"

REAPPLY_STARTED="$(date +%s)"
apply_sql "${FORWARD_SQL}"
REAPPLY_SECONDS=$(( $(date +%s) - REAPPLY_STARTED ))
REAPPLY_SIGNATURE="$(migration_signature)"
assert_equals "${FIRST_SIGNATURE}" "${REAPPLY_SIGNATURE}" "reapply migration signature"
assert_equals "${TARGET_BUSINESS_BEFORE}" "$(business_signature "${TARGET_DATABASE}")" \
    "business rows after reapply"

ERP_AUDIT_MYSQL_HOST="${HOST}" \
ERP_AUDIT_MYSQL_PORT="${PORT}" \
ERP_AUDIT_MYSQL_USER="${USER}" \
ERP_AUDIT_MYSQL_PASSWORD="${PASSWORD}" \
ERP_AUDIT_MYSQL_DATABASE="${TARGET_DATABASE}" \
ERP_AUDIT_STAGE="reapply" \
ERP_IT_ALLOW_REMOTE_NONPROD="${ALLOW_REMOTE}" \
    "${AUDIT_SCRIPT}"
pass "SQL_REAPPLY_OK seconds=${REAPPLY_SECONDS} signature=${REAPPLY_SIGNATURE}"

SOURCE_BUSINESS_AFTER="$(business_signature "${SOURCE_DATABASE}")"
assert_equals "${SOURCE_BUSINESS_BEFORE}" "${SOURCE_BUSINESS_AFTER}" \
    "source database remained read-only"
pass "SYSTEM_MANAGEMENT_MIGRATION_REHEARSAL_OK version=${VERSION} target=${TARGET_DATABASE}"
