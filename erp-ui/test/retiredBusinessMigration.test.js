const assert = require("assert")
const fs = require("fs")
const path = require("path")

const repoRoot = path.resolve(__dirname, "../..")
const migrationName = "erp_retire_hr_team_oe_replenishment_20260730.sql"
const migration = fs.readFileSync(path.join(repoRoot, "sql", migrationName), "utf8")
const mirror = fs.readFileSync(path.join(repoRoot, "docker/mysql/db", migrationName), "utf8")
const bootstrap = fs.readFileSync(path.join(repoRoot, "docker/mysql/bootstrap-files.list"), "utf8")
const executableSql = migration
  .split(/\r?\n/)
  .filter(line => !line.trim().startsWith("--"))
  .join("\n")

const retiredRuntimePaths = [
  "erp-ui/src/api/hr/team.js",
  "erp-ui/src/api/inventory/oeReplenishment.js",
  "erp-ui/src/views/hr/team/index.vue",
  "erp-ui/src/views/inventory/oeReplenishment/index.vue",
  "erp-ui/src/views/mobile/hr/team/index.vue",
  "erp-ui/src/views/mobile/inventory/oeReplenishment/index.vue",
  "erp-api/erp-api-inventory/pom.xml",
  "erp-api/erp-api-inventory/src/main/java/com/erp/inventory/api/RemoteOeReplenishmentService.java",
  "erp-api/erp-api-inventory/src/main/java/com/erp/inventory/api/domain/OeReplenishmentEvent.java",
  "erp-api/erp-api-inventory/src/main/java/com/erp/inventory/api/factory/RemoteOeReplenishmentFallbackFactory.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvOeReplenishmentDemand.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvOeReplenishmentDemandMapper.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvOeTransferCreationService.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaFixedAssetOutboxController.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetReplenishmentDispatcher.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetReplenishmentOutboxService.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetReplenishmentOutboxWriter.java"
]

retiredRuntimePaths.forEach(relativePath => {
  assert.ok(!fs.existsSync(path.join(repoRoot, relativePath)), `retired runtime file must stay deleted: ${relativePath}`)
})

const retiredActivationMigrations = [
  "erp_hr_team_permission_20260713.sql",
  "erp_inventory_oe_replenishment_20260713.sql",
  "erp_oa_fixed_asset_replenishment_outbox_20260713.sql"
]
const activeReleaseList = fs.readFileSync(path.join(repoRoot, "scripts/new-business-migrations-20260713.list"), "utf8")
const activeReleaseManifest = JSON.parse(fs.readFileSync(path.join(repoRoot, "scripts/new-business-release-20260714.json"), "utf8"))

retiredActivationMigrations.forEach(file => {
  assert.ok(fs.existsSync(path.join(repoRoot, "sql", file)), `historical migration must be retained: ${file}`)
  assert.ok(!fs.existsSync(path.join(repoRoot, "docker/mysql/db", file)), `retired migration must not enter fresh bootstrap: ${file}`)
  assert.ok(!bootstrap.split(/\r?\n/).includes(file), `retired migration must not be listed in bootstrap: ${file}`)
  assert.ok(!activeReleaseList.split(/\r?\n/).includes(file), `retired migration must not be automatically released: ${file}`)
  assert.ok(!activeReleaseManifest.migrations.some(item => item.file === file), `retired migration must not be declared active: ${file}`)
})

assert.strictEqual(mirror, migration, "Docker bootstrap migration must be byte-identical to root SQL")
assert.ok(
  bootstrap.split(/\r?\n/).includes(migrationName),
  "retirement migration must be included in fresh-database bootstrap"
)

;[
  "feature.hr.team.enabled",
  "feature.inventory.oe-replenishment.enabled",
  "feature.oa.oe-replenishment.enabled",
  "hr:team:list",
  "inv:oeReplenishment:list",
  "oa:fixedAsset:outbox:list"
].forEach(retiredKey => {
  assert.ok(migration.includes(retiredKey), `migration must retire ${retiredKey}`)
})

assert.ok(
  migration.includes("information_schema.tables") &&
    migration.includes("PREPARE erp_retire_stmt") &&
    migration.includes("status = ''DEAD''") &&
    migration.includes("last_error_code = ''FEATURE_RETIRED''") &&
    migration.includes("status = ''CLOSED''"),
  "migration must conditionally archive unfinished outbox and demand rows"
)

assert.ok(
  !/\b(drop|truncate)\s+(table\s+)?(oa_fixed_asset_replenishment_outbox|inv_oe_replenishment_demand|inv_oe_replenishment_transfer)\b/i.test(executableSql) &&
    !/\bdelete\s+from\s+(oa_fixed_asset_replenishment_outbox|inv_oe_replenishment_demand|inv_oe_replenishment_transfer)\b/i.test(executableSql),
  "retirement migration must never delete historical OE replenishment tables or business rows"
)

console.log("retired business migration checks passed")
