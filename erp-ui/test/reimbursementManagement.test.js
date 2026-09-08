const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")

const api = read("src/api/oa/reimbursement.js")
const desktop = read("src/views/oa/reimbursement/index.vue")
const mobile = read("src/views/mobile/oa/reimbursement/index.vue")
const routes = read("src/views/mobile/mobileRouteDefinitions.js")
const todoRoutes = read("src/utils/todoRouteResolver.js")
const todoFocus = read("src/utils/todoBusinessFocus.js")
const approvalUi = read(
  "src/views/approval/manage/components/approvalUi.js"
)
const {
  formatMoney,
  mergeReimbursementRows,
  reimbursementErrorMessage,
  serializeReimbursementForm,
  stableApprovalRequestId,
  sumMoneyCents,
  validateReimbursementSubmission
} = require("../src/views/mobile/oa/reimbursement/mobileReimbursementState")

function extractMethodBody(source, methodName, nextMethodName) {
  const match = source.match(new RegExp(
    `\\n    (?:async\\s+)?${methodName}\\([^\\n]*\\) \\{([\\s\\S]*?)\\n    \\},\\n    ${nextMethodName}`
  ))
  assert.ok(match, `should extract Vue method ${methodName}`)
  return match[1]
}

const uploadContextMessage = "请先选择门店或仓库，再上传发票"

const desktopHasInvoiceUploadContext = new Function(
  "hasValidatedSelectedDeptContext",
  "INVOICE_UPLOAD_CONTEXT_MESSAGE",
  extractMethodBody(desktop, "hasInvoiceUploadContext", "beforeInvoiceUpload")
)
const desktopBeforeInvoiceUpload = new Function(
  "file",
  extractMethodBody(desktop, "beforeInvoiceUpload", "uploadInvoice")
)
const desktopUploadInvoice = new Function(
  "option",
  extractMethodBody(desktop, "uploadInvoice", "uploadFileKey")
)
const desktopEnsureDraftForUpload = new Function(
  "ensureDraftForInvoiceUpload",
  "reimbursementFormSnapshot",
  "saveReimbursement",
  "INVOICE_UPLOAD_CONTEXT_MESSAGE",
  extractMethodBody(desktop, "ensureDraftForUpload", "processInvoiceUpload")
)
const desktopProcessInvoiceUpload = new Function(
  "file",
  "uploadReimbursementInvoice",
  "applyInvoiceToForm",
  "reimbursementFormSnapshot",
  "saveReimbursement",
  "responseMessage",
  extractMethodBody(desktop, "processInvoiceUpload", "reloadFormDetail")
)

const mobileHasInvoiceUploadContext = new Function(
  "hasValidatedSelectedDeptContext",
  "INVOICE_UPLOAD_CONTEXT_MESSAGE",
  extractMethodBody(mobile, "hasInvoiceUploadContext", "openInvoicePicker")
)
const mobileOpenInvoicePicker = new Function(
  "kind",
  extractMethodBody(mobile, "openInvoicePicker", "invoiceSelected")
)
const mobileInvoiceSelected = new Function(
  "event",
  extractMethodBody(mobile, "invoiceSelected", "validateInvoiceFile")
)
const mobileProcessInvoiceQueue = new Function(
  "reimbursementErrorMessage",
  `return async function (files) {\n${extractMethodBody(mobile, "processInvoiceQueue", "uploadInvoiceFile")}\n}`
)(value => value)
const mobileUploadInvoiceFile = new Function(
  "file",
  "uploadReimbursementInvoice",
  "applyInvoiceToForm",
  "reimbursementErrorMessage",
  extractMethodBody(mobile, "uploadInvoiceFile", "retryInvoiceUpload")
)
const mobileRetryInvoiceUpload = new Function(
  extractMethodBody(mobile, "retryInvoiceUpload", "cancelInvoiceUpload")
)

;[
  "/availability",
  "/my",
  "/finance",
  "/save",
  "/submit",
  "/withdraw",
  "/invoices",
  "/recognize",
  "/recognition",
  "/finance/exports"
].forEach(fragment => {
  assert.ok(api.includes(fragment), `reimbursement API should include ${fragment}`)
})

