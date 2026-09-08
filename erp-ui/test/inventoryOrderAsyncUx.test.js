const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const salesSource = fs.readFileSync(path.join(root, "src/views/inventory/sales/index.vue"), "utf8")
const purchaseSource = fs.readFileSync(path.join(root, "src/views/inventory/purchase/index.vue"), "utf8")

for (const source of [salesSource, purchaseSource]) {
  assert.ok(source.includes("listRequestSequence"), "inventory lists must sequence requests")
  assert.ok(source.includes("activeListQuerySnapshot"), "inventory lists must bind responses to a query snapshot")
  assert.ok(source.includes('role="alert"') && source.includes("重新加载"), "list failures must expose an inline retry")
  assert.ok(source.includes("formSubmitting"), "order editors must expose a single-flight state")
  assert.ok(source.includes(':close-on-press-escape="!formSubmitting"'), "Escape must be blocked while an editor write is pending")
  assert.ok(source.includes(':before-close="handleFormBeforeClose"'), "editor close attempts must pass through the write guard")
}
assert.ok(purchaseSource.includes("qcRequestSequence") && purchaseSource.includes("qcTargetOrderId"), "QC loads must be scoped to the active order")
assert.ok(purchaseSource.includes("qcLoadError") && purchaseSource.includes("retryQualityCheckLoad"), "QC load failures must have an explicit retry state")
assert.ok(purchaseSource.includes('@closed="handleQcDialogClosed"'), "closing QC must invalidate pending loads")

function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

function flushAsync() {
  return new Promise(resolve => setImmediate(resolve))
}

function loadSfc(source, filename, globals) {
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, `${filename} must expose a script block`)
  const transformed = script[1]
    .replace(/^import .*$/gm, "")
    .replace("export default", "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    require(specifier) {
      if (specifier === "@/mixins/todoBusinessFocus") {
        return { createTodoBusinessFocusMixin: () => ({}) }
      }
      throw new Error(`unexpected require: ${specifier}`)
    },
    ...globals
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(transformed, sandbox, { filename })
  return sandbox.module.exports
}

function bind(component, initial = {}) {
  const base = { ...initial }
  const data = component.data ? component.data.call(base) : {}
  const target = { ...data, ...initial }
  Object.entries(component.methods || {}).forEach(([name, method]) => {
    target[name] = method.bind(target)
  })
  Object.entries(component.computed || {}).forEach(([name, computed]) => {
    if (Object.prototype.hasOwnProperty.call(target, name)) return
    const getter = typeof computed === "function" ? computed : computed.get
    Object.defineProperty(target, name, { configurable: true, get: getter.bind(target) })
  })
  return target
}

function modal(overrides = {}) {
  return {
    msgError() {},
    msgWarning() {},
    msgSuccess() {},
    confirm: () => Promise.resolve(),
    ...overrides
  }
}

function salesDefinition(overrides = {}) {
  const resolved = () => Promise.resolve({ rows: [], total: 0, data: {} })
  return loadSfc(salesSource, "sales/index.vue", {
    InventoryItemSelect: {},
    listSales: resolved,
    getSalesDetail: resolved,
    saveSales: resolved,
    submitSales: resolved,
    cancelSales: resolved,
    createDeliveryNoticeApi: resolved,
    listCustomerOptions: resolved,
    getSelectedDeptContext: () => ({}),
    isSelectedStore: () => true,
    getBusinessEmptyText: () => "",
    parseTime: () => "2026-07-16",
    ...overrides
  })
}

function purchaseDefinition(overrides = {}) {
  const resolved = () => Promise.resolve({ rows: [], total: 0, data: {} })
  return loadSfc(purchaseSource, "purchase/index.vue", {
    WarehouseSelect: {},
    listPurchase: resolved,
    getPurchaseDetail: resolved,
    savePurchase: resolved,
    submitPurchase: resolved,
    receivePurchase: resolved,
    qualityCheckPurchase: resolved,
    listPendingReceiptBatches: resolved,
    qualityCheckPurchaseBatch: resolved,
    cancelPurchase: resolved,
    deleteDraftPurchase: resolved,
    listProduct: resolved,
    listOe: resolved,
    listGift: resolved,
    listSupplier: resolved,
    parseTime: () => "2026-07-16",
    getSelectedDeptId: () => 9,
    getSelectedDeptName: () => "测试仓",
    isSelectedWarehouse: () => true,
    canReceivePurchase: () => true,
    canQualityCheckPurchase: () => true,
    canCancelPurchase: () => true,
    purchaseBusinessStageLabel: () => "",
    purchaseBusinessStageTone: () => "",
    ...overrides
  })
}

function listInstance(component, kind) {
  const common = {
    addDateRange(query) { return { ...query } },
    $modal: modal(),
    $nextTick(callback) { callback() }
  }
  if (kind === "purchase") {
    Object.assign(common, {
      isWarehouseContext: true,
      loadTodoBusinessList(loader) { return loader() },
      handleTodoFocusRows() { return Promise.resolve() }
    })
  } else {
    common.isStoreContext = true
  }
  return bind(component, common)
}

