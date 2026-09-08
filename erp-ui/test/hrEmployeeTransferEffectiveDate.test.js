const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.join(root, file), "utf8")
const dialogPath = "src/views/hr/components/HrEmployeeTransferDialog.vue"

assert.ok(fs.existsSync(path.join(root, dialogPath)), "transfer confirmation dialog must exist")

const dialog = read(dialogPath)
const employeeList = read("src/views/hr/components/HrEmployeeList.vue")
const detailDrawer = read("src/views/hr/components/HrProfileDetailDrawer.vue")
const employeeApi = read("src/api/hr/employee.js")
const signTaskDetail = read("src/views/oa/signTask/SignTaskDetailDrawer.vue")
const signTemplateCatalog = require("../src/views/oa/signPackage/signTemplateCatalog")
const signTemplateByCode = new Map(signTemplateCatalog.DEFAULT_TEMPLATE_TYPE_OPTIONS
  .map(option => [option.code, option]))
const backendOptions = read(path.join("..", "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java"))

assert.ok(employeeApi.includes("/system/hr/employee/transfer/business-date"))
assert.ok(employeeApi.includes("/system/hr/employee/${userId}/transfer"))
assert.ok(employeeList.includes("HrEmployeeTransferDialog"))
assert.ok(employeeList.includes("hr:employee:transfer"))
assert.ok(detailDrawer.includes("确认调岗") && detailDrawer.includes("hr:employee:transfer"))
assert.ok(backendOptions.includes('item.put("postCode",value.getPostCode())'), "post options must expose canonical codes")

for (const text of [
  "未来日期的调岗暂不能确认，请在生效当天操作",
  "高风险补录",
  "历史调岗补录",
  "该操作将按历史日期补录并立即修改当前员工档案",
  "实际操作日期",
  "riskConfirmation",
  "requestId"
]) assert.ok(dialog.includes(text), `transfer dialog must contain ${text}`)

assert.ok(dialog.includes('value-format="yyyy-MM-dd"'))
assert.ok(dialog.includes('v-model="model.salaryTotal"') && dialog.includes("disabled"),
  "salary total must be derived rather than manually entered")
assert.ok(!dialog.includes("new Date("), "business-day comparison must not parse browser-local dates")
assert.ok(!dialog.toLowerCase().includes("hash"), "ordinary HR transfer UI must not expose technical hashes")
assert.ok(signTaskDetail.includes("历史调岗补录") && signTaskDetail.includes("businessEffectiveDate"),
  "task detail must show the historical transfer business marker")
for (const type of ["TRANSFER_CONFIRMATION", "TRANSFER_POST_DUTY", "TRANSFER_SALARY_CONFIRM"]) {
  assert.ok(signTemplateByCode.has(type), `template fallback must expose ${type}`)
}
for (const placeholder of ["transferEffectiveDate", "beforeDeptName", "afterDeptName", "beforePostName", "afterPostName"]) {
  assert.ok([...signTemplateByCode.values()].some(option =>
    option.scenario === "transfer" && option.requiredPlaceholders.includes(placeholder)
  ), `template fallback must expose ${placeholder}`)
}

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
    options: options(),
    $emit(name, value) { emitted.push({ name, value }) },
    $modal: { msgSuccess() {} },
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
    deptId: 20,
    departmentName: "上海一店",
    postIds: [401],
    postNames: "销售顾问",
    fields: {
      jobGrade: "P3",
      directSupervisorUserId: 55,
      directSupervisor: "原店长",
      workLocation: "上海市黄浦区",
      workCityLevel: "一线",
      legalEntityId: 301,
      legalEntityCode: "SH-COMPANY",
      legalEntity: "上海公司",
      baseSalary: 5500,
      postSalary: 1800,
      fieldAllowance: 400,
      performanceSalary: 1300,
      salaryTotal: 9000,
      salaryVersion: "CURRENT-2026"
    }
  }
}

function options() {
  return {
    departments: [
      { deptId: 20, deptName: "上海一店" },
      { deptId: 30, deptName: "上海二店" }
    ],
    posts: [
      { postId: 401, postCode: "SALES", postName: "销售顾问" },
      { postId: 402, postCode: "STORE_MANAGER", postName: "店长" }
    ],
    supervisors: [
      { userId: 55, employeeName: "原店长" },
      { userId: 66, employeeName: "区域经理" }
    ]
  }
}

const plain = value => JSON.parse(JSON.stringify(value))
const flush = () => new Promise(resolve => setImmediate(resolve))

