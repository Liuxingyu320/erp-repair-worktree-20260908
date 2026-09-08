const assert = require("assert")
const fs = require("fs")
const path = require("path")

const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)

const requiredComponentFiles = [
  "MobileDetailSheet.vue",
  "MobileFormSheet.vue",
  "MobileActionDialog.vue",
  "MobileConfirmDialog.vue",
  "MobileLineItemsEditor.vue",
  "MobileEntityPicker.vue"
]

requiredComponentFiles.forEach(fileName => {
  const absolutePath = path.resolve(__dirname, "../src/views/mobile/feature/components", fileName)
  assert.ok(fs.existsSync(absolutePath), `${fileName} should exist as a mobile feature component`)
})

assert.ok(
  featurePageSource.includes("import MobileDetailSheet") &&
    featurePageSource.includes("import MobileFormSheet") &&
    featurePageSource.includes("import MobileActionDialog") &&
    featurePageSource.includes("import MobileConfirmDialog") &&
    featurePageSource.includes("<mobile-detail-sheet") &&
    featurePageSource.includes("<mobile-form-sheet") &&
    featurePageSource.includes("<mobile-action-dialog") &&
    featurePageSource.includes("<mobile-confirm-dialog"),
  "mobile feature page should compose detail, form, action dialog, and confirm dialog components"
)

assert.ok(
  !featurePageSource.includes("$modal.confirm") &&
    !featurePageSource.includes("this.$confirm"),
  "mobile feature page should use a mobile confirm dialog instead of desktop confirmation overlays"
)

assert.ok(
  !featurePageSource.includes(".detail-mask {") &&
    !featurePageSource.includes(".form-mask {"),
  "mobile feature page should not keep stale sheet overlay styles that can override component z-index and mobile viewport fixes"
)

assert.ok(
  featurePageSource.includes("featureSearchConfigs") &&
    !featurePageSource.includes("const MOBILE_SEARCH_CONFIG = {"),
  "mobile search config should be moved out of the feature page"
)

assert.ok(
  featurePageSource.includes("emptyListText") &&
    featurePageSource.includes("当前搜索没有匹配结果") &&
    featurePageSource.includes("当前筛选没有待处理数据") &&
    featurePageSource.includes("请先选择") &&
    featurePageSource.includes("当前组织不能使用该功能"),
  "mobile feature page should show actionable empty states instead of a generic no-data message"
)

assert.ok(
  featurePageSource.includes("mobileFormPayloads") &&
    featurePageSource.includes("mobileValidation") &&
    !featurePageSource.includes("parseMobileLineItems(value)"),
  "mobile form payload and validation logic should be outside the feature page"
)

const formSheetSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileFormSheet.vue"),
  "utf8"
)

assert.ok(
  formSheetSource.includes("MobileEntityPicker") &&
    formSheetSource.includes("MobileLineItemsEditor") &&
    formSheetSource.includes("ImageUpload") &&
    formSheetSource.includes("field.type === 'image-upload'") &&
    formSheetSource.includes(":accept=\"field.accept || 'image/*'\"") &&
    formSheetSource.includes(":capture=\"field.capture || 'environment'\"") &&
    formSheetSource.includes("context-dept") &&
    formSheetSource.includes("mobileSheet.scss") &&
    formSheetSource.includes("form-body") &&
    formSheetSource.includes("form-footer") &&
    formSheetSource.includes("submitModes") &&
    formSheetSource.includes("$emit(\"submit\""),
  "mobile form sheet should render entity pickers, image upload, line-item editor, scoped sheet styles, context fields, fixed footer actions, and multiple submit modes"
)

const mobileSheetSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/mobileSheet.scss"),
  "utf8"
)
const overlayStackSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/mobileOverlayStack.js"),
  "utf8"
)

function extractCssBlock(source, selector) {
  const start = source.indexOf(selector + " {")
  if (start === -1) return ""
  const end = source.indexOf("}", start)
  return source.slice(start, end > -1 ? end : undefined)
}

const formBodyBlock = extractCssBlock(mobileSheetSource, ".form-body")
const formSheetBlock = extractCssBlock(mobileSheetSource, ".form-sheet")

assert.ok(
  !overlayStackSource.includes("appendChild") &&
    overlayStackSource.includes("mobile-overlay-open") &&
    overlayStackSource.includes("activeOverlayClasses") &&
    overlayStackSource.includes("releaseAllMobileOverlays") &&
    overlayStackSource.includes("window.scrollTo"),
  "mobile feature overlays should stay in the Vue tree and share only a scroll-lock stack"
)

