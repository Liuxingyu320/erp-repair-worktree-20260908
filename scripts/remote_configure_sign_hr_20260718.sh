#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

DB="bosserp_stock_state_75c59ee"
ENV_FILE="/opt/erp-new/.env"
PASS_FILE="/root/.erp-mysql-root-pass"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORK_DIR="/opt/erp-new-data-imports/sign-hr-config-${STAMP}"
BACKUP_DIR="/opt/erp-new-data-backups/sign-hr-config-${STAMP}"

case "$WORK_DIR" in /opt/erp-new-data-imports/sign-hr-config-*) ;; *) exit 2;; esac
case "$BACKUP_DIR" in /opt/erp-new-data-backups/sign-hr-config-*) ;; *) exit 2;; esac
[[ ! -e "$WORK_DIR" && ! -e "$BACKUP_DIR" ]]
mkdir -m 700 "$WORK_DIR" "$BACKUP_DIR"

cleanup() {
  for file in mysql.cnf jwt-candidates.tsv probe.json response.json; do
    target="$WORK_DIR/$file"
    if [[ -f "$target" ]]; then
      if command -v shred >/dev/null 2>&1; then
        shred -u "$target" 2>/dev/null || rm -f "$target"
      else
        rm -f "$target"
      fi
    fi
  done
  rmdir "$WORK_DIR" 2>/dev/null || true
}
trap cleanup EXIT

for command in python3 mysql mysqldump gzip sha256sum redis-cli curl flock; do
  command -v "$command" >/dev/null 2>&1 || { echo "MISSING_COMMAND_${command}" >&2; exit 3; }
done
[[ -r "$ENV_FILE" && -r "$PASS_FILE" ]]

exec 9>/var/lock/erp-sign-hr-config-20260718.lock
flock -n 9 || { echo "SIGN_HR_CONFIG_ALREADY_RUNNING" >&2; exit 5; }

