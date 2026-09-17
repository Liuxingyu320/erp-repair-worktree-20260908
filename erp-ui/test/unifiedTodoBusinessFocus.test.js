const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const {
  buildTodoFocusQuery,
  consumeTodoFocusQuery,
  findTodoFocusShipment,
  findTodoFocusRow,
  getTodoFocus,
  normalizeTodoFocusRow,
  resolveTodoFocusResult
} = require("../src/utils/todoBusinessFocus")
const { createTodoBusinessFocusMixin } = require("../src/mixins/todoBusinessFocus")
const { getMobileFeatureActions } = require("../src/views/mobile/feature/featureActions")

const focusCases = [
  ["INV_PURCHASE_QC", "purchase", "orderId", "qualityCheckPurchase"],
  ["INV_PURCHASE_RECEIVE", "purchase", "orderId", "receivePurchaseAll"],
  ["INV_SALES_NOTICE_CREATE", "sales", "orderId", "createDeliveryNotice"],
  ["INV_DELIVERY_EXECUTE", "outbound", "noticeId", "deliverDeliveryNoticeAll"],
  ["INV_TRANSFER_APPROVAL", "transferApproval", "transferId", "approveTransfer"],
  ["INV_TRANSFER_SOURCE_CONFIRM", "transfer", "transferId", "confirmTransferSource"],
  ["INV_TRANSFER_DELIVER", "transfer", "transferId", "deliverTransferAll"],
  ["INV_TRANSFER_DISCREPANCY", "transfer", "transferId", "handleTransferDiscrepancy"],
  ["INV_TRANSFER_RETURNED", "transfer", "transferId", "editReplenishment"],
  ["INV_TRANSFER_SOURCE_RESELECT", "transfer", "transferId", "editTransfer"],
  ["INV_STOCK_CHECK_APPROVAL", "stockCheck", "checkId", "approveStockCheck"],
  ["INV_STOCK_CHECK_EXECUTE", "stockCheck", "checkId", "saveStockCheckInput"],
  ["INV_STOCK_CHECK_RETURNED", "stockCheck", "checkId", "saveStockCheckInput"],
  ["INV_STOCK_CHECK_RESTART", "stockCheck", "checkId", "restartStockCheck"],
  ["INV_PURCHASE_RETURN_CONFIRM", "purchaseReturn", "returnId", "confirmPurchaseReturn"],
  ["INV_SALES_RETURN_CONFIRM", "salesReturn", "returnId", "confirmSalesReturn"],
  ["OA_LABOR_CONTRACT_SIGN", "laborContract", "contractId", "openLaborContract"],
  ["OA_SIGN_PACKAGE_SIGN", "signPackage", "packageId", "openSignPackage"]
]

focusCases.forEach(([todoType, featureKey, idKey, actionId]) => {
  const query = { todoType, businessId: "9007199254740999", contextDeptId: "20" }
  const focus = getTodoFocus(featureKey, query)
  assert.ok(focus, `${todoType} should be supported by ${featureKey}`)
  assert.strictEqual(focus.businessId, "9007199254740999", `${todoType} must preserve an exact string id`)
  assert.strictEqual(focus.actionId, actionId, `${todoType} should reuse the expected business action`)

  const requestQuery = buildTodoFocusQuery(featureKey, query)
  assert.strictEqual(requestQuery.businessId, "9007199254740999", `${todoType} should pass businessId to the list request`)
  assert.strictEqual(requestQuery[idKey], "9007199254740999", `${todoType} should apply its exact domain id`)
  assert.strictEqual(requestQuery.todoType, undefined, "todoType is a UI focus marker and must not leak to business APIs")

  const decoy = { [idKey]: "1" }
  const target = { [idKey]: "9007199254740999" }
  assert.strictEqual(findTodoFocusRow([decoy, target], focus), target, `${todoType} must select the exact row`)
  assert.strictEqual(findTodoFocusRow([decoy], focus), null, `${todoType} must never fall back to the first row`)
  assert.deepStrictEqual(resolveTodoFocusResult([decoy], focus), {
    status: "handled",
    row: null,
    actionId
  }, `${todoType} should report an already-handled item when the exact row is absent`)
})

