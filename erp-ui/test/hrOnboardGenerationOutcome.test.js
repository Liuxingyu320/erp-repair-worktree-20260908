const assert = require("node:assert/strict")
const { test } = require("node:test")
const fs = require("node:fs")
const path = require("node:path")
const Vue = require("vue")
const compiler = require("vue-template-compiler")

Vue.config.productionTip = false
Vue.config.silent = true

const sourcePath = path.resolve(__dirname, "../src/views/hr/components/HrSignDataImportDialog.vue")
const descriptor = compiler.parseComponent(fs.readFileSync(sourcePath, "utf8"))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const script = descriptor.script.content
  .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
  .replace("export default", "return")

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function readyRow(rowId) {
  return { rowId: String(rowId), version: 1, status: "READY_TO_GENERATE", planVersionId: "10", missingFields: [], warningCodes: [] }
}

function outcome(results, rowIds = results.map((_, index) => String(index + 1))) {
  return {
    data: {
      totalCount: results.length,
      generatedCount: results.filter(value => value === "GENERATED").length,
      reusedCount: results.filter(value => value === "REUSED").length,
      blockedCount: results.filter(value => value === "BLOCKED").length,
      failedCount: results.filter(value => value === "FAILED").length,
      items: results.map((result, index) => ({ rowId: rowIds[index], result, taskId: String(100 + index), packageId: String(200 + index) }))
    }
  }
}

function harness(options = {}) {
  const calls = { sync: [], generate: [], refresh: [], send: [], success: [], warning: [] }
  let requestNumber = 0
  let generatedResult
  const handlers = {
    sync: async () => {},
    generate: async () => outcome(["GENERATED"]),
    refresh: async batchId => ({ data: { batchId, version: 2, rows: (generatedResult && generatedResult.data && generatedResult.data.items || []).map(item => ({
      ...readyRow(item.rowId), status: ["GENERATED", "REUSED"].includes(item.result) ? "GENERATED" : "NEEDS_HR_DATA",
      taskId: item.taskId, packageId: item.packageId
    })) } }),
    ...options
  }
  const globals = {
    SignScopeSelector: {}, getSelectedSignScopeDeptId: () => "88",
    profileFieldLabel: value => value, signTaskStatusLabel: value => value, signTaskReasonLabel: value => value,
    syncOnboardSalaryBeforeGenerate: async (rows, state) => { calls.sync.push({ rows, state }); return handlers.sync(rows, state) },
    generateOnboardSignImport: async (batchId, request) => {
      calls.generate.push({ batchId, request })
      generatedResult = await handlers.generate(batchId, request)
      return generatedResult
    },
    getOnboardSignImportBatch: async batchId => { calls.refresh.push(batchId); return handlers.refresh(batchId) },
    sendSignTaskBatch: async request => { calls.send.push(request); throw new Error("generation must not send") },
    getOnboardSignDataRequest() {}, previewOnboardSignImport() {}, reviewOnboardSignDataRequest() {},
    sendOnboardSignDataRequests() {}, updateOnboardSignImportRow() {},
    crypto: { randomUUID: () => `generation-request-${++requestNumber}` }
  }
  const definition = new Function(...Object.keys(globals), script)(...Object.values(globals))
  const model = new Vue({ ...definition, propsData: { visible: true, employees: [] } })
  model.$modal = { msgSuccess: value => calls.success.push(value), msgWarning: value => calls.warning.push(value) }
  model.batch = { batchId: "77", version: 1 }
  model.rows = (options.rowIds || ["1"]).map(readyRow)
  model.selectedRows = model.rows.slice()
  model.confirmation.noExternalContractConfirmed = true
  model.activeStep = 1
  return { model, calls, handlers, definition }
}

for (const result of ["FAILED", "BLOCKED"]) {
  test(`all ${result} results stay in review without claiming contracts were generated`, async () => {
    const { model, calls } = harness({ generate: async () => outcome([result, result]), rowIds: ["1", "2"] })
    await model.generateRows(model.rows)
    assert.equal(calls.success.length, 0)
    assert.equal(calls.warning.length, 1)
    assert.match(model.operationError, /本次没有可预览的合同/)
    assert.equal(model.activeStep, 1)
    assert.equal(model.generating, false)
    assert.equal(calls.refresh.length, 1)
    assert.equal(calls.send.length, 0)
    model.$destroy()
  })
}

