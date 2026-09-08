#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

RELEASE_ID="erp-current-release-20260722"
PACKAGE="/opt/erp-new-packages/erp-aliyun-deploy-20260722-100205-current-ecs-host.tar.gz"
PACKAGE_SHA="d0d3392fbe8654e5f86ca9b623c8bff0b3cefadd614e0afe55527591ffc70108"
CURRENT_LINK="/opt/erp-new"
PASSWORD_FILE="/root/.erp-mysql-root-pass"
STAMP="$(date +%Y%m%d%H%M%S)"
WORK_DIR="/opt/erp-new-data-imports/${RELEASE_ID}-${STAMP}"
BACKUP_DIR="/opt/erp-new-data-backups/${RELEASE_ID}-${STAMP}"
LEDGER_DIR="/opt/erp-new-data-migrations/${RELEASE_ID}"

test -f "$PACKAGE"
test "$(sha256sum "$PACKAGE" | awk '{print $1}')" = "$PACKAGE_SHA"
test -r "$CURRENT_LINK/.env"
test -r "$PASSWORD_FILE"
command -v mysql >/dev/null 2>&1
command -v mysqldump >/dev/null 2>&1

DB="$(awk -F= '$1=="MYSQL_DATABASE" {gsub(/^[[:space:]\"'"'"']+|[[:space:]\"'"'"']+$/, "", $2); print $2; exit}' "$CURRENT_LINK/.env")"
test "$DB" = "BossERP_stock_state_75c59ee"
export MYSQL_PWD="$(cat "$PASSWORD_FILE")"
MYSQL=(mysql --protocol=tcp --batch --raw --skip-column-names --default-character-set=utf8mb4 -uroot "$DB")

mkdir -p "$WORK_DIR" "$BACKUP_DIR" "$LEDGER_DIR"
chmod 700 "$WORK_DIR" "$BACKUP_DIR" "$LEDGER_DIR"

tar -xzf "$PACKAGE" -C "$WORK_DIR" \
  "docker/mysql/releases/${RELEASE_ID}"
SQL_DIR="$WORK_DIR/docker/mysql/releases/${RELEASE_ID}"
test -d "$SQL_DIR"

NAMES=(
  erp_oa_sign_onboard_import_20260718.sql
  erp_system_sign_profile_supplement_20260718.sql
  erp_oa_sign_salary_social_mapping_20260719.sql
  erp_system_sign_candidate_phone_index_20260719.sql
  erp_oa_sign_company_salary_policy_20260720.sql
  erp_oa_sign_dual_sequence_evidence_20260720.sql
  erp_oa_sign_onboard_send_idempotency_20260720.sql
  erp_oa_sign_task_batch_finalize_20260720.sql
  erp_oa_sign_file_cleanup_20260720.sql
  erp_oa_sign_task_hard_delete_idempotency_20260720.sql
  erp_oa_sign_contract_term_snapshot_20260721.sql
)
HASHES=(
  e79554c95992186ebf15318cd14066fb65ec77883c1887ca01b642f595e3f945
  07e04d77a4e60dd174846151f9f3cdfca1a732e42f0239c85717408b9ad2ba3e
  eb9607af03f34d65290b2a031bb29966e65a6d9726f36b918bc9a8dd1555a8a1
  ad662ad63dd2f431d1ad59dce928bf54596616c4c96e449b79f586b6d4681328
  6f222d063f185490254915ffa4324605e1c189fa18f79e2014da9b83a7facd2b
  eb962dd09462732b12a67e52420a93c7c3e672fed5bbb26cf47920972606fa0a
  c6d2eda6d530504b503f6695a8ad7861035768aac635b4b0aeb069209fce2050
  5cd0884efb6613763c3ef5ac0be3554e8a7b0c31162c5eef06a67dd838331960
  0d57049074315bda6db4f802d689d1ab64c37473fa01c410e3b2fdf90d9fa559
  cbddc4165293f04e8ceb3604317538ab2e1a198fae638316c22ed00c1cec7eca
  458fe540017d40c3902b387507b082bc447c2c4a826144e88b274f88927ab683
)

for index in "${!NAMES[@]}"; do
  test -f "$SQL_DIR/${NAMES[$index]}"
  test "$(sha256sum "$SQL_DIR/${NAMES[$index]}" | awk '{print $1}')" = "${HASHES[$index]}"
done

FINAL_COLUMN_COUNT="$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_sign_package_document' AND column_name IN ('package_id','final_document_version','final_read_confirmed')")"
test "$FINAL_COLUMN_COUNT" = 3

PARTIAL_POLICY_COLUMN_COUNT="$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_sign_onboard_import_row' AND column_name IN ('matched_legal_entity_id','company_match_mode','company_match_score','company_second_score','company_match_policy_version','company_master_version','dept_legal_entity_id','company_dept_conflict','company_candidates_json','recommended_seal_id','seal_recommendation_mode','seal_candidates_json')")"
if [ "$PARTIAL_POLICY_COLUMN_COUNT" != 0 ] && [ "$PARTIAL_POLICY_COLUMN_COUNT" != 12 ]; then
  echo "partial company salary policy migration detected: columns=$PARTIAL_POLICY_COLUMN_COUNT" >&2
  exit 61
