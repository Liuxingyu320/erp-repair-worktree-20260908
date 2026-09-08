const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  getMobileFormValidationError,
  isFieldRequired,
  validateMobileForm
} = require("../src/views/mobile/feature/mobileValidation")

let mobileFocus = {}
try {
  mobileFocus = require("../src/views/mobile/feature/components/mobileFocus")
} catch (error) {
  if (!error || error.code !== "MODULE_NOT_FOUND") throw error
}

const { focusElementAndVerify, runFocusWithFallback } = mobileFocus

assert.strictEqual(typeof focusElementAndVerify, "function", "verified focus helper should be exported")
assert.strictEqual(typeof runFocusWithFallback, "function", "focus fallback helper should be exported")

const stalledDocument = { activeElement: null }
const stalledTarget = {
  ownerDocument: stalledDocument,
  focus() {}
}
assert.strictEqual(
  focusElementAndVerify(stalledTarget),
  false,
  "calling focus without changing activeElement should report failure"
)

let fallbackCalls = 0
assert.strictEqual(
  runFocusWithFallback(
    () => focusElementAndVerify(stalledTarget),
    () => {
      fallbackCalls += 1
      return true
    }
  ),
  true,
  "an unfocusable validation target should continue to the error summary fallback"
)
assert.strictEqual(fallbackCalls, 1, "the error summary fallback should run once")

const focusedDocument = { activeElement: null }
const focusedTarget = {
  ownerDocument: focusedDocument,
  focus() {
    this.ownerDocument.activeElement = this
  }
}
assert.strictEqual(focusElementAndVerify(focusedTarget), true, "a target that receives focus should report success")

let unexpectedFallbackCalls = 0
assert.strictEqual(
  runFocusWithFallback(
    () => focusElementAndVerify(focusedTarget),
    () => {
      unexpectedFallbackCalls += 1
      return true
    }
  ),
  true,
  "a focused validation target should complete without using the summary"
)
assert.strictEqual(unexpectedFallbackCalls, 0, "the summary fallback should not run after successful focus")

const validationConfig = {
  fields: [
    { key: "title", label: "申请标题", required: true },
    {
      key: "details",
      label: "采购明细",
      type: "line-items",
      requiredWhen: { key: "scope", value: "products" },
      itemFields: [
        { key: "productId", label: "商品", required: true },
        { key: "quantity", label: "数量", type: "number", required: true }
      ]
    }
  ]
}

assert.strictEqual(typeof getMobileFormValidationError, "function", "structured validation should be exported")
assert.strictEqual(typeof isFieldRequired, "function", "dynamic required evaluation should be reusable")

assert.strictEqual(isFieldRequired(validationConfig.fields[1], { scope: "all" }), false)
assert.strictEqual(isFieldRequired(validationConfig.fields[1], { scope: "products" }), true)
assert.strictEqual(isFieldRequired(validationConfig.fields[1]), false)

assert.deepStrictEqual(
  getMobileFormValidationError(validationConfig, { title: "", scope: "products", details: [] }),
  { message: "请填写申请标题", fieldKey: "title", rowIndex: null, itemFieldKey: null },
  "ordinary required fields should expose an exact focus target"
)

assert.deepStrictEqual(
  getMobileFormValidationError(validationConfig, { title: "采购", scope: "products", details: [] }),
  { message: "请添加采购明细", fieldKey: "details", rowIndex: null, itemFieldKey: null },
  "empty required line items should target the line-items group"
)

assert.deepStrictEqual(
  getMobileFormValidationError(validationConfig, {
    title: "采购",
    scope: "products",
    details: [{ productId: "", quantity: 1 }]
  }),
  { message: "采购明细第1行请填写商品", fieldKey: "details", rowIndex: 0, itemFieldKey: "productId" },
  "line item required errors should identify the row and item field"
)

assert.deepStrictEqual(
  getMobileFormValidationError(validationConfig, {
    title: "采购",
    scope: "products",
    details: [{ productId: 7, quantity: 0 }]
  }),
  { message: "采购明细第1行数量必须大于0", fieldKey: "details", rowIndex: 0, itemFieldKey: "quantity" },
  "invalid quantities should identify the exact quantity control"
)

assert.strictEqual(
  getMobileFormValidationError(validationConfig, {
    title: "采购",
    scope: "products",
    details: [{ productId: 7, quantity: 2 }]
  }),
  null,
  "valid forms should have no structured validation error"
)

assert.strictEqual(
  validateMobileForm(validationConfig, { title: "", scope: "products", details: [] }),
  "请填写申请标题",
  "the legacy string validation API should remain available as a wrapper"
)

