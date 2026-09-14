const assert = require("node:assert/strict")
const { test } = require("node:test")
const fs = require("node:fs")
const vm = require("node:vm")
const path = require("node:path")
const runtime = require("../src/views/mobile/feature/featureActionRuntime")
const servicePath = path.resolve(__dirname, "../src/views/mobile/feature/featureActionService.js")

function actualService(precheck, submit) {
  const api = { "@/api/oa/fixedAsset": { precheckFixedAssetRepair: precheck, submitFixedAssetRepair: submit } }
  let source = fs.readFileSync(servicePath, "utf8")
  source = source.replace(/import\s+(\{[\s\S]*?\})\s+from\s+"([^"]+)"/g,
    (_, bindings, module) => "const " + bindings.replace(/\bas\b/g, ":") + " = api[" + JSON.stringify(module) + "] || {}")
    .replace(/export function /g, "function ")
  const sandbox = { api, require(id) {
    if (id === './featureActionRuntime') return runtime
    if (id === '@/utils/purchaseReceiveRecovery') return require('../src/utils/purchaseReceiveRecovery')
    throw new Error('unexpected action service dependency ' + id)
  }, module: { exports: {} } }
  vm.runInNewContext(source + "\nmodule.exports = { saveMobileFeatureForm }", sandbox)
  return sandbox.module.exports
}
test("the actual service injects precheck before one repair submission", async () => {
  const calls = []
  const service = actualService(async () => { calls.push("precheck"); return { data: { allowed: true } } },
    async () => { calls.push("submit"); return { code: 200 } })
  await service.saveMobileFeatureForm("fixedAssetRepair", { oeItemId: 3 })
  assert.deepEqual(calls, ["precheck", "submit"])
})
test("quota rejection and invalid results do not submit", async () => {
  let submits = 0
  const denied = { allowed: false, message: "额度不足", purchaseReferenceReady: true }
  await assert.rejects(actualService(async () => ({ data: denied }), () => submits++)
    .saveMobileFeatureForm("fixedAssetRepair", {}), error => runtime.getFixedAssetPrecheckFromError(error) === denied)
  await assert.rejects(actualService(async () => ({ data: {} }), () => submits++)
    .saveMobileFeatureForm("fixedAssetRepair", {}), /有效/)
  assert.equal(submits, 0)
})
test("closing or editing the form during precheck cancels the later submission", async () => {
  let resolve, submits = 0, current = true
  const service = actualService(() => new Promise(r => { resolve = r }), () => submits++)
  const request = service.saveMobileFeatureForm("fixedAssetRepair", {}, { isCurrent: () => current })
  current = false
  resolve({ data: { allowed: true } })
  await assert.rejects(request, /表单已变化/)
  assert.equal(submits, 0)
})

const babel = require("@babel/core")
const generate = require("@babel/generator").default
const compiler = require("vue-template-compiler")
const Vue = require("vue")
const pagePath = path.resolve(__dirname, "../src/views/mobile/feature/index.vue")
const sheetPath = path.resolve(__dirname, "../src/views/mobile/feature/components/MobileFormSheet.vue")
const { getMobileFormConfig } = require("../src/views/mobile/feature/mobileFormConfigs")
const { buildMobileFormPayload } = require("../src/views/mobile/feature/mobileFormPayloads")
const { mobileErrorMessage } = require("../src/views/mobile/mobileErrorMessage")

// Extract unchanged executable methods from the actual SFC. Network/UI boundaries
// are explicit dependencies; no copy of the submit implementation or universal API proxy.
function actualMethods(filename, names, dependencies = {}) {
  const descriptor = compiler.parseComponent(fs.readFileSync(filename, "utf8"))
  const parsed = babel.parseSync(descriptor.script.content, {
    filename, babelrc: false, configFile: false, sourceType: "module"
  })
  const definition = parsed.program.body.find(node => node.type === "ExportDefaultDeclaration").declaration
  const methods = definition.properties.find(node => node.key.name === "methods").value
  const selected = names.map(name => {
    const method = methods.properties.find(node => node.key.name === name)
    assert.ok(method, `${filename} must expose real ${name}`)
    return method
  })
  const expression = generate({ type: "ObjectExpression", properties: selected }).code
  const sandbox = Object.assign({ module: { exports: {} }, Promise }, dependencies)
  vm.runInNewContext("module.exports = " + expression, sandbox, { filename })
  return sandbox.module.exports
}
function deferredResponse() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const settlePage = () => new Promise(resolve => setImmediate(resolve))

