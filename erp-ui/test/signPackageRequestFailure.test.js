const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const componentPath = path.resolve(__dirname, "../src/views/mobile/signPackage/index.vue")
const componentSource = fs.readFileSync(componentPath, "utf8")
const policySource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/signPackage/mobileSignPackagePolicy.js"),
  "utf8"
)
const fileGate = require("../src/utils/signPackageFileGate")
const mobileSignPackagePolicy = require("../src/views/mobile/signPackage/mobileSignPackagePolicy")

assert.ok(
  componentSource.includes("pageLevelError") &&
    componentSource.indexOf('v-if="pageLevelError"') <
      componentSource.indexOf('filteredPackages.length === 0 && filteredDataRequests.length === 0"'),
  "a failed list request must render a single merged error with retry before the confirmed empty state"
)
assert.ok(
  componentSource.includes("pageLevelError") &&
    componentSource.includes("签约包刷新失败") &&
    componentSource.includes("handlePageLevelErrorAction") &&
    (componentSource.includes("重新刷新") || componentSource.includes("重新加载")),
  "detail refresh failures must stay visible with an explicit retry action"
)
for (const feedback of [
  "重新加载",
  "重新加载详情",
  "重试阅读确认",
  "重试提交签署",
  "重试确认文件"
]) {
  assert.ok(
    componentSource.includes(feedback) || policySource.includes(feedback),
    `mobile signing should expose explicit retry feedback: ${feedback}`
  )
}
assert.ok(componentSource.includes("freezeMutationEnvelope") &&
  componentSource.includes("signFrozenMutation") &&
  componentSource.includes("finalConfirmFrozenMutation"),
"sign and final-confirm retries must retain immutable full mutation envelopes")
assert.ok(componentSource.includes(':disabled="signing || mutationReplayLocked"') &&
  componentSource.includes('finalConfirming || mutationReplayLocked'),
"frozen mutations must disable signature editing and final-confirm checkbox changes")
assert.ok(componentSource.includes("{{ fileListProgressCount }}/{{ fileListProgressTotal }}"),
  "the file-list header must use stage-aware progress instead of the initial-sign gate counters")
assert.ok(componentSource.includes("getSignTaskCapabilities") &&
  componentSource.includes("excelImportEnabled") &&
  componentSource.includes("v-else-if=\"selectedPackage\"") &&
  componentSource.includes("reload-capabilities"),
  "mobile My Signings must fail closed for the Excel extension without rendering a null package")

function loadComponent(apiOverrides = {}) {
  const scriptMatch = componentSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "mobile sign package component script should exist")
  const script = scriptMatch[1]
    .replace(/^import(?:[\s\S]*?)\s+from\s+['"][^'"]+['"]\s*$/gm, "")
    .replace(/export default/, "module.exports =")
  const noopPromise = () => Promise.resolve(new Blob(["%PDF-1.7\n%%EOF\n"], { type: "application/pdf" }))
  const noopPng = () => Promise.resolve(new Blob([
    Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10])
  ], { type: "image/png" }))
  const sandbox = {
    module: { exports: {} },
    exports: {},
    confirmSignPackageFinalDocumentRead: () => Promise.resolve({ data: {} }),
    confirmSignPackageDocumentRead: () => Promise.resolve({ data: {} }),
    confirmMyFinalSignPackage: () => Promise.resolve({ data: {} }),
    downloadMySignPackageCertificate: noopPromise,
    downloadMySignPackageDocument: noopPromise,
    downloadMySignPackageDocumentPreviewPage: noopPng,
    downloadMyFinalSignPackageDocument: noopPromise,
    downloadMySignedSignPackageDocument: noopPromise,
    getMySignPackageDocumentPreview: () => Promise.resolve({ data: { pageCount: 1 } }),
    getMySignPackage: () => Promise.resolve({ data: {} }),
    listMySignPackages: () => Promise.resolve({ rows: [] }),
    getSignTaskCapabilities: () => Promise.resolve({ data: { coreEnabled: true, excelImportEnabled: true } }),
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
    process: { env: { VUE_APP_BASE_API: "", VUE_APP_SIGN_EXCEL_IMPORT_ENABLED: "true" } },
    ...apiOverrides
  }
  vm.runInNewContext(script, sandbox, { filename: componentPath })
  return sandbox.module.exports
}

function createContext(component, overrides = {}) {
  const warnings = []
  const successes = []
  const errors = []
  const context = {
    ...component.data(),
    $modal: {
      msgWarning(message) { warnings.push(message) },
      msgSuccess(message) { successes.push(message) },
      msgError(message) { errors.push(message) }
    },
    $message: { error(message) { errors.push(message) } },
    $refs: {},
    $nextTick(callback) {
      return Promise.resolve().then(() => callback && callback())
    },
    $set(target, key, value) { target[key] = value },
    $delete(target, key) { delete target[key] },
    $router: { back() {} },
    ...component.methods
  }
  defineComputed(context, component, ["excelImportFrontendEnabled", "excelImportEnabled"])
  Object.assign(context, overrides)
  return { context, errors, successes, warnings }
}

