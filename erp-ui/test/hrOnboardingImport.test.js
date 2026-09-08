const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const dialogPath = path.join(root, "src/views/hr/onboarding/components/HrOnboardingImportDialog.vue")
const onboardingPath = path.join(root, "src/views/hr/onboarding/index.vue")
const importExportPath = path.join(root, "src/views/hr/importExport/index.vue")

assert.ok(fs.existsSync(dialogPath), "the dedicated preview-first onboarding import dialog must exist")

const dialog = fs.readFileSync(dialogPath, "utf8")
const onboarding = fs.readFileSync(onboardingPath, "utf8")
const importExport = fs.readFileSync(importExportPath, "utf8")

for (const fragment of [
  "previewHrOnboardingImport", "confirmHrOnboardingImport", "batchId", "version", "rowId",
  "IMPORTABLE", "WARNING", "INVALID", "POSSIBLE_DUPLICATE", "BINDABLE_ACCOUNT",
  "decision", "bindUserId", "downloadHrOnboardingTemplate", "downloadHrOnboardingErrorRows"
]) assert.ok(dialog.includes(fragment), `import dialog must include ${fragment}`)

assert.ok(dialog.includes("new FormData()"), "preview must submit multipart FormData")
assert.ok(dialog.includes("plainText"), "all server-provided row messages must use text-only rendering")
assert.ok(dialog.includes(':before-close="beforeClose"'), "dialog X must respect in-flight request lifecycle")
assert.ok(dialog.includes(':close-on-press-escape="!requestInFlight"'), "Esc must not hide in-flight import context")
assert.ok(dialog.includes(':show-close="!requestInFlight"'), "the X affordance must be hidden while a request is in flight")
assert.ok(dialog.includes(':disabled="fileMutationLocked"'), "upload replacement/removal must be disabled while batch state is in flight")
assert.ok(dialog.includes("blobValidate"), "download actions must validate JSON error blobs before saving")
assert.ok(!dialog.includes("ExcelImportDialog"), "generic direct-import dialog must not be reused")
assert.ok(!dialog.includes("dangerouslyUseHTMLString"), "import errors must never render HTML")
assert.ok(!dialog.includes("v-html"), "spreadsheet/server content must never render as HTML")
assert.ok(onboarding.includes("HrOnboardingImportDialog"), "onboarding workbench must reuse the import dialog")
assert.ok(importExport.includes("HrOnboardingImportDialog"), "import/export page must reuse the import dialog")
assert.ok(importExport.includes("过渡能力"), "legacy formal-employee import must be visibly transitional")

function loadComponent(source) {
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, "dialog script must exist")
  let script = match[1]
    .replace(/import\s*\{[\s\S]*?\}\s*from\s*["'][^"']+["']/g, "")
    .replace(/export const /g, "const ")
    .replace(/export default/, "component =")
  script += "\nexports.component = component; exports.api = { buildSelectedRows, initializePreviewRows, unresolvedDecisionRow, unwrapImportResponse, plainText, importCategoryLabel, importIssueLabel, importIssueListLabel };"
  const calls = { template: 0, errors: [], confirm: [], preview: [] }
  const sandbox = {
    exports: {},
    Blob,
    blobValidate(data) { return data.type !== "application/json" },
    FormData: class FormData {
      constructor() { this.entries = [] }
      append(key, value) { this.entries.push([key, value]) }
    },
    previewHrOnboardingImport(data) {
      calls.preview.push(data)
      return calls.previewPromise || Promise.resolve({ data: { batchId: 80, version: 1, status: "PREVIEWED", rows: [] } })
    },
    getHrOnboardingImportBatch() {},
    confirmHrOnboardingImport(id, payload) {
      calls.confirm.push({ id, payload })
      return calls.confirmPromise || Promise.resolve({ data: { batchId: id, version: payload.version + 1, status: "COMPLETED", rows: [] } })
    },
    downloadHrOnboardingTemplate() {
      calls.template += 1
      return calls.templatePromise || Promise.resolve(new Blob([new Uint8Array([1, 2, 3])], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }))
    },
    downloadHrOnboardingErrorRows(id) {
      calls.errors.push(id)
      return Promise.resolve(new Uint8Array([4, 5, 6]))
    }
  }
  vm.runInNewContext(script, sandbox, { filename: dialogPath })
  return { ...sandbox.exports, calls }
}