async function assertLatestListWins(kind) {
  const first = deferred()
  const second = deferred()
  const calls = []
  const listApi = query => {
    calls.push(JSON.parse(JSON.stringify(query)))
    return calls.length === 1 ? first.promise : second.promise
  }
  const component = kind === "sales"
    ? salesDefinition({ listSales: listApi })
    : purchaseDefinition({ listPurchase: listApi })
  const instance = listInstance(component, kind)
  let focusCalls = 0
  if (kind === "purchase") instance.handleTodoFocusRows = () => { focusCalls += 1; return Promise.resolve() }

  instance.queryParams.orderNo = "old"
  const oldRequest = instance.getList()
  instance.queryParams.orderNo = "new"
  const newRequest = instance.getList()
  assert.strictEqual(instance.list.length, 0, `${kind} must clear old rows as soon as filters reload`)
  assert.notStrictEqual(calls[0], calls[1], `${kind} requests must receive distinct query objects`)
  assert.strictEqual(calls[0].orderNo, "old")
  assert.strictEqual(calls[1].orderNo, "new")

  second.resolve({ rows: [{ orderNo: "new-result" }], total: 1 })
  await newRequest
  assert.strictEqual(instance.list[0].orderNo, "new-result")
  assert.strictEqual(instance.loading, false)

  first.resolve({ rows: [{ orderNo: "old-result" }], total: 1 })
  await oldRequest
  assert.strictEqual(instance.list[0].orderNo, "new-result", `${kind} must discard a late old response`)
  if (kind === "purchase") assert.strictEqual(focusCalls, 1, "stale purchase rows must not trigger todo focus")
}

async function assertListFailureRetry(kind) {
  let calls = 0
  const listApi = () => {
    calls += 1
    return calls === 1
      ? Promise.reject(new Error("offline"))
      : Promise.resolve({ rows: [{ orderNo: "retry-result" }], total: 1 })
  }
  const component = kind === "sales"
    ? salesDefinition({ listSales: listApi })
    : purchaseDefinition({ listPurchase: listApi })
  const instance = listInstance(component, kind)
  instance.list = [{ orderNo: "stale" }]
  instance.total = 1

  await instance.getList()
  assert.strictEqual(instance.list.length, 0, `${kind} failure must not leave stale rows visible`)
  assert.ok(instance.listError.includes("加载失败"))
  await instance.getList()
  assert.strictEqual(instance.listError, "")
  assert.strictEqual(instance.list[0].orderNo, "retry-result")
}

async function assertSalesEditorSingleFlight() {
  const failedWrite = deferred()
  const retryWrite = deferred()
  let writeCalls = 0
  const component = salesDefinition({
    saveSales() {
      writeCalls += 1
      return writeCalls === 1 ? failedWrite.promise : retryWrite.promise
    }
  })
  let validate
  const instance = bind(component, {
    isStoreContext: true,
    open: true,
    $modal: modal(),
    $refs: { formRef: { validate(callback) { validate = callback } } },
    $nextTick(callback) { callback() }
  })
  instance.getList = () => Promise.resolve()
  instance.form = {
    orderId: undefined, orderTitle: "销售单", customerId: 1, customerName: "", orderDate: "2026-07-16", remark: "",
    totalAmount: 0, details: [{ itemType: "product", itemId: 11, itemName: "A", quantity: 1, unitPrice: 10 }]
  }
  instance.customerOptions = [{ customerId: 1, customerName: "客户" }]

  const first = instance.doSave(false)
  assert.strictEqual(instance.formSubmitting, true, "sales must lock before validation finishes")
  await instance.doSave(true)
  assert.strictEqual(writeCalls, 0, "a second sales click must be ignored during validation")
  let closed = false
  instance.handleFormBeforeClose(() => { closed = true })
  assert.strictEqual(closed, false, "sales dialog close must be blocked while pending")
  validate(true)
  await flushAsync()
  assert.strictEqual(writeCalls, 1)
  failedWrite.reject(new Error("save failed"))
  await first
  assert.strictEqual(instance.formSubmitting, false)
  assert.strictEqual(instance.open, true, "failed sales writes must remain retryable")

  instance.$refs.formRef.validate = callback => callback(true)
  const retry = instance.doSave(false)
  assert.strictEqual(instance.formSubmitting, true)
  retryWrite.resolve({})
  await retry
  assert.strictEqual(instance.formSubmitting, false)
  assert.strictEqual(instance.open, false)

  instance.open = true
  instance.$refs.formRef.validate = callback => callback(false)
  await instance.doSave(false)
  assert.strictEqual(instance.formSubmitting, false, "invalid sales forms must release the lock")
  assert.strictEqual(writeCalls, 2)
}

