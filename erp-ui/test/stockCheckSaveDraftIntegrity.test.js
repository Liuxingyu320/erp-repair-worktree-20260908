const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("@babel/core")
const stockCheckDetailRules = require("../src/views/inventory/stockCheck/stockCheckDetailRules")

function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

const runtimeRef = { current: null }
const stockApi = {
  getStockCheck(...args) { return runtimeRef.current.getStockCheck(...args) },
  createStockCheck(...args) { return runtimeRef.current.createStockCheck(...args) },
  inputStockCheck(...args) { return runtimeRef.current.inputStockCheck(...args) },
  submitStockCheck(...args) { return runtimeRef.current.submitStockCheck(...args) },
  listStockCheck() { return Promise.resolve({ rows: [], total: 0 }) },
  listStockCheckApprovalTodos() { return Promise.resolve({ rows: [], total: 0 }) },
  listStockCheckCounterCandidates() { return Promise.resolve({ data: [] }) },
  assignStockCheck() { return Promise.resolve({ data: {} }) },
  restartStockCheck() { return Promise.resolve({}) },
  approveStockCheck() { return Promise.resolve({}) },
  rejectStockCheck() { return Promise.resolve({}) },
  getStockCheckApprovalTrack() { return Promise.resolve({ data: [] }) },
  cancelStockCheck() { return Promise.resolve({}) },
  withdrawStockCheck() { return Promise.resolve({}) },
  deleteStockCheck() { return Promise.resolve({}) }
}

function loadComponent() {
  const file = path.resolve(__dirname, "../src/views/inventory/stockCheck/index.vue")
  const script = fs.readFileSync(file, "utf8").match(/<script>([\s\S]*?)<\/script>/)[1]
  const code = babel.transformSync(script, {
    filename: file,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
  }).code
  const testModule = { exports: {} }
  vm.runInNewContext(code, {
    module: testModule,
    exports: testModule.exports,
    process: { env: {} },
    Promise,
    require(name) {
      if (name === "@/api/inventory/stockCheck") return stockApi
      if (name === "./stockCheckDetailRules") return stockCheckDetailRules
      if (name === "@/mixins/todoBusinessFocus") {
        return {
          createTodoBusinessFocusMixin() {
            return {
              methods: {
                loadTodoBusinessList(loader) { return loader() },
                handleTodoFocusRows(list) { return list },
                showTodoBusinessHandled() {}
              }
            }
          }
        }
      }
      if (name === "@/utils/shopContext") {
        return {
          getSelectedDeptId() { return 100 },
          getSelectedDeptContext() { return { deptId: 100 } },
          formatInventoryDeptLabel() { return "仓" }
        }
      }
      if (name === "@/utils/exportConfirm") return { confirmExportAction() { return Promise.resolve() } }
      if (name === "@/views/approval/manage/components/approvalUi") return { unwrapData(value) { return value } }
      return {}
    }
  }, { filename: file })
  return testModule.exports.default
}

const component = loadComponent()
assert.ok(component && component.methods && component.methods.saveActualQty, "stock check page must load")

function createRuntime() {
  const queues = {}
  const log = { gets: [], creates: [], inputs: [], submits: [] }
  function enqueue(name) {
    const item = deferred()
    queues[name] = queues[name] || []
    queues[name].push(item)
    return item.promise
  }
  function settle(name, value, isError) {
    const list = queues[name] || []
    const item = list.shift()
    if (!item) throw new Error("no pending " + name)
    if (isError) item.reject(value)
    else item.resolve(value)
  }
  const runtime = {
    log,
    pending() {
      return Object.keys(queues).filter(key => queues[key] && queues[key].length)
    },
    resolve(name, value) { settle(name, value, false) },
    reject(name, error) { settle(name, error, true) },
    getStockCheck(checkId, config) {
      log.gets.push({ checkId, silentError: !!(config && config.silentError === true) })
      return enqueue("get:" + checkId)
    },
    createStockCheck(data, config) {
      log.creates.push({ data, silentError: !!(config && config.silentError === true) })
      return enqueue("create")
    },
    inputStockCheck(checkId, data, config) {
      log.inputs.push({
        checkId,
        data,
        silentError: !!(config && config.silentError === true)
      })
      return enqueue("input:" + checkId)
    },
    submitStockCheck(checkId, data, config) {
      log.submits.push({
        checkId,
        data,
        silentError: !!(config && config.silentError === true)
      })
      return enqueue("submit:" + checkId)
    }
  }
  runtimeRef.current = runtime
  return runtime
}

