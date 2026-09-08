const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(root, relativePath), "utf8")
const LARGE_EMPLOYEE_ID = "9007199254740993"
const LARGE_TASK_ID = "9007199254740995"

const apiSource = read("src/api/oa/signTask.js")
const employeeListSource = read("src/views/hr/components/HrEmployeeList.vue")
const importDialogSource = read("src/views/hr/components/HrSignDataImportDialog.vue")
const mobileSource = read("src/views/mobile/onboardData/index.vue")
const mobileSignPackageSource = read("src/views/mobile/signPackage/index.vue")
const routesSource = read("src/views/mobile/mobileRouteDefinitions.js")
const resolverSource = read("src/utils/todoRouteResolver.js")
const mutationSource = read("src/utils/todoMutationMatcher.js")

function apiExportBlock(name) {
  const start = apiSource.indexOf(`export function ${name}`)
  assert.ok(start >= 0, `${name} must be exported`)
  const next = apiSource.indexOf("\nexport function ", start + 1)
  return apiSource.slice(start, next < 0 ? apiSource.length : next)
}

assert.ok(!fs.existsSync(path.join(root, "src/views/hr/components/HrOnboardContractBatchDialog.vue")),
  "the old onboarding-contract dialog must be deleted")
for (const removed of [
  "previewOnboardSignTaskBatch",
  "initiateOnboardSignTaskBatch",
  "/oa/signTask/onboard/batch/preview",
  "/oa/signTask/onboard/batch/initiate"
]) {
  assert.ok(!apiSource.includes(removed), `${removed} must not remain in the frontend API`)
  assert.ok(!employeeListSource.includes(removed), `${removed} must not remain in the employee archive entry`)
}

for (const [name, endpoint] of [
  ["previewOnboardSignImport", "/oa/signTask/onboard/import/preview"],
  ["getOnboardSignImportBatch", "/oa/signTask/onboard/import/"],
  ["updateOnboardSignImportRow", "/rows/"],
  ["sendOnboardSignDataRequests", "/data-request/send"],
  ["reviewOnboardSignDataRequest", "/review"],
  ["generateOnboardSignImport", "/generate"],
  ["listMyOnboardSignDataRequests", "/oa/signTask/onboard/data-request/mine"],
  ["getOnboardSignDataRequest", "/oa/signTask/onboard/data-request/"],
  ["submitOnboardSignDataRequest", "/submit"],
  ["sendSignTaskBatch", "/oa/signTask/batch/send"]
]) {
  assert.ok(apiSource.includes(`export function ${name}`), `${name} must be exported`)
  assert.ok(apiSource.includes(endpoint), `${name} must target ${endpoint}`)
}
assert.ok(apiSource.includes("const PDF_OPERATION_TIMEOUT = 300000"),
  "synchronous signing PDF generation should use the bounded five-minute timeout")
for (const name of [
  "revalidateSignTask",
  "retrySignTask",
  "generateOnboardSignImport",
  "finalizeSignTaskBatch",
  "executeOnboardSignCompanyWork"
]) {
  assert.ok(apiExportBlock(name).includes("timeout: PDF_OPERATION_TIMEOUT"),
    `${name} must not inherit the ordinary ten-second timeout while rendering PDFs`)
}
for (const name of [
  "sendOnboardSignDataRequests",
  "sendSignTaskBatch",
  "previewSignTaskBatchFinalize",
  "previewOnboardSignCompanyWork"
]) {
  assert.ok(!apiExportBlock(name).includes("timeout: PDF_OPERATION_TIMEOUT"),
    `${name} should keep the ordinary shared request timeout`)
}
assert.ok(apiSource.includes("new FormData()") && apiSource.includes("data.append('employeeIds', JSON.stringify"),
  "preview must use multipart and preserve employee ids as strings")
assert.ok(apiSource.includes("data.append('matchMode'") && apiSource.includes("EXCEL_PHONE_NAME") &&
  apiSource.includes("MANUAL_SELECTED"), "preview must send an explicit fail-closed employee match mode")

for (const marker of [
  "HrSignDataImportDialog",
  "VUE_APP_SIGN_EXCEL_IMPORT_ENABLED",
  "selectedSignDataEmployeeCount",
  "批量处理入职合同"
]) assert.ok(employeeListSource.includes(marker), `employee archive must contain ${marker}`)
assert.ok(employeeListSource.includes("this.selectedSignDataEmployeeCount >= 100"),
  "employee selection must fail closed above 100 employees")
assert.ok(!employeeListSource.includes("请先明确选择需要处理入职合同的员工"),
  "the Excel-only entry must remain available without preselecting employees")
assert.ok(!employeeListSource.includes("loadOnboardContractStates"),
  "the removed preview endpoint must not be emulated from the employee list")

