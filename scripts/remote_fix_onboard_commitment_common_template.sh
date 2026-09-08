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

STAMP="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="/opt/erp-new-backups/onboard-commitment-common-$STAMP"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

mysqldump -uroot -p"$MYSQL_ROOT_PASS" \
  --no-create-info --skip-extended-insert --complete-insert \
  --where='template_id=80' "$DB" oa_sign_template \
  > "$BACKUP_DIR/oa_sign_template_80.sql"
chmod 600 "$BACKUP_DIR/oa_sign_template_80.sql"

mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" \
  > "$BACKUP_DIR/before.tsv" <<'SQL'
SELECT template_id, template_type, template_name, template_version, scenario,
       employment_type, match_condition_json, status, file_url, file_hash,
       update_by, update_time, remark
FROM oa_sign_template
WHERE template_id = 80;
SQL
chmod 600 "$BACKUP_DIR/before.tsv"

CHANGED="$(
  mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
START TRANSACTION;
SELECT template_id
FROM oa_sign_template
WHERE template_id = 80
  AND template_type = 'ONBOARD_COMMITMENT'
  AND scenario = 'onboard'
  AND status = '0'
  AND employment_type = '劳动合同'
  AND match_condition_json IS NULL
FOR UPDATE;
UPDATE oa_sign_template
SET employment_type = NULL,
    match_condition_json = NULL,
    update_by = 'admin',
    update_time = NOW(),
    remark = CONCAT_WS(';', NULLIF(remark, ''),
        '2026-07-24 入职承诺书确认为劳动/劳务通用，清除合同类型限制')
WHERE template_id = 80
  AND template_type = 'ONBOARD_COMMITMENT'
  AND scenario = 'onboard'
  AND status = '0'
  AND employment_type = '劳动合同'
  AND match_condition_json IS NULL;
SELECT ROW_COUNT();
COMMIT;
SQL
)"
CHANGED="$(printf '%s\n' "$CHANGED" | tail -1)"
test "$CHANGED" = "1"

mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" \
  > "$BACKUP_DIR/after.tsv" <<'SQL'
SELECT template_id, template_type, template_name, template_version, scenario,
       employment_type, match_condition_json, status, file_url, file_hash,
       update_by, update_time, remark
FROM oa_sign_template
WHERE template_id = 80;
SQL
chmod 600 "$BACKUP_DIR/after.tsv"

EMPLOYMENT_TYPE="$(
  mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASS" "$DB" \
    -e "SELECT COALESCE(employment_type, '<NULL>') FROM oa_sign_template WHERE template_id=80"
)"
MATCH_CONDITION="$(
  mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASS" "$DB" \
    -e "SELECT COALESCE(match_condition_json, '<NULL>') FROM oa_sign_template WHERE template_id=80"
)"
test "$EMPLOYMENT_TYPE" = "<NULL>"
test "$MATCH_CONDITION" = "<NULL>"

echo "TEMPLATE_COMMON_OK database=$DB template_id=80 backup=$BACKUP_DIR employment_type=$EMPLOYMENT_TYPE match_condition_json=$MATCH_CONDITION"
