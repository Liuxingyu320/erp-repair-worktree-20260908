const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

const runnerSource = fs.readFileSync(path.join(uiRoot, "scripts/run-node-tests.cjs"), "utf8")

assert.ok(
  runnerSource.includes("const duplicatePattern = / [2-9][0-9]*\\.js$/") &&
    runnerSource.includes("filter(file => duplicatePattern.test(file))"),
  "the frontend test gate should reject every numbered copied JavaScript file"
)

;[
  "sql/erp_oa_labor_contract_20260612 2.sql",
  "sql/security_admin_privilege_audit_20260613 2.sql",
  "docker/mysql/db/erp_oa_labor_contract_20260612 2.sql",
  "docker/mysql/db/security_admin_privilege_audit_20260613 2.sql"
].forEach(relativePath => {
  assert.ok(!fs.existsSync(path.join(repoRoot, relativePath)), `${relativePath} should not exist as a numbered deployment copy`)
})

console.log("contractAutomationBaseline tests passed")