for (const marker of [
  "上传签约数据",
  "核对套餐与缺失资料",
  "一键生成全部",
  "发送选中",
  "一键发送全部",
  "noExternalContractConfirmed",
  "historicalSupplementConfirmed",
  "warningReason",
  "Excel 自动匹配",
  "完整手机号＋姓名",
  "sendOnboardSignDataRequests",
  "reviewOnboardSignDataRequest",
  "sendSignTaskBatch",
  "COMPANY_FIRST",
  "SIGNATURE_FIRST",
  "先确认签约包并签一次，再选公司",
  "发送入职签约包",
  "发送最终文件",
  "dataRequestSignatureCaptured"
]) assert.ok(importDialogSource.includes(marker), `import dialog must contain ${marker}`)
for (const rehireMarker of ["EMPLOYEE_REHIRE_REQUIRED", "恢复原账号（再入职）", "rehireRequiredRows"]) {
  assert.ok(importDialogSource.includes(rehireMarker), `import dialog must explain rehire workflow: ${rehireMarker}`)
}
for (const editableField of [
  "contractTypeCode", "socialTypeCode", "contractTermCode", "employeePost", "jobGradeCode",
  "workLocation", "cityLevel", "contractStartDate", "contractEndDate", "probationStartDate",
  "probationEndDate", "salaryTotal", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary"
]) assert.ok(importDialogSource.includes(`rowForm.${editableField}`), `HR row editor must support ${editableField}`)
assert.ok(importDialogSource.includes("EMPLOYEE_DATA_REQUEST_FIELDS") && importDialogSource.includes("employeeDataRequestFields"),
  "employee data requests must use an explicit personal-fact allowlist")
assert.ok(importDialogSource.includes("rowOriginalForm") && importDialogSource.includes("buildRowPatch"),
  "row updates must be built from dirty fields instead of posting the whole preview form")
for (const existingTaskField of ["existingTaskId", "existingTaskStatus", "existingTaskSourceType"]) {
  assert.ok(importDialogSource.includes(existingTaskField),
    `preview rows must display and block on ${existingTaskField}`)
}
assert.ok(importDialogSource.includes("已有开放的入职签约任务") && importDialogSource.includes("不会重复新建"),
  "existing open tasks must be explained prominently instead of looking generatable")
assert.ok(importDialogSource.includes("signTaskStatusLabel") && importDialogSource.includes("signTaskReasonLabel"),
  "existing task status and source codes must use the shared Chinese dictionaries")
assert.ok(importDialogSource.includes("showLatestTaskFact"),
  "the current generated task must not be repeated as historical task context")
assert.ok(importDialogSource.includes("待确认签约包并签名"),
  "zero-missing signature-first requests must describe the employee package confirmation action")
assert.ok(importDialogSource.includes("本次不会自动发送"),
  "generation must explicitly remain separate from sending")
assert.ok(!importDialogSource.includes("sendSignPackage"),
  "generated task sending must reuse the existing task batch endpoint")
for (const recommendationField of [
  "recommendedCompany", "recommendedLegalRepresentative", "recommendedRegisteredAddress"
]) assert.ok(importDialogSource.includes(recommendationField),
  `Excel preview must display ${recommendationField} as a read-only recommendation`)
assert.ok(importDialogSource.includes("不会在此新建或修改公司档案"),
  "Excel company facts must be presented as recommendations rather than legal-entity mutations")
for (const missingTemplateCode of [
  "PLAN_MISSING_ONBOARD_COMMITMENT",
  "PLAN_MISSING_ONBOARD_CONFIDENTIAL_NONCOMPETE",
  "PLAN_MISSING_ONBOARD_MINOR_NONSTUDENT_DECLARATION"
]) assert.ok(importDialogSource.includes(missingTemplateCode),
  `missing package template ${missingTemplateCode} must have a readable preview message`)

for (const employeeField of [
  "currentAddress", "studentStatus", "schoolName", "retirementStatus", "incomeStartYearMonth"
]) assert.ok(mobileSource.includes(employeeField), `employee page must support ${employeeField}`)
for (const signatureFirstMarker of [
  "factSnapshot", "plannedDocumentNames", "signatureConfirmationPrompt", "factConfirmationText",
  "signatureRequestId", "signatureDataUrl", "requiresSignatureCapture",
  "本签约包唯一一次手写签名", "不会保存到员工全局档案",
  "本次只需更正事实并重新提交", "无需再次签名"
]) assert.ok(mobileSource.includes(signatureFirstMarker), `signature-first employee page must contain ${signatureFirstMarker}`)
assert.ok(!mobileSource.includes("localStorage") && !mobileSource.includes("sessionStorage"),
  "task-scoped signature data must not be persisted in browser profile storage")
assert.ok(mobileSource.includes("@lostpointercapture=\"finishDraw\"") &&
  !mobileSource.includes("@touchstart") && !mobileSource.includes("@mousedown"),
"signature canvas must use one pointer-event stream with pointer capture")
assert.ok(mobileSource.includes("class=\"confirmation-copy\""),
  "the exact signature confirmation phrase must remain visible while typing")
for (const forbiddenPayloadField of ["laborServicePersonType:", "commercialInsuranceType:", "contractType:", "salary:", "legalEntityId:"]) {
  assert.ok(!mobileSource.includes(forbiddenPayloadField), `employee payload must not expose ${forbiddenPayloadField}`)
}
assert.ok(mobileSource.includes("资料已提交，请等待 HR 审核"))
assert.ok(routesSource.includes("/mobile/onboard-data"))
assert.ok(routesSource.includes("redirect: route => ({ path: '/mobile/sign-package', query: route.query })"),
  "legacy onboard-data links must preserve their query while entering My Signings")
assert.ok(resolverSource.includes("OA_SIGN_ONBOARD_DATA_REQUEST") && resolverSource.includes("personal(\"/mobile/sign-package\")"),
  "onboard employee todos must open inside My Signings")
