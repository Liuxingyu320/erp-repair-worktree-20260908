#!/usr/bin/env bash
set -euo pipefail
umask 077

DB="bosserp_stock_state_75c59ee"
PASS_FILE="/root/.erp-mysql-root-pass"
MYSQL_CNF="$(mktemp /tmp/sign-activation-preflight.XXXXXX.cnf)"

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
Path(sys.argv[2]).write_text(
    '[client]\nuser=root\npassword="' + escaped + '"\n',
    encoding="utf-8",
)
os.chmod(sys.argv[2], 0o600)
PY

MYSQL=(mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket --batch --raw --skip-column-names "$DB")

"${MYSQL[@]}" <<'SQL'
SELECT CONCAT('server=',@@hostname,' db=',DATABASE(),' version=',VERSION());

SELECT CONCAT('target_accounts=',COUNT(*),
              ' active=',SUM(status='0' AND del_flag='0'),
              ' disabled=',SUM(status='1' AND del_flag='0'),
              ' change_required=',SUM(credential_state='CHANGE_REQUIRED'),
              ' address_missing=',SUM(p.current_address IS NULL OR TRIM(p.current_address)=''))
FROM sys_user u
JOIN sys_user_profile p ON p.user_id=u.user_id
WHERE u.create_by='beijing0716_placeholder';

SELECT CONCAT_WS('|','TARGET',u.user_id,u.user_name,u.nick_name,u.status,u.del_flag,
                 COALESCE(u.credential_state,'NULL'),
                 CASE WHEN p.current_address IS NULL OR TRIM(p.current_address)='' THEN 'ADDRESS_MISSING' ELSE 'ADDRESS_OK' END,
                 COALESCE(p.contract_type,'NULL'),COALESCE(p.social_type,'NULL'),
                 COALESCE(p.job_grade,'NULL'),COALESCE(d.dept_name,'NULL'))
FROM sys_user u
JOIN sys_user_profile p ON p.user_id=u.user_id
LEFT JOIN sys_dept d ON d.dept_id=u.dept_id
WHERE u.create_by='beijing0716_placeholder'
ORDER BY u.user_id;

SELECT CONCAT('templates_total=',COUNT(*),
              ' enabled=',SUM(status='0'),
              ' disabled=',SUM(status='1'),
              ' onboard_enabled=',SUM(status='0' AND LOWER(scenario)='onboard'))
FROM oa_sign_template;

SELECT CONCAT_WS('|','TEMPLATE',template_id,template_type,template_name,
                 COALESCE(template_version,'NULL'),COALESCE(scenario,'NULL'),
                 COALESCE(employment_type,'NULL'),COALESCE(social_type,'NULL'),
                 COALESCE(post_level_scope,'NULL'),COALESCE(salary_version,'NULL'),status,
                 COALESCE(employee_visible,'NULL'),COALESCE(read_confirmation_required,'NULL'),
                 COALESCE(employee_sign_required,'NULL'),COALESCE(company_seal_required,'NULL'),
                 COALESCE(file_url,'NULL'),COALESCE(file_hash,'NULL'),COALESCE(remark,''))
FROM oa_sign_template
ORDER BY scenario,sort_order,template_id;

SELECT CONCAT('plans_total=',COUNT(*),' enabled=',SUM(status='0'),
              ' onboard_enabled=',SUM(status='0' AND LOWER(scenario)='onboard'))
FROM oa_sign_plan;

SELECT CONCAT_WS('|','PLAN',p.plan_id,p.plan_name,p.scenario,p.shop_dept_id,
                 COALESCE(d.dept_name,'NULL'),p.status,
                 COALESCE(p.employment_type,'NULL'),COALESCE(p.social_type,'NULL'),
                 COALESCE(p.service_person_type,'NULL'),COALESCE(p.insurance_type,'NULL'),
                 COALESCE(p.post_level_snapshot,'NULL'),COALESCE(p.salary_version,'NULL'))
FROM oa_sign_plan p
LEFT JOIN sys_dept d ON d.dept_id=p.shop_dept_id
ORDER BY p.plan_id;

SELECT CONCAT_WS('|','BINDING',pt.plan_id,pt.template_id,pt.sort_order,
                 COALESCE(t.template_type,'NULL'),COALESCE(t.template_name,'NULL'),
                 COALESCE(t.status,'NULL'))
FROM oa_sign_plan_template pt
LEFT JOIN oa_sign_template t ON t.template_id=pt.template_id
ORDER BY pt.plan_id,pt.sort_order,pt.id;

SELECT CONCAT('versions_total=',COUNT(*),
              ' published_enabled=',SUM(publish_status='PUBLISHED' AND matching_status='ENABLED'),
              ' onboard_published_enabled=',SUM(LOWER(scenario)='onboard' AND publish_status='PUBLISHED' AND matching_status='ENABLED'))
FROM oa_sign_plan_version;

SELECT CONCAT_WS('|','VERSION',version_id,plan_id,plan_name,version_no,scenario,shop_dept_id,
                 publish_status,matching_status,sign_deadline_days,version_hash)
FROM oa_sign_plan_version
ORDER BY version_id;

SELECT CONCAT_WS('|','VERSION_TEMPLATE',plan_version_id,template_id,template_type,
                 COALESCE(template_name,'NULL'),source_file_url,source_file_hash,
                 employee_visible,read_confirmation_required,employee_sign_required,
                 COALESCE(company_seal_required,'NULL'))
FROM oa_sign_plan_version_template
ORDER BY plan_version_id,sort_order,id;

SELECT CONCAT('legal_entities=',COUNT(*),' active=',SUM(status='0')) FROM sys_legal_entity;
SELECT CONCAT('seals=',COUNT(*),' active=',SUM(status='0')) FROM oa_company_seal_config;

SELECT CONCAT('sign_hr_config=',COALESCE(MAX(config_value),'NULL'))
FROM sys_config WHERE config_key='sign.hr.user-id';

SELECT CONCAT('password_policy=',COALESCE(MAX(config_value),'NULL'))
FROM sys_config WHERE config_key='sys.account.chrtype';

SELECT CONCAT_WS('|','SIGN_HR',u.user_id,u.user_name,u.nick_name,u.status,u.del_flag,
                 COALESCE(GROUP_CONCAT(DISTINCT r.role_key ORDER BY r.role_id SEPARATOR ','),'NO_ROLE'))
FROM sys_config c
JOIN sys_user u ON CAST(c.config_value AS UNSIGNED)=u.user_id
LEFT JOIN sys_user_role ur ON ur.user_id=u.user_id
LEFT JOIN sys_role r ON r.role_id=ur.role_id AND r.del_flag='0'
WHERE c.config_key='sign.hr.user-id'
GROUP BY u.user_id,u.user_name,u.nick_name,u.status,u.del_flag;

SELECT CONCAT_WS('|','ADMIN',u.user_id,u.user_name,u.nick_name,u.status,u.del_flag)
FROM sys_user u WHERE u.user_id=1;
SQL

echo "FILE_AUDIT_BEGIN"
find /opt/erp-new-data/uploadPath/sign-template -maxdepth 3 -type f -print0 2>/dev/null \
  | sort -z \
  | xargs -0 -r sha256sum
echo "FILE_AUDIT_END"

for svc in gateway auth system oa; do
  status="$(systemctl is-active "erp-new@$svc.service" 2>/dev/null || true)"
  echo "service=$svc status=${status:-unknown}"
done

echo "PREFLIGHT_OK"