const { component, api, calls } = loadComponent(dialog)

const preview = api.unwrapImportResponse({ data: {
  batchId: 81,
  version: 3,
  rows: [
    { rowId: 1, category: "IMPORTABLE" },
    { rowId: 2, category: "WARNING" },
    { rowId: 3, category: "INVALID" },
    { rowId: 4, category: "POSSIBLE_DUPLICATE" },
    { rowId: 5, category: "BINDABLE_ACCOUNT", candidateUserId: 51, candidateSummary: "候选账号: 张** 138****0000" }
  ]
} })
assert.strictEqual(preview.batchId, 81, "AjaxResult data must be unwrapped")

const initialized = api.initializePreviewRows(preview.rows)
assert.strictEqual(initialized[0].selected, true, "clean rows are selected by default")
assert.strictEqual(initialized[0].decision, "IMPORT", "clean rows use an explicit import decision")
assert.strictEqual(initialized[1].selected, false, "warning rows require explicit continue")
assert.strictEqual(initialized[2].disabled, true, "invalid rows are disabled")
assert.strictEqual(initialized[3].selected, false, "possible duplicates require explicit continue")
assert.strictEqual(initialized[4].bindUserId, null, "bindable rows require an explicit masked candidate choice")
assert.strictEqual(api.unresolvedDecisionRow(initialized).rowId, 2, "warning/duplicate/bindable rows cannot be silently skipped")

initialized[1].selected = true
initialized[1].decision = "CONTINUE"
initialized[2].selected = true
initialized[4].selected = true
initialized[4].decision = "BIND_EXISTING"
initialized[4].bindUserId = 51
const selectedRows = api.buildSelectedRows(initialized)
assert.deepStrictEqual(JSON.parse(JSON.stringify(selectedRows)), [
  { rowId: 1, decision: "IMPORT", bindUserId: null },
  { rowId: 2, decision: "CONTINUE", bindUserId: null },
  { rowId: 5, decision: "BIND_EXISTING", bindUserId: 51 }
], "confirm payload must contain only selected valid row decision fields")

const instance = {
  ...component.data(),
  $message: { error() {}, warning() {}, success() {} },
  $download: { saveAs() {} },
  ...component.methods
}
for (const key of ["requestInFlight", "fileMutationLocked"]) {
  Object.defineProperty(instance, key, {
    configurable: true,
    get() { return component.computed[key].call(this) }
  })
}
instance.previewRequestSequence = 7
instance.applyPreviewResponse({ data: { batchId: 90, version: 4, rows: [] } }, 6)
assert.strictEqual(instance.batchId, null, "stale preview responses must be ignored")
instance.applyPreviewResponse({ data: { batchId: 91, version: 5, rows: [] } }, 7)
assert.strictEqual(instance.batchId, 91, "current preview response updates the active batch")

instance.batchId = 91
instance.version = 5
instance.rows = initialized
assert.deepStrictEqual(JSON.parse(JSON.stringify(instance.buildConfirmPayload())), {
  version: 5,
  rows: JSON.parse(JSON.stringify(selectedRows))
}, "confirm must use the active batch version and selected rows")

let previewCalls = 0
instance.previewing = true
instance.previewFile = { name: "people.xlsx" }
instance.submitPreview = () => { previewCalls += 1 }
component.methods.handlePreview.call(instance)
assert.strictEqual(previewCalls, 0, "double preview submit must be blocked")