assert.ok(mobileSource.includes("embedded") && mobileSource.includes("requestId") &&
  mobileSource.includes('this.$emit("back")') && mobileSource.includes('this.$emit("updated", detail)'),
"the employee fact/signature flow must be reusable as an embedded My Signings detail")
assert.ok(mobileSource.includes(":is=\"embedded ? 'div' : 'main'\"") &&
  mobileSource.includes("本签约包唯一一次签名已完成") && mobileSource.includes("等待 HR 选择公司与印章"),
"embedded onboarding detail should avoid nested main landmarks and explain the post-signature wait state")
for (const marker of [
  "MobileOnboardDataRequest", "listMyOnboardSignDataRequests", "selectedDataRequest",
  "filteredDataRequests", "openDataRequestDeepLink", "packageDataRequestsById",
  "showEmbeddedOnboardDataStage", "唯一一次签名已完成", "等待公司与印章"
]) assert.ok(mobileSignPackageSource.includes(marker), `My Signings must integrate ${marker}`)
assert.ok(mobileSignPackageSource.includes("visiblePackageCount") &&
  mobileSignPackageSource.includes("this.statusTabValue(item.status)"),
"a draft package waiting to take over must not make one signing journey count as two tasks")
assert.ok(mutationSource.includes("data-request\\/\\d+\\/(?:review|submit)"),
  "data request mutations must invalidate unified todo summaries")

function loadMobileDefinitions(enabled) {
  const modulePath = require.resolve("../src/views/mobile/mobileRouteDefinitions")
  const previous = process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED
  process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED = enabled ? "true" : "false"
  delete require.cache[modulePath]
  const loaded = require(modulePath)
  if (previous === undefined) delete process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED
  else process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED = previous
  delete require.cache[modulePath]
  return loaded
}

function mineOnboardDataEntry(definitions) {
  const mine = definitions.mobileRouteDefinitions.find(route => route.path === "/mobile/mine")
  const actions = mine && mine.meta && mine.meta.mobileFeature && mine.meta.mobileFeature.actions
  return (actions || []).find(action => action.path === "/mobile/onboard-data")
}

const disabledMobileDefinitions = loadMobileDefinitions(false)
const enabledMobileDefinitions = loadMobileDefinitions(true)
assert.strictEqual(disabledMobileDefinitions.SIGN_EXCEL_IMPORT_ENABLED, false)
assert.strictEqual(mineOnboardDataEntry(disabledMobileDefinitions), undefined,
  "mobile mine must hide the onboarding-data entry while the Excel signing flag is off")
assert.strictEqual(mineOnboardDataEntry(enabledMobileDefinitions), undefined,
  "mobile mine must not expose a second onboarding-data entry while the Excel signing flag is on")
assert.ok(disabledMobileDefinitions.mobileRouteDefinitions.some(route => route.path === "/mobile/onboard-data"),
  "the hidden route must remain registered so existing employee tasks can still be opened safely")

function loadApi(request, FormDataImpl) {
  const transformed = apiSource
    .replace(/import\s+request\s+from\s+["'][^"']+["']\s*/, "")
    .replace(/export function /g, "function ")
  const names = [
    "previewOnboardSignImport", "getOnboardSignImportBatch", "updateOnboardSignImportRow",
    "sendOnboardSignDataRequests", "reviewOnboardSignDataRequest", "generateOnboardSignImport",
    "listMyOnboardSignDataRequests", "getOnboardSignDataRequest", "submitOnboardSignDataRequest", "sendSignTaskBatch"
  ]
  const sandbox = { module: { exports: {} }, exports: {}, request, FormData: FormDataImpl }
  vm.runInNewContext(`${transformed}\nmodule.exports = { ${names.join(", ")} }`, sandbox, { filename: "src/api/oa/signTask.js" })
  return sandbox.module.exports
}

class FakeFormData {
  constructor() { this.entries = [] }
  append(key, value) { this.entries.push([key, value]) }
}

async function testApiKeepsLargeIdsAndPayloads() {
  const configs = []
  const api = loadApi(config => {
    configs.push(config)
    return Promise.resolve(config)
  }, FakeFormData)
  const file = { name: "签约数据.xlsx" }
  await api.previewOnboardSignImport(file, [LARGE_EMPLOYEE_ID, "12"])
  assert.strictEqual(configs[0].data.entries[0][0], "file")
  assert.deepStrictEqual(JSON.parse(configs[0].data.entries.find(([key]) => key === "employeeIds")[1]),
    [LARGE_EMPLOYEE_ID, "12"])
  assert.strictEqual(configs[0].data.entries.find(([key]) => key === "matchMode")[1], "MANUAL_SELECTED")
  await api.previewOnboardSignImport(file, [])
  assert.deepStrictEqual(JSON.parse(configs[1].data.entries.find(([key]) => key === "employeeIds")[1]), [])
  assert.strictEqual(configs[1].data.entries.find(([key]) => key === "matchMode")[1], "EXCEL_PHONE_NAME")
  const sendPayload = { requestId: "send-1", taskIds: [LARGE_TASK_ID] }
  await api.sendSignTaskBatch(sendPayload)
  assert.strictEqual(configs[2].data, sendPayload)
}

function loadSfcComponent(source, globals, filename) {
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, `${filename} must contain a script block`)
  const transformed = script[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = {
    module: { exports: {} }, exports: {}, process: { env: { VUE_APP_SIGN_EXCEL_IMPORT_ENABLED: "true" } },
    setTimeout, clearTimeout, crypto: { randomUUID: () => "uuid" },
    require(request) {
      if (request === "../mobileErrorMessage") return require("../src/views/mobile/mobileErrorMessage")
      throw new Error(`unexpected dependency: ${request}`)
    },
    ...globals
  }
  vm.runInNewContext(transformed, sandbox, { filename })
  return sandbox.module.exports
}

