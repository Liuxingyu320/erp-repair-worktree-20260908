#!/usr/bin/env bash

# Runs session-governance integration and performance gates on one native
# non-production Redis DB. It never flushes a database and only cleans this run id.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${ROOT_DIR}/erp-modules/erp-system/target/failsafe-reports"
HOST="${ERP_IT_REDIS_HOST:-}"
PORT="${ERP_IT_REDIS_PORT:-6379}"
DATABASE="${ERP_IT_REDIS_DATABASE:-15}"
USERNAME="${ERP_IT_REDIS_USERNAME:-}"
PASSWORD="${ERP_IT_REDIS_PASSWORD:-}"
ALLOW_REMOTE="${ERP_IT_ALLOW_REMOTE_NONPROD:-0}"
RUN_ID="${ERP_IT_RUN_ID:-r$(date +%m%d%H%M%S)_$$}"
LOG_FILE="$(mktemp -t erp-native-redis-it.XXXXXX)"

# Local native Redis 8.6.2 baseline: two warmed runs, 20 measured samples each;
# defaults use the larger observed p95 and the gate allows at most 20% regression.
BASELINE_REVOKE_100="${ERP_IT_REDIS_BASELINE_REVOKE_P95_MS_100:-25}"
BASELINE_REVOKE_1000="${ERP_IT_REDIS_BASELINE_REVOKE_P95_MS_1000:-99}"
BASELINE_REVOKE_10000="${ERP_IT_REDIS_BASELINE_REVOKE_P95_MS_10000:-566}"
BASELINE_ONLINE_10000="${ERP_IT_REDIS_BASELINE_ONLINE_P95_MS_10000:-303}"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    printf '[PASS] %s\n' "$*"
}

redis_cli()
{
    local args=(--no-auth-warning -h "${HOST}" -p "${PORT}" -n "${DATABASE}")
    if [[ -n "${USERNAME}" ]]; then
        args+=(--user "${USERNAME}")
    fi
    if [[ -n "${PASSWORD}" ]]; then
        REDISCLI_AUTH="${PASSWORD}" redis-cli "${args[@]}" "$@"
    else
        env -u REDISCLI_AUTH redis-cli "${args[@]}" "$@"
    fi
}

command_calls()
{
    local command_name="$1"
    local line
    line="$(redis_cli INFO commandstats | tr -d '\r' | grep -E "^cmdstat_${command_name}:" || true)"
    if [[ -z "${line}" ]]; then
        printf '0\n'
        return
    fi
    sed -E 's/^[^:]+:calls=([0-9]+),.*/\1/' <<< "${line}"
}

