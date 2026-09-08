#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Strictly read-only MySQL gate. The optional expected phase is one of:
# disabled, single, dual, full. Full means an explicit list of every currently
# active store; wildcard allowlists are deliberately forbidden.
PHASE="${1:-${CUSTOMER_CARD_PHASE:-disabled}}"
case "$PHASE" in
    disabled|single|dual|full) ;;
    *) echo 'usage: remote_customer_service_card_preflight_20260719.sh [disabled|single|dual|full]' >&2; exit 2 ;;
esac

DB="${CUSTOMER_CARD_DATABASE:-BossERP_stock_state_75c59ee}"
PASS_FILE="${CUSTOMER_CARD_MYSQL_PASSWORD_FILE:-/root/.erp-mysql-root-pass}"
EXPECTED_SHOPS="${CUSTOMER_CARD_EXPECTED_SHOPS:-}"
ENABLED_KEY='feature.inventory.customer-service-card.enabled'
ALLOWLIST_KEY='feature.inventory.customer-service-card.allowed-shop-dept-ids'
ALLOWLIST_MAX_CHARS=500

[[ "$DB" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'UNSAFE_DATABASE_NAME' >&2; exit 3; }
command -v mysql >/dev/null 2>&1 || { echo 'MYSQL_CLIENT_REQUIRED' >&2; exit 3; }
[[ -r "$PASS_FILE" ]] || { echo 'MYSQL_PASSWORD_FILE_UNAVAILABLE' >&2; exit 3; }

MYSQL_PWD="$(<"$PASS_FILE")"
[[ -n "$MYSQL_PWD" && "$MYSQL_PWD" != *$'\n'* && "$MYSQL_PWD" != *$'\r'* ]] \
    || { echo 'INVALID_MYSQL_PASSWORD_FILE' >&2; exit 3; }
export MYSQL_PWD
trap 'unset MYSQL_PWD' EXIT HUP INT TERM

MYSQL=(mysql --protocol=socket --connect-timeout=10 --batch --raw \
    --skip-column-names --init-command='SET SESSION TRANSACTION READ ONLY' "$DB")

ID_ERROR_PREFIX='PREFLIGHT_BLOCKED'
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

mysql_scalar()
{
    local result
    result="$("${MYSQL[@]}")"
    [[ "$result" =~ ^[0-9]+$ ]] || {
        echo "NON_NUMERIC_MYSQL_RESULT: $result" >&2
        return 1
    }
    printf '%s\n' "$result"
}

fail_nonzero()
{
    local name="$1"
    local value="$2"
    if [[ "$value" != '0' ]]; then
        echo "PREFLIGHT_BLOCKED $name=$value" >&2
        return 1
    fi
    printf '%s=%s\n' "$name" "$value"
}

schema_missing="$(mysql_scalar <<'SQL'
SELECT COUNT(*)
FROM (
    SELECT 'inv_customer_service_profile' table_name, 'customer_id' column_name
    UNION ALL SELECT 'inv_customer_service_profile','photo_node_id'
    UNION ALL SELECT 'inv_customer_service_profile','tea_preferences'
    UNION ALL SELECT 'inv_customer_service_profile','preference_tags'
    UNION ALL SELECT 'inv_customer_service_profile','brewing_service_preferences'
    UNION ALL SELECT 'inv_customer_service_profile','cautions'
    UNION ALL SELECT 'inv_customer_service_profile','budget_min'
    UNION ALL SELECT 'inv_customer_service_profile','budget_max'
    UNION ALL SELECT 'inv_customer_service_profile','last_visit_date'
    UNION ALL SELECT 'inv_customer_service_profile','version'
    UNION ALL SELECT 'inv_customer_service_profile','create_by'
    UNION ALL SELECT 'inv_customer_service_profile','create_time'
    UNION ALL SELECT 'inv_customer_service_profile','update_by'
    UNION ALL SELECT 'inv_customer_service_profile','update_time'
    UNION ALL SELECT 'inv_customer_service_record','record_id'
    UNION ALL SELECT 'inv_customer_service_record','customer_id'
    UNION ALL SELECT 'inv_customer_service_record','service_date'
    UNION ALL SELECT 'inv_customer_service_record','service_user_id'
    UNION ALL SELECT 'inv_customer_service_record','service_user_name'
    UNION ALL SELECT 'inv_customer_service_record','party_size'
    UNION ALL SELECT 'inv_customer_service_record','tea_served'
    UNION ALL SELECT 'inv_customer_service_record','preference_snapshot'
    UNION ALL SELECT 'inv_customer_service_record','caution_snapshot'
    UNION ALL SELECT 'inv_customer_service_record','service_note'
    UNION ALL SELECT 'inv_customer_service_record','consumption_amount'
    UNION ALL SELECT 'inv_customer_service_record','shop_dept_id'
    UNION ALL SELECT 'inv_customer_service_record','request_key'
    UNION ALL SELECT 'inv_customer_service_record','source_client'
    UNION ALL SELECT 'inv_customer_service_record','create_by'
    UNION ALL SELECT 'inv_customer_service_record','create_time'
    UNION ALL SELECT 'inv_customer_service_change_log','log_id'
    UNION ALL SELECT 'inv_customer_service_change_log','customer_id'
    UNION ALL SELECT 'inv_customer_service_change_log','request_key'
    UNION ALL SELECT 'inv_customer_service_change_log','change_type'
    UNION ALL SELECT 'inv_customer_service_change_log','changed_fields'
    UNION ALL SELECT 'inv_customer_service_change_log','before_summary'
    UNION ALL SELECT 'inv_customer_service_change_log','after_summary'
    UNION ALL SELECT 'inv_customer_service_change_log','operator_user_id'
    UNION ALL SELECT 'inv_customer_service_change_log','operator_name'
    UNION ALL SELECT 'inv_customer_service_change_log','shop_dept_id'
    UNION ALL SELECT 'inv_customer_service_change_log','source_client'
    UNION ALL SELECT 'inv_customer_service_change_log','create_time'
) expected
LEFT JOIN information_schema.COLUMNS actual
  ON actual.TABLE_SCHEMA=DATABASE()
 AND actual.TABLE_NAME=expected.table_name
 AND actual.COLUMN_NAME=expected.column_name
WHERE actual.COLUMN_NAME IS NULL;
SQL
)"
fail_nonzero schema_missing "$schema_missing"