function instantiate(component, props = {}) {
  const target = { ...props }
  Object.entries(component.methods || {}).forEach(([name, method]) => { target[name] = method.bind(target) })
  Object.assign(target, component.data.call(target))
  Object.entries(component.computed || {}).forEach(([name, computed]) => {
    const getter = typeof computed === "function" ? computed : computed.get
    const setter = typeof computed === "object" ? computed.set : undefined
    Object.defineProperty(target, name, { configurable: true, get: getter && getter.bind(target), set: setter && setter.bind(target) })
  })
  return target
}

async function testScopeAndFileChangesInvalidatePreview() {
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: () => Promise.resolve({}),
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const warnings = []
  const target = instantiate(component, {
    visible: true,
    employees: [{ employeeId: LARGE_EMPLOYEE_ID }],
    $modal: { msgWarning: message => warnings.push(message), msgSuccess() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {},
    $router: { resolve: () => ({ href: "#" }) }
  })
  assert.deepStrictEqual(Array.from(target.employeeIds), [LARGE_EMPLOYEE_ID])
  target.batch = { batchId: "88" }
  target.rows = [{ rowId: "1" }]
  target.handleSignScopeChange({ deptId: "200" })
  assert.strictEqual(target.batch, null)
  assert.strictEqual(target.rows.length, 0)
  assert.ok(target.operationError.includes("重新预览"))

  target.batch = { batchId: "99" }
  target.rows = [{ rowId: "2" }]
  target.handleFileChange({ name: "new.xlsx", raw: { name: "new.xlsx", size: 100 } })
  assert.strictEqual(target.selectedFile.name, "new.xlsx")
  assert.strictEqual(target.batch, null)
  assert.ok(target.operationError.includes("文件已变更"))
}

async function testExcelOnlyPreviewWorksWithoutSelectedEmployees() {
  const calls = []
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    previewOnboardSignImport: (file, employeeIds) => {
      calls.push({ file, employeeIds: Array.from(employeeIds) })
      return Promise.resolve({ data: { batchId: "81", rows: [] } })
    },
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: () => Promise.resolve({}),
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, {
    visible: true,
    employees: [],
    $modal: { msgWarning() {}, msgSuccess() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {},
    $router: { resolve: () => ({ href: "#" }) }
  })
  target.signScopeReady = true
  target.selectedFile = { name: "签约数据.xlsx", size: 100 }
  assert.strictEqual(target.canPreview, true)
  await target.previewImport()
  assert.strictEqual(calls.length, 1)
  assert.deepStrictEqual(calls[0].employeeIds, [])
  target.rows = [{ employeeId: "1106", matchType: "PHONE_AND_NAME", missingFields: ["noExternalContractConfirmation"] }]
  assert.strictEqual(target.summaryCount("matched"), 1)
  assert.strictEqual(target.summaryCount("missing"), 0,
    "the mandatory HR confirmation must not be reported as missing employee data")
}

async function testSingleAndBatchSendUseTaskIdsAsStrings() {
  const calls = []
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: () => Promise.resolve({}),
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: payload => {
      calls.push(payload)
      return Promise.resolve({ data: { rows: [
        { taskId: payload.taskIds[0], success: true, result: "SENT" },
        ...(payload.taskIds[1] ? [{ taskId: payload.taskIds[1], success: false, result: "FAILED" }] : [])
      ] } })
    }
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, {
    visible: true,
    employees: [],
    $modal: { msgWarning() {}, msgSuccess() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {},
    $router: { resolve: () => ({ href: "#" }) }
  })
  target.batch = { batchId: "77" }
  target.refreshBatch = () => Promise.resolve([])
  const first = { rowId: "1", taskId: LARGE_TASK_ID, status: "READY_TO_SEND" }
  const second = { rowId: "2", taskId: "9007199254740997", status: "READY_TO_SEND" }
  await target.sendRows([first])
  assert.deepStrictEqual(Array.from(calls[0].taskIds), [LARGE_TASK_ID])
  await target.sendRows([first, second])
  assert.deepStrictEqual(Array.from(calls[1].taskIds), [LARGE_TASK_ID, "9007199254740997"])
  assert.strictEqual(target.sendResults.length, 2)
  assert.strictEqual(target.sendResults[1].result, "FAILED", "partial failures must remain visible")
}

async function testEmployeeDataRequestUsesOnlyEmployeeFacts() {
  const calls = []
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: (batchId, payload) => {
      calls.push({ batchId, payload })
      return Promise.resolve({})
    },
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, {
    visible: true,
    employees: [],
    $modal: { msgWarning() {}, msgSuccess() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {},
    $router: { resolve: () => ({ href: "#" }) }
  })
  target.batch = { batchId: "77" }
  target.refreshBatch = () => Promise.resolve([])
  const hrOnly = { rowId: "1", employeeId: "1105", status: "NEEDS_HR_DATA", missingFields: ["servicePersonType", "insuranceType"] }
  const mixed = {
    rowId: "2", employeeId: "1106",
    status: "NEEDS_HR_DATA",
    missingFields: ["servicePersonType", { field: "currentAddress" }, "noExternalContractConfirmation"]
  }
  assert.strictEqual(target.canRequestEmployeeData(hrOnly), false,
    "HR-only service/insurance facts must never create an empty employee task")
  assert.deepStrictEqual(Array.from(target.employeeDataRequestFields(mixed)), ["currentAddress"])
  await target.sendDataRequests([hrOnly])
  assert.strictEqual(calls.length, 0)
  await target.sendDataRequests([hrOnly, mixed])
  assert.strictEqual(calls.length, 1)
  assert.deepStrictEqual(Array.from(calls[0].payload.rowIds), ["2"])
}

async function testSignatureFirstRequestAndGenerationGate() {
  const calls = []
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: (batchId, payload) => {
      calls.push({ batchId, payload: JSON.parse(JSON.stringify(payload)) })
      return Promise.resolve({})
    },
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, {
    visible: true,
    employees: [],
    $modal: { msgWarning() {}, msgSuccess() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {},
    $router: { resolve: () => ({ href: "#" }) }
  })
  target.batch = { batchId: "77" }
  target.signingSequence = "SIGNATURE_FIRST"
  target.refreshBatch = () => Promise.resolve([])
  const unsignedReady = {
    rowId: "1", employeeId: "1106", status: "READY_TO_GENERATE", planVersionId: "10",
    missingFields: [], warningCodes: [], signatureRequestable: true
  }
  assert.strictEqual(target.canRequestEmployeeData(unsignedReady), true,
    "signature-first must allow a fact/signature task even when no personal field is missing")
  assert.strictEqual(target.isReadyToGenerate(unsignedReady), false,
    "signature-first selection must block generation until the task signature is captured")
  await target.sendDataRequests([unsignedReady])
  assert.strictEqual(calls.length, 1)
  assert.deepStrictEqual(calls[0].payload.rowIds, ["1"])
  assert.strictEqual(calls[0].payload.signingSequence, "SIGNATURE_FIRST")

  const companyConflict = {
    rowId: "2", employeeId: "1107", status: "CONFLICT", planVersionId: "10",
    errorCodes: ["COMPANY_MASTER_DATA_INCOMPLETE", "COMPANY_SEAL_REQUIRES_HR"],
    missingFields: [], warningCodes: [], signatureRequestable: true
  }
  assert.strictEqual(target.rowSelectable(companyConflict), true,
    "a server-approved company conflict must remain selectable for signature-first")
  assert.strictEqual(target.canRequestEmployeeData(companyConflict), true,
    "company and seal finalization must not block the employee fact/signature request")
  assert.strictEqual(target.isReadyToGenerate(companyConflict), false,
    "company and seal conflicts must continue to block formal document generation")
  await target.sendDataRequests([companyConflict])
  assert.strictEqual(calls.length, 2)
  assert.deepStrictEqual(calls[1].payload.rowIds, ["2"])

  const identityConflict = Object.assign({}, companyConflict, {
    rowId: "3",
    errorCodes: ["EMPLOYEE_IDENTITY_MISMATCH"],
    signatureRequestable: false
  })
  assert.strictEqual(target.rowSelectable(identityConflict), false)
  assert.strictEqual(target.canRequestEmployeeData(identityConflict), false,
    "identity or duplicate conflicts must remain blocked")

  const signedReady = Object.assign({}, unsignedReady, {
    dataRequestId: "91",
    dataRequestSigningSequence: "SIGNATURE_FIRST",
    dataRequestSignatureCaptured: true
  })
  assert.strictEqual(target.canRequestEmployeeData(signedReady), false)
  assert.strictEqual(target.isReadyToGenerate(signedReady), true,
    "a server-confirmed task signature must unlock signature-first generation")
}

async function testEmployeeSignatureFirstSubmissionIsTaskScoped() {
  const submissions = []
  const component = loadSfcComponent(mobileSource, {
    getOnboardSignDataRequest: () => Promise.resolve({}),
    listMyOnboardSignDataRequests: () => Promise.resolve({ data: [] }),
    submitOnboardSignDataRequest: (requestId, payload) => {
      submissions.push({ requestId, payload: JSON.parse(JSON.stringify(payload)) })
      return Promise.resolve({})
    }
  }, "src/views/mobile/onboardData/index.vue")
  const target = instantiate(component, {
    $route: { query: {}, params: {} },
    $router: { push: () => Promise.resolve() },
    $modal: { msgSuccess() {}, msgWarning() {} },
    $refs: { signatureCanvas: { toDataURL: () => "data:image/png;base64,task-signature" } },
    $nextTick: callback => callback(),
    $set(object, key, value) { object[key] = value }
  })
  target.selectedRequest = {
    requestId: "123",
    version: 5,
    status: "PENDING_EMPLOYEE",
    signingSequence: "SIGNATURE_FIRST",
    signatureConfirmationPrompt: "本人已核对签约事实并留存本任务手写签名",
    factSnapshot: { employeeName: "段先生", employeePost: "行政经理", salaryTotal: "9000" },
    plannedDocumentNames: ["劳动合同", "薪酬结构确认书（B版）"],
    allowedFields: []
  }
  target.formValues = {}
  target.factConfirmationText = target.signatureConfirmationPrompt
  target.hasSignature = true
  target.loadDetail = () => Promise.resolve(target.selectedRequest)
  await target.submit()
  assert.strictEqual(submissions.length, 1)
  assert.strictEqual(submissions[0].requestId, "123")
  assert.strictEqual(submissions[0].payload.version, 5)
  assert.strictEqual(submissions[0].payload.factConfirmationText,
    "本人已核对签约事实并留存本任务手写签名")
  assert.ok(/^onboard-sign-/.test(submissions[0].payload.signatureRequestId))
  assert.strictEqual(submissions[0].payload.signatureDataUrl, "data:image/png;base64,task-signature")
}

async function testRejectedSignatureFirstCorrectionNeverUploadsAnotherSignature() {
  const submissions = []
  const component = loadSfcComponent(mobileSource, {
    getOnboardSignDataRequest: () => Promise.resolve({}),
    listMyOnboardSignDataRequests: () => Promise.resolve({ data: [] }),
    submitOnboardSignDataRequest: (requestId, payload) => {
      submissions.push({ requestId, payload: JSON.parse(JSON.stringify(payload)) })
      return Promise.resolve({})
    }
  }, "src/views/mobile/onboardData/index.vue")
  const target = instantiate(component, {
    $route: { query: {}, params: {} },
    $router: { push: () => Promise.resolve() },
    $modal: { msgSuccess() {}, msgWarning() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $set(object, key, value) { object[key] = value }
  })
  target.selectedRequest = {
    requestId: "124",
    version: 7,
    status: "REJECTED",
    signingSequence: "SIGNATURE_FIRST",
    signatureCaptured: true,
    signatureSampleTime: "2026-07-21 14:00:00",
    signatureConfirmationPrompt: "不应再提交的签名确认语",
    factSnapshot: { employeeName: "段先生", employeePost: "行政经理" },
    plannedDocumentNames: ["劳动合同"],
    allowedFields: ["currentAddress"]
  }
  target.formValues = { currentAddress: "浙江省舟山市新城路2号" }
  target.loadDetail = () => Promise.resolve(target.selectedRequest)

  assert.strictEqual(target.requiresSignatureCapture, false)
  assert.strictEqual(target.canSubmitCurrentRequest, true)
  assert.strictEqual(target.validate(), "")
  await target.submit()

  assert.strictEqual(submissions.length, 1)
  assert.deepStrictEqual(submissions[0], {
    requestId: "124",
    payload: { version: 7, currentAddress: "浙江省舟山市新城路2号" }
  })
  for (const forbidden of ["factConfirmationText", "signatureRequestId", "signatureDataUrl"]) {
    assert.strictEqual(Object.prototype.hasOwnProperty.call(submissions[0].payload, forbidden), false,
      `a rejected correction must not send ${forbidden}`)
  }
}

function testEmployeeConditionalFieldsAndSignatureThreshold() {
  const component = loadSfcComponent(mobileSource, {
    getOnboardSignDataRequest: () => Promise.resolve({}),
    listMyOnboardSignDataRequests: () => Promise.resolve({ data: [] }),
    submitOnboardSignDataRequest: () => Promise.resolve({})
  }, "src/views/mobile/onboardData/index.vue")
  const context = {
    beginPath() {}, moveTo() {}, lineTo() {}, stroke() {}, arc() {}, fill() {},
    setTransform() {}, fillRect() {}
  }
  let captured = null
  const canvas = {
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 300, height: 180 }),
    getContext: () => context,
    setPointerCapture: pointerId => { captured = pointerId },
    hasPointerCapture: pointerId => captured === pointerId,
    releasePointerCapture: pointerId => { if (captured === pointerId) captured = null }
  }
  const target = instantiate(component, {
    $route: { query: {}, params: {} },
    $router: { push: () => Promise.resolve() },
    $modal: { msgSuccess() {}, msgWarning() {} },
    $refs: { signatureCanvas: canvas },
    $nextTick: callback => callback(),
    $set(object, key, value) { object[key] = value }
  })
  target.selectedRequest = {
    requestId: "123", status: "PENDING_EMPLOYEE", signingSequence: "SIGNATURE_FIRST",
    allowedFields: ["studentStatus", "schoolName", "incomeStartYearMonth"]
  }
  target.formValues = { studentStatus: "STUDENT", schoolName: "", incomeStartYearMonth: "" }
  assert.deepStrictEqual(Array.from(target.editableFieldDefinitions, field => field.key),
    ["studentStatus", "schoolName"])
  assert.strictEqual(target.editableFieldDefinitions.find(field => field.key === "schoolName").required, true)
  target.formValues.studentStatus = "NON_STUDENT"
  assert.deepStrictEqual(Array.from(target.editableFieldDefinitions, field => field.key),
    ["studentStatus", "incomeStartYearMonth"])

  const pointer = (x, y) => ({ pointerId: 7, pointerType: "pen", button: 0, clientX: x, clientY: y })
  target.startDraw(pointer(10, 10))
  target.finishDraw(pointer(10, 10))
  assert.strictEqual(target.hasSignature, false, "a single tap must not count as a handwritten signature")
  target.startDraw(pointer(10, 10))
  target.draw(pointer(20, 10))
  target.draw(pointer(30, 10))
  target.draw(pointer(40, 10))
  target.draw(pointer(50, 10))
  assert.strictEqual(captured, 7)
  target.finishDraw(pointer(50, 10))
  assert.strictEqual(captured, null)
  assert.strictEqual(target.hasSignature, true, "a sufficiently long multi-point stroke must count as a signature")
}