assert.ok(
  /invoices\/\$\{invoiceId\}\/content[\s\S]{0,300}responseType:\s*['"]blob['"]/.test(api),
  "invoice access must use an authenticated blob endpoint"
)
assert.ok(
  /finance\/exports\/\$\{batchId\}\/download[\s\S]{0,300}responseType:\s*['"]blob['"]/.test(api),
  "accounting export must download from the protected batch endpoint"
)
assert.ok(
  !api.includes("/profile/") && !api.includes("storagePath"),
  "the browser API must not expose a public or physical invoice path"
)

assert.ok(
  desktop.includes("导出 Excel＋发票") &&
    desktop.includes("三张 Excel 工作表") &&
    desktop.includes("疑似重复发票") &&
    desktop.includes("云端识别") &&
    desktop.includes("本地识别") &&
    desktop.includes("保存人工修正") &&
    desktop.includes("先上传发票，系统自动生成报销内容") &&
    desktop.includes("选择本页未导出") &&
    desktop.includes("submissionReadiness") &&
    desktop.includes("processInvoiceUpload") &&
    desktop.includes("uncertainUploadFiles") &&
    desktop.includes("confirmUncertainUpload") &&
    desktop.includes("applySavedForm") &&
    desktop.includes("changedDuringRequest") &&
    desktop.includes("uploadedInvoice.idempotentReplay") &&
    desktop.includes("未重复识别或生成明细") &&
    desktop.includes("ensureDraftForInvoiceUpload") &&
    desktop.includes("deleteStarted") &&
    desktop.includes("recoverReimbursementDeleteFailure") &&
    desktop.includes("responseMessage(value)") &&
    desktop.includes("hasValidatedSelectedDeptContext") &&
    desktop.includes("请先选择门店或仓库，再上传发票") &&
    desktop.includes("beforeInvoiceUpload") &&
    desktop.includes("ensureDraftForUpload") &&
    desktop.includes("approveApprovalTask") &&
    desktop.includes("returnApprovalTask") &&
    desktop.includes("rejectApprovalTask"),
  "desktop reimbursement page should cover employee, finance and approval flows"
)
assert.ok(
  mobile.includes("发票随手传") &&
    mobile.includes("云端识别") &&
    mobile.includes("本地识别") &&
    mobile.includes("保存核对") &&
    mobile.includes("提交审批") &&
    mobile.includes("审批轨迹") &&
    mobile.includes("approveApprovalTask") &&
    mobile.includes("loadMoreRows") &&
    mobile.includes("confirmSafeLeave()") &&
    mobile.includes("if (this.isFormDirty) return this.confirmDiscardChanges()") &&
    mobile.includes("confirmDiscardChanges") &&
    mobile.includes("拍照发票") &&
    mobile.includes("先拍发票，自动生成报销内容") &&
    mobile.includes("processInvoiceQueue") &&
    mobile.includes("submissionReadiness") &&
    mobile.includes("uploadQueueToken") &&
    mobile.includes("pendingInvoiceKnownIds") &&
    mobile.includes("checkingUploadResult") &&
    mobile.includes("uploadedInvoice.idempotentReplay") &&
    mobile.includes("未重复识别或生成明细") &&
    mobile.includes("deleteStarted") &&
    mobile.includes("recoverReimbursementDeleteFailure") &&
    mobile.includes("reimbursementErrorMessage(value)") &&
    mobile.includes("deletingInvoiceId") &&
    mobile.includes("uploadWorkActive") &&
    mobile.includes("retryToken !== this.uploadQueueToken") &&
    mobile.includes("hasValidatedSelectedDeptContext") &&
    mobile.includes("openInvoicePicker") &&
    mobile.includes("请先选择门店或仓库，再上传发票") &&
    mobile.includes("confirmSafeLeave") &&
    mobile.includes("openInvoicePreview") &&
    mobile.includes('v-model="invoice.targetItemKey"') &&
    mobile.includes("自动选择（优先复用已关联明细）"),
  "mobile reimbursement page should cover creation, invoices and approvals"
)
assert.ok(
  api.includes("onUploadProgress") && api.includes("signal"),
  "invoice upload should expose progress and cancellation"
)

assert.strictEqual(sumMoneyCents(["0.10", "0.20", "12.345"]), 30)
assert.strictEqual(formatMoney("900719.10"), "¥900719.10")
assert.strictEqual(
  stableApprovalRequestId(91, "approve"),
  "OA_REIMBURSEMENT:91:approve:v1",
  "approval retries should reuse a stable request id"
)
assert.strictEqual(
  reimbursementErrorMessage({
    response: {
      status: 500,
      data: { msg: "java.sql.SQLException at com.erp.Mapper.java:91" }
    }
  }),
  "服务暂时不可用，请稍后重试",
  "technical server details must not reach the mobile page"
)
assert.strictEqual(
  reimbursementErrorMessage({
    response: {
      status: 401,
      data: { code: 401, msg: "令牌不能为空" }
    }
  }),
  "登录状态已失效，请重新登录",
  "authentication implementation details must become clear session guidance"
)

const incompleteDraft = {
  title: "差旅报销",
  purpose: "",
  items: [{ expenseType: "", expenseDate: "2026-07-31", claimedAmount: "" }],
  invoices: []
}
assert.strictEqual(
  validateReimbursementSubmission(incompleteDraft),
  "请输入报销事由"
)
assert.strictEqual(
  validateReimbursementSubmission({
    title: "差旅报销",
    purpose: "客户拜访",
    items: [{
      expenseType: "交通费",
      expenseDate: "2026-07-31",
      description: "铁路客票",
      claimedAmount: "88.00"
    }],
    invoices: [{ recognitionStatus: "partial" }]
  }),
  "",
  "partial OCR should follow the backend submission policy and remain reviewable"
)
const baseline = serializeReimbursementForm(incompleteDraft)
assert.notStrictEqual(
  serializeReimbursementForm({ ...incompleteDraft, purpose: "客户拜访" }),
  baseline,
  "unsaved form edits should be detectable"
)
assert.deepStrictEqual(
  mergeReimbursementRows(
    [{ reimbursementId: 1 }, { reimbursementId: 2 }],
    [{ reimbursementId: 2 }, { reimbursementId: 3 }]
  ).map(row => row.reimbursementId),
  [1, 2, 3],
  "incremental pages should remain ordered and deduplicated"
)

assert.ok(
  routes.includes("path: '/mobile/reimbursement'") &&
    routes.includes("featureKey: 'reimbursement'") &&
    routes.includes("requiresBusinessContext: true"),
  "mobile reimbursement route should require the selected business context"
)
assert.ok(
  todoRoutes.includes(
    "OA_REIMBURSEMENT_APPROVAL: organization(OA_REIMBURSEMENT, \"/mobile/reimbursement\")"
  ) &&
    todoFocus.includes("OA_REIMBURSEMENT_APPROVAL") &&
    todoFocus.includes("viewReimbursementApproval"),
  "reimbursement approval todos should deep-link to the target claim"
)
assert.ok(
  approvalUi.includes("OA_REIMBURSEMENT") &&
    approvalUi.includes("费用报销"),
  "approval administration should label the reimbursement business"
)

;(async () => {
  const invoiceFile = { name: "无组织发票.pdf", size: 1024, lastModified: 1 }
  let desktopSaveCalls = 0
  let desktopUploadCalls = 0
  let desktopProcessCalls = 0
  const desktopWarnings = []
  const desktopHarness = {
    form: { reimbursementId: "", invoices: [] },
    uploadQueue: Promise.resolve(),
    uploadPendingCount: 0,
    uploadCompletedCount: 0,
    uploadFailedCount: 0,
    uploading: false,
    uncertainUploadFiles: {},
    $modal: {
      msgWarning(message) { desktopWarnings.push(message) },
      msgSuccess() {},
      msgError() {}
    },
    hasInvoiceUploadContext() {
      return desktopHasInvoiceUploadContext.call(
        this,
        () => false,
        uploadContextMessage
      )
    },
    beforeInvoiceUpload: desktopBeforeInvoiceUpload,
    confirmUncertainUpload() { return Promise.resolve(true) },
    processInvoiceUpload(file) {
      desktopProcessCalls += 1
      return desktopProcessInvoiceUpload.call(
        this,
        file,
        () => { desktopUploadCalls += 1; return Promise.resolve({ data: {} }) },
        () => ({ applied: true }),
        () => "snapshot",
        () => { desktopSaveCalls += 1; return Promise.resolve({ data: this.form }) },
        value => value
      )
    },
    uploadInvoice: desktopUploadInvoice,
    ensureDraftForUpload() {
      return desktopEnsureDraftForUpload.call(
        this,
        () => Promise.resolve(this.form),
        () => "snapshot",
        () => {
          desktopSaveCalls += 1
          return Promise.resolve({ data: this.form })
        },
        uploadContextMessage
      )
    }
  }
  assert.strictEqual(desktopHarness.beforeInvoiceUpload(invoiceFile), false)
  assert.strictEqual(await desktopHarness.uploadInvoice({ file: invoiceFile }), false)
  await assert.rejects(
    desktopHarness.ensureDraftForUpload(),
    /请先选择门店或仓库，再上传发票/
  )
  assert.strictEqual(await desktopProcessInvoiceUpload.call(
    desktopHarness,
    invoiceFile,
    () => { desktopUploadCalls += 1; return Promise.resolve({ data: {} }) },
    () => ({ applied: true }),
    () => "snapshot",
    () => { desktopSaveCalls += 1; return Promise.resolve({ data: desktopHarness.form }) },
    value => value
  ), false, "desktop process must fail closed without organization context")
  assert.strictEqual(desktopProcessCalls, 0,
    "desktop upload queue must not enter processInvoiceUpload without organization context")
  assert.strictEqual(desktopSaveCalls, 0,
    "desktop upload guards must not save a draft without organization context")
  assert.strictEqual(desktopUploadCalls, 0,
    "desktop upload guards must not call the invoice API without organization context")
  assert.ok(desktopWarnings.includes(uploadContextMessage))

  let mobilePersistCalls = 0
  let mobileUploadCalls = 0
  let mobileQueueProcessCalls = 0
  let pickerClicks = 0
  const mobileHarness = {
    form: { reimbursementId: "88", invoices: [] },
    busy: false,
    saving: false,
    uploading: false,
    uploadQueueActive: false,
    uploadQueueToken: 0,
    uploadBatchTotal: 0,
    uploadBatchCompleted: 0,
    uploadBatchFailures: [],
    pendingInvoiceFile: invoiceFile,
    pendingInvoiceKnownIds: [],
    checkingUploadResult: false,
    uploadError: "",
    uploadController: null,
    $refs: { invoiceInput: { click() { pickerClicks += 1 } } },
    $modal: {
      msgWarning() {},
      msgSuccess() {},
      msgError() {}
    },
    hasInvoiceUploadContext() {
      return mobileHasInvoiceUploadContext.call(
        this,
        () => false,
        uploadContextMessage
      )
    },
    persist() {
      mobilePersistCalls += 1
      return Promise.resolve(this.form)
    },
    processInvoiceQueue(files) {
      mobileQueueProcessCalls += 1
      return mobileProcessInvoiceQueue.call(this, value => value, files)
    },
    uploadInvoiceFile(file) {
      return mobileUploadInvoiceFile.call(
        this,
        file,
        () => { mobileUploadCalls += 1; return Promise.resolve({ data: {} }) },
        () => ({ applied: true }),
        value => value
      )
    },
    retryInvoiceUpload: mobileRetryInvoiceUpload,
    refreshForm() { return Promise.resolve(this.form) },
    isCanceledUpload() { return false }
  }
  mobileOpenInvoicePicker.call(mobileHarness, "file")
  assert.strictEqual(pickerClicks, 0,
    "mobile picker entry must not open the file chooser without organization context")
  mobileInvoiceSelected.call(mobileHarness, {
    target: { files: [invoiceFile], value: "stale-file-value" }
  })
  assert.strictEqual(await mobileHarness.processInvoiceQueue([invoiceFile]), false)
  assert.strictEqual(mobileHarness.uploadQueueActive, false)
  assert.strictEqual(await mobileHarness.uploadInvoiceFile(invoiceFile), false)
  mobileHarness.retryInvoiceUpload.call(mobileHarness)
  assert.strictEqual(mobilePersistCalls, 0,
    "mobile no-context queue must not persist a draft")
  assert.strictEqual(mobileUploadCalls, 0,
    "mobile no-context queue must not call the invoice API")
  assert.strictEqual(mobileQueueProcessCalls, 1,
    "mobile queue entry was exercised through the extracted Vue method")
  assert.strictEqual(mobileHarness.uploadQueueActive, false,
    "mobile no-context queue must never remain active")
})().then(() => {
  console.log("reimbursementManagement tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
