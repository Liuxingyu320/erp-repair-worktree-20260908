const test = require("node:test")
const assert = require("node:assert/strict")
const fs = require("node:fs"), path = require("node:path"), vm = require("node:vm")
const Vue = require("vue"), compiler = require("vue-template-compiler"), babel = require("@babel/core")
const scopeModule = require("../src/utils/uiOperationScope")
const selection = require("../src/utils/returnSelection")
const purchaseContext = require("../src/utils/oaPurchaseContext")
const root = path.resolve(__dirname, "..")
const tick = async () => { for (let i = 0; i < 5; i++) await Vue.nextTick() }
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function loadComponent(file, resolveImport) {
  const source = fs.readFileSync(path.join(root, file), "utf8")
  const script = compiler.parseComponent(source).script.content
  const code = babel.transformSync(script, { babelrc: false, configFile: false,
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, console,
    require: id => resolveImport(id) }, { filename: file })
  return module.exports.default
}
function setupReturn() {
  const calls = [], messages = [], env = { dept: "10", confirm: () => Promise.resolve() }
  const api = new Proxy({}, { get(target, name) {
    return (...args) => { const pending = deferred(); calls.push({ name, args, ...pending }); return pending.promise }
  } })
  const component = loadComponent("src/views/inventory/purchaseReturn/index.vue", id => {
    if (id === "@/utils/uiOperationScope") return scopeModule
    if (id === "@/utils/returnSelection") return selection
    if (id === "@/utils/shopContext") return { isSelectedWarehouse: () => true, getSelectedDeptId: () => env.dept }
    if (id === "@/api/inventory/purchaseReturn") return api
    if (id === "@/mixins/todoBusinessFocus") return { createTodoBusinessFocusMixin: () => ({}) }
    if (id === "@/utils/businessEmptyState") return { getBusinessEmptyText: () => "empty" }
    throw Error("Unknown import " + id)
  })
  const store = Vue.observable({ getters: { id: "1" }, state: { user: { sessionRevision: 1 } } })
  const page = new Vue({ ...component, mixins: [], created: [], beforeCreate() {
    this.$store = store
    this.$modal = { msgWarning: m => messages.push(m), msgError: m => messages.push(m), msgSuccess: m => messages.push(m),
      confirm: (...args) => env.confirm(...args) }
  } })
  page.loadTodoBusinessList = loader => loader()
  page.handleTodoFocusRows = () => Promise.resolve()
  page.$refs.formRef = { validate: callback => callback(true), clearValidate() {} }
  page.dialogOpen = true
  const take = name => {
    const call = calls.find(c => c.name === name && !c.used)
    assert.ok(call, "Expected " + name); call.used = true; return call
  }
  const source = id => ({ data: { orderId: id, orderNo: "PO-" + id, supplierName: "供应商" + id,
    details: [{ detailId: id + "1", productId: id, productName: "商品" + id, returnableQuantity: 5, unitPrice: 2 }] } })
  const ready = async id => {
    const pending = page.handlePurchaseOrderChange(id)
    take("getPurchaseReturnSourceOrder").resolve(source(id))
    await pending; await tick()
  }
  return { page, env, store, messages, calls, take, source, ready }
}

test("A06: a late source A cannot overwrite B or finish B's spinner", async () => {
  const h = setupReturn(), p = h.page
  const a = p.handlePurchaseOrderChange("101"), ra = h.take("getPurchaseReturnSourceOrder")
  const b = p.handlePurchaseOrderChange("102"), rb = h.take("getPurchaseReturnSourceOrder")
  ra.reject(Error("obsolete A")); await a
  assert.equal(p.sourceLoading, true); assert.equal(p.sourceError, "")
  rb.resolve(h.source("102")); await b
  assert.equal(p.form.purchaseOrderId, "102"); assert.equal(p.sourceLoadedId, "102")
  assert.equal(p.form.details[0].productId, "102"); assert.equal(p.sourceReady, true)
  assert.equal(p.sourceLoading, false); assert.equal(p.sourceError, "")
})