async function testDirtyRowPatchProtectsMaskedAddress() {
  const updates = []
  const warnings = []
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: (batchId, rowId, payload) => {
      updates.push({ batchId, rowId, payload: JSON.parse(JSON.stringify(payload)) })
      return Promise.resolve({})
    },
    sendOnboardSignDataRequests: () => Promise.resolve({}),
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, {
    visible: true,
    employees: [],
    $modal: { msgWarning: message => warnings.push(message), msgSuccess() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {},
    $router: { resolve: () => ({ href: "#" }) }
  })
  target.batch = { batchId: "77" }
  target.refreshBatch = () => Promise.resolve([])
  const row = {
    rowId: "9",
    version: 4,
    currentAddress: "北京市朝阳***",
    contractTypeCode: "LABOR_CONTRACT",
    socialTypeCode: "SOCIAL_INSURED",
    jobGradeCode: "7"
  }
  target.openRowEditor(row)
  assert.strictEqual(target.rowForm.currentAddress, "",
    "masked address previews must never become editable payload defaults")
  target.rowForm.employeePost = "区域运营总监"
  await target.saveRow()
  assert.deepStrictEqual(updates[0], {
    batchId: "77",
    rowId: "9",
    payload: { version: 4, employeePost: "区域运营总监" }
  })
  assert.strictEqual(Object.prototype.hasOwnProperty.call(updates[0].payload, "currentAddress"), false)

  target.openRowEditor(row)
  target.rowForm.currentAddress = "北京市朝阳***"
  await target.saveRow()
  assert.strictEqual(updates.length, 1, "masked input must be rejected before the API call")
  assert.ok(warnings.some(message => message.includes("脱敏预览值")))
}