index_missing="$(mysql_scalar <<'SQL'
SELECT COUNT(*)
FROM (
    SELECT 'inv_customer_service_profile' table_name, 'PRIMARY' index_name,
           'customer_id' columns_csv, 0 non_unique
    UNION ALL SELECT 'inv_customer_service_record','uk_inv_customer_service_record_request',
                     'customer_id,request_key',0
    UNION ALL SELECT 'inv_customer_service_change_log','uk_inv_customer_service_log_request',
                     'customer_id,request_key',0
    UNION ALL SELECT 'inv_customer_service_change_log','uk_inv_customer_service_log_shop_request_type',
                     'shop_dept_id,request_key,change_type',0
) expected
LEFT JOIN (
    SELECT TABLE_NAME, INDEX_NAME, MIN(NON_UNIQUE) non_unique,
           GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') columns_csv
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME IN ('inv_customer_service_profile','inv_customer_service_record',
                         'inv_customer_service_change_log')
    GROUP BY TABLE_NAME, INDEX_NAME
) actual
  ON actual.TABLE_NAME=expected.table_name
 AND actual.INDEX_NAME=expected.index_name
 AND actual.columns_csv=expected.columns_csv
 AND actual.non_unique=expected.non_unique
WHERE actual.INDEX_NAME IS NULL;
SQL
)"
fail_nonzero required_index_missing "$index_missing"

permission_object_anomalies="$(mysql_scalar <<'SQL'
SELECT COUNT(*)
FROM (
    SELECT required.permission_code,
           SUM(CASE WHEN menu.status='0' THEN 1 ELSE 0 END) active_count
    FROM (
        SELECT 'inv:customer:option' permission_code
        UNION ALL SELECT 'inv:customerCard:list'
        UNION ALL SELECT 'inv:customerCard:query'
        UNION ALL SELECT 'inv:customerCard:add'
        UNION ALL SELECT 'inv:customerCard:edit'
        UNION ALL SELECT 'inv:customerCard:record:add'
        UNION ALL SELECT 'inv:customerCard:archive'
        UNION ALL SELECT 'inv:customerCard:audit'
    ) required
    LEFT JOIN sys_menu menu ON menu.perms=required.permission_code
    GROUP BY required.permission_code
) checked
WHERE active_count <> 1;
SQL
)"
fail_nonzero permission_object_anomalies "$permission_object_anomalies"

customer_page_anomalies="$(mysql_scalar <<'SQL'
SELECT COUNT(*)
FROM sys_menu
WHERE perms='inv:customerCard:list'
  AND (status<>'0' OR visible<>'0' OR menu_type<>'C'
       OR component<>'inventory/customer/index');
SQL
)"
fail_nonzero customer_page_anomalies "$customer_page_anomalies"

employee_permission_gaps="$(mysql_scalar <<'SQL'
SELECT COUNT(*)
FROM sys_user_shop membership
JOIN sys_dept shop
  ON shop.dept_id=membership.dept_id
 AND UPPER(COALESCE(shop.dept_type,''))='STORE'
 AND shop.status='0' AND shop.del_flag='0'
