#!/usr/bin/env bash

# Runs the release-rehearsal four-role API acceptance matrix without printing
# passwords, bearer tokens, or revealed sensitive values. It is read-only apart
# from the expected sensitive-access audit row and login/logout records.

set -Eeuo pipefail

BASE_URL="${ERP_E2E_BASE_URL:-http://127.0.0.1:8080}"
MYSQL_HOST="${ERP_E2E_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${ERP_E2E_MYSQL_PORT:-3306}"
MYSQL_USER="${ERP_E2E_MYSQL_USER:-}"
MYSQL_PASSWORD="${ERP_E2E_MYSQL_PASSWORD:-}"
DATABASE="${ERP_E2E_MYSQL_DATABASE:-}"
PREFIX="${ERP_E2E_PREFIX:-}"
E2E_PASSWORD="${ERP_E2E_PASSWORD:-}"
ALLOW_REMOTE="${ERP_IT_ALLOW_REMOTE_NONPROD:-0}"

PASS_COUNT=0
REQUEST_SEQUENCE=0
RESPONSE_BODY=""
RESPONSE_HTTP=""
SUPER_TOKEN=""
SYSADMIN_TOKEN=""
HR_TOKEN=""
SHOP_TOKEN=""

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    PASS_COUNT=$((PASS_COUNT + 1))
    printf '[PASS] %s\n' "$*"
}

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v jq >/dev/null 2>&1 || fail "jq is required"
command -v mysql >/dev/null 2>&1 || fail "mysql CLI is required"

BASE_URL="${BASE_URL%/}"
[[ "${MYSQL_PORT}" =~ ^[0-9]+$ ]] && (( MYSQL_PORT >= 1 && MYSQL_PORT <= 65535 )) \
    || fail "ERP_E2E_MYSQL_PORT must be between 1 and 65535"