async function testGenerationConfirmationsUseOnlyTargetRows() {
  const generated = []
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: () => Promise.resolve({}),
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: (batchId, payload) => {
      generated.push({ batchId, payload: JSON.parse(JSON.stringify(payload)) })
      return Promise.resolve({})
    },
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, {
    visible: true,
    employees: [],
    $modal: { msgWarning() {}, msgSuccess() {} },
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {},
    $router: { resolve: () => ({ href: "#" }) }
  })
  target.batch = { batchId: "77", version: 8 }
  target.refreshBatch = () => Promise.resolve([])
  const clean = { rowId: "1", status: "READY_TO_GENERATE", planVersionId: "10", missingFields: [], warningCodes: [] }
  const historical = {
    rowId: "2",
    status: "NEEDS_HR_DATA",
    planVersionId: "10",
    missingFields: ["noExternalContractConfirmation", "historicalSupplementReason"],
    warningCodes: []
  }
  const warning = {
    rowId: "3",
    status: "NEEDS_HR_DATA",
    planVersionId: "10",
    missingFields: ["noExternalContractConfirmation", "warningConfirmationReason"],
    warningCodes: ["SALARY_OUTSIDE_REFERENCE_RANGE"],
    warningConfirmed: false
  }
  const existingOpenTask = {
    rowId: "4",
    status: "READY_TO_GENERATE",
    planVersionId: "10",
    missingFields: [],
    warningCodes: [],
    existingTaskId: LARGE_TASK_ID,
    existingTaskStatus: "PENDING_SIGN",
    existingTaskSourceType: "MANUAL_SIGN_EXCEL_IMPORT"
  }
  assert.strictEqual(target.isReadyToGenerate(existingOpenTask), false,
    "an existing open onboarding task must block duplicate generation in the UI")
  target.rows = [clean, historical, warning]
  target.selectedRows = [clean]
  target.confirmation.noExternalContractConfirmed = true
  assert.strictEqual(target.canGenerateSelected, true,
    "an unrelated historical/warning row must not block selected clean rows")
  assert.strictEqual(target.canGenerateAll, false,
    "generate-all must still gate on every ready target row")
  await target.generateRows([clean])
  assert.deepStrictEqual(generated[0].payload.rowIds, ["1"])
  assert.strictEqual(generated[0].payload.historicalReason, "")
  assert.strictEqual(generated[0].payload.warningReason, "")

  target.confirmation.historicalSupplementConfirmed = true
  target.confirmation.historicalSupplementReason = "补签往期入职合同"
  target.confirmation.warningReason = "HR 已核对岗位薪资区间"
  assert.strictEqual(target.canGenerateAll, true)
  await target.generateRows(target.readyRows)
  assert.deepStrictEqual(generated[1].payload.rowIds, ["1", "2", "3"])
  assert.strictEqual(generated[1].payload.historicalReason, "补签往期入职合同")
  assert.strictEqual(generated[1].payload.warningReason, "HR 已核对岗位薪资区间")
}

