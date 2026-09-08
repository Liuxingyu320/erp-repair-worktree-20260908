#!/usr/bin/env bash

# Creates four isolated browser-acceptance identities plus same/cross-department
# probe rows. It accepts only a release-rehearsal database and never prints secrets.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE_SQL="${ROOT_DIR}/sql/system_management_e2e_fixture.sql"
HOST="${ERP_E2E_MYSQL_HOST:-127.0.0.1}"
PORT="${ERP_E2E_MYSQL_PORT:-3306}"
USER="${ERP_E2E_MYSQL_USER:-}"
PASSWORD="${ERP_E2E_MYSQL_PASSWORD:-}"
DATABASE="${ERP_E2E_MYSQL_DATABASE:-}"
PREFIX="${ERP_E2E_PREFIX:-}"
PASSWORD_HASH="${ERP_E2E_PASSWORD_HASH:-}"
ALLOW_REMOTE="${ERP_IT_ALLOW_REMOTE_NONPROD:-0}"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

query()
{
    local statement="$1"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" \
        --batch --skip-column-names --raw "${DATABASE}" -e "${statement}" \
        2>/dev/null | tr -d '\r'
}

command -v mysql >/dev/null 2>&1 || fail "mysql CLI is required"
[[ -r "${FIXTURE_SQL}" ]] || fail "fixture SQL is not readable"
[[ -n "${USER}" ]] || fail "ERP_E2E_MYSQL_USER is required"
[[ "${PORT}" =~ ^[0-9]+$ ]] && (( PORT >= 1 && PORT <= 65535 )) \
    || fail "ERP_E2E_MYSQL_PORT must be between 1 and 65535"
[[ "${HOST}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "MySQL host has an invalid format"
[[ "${DATABASE}" =~ ^erp_system_release_rehearsal_[a-z0-9_]+$ ]] \
    || fail "E2E fixture database must use the release rehearsal prefix"
(( ${#DATABASE} <= 64 )) || fail "database name is too long"
[[ "${PREFIX}" =~ ^[a-z][a-z0-9_]{2,11}$ ]] \
    || fail "ERP_E2E_PREFIX must be 3-12 lowercase letters, digits, or underscores"
[[ "${PASSWORD_HASH}" =~ ^\$2[aaby]\$[0-9][0-9]\$[./A-Za-z0-9]{53}$ ]] \
    || fail "ERP_E2E_PASSWORD_HASH must be a 60-character BCrypt hash"

if [[ "${HOST}" != "127.0.0.1" && "${HOST}" != "localhost" && "${HOST}" != "::1" \
      && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote MySQL is denied by default; explicitly confirm a non-production target"
fi

[[ "$(query "SELECT COUNT(*) FROM information_schema.schemata
    WHERE schema_name='${DATABASE}'")" == "1" ]] || fail "rehearsal database does not exist"
[[ "$(query "SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sys_user'
      AND column_name='must_change_password'")" == "1" ]] \
    || fail "system-management migration has not been applied"
[[ "$(query "SELECT COUNT(*) FROM sys_menu WHERE perms IN
    ('hr:employee:renewal','hr:employee:regularize')")" == "2" ]] \
    || fail "HR lifecycle permission catalog is incomplete"

INIT_COMMAND="SET NAMES utf8mb4 COLLATE utf8mb4_general_ci; SET @e2e_prefix='${PREFIX}'; SET @e2e_password_hash='${PASSWORD_HASH}'"
SIGNATURE="$(MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
    -h "${HOST}" -P "${PORT}" -u "${USER}" --batch --skip-column-names --raw \
    --init-command="${INIT_COMMAND}" "${DATABASE}" < "${FIXTURE_SQL}" \
    2>/dev/null | tail -n 1 | tr -d '\r')"

[[ "${SIGNATURE}" =~ ^4\|5\|3\|[1-9][0-9]*\|1\|1\|2$ ]] \
    || fail "unexpected E2E fixture signature: ${SIGNATURE}"
[[ "$(query "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id
    JOIN sys_menu m ON m.menu_id=rm.menu_id
    WHERE r.role_key='${PREFIX}_hr'
      AND m.perms IN ('hr:employee:renewal','hr:employee:regularize')")" == "2" ]] \
    || fail "HR role did not receive both lifecycle permissions"
[[ "$(query "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id
    JOIN sys_menu m ON m.menu_id=rm.menu_id
    WHERE r.role_key IN ('${PREFIX}_sysadmin','${PREFIX}_hr','${PREFIX}_shop')
      AND m.perms IN ('system:salary:emergency','system:logininfor:unlock',
                      'system:operlog:detail','system:operlog:remove')")" == "0" ]] \
    || fail "fixture granted a forbidden high-risk permission"
[[ "$(query "SELECT COUNT(*) FROM sys_user u JOIN sys_user_profile p ON p.user_id=u.user_id
    WHERE u.user_name IN ('${PREFIX}_super','${PREFIX}_sysadmin','${PREFIX}_hr','${PREFIX}_shop')
      AND u.phonenumber <> '' AND u.sex IN ('0','1')
      AND p.birth_date IS NOT NULL AND p.id_type <> '' AND p.id_number <> ''
      AND p.registered_residence <> '' AND p.current_address <> ''
      AND p.marital_status <> '' AND p.ethnicity <> ''
      AND p.emergency_contact <> '' AND p.emergency_contact_relation <> ''
      AND p.emergency_contact_phone <> '' AND p.bank_name <> '' AND p.bank_account <> ''")" == "4" ]] \
    || fail "browser identities do not have complete synthetic profiles"
[[ "$(query "SELECT COUNT(*) FROM sys_user_shop s JOIN sys_user u ON u.user_id=s.user_id
    JOIN sys_dept d ON d.dept_id=s.dept_id
    WHERE u.user_name='${PREFIX}_shop' AND u.dept_id=s.dept_id AND d.dept_type='STORE'")" == "1" ]] \
    || fail "shop identity is not restricted to one concrete store"

printf '[PASS] SYSTEM_MANAGEMENT_E2E_FIXTURE_OK database=%s prefix=%s signature=%s\n' \
    "${DATABASE}" "${PREFIX}" "${SIGNATURE}"
printf '[E2E] accounts=%s_super,%s_sysadmin,%s_hr,%s_shop probes=2 password_hash=redacted\n' \
    "${PREFIX}" "${PREFIX}" "${PREFIX}" "${PREFIX}"
