const assert = require("assert")
const crypto = require("crypto")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const read = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), "utf8")
const releaseListPath = path.join(repoRoot, "scripts/new-business-migrations-20260713.list")
const manifest = JSON.parse(read("scripts/new-business-release-20260714.json"))
const releaseContract = JSON.parse(read("scripts/release/release-contract.json"))
const runner = read("scripts/verify-new-business-migrations.sh")
const totalGate = read("scripts/verify-new-business-release.sh")
const bootstrapList = read("docker/mysql/bootstrap-files.list")
  .split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))
const unsafeAutomaticMigrations = [
  "erp_unified_approval_center_20260714.sql",
  "erp_inventory_unified_approval_cutover_20260714.sql",
  "erp_oa_flowable_test_data_purge_20260714.sql"
]
const dependencies = new Map([
  ["erp_hr_health_certificate_unified_approval_20260714.sql", [
    "erp_hr_health_certificate_20260713.sql"
  ]],
  ["erp_unified_approval_seed_20260716.sql", [
    "erp_hr_health_certificate_unified_approval_20260714.sql",
    "erp_inventory_unified_approval_expand_20260716.sql",
    "erp_oa_purchase_unified_approval_expand_20260716.sql",
    "erp_unified_approval_schema_20260716.sql"
  ]],
  ["erp_inventory_transfer_approval_start_outbox_20260716.sql", [
    "erp_inventory_unified_approval_expand_20260716.sql",
    "erp_unified_approval_seed_20260716.sql"
  ]],
  ["erp_inventory_stock_check_approval_start_outbox_20260716.sql", [
    "erp_inventory_unified_approval_expand_20260716.sql",
    "erp_unified_approval_seed_20260716.sql"
  ]],
  ["erp_oa_purchase_approval_start_outbox_20260716.sql", [
    "erp_oa_purchase_unified_approval_expand_20260716.sql",
    "erp_unified_approval_seed_20260716.sql"
  ]],
  ["erp_hr_health_certificate_approval_start_outbox_20260716.sql", [
    "erp_hr_health_certificate_unified_approval_20260714.sql",
    "erp_unified_approval_seed_20260716.sql"
  ]]
])
const featureFlags = new Map([
  ["erp_hr_health_certificate_20260713.sql", ["feature.hr.health-certificate.enabled"]],
  ["erp_inventory_store_return_20260713.sql", ["feature.inventory.store-return.enabled"]],
  ["erp_inventory_transfer_discrepancy_20260713.sql", ["feature.inventory.transfer-discrepancy.enabled"]],
  ["erp_inventory_customer_service_card_20260713.sql", ["feature.inventory.customer-service-card.enabled"]],
  ["erp_inventory_unified_approval_expand_20260716.sql", [
    "feature.inventory.stock-check-native-approval.enabled",
    "feature.inventory.transfer-native-approval.enabled"
  ]],
  ["erp_unified_approval_seed_20260716.sql", ["feature.oa.purchase.enabled"]]
])

const releaseList = fs.readFileSync(releaseListPath, "utf8")
  .split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))
const manifestMigrations = manifest.migrations.map(item => item.file)

assert.strictEqual(manifest.migrationCount, 15, "new-business automatic release must contain exactly 15 active migrations")
assert.strictEqual(manifest.migrations.length, manifest.migrationCount, "manifest migrationCount must match its entries")
assert.deepStrictEqual(releaseList, manifestMigrations, "release list order must be owned by the signed manifest")
assert.strictEqual(new Set(releaseList).size, releaseList.length, "migration release list must not contain duplicates")

const positions = new Map(releaseList.map((file, index) => [file, index]))
dependencies.forEach((required, migration) => {
  assert.ok(positions.has(migration), `required automatic migration is missing: ${migration}`)
  required.forEach(dependency => {
    assert.ok(positions.has(dependency), `${migration} dependency closure is missing ${dependency}`)
    assert.ok(positions.get(dependency) < positions.get(migration), `${dependency} must precede ${migration}`)
  })
})
unsafeAutomaticMigrations.forEach(file => {
  assert.ok(!positions.has(file), `${file} is unsafe and must not enter the automatic release`)
  assert.ok(!bootstrapList.includes(file), `${file} is unsafe and must not enter bootstrap`)
})

