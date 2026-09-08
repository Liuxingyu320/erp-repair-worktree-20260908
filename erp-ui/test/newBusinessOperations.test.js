const assert = require("assert")
const fs = require("fs")
const path = require("path")

const repoRoot = path.resolve(__dirname, "../..")

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8")
}

const fixedAssetApi = read("erp-ui/src/api/oa/fixedAsset.js")
const fixedAssetPage = read("erp-ui/src/views/oa/fixedAsset/config/index.vue")
const fixedAssetService = read("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java")
const systemFeatureGate = read("erp-modules/erp-system/src/main/java/com/erp/system/service/BusinessFeatureGate.java")
const oaFeatureGate = read("erp-modules/erp-oa/src/main/java/com/erp/oa/service/BusinessFeatureGate.java")
const inventoryFeatureGate = read("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/BusinessFeatureGate.java")
const inventoryMetrics = read("erp-modules/erp-inventory/src/main/java/com/erp/inventory/metric/InventoryBusinessMetrics.java")
const oaTodoMapper = read("erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml")
const inventoryTodoMapper = read("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml")
const reconciliation = read("scripts/new-business-daily-reconciliation.sql")
const verifier = read("scripts/verify-new-business-reconciliation.sh")
const reconciliationRunner = read("scripts/run-new-business-reconciliation.sh")

const retiredRuntimeSources = [
  fixedAssetApi,
  fixedAssetPage,
  fixedAssetService,
  systemFeatureGate,
  oaFeatureGate,
  inventoryFeatureGate,
  oaTodoMapper,
  inventoryTodoMapper
].join("\n")

;[
  "feature.hr.team.enabled",
  "feature.oa.oe-replenishment.enabled",
  "feature.inventory.oe-replenishment.enabled",
  "INV_OE_REPLENISHMENT",
  "OA_FIXED_ASSET_REPLENISHMENT_DEAD",
  "replenishment-outbox"
].forEach(retiredReference => {
  assert.ok(
    !retiredRuntimeSources.includes(retiredReference),
    `retired runtime reference must be absent: ${retiredReference}`
  )
})

assert.ok(
  fixedAssetService.includes("insertLedger") &&
    !fixedAssetService.includes("enqueueReplenishment"),
  "fixed asset reporting should keep its ledger record without creating a separate replenishment event"
)

assert.ok(
  inventoryMetrics.includes("erp.inventory.transfer_discrepancy.total") &&
    inventoryMetrics.includes("erp.inventory.customer_card.write.total") &&
    !inventoryMetrics.includes("erp.inventory.oe_demand.transition.total"),
  "inventory metrics should retain live flows and remove the retired OE demand metric"
)
assert.ok(
  inventoryMetrics.includes("Set.of(") &&
    inventoryMetrics.includes("bounded(") &&
    !inventoryMetrics.includes('"user_id"') &&
    !inventoryMetrics.includes('"customer_id"'),
  "business metrics should continue using bounded tags rather than entity identifiers"
)

assert.ok(
  reconciliation.includes("STORE_RETURN_SHIPMENT_QUANTITY_CONSERVATION") &&
    reconciliation.includes("TRANSFER_DISCREPANCY_MASTER_DETAIL_MISMATCH") &&
    reconciliation.includes("CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH") &&
    reconciliation.includes("HEALTH_CERTIFICATE_CURRENT_INVALID") &&
    reconciliation.includes("NEW_BUSINESS_RECONCILIATION_SUMMARY"),
  "daily reconciliation should retain checks for the remaining live business chains"
)
assert.ok(
  !/^[\t ]*(insert|update|delete|replace|alter|create|drop|truncate|call|set|prepare|execute|deallocate|lock|unlock)\s/im.test(reconciliation) &&
    !/^\s*with\s/im.test(reconciliation),
  "daily reconciliation should stay read-only and MySQL 5.7 compatible"
)
assert.ok(
  verifier.includes("--run") &&
    verifier.includes("run-new-business-reconciliation.sh") &&
    reconciliationRunner.includes("SET SESSION TRANSACTION READ ONLY") &&
    reconciliationRunner.includes("ERP_NEW_BUSINESS_MYSQL_DATABASE") &&
    reconciliationRunner.includes("ERP_NEW_BUSINESS_MYSQL_DEFAULTS_FILE") &&
    reconciliationRunner.includes("--defaults-extra-file") &&
    reconciliationRunner.includes("parse_new_business_reconciliation.py"),
  "reconciliation runner should require an explicit run, owner-only credentials, read-only execution, and sanitized output"
)

console.log("retired business features and remaining operations checks passed")