test("A06: reverse success preserves manual title/reason and B's source data", async () => {
  const h = setupReturn(), p = h.page
  p.form.returnTitle = "手填主题"; p.form.returnReason = "包装损坏"
  const a = p.handlePurchaseOrderChange("101"), ra = h.take("getPurchaseReturnSourceOrder")
  const b = p.handlePurchaseOrderChange("102"), rb = h.take("getPurchaseReturnSourceOrder")
  rb.resolve(h.source("102")); await b; ra.resolve(h.source("101")); await a
  assert.equal(p.form.purchaseOrderId, "102"); assert.equal(p.form.purchaseOrderNo, "PO-102")
  assert.equal(p.form.returnTitle, "手填主题"); assert.equal(p.form.returnReason, "包装损坏")
})

test("A06: source mismatch and pending reads prevent writes", async () => {
  const h = setupReturn(), p = h.page
  const read = p.handlePurchaseOrderChange("101"), r = h.take("getPurchaseReturnSourceOrder")
  p.doSave(); assert.equal(h.calls.some(c => c.name === "savePurchaseReturn"), false)
  r.resolve(h.source("102")); await read
  assert.equal(p.sourceReady, false); assert.match(p.sourceError, /已变化/)
  p.doSubmit(); assert.equal(h.calls.some(c => c.name === "submitPurchaseReturn"), false)
})

test("A06: cancelling a source change preserves the accepted source and quantities", async () => {
  const h = setupReturn(), p = h.page
  await h.ready("101")
  p.fillAllReturnable()
  const confirm = deferred(); h.env.confirm = () => confirm.promise
  const change = p.handlePurchaseOrderChange("102")
  assert.equal(p.form.purchaseOrderId, "101")
  confirm.reject("cancel"); await change
  assert.equal(p.form.purchaseOrderId, "101"); assert.equal(p.form.details[0].quantity, 5)
  assert.equal(p.sourceReady, true)
  assert.equal(h.calls.filter(c => c.name === "getPurchaseReturnSourceOrder").length, 1)
})

test("A06: old draft, validation and save continuations cannot operate on a reopened form", async () => {
  const h = setupReturn(), p = h.page
  await h.ready("101"); p.fillAllReturnable()
  let validated
  p.$refs.formRef.validate = fn => { validated = fn }
  p.doSave()
  p.openForm(); h.take("listPurchaseReturnSourceOrders").resolve({ rows: [] })
  validated(true)
  assert.equal(h.calls.some(c => c.name === "savePurchaseReturn"), false)
  await h.ready("102"); p.fillAllReturnable()
  p.$refs.formRef.validate = fn => fn(true)
  p.doSave(); const saved = h.take("savePurchaseReturn")
  p.openForm(); h.take("listPurchaseReturnSourceOrders").resolve({ rows: [] })
  await h.ready("103")
  saved.resolve({ data: {} }); await tick()
  assert.equal(p.dialogOpen, true); assert.equal(p.form.purchaseOrderId, "103")
  assert.equal(h.messages.includes("保存成功"), false)
})

test("A06: organization A-B-A and closing a form never revive pending source reads", async () => {
  const h = setupReturn(), p = h.page
  const read = p.handlePurchaseOrderChange("101"), response = h.take("getPurchaseReturnSourceOrder")
  h.env.dept = "20"; p.handleReturnContextChanged()
  h.env.dept = "10"; p.handleReturnContextChanged()
  p.openForm(); h.take("listPurchaseReturnSourceOrders").resolve({ rows: [] })
  response.resolve(h.source("101")); await read
  assert.equal(p.form.details.length, 0); assert.equal(p.sourceReady, false)
  assert.equal(p.sourceLoading, false)
})

test("A06: keep-alive preserves an already-loaded edited draft without reviving an old request", async () => {
  const h = setupReturn(), p = h.page
  await h.ready("101"); p.fillAllReturnable()
  p.$options.deactivated.forEach(fn => fn.call(p))
  assert.equal(p.sourceReady, false)
  p.$options.activated.forEach(fn => fn.call(p))
  assert.equal(p.sourceReady, true); assert.equal(p.form.details[0].quantity, 5)
  assert.equal(p.ensureReturnSourceReady(), true)
})