function pageHarness(service) {
  const effects = { success: [], errors: [], acknowledgements: [], loads: 0, closedDetails: 0 }
  const config = getMobileFormConfig("fixedAssetRepair")
  const instance = {
    featureKey: "fixedAssetRepair", selectedDeptId: 8, selectedDeptType: "STORE", pageNum: 3,
    formSheet: {
      open: true, mode: "create", saving: false, error: "", validationError: null,
      fixedAssetPrecheck: null, config, data: { oeItemId: 3, quantity: 1, repairReason: "破损" }, initialData: {}
    },
    canUseMobileSubmitMode: () => true,
    getMobileFormValidationError: () => null,
    showActionSuccess(action, response) { effects.success.push({ action, response }) },
    showToastError(message) { effects.errors.push(message) },
    closeItem() { effects.closedDetails++ },
    loadData() { effects.loads++ },
    $set(target, key, value) { target[key] = value }
  }
  const methods = actualMethods(pagePath, [
    "submitMobileForm", "closeMobileForm", "handleMobileFormInput", "snapshotMobileFormData", "normalizeMobileFormPayload"
  ], {
    saveMobileFeatureForm: service.saveMobileFeatureForm,
    acknowledgeTransferCommand: response => effects.acknowledgements.push(response),
    getFixedAssetPrecheckFromError: runtime.getFixedAssetPrecheckFromError,
    mobileErrorMessage, buildMobileFormPayload
  })
  for (const [name, method] of Object.entries(methods)) instance[name] = method.bind(instance)
  return { instance, effects }
}

function pendingRepairPage() {
  const response = deferredResponse(), dispatched = deferredResponse(), calls = []
  const service = actualService(
    async () => { calls.push("precheck"); return { data: { allowed: true } } },
    payload => { calls.push("submit"); dispatched.resolve(payload); return response.promise }
  )
  return { ...pageHarness(service), response, dispatched, calls }
}

test("actual page consumes a successful POST even if local data changes after dispatch", async () => {
  const h = pendingRepairPage()
  h.instance.submitMobileForm("save")
  await h.dispatched.promise
  assert.equal(h.instance.formSheet.saving, true)
  // Deliberately bypass UI freezing to guard against a late asynchronous model mutation.
  h.instance.formSheet.data.repairReason = "POST之后的本地变化"
  h.instance.formSheet.data.quantity = 2
  h.response.resolve({ code: 200, data: { repairId: 901 } })
  await settlePage()
  assert.deepEqual(h.calls, ["precheck", "submit"])
  assert.equal(h.effects.success.length, 1)
  assert.equal(h.effects.success[0].response.data.repairId, 901)
  assert.equal(h.effects.acknowledgements.length, 1)
  assert.equal(h.effects.loads, 1)
  assert.equal(h.effects.closedDetails, 1)
  assert.equal(h.instance.formSheet.open, false, "the successful create must not remain open for a second create")
  assert.equal(h.instance.formSheet.saving, false)
})

test("actual page stores quota precheck in the active form and does not issue a POST", async () => {
  const quota = { allowed: false, message: "额度不足", oeItemName: "茶具", purchaseReferenceReady: true, purchaseReferenceUrl: "https://example.test/tea" }
  let posts = 0
  const h = pageHarness(actualService(async () => ({ data: quota }), () => { posts++; return Promise.resolve() }))
  h.instance.submitMobileForm("save")
  await settlePage()
  assert.equal(posts, 0)
  assert.equal(h.instance.formSheet.fixedAssetPrecheck, quota)
  assert.equal(h.instance.formSheet.error, "额度不足")
  assert.equal(h.instance.formSheet.open, true)
  assert.equal(h.instance.formSheet.saving, false)
  assert.equal(h.instance.formSheet.validationError, null)
  assert.deepEqual(h.effects.errors, ["额度不足"])
  assert.equal(h.effects.success.length, 0)
})

for (const outcome of ["success", "failure"]) {
  test(`actual page ignores late ${outcome} after closing and opening another form`, async () => {
    const h = pendingRepairPage()
    h.instance.submitMobileForm("save")
    await h.dispatched.promise
    h.instance.closeMobileForm(true)
    const nextSheet = {
      open: true, mode: "create", saving: false, error: "新表单提示", validationError: { message: "新校验" },
      fixedAssetPrecheck: { allowed: false, message: "新预检" }, data: { oeItemId: 77, quantity: 8 }
    }
    h.instance.formSheet = nextSheet
    const before = JSON.stringify(nextSheet)
    if (outcome === "success") h.response.resolve({ code: 200, data: { repairId: 902 } })
    else h.response.reject(new Error("旧请求失败"))
    await settlePage()
    assert.equal(h.instance.formSheet, nextSheet)
    assert.equal(JSON.stringify(nextSheet), before)
    assert.equal(h.effects.success.length, 0)
    assert.equal(h.effects.errors.length, 0)
    assert.equal(h.effects.loads, 0)
  })
}

