#!/usr/bin/env bash
set -euo pipefail
umask 077

DB="bosserp_stock_state_75c59ee"
PASS_FILE="/root/.erp-mysql-root-pass"
MYSQL_CNF="$(mktemp /tmp/sign-release-state.XXXXXX.cnf)"

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

mysql --defaults-extra-file="$MYSQL_CNF" --protocol=socket \
  --batch --raw --skip-column-names "$DB" <<'SQL'
SELECT CONCAT('legal_entities_total=', COUNT(*),
              ' active=', COALESCE(SUM(status='0'), 0))
FROM sys_legal_entity;

SELECT CONCAT_WS('|', 'LEGAL_ENTITY', legal_entity_id, legal_entity_code,
                 legal_entity_name, COALESCE(unified_social_credit_code, 'NULL'),
                 COALESCE(registered_address, 'NULL'),
                 COALESCE(legal_representative, 'NULL'), status)
FROM sys_legal_entity
ORDER BY legal_entity_id;

SELECT CONCAT('seals_total=', COUNT(*),
              ' active=', COALESCE(SUM(status='0'), 0),
              ' active_usable_now=', COALESCE(SUM(
                status='0'
                AND (valid_from IS NULL OR valid_from <= NOW())
                AND (valid_to IS NULL OR valid_to >= NOW())
              ), 0))
FROM oa_company_seal_config;

SELECT CONCAT_WS('|', 'SEAL', seal_id, COALESCE(legal_entity_id, 'NULL'),
                 COALESCE(seal_code, 'NULL'), seal_name,
                 COALESCE(seal_type, 'NULL'), COALESCE(is_default, 'NULL'),
                 status, COALESCE(DATE_FORMAT(valid_from, '%Y-%m-%d %H:%i:%s'), 'NULL'),
                 COALESCE(DATE_FORMAT(valid_to, '%Y-%m-%d %H:%i:%s'), 'NULL'),
                 COALESCE(seal_image_url, 'NULL'),
                 CASE WHEN seal_image_url IS NULL OR TRIM(seal_image_url)=''
                      THEN 'IMAGE_MISSING' ELSE 'IMAGE_PRESENT' END,
                 CASE WHEN seal_image_hash IS NULL OR TRIM(seal_image_hash)=''
                      THEN 'HASH_MISSING' ELSE 'HASH_PRESENT' END)
FROM oa_company_seal_config
ORDER BY seal_id;

SELECT CONCAT_WS('|', 'TARGET_DEPT', d.dept_id, d.dept_name,
                 COALESCE(d.legal_entity_id, 'NULL'),
                 COALESCE(le.legal_entity_name, 'UNBOUND'))
FROM sys_dept d
LEFT JOIN sys_legal_entity le ON le.legal_entity_id=d.legal_entity_id
WHERE d.dept_id IN (1176, 1171, 1157)
ORDER BY d.dept_id;

SELECT CONCAT('plans_total=', COUNT(*),
              ' enabled=', COALESCE(SUM(status='0'), 0))
FROM oa_sign_plan;

SELECT CONCAT_WS('|', 'TARGET_TEMPLATE', template_id, template_type,
                 template_name, status,
                 COALESCE(employee_sign_required, 'NULL'),
                 COALESCE(company_seal_required, 'NULL'),
                 CASE WHEN signature_position_json IS NULL OR TRIM(signature_position_json)=''
                      THEN 'SIGN_POSITION_MISSING' ELSE 'SIGN_POSITION_PRESENT' END,
                 CASE WHEN company_seal_position_json IS NULL OR TRIM(company_seal_position_json)=''
                      THEN 'SEAL_POSITION_MISSING' ELSE 'SEAL_POSITION_PRESENT' END)
FROM oa_sign_template
WHERE template_id IN (7, 11, 12)
ORDER BY template_id;

SELECT CONCAT_WS('|', 'TARGET_PLAN', p.plan_id, p.plan_name, p.scenario,
                 p.shop_dept_id, COALESCE(d.dept_name, 'NULL'), p.status,
                 COALESCE(p.employment_type, 'NULL'),
                 COALESCE(p.social_type, 'NULL'),
                 COALESCE(p.service_person_type, 'NULL'),
                 COALESCE(p.insurance_type, 'NULL'),
                 COALESCE(p.post_level_snapshot, 'NULL'))
FROM oa_sign_plan p
LEFT JOIN sys_dept d ON d.dept_id=p.shop_dept_id
WHERE p.shop_dept_id IN (1176, 1171, 1157)
  AND LOWER(p.scenario)='onboard'
ORDER BY p.shop_dept_id, p.plan_id;

SELECT CONCAT('plan_versions_total=', COUNT(*),
              ' published_enabled=', COALESCE(SUM(
                publish_status='PUBLISHED' AND matching_status='ENABLED'
              ), 0))
FROM oa_sign_plan_version;

SELECT CONCAT_WS('|', 'TARGET_VERSION', version_id, plan_id, plan_name,
                 version_no, publish_status, matching_status,
                 shop_dept_id, version_hash)
FROM oa_sign_plan_version
WHERE shop_dept_id IN (1176, 1171, 1157)
  AND LOWER(scenario)='onboard'
ORDER BY shop_dept_id, version_id;
SQL

echo "SIGN_RELEASE_STATE_OK"
