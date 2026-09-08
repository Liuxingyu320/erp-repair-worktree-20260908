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
IDEMPOTENCY_DB="erp_system_mgmt_test_${suffix}"
ROLLBACK_DB="erp_system_mgmt_test_${suffix}_rollback"
[[ "$IDEMPOTENCY_DB" =~ ^erp_system_mgmt_test_[0-9a-f]{12}$ ]] \
    || fail "unsafe generated test database name"
[[ "$ROLLBACK_DB" =~ ^erp_system_mgmt_test_[0-9a-f]{12}_rollback$ ]] \
    || fail "unsafe generated rollback database name"

MYSQL=(mysql --protocol=tcp --host="$HOST" --port="$PORT" --user="$USER_NAME" \
    --batch --raw --default-character-set=utf8mb4)

cleanup()
{
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$IDEMPOTENCY_DB\`; DROP DATABASE IF EXISTS \`$ROLLBACK_DB\`;" \
        >/dev/null 2>&1 || true
    unset MYSQL_PWD
}
trap cleanup EXIT HUP INT TERM

server_version="$("${MYSQL[@]}" --skip-column-names -e 'select @@version')"
case "$server_version" in
    5.7.*|8.0.*) ;;
    *) fail "unsupported native MySQL version: $server_version" ;;
esac

"${MYSQL[@]}" -e "CREATE DATABASE \`$IDEMPOTENCY_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE \`$ROLLBACK_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

apply_file()
{
    local database="$1"
    local file="$2"
    [[ "$database" == erp_system_mgmt_test_* ]] || fail "refusing unsafe test database"
    [[ -r "$file" ]] || fail "missing SQL file: $file"
    "${MYSQL[@]}" "$database" < "$file" >/dev/null
}

FIXTURE="$ROOT_DIR/scripts/fixtures/system-management-baseline.sql"
EXPAND="$ROOT_DIR/sql/erp_system_management_expand_20260714.sql"
PERMISSIONS="$ROOT_DIR/sql/erp_system_management_permissions_20260714.sql"
FINALIZE="$ROOT_DIR/sql/erp_system_management_finalize_20260714.sql"
PERMISSIONS_ROLLBACK="$ROOT_DIR/sql/erp_system_management_permissions_rollback_20260714.sql"
EXPAND_ROLLBACK="$ROOT_DIR/sql/erp_system_management_expand_rollback_20260714.sql"

apply_file "$IDEMPOTENCY_DB" "$FIXTURE"
apply_file "$IDEMPOTENCY_DB" "$EXPAND"
apply_file "$IDEMPOTENCY_DB" "$EXPAND"
apply_file "$IDEMPOTENCY_DB" "$PERMISSIONS"
apply_file "$IDEMPOTENCY_DB" "$PERMISSIONS"

if apply_file "$IDEMPOTENCY_DB" "$FINALIZE" 2>/dev/null; then
    fail "finalize unexpectedly succeeded without a zero-match readiness audit"
fi
"${MYSQL[@]}" "$IDEMPOTENCY_DB" -e \
    "INSERT INTO sys_credential_migration_audit (batch_id, phase, target_database, total_user_count, shared_match_count, active_shared_match_count, change_required_count, temporary_count, candidate_digest, status) VALUES ('native-gate', 'FINAL_READINESS', DATABASE(), 1, 0, 0, 0, 0, REPEAT('0', 64), 'CONFIRMED');"
apply_file "$IDEMPOTENCY_DB" "$FINALIZE"
apply_file "$IDEMPOTENCY_DB" "$FINALIZE"

remaining_config="$("${MYSQL[@]}" "$IDEMPOTENCY_DB" --skip-column-names -e "SELECT COUNT(*) FROM sys_config WHERE config_key IN ('sys.user.initPassword','sys.account.initPasswordModify')")"
[[ "$remaining_config" == 0 ]] || fail "legacy credential config remains after finalize"
new_permission_count="$("${MYSQL[@]}" "$IDEMPOTENCY_DB" --skip-column-names -e "SELECT COUNT(*) FROM sys_menu WHERE perms IN ('system:config:refresh','system:user:pii:read','system:user:pii:edit','system:user:pii:export')")"
[[ "$new_permission_count" == 4 ]] || fail "new permission cardinality differs after double execution"

apply_file "$ROLLBACK_DB" "$FIXTURE"
apply_file "$ROLLBACK_DB" "$EXPAND"
apply_file "$ROLLBACK_DB" "$EXPAND"
apply_file "$ROLLBACK_DB" "$PERMISSIONS"
apply_file "$ROLLBACK_DB" "$PERMISSIONS_ROLLBACK"
apply_file "$ROLLBACK_DB" "$EXPAND_ROLLBACK"

remaining_security_tables="$("${MYSQL[@]}" "$ROLLBACK_DB" --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('sys_security_session_outbox','sys_user_pii_access_audit','sys_credential_migration_audit')")"
remaining_security_columns="$("${MYSQL[@]}" "$ROLLBACK_DB" --skip-column-names -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name IN ('credential_state','temporary_password_expires_at')")"
[[ "$remaining_security_tables" == 0 && "$remaining_security_columns" == 0 ]] \
    || fail "guarded expansion rollback left release-owned schema objects"

printf 'SYSTEM_MANAGEMENT_NATIVE_MYSQL_OK server=%s idempotent_runs=2 rollback=verified\n' "$server_version"
