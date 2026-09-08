#!/usr/bin/env bash
set -euo pipefail
umask 077

DB=bosserp_stock_state_75c59ee
PASS_FILE=/root/.erp-mysql-root-pass
MYSQL_CNF="$(mktemp /tmp/beijing-poststate-mysql.XXXXXX)"
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

mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --raw --skip-column-names "$DB" <<'SQL'
SELECT CONCAT('database=',DATABASE());
SELECT CONCAT('counts=',CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user),(SELECT COUNT(*) FROM sys_user_profile),
 (SELECT COUNT(*) FROM sys_user_post),(SELECT COUNT(*) FROM sys_user_role),
 (SELECT COUNT(*) FROM sys_user_shop),(SELECT COUNT(*) FROM hr_employee_position_no_history),
 (SELECT current_value FROM hr_employee_no_sequence WHERE sequence_key='GLOBAL')));
SELECT CONCAT('create_only=',CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user WHERE create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user WHERE create_by='beijing0716_placeholder' AND status='1'
   AND del_flag='0' AND credential_state='CHANGE_REQUIRED' AND temporary_password_expires_at IS NULL),
 (SELECT COUNT(*) FROM sys_user_profile WHERE create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_post up JOIN sys_user u ON u.user_id=up.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.user_id=ur.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_shop us JOIN sys_user u ON u.user_id=us.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM hr_employee_position_no_history h JOIN sys_user u ON u.user_id=h.user_id
   WHERE u.create_by='beijing0716_placeholder' AND h.change_source='PROFILE_INSERT')));
SELECT CONCAT('numbers=',COALESCE(GROUP_CONCAT(user_name ORDER BY user_name SEPARATOR ','),'none'))
FROM sys_user WHERE create_by='beijing0716_placeholder';
SQL

find /opt/erp-new-data-backups -maxdepth 1 -type d -name 'beijing-create-only-*' \
  -printf 'backup_dir=%f\n' 2>/dev/null | sort | tail -n 5
find /opt/erp-new-data-imports -maxdepth 1 -type d -name 'beijing-create-only-transfer-*' \
  -printf 'transfer_residue=%f\n' 2>/dev/null | sort | tail -n 5

FAILED_ATTEMPT=/opt/erp-new-data-backups/beijing-create-only-20260717T210825Z-af3c97d7
if [[ -d "$FAILED_ATTEMPT" ]]; then
  find "$FAILED_ATTEMPT" -maxdepth 1 -printf 'failed_attempt_entry=%y:%f:%s\n' | sort
fi