async function run() {
  const submitted = []
  const component = loadSfc({
    getHrTransferBusinessDate: () => Promise.resolve({ data: { businessDate: "2027-01-01" } }),
    confirmHrEmployeeTransfer: (userId, payload) => {
      submitted.push({ userId, payload: plain(payload) })
      return Promise.resolve({ data: { actionId: 801 } })
    }
  })
  const target = bind(component)
  await target.openDialog()
  assert.strictEqual(target.businessDate, "2027-01-01")
  assert.strictEqual(target.dateRelation("2027-01-02", "2027-01-01"), "FUTURE")
  assert.strictEqual(target.dateRelation("2027-01-01", "2027-01-01"), "TODAY")
  assert.strictEqual(target.dateRelation("2026-12-31", "2027-01-01"), "HISTORICAL")
  assert.strictEqual(target.dateRelation("2024-02-29", "2024-03-01"), "HISTORICAL")

  target.model.effectiveDate = "2027-01-02"
  assert.strictEqual(target.isFuture, true)
  assert.strictEqual(target.canSubmit, false)
  assert.strictEqual(target.dateWarning, "未来日期的调岗暂不能确认，请在生效当天操作")

  const componentRedistribution = bind(component)
  await componentRedistribution.openDialog()
  componentRedistribution.model.baseSalary += 100
  componentRedistribution.model.postSalary -= 100
  componentRedistribution.syncSalaryTotal()
  assert.strictEqual(componentRedistribution.model.salaryTotal, 9000, "salary components should recalculate the total")
  assert.strictEqual(componentRedistribution.salaryValid, true)
  assert.strictEqual(componentRedistribution.hasBusinessChanges, true,
    "salary component redistribution must count as a transfer change")

  target.model.effectiveDate = "2027-01-01"
  target.model.targetDeptId = 30
  target.model.postId = 402
  target.model.jobGradeCode = "P4"
  target.model.directSupervisorId = 66
  target.model.workLocation = "上海市浦东新区"
  target.model.baseSalary = 6000
  target.model.postSalary = 2000
  target.model.fieldAllowance = 500
  target.model.performanceSalary = 1500
  target.model.salaryTotal = 10000
  target.model.salaryVersion = "TRANSFER-2027"
  assert.strictEqual(target.isToday, true)
  assert.strictEqual(target.canSubmit, true)
  await target.submit()
  assert.strictEqual(submitted.length, 1)
  assert.strictEqual(submitted[0].payload.riskConfirmation, null)
  assert.ok(target.emitted.some(event => event.name === "confirmed"))

  const historical = bind(component)
  await historical.openDialog()
  historical.model.effectiveDate = "2026-12-31"
  historical.model.targetDeptId = 30
  historical.model.postId = 402
  historical.model.jobGradeCode = "P4"
  historical.model.directSupervisorId = 66
  historical.model.workLocation = "上海市浦东新区"
  historical.model.salaryVersion = "TRANSFER-HISTORY"
  historical.model.salaryTotal = 9000
  const beforeSubmitCount = submitted.length
  await historical.submit()
  assert.strictEqual(submitted.length, beforeSubmitCount, "historical transfer must pause for second confirmation")
  assert.strictEqual(historical.riskDialogOpen, true)
  historical.riskAcknowledged = true
  historical.historicalReason = "补录纸质调岗单"
  await historical.confirmHistorical()
  const payload = submitted.at(-1).payload
  assert.strictEqual(payload.riskConfirmation.confirmed, true)
  assert.strictEqual(payload.riskConfirmation.employeeId, 9)
  assert.strictEqual(payload.riskConfirmation.beforeDeptName, "上海一店")
  assert.strictEqual(payload.riskConfirmation.beforePostName, "销售顾问")
  assert.strictEqual(payload.riskConfirmation.afterDeptName, "上海二店")
  assert.strictEqual(payload.riskConfirmation.afterPostName, "店长")
  assert.strictEqual(payload.riskConfirmation.effectiveDate, "2026-12-31")
  assert.strictEqual(payload.riskConfirmation.operationDate, "2027-01-01")
  assert.strictEqual(payload.riskConfirmation.riskStatement, "该操作将按历史日期补录并立即修改当前员工档案")

  let rejectedCalls = 0
  const rejectedComponent = loadSfc({
    getHrTransferBusinessDate: () => Promise.resolve({ data: { businessDate: "2027-01-01" } }),
    confirmHrEmployeeTransfer: () => { rejectedCalls += 1; return Promise.reject(new Error("backend rejected")) }
  })
  const rejected = bind(rejectedComponent)
  await rejected.openDialog()
  rejected.model.targetDeptId = 30
  rejected.model.postId = 402
  rejected.model.jobGradeCode = "P4"
  rejected.model.salaryVersion = "TRANSFER-REJECT"
  await rejected.submit()
  await flush()
  assert.strictEqual(rejectedCalls, 1)
  assert.ok(rejected.errorMessage.includes("backend rejected"))
  assert.ok(!rejected.emitted.some(event => event.name === "confirmed"), "backend rejection must never be presented as success")
}

run().then(() => console.log("hrEmployeeTransferEffectiveDate tests passed"))
  .catch(error => { console.error(error); process.exit(1) })