const shipmentQuery = {
  todoType: "INV_TRANSFER_RECEIVE",
  businessId: "301",
  shipmentId: "301",
  transferId: "77"
}
const shipmentFocus = getTodoFocus("transfer", shipmentQuery)
assert.deepStrictEqual(buildTodoFocusQuery("transfer", shipmentQuery), {
  businessId: "301",
  shipmentId: "301",
  transferId: "77"
}, "transfer receipt must query the transfer and preserve the action-specific shipment id")
assert.strictEqual(findTodoFocusRow([
  { transferId: "301" },
  { transferId: "77", shipments: [{ shipmentId: "301" }] }
], shipmentFocus).transferId, "77", "transfer receipt must focus its parent transfer instead of confusing shipmentId with transferId")
assert.strictEqual(shipmentFocus.actionId, "receiveTransferShipment")
assert.strictEqual(shipmentFocus.shipmentId, "301", "transfer receipt must retain the exact shipment id")
assert.strictEqual(findTodoFocusShipment({ shipments: [
  { shipmentId: "1", status: "pending_receive" },
  { shipmentId: "301", status: "pending_receive" }
] }, shipmentFocus).shipmentId, "301", "focused receipt must select only its pending shipment")
assert.strictEqual(findTodoFocusShipment({ shipments: [
  { shipmentId: "1", status: "pending_receive" },
  { shipmentId: "301", status: "received" }
] }, shipmentFocus), null, "a completed or missing focused shipment must never fall back to the first pending shipment")
assert.ok(getTodoFocus("transfer", { todoType: "INV_TRANSFER_APPROVAL", businessId: "77" }),
  "the shared desktop transfer page must consume approval focus as well as the mobile approval alias")
assert.ok(getMobileFeatureActions("transfer", {
  status: "draft",
  _raw: { transferId: "77", status: "draft", toDeptId: "20" }
}, { selectedDeptType: "STORE", selectedDeptId: "20" }).some(action => action.id === "editReplenishment"),
"a returned transfer must expose its existing mobile edit flow on the generic transfer page")
assert.deepStrictEqual(
  getTodoFocus("transfer", {
    todoType: "INV_TRANSFER_RETURNED",
    businessId: "77"
  }).actionIdAliases,
  ["editTransfer"],
  "a returned todo must accept the manual-transfer editor when the draft is not a warehouse replenishment"
)

assert.deepStrictEqual(buildTodoFocusQuery("stock", {
  todoType: "INV_OUT_OF_STOCK",
  businessId: "20",
  contextDeptId: "20"
}), {
  businessId: "20",
  contextDeptId: "20",
  shopDeptId: "20",
  riskFilter: "outOfStock",
  stockStatus: "empty"
}, "out-of-stock risk should open the exact organization and filter")
assert.deepStrictEqual(buildTodoFocusQuery("stock", {
  todoType: "INV_LOW_STOCK",
  businessId: "21"
}), {
  businessId: "21",
  shopDeptId: "21",
  riskFilter: "lowStock",
  stockStatus: "low"
}, "low-stock risk should open the exact organization and filter")
const riskFocus = getTodoFocus("stock", { todoType: "INV_LOW_STOCK", businessId: "21" })
assert.strictEqual(resolveTodoFocusResult([], riskFocus).status, "handled",
  "an empty exact risk filter should also be treated as already handled")
assert.strictEqual(resolveTodoFocusResult([{ stockId: "1" }], riskFocus).status, "filtered",
  "a non-empty risk filter should remain on the filtered stock page")

assert.strictEqual(getTodoFocus("sales", {
  todoType: "INV_PURCHASE_QC",
  businessId: "4"
}), null, "a todo must not be consumed by the wrong business page")
assert.strictEqual(getTodoFocus("purchase", { todoType: "INV_PURCHASE_QC" }), null,
  "an exact business id is required before focusing")

const lossyFocus = getTodoFocus("purchase", {
  todoType: "INV_PURCHASE_QC",
  businessId: "9999999999999999"
})
const lossyResponse = JSON.parse('{"orderId":9999999999999999,"status":"submitted"}')
assert.notStrictEqual(String(lossyResponse.orderId), lossyFocus.targetId,
  "fixture must demonstrate browser JSON precision loss")
const normalizedLossyResponse = normalizeTodoFocusRow(lossyResponse, lossyFocus)
assert.strictEqual(normalizedLossyResponse.orderId, "9999999999999999",
  "a successful exact detail request must preserve the authoritative requested id")
assert.strictEqual(findTodoFocusRow([normalizedLossyResponse], lossyFocus), normalizedLossyResponse,
  "precision loss in the response must not turn a successful exact request into an already-handled result")

const consumed = consumeTodoFocusQuery({
  todoType: "INV_PURCHASE_QC",
  businessId: "9",
  orderId: "9",
  status: "submitted"
})
assert.deepStrictEqual(consumed, {
  businessId: "9",
  orderId: "9",
  status: "submitted"
}, "consuming focus must remove only the transient todoType flag")

