const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const read = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), "utf8")

const rootPom = read("pom.xml")
const modulesPom = read("erp-modules/pom.xml")
const releaseManifest = JSON.parse(read("scripts/new-business-release-20260714.json"))
const contract = read("scripts/verify-new-business-it-contract.sh")
const dockerContextHelper = read("scripts/configure-testcontainers-docker.sh")
const ciVerify = read("scripts/ci/verify.sh")
const releaseVerify = read("scripts/verify-new-business-release.sh")
const inventoryIt = read("erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java")
const migrationBaseline = read("erp-modules/erp-inventory/src/test/resources/new-business-it-baseline.sql")

assert.ok(rootPom.includes("<id>new-business-mysql-it</id>"), "release MySQL profile must exist")
assert.ok(rootPom.includes("maven-failsafe-plugin"), "release MySQL profile must use Failsafe")
assert.ok(rootPom.includes("<include>**/*IT.java</include>"), "Failsafe must select integration tests")
assert.ok(
  rootPom.includes("<classesDirectory>${project.build.outputDirectory}</classesDirectory>"),
  "Failsafe must load application classes from compiler output instead of the Spring Boot executable JAR"
)
assert.ok(modulesPom.includes("<exclude>**/*IT.java</exclude>"), "default Surefire tests must stay Docker-free")
assert.ok(contract.includes("disabledWithoutDocker = false"), "release gate must reject Docker-less execution")
assert.ok(contract.includes("old_tests - new_tests"), "rename gate must preserve every original integration-test method")
assert.ok(!contract.includes("old_source.replace(old_name, new_name) != new_source"), "rename gate must allow additive IT hardening")
assert.ok(
  contract.includes("find_fast_unit_tests") &&
    contract.includes("-name target -prune"),
  "integration-test classification must skip Maven build outputs instead of scanning generated class trees"
)
assert.ok(
  dockerContextHelper.includes("docker context inspect --format '{{.Endpoints.docker.Host}}'"),
  "Testcontainers must inherit the active Docker CLI context when DOCKER_HOST is unset"
)
assert.ok(
  dockerContextHelper.includes("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock"),
  "non-default Unix sockets must be translated for Testcontainers sidecar mounts"
)
assert.deepStrictEqual(
  releaseManifest.integrationTests,
  [
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionIT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionIT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionIT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57IT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrHealthCertificateMySqlIT.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java"
  ],
  "release manifest must match the six executable fail-closed integration-test classes"
)
for (const [name, gate] of [["CI", ciVerify], ["release", releaseVerify]]) {
  assert.ok(
    gate.includes("source ") &&
      gate.includes("scripts/configure-testcontainers-docker.sh") &&
      gate.includes("erp_configure_testcontainers_docker"),
    `${name} MySQL gate must configure Testcontainers from the Docker context`
  )
}

assert.ok(inventoryIt.includes('mysql:5.7.44'), "migration gate must execute on MySQL 5.7")
assert.ok(inventoryIt.includes('mysql:8.0.36'), "migration gate must execute on MySQL 8.0")
assert.ok(
  inventoryIt.includes("--log-bin-trust-function-creators=1"),
  "migration gate must model the binary-log prerequisite for trigger creation"
)
assert.ok(inventoryIt.includes("new-business-migrations-20260713.list"), "tests and deployment must share one migration order")
assert.ok(inventoryIt.includes("applyEveryMigrationTwice"), "every migration must be tested for repeatability")
assert.ok(inventoryIt.includes("hasSize(15)"), "MySQL 5.7/8.0 must execute all 15 active automatic migrations")
for (const token of [
  "APPROVAL_TABLES", "APPROVAL_START_OUTBOX_TABLES",
  "feature.oa.purchase.enabled",
  "feature.inventory.stock-check-native-approval.enabled",
  "feature.inventory.transfer-native-approval.enabled",
  "automaticApprovalSeedRemainsFailClosed",
  "rule_info.rule_status='ACTIVE'"
]) assert.ok(inventoryIt.includes(token), `migration IT is missing unified-approval assertion: ${token}`)
for (const table of ["sys_dept", "sys_user", "sys_user_profile", "sys_post", "sys_user_role", "sys_user_post", "sys_user_shop", "oa_purchase", "inv_stock_check"]) {
  assert.ok(migrationBaseline.includes(`CREATE TABLE ${table}`), `migration baseline must provide ${table}`)
}
assert.ok(!inventoryIt.includes("demandLockPreventsMultiWarehouseOverPlanning"), "retired OE replenishment concurrency tests must leave the active migration gate")

console.log("newBusinessMysqlGate tests passed")
