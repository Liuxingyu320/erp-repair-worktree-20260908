const assert = require("assert")

const {
  applyInvoiceToForm,
  ensureDraftForInvoiceUpload,
  inferExpenseType,
  isBlankExpenseItem,
  recoverReimbursementDeleteFailure,
  reimbursementFormSnapshot,
  reimbursementReadiness
} = require("../src/utils/reimbursementSmartFill")

assert.strictEqual(
  isBlankExpenseItem({
    expenseType: "",
    expenseDate: "2026-07-31",
    merchantName: "",
    description: "",
    claimedAmount: ""
  }),
  true,
  "a default date alone must not prevent OCR from reusing the initial row"
)
assert.strictEqual(
  isBlankExpenseItem({
    expenseType: "",
    expenseDate: "2026-07-31",
    merchantName: "",
    description: "",
    claimedAmount: 0.01
  }),
  true,
  "a control-generated 0.01 amount is still an empty placeholder"
)
assert.strictEqual(
  isBlankExpenseItem({
    expenseType: "",
    expenseDate: "2026-07-31",
    merchantName: "",
    description: "",
    claimedAmount: 0,
    sourceInvoiceId: 99
  }),
  false,
  "an existing invoice association must not be overwritten as a blank row"
)
assert.strictEqual(
  inferExpenseType({
    serviceType: "旅客运输服务",
    commoditySummary: "铁路电子客票"
  }),
  "交通费"
)
assert.strictEqual(
  inferExpenseType({ sellerName: "杭州某某大酒店有限公司" }),
  "住宿费"
)

const form = {
  title: "",
  purpose: "",
  items: [{
    expenseType: "",
    expenseDate: "2026-07-31",
    merchantName: "",
    description: "",
    claimedAmount: ""
  }],
  invoices: []
}
const first = applyInvoiceToForm(form, {
  invoiceId: 101,
  invoiceDate: "2026-07-29",
  sellerName: "城市轨道交通",
  invoiceTotalAmount: "18.50",
  commoditySummary: "地铁客运服务"
})
assert.deepStrictEqual(
  { applied: first.applied, itemIndex: first.itemIndex },
  { applied: true, itemIndex: 0 }
)
assert.strictEqual(form.items[0].expenseType, "交通费")
assert.strictEqual(form.items[0].expenseDate, "2026-07-29")
assert.strictEqual(form.items[0].claimedAmount, 18.5)
assert.strictEqual(form.items[0].sourceInvoiceId, 101)
assert.strictEqual(form.title, "城市轨道交通报销")
assert.strictEqual(form.purpose, "发票费用报销")

const second = applyInvoiceToForm(form, {
  invoiceId: 102,
  invoiceDate: "2026-07-30",
  sellerName: "湖畔酒店",
  invoiceTotalAmount: 388,
  commoditySummary: "住宿服务"
})
assert.strictEqual(second.itemIndex, 1)
assert.strictEqual(form.items[0].merchantName, "城市轨道交通")
assert.strictEqual(form.items[1].expenseType, "住宿费")

const replay = applyInvoiceToForm(form, {
  invoiceId: 101,
  invoiceDate: "2026-07-29",
  sellerName: "城市轨道交通",
  invoiceTotalAmount: 20,
  commoditySummary: "地铁客运服务"
})
assert.strictEqual(replay.itemIndex, 0)
assert.strictEqual(form.items.length, 2)
assert.strictEqual(form.items[0].claimedAmount, 20)

const placeholderForm = {
  title: "",
  purpose: "",
  items: [{
    expenseType: "",
    expenseDate: "2026-07-31",
    merchantName: "",
    description: "",
    claimedAmount: 0.01
  }],
  invoices: []
}
const placeholderResult = applyInvoiceToForm(placeholderForm, {
  invoiceId: 103,
  invoiceTotalAmount: 100,
  sellerName: "测试商户",
  commoditySummary: "办公用品"
})
assert.strictEqual(placeholderResult.itemIndex, 0)
assert.strictEqual(placeholderForm.items.length, 1)
assert.strictEqual(placeholderForm.items[0].claimedAmount, 100)

const presetDateForm = {
  title: "",
  purpose: "",
  items: [{
    expenseType: "",
    expenseDate: "2026-07-28",
    merchantName: "",
    description: "",
    claimedAmount: ""
  }],
  invoices: []
}
applyInvoiceToForm(presetDateForm, {
  sellerName: "某办公用品店",
  invoiceTotalAmount: 39,
  commoditySummary: "办公文具"
})
assert.strictEqual(
  presetDateForm.items[0].expenseDate,
  "2026-07-28",
  "OCR without a date must not erase a date already entered by the user"
)

form.invoices = [{
  originalName: "地铁.pdf",
  recognitionStatus: "succeeded",
  invoiceTotalAmount: 18.5,
  duplicateStatus: "none"
}, {
  originalName: "酒店.pdf",
  recognitionStatus: "partial",
  invoiceTotalAmount: 388,
  duplicateStatus: "warning"
}]
const readiness = reimbursementReadiness(form)
assert.strictEqual(readiness.ready, true)
assert.ok(readiness.warnings.some(value => value.includes("疑似重复")))
assert.strictEqual(readiness.difference, 1.5)

const snapshot = reimbursementFormSnapshot(form)
assert.ok(snapshot.includes('"sourceInvoiceId":"101"'))
form.title = "已修改"
assert.notStrictEqual(reimbursementFormSnapshot(form), snapshot)

let existingDraftSaveCalls = 0
const existingDraft = { reimbursementId: 77, rowVersion: 4 }
assert.strictEqual(
  ensureDraftForInvoiceUpload(existingDraft, () => {
    existingDraftSaveCalls += 1
    return Promise.resolve(existingDraft)
  }).then,
  Promise.prototype.then,
  "existing drafts should keep the upload preparation asynchronous"
)
assert.strictEqual(
  existingDraftSaveCalls,
  0,
  "re-uploading an existing draft must not pre-save and advance rowVersion"
)
let newDraftSaveCalls = 0
const newDraft = { reimbursementId: 88, rowVersion: 0 }
ensureDraftForInvoiceUpload({}, () => {
  newDraftSaveCalls += 1
  return newDraft
})
assert.strictEqual(
  newDraftSaveCalls,
  1,
  "first upload for a new form must still persist a draft"
)

const desktopDeleteEvents = []
recoverReimbursementDeleteFailure(
  new Error("stale version"),
  error => desktopDeleteEvents.push(`error:${error.message}`),
  () => desktopDeleteEvents.push("refresh")
)
assert.deepStrictEqual(
  desktopDeleteEvents,
  ["error:stale version", "refresh"],
  "desktop delete failures must be surfaced and followed by a refresh"
)
const mobileDeleteEvents = []
recoverReimbursementDeleteFailure(
  new Error("network"),
  error => mobileDeleteEvents.push(`error:${error.message}`),
  () => mobileDeleteEvents.push("refresh")
)
assert.deepStrictEqual(
  mobileDeleteEvents,
  ["error:network", "refresh"],
  "mobile delete failures must be surfaced and followed by a refresh"
)

console.log("reimbursementSmartFill tests passed")
