const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function read(root, relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8")
}

const page = read(uiRoot, "src/views/oa/signTask/index.vue")
const request = read(uiRoot, "src/utils/request.js")
const controller = read(repoRoot,
  "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java")
const packageMapper = read(repoRoot,
  "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java")
const packageMapperXml = read(repoRoot,
  "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml")
const importRowMapper = read(repoRoot,
  "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignOnboardImportRowMapper.java")
const importRowMapperXml = read(repoRoot,
  "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignOnboardImportRowMapper.xml")
const {
  MAX_SIGN_TASK_EXPORT_ROWS,
  signTaskExportBlockReason
} = require("../src/views/oa/signTask/signTaskExportPolicy")

assert.strictEqual(MAX_SIGN_TASK_EXPORT_ROWS, 500)
assert.strictEqual(signTaskExportBlockReason(500), "")
assert.ok(signTaskExportBlockReason(501).includes("共 501 条"))
assert.ok(signTaskExportBlockReason(501).includes("最多导出 500 条"))
assert.ok(signTaskExportBlockReason(0).includes("没有可导出"))

assert.ok(page.includes(">导出签约数据</el-button>"))
assert.ok(page.includes("signTaskExportBlockReason(this.total)"))
assert.ok(page.includes("export-limit-warning"))
assert.ok(page.includes("'/oa/signTask/export'"))
assert.ok(page.includes("{ ...this.queryParams }"))
assert.ok(!page.includes("oa:signTask:export"))

assert.ok(request.includes('import { blobValidate, tansParams } from "@/utils/common"'))
assert.ok(request.includes("const isBlob = blobValidate(data)"))

assert.ok(controller.includes('"oa:signTask:list", "oa:signTask:technicalEvidence"'))
assert.ok(controller.includes('@PostMapping("/export")'))
assert.ok(packageMapper.includes("selectOaSignPackagesByIds"))
assert.ok(packageMapperXml.includes('id="selectOaSignPackagesByIds"'))
assert.ok(importRowMapper.includes("selectByTaskIds"))
assert.ok(importRowMapperXml.includes('id="selectByTaskIds"'))

console.log("sign-task export release contract tests passed")
