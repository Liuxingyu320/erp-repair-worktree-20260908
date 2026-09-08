const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const componentPath = path.resolve(__dirname, "../src/views/mobile/signPackage/index.vue")
const apiPath = path.resolve(__dirname, "../src/api/oa/signPackage.js")
const componentSource = fs.readFileSync(componentPath, "utf8")
const apiSource = fs.readFileSync(apiPath, "utf8")
const fileGate = require("../src/utils/signPackageFileGate")
const mobileSignPackagePolicy = require("../src/views/mobile/signPackage/mobileSignPackagePolicy")

assert.ok(apiSource.includes("export function refuseMySignPackage"))
assert.ok(apiSource.includes("'/oa/signPackage/mobile/' + packageId + '/refuse'"))
assert.deepStrictEqual(
  mobileSignPackagePolicy.REFUSAL_REASON_OPTIONS.map(option => option.value),
  [
    "CONTRACT_CONTENT_DISPUTE",
    "PERSONAL_INFORMATION_ERROR",
    "COMPANY_INFORMATION_ERROR",
    "SIGNING_NOT_INTENDED",
    "OTHER"
  ],
  "refusal should require a structured reason category"
)
assert.ok(componentSource.includes('maxlength="500"') && componentSource.includes("原因说明"))
assert.ok(componentSource.includes("$modal.confirm") && componentSource.includes("拒签后本版本将永久终止"),
  "refusal must have an explicit second confirmation")
assert.ok(componentSource.includes("使用原请求号重试") &&
  componentSource.includes("refuseFrozenMutation") &&
  componentSource.includes(':disabled="refusing || !!refuseError || mutationReplayLocked"'),
"an uncertain response must freeze the payload and expose a stable-request retry")
assert.ok(mobileSignPackagePolicy.TERMINAL_PACKAGE_STATUSES.includes("refused") &&
  mobileSignPackagePolicy.TERMINAL_PACKAGE_STATUSES.includes("expired") &&
  mobileSignPackagePolicy.createStatusTabs()
    .find(tab => tab.value === "voided").statuses.includes("failed"),
"refused and expired packages must remain visible in the terminal tab")

function loadComponent(apiOverrides = {}) {
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
    process: { env: { VUE_APP_BASE_API: "" } },
    ...apiOverrides
  }
  vm.runInNewContext(script, sandbox, { filename: componentPath })
  return sandbox.module.exports
}

;(async () => {
  const payloads = []
  let attempts = 0
  const current = {
    packageId: "91",
    status: "pending_sign",
    documentVersion: "document-v1",
    signDeadline: "2099-01-01 23:59:59",
    deadlinePolicySource: "PLAN_VERSION",
    deadlineDaysSnapshot: 7,
    documents: []
  }
  const refused = Object.assign({}, current, {
    status: "refused",
    terminalReasonCode: "CONTRACT_CONTENT_DISPUTE",
    terminalReasonDetail: "合同内容需要重新确认"
  })
  let refreshedCurrent = current
  const component = loadComponent({
    getMySignPackage: () => Promise.resolve({ data: refreshedCurrent }),
    refuseMySignPackage: (packageId, payload) => {
      attempts += 1
      payloads.push({ packageId, payload })
      return attempts === 1
        ? Promise.reject(new Error("拒签响应中断"))
        : Promise.resolve({ data: refused })
    }
  })
  const confirmations = []
  const successes = []
  const errors = []
  const context = {
    ...component.data(),
    ...component.methods,
    selectedPackage: current,
    canRefuse: true,
    refuseOpen: true,
    refuseRequestId: "stable-refusal-request",
    refuseForm: {
      reasonCode: "CONTRACT_CONTENT_DISPUTE",
      reasonDetail: "合同内容需要重新确认"
    },
    $modal: {
      confirm(message) { confirmations.push(message); return Promise.resolve() },
      msgWarning() {},
      msgSuccess(message) { successes.push(message) },
      msgError(message) { errors.push(message) }
    },
    $message: { error(message) { errors.push(message) } },
    applyPackageDetail(signPackage) {
      this.selectedPackage = signPackage
      return signPackage
    },
    loadList: () => Promise.resolve(true)
  }

  assert.strictEqual(await context.requestRefusal(), null)
  assert.strictEqual(context.refusing, false)
  assert.ok(context.refuseError.includes("响应中断"))
  assert.strictEqual(context.refuseRequestId, "stable-refusal-request",
    "an uncertain refusal response must retain its requestId")
  assert.strictEqual(context.refuseForm.reasonDetail, "合同内容需要重新确认")
  assert.ok(Object.isFrozen(context.refuseFrozenMutation))
  assert.ok(Object.isFrozen(context.refuseFrozenMutation.payload))
  context.refuseError = ""
  context.closeRefusal()
  assert.strictEqual(context.refuseForm.reasonDetail, "合同内容需要重新确认",
    "closing a frozen refusal dialog cannot clear its reason")

  context.refuseOpen = true
  context.refuseForm = { reasonCode: "OTHER", reasonDetail: "被篡改的拒签原因" }
  context.refuseRequestId = "mutated-refusal-request"
  refreshedCurrent = Object.assign({}, current, { documentVersion: "document-v2" })

  const retried = await context.requestRefusal()
  assert.strictEqual(retried.data.status, "refused")
  assert.strictEqual(context.selectedPackage.status, "refused")
  assert.strictEqual(context.refuseRequestId, "")
  assert.strictEqual(context.refuseOpen, false)
  assert.strictEqual(confirmations.length, 2, "each submission attempt must receive a second confirmation")
  assert.strictEqual(payloads[0].payload, payloads[1].payload,
    "refusal retries must send the exact first frozen payload")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(payloads[1].payload)), {
    requestId: "stable-refusal-request",
    documentVersion: "document-v1",
    reasonCode: "CONTRACT_CONTENT_DISPUTE",
    reasonDetail: "合同内容需要重新确认"
  })
  assert.deepStrictEqual(payloads.map(item => item.packageId), ["91", "91"])
  assert.strictEqual(context.refuseFrozenMutation, null)
  assert.strictEqual(successes.includes("拒签已提交，本版本已转为只读"), true)

  console.log("sign package refusal tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