cleanup_pattern()
{
    local pattern="$1"
    local batch=()
    local key
    while IFS= read -r key; do
        [[ -n "${key}" ]] || continue
        batch+=("${key}")
        if (( ${#batch[@]} >= 250 )); then
            redis_cli DEL "${batch[@]}" >/dev/null || return 1
            batch=()
        fi
    done < <(redis_cli --scan --pattern "${pattern}")
    if (( ${#batch[@]} > 0 )); then
        redis_cli DEL "${batch[@]}" >/dev/null || return 1
    fi
}

count_pattern()
{
    local pattern="$1"
    redis_cli --scan --pattern "${pattern}" | awk 'NF { count++ } END { print count + 0 }'
}

cleanup()
{
    local original_status=$?
    local cleanup_status=0
    trap - EXIT INT TERM
    set +e
    cleanup_pattern "login_tokens:${RUN_ID}_*"
    [[ $? -eq 0 ]] || cleanup_status=1
    cleanup_pattern "erp_it_redis:${RUN_ID}_*"
    [[ $? -eq 0 ]] || cleanup_status=1
    rm -f "${LOG_FILE}"
    set -e
    if [[ "${cleanup_status}" -ne 0 ]]; then
        printf '[FAIL] unable to clean all Redis keys for run id %s\n' "${RUN_ID}" >&2
    fi
    if [[ "${original_status}" -ne 0 ]]; then
        exit "${original_status}"
    fi
    exit "${cleanup_status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

metric_p95()
{
    local operation="$1"
    local dataset="$2"
    awk -v expected_operation="${operation}" -v expected_dataset="${dataset}" '
        index($0, "[redis-benchmark]") {
            operation = ""; dataset = ""; p95 = ""
            for (i = 1; i <= NF; i++) {
                if ($i ~ /^operation=/) {
                    split($i, value, "="); operation = value[2]
                }
                if ($i ~ /^dataset=/) {
                    split($i, value, "="); dataset = value[2]
                }
                if ($i ~ /^p95_ms=/) {
                    split($i, value, "="); p95 = value[2]
                }
            }
            if (operation == expected_operation && dataset == expected_dataset) result = p95
        }
        END { if (result != "") print result }
    ' "${LOG_FILE}"
}

assert_within_baseline()
{
    local label="$1"
    local current="$2"
    local baseline="$3"
    [[ "${current}" =~ ^[0-9]+$ ]] || fail "missing or invalid p95 metric for ${label}"
    [[ "${baseline}" =~ ^[0-9]+$ ]] || fail "invalid baseline for ${label}"
    local limit=$(( (baseline * 120 + 99) / 100 ))
    (( current <= limit )) \
        || fail "${label} p95 regression: current=${current}ms baseline=${baseline}ms limit=${limit}ms"
    pass "${label}: p95=${current}ms baseline=${baseline}ms limit=${limit}ms"
}

command -v redis-cli >/dev/null 2>&1 || fail "redis-cli is required"
command -v mvn >/dev/null 2>&1 || fail "mvn is required"
[[ -n "${HOST}" ]] || fail "ERP_IT_REDIS_HOST is required"
[[ "${PORT}" =~ ^[0-9]+$ ]] && (( PORT >= 1 && PORT <= 65535 )) \
    || fail "ERP_IT_REDIS_PORT must be between 1 and 65535"
[[ "${DATABASE}" =~ ^[0-9]+$ ]] && (( DATABASE >= 0 && DATABASE <= 63 )) \
    || fail "ERP_IT_REDIS_DATABASE must be between 0 and 63"
[[ "${HOST}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "ERP_IT_REDIS_HOST has an invalid format"
[[ "${RUN_ID}" =~ ^[a-z0-9_]+$ ]] \
    || fail "ERP_IT_RUN_ID must contain only lowercase letters, digits, and underscores"
(( ${#RUN_ID} <= 20 )) || fail "ERP_IT_RUN_ID must not exceed 20 characters"
if [[ "${DATABASE}" == "0" && "${ERP_IT_REDIS_ALLOW_DATABASE_ZERO:-0}" != "1" ]]; then
    fail "Redis DB 0 is denied by default"
fi
if [[ "${HOST}" != "127.0.0.1" && "${HOST}" != "localhost" && "${HOST}" != "::1" \
      && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote Redis is denied by default; explicitly confirm a non-production target"
fi

PONG="$(redis_cli PING)" || fail "unable to connect to native Redis"
[[ "${PONG}" == "PONG" ]] || fail "native Redis PING failed"
VERSION="$(redis_cli INFO server | tr -d '\r' | awk -F: '/^redis_version:/ { print $2 }')"
ROLE="$(redis_cli INFO replication | tr -d '\r' | awk -F: '/^role:/ { print $2 }')"
[[ "${ROLE}" == "master" ]] || fail "native Redis test target must be writable master, actual=${ROLE}"
[[ "$(count_pattern "login_tokens:${RUN_ID}_*")" == "0" ]] \
    || fail "current run id already has login session keys"
EXISTING_LOGIN_KEYS="$(count_pattern 'login_tokens:*')"
if [[ "${EXISTING_LOGIN_KEYS}" != "0" && "${ERP_IT_REDIS_ALLOW_EXISTING_LOGIN_KEYS:-0}" != "1" ]]; then
    fail "test Redis DB already contains ${EXISTING_LOGIN_KEYS} login_tokens keys"
fi

KEYS_BEFORE="$(command_calls keys)"
SCAN_BEFORE="$(command_calls scan)"
GET_BEFORE="$(command_calls get)"
SLOWLOG_ID_BEFORE="$(redis_cli --raw SLOWLOG GET 1 | head -n 1 || true)"
BLOCKED_BEFORE="$(redis_cli INFO clients | tr -d '\r' | awk -F: '/^blocked_clients:/ { print $2 }')"
printf '[native-redis-it] host=%s:%s version=%s role=%s db=%s run_id=%s existing_login_keys=%s blocked_clients=%s\n' \
    "${HOST}" "${PORT}" "${VERSION}" "${ROLE}" "${DATABASE}" "${RUN_ID}" \
    "${EXISTING_LOGIN_KEYS}" "${BLOCKED_BEFORE}"

mkdir -p "${REPORT_DIR}"
EXPECTED_CLASSES=(TokenServiceRedisIT SysUserOnlineControllerRedisIT)
for class_name in "${EXPECTED_CLASSES[@]}"; do
    rm -f "${REPORT_DIR}/TEST-com.erp.system.redis.${class_name}.xml"
done

(
    cd "${ROOT_DIR}"
    mvn -pl erp-modules/erp-system -am -Pnative-redis-it verify
) | tee "${LOG_FILE}"

TOTAL_TESTS=0
for class_name in "${EXPECTED_CLASSES[@]}"; do
    report="${REPORT_DIR}/TEST-com.erp.system.redis.${class_name}.xml"
    [[ -s "${report}" ]] || fail "missing failsafe report for ${class_name}"
    suite_line="$(grep -m 1 '<testsuite ' "${report}" || true)"
    [[ "${suite_line}" =~ tests=\"([1-9][0-9]*)\" ]] \
        || fail "${class_name} did not execute any tests"
    tests="${BASH_REMATCH[1]}"
    [[ "${suite_line}" == *'errors="0"'* \
        && "${suite_line}" == *'failures="0"'* \
        && "${suite_line}" == *'skipped="0"'* ]] \
        || fail "${class_name} contains failed, errored, or skipped tests"
    TOTAL_TESTS=$((TOTAL_TESTS + tests))
    pass "${class_name}: ${tests} tests, 0 failed, 0 skipped"
done

KEYS_AFTER="$(command_calls keys)"
SCAN_AFTER="$(command_calls scan)"
GET_AFTER="$(command_calls get)"
SLOWLOG_ID_AFTER="$(redis_cli --raw SLOWLOG GET 1 | head -n 1 || true)"
BLOCKED_AFTER="$(redis_cli INFO clients | tr -d '\r' | awk -F: '/^blocked_clients:/ { print $2 }')"
assert_within_baseline "revoke-100" "$(metric_p95 revoke 100)" "${BASELINE_REVOKE_100}"
assert_within_baseline "revoke-1000" "$(metric_p95 revoke 1000)" "${BASELINE_REVOKE_1000}"
assert_within_baseline "revoke-10000" "$(metric_p95 revoke 10000)" "${BASELINE_REVOKE_10000}"
assert_within_baseline "online-list-10000" "$(metric_p95 online-list 10000)" \
    "${BASELINE_ONLINE_10000}"

assert_equals_keys="${KEYS_BEFORE}:${KEYS_AFTER}"
[[ "${KEYS_AFTER}" == "${KEYS_BEFORE}" ]] \
    || fail "Redis KEYS command was executed: calls before/after=${assert_equals_keys}"
(( SCAN_AFTER > SCAN_BEFORE )) || fail "Redis SCAN command count did not increase"
(( GET_AFTER > GET_BEFORE )) || fail "Redis GET command count did not increase"
[[ "${SLOWLOG_ID_AFTER}" == "${SLOWLOG_ID_BEFORE}" ]] \
    || fail "integration run added a Redis slowlog entry"
[[ "${BLOCKED_AFTER}" == "0" ]] || fail "Redis has blocked clients after integration run"
[[ "$(count_pattern "login_tokens:${RUN_ID}_*")" == "0" ]] \
    || fail "integration tests left login session keys behind"
[[ "$(count_pattern "erp_it_redis:${RUN_ID}_*")" == "0" ]] \
    || fail "integration tests left business probe keys behind"

pass "native Redis gate completed: version=${VERSION}, tests=${TOTAL_TESTS}, "\
"keys_calls=${KEYS_BEFORE}:${KEYS_AFTER}, scan_delta=$((SCAN_AFTER - SCAN_BEFORE)), "\
"get_delta=$((GET_AFTER - GET_BEFORE)), blocked_clients=${BLOCKED_AFTER}"
