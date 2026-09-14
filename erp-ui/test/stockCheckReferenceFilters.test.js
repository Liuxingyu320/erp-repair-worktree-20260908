const assert = require("assert")
const { test } = require("node:test")
const compiler = require("vue-template-compiler")
const current = { dept: 100, restart: () => Promise.resolve({}) }
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
  restartStockCheck(...args) { return current.restart(...args) },
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
          getSelectedDeptId() { return current.dept },
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

const ids = rows => Array.from(rows, row => row.detailId)
const clone = value => JSON.parse(JSON.stringify(value))
function pageWithRows() {
  current.dept = 100
  current.restart = () => Promise.resolve({})
  const page = harness()
  page.form = Object.assign(page.emptyForm(), { checkId: "11", details: [
    { detailId: "1", productName: "红茶", productCode: "TEA-A", categoryId: 2, bookQty: 10, actualQty: null, previousActualQty: 8, previousBookQty: 9, needsSnapshotReview: true, snapshotVersion: "9007199254741001" },
    { detailId: "2", productName: "绿茶", productCode: "TEA-B", categoryId: 2, bookQty: 0, actualQty: 0 },
    { detailId: "3", itemName: "礼盒", itemCode: "GIFT-3", categoryId: 3, bookQty: 10, actualQty: 8, recountQty: 10 },
    { detailId: "4", productName: "茶杯", productCode: "CUP-4", categoryId: 3, bookQty: 5, actualQty: 6 }
  ] })
  return page
}
test("name and code search keeps original row identities and editable quantities", () => {
  const p = pageWithRows(), row = p.form.details[0]
  p.formDetailKeyword = " tea-a "
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["1"])
  assert.strictEqual(p.filteredFormDetails[0], row)
  p.filteredFormDetails[0].actualQty = 7
  p.formDetailKeyword = "礼盒"
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["3"])
  p.formDetailKeyword = ""
  assert.strictEqual(p.filteredFormDetails[0].actualQty, 7)
  assert.strictEqual(p.buildInputPayload().details.length, 4)
})
test("unentered treats zero as an entered count and combines category filtering", () => {
  const p = pageWithRows()
  p.formDetailFilter = "unentered"
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["1"])
  p.categoryOptions = [{ categoryId: 1, children: [{ categoryId: 2 }] }, { categoryId: 2, ancestors: "0,1" }]
  p.formDetailFilter = "all"; p.formDetailCategoryId = 1
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["1", "2"])
  p.formDetailKeyword = "tea-b"
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["2"])
})
test("difference filter uses final recount and excludes missing or masked book counts", () => {
  const p = pageWithRows(); p.formDetailFilter = "difference"
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["4"])
  p.form.details[3].bookQty = null
  assert.strictEqual(p.filteredFormDetails.length, 0)
  p.form.details[2].recountQty = 9
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["3"])
})
test("draft rejected and returned blind checks do not expose difference filtering", () => {
  const p = pageWithRows(); p.form.blindCheck = "1"; p.formDetailFilter = "difference"
  for (const status of ["draft", "rejected", "returned"]) {
    p.form.status = status
    assert.strictEqual(p.isBlindInputForm, true)
    assert.strictEqual(p.filteredFormDetails.length, 0)
    assert.strictEqual(p.isBlindCheck(p.form), true)
  }
  p.form.status = "completed"
  assert.strictEqual(p.isBlindInputForm, false)
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["4"])
})
test("reference counts remain separate from current quantities and need explicit reentry", () => {
  const p = pageWithRows(), original = clone(p.form.details[0])
  p.formDetailFilter = "review"
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["1"])
  const row = p.normalizeDetail(p.filteredFormDetails[0])
  assert.strictEqual(row.actualQty, null)
  assert.strictEqual(row.previousActualQty, 8)
  assert.strictEqual(p.hasStockCheckReference(row), true)
  assert.strictEqual(p.hasStockCheckReference({ previousActualQty: 0 }), true)
  assert.deepStrictEqual(p.form.details[0], original)
  p.form.details[0].actualQty = 0
  assert.strictEqual(p.filteredFormDetails.length, 0)
})
test("detail filters are independent and never shrink form payload scope", () => {
  const p = pageWithRows()
  p.detail = Object.assign({}, p.form)
  p.detailKeyword = "gift"; p.formDetailKeyword = "tea"
  assert.deepStrictEqual(ids(p.filteredDetailDetails), ["3"])
  assert.deepStrictEqual(ids(p.filteredFormDetails), ["1", "2"])
  assert.deepStrictEqual(ids(p.buildInputPayload().details), ["1", "2", "3", "4"])
})
test("input includes exact restart token without forging reference metadata or legacy fields", () => {
  const p = pageWithRows(), data = p.buildInputPayload()
  assert.strictEqual(data.details[0].snapshotVersion, "9007199254741001")
  assert.strictEqual(Object.hasOwn(data.details[1], "snapshotVersion"), false)
  for (const row of data.details) {
    for (const key of ["previousActualQty", "previousBookQty", "previousRecountQty", "needsSnapshotReview", "bookQty"]) assert.strictEqual(Object.hasOwn(row, key), false)
  }
})
test("save readback from a new restart never carries invalidated local count into new round", () => {
  const p = pageWithRows(), snapshot = p.buildInputPayload(), remote = clone(p.form)
  p.form.details[0].actualQty = 99
  remote.details[0].snapshotVersion = "9007199254741002"
  remote.details[0].actualQty = null; remote.details[0].previousActualQty = 8
  assert.strictEqual(p.mergeSavedStockCheck(remote, snapshot, "11"), false)
  assert.strictEqual(p.form.details[0].actualQty, null)
  assert.strictEqual(p.form.details[0].previousActualQty, 8)
  assert.strictEqual(p.form.details[0].snapshotVersion, "9007199254741002")
  assert.strictEqual(p.stockCheckNeedsReview, true)
})
test("ordinary same-round save preserves edits made while request was pending", () => {
  const p = pageWithRows(), snapshot = p.buildInputPayload(), remote = clone(p.form)
  p.form.details[0].actualQty = 7
  assert.strictEqual(p.mergeSavedStockCheck(remote, snapshot, "11"), true)
  assert.strictEqual(p.form.details[0].actualQty, 7)
  assert.strictEqual(p.unsavedStockCheckInput, true)
  assert.strictEqual(p.stockCheckNeedsReview, false)
})
const invalidated = () => ({ checkId: "11", warehouseId: 100, status: "invalidated", counterUserId: 7 })
test("restart confirmation becomes invalid when account or organization changes", async () => {
  for (const change of [p => { current.dept = 200 }, p => { p.$store.getters.id = 8 }]) {
    const p = pageWithRows(), gate = deferred(); let calls = 0
    p.$modal.confirm = () => gate.promise; current.restart = () => { calls++; return Promise.resolve({}) }
    const task = p.handleRestart(invalidated()); change(p); gate.resolve(); await task
    assert.strictEqual(calls, 0)
  }
})
test("restart submits once and opens server-provided new round without copying old input", async () => {
  const p = pageWithRows(), gate = deferred(); let calls = 0, opened
  current.restart = id => { assert.strictEqual(id, "11"); calls++; return gate.promise }
  p.getList = () => {}; p.openInput = row => { opened = row }
  const task = p.handleRestart(invalidated())
  await Promise.resolve(); await Promise.resolve()
  await p.handleRestart(invalidated()); assert.strictEqual(calls, 1)
  gate.resolve({}); await task
  assert.strictEqual(opened.status, "draft")
  assert.strictEqual(Object.hasOwn(opened, "details"), false)
  assert.strictEqual(p.restartLoadingId, null)
})
test("late restart response after scope change does not reopen old organization data", async () => {
  const p = pageWithRows(), gate = deferred(); let opened = 0
  current.restart = () => gate.promise; p.openInput = () => { opened++ }; p.getList = () => { opened++ }
  const task = p.handleRestart(invalidated()); await Promise.resolve(); await Promise.resolve()
  current.dept = 200; gate.resolve({}); await task
  assert.strictEqual(opened, 0); assert.strictEqual(p.restartLoadingId, null)
})
test("restart failure preserves form input and permits a deliberate retry", async () => {
  const p = pageWithRows(), before = { ...p.form, details: p.form.details.map(row => ({ ...row })) }
  current.restart = () => Promise.reject(new Error("offline"))
  const result = await p.handleRestart(invalidated())
  assert.strictEqual(result.failed, true); assert.strictEqual(p.restartLoadingId, null)
  assert.deepStrictEqual({ ...p.form, details: Array.from(p.form.details, row => ({ ...row })) }, before)
})
test("actual template compiles and reference fields have no editable binding", () => {
  const source = fs.readFileSync(path.resolve(__dirname, "../src/views/inventory/stockCheck/index.vue"), "utf8")
  const parsed = compiler.parseComponent(source), result = compiler.compile(parsed.template.content)
  assert.deepStrictEqual(result.errors, [])
  assert.ok(source.includes('label="上轮参考"'))
  assert.ok(source.includes('v-if="!isBlindInputForm">原账面'))
  assert.ok(source.includes('v-if="!isBlindCheck(detail)">原账面'))
  assert.ok(!/v-model[^=]*="[^"]*previous(?:Actual|Recount|Book)Qty/.test(source))
})
