"use strict"

const assert = require("assert")
const childProcess = require("child_process")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const root = path.resolve(uiRoot, "..")
const manifestPath = path.join(
  root,
  "scripts/mobile-reimbursement-drive-release-20260731.json"
)
const listPath = path.join(
  root,
  "scripts/mobile-reimbursement-drive-release-files-20260731.list"
)
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"))
const entries = fs.readFileSync(listPath, "utf8")
  .split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))

assert.strictEqual(manifest.releaseId, "mobile-reimbursement-drive-20260731")
assert.strictEqual(manifest.status, "development")
assert.strictEqual(manifest.productionDeploymentAuthorized, false)
assert.strictEqual(manifest.sourceFileCount, entries.length)
assert.strictEqual(new Set(entries).size, entries.length)

entries.forEach(relative => {
  assert.ok(
    fs.statSync(path.join(root, relative)).isFile(),
    `${relative} must exist as a regular release source file`
  )
})

const tracked = new Set(
  childProcess.execFileSync(
    "git",
    ["-C", root, "ls-files", "--cached"],
    { encoding: "utf8" }
  ).split(/\r?\n/).filter(Boolean)
)
const untracked = entries.filter(relative => !tracked.has(relative))
assert.ok(
  manifest.status === "development" || untracked.length === 0,
  "only a development source manifest may include files not yet tracked by Git"
)

const requiredOutboxFiles = [
  "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementApprovalStartOutboxController.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaReimbursementApprovalStartOutbox.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaReimbursementApprovalStartOutboxMapper.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementApprovalStartDispatcher.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementApprovalStartOutboxService.java",
  "erp-modules/erp-oa/src/main/resources/mapper/oa/OaReimbursementApprovalStartOutboxMapper.xml",
  "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaReimbursementApprovalStartFlowTest.java",
  "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaReimbursementApprovalStartOutboxServiceTest.java",
  "erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java",
  "erp-modules/erp-inventory/src/test/resources/new-business-it-baseline.sql",
  "scripts/verify-new-business-it-contract.sh"
]
requiredOutboxFiles.forEach(relative => {
  assert.ok(entries.includes(relative), `${relative} must be in the release scope`)
})

const migrationPaths = [
  manifest.migration.canonical,
  ...manifest.migration.copies
]
const migrations = migrationPaths.map(relative =>
  fs.readFileSync(path.join(root, relative))
)
assert.ok(
  migrations.slice(1).every(value => value.equals(migrations[0])),
  "all reimbursement migration copies must remain byte-identical"
)
const migrationSql = migrations[0].toString("utf8")
;[
  "CREATE TABLE IF NOT EXISTS oa_reimbursement_approval_start_outbox",
  "oa:reimbursement:approvalStartOutbox:list",
  "oa:reimbursement:approvalStartOutbox:replay"
].forEach(requiredSql => {
  assert.ok(
    migrationSql.includes(requiredSql),
    `reimbursement migration must contain ${requiredSql}`
  )
})

const bootstrap = fs.readFileSync(
  path.join(root, "docker/mysql/bootstrap-files.list"),
  "utf8"
)
const bootstrapEntries = bootstrap.split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))
assert.deepStrictEqual(manifest.migration.prerequisites, [
  "sql/erp_hr_dept_leader_identity_20260713.sql",
  "sql/erp_unified_approval_schema_20260716.sql",
  "sql/erp_unified_approval_seed_20260716.sql"
])
manifest.migration.prerequisites.forEach(relative => {
  const prerequisite = path.basename(relative)
  assert.ok(
    fs.statSync(path.join(root, relative)).isFile(),
    `${relative} must exist as a migration prerequisite`
  )
  assert.ok(
    bootstrapEntries.indexOf(prerequisite) <
      bootstrapEntries.indexOf(path.basename(manifest.migration.canonical)),
    `${prerequisite} must precede reimbursement in the bootstrap order`
  )
})
assert.strictEqual(
  bootstrap.split(/\r?\n/).filter(
    line => line === "erp_oa_reimbursement_20260730.sql"
  ).length,
  1,
  "the fresh-database bootstrap must include reimbursement exactly once"
)

const validator = path.join(
  root,
  "scripts/verify-mobile-reimbursement-drive-release.sh"
)
childProcess.execFileSync("bash", [validator, "--source"], {
  cwd: root,
  encoding: "utf8"
})

const worktreeDirty = childProcess.execFileSync(
  "git",
  ["-C", root, "status", "--porcelain=v1", "--untracked-files=all", "--"],
  { encoding: "utf8" }
).trim().length > 0
const candidate = childProcess.spawnSync("bash", [validator, "--candidate"], {
  cwd: root,
  encoding: "utf8"
})
if (untracked.length > 0 || worktreeDirty) {
  assert.notStrictEqual(
    candidate.status,
    0,
    "candidate precheck must reject untracked release files or a dirty worktree"
  )
} else {
  assert.strictEqual(
    candidate.status,
    0,
    `candidate precheck unexpectedly failed: ${candidate.stderr}`
  )
}

console.log("mobile reimbursement/drive release contract tests passed")
