const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const read = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")
const salaryApiSource = read("../src/api/oa/salary.js")
const salarySource = read("../src/views/oa/salary/index.vue")
const attendanceSource = read("../src/views/oa/attendance/index.vue")
const dayResultSource = read("../src/views/oa/attendance/components/DayResultManagement.vue")
const modalSource = read("../src/plugins/modal.js")

assert.ok(salaryApiSource.includes("/oa/salary/attendance-preflight"))
assert.ok(salarySource.includes("preflightSalaryAttendance"))
assert.ok(salarySource.includes("exceptionOnly: \"true\""))
assert.ok(salarySource.includes('prop="scheduledMinutes"') && salarySource.includes('prop="workedMinutes"'))
assert.ok(salarySource.includes('prop="paidLeaveMinutes"') && salarySource.includes('prop="unpaidLeaveMinutes"') && salarySource.includes('prop="absenceMinutes"'))
assert.ok(!salarySource.includes('label="上班时间"') && !salarySource.includes('label="下班时间"'))
assert.ok(attendanceSource.includes('name="day"'))
assert.ok(attendanceSource.includes("<day-result-management"))
assert.ok(dayResultSource.includes("listAttendanceDayResults") && dayResultSource.includes("getAttendanceDayResultPreflight") && dayResultSource.includes("settleAttendanceDayResults"))
assert.ok(modalSource.includes("confirm(content, title") && modalSource.includes("...options"),
  "the shared confirm helper must preserve custom action labels and safe dialog options")

function loadComponent(source, globals = {}) {
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, "Vue component script must exist")
  const transformed = match[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = {
    require: name => name === '@/utils/uiOperationScope' ? require('../src/utils/uiOperationScope') : {},
    getSelectedDeptId: () => '202',
    module: { exports: {} },
    exports: {},
    Promise,
    Date,
    Math,
    Object,
    Array,
    String,
    Number,
    setInterval: () => 1,
    clearInterval: () => {},
    getBusinessEmptyText: () => "",
    LegacySalaryNotice: {},
    ...globals
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(transformed, sandbox)
  return sandbox.module.exports
}

function bind(component, target) {
  Object.entries(component.methods || {}).forEach(([name, method]) => {
    target[name] = method.bind(target)
  })
  return target
}

function flushPromises() {
  return new Promise(resolve => setImmediate(resolve))
}

async function shouldBlockSalaryCalculationAndRouteToExactExceptionQueue() {
  let calculateCalls = 0
  const routes = []
  const originalList = [{ salaryId: 7, totalSalary: 3000 }]
  const component = loadComponent(salarySource, {
    preflightSalaryAttendance: () => Promise.resolve({
      data: {
        shopDeptId: 202,
        blocked: true,
        exceptionRecordCount: 2,
        affectedEmployeeCount: 1,
        issues: [{ userName: "seller01", workDate: "2026-07-03", reason: "未签退" }]
      }
    }),
    calculateSalary: () => { calculateCalls += 1; return Promise.resolve() },
    listMySalary: () => Promise.resolve({ rows: [], total: 0 }),
    listAllSalary: () => Promise.resolve({ rows: [], total: 0 }),
    getSalaryConfig: () => Promise.resolve({ data: {} }),
    saveSalaryConfig: () => Promise.resolve()
  })
  const target = bind(component, {
    salaryMonth: "2026-07",
    salaryConfig: { shopDeptId: 202 },
    list: originalList,
    calcLoading: false,
    $modal: { confirm: () => Promise.resolve(), msgWarning() {}, msgSuccess() {} },
    $router: { push: route => { routes.push(route); return Promise.resolve() } }
  })

  await target.doCalculate()

  assert.strictEqual(calculateCalls, 0, "blocked preflight must not call salary calculation")
  assert.strictEqual(target.list, originalList, "blocked preflight must preserve the salary table")
  assert.strictEqual(target.calcLoading, false, "loading must be cleared after the warning flow")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(routes[0])), {
    path: "/oa/attendance-v2",
    query: { tab: "day", salaryMonth: "2026-07", shopId: "202", exceptionOnly: "true" }
  })
}

async function shouldFocusNewDailyResultTabFromSalaryExceptionRoute() {
  const component = loadComponent(attendanceSource, {
    AttendanceSiteManagement: {},
    CorrectionManagement: {},
    DayResultManagement: {},
    LeaveManagement: {},
    ShiftManagement: {},
    WeeklySchedule: {},
    getSelectedDeptContext: () => ({})
  })
  const target = bind(component, {
    activeTab: "shift",
    requestMode: "",
    $route: { query: { tab: "day", salaryMonth: "2026-07", shopId: "202", exceptionOnly: "true" } }
  })

  target.applyRouteFocus()
  assert.strictEqual(target.activeTab, "day")
}

async function shouldRouteMissingContractSalaryToEmployeeArchive() {
  const routes = []
  let confirmation
  const component = loadComponent(salarySource)
  const target = bind(component, {
    salaryMonth: "2026-09", salaryConfig: {},
    $modal: { confirm: (message, title, options) => { confirmation = { message, options }; return Promise.resolve() } },
    $router: { push: route => { routes.push(route); return Promise.resolve() } }
  })
  await target.showAttendancePreflightWarning({ blocked: true, exceptionRecordCount: 1,
    affectedEmployeeCount: 1, issues: [{ category: "SALARY_PROFILE", userName: "employee", reason: "合同工资未入档" }] })
  assert.strictEqual(routes[0].path, "/hr/employee")
  assert.strictEqual(confirmation.options.confirmButtonText, "查看员工档案")
  assert.ok(confirmation.message.includes("工资计算待处理项"))
}

async function shouldInitializeDailyResultsFromSalaryExceptionRoute() {
  const component = loadComponent(dayResultSource, {
    checkPermi: () => true,
    countUnfinalizedAttendanceDayResults: () => Promise.resolve({ data: 0 }),
    getAttendanceDayResultPreflight: () => Promise.resolve({ data: { items: [] } }),
    listAttendanceDayResults: () => Promise.resolve({ data: [] }),
    listAttendanceEmployeeOptions: () => Promise.resolve({ data: [] }),
    settleAttendanceDayResults: () => Promise.resolve({ data: {} })
  })
  const target = bind(component, {
    salaryMonth: "",
    expectedShopId: "",
    query: { dates: [], userId: "old", exceptionOnly: false },
    $route: { query: { salaryMonth: "2026-07", shopId: "202", userId: "88", exceptionOnly: "true" } }
  })

  target.applyRouteQuery()

  assert.strictEqual(target.salaryMonth, "2026-07")
  assert.strictEqual(target.expectedShopId, "202")
  assert.strictEqual(target.query.userId, "88")
  assert.strictEqual(target.query.exceptionOnly, true)
  assert.deepStrictEqual(Array.from(target.query.dates), ["2026-07-01", "2026-07-31"])
}

Promise.resolve()
  .then(shouldBlockSalaryCalculationAndRouteToExactExceptionQueue)
  .then(shouldRouteMissingContractSalaryToEmployeeArchive)
  .then(shouldFocusNewDailyResultTabFromSalaryExceptionRoute)
  .then(shouldInitializeDailyResultsFromSalaryExceptionRoute)
  .then(() => console.log("attendance/payroll guard tests passed"))
  .catch(error => {
    console.error(error)
    process.exitCode = 1
  })