assert.ok(
  formBodyBlock.includes("flex: 1 1 auto") &&
    formBodyBlock.includes("overflow-y: auto") &&
    formBodyBlock.includes("-webkit-overflow-scrolling: touch") &&
    formBodyBlock.includes("scroll-padding-bottom") &&
    mobileSheetSource.includes(".form-footer") &&
    mobileSheetSource.includes("position: sticky") &&
    mobileSheetSource.includes("bottom: 0"),
  "mobile form sheet should keep the body scrollable while the save/submit footer stays reachable"
)

assert.ok(
  formSheetSource.includes("lockFormSheetBody") &&
    formSheetSource.includes('mountMobileOverlay("mobile-form-sheet-open")') &&
    formSheetSource.includes("releaseMobileOverlay") &&
    formSheetSource.includes("mobile-form-sheet-open"),
  "mobile form sheet should lock page scroll without moving its Vue-owned DOM node"
)

assert.ok(
  mobileSheetSource.includes("height: var(--mobile-viewport-height") &&
    /(^|\n)\s*height:\s*100%/.test(formSheetBlock) &&
    /(^|\n)\s*max-height:\s*100%/.test(formSheetBlock) &&
    mobileSheetSource.includes("font-size: 16px"),
  "the viewport-sized mask should constrain a full-height form sheet with 16px controls to avoid iOS keyboard zoom"
)

const lineItemsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileLineItemsEditor.vue"),
  "utf8"
)

assert.ok(
  lineItemsSource.includes("stock-picker-sheet") &&
    lineItemsSource.includes("loadPickerOptions") &&
    lineItemsSource.includes("confirmStockSelection") &&
    lineItemsSource.includes("参考成本价") &&
    lineItemsSource.includes("showsReferenceCost") &&
    lineItemsSource.includes("已选"),
  "mobile line-item editor should support a replenishment stock picker drawer with selected count and reference cost"
)

assert.ok(
  lineItemsSource.includes("lineAmount(row)") &&
    lineItemsSource.includes("this.showsReferenceCost") &&
    lineItemsSource.includes("this.referenceCost(row)"),
  "mobile replenishment line items should calculate line subtotal from the visible reference cost"
)

assert.ok(
  lineItemsSource.includes("referenceTotalAmount") &&
    lineItemsSource.includes("selectedPickerTotalAmount") &&
    lineItemsSource.includes("参考总价"),
  "mobile replenishment should show the reference total in both the stock picker and selected line items"
)

assert.ok(
  lineItemsSource.includes("lockStockPickerBody") &&
    lineItemsSource.includes('mountMobileOverlay("mobile-stock-picker-open")') &&
    lineItemsSource.includes("releaseMobileOverlay") &&
    lineItemsSource.includes("mobile-stock-picker-open"),
  "mobile replenishment stock picker should lock page scroll without moving its Vue-owned DOM node"
)

function extractZIndex(source, selector) {
  const block = extractCssBlock(source, selector)
  const match = /z-index:\s*(\d+)/.exec(block)
  return match ? Number(match[1]) : NaN
}

const formMaskZIndex = extractZIndex(mobileSheetSource, ".form-mask")
const stockPickerMaskZIndex = extractZIndex(lineItemsSource, ".stock-picker-mask")

assert.ok(
  stockPickerMaskZIndex > formMaskZIndex,
  `mobile replenishment stock picker z-index (${stockPickerMaskZIndex}) should be above the create form sheet (${formMaskZIndex}) so Android WebView does not render it behind the form`
)

assert.ok(
  !/<button\b(?=[^>]*class="stock-option")[^>]*>[\s\S]*?<input[\s\S]*?<\/button>/.test(lineItemsSource),
  "mobile replenishment stock option should not render an input inside a button because Android Chrome/WebView treats nested interactive controls inconsistently"
)

assert.ok(
  !/body\.mobile-stock-picker-open\s*\{[\s\S]*?touch-action:\s*none[\s\S]*?\}/.test(lineItemsSource) &&
    /\.stock-picker-list\s*\{[\s\S]*?touch-action:\s*pan-y[\s\S]*?\}/.test(lineItemsSource),
  "mobile replenishment stock picker should allow Android touch scrolling inside the picker while the page body is locked"
)

const actionDialogSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileActionDialog.vue"),
  "utf8"
)

const detailSheetSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileDetailSheet.vue"),
  "utf8"
)

