#!/bin/sh
set -eu

DATABASE=
APPROVAL=

usage() {
    echo "Usage: sh scripts/verify-system-oper-log-redaction.sh --database NAME --approve-readonly NAME"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --database) DATABASE=${2:-}; shift 2 ;;
        --approve-readonly) APPROVAL=${2:-}; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done

case "$DATABASE" in
    ''|*[!A-Za-z0-9_]*) echo "unsafe database name" >&2; exit 2 ;;
esac
[ "$APPROVAL" = "$DATABASE" ] || { echo "--approve-readonly must exactly match database" >&2; exit 2; }

MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASSWORD_FILE=${MYSQL_PASSWORD_FILE:-}
case "$MYSQL_HOST" in *[!A-Za-z0-9.:-]*) echo "unsafe MYSQL_HOST" >&2; exit 2 ;; esac
case "$MYSQL_PORT" in ''|*[!0-9]*) echo "unsafe MYSQL_PORT" >&2; exit 2 ;; esac
case "$MYSQL_USER" in ''|*[!A-Za-z0-9_@.-]*) echo "unsafe MYSQL_USER" >&2; exit 2 ;; esac
[ -n "$MYSQL_PASSWORD_FILE" ] && [ -r "$MYSQL_PASSWORD_FILE" ] \
    || { echo "MYSQL_PASSWORD_FILE must name a readable file" >&2; exit 2; }
command -v mysql >/dev/null 2>&1 || { echo "mysql client is required" >&2; exit 2; }

MYSQL_PWD=$(cat "$MYSQL_PASSWORD_FILE")
export MYSQL_PWD
trap 'unset MYSQL_PWD' EXIT HUP INT TERM

mysql_readonly_query() {
    mysql --protocol=tcp --batch --raw --skip-column-names --default-character-set=utf8mb4 \
        -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$DATABASE" -e "$1"
}

sensitive_count=$(mysql_readonly_query "
SELECT COUNT(*)
FROM sys_oper_log
WHERE LOWER(CONCAT_WS(' ', oper_param, json_result, error_msg)) REGEXP
      'signaturedataurl|password|passwd|secret|token|credential|bankaccount|bankcard|idnumber|idcard|privatekey|accesskey|phonenumber|;base64,|-----begin (rsa )?private key-----|/users/|/home/|/root/'
   OR CONCAT_WS(' ', oper_param, json_result, error_msg) REGEXP '(^|[^0-9])[0-9]{15,19}([^0-9]|$)';")

audit_incomplete=$(mysql_readonly_query "
SELECT COUNT(*) FROM sys_oper_log_redaction_audit WHERE execute_status <> 'COMPLETED';")

lengths=$(mysql_readonly_query "
SELECT COALESCE(MAX(CHAR_LENGTH(oper_param)),0),
       COALESCE(MAX(CHAR_LENGTH(json_result)),0),
       COALESCE(MAX(CHAR_LENGTH(error_msg)),0)
FROM sys_oper_log;")

[ "$sensitive_count" = "0" ] || { echo "sensitive_log_rows=$sensitive_count" >&2; exit 51; }
[ "$audit_incomplete" = "0" ] || { echo "incomplete_redaction_batches=$audit_incomplete" >&2; exit 52; }

echo "OPER_LOG_REDACTION_OK database=$DATABASE sensitive_rows=0 max_lengths=$lengths"
