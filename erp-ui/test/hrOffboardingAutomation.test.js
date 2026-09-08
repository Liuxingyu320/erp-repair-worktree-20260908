const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.join(root, file), "utf8")
const dialogPath = "src/views/hr/components/HrOffboardingDialog.vue"

assert.ok(fs.existsSync(path.join(root, dialogPath)), "offboarding confirmation dialog must exist")

const dialog = read(dialogPath)
const employeeList = read("src/views/hr/components/HrEmployeeList.vue")
const detailDrawer = read("src/views/hr/components/HrProfileDetailDrawer.vue")
const employeeApi = read("src/api/hr/employee.js")
const signTaskDetail = read("src/views/oa/signTask/SignTaskDetailDrawer.vue")
const signTemplateCatalog = require("../src/views/oa/signPackage/signTemplateCatalog")
const signTemplateByCode = new Map(signTemplateCatalog.DEFAULT_TEMPLATE_TYPE_OPTIONS
  .map(option => [option.code, option]))

assert.ok(employeeApi.includes("/system/hr/employee/offboard/business-date"))
assert.ok(employeeApi.includes("/system/hr/employee/${userId}/offboard"))
assert.ok(employeeApi.includes("silentError: true"))
assert.ok(employeeList.includes("HrOffboardingDialog"))
assert.ok(employeeList.includes("hr:employee:offboard"))
assert.ok(employeeList.includes("profileValue(row, 'employeeStatus') !== '离职'"))
assert.ok(employeeList.includes("离职已确认，合同任务正在生成"))
assert.ok(employeeList.includes('this.$store.dispatch("todo/refreshSummaries")'))
assert.ok(detailDrawer.includes("确认离职") && detailDrawer.includes("hr:employee:offboard"))
assert.ok(signTaskDetail.includes("OFFBOARD") && signTaskDetail.includes("历史离职补录"))

for (const text of [
  "未来最后工作日暂不能确认离职，请在最后工作日当天操作",
  "riskConfirmation",
  "requestId",
  "工资结算",
  "资产交接",
  "竞业决定",
  "补偿金额",
  "风险提示",
  "预期主动离职",
  "工资结算未完成",
  "竞业决定需复核"
]) assert.ok(dialog.includes(text), `offboarding dialog must contain ${text}`)
assert.ok(dialog.includes('value-format="yyyy-MM-dd"'))
assert.ok(!dialog.includes("new Date("), "business-day comparison must not parse browser-local dates")
assert.ok(!dialog.toLowerCase().includes("hash"), "ordinary HR offboarding UI must not expose hashes")

for (const type of [
  "OFFBOARD_CONFIRMATION",
  "OFFBOARD_HANDOVER",
  "OFFBOARD_SETTLEMENT",
  "OFFBOARD_CONFIDENTIALITY_NONCOMPETE",
  "OFFBOARD_TERMINATION_NOTICE",
  "OFFBOARD_LEAVE_CERTIFICATE"
]) assert.ok(signTemplateByCode.has(type), `template fallback must expose ${type}`)
for (const placeholder of [
  "offboardingType", "salarySettlementStatus", "assetHandoverStatus",
  "nonCompeteDecision", "compensationAmount", "compensationNote"
]) assert.ok([...signTemplateByCode.values()].some(option =>
  option.scenario === "offboard" && option.requiredPlaceholders.includes(placeholder)
), `template fallback must expose ${placeholder}`)

function loadSfc(globals) {
  const script = dialog.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script)
  const source = script[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = { module: { exports: {} }, exports: {}, setTimeout, clearTimeout, ...globals }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(source, sandbox, { filename: dialogPath })
  return sandbox.module.exports
}