async function runDesktopFocusBehavior() {
  const opened = []
  const warnings = []
  const dispatched = []
  const replaced = []
  const mixin = createTodoBusinessFocusMixin({
    featureKey: "salesReturn",
    loadFocusedRow(targetId) {
      return Promise.resolve({ data: { returnId: targetId, status: "submitted" } })
    },
    actions: {
      confirmSalesReturn(row) { opened.push(row.returnId) }
    }
  })
  const context = { InventoryDraftRecovery: {},
    ...mixin.data(),
    queryParams: { pageNum: 3, pageSize: 10 },
    $route: { path: "/inventory/salesReturn", query: {
      todoType: "INV_SALES_RETURN_CONFIRM", businessId: "44", status: "submitted"
    } },
    $router: { replace: location => { replaced.push(location); return Promise.resolve() } },
    $modal: { msgWarning: message => warnings.push(message) },
    $store: { dispatch: type => { dispatched.push(type); return Promise.resolve() } }
  }
  Object.assign(context, mixin.methods)
  mixin.created.call(context)
  assert.strictEqual(context.queryParams.pageNum, 1, "desktop focused queries must start on the first page")
  assert.strictEqual(context.queryParams.returnId, "44", "desktop focused queries must use the exact domain id")
  let listCalled = false
  const focusedResponse = await context.loadTodoBusinessList(() => {
    listCalled = true
    return Promise.resolve({ rows: [{ returnId: "1" }], total: 1 })
  })
  assert.strictEqual(listCalled, false, "a desktop focus must use the detail API instead of an unfiltered list endpoint")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(focusedResponse)), {
    rows: [{ returnId: "44", status: "submitted" }], total: 1
  }, "the exact detail response should become the one-row focused list")
  await context.handleTodoFocusRows(focusedResponse.rows)
  assert.deepStrictEqual(opened, ["44"], "desktop focus must invoke the existing action for the exact row")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(replaced[0].query)), {
    businessId: "44",
    status: "submitted"
  }, "desktop focus must be transient while preserving action-specific query parameters")

  const missing = {
    ...mixin.data(),
    queryParams: { pageNum: 1, pageSize: 10 },
    $route: { path: "/inventory/salesReturn", query: {
      todoType: "INV_SALES_RETURN_CONFIRM", businessId: "45"
    } },
    $router: { replace: () => Promise.resolve() },
    $modal: context.$modal,
    $store: context.$store
  }
  Object.assign(missing, mixin.methods)
  mixin.created.call(missing)
  await missing.handleTodoFocusRows([{ returnId: "1" }])
  assert.ok(warnings.includes("事项已处理"), "desktop missing rows must use the same handled copy")
  assert.ok(dispatched.includes("todo/refreshSummaries"), "desktop missing rows must refresh the Todo Store")
}

function loadMobileFeatureComponent(actionFactory) {
  const source = fs.readFileSync(path.resolve(__dirname, "../src/views/mobile/feature/index.vue"), "utf8")
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  const script = match[1]
    .replace(/^import .*$/gm, "")
    .replace(/export default/, "module.exports =")
  const emptyModule = {
    MOBILE_FORM_CONFIG: {},
    MOBILE_EXPORT_CONFIG: {},
    getMobileFormConfig: () => null,
    getMobileSearchConfig: () => null,
    getStockStatusFilters: () => [],
    createMobileFormData: () => ({}),
    buildMobileFormPayload: () => ({}),
    validateMobileForm: () => ({ valid: true }),
    getMobileBottomNav: () => [],
    getMobileRouteAccessDecision: () => ({ allowed: true }),
    mapMobileFeatureRows: (featureKey, rows) => rows,
    mapMobileMineRows: () => [],
    getMobileFeatureActions: actionFactory,
    partitionMobileDetailActions: actions => ({ primary: actions || [], secondary: [] })
  }
  const context = {
    InventoryDraftRecovery: {},
    module: { exports: {} },
    exports: {},
    require(request) {
      if (request.includes("todoBusinessFocus")) return require("../src/utils/todoBusinessFocus")
      return emptyModule
    },
    getSelectedDeptContext: () => ({}),
    MobileDetailSheet: {},
    MobileFormSheet: {},
    MobileActionDialog: {},
    MobileCustomerRecordDialog: {},
    MobileConfirmDialog: {},
    Promise,
    Object,
    Array,
    Set,
    String,
    Number,
    Date,
    Math,
    window: { setTimeout }
  }
  vm.runInNewContext(script, context)
  return context.module.exports
}