for (const [name, response] of [
  ["empty", {}], ["null", null], ["unknown item", outcome(["UNKNOWN"])],
  ["summary without rows", { data: { totalCount: 1, generatedCount: 1, reusedCount: 0, blockedCount: 0, failedCount: 0 } }],
  ["wrong row", outcome(["GENERATED"], ["99"])],
  ["inconsistent count", { data: { ...outcome(["FAILED"]).data, generatedCount: 1 } }]
]) {
  test(`${name} response preserves the original generation request for recovery`, async () => {
    const { model, calls, handlers } = harness({ generate: async () => response })
    const salaryState = model.salarySyncState
    await model.generateRows(model.rows)
    const requestId = calls.generate[0].request.requestId
    assert.equal(model.generateRequestId, requestId)
    assert.equal(model.salarySyncState, salaryState)
    assert.equal(model.activeStep, 1)
    assert.match(model.operationError, /结果暂未确认/)
    assert.equal(calls.success.length, 0)
    assert.equal(calls.refresh.length, 0)
    handlers.generate = async () => outcome(["REUSED"])
    await model.generateRows(model.rows)
    assert.equal(calls.generate[1].request.requestId, requestId)
    assert.equal(model.activeStep, 2)
    assert.equal(calls.success.length, 1)
    assert.match(calls.success[0], /复用 1 份/)
    assert.notEqual(model.generateRequestId, requestId)
    assert.equal(calls.send.length, 0)
    model.$destroy()
  })
}

test("partial success reports each outcome and enables preview only for completed contracts", async () => {
  const { model, calls } = harness({ rowIds: ["1", "2", "3", "4"], generate: async () => outcome(["GENERATED", "REUSED", "FAILED", "BLOCKED"]) })
  await model.generateRows(model.rows)
  assert.equal(calls.success.length, 0)
  assert.equal(calls.warning.length, 1)
  assert.match(calls.warning[0], /生成 1 份，复用 1 份，需处理 1 份，失败 1 份/)
  assert.match(calls.warning[0], /本次不会自动发送/)
  assert.equal(model.activeStep, 2)
  assert.equal(model.generatedRows.length, 2)
  assert.equal(calls.send.length, 0)
  model.$destroy()
})

test("successful signature-first generation preserves the separate final-file send step", async () => {
  const { model, calls } = harness()
  model.signingSequence = "SIGNATURE_FIRST"
  model.rows[0].dataRequestSigningSequence = "SIGNATURE_FIRST"
  model.rows[0].dataRequestSignatureCaptured = true
  await model.generateRows(model.rows)
  assert.equal(calls.success.length, 1)
  assert.match(calls.success[0], /最终合同可预览.*本次不会自动发送/)
  assert.equal(model.activeStep, 2)
  assert.equal(calls.send.length, 0)
  model.$destroy()
})

test("duplicate clicks during salary synchronization issue one generation command", async () => {
  const pending = deferred()
  const { model, calls } = harness({ sync: () => pending.promise })
  const first = model.generateRows(model.rows)
  await model.generateRows(model.rows)
  assert.equal(calls.sync.length, 1)
  assert.equal(calls.generate.length, 0)
  pending.resolve()
  await first
  assert.equal(calls.generate.length, 1)
  assert.equal(model.generating, false)
  model.$destroy()
})

test("a lost generation response retries the same request and retained salary-sync state", async () => {
  const { model, calls, handlers } = harness({ generate: async () => { throw new Error("请求超时") } })
  await model.generateRows(model.rows)
  const firstId = calls.generate[0].request.requestId
  const state = calls.sync[0].state
  assert.equal(model.generateRequestId, firstId)
  assert.equal(calls.success.length, 0)
  handlers.generate = async () => outcome(["REUSED"])
  await model.generateRows(model.rows)
  assert.equal(calls.generate[1].request.requestId, firstId)
  assert.equal(calls.sync[1].state, state)
  assert.equal(model.activeStep, 2)
  model.$destroy()
})

test("known success with failed refresh retains the command and does not pretend the list is ready", async () => {
  const { model, calls } = harness({ refresh: async () => { throw new Error("刷新超时") } })
  const state = model.salarySyncState
  await model.generateRows(model.rows)
  assert.equal(model.generateRequestId, calls.generate[0].request.requestId)
  assert.equal(model.salarySyncState, state)
  assert.equal(model.activeStep, 1)
  assert.equal(calls.success.length, 0)
  assert.match(calls.warning[0], /本次生成 1 份.*列表刷新失败/)
  assert.equal(model.generating, false)
  assert.equal(model.refreshLoading, false)
  model.$destroy()
})