function setupPurchase(mobile) {
  const calls = [], messages = [], env = { dept: "10", confirm: () => Promise.resolve() }
  const target = id => ({ businessId: id, approvalInstanceId: "11" + id, approvalTaskId: "21" + id })
  const route = Vue.observable({ path: mobile ? "/mobile/oa-purchase-approval" : "/oa/purchase", query: target("101") })
  const store = Vue.observable({ getters: { id: "1" }, state: { user: { sessionRevision: 1 } }, dispatch: () => Promise.resolve() })
  const api = new Proxy({}, { get(object, name) {
    if (name === "getPurchaseAvailability") return () => Promise.resolve({ data: { enabled: true } })
    if (name === "listMyPurchases") return () => Promise.resolve({ rows: [], total: 0 })
    return (...args) => { const pending = deferred(); calls.push({ name, args, ...pending }); return pending.promise }
  } })
  const file = mobile ? "src/views/mobile/oa/purchaseApproval/index.vue" : "src/views/oa/purchase/index.vue"
  const component = loadComponent(file, id => {
    if (id === "@/mixins/approvalCommandRecovery") return require("./helpers/approvalRecoveryHarness")(api, env)
    if (id === "@/components/ApprovalCommandRecovery") return {}
    if (id.startsWith("@/api/")) return api
    if (id === "@/utils/uiOperationScope") return scopeModule
    if (id === "@/utils/oaPurchaseContext") return purchaseContext
    if (id === "@/utils/todoBusinessFocus") return require("../src/utils/todoBusinessFocus")
    if (id === "@/utils/shopContext") return { getSelectedDeptId: () => env.dept }
    if (id === "@/mixins/todoBusinessFocus") return { createTodoBusinessFocusMixin: () => ({}) }
    if (id === "@/views/approval/manage/components/approvalUi") return { statusLabel: x => x, statusType: () => "info" }
    if (id === "@/utils/businessEmptyState") return { getBusinessEmptyText: () => "empty" }
    if (id === "../../mobileErrorMessage") return { mobileErrorMessage: (e, fallback) => e && e.message || fallback }
    throw Error("Unknown import " + id)
  })
  const page = new Vue({ ...component, created: [], beforeCreate() {
    this.$route = route; this.$store = store
    this.$modal = { msgSuccess: m => messages.push(m), msgError: m => messages.push(m), msgWarning: m => messages.push(m),
      confirm: (...args) => env.confirm(...args) }
    this.$prompt = () => env.confirm().then(() => ({ value: "原因" }))
  } })
  page.loadTodoBusinessList = loader => loader()
  page.handleTodoFocusRows = () => Promise.resolve()
  page.$refs.formRef = { validate: fn => fn(true) }
  const take = name => {
    const call = calls.find(c => c.name === name && !c.used)
    assert.ok(call, "Expected " + name); call.used = true; return call
  }
  const purchase = (id, extra = {}) => ({ data: { purchaseId: id, title: "采购" + id, status: "pending", amount: "12.00", reason: "申请理由",
    approvalInstanceId: "11" + id, ...extra } })
  const approval = (id, extra = {}) => ({ data: { instance: { instanceId: "11" + id, businessCode: "OA_PURCHASE", businessId: id, status: "RUNNING" },
    tasks: [{ taskId: "21" + id, instanceId: "11" + id, taskStatus: "PENDING" }], ...extra } })
  const start = id => mobile ? page.load() : page.showDetail(id)
  const ready = async id => {
    const pending = start(id)
    take("getPurchaseDetail").resolve(purchase(id)); await tick()
    take("getApprovalInstance").resolve(approval(id)); await pending; await tick()
  }
  return { page, route, store, env, calls, messages, take, purchase, approval, target, start, ready }
}