async function assertPurchaseEditorSingleFlight() {
  const failedWrite = deferred()
  const retryWrite = deferred()
  let writeCalls = 0
  const component = purchaseDefinition({
    savePurchase() {
      writeCalls += 1
      return writeCalls === 1 ? failedWrite.promise : retryWrite.promise
    }
  })
  let validate
  const instance = bind(component, {
    isWarehouseContext: true,
    currentWarehouseId: 9,
    currentWarehouseName: "测试仓",
    open: true,
    $modal: modal(),
    $refs: { formRef: { validate(callback) { validate = callback } } },
    $nextTick(callback) { callback() }
  })
  instance.getList = () => Promise.resolve()
  instance.form = {
    orderId: undefined, orderTitle: "采购单", supplierId: 2, supplierName: "", orderDate: "2026-07-16", remark: "", totalAmount: 0,
    details: [{ itemType: "product", itemId: 22, itemName: "B", quantity: 2, unitPrice: 3 }]
  }
  instance.supplierOptions = [{ supplierId: 2, supplierName: "供应商" }]

  const first = instance.doSave(false)
  assert.strictEqual(instance.formSubmitting, true, "purchase must lock before validation finishes")
  await instance.doSave(true)
  assert.strictEqual(writeCalls, 0, "a second purchase click must be ignored during validation")
  let closed = false
  instance.handleFormBeforeClose(() => { closed = true })
  assert.strictEqual(closed, false, "purchase dialog close must be blocked while pending")
  validate(true)
  await flushAsync()
  assert.strictEqual(writeCalls, 1)
  failedWrite.reject(new Error("save failed"))
  await first
  assert.strictEqual(instance.formSubmitting, false)
  assert.strictEqual(instance.open, true, "failed purchase writes must remain retryable")

  instance.$refs.formRef.validate = callback => callback(true)
  const retry = instance.doSave(false)
  retryWrite.resolve({})
  await retry
  assert.strictEqual(instance.formSubmitting, false)
  assert.strictEqual(instance.open, false)

  instance.open = true
  instance.$refs.formRef.validate = callback => callback(false)
  await instance.doSave(false)
  assert.strictEqual(instance.formSubmitting, false, "invalid purchase forms must release the lock")
  assert.strictEqual(writeCalls, 2)
}

function qcBatch(id, itemName) {
  return {
    batchId: id,
    batchNo: `B-${id}`,
    details: [{ batchDetailId: id * 10, itemName, pendingQuantity: 2 }]
  }
}

async function assertQualityCheckRequestIsolation() {
  const requests = []
  let qualityCalls = 0
  let confirmCalls = 0
  const component = purchaseDefinition({
    listPendingReceiptBatches() {
      const request = deferred()
      requests.push(request)
      return request.promise
    },
    qualityCheckPurchaseBatch() { qualityCalls += 1; return Promise.resolve() }
  })
  const instance = bind(component, {
    isWarehouseContext: true,
    currentWarehouseId: 9,
    qcOpen: false,
    $modal: modal({ confirm() { confirmCalls += 1; return Promise.resolve() } }),
    $refs: { qcFormRef: { clearValidate() {}, validate(callback) { callback(true) } } },
    $nextTick(callback) { callback() }
  })
  instance.getList = () => Promise.resolve()

  const first = instance.openQualityCheck({ orderId: 1, orderNo: "PO-1" })
  const second = instance.openQualityCheck({ orderId: 2, orderNo: "PO-2" })
  requests[1].resolve({ data: [qcBatch(2, "new-item")] })
  await second
  assert.strictEqual(instance.qcForm.orderId, 2)
  assert.strictEqual(instance.qcBatches[0].batchId, 2)
  assert.strictEqual(instance.qcForm.items[0].itemName, "new-item")

  requests[0].resolve({ data: [qcBatch(1, "old-item")] })
  await first
  assert.strictEqual(instance.qcBatches[0].batchId, 2, "a late QC response must not replace the active order")

  const failed = instance.openQualityCheck({ orderId: 3, orderNo: "PO-3" })
  requests[2].reject(new Error("offline"))
  await failed
  assert.ok(instance.qcLoadError.includes("加载失败"))
  assert.strictEqual(instance.qcForm.items.length, 0)
  await instance.submitQualityCheck()
  assert.strictEqual(confirmCalls, 0, "QC error state must block confirmation")
  assert.strictEqual(qualityCalls, 0, "QC error state must block writes")

  const retry = instance.retryQualityCheckLoad()
  requests[3].resolve({ data: [qcBatch(3, "retry-item")] })
  await retry
  assert.strictEqual(instance.qcLoadError, "")
  assert.strictEqual(instance.qcForm.items[0].itemName, "retry-item")

  const closed = instance.openQualityCheck({ orderId: 4, orderNo: "PO-4" })
  instance.closeQualityCheck()
  requests[4].resolve({ data: [qcBatch(4, "closed-item")] })
  await closed
  assert.strictEqual(instance.qcOpen, false)
  assert.strictEqual(instance.qcBatches.length, 0, "closing QC must invalidate its pending response")
}

;(async () => {
  await assertLatestListWins("sales")
  await assertLatestListWins("purchase")
  await assertListFailureRetry("sales")
  await assertListFailureRetry("purchase")
  await assertSalesEditorSingleFlight()
  await assertPurchaseEditorSingleFlight()
  await assertQualityCheckRequestIsolation()
  console.log("inventory order async UX tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