const source = file => fs.readFileSync(path.resolve(__dirname, file), "utf8")
const formSheetSource = source("../src/views/mobile/feature/components/MobileFormSheet.vue")
const pageSource = source("../src/views/mobile/feature/index.vue")
const entityPickerSource = source("../src/views/mobile/feature/components/MobileEntityPicker.vue")
const lineItemsSource = source("../src/views/mobile/feature/components/MobileLineItemsEditor.vue")
const sheetStyles = source("../src/views/mobile/feature/components/mobileSheet.scss")

assert.ok(
  pageSource.includes(':validation-error="formSheet.validationError"') &&
    pageSource.includes("getMobileFormValidationError") &&
    pageSource.includes("handleMobileFormInput") &&
    (pageSource.match(/validationError: null/g) || []).length >= 3,
  "the page should keep a reactive structured validation target and clear it through the input path"
)

assert.ok(
  formSheetSource.includes('<label :id="fieldLabelId(field)" :for="fieldControlId(field)"') &&
    formSheetSource.includes(':aria-required="ariaBoolean(fieldIsRequired(field))"') &&
    formSheetSource.includes(':aria-invalid="ariaBoolean(isFieldInvalid(field))"') &&
    formSheetSource.includes(":role=\"isCustomField(field) ? 'group' : null\"") &&
    formSheetSource.includes(':aria-labelledby="isCustomField(field) ? fieldLabelId(field) : null"'),
  "native fields should have real labels and custom controls should expose labelled group semantics"
)

const summaryIndex = formSheetSource.indexOf('class="mobile-form-error-summary"')
const footerIndex = formSheetSource.indexOf('<footer class="form-footer')
assert.ok(
  summaryIndex > -1 && summaryIndex < footerIndex &&
    formSheetSource.includes('role="alert"') &&
    formSheetSource.includes('aria-live="assertive"') &&
    formSheetSource.includes('tabindex="-1"') &&
    formSheetSource.includes("转到错误字段") &&
    formSheetSource.includes("focusValidationError"),
  "the assertive error summary should sit above the action row and move focus to the invalid control"
)

assert.ok(
  formSheetSource.includes("fieldControlId(field)") &&
    formSheetSource.includes('data-field-key') &&
    lineItemsSource.includes(':data-row-index="index"') &&
    lineItemsSource.includes(':data-item-field-key="itemField.key"') &&
    lineItemsSource.includes(':tabindex="isItemInvalid(index, itemField) ? -1 : null"') &&
    lineItemsSource.includes(':aria-required="ariaBoolean(itemFieldIsRequired(itemField, row))"') &&
    lineItemsSource.includes('aria-label="搜索物料名称、编码或条码"') &&
    entityPickerSource.includes(':aria-label="searchAriaLabel"') &&
    formSheetSource.includes("if (!itemContainer) return this.focusErrorSummary()"),
  "stable field and row hooks should identify errors and custom search inputs should have an accessible name"
)

assert.ok(
  formSheetSource.includes("window.visualViewport") &&
    formSheetSource.includes('addEventListener("resize"') &&
    formSheetSource.includes('addEventListener("scroll"') &&
    formSheetSource.includes("requestAnimationFrame") &&
    formSheetSource.includes('removeEventListener("resize"') &&
    formSheetSource.includes('removeEventListener("scroll"') &&
    !formSheetSource.includes('event.key === "Tab"'),
  "visual viewport changes should re-scroll the active field without adding the later dialog tab trap"
)

function cssBlock(selector) {
  const start = sheetStyles.indexOf(selector)
  assert.ok(start > -1, `missing ${selector} styles`)
  const open = sheetStyles.indexOf("{", start)
  const close = sheetStyles.indexOf("}", open)
  return sheetStyles.slice(open + 1, close)
}

const formSheetBlock = cssBlock(".form-sheet")
const formBodyBlock = cssBlock(".form-body")
const actionBlock = cssBlock(".detail-actions")
const actionButtonBlock = cssBlock(".detail-actions button")

assert.ok(
  formSheetBlock.includes("height: 100%") && !formSheetBlock.includes("safe-area-inset"),
  "the form sheet should consume the already safe-area-constrained mask without subtracting the inset twice"
)
assert.ok(
  !formBodyBlock.includes("168px") &&
    formBodyBlock.includes("scroll-padding-bottom") &&
    sheetStyles.includes(".mobile-form-field:last-child") &&
    sheetStyles.includes("scroll-margin-bottom"),
  "the form body should reserve one compact action row and keep its last field scrollable above it"
)
assert.ok(
  actionBlock.includes("display: flex") &&
    actionBlock.includes("flex-wrap: nowrap") &&
    /height:\s*(?:44|45|46|47|48)px/.test(actionButtonBlock),
  "mobile form actions should stay on one 44-48px row"
)
