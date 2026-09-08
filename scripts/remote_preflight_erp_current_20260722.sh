#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

CURRENT_LINK=/opt/erp-new
SHARED_UPLOAD=/opt/erp-new-data/uploadPath
ENV_FILE="$CURRENT_LINK/.env"
SERVICES=(gateway auth monitor system gen job oa inventory file approval)

env_value() {
  local key="$1"
  local value
  value="$(awk -F= -v wanted="$key" '$1 == wanted { print substr($0, index($0, "=") + 1); exit }' "$ENV_FILE")"
  if [[ ${#value} -ge 2 ]]; then
    if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]] ||
       [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  printf '%s' "$value"
}

service_port() {
  case "$1" in
    gateway) echo 8080 ;;
    auth) echo 9200 ;;
    monitor) echo 9100 ;;
    system) echo 9201 ;;
    gen) echo 9202 ;;
    job) echo 9203 ;;
    oa) echo 9204 ;;
    inventory) echo 9205 ;;
    approval) echo 9206 ;;
    file) echo 9300 ;;
    *) return 1 ;;
  esac
}

echo "PREFLIGHT_BEGIN host=$(hostname) time=$(date '+%F %T %z')"
test -L "$CURRENT_LINK"
test -r "$ENV_FILE"
test -d "$SHARED_UPLOAD"
echo "current_release=$(readlink -f "$CURRENT_LINK")"
echo "shared_upload=$(readlink -f "$CURRENT_LINK/erp/uploadPath")"
test "$(readlink -f "$CURRENT_LINK/erp/uploadPath")" = "$SHARED_UPLOAD"
df -Pk / /opt "$SHARED_UPLOAD" | awk 'NR == 1 || !seen[$6]++'

for service in "${SERVICES[@]}"; do
  port="$(service_port "$service")"
  state="$(systemctl is-active "erp-new@$service.service" 2>/dev/null || true)"
  health="DOWN"
  if [[ "$state" == active ]] &&
     curl -fsS --max-time 5 "http://127.0.0.1:$port/actuator/health" |
       grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    health=UP
  fi
  echo "service=$service state=$state health=$health port=$port"
  [[ "$state" == active && "$health" == UP ]]
done

for key in \
  MYSQL_DATABASE MYSQL_USERNAME REDIS_PASSWORD ERP_JWT_SECRET \
  OA_SIGN_EXCEL_IMPORT_ENABLED HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED \
  OA_SIGN_EXPIRY_ENABLED OA_SIGN_REMINDER_ENABLED OA_SIGN_EMERGENCY_CREATE_ENABLED \
  PROFILE_COMPLETION_GATE_ENABLED SIGN_PACKAGE_PDF_CONVERTER_COMMAND \
  SIGN_PACKAGE_STORAGE_ROOT SIGN_PACKAGE_TEMP_ROOT; do
  value="$(env_value "$key")"
  case "$key" in
    MYSQL_DATABASE|OA_SIGN_EXCEL_IMPORT_ENABLED|HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED|OA_SIGN_EXPIRY_ENABLED|OA_SIGN_REMINDER_ENABLED|OA_SIGN_EMERGENCY_CREATE_ENABLED|PROFILE_COMPLETION_GATE_ENABLED)
      echo "env|$key=${value:-UNSET}"
      ;;
    *)
      if [[ -n "$value" ]]; then echo "env|$key=SET"; else echo "env|$key=UNSET"; fi
      ;;
  esac
done

for command in libreoffice soffice pdftotext pdfinfo pdftoppm fc-list; do
  if command -v "$command" >/dev/null 2>&1; then
    echo "runtime|$command=SET"
  else
    echo "runtime|$command=UNSET"
  fi
done
if command -v fc-list >/dev/null 2>&1; then
  cjk_font_count="$(fc-list :lang=zh family 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l)"
  echo "runtime|cjk_font_count=$cjk_font_count"
fi

if nginx -T 2>/dev/null | grep -Eq 'listen[[:space:]]+443([^0-9]|$).*ssl|ssl_certificate[[:space:]]'; then
  echo 'nginx|tls=ENABLED'
else
  echo 'nginx|tls=DISABLED'
fi

database="$(env_value MYSQL_DATABASE)"
mysql_user="$(env_value MYSQL_USERNAME)"
mysql_password="$(env_value MYSQL_PASSWORD)"
mysql_host="$(env_value MYSQL_HOST)"
mysql_port="$(env_value MYSQL_PORT)"
[[ "$database" =~ ^[A-Za-z0-9_]+$ ]]
[[ "$mysql_user" =~ ^[A-Za-z0-9_.@-]+$ ]]
mysql_host="${mysql_host:-127.0.0.1}"
mysql_port="${mysql_port:-3306}"
[[ "$mysql_port" =~ ^[0-9]+$ ]]
mysql_args=(-h "$mysql_host" -P "$mysql_port" -u "$mysql_user" -N -B "$database")
export MYSQL_PWD="$mysql_password"

mysql "${mysql_args[@]}" -e 'SELECT CONCAT("database|name=", DATABASE(), " version=", VERSION());'

marker() {
  local sequence="$1"
  local name="$2"
  local sql="$3"
  local count
  count="$(mysql "${mysql_args[@]}" -e "$sql")"
  if [[ "$count" =~ ^[1-9][0-9]*$ ]]; then
    echo "migration|sequence=$sequence name=$name state=PRESENT count=$count"
  else
    echo "migration|sequence=$sequence name=$name state=MISSING count=${count:-0}"
  fi
}

marker 01 menu_permission_repair \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('sys_sign_hr_menu_grant','sys_sign_hr_state');"
marker 02 package_lifecycle \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='oa_sign_package' AND COLUMN_NAME='deadline_policy_source') OR (TABLE_NAME='oa_sign_task' AND COLUMN_NAME='resolution_status'));"
marker 03 document_policy_snapshot \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_package_document' AND COLUMN_NAME='document_policy_mode';"
marker 04 user_push_delivery \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user_push_delivery';"
marker 05 onboard_import \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('oa_sign_onboard_import_batch','oa_sign_onboard_import_row','oa_sign_onboard_data_request');"
marker 06 sign_profile_supplement \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_sign_profile_supplement_audit';"
echo 'migration|sequence=07 name=salary_social_mapping state=DATA_RERUN_REQUIRED_IF_UNRECORDED'
marker 08 candidate_phone_index \
  "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND INDEX_NAME='idx_sys_user_sign_phone';"
marker 09 company_salary_policy \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_onboard_import_row' AND COLUMN_NAME='matched_legal_entity_id';"
marker 10 dual_sequence_evidence \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_package' AND COLUMN_NAME='signing_sequence';"
marker 11 onboard_send_idempotency \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_onboard_send_request';"
marker 12 task_batch_finalize \
  "SELECT COUNT(*) FROM sys_menu WHERE BINARY perms=BINARY 'oa:signTask:batchFinalize' AND status='0';"
marker 13 file_cleanup \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_file_cleanup';"
marker 14 hard_delete_idempotency \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_task_hard_delete_operation';"
marker 15 contract_term_snapshot \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oa_sign_package' AND COLUMN_NAME='contract_term_code_snapshot';"

unset MYSQL_PWD mysql_password
echo 'PREFLIGHT_OK'
