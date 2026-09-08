const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..", "..")
const scriptPath = path.join(root, "scripts/remote_test_data_audit.sh")

assert.ok(fs.existsSync(scriptPath), "remote test data audit script should exist")

const source = fs.readFileSync(scriptPath, "utf8")
const lowerSource = source.toLowerCase()

assert.ok(source.startsWith("#!/usr/bin/env bash"), "audit script should be executable bash")
assert.ok(source.includes("set -euo pipefail"), "audit script should fail fast")
assert.ok(source.includes('DB="${DB:-BossERP_NEW}"'),
  "audit script should default to the active deployment schema while allowing an explicit audited override")
assert.ok(source.includes("/root/.erp-mysql-root-pass"), "audit script should use the existing remote credential file")

;[
  "云岫",
  "青炉",
  "SO-MOBILE",
  "PO-MOBILE",
  "MOBILE-READY",
  "测试",
  "demo",
  "mock",
  "codex",
  "qa_",
  "ux_"
].forEach(token => {
  assert.ok(source.includes(token), `audit script should scan for ${token}`)
})

;[
  "delete ",
  "update ",
  "insert ",
  "truncate ",
  "drop ",
  "alter ",
  "create "
].forEach(keyword => {
  assert.ok(!lowerSource.includes(keyword), `audit script should stay read-only and avoid ${keyword.trim()}`)
})

;[
  "candidate_sys_dept",
  "candidate_sys_user",
  "candidate_inventory_rows",
  "business_reference_counts",
  "backup_table_inventory",
  "information_schema.tables",
  "information_schema.columns",
  "sys_user_shop"
].forEach(token => {
  assert.ok(source.includes(token), `audit script should include ${token}`)
})

console.log("remoteTestDataAuditScript tests passed")
