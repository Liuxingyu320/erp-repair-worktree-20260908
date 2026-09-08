#!/usr/bin/env bash
set -euo pipefail
umask 077

DB="bosserp_stock_state_75c59ee"
BACKUP_DIR="/opt/erp-new-data-backups/account-activation-20260717T221343Z"
PASS_FILE="/root/.erp-mysql-root-pass"
MYSQL_CNF="$(mktemp /tmp/account-activation-verify.XXXXXX.cnf)"

cleanup() {
  if command -v shred >/dev/null 2>&1; then
    shred -u "$MYSQL_CNF" 2>/dev/null || rm -f "$MYSQL_CNF"
  else
    rm -f "$MYSQL_CNF"
  fi
}
trap cleanup EXIT

python3 - "$PASS_FILE" "$MYSQL_CNF" <<'PY'
import os, sys
from pathlib import Path
password = Path(sys.argv[1]).read_text(encoding="utf-8").rstrip("\r\n")
if not password or "\n" in password or "\r" in password:
    raise SystemExit("invalid mysql password file")
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
Path(sys.argv[2]).write_text('[client]\nuser=root\npassword="' + escaped + '"\n', encoding="utf-8")
os.chmod(sys.argv[2], 0o600)
PY

[[ -d "$BACKUP_DIR" ]]
[[ "$(stat -c '%a' "$BACKUP_DIR")" == "700" ]]
for file in sys_user-before.sql.gz sys_user-before.sql.gz.sha256 manifest.txt; do
  [[ -f "$BACKUP_DIR/$file" ]]
  [[ "$(stat -c '%a' "$BACKUP_DIR/$file")" == "600" ]]
done
(cd "$BACKUP_DIR" && sha256sum -c sys_user-before.sql.gz.sha256)
gzip -t "$BACKUP_DIR/sys_user-before.sql.gz"
grep -q '^prestate=6/6/6/6/6$' "$BACKUP_DIR/manifest.txt"
grep -q '^poststate=6/6/6/6/6$' "$BACKUP_DIR/manifest.txt"
grep -q '^mutation_path=system-user-api$' "$BACKUP_DIR/manifest.txt"

mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --raw --skip-column-names "$DB" <<'SQL'
SELECT CONCAT('aggregate=',CONCAT_WS('/',COUNT(*),
  SUM(status='0' AND del_flag='0'),SUM(credential_state='TEMPORARY'),
  SUM(temporary_password_expires_at > NOW()),
  SUM(temporary_password_expires_at <= DATE_ADD(NOW(), INTERVAL 25 HOUR)),
  COUNT(DISTINCT password)))
FROM sys_user WHERE user_id IN (1095,1096,1097,1098,1099,1100);

SELECT CONCAT_WS('|','ACCOUNT',u.user_id,u.user_name,u.nick_name,u.status,u.del_flag,
  u.credential_state,
  CASE WHEN u.temporary_password_expires_at > NOW() THEN 'TEMP_VALID' ELSE 'TEMP_INVALID' END,
  CASE WHEN p.current_address IS NULL OR TRIM(p.current_address)='' THEN 'ADDRESS_MISSING' ELSE 'ADDRESS_READY' END)
FROM sys_user u JOIN sys_user_profile p ON p.user_id=u.user_id
WHERE u.user_id IN (1095,1096,1097,1098,1099,1100)
ORDER BY u.user_id;

SELECT CONCAT('recent_account_audit=',COUNT(*))
FROM sys_oper_log
WHERE oper_name='admin'
  AND title='用户管理'
  AND oper_time >= DATE_SUB(NOW(), INTERVAL 30 MINUTE)
  AND status='0';
SQL

if find /opt/erp-new-data-imports -maxdepth 1 -type d \
  -name 'account-activation-20260717T221343Z' -print -quit | grep -q .; then
  echo "SENSITIVE_WORK_DIR_RESIDUE" >&2
  exit 40
fi

echo "ACCOUNT_ACTIVATION_VERIFY_OK backup=$BACKUP_DIR"
