#!/usr/bin/env bash
set -euo pipefail

MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
DB="$(
  mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASS" information_schema \
    -e "SELECT table_schema
        FROM tables
        WHERE table_name = 'oa_sign_template'
          AND table_schema NOT IN ('mysql','information_schema','performance_schema','sys')
        ORDER BY table_schema
        LIMIT 1"
)"
test -n "$DB"
echo "DATABASE=$DB"

mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
SELECT 'TEMPLATE' AS section,
       template_id, template_type, template_name, template_version,
       scenario, employment_type, match_condition_json,
       status, file_url, file_hash
FROM oa_sign_template
WHERE template_type = 'ONBOARD_COMMITMENT'
ORDER BY status, template_id;

SELECT 'PLANS' AS section,
       plan_id, plan_name, scenario, employment_type,
       post_level_snapshot, rule_json, status, update_time
FROM oa_sign_plan
WHERE plan_id IN (37,45)
   OR JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.routeCode')) IN ('B1','B3')
   OR plan_name LIKE '%B1%'
   OR plan_name LIKE '%B3%'
ORDER BY plan_id;

SELECT 'BINDINGS' AS section,
       pt.plan_id, p.plan_name, pt.template_id, pt.template_type,
       pt.sort_order, t.employment_type, t.match_condition_json
FROM oa_sign_plan_template pt
JOIN oa_sign_plan p ON p.plan_id = pt.plan_id
LEFT JOIN oa_sign_template t ON t.template_id = pt.template_id
WHERE pt.plan_id IN (
    SELECT plan_id
    FROM oa_sign_plan
    WHERE plan_id IN (37,45)
       OR JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.routeCode')) IN ('B1','B3')
       OR plan_name LIKE '%B1%'
       OR plan_name LIKE '%B3%'
)
ORDER BY pt.plan_id, pt.sort_order, pt.id;

SELECT 'VERSIONS' AS section,
       version_id, plan_id, plan_name, version_no, rule_json,
       publish_status, matching_status, published_time, version_hash
FROM oa_sign_plan_version
WHERE plan_id IN (
    SELECT plan_id
    FROM oa_sign_plan
    WHERE plan_id IN (37,45)
       OR JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.routeCode')) IN ('B1','B3')
       OR plan_name LIKE '%B1%'
       OR plan_name LIKE '%B3%'
)
ORDER BY plan_id, version_no DESC;

SELECT 'VERSION_COMMITMENT' AS section,
       v.version_id, v.plan_id, v.version_no, v.matching_status,
       vt.template_id, vt.template_type, vt.template_name,
       vt.match_condition_json, vt.source_file_hash
FROM oa_sign_plan_version v
JOIN oa_sign_plan_version_template vt ON vt.plan_version_id = v.version_id
WHERE vt.template_type = 'ONBOARD_COMMITMENT'
  AND v.plan_id IN (
      SELECT plan_id
      FROM oa_sign_plan
      WHERE plan_id IN (37,45)
         OR JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.routeCode')) IN ('B1','B3')
         OR plan_name LIKE '%B1%'
         OR plan_name LIKE '%B3%'
  )
ORDER BY v.plan_id, v.version_no DESC;
SQL