function bind(component, initial = {}) {
  const emitted = []
  const base = {
    visible: true,
    employee: employee(),
    $emit(name, value) { emitted.push({ name, value }) },
    ...initial
  }
  const target = { ...base, ...(component.data ? component.data.call(base) : {}), ...initial, emitted }
  Object.entries(component.methods || {}).forEach(([name, method]) => { target[name] = method.bind(target) })
  Object.entries(component.computed || {}).forEach(([name, computed]) => {
    const getter = typeof computed === "function" ? computed : computed.get
    if (getter) Object.defineProperty(target, name, { configurable: true, get: getter.bind(target) })
  })
  return target
}

function employee() {
  return {
    userId: 9,
    employeeName: "张三",
    nickName: "张三",
    status: "0",
    fields: { employeeStatus: "正式" }
  }
}

function completeStandardModel(target) {
  target.model.offboardingType = "VOLUNTARY_EXPECTED"
  target.model.salarySettlementStatus = "COMPLETED"
  target.model.assetHandoverStatus = "COMPLETED"
  target.model.nonCompeteDecision = "NOT_APPLICABLE"
}

const plain = value => JSON.parse(JSON.stringify(value))

async function run() {
  const submitted = []
  const component = loadSfc({
    getHrOffboardingBusinessDate: () => Promise.resolve({ data: { businessDate: "2027-01-01" } }),
    confirmHrEmployeeOffboarding: (userId, payload) => {
      submitted.push({ userId, payload: plain(payload) })
      return Promise.resolve({ data: { actionId: 901 } })
    }
  })

  const standard = bind(component)
  await standard.openDialog()
  assert.strictEqual(standard.businessDate, "2027-01-01")
  assert.strictEqual(standard.model.lastWorkingDate, "2027-01-01")
  assert.strictEqual(standard.model.offboardingType, "")
  assert.strictEqual(standard.model.salarySettlementStatus, "")
  assert.strictEqual(standard.model.assetHandoverStatus, "")
  assert.strictEqual(standard.model.nonCompeteDecision, "")
  assert.strictEqual(standard.canSubmit, false, "attestation fields must require explicit choices")
  assert.strictEqual(standard.dateRelation("2027-01-02", "2027-01-01"), "FUTURE")
  standard.model.lastWorkingDate = "2027-01-02"
  assert.strictEqual(standard.isFuture, true)
  assert.strictEqual(standard.canSubmit, false)
  assert.strictEqual(standard.dateWarning,
    "未来最后工作日暂不能确认离职，请在最后工作日当天操作")

  standard.model.lastWorkingDate = "2027-01-01"
  standard.model.reason = "个人职业规划"
  completeStandardModel(standard)
  assert.strictEqual(standard.isHighRisk, false)
  assert.strictEqual(standard.canSubmit, true)
  await standard.submit()
  assert.strictEqual(submitted.length, 1)
  assert.strictEqual(submitted[0].payload.riskConfirmation, null)
  assert.ok(standard.emitted.some(event => event.name === "confirmed"))

  const mutations = [
    ["lastWorkingDate", "2026-12-31", "HISTORICAL_OFFBOARDING"],
    ["offboardingType", "VOLUNTARY_UNEXPECTED", "NON_STANDARD_OFFBOARDING_TYPE"],
    ["offboardingType", "TERMINATION", "NON_STANDARD_OFFBOARDING_TYPE"],
    ["offboardingType", "DISCIPLINARY_TERMINATION", "NON_STANDARD_OFFBOARDING_TYPE"],
    ["offboardingType", "DISPUTED_TERMINATION", "NON_STANDARD_OFFBOARDING_TYPE"],
    ["salarySettlementStatus", "PENDING", "SALARY_SETTLEMENT_PENDING"],
    ["assetHandoverStatus", "PENDING", "ASSET_HANDOVER_PENDING"],
    ["nonCompeteDecision", "REQUIRED", "NON_COMPETE_REVIEW_REQUIRED"],
    ["nonCompeteDecision", "PENDING", "NON_COMPETE_REVIEW_REQUIRED"],
    ["compensationAmount", 1000, "COMPENSATION_REVIEW_REQUIRED"],
    ["compensationNote", "协商补偿", "COMPENSATION_REVIEW_REQUIRED"]
  ]
  for (const [field, value, risk] of mutations) {
    const target = bind(component)
    await target.openDialog()
    target.model.reason = "离职原因"
    completeStandardModel(target)
    target.model[field] = value
    assert.strictEqual(target.isHighRisk, true, `${field} must be high risk`)
    assert.ok(target.riskCodes.includes(risk), `${field} must expose ${risk}`)
  }

  const high = bind(component)
  await high.openDialog()
  high.model.reason = "公司解除"
  completeStandardModel(high)
  high.model.offboardingType = "TERMINATION"
  high.model.salarySettlementStatus = "PENDING"
  high.model.compensationAmount = 20000
  high.model.compensationNote = "双方协商补偿"
  high.model.nonCompeteDecision = "REQUIRED"
  assert.strictEqual(high.offboardingTypeText, "辞退")
  assert.strictEqual(high.salarySettlementStatusText, "未完成")
  assert.strictEqual(high.assetHandoverStatusText, "已完成")
  assert.strictEqual(high.nonCompeteDecisionText, "确定执行")
  assert.strictEqual(high.riskCodeText,
    "非标准离职类型、工资结算未完成、竞业决定需复核、补偿信息需复核")
  const callsBeforeHigh = submitted.length
  await high.submit()
  assert.strictEqual(submitted.length, callsBeforeHigh, "high risk must pause for confirmation")
  assert.strictEqual(high.riskDialogOpen, true)
  high.riskAcknowledged = true
  high.riskReason = "已核对解除依据和补偿方案"
  await high.confirmRisk()
  const payload = submitted.at(-1).payload
  assert.strictEqual(payload.riskConfirmation.confirmed, true)
  assert.strictEqual(payload.riskConfirmation.employeeId, 9)
  assert.strictEqual(payload.riskConfirmation.employeeName, "张三")
  assert.strictEqual(payload.riskConfirmation.offboardingType, "TERMINATION")
  assert.strictEqual(payload.riskConfirmation.lastWorkingDate, "2027-01-01")
  assert.strictEqual(payload.riskConfirmation.operationDate, "2027-01-01")
  assert.strictEqual(payload.riskConfirmation.salarySettlementStatus, "PENDING")
  assert.strictEqual(payload.riskConfirmation.assetHandoverStatus, "COMPLETED")
  assert.strictEqual(payload.riskConfirmation.nonCompeteDecision, "REQUIRED")
  assert.strictEqual(payload.riskConfirmation.compensationAmount, 20000)
  assert.strictEqual(payload.riskConfirmation.riskStatement,
    "我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用")

  let rejectedCalls = 0
  const rejectedComponent = loadSfc({
    getHrOffboardingBusinessDate: () => Promise.resolve({ data: { businessDate: "2027-01-01" } }),
    confirmHrEmployeeOffboarding: () => {
      rejectedCalls += 1
      return Promise.reject(new Error("backend rejected"))
    }
  })
  const rejected = bind(rejectedComponent)
  await rejected.openDialog()
  rejected.model.reason = "个人原因"
  completeStandardModel(rejected)
  const retryRequestId = rejected.requestId
  await rejected.submit()
  assert.strictEqual(rejectedCalls, 1)
  assert.strictEqual(rejected.requestId, retryRequestId, "failed retry must preserve requestId")
  assert.ok(rejected.errorMessage.includes("backend rejected"))
  assert.ok(!rejected.emitted.some(event => event.name === "confirmed"))

  rejected.close()
  rejected.visible = true
  await rejected.openDialog()
  assert.notStrictEqual(rejected.requestId, retryRequestId, "reopen must create a new requestId")
}

run().then(() => console.log("hrOffboardingAutomation tests passed"))
  .catch(error => { console.error(error); process.exit(1) })
