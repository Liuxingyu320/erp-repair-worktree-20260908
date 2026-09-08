#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST="${ERP_SYSTEM_NATIVE_MYSQL_HOST:-127.0.0.1}"
PORT="${ERP_SYSTEM_NATIVE_MYSQL_PORT:-3306}"
USER_NAME="${ERP_SYSTEM_NATIVE_MYSQL_USER:-root}"
PASSWORD_FILE="${ERP_SYSTEM_NATIVE_MYSQL_PASSWORD_FILE:-}"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

case "$HOST" in
    127.0.0.1|localhost|::1) ;;
    *) fail "native MySQL verification only accepts a loopback host" ;;
esac
[[ "$PORT" =~ ^[0-9]{1,5}$ ]] || fail "invalid native MySQL port"
[[ "$USER_NAME" =~ ^[A-Za-z0-9_@.-]+$ ]] || fail "invalid native MySQL user"
command -v mysql >/dev/null 2>&1 || fail "native mysql client is required"
command -v python3 >/dev/null 2>&1 || fail "python3 is required"

if [[ -n "$PASSWORD_FILE" ]]; then
    [[ "$PASSWORD_FILE" = /* && -r "$PASSWORD_FILE" ]] \
        || fail "ERP_SYSTEM_NATIVE_MYSQL_PASSWORD_FILE must be a readable absolute path"
    MYSQL_PWD="$(<"$PASSWORD_FILE")"
else
    MYSQL_PWD="${ERP_SYSTEM_NATIVE_MYSQL_PASSWORD:-}"
fi
export MYSQL_PWD

suffix="$(python3 -c 'import secrets; print(secrets.token_hex(6))')"
IDEMPOTENCY_DB="erp_notice_workflow_test_${suffix}"
GUARD_DB="erp_notice_workflow_test_${suffix}_guard"
[[ "$IDEMPOTENCY_DB" =~ ^erp_notice_workflow_test_[0-9a-f]{12}$ ]] \
    || fail "unsafe generated idempotency database name"
[[ "$GUARD_DB" =~ ^erp_notice_workflow_test_[0-9a-f]{12}_guard$ ]] \
    || fail "unsafe generated guard database name"

MYSQL=(mysql --protocol=tcp --host="$HOST" --port="$PORT" --user="$USER_NAME" \
    --batch --raw --default-character-set=utf8mb4)

cleanup()
{
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$IDEMPOTENCY_DB\`; DROP DATABASE IF EXISTS \`$GUARD_DB\`;" \
        >/dev/null 2>&1 || true
    unset MYSQL_PWD
}
trap cleanup EXIT HUP INT TERM

server_version="$("${MYSQL[@]}" --skip-column-names -e 'select @@version')"
case "$server_version" in
    5.7.*|8.0.*) ;;
    *) fail "unsupported native MySQL version: $server_version" ;;
esac

"${MYSQL[@]}" -e "CREATE DATABASE \`$IDEMPOTENCY_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE \`$GUARD_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

apply_file()
{
    local database="$1"
    local file="$2"
    [[ "$database" == erp_notice_workflow_test_* ]] || fail "refusing unsafe test database"
    [[ -r "$file" ]] || fail "missing SQL file: $file"
    "${MYSQL[@]}" "$database" < "$file" >/dev/null
}

scalar()
{
    local database="$1"
    local query="$2"
    "${MYSQL[@]}" "$database" --skip-column-names -e "$query"
}

FIXTURE="$ROOT_DIR/scripts/fixtures/system-notice-workflow-baseline.sql"
FORWARD="$ROOT_DIR/sql/erp_system_notice_workflow_20260714.sql"
ROLLBACK="$ROOT_DIR/sql/erp_system_notice_workflow_rollback_20260714.sql"
CONFIG_KEY="feature.system.notice-workflow.enabled"

apply_file "$IDEMPOTENCY_DB" "$FIXTURE"
apply_file "$IDEMPOTENCY_DB" "$FORWARD"
first_state="$(scalar "$IDEMPOTENCY_DB" "SELECT CONCAT((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name IN ('lifecycle_status','audience_type','scheduled_publish_time','published_time','expire_time','version','previous_notice_id')), '|', (SELECT COUNT(*) FROM sys_notice_audience), '|', (SELECT COUNT(*) FROM sys_notice_recipient), '|', (SELECT COUNT(*) FROM sys_menu WHERE perms='system:notice:publish'), '|', (SELECT COUNT(*) FROM sys_config WHERE config_key='$CONFIG_KEY'))")"
apply_file "$IDEMPOTENCY_DB" "$FORWARD"
second_state="$(scalar "$IDEMPOTENCY_DB" "SELECT CONCAT((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name IN ('lifecycle_status','audience_type','scheduled_publish_time','published_time','expire_time','version','previous_notice_id')), '|', (SELECT COUNT(*) FROM sys_notice_audience), '|', (SELECT COUNT(*) FROM sys_notice_recipient), '|', (SELECT COUNT(*) FROM sys_menu WHERE perms='system:notice:publish'), '|', (SELECT COUNT(*) FROM sys_config WHERE config_key='$CONFIG_KEY'))")"
[[ "$first_state" == "7|2|3|1|1" ]] || fail "unexpected first migration state: $first_state"
[[ "$second_state" == "$first_state" ]] || fail "second migration changed cardinality: $second_state"

lifecycle_state="$(scalar "$IDEMPOTENCY_DB" "SELECT GROUP_CONCAT(CONCAT(lifecycle_status, ':', c) ORDER BY lifecycle_status SEPARATOR '|') FROM (SELECT lifecycle_status, COUNT(*) c FROM sys_notice GROUP BY lifecycle_status) s")"
[[ "$lifecycle_state" == "OFFLINE:1|PUBLISHED:1" ]] || fail "legacy lifecycle classification is wrong: $lifecycle_state"
recipient_state="$(scalar "$IDEMPOTENCY_DB" "SELECT CONCAT(COUNT(*), '|', COUNT(DISTINCT user_id), '|', MIN(recipient_source), '|', MAX(recipient_source)) FROM sys_notice_recipient WHERE notice_id=1")"
[[ "$recipient_state" == "3|3|LEGACY_MIGRATION|LEGACY_MIGRATION" ]] \
    || fail "legacy active-user/read-evidence snapshot is wrong: $recipient_state"
flag_state="$(scalar "$IDEMPOTENCY_DB" "SELECT CONCAT(COUNT(*), '|', MAX(config_value), '|', MAX(create_by)) FROM sys_config WHERE config_key='$CONFIG_KEY'")"
[[ "$flag_state" == "1|false|system" ]] || fail "workflow flag is not fail-closed: $flag_state"
auto_grants="$(scalar "$IDEMPOTENCY_DB" "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms='system:notice:publish'")"
[[ "$auto_grants" == "0" ]] || fail "publish permission was unexpectedly granted to a role"

apply_file "$IDEMPOTENCY_DB" "$ROLLBACK"
rollback_state="$(scalar "$IDEMPOTENCY_DB" "SELECT CONCAT((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name IN ('lifecycle_status','audience_type','scheduled_publish_time','published_time','expire_time','version','previous_notice_id')), '|', (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('sys_notice_audience','sys_notice_recipient')), '|', (SELECT COUNT(*) FROM sys_menu WHERE perms='system:notice:publish'), '|', (SELECT COUNT(*) FROM sys_config WHERE config_key='$CONFIG_KEY'))")"
[[ "$rollback_state" == "0|0|0|0" ]] || fail "safe rollback left Release C objects: $rollback_state"
legacy_statuses="$(scalar "$IDEMPOTENCY_DB" "SELECT GROUP_CONCAT(CONCAT(notice_id, ':', status) ORDER BY notice_id SEPARATOR '|') FROM sys_notice")"
[[ "$legacy_statuses" == "1:0|2:1" ]] || fail "safe rollback changed legacy notice statuses: $legacy_statuses"

apply_file "$GUARD_DB" "$FIXTURE"
apply_file "$GUARD_DB" "$FORWARD"
"${MYSQL[@]}" "$GUARD_DB" -e "INSERT INTO sys_notice (notice_title, notice_type, notice_content, status, lifecycle_status, audience_type, version, create_by, create_time) VALUES ('new workflow draft', '2', '<p>draft</p>', '1', 'DRAFT', 'MIXED', 1, 'native-gate', NOW());"
if apply_file "$GUARD_DB" "$ROLLBACK" 2>/dev/null; then
    fail "guarded rollback unexpectedly succeeded with new workflow data"
fi
guard_state="$(scalar "$GUARD_DB" "SELECT CONCAT((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name='lifecycle_status'), '|', (SELECT COUNT(*) FROM sys_notice WHERE lifecycle_status='DRAFT'))")"
[[ "$guard_state" == "1|1" ]] || fail "failed rollback did not preserve workflow schema/data: $guard_state"

printf 'SYSTEM_NOTICE_WORKFLOW_NATIVE_MYSQL_OK server=%s idempotent_runs=2 rollback=safe guard=blocked\n' "$server_version"