test("actual page ignores a prior feature's response after navigation to a new form", async () => {
  const h = pendingRepairPage()
  h.instance.submitMobileForm("save")
  await h.dispatched.promise
  h.instance.featureKey = "sales"
  const nextSheet = { open: true, mode: "create", saving: true, data: { orderTitle: "新销售单" }, error: "" }
  h.instance.formSheet = nextSheet
  h.response.resolve({ code: 200, data: { repairId: 903 } })
  await settlePage()
  assert.equal(h.instance.formSheet, nextSheet)
  assert.equal(nextSheet.open, true)
  assert.equal(nextSheet.saving, true, "the old finally block must not unlock another form's pending submission")
  assert.equal(nextSheet.data.orderTitle, "新销售单")
  assert.equal(h.effects.success.length, 0)
  assert.equal(h.effects.loads, 0)
})

test("actual parent and child input methods reject changes and repeat submit while saving", () => {
  const h = pageHarness({ saveMobileFeatureForm: () => { throw new Error("unexpected submit") } })
  const originalData = h.instance.formSheet.data
  h.instance.formSheet.saving = true
  h.instance.handleMobileFormInput({ oeItemId: 99 })
  assert.equal(h.instance.formSheet.data, originalData)
  const methods = actualMethods(sheetPath, ["emitInput", "emitSubmit"])
  const events = []
  const child = { saving: true, localData: { oeItemId: 99 }, effectiveSubmitModes: [{ action: "save" }],
    returnSourceLoading: false, returnSourceError: "", $emit: (...event) => events.push(event) }
  for (const [name, method] of Object.entries(methods)) child[name] = method.bind(child)
  child.emitInput()
  child.emitSubmit("save")
  assert.equal(events.length, 0)
  child.saving = false
  child.emitInput()
  assert.equal(events.length, 1)
  assert.equal(events[0][0], "input")
  assert.equal(events[0][1].oeItemId, 99)
  h.instance.formSheet.saving = false
  h.instance.handleMobileFormInput(events[0][1])
  assert.equal(h.instance.formSheet.data.oeItemId, 99)
})

test("actual compiled form renders disabled and inert fields during saving and restores them afterwards", () => {
  const template = compiler.parseComponent(fs.readFileSync(sheetPath, "utf8")).template.content
  const compiled = compiler.compile(template)
  assert.deepEqual(compiled.errors, [])
  const render = new Function(compiled.render)
  const staticRenderFns = compiled.staticRenderFns.map(code => new Function(code))
  const instance = new Vue({
    render, staticRenderFns,
    data: () => ({ open: true, saving: true, fields: [], feature: { heading: "报修" }, title: "新增报修",
      iconPaths: { close: "" }, defaultSubmitAction: "save", primarySubmitAction: "save", effectiveSubmitModes: [],
      localData: {}, errorSummaryId: "repair-error-summary", showFixedAssetPurchaseReference: false, error: "", validationError: null,
      returnSourceLoading: false, returnSourceError: "", uploadsUnfinished: false }),
    methods: { emitSubmit() {}, emitInput() {}, handleFocusIn() {}, handleSubmitModeClick() {}, focusValidationError() {} }
  })
  function findFieldset(node) {
    if (!node) return null
    if (node.tag === "fieldset") return node
    for (const child of node.children || []) {
      const found = findFieldset(child)
      if (found) return found
    }
    return null
  }
  let fieldset = findFieldset(instance._render())
  assert.ok(fieldset, "actual form fields must remain under the native fieldset")
  assert.equal(fieldset.data.attrs.disabled, true)
  assert.equal(fieldset.data.attrs.inert, true)
  assert.equal(fieldset.data.attrs["aria-busy"], "true")
  instance.saving = false
  fieldset = findFieldset(instance._render())
  assert.equal(fieldset.data.attrs.disabled, false)
  assert.equal(fieldset.data.attrs.inert, null)
  assert.equal(fieldset.data.attrs["aria-busy"], "false")
  instance.$destroy()
})

test("actual page does not apply a late quota precheck to the next form", async () => {
  const response = deferredResponse()
  let posts = 0
  const h = pageHarness(actualService(() => response.promise, () => { posts++; return Promise.resolve() }))
  h.instance.submitMobileForm("save")
  h.instance.closeMobileForm(true)
  const nextSheet = { open: true, mode: "create", saving: false, error: "", data: { oeItemId: 88 }, fixedAssetPrecheck: null }
  h.instance.formSheet = nextSheet
  response.resolve({ data: { allowed: false, message: "旧表单额度不足" } })
  await settlePage()
  assert.equal(h.instance.formSheet, nextSheet)
  assert.equal(nextSheet.fixedAssetPrecheck, null)
  assert.equal(nextSheet.error, "")
  assert.equal(nextSheet.data.oeItemId, 88)
  assert.equal(posts, 0)
  assert.equal(h.effects.errors.length, 0)
})