function harness() {
  const instance = {
    $refs: {},
    $modal: { confirm() { return Promise.resolve() }, msgSuccess() {}, msgError() {}, msgWarning() {} },
    parseTime() { return "2026-09-12" },
    $nextTick() { return Promise.resolve() },
    $set(obj, key, val) { if (obj) obj[key] = val },
    $auth: { hasPermi() { return true } },
    $store: { getters: { id: 7 } }
  }
  ;(component.mixins || []).forEach(mixin => {
    Object.entries((mixin && mixin.methods) || {}).forEach(([key, fn]) => {
      if (typeof fn === "function") instance[key] = fn.bind(instance)
    })
  })
  Object.entries(component.methods).forEach(([key, fn]) => {
    if (typeof fn === "function") instance[key] = fn.bind(instance)
  })
  Object.assign(instance, component.data.call(instance))
  Object.entries(component.computed || {}).forEach(([key, fn]) => {
    if (typeof fn === "function") Object.defineProperty(instance, key, { get: fn.bind(instance), configurable: true })
  })
  return instance
}

function fireWatch(page, key, value) {
  const spec = component.watch && component.watch[key]
  if (!spec) return
  const handler = typeof spec === "function" ? spec : spec.handler
  handler.call(page, value)
}

function mount(options = {}) {
  const runtime = createRuntime()
  const messages = { success: [], error: [], warning: [] }
  const page = harness()
  page.$confirm = options.confirm || (() => Promise.resolve())
  page.$modal = {
    msgSuccess(text) { messages.success.push(text) },
    msgError(text) { messages.error.push(text) },
    msgWarning(text) { messages.warning.push(text) },
    confirm: options.confirm || (() => Promise.resolve())
  }
  page.$refs.formRef = { validate(fn) { fn(true) }, clearValidate() {} }
  page.getList = () => { page.listReloads = (page.listReloads || 0) + 1 }
  page.loadCategoryOptions = () => {}
  page.hydrateDetailCategories = () => {}
  page.messages = messages
  component.created.call(page)
  return { page, runtime }
}

const tick = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }

function asList(value) {
  assert.ok(Array.isArray(value), "list must be an array; a missing field is not an empty list")
  return Array.from(value)
}

function assertSameList(actual, expected, message) {
  assert.deepStrictEqual(asList(actual), asList(expected), message)
}

function assertSameRecord(actual, expected, message) {
  const label = message || "record"
  assert.ok(actual && typeof actual === "object" && !Array.isArray(actual), label + " actual must be a record")
  assert.ok(expected && typeof expected === "object" && !Array.isArray(expected), label + " expected must be a record")
  assertSameList(Object.keys(actual).sort(), Object.keys(expected).sort(), label + " keys")
  Object.keys(expected).forEach(key => {
    const actualValue = actual[key]
    const expectedValue = expected[key]
    if (Array.isArray(actualValue) || Array.isArray(expectedValue)) {
      assertSameList(actualValue, expectedValue, label + " " + key)
      return
    }
    if (
      actualValue !== null && expectedValue !== null &&
      typeof actualValue === "object" && typeof expectedValue === "object"
    ) {
      assertSameRecord(actualValue, expectedValue, label + " " + key)
      return
    }
    assert.strictEqual(actualValue, expectedValue, label + " " + key)
  })
}

function assertSettled(runtime, name) {
  assertSameList(runtime.pending(), [], name + " left pending requests")
}

function detail(id, extra) {
  return Object.assign({
    detailId: id,
    productId: 1000 + id,
    productName: "商品" + id,
    bookQty: extra && extra.bookQty !== undefined ? extra.bookQty : 10,
    actualQty: extra && extra.actualQty !== undefined ? extra.actualQty : null,
    recountQty: extra && extra.recountQty !== undefined ? extra.recountQty : null,
    recountRequired: extra && extra.recountRequired || "0",
    categoryId: extra && extra.categoryId || 1
  }, extra || {})
}