async function runMobileFocusBehavior() {
  const actions = [{ id: "qualityCheckPurchase", confirmText: "确认质检" }]
  const component = loadMobileFeatureComponent(() => actions)
  assert.strictEqual(typeof component.methods.handleLoadedTodoFocus, "function",
    "the generic mobile page should consume a focused list result")

  const replacements = []
  const handledActions = []
  const dispatched = []
  const warnings = []
  const target = { orderId: "8", status: "submitted" }
  const foundContext = {
    todoFocus: getTodoFocus("purchase", { todoType: "INV_PURCHASE_QC", businessId: "8" }),
    $route: { path: "/mobile/purchase", query: { todoType: "INV_PURCHASE_QC", businessId: "8", orderId: "8" } },
    $router: { replace: location => { replacements.push(location); return Promise.resolve() } },
    $store: { dispatch: type => { dispatched.push(type); return Promise.resolve() } },
    $modal: { msgWarning: message => warnings.push(message) },
    featureKey: "purchase",
    selectedDeptType: "WAREHOUSE",
    selectedDeptId: "20",
    canShowDetailAction: () => true,
    selectedItem: null,
    skipNextRouteApply: false
  }
  Object.assign(foundContext, component.methods)
  foundContext.openItem = function openItem(row) { this.selectedItem = row; return Promise.resolve(row) }
  foundContext.openActionDialog = function openActionDialog(action) { handledActions.push(action.id) }
  foundContext.canShowDetailAction = () => true
  await foundContext.handleLoadedTodoFocus([{ orderId: "1" }, target], false)
  assert.strictEqual(foundContext.selectedItem, target, "the generic page must open only the exact focused row")
  assert.deepStrictEqual(handledActions, [], "the focused row must remain on detail until the user chooses an action")
  assert.strictEqual(foundContext.todoFocusedAction.id, "qualityCheckPurchase",
    "the detail page must retain the focused action for an explicit user choice")
  foundContext.actionLoadingKey = ""
  foundContext.detailLoading = false
  foundContext.handleDetailAction(actions[0])
  assert.deepStrictEqual(handledActions, ["qualityCheckPurchase"],
    "choosing the detail action must still open its existing confirmation dialog")
  assert.strictEqual(foundContext.todoFocus, null, "focus must be consumed once")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(replacements[0])), {
    path: "/mobile/purchase",
    query: { businessId: "8", orderId: "8" }
  }, "focus consumption should remove todoType while preserving exact action ids")

  const returnedComponent = loadMobileFeatureComponent(getMobileFeatureActions)
  const returnedWarnings = []
  const createReturnedContext = row => {
    const query = {
      todoType: "INV_TRANSFER_RETURNED",
      businessId: String(row.transferId),
      transferId: String(row.transferId)
    }
    const context = {
      todoFocus: getTodoFocus("transfer", query),
      $route: { path: "/mobile/transfer", query },
      $router: { replace: () => Promise.resolve() },
      $store: { dispatch: () => Promise.resolve() },
      $modal: { msgWarning: message => returnedWarnings.push(message) },
      featureKey: "transfer",
      selectedDeptType: "STORE",
      selectedDeptId: "20",
      userPermissions: ["inv:transfer:edit"],
      selectedItem: null,
      skipNextRouteApply: false
    }
    Object.assign(context, returnedComponent.methods)
    context.openItem = function openItem(item) {
      this.selectedItem = item
      return Promise.resolve(item)
    }
    context.canShowDetailAction = () => true
    return context
  }

  const returnedWarehouseRow = {
    transferId: "78",
    status: "draft",
    _raw: {
      transferId: "78",
      status: "draft",
      transferType: "warehouse",
      fromDeptId: "10",
      toDeptId: "20"
    }
  }
  const returnedWarehouseContext = createReturnedContext(returnedWarehouseRow)
  await returnedWarehouseContext.handleLoadedTodoFocus([returnedWarehouseRow], false)
  assert.strictEqual(returnedWarehouseContext.todoFocusedAction.id, "editReplenishment",
    "a returned warehouse request must focus the warehouse-replenishment editor on mobile")

  const returnedCrossStoreRow = {
    transferId: "79",
    status: "draft",
    _raw: {
      transferId: "79",
      status: "draft",
      transferType: "cross_store",
      fromDeptId: "10",
      toDeptId: "20"
    }
  }
  const returnedCrossStoreContext = createReturnedContext(returnedCrossStoreRow)
  await returnedCrossStoreContext.handleLoadedTodoFocus([returnedCrossStoreRow], false)
  assert.strictEqual(returnedCrossStoreContext.todoFocusedAction.id, "editTransfer",
    "a returned cross-store request must focus the manual-transfer editor on mobile")
  assert.deepStrictEqual(returnedWarnings, [],
    "valid returned transfer drafts must not be misreported as already handled")

  const discrepancyRow = {
    transferId: "80",
    status: "discrepancy",
    _raw: {
      transferId: "80",
      status: "discrepancy",
      fromDeptId: "10",
      toDeptId: "20",
      discrepancies: [{ discrepancyId: "801", status: "OPEN", version: 1 }]
    }
  }
  const discrepancyContext = createReturnedContext(discrepancyRow)
  const discrepancyQuery = {
    todoType: "INV_TRANSFER_DISCREPANCY",
    businessId: "80",
    transferId: "80"
  }
  discrepancyContext.todoFocus = getTodoFocus("transfer", discrepancyQuery)
  discrepancyContext.$route = { path: "/mobile/transfer", query: discrepancyQuery }
  await discrepancyContext.handleLoadedTodoFocus([discrepancyRow], false)
  assert.strictEqual(discrepancyContext.todoFocusedAction.id, "handleTransferDiscrepancy",
    "a transfer-discrepancy todo must focus the executable mobile discrepancy action")

  const missingContext = {
    todoFocus: getTodoFocus("purchase", { todoType: "INV_PURCHASE_QC", businessId: "9" }),
    $route: { path: "/mobile/purchase", query: { todoType: "INV_PURCHASE_QC", businessId: "9" } },
    $router: { replace: () => Promise.resolve() },
    $store: { dispatch: type => { dispatched.push(type); return Promise.resolve() } },
    $modal: { msgWarning: message => warnings.push(message) },
    skipNextRouteApply: false
  }
  Object.assign(missingContext, component.methods)
  await missingContext.handleLoadedTodoFocus([{ orderId: "1" }], false)
  assert.ok(warnings.includes("事项已处理"), "a missing exact row should tell the user it was already handled")
  assert.ok(dispatched.includes("todo/refreshSummaries"), "a missing exact row should refresh the shared Todo Store")

  const transferActions = [{ id: "receiveTransferShipment", confirmText: "确认收货" }]
  const transferComponent = loadMobileFeatureComponent(() => transferActions)
  const transferFocus = getTodoFocus("transfer", shipmentQuery)
  const focusedActionObjects = []
  const transferContext = {
    todoFocus: transferFocus,
    $route: { path: "/mobile/transfer", query: shipmentQuery },
    $router: { replace: () => Promise.resolve() },
    $store: { dispatch: type => { dispatched.push(type); return Promise.resolve() } },
    $modal: { msgWarning: message => warnings.push(message) },
    featureKey: "transfer",
    selectedDeptType: "STORE",
    selectedDeptId: "20",
    selectedItem: null,
    skipNextRouteApply: false
  }
  Object.assign(transferContext, transferComponent.methods)
  transferContext.openItem = function openItem(row) {
    this.selectedItem = Object.assign({}, row, {
      _raw: Object.assign({}, row._raw || {}, {
        shipments: [{ shipmentId: "301", status: "pending_receive" }]
      })
    })
    return Promise.resolve(this.selectedItem)
  }
  transferContext.openActionDialog = action => focusedActionObjects.push(action)
  transferContext.canShowDetailAction = () => true
  const focusedTransferRow = {
    transferId: "77",
    _raw: { transferId: "77" }
  }
  await transferContext.handleLoadedTodoFocus([focusedTransferRow], false)
  assert.strictEqual(focusedActionObjects.length, 0,
    "a focused receipt must show transfer detail before opening its confirmation dialog")
  assert.strictEqual(transferContext.todoFocusedAction.preferredShipmentId, "301",
    "the detail page must retain the exact focused shipment id")
  transferContext.actionLoadingKey = ""
  transferContext.detailLoading = false
  transferContext.handleDetailAction(transferActions[0])
  assert.strictEqual(focusedActionObjects[0].preferredShipmentId, "301",
    "the explicit receipt action must pass the exact focused shipment id to its dialog")

  const missingShipmentContext = Object.assign({}, transferContext, {
    todoFocus: transferFocus,
    selectedItem: null,
    $route: { path: "/mobile/transfer", query: shipmentQuery }
  })
  missingShipmentContext.openItem = function openItem(row) { this.selectedItem = row; return Promise.resolve(row) }
  const actionsBeforeMissing = focusedActionObjects.length
  await missingShipmentContext.handleLoadedTodoFocus([{
    transferId: "77",
    _raw: { transferId: "77", shipments: [{ shipmentId: "1", status: "pending_receive" }] }
  }], false)
  assert.strictEqual(focusedActionObjects.length, actionsBeforeMissing,
    "a missing mobile shipment must not open an action for another pending shipment")
  assert.ok(warnings.includes("事项已处理"), "a missing mobile shipment must be reported as already handled")

  const staleDetailContext = Object.assign({}, transferContext, {
    todoFocus: transferFocus,
    selectedItem: null,
    detailLoadFailed: false,
    $route: { path: "/mobile/transfer", query: shipmentQuery }
  })
  staleDetailContext.openItem = function openItem(row) {
    this.selectedItem = row
    this.detailLoadFailed = true
    return Promise.resolve(row)
  }
  const actionsBeforeStaleDetail = focusedActionObjects.length
  await staleDetailContext.handleLoadedTodoFocus([{
    transferId: "77",
    _raw: { transferId: "77", shipments: [{ shipmentId: "301", status: "pending_receive" }] }
  }], false)
  assert.strictEqual(focusedActionObjects.length, actionsBeforeStaleDetail,
    "a failed fresh detail read must not reuse a stale list shipment")

  const throwingDispatchContext = {
    todoFocus: getTodoFocus("purchase", { todoType: "INV_PURCHASE_QC", businessId: "404" }),
    $route: { path: "/mobile/purchase", query: { todoType: "INV_PURCHASE_QC", businessId: "404" } },
    $router: { replace: () => Promise.resolve() },
    $store: { dispatch: () => { throw new Error("store unavailable") } },
    $modal: { msgWarning: message => warnings.push(message) },
    skipNextRouteApply: false
  }
  Object.assign(throwingDispatchContext, component.methods)
  await assert.doesNotReject(
    () => throwingDispatchContext.handleLoadedTodoFocus([{ orderId: "1" }], false),
    "a synchronous Todo Store failure must not replace the handled result"
  )
}

