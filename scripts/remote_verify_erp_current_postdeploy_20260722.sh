#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

CURRENT_LINK="/opt/erp-new"
ENV_FILE="$CURRENT_LINK/.env"
PASSWORD_FILE="/root/.erp-mysql-root-pass"
UPLOAD_ROOT="/opt/erp-new-data/uploadPath"
EXPECTED_COMMIT="ececf4bc6e9ec6a945748cf32bff604b24d69dfb"
SERVICES=(gateway auth monitor system gen job oa inventory file approval)

port_for() {
  case "$1" in
    gateway) echo 8080;; auth) echo 9200;; monitor) echo 9100;;
    system) echo 9201;; gen) echo 9202;; job) echo 9203;; oa) echo 9204;;
    inventory) echo 9205;; file) echo 9300;; approval) echo 9206;;
    *) return 1;;
  esac
}

test -L "$CURRENT_LINK"
RELEASE_ROOT="$(readlink -f "$CURRENT_LINK")"
test "$RELEASE_ROOT" = "/opt/erp-new-20260722141503/docker"
test -f "$RELEASE_ROOT/nginx/html/dist/release-provenance.json"
grep -q "$EXPECTED_COMMIT" "$RELEASE_ROOT/nginx/html/dist/release-provenance.json"
echo 'VERIFY_CHECK release=OK'

for service in "${SERVICES[@]}"; do
  systemctl is-active --quiet "erp-new@$service.service"
  port="$(port_for "$service")"
  curl -fsS --max-time 6 "http://127.0.0.1:${port}/actuator/health" | grep -q '"status":"UP"'
done
echo 'VERIFY_CHECK services=OK'

test "$(grep -Ec '^OA_SIGN_EXCEL_IMPORT_ENABLED=true$' "$ENV_FILE")" = 1
test "$(grep -Ec '^PROFILE_COMPLETION_GATE_ENABLED=true$' "$ENV_FILE")" = 1
test "$(grep -Ec '^HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^OA_SIGN_EXPIRY_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^OA_SIGN_REMINDER_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^OA_SIGN_EMERGENCY_CREATE_ENABLED=false$' "$ENV_FILE")" = 1
test "$(grep -Ec '^ERP_JWT_SECRET=.+$' "$ENV_FILE")" = 1
JWT_SECRET_VALUE="$(awk -F= '$1=="ERP_JWT_SECRET" {print substr($0,index($0,"=")+1); exit}' "$ENV_FILE")"
test "${#JWT_SECRET_VALUE}" -ge 32
test "$JWT_SECRET_VALUE" != 'erp-local-dev-jwt-secret-8c0d4f2b79a1'
unset JWT_SECRET_VALUE
echo 'VERIFY_CHECK environment=OK'

INDEX_BODY="$(mktemp /tmp/erp-current-index.XXXXXX)"
CODE_BODY="$(mktemp /tmp/erp-current-code.XXXXXX)"
cleanup() { rm -f -- "$INDEX_BODY" "$CODE_BODY"; unset MYSQL_PWD; }
trap cleanup EXIT
curl -fsS --max-time 10 -H 'Host: 8.152.199.39' http://127.0.0.1/ -o "$INDEX_BODY"
grep -q '企业管理系统' "$INDEX_BODY"
echo 'VERIFY_CHECK homepage=OK'
curl -fsS --max-time 10 -H 'Host: 8.152.199.39' http://127.0.0.1/prod-api/code -o "$CODE_BODY"
grep -Eq '"code"[[:space:]]*:[[:space:]]*200' "$CODE_BODY"
echo 'VERIFY_CHECK captcha=OK'

DB="$(awk -F= '$1=="MYSQL_DATABASE" {gsub(/^[[:space:]\"'"'"']+|[[:space:]\"'"'"']+$/, "", $2); print $2; exit}' "$ENV_FILE")"
test "$DB" = "BossERP_stock_state_75c59ee"
export MYSQL_PWD="$(cat "$PASSWORD_FILE")"
MYSQL=(mysql --protocol=tcp --batch --raw --skip-column-names --default-character-set=utf8mb4 -uroot "$DB")
echo 'VERIFY_CHECK database_connection=OK'