assert.ok(
  detailSheetSource.includes("lockDetailSheetBody") &&
    detailSheetSource.includes('mountMobileOverlay("mobile-detail-sheet-open")') &&
    detailSheetSource.includes("releaseMobileOverlay") &&
    detailSheetSource.includes("mobile-detail-sheet-open"),
  "mobile detail sheet should lock page scroll without moving its Vue-owned DOM node"
)

assert.ok(
  detailSheetSource.includes("isActionDisabled(action)") &&
    detailSheetSource.includes("actionButtonLabel(action)") &&
    detailSheetSource.includes('role="dialog"') &&
    detailSheetSource.includes('aria-modal="true"'),
  "mobile detail sheet should disable actions while full detail is loading and expose mobile dialog semantics"
)

assert.ok(
  mobileSheetSource.includes("z-index: 10000") &&
    mobileSheetSource.includes("height: var(--mobile-viewport-height") &&
    detailSheetSource.includes("body.mobile-detail-sheet-open"),
  "mobile detail sheet overlay should cover the app bottom nav and lock page scrolling while open"
)

assert.ok(
  actionDialogSource.includes("textarea") &&
    actionDialogSource.includes("mobileSheet.scss") &&
    actionDialogSource.includes("quantityRows") &&
    actionDialogSource.includes("shipmentOptions") &&
    actionDialogSource.includes("action.confirmText") &&
    actionDialogSource.includes("$emit(\"confirm\""),
  "mobile action dialog should support comments, confirmation text, quantity rows, scoped sheet styles, shipment selection, and confirm payloads"
)

assert.ok(
  actionDialogSource.includes("lockActionDialogBody") &&
    actionDialogSource.includes('mountMobileOverlay("mobile-action-dialog-open")') &&
    actionDialogSource.includes("releaseMobileOverlay") &&
    actionDialogSource.includes("mobile-action-dialog-open"),
  "mobile action dialog should lock page scroll without moving its Vue-owned DOM node"
)

assert.ok(
  actionDialogSource.includes("validateQuantityRows") &&
    actionDialogSource.includes("requiresQuantityRows") &&
    actionDialogSource.includes("allRemaining: true") &&
    actionDialogSource.includes('role="dialog"') &&
    actionDialogSource.includes('aria-modal="true"'),
  "mobile action dialog should reject empty quantity submissions and explicitly mark generated all-remaining rows"
)

assert.ok(
  actionDialogSource.includes("showTransferDiscrepancy") &&
    actionDialogSource.includes('value="RESHIP"') &&
    actionDialogSource.includes('value="RETURN_SOURCE"') &&
    actionDialogSource.includes('value="ACCEPT_ACTUAL"') &&
    actionDialogSource.includes('value="PENDING_QC"') &&
    actionDialogSource.includes('value="WRITE_OFF"') &&
    actionDialogSource.includes("discrepancyResponsibleParty") &&
    actionDialogSource.includes("discrepancyId"),
  "mobile discrepancy handling should require an explicit discrepancy, business decision, and responsible party"
)

assert.ok(
  actionDialogSource.includes("showTransferReceipt") &&
    actionDialogSource.includes("验收入库") &&
    actionDialogSource.includes("rejectedQuantity") &&
    actionDialogSource.includes("damagedQuantity") &&
    actionDialogSource.includes("transferReceiptShortage") &&
    actionDialogSource.includes("discrepancyNote"),
  "mobile transfer receipt should classify accepted, rejected, damaged, and shortage quantities with discrepancy evidence"
)

const confirmDialogSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileConfirmDialog.vue"),
  "utf8"
)

const dialogFocusSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/mobileDialogFocus.js"),
  "utf8"
)

assert.ok(
  dialogFocusSource.includes("focusElementAndVerify") &&
    dialogFocusSource.includes("activeManagers") &&
    dialogFocusSource.includes("resetMobileDialogFocusStackForTest"),
  "mobile dialogs should share verified focus movement and a topmost-only focus manager stack"
)

;[
  [formSheetSource, "activateFormSheetFocus", "deactivateFormSheetFocus"],
  [detailSheetSource, "activateDetailSheetFocus", "deactivateDetailSheetFocus"],
  [actionDialogSource, "activateActionDialogFocus", "deactivateActionDialogFocus"],
  [confirmDialogSource, "activateConfirmDialogFocus", "deactivateConfirmDialogFocus"],
  [lineItemsSource, "activateStockPickerFocus", "deactivateStockPickerFocus"]
].forEach(([source, activateMethod, deactivateMethod]) => {
  assert.ok(
    source.includes("createMobileDialogFocusManager") &&
      source.includes(activateMethod) &&
      source.includes(deactivateMethod) &&
      source.includes('tabindex="-1"'),
    `${activateMethod} should activate a shared focus trap and release it with its dialog lifecycle`
  )
})

