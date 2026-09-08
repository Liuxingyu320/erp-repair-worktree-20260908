#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

CURRENT_LINK="/opt/erp-new"
ENV_FILE="$CURRENT_LINK/.env"
PASSWORD_FILE="/root/.erp-mysql-root-pass"

test -r "$ENV_FILE"
test -r "$PASSWORD_FILE"
command -v mysql >/dev/null 2>&1

DB="$(awk -F= '$1=="MYSQL_DATABASE" {gsub(/^[[:space:]\"'"'"']+|[[:space:]\"'"'"']+$/, "", $2); print $2; exit}' "$ENV_FILE")"
test -n "$DB"
export MYSQL_PWD="$(cat "$PASSWORD_FILE")"
MYSQL=(mysql --protocol=tcp --batch --raw --skip-column-names --default-character-set=utf8mb4 -uroot "$DB")

echo "AUDIT_BEGIN database=$DB"

"${MYSQL[@]}" <<'SQL'
SELECT CONCAT('table_count|', table_name, '|', table_rows)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'sys_legal_entity','oa_company_seal_config','oa_sign_template',
    'oa_sign_plan','oa_sign_plan_template','oa_sign_plan_version',
    'oa_sign_plan_version_template','oa_sign_task','oa_sign_package',
    'oa_sign_package_document','oa_sign_onboard_import_row',
    'oa_sign_onboard_data_request'
  )
ORDER BY table_name;

SELECT CONCAT('prerequisite|final_document_columns|', COUNT(*))
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'oa_sign_package_document'
  AND column_name IN ('package_id','final_document_version','final_read_confirmed');

SELECT CONCAT('migration9_columns|', COUNT(*))
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'oa_sign_onboard_import_row'
  AND column_name IN (
    'matched_legal_entity_id','company_match_mode',
    'company_match_score','company_second_score',
    'company_match_policy_version','company_master_version',
    'dept_legal_entity_id','company_dept_conflict',
    'company_candidates_json','recommended_seal_id',
    'seal_recommendation_mode','seal_candidates_json'
  );

SELECT CONCAT('open_onboard_duplicate|', COUNT(*))
FROM (
  SELECT employee_id
  FROM oa_sign_task
  WHERE UPPER(TRIM(scenario)) = 'ONBOARD'
    AND status NOT IN ('SIGNED','REFUSED','EXPIRED','CANCELLED','NO_ACTION')
  GROUP BY employee_id
  HAVING COUNT(*) > 1
) duplicate_rows;

SELECT CONCAT('sign_activity|open_tasks|', COUNT(*))
FROM oa_sign_task
WHERE status NOT IN ('SIGNED','REFUSED','EXPIRED','CANCELLED','NO_ACTION');

SELECT CONCAT('entity|', legal_entity_id, '|', legal_entity_code, '|', legal_entity_name, '|', status, '|', version)
FROM sys_legal_entity
ORDER BY legal_entity_id;

SELECT CONCAT('seal|', seal_id, '|', COALESCE(legal_entity_id,''), '|', COALESCE(seal_code,''), '|', seal_name,
              '|', seal_type, '|', is_default, '|', status, '|', seal_image_url, '|', COALESCE(seal_image_hash,''))
FROM oa_company_seal_config
ORDER BY seal_id;

SELECT CONCAT('template|', template_id, '|', template_name, '|', template_type, '|', COALESCE(template_version,''),
              '|', status, '|', file_url, '|', COALESCE(file_hash,''), '|', COALESCE(company_seal_required,''))
FROM oa_sign_template
ORDER BY template_id;

SELECT CONCAT('plan|', plan_id, '|', plan_name, '|', scenario, '|', COALESCE(employment_type,''),
              '|', COALESCE(social_type,''), '|', COALESCE(post_level_snapshot,''),
              '|', COALESCE(salary_version,''), '|', status, '|', COALESCE(legal_entity_id,''))
FROM oa_sign_plan
ORDER BY plan_id;

SELECT CONCAT('plan_version|', version_id, '|', plan_id, '|', version_no, '|', publish_status,
              '|', matching_status, '|', version_hash)
FROM oa_sign_plan_version
ORDER BY version_id;
SQL

unset MYSQL_PWD
echo "AUDIT_OK"