fi
if [ "$PARTIAL_POLICY_COLUMN_COUNT" = 12 ] && [ ! -f "$LEDGER_DIR/09-company-salary-policy.ok" ]; then
  echo "company salary policy columns exist without trusted ledger marker; refusing replay" >&2
  exit 61
fi

OPEN_DUPLICATES="$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM (SELECT employee_id FROM oa_sign_task WHERE UPPER(TRIM(scenario))='ONBOARD' AND status NOT IN ('SIGNED','REFUSED','EXPIRED','CANCELLED','NO_ACTION') GROUP BY employee_id HAVING COUNT(*)>1) d")"
test "$OPEN_DUPLICATES" = 0

BACKUP_CANDIDATES=(
  oa_sign_task oa_sign_onboard_import_batch oa_sign_onboard_import_row
  oa_sign_onboard_data_request oa_sign_package oa_sign_package_document
  oa_sign_template oa_sign_plan oa_sign_plan_template oa_sign_plan_version
  oa_sign_plan_version_template oa_sign_onboard_send_request oa_sign_file_cleanup
  oa_sign_task_hard_delete_operation sys_user sys_user_profile
  sys_sign_profile_supplement_audit sys_menu sys_role_menu
  sys_sign_hr_menu_grant sys_role sys_sign_hr_state sys_legal_entity
  oa_company_seal_config
)
EXISTING_TABLES=()
for table in "${BACKUP_CANDIDATES[@]}"; do
  if [ "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='${table}'")" = 1 ]; then
    EXISTING_TABLES+=("$table")
  fi
done
test "${#EXISTING_TABLES[@]}" -gt 0
mysqldump --protocol=tcp --single-transaction --quick --routines --triggers \
  --default-character-set=utf8mb4 -uroot "$DB" "${EXISTING_TABLES[@]}" \
  | gzip -1 > "$BACKUP_DIR/tables-before.sql.gz"
test -s "$BACKUP_DIR/tables-before.sql.gz"
sha256sum "$BACKUP_DIR/tables-before.sql.gz" > "$BACKUP_DIR/tables-before.sql.gz.sha256"

OA_WAS_ACTIVE=0
if systemctl is-active --quiet erp-new@oa.service; then
  OA_WAS_ACTIVE=1
  systemctl stop erp-new@oa.service
fi
restart_oa() {
  if [ "$OA_WAS_ACTIVE" = 1 ]; then
    systemctl start erp-new@oa.service || true
  fi
}
trap restart_oa EXIT

for index in "${!NAMES[@]}"; do
  sequence="$((index + 5))"
  name="${NAMES[$index]}"
  hash="${HASHES[$index]}"
  if [ "$sequence" = 9 ]; then
    marker="$LEDGER_DIR/09-company-salary-policy.ok"
  else
    marker="$LEDGER_DIR/$(printf '%02d' "$sequence")-${name%.sql}.ok"
  fi
  if [ -f "$marker" ]; then
    test "$(cat "$marker")" = "$hash"
    echo "MIGRATION_SKIPPED sequence=$sequence file=$name sha256=$hash"
    continue
  fi
  echo "MIGRATION_APPLY sequence=$sequence file=$name sha256=$hash"
  "${MYSQL[@]}" < "$SQL_DIR/$name"
  marker_tmp="${marker}.tmp.$$"
  printf '%s' "$hash" > "$marker_tmp"
  chmod 600 "$marker_tmp"
  mv "$marker_tmp" "$marker"
  printf '%s\t%s\t%s\t%s\n' "$sequence" "$name" "$hash" "$(date -Iseconds)" >> "$BACKUP_DIR/applied.tsv"
  echo "MIGRATION_OK sequence=$sequence file=$name"
done

NEW_TABLE_COUNT="$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('oa_sign_onboard_import_batch','oa_sign_onboard_import_row','oa_sign_onboard_data_request','sys_sign_profile_supplement_audit','oa_sign_onboard_send_request','oa_sign_file_cleanup','oa_sign_task_hard_delete_operation')")"
test "$NEW_TABLE_COUNT" = 7
INDEX_COUNT="$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name IN ('uk_oa_sign_task_open_onboard_employee','idx_sys_user_sign_phone','uk_oa_sign_onboard_signature_request','idx_oa_sign_final_read_version')")"
test "$INDEX_COUNT" -ge 4
INVALID_TERM_COUNT="$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_package WHERE contract_term_code_snapshot IS NOT NULL AND BINARY contract_term_code_snapshot NOT IN (BINARY 'FIXED_TERM',BINARY 'OPEN_ENDED')")"
test "$INVALID_TERM_COUNT" = 0

restart_oa
OA_WAS_ACTIVE=0
trap - EXIT
unset MYSQL_PWD

echo "MIGRATIONS_COMPLETE release=$RELEASE_ID backup=$BACKUP_DIR work=$WORK_DIR applied=${#NAMES[@]}"