test("ROOT-01: purchase context rejects mismatched business, task and unsafe numeric identifiers", () => {
  const target = purchaseContext.purchaseRouteTarget({ businessId: "9007199254740993", approvalTaskId: "10", approvalInstanceId: "20" })
  assert.equal(target.purchaseId, "9007199254740993")
  assert.equal(purchaseContext.normalizePurchaseId(9007199254740993), "")
  const purchase = { purchaseId: target.purchaseId, approvalInstanceId: "20" }
  const approval = { instance: { instanceId: "20", businessCode: "OA_PURCHASE", businessId: target.purchaseId, status: "RUNNING" },
    tasks: [{ taskId: "10", instanceId: "20", taskStatus: "PENDING" }] }
  assert.equal(purchaseContext.canActOnPurchase(purchase, approval, target), true)
  approval.instance.businessId = "9"
  assert.equal(purchaseContext.canActOnPurchase(purchase, approval, target), false)
  approval.instance.businessId = target.purchaseId; approval.tasks[0].instanceId = "99"
  assert.equal(purchaseContext.canActOnPurchase(purchase, approval, target), false)
  const other = purchaseContext.purchaseRouteTarget({ businessId: "1", approvalTaskId: "2", approvalInstanceId: "3" }, "4")
  assert.equal(other.taskId, ""); assert.equal(other.instanceId, "")
})

for (const mobile of [false, true]) {
  const label = mobile ? "H5" : "PC"
  test(`ROOT-01 ${label}: route A to B loads B immediately and discards A success/finally`, async () => {
    const h = setupPurchase(mobile), p = h.page
    const a = h.start("101"), ra = h.take("getPurchaseDetail")
    h.route.query = h.target("102"); await tick()
    const rb = h.take("getPurchaseDetail")
    assert.equal(rb.args[0], "102")
    rb.resolve(h.purchase("102")); await tick()
    const instanceB = h.take("getApprovalInstance"); assert.equal(instanceB.args[0], "11102")
    instanceB.resolve(h.approval("102")); await tick()
    ra.resolve(h.purchase("101")); await a; await tick()
    const shown = mobile ? p.purchase : p.detail
    assert.equal(shown.purchaseId, "102")
    assert.equal(mobile ? p.canAct : p.canHandleApproval, true)
    assert.equal(h.calls.filter(c => c.name === "getApprovalInstance").length, 1)
    assert.equal(mobile ? p.loading : p.detailLoading, false)
  })
  test(`ROOT-01 ${label}: old failure cannot finish a new read or display its error`, async () => {
    const h = setupPurchase(mobile), p = h.page
    const a = h.start("101"), ra = h.take("getPurchaseDetail")
    h.route.query = h.target("102"); await tick()
    ra.reject(Error("old forbidden")); await a
    assert.equal(mobile ? p.loading : p.detailLoading, true)
    assert.equal(mobile ? p.error : p.detailError, "")
    h.take("getPurchaseDetail").resolve(h.purchase("102")); await tick()
    h.take("getApprovalInstance").resolve(h.approval("102")); await tick()
  })
  test(`ROOT-01 ${label}: changing target during confirmation sends no approval`, async () => {
    const h = setupPurchase(mobile), p = h.page
    await h.ready("101")
    const confirm = deferred(); h.env.confirm = () => confirm.promise
    const action = mobile ? p.executeAction("approve") : p.handleApprovalAction("approve")
    h.route.query = h.target("102"); await tick()
    confirm.resolve(); await action
    assert.equal(h.calls.some(c => c.name === "approveApprovalTask"), false)
    h.take("getPurchaseDetail").resolve(h.purchase("102")); await tick()
    h.take("getApprovalInstance").resolve(h.approval("102")); await tick()
    assert.equal(mobile ? p.purchase.purchaseId : p.detail.purchaseId, "102")
    assert.equal(p.actionLoading, false)
  })
  test(`ROOT-01 ${label}: mismatching approval business fails closed`, async () => {
    const h = setupPurchase(mobile), p = h.page
    const load = h.start("101")
    h.take("getPurchaseDetail").resolve(h.purchase("101")); await tick()
    const bad = h.approval("101"); bad.data.instance.businessId = "102"
    h.take("getApprovalInstance").resolve(bad); await load
    assert.equal(mobile ? p.canAct : p.canHandleApproval, false)
    assert.match(mobile ? p.error : p.detailError, /不一致/)
  })
  test(`ROOT-01 ${label}: action A's late completion cannot notify, reload or unlock action B`, async () => {
    const h = setupPurchase(mobile), p = h.page
    await h.ready("101")
    const actionA = mobile ? p.executeAction("approve") : p.handleApprovalAction("approve")
    await tick(); const postA = h.take("approveApprovalTask")
    assert.equal(postA.args[0], "21101")
    h.route.query = h.target("102"); await tick()
    h.take("getPurchaseDetail").resolve(h.purchase("102")); await tick()
    h.take("getApprovalInstance").resolve(h.approval("102")); await tick()
    const actionB = mobile ? p.executeAction("approve") : p.handleApprovalAction("approve")
    await tick(); const postB = h.take("approveApprovalTask")
    assert.equal(postB.args[0], "21102")
    postA.resolve({ data: {} }); await actionA
    assert.equal(p.actionLoading, true)
    assert.equal(h.messages.includes("审批动作已提交"), false)
    assert.equal(h.calls.filter(c => c.name === "getPurchaseDetail").length, 2)
    postB.reject(Object.assign(Error("permission changed"), { notified: true })); await actionB
    assert.equal(p.actionLoading, false)
  })
}

