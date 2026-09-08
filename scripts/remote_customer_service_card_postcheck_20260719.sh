#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Read-only runtime and configuration-audit verification. Run the dedicated
# preflight for the same phase immediately before this script.
PHASE="${1:-${CUSTOMER_CARD_PHASE:-disabled}}"
case "$PHASE" in
    disabled|single|dual|full) ;;
    *) echo 'usage: remote_customer_service_card_postcheck_20260719.sh [disabled|single|dual|full]' >&2; exit 2 ;;
esac

DB="${CUSTOMER_CARD_DATABASE:-BossERP_stock_state_75c59ee}"
PASS_FILE="${CUSTOMER_CARD_MYSQL_PASSWORD_FILE:-/root/.erp-mysql-root-pass}"
AUDIT_WINDOW_MINUTES="${CUSTOMER_CARD_AUDIT_WINDOW_MINUTES:-120}"
AUDIT_WINDOW_MAX_MINUTES=1440
RELEASE_ROOT="${CUSTOMER_CARD_RELEASE_ROOT:-/opt/erp-new}"
RELEASE_MANIFEST="$RELEASE_ROOT/release/customer-service-card-release-20260719.json"
ENABLED_KEY='feature.inventory.customer-service-card.enabled'
ALLOWLIST_KEY='feature.inventory.customer-service-card.allowed-shop-dept-ids'
ALLOWLIST_MAX_CHARS=500