test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM sys_legal_entity')" = 4
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_company_seal_config')" = 4
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_template')" = 9
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_plan')" = 6
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_plan_template')" = 24
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_plan_version')" = 10
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_plan_version_template')" = 39
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM oa_sign_plan_version WHERE matching_status='ENABLED'")" = 6
test "$("${MYSQL[@]}" -e 'SELECT COUNT(DISTINCT plan_id) FROM oa_sign_plan_version WHERE matching_status="ENABLED"')" = 6
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_task')" = 0
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_package')" = 0
test "$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_sign_onboard_import_row' AND column_name IN ('matched_legal_entity_id','company_match_mode','company_match_score','company_second_score','company_match_policy_version','company_master_version','dept_legal_entity_id','company_dept_conflict','company_candidates_json','recommended_seal_id','seal_recommendation_mode','seal_candidates_json')")" = 12
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_plan_template pt LEFT JOIN oa_sign_plan p ON p.plan_id=pt.plan_id LEFT JOIN oa_sign_template t ON t.template_id=pt.template_id WHERE p.plan_id IS NULL OR t.template_id IS NULL')" = 0
test "$("${MYSQL[@]}" -e 'SELECT COUNT(*) FROM oa_sign_plan_version_template vt LEFT JOIN oa_sign_plan_version v ON v.version_id=vt.plan_version_id WHERE v.version_id IS NULL')" = 0
echo 'VERIFY_CHECK database_invariants=OK'

while read -r expected relative; do
  target="$UPLOAD_ROOT/$relative"
  test -f "$target"
  test "$(sha256sum "$target" | awk '{print $1}')" = "$expected"
done <<'HASHES'
4fa824798d039fabf04382f547a3d867f645343065d1cb87bfde7e35e4d4de14 public/2026/06/14/图片1_20260614140927A001.png
4fa824798d039fabf04382f547a3d867f645343065d1cb87bfde7e35e4d4de14 public/2026/07/19/图片1_20260719201008A006.png
4fa824798d039fabf04382f547a3d867f645343065d1cb87bfde7e35e4d4de14 public/2026/07/20/北京金英灵韵_20260720181145A001.png
b14583e12e150ff25482e07d9ae9162c7bd5b274260254c8696d24b0926982d1 public/2026/07/20/舟山_20260720181201A002.jpg
9cc4474fa78b6e9707cb87316d87023ab6b654b31e6488c4bbd857edd47b5816 sign-template/onboard-20260718-v4-draft/05_ONBOARD_COMMITMENT.docx
fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118 sign-template/onboard-20260718-v4-draft/09_ONBOARD_LABOR_CONTRACT.docx
65675b6182ee5c9cdd6650f94341cfccd4b23e69dfbde87c806cd41aee2f4b66 sign-template/onboard-20260718-v4-draft/10_ONBOARD_HANDBOOK_RECEIPT.docx
c3341591b4806fcc5909315040a2e174da0eaa90245169cf74aba735d5ad8551 sign-template/onboard-20260718-v4-draft/11_ONBOARD_SALARY_CONFIRM_A.docx
e2ba862e63fc8e41485fc26348e354fc568126247f9676571690a030f3957c1c sign-template/onboard-20260718-v4-draft/12_ONBOARD_SALARY_CONFIRM_B.docx
3794b2a1f54b734fd36acd49dfd6e7920892a290744de6fc34e19d9ef51eb5b4 sign-template/onboard-20260718-v4-draft/13_ONBOARD_SERVICE_CONTRACT.docx
4c72f4ec27b75aa070df051c736b316dae1c9752550ef46b9d670ede887c6a13 sign-template/onboard-20260718-v4-draft/14_ONBOARD_SERVICE_RECEIPT.docx
0ab9894b08ad4b91ea1b5b1cd3b2c07332c5ef6483453af525069bee7999ba40 sign-template/onboard-20260718-v4-draft/15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx
1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558 sign-template/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx
HASHES
echo 'VERIFY_CHECK asset_hashes=OK'

unset MYSQL_PWD
trap - EXIT
rm -f -- "$INDEX_BODY" "$CODE_BODY"
echo "POSTDEPLOY_OK release=$RELEASE_ROOT commit=$EXPECTED_COMMIT services=10 migrations=15 entities=4 seals=4 templates=9 plans=6 enabled_versions=6 assets=13"