function countGuardedNextTick(source, stateExpression, firstAction) {
  const pattern = new RegExp(
    "this\\.\\$nextTick\\(\\(\\) => \\{\\s*if \\(!" + stateExpression + "\\) return\\s*" + firstAction,
    "g"
  )
  return (source.match(pattern) || []).length
}

assert.strictEqual(
  countGuardedNextTick(formSheetSource, "this\\.open", "this\\.lockFormSheetBody\\(\\)"),
  2,
  "form sheet watcher and mounted callbacks should recheck open before locking or activating"
)
assert.strictEqual(
  countGuardedNextTick(detailSheetSource, "this\\.item", "this\\.lockDetailSheetBody\\(\\)"),
  2,
  "detail sheet watcher and mounted callbacks should recheck item before locking or activating"
)
assert.strictEqual(
  countGuardedNextTick(actionDialogSource, "this\\.open", "this\\.lockActionDialogBody\\(\\)"),
  2,
  "action dialog watcher and mounted callbacks should recheck open before locking or activating"
)
assert.strictEqual(
  countGuardedNextTick(confirmDialogSource, "this\\.open", "this\\.lockConfirmDialogBody\\(\\)"),
  2,
  "confirm dialog watcher and mounted callbacks should recheck open before locking or activating"
)
assert.strictEqual(
  countGuardedNextTick(lineItemsSource, "this\\.pickerOpen", "this\\.lockStockPickerBody\\(\\)"),
  2,
  "stock picker watcher and mounted callbacks should recheck pickerOpen before locking or activating"
)

assert.ok(
  /class="detail-loading"[^>]*role="status"[^>]*aria-live="polite"/.test(detailSheetSource) &&
    /class="detail-action-message"[^>]*:role="actionMessageRole"[^>]*:aria-live="actionMessageLive"/.test(detailSheetSource) &&
    detailSheetSource.includes("actionMessageType") &&
    detailSheetSource.includes('return this.actionMessageType === "error" ? "alert" : "status"') &&
    detailSheetSource.includes('return this.actionMessageType === "error" ? "assertive" : "polite"') &&
    !detailSheetSource.includes(".test(this.actionMessage"),
  "mobile detail live-region semantics should use explicit message type rather than guessing from text"
)

assert.ok(
  featurePageSource.includes(':action-message-type="actionMessageType"') &&
    featurePageSource.includes('actionMessageType: "status"') &&
    featurePageSource.includes('setActionMessage(message, type = "status")') &&
    featurePageSource.includes('this.setActionMessage(message, "error")') &&
    featurePageSource.includes('this.setActionMessage("完整详情加载中，请稍后再处理", "status")') &&
    (featurePageSource.match(/this\.actionMessage\s*=/g) || []).length === 1,
  "parent feature state should explicitly mark errors so text such as 操作未成功 remains an assertive alert"
)

assert.ok(
  /class="detail-action-message"[^>]*role="alert"[^>]*aria-live="assertive"/.test(actionDialogSource) &&
    /type="number"[^>]*:aria-label="quantityInputLabel\(row\)"/.test(actionDialogSource),
  "mobile action errors should be assertive and each quantity input should have an accessible name"
)

assert.ok(
  !/<article\b(?=[^>]*class="stock-option")[^>]*@click/.test(lineItemsSource) &&
    lineItemsSource.includes("stock-option-select") &&
    lineItemsSource.includes(":aria-label=\"stockOptionSelectLabel(option)\"") &&
    lineItemsSource.includes('role="status"') &&
    lineItemsSource.includes('aria-live="polite"'),
  "stock options should use an explicit keyboard button and announce selection state"
)

assert.ok(
  (lineItemsSource.match(/ref="stockPickerOpener"/g) || []).length === 2 &&
    lineItemsSource.includes('ref="lineItemsRoot"') &&
    lineItemsSource.includes("focusStockPickerAfterConfirm") &&
    lineItemsSource.includes("this.$nextTick(() => this.focusStockPickerAfterConfirm())") &&
    lineItemsSource.includes("focusElementAndVerify(this.$refs.stockPickerOpener)") &&
    lineItemsSource.includes("focusElementAndVerify(this.$refs.lineItemsRoot)"),
  "confirmed stock selection should focus the post-patch opener or line-items root after the old trigger is removed"
)