[[ "${MYSQL_HOST}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "MySQL host has an invalid format"
[[ "${DATABASE}" =~ ^erp_system_release_rehearsal_[a-z0-9_]+$ ]] \
    || fail "ERP_E2E_MYSQL_DATABASE must use the release-rehearsal prefix"
[[ "${PREFIX}" =~ ^[a-z][a-z0-9_]{2,11}$ ]] \
    || fail "ERP_E2E_PREFIX must be 3-12 lowercase letters, digits, or underscores"
[[ -n "${MYSQL_USER}" ]] || fail "ERP_E2E_MYSQL_USER is required"
[[ -n "${E2E_PASSWORD}" ]] || fail "ERP_E2E_PASSWORD is required"

if [[ ! "${BASE_URL}" =~ ^https?://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]+)?$ \
      && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote application target is denied by default; explicitly confirm a non-production target"
fi
if [[ "${MYSQL_HOST}" != "127.0.0.1" && "${MYSQL_HOST}" != "localhost" \
      && "${MYSQL_HOST}" != "::1" && "${ALLOW_REMOTE}" != "1" ]]; then
    fail "remote MySQL is denied by default; explicitly confirm a non-production target"
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/erp-system-e2e-api.XXXXXX")"
chmod 700 "${TMP_DIR}"

cleanup()
{
    local token
    for token in "${SUPER_TOKEN}" "${SYSADMIN_TOKEN}" "${HR_TOKEN}" "${SHOP_TOKEN}"; do
        if [[ -n "${token}" ]]; then
            curl -sS --max-time 5 -o /dev/null -X DELETE \
                -H "Authorization: Bearer ${token}" "${BASE_URL}/auth/logout" || true
        fi
    done
    rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

db_query()
{
    local statement="$1"
    MYSQL_PWD="${MYSQL_PASSWORD}" mysql --protocol=TCP --connect-timeout=8 \
        -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" \
        --batch --skip-column-names --raw "${DATABASE}" -e "${statement}" | tr -d '\r'
}

api_request()
{
    local token="$1"
    local method="$2"
    local endpoint="$3"
    local payload_file="${4:-}"
    local response_file
    local -a curl_args

    REQUEST_SEQUENCE=$((REQUEST_SEQUENCE + 1))
    response_file="${TMP_DIR}/response-${REQUEST_SEQUENCE}.json"
    curl_args=(-sS --max-time 30 -X "${method}" -H 'Accept: application/json')
    if [[ -n "${token}" ]]; then
        curl_args+=(-H "Authorization: Bearer ${token}")
    fi
    if [[ -n "${payload_file}" ]]; then
        curl_args+=(-H 'Content-Type: application/json' --data-binary "@${payload_file}")
    fi

    RESPONSE_HTTP="$(curl "${curl_args[@]}" -o "${response_file}" -w '%{http_code}' \
        "${BASE_URL}${endpoint}")"
    RESPONSE_BODY="$(<"${response_file}")"
    jq -e . >/dev/null 2>&1 <<<"${RESPONSE_BODY}" \
        || fail "${method} ${endpoint} did not return a JSON response (HTTP ${RESPONSE_HTTP})"
}

expect_code()
{
    local expected="$1"
    local label="$2"
    local actual
    actual="$(jq -r '.code // empty' <<<"${RESPONSE_BODY}")"
    [[ "${RESPONSE_HTTP}" == "200" ]] \
        || fail "${label}: expected HTTP 200 envelope, got ${RESPONSE_HTTP}"
    [[ "${actual}" == "${expected}" ]] \
        || fail "${label}: expected application code ${expected}, got ${actual:-missing}"
    pass "${label} http=${RESPONSE_HTTP} app=${actual}"
}

expect_jq()
{
    local expression="$1"
    local label="$2"
    shift 2
    jq -e "$@" "${expression}" >/dev/null 2>&1 <<<"${RESPONSE_BODY}" \
        || fail "${label}: response assertion failed"
    pass "${label}"
}

login_as()
{
    local username="$1"
    local target_variable="$2"
    local payload_file="${TMP_DIR}/login-${target_variable}.json"
    local token

    LOGIN_USERNAME="${username}" LOGIN_PASSWORD="${E2E_PASSWORD}" \
        jq -nc '{username:env.LOGIN_USERNAME,password:env.LOGIN_PASSWORD}' > "${payload_file}"
    chmod 600 "${payload_file}"
    api_request "" POST "/auth/login" "${payload_file}"
    expect_code 200 "${target_variable%_TOKEN} login"
    token="$(jq -r '.data.access_token // empty' <<<"${RESPONSE_BODY}")"
    [[ -n "${token}" ]] || fail "${target_variable%_TOKEN} login returned no access token"
    printf -v "${target_variable}" '%s' "${token}"
    rm -f "${payload_file}"
}

[[ "$(db_query "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${DATABASE}'")" == "1" ]] \
    || fail "release-rehearsal database does not exist"
[[ "$(db_query "SELECT COUNT(*) FROM sys_user WHERE user_name IN \
    ('${PREFIX}_super','${PREFIX}_sysadmin','${PREFIX}_hr','${PREFIX}_shop')")" == "4" ]] \
    || fail "four-role E2E fixture is incomplete"
[[ "$(db_query "SELECT COUNT(*) FROM sys_user_profile p JOIN sys_user u ON u.user_id=p.user_id \
    WHERE u.user_name IN ('${PREFIX}_probe_same','${PREFIX}_probe_other')")" == "2" ]] \
    || fail "sensitive-field E2E probes are incomplete"

SAME_ID="$(db_query "SELECT user_id FROM sys_user WHERE user_name='${PREFIX}_probe_same' LIMIT 1")"
OTHER_ID="$(db_query "SELECT user_id FROM sys_user WHERE user_name='${PREFIX}_probe_other' LIMIT 1")"
OPER_ID="$(db_query "SELECT oper_id FROM sys_oper_log ORDER BY oper_id LIMIT 1")"
read -r REVISION_ID SALARY_VERSION <<<"$(db_query "SELECT r.revision_id,s.version \
    FROM sys_salary_scheme_revision r JOIN sys_salary_scheme s ON s.scheme_id=r.scheme_id \
    ORDER BY r.revision_id LIMIT 1")"
[[ "${SAME_ID}" =~ ^[0-9]+$ && "${OTHER_ID}" =~ ^[0-9]+$ ]] || fail "probe IDs are invalid"
[[ "${OPER_ID}" =~ ^[0-9]+$ ]] || fail "an operation-log row is required for detail authorization acceptance"
[[ "${REVISION_ID}" =~ ^[0-9]+$ && "${SALARY_VERSION}" =~ ^[0-9]+$ ]] \
    || fail "a salary revision is required for emergency-permission acceptance"

api_request "" GET "/actuator/health"
[[ "${RESPONSE_HTTP}" == "200" ]] || fail "gateway health check failed with HTTP ${RESPONSE_HTTP}"
pass "gateway health http=${RESPONSE_HTTP}"

login_as "${PREFIX}_super" SUPER_TOKEN
login_as "${PREFIX}_sysadmin" SYSADMIN_TOKEN
login_as "${PREFIX}_hr" HR_TOKEN
login_as "${PREFIX}_shop" SHOP_TOKEN

api_request "${SUPER_TOKEN}" GET "/system/user/getInfo"
expect_code 200 "super getInfo"
expect_jq '.permissions | index("*:*:*") != null' "super wildcard permission"

api_request "${SYSADMIN_TOKEN}" GET "/system/user/getInfo"
expect_code 200 "sysadmin getInfo"
expect_jq '(.roles == [$role]) and (.mustChangePassword == false) and
    (.permissions | index("system:user:list") != null) and
    (.permissions | index("system:config:refresh") != null) and
    ([.permissions[] | select(startswith("hr:"))] | length == 0) and
    ([.permissions[] | select(. == "system:salary:emergency" or
        . == "system:logininfor:unlock" or . == "system:operlog:detail" or
        . == "system:operlog:remove")] | length == 0)' \
    "sysadmin dedicated permissions and high-risk exclusions" --arg role "${PREFIX}_sysadmin"

api_request "${HR_TOKEN}" GET "/system/user/getInfo"
expect_code 200 "HR getInfo"
expect_jq '(.roles == [$role]) and (.mustChangePassword == false) and
    (.permissions | index("hr:employee:renewal") != null) and
    (.permissions | index("hr:employee:regularize") != null) and
    (.permissions | index("hr:employee:sensitive:view") != null) and
    ([.permissions[] | select(startswith("system:"))] | length == 0)' \
    "HR lifecycle and sensitive permissions without system rights" --arg role "${PREFIX}_hr"

api_request "${SHOP_TOKEN}" GET "/system/user/getInfo"
expect_code 200 "shop getInfo allowed during forced password change"
expect_jq '(.roles == [$role]) and (.mustChangePassword == true) and
    ((.permissions | sort) == ["system:userShop:edit","system:userShop:list","system:userShop:query"])' \
    "shop minimal permissions and forced-change state" --arg role "${PREFIX}_shop"

api_request "${SUPER_TOKEN}" GET "/system/menu/getRouters"
expect_code 200 "super routers"
expect_jq '([.data[]?.path] | index("/system") != null) and
    ([.data[]?.path] | index("/hr") != null)' "super system and HR navigation"

api_request "${SYSADMIN_TOKEN}" GET "/system/menu/getRouters"
expect_code 200 "sysadmin routers"
expect_jq '([.data[]?.path] | index("/system") != null) and
    ([.data[]?.path] | index("/hr") == null)' "sysadmin navigation excludes HR"

api_request "${HR_TOKEN}" GET "/system/menu/getRouters"
expect_code 200 "HR routers"
expect_jq '([.data[]?.path] | index("/hr") != null) and
    ([.data[]?.path] | index("/system") == null)' "HR navigation excludes system management"

api_request "${SHOP_TOKEN}" GET "/system/menu/getRouters"
expect_code 428 "shop routers blocked until password change"
api_request "${SHOP_TOKEN}" GET "/system/user/shop/tree"
expect_code 428 "shop direct API blocked until password change"

api_request "${SUPER_TOKEN}" GET "/system/user/${OTHER_ID}"
expect_code 200 "super cross-department user access"
api_request "${SYSADMIN_TOKEN}" GET "/system/user/${SAME_ID}"
expect_code 200 "sysadmin same-department user access"
api_request "${SYSADMIN_TOKEN}" GET "/system/user/${OTHER_ID}"
expect_code 403 "sysadmin cross-department direct access denied"
api_request "${SYSADMIN_TOKEN}" GET "/system/user/list?pageNum=1&pageSize=20&userName=${PREFIX}_probe"
expect_code 200 "sysadmin scoped user list"
expect_jq '(.total == 1) and (.rows | length == 1) and (.rows[0].userName == $name)' \
    "sysadmin list contains only the same-department probe" --arg name "${PREFIX}_probe_same"

api_request "${HR_TOKEN}" GET "/system/user/${SAME_ID}"
expect_code 403 "HR direct system-user API denied"
api_request "${SYSADMIN_TOKEN}" GET "/system/operlog/${OPER_ID}"
expect_code 403 "sysadmin operation-log detail denied"
api_request "${SUPER_TOKEN}" GET "/system/operlog/${OPER_ID}"
expect_code 200 "super operation-log detail allowed"
api_request "${SYSADMIN_TOKEN}" POST "/system/logininfor/${PREFIX}_probe_same/unlock"
expect_code 403 "sysadmin account unlock denied"

SALARY_PAYLOAD="${TMP_DIR}/salary-emergency.json"
jq -nc --argjson version "${SALARY_VERSION}" \
    '{expectedVersion:$version,reason:"E2E permission boundary probe",emergencyCorrection:true}' \
    > "${SALARY_PAYLOAD}"
api_request "${SYSADMIN_TOKEN}" POST "/system/salaryConfig/revision/${REVISION_ID}/rollback" \
    "${SALARY_PAYLOAD}"
expect_code 403 "sysadmin salary emergency rollback denied"

api_request "${HR_TOKEN}" GET "/system/hr/employee/list?keyword=${PREFIX}_probe_"
expect_code 200 "HR scoped employee list"
expect_jq '(.total == 2) and (.rows | length == 2) and
    ([.rows[].phoneNumberMasked | test("^[0-9]{3}\\*+[0-9]{4}$")] | all)' \
    "HR employee list returns deterministic phone masks"
if jq -e '.. | strings | select(. == "13800000001" or . == "13800000002" or
    . == "110101199001010011" or . == "110101199001010029" or
    . == "6222020202020202001" or . == "6222020202020202002")' \
    >/dev/null 2>&1 <<<"${RESPONSE_BODY}"; then
    fail "HR employee list exposed an unmasked probe value"
fi
pass "HR employee list contains no raw probe phone, ID, or bank value"

api_request "${HR_TOKEN}" GET "/system/hr/employee/${SAME_ID}"
expect_code 200 "HR employee detail"
expect_jq '(.data.phoneNumberMasked | contains("*")) and
    (.data.idNumberMasked | contains("*")) and
    (.data.bankAccountMasked | contains("*")) and
    (.data.fields.idNumber | contains("*"))' "HR employee detail is masked by default"
if jq -e '.. | strings | select(. == "13800000001" or . == "110101199001010011" or
    . == "6222020202020202001")' >/dev/null 2>&1 <<<"${RESPONSE_BODY}"; then
    fail "HR employee detail exposed an unmasked probe value"
fi
pass "HR employee detail contains no raw probe phone, ID, or bank value"

AUDIT_BEFORE="$(db_query "SELECT COUNT(*) FROM hr_sensitive_access_log \
    WHERE operator_name='${PREFIX}_hr' AND employee_user_id=${SAME_ID} \
      AND field_key='idNumber' AND action_type='REVEAL' AND result='SUCCESS'")"
REVEAL_PAYLOAD="${TMP_DIR}/sensitive-reveal.json"
printf '%s\n' '{"fieldKey":"idNumber"}' > "${REVEAL_PAYLOAD}"
api_request "${HR_TOKEN}" POST "/system/hr/employee/${SAME_ID}/sensitive/reveal" \
    "${REVEAL_PAYLOAD}"
expect_code 200 "HR explicit sensitive-field reveal"
expect_jq '(.data.fieldKey == "idNumber") and
    (.data.value | type == "string" and test("^[0-9]{18}$"))' \
    "HR reveal returns only the requested field"
AUDIT_AFTER="$(db_query "SELECT COUNT(*) FROM hr_sensitive_access_log \
    WHERE operator_name='${PREFIX}_hr' AND employee_user_id=${SAME_ID} \
      AND field_key='idNumber' AND action_type='REVEAL' AND result='SUCCESS'")"
[[ "${AUDIT_BEFORE}" =~ ^[0-9]+$ && "${AUDIT_AFTER}" =~ ^[0-9]+$ \
    && $((AUDIT_BEFORE + 1)) -eq AUDIT_AFTER ]] \
    || fail "sensitive reveal did not add exactly one metadata-only audit row"
[[ "$(db_query "SELECT COUNT(*) FROM hr_sensitive_access_log \
    WHERE operator_name='${PREFIX}_hr' AND employee_user_id=${SAME_ID} \
      AND field_key='idNumber' AND action_type='REVEAL' AND result='SUCCESS' \
      AND (result_message IS NULL OR result_message NOT IN ('EMPTY','REVEALED'))")" == "0" ]] \
    || fail "sensitive reveal audit stored an unexpected result payload"
pass "sensitive reveal audit incremented once and contains metadata only"

printf '[PASS] SYSTEM_MANAGEMENT_E2E_API_OK assertions=%d roles=4 raw_sensitive_values=0\n' \
    "${PASS_COUNT}"
