const assert = require("assert")
const crypto = require("crypto")
const fs = require("fs")
const path = require("path")
const { execFileSync, spawnSync } = require("child_process")

const root = path.resolve(__dirname, "../..")
const manifestPath = path.join(root, "scripts/inventory-transfer-audit-release-20260807.json")
const sourceListPath = path.join(root, "scripts/inventory-transfer-audit-release-files-20260807.list")
const migrationListPath = path.join(root, "scripts/inventory-transfer-audit-migrations-20260807.list")
const verifier = path.join(root, "scripts/verify-inventory-transfer-audit-release.sh")
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"))
const entries = fs.readFileSync(sourceListPath, "utf8").split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))
const migrations = fs.readFileSync(migrationListPath, "utf8").split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))

assert.strictEqual(manifest.schemaVersion, 1)
assert.strictEqual(manifest.releaseId, "inventory-transfer-audit-20260807")
assert.strictEqual(manifest.baselineCommit, "b408b7054a481fac692878fcdc24e7ea5bc9cc68")
assert.strictEqual(manifest.workingTreeReproducible, false)
assert.strictEqual(manifest.sourceFileCount, entries.length)
assert.strictEqual(new Set(entries).size, entries.length)
assert.strictEqual(manifest.migrationCount, 1)
assert.deepStrictEqual(migrations, ["erp_inventory_transfer_audit_identity_20260807.sql"])
assert.strictEqual(manifest.migrations[0].sha256, "666c2c7bf388839eab3d13534a4c25b758206c085e01e1feab9ede44b6f45151")
assert.deepStrictEqual(manifest.migrations[0].creates, [])
assert.ok(manifest.migrations[0].backupTables.includes("inv_transfer_order"))
assert.ok(entries.includes("scripts/inventory-transfer-audit-release-20260807.json"))
assert.ok(entries.includes("erp-ui/src/views/inventory/transfer/transferOrgHierarchy.js"))
assert.ok(entries.includes("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java"))

const bootstrap = fs.readFileSync(path.join(root, "docker/mysql/bootstrap-files.list"), "utf8")
const bootstrapMatches = bootstrap.match(/^erp_inventory_transfer_audit_identity_20260807\.sql$/gm) || []
assert.strictEqual(bootstrapMatches.length, 1)
assert.ok(bootstrap.indexOf("erp_inventory_stock_log_business_type_20260804.sql") < bootstrap.indexOf("erp_inventory_transfer_audit_identity_20260807.sql"))

const migrationPaths = [
  path.join(root, "sql/erp_inventory_transfer_audit_identity_20260807.sql"),
  path.join(root, "docker/mysql/db/erp_inventory_transfer_audit_identity_20260807.sql")
]
const ignoredReleaseMigrationPath = path.join(
  root,
  "docker/mysql/releases/inventory-transfer-audit-20260807/erp_inventory_transfer_audit_identity_20260807.sql"
)
if (fs.existsSync(ignoredReleaseMigrationPath)) migrationPaths.push(ignoredReleaseMigrationPath)
const migrationBytes = migrationPaths.map(file => fs.readFileSync(file))
assert.ok(migrationBytes.every(bytes => bytes.equals(migrationBytes[0])))
assert.strictEqual(crypto.createHash("sha256").update(migrationBytes[0]).digest("hex"), manifest.migrations[0].sha256)
assert.ok(!migrationBytes[0].toString("utf8").match(/\b(DROP|TRUNCATE|DELETE|CREATE\s+PROCEDURE)\b/i))

execFileSync("bash", ["-n", verifier], { cwd: root, stdio: "pipe" })
const verifierText = fs.readFileSync(verifier, "utf8")
assert.match(verifierText, /sha256\(path\) != item\["sha256"\]/,
  "the immutable historical verifier must retain content-hash enforcement")
const sourceGate = spawnSync("bash", [verifier, "--source"], {
  cwd: root,
  encoding: "utf8"
})
const sourceGateOutput = `${sourceGate.stdout}${sourceGate.stderr}`
if (sourceGate.status === 0) {
  assert.match(sourceGateOutput, /INVENTORY_TRANSFER_AUDIT_RELEASE_SOURCE_OK files=\d+ migrations=1/)
  assert.match(sourceGateOutput, /INVENTORY_TRANSFER_AUDIT_RELEASE_STATIC_OK/)
} else {
  // This manifest intentionally attests a dirty candidate that cannot be reconstructed from
  // its baseline commit. A later integration branch must be rejected, not made to impersonate it.
  assert.match(sourceGateOutput, /\[FAIL\] (?:source hash|git status) drift: /)
}

console.log(`inventory transfer audit release contract passed (files=${entries.length}, migrations=${migrations.length})`)
