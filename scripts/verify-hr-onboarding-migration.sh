#!/usr/bin/env bash

# Explicit, fail-closed migration gate for one configured native non-production MySQL.
# Run this script once per required server version; no VM or container runtime is used.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION_SQL="${ROOT_DIR}/sql/erp_user_hr_onboarding_20260710.sql"
HOST="${ERP_IT_MYSQL_HOST:-}"
PORT="${ERP_IT_MYSQL_PORT:-3306}"
USER="${ERP_IT_MYSQL_USER:-}"
PASSWORD="${ERP_IT_MYSQL_PASSWORD:-}"
EXPECTED_VERSION="${EXPECTED_MYSQL_VERSION_PREFIX:-${ERP_IT_EXPECTED_MYSQL_VERSION_PREFIX:-}}"
ALLOW_REMOTE="${ERP_IT_ALLOW_REMOTE_NONPROD:-0}"
RUN_ID="${ERP_IT_RUN_ID:-r$(date +%m%d%H%M%S)_$$}"
VERIFY_DATABASE="erp_it_${RUN_ID}_hr_onboarding_migration"
DATABASE_CREATED=0

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

cleanup()
{
    local original_status=$?
    local cleanup_status=0
    trap - EXIT INT TERM
    if [[ "${DATABASE_CREATED}" -eq 1 ]]; then
        set +e
        mysql_server_exec native "DROP DATABASE IF EXISTS \`${VERIFY_DATABASE}\`" >/dev/null
        cleanup_status=$?
        set -e
        if [[ "${cleanup_status}" -eq 0 ]]; then
            printf '[cleanup] dropped %s\n' "${VERIFY_DATABASE}"
        else
            printf '[FAIL] unable to clean isolation database: %s\n' \
                "${VERIFY_DATABASE}" >&2
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

mysql_server_exec()
{
    local target="$1"
    local statement="$2"
    : "${target}"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" -N -B \
        -e "${statement}" 2>/dev/null
}

mysql_exec()
{
    local target="$1"
    local statement="$2"
    : "${target}"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" -N -B \
        "${VERIFY_DATABASE}" -e "${statement}" 2>/dev/null
}

mysql_query()
{
    mysql_exec "$1" "$2" | tr -d '\r'
}

command -v mysql >/dev/null 2>&1 || fail "mysql CLI is required"
command -v shasum >/dev/null 2>&1 || fail "shasum is required"
[[ -r "${MIGRATION_SQL}" ]] || fail "migration SQL is not readable: ${MIGRATION_SQL}"
[[ -n "${HOST}" ]] || fail "ERP_IT_MYSQL_HOST is required"
[[ -n "${USER}" ]] || fail "ERP_IT_MYSQL_USER is required"
[[ -n "${EXPECTED_VERSION}" ]] || fail "EXPECTED_MYSQL_VERSION_PREFIX is required"
[[ "${PORT}" =~ ^[0-9]+$ ]] && (( PORT >= 1 && PORT <= 65535 )) \
    || fail "ERP_IT_MYSQL_PORT must be between 1 and 65535"
[[ "${HOST}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "ERP_IT_MYSQL_HOST has an invalid format"
[[ "${RUN_ID}" =~ ^[a-z0-9_]+$ ]] \
    || fail "ERP_IT_RUN_ID must contain only lowercase letters, digits, and underscores"
(( ${#RUN_ID} <= 20 )) || fail "ERP_IT_RUN_ID must not exceed 20 characters"
[[ "${VERIFY_DATABASE}" =~ ^erp_it_[a-z0-9_]+$ ]] \
    || fail "generated database name is unsafe"
(( ${#VERIFY_DATABASE} <= 64 )) || fail "generated database name is too long"

if [[ "${HOST}" != "127.0.0.1" && "${HOST}" != "localhost" && "${HOST}" != "::1" \
      && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote MySQL is denied by default; set ERP_IT_ALLOW_REMOTE_NONPROD=1 only for a confirmed non-production instance"
fi

VERSION="$(mysql_server_exec native 'SELECT VERSION()')" \
    || fail "unable to connect to native MySQL"
[[ -n "${VERSION}" ]] || fail "native MySQL returned an empty version"
[[ "${VERSION}" == "${EXPECTED_VERSION}"* ]] \
    || fail "MySQL version mismatch: expected prefix ${EXPECTED_VERSION}, actual ${VERSION}"
EXISTING="$(mysql_server_exec native \
    "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${VERIFY_DATABASE}'")"
assert_equals "0" "${EXISTING}" "isolation database collision check"
mysql_server_exec native "CREATE DATABASE \`${VERIFY_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
DATABASE_CREATED=1
MIGRATION_SHA="$(shasum -a 256 "${MIGRATION_SQL}" | awk '{print $1}')"
printf '[native-mysql-migration-it] host=%s:%s version=%s database=%s sql_sha256=%s\n' \
    "${HOST}" "${PORT}" "${VERSION}" "${VERIFY_DATABASE}" "${MIGRATION_SHA}"

reset_schema()
{
    local target="$1"
    : "${target}"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" 2>/dev/null <<SQL
DROP DATABASE IF EXISTS \`${VERIFY_DATABASE}\`;
CREATE DATABASE \`${VERIFY_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE \`${VERIFY_DATABASE}\`;

CREATE TABLE sys_user_profile (
    profile_id bigint NOT NULL AUTO_INCREMENT,
    direct_supervisor varchar(100) DEFAULT NULL,
    PRIMARY KEY (profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
    menu_id bigint NOT NULL,
    menu_name varchar(64) NOT NULL,
    parent_id bigint NOT NULL DEFAULT 0,
    order_num int NOT NULL DEFAULT 0,
    path varchar(200) NOT NULL DEFAULT '',
    component varchar(255) DEFAULT NULL,
    query varchar(255) DEFAULT NULL,
    route_name varchar(64) NOT NULL DEFAULT '',
    is_frame int NOT NULL DEFAULT 1,
    is_cache int NOT NULL DEFAULT 0,
    menu_type char(1) NOT NULL,
    visible char(1) NOT NULL DEFAULT '0',
    status char(1) NOT NULL DEFAULT '0',
    perms varchar(100) DEFAULT NULL,
    icon varchar(100) DEFAULT '#',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Deliberately no unique key on config_key: the migration must serialize and
-- detect ambiguous deployed data itself.
CREATE TABLE sys_config (
    config_id bigint NOT NULL AUTO_INCREMENT,
    config_name varchar(100) NOT NULL,
    config_key varchar(100) NOT NULL,
    config_value varchar(500) NOT NULL,
    config_type char(1) DEFAULT 'N',
    create_by varchar(64) DEFAULT '',
    create_time datetime DEFAULT NULL,
    update_by varchar(64) DEFAULT '',
    update_time datetime DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SQL
}

run_migration()
{
    local target="$1"
    : "${target}"
    MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" \
        "${VERIFY_DATABASE}" 2>/dev/null < "${MIGRATION_SQL}"
}

expect_migration_failure()
{
    local container="$1"
    local expected_message="$2"
    local mode="${3:-normal}"
    local output
    local normalized_output
    local normalized_expected
    local status

    set +e
    if [[ "${mode}" == "lock-guard" ]]; then
        output="$(MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
            --init-command='SET @hr_onboarding_migration_lock_timeout_seconds=0' \
            -h "${HOST}" -P "${PORT}" -u "${USER}" "${VERIFY_DATABASE}" \
            < "${MIGRATION_SQL}" 2>&1)"
        status=$?
    else
        output="$(MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
            -h "${HOST}" -P "${PORT}" -u "${USER}" "${VERIFY_DATABASE}" \
            < "${MIGRATION_SQL}" 2>&1)"
        status=$?
    fi
    set -e

    [[ "${status}" -ne 0 ]] || fail "migration unexpectedly succeeded: ${expected_message}"
    normalized_output="$(tr '[:upper:]' '[:lower:]' <<< "${output}")"
    normalized_expected="$(tr '[:upper:]' '[:lower:]' <<< "${expected_message}")"
    if [[ "${normalized_output}" != *"${normalized_expected}"* ]]; then
        printf '%s\n' "${output}" >&2
        fail "migration failed without expected message: ${expected_message}"
    fi
}

assert_default_config_values()
{
    local container="$1"
    local actual
    local expected="hr.employee.no.prefix=E,hr.onboarding.import_retention_days=30,hr.onboarding.post_entry_due_days=7"
    actual="$(mysql_query "${container}" "
        SELECT GROUP_CONCAT(CONCAT(config_key, '=', config_value)
                            ORDER BY config_key SEPARATOR ',')
        FROM sys_config
        WHERE config_key IN (
            'hr.onboarding.post_entry_due_days',
            'hr.onboarding.import_retention_days',
            'hr.employee.no.prefix'
        )")"
    assert_equals "${expected}" "${actual}" "default config values"
}

assert_migration_twice_is_idempotent()
{
    local container="$1"
    reset_schema "${container}"
    run_migration "${container}"
    run_migration "${container}"

    assert_equals "7" "$(mysql_query "${container}" \
        "SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE() AND table_name LIKE 'hr_%'")" \
        "HR table count"
    assert_equals "1" "$(mysql_query "${container}" \
        "SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'sys_user_profile'
           AND column_name = 'direct_supervisor_user_id'")" \
        "direct supervisor column count"
    assert_equals "26:26" "$(mysql_query "${container}" \
        "SELECT CONCAT(COUNT(*), ':', COUNT(DISTINCT menu_id)) FROM sys_menu")" \
        "menu idempotency"
    assert_equals "3:3" "$(mysql_query "${container}" \
        "SELECT CONCAT(COUNT(*), ':', COUNT(DISTINCT config_key)) FROM sys_config")" \
        "config idempotency"
    assert_default_config_values "${container}"
    assert_equals "0" "$(mysql_query "${container}" \
        "SELECT COUNT(*) FROM information_schema.routines
         WHERE routine_schema = DATABASE()
           AND routine_name LIKE 'erp_hr_onboarding_%'")" \
        "temporary migration routine cleanup"
    assert_equals "1" "$(mysql_query "${container}" \
        "SELECT IS_FREE_LOCK(CONCAT('erp_hr_onboarding_20260710:', MD5(DATABASE())))")" \
        "named lock release after double migration"
}

assert_concurrent_migration_preserves_sentinel()
{
    local container="$1"
    local pid_one
    local pid_two
    local status_one
    local status_two

    reset_schema "${container}"
    mysql_exec "${container}" "
        INSERT INTO sys_menu(menu_id, menu_name, path, menu_type, remark)
        VALUES (700, 'concurrency sentinel', 'sentinel', 'M', 'must remain unchanged')"

    set +e
    run_migration "${container}" >/dev/null 2>&1 & pid_one=$!
    run_migration "${container}" >/dev/null 2>&1 & pid_two=$!
    wait "${pid_one}"; status_one=$?
    wait "${pid_two}"; status_two=$?
    set -e

    assert_equals "0:0" "${status_one}:${status_two}" "concurrent migration exits"
    assert_equals "27:27" "$(mysql_query "${container}" \
        "SELECT CONCAT(COUNT(*), ':', COUNT(DISTINCT menu_id)) FROM sys_menu")" \
        "concurrent menu ids"
    assert_equals "concurrency sentinel|sentinel|must remain unchanged" \
        "$(mysql_query "${container}" \
            "SELECT CONCAT(menu_name, '|', path, '|', remark)
             FROM sys_menu WHERE menu_id = 700")" \
        "sentinel menu preservation"
    assert_equals "3:3" "$(mysql_query "${container}" \
        "SELECT CONCAT(COUNT(*), ':', COUNT(DISTINCT config_key)) FROM sys_config")" \
        "concurrent config keys"
    assert_default_config_values "${container}"
}

assert_global_identity_lock_serialization()
{
    local container="$1"
    local holder_process
    local holder_connection_id=""
    local holder_status
    local contender_output
    local contender_status
    local attempt
    local lock_name="hr_onboarding_global_identity_it"

    reset_schema "${container}"
    run_migration "${container}"
    mysql_exec "${container}" "
        INSERT INTO hr_onboarding
            (onboarding_id, onboarding_no, status, version, employee_name, phone_number,
             target_dept_id, target_post_id, owner_user_id, employee_category, expected_entry_date)
        VALUES
            (1001, 'IT-GLOBAL-1', 'READY', 0, 'scope-a', '13800008888',
             10, 30, 91, 'FORMAL', '2026-07-11'),
            (1002, 'IT-GLOBAL-2', 'DRAFT', 0, 'scope-b', '13800008888',
             20, 30, 92, 'FORMAL', '2026-07-11')"

    mysql_exec "${container}" "
            SET SESSION innodb_lock_wait_timeout = 10;
            START TRANSACTION;
            SELECT o.onboarding_id, o.status
            FROM hr_onboarding o FORCE INDEX (PRIMARY)
            WHERE o.status IN ('DRAFT', 'READY')
              AND o.phone_number = '13800008888'
            ORDER BY o.onboarding_id ASC
            FOR UPDATE;
            SELECT GET_LOCK('${lock_name}', 0);
            DO SLEEP(4);
            UPDATE hr_onboarding SET status = 'CANCELLED' WHERE onboarding_id = 1002;
            COMMIT;
            DO RELEASE_LOCK('${lock_name}')" >/dev/null 2>&1 &
    holder_process=$!

    for attempt in $(seq 1 30); do
        holder_connection_id="$(mysql_query "${container}" \
            "SELECT COALESCE(IS_USED_LOCK('${lock_name}'), 0)")"
        [[ "${holder_connection_id}" != "0" ]] && break
        sleep 1
    done
    [[ "${holder_connection_id}" != "0" ]] || fail "global identity lock holder did not become ready"

    set +e
    contender_output="$(MYSQL_PWD="${PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${HOST}" -P "${PORT}" -u "${USER}" "${VERIFY_DATABASE}" -e "
            SET SESSION innodb_lock_wait_timeout = 1;
            START TRANSACTION;
            SELECT o.onboarding_id, o.status
            FROM hr_onboarding o FORCE INDEX (PRIMARY)
            WHERE o.status IN ('DRAFT', 'READY')
              AND o.phone_number = '13800008888'
            ORDER BY o.onboarding_id ASC
            FOR UPDATE;
            ROLLBACK" 2>&1)"
    contender_status=$?
    set -e
    [[ "${contender_status}" -ne 0 ]] \
        || fail "global identity lock must block cross-department confirmation"
    [[ "${contender_output}" == *"Lock wait timeout exceeded"* ]] \
        || fail "global identity lock contender failed without bounded lock timeout"

    set +e
    wait "${holder_process}"
    holder_status=$?
    set -e
    assert_equals "0" "${holder_status}" "global identity lock holder exit"
    assert_equals $'1001\tREADY' "$(mysql_query "${container}" "
        SELECT o.onboarding_id, o.status
        FROM hr_onboarding o FORCE INDEX (PRIMARY)
        WHERE o.status IN ('DRAFT', 'READY')
          AND o.phone_number = '13800008888'
        ORDER BY o.onboarding_id ASC
        FOR UPDATE")" "global identity lock after duplicate resolution"
    assert_equals "CANCELLED" "$(mysql_query "${container}" \
        "SELECT status FROM hr_onboarding WHERE onboarding_id = 1002")" \
        "global identity duplicate resolution"
}

assert_lock_guard_zero_writes()
{
    local container="$1"
    local holder_process
    local holder_connection_id=""
    local attempt

    reset_schema "${container}"
    mysql_exec "${container}" "
        INSERT INTO sys_menu(menu_id, menu_name, path, menu_type, remark)
        VALUES (900, 'lock sentinel', 'lock-sentinel', 'M', 'must remain unchanged');
        INSERT INTO sys_config(config_name, config_key, config_value)
        VALUES ('lock sentinel', 'sentinel.lock.guard', 'unchanged');
        CREATE PROCEDURE erp_hr_onboarding_raise_apply_error()
        SELECT 'sentinel procedure remains' AS sentinel"

    mysql_exec "${container}" "
            SELECT GET_LOCK(CONCAT('erp_hr_onboarding_20260710:', MD5(DATABASE())), 0);
            LOCK TABLES sys_menu WRITE, sys_config WRITE;
            DO SLEEP(120)" >/dev/null 2>&1 &
    holder_process=$!

    for attempt in $(seq 1 30); do
        holder_connection_id="$(mysql_query "${container}" \
            "SELECT COALESCE(IS_USED_LOCK(
                CONCAT('erp_hr_onboarding_20260710:', MD5(DATABASE()))), 0)")"
        [[ "${holder_connection_id}" != "0" ]] && break
        sleep 1
    done
    [[ "${holder_connection_id}" != "0" ]] || fail "lock holder did not acquire named lock"

    # The failed connection must stop at the session-local guard. Assert the
    # explicit marker as written because diagnostic case differs by version.
    expect_migration_failure "${container}" \
        "erp_hr_onboarding_lock_not_acquired" "lock-guard"

    mysql_exec "${container}" "KILL ${holder_connection_id}" || true
    wait "${holder_process}" >/dev/null 2>&1 || true

    assert_equals "0" "$(mysql_query "${container}" \
        "SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE() AND table_name LIKE 'hr_%'")" \
        "lock timeout HR table writes"
    assert_equals "0" "$(mysql_query "${container}" \
        "SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'sys_user_profile'
           AND column_name = 'direct_supervisor_user_id'")" \
        "lock timeout profile writes"
    assert_equals "lock sentinel|lock-sentinel|must remain unchanged" \
        "$(mysql_query "${container}" \
            "SELECT CONCAT(menu_name, '|', path, '|', remark)
             FROM sys_menu WHERE menu_id = 900")" \
        "lock timeout menu sentinel"
    assert_equals "sentinel.lock.guard=unchanged" "$(mysql_query "${container}" \
        "SELECT CONCAT(config_key, '=', config_value) FROM sys_config")" \
        "lock timeout config sentinel"
    assert_equals "1" "$(mysql_query "${container}" \
        "SELECT COUNT(*) FROM information_schema.routines
         WHERE routine_schema = DATABASE()
           AND routine_name = 'erp_hr_onboarding_raise_apply_error'
           AND routine_definition LIKE '%sentinel procedure remains%'")" \
        "lock timeout routine sentinel"

    # Closing the dedicated holder connection must release both lock types.
    assert_equals "1:1" "$(mysql_query "${container}" \
        "SET @lock_name = CONCAT('erp_hr_onboarding_20260710:', MD5(DATABASE()));
         SELECT CONCAT(GET_LOCK(@lock_name, 0), ':', RELEASE_LOCK(@lock_name))")" \
        "named lock reacquire after connection close"
    mysql_exec "${container}" "
        INSERT INTO sys_menu(menu_id, menu_name, path, menu_type)
        VALUES (901, 'table lock release probe', 'table-lock-release', 'M')"
    assert_equals "1" "$(mysql_query "${container}" \
        "SELECT COUNT(*) FROM sys_menu WHERE menu_id = 901")" \
        "table lock release after connection close"
}

assert_menu_ambiguity_zero_writes()
{
    local container="$1"
    reset_schema "${container}"
    mysql_exec "${container}" "
        INSERT INTO sys_menu(menu_id, menu_name, path, menu_type)
        VALUES
            (100, 'other root', 'hr', 'M'),
            (101, '人事管理', 'other-hr', 'M')"
    expect_migration_failure "${container}" "ambiguous HR menu seed HR_ROOT"
    assert_equals "2" "$(mysql_query "${container}" "SELECT COUNT(*) FROM sys_menu")" \
        "ambiguous menu zero menu writes"
    assert_equals "0" "$(mysql_query "${container}" "SELECT COUNT(*) FROM sys_config")" \
        "ambiguous menu zero config writes"
    assert_equals "1" "$(mysql_query "${container}" \
        "SELECT IS_FREE_LOCK(CONCAT('erp_hr_onboarding_20260710:', MD5(DATABASE())))")" \
        "ambiguous menu lock release"
}

assert_config_ambiguity_zero_writes()
{
    local container="$1"
    reset_schema "${container}"
    mysql_exec "${container}" "
        INSERT INTO sys_menu(menu_id, menu_name, path, menu_type, remark)
        VALUES (777, 'config ambiguity sentinel', 'config-sentinel', 'M', 'unchanged');
        INSERT INTO sys_config(config_name, config_key, config_value)
        VALUES
            ('duplicate one', 'hr.onboarding.post_entry_due_days', '7'),
            ('duplicate two', 'hr.onboarding.post_entry_due_days', '9')"
    expect_migration_failure "${container}" \
        "ambiguous HR config key hr.onboarding.post_entry_due_days"
    assert_equals "1" "$(mysql_query "${container}" "SELECT COUNT(*) FROM sys_menu")" \
        "ambiguous config zero menu writes"
    assert_equals "config ambiguity sentinel|config-sentinel|unchanged" \
        "$(mysql_query "${container}" \
            "SELECT CONCAT(menu_name, '|', path, '|', remark)
             FROM sys_menu WHERE menu_id = 777")" \
        "ambiguous config menu sentinel"
    assert_equals "2" "$(mysql_query "${container}" "SELECT COUNT(*) FROM sys_config")" \
        "ambiguous config zero config writes"
    assert_equals "1" "$(mysql_query "${container}" \
        "SELECT IS_FREE_LOCK(CONCAT('erp_hr_onboarding_20260710:', MD5(DATABASE())))")" \
        "ambiguous config lock release"
}

run_native_mysql_suite()
{
    local target="native"

    assert_migration_twice_is_idempotent "${target}"
    assert_concurrent_migration_preserves_sentinel "${target}"
    assert_global_identity_lock_serialization "${target}"
    assert_lock_guard_zero_writes "${target}"
    assert_menu_ambiguity_zero_writes "${target}"
    assert_config_ambiguity_zero_writes "${target}"

    pass "native MySQL ${VERSION} HR onboarding migration suite (sql_sha256=${MIGRATION_SHA})"
}

run_native_mysql_suite
pass "all HR onboarding migration integration checks"