function loadVueComponent(relativePath, overrides) {
  const source = fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, `${relativePath} must contain a script block`)
  const script = match[1]
    .replace(/import\s*\{[\s\S]*?\}\s*from\s*["'][^"']+["']\s*/g, "")
    .replace(/^import .*$/gm, "")
    .replace(/export default/, "module.exports =")
  const context = Object.assign({
    module: { exports: {} },
    exports: {},
    require(request) {
      if (request === '@/utils/inventoryQuantity') return require('../src/utils/inventoryQuantity')
      if (request === '@/utils/uiOperationScope') return require('../src/utils/uiOperationScope')
      if (request === '@/utils/oaPurchaseContext') return require('../src/utils/oaPurchaseContext')
      if (request === '@/utils/todoBusinessFocus') return require('../src/utils/todoBusinessFocus')
      if (request.includes("todoBusinessFocus")) return require("../src/mixins/todoBusinessFocus")
      if (request.includes("signDateTime")) return require("../src/utils/signDateTime")
      if (request === "./mobileSignPackagePolicy") {
        return require("../src/views/mobile/signPackage/mobileSignPackagePolicy")
      }
      return {}
    },
    InventoryDraftRecovery: {}, ApprovalCommandRecovery: {},
    createApprovalCommandRecovery: () => ({}),
    getSelectedDeptId: () => '20',
    Treeselect: {},
    ImageUpload: {},
    approvalStatusLabel: status => status || "",
    approvalStatusType: () => "info",
    getBusinessEmptyText: () => "",
    Promise,
    Object,
    Array,
    Set,
    String,
    Number,
    Date,
    Math,
    process: { env: { VUE_APP_BASE_API: "", VUE_APP_SIGN_EXCEL_IMPORT_ENABLED: "true" } },
    getSignTaskCapabilities: () => Promise.resolve({ data: { coreEnabled: true, excelImportEnabled: true } }),
    Blob: function Blob(parts, options) { this.parts = parts; this.type = options && options.type },
    URL: { createObjectURL: () => "blob:test", revokeObjectURL: () => {} },
    window: { addEventListener: () => {}, removeEventListener: () => {}, devicePixelRatio: 1 }
  }, overrides || {})
  vm.runInNewContext(script, context)
  return context.module.exports
}

