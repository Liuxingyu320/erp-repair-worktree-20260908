#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

RELEASE_ID="sign-config-release-20260722"
BUNDLE="/opt/erp-new-packages/erp-sign-config-20260722.tar.gz"
BUNDLE_SHA="2ce85daeb263b5b414f27312207dbfe2bdeea44acdfedd61cdfd05c4e5584001"
CURRENT_LINK="/opt/erp-new"
PASSWORD_FILE="/root/.erp-mysql-root-pass"
UPLOAD_ROOT="/opt/erp-new-data/uploadPath"
STAMP="$(date +%Y%m%d%H%M%S)"
WORK_DIR="/opt/erp-new-data-imports/${RELEASE_ID}-${STAMP}"
BACKUP_DIR="/opt/erp-new-data-backups/${RELEASE_ID}-${STAMP}"
LOCK_FILE="/run/lock/erp-sign-config-20260722.lock"

exec 9>"$LOCK_FILE"
flock -n 9 || { echo "another signing configuration import is running" >&2; exit 70; }

test -f "$BUNDLE"
test "$(sha256sum "$BUNDLE" | awk '{print $1}')" = "$BUNDLE_SHA"
test -r "$CURRENT_LINK/.env"
test -r "$PASSWORD_FILE"
command -v mysql >/dev/null 2>&1
command -v mysqldump >/dev/null 2>&1
command -v sha256sum >/dev/null 2>&1
command -v gzip >/dev/null 2>&1

DB="$(awk -F= '$1=="MYSQL_DATABASE" {gsub(/^[[:space:]\"'"'"']+|[[:space:]\"'"'"']+$/, "", $2); print $2; exit}' "$CURRENT_LINK/.env")"
test "$DB" = "BossERP_stock_state_75c59ee"
export MYSQL_PWD="$(cat "$PASSWORD_FILE")"
MYSQL=(mysql --protocol=tcp --batch --raw --skip-column-names --default-character-set=utf8mb4 -uroot "$DB")

mkdir -p "$WORK_DIR" "$BACKUP_DIR" "$UPLOAD_ROOT"
chmod 700 "$WORK_DIR" "$BACKUP_DIR"
tar -xzf "$BUNDLE" -C "$WORK_DIR"
SOURCE_DIR="$WORK_DIR/$RELEASE_ID"
test -f "$SOURCE_DIR/MANIFEST.sha256"
(cd "$SOURCE_DIR" && sha256sum -c MANIFEST.sha256)
test "$(find "$SOURCE_DIR/sql" -type f | wc -l | tr -d ' ')" = 7
test "$(find "$SOURCE_DIR/files" -type f | wc -l | tr -d ' ')" = 13

OA_WAS_ACTIVE=0
DB_COMMITTED=0
COMPLETED=0
if systemctl is-active --quiet erp-new@oa.service; then
  OA_WAS_ACTIVE=1
  systemctl stop erp-new@oa.service
fi
cleanup() {
  rc=$?
  if [ "$COMPLETED" != 1 ] && [ "$DB_COMMITTED" = 0 ] && [ "$OA_WAS_ACTIVE" = 1 ]; then
    systemctl start erp-new@oa.service || true
  fi
  unset MYSQL_PWD
  exit "$rc"
}
trap cleanup EXIT

test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_task")" = 0
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_package")" = 0
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_plan")" = 0
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_plan_template")" = 0
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_plan_version")" = 0
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_plan_version_template")" = 0

TABLES=(
  sys_legal_entity oa_company_seal_config oa_sign_template oa_sign_plan
  oa_sign_plan_template oa_sign_plan_version oa_sign_plan_version_template
)
mysqldump --protocol=tcp --single-transaction --quick --hex-blob \
  --set-gtid-purged=OFF --routines --triggers --default-character-set=utf8mb4 \
  -uroot "$DB" "${TABLES[@]}" | gzip -1 > "$BACKUP_DIR/sign-config-before.sql.gz"
test -s "$BACKUP_DIR/sign-config-before.sql.gz"
gzip -t "$BACKUP_DIR/sign-config-before.sql.gz"
sha256sum "$BACKUP_DIR/sign-config-before.sql.gz" > "$BACKUP_DIR/sign-config-before.sql.gz.sha256"

# Existing assets are never overwritten. A different hash at the same path aborts
# before any database mutation; missing files are installed atomically.
while IFS= read -r -d '' source_file; do
  relative_path="${source_file#"$SOURCE_DIR/files/"}"
  target_file="$UPLOAD_ROOT/$relative_path"
  source_hash="$(sha256sum "$source_file" | awk '{print $1}')"
  if [ -e "$target_file" ]; then
    test -f "$target_file"
    target_hash="$(sha256sum "$target_file" | awk '{print $1}')"
    if [ "$target_hash" != "$source_hash" ]; then
      echo "asset collision: $relative_path" >&2
      exit 71
    fi
    continue
  fi
  mkdir -p "$(dirname "$target_file")"
  temp_target="${target_file}.tmp.$$"
  install -m 0640 "$source_file" "$temp_target"
  chown --reference="$UPLOAD_ROOT" "$temp_target"
  test "$(sha256sum "$temp_target" | awk '{print $1}')" = "$source_hash"
  mv "$temp_target" "$target_file"
