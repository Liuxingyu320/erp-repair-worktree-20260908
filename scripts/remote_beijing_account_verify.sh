#!/usr/bin/env bash
set -euo pipefail
umask 077

DB=bosserp_stock_state_75c59ee
BACKUP_DIR=/opt/erp-new-data-backups/beijing-create-only-20260717T211225Z-eb22c1ee
PASS_FILE=/root/.erp-mysql-root-pass
MYSQL_CNF="$(mktemp /tmp/beijing-verify-mysql.XXXXXX)"
cleanup() {
  if command -v shred >/dev/null 2>&1; then
    shred -u "$MYSQL_CNF" 2>/dev/null || rm -f "$MYSQL_CNF"
  else
    rm -f "$MYSQL_CNF"
  fi
}
trap cleanup EXIT

python3 - "$PASS_FILE" "$MYSQL_CNF" <<'PY'
import os
import sys
from pathlib import Path

password = Path(sys.argv[1]).read_text(encoding="utf-8").rstrip("\r\n")
if not password or "\n" in password or "\r" in password:
    raise SystemExit("invalid mysql password file")
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
Path(sys.argv[2]).write_text('[client]\nuser=root\npassword="' + escaped + '"\n', encoding="utf-8")
os.chmod(sys.argv[2], 0o600)
PY

[[ -d "$BACKUP_DIR" ]]
(cd "$BACKUP_DIR" && sha256sum -c employee-tables-before.sql.gz.sha256)
gzip -t "$BACKUP_DIR/employee-tables-before.sql.gz"
[[ "$(stat -c '%a' "$BACKUP_DIR")" = "700" ]]
for file in employee-tables-before.sql.gz employee-tables-before.sql.gz.sha256 apply-output.log manifest.txt; do
  [[ -f "$BACKUP_DIR/$file" ]]
  [[ "$(stat -c '%a' "$BACKUP_DIR/$file")" = "600" ]]
done
grep -q '^existing_accounts_transactional_hash=unchanged_21$' "$BACKUP_DIR/manifest.txt"
grep -q '^baseline=158/148/153/153/500/148/148$' "$BACKUP_DIR/manifest.txt"
grep -q '^post=164/154/159/159/508/154/154$' "$BACKUP_DIR/manifest.txt"
grep -q '^aggregate=6/6/6/6/8/6$' "$BACKUP_DIR/manifest.txt"

mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --raw --skip-column-names "$DB" <<'SQL'
SELECT CONCAT('counts=',CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user),(SELECT COUNT(*) FROM sys_user_profile),
 (SELECT COUNT(*) FROM sys_user_post),(SELECT COUNT(*) FROM sys_user_role),
 (SELECT COUNT(*) FROM sys_user_shop),(SELECT COUNT(*) FROM hr_employee_position_no_history),
 (SELECT current_value FROM hr_employee_no_sequence WHERE sequence_key='GLOBAL')));

SELECT CONCAT('security=',CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user WHERE create_by='beijing0716_placeholder' AND status='1' AND del_flag='0'),
 (SELECT COUNT(*) FROM sys_user WHERE create_by='beijing0716_placeholder' AND credential_state='CHANGE_REQUIRED'
   AND temporary_password_expires_at IS NULL AND pwd_update_date IS NULL),
 (SELECT COUNT(*) FROM sys_user WHERE create_by='beijing0716_placeholder'
   AND password REGEXP '^\\$2a\\$12\\$[./A-Za-z0-9]{53}$'),
 (SELECT COUNT(DISTINCT password) FROM sys_user WHERE create_by='beijing0716_placeholder')));

SELECT CONCAT('profile=',CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user_profile WHERE create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_profile WHERE create_by='beijing0716_placeholder' AND id_number IS NOT NULL),
 (SELECT COUNT(*) FROM sys_user_profile WHERE create_by='beijing0716_placeholder'
   AND entry_date IS NOT NULL AND contract_start_date IS NOT NULL AND contract_end_date IS NOT NULL
   AND contract_type IS NOT NULL AND contract_term IS NOT NULL),
 (SELECT COUNT(*) FROM sys_user_profile WHERE create_by='beijing0716_placeholder'
   AND current_address IS NULL),
 (SELECT COUNT(*) FROM sys_user_profile WHERE create_by='beijing0716_placeholder'
   AND legal_entity='舟山茗汇文化传播有限公司' AND legal_entity_id IS NULL
   AND legal_entity_code IS NULL)));

SELECT CONCAT('relations=',CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user_post up JOIN sys_user u ON u.user_id=up.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.user_id=ur.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_shop us JOIN sys_user u ON u.user_id=us.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_shop us JOIN sys_user u ON u.user_id=us.user_id
   WHERE u.create_by='beijing0716_placeholder' AND us.is_default='Y'),
 (SELECT COUNT(*) FROM hr_employee_position_no_history h JOIN sys_user u ON u.user_id=h.user_id
   WHERE u.create_by='beijing0716_placeholder' AND h.change_source='PROFILE_INSERT')));

SELECT CONCAT('categories=',employee_category,':',COUNT(*))
FROM sys_user_profile WHERE create_by='beijing0716_placeholder'
GROUP BY employee_category ORDER BY employee_category;

SELECT CONCAT_WS('|',u.nick_name,u.user_name,d.dept_name,sp.post_name,r.role_name,
 GROUP_CONCAT(CONCAT(sd.dept_name,':',us.is_default) ORDER BY us.is_default DESC,sd.dept_name SEPARATOR ','))
FROM sys_user u
JOIN sys_dept d ON d.dept_id=u.dept_id
JOIN sys_user_post up ON up.user_id=u.user_id
JOIN sys_post sp ON sp.post_id=up.post_id
JOIN sys_user_role ur ON ur.user_id=u.user_id
JOIN sys_role r ON r.role_id=ur.role_id
JOIN sys_user_shop us ON us.user_id=u.user_id
JOIN sys_dept sd ON sd.dept_id=us.dept_id
WHERE u.create_by='beijing0716_placeholder'
GROUP BY u.user_id,u.nick_name,u.user_name,d.dept_name,sp.post_name,r.role_name
ORDER BY u.user_name;
SQL

if find /opt/erp-new-data-imports -maxdepth 1 -type d -name 'beijing-create-only-transfer-*' -print -quit | grep -q .; then
  echo "TRANSFER_RESIDUE_FOUND" >&2
  exit 40
fi
if find /opt/erp-new-data-imports -maxdepth 2 -type f \
  \( -name 'apply.sql' -o -name 'private.pem' -o -name 'key-material.bin' -o -name 'mysql-client.cnf' \) \
  -print -quit | grep -q .; then
  echo "SENSITIVE_TRANSFER_FILE_FOUND" >&2
  exit 41
fi

echo "BACKUP_VERIFY_OK directory=$BACKUP_DIR"
echo "SENSITIVE_TRANSFER_CLEANUP_OK"