function makePersonalContext(component, routeQuery, events) {
  const mixin = component.mixins && component.mixins[0]
  const context = Object.assign(
    {},
    mixin && typeof mixin.data === "function" ? mixin.data() : {},
    component.data(),
    mixin && mixin.methods,
    component.methods,
    {
    $route: { path: events.path, query: routeQuery },
    $router: {
      replace(location) { events.replaced.push(location); return Promise.resolve() },
      back() {}
    },
    $store: {
      dispatch(type) { events.dispatched.push(type); return Promise.resolve() }
    },
    $modal: {
      msgWarning(message) { events.warnings.push(message) },
      msgSuccess() {}
    },
    $nextTick(callback) { if (typeof callback === "function") callback() },
    loadContractFile() {},
    loadActiveDocumentFiles() {},
    initSignatureCanvas() {},
    revokeFileObjectUrl() {},
    revokeObjectUrls() {}
    }
  )
  if (component.computed && component.computed.excelImportFrontendEnabled) {
    Object.defineProperty(context, "excelImportFrontendEnabled", {
      configurable: true,
      get() { return component.computed.excelImportFrontendEnabled.call(context) }
    })
    Object.defineProperty(context, "excelImportEnabled", {
      configurable: true,
      get() { return component.computed.excelImportEnabled.call(context) }
    })
  }
  return context
}