JOIN sys_user user_account ON user_account.user_id=membership.user_id
LEFT JOIN sys_user_profile profile ON profile.user_id=user_account.user_id
JOIN (
    SELECT 'inv:customerCard:list' permission_code
    UNION ALL SELECT 'inv:customerCard:query'
    UNION ALL SELECT 'inv:customerCard:add'
    UNION ALL SELECT 'inv:customerCard:edit'
    UNION ALL SELECT 'inv:customerCard:record:add'
) required
WHERE user_account.user_id<>1
  AND user_account.status='0' AND user_account.del_flag='0'
  AND (profile.employee_status IS NULL OR profile.employee_status<>'离职')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role user_role
      JOIN sys_role role_row ON role_row.role_id=user_role.role_id
                            AND role_row.status='0' AND role_row.del_flag='0'
      JOIN sys_role_menu role_menu ON role_menu.role_id=role_row.role_id
      JOIN sys_menu permission_menu ON permission_menu.menu_id=role_menu.menu_id
                                   AND permission_menu.status='0'
      WHERE user_role.user_id=user_account.user_id
        AND permission_menu.perms=required.permission_code
  );
SQL
)"
fail_nonzero active_store_employee_permission_gaps "$employee_permission_gaps"

config_key_anomalies="$(mysql_scalar <<SQL
SELECT COUNT(*)
FROM (
    SELECT required.config_key, COUNT(config.config_id) key_count
    FROM (
        SELECT '$ENABLED_KEY' config_key
        UNION ALL SELECT '$ALLOWLIST_KEY'
    ) required
    LEFT JOIN sys_config config ON config.config_key=required.config_key
    GROUP BY required.config_key
) checked
WHERE key_count<>1;
SQL
)"
fail_nonzero config_key_anomalies "$config_key_anomalies"

feature_value="$("${MYSQL[@]}" --execute \
    "SELECT LOWER(TRIM(config_value)) FROM sys_config WHERE config_key='$ENABLED_KEY' LIMIT 1;")"
allowlist_value="$("${MYSQL[@]}" --execute \
    "SELECT config_value FROM sys_config WHERE config_key='$ALLOWLIST_KEY' LIMIT 1;")"
(( ${#allowlist_value} <= ALLOWLIST_MAX_CHARS )) || {
    echo "PREFLIGHT_BLOCKED allowlist exceeds sys_config limit of $ALLOWLIST_MAX_CHARS characters" >&2
    exit 4
}
parse_positive_long_id_list "$allowlist_value" 'allowlist' || exit 4
allowlist_compact="$PARSED_ID_LIST"
allowlist_sorted="$PARSED_ID_SORTED"
allowlist_count="$PARSED_ID_COUNT"

case "$PHASE" in
    disabled)
        [[ "$feature_value" =~ ^(false|0|no|off)$ ]] || {
            echo "PREFLIGHT_BLOCKED expected_disabled feature_value=$feature_value" >&2
            exit 4
        }
        ;;
    single|dual|full)
        [[ "$feature_value" =~ ^(true|1|yes|on)$ ]] || {
            echo "PREFLIGHT_BLOCKED expected_enabled feature_value=$feature_value" >&2
            exit 4
        }
        [[ -n "$EXPECTED_SHOPS" ]] || {
            echo 'PREFLIGHT_BLOCKED CUSTOMER_CARD_EXPECTED_SHOPS is required for rollout phases' >&2
            exit 4
        }
        ;;
esac

inactive_allowlist_shops="$("${MYSQL[@]}" --execute \
    "SELECT COUNT(*) FROM sys_dept WHERE dept_id IN ($allowlist_compact) AND NOT (UPPER(COALESCE(dept_type,''))='STORE' AND status='0' AND del_flag='0');")"
matched_allowlist_shops="$("${MYSQL[@]}" --execute \
    "SELECT COUNT(*) FROM sys_dept WHERE dept_id IN ($allowlist_compact) AND UPPER(COALESCE(dept_type,''))='STORE' AND status='0' AND del_flag='0';")"
[[ "$inactive_allowlist_shops" == '0' && "$matched_allowlist_shops" == "$allowlist_count" ]] || {
    echo "PREFLIGHT_BLOCKED allowlist does not resolve to active stores count=$allowlist_count matched=$matched_allowlist_shops inactive=$inactive_allowlist_shops" >&2
    exit 4
}

