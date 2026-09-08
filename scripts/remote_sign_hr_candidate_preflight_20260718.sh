#!/usr/bin/env bash
set -euo pipefail
umask 077

DB="bosserp_stock_state_75c59ee"
PASS_FILE="/root/.erp-mysql-root-pass"
MYSQL_CNF="$(mktemp /tmp/sign-hr-candidate.XXXXXX.cnf)"
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

mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --raw --skip-column-names "$DB" <<'SQL'
SELECT CONCAT('candidate_count=',COUNT(*))
FROM sys_user WHERE nick_name='段继康' AND del_flag='0';

SELECT CONCAT_WS('|','CANDIDATE',u.user_id,u.user_name,u.nick_name,u.status,u.del_flag,
  COALESCE(p.employee_no,'NO_EMPLOYEE_NO'),COALESCE(d.dept_name,'NO_DEPT'),
  COALESCE(GROUP_CONCAT(DISTINCT CONCAT(r.role_id,':',r.role_key,':',r.status)
    ORDER BY r.role_id SEPARATOR ','),'NO_ROLE'))
FROM sys_user u
LEFT JOIN sys_user_profile p ON p.user_id=u.user_id
LEFT JOIN sys_dept d ON d.dept_id=u.dept_id
LEFT JOIN sys_user_role ur ON ur.user_id=u.user_id
LEFT JOIN sys_role r ON r.role_id=ur.role_id AND r.del_flag='0'
WHERE u.nick_name='段继康' AND u.del_flag='0'
GROUP BY u.user_id,u.user_name,u.nick_name,u.status,u.del_flag,p.employee_no,d.dept_name;

SELECT CONCAT('current_sign_hr=',COALESCE(MAX(config_value),'NULL'))
FROM sys_config WHERE config_key='sign.hr.user-id';

SELECT CONCAT_WS('|','ROLE_PERMISSION',u.user_id,r.role_id,r.role_key,
  SUM(m.perms='oa:signPackage:template'),
  SUM(m.perms='oa:signPackage:plan'),
  SUM(m.perms='oa:signPackage:plan:publish'),
  SUM(m.perms='oa:signPackage:batchCreate'))
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.user_id
JOIN sys_role r ON r.role_id=ur.role_id AND r.del_flag='0'
LEFT JOIN sys_role_menu rm ON rm.role_id=r.role_id
LEFT JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE u.nick_name='段继康' AND u.del_flag='0'
GROUP BY u.user_id,r.role_id,r.role_key
ORDER BY r.role_id;
SQL

echo "SIGN_HR_CANDIDATE_PREFLIGHT_OK"
