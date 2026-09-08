const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const componentPath = path.resolve(__dirname, "../src/views/mobile/signPackage/index.vue")
const componentSource = fs.readFileSync(componentPath, "utf8")
const fileGate = require("../src/utils/signPackageFileGate")
const mobileSignPackagePolicy = require("../src/views/mobile/signPackage/mobileSignPackagePolicy")

assert.ok(componentSource.includes("签署截止") &&
  mobileSignPackagePolicy.summarizeDeadline({
    status: "pending_sign",
    signDeadline: "2099-01-01 00:01:00"
  }, Date.parse("2099-01-01T00:00:00+08:00")).includes("剩余"),
  "mobile signing should expose the frozen deadline and a countdown")
assert.ok(componentSource.includes("签署时间已截止") && componentSource.includes("已超过签署截止时间"),
  "locally elapsed deadlines must fail closed before the expiry scanner updates status")
assert.ok(componentSource.includes("activeDocument.readConfirmed === 'Y'") &&
  componentSource.includes("deadlineMissing || deadlineExpired || fileLoading"),
  "read confirmation must also stop after the deadline")
assert.ok(
  mobileSignPackagePolicy.shouldUseFinalDocument(
    { status: "signed" },
    { finalFileAvailable: true }
  ) &&
  !mobileSignPackagePolicy.shouldUseFinalDocument(
    { status: "refused" },
    { finalFileAvailable: true }
  ),
  "only confirmed packages should expose the final archive; refused or expired pending files stay unavailable")
assert.ok(componentSource.includes("initializePackageEntry") &&
  componentSource.includes("openPackageDeepLink") &&
  componentSource.includes("consumePackageDeepLink"),
"packageId notification links must restore and consume the exact package target")

function loadComponent() {
  const scriptMatch = componentSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch)
  const script = scriptMatch[1]
    .replace(/^import(?:[\s\S]*?)\s+from\s+['"][^'"]+['"]\s*$/gm, "")
    .replace(/export default/, "module.exports =")
  const noopPdf = () => Promise.resolve(new Blob(["%PDF-1.7\n%%EOF\n"], { type: "application/pdf" }))
  const noopPng = () => Promise.resolve(new Blob([
    Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10])
  ], { type: "image/png" }))
  const sandbox = {
    module: { exports: {} },
    exports: {},
    confirmSignPackageFinalDocumentRead: () => Promise.resolve({ data: {} }),
    confirmSignPackageDocumentRead: () => Promise.resolve({ data: {} }),
    confirmMyFinalSignPackage: () => Promise.resolve({ data: {} }),
    downloadMySignPackageCertificate: noopPdf,
    downloadMySignPackageDocument: noopPdf,
    downloadMySignPackageDocumentPreviewPage: noopPng,
    downloadMyFinalSignPackageDocument: noopPdf,
    downloadMySignedSignPackageDocument: noopPdf,
    getMySignPackageDocumentPreview: () => Promise.resolve({ data: { pageCount: 1 } }),
    getMySignPackage: () => Promise.resolve({ data: {} }),
    listMySignPackages: () => Promise.resolve({ rows: [] }),
    refuseMySignPackage: () => Promise.resolve({ data: {} }),
    signMySignPackage: () => Promise.resolve({ data: {} }),
    parseTime: value => value,
    signDictionaryLabel: value => value,
    require(request) {
      if (request === "@/mixins/todoBusinessFocus") {
        return { createTodoPersonalFocusMixin: () => ({}) }
      }
      if (request === "../mobileViewport") return {}
      if (request === "@/utils/signScenario") return { signScenarioLabel: value => value }
      if (request === "@/utils/signPackageFileGate") return fileGate
      if (request === "@/utils/signDateTime") return require("../src/utils/signDateTime")
      if (request === "./mobileSignPackagePolicy") return mobileSignPackagePolicy
      throw new Error(`Unexpected require: ${request}`)
    },
    window: {
      crypto: { randomUUID: () => "generated-request-id" },
      addEventListener() {},
      removeEventListener() {},
      setInterval() { return 1 },
      clearInterval() {},
      devicePixelRatio: 1
    },
    URL: { createObjectURL: () => "blob:test", revokeObjectURL() {} },
    Blob,
    Uint8Array,
    Promise,
    Object,
    Array,
    String,
    Number,
    Boolean,
    Math,
    RegExp,
    TypeError,
    Date,
    process: { env: { VUE_APP_BASE_API: "" } }
  }
  vm.runInNewContext(script, sandbox, { filename: componentPath })
  return sandbox.module.exports
}