case "$PHASE" in
    single) [[ "$allowlist_count" == '1' ]] || { echo 'PREFLIGHT_BLOCKED single phase requires one store' >&2; exit 4; } ;;
    dual) [[ "$allowlist_count" == '2' ]] || { echo 'PREFLIGHT_BLOCKED dual phase requires two stores' >&2; exit 4; } ;;
    full)
        active_store_count="$("${MYSQL[@]}" --execute \
            "SELECT COUNT(*) FROM sys_dept WHERE UPPER(COALESCE(dept_type,''))='STORE' AND status='0' AND del_flag='0';")"
        [[ "$allowlist_count" == "$active_store_count" ]] || {
            echo "PREFLIGHT_BLOCKED full phase requires every current active store allowlist=$allowlist_count active=$active_store_count" >&2
            exit 4
        }
        ;;
esac

if [[ -n "$EXPECTED_SHOPS" ]]; then
    parse_positive_long_id_list "$EXPECTED_SHOPS" 'CUSTOMER_CARD_EXPECTED_SHOPS' || exit 4
    expected_sorted="$PARSED_ID_SORTED"
    [[ "$allowlist_sorted" == "$expected_sorted" ]] || {
        echo "PREFLIGHT_BLOCKED allowlist differs from approved expected shops" >&2
        exit 4
    }
fi

orphan_profiles="$(mysql_scalar <<'SQL'
SELECT COUNT(*) FROM inv_customer_service_profile profile
LEFT JOIN inv_customer customer ON customer.customer_id=profile.customer_id
WHERE customer.customer_id IS NULL;
SQL
)"
orphan_records="$(mysql_scalar <<'SQL'
SELECT COUNT(*) FROM inv_customer_service_record service_record
LEFT JOIN inv_customer customer ON customer.customer_id=service_record.customer_id
WHERE customer.customer_id IS NULL;
SQL
)"
orphan_change_logs="$(mysql_scalar <<'SQL'
SELECT COUNT(*) FROM inv_customer_service_change_log change_log
LEFT JOIN inv_customer customer ON customer.customer_id=change_log.customer_id
WHERE customer.customer_id IS NULL;
SQL
)"
missing_profiles="$(mysql_scalar <<'SQL'
SELECT COUNT(*) FROM inv_customer customer
LEFT JOIN inv_customer_service_profile profile ON profile.customer_id=customer.customer_id
WHERE profile.customer_id IS NULL;
SQL
)"
record_cross_store="$(mysql_scalar <<'SQL'
SELECT COUNT(*) FROM inv_customer_service_record service_record
JOIN inv_customer customer ON customer.customer_id=service_record.customer_id
WHERE NOT (service_record.shop_dept_id <=> customer.shop_dept_id);
SQL
)"
change_log_cross_store="$(mysql_scalar <<'SQL'
SELECT COUNT(*) FROM inv_customer_service_change_log change_log
JOIN inv_customer customer ON customer.customer_id=change_log.customer_id
WHERE NOT (change_log.shop_dept_id <=> customer.shop_dept_id);
SQL
)"
invalid_customer_store_scope="$(mysql_scalar <<'SQL'
SELECT COUNT(*)
FROM inv_customer customer
LEFT JOIN sys_dept shop ON shop.dept_id=customer.shop_dept_id
WHERE customer.shop_dept_id IS NULL
   OR customer.shop_dept_id<=0
   OR shop.dept_id IS NULL
   OR UPPER(COALESCE(shop.dept_type,''))<>'STORE'
   OR COALESCE(shop.status,'')<>'0'
   OR COALESCE(shop.del_flag,'')<>'0';
SQL
)"

fail_nonzero orphan_profiles "$orphan_profiles"
fail_nonzero orphan_records "$orphan_records"
fail_nonzero orphan_change_logs "$orphan_change_logs"
fail_nonzero missing_profiles "$missing_profiles"
fail_nonzero record_cross_store "$record_cross_store"
fail_nonzero change_log_cross_store "$change_log_cross_store"
fail_nonzero invalid_customer_store_scope "$invalid_customer_store_scope"

printf 'feature_state=%s phase=%s allowlist_count=%s allowlist=%s\n' \
    "$feature_value" "$PHASE" "$allowlist_count" "${allowlist_compact:-<empty>}"
if [[ "$PHASE" == 'disabled' ]]; then
    echo 'runtime_semantic_expectation capability=false legacy=available writes=FEATURE_DISABLED'
else
    echo 'runtime_semantic_expectation allowlisted_capability=true nonallowlisted_capability=false authorized_legacy_probe_all_stores=FEATURE_REPLACED ordinary_legacy_permission_denial=safe nonallowlisted_write=FEATURE_DISABLED_FOR_SHOP'
fi
echo "CUSTOMER_SERVICE_CARD_REMOTE_PREFLIGHT_OK phase=$PHASE database=$DB readonly=true"