async function testBatchRefreshKeepsSelectedRowsInSync() {
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    signTaskStatusLabel: value => value,
    signTaskReasonLabel: value => value,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: () => Promise.resolve({}),
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, {
    visible: true,
    employees: [],
    $refs: {},
    $nextTick: callback => callback(),
    $emit() {}
  })
  const response = suffix => ({
    batch: { batchId: "77", version: 8 },
    rows: [
      { rowId: "1", status: "READY_TO_GENERATE", planVersionId: "10", missingFields: [], warningCodes: [], suffix },
      { rowId: "2", status: "READY_TO_GENERATE", planVersionId: "11", missingFields: [], warningCodes: [], suffix },
      { rowId: "3", status: "CONFLICT", planVersionId: "", errorCodes: ["PLAN_NOT_FOUND"], missingFields: [], suffix }
    ]
  })

  target.applyBatchResponse(response("initial"))
  assert.deepStrictEqual(target.selectedRows.map(row => row.rowId), ["1", "2"],
    "the first preview should select every ready row in both the table model and computed state")

  target.selectedRows = [target.rows[0]]
  target.applyBatchResponse(response("refreshed"))
  assert.deepStrictEqual(target.selectedRows.map(row => row.rowId), ["1"],
    "refresh must preserve only the rows the user kept selected")
  assert.strictEqual(target.selectedRows[0], target.rows[0],
    "refresh must replace stale selected row objects with the current normalized row")
  assert.strictEqual(target.selectedReadyRows.length, 1,
    "the selected generation count must match the visible current-row selection")

  target.selectedRows = []
  target.applyBatchResponse(response("deselected"))
  assert.deepStrictEqual(target.selectedRows, [],
    "refresh must preserve an intentional empty selection instead of selecting all ready rows again")
}

