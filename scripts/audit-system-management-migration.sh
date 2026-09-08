#!/usr/bin/env bash

# Read-only, fail-closed audit for the system-management release migration.
# It accepts only a dedicated rehearsal database name and never prints row data or credentials.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FORWARD_SQL="${ROOT_DIR}/sql/erp_system_management_hardening_20260713.sql"

HOST="${ERP_AUDIT_MYSQL_HOST:-${ERP_IT_MYSQL_HOST:-}}"
PORT="${ERP_AUDIT_MYSQL_PORT:-${ERP_IT_MYSQL_PORT:-3306}}"
USER="${ERP_AUDIT_MYSQL_USER:-${ERP_IT_MYSQL_USER:-}}"
PASSWORD="${ERP_AUDIT_MYSQL_PASSWORD:-${ERP_IT_MYSQL_PASSWORD:-}}"
DATABASE="${ERP_AUDIT_MYSQL_DATABASE:-}"
STAGE="${ERP_AUDIT_STAGE:-applied}"
ALLOW_REMOTE="${ERP_IT_ALLOW_REMOTE_NONPROD:-0}"

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

query()
{
    local statement="$1"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" \
        --batch --skip-column-names --raw "${DATABASE}" \
        -e "SET SESSION TRANSACTION READ ONLY; ${statement}" | tr -d '\r'
}

command -v mysql >/dev/null 2>&1 || fail "mysql CLI is required"
command -v shasum >/dev/null 2>&1 || fail "shasum is required"
[[ -r "${FORWARD_SQL}" ]] || fail "migration SQL file is not readable"
[[ -n "${HOST}" ]] || fail "ERP_AUDIT_MYSQL_HOST or ERP_IT_MYSQL_HOST is required"
[[ -n "${USER}" ]] || fail "ERP_AUDIT_MYSQL_USER or ERP_IT_MYSQL_USER is required"
[[ "${PORT}" =~ ^[0-9]+$ ]] && (( PORT >= 1 && PORT <= 65535 )) \
    || fail "MySQL port must be between 1 and 65535"