while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "$line" || "$line" == \#* ]] && continue
  key="${line%%=*}"; value="${line#*=}"
  [[ "$line" == *=* && "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || {
    echo "INVALID_ENV_ASSIGNMENT" >&2; exit 4;
  }
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"; last="${value: -1}"
    if [[ ( "$first" == '"' && "$last" == '"' ) || ( "$first" == "'" && "$last" == "'" ) ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  export "$key=$value"
done < "$ENV_FILE"
: "${ERP_JWT_SECRET:?ERP_JWT_SECRET is required}"

if [[ "$(redis-cli --raw PING 2>/dev/null || true)" == "PONG" ]]; then
  redis=(redis-cli --raw)
else
  : "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
  redis=(redis-cli --no-auth-warning -a "$REDIS_PASSWORD" --raw)
fi
[[ "$("${redis[@]}" PING)" == "PONG" ]]

python3 - "$PASS_FILE" "$WORK_DIR/mysql.cnf" <<'PY'
import os, sys
from pathlib import Path
password = Path(sys.argv[1]).read_text(encoding="utf-8").rstrip("\r\n")
if not password or "\n" in password or "\r" in password:
    raise SystemExit("INVALID_MYSQL_PASSWORD_FILE")
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
Path(sys.argv[2]).write_text('[client]\nuser=root\npassword="' + escaped + '"\n', encoding="utf-8")
os.chmod(sys.argv[2], 0o600)
PY

mysql_cmd=(mysql --defaults-extra-file="$WORK_DIR/mysql.cnf" --protocol=socket --batch --raw --skip-column-names "$DB")
dump_cmd=(mysqldump --defaults-extra-file="$WORK_DIR/mysql.cnf" --protocol=socket \
  --single-transaction --quick --hex-blob --set-gtid-purged=OFF --column-statistics=0 \
  --no-create-info --complete-insert --skip-add-locks --skip-disable-keys)

prestate="$("${mysql_cmd[@]}" <<'SQL'
SELECT CONCAT_WS('/',
  (SELECT COUNT(*) FROM sys_user WHERE user_id=940 AND nick_name='段继康' AND status='0' AND del_flag='0'),
  (SELECT COUNT(*) FROM sys_config WHERE config_key='sign.hr.user-id'),
  (SELECT COUNT(*) FROM sys_role WHERE role_key='sign_single_hr' AND del_flag='0'),
  (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id
    WHERE r.role_key='sign_single_hr'));
SQL
)"
[[ "$prestate" =~ ^1/0/[01]/[01]$ ]] || {
  echo "SIGN_HR_PRESTATE_REFUSED expected=1/0/[0-1]/[0-1] actual=$prestate" >&2; exit 10;
}

for table in sys_config sys_sign_hr_state sys_role sys_user_role sys_role_menu sys_sign_hr_menu_grant; do
  "${dump_cmd[@]}" "$DB" "$table" | gzip -9 > "$BACKUP_DIR/${table}-before.sql.gz"
  chmod 600 "$BACKUP_DIR/${table}-before.sql.gz"
  gzip -t "$BACKUP_DIR/${table}-before.sql.gz"
  sha256sum "$BACKUP_DIR/${table}-before.sql.gz" > "$BACKUP_DIR/${table}-before.sql.gz.sha256"
  chmod 600 "$BACKUP_DIR/${table}-before.sql.gz.sha256"
done

admin_token_id=""
while IFS= read -r candidate; do
  candidate="${candidate#\"}"; candidate="${candidate%\"}"
  if [[ -n "$candidate" && "$("${redis[@]}" EXISTS "login_tokens:${candidate}")" == "1" ]]; then
    admin_token_id="$candidate"; break
  fi
done < <("${redis[@]}" SMEMBERS user_login_tokens:1)
[[ -n "$admin_token_id" ]] || { echo "LIVE_ADMIN_SESSION_REQUIRED" >&2; exit 11; }

python3 - "$admin_token_id" "$WORK_DIR/jwt-candidates.tsv" <<'PY'
import base64, hashlib, hmac, json, os, sys
token_id, output = sys.argv[1:]
secret = os.environ["ERP_JWT_SECRET"]
def b64url(value): return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")
header = b64url(json.dumps({"alg":"HS512"}, separators=(",", ":")).encode())
payload = b64url(json.dumps({"user_key":token_id,"user_id":1,"username":"admin"},
                            separators=(",", ":")).encode())
signing_input = (header + "." + payload).encode("ascii")
keys = [("utf8", secret.encode("utf-8"))]
try:
    decoded = base64.b64decode(secret + "=" * (-len(secret) % 4), validate=True)
    if decoded and decoded != keys[0][1]: keys.append(("base64", decoded))
except Exception: pass
with open(output, "w", encoding="ascii") as handle:
    for label, key in keys:
        signature = b64url(hmac.new(key, signing_input, hashlib.sha512).digest())
        handle.write(label + "\t" + header + "." + payload + "." + signature + "\n")
os.chmod(output, 0o600)
PY

admin_jwt=""
while IFS=$'\t' read -r jwt_kind jwt_value; do
  http_code="$(curl --silent --show-error --output "$WORK_DIR/probe.json" \
    --write-out '%{http_code}' --max-time 15 \
    -H "Authorization: Bearer $jwt_value" \
    'http://127.0.0.1:8080/system/config/list?pageNum=1&pageSize=1')"
  if [[ "$http_code" =~ ^2[0-9][0-9]$ ]] && python3 - "$WORK_DIR/probe.json" <<'PY'
import json, sys
try: payload = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception: raise SystemExit(1)
raise SystemExit(0 if payload.get("code") == 200 else 1)
PY
  then
    admin_jwt="$jwt_value"; break
  fi
done < "$WORK_DIR/jwt-candidates.tsv"
[[ -n "$admin_jwt" ]] || { echo "ADMIN_JWT_VALIDATION_FAILED" >&2; exit 12; }

http_code="$(curl --silent --show-error --output "$WORK_DIR/response.json" \
  --write-out '%{http_code}' --max-time 60 --request POST \
  -H "Authorization: Bearer $admin_jwt" -H 'Content-Type: application/json' \
  --data '{"configName":"签约唯一HR","configKey":"sign.hr.user-id","configValue":"940","configType":"Y","remark":"段继康（E00143），2026-07-18启用"}' \
  'http://127.0.0.1:8080/system/config')"
[[ "$http_code" =~ ^2[0-9][0-9]$ ]] || { echo "CONFIG_HTTP_FAILED" >&2; exit 20; }
python3 - "$WORK_DIR/response.json" <<'PY'
import json, sys
try: payload = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception: raise SystemExit("CONFIG_RESPONSE_INVALID")
if payload.get("code") != 200: raise SystemExit("CONFIG_RESPONSE_REJECTED")
PY

poststate="$("${mysql_cmd[@]}" <<'SQL'
SELECT CONCAT_WS('/',
  (SELECT COUNT(*) FROM sys_config WHERE config_key='sign.hr.user-id' AND config_value='940' AND config_type='Y'),
  (SELECT COUNT(*) FROM sys_sign_hr_state WHERE state_id=1 AND hr_user_id=940 AND managed_role_id IS NOT NULL),
  (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id
    WHERE ur.user_id=940 AND r.role_key='sign_single_hr' AND r.status='0' AND r.del_flag='0'),
  (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id
    WHERE ur.user_id<>940 AND r.role_key='sign_single_hr'),
  (SELECT COUNT(DISTINCT m.perms) FROM sys_user_role ur
    JOIN sys_role r ON r.role_id=ur.role_id AND r.role_key='sign_single_hr'
    JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id
    WHERE ur.user_id=940 AND m.perms IN ('oa:signPackage:list','oa:signPackage:query',
      'oa:signPackage:add','oa:signPackage:send','oa:signPackage:void','oa:signPackage:template')));
SQL
)"
[[ "$poststate" == "1/1/1/0/6" ]] || {
  echo "SIGN_HR_POSTSTATE_MISMATCH expected=1/1/1/0/6 actual=$poststate" >&2; exit 30;
}

cat > "$BACKUP_DIR/manifest.txt" <<EOF
database=$DB
stamp=$STAMP
candidate_user_id=940
candidate_name=段继康
prestate=$prestate
poststate=$poststate
mutation_path=system-config-api
restore_note=data-only snapshots; restore through an isolated staging database, never directly
EOF
chmod 600 "$BACKUP_DIR/manifest.txt"

echo "SIGN_HR_CONFIG_OK user_id=940 name=段继康 poststate=$poststate"
echo "BACKUP_OK directory=$BACKUP_DIR"