for (const [name, response] of [
  ["empty", {}], ["null", null],
  ["wrong batch", { data: { batchId: "99", version: 2, rows: [readyRow("1")] } }],
  ["missing rows", { data: { batchId: "77", version: 2 } }],
  ["invalid row", { data: { batchId: "77", version: 2, rows: [null] } }]
]) {
  test(`${name} refresh retains the original batch and command until authoritative recovery`, async () => {
    const { model, calls, handlers } = harness({ refresh: async () => response })
    const batch = model.batch
    const rows = model.rows
    const state = model.salarySyncState
    await model.generateRows(model.rows)
    const requestId = calls.generate[0].request.requestId
    assert.equal(model.batch, batch)
    assert.equal(model.rows, rows)
    assert.equal(model.generateRequestId, requestId)
    assert.equal(model.salarySyncState, state)
    assert.equal(model.activeStep, 1)
    assert.equal(calls.success.length, 0)
    assert.match(model.operationError, /列表刷新失败.*保留原请求号/)
    assert.equal(model.generating, false)
    assert.equal(model.refreshLoading, false)
    handlers.generate = async () => outcome(["REUSED"])
    handlers.refresh = async batchId => ({ data: { batchId, version: 2, rows: [{ ...readyRow("1"), status: "GENERATED", taskId: "100", packageId: "200" }] } })
    await model.generateRows(model.rows)
    assert.equal(calls.generate[1].request.requestId, requestId)
    assert.equal(model.activeStep, 2)
    assert.equal(calls.success.length, 1)
    assert.equal(calls.send.length, 0)
    model.$destroy()
  })
}

test("closing during salary sync prevents the later generation POST", async () => {
  const pending = deferred()
  const { model, calls } = harness({ sync: () => pending.promise })
  const running = model.generateRows(model.rows)
  model.visible = false
  await Vue.nextTick()
  pending.resolve()
  await running
  assert.equal(calls.generate.length, 0)
  assert.equal(calls.success.length, 0)
  model.$destroy()
})

test("a late POST failure cannot replace the next batch error or unlock its generation", async () => {
  const pending = deferred()
  const entered = deferred()
  const { model, calls } = harness({ generate: () => { entered.resolve(); return pending.promise } })
  const running = model.generateRows(model.rows)
  await entered.promise
  assert.equal(calls.generate.length, 1)
  model.invalidatePreview("新批次提示")
  model.batch = { batchId: "99", version: 1 }
  model.generating = true
  pending.reject(new Error("旧批次失败"))
  await running
  assert.equal(model.operationError, "新批次提示")
  assert.equal(model.generating, true)
  assert.equal(calls.success.length, 0)
  model.$destroy()
})

test("a late POST success cannot announce a result for a different batch", async () => {
  const pending = deferred()
  const entered = deferred()
  const { model, calls } = harness({ generate: () => { entered.resolve(); return pending.promise } })
  const running = model.generateRows(model.rows)
  await entered.promise
  model.invalidatePreview("已换批次")
  model.batch = { batchId: "99", version: 1 }
  pending.resolve(outcome(["GENERATED"]))
  await running
  assert.equal(calls.refresh.length, 0)
  assert.equal(calls.success.length, 0)
  assert.equal(model.operationError, "已换批次")
  assert.equal(model.activeStep, 0)
  model.$destroy()
})

test("destroying the actual component during salary sync prevents generation", async () => {
  const pending = deferred()
  const { model, calls } = harness({ sync: () => pending.promise })
  const running = model.generateRows(model.rows)
  model.$destroy()
  pending.resolve()
  await running
  assert.equal(calls.generate.length, 0)
  assert.equal(calls.success.length, 0)
})

test("a late refresh cannot overwrite a reopened dialog or clear its loading flags", async () => {
  const pending = deferred()
  const entered = deferred()
  const { model, calls } = harness({ refresh: () => { entered.resolve(); return pending.promise } })
  const running = model.generateRows(model.rows)
  await entered.promise
  model.visible = false
  await Vue.nextTick()
  model.visible = true
  await Vue.nextTick()
  model.batch = { batchId: "99", version: 1 }
  model.rows = [readyRow("9")]
  model.generating = true
  model.refreshLoading = true
  pending.resolve({ data: { batch: { batchId: "77" }, rows: [{ rowId: "1", status: "GENERATED" }] } })
  await running
  assert.equal(model.batchId, "99")
  assert.equal(model.rows[0].rowId, "9")
  assert.equal(model.activeStep, 0)
  assert.equal(model.generating, true)
  assert.equal(model.refreshLoading, true)
  assert.equal(calls.success.length, 0)
  assert.equal(calls.warning.length, 0)
  model.$destroy()
})
