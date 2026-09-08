const assert = require("assert")
const fs = require("fs")
const path = require("path")

const { MOBILE_FORM_CONFIG } = require("../src/views/mobile/feature/mobileFormConfigs")
const { validateMobileForm } = require("../src/views/mobile/feature/mobileValidation")

const readSource = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")

const pcPurchaseSource = readSource("../src/views/oa/purchase/index.vue")
const purchaseApiSource = readSource("../src/api/oa/purchase.js")
const mobileFeatureSource = readSource("../src/views/mobile/feature/index.vue")
const mobileFormSheetSource = readSource("../src/views/mobile/feature/components/MobileFormSheet.vue")
const imageUploadSource = readSource("../src/components/ImageUpload/index.vue")
const editorSource = readSource("../src/components/Editor/index.vue")

const oaPurchaseConfig = MOBILE_FORM_CONFIG.oaPurchase
const oaPurchaseFieldKeys = oaPurchaseConfig.fields.map(field => field.key)

assert.deepStrictEqual(
  oaPurchaseFieldKeys,
  ["title", "amount", "reason", "remark"],
  "mobile OA purchase form should match the desktop/backend purchase fields"
)

assert.ok(
  oaPurchaseConfig.fields.some(field => field.key === "amount" && field.type === "number" && field.required),
  "mobile OA purchase form should require the same amount field as desktop"
)

assert.strictEqual(
  validateMobileForm(oaPurchaseConfig, { title: "采购申请", reason: "门店补货" }),
  "请填写申请金额",
  "mobile OA purchase validation should block submissions without amount"
)

assert.strictEqual(
  validateMobileForm(oaPurchaseConfig, { title: "采购申请", amount: 120, reason: "门店补货" }),
  "",
  "mobile OA purchase validation should accept desktop-compatible data"
)

assert.ok(
  pcPurchaseSource.includes("submitLoading") &&
    pcPurchaseSource.includes(":loading=\"submitLoading\"") &&
    pcPurchaseSource.includes(":disabled=\"submitLoading\""),
  "desktop purchase save/submit buttons should expose loading and duplicate-click protection"
)

assert.ok(
  pcPurchaseSource.includes(":before-close=\"handleDialogClose\"") &&
    pcPurchaseSource.includes(":close-on-click-modal=\"false\"") &&
    pcPurchaseSource.includes("beforeRouteLeave") &&
    pcPurchaseSource.includes("isFormDirty"),
  "desktop purchase form should warn before closing or leaving with unsaved changes"
)

assert.ok(
  purchaseApiSource.includes("export function closePurchase(purchaseId)") &&
    purchaseApiSource.includes("'/oa/purchase/' + purchaseId + '/close'") &&
    purchaseApiSource.includes("method: 'post'"),
  "desktop purchase API should expose the rejected-purchase close endpoint"
)

assert.ok(
  pcPurchaseSource.includes("scope.row.status === 'cancelled'") &&
    pcPurchaseSource.includes("已关闭"),
  "desktop purchase list should render the cancelled lifecycle state"
)

assert.ok(
  pcPurchaseSource.includes("canClosePurchase(scope.row)") &&
    pcPurchaseSource.includes("关闭申请") &&
    pcPurchaseSource.includes("v-hasPermi=\"['oa:purchase:add']\"") &&
    pcPurchaseSource.includes("String(row.applicantId) === String(this.currentUserId)"),
  "desktop purchase list should only show close for permitted current-user rejected rows"
)

assert.ok(
  pcPurchaseSource.includes("closePurchase(row.purchaseId)") &&
    pcPurchaseSource.includes("确认关闭这条已驳回的采购申请") &&
    pcPurchaseSource.includes("this.getList()"),
  "desktop purchase close action should confirm, call the API, and refresh the list"
)

assert.ok(
  pcPurchaseSource.includes('["draft", "returned", "withdrawn"].includes(row.status)') &&
    pcPurchaseSource.includes("openForm(scope.row)") &&
    pcPurchaseSource.includes("doSubmit(scope.row)") &&
    pcPurchaseSource.includes("canClosePurchase(scope.row)"),
  "draft, returned, and withdrawn purchases should remain editable while rejected purchases use the explicit close flow"
)

assert.ok(
  pcPurchaseSource.includes('@click="openForm()"'),
  "the new-purchase button must not pass its click event as a purchase row"
)
const openFormMatch = pcPurchaseSource.match(
  /    openForm\(row\) \{([\s\S]*?)\n    \},\n    save\(/
)
assert.ok(openFormMatch, "purchase openForm implementation should remain locally testable")
assert.ok(!openFormMatch[1].includes("Number("),
  "purchase ids must not be coerced through Number and lose large integer precision")
const openPurchaseForm = new Function("getPurchaseDetail", "row", openFormMatch[1])
const detailCalls = []
const detailResponse = id => ({
  then(callback) {
    callback({ data: { purchaseId: id, title: "采购草稿" } })
    return Promise.resolve()
  }
})
const newFormHarness = {
  form: null,
  open: false,
  $nextTick() {},
  markFormClean() {}
}
openPurchaseForm.call(newFormHarness, id => {
  detailCalls.push(id)
  return detailResponse(id)
}, { type: "click", target: {}, purchaseId: "9007199254740993" })
assert.deepStrictEqual(detailCalls, [],
  "a MouseEvent-like argument must open a blank form without requesting /purchase/undefined")
assert.strictEqual(newFormHarness.form.purchaseId, undefined)
const existingFormHarness = {
  form: null,
  open: false,
  $nextTick() {},
  markFormClean() {}
}
openPurchaseForm.call(existingFormHarness, id => {
  detailCalls.push(id)
  return detailResponse(id)
}, { purchaseId: "9007199254740993" })
assert.deepStrictEqual(detailCalls, ["9007199254740993"],
  "a legal large purchase id must be sent to detail lookup as the original string")
assert.strictEqual(existingFormHarness.form.purchaseId, "9007199254740993")

assert.ok(
  mobileFeatureSource.includes("withMobileRetry") &&
    mobileFeatureSource.includes("fetchMobileFeatureData") &&
    mobileFeatureSource.includes("fetchMobileFeatureDetail"),
  "mobile feature requests should retry transient list/detail failures"
)

assert.ok(
  mobileFeatureSource.includes("requestCloseMobileForm") &&
    mobileFeatureSource.includes("isMobileFormDirty") &&
    mobileFeatureSource.includes("beforeRouteLeave") &&
    mobileFeatureSource.includes("beforeRouteUpdate"),
  "mobile form sheet should confirm before discarding unsaved changes"
)

assert.ok(
  mobileFormSheetSource.includes("@focusin=\"handleFocusIn\"") &&
    mobileFormSheetSource.includes("scrollTargetWithinFormBody") &&
    mobileFormSheetSource.includes("formBody.scrollTo") &&
    !mobileFormSheetSource.includes("target.scrollIntoView"),
  "mobile form sheet should scroll focused inputs inside its own content area above the soft keyboard"
)

assert.ok(
  mobileFormSheetSource.includes(":compress=\"field.compress !== false\"") &&
    imageUploadSource.includes("compressMaxWidth") &&
    imageUploadSource.includes("compressImageFile") &&
    imageUploadSource.includes("canvas.toBlob"),
  "mobile image uploads should opt into client-side compression before upload"
)

assert.ok(
  editorSource.includes("lastSelectionIndex") &&
    editorSource.includes("getSelection(true)") &&
    editorSource.includes("insertImage(file).catch"),
  "rich text editor image insertion should survive focus loss and report paste upload failures"
)