assert.ok(
  /\.detail-head button\s*\{[\s\S]*?width:\s*44px;[\s\S]*?height:\s*44px;/.test(mobileSheetSource) &&
    /\.detail-action-button\s*\{[\s\S]*?min-height:\s*44px;/.test(mobileSheetSource) &&
    /\.line-item header button\s*\{[\s\S]*?min-height:\s*44px;/.test(lineItemsSource) &&
    /\.stock-picker-head button\s*\{[\s\S]*?width:\s*44px;[\s\S]*?height:\s*44px;/.test(lineItemsSource),
  "mobile close, delete, select, quantity, and action controls should provide at least 44px touch targets"
)

assert.ok(
  confirmDialogSource.includes("lockConfirmDialogBody") &&
    confirmDialogSource.includes('mountMobileOverlay("mobile-confirm-dialog-open")') &&
    confirmDialogSource.includes("releaseMobileOverlay") &&
    confirmDialogSource.includes("mobile-confirm-dialog-open") &&
    confirmDialogSource.includes("mobile-confirm-mask"),
  "mobile confirm dialog should lock page scroll without moving its Vue-owned DOM node"
)

assert.ok(
  extractZIndex(confirmDialogSource, ".mobile-confirm-mask") > formMaskZIndex,
  "mobile confirm dialog z-index should be above the mobile form sheet"
)

assert.ok(
  featurePageSource.includes("!!action.confirmText") &&
    featurePageSource.includes("runDetailAction(action, actionPayload || {}, true)") &&
    featurePageSource.includes("runDetailAction(action, actionPayload = {}, confirmed = false)"),
  "mobile detail actions with confirmation text should use the mobile action dialog instead of the desktop ElementUI confirm box"
)

assert.ok(
    featurePageSource.includes("this.selectedItemEditSource = cloneMobileEditFormSource(detailRow)") &&
    featurePageSource.includes("openHydratedMobileEditForm(action)") &&
    featurePageSource.includes("fetchMobileFeatureDetail(") &&
    featurePageSource.includes("hydratedItem = cloneMobileEditFormSource(") &&
    featurePageSource.includes("refreshMobileTransferAvailability") &&
    featurePageSource.includes("listStock(query, { silentError: true })") &&
    featurePageSource.includes("this.closeItem()") &&
    featurePageSource.includes("this.$nextTick(() => this.openMobileForm(action, hydratedItem, refreshedData))"),
  "mobile transfer edit-form actions should refetch the full draft and source availability before closing the detail sheet and opening the form"
)

assert.ok(
  featurePageSource.includes("!action || this.actionLoadingKey || this.detailLoading") &&
    featurePageSource.includes("完整详情加载中，请稍后再处理") &&
    featurePageSource.includes("this.detailLoadFailed") &&
    featurePageSource.includes("完整详情加载失败，请重新加载详情后再处理") &&
    featurePageSource.includes("retrySelectedItemDetail") &&
    detailSheetSource.includes(":disabled=\"detailLoading || detailLoadFailed\"") &&
    detailSheetSource.includes("$emit('retry-detail')"),
  "all mobile business actions should fail closed until full details load and expose an in-place retry"
)

assert.ok(
  detailSheetSource.includes("detail-primary-footer") &&
    detailSheetSource.includes('v-for="action in primaryActions"') &&
    detailSheetSource.includes('v-for="action in secondaryActions"') &&
    detailSheetSource.indexOf("detail-primary-footer") > detailSheetSource.indexOf("</div>\n      <footer") &&
    featurePageSource.includes(":primary-actions=\"detailPrimaryActions\"") &&
    featurePageSource.includes(":secondary-actions=\"detailSecondaryActions\""),
  "mobile detail primary business actions should render in the non-scrolling footer while other actions remain separate"
)

assert.ok(
  actionDialogSource.includes(":review-fields=\"selectedReviewFields\"") === false &&
    featurePageSource.includes(":review-fields=\"selectedReviewFields\"") &&
    actionDialogSource.includes("<dl v-if=\"reviewFields.length\"") &&
    actionDialogSource.includes("<dt>{{ field.label }}</dt>") &&
    actionDialogSource.includes("<dd>{{ field.value }}</dd>"),
  "mobile action confirmation should render mapper-provided structured review fields"
)