function sha256(buffer) {
  return crypto.createHash("sha256").update(buffer).digest("hex")
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

manifest.migrations.forEach(item => {
  const source = fs.readFileSync(path.join(repoRoot, manifest.sourceDirectory, item.file))
  const bootstrap = fs.readFileSync(path.join(repoRoot, "docker/mysql/db", item.file))
  assert.ok(source.equals(bootstrap), `${item.file} source and Docker bootstrap copy must be byte-identical`)
  assert.strictEqual(sha256(source), item.sha256, `${item.file} must match its manifest sha256`)

  const text = source.toString("utf8")
  ;(featureFlags.get(item.file) || []).forEach(key => {
    const escapedKey = escapeRegExp(key)
    assert.ok(
      new RegExp(`SELECT[\\s\\S]{0,500}'${escapedKey}'[\\s\\S]{0,240}'false'`).test(text),
      `${key} must be inserted disabled by ${item.file}`
    )
    assert.ok(
      !new RegExp(`SELECT[\\s\\S]{0,500}'${escapedKey}'[\\s\\S]{0,240}'true'`).test(text),
      `${key} must never default to true`
    )
    assert.ok(manifest.featureFlags.includes(key), `${key} must be declared in the release manifest`)
  })
})

assert.ok(
  releaseContract.forbiddenDeploymentDirectories.includes("mysql/releases"),
  "generated SQL release copies must be excluded from immutable release inputs"
)
assert.ok(
  !fs.existsSync(path.join(repoRoot, manifest.deployDirectory)),
  "generated SQL release copies must not be committed beside Docker source inputs"
)

const approvalSchema = read("sql/erp_unified_approval_schema_20260716.sql")
const approvalSeed = read("sql/erp_unified_approval_seed_20260716.sql")
const approvalTables = [...approvalSchema.matchAll(/CREATE TABLE IF NOT EXISTS\s+(approval_[a-z0-9_]+)/gi)]
  .map(match => match[1])
assert.strictEqual(new Set(approvalTables).size, 12, "schema migration must create the 12 approval tables")
assert.ok(!/INSERT\s+INTO\s+approval_(?:template|rule|rule_version|version_node)/i.test(approvalSchema),
  "schema migration must not publish or seed approval definitions")
assert.ok(!/INSERT\s+INTO\s+sys_config/i.test(approvalSchema), "schema migration must not enable business flags")
assert.ok(/SELECT 'INV_TRANSFER',[\s\S]{0,240}'LEGACY'/i.test(approvalSeed), "transfer template must seed on LEGACY")
assert.ok(/SELECT 'INV_STOCK_CHECK',[\s\S]{0,240}'LEGACY'/i.test(approvalSeed), "stock-check template must seed on LEGACY")
assert.ok(/SELECT @stock_check_template_id,[\s\S]{0,220}'DRAFT'/i.test(approvalSeed), "stock-check rule must seed non-ACTIVE")
assert.ok(/SELECT @transfer_template_id,[\s\S]{0,220}'DRAFT'/i.test(approvalSeed), "transfer rule must seed non-ACTIVE")
assert.ok(!/SET\s+config_value\s*=\s*'true'/i.test(approvalSeed), "automatic seed must remain fail-closed")

assert.ok(runner.includes("set -Eeuo pipefail"), "release runner must fail closed")
assert.ok(runner.includes("GET_LOCK") && runner.includes("RELEASE_LOCK"), "release runner must serialize the full migration set")
assert.ok(runner.includes("cmp -s"), "release runner must reject source/deployment drift")
assert.ok(runner.includes("ERP_NEW_BUSINESS_MYSQL_DATABASE is required"), "database apply must require an explicit target")
assert.ok(totalGate.includes('"$ROOT_DIR/scripts/verify-unified-approval-cutover.sh"'),
  "the total release gate must invoke the unified approval cutover gate")
assert.ok(totalGate.indexOf("verify-unified-approval-cutover.sh") > totalGate.indexOf("verify-new-business-migrations.sh"),
  "unified approval validation must run after the canonical migration contract")

const salesDeliveryMigration = "erp_inventory_sales_delivery_transfer_consistency_20260805.sql"
const salesDeliverySource = fs.readFileSync(path.join(repoRoot, "sql", salesDeliveryMigration))
const salesDeliveryBootstrap = fs.readFileSync(path.join(repoRoot, "docker/mysql/db", salesDeliveryMigration))
const salesDeliverySql = salesDeliverySource.toString("utf8")
assert.ok(salesDeliverySource.equals(salesDeliveryBootstrap),
  "sales-delivery source and Docker bootstrap migration must be byte-identical")
assert.strictEqual(bootstrapList.filter(file => file === salesDeliveryMigration).length, 1,
  "sales-delivery migration must appear exactly once in bootstrap")
assert.ok(!/ADD\s+(?:COLUMN|UNIQUE\s+KEY)\s+IF\s+NOT\s+EXISTS/i.test(salesDeliverySql),
  "sales-delivery migration must remain MySQL 5.7 compatible")
assert.ok(!/CREATE\s+PROCEDURE\s+IF\s+NOT\s+EXISTS/i.test(salesDeliverySql),
  "sales-delivery migration must not use unsupported CREATE PROCEDURE IF NOT EXISTS")
assert.ok(/SIGNAL\s+SQLSTATE\s+'45000'/i.test(salesDeliverySql),
  "unknown historical warehouse/cost and duplicate sources must block release")
assert.ok(/ALTER\s+TABLE\s+inv_sales_order\s+ADD\s+COLUMN\s+target_dept_id/i.test(salesDeliverySql),
  "sales-delivery migration must create its target-department prerequisite before history checks")
assert.ok(/GENERATED\s+ALWAYS[\s\S]+sales_delivery_notice[\s\S]+UNIQUE\s+KEY/i.test(salesDeliverySql),
  "sales-delivery dedupe must use a selective generated-column unique key")
assert.ok(/SET\s+delivered_cost_amount\s*=\s*0\.00[\s\S]{0,180}COALESCE\(delivered_qty,\s*0\)\s*=\s*0/i.test(salesDeliverySql),
  "only never-shipped notice rows may initialize delivered cost to zero")
assert.ok(/source_business_type\s*=\s*'sales_delivery'[\s\S]{0,160}purchase_id\s*=\s*source_business_id/i.test(salesDeliverySql),
  "legacy purchase_id cleanup must require proof that it contains the old sales source id")

const discrepancyDispositionMigration = "erp_inventory_transfer_discrepancy_disposition_20260805.sql"
const discrepancyDispositionSource = fs.readFileSync(path.join(repoRoot, "sql", discrepancyDispositionMigration))
const discrepancyDispositionBootstrap = fs.readFileSync(path.join(repoRoot, "docker/mysql/db", discrepancyDispositionMigration))
const discrepancyDispositionSql = discrepancyDispositionSource.toString("utf8")
assert.ok(discrepancyDispositionSource.equals(discrepancyDispositionBootstrap),
  "discrepancy disposition source and Docker bootstrap migration must be byte-identical")
assert.strictEqual(bootstrapList.filter(file => file === discrepancyDispositionMigration).length, 1,
  "discrepancy disposition migration must appear exactly once in bootstrap")
assert.ok(discrepancyDispositionSql.includes("CREATE TABLE IF NOT EXISTS inv_transfer_discrepancy_disposition"),
  "discrepancy disposition migration must create the cost ledger")
assert.ok(discrepancyDispositionSql.includes("uk_inv_transfer_disposition_request_category"),
  "discrepancy disposition migration must enforce request/detail/category idempotency")
assert.ok(!/ADD\s+(?:COLUMN|UNIQUE\s+KEY)\s+IF\s+NOT\s+EXISTS/i.test(discrepancyDispositionSql),
  "discrepancy disposition migration must remain MySQL 5.7 compatible")

console.log("newBusinessMigrationRelease tests passed")
