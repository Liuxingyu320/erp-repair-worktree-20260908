#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

expected_it_files=(
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionIT.java"
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionIT.java"
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionIT.java"
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57IT.java"
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrHealthCertificateMySqlIT.java"
  "erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java"
  "erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceLeaveBalanceMySqlIT.java"
  "erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceOvertimeTransferMySqlIT.java"
  "erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceLeaveQuotaMySqlIT.java"
)

renamed_it_files=(
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionTest.java:erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionIT.java"
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java:erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionIT.java"
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionTest.java:erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionIT.java"
  "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57Test.java:erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57IT.java"
  "erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceLeaveBalanceMysqlTest.java:erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceLeaveBalanceMySqlIT.java"
  "erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceOvertimeTransferMysqlTest.java:erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceOvertimeTransferMySqlIT.java"
  "erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceLeaveQuotaMysqlTest.java:erp-modules/erp-oa/src/test/java/com/erp/oa/attendance/leave/balance/AttendanceLeaveQuotaMySqlIT.java"
)

for relative_path in "${expected_it_files[@]}"; do
  file="$ROOT_DIR/$relative_path"
  [[ -f "$file" ]] || {
    echo "[FAIL] required integration test is missing: $relative_path" >&2
    exit 1
  }
  grep -q '@Testcontainers(disabledWithoutDocker = false)' "$file" || {
    echo "[FAIL] integration test must fail when Docker is unavailable: $relative_path" >&2
    exit 1
  }
done

python3 - "$ROOT_DIR/scripts/new-business-release-20260714.json" \
  "${expected_it_files[@]}" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
expected = sys.argv[2:]
actual = manifest.get("integrationTests")
if actual != expected:
    raise SystemExit(
        "[FAIL] release manifest integrationTests differ from the executable IT inventory"
    )
PY

for rename in "${renamed_it_files[@]}"; do
  old_relative="${rename%%:*}"
  new_relative="${rename#*:}"
  [[ ! -e "$ROOT_DIR/$old_relative" ]] || {
    echo "[FAIL] legacy container test still exists with *Test suffix: $old_relative" >&2
    exit 1
  }
  [[ -f "$ROOT_DIR/$new_relative" ]] || {
    echo "[FAIL] renamed integration test is missing: $new_relative" >&2
    exit 1
  }

  # During the uncommitted rename transition, prove that every original test
  # method survived the move. The IT may legitimately add schema setup and
  # assertions while the rename is still uncommitted; exact byte equality
  # would make those safety improvements fail the release gate.
  if git -C "$ROOT_DIR" cat-file -e "HEAD:$old_relative" 2>/dev/null; then
    python3 - "$ROOT_DIR" "$old_relative" "$new_relative" <<'PY'
import subprocess
import sys
import re
from pathlib import Path

root = Path(sys.argv[1])
old_relative = sys.argv[2]
new_relative = sys.argv[3]
old_name = Path(old_relative).stem
new_name = Path(new_relative).stem
old_source = subprocess.check_output(
    ["git", "-C", str(root), "show", f"HEAD:{old_relative}"],
    text=True,
)
new_source = (root / new_relative).read_text(encoding="utf-8")
if f"class {new_name}" not in new_source or f"class {old_name}" in new_source:
    raise SystemExit(
        f"[FAIL] {old_relative} -> {new_relative} did not complete the Java class rename"
    )

test_method_pattern = re.compile(
    r"@Test\b(?:(?!\bvoid\b).)*?\bvoid\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(",
    re.DOTALL,
)
old_tests = set(test_method_pattern.findall(old_source))
new_tests = set(test_method_pattern.findall(new_source))
missing = sorted(old_tests - new_tests)
if not old_tests:
    raise SystemExit(f"[FAIL] no @Test methods found in legacy integration test: {old_relative}")
if missing:
    raise SystemExit(
        f"[FAIL] {old_relative} -> {new_relative} lost test methods: {', '.join(missing)}"
    )
PY
  fi
done

find_fast_unit_tests() {
  find "$ROOT_DIR/erp-modules" \
    -type d -name target -prune -o \
    -path '*/src/test/*' -type f -name '*Test.java' -print0
}

