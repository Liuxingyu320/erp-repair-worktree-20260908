const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const sql = fs.readFileSync(path.join(repoRoot, "scripts/new-business-permission-readiness.sql"), "utf8")
const runner = fs.readFileSync(path.join(repoRoot, "scripts/verify-new-business-readiness.sh"), "utf8")
const inventoryBootstrap = fs.readFileSync(path.join(repoRoot, "erp-modules/erp-inventory/src/main/resources/bootstrap.yml"), "utf8")

const mutationLine = sql.split(/\r?\n/).find(line =>
  /^\s*(insert|update|delete|replace|alter|create|drop|truncate|call|set)\s/i.test(line)
)
assert.strictEqual(mutationLine, undefined, "permission readiness report must remain read-only")

const expectedSections = [
  "PERMISSION_OBJECT_NOT_READY",
  "STORE_EMPLOYEE_MISSING_PERMISSION",
  "STORE_LEADER_MISSING_PERMISSION",
  "WAREHOUSE_EMPLOYEE_MISSING_PERMISSION",
  "HIGH_RISK_ROLE_GRANT_REVIEW",
  "OE_MASTER_DATA_NOT_READY",
  "LEADER_IDENTITY_NOT_READY"
]
expectedSections.forEach(section => {
  assert.ok(sql.includes(section), `readiness report must include ${section}`)
})

assert.ok(sql.includes("inv:customerCard:record:add"), "store employee matrix must cover mobile service records")
assert.ok(sql.includes("hr:healthCertificate:remind"), "store leader matrix must cover health reminders")
assert.ok(
  !sql.includes("hr:team") &&
    !sql.includes("oeReplenishment") &&
    !sql.includes("fixedAsset:outbox"),
  "readiness matrix must not include retired HR team or standalone OE replenishment permissions"
)
assert.ok(sql.includes("purchase_reference_url"), "OE master-data readiness must validate the purchase link")
assert.ok(sql.includes("leader_user_id"), "readiness must use stable leader identities")

assert.ok(runner.includes("INVENTORY_OE_PURCHASE_REFERENCE_ALLOWED_HOSTS is required"), "runner must require a trusted-host deployment value")
assert.ok(runner.includes("ERP_NEW_BUSINESS_MYSQL_DATABASE is required"), "runner must require an explicit database")
assert.ok(runner.includes("every returned readiness row requires remediation"), "runner must not silently auto-grant permissions")
assert.ok(inventoryBootstrap.includes("allowed-hosts: ${INVENTORY_OE_PURCHASE_REFERENCE_ALLOWED_HOSTS:}"), "inventory deployment must expose the trusted-host environment contract")

console.log("newBusinessReadiness tests passed")