async function testImportDialogDoesNotExposeBackendCodes() {
  const statusLabels = { PENDING_SIGN: "待员工签署", READY_TO_SEND: "待发送" }
  const reasonLabels = { MANUAL_SIGN_EXCEL_IMPORT: "签约名单导入" }
  const component = loadSfcComponent(importDialogSource, {
    SignScopeSelector: {},
    getSelectedSignScopeDeptId: () => "1171",
    profileFieldLabel: value => value,
    signTaskStatusLabel: (value, fallback) => statusLabels[value] || fallback,
    signTaskReasonLabel: (value, fallback) => reasonLabels[value] || fallback,
    previewOnboardSignImport: () => Promise.resolve({}),
    getOnboardSignImportBatch: () => Promise.resolve({}),
    updateOnboardSignImportRow: () => Promise.resolve({}),
    sendOnboardSignDataRequests: () => Promise.resolve({}),
    reviewOnboardSignDataRequest: () => Promise.resolve({}),
    archiveOnboardSignSalary: () => Promise.resolve({ data: { updated: 1, reused: 0 } }),
    generateOnboardSignImport: () => Promise.resolve({}),
    sendSignTaskBatch: () => Promise.resolve({ data: [] })
  }, "src/views/hr/components/HrSignDataImportDialog.vue")
  const target = instantiate(component, { visible: true, employees: [] })
  assert.strictEqual(target.taskStatusLabel("PENDING_SIGN"), "待员工签署")
  assert.strictEqual(target.taskSourceLabel("MANUAL_SIGN_EXCEL_IMPORT"), "签约名单导入")
  assert.strictEqual(target.issueText("EXISTING_OPEN_ONBOARD_TASK"), "已有开放的入职签约任务")
  assert.strictEqual(target.issueText("SOME_NEW_BACKEND_CODE"), "数据需核对")
  assert.strictEqual(target.rowStatusLabel({ status: "SOME_NEW_STATUS" }), "状态待核对")
  assert.strictEqual(target.rowStatusLabel({
    status: "WAITING_EMPLOYEE_DATA",
    dataRequestSigningSequence: "SIGNATURE_FIRST",
    missingFields: []
  }), "待确认签约包并签名")
  assert.strictEqual(target.rowStatusLabel({
    status: "WAITING_EMPLOYEE_DATA",
    dataRequestSigningSequence: "COMPANY_FIRST",
    missingFields: []
  }), "待员工补充", "the old company-first status copy must remain compatible")
  assert.strictEqual(target.rowStatusLabel({ status: "REFUSED" }),
    "员工已拒签，请新批次重发")
  assert.strictEqual(target.rowStatusLabel({ status: "EXPIRED" }),
    "首阶段已逾期，请新批次重发")
  assert.strictEqual(target.rowStatusType({ status: "REFUSED" }), "danger")
  assert.strictEqual(target.rowStatusType({ status: "EXPIRED" }), "danger")
  assert.strictEqual(target.showLatestTaskFact({ taskId: "25", latestTaskId: "25" }), false,
    "the current task is not historical context")
  assert.strictEqual(target.showLatestTaskFact({ taskId: "25", latestTaskId: "24" }), true,
    "a genuinely different previous task remains visible")
  assert.strictEqual(target.displayMessage("任务状态 PENDING_SIGN · MANUAL_SIGN_EXCEL_IMPORT"),
    "任务状态 待员工签署 · 签约名单导入")
  assert.strictEqual(target.displayMessage("任务状态 SOME_NEW_BACKEND_CODE"), "任务状态 相关状态")
  assert.strictEqual(target.displayMessage("Network Error", "操作失败"), "操作失败")
  assert.strictEqual(target.sendResultText({ result: "FAILED" }), "发送失败")
}

Promise.resolve()
  .then(testApiKeepsLargeIdsAndPayloads)
  .then(testScopeAndFileChangesInvalidatePreview)
  .then(testExcelOnlyPreviewWorksWithoutSelectedEmployees)
  .then(testSingleAndBatchSendUseTaskIdsAsStrings)
  .then(testEmployeeDataRequestUsesOnlyEmployeeFacts)
  .then(testSignatureFirstRequestAndGenerationGate)
  .then(testEmployeeSignatureFirstSubmissionIsTaskScoped)
  .then(testRejectedSignatureFirstCorrectionNeverUploadsAnotherSignature)
  .then(testEmployeeConditionalFieldsAndSignatureThreshold)
  .then(testDirtyRowPatchProtectsMaskedAddress)
  .then(testGenerationConfirmationsUseOnlyTargetRows)
  .then(testBatchRefreshKeepsSelectedRowsInSync)
  .then(testImportDialogDoesNotExposeBackendCodes)
  .then(() => console.log("hrExistingEmployeeOnboardContract tests passed"))
  .catch(error => {
    console.error(error)
    process.exitCode = 1
  })