;(async () => {
  const component = loadComponent()
  const now = Date.parse("2026-07-17T12:00:00+08:00")
  const active = {
    packageId: "77",
    status: "pending_sign",
    signDeadline: "2026-07-17 13:30:00",
    deadlinePolicySource: "PLAN_VERSION",
    deadlineDaysSnapshot: 7
  }
  const context = {
    ...component.data(),
    ...component.methods,
    selectedPackage: active,
    deadlineNow: now
  }

  assert.strictEqual(component.computed.deadlineMissing.call(context), false)
  assert.strictEqual(component.computed.deadlineExpired.call(context), false)
  assert.ok(context.deadlineSummary(active).includes("剩余 1小时30分钟"))

  context.selectedPackage = Object.assign({}, active, { signDeadline: "2026-07-17 11:59:59" })
  context.deadlineExpired = component.computed.deadlineExpired.call(context)
  assert.strictEqual(context.deadlineExpired, true)
  context.showSignBar = true
  context.deadlineMissing = false
  assert.ok(component.computed.signDisabledReason.call(context).includes("不能继续签署"))
  assert.strictEqual(component.computed.canRefuse.call(context), false)

  context.selectedPackage = Object.assign({}, active, {
    status: "pending_final_confirm",
    signDeadline: "2026-07-17 11:59:59"
  })
  assert.ok(component.computed.finalConfirmDisabledReason.call(context).includes("不能确认文件"))

  context.selectedPackage = Object.assign({}, active, { deadlinePolicySource: "", deadlineDaysSnapshot: null })
  context.deadlineExpired = false
  assert.strictEqual(component.computed.deadlineMissing.call(context), true,
    "missing frozen deadline policy must fail closed")

  context.selectedPackage = Object.assign({}, active, {
    status: "pending_company",
    signDeadline: "2026-07-17 11:59:59"
  })
  assert.strictEqual(component.computed.deadlineExpired.call(context), false,
    "the HR company stage must not consume employee signing time")
  assert.ok(context.deadlineSummary(context.selectedPackage).includes("计时已暂停"))

  context.selectedPackage = { status: "refused" }
  assert.strictEqual(context.shouldUseFinalDocument({ finalFileAvailable: true }), false)
  context.selectedPackage = { status: "expired" }
  assert.strictEqual(context.shouldUseFinalDocument({ finalFileAvailable: true }), false)
  context.selectedPackage = { status: "signed" }
  assert.strictEqual(context.shouldUseFinalDocument({ finalFileAvailable: true }), true)
  assert.strictEqual(context.shouldUseFinalDocument({ finalFileAvailable: false }), false)

  const opened = []
  const replacements = []
  const deepLinkContext = {
    ...component.data(),
    ...component.methods,
    $route: { path: "/mobile/sign-package", query: { packageId: "77", source: "push" } },
    $router: {
      replace(location) {
        replacements.push(location)
        return Promise.resolve()
      }
    },
    loadList: () => Promise.resolve(true),
    openPackage(item) {
      opened.push(item.packageId)
      return Promise.resolve({ packageId: item.packageId, status: "expired" })
    },
    initializeTodoPersonalFocus: () => Promise.reject(new Error("deep link should win"))
  }
  const restored = await deepLinkContext.initializePackageEntry()
  assert.strictEqual(restored.packageId, "77")
  assert.deepStrictEqual(opened, ["77"])
  assert.strictEqual(replacements.length, 1)
  assert.strictEqual(replacements[0].path, "/mobile/sign-package")
  assert.strictEqual(replacements[0].query.packageId, undefined)
  assert.strictEqual(replacements[0].query.source, "push")

  console.log("sign deadline UX tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
