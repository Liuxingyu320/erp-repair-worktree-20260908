const assert = require("assert")
const fs = require("fs")
const path = require("path")

const transferSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)
const transferControllerSource = fs.readFileSync(
  path.resolve(__dirname, "../../erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java"),
  "utf8"
)
const transferServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../../erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java"),
  "utf8"
)
const transferMapperSource = fs.readFileSync(
  path.resolve(__dirname, "../../erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml"),
  "utf8"
)

assert.ok(
  transferSource.includes("buildTransferQuery()") &&
    !transferSource.includes('query.statusGroup = "deliverable"') &&
    transferSource.includes("listTransferProcessing(this.buildTransferQuery())"),
  "transfer processing list should retain all active statuses needed for dispatch, return receipt, and discrepancy handling"
)

assert.ok(
  transferSource.includes("transferPanelSub") &&
    transferSource.includes("当前仓库显示已提交要货、返仓收货及差异处理单；草稿不会出现") &&
    transferSource.includes("row.transferType === \"store_return\"") &&
    transferSource.includes("只能由目标仓库验收返仓"),
  "warehouse transfer panel should cover outbound replenishment, inbound store returns, and discrepancies"
)

assert.ok(
  transferSource.includes('this.download("inventory/transfer/export", this.buildTransferQuery()') &&
    /PROCESSING_STATUSES = List\.of\([\s\S]*?"delivered"[\s\S]*?"partial_received"[\s\S]*?"discrepancy"/.test(transferControllerSource) &&
    /public void export[\s\S]*?applyAllowedStatuses\(transfer, PROCESSING_STATUSES\);[\s\S]*?exportRows\(response, transfer, request/.test(transferControllerSource) &&
    /private void exportRows[\s\S]*?transferExportService\.selectForExport/.test(transferControllerSource),
  "transfer export should use the same processing status filters as the visible list"
)

assert.ok(
  /activeStatusOptions\(\)[\s\S]*?item\.value !== "draft"/.test(transferSource) &&
    transferSource.includes("提交后的单据会出现在这里，草稿不会出现") &&
    transferServiceSource.includes('params.put("hideDraftTransfers", true)') &&
    /hideDraftTransfers == true[\s\S]*?status &lt;&gt; 'draft'/.test(transferMapperSource) &&
    !transferMapperSource.includes("status not in ('draft', 'submitted')"),
  "warehouse context should hide only drafts while keeping submitted requests visible"
)

assert.ok(
  transferSource.includes('v-model="queryParams.storeDeptId"') &&
    transferSource.includes("listVisibleStoreDept") &&
    transferSource.includes("buildTransferOrgHierarchy") &&
    transferSource.includes("resolveTransferOrgAncestorPath") &&
    transferSource.includes("erp:dept-changed") &&
    transferSource.includes("storeOptionsRequestSequence") &&
    transferSource.includes("targetOrgAncestorPath") &&
    transferSource.includes("createdByName") &&
    transferSource.includes("submittedByName") &&
    transferMapperSource.includes("params.storeDeptId") &&
    transferMapperSource.includes("to_dept_hierarchy") &&
    transferMapperSource.includes("from_dept_id = #{params.storeDeptId}") &&
    transferMapperSource.includes("to_dept_id = #{params.storeDeptId}"),
  "transfer list should expose a scoped store filter, organization path, and detail audit names"
)

const auditNameMatch = /    auditName\(value\) \{\n([\s\S]*?)\n    \},\n    submittedAuditName/.exec(transferSource)
const submittedAuditNameMatch = /    submittedAuditName\(row\) \{\n([\s\S]*?)\n    \},\n    statusType/.exec(transferSource)
assert.ok(auditNameMatch && submittedAuditNameMatch, "audit display methods should remain executable page methods")
const auditName = new Function("value", auditNameMatch[1])
const submittedAuditName = new Function("row", submittedAuditNameMatch[1])
const auditContext = { auditName }
assert.strictEqual(
  submittedAuditName.call(auditContext, { status: "draft" }),
  "未提交",
  "a draft without submission evidence should show 未提交"
)
assert.strictEqual(
  submittedAuditName.call(auditContext, {
    status: "draft",
    submittedTime: "2026-08-07 10:00:00",
    submittedByName: "李四",
    createBy: "account",
    updateBy: "other-account"
  }),
  "李四",
  "a withdrawn draft should retain its submitted name snapshot"
)
assert.strictEqual(
  submittedAuditName.call(auditContext, {
    status: "draft",
    submittedTime: "2026-08-07 10:00:00",
    submittedByName: "",
    createBy: "account",
    updateBy: "other-account"
  }),
  "历史姓名未记录",
  "submission time without a name must not fall back to an account"
)

function extractPageMethod(name) {
  const match = new RegExp("\\n    (" + name + "\\([^\\n]*\\) \\{[\\s\\S]*?\\n    \\}),").exec(transferSource)
  assert.ok(match, "page method should be extractable: " + name)
  return match[1]
}

function compilePageMethod(name, listLoader, summaryLoader) {
  const declaration = extractPageMethod(name)
  return new Function(
    "listTransferProcessing",
    "getTransferOpsSummary",
    "return function " + declaration
  )(listLoader || (() => Promise.resolve()), summaryLoader || (() => Promise.resolve({ data: {} })))
}

function compilePageMethodWithDependencies(name, dependencies) {
  const names = Object.keys(dependencies || {})
  const values = names.map(key => dependencies[key])
  return new Function(
    ...names,
    "return function " + extractPageMethod(name)
  )(...values)
}

function deferred(label) {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { label, promise, resolve, reject }
}

function createTransferAsyncHarness() {
  const listDeferreds = [deferred("A-list"), deferred("B-list")]
  const summaryDeferreds = [deferred("A-summary"), deferred("B-summary")]
  let listIndex = 0
  let summaryIndex = 0
  const listPromises = []
  const summaryPromises = []
  const methods = {
    transferContextKey: compilePageMethod("transferContextKey"),
    transferContextToken: compilePageMethod("transferContextToken"),
    isTransferContextCurrent: compilePageMethod("isTransferContextCurrent"),
    resetTransferContextState: compilePageMethod("resetTransferContextState"),
    handleTransferDeptChanged: compilePageMethod("handleTransferDeptChanged"),
    getList: compilePageMethod("getList", () => Promise.resolve()),
    loadOpsSummary: compilePageMethod("loadOpsSummary", () => Promise.resolve(), () => {
      const request = summaryDeferreds[summaryIndex++]
      assert.ok(request, "summary request count should stay within the scenario")
      return request.promise
    })
  }
  const harness = {
    transferContextEpoch: 0,
    currentShopDeptId: "A",
    isWarehouseContext: true,
    isStoreContext: false,
    listRequestSequence: 0,
    opsSummaryRequestSequence: 0,
    stockPickerRequestSequence: 0,
    detailRequestSequence: 0,
    approvalRequestSequence: 0,
    actionRequestSequence: 0,
    stockCategoryRequestId: 0,
    loading: false,
    actionLoading: true,
    list: [{ orderNo: "old" }],
    total: 9,
    opsSummary: { old: true },
    queryParams: { storeDeptId: 88 },
    open: true,
    form: { transferId: 88 },
    detailOpen: true,
    detailLoading: true,
    detail: { transferId: 88 },
    transferApprovalOpen: true,
    transferApprovalLoading: true,
    transferApprovalReview: { transferId: 88 },
    sourceConfirmOpen: true,
    sourceConfirmDetail: { transferId: 88 },
    sourceConfirmForm: { items: [{ transferId: 88 }] },
    shipmentOpen: true,
    shipmentDetail: { transferId: 88 },
    shipmentForm: { items: [{ transferId: 88 }] },
    receiptOpen: true,
    receiptDetail: { transferId: 88 },
    receiptForm: { items: [{ transferId: 88 }] },
    discrepancyOpen: true,
    selectedDiscrepancy: { transferId: 88 },
    discrepancyForm: { items: [{ transferId: 88 }] },
    stockPickerOpen: true,
    stockPickerLoading: true,
    productPickerOpen: true,
    smartPasteOpen: true,
    stockList: [{ stockId: 88 }],
    stockTotal: 8,
    stockProductMap: { 88: { itemId: 88 } },
    stockCategoryOptions: [{ categoryId: 88 }],
    stockCategoryLoading: true,
    stockCategoryItemType: "product",
    selectedStockRows: [{ stockId: 88 }],
    withdrawLoadingId: 88,
    emptyForm: () => ({ transferId: undefined, details: [] }),
    clearDetailApprovalState() {},
    loadAuthorizedStoreOptions: () => Promise.resolve([]),
    buildTransferQuery: () => ({}),
    handleTodoFocusRows: () => Promise.resolve(),
    loadTodoBusinessList: () => {
      const request = listDeferreds[listIndex++]
      assert.ok(request, "list request count should stay within the scenario")
      return request.promise
    }
  }
  Object.assign(harness, methods)
  harness.getList = function getListWithTrace() {
    const promise = methods.getList.call(this)
    listPromises.push(promise)
    return promise
  }
  harness.loadOpsSummary = function loadOpsSummaryWithTrace(contextToken) {
    const promise = methods.loadOpsSummary.call(this, contextToken)
    summaryPromises.push(promise)
    return promise
  }
  return { harness, listDeferreds, summaryDeferreds, listPromises, summaryPromises }
}

async function flushAsyncWork(rounds = 1) {
  for (let index = 0; index < rounds; index += 1) {
    await new Promise(resolve => setImmediate(resolve))
  }
}

function createActionHarness(apiOverrides) {
  const messages = []
  let listCalls = 0
  const api = Object.assign({
    saveTransfer: () => Promise.resolve(),
    submitTransfer: () => Promise.resolve(),
    getTransferDetail: () => Promise.resolve({ data: {} }),
    approveApprovalTask: () => Promise.resolve(),
    rejectApprovalTask: () => Promise.resolve(),
    returnApprovalTask: () => Promise.resolve(),
    returnAfterTodoAction: () => Promise.resolve({ returned: false }),
    approveTransfer: () => Promise.resolve(),
    confirmTransferSource: () => Promise.resolve({ data: {} }),
    deliverTransfer: () => Promise.resolve(),
    receiveShipment: () => Promise.resolve(),
    getTransferDiscrepancy: () => Promise.resolve({ data: {} }),
    resolveTransferDiscrepancy: () => Promise.resolve(),
    cancelTransfer: () => Promise.resolve(),
    withdrawTransfer: () => Promise.resolve()
  }, apiOverrides || {})
  const page = {
    transferContextEpoch: 0,
    currentShopDeptId: "A",
    isWarehouseContext: true,
    isStoreContext: false,
    listRequestSequence: 0,
    opsSummaryRequestSequence: 0,
    stockPickerRequestSequence: 0,
    detailRequestSequence: 0,
    approvalRequestSequence: 0,
    actionRequestSequence: 0,
    stockCategoryRequestId: 0,
    storeOptionsRequestSequence: 0,
    queryParams: { storeDeptId: 77 },
    loading: false,
    actionLoading: false,
    transferApprovalLoading: false,
    withdrawLoadingId: undefined,
    list: [{ orderNo: "A" }],
    total: 1,
    opsSummary: { warehouse: "A" },
    open: true,
    form: { transferId: "A", details: [] },
    detailOpen: true,
    detailLoading: false,
    detail: { transferId: "A" },
    transferApprovalOpen: true,
    transferApprovalReview: { transferId: "A" },
    transferApprovalForm: { transferId: "A", action: "approve", engineMode: "LEGACY", comment: "" },
    sourceConfirmOpen: true,
    sourceConfirmDetail: { transferId: "A" },
    sourceConfirmForm: { items: [], remark: "" },
    shipmentOpen: true,
    shipmentDetail: { transferId: "A", orderNo: "A" },
    shipmentForm: { items: [] },
    receiptOpen: true,
    receiptDetail: { transferId: "A", orderNo: "A" },
    receiptForm: { shipmentId: "A-SHIP", shipmentNo: "A-001", items: [] },
    discrepancyOpen: true,
    selectedDiscrepancy: { discrepancyId: "A-DIFF" },
    discrepancyForm: { requestId: "A-REQ", version: 1, responsibleParty: "UNCONFIRMED", note: "A", items: [] },
    stockPickerOpen: true,
    stockPickerLoading: true,
    productPickerOpen: true,
    smartPasteOpen: true,
    stockList: [{ stockId: "A" }],
    stockTotal: 1,
    stockProductMap: {},
    stockCategoryOptions: [],
    stockCategoryLoading: false,
    stockCategoryItemType: null,
    selectedStockRows: [],
    emptyForm: () => ({ transferId: undefined, transferType: "warehouse", details: [] }),
    clearDetailApprovalState() {},
    loadAuthorizedStoreOptions: () => Promise.resolve([]),
    getList: () => {
      listCalls += 1
      return Promise.resolve()
    },
    ensureStoreContext: () => true,
    ensureCanShip: () => true,
    ensureCanReceive: () => true,
    canConfirmSource: () => true,
    canCancelTransfer: () => true,
    canWithdrawNativeTransfer: () => true,
    validateDetails: () => true,
    applyCurrentShopToForm() {},
    buildDetailPayload: item => Object.assign({}, item),
    lineAmount: () => 0,
    formTotalAmount: 0,
    isReturnForm: false,
    isCrossStoreForm: false,
    storeReturnEnabled: true,
    transferDiscrepancyEnabled: true,
    isNativeApproval: () => false,
    unifiedTaskId: () => "",
    canHandleUnifiedApproval: () => true,
    approvalRouteContext: () => ({ instanceId: "" }),
    toNumber: value => Number(value || 0),
    receiptShortage: item => Math.max(Number(item.remainingQuantity || 0) - Number(item.receiveQuantity || 0) - Number(item.rejectedQuantity || 0) - Number(item.damagedQuantity || 0), 0),
    displayValue: value => String(value || ""),
    formatQuantity: value => String(value || 0),
    getShipmentConfirmMessage: () => "shipment-confirm",
    getReceiptConfirmMessage: () => "receipt-confirm",
    getTransferCancelConfirmMessage: () => "cancel-confirm",
    isCancelError: error => error === "cancel" || error === "close",
    handleTransferActionError: (error, fallback) => messages.push({ type: "error", message: fallback }),
    handleActionError: (error, fallback) => messages.push({ type: "error", message: fallback }),
    showSubmitResult: () => messages.push({ type: "success", message: "submit" }),
    $refs: { formRef: { validate: () => {} } },
    $modal: {
      confirm: () => Promise.resolve(),
      msgSuccess: message => messages.push({ type: "success", message }),
      msgError: message => messages.push({ type: "error", message }),
      msgWarning: message => messages.push({ type: "warning", message })
    },
    $store: null
  }
  const contextMethods = {
    transferContextKey: compilePageMethod("transferContextKey"),
    transferContextToken: compilePageMethod("transferContextToken"),
    isTransferContextCurrent: compilePageMethod("isTransferContextCurrent"),
    captureTransferAction: compilePageMethod("captureTransferAction"),
    isTransferActionCurrent: compilePageMethod("isTransferActionCurrent"),
    snapshotTransferValue: compilePageMethod("snapshotTransferValue"),
    resetTransferContextState: compilePageMethod("resetTransferContextState"),
    handleTransferDeptChanged: compilePageMethod("handleTransferDeptChanged")
  }
  Object.assign(page, contextMethods)
  Object.assign(page, {
    doSave: compilePageMethodWithDependencies("doSave", api),
    handleSubmit: compilePageMethodWithDependencies("handleSubmit", api),
    openTransferApproval: compilePageMethodWithDependencies("openTransferApproval", api),
    submitTransferApproval: compilePageMethodWithDependencies("submitTransferApproval", api),
    openSourceConfirm: compilePageMethodWithDependencies("openSourceConfirm", api),
    submitSourceConfirm: compilePageMethodWithDependencies("submitSourceConfirm", api),
    openShipment: compilePageMethodWithDependencies("openShipment", api),
    submitShipment: compilePageMethodWithDependencies("submitShipment", api),
    openReceipt: compilePageMethodWithDependencies("openReceipt", api),
    submitReceipt: compilePageMethodWithDependencies("submitReceipt", api),
    openDiscrepancy: compilePageMethodWithDependencies("openDiscrepancy", api),
    openDiscrepancyForTransfer: compilePageMethodWithDependencies("openDiscrepancyForTransfer", api),
    submitDiscrepancy: compilePageMethodWithDependencies("submitDiscrepancy", api),
    handleCancel: compilePageMethodWithDependencies("handleCancel", api),
    handleWithdraw: compilePageMethodWithDependencies("handleWithdraw", api)
  })
  return {
    page,
    messages,
    listCalls: () => listCalls,
    api
  }
}

function setShipmentState(page, transferId) {
  page.shipmentDetail = { transferId, orderNo: transferId, transferType: "warehouse", fromDeptName: "仓库", toDeptName: "门店" }
  page.shipmentForm = { items: [{ detailId: transferId + "-ITEM", deliverQuantity: 2 }] }
  page.shipmentOpen = true
}

async function shouldNotSendShipmentAfterSwitchDuringConfirmation() {
  const confirmation = deferred("shipment-confirm")
  let deliverCalls = 0
  const scenario = createActionHarness({
    deliverTransfer: () => {
      deliverCalls += 1
      return Promise.resolve()
    }
  })
  const page = scenario.page
  setShipmentState(page, "A")
  page.$modal.confirm = () => confirmation.promise
  page.submitShipment()
  page.currentShopDeptId = "B"
  page.handleTransferDeptChanged()
  confirmation.resolve()
  await flushAsyncWork(2)
  assert.strictEqual(deliverCalls, 0, "A shipment confirmation resolved after a warehouse switch must not send")
}

async function shouldKeepBShipmentStateWhenARequestFinishes() {
  for (const outcome of ["success", "failure"]) {
    const requestA = deferred("shipment-A-" + outcome)
    const requestB = deferred("shipment-B-" + outcome)
    const calls = []
    const scenario = createActionHarness({
      deliverTransfer: (transferId, payload) => {
        calls.push({ transferId, payload })
        return transferId === "A" ? requestA.promise : requestB.promise
      }
    })
    const page = scenario.page
    page.$modal.confirm = () => Promise.resolve()
    setShipmentState(page, "A")
    page.submitShipment()
    await flushAsyncWork(2)
    assert.strictEqual(calls.length, 1, "A shipment API should be sent before the context switch")

    page.currentShopDeptId = "B"
    page.handleTransferDeptChanged()
    setShipmentState(page, "B")
    page.submitShipment()
    await flushAsyncWork(2)
    assert.strictEqual(calls.length, 2, "B shipment API should be independently started")
    assert.strictEqual(page.actionLoading, true, "B action loading must be active")
    const listCallsBeforeA = scenario.listCalls()
    const messagesBeforeA = scenario.messages.length

    if (outcome === "success") requestA.resolve()
    else requestA.reject(new Error("A shipment failed"))
    await flushAsyncWork(2)

    assert.strictEqual(page.shipmentOpen, true, "A completion must not close B shipment dialog")
    assert.strictEqual(page.actionLoading, true, "A finally must not clear B action loading")
    assert.strictEqual(scenario.listCalls(), listCallsBeforeA, "A completion must not refresh B list")
    assert.strictEqual(scenario.messages.length, messagesBeforeA, "A completion must not message B")
  }
}

async function shouldNotMixReceiptSnapshotAfterSwitchDuringConfirmation() {
  const confirmation = deferred("receipt-confirm")
  let receiveCalls = 0
  const scenario = createActionHarness({
    receiveShipment: () => {
      receiveCalls += 1
      return Promise.resolve()
    }
  })
  const page = scenario.page
  page.receiptDetail = { transferId: "A", orderNo: "A", toDeptName: "A门店", fromDeptName: "A仓库" }
  page.receiptForm = {
    shipmentId: "A-SHIP",
    shipmentNo: "A-001",
    items: [{ detailId: "A-ITEM", remainingQuantity: 5, receiveQuantity: 5, rejectedQuantity: 0, damagedQuantity: 0, discrepancyNote: "", attachmentRefs: "" }]
  }
  page.$modal.confirm = () => confirmation.promise
  page.submitReceipt()
  page.currentShopDeptId = "B"
  page.handleTransferDeptChanged()
  page.receiptDetail = { transferId: "B", orderNo: "B" }
  page.receiptForm = { shipmentId: "B-SHIP", shipmentNo: "B-001", items: [] }
  confirmation.resolve()
  await flushAsyncWork(2)
  assert.strictEqual(receiveCalls, 0, "A receipt confirmation must not combine A items with B IDs")
}

async function shouldNotSaveAfterPendingValidationCrossesContext() {
  for (const submitAfter of [false, true]) {
    let validateCallback
    let saveCalls = 0
    let submitCalls = 0
    const scenario = createActionHarness({
      saveTransfer: () => {
        saveCalls += 1
        return Promise.resolve()
      },
      submitTransfer: () => {
        submitCalls += 1
        return Promise.resolve()
      }
    })
    const page = scenario.page
    page.isStoreContext = true
    page.isWarehouseContext = false
    page.form = {
      transferId: "A",
      transferType: "warehouse",
      details: [{ itemType: "product", itemId: 1, quantity: 1 }]
    }
    page.$refs.formRef.validate = callback => { validateCallback = callback }
    page.doSave(submitAfter)
    page.currentShopDeptId = "B"
    page.handleTransferDeptChanged()
    validateCallback(true)
    await flushAsyncWork(2)
    assert.strictEqual(saveCalls, 0, "stale validation must not call save API")
    assert.strictEqual(submitCalls, 0, "stale validation must not call submit API")
  }
}

const actionMethodNames = [
  "doSave",
  "handleSubmit",
  "submitTransferApproval",
  "submitSourceConfirm",
  "submitShipment",
  "submitReceipt",
  "openDiscrepancy",
  "openDiscrepancyForTransfer",
  "submitDiscrepancy",
  "handleCancel",
  "handleWithdraw"
]
actionMethodNames.forEach(name => {
  assert.ok(
    extractPageMethod(name).includes("captureTransferAction"),
    name + " must capture the unified transfer action token"
  )
})
assert.ok(
  /handleTransferDeptChanged\(\)[\s\S]*?this\.actionRequestSequence \+= 1/.test(transferSource) &&
    /beforeDestroy\(\)[\s\S]*?this\.actionRequestSequence \+= 1/.test(transferSource),
  "context changes and destruction must invalidate transfer actions"
)

async function shouldRejectOldTransferListAndSummaryResponses() {
  const scenario = createTransferAsyncHarness()
  const page = scenario.harness

  const requestA = page.getList()
  page.currentShopDeptId = "B"
  page.handleTransferDeptChanged()

  assert.strictEqual(page.list.length, 0, "warehouse switch must clear the old list immediately")
  assert.strictEqual(page.total, 0, "warehouse switch must clear the old total immediately")
  assert.deepStrictEqual(page.opsSummary, {}, "warehouse switch must clear the old summary immediately")
  assert.strictEqual(page.queryParams.storeDeptId, undefined, "warehouse switch must clear the store filter")
  assert.strictEqual(page.detailOpen, false, "warehouse switch must close the old detail")
  assert.strictEqual(page.open, false, "warehouse switch must close the old edit form")
  assert.strictEqual(page.transferApprovalOpen, false, "warehouse switch must close the old approval dialog")
  assert.strictEqual(page.sourceConfirmOpen, false, "warehouse switch must close the old source confirmation")
  assert.strictEqual(page.shipmentOpen, false, "warehouse switch must close the old shipment dialog")
  assert.strictEqual(page.receiptOpen, false, "warehouse switch must close the old receipt dialog")
  assert.strictEqual(page.discrepancyOpen, false, "warehouse switch must close the old discrepancy dialog")
  assert.strictEqual(page.stockPickerOpen, false, "warehouse switch must close the old stock picker")
  assert.strictEqual(page.stockPickerLoading, false, "warehouse switch must clear old stock loading")
  assert.strictEqual(page.productPickerOpen, false, "warehouse switch must close the old product picker")
  assert.strictEqual(page.smartPasteOpen, false, "warehouse switch must close the old smart paste dialog")

  const requestB = scenario.listPromises[1]
  scenario.listDeferreds[1].resolve({ rows: [{ orderNo: "B" }], total: 1 })
  await flushAsyncWork()
  // A 列表尚未返回，因此 B 是本场景第一个实际发出的汇总请求。
  scenario.summaryDeferreds[0].resolve({ data: { warehouse: "B" } })
  await requestB
  assert.deepStrictEqual(page.list.map(row => row.orderNo), ["B"], "the fast B list should be visible")
  assert.deepStrictEqual(page.opsSummary, { warehouse: "B" }, "the B summary should be visible")

  scenario.listDeferreds[0].resolve({ rows: [{ orderNo: "A" }], total: 1 })
  await requestA
  assert.deepStrictEqual(page.list.map(row => row.orderNo), ["B"], "the late A list must not overwrite B")
  assert.strictEqual(page.total, 1, "the late A finally must not overwrite B total")
  assert.deepStrictEqual(page.opsSummary, { warehouse: "B" }, "the late A list must not start or overwrite B summary")
  assert.strictEqual(page.loading, false, "the stale A finally must not corrupt current loading state")
}

async function shouldRejectOldSummaryAfterTheListAlreadyStartedIt() {
  const scenario = createTransferAsyncHarness()
  const page = scenario.harness
  const requestA = page.getList()

  scenario.listDeferreds[0].resolve({ rows: [{ orderNo: "A" }], total: 1 })
  await flushAsyncWork()
  assert.strictEqual(scenario.summaryPromises.length, 1, "A summary request should be in flight before switching")

  page.currentShopDeptId = "B"
  page.handleTransferDeptChanged()
  const requestB = scenario.listPromises[1]
  scenario.listDeferreds[1].resolve({ rows: [{ orderNo: "B" }], total: 1 })
  await flushAsyncWork()
  scenario.summaryDeferreds[1].resolve({ data: { warehouse: "B" } })
  await requestB

  scenario.summaryDeferreds[0].resolve({ data: { warehouse: "A" } })
  await requestA
  assert.deepStrictEqual(page.list.map(row => row.orderNo), ["B"], "the late A summary must not change the B list")
  assert.deepStrictEqual(page.opsSummary, { warehouse: "B" }, "the late A summary must not overwrite B")
  assert.strictEqual(page.loading, false, "A summary finally must not alter B loading")
}

Promise.all([
  shouldRejectOldTransferListAndSummaryResponses(),
  shouldRejectOldSummaryAfterTheListAlreadyStartedIt(),
  shouldNotSendShipmentAfterSwitchDuringConfirmation(),
  shouldKeepBShipmentStateWhenARequestFinishes(),
  shouldNotMixReceiptSnapshotAfterSwitchDuringConfirmation(),
  shouldNotSaveAfterPendingValidationCrossesContext()
]).then(() => {
  console.log("transferWarehouseProcessingVisibility tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