test("ROOT-01 PC: late edit A does not overwrite B or its user input", async () => {
  const h = setupPurchase(false), p = h.page
  const a = p.openForm({ purchaseId: "101" }), ra = h.take("getPurchaseDetail")
  const b = p.openForm({ purchaseId: "102" }), rb = h.take("getPurchaseDetail")
  rb.resolve(h.purchase("102", { status: "draft" })); await b; await tick()
  p.form.title = "B未保存输入"
  ra.resolve(h.purchase("101", { status: "draft" })); await a
  assert.equal(p.form.purchaseId, "102"); assert.equal(p.form.title, "B未保存输入")
  assert.equal(p.formReady, true); assert.equal(p.formLoading, false)
})

test("ROOT-01 PC: cancelled dirty-form switch retains original ready form", async () => {
  const h = setupPurchase(false), p = h.page
  const a = p.openForm({ purchaseId: "101" })
  h.take("getPurchaseDetail").resolve(h.purchase("101", { status: "draft" })); await a; await tick()
  p.form.title = "未保存"
  h.env.confirm = () => Promise.reject("cancel")
  await p.openForm({ purchaseId: "102" })
  assert.equal(p.form.purchaseId, "101"); assert.equal(p.formReady, true)
  assert.equal(h.calls.filter(c => c.name === "getPurchaseDetail").length, 1)
})

test("ROOT-01 PC: a saved A cannot close B or submit its stale continuation", async () => {
  const h = setupPurchase(false), p = h.page
  const openA = p.openForm({ purchaseId: "101" })
  h.take("getPurchaseDetail").resolve(h.purchase("101", { status: "draft" })); await openA; await tick()
  p.purchaseSubmissionAvailable = true
  p.save(true); const saveA = h.take("savePurchase")
  const openB = p.openForm({ purchaseId: "102" })
  h.take("getPurchaseDetail").resolve(h.purchase("102", { status: "draft" })); await openB; await tick()
  p.form.title = "B修改"
  saveA.resolve(h.purchase("101", { status: "draft", rowVersion: 2 })); await tick()
  assert.equal(p.form.purchaseId, "102"); assert.equal(p.form.title, "B修改"); assert.equal(p.open, true)
  assert.equal(h.calls.some(c => c.name === "submitPurchase"), false)
})

test("ROOT-01: both purchase templates compile after guarded state bindings", () => {
  for (const file of ["src/views/oa/purchase/index.vue", "src/views/mobile/oa/purchaseApproval/index.vue"]) {
    const parsed = compiler.parseComponent(fs.readFileSync(path.join(root, file), "utf8"))
    assert.deepEqual(compiler.compile(parsed.template.content).errors, [], file)
  }
})