async function runPersonalFocusBehavior() {
  const exactId = "9007199254740999"

  const contractEvents = { path: "/mobile/contract", details: [], lists: 0, replaced: [], dispatched: [], warnings: [] }
  const contractComponent = loadVueComponent("../src/views/mobile/contract/index.vue", {
    listMyLaborContracts() { contractEvents.lists += 1; return Promise.resolve({ rows: [{ contractId: "1" }] }) },
    getMyLaborContract(id) {
      contractEvents.details.push(String(id))
      return Promise.resolve({ data: { contractId: Number(id), status: "pending_sign" } })
    },
    signMyLaborContract() { return Promise.resolve({ data: {} }) },
    downloadLaborContractFile() { return Promise.resolve({}) }
  })
  const contractContext = makePersonalContext(contractComponent, {
    todoType: "OA_LABOR_CONTRACT_SIGN",
    businessId: exactId,
    source: "oa"
  }, contractEvents)
  await contractComponent.created.call(contractContext)
  assert.deepStrictEqual(contractEvents.details, [exactId],
    "labor todo focus must load its exact owned detail by businessId")
  assert.strictEqual(contractEvents.lists, 0,
    "labor todo focus must not use the first row from the ordinary list")
  assert.strictEqual(String(contractContext.selectedContract.contractId), exactId,
    "labor exact detail must preserve the authoritative requested id")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(contractEvents.replaced[0].query)), {
    businessId: exactId,
    source: "oa"
  }, "labor focus consumption must remove only todoType")
  const recheckedContract = await contractContext.recheckTodoPersonalAction(exactId)
  assert.strictEqual(String(recheckedContract.contractId), exactId,
    "personal action rechecks must preserve the authoritative requested id after a lossy JSON response")

  const packageEvents = { path: "/mobile/sign-package", details: [], lists: 0, replaced: [], dispatched: [], warnings: [] }
  const packageComponent = loadVueComponent("../src/views/mobile/signPackage/index.vue", {
    listMySignPackages() { packageEvents.lists += 1; return Promise.resolve({ rows: [{ packageId: "1" }] }) },
    getMySignPackage(id) {
      packageEvents.details.push(String(id))
      return Promise.resolve({ data: {
        packageId: Number(id),
        status: "pending_sign",
        signDeadline: "2099-12-31 23:59:59",
        documents: []
      } })
    },
    confirmSignPackageDocumentRead() { return Promise.resolve({ data: {} }) },
    downloadMySignPackageCertificate() { return Promise.resolve({}) },
    downloadMySignPackageDocument() { return Promise.resolve({}) },
    signMySignPackage() { return Promise.resolve({ data: {} }) }
  })
  const packageContext = makePersonalContext(packageComponent, {
    todoType: "OA_SIGN_PACKAGE_SIGN",
    businessId: exactId,
    source: "oa"
  }, packageEvents)
  await packageComponent.created.call(packageContext)
  assert.deepStrictEqual(packageEvents.details, [exactId],
    "sign-package todo focus must load its exact owned detail by businessId")
  assert.strictEqual(packageEvents.lists, 0,
    "sign-package todo focus must not use the first row from the ordinary list")
  assert.strictEqual(String(packageContext.selectedPackage.packageId), exactId,
    "sign-package exact detail must preserve the authoritative requested id")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(packageEvents.replaced[0].query)), {
    businessId: exactId,
    source: "oa"
  }, "sign-package focus consumption must remove only todoType")

  const onboardEvents = { path: "/mobile/sign-package", packageDetails: 0, replaced: [], dispatched: [], warnings: [] }
  const onboardComponent = loadVueComponent("../src/views/mobile/signPackage/index.vue", {
    listMySignPackages() { return Promise.resolve({ rows: [] }) },
    listMyOnboardSignDataRequests() { return Promise.resolve({ data: [] }) },
    getMySignPackage() {
      onboardEvents.packageDetails += 1
      return Promise.resolve({ data: {} })
    },
    confirmSignPackageDocumentRead() { return Promise.resolve({ data: {} }) },
    downloadMySignPackageCertificate() { return Promise.resolve({}) },
    downloadMySignPackageDocument() { return Promise.resolve({}) },
    signMySignPackage() { return Promise.resolve({ data: {} }) }
  })
  const onboardContext = makePersonalContext(onboardComponent, {
    todoType: "OA_SIGN_ONBOARD_DATA_REQUEST",
    businessId: exactId,
    requestId: exactId,
    source: "oa"
  }, onboardEvents)
  await onboardComponent.created.call(onboardContext)
  assert.strictEqual(String(onboardContext.selectedDataRequest.requestId), exactId,
    "onboard employee todos must preserve the exact request id inside My Signings")
  assert.strictEqual(onboardEvents.packageDetails, 0,
    "an onboard request deep link must not be mistaken for a sign-package id")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(onboardEvents.replaced[0].query)), {
    businessId: exactId,
    source: "oa"
  }, "onboard request focus consumption must keep durable context while removing transient routing markers")

  const missingEvents = { path: "/mobile/contract", details: [], lists: 0, replaced: [], dispatched: [], warnings: [] }
  const missingComponent = loadVueComponent("../src/views/mobile/contract/index.vue", {
    listMyLaborContracts() { missingEvents.lists += 1; return Promise.resolve({ rows: [] }) },
    getMyLaborContract() {
      return Promise.reject(new Error("劳动合同不存在"))
    },
    signMyLaborContract() { return Promise.resolve({ data: {} }) },
    downloadLaborContractFile() { return Promise.resolve({}) }
  })
  const missingContext = makePersonalContext(missingComponent, {
    todoType: "OA_LABOR_CONTRACT_SIGN", businessId: "404"
  }, missingEvents)
  await missingComponent.created.call(missingContext)
  assert.ok(missingEvents.warnings.includes("事项已处理"),
    "a missing owned personal detail must be reported as already handled")
  assert.ok(missingEvents.dispatched.includes("todo/refreshSummaries"),
    "a missing owned personal detail must refresh the Todo Store")
}