function checkPayload(checkId, details, extra) {
  return Object.assign({
    checkId,
    checkNo: "SC" + checkId,
    checkDate: "2026-09-12",
    warehouseId: 100,
    shopDeptId: 100,
    status: "draft",
    counterUserId: 7,
    counterName: "盘点人",
    remark: extra && extra.remark || "",
    recountThreshold: extra && extra.recountThreshold !== undefined ? extra.recountThreshold : 1,
    blindCheck: extra && extra.blindCheck || "0",
    details
  }, extra || {})
}

async function openCheck(page, runtime, payload) {
  const pending = page.openInput({
    checkId: payload.checkId,
    warehouseId: 100,
    status: "draft",
    counterUserId: 7
  })
  await tick()
  runtime.resolve("get:" + payload.checkId, { data: payload })
  await pending
  await tick()
}

function loadStockCheckApi() {
  const calls = []
  const file = path.resolve(__dirname, "../src/api/inventory/stockCheck.js")
  const code = babel.transformSync(fs.readFileSync(file, "utf8"), {
    filename: file,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
  }).code
  const testModule = { exports: {} }
  vm.runInNewContext(code, {
    module: testModule,
    exports: testModule.exports,
    require(name) {
      if (name === "@/utils/request") {
        return function request(config) {
          calls.push(config)
          return Promise.resolve({ data: {} })
        }
      }
      return {}
    }
  }, { filename: file })
  return { api: testModule.exports, calls }
}