done < <(find "$SOURCE_DIR/files" -type f -print0)

{
  printf '%s\n' \
    'SET SESSION sql_mode=CONCAT(@@sql_mode,",STRICT_ALL_TABLES");' \
    'SET FOREIGN_KEY_CHECKS=0;' \
    'START TRANSACTION;' \
    'DELETE FROM oa_sign_plan_version_template;' \
    'DELETE FROM oa_sign_plan_version;' \
    'DELETE FROM oa_sign_plan_template;' \
    'DELETE FROM oa_sign_plan;' \
    'DELETE FROM oa_company_seal_config;' \
    'DELETE FROM oa_sign_template;' \
    'DELETE FROM sys_legal_entity;'
  for sql_file in \
    01_sys_legal_entity.sql \
    02_oa_company_seal_config.sql \
    03_oa_sign_template.sql \
    04_oa_sign_plan.sql \
    05_oa_sign_plan_template.sql \
    06_oa_sign_plan_version.sql \
    07_oa_sign_plan_version_template.sql; do
    cat "$SOURCE_DIR/sql/$sql_file"
  done
  printf '%s\n' \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM sys_legal_entity)=4,'SELECT 1','SELECT * FROM __bad_legal_entity_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_company_seal_config)=4,'SELECT 1','SELECT * FROM __bad_seal_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_template)=9,'SELECT 1','SELECT * FROM __bad_template_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_plan)=6,'SELECT 1','SELECT * FROM __bad_plan_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_plan_template)=24,'SELECT 1','SELECT * FROM __bad_plan_template_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_plan_version)=10,'SELECT 1','SELECT * FROM __bad_plan_version_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_plan_version_template)=39,'SELECT 1','SELECT * FROM __bad_version_template_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_plan_version WHERE matching_status='ENABLED')=6,'SELECT 1','SELECT * FROM __bad_enabled_version_count__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_plan_template pt LEFT JOIN oa_sign_plan p ON p.plan_id=pt.plan_id LEFT JOIN oa_sign_template t ON t.template_id=pt.template_id WHERE p.plan_id IS NULL OR t.template_id IS NULL)=0,'SELECT 1','SELECT * FROM __bad_plan_reference__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_sign_plan_version_template vt LEFT JOIN oa_sign_plan_version v ON v.version_id=vt.plan_version_id WHERE v.version_id IS NULL)=0,'SELECT 1','SELECT * FROM __bad_version_reference__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    "SET @assert_sql=IF((SELECT COUNT(*) FROM oa_company_seal_config s LEFT JOIN sys_legal_entity e ON e.legal_entity_id=s.legal_entity_id WHERE s.legal_entity_id IS NOT NULL AND e.legal_entity_id IS NULL)=0,'SELECT 1','SELECT * FROM __bad_seal_reference__'); PREPARE s FROM @assert_sql; EXECUTE s; DEALLOCATE PREPARE s;" \
    'COMMIT;' \
    'SET FOREIGN_KEY_CHECKS=1;'
} | "${MYSQL[@]}"
DB_COMMITTED=1

test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM sys_legal_entity")" = 4
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_company_seal_config")" = 4
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_template")" = 9
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_plan")" = 6
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_plan_version WHERE matching_status='ENABLED'")" = 6

while IFS= read -r -d '' source_file; do
  relative_path="${source_file#"$SOURCE_DIR/files/"}"
  test "$(sha256sum "$UPLOAD_ROOT/$relative_path" | awk '{print $1}')" = \
       "$(sha256sum "$source_file" | awk '{print $1}')"
done < <(find "$SOURCE_DIR/files" -type f -print0)

if [ "$OA_WAS_ACTIVE" = 1 ]; then
  systemctl start erp-new@oa.service
  for _ in $(seq 1 45); do
    if curl -fsS --max-time 4 http://127.0.0.1:9204/actuator/health | grep -q '"status":"UP"'; then
      break
    fi
    sleep 2
  done
  systemctl is-active --quiet erp-new@oa.service
  curl -fsS --max-time 5 http://127.0.0.1:9204/actuator/health | grep -q '"status":"UP"'
fi

COMPLETED=1
unset MYSQL_PWD
trap - EXIT
echo "SIGN_CONFIG_SYNC_OK release=$RELEASE_ID backup=$BACKUP_DIR entities=4 seals=4 templates=9 plans=6 versions=10 assets=13"