[[ "$DB" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'UNSAFE_DATABASE_NAME' >&2; exit 3; }
[[ "$AUDIT_WINDOW_MINUTES" =~ ^[1-9][0-9]{0,3}$ ]] || { echo 'INVALID_AUDIT_WINDOW' >&2; exit 3; }
(( 10#$AUDIT_WINDOW_MINUTES <= AUDIT_WINDOW_MAX_MINUTES )) \
    || { echo 'INVALID_AUDIT_WINDOW maximum is 1440 minutes' >&2; exit 3; }
for command in mysql python3 systemctl curl; do
    command -v "$command" >/dev/null 2>&1 || { echo "MISSING_COMMAND_$command" >&2; exit 3; }
done
[[ -r "$PASS_FILE" ]] || { echo 'MYSQL_PASSWORD_FILE_UNAVAILABLE' >&2; exit 3; }
[[ -r "$RELEASE_MANIFEST" ]] || { echo 'CUSTOMER_CARD_RELEASE_MANIFEST_MISSING' >&2; exit 3; }

python3 - "$RELEASE_MANIFEST" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if manifest.get("releaseId") != "customer-service-card-code-only-20260719":
    raise SystemExit("RUNTIME_RELEASE_ID_MISMATCH")
if manifest.get("deploymentMode") != "code-only":
    raise SystemExit("RUNTIME_DEPLOYMENT_MODE_MISMATCH")
if manifest.get("migrationCount") != 0 or manifest.get("migrations") != []:
    raise SystemExit("RUNTIME_MIGRATION_CONTRACT_VIOLATION")
if manifest.get("featureFlagMutationCount") != 0 or manifest.get("featureFlags") != []:
    raise SystemExit("RUNTIME_FEATURE_FLAG_MUTATION_CONTRACT_VIOLATION")
print("runtime_manifest=ok deploymentMode=code-only migrations=0 featureFlags=0")
PY

systemctl is-active --quiet erp-new@inventory.service || {
    echo 'INVENTORY_SERVICE_NOT_ACTIVE' >&2
    exit 4
}
inventory_health="$(curl -fsS --max-time 5 http://127.0.0.1:9205/actuator/health)"
printf '%s' "$inventory_health" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || {
    echo 'INVENTORY_SERVICE_NOT_READY' >&2
    exit 4
}
HEALTHCHECK_HOST="${ERP_HEALTHCHECK_HOST:-8.152.199.39}"
curl -fsS --max-time 8 -H "Host: $HEALTHCHECK_HOST" http://127.0.0.1/ >/dev/null
curl -fsS --max-time 8 -H "Host: $HEALTHCHECK_HOST" http://127.0.0.1/prod-api/code >/dev/null

MYSQL_PWD="$(<"$PASS_FILE")"
[[ -n "$MYSQL_PWD" && "$MYSQL_PWD" != *$'\n'* && "$MYSQL_PWD" != *$'\r'* ]] \
    || { echo 'INVALID_MYSQL_PASSWORD_FILE' >&2; exit 3; }
export MYSQL_PWD
trap 'unset MYSQL_PWD' EXIT HUP INT TERM
MYSQL=(mysql --protocol=socket --connect-timeout=10 --batch --raw \
    --skip-column-names --init-command='SET SESSION TRANSACTION READ ONLY' "$DB")

ID_ERROR_PREFIX='POSTCHECK_BLOCKED'
# BEGIN STRICT_POSITIVE_LONG_ID_VALIDATION
LONG_MAX_ID='9223372036854775807'

trim_edges()
{
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

validate_positive_long_id()
{
    local raw_value="$1"
    local label="$2"
    local candidate
    candidate="$(trim_edges "$raw_value")"
    if [[ "$candidate" == '*' ]]; then
        echo "$ID_ERROR_PREFIX wildcard $label is forbidden" >&2
        return 1
    fi
    [[ "$candidate" =~ ^[1-9][0-9]*$ ]] || {
        echo "$ID_ERROR_PREFIX invalid $label syntax" >&2
        return 1
    }
    if (( ${#candidate} > ${#LONG_MAX_ID} )); then
        echo "$ID_ERROR_PREFIX $label exceeds Java Long maximum" >&2
        return 1
    fi
    if (( ${#candidate} == ${#LONG_MAX_ID} )) \
        && [[ "$candidate" > "$LONG_MAX_ID" ]]; then
        echo "$ID_ERROR_PREFIX $label exceeds Java Long maximum" >&2
        return 1
    fi
    VALIDATED_ID="$candidate"
}

parse_positive_long_id_list()
{
    local raw_value="$1"
    local label="$2"
    local remaining token candidate has_more
    local canonical='' id_lines='' count=0 seen_ids=','

    remaining="$(trim_edges "$raw_value")"
    [[ -n "$remaining" ]] || {
        echo "$ID_ERROR_PREFIX invalid $label syntax" >&2
        return 1
    }
    while true; do
        if [[ "$remaining" == *,* ]]; then
            token="${remaining%%,*}"
            remaining="${remaining#*,}"
            has_more=true
        else
            token="$remaining"
            has_more=false
        fi
        validate_positive_long_id "$token" "$label token" || return 1
        candidate="$VALIDATED_ID"
        [[ "$seen_ids" != *",$candidate,"* ]] || {
            echo "$ID_ERROR_PREFIX duplicate IDs in $label" >&2
            return 1
        }
        seen_ids+="$candidate,"
        if (( count > 0 )); then
            canonical+=','
        fi
        canonical+="$candidate"
        id_lines+="$candidate"$'\n'
        ((count += 1))
        [[ "$has_more" == true ]] || break
    done

    PARSED_ID_LIST="$canonical"
    PARSED_ID_SORTED="$(printf '%s' "$id_lines" | LC_ALL=C sort -u | paste -sd, -)"
    PARSED_ID_COUNT="$count"
}
# END STRICT_POSITIVE_LONG_ID_VALIDATION

feature_value="$("${MYSQL[@]}" --execute \
    "SELECT LOWER(TRIM(config_value)) FROM sys_config WHERE config_key='$ENABLED_KEY' LIMIT 1;")"
allowlist_value="$("${MYSQL[@]}" --execute \
    "SELECT config_value FROM sys_config WHERE config_key='$ALLOWLIST_KEY' LIMIT 1;")"
(( ${#allowlist_value} <= ALLOWLIST_MAX_CHARS )) || {
    echo "POSTCHECK_BLOCKED allowlist exceeds sys_config limit of $ALLOWLIST_MAX_CHARS characters" >&2
    exit 5
}
parse_positive_long_id_list "$allowlist_value" 'allowlist' || exit 5
allowlist_compact="$PARSED_ID_LIST"
allowlist_count="$PARSED_ID_COUNT"

case "$PHASE" in
    single) [[ "$allowlist_count" == '1' ]] || { echo 'POSTCHECK_BLOCKED single phase requires one store' >&2; exit 5; } ;;
    dual) [[ "$allowlist_count" == '2' ]] || { echo 'POSTCHECK_BLOCKED dual phase requires two stores' >&2; exit 5; } ;;
esac

read_fresh_token()
{
    local token_file="$1"
    local now_epoch file_epoch file_mode token
    [[ -f "$token_file" && -r "$token_file" ]] || {
        echo "POSTCHECK_BLOCKED token file unavailable: $token_file" >&2
        return 1
    }
    file_mode="$(stat -c '%a' "$token_file")"
    [[ "$file_mode" == '600' || "$file_mode" == '400' ]] || {
        echo "POSTCHECK_BLOCKED token file mode must be 400 or 600: $token_file" >&2
        return 1
    }
    now_epoch="$(date +%s)"
    file_epoch="$(stat -c '%Y' "$token_file")"
    (( now_epoch - file_epoch <= AUDIT_WINDOW_MINUTES * 60 )) || {
        echo "POSTCHECK_BLOCKED token evidence is older than the audit window: $token_file" >&2
        return 1
    }
    token="$(<"$token_file")"
    [[ "$token" =~ ^[A-Za-z0-9._-]+$ ]] || {
        echo "POSTCHECK_BLOCKED invalid token file content: $token_file" >&2
        return 1
    }
    printf '%s' "$token"
}

api_call()
{
    local method="$1" token="$2" shop_id="$3" path="$4" body="${5:-}"
    local args=(--silent --show-error --max-time 10
        --request "$method"
        --header "Authorization: Bearer $token"
        --header "Dept-NumId: $shop_id"
        --header 'Content-Type: application/json'
        --header "Host: $HEALTHCHECK_HOST")
    if [[ -n "$body" ]]; then
        args+=(--data "$body")
    fi
    curl "${args[@]}" "http://127.0.0.1/prod-api$path"
}

assert_api_semantic()
{
    local expectation="$1"
    python3 -c '
import json, sys
kind = sys.argv[1]
try:
    payload = json.load(sys.stdin)
except Exception as exc:
    raise SystemExit("POSTCHECK_BLOCKED invalid API JSON: " + type(exc).__name__)
code = payload.get("code")
message = str(payload.get("msg") or "")
data = payload.get("data") or {}
checks = {
    "capability_true": code == 200 and data.get("writeEnabled") is True,
    "capability_false": code == 200 and data.get("writeEnabled") is False,
    "legacy_replaced_admin": "FEATURE_REPLACED" in message,
}
if not checks.get(kind, False):
    raise SystemExit("POSTCHECK_BLOCKED API semantic mismatch: " + kind)
print("api_semantic=" + kind + " ok")
' "$expectation"
}

if [[ "$PHASE" == 'disabled' ]]; then
    [[ "$feature_value" =~ ^(false|0|no|off)$ ]] || {
        echo 'POSTCHECK_BLOCKED feature must remain disabled after code deployment' >&2
        exit 5
    }
else
    [[ "$feature_value" =~ ^(true|1|yes|on)$ ]] || {
        echo 'POSTCHECK_BLOCKED feature is not enabled for rollout' >&2
        exit 5
    }

    if [[ "$PHASE" == 'single' ]]; then
        required_recent_keys=2
        recent_key_filter="'$ENABLED_KEY','$ALLOWLIST_KEY'"
    else
        required_recent_keys=1
        recent_key_filter="'$ALLOWLIST_KEY'"
    fi
    audited_config_count="$("${MYSQL[@]}" --execute "
        SELECT COUNT(*)
        FROM sys_config config
        WHERE config.config_key IN ($recent_key_filter)
          AND GREATEST(COALESCE(config.update_time,config.create_time),config.create_time)
              >= DATE_SUB(NOW(), INTERVAL $AUDIT_WINDOW_MINUTES MINUTE)
          AND COALESCE(NULLIF(config.update_by,''),NULLIF(config.create_by,'')) IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM sys_oper_log operation_log
              WHERE operation_log.title='参数管理'
                AND operation_log.business_type IN (1,2)
                AND operation_log.status=0
                AND operation_log.oper_name=
                    COALESCE(NULLIF(config.update_by,''),NULLIF(config.create_by,''))
                AND operation_log.oper_time>=DATE_SUB(NOW(), INTERVAL $AUDIT_WINDOW_MINUTES MINUTE)
          );")"
    [[ "$audited_config_count" == "$required_recent_keys" ]] || {
        echo "POSTCHECK_BLOCKED parameter audit evidence missing expected=$required_recent_keys actual=$audited_config_count window_minutes=$AUDIT_WINDOW_MINUTES" >&2
        exit 5
    }
    printf 'parameter_audit=ok recent_keys=%s window_minutes=%s\n' \
        "$audited_config_count" "$AUDIT_WINDOW_MINUTES"

    PILOT_SHOP_ID_RAW="${CUSTOMER_CARD_PILOT_SHOP_ID:-}"
    PILOT_TOKEN_FILE="${CUSTOMER_CARD_PILOT_TOKEN_FILE:-}"
    LEGACY_ADMIN_TOKEN_FILE="${CUSTOMER_CARD_LEGACY_ADMIN_TOKEN_FILE:-}"
    [[ -n "$PILOT_TOKEN_FILE" && -n "$LEGACY_ADMIN_TOKEN_FILE" ]] || {
        echo 'POSTCHECK_BLOCKED pilot and dedicated legacy-admin token-file evidence is required after re-login' >&2
        exit 5
    }
    validate_positive_long_id "$PILOT_SHOP_ID_RAW" \
        'CUSTOMER_CARD_PILOT_SHOP_ID' || exit 5
    PILOT_SHOP_ID="$VALIDATED_ID"
    [[ ",$allowlist_compact," == *",$PILOT_SHOP_ID,"* ]] || {
        echo 'POSTCHECK_BLOCKED pilot shop is not allowlisted' >&2
        exit 5
    }
    pilot_token="$(read_fresh_token "$PILOT_TOKEN_FILE")"
    legacy_admin_token="$(read_fresh_token "$LEGACY_ADMIN_TOKEN_FILE")"
    api_call GET "$pilot_token" "$PILOT_SHOP_ID" \
        '/inventory/customer/service-card/capabilities' \
        | assert_api_semantic capability_true
    # A normal fresh store token may be rejected by permission interception
    # before the legacy guard. Use a separate fresh admin/*:*:* token so this
    # probe specifically proves the global FEATURE_REPLACED branch.
    api_call GET "$legacy_admin_token" "$PILOT_SHOP_ID" \
        '/inventory/customer/list?pageNum=1&pageSize=1' \
        | assert_api_semantic legacy_replaced_admin

    if [[ "$PHASE" != 'full' ]]; then
        NONPILOT_SHOP_ID_RAW="${CUSTOMER_CARD_NONPILOT_SHOP_ID:-}"
        NONPILOT_TOKEN_FILE="${CUSTOMER_CARD_NONPILOT_TOKEN_FILE:-}"
        [[ -n "$NONPILOT_TOKEN_FILE" ]] || {
            echo 'POSTCHECK_BLOCKED non-pilot shop/token-file evidence is required' >&2
            exit 5
        }
        validate_positive_long_id "$NONPILOT_SHOP_ID_RAW" \
            'CUSTOMER_CARD_NONPILOT_SHOP_ID' || exit 5
        NONPILOT_SHOP_ID="$VALIDATED_ID"
        [[ ",$allowlist_compact," != *",$NONPILOT_SHOP_ID,"* ]] || {
            echo 'POSTCHECK_BLOCKED non-pilot shop unexpectedly appears in allowlist' >&2
            exit 5
        }
        nonpilot_token="$(read_fresh_token "$NONPILOT_TOKEN_FILE")"
        api_call GET "$nonpilot_token" "$NONPILOT_SHOP_ID" \
            '/inventory/customer/service-card/capabilities' \
            | assert_api_semantic capability_false
    fi
fi

printf 'inventory_service=active inventory_health=UP feature_state=%s allowlist=%s\n' \
    "$feature_value" "$allowlist_compact"
echo "CUSTOMER_SERVICE_CARD_REMOTE_POSTCHECK_OK phase=$PHASE database_writes=0"
