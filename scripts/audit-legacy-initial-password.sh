#!/bin/sh
set -eu

required_vars="ERP_SYSTEM_JAR APPROVED_DATABASE CREDENTIAL_MAINTENANCE_CONFIRM CREDENTIAL_OPERATOR_USER_ID"
for name in $required_vars; do
    eval "value=\${$name:-}"
    if [ -z "$value" ]; then
        echo "missing required environment variable: $name" >&2
        exit 2
    fi
done

case "$APPROVED_DATABASE" in
    *[!A-Za-z0-9_]*|'') echo "unsafe APPROVED_DATABASE" >&2; exit 2 ;;
esac
case "$CREDENTIAL_OPERATOR_USER_ID" in
    *[!0-9]*|'') echo "CREDENTIAL_OPERATOR_USER_ID must be numeric" >&2; exit 2 ;;
esac
if [ "$CREDENTIAL_MAINTENANCE_CONFIRM" != "I_UNDERSTAND_CREDENTIAL_MAINTENANCE" ]; then
    echo "explicit credential maintenance confirmation does not match" >&2
    exit 2
fi
if [ ! -f "$ERP_SYSTEM_JAR" ]; then
    echo "ERP_SYSTEM_JAR is not a file" >&2
    exit 2
fi

MODE=${CREDENTIAL_MAINTENANCE_MODE:-AUDIT}
case "$MODE" in AUDIT|FINAL_READINESS) ;; *) echo "unsupported maintenance mode" >&2; exit 2 ;; esac
BATCH_ID=${CREDENTIAL_MAINTENANCE_BATCH_ID:-credential-audit-$(date -u +%Y%m%dT%H%M%SZ)}
case "$BATCH_ID" in *[!A-Za-z0-9_.-]*|'') echo "unsafe maintenance batch id" >&2; exit 2 ;; esac
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
case "$MYSQL_HOST" in *[!A-Za-z0-9_.:-]*|'') echo "unsafe MYSQL_HOST" >&2; exit 2 ;; esac
case "$MYSQL_PORT" in *[!0-9]*|'') echo "unsafe MYSQL_PORT" >&2; exit 2 ;; esac

JAVA_BIN=${JAVA_BIN:-java}
exec "$JAVA_BIN" -jar "$ERP_SYSTEM_JAR" \
    --spring.main.web-application-type=none \
    --spring.profiles.active=local,credential-maintenance \
    --spring.cloud.nacos.discovery.enabled=false \
    --spring.cloud.nacos.config.enabled=false \
    --spring.datasource.druid.dynamic.datasource.master.url="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${APPROVED_DATABASE}?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
    --system.credential-maintenance.enabled=true \
    --system.credential-maintenance.mode="$MODE" \
    --system.credential-maintenance.confirmation="$CREDENTIAL_MAINTENANCE_CONFIRM" \
    --system.credential-maintenance.target-database="$APPROVED_DATABASE" \
    --system.credential-maintenance.approved-databases="$APPROVED_DATABASE" \
    --system.credential-maintenance.legacy-config-key="sys.user.initPassword" \
    --system.credential-maintenance.batch-id="$BATCH_ID" \
    --system.credential-maintenance.operator-user-id="$CREDENTIAL_OPERATOR_USER_ID"