[[ "${HOST}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "MySQL host has an invalid format"
[[ "${DATABASE}" =~ ^erp_system_release_rehearsal_[a-z0-9_]+$ ]] \
    || fail "audit database must use the erp_system_release_rehearsal_ prefix"
(( ${#DATABASE} <= 64 )) || fail "audit database name is too long"
[[ "${STAGE}" =~ ^[a-z0-9_-]+$ ]] || fail "audit stage has an invalid format"

if [[ "${HOST}" != "127.0.0.1" && "${HOST}" != "localhost" && "${HOST}" != "::1" \
      && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote MySQL is denied by default; explicitly confirm a non-production target"
fi

FORWARD_SHA="$(shasum -a 256 "${FORWARD_SQL}" | awk '{print $1}')"

ENVIRONMENT="$(query "SELECT CONCAT(VERSION(), '|', DATABASE(), '|',
    @@character_set_database, '|', @@collation_database)")" \
    || fail "unable to connect to rehearsal database"
IFS='|' read -r MYSQL_VERSION ACTUAL_DATABASE DATABASE_CHARSET DATABASE_COLLATION \
    <<< "${ENVIRONMENT}"
assert_equals "${DATABASE}" "${ACTUAL_DATABASE}" "connected database"
printf '[AUDIT] stage=%s version=%s database=%s charset=%s collation=%s sql_sha256=%s\n' \
    "${STAGE}" "${MYSQL_VERSION}" "${ACTUAL_DATABASE}" "${DATABASE_CHARSET}" \
    "${DATABASE_COLLATION}" "${FORWARD_SHA}"

REQUIRED_TABLES="$(query "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema=DATABASE()
      AND table_name IN (
        'sys_user','sys_config','sys_menu','sys_role_menu','sys_role',
        'sys_salary_scheme','sys_salary_scheme_item','sys_oper_log','sys_logininfor',
        'hr_onboarding','sys_hr_lifecycle_action','inv_transfer_approval_rule'
      )")"
assert_equals "12" "${REQUIRED_TABLES}" "required full-schema tables"

MUST_PASSWORD_COLUMN="$(query "SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sys_user'
      AND column_name='must_change_password' AND data_type='char'
      AND character_maximum_length=1 AND is_nullable='NO' AND column_default='0'")"
assert_equals "1" "${MUST_PASSWORD_COLUMN}" "sys_user.must_change_password definition"

CONFIG_COLUMNS="$(query "SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sys_config'
      AND column_name IN ('group_code','value_type','sensitive_flag',
                          'validation_rule','display_order','version')")"
assert_equals "6" "${CONFIG_COLUMNS}" "sys_config governance columns"

SALARY_VERSION_COLUMN="$(query "SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sys_salary_scheme'
      AND column_name='version' AND is_nullable='NO'")"
assert_equals "1" "${SALARY_VERSION_COLUMN}" "sys_salary_scheme.version definition"

TRANSFER_RULE_VERSION_COLUMN="$(query "SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='inv_transfer_approval_rule'
      AND column_name='version' AND data_type='int'
      AND is_nullable='NO' AND column_default='1'")"
assert_equals "1" "${TRANSFER_RULE_VERSION_COLUMN}" \
    "inv_transfer_approval_rule.version definition"

CONFIG_INDEX="$(query "SELECT COUNT(DISTINCT index_name)
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='sys_config'
      AND index_name='idx_sys_config_group_order'")"
assert_equals "1" "${CONFIG_INDEX}" "sys_config governance index"

AUDIT_INDEXES="$(query "SELECT COUNT(DISTINCT index_name)
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='sys_audit_archive_batch'
      AND index_name IN ('PRIMARY','idx_audit_archive_type_cutoff',
                         'idx_audit_archive_status_time')")"
assert_equals "3" "${AUDIT_INDEXES}" "audit archive indexes"

SALARY_REVISION_INDEXES="$(query "SELECT COUNT(DISTINCT index_name)
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='sys_salary_scheme_revision'
      AND index_name IN ('PRIMARY','uk_salary_revision_scheme_version',
                         'idx_salary_revision_time')")"
assert_equals "3" "${SALARY_REVISION_INDEXES}" "salary revision indexes"

PASSWORD_DISTRIBUTION="$(query "SELECT CONCAT(
    '0=', SUM(CASE WHEN must_change_password='0' THEN 1 ELSE 0 END),
    ',1=', SUM(CASE WHEN must_change_password='1' THEN 1 ELSE 0 END),
    ',invalid=', SUM(CASE WHEN must_change_password IS NULL
                          OR must_change_password NOT IN ('0','1') THEN 1 ELSE 0 END))
    FROM sys_user")"
INVALID_PASSWORDS="$(query "SELECT COUNT(*) FROM sys_user
    WHERE must_change_password IS NULL OR must_change_password NOT IN ('0','1')")"
assert_equals "0" "${INVALID_PASSWORDS}" "invalid must_change_password rows"
printf '[AUDIT] must_change_password=%s\n' "${PASSWORD_DISTRIBUTION}"

CONFIG_METADATA_INVALID="$(query "SELECT COUNT(*) FROM sys_config
    WHERE version < 1 OR sensitive_flag NOT IN ('Y','N')
       OR group_code IS NULL OR group_code=''
       OR value_type IS NULL OR value_type=''")"
assert_equals "0" "${CONFIG_METADATA_INVALID}" "invalid config governance metadata"

TRANSFER_RULE_VERSION_INVALID="$(query "SELECT COUNT(*) FROM inv_transfer_approval_rule
    WHERE version IS NULL OR version < 1")"
assert_equals "0" "${TRANSFER_RULE_VERSION_INVALID}" "invalid transfer approval rule versions"

CONFIG_KEY_DUPLICATES="$(query "SELECT COUNT(*) FROM (
    SELECT config_key FROM sys_config GROUP BY config_key HAVING COUNT(*) > 1
    ) duplicate_keys")"
assert_equals "0" "${CONFIG_KEY_DUPLICATES}" "duplicate config keys"

SALARY_COUNTS="$(query "SELECT CONCAT(
    (SELECT COUNT(*) FROM sys_salary_scheme), '|',
    (SELECT COUNT(*) FROM sys_salary_scheme_revision r
      JOIN sys_salary_scheme s ON s.scheme_id=r.scheme_id WHERE r.version=1), '|',
    (SELECT COUNT(*) FROM sys_salary_scheme_revision WHERE JSON_VALID(snapshot_json)=0))")"
IFS='|' read -r SALARY_SCHEMES SALARY_BASELINES INVALID_SNAPSHOTS <<< "${SALARY_COUNTS}"
assert_equals "${SALARY_SCHEMES}" "${SALARY_BASELINES}" "salary baseline revision coverage"
assert_equals "0" "${INVALID_SNAPSHOTS}" "invalid salary revision snapshots"
printf '[AUDIT] salary_schemes=%s baseline_revisions=%s invalid_snapshots=%s\n' \
    "${SALARY_SCHEMES}" "${SALARY_BASELINES}" "${INVALID_SNAPSHOTS}"

PERMISSION_ROWS="$(query "SELECT
    p.perm,
    COUNT(DISTINCT m.menu_id) AS menu_count,
    COUNT(DISTINCT rm.role_id) AS grant_count,
    COUNT(DISTINCT CASE WHEN r.role_key='admin' THEN rm.role_id END) AS admin_grants,
    COUNT(DISTINCT CASE WHEN r.role_key<>'admin' THEN rm.role_id END) AS non_admin_grants
FROM (
    SELECT 'system:role:dataScope' AS perm
    UNION ALL SELECT 'system:operlog:detail'
    UNION ALL SELECT 'system:salary:emergency'
    UNION ALL SELECT 'system:user:authRole'
    UNION ALL SELECT 'system:role:authUser'
    UNION ALL SELECT 'system:config:refresh'
    UNION ALL SELECT 'system:dict:refresh'
    UNION ALL SELECT 'hr:employee:renewal'
    UNION ALL SELECT 'hr:employee:regularize'
) p
LEFT JOIN sys_menu m ON m.perms=p.perm
LEFT JOIN sys_role_menu rm ON rm.menu_id=m.menu_id
LEFT JOIN sys_role r ON r.role_id=rm.role_id AND r.del_flag='0'
GROUP BY p.perm ORDER BY p.perm")"

while IFS=$'\t' read -r permission menu_count grant_count admin_grants non_admin_grants; do
    [[ -n "${permission}" ]] || continue
    assert_equals "1" "${menu_count}" "unique menu for ${permission}"
    assert_equals "0" "${non_admin_grants}" "non-admin grants for ${permission}"
    case "${permission}" in
        system:role:dataScope|system:operlog:detail|system:salary:emergency)
            assert_equals "1" "${grant_count}" "minimal grant count for ${permission}"
            assert_equals "1" "${admin_grants}" "admin grant for ${permission}"
            ;;
        *)
            assert_equals "0" "${grant_count}" "default grant count for ${permission}"
            assert_equals "0" "${admin_grants}" "admin row grant for ${permission}"
            ;;
    esac
    printf '[AUDIT] permission=%s menus=%s grants=%s admin=%s non_admin=%s\n' \
        "${permission}" "${menu_count}" "${grant_count}" \
        "${admin_grants}" "${non_admin_grants}"
done <<< "${PERMISSION_ROWS}"

ROW_SUMMARIES="$(query "SELECT 'sys_user', COUNT(*),
        COALESCE(CAST(MIN(user_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(user_id) AS CHAR),'NULL')
    FROM sys_user
    UNION ALL SELECT 'sys_role', COUNT(*),
        COALESCE(CAST(MIN(role_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(role_id) AS CHAR),'NULL')
    FROM sys_role
    UNION ALL SELECT 'sys_menu', COUNT(*),
        COALESCE(CAST(MIN(menu_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(menu_id) AS CHAR),'NULL')
    FROM sys_menu
    UNION ALL SELECT 'sys_config', COUNT(*),
        COALESCE(CAST(MIN(config_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(config_id) AS CHAR),'NULL')
    FROM sys_config
    UNION ALL SELECT 'sys_salary_scheme', COUNT(*),
        COALESCE(CAST(MIN(scheme_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(scheme_id) AS CHAR),'NULL')
    FROM sys_salary_scheme
    UNION ALL SELECT 'sys_salary_scheme_revision', COUNT(*),
        COALESCE(CAST(MIN(revision_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(revision_id) AS CHAR),'NULL')
    FROM sys_salary_scheme_revision
    UNION ALL SELECT 'inv_transfer_approval_rule', COUNT(*),
        COALESCE(CAST(MIN(rule_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(rule_id) AS CHAR),'NULL')
    FROM inv_transfer_approval_rule
    UNION ALL SELECT 'sys_audit_archive_batch', COUNT(*),
        COALESCE(CAST(MIN(batch_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(batch_id) AS CHAR),'NULL')
    FROM sys_audit_archive_batch
    UNION ALL SELECT 'sys_oper_log', COUNT(*),
        COALESCE(CAST(MIN(oper_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(oper_id) AS CHAR),'NULL')
    FROM sys_oper_log
    UNION ALL SELECT 'sys_logininfor', COUNT(*),
        COALESCE(CAST(MIN(info_id) AS CHAR),'NULL'), COALESCE(CAST(MAX(info_id) AS CHAR),'NULL')
    FROM sys_logininfor")"

while IFS=$'\t' read -r table_name row_count min_id max_id; do
    [[ -n "${table_name}" ]] || continue
    printf '[AUDIT] table=%s rows=%s min_id=%s max_id=%s\n' \
        "${table_name}" "${row_count}" "${min_id}" "${max_id}"
done <<< "${ROW_SUMMARIES}"

pass "SYSTEM_MANAGEMENT_MIGRATION_AUDIT_OK stage=${STAGE} sql_sha256=${FORWARD_SHA}"