assert.strictEqual(api.plainText("<img src=x onerror=alert(1)>"), "<img src=x onerror=alert(1)>", "messages remain inert text")
assert.strictEqual(api.importCategoryLabel("IMPORTABLE"), "可导入", "import categories must render in Chinese")
assert.strictEqual(api.importCategoryLabel("UNRECOGNIZED"), "待确认分类", "unknown import categories must not expose backend codes")
assert.strictEqual(api.importIssueLabel("PHONE_INVALID"), "手机号格式不正确", "known import issue codes must render in Chinese")
assert.strictEqual(api.importIssueLabel("NEW_BACKEND_CODE"), "未识别的导入校验问题", "unknown issue codes must not leak to users")
assert.strictEqual(
  api.importIssueListLabel(["PHONE_INVALID", "NEW_BACKEND_CODE"]),
  "手机号格式不正确；未识别的导入校验问题",
  "mixed issue lists must remain fully localized"
)
assert.ok(!/scope\.row\.phoneNumber(?!Masked)/.test(dialog) && !/scope\.row\.idNumber(?!Masked)/.test(dialog) && !/scope\.row\.bankAccount(?!Masked)/.test(dialog),
  "raw sensitive fields must not be rendered")

async function verifyDownloadActions() {
  const saved = []
  const parsedErrors = []
  instance.$download = {
    saveAs(blob, name) { saved.push({ blob, name }) },
    printErrMsg(blob) { parsedErrors.push(blob); return Promise.resolve() }
  }
  instance.templateDownloading = false
  await component.methods.downloadTemplate.call(instance)
  assert.strictEqual(calls.template, 1, "template action must use the audited GET/blob API wrapper")
  assert.strictEqual(saved[0].name, "入职导入模板.xlsx")

  instance.batchId = 91
  instance.errorsDownloading = false
  await component.methods.downloadErrorRows.call(instance)
  assert.deepStrictEqual(calls.errors, [91], "error-row action must target only the active batch")
  assert.strictEqual(saved[1].name, "入职导入失败行_91.xlsx")

  let closed = 0
  instance.previewing = true
  component.methods.beforeClose.call(instance, () => { closed += 1 })
  assert.strictEqual(closed, 0, "the dialog X/Esc lifecycle must not close during preview")
  instance.previewing = false
  instance.confirming = true
  component.methods.beforeClose.call(instance, () => { closed += 1 })
  assert.strictEqual(closed, 0, "the dialog X/Esc lifecycle must not close during confirmation")
  instance.confirming = false
  component.methods.beforeClose.call(instance, () => { closed += 1 })
  assert.strictEqual(closed, 1, "the dialog may close after all requests settle")

  calls.templatePromise = Promise.resolve(new Blob([JSON.stringify({ code: 500, msg: "template failed" })], { type: "application/json" }))
  instance.templateDownloading = false
  await component.methods.downloadTemplate.call(instance)
  assert.strictEqual(saved.length, 2, "a JSON error response must never be saved as an xlsx file")
  assert.strictEqual(parsedErrors.length, 1, "a JSON error response must use the standard text error parser")

  const lifecycle = {
    ...component.data(),
    $message: { error() {}, warning() {}, success() {} },
    $refs: { upload: { clearFiles() {} } },
    ...component.methods
  }
  for (const key of ["requestInFlight", "fileMutationLocked", "selectedRows"]) {
    Object.defineProperty(lifecycle, key, {
      configurable: true,
      get() { return component.computed[key].call(this) }
    })
  }
  const fileA = { name: "A.xlsx", size: 100 }
  const fileB = { name: "B.xlsx", size: 100 }
  const fileC = { name: "C.xlsx", size: 100 }
  component.methods.handleFileChange.call(lifecycle, { raw: fileA }, [{ raw: fileA }])
  let resolvePreviewA
  calls.previewPromise = new Promise(resolve => { resolvePreviewA = resolve })
  const previewA = component.methods.submitPreview.call(lifecycle)
  component.methods.handleFileChange.call(lifecycle, { raw: fileB }, [{ raw: fileB }])
  assert.strictEqual(lifecycle.previewFile, fileA, "the UI cannot replace the selected file during preview")

  component.methods.resetState.call(lifecycle)
  component.methods.handleFileChange.call(lifecycle, { raw: fileB }, [{ raw: fileB }])
  resolvePreviewA({ data: { batchId: 101, version: 1, status: "PREVIEWED", rows: [] } })
  await previewA
  assert.strictEqual(lifecycle.previewFile, fileB, "programmatic lifecycle may select a new file after invalidating A")
  assert.strictEqual(lifecycle.batchId, null, "a deferred response for file A cannot attach to file B")

  lifecycle.batchId = 201
  lifecycle.version = 4
  lifecycle.status = "PREVIEWED"
  lifecycle.rows = api.initializePreviewRows([{ rowId: 1, category: "IMPORTABLE" }])
  component.methods.handleFileChange.call(lifecycle, { raw: fileC }, [{ raw: fileC }])
  assert.strictEqual(lifecycle.previewFile, fileC)
  assert.strictEqual(lifecycle.batchId, null, "selecting valid file C must atomically clear file B's active batch")
  assert.strictEqual(lifecycle.version, null)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(lifecycle.rows)), [])

  lifecycle.batchId = 202
  lifecycle.version = 5
  lifecycle.status = "PREVIEWED"
  lifecycle.rows = api.initializePreviewRows([{ rowId: 2, category: "IMPORTABLE" }])
  component.methods.handleFileRemove.call(lifecycle)
  assert.strictEqual(lifecycle.previewFile, null, "removing a file clears its identity")
  assert.strictEqual(lifecycle.batchId, null, "removing a file clears the old batch")

  lifecycle.previewFile = fileA
  lifecycle.fileList = [{ raw: fileA }]
  lifecycle.batchId = 203
  lifecycle.version = 6
  lifecycle.status = "PREVIEWED"
  lifecycle.rows = api.initializePreviewRows([{ rowId: 3, category: "IMPORTABLE" }])
  component.methods.handleFileChange.call(lifecycle, { raw: { name: "bad.txt", size: 10 } }, [])
  assert.strictEqual(lifecycle.previewFile, null, "an invalid replacement clears the selected file")
  assert.strictEqual(lifecycle.batchId, null, "an invalid replacement clears the old batch")

  const confirmCountBeforeMissingBatch = calls.confirm.length
  lifecycle.rows = api.initializePreviewRows([{ rowId: 99, category: "IMPORTABLE" }])
  component.methods.confirmSelectedRows.call(lifecycle)
  assert.strictEqual(calls.confirm.length, confirmCountBeforeMissingBatch, "confirmation cannot call the API without a fresh file-bound batch")

  let resolveConfirm
  calls.confirmPromise = new Promise(resolve => { resolveConfirm = resolve })
  instance.batchId = 91
  instance.version = 5
  instance.status = "PREVIEWED"
  instance.previewFile = { name: "confirmed.xlsx" }
  instance.fileGeneration = 10
  instance.batchFile = instance.previewFile
  instance.batchFileGeneration = 10
  instance.rows = api.initializePreviewRows([{ rowId: 1, category: "IMPORTABLE" }])
  instance.confirming = false
  instance.previewing = false
  Object.defineProperty(instance, "selectedRows", { configurable: true, get() { return api.buildSelectedRows(this.rows) } })
  const confirmPromise = component.methods.confirmSelectedRows.call(instance)
  component.methods.confirmSelectedRows.call(instance)
  assert.strictEqual(calls.confirm.length, 1, "double confirm submit must be blocked")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(calls.confirm[0])), {
    id: 91,
    payload: { version: 5, rows: [{ rowId: 1, decision: "IMPORT", bindUserId: null }] }
  }, "confirm must send the active batch and exact minimal payload")
  instance.version = 6
  resolveConfirm({ data: { batchId: 91, version: 7, status: "COMPLETED", successRows: 1, failureRows: 0, rows: [] } })
  await confirmPromise
  assert.strictEqual(instance.version, 6, "a response for a stale batch version must be ignored")

  console.log("hrOnboardingImport tests passed")
}

verifyDownloadActions().catch(error => {
  console.error(error)
  process.exitCode = 1
})