const scenarios = [
  ["api-silent-error-contract", async () => {
    const { api, calls } = loadStockCheckApi()
    const hijack = {
      silentError: true,
      url: "/hacked",
      method: "delete",
      data: { x: 1 },
      checkId: 999,
      headers: { Authorization: "no" }
    }
    api.getStockCheck(8)
    api.getStockCheck(8, hijack)
    api.createStockCheck({ checkDate: "2026-09-12" })
    api.createStockCheck({ checkDate: "2026-09-12" }, hijack)
    api.inputStockCheck(8, { remark: "a", details: [{ detailId: 1, actualQty: 10 }] })
    api.inputStockCheck(8, { remark: "a", details: [{ detailId: 1, actualQty: 10 }] }, hijack)
    api.submitStockCheck(8)
    api.submitStockCheck(8, undefined, hijack)

    function assertCall(index, expected) {
      const actual = calls[index]
      assert.equal(actual.url, expected.url)
      assert.equal(actual.method, expected.method)
      assert.equal(actual.silentError, expected.silentError)
      assert.equal(actual.checkId, undefined)
      assert.equal(actual.headers, undefined)
      if (Object.prototype.hasOwnProperty.call(expected, "data")) {
        if (expected.data === undefined) {
          assert.strictEqual(actual.data, undefined)
        } else {
          assertSameRecord(actual.data, expected.data, expected.url + " data")
        }
      }
    }

    assert.equal(calls.length, 8)
    assertCall(0, { url: "/inventory/stockCheck/8", method: "get", silentError: false })
    assertCall(1, { url: "/inventory/stockCheck/8", method: "get", silentError: true })
    assertCall(2, { url: "/inventory/stockCheck/create", method: "post", silentError: false, data: { checkDate: "2026-09-12" } })
    assertCall(3, { url: "/inventory/stockCheck/create", method: "post", silentError: true, data: { checkDate: "2026-09-12" } })
    assertCall(4, {
      url: "/inventory/stockCheck/input/8",
      method: "post",
      silentError: false,
      data: { remark: "a", details: [{ detailId: 1, actualQty: 10 }] }
    })
    assertCall(5, {
      url: "/inventory/stockCheck/input/8",
      method: "post",
      silentError: true,
      data: { remark: "a", details: [{ detailId: 1, actualQty: 10 }] }
    })
    assertCall(6, { url: "/inventory/stockCheck/submit/8", method: "post", silentError: false, data: undefined })
    assertCall(7, { url: "/inventory/stockCheck/submit/8", method: "post", silentError: true, data: undefined })
  }],

  ["save-10-keep-12", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: null, bookQty: 10 })]))
    page.form.details[0].actualQty = 10
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 1)
    assert.equal(runtime.log.inputs[0].checkId, 1)
    assert.equal(runtime.log.inputs[0].silentError, true)
    assert.equal(runtime.log.inputs[0].data.details[0].actualQty, 10)
    page.form.details[0].actualQty = 12
    page.form.remark = "保存后备注"
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 10, bookQty: 10 })], { remark: "" }) })
    await tick()
    assert.equal(page.form.details[0].actualQty, 12)
    assert.equal(page.form.remark, "保存后备注")
    assert.equal(page.unsavedStockCheckInput, true)
    assert.ok(page.messages.warning.some(text => text.indexOf("未保存") >= 0))
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 2)
    assert.equal(runtime.log.inputs[1].data.details[0].actualQty, 12)
    assert.equal(runtime.log.inputs[1].data.remark, "保存后备注")
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 12, bookQty: 10 })], { remark: "保存后备注" }) })
    await tick()
    assert.equal(page.unsavedStockCheckInput, false)
    assert.ok(page.messages.success.includes("实盘数据已保存"))
    assertSettled(runtime, "save-10-keep-12")
  }],

  ["merge-by-detail-id-hidden-and-reordered", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [
      detail(11, { actualQty: 1, bookQty: 10, categoryId: 1 }),
      detail(22, { actualQty: 2, bookQty: 10, categoryId: 2 })
    ]))
    page.formDetailCategoryId = 1
    page.saveActualQty()
    await tick()
    const firstPayload = runtime.log.inputs[0].data.details
    assert.equal(firstPayload[0].detailId, 11)
    assert.equal(firstPayload[1].detailId, 22)
    assert.equal(firstPayload[1].actualQty, 2)
    page.form.details.find(item => item.detailId === 22).actualQty = 22
    runtime.resolve("input:1", {
      data: checkPayload(1, [
        detail(22, { actualQty: 2, bookQty: 10, categoryId: 2 }),
        detail(11, { actualQty: 1, bookQty: 10, categoryId: 1 })
      ])
    })
    await tick()
    const row22 = page.form.details.find(item => item.detailId === 22)
    const row11 = page.form.details.find(item => item.detailId === 11)
    assert.equal(row22.actualQty, 22)
    assert.equal(row11.actualQty, 1)
    assert.equal(page.unsavedStockCheckInput, true)
    page.saveActualQty()
    await tick()
    const second = runtime.log.inputs[1].data.details
    const sent22 = second.find(item => item.detailId === 22)
    assert.equal(sent22.actualQty, 22)
    runtime.resolve("input:1", {
      data: checkPayload(1, [
        detail(22, { actualQty: 22, bookQty: 10, categoryId: 2 }),
        detail(11, { actualQty: 1, bookQty: 10, categoryId: 1 })
      ])
    })
    await tick()
    assert.equal(page.unsavedStockCheckInput, false)
    assertSettled(runtime, "merge-by-detail-id-hidden-and-reordered")
  }],

  ["normal-save-accepts-server-baseline", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 5, bookQty: 10 })]))
    page.saveActualQty()
    await tick()
    runtime.resolve("input:1", {
      data: checkPayload(1, [detail(11, { actualQty: 5, bookQty: 10, recountRequired: "0" })], { status: "draft" })
    })
    await tick()
    assert.equal(page.form.details[0].actualQty, 5)
    assert.equal(page.unsavedStockCheckInput, false)
    assert.ok(page.messages.success.includes("实盘数据已保存"))
    assertSettled(runtime, "normal-save-accepts-server-baseline")
  }],

  ["first-count-change-invalidates-recount", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [
      detail(11, { actualQty: 8, bookQty: 10, recountQty: 8, recountRequired: "1" })
    ]))
    page.saveActualQty()
    await tick()
    page.form.details[0].actualQty = 10
    page.handleActualQtyChange(page.form.details[0])
    runtime.resolve("input:1", {
      data: checkPayload(1, [
        detail(11, { actualQty: 8, bookQty: 10, recountQty: 8, recountRequired: "1" })
      ])
    })
    await tick()
    assert.equal(page.form.details[0].actualQty, 10)
    assert.strictEqual(page.form.details[0].recountQty, null)
    assert.notEqual(page.form.details[0].recountRequired, "1")
    assertSettled(runtime, "first-count-change-invalidates-recount")
  }],

  ["save-failure-keeps-input-and-retries", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: null })]))
    page.form.details[0].actualQty = 15
    page.form.remark = "失败保留"
    page.saveActualQty()
    await tick()
    runtime.reject("input:1", new Error("save failed"))
    await tick()
    assert.equal(page.form.details[0].actualQty, 15)
    assert.equal(page.form.remark, "失败保留")
    assert.ok(page.messages.error.some(text => text.indexOf("实盘保存失败") >= 0))
    assert.equal(page.submitLoading, false)
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 2)
    assert.equal(runtime.log.inputs[1].data.details[0].actualQty, 15)
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 15 })], { remark: "失败保留" }) })
    await tick()
    assert.equal(page.unsavedStockCheckInput, false)
    assertSettled(runtime, "save-failure-keeps-input-and-retries")
  }],

  ["no-concurrent-save", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 3 })]))
    page.saveActualQty()
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 1)
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 3 })]) })
    await tick()
    assertSettled(runtime, "no-concurrent-save")
  }],

  ["stale-open-and-save-do-not-clobber-b", async () => {
    const { page, runtime } = mount()
    const first = page.openInput({ checkId: 1, warehouseId: 100, status: "draft", counterUserId: 7 })
    await tick()
    const second = page.openInput({ checkId: 2, warehouseId: 100, status: "draft", counterUserId: 7 })
    await tick()
    runtime.resolve("get:2", { data: checkPayload(2, [detail(22, { actualQty: 4, bookQty: 9 })], { remark: "乙" }) })
    await second
    await tick()
    runtime.resolve("get:1", { data: checkPayload(1, [detail(11, { actualQty: 1 })], { remark: "甲迟到" }) })
    await first
    await tick()
    assert.equal(page.form.checkId, 2)
    assert.equal(page.form.remark, "乙")
    assert.equal(page.form.details[0].detailId, 22)
    page.form.details[0].actualQty = 40
    page.saveActualQty()
    await tick()
    const staleSave = page.openInput({ checkId: 1, warehouseId: 100, status: "draft", counterUserId: 7 })
    await tick()
    runtime.resolve("get:1", { data: checkPayload(1, [detail(11, { actualQty: 1 })]) })
    await staleSave
    await tick()
    runtime.resolve("input:2", { data: checkPayload(2, [detail(22, { actualQty: 4 })], { remark: "乙" }) })
    await tick()
    assert.equal(page.form.checkId, 1)
    assert.equal(page.form.details[0].detailId, 11)
    assert.equal(page.submitLoading, false)
    assert.equal(page.messages.success.includes("实盘数据已保存"), false)
    assertSettled(runtime, "stale-open-and-save-do-not-clobber-b")
  }],

  ["submit-stops-when-new-input-during-save", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 10, bookQty: 10 })]))
    page.submitActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 1)
    assert.equal(runtime.log.inputs[0].data.details[0].actualQty, 10)
    page.form.details[0].actualQty = 12
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 10, bookQty: 10 })]) })
    await tick()
    assert.equal(runtime.log.submits.length, 0)
    assert.equal(page.form.details[0].actualQty, 12)
    assert.equal(page.unsavedStockCheckInput, true)
    assert.equal(page.formOpen, true)
    assert.ok(page.messages.warning.some(text => text.indexOf("请保存后再提交") >= 0))
    assertSettled(runtime, "submit-stops-when-new-input-during-save")
  }],

  ["submit-clean-path-finishes", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 10, bookQty: 10 })]))
    page.submitActualQty()
    await tick()
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 10, bookQty: 10 })]) })
    await tick()
    assert.equal(runtime.log.submits.length, 1)
    assert.equal(runtime.log.submits[0].checkId, 1)
    assert.equal(runtime.log.submits[0].silentError, true)
    runtime.resolve("submit:1", { data: { status: "pending_approval" } })
    await tick()
    assert.ok(page.messages.success.includes("盘点已提交审批"))
    assert.equal(page.formOpen, false)
    assertSettled(runtime, "submit-clean-path-finishes")
  }],

  ["backend-new-recount-is-kept", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 9, bookQty: 10, recountRequired: "0" })]))
    page.saveActualQty()
    await tick()
    runtime.resolve("input:1", {
      data: checkPayload(1, [detail(11, { actualQty: 9, bookQty: 10, recountRequired: "1" })])
    })
    await tick()
    assert.equal(page.form.details[0].recountRequired, "1")
    assert.equal(page.needsRecount(page.form.details[0]), true)
    assert.equal(page.unsavedStockCheckInput, false)
    assertSettled(runtime, "backend-new-recount-is-kept")
  }],

  ["blind-check-does-not-leak-book-or-diff", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [
      detail(11, { actualQty: 3, bookQty: "", recountRequired: "0" })
    ], { blindCheck: "1" }))
    assert.equal(page.isBlindInputForm, true)
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs[0].data.details[0].actualQty, 3)
    page.form.details[0].actualQty = 4
    runtime.resolve("input:1", {
      data: checkPayload(1, [detail(11, { actualQty: 3, bookQty: "", recountRequired: "1" })], { blindCheck: "1" })
    })
    await tick()
    assert.equal(page.form.details[0].actualQty, 4)
    assert.equal(page.form.details[0].recountRequired, "1")
    const joined = page.messages.success.concat(page.messages.warning, page.messages.error).join(" ")
    assert.equal(joined.indexOf("账面") === -1, true)
    assert.equal(joined.indexOf("差异") === -1, true)
    assertSettled(runtime, "blind-check-does-not-leak-book-or-diff")
  }],

  ["close-invalidates-and-reopen-works", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 6 })]))
    page.saveActualQty()
    await tick()
    page.closeStockCheckForm()
    fireWatch(page, "formOpen", false)
    await tick()
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 6 })]) })
    await tick()
    assert.equal(page.formOpen, false)
    await openCheck(page, runtime, checkPayload(2, [detail(22, { actualQty: 8 })]))
    assert.equal(page.form.checkId, 2)
    assert.equal(page.form.details[0].actualQty, 8)
    assert.equal(page.submitLoading, false)
    component.beforeDestroy.call(page)
    assertSettled(runtime, "close-invalidates-and-reopen-works")
  }],

  ["empty-or-wrong-response-keeps-draft", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 9 })]))
    page.form.details[0].actualQty = 18
    page.saveActualQty()
    await tick()
    runtime.resolve("input:1", { data: null })
    await tick()
    assert.equal(page.form.checkId, 1)
    assert.equal(page.form.details[0].actualQty, 18)
    assert.equal(page.unsavedStockCheckInput, true)
    assert.equal(page.messages.success.includes("实盘数据已保存"), false)
    assert.ok(page.messages.error.some(text => text.indexOf("无法确认") >= 0))
    page.saveActualQty()
    await tick()
    runtime.resolve("input:1", { data: checkPayload(99, [detail(11, { actualQty: 18 })]) })
    await tick()
    assert.equal(page.form.checkId, 1)
    assert.equal(page.form.details[0].actualQty, 18)
    assert.equal(page.messages.success.includes("实盘数据已保存"), false)
    assertSettled(runtime, "empty-or-wrong-response-keeps-draft")
  }],

  ["missing-submitted-row-is-kept-for-review", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [
      detail(11, { actualQty: 1 }),
      detail(22, { actualQty: 2 })
    ]))
    page.form.details[1].actualQty = 8
    page.saveActualQty()
    await tick()
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 1 })]) })
    await tick()
    const row22 = page.form.details.find(item => item.detailId === 22)
    assert.equal(row22.actualQty, 8)
    assert.equal(page.unsavedStockCheckInput, true)
    assert.equal(page.stockCheckNeedsReview, true)
    assert.equal(page.messages.success.includes("实盘数据已保存"), false)
    assert.ok(page.messages.warning.some(text => text.indexOf("不完整") >= 0))
    assertSettled(runtime, "missing-submitted-row-is-kept-for-review")
  }],

  ["loading-b-blocks-save-of-a", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 3 })]))
    page.form.details[0].actualQty = 4
    const pendingB = page.openInput({ checkId: 2, warehouseId: 100, status: "draft", counterUserId: 7 })
    await tick()
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 0)
    assert.equal(page.form.checkId, 1)
    runtime.resolve("get:2", { data: checkPayload(2, [detail(22, { actualQty: 5 })]) })
    await pendingB
    await tick()
    assert.equal(page.form.checkId, 2)
    assert.equal(page.form.details[0].detailId, 22)
    assert.equal(runtime.log.inputs.length, 0)
    assertSettled(runtime, "loading-b-blocks-save-of-a")
  }],

  ["keep-alive-rejects-stale-and-restores-draft", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 6 })]))
    page.saveActualQty()
    await tick()
    page.form.details[0].actualQty = 16
    component.deactivated.call(page)
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 1)
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 6 })]) })
    await tick()
    component.activated.call(page)
    assert.equal(page.form.details[0].actualQty, 16)
    assert.equal(page.form.checkId, 1)
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 2)
    assert.equal(runtime.log.inputs[1].data.details[0].actualQty, 16)
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 16 })]) })
    await tick()
    assert.equal(page.unsavedStockCheckInput, false)
    assertSettled(runtime, "keep-alive-rejects-stale-and-restores-draft")
  }],

  ["delayed-validate-and-confirm-do-not-send-after-close", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 7, bookQty: 7 })]))
    let validate
    page.$refs.formRef.validate = fn => { validate = fn }
    page.saveActualQty()
    page.closeStockCheckForm()
    fireWatch(page, "formOpen", false)
    validate(true)
    await tick()
    assert.equal(runtime.log.inputs.length, 0)
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 7, bookQty: 7 })]))
    let confirm
    page.$refs.formRef.validate = fn => fn(true)
    page.$modal.confirm = () => new Promise(resolve => { confirm = resolve })
    page.submitActualQty()
    await tick()
    page.closeStockCheckForm()
    fireWatch(page, "formOpen", false)
    confirm()
    await tick()
    assert.equal(runtime.log.inputs.length, 0)
    assert.equal(runtime.log.submits.length, 0)
    assertSettled(runtime, "delayed-validate-and-confirm-do-not-send-after-close")
  }],

  ["stale-confirm-does-not-write-after-new-save", async () => {
    const { page, runtime } = mount()
    await openCheck(page, runtime, checkPayload(1, [detail(11, { actualQty: 10, bookQty: 10 })]))
    let confirm
    page.$modal.confirm = () => new Promise(resolve => { confirm = resolve })
    page.submitActualQty()
    page.submitActualQty()
    await tick()
    page.form.details[0].actualQty = 12
    page.saveActualQty()
    await tick()
    assert.equal(runtime.log.inputs.length, 1)
    assert.equal(runtime.log.inputs[0].data.details[0].actualQty, 12)
    confirm()
    await tick()
    assert.equal(runtime.log.inputs.length, 1)
    assert.equal(runtime.log.submits.length, 0)
    runtime.resolve("input:1", { data: checkPayload(1, [detail(11, { actualQty: 12, bookQty: 10 })]) })
    await tick()
    assert.equal(page.form.details[0].actualQty, 12)
    assertSettled(runtime, "stale-confirm-does-not-write-after-new-save")
  }]
]

const EXPECTED_SCENARIOS = scenarios.map(item => item[0])

async function run() {
  const completed = []
  for (const [name, fn] of scenarios) {
    await fn()
    completed.push(name)
  }
  assertSameList(completed, EXPECTED_SCENARIOS, "every stock check draft scenario must actually run")
}

Promise.resolve()
  .then(run)
  .then(() => {
    console.log("stockCheckSaveDraftIntegrity: real component methods passed (" + EXPECTED_SCENARIOS.length + " scenarios)")
  })
  .catch(error => {
    console.error(error && error.stack ? error.stack : error)
    process.exitCode = 1
  })