function defineComputed(context, component, names) {
  for (const name of names) {
    Object.defineProperty(context, name, {
      configurable: true,
      get() { return component.computed[name].call(context) }
    })
  }
}

function packageFixture(status = "pending_sign") {
  return {
    packageId: "pkg-1",
    version: 7,
    status,
    documentVersion: "review-v1",
    finalDocumentVersion: "final-v1",
    finalDocumentRootHash: "root-v1",
    documents: [{
      documentId: "doc-1",
      documentName: "劳动合同",
      employeeSignRequired: "Y",
      readConfirmationRequired: "Y",
      readConfirmed: "N",
      reviewPdfHash: "review-hash-1",
      finalPdfUrl: "/final.pdf",
      finalPdfHash: "final-hash-1"
    }]
  }
}

;(async () => {
  let listAttempts = 0
  let dataRequestListAttempts = 0
  const listComponent = loadComponent({
    listMySignPackages: () => {
      listAttempts += 1
      return listAttempts === 1
        ? Promise.reject(new Error("列表服务暂不可用"))
        : Promise.resolve({ rows: [{ packageId: "pkg-retry" }] })
    },
    listMyOnboardSignDataRequests: () => {
      dataRequestListAttempts += 1
      return Promise.resolve({ data: [{
        requestId: "91", status: "PENDING_EMPLOYEE", signingSequence: "SIGNATURE_FIRST"
      }] })
    }
  })
  const listHarness = createContext(listComponent)
  const firstListResult = await listHarness.context.loadList()
  assert.strictEqual(firstListResult, false, "list rejection should be handled instead of escaping")
  assert.strictEqual(listHarness.context.loading, false, "list loading must release after rejection")
  assert.ok(listHarness.context.listError.includes("暂不可用"))
  assert.strictEqual(listHarness.context.list.length, 0, "a failed first load must not masquerade as confirmed data")
  const retryListResult = await listHarness.context.loadList()
  assert.strictEqual(retryListResult, true)
  assert.strictEqual(listHarness.context.listError, "")
  assert.strictEqual(listHarness.context.list.length, 1)
  assert.strictEqual(dataRequestListAttempts, 2,
    "My Signings refreshes must load employee fact/signature work items with packages")
  assert.strictEqual(listHarness.context.dataRequestList.length, 1)
  assert.strictEqual(listHarness.context.dataRequestStatusTab(listHarness.context.dataRequestList[0]), "todo")

  let disabledPackageListAttempts = 0
  let disabledDataRequestAttempts = 0
  const disabledComponent = loadComponent({
    getSignTaskCapabilities: () => Promise.resolve({ data: { coreEnabled: true, excelImportEnabled: false } }),
    listMySignPackages: () => {
      disabledPackageListAttempts += 1
      return Promise.resolve({ rows: [] })
    },
    listMyOnboardSignDataRequests: () => {
      disabledDataRequestAttempts += 1
      return Promise.resolve({ data: [] })
    }
  })
  const disabledHarness = createContext(disabledComponent, {
    $route: { query: {}, path: "/mobile/sign-package" }
  })
  await disabledHarness.context.ensureSignCapabilities()
  assert.strictEqual(await disabledHarness.context.loadList(), true)
  assert.strictEqual(disabledPackageListAttempts, 1,
    "core package loading must continue when Excel capability is closed")
  assert.strictEqual(disabledDataRequestAttempts, 0,
    "Excel data-request endpoints must not be called when capability is closed")

  const disabledDeepLinkComponent = loadComponent({
    getSignTaskCapabilities: () => Promise.resolve({ data: { coreEnabled: true, excelImportEnabled: false } }),
    listMyOnboardSignDataRequests: () => {
      disabledDataRequestAttempts += 1
      return Promise.resolve({ data: [] })
    }
  })
  const disabledDeepLinkHarness = createContext(disabledDeepLinkComponent, {
    $route: { query: {}, path: "/mobile/sign-package" }
  })
  await disabledDeepLinkHarness.context.openDataRequestDeepLink("910")
  assert.ok(disabledDeepLinkHarness.context.excelExtensionError.includes("未启用"))
  assert.strictEqual(disabledDataRequestAttempts, 0,
    "disabled request deep links must fail closed without querying data-request/mine")

  let optionalFailureCalls = 0
  let corePackageDetailCalls = 0
  const optionalFailurePackage = packageFixture("pending_sign")
  optionalFailurePackage.packageId = "core-1"
  const optionalFailureComponent = loadComponent({
    listMySignPackages: () => Promise.resolve({ rows: [optionalFailurePackage] }),
    listMyOnboardSignDataRequests: () => {
      optionalFailureCalls += 1
      return Promise.reject({ response: { status: 503, data: { msg: "upstream stack detail" } } })
    },
    getMySignPackage: () => {
      corePackageDetailCalls += 1
      return Promise.resolve({ data: optionalFailurePackage })
    }
  })
  const optionalFailureHarness = createContext(optionalFailureComponent, {
    $route: { query: {}, path: "/mobile/sign-package" },
    applyPackageDetail(signPackage) {
      this.selectedPackage = signPackage
      return signPackage
    }
  })
  defineComputed(optionalFailureHarness.context, optionalFailureComponent, ["pageLevelError"])
  await optionalFailureHarness.context.ensureSignCapabilities()
  assert.strictEqual(await optionalFailureHarness.context.loadList(), true,
    "optional data-request failure must not fail the core package load")
  assert.strictEqual(optionalFailureHarness.context.loading, false,
    "core/optional combined loading must release after optional rejection")
  assert.strictEqual(optionalFailureHarness.context.list.length, 1)
  assert.ok(optionalFailureHarness.context.dataRequestListError)
  assert.ok(!optionalFailureHarness.context.dataRequestListError.includes("upstream stack detail"),
    "optional data-request failures must not expose raw technical details")
  assert.strictEqual(optionalFailureHarness.context.pageLevelError, null,
    "optional data-request failure must not become a page-blocking error")
  await optionalFailureHarness.context.openPackage(optionalFailurePackage)
  assert.strictEqual(corePackageDetailCalls, 1,
    "the core package must remain openable after the optional endpoint fails")
  assert.strictEqual(optionalFailureHarness.context.selectedPackage.packageId, "core-1")
  assert.strictEqual(optionalFailureCalls, 1)

  let capabilityFailureDataRequestCalls = 0
  const capabilityFailureComponent = loadComponent({
    getSignTaskCapabilities: () => Promise.reject(new Error("capability backend unavailable")),
    listMySignPackages: () => Promise.resolve({ rows: [optionalFailurePackage] }),
    listMyOnboardSignDataRequests: () => {
      capabilityFailureDataRequestCalls += 1
      return Promise.resolve({ data: [] })
    }
  })
  const capabilityFailureHarness = createContext(capabilityFailureComponent)
  await capabilityFailureHarness.context.ensureSignCapabilities()
  await capabilityFailureHarness.context.loadList()
  assert.strictEqual(capabilityFailureDataRequestCalls, 0,
    "capability query failure must keep optional data-request calls disabled")

  const stagedPackage = packageFixture("pending_sign")
  stagedPackage.packageId = "91"
  stagedPackage.signingSequence = "SIGNATURE_FIRST"
  const stagedRequest = {
    requestId: "910",
    packageId: "91",
    status: "PENDING_EMPLOYEE",
    signingSequence: "SIGNATURE_FIRST"
  }
  const stagedHarness = createContext(listComponent, {
    list: [stagedPackage],
    dataRequestList: [stagedRequest],
    selectedPackage: stagedPackage
  })
  defineComputed(stagedHarness.context, listComponent, [
    "legacyDataRequests",
    "packageDataRequestsById",
    "isSignatureFirstEmployeeStage",
    "showEmbeddedOnboardDataStage"
  ])
  stagedHarness.context.selectedPackageDataRequest = stagedHarness.context.packageDataRequest(stagedPackage)
  assert.strictEqual(stagedHarness.context.legacyDataRequests.length, 0,
    "a staged request linked by packageId must not remain in the legacy request-card list")
  assert.strictEqual(stagedHarness.context.selectedPackageDataRequest.requestId, "910")
  assert.strictEqual(stagedHarness.context.showEmbeddedOnboardDataStage, true,
    "the pending-sign real package must embed its employee fact/signature stage")

  let handoffPackageRefreshes = 0
  let handoffRequestRefreshes = 0
  const draftPackage = packageFixture("pending_company")
  draftPackage.packageId = "92"
  draftPackage.signingSequence = "SIGNATURE_FIRST"
  const pendingFinalPackage = packageFixture("pending_final_confirm")
  pendingFinalPackage.packageId = "92"
  pendingFinalPackage.signingSequence = "SIGNATURE_FIRST"
  const completedSignatureRequest = {
    requestId: "920",
    packageId: "92",
    status: "COMPLETED",
    signingSequence: "SIGNATURE_FIRST",
    factSnapshot: { employeeName: "段先生", employeePost: "行政经理" }
  }
  const handoffComponent = loadComponent({
    listMySignPackages: () => {
      handoffPackageRefreshes += 1
      return Promise.resolve({
        rows: [handoffPackageRefreshes === 1 ? draftPackage : pendingFinalPackage]
      })
    },
    listMyOnboardSignDataRequests: () => {
      handoffRequestRefreshes += 1
      return Promise.resolve({
        data: [completedSignatureRequest]
      })
    }
  })
  const handoffHarness = createContext(handoffComponent)
  defineComputed(handoffHarness.context, handoffComponent, [
    "headerSubtitle",
    "statusTabs",
    "sortedPackages",
    "filteredPackages",
    "legacyDataRequests",
    "packageDataRequestsById",
    "sortedDataRequests",
    "filteredDataRequests"
  ])

  assert.strictEqual(await handoffHarness.context.loadList(), true)
  handoffHarness.context.activeStatus = "progress"
  assert.strictEqual(handoffHarness.context.filteredPackages.length, 1,
    "the same real package must represent the waiting-company journey")
  assert.strictEqual(handoffHarness.context.filteredDataRequests.length, 0,
    "a package-bound data request must not render as a second card")
  assert.strictEqual(
    handoffHarness.context.filteredPackages.length + handoffHarness.context.filteredDataRequests.length,
    1,
    "the waiting-company phase must render exactly one unified My Signings work item"
  )
  assert.strictEqual(handoffHarness.context.dataRequestStatusLabel(completedSignatureRequest), "等待公司与印章")
  assert.ok(handoffHarness.context.dataRequestProgressSummary(completedSignatureRequest).includes("唯一一次签名已完成"))
  assert.strictEqual(handoffHarness.context.packageDataRequest(draftPackage).requestId, "920")
  assert.strictEqual(handoffHarness.context.headerSubtitle, "1 个签约包")

  assert.strictEqual(await handoffHarness.context.loadList(), true)
  handoffHarness.context.activeStatus = "todo"
  assert.strictEqual(handoffHarness.context.dataRequestList.length, 1,
    "the completed request may remain available as linked package evidence")
  assert.strictEqual(handoffHarness.context.filteredPackages.length, 1)
  assert.strictEqual(handoffHarness.context.filteredPackages[0].status, "pending_final_confirm")
  assert.strictEqual(
    handoffHarness.context.filteredPackages.length + handoffHarness.context.filteredDataRequests.length,
    1,
    "the pending-final phase must still render exactly one formal My Signings work item"
  )
  assert.strictEqual(handoffHarness.context.headerSubtitle, "1 个签约包")
  assert.strictEqual(handoffPackageRefreshes, 2)
  assert.strictEqual(handoffRequestRefreshes, 2)

  let detailAttempts = 0
  const detailPackage = packageFixture()
  const detailComponent = loadComponent({
    getMySignPackage: () => {
      detailAttempts += 1
      return detailAttempts === 1
        ? Promise.reject(new Error("详情网络异常"))
        : Promise.resolve({ data: detailPackage })
    }
  })
  const detailHarness = createContext(detailComponent, {
    applyPackageDetail(signPackage) {
      this.selectedPackage = signPackage
      return signPackage
    }
  })
  const detailItem = { packageId: detailPackage.packageId }
  assert.strictEqual(await detailHarness.context.openPackage(detailItem), null)
  assert.strictEqual(detailHarness.context.detailLoading, false, "detail loading must release after rejection")
  assert.ok(detailHarness.context.detailError.includes("网络异常"))
  assert.strictEqual(detailHarness.context.detailRetryItem, detailItem)
  const retriedDetail = await detailHarness.context.retryOpenPackage()
  assert.strictEqual(retriedDetail, detailPackage)
  assert.strictEqual(detailHarness.context.detailError, "")
  assert.strictEqual(detailHarness.context.detailRetryItem, null)

  let readAttempts = 0
  const readPayloads = []
  const readCurrent = packageFixture()
  const readSuccess = packageFixture()
  readSuccess.documents[0].readConfirmed = "Y"
  const readComponent = loadComponent({
    confirmSignPackageDocumentRead: (packageId, documentId, payload) => {
      readAttempts += 1
      readPayloads.push({ packageId, documentId, payload })
      return readAttempts === 1
        ? Promise.reject(new Error("阅读确认网络中断"))
        : Promise.resolve({ data: readSuccess })
    }
  })
  const readHarness = createContext(readComponent, {
    selectedPackage: readCurrent,
    activeDocument: readCurrent.documents[0],
    recheckTodoPersonalAction: () => Promise.resolve(readCurrent),
    showTodoPersonalHandled: () => Promise.resolve(),
    isLoadedDocument: () => true,
    loadActiveDocumentFiles: () => Promise.resolve([]),
    initSignatureCanvas() {}
  })
  defineComputed(readHarness.context, readComponent, ["documents"])
  assert.strictEqual(await readHarness.context.confirmRead(readCurrent.documents[0]), null)
  assert.strictEqual(readHarness.context.readConfirmingDocumentId, "", "read loading must release after rejection")
  assert.ok(readHarness.context.readActionError.includes("网络中断"))
  assert.strictEqual(readHarness.context.readRetryDocumentId, "doc-1")
  assert.ok(Object.isFrozen(readHarness.context.readFrozenMutation),
    "an uncertain read result must retain the complete mutation envelope")
  assert.ok(Object.isFrozen(readHarness.context.readFrozenMutation.payload))
  assert.strictEqual(await readHarness.context.confirmRead(readHarness.context.activeDocument).then(result => result.data), readSuccess)
  assert.strictEqual(readHarness.context.readActionError, "")
  assert.strictEqual(readHarness.context.readRetryDocumentId, "")
  assert.strictEqual(readAttempts, 2)
  assert.strictEqual(readPayloads[0].payload, readPayloads[1].payload,
    "read retries must reuse the exact frozen payload object")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(readPayloads[1])), {
    packageId: "pkg-1",
    documentId: "doc-1",
    payload: {
      requestId: "generated-request-id",
      expectedVersion: 7,
      documentVersion: "review-v1",
      reviewPdfHash: "review-hash-1"
    }
  })
  assert.strictEqual(readHarness.context.readFrozenMutation, null,
    "the read mutation envelope should clear only after a confirmed success")

  let signAttempts = 0
  const signPayloads = []
  const signCurrent = packageFixture()
  const signedResult = packageFixture("pending_company")
  const signComponent = loadComponent({
    signMySignPackage: (packageId, payload) => {
      signAttempts += 1
      signPayloads.push({ packageId, payload })
      return signAttempts === 1
        ? Promise.reject(new Error("签署响应中断"))
        : Promise.resolve({ data: signedResult })
    }
  })
  const signHarness = createContext(signComponent, {
    selectedPackage: signCurrent,
    activeDocument: signCurrent.documents[0],
    signConfirmText: "本人确认签署本签约包",
    signRequestId: "stable-sign-request",
    hasSignature: true,
    signDisabledReason: "",
    requiredDocuments: signCurrent.documents,
    allSigningFilesLoaded: true,
    recheckTodoPersonalAction: () => Promise.resolve(signCurrent),
    showTodoPersonalHandled: () => Promise.resolve(),
    loadActiveDocumentFiles: () => Promise.resolve([]),
    loadList: () => Promise.resolve(true),
    $refs: { signatureCanvas: { toDataURL: () => "data:image/png;base64,stable-signature" } }
  })
  defineComputed(signHarness.context, signComponent, ["documents"])
  assert.strictEqual(await signHarness.context.submitSign(), null)
  assert.strictEqual(signHarness.context.signing, false, "sign loading must release after rejection")
  assert.ok(signHarness.context.signError.includes("响应中断"))
  assert.strictEqual(signHarness.context.signRequestId, "stable-sign-request",
    "an uncertain sign result must retain the idempotency key")
  assert.strictEqual(signHarness.context.signConfirmText, "本人确认签署本签约包")
  assert.strictEqual(signHarness.context.hasSignature, true, "an uncertain sign result must retain the signature")
  assert.ok(Object.isFrozen(signHarness.context.signFrozenMutation))
  assert.ok(Object.isFrozen(signHarness.context.signFrozenMutation.payload))
  assert.ok(Object.isFrozen(signHarness.context.signFrozenMutation.payload.documentHashes))
  const frozenSignMutation = signHarness.context.signFrozenMutation
  signHarness.context.clearSignature()
  assert.strictEqual(signHarness.context.hasSignature, true,
    "the signature cannot be cleared while an uncertain mutation is frozen")

  signHarness.context.signConfirmText = "被篡改的确认语"
  signHarness.context.signRequestId = "mutated-sign-request"
  signHarness.context.hasSignature = false
  signHarness.context.requiredDocuments = [{
    documentId: "doc-mutated",
    employeeSignRequired: "Y",
    reviewPdfHash: "review-hash-mutated"
  }]
  signHarness.context.$refs.signatureCanvas.toDataURL = () => "data:image/png;base64,mutated-signature"
  const retriedSign = await signHarness.context.submitSign()
  assert.strictEqual(retriedSign.data, signedResult)
  assert.strictEqual(signPayloads[0].payload, signPayloads[1].payload,
    "a retry must send the exact frozen payload object")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(signPayloads[1].payload)), {
    documentVersion: "review-v1",
    documentHashes: [{ documentId: "doc-1", reviewPdfHash: "review-hash-1" }],
    signConfirmText: "本人确认签署本签约包",
    signatureDataUrl: "data:image/png;base64,stable-signature",
    requestId: "stable-sign-request"
  })
  assert.deepStrictEqual(signPayloads.map(item => item.packageId), ["pkg-1", "pkg-1"])
  assert.strictEqual(signHarness.context.signRequestId, "", "idempotency key should clear only after success")
  assert.strictEqual(signHarness.context.hasSignature, false)
  assert.strictEqual(signHarness.context.signFrozenMutation, null)

  const companyFirstPayloads = []
  const companyFirstCurrent = packageFixture()
  companyFirstCurrent.signingSequence = "COMPANY_FIRST"
  companyFirstCurrent.documents[0].finalReadConfirmed = "Y"
  const companyFirstResult = packageFixture("signed")
  companyFirstResult.signingSequence = "COMPANY_FIRST"
  const companyFirstComponent = loadComponent({
    signMySignPackage: (packageId, payload) => {
      companyFirstPayloads.push({ packageId, payload })
      return Promise.resolve({ data: companyFirstResult })
    }
  })
  const companyFirstHarness = createContext(companyFirstComponent, {
    selectedPackage: companyFirstCurrent,
    activeDocument: companyFirstCurrent.documents[0],
    signConfirmText: "本人确认签署本签约包",
    signRequestId: "company-first-sign-request",
    hasSignature: true,
    signDisabledReason: "",
    requiredDocuments: companyFirstCurrent.documents,
    recheckTodoPersonalAction: () => Promise.resolve(companyFirstCurrent),
    showTodoPersonalHandled: () => Promise.resolve(),
    loadActiveDocumentFiles: () => Promise.resolve([]),
    loadList: () => Promise.resolve(true),
    $refs: { signatureCanvas: { toDataURL: () => "data:image/png;base64,company-first-signature" } }
  })
  defineComputed(companyFirstHarness.context, companyFirstComponent, ["documents"])
  await companyFirstHarness.context.submitSign()
  assert.strictEqual(companyFirstPayloads.length, 1)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(companyFirstPayloads[0].payload)), {
    documentVersion: "review-v1",
    signConfirmText: "本人确认签署本签约包",
    signatureDataUrl: "data:image/png;base64,company-first-signature",
    requestId: "company-first-sign-request",
    finalDocumentVersion: "final-v1",
    finalDocumentRootHash: "root-v1",
    finalDocumentHashes: [{ documentId: "doc-1", finalPdfHash: "final-hash-1" }]
  }, "company-first signing must bind the exact pre-generated final version, root, and file hashes")
  assert.strictEqual(Object.prototype.hasOwnProperty.call(companyFirstPayloads[0].payload,
    "documentHashes"), false, "company-first must not bind unseen initial review files")

  const companyFirstCountHarness = createContext(companyFirstComponent, {
    selectedPackage: companyFirstCurrent
  })
  defineComputed(companyFirstCountHarness.context, companyFirstComponent, [
    "documents",
    "documentProgressSummary",
    "isCompanyFirstSequence",
    "requiredReadDocuments",
    "requiredDocumentCount",
    "requiredReadCount"
  ])
  assert.strictEqual(companyFirstCountHarness.context.requiredDocumentCount, 1,
    "company-first progress must count every employee-visible final document")
  assert.strictEqual(companyFirstCountHarness.context.requiredReadCount, 1,
    "company-first progress must use finalReadConfirmed after FINAL_DOCUMENT_OPENED")

  const signatureFirstCountPackage = packageFixture()
  const signatureFirstCountHarness = createContext(companyFirstComponent, {
    selectedPackage: signatureFirstCountPackage
  })
  defineComputed(signatureFirstCountHarness.context, companyFirstComponent, [
    "documents",
    "documentProgressSummary",
    "isCompanyFirstSequence",
    "requiredReadDocuments",
    "requiredDocumentCount",
    "requiredReadCount"
  ])
  assert.strictEqual(signatureFirstCountHarness.context.requiredDocumentCount, 1)
  assert.strictEqual(signatureFirstCountHarness.context.requiredReadCount, 0,
    "other signing sequences must keep the original readConfirmed progress semantics")

  const pendingFinalProgressPackage = packageFixture("pending_final_confirm")
  pendingFinalProgressPackage.signingSequence = "SIGNATURE_FIRST"
  pendingFinalProgressPackage.documents = Array.from({ length: 4 }, (_, index) => ({
    ...pendingFinalProgressPackage.documents[0],
    documentId: `pending-final-${index + 1}`,
    readConfirmed: "N",
    finalReadConfirmed: "Y"
  }))
  const pendingFinalProgressHarness = createContext(companyFirstComponent, {
    selectedPackage: pendingFinalProgressPackage
  })
  defineComputed(pendingFinalProgressHarness.context, companyFirstComponent, [
    "documents",
    "documentProgressSummary",
    "isCompanyFirstSequence",
    "requiredReadDocuments",
    "requiredDocumentCount",
    "requiredReadCount",
    "finalReadConfirmedCount",
    "finalArchiveAvailableCount",
    "fileListProgressCount",
    "fileListProgressTotal"
  ])
  assert.strictEqual(pendingFinalProgressHarness.context.requiredReadCount, 0,
    "final-document progress must not rewrite the original signature gate")
  assert.strictEqual(pendingFinalProgressHarness.context.fileListProgressCount, 4,
    "pending-final file-list progress must count persisted final-document opens")
  assert.strictEqual(pendingFinalProgressHarness.context.fileListProgressTotal, 4)

  const signedProgressPackage = JSON.parse(JSON.stringify(pendingFinalProgressPackage))
  signedProgressPackage.status = "signed"
  signedProgressPackage.documents.forEach((document, index) => {
    document.finalFileAvailable = index < 3
  })
  const signedProgressHarness = createContext(companyFirstComponent, {
    selectedPackage: signedProgressPackage
  })
  defineComputed(signedProgressHarness.context, companyFirstComponent, [
    "documents",
    "documentProgressSummary",
    "finalArchiveAvailableCount",
    "signedArchiveOrFileAvailableCount",
    "fileListProgressCount",
    "fileListProgressTotal"
  ])
  assert.strictEqual(signedProgressHarness.context.fileListProgressCount, 3,
    "signed file-list progress must reflect server-confirmed final archive availability")
  assert.strictEqual(signedProgressHarness.context.fileListProgressTotal, 4)

  const legacySignedPackage = JSON.parse(JSON.stringify(signedProgressPackage))
  legacySignedPackage.finalDocumentVersion = null
  legacySignedPackage.documents.forEach((document, index) => {
    document.finalFileAvailable = false
    document.signedFileAvailable = index < 3
  })
  const legacySignedHarness = createContext(companyFirstComponent, {
    selectedPackage: legacySignedPackage
  })
  defineComputed(legacySignedHarness.context, companyFirstComponent, [
    "documents",
    "documentProgressSummary",
    "finalArchiveAvailableCount",
    "signedFileAvailableCount",
    "signedArchiveOrFileAvailableCount",
    "fileListProgressCount",
    "fileListProgressTotal"
  ])
  assert.strictEqual(legacySignedHarness.context.fileListProgressCount, 3,
    "legacy signed packages must count the available signed files instead of final archives")
  assert.strictEqual(legacySignedHarness.context.fileListProgressTotal, 4)

  const migratedLegacySignedPackage = JSON.parse(JSON.stringify(legacySignedPackage))
  migratedLegacySignedPackage.finalDocumentVersion = "SP-HISTORICAL-V2"
  const migratedLegacySignedHarness = createContext(companyFirstComponent, {
    selectedPackage: migratedLegacySignedPackage
  })
  defineComputed(migratedLegacySignedHarness.context, companyFirstComponent, [
    "documents",
    "documentProgressSummary",
    "finalArchiveAvailableCount",
    "signedFileAvailableCount",
    "signedArchiveOrFileAvailableCount",
    "fileListProgressCount",
    "fileListProgressTotal"
  ])
  assert.strictEqual(migratedLegacySignedHarness.context.fileListProgressCount, 3,
    "migrated historical packages with a final version but no archive files must count old signed files")
  assert.strictEqual(migratedLegacySignedHarness.context.fileListProgressTotal, 4)

  const sampleTimeHarness = createContext(companyFirstComponent, {
    selectedPackage: {
      initialSignedTime: null,
      signatureSampleTime: "2026-07-21T10:41:13Z",
      signedTime: null
    }
  })
  defineComputed(sampleTimeHarness.context, companyFirstComponent, ["firstSignatureTime"])
  assert.strictEqual(sampleTimeHarness.context.firstSignatureTime, "2026-07-21T10:41:13Z",
    "signature-first onboarding must display the captured task signature time")

  let rawReviewDownloadCalls = 0
  const rawDownloadGateComponent = loadComponent({
    downloadMySignPackageDocument: () => {
      rawReviewDownloadCalls += 1
      return Promise.resolve(new Blob(["%PDF-1.7\n%%EOF\n"], { type: "application/pdf" }))
    }
  })
  const pendingFinalPreview = packageFixture("pending_final_confirm")
  const pendingFinalPreviewHarness = createContext(rawDownloadGateComponent, {
    selectedPackage: pendingFinalPreview,
    activeDocument: pendingFinalPreview.documents[0]
  })
  await pendingFinalPreviewHarness.context.loadActiveDocumentFiles()
  assert.strictEqual(rawReviewDownloadCalls, 0,
    "pending-final should fetch only authenticated watermarked PNG pages, never raw /file")

  const signedArchiveView = packageFixture("signed")
  signedArchiveView.documents[0].finalFileAvailable = true
  const signedArchiveViewHarness = createContext(rawDownloadGateComponent, {
    selectedPackage: signedArchiveView,
    activeDocument: signedArchiveView.documents[0]
  })
  await signedArchiveViewHarness.context.loadActiveDocumentFiles()
  assert.strictEqual(rawReviewDownloadCalls, 1,
    "raw initial-file evidence may be requested only after the package is signed")

  const signCallsBeforeHandledRefresh = signAttempts
  const handledSignHarness = createContext(signComponent, {
    selectedPackage: signCurrent,
    activeDocument: signCurrent.documents[0],
    signConfirmText: "本人确认签署本签约包",
    signRequestId: "stable-sign-request",
    signError: "结果未确认",
    signFrozenMutation: frozenSignMutation,
    hasSignature: true,
    signDisabledReason: "",
    recheckTodoPersonalAction: () => Promise.resolve(null),
    showTodoPersonalHandled: () => Promise.resolve(),
    loadList: () => Promise.resolve(true)
  })
  defineComputed(handledSignHarness.context, signComponent, ["documents"])
  assert.strictEqual(await handledSignHarness.context.submitSign() == null, true)
  assert.strictEqual(signAttempts, signCallsBeforeHandledRefresh,
    "a server-side status transition must not send the frozen mutation again")
  assert.strictEqual(handledSignHarness.context.signFrozenMutation, null)
  assert.strictEqual(handledSignHarness.context.signRequestId, "")
  assert.strictEqual(handledSignHarness.context.signConfirmText, "")
  assert.strictEqual(handledSignHarness.context.hasSignature, false)

  let finalAttempts = 0
  let secondSignatureAttempts = 0
  const finalPayloads = []
  const finalCurrent = packageFixture("pending_final_confirm")
  const finalResult = packageFixture("signed")
  const finalComponent = loadComponent({
    signMySignPackage: () => {
      secondSignatureAttempts += 1
      return Promise.resolve({ data: finalResult })
    },
    confirmMyFinalSignPackage: (packageId, payload) => {
      finalAttempts += 1
      finalPayloads.push({ packageId, payload })
      return finalAttempts === 1
        ? Promise.reject(new Error("最终确认响应中断"))
        : Promise.resolve({ data: finalResult })
    }
  })
  const finalHarness = createContext(finalComponent, {
    selectedPackage: finalCurrent,
    activeDocument: finalCurrent.documents[0],
    finalConfirmChecked: true,
    finalConfirmRequestId: "stable-final-request",
    finalConfirmDisabledReason: "",
    recheckTodoPersonalAction: () => Promise.resolve(finalCurrent),
    showTodoPersonalHandled: () => Promise.resolve(),
    loadActiveDocumentFiles: () => Promise.resolve([]),
    loadList: () => Promise.resolve(true)
  })
  defineComputed(finalHarness.context, finalComponent, ["documents"])
  assert.strictEqual(await finalHarness.context.submitFinalConfirm(), null)
  assert.strictEqual(finalHarness.context.finalConfirming, false, "final-confirm loading must release after rejection")
  assert.ok(finalHarness.context.finalConfirmError.includes("响应中断"))
  assert.strictEqual(finalHarness.context.finalConfirmRequestId, "stable-final-request")
  assert.strictEqual(finalHarness.context.finalConfirmChecked, true,
    "an uncertain final-confirm result must preserve the user's confirmation")
  assert.ok(Object.isFrozen(finalHarness.context.finalConfirmFrozenMutation.payload))
  finalHarness.context.selectDocument({ documentId: "doc-other" })
  assert.strictEqual(finalHarness.context.finalConfirmChecked, true,
    "switching documents cannot clear a frozen final confirmation")
  finalHarness.context.finalConfirmChecked = false
  finalHarness.context.finalConfirmRequestId = "mutated-final-request"
  finalCurrent.finalDocumentVersion = "final-v2"
  finalCurrent.finalDocumentRootHash = "root-v2"
  const retriedFinal = await finalHarness.context.submitFinalConfirm()
  assert.strictEqual(retriedFinal.data, finalResult)
  assert.strictEqual(finalPayloads[0].payload, finalPayloads[1].payload,
    "final-confirm retries must reuse the complete first payload")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(finalPayloads[1].payload)), {
    finalDocumentVersion: "final-v1",
    documentRootHash: "root-v1",
    confirmationText: "本人已阅读并确认最终合同中的公司及印章信息",
    requestId: "stable-final-request"
  })
  assert.strictEqual(Object.prototype.hasOwnProperty.call(finalPayloads[1].payload, "signatureDataUrl"), false,
    "signature-first final confirmation must not upload a second handwritten signature")
  assert.strictEqual(secondSignatureAttempts, 0,
    "signature-first final confirmation must not call the initial signing endpoint again")
  assert.strictEqual(finalHarness.context.finalConfirmRequestId, "")
  assert.strictEqual(finalHarness.context.finalConfirmChecked, false)
  assert.strictEqual(finalHarness.context.finalConfirmFrozenMutation, null)

  console.log("sign package request failure tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