async function runOaDesktopFocusBehavior() {
  const purchaseSource = fs.readFileSync(path.resolve(__dirname, "../src/views/oa/purchase/index.vue"), "utf8")
  assert.ok(purchaseSource.includes('featureKey: "oaPurchase"') &&
    purchaseSource.includes("loadTodoBusinessList") && purchaseSource.includes("handleTodoFocusRows"),
  "desktop returned purchases must consume the shared exact-detail focus mixin")

  const exactPurchaseId = "9007199254740999"
  let lossyPurchaseDetail = false
  const purchaseComponent = loadVueComponent("../src/views/oa/purchase/index.vue", {
    closePurchase() { return Promise.resolve() },
    getPurchaseDetail(id) {
      return Promise.resolve({ data: { purchaseId: lossyPurchaseDetail ? Number(id) : id, status: "returned", title: "精确采购" } })
    },
    listMyPurchases() { return Promise.resolve({ rows: [], total: 0 }) },
    savePurchase() { return Promise.resolve({ data: {} }) },
    submitPurchase() { return Promise.resolve() }
  })
  const purchaseContext = Object.assign({}, purchaseComponent.data(), purchaseComponent.methods, {
    purchaseDraftAvailable: true, purchaseAvailabilityResolved: true,
    $nextTick(callback) { if (typeof callback === "function") callback.call(purchaseContext) }
  })
  await purchaseContext.openForm({ purchaseId: exactPurchaseId })
  assert.strictEqual(String(purchaseContext.form.purchaseId), exactPurchaseId,
    "returned purchase editing must preserve the exact string business ID from a matching detail response")
  lossyPurchaseDetail = true
  await purchaseContext.openForm({ purchaseId: exactPurchaseId })
  assert.strictEqual(purchaseContext.formReady, false, "unsafe rounded response ID must not permit editing another record")
  assert.ok(purchaseContext.formError.includes("采购申请已变化"), "lossy ID response must be recoverable by an explicit reload")

}

Promise.all([
  runDesktopFocusBehavior(),
  runMobileFocusBehavior(),
  runPersonalFocusBehavior(),
  runOaDesktopFocusBehavior()
]).then(() => {
  console.log("unifiedTodoBusinessFocus tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
