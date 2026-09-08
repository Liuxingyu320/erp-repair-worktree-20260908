const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")

function requireModule(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), "utf8")
    .replace(/export function /g, "function ")
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(`${source}\nmodule.exports = { plainImportLines, parseLegacyImportResult, parseImportResult, failureCsv, credentialCsv, neutralizeCsvCell }`, sandbox)
  return sandbox.module.exports
}

const { parseLegacyImportResult, parseImportResult, failureCsv, credentialCsv, neutralizeCsvCell } = requireModule("src/utils/importResult.js")
const dialogSource = fs.readFileSync(path.join(root, "src/components/ExcelImportDialog/index.vue"), "utf8")
const result = parseLegacyImportResult("成功导入 2 条<br/>第 3 行：手机号错误<br><script>alert(1)</script>第 4 行：工号重复")

assert.strictEqual(result.successCount, 2)
assert.strictEqual(result.failureCount, 2)
assert.deepStrictEqual(JSON.parse(JSON.stringify(result.failures.map(item => item.line))), [3, 4])
assert.ok(!result.failures.some(item => item.reason.includes("<script>")))
assert.ok(failureCsv(result.failures).includes('3,"","手机号错误"'))
assert.strictEqual(neutralizeCsvCell("=HYPERLINK('x')"), "'=HYPERLINK('x')")
const structured = parseImportResult({ data: {
  committed: true,
  createdCount: 1,
  updatedCount: 1,
  failureCount: 0,
  failures: [],
  temporaryCredentials: [{ userId: 9, userName: '=unsafe', temporaryPassword: 'Abc234!safe', expiresAt: '2026-07-15' }]
} })
assert.strictEqual(structured.successCount, 2)
assert.strictEqual(structured.temporaryCredentials.length, 1)
assert.ok(credentialCsv(structured.temporaryCredentials).includes("'=unsafe"))
assert.ok(!dialogSource.includes("dangerouslyUseHTMLString"))
assert.ok(!dialogSource.includes("v-html"))
assert.ok(dialogSource.includes("parseLegacyImportResult"))
assert.ok(dialogSource.includes("downloadFailures"))
assert.ok(dialogSource.includes("downloadCredentials"))
assert.ok(dialogSource.includes("URL.revokeObjectURL"))

console.log("excelImportDialogSafety tests passed")
