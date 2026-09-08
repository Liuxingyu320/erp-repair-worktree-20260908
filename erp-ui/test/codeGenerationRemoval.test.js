const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readRepo = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), "utf8")

for (const relativePath of [
  "erp-modules/erp-gen",
  "erp-ui/src/api/tool/gen.js",
  "erp-ui/src/views/tool/gen",
  "erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/GenConstants.java",
  "docker/erp/modules/gen"
]) {
  assert.ok(!fs.existsSync(path.join(repoRoot, relativePath)), `${relativePath} must remain removed`)
}

assert.ok(
  fs.existsSync(path.join(uiRoot, "src/views/tool/build/index.vue")),
  "form builder must remain available"
)

const modulesPom = readRepo("erp-modules/pom.xml")
const gatewayConfig = readRepo("erp-gateway/src/main/resources/bootstrap.yml")
const routerSource = readRepo("erp-ui/src/router/index.js")
const composeSource = readRepo("docker/docker-compose.yml")
const ecsComposeSource = readRepo("docker/docker-compose.ecs-host.yml")
const releaseContractSource = readRepo("scripts/release/release-contract.json")
const productionPreflightSource = readRepo("docker/preflight-ecs.sh")
const productionHealthcheckSource = readRepo("docker/healthcheck-ecs.sh")
const nacosPreflightSource = readRepo("docker/nacos-production-preflight.py")
const bootstrapList = readRepo("docker/mysql/bootstrap-files.list")
const migrationSource = readRepo("sql/erp_remove_code_generation_20260728.sql")
const packagedMigrationSource = readRepo("docker/mysql/db/erp_remove_code_generation_20260728.sql")

assert.ok(!modulesPom.includes("<module>erp-gen</module>"))
assert.ok(!gatewayConfig.includes("id: erp-gen") && !gatewayConfig.includes("Path=/code/**"))
assert.ok(!routerSource.includes("/tool/gen-edit") && !routerSource.includes("tool:gen:"))
assert.ok(!composeSource.includes("erp-modules-gen"))
assert.ok(!ecsComposeSource.includes("erp-modules-gen"))
assert.ok(!releaseContractSource.includes("erp-modules-gen") && !releaseContractSource.includes('"component": "gen"'))
assert.ok(!productionPreflightSource.includes("erp-modules-gen") && !productionPreflightSource.includes("modules/gen"))
assert.ok(!productionHealthcheckSource.includes("erp-modules-gen") && !productionHealthcheckSource.includes("gen:9202"))
assert.ok(!nacosPreflightSource.includes("erp-gen-prod.yml"))
assert.ok(bootstrapList.includes("erp_remove_code_generation_20260728.sql"))
assert.strictEqual(packagedMigrationSource, migrationSource)
assert.ok(migrationSource.includes("DELETE role_menu") && migrationSource.includes("DELETE FROM sys_menu"))

console.log("code generation removal tests passed")