if find_fast_unit_tests \
    | xargs -0 grep -l 'org.testcontainers' >/dev/null 2>&1; then
  echo "[FAIL] container-backed tests must use the *IT.java suffix" >&2
  find_fast_unit_tests \
    | xargs -0 grep -l 'org.testcontainers' >&2
  exit 1
fi

grep -q '<id>new-business-mysql-it</id>' "$ROOT_DIR/pom.xml"
grep -q '<artifactId>maven-failsafe-plugin</artifactId>' "$ROOT_DIR/pom.xml"
grep -q '<include>\*\*/\*IT.java</include>' "$ROOT_DIR/pom.xml"
grep -q '<classesDirectory>${project.build.outputDirectory}</classesDirectory>' \
  "$ROOT_DIR/pom.xml" \
  || { echo '[FAIL] Failsafe must load application classes from compiler output' >&2; exit 1; }

docker_context_helper="$ROOT_DIR/scripts/configure-testcontainers-docker.sh"
[[ -f "$docker_context_helper" ]] \
  || { echo '[FAIL] Testcontainers Docker-context helper is missing' >&2; exit 1; }
grep -q "docker context inspect --format '{{.Endpoints.docker.Host}}'" "$docker_context_helper" \
  || { echo '[FAIL] Testcontainers helper must resolve the active Docker context' >&2; exit 1; }
grep -q 'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock' "$docker_context_helper" \
  || { echo '[FAIL] Testcontainers helper must translate non-default Unix sockets' >&2; exit 1; }
for gate_script in \
  "$ROOT_DIR/scripts/ci/verify.sh" \
  "$ROOT_DIR/scripts/verify-new-business-release.sh"; do
  grep -q 'source .*scripts/configure-testcontainers-docker.sh' "$gate_script" \
    || { echo "[FAIL] MySQL gate does not load the Docker-context helper: $gate_script" >&2; exit 1; }
  grep -q 'erp_configure_testcontainers_docker' "$gate_script" \
    || { echo "[FAIL] MySQL gate does not configure Testcontainers: $gate_script" >&2; exit 1; }
done

migration_it="$ROOT_DIR/erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java"
grep -q 'mysql:5.7.44' "$migration_it" \
  || { echo '[FAIL] migration IT no longer covers MySQL 5.7.44' >&2; exit 1; }
grep -q 'mysql:8.0.36' "$migration_it" \
  || { echo '[FAIL] migration IT no longer covers MySQL 8.0.36' >&2; exit 1; }
grep -q 'hasSize(15)' "$migration_it" \
  || { echo '[FAIL] migration IT must execute all 15 active automatic migrations' >&2; exit 1; }
for required_token in \
  APPROVAL_TABLES APPROVAL_START_OUTBOX_TABLES \
  REIMBURSEMENT_MIGRATION REIMBURSEMENT_PREREQUISITES \
  REIMBURSEMENT_TABLES \
  erp_hr_dept_leader_identity_20260713.sql \
  erp_oa_reimbursement_20260730.sql \
  oa_reimbursement_approval_start_outbox \
  reimbursementMigrationIsRepeatSafeAndOperationsStayAdminOnly \
  oa:reimbursement:approvalStartOutbox:list \
  oa:reimbursement:approvalStartOutbox:replay \
  feature.oa.purchase.enabled \
  feature.inventory.stock-check-native-approval.enabled \
  feature.inventory.transfer-native-approval.enabled \
  remote_business_round \
  healthApprovalAdministratorDisableSurvivesSeedReplay \
  automaticApprovalSeedRemainsFailClosed; do
  grep -q "$required_token" "$migration_it" \
    || { echo "[FAIL] migration IT is missing unified-approval assertion: $required_token" >&2; exit 1; }
done

migration_baseline="$ROOT_DIR/erp-modules/erp-inventory/src/test/resources/new-business-it-baseline.sql"
for required_table in \
  sys_dept sys_user sys_user_profile sys_post sys_user_role sys_user_post \
  sys_user_shop oa_purchase inv_stock_check; do
  grep -q "CREATE TABLE $required_table" "$migration_baseline" \
    || { echo "[FAIL] migration IT baseline is missing table: $required_table" >&2; exit 1; }
done
grep -q 'leader varchar(20) DEFAULT NULL' "$migration_baseline" \
  || { echo '[FAIL] migration IT baseline is missing sys_dept.leader' >&2; exit 1; }

echo "[PASS] fast tests exclude Docker and the fail-closed MySQL integration suite is wired"
