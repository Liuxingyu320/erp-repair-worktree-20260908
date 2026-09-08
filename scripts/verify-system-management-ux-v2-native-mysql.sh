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
IDEMPOTENCY_DB="erp_system_ux_test_${suffix}"
PRESERVE_DB="erp_system_ux_test_${suffix}_preserve"
[[ "$IDEMPOTENCY_DB" =~ ^erp_system_ux_test_[0-9a-f]{12}$ ]] || fail "unsafe generated test database name"
[[ "$PRESERVE_DB" =~ ^erp_system_ux_test_[0-9a-f]{12}_preserve$ ]] || fail "unsafe generated preserve database name"

MYSQL=(mysql --protocol=tcp --host="$HOST" --port="$PORT" --user="$USER_NAME" \
    --batch --raw --default-character-set=utf8mb4)

cleanup()
{
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$IDEMPOTENCY_DB\`; DROP DATABASE IF EXISTS \`$PRESERVE_DB\`;" \
        >/dev/null 2>&1 || true
    unset MYSQL_PWD
}
trap cleanup EXIT HUP INT TERM

server_version="$("${MYSQL[@]}" --skip-column-names -e 'select @@version')"
case "$server_version" in
    5.7.*|8.0.*) ;;
    *) fail "unsupported native MySQL version: $server_version" ;;
esac

"${MYSQL[@]}" -e "CREATE DATABASE \`$IDEMPOTENCY_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE \`$PRESERVE_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

apply_file()
{
    local database="$1"
    local file="$2"
    [[ "$database" == erp_system_ux_test_* ]] || fail "refusing unsafe test database"
    [[ -r "$file" ]] || fail "missing SQL file: $file"
    "${MYSQL[@]}" "$database" < "$file" >/dev/null
}

FIXTURE="$ROOT_DIR/scripts/fixtures/system-management-baseline.sql"
FORWARD="$ROOT_DIR/sql/erp_system_management_ux_v2_20260714.sql"
ROLLBACK="$ROOT_DIR/sql/erp_system_management_ux_v2_rollback_20260714.sql"
CONFIG_KEY="feature.system.management-ux-v2.enabled"

apply_file "$IDEMPOTENCY_DB" "$FIXTURE"
apply_file "$IDEMPOTENCY_DB" "$FORWARD"
apply_file "$IDEMPOTENCY_DB" "$FORWARD"
created_row="$("${MYSQL[@]}" "$IDEMPOTENCY_DB" --skip-column-names -e "SELECT CONCAT(COUNT(*), '|', MAX(config_value), '|', MAX(create_by)) FROM sys_config WHERE config_key='$CONFIG_KEY'")"
[[ "$created_row" == "1|false|system" ]] || fail "double execution did not preserve one disabled system-owned flag"
apply_file "$IDEMPOTENCY_DB" "$ROLLBACK"
remaining="$("${MYSQL[@]}" "$IDEMPOTENCY_DB" --skip-column-names -e "SELECT COUNT(*) FROM sys_config WHERE config_key='$CONFIG_KEY'")"
[[ "$remaining" == "0" ]] || fail "safe rollback did not remove the unchanged system-owned false flag"

apply_file "$PRESERVE_DB" "$FIXTURE"
"${MYSQL[@]}" "$PRESERVE_DB" -e "INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark) VALUES ('管理员预置', '$CONFIG_KEY', 'true', 'Y', 'admin', NOW(), 'must survive release scripts');"
apply_file "$PRESERVE_DB" "$FORWARD"
apply_file "$PRESERVE_DB" "$ROLLBACK"
preserved_row="$("${MYSQL[@]}" "$PRESERVE_DB" --skip-column-names -e "SELECT CONCAT(COUNT(*), '|', MAX(config_value), '|', MAX(create_by)) FROM sys_config WHERE config_key='$CONFIG_KEY'")"
[[ "$preserved_row" == "1|true|admin" ]] || fail "release scripts changed or removed an administrator-owned flag"

printf 'SYSTEM_MANAGEMENT_UX_V2_NATIVE_MYSQL_OK server=%s idempotent_runs=2 rollback=guarded\n' "$server_version"
