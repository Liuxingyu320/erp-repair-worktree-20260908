const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.join(root, file), "utf8")
const component = read("src/views/hr/components/HrEmployeeList.vue")
const config = read("src/views/hr/components/hrFieldConfig.js")
const detail = read("src/views/hr/components/HrProfileDetailDrawer.vue")
const edit = read("src/views/hr/components/HrProfileEditDrawer.vue")
const employeeApi = read("src/api/hr/employee.js")
const employeePage = read("src/views/hr/employee/index.vue")
const completenessPage = read("src/views/hr/completeness/index.vue")
const mobileProfileEditor = read("src/views/mobile/hr/components/MobileHrProfileEditor.vue")
const systemUserPage = read("src/views/system/user/index.vue")
const sensitivePath = path.join(root, "src/views/hr/components/HrSensitiveFieldValue.vue")
const LARGE_EMPLOYEE_ID = "9007199254740993"

function loadFieldConfig() {
  const source = config
    .replace(/export const /g, "const ")
    .replace(/export function /g, "function ")
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(`${source}\nmodule.exports = { profileFieldLabel, formatProfileDisplayValue, groupMissingProfileFields, resolveMissingProfileFields, minimizeHrBusinessRemark }`, sandbox, { filename: "hrFieldConfig.js" })
  return sandbox.module.exports
}

assert.ok(config.includes('["employeeNo", "工号"]'), "employee number must be visible in profile fields")
assert.ok(config.includes('["remark", "备注"]'), "remark must be visible and editable")
assert.ok(config.includes('title: "数据记录"'), "record group needs the approved title")
assert.ok(config.includes("READ_ONLY_DERIVED_FIELDS"), "derived field boundary must be explicit")
const fieldConfig = loadFieldConfig()
assert.strictEqual(fieldConfig.profileFieldLabel("highestGraduationSchool"), "最高学历毕业学校")
assert.strictEqual(fieldConfig.profileFieldLabel("unknownBackendField"), "其他资料")
assert.strictEqual(fieldConfig.formatProfileDisplayValue("sex", "0"), "男")
assert.strictEqual(fieldConfig.formatProfileDisplayValue("employeeCategory", "FULL_TIME"), "全职")
assert.strictEqual(fieldConfig.formatProfileDisplayValue("foreignNationalFlag", "1"), "是")
assert.strictEqual(fieldConfig.formatProfileDisplayValue("entryDate", "2026-06-15T16:00:00.000Z"), "2026-06-16")
assert.strictEqual(fieldConfig.formatProfileDisplayValue("postNames", [81]), "岗位名称未配置")
assert.strictEqual(
  fieldConfig.formatProfileDisplayValue(
    "remark",
    "Excel导入；外部UserID：external-placeholder；员工类型：正式；入职：2026-01-01；业务备注：需安排培训"
  ),
  "业务备注：需安排培训",
  "read-only HR views must remove importer metadata while retaining business notes"
)
assert.strictEqual(
  fieldConfig.formatProfileDisplayValue("remark", "业务备注：保留原始业务信息"),
  "业务备注：保留原始业务信息",
  "ordinary business remarks must remain visible"
)
assert.strictEqual(
  fieldConfig.formatProfileDisplayValue("remark", "Excel导入；外部用户ID：external-placeholder；来源：sheet-placeholder"),
  "未填写",
  "a metadata-only import remark must not expose internal identifiers"
)
const groupedMissing = fieldConfig.groupMissingProfileFields(["email", "companyName", "highestGraduationSchool"])
assert.deepStrictEqual(JSON.parse(JSON.stringify(groupedMissing.map(group => group.title))), ["基础信息", "组织岗位", "教育信息"])
assert.strictEqual(groupedMissing[1].derivedFields[0].label, "所属公司")
assert.ok(!JSON.stringify(groupedMissing).includes("highestGraduationSchool"), "missing-field UI data must not expose backend field keys")
assert.deepStrictEqual(JSON.parse(JSON.stringify(fieldConfig.resolveMissingProfileFields([
  "bankAccount", "employeeNo", "companyName", "unknownLegacyField"
]))), {
  actionable: ["bankAccount"],
  workflow: [{ key: "employeeNo", source: "通过入职确认或工号生成规则处理" }],
  derived: [{ key: "companyName", source: "通过组织岗位配置或系统派生处理" }],
  unknown: ["unknownLegacyField"]
})
assert.ok(!component.includes("filteredRows"), "server-paged employees must not be filtered again")
assert.ok(component.includes("getHrEmployeeSummary"), "task totals must come from the backend summary")
for (const key of ["keyword", "employeeStatus", "employeeCategory", "deptId", "completenessStatus", "accountConfigurationStatus", "contractDue", "offboardAccountOnly"]) {
  assert.ok(component.includes(key), `employee query must send ${key}`)
}
for (const key of ["idNumber", "bankAccount", "registeredResidence", "currentAddress"]) {
  assert.ok(!component.includes(`prop="${key}"`), `${key} must not appear in the default employee list`)
}

assert.ok(fs.existsSync(sensitivePath), "sensitive value renderer must exist")
const sensitive = fs.existsSync(sensitivePath) ? fs.readFileSync(sensitivePath, "utf8") : ""
assert.ok(detail.includes("HrSensitiveFieldValue"), "profile detail must delegate sensitive rendering")
assert.ok(sensitive.includes("revealHrEmployeeSensitiveField"), "reveal must call the audited backend API")
assert.ok(sensitive.includes("hr:employee:sensitive:view"), "reveal action must be permission guarded")
assert.ok(sensitive.includes("maskedValue"), "masked value must be rendered by default")
assert.ok(!sensitive.includes("localStorage"), "revealed values must never be persisted locally")
assert.ok(!sensitive.includes("$store"), "revealed values must never be stored in Vuex")

for (const key of ["deptId", "postIds", "directSupervisorUserId"]) {
  assert.ok(edit.includes(key), `employee edit must submit ${key}`)
}
assert.ok(!detail.includes('profileValue(detail, "deptId")'), "detail drawer must not expose department ids")
assert.ok(!detail.includes('profileValue(detail, "postIds")'), "detail drawer must not expose post ids")
assert.ok(edit.includes("dirtySensitiveFields"), "sensitive dirtiness must be tracked per field")
assert.ok(edit.includes("buildEmployeeUpdatePayload"), "employee patch construction must be isolated")
assert.ok(edit.includes('field.key === "employeeNo"'), "employee number must have an explicit read-only rule")
assert.ok(edit.includes("disabled"), "read-only fields must render disabled")
assert.ok(edit.includes("getHrEmployeeFormOptions"), "edit options must come from the backend")
assert.ok(edit.includes("previewHrEmployeeDerived"), "derived organization fields must use backend preview")
assert.ok(!edit.includes("allow-create"), "HR enums must not accept hardcoded free-form values")
assert.ok(employeeApi.includes("delete patch.userId"), "employee API must strip the route id from the PATCH body")
assert.ok(component.includes("updateHrEmployee(employeeId, frozenPayload)"), "employee save must pass the route id separately")
assert.ok(employeePage.includes(":initial-employee-id=\"routeEmployeeId\""), "employee route must pass a validated deep-link id")
assert.ok(employeePage.includes(":initial-filters=\"routeFilters\""), "employee route must pass validated todo queue filters")
assert.ok(!employeePage.includes("openRouteEmployee"), "only the child prop watcher may own route reloads")
assert.ok(component.includes("initialEmployeeId(value, previous)"), "employee list must own route reuse through its prop watcher")
assert.ok(component.includes("retryList"), "employee list failures must offer retry")
assert.ok(component.includes("listEmptyText"), "employee empty and error states must be distinct")
assert.ok(component.includes("重试"), "employee list error state must expose a visible retry action")
assert.ok(component.includes("档案覆盖度") && detail.includes("档案覆盖度"))
assert.ok(component.includes('profileValue(row, "employeeCategory")'), "employee category codes must be displayed as Chinese labels")
assert.ok(component.includes("必填字段：已填 {{ row.requiredCompletedFieldCount || 0 }}/{{ row.requiredApplicableFieldCount || 0 }}"), "employee card coverage counts must use required fields")
assert.ok(
  component.includes('{{ profileValue(row, "remark") }}') &&
    !component.includes('{{ row.remark || "-" }}'),
  "employee cards must render the minimized remark rather than raw importer metadata"
)
assert.ok(!component.includes('this.profileRawValue(row, "profileCompletionPercent")'), "employee card percentage must not fall back to full-profile coverage")
assert.ok(!component.includes("资料完整度") && !detail.includes("资料完整度"), "employee coverage must not use the ambiguous old label")
assert.ok(mobileProfileEditor.includes("dirtySensitiveFields"), "mobile sensitive replacements must be explicitly tracked")
assert.ok(mobileProfileEditor.includes("patch: this.buildPatch()"), "mobile editor must emit a flat employee patch")
assert.ok(!mobileProfileEditor.includes("form.profile"), "mobile editor must not rebuild the nested compatibility profile")
assert.ok(
  !systemUserPage.includes("getHrEmployeeFormOptions") &&
    systemUserPage.includes("response.employeeStatusOptions"),
  "system user page must load safe employee-status options from its own authorized endpoint"
)
assert.ok(!systemUserPage.includes("employeeStatusOptions: ["), "system user page must not hard-code employee statuses")
assert.ok(!systemUserPage.includes('v-model="queryParams.employeeStatus" placeholder="请选择员工状态" clearable filterable allow-create'))
assert.ok(!systemUserPage.includes('v-model="form.profile.employeeStatus" placeholder="请选择员工状态" clearable filterable allow-create'))
assert.ok(systemUserPage.includes("employeeStatusNeedsNormalization"))
assert.ok(systemUserPage.includes("状态待规范"))
for (const token of ["****", "••••", "●●●●"]) {
  assert.ok(edit.includes(token), `payload guard must reject ${token}`)
}

function loadSfcScript(relativePath, globals = {}) {
  const source = read(relativePath)
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, `${relativePath} must contain a script block`)
  const transformed = match[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    setTimeout,
    clearTimeout,
    getSelectedDeptId: () => "10",
    require(id) { return require(path.resolve(root, "src", id.slice(2))) },
    HrEmployeeTransferDialog: {},
    HrSignDataImportDialog: {},
    process: { env: { VUE_APP_SIGN_EXCEL_IMPORT_ENABLED: "true" } },
    loadHrEmployeePreferences: () => ({ advancedFilterOpen: false, selectedOptionalColumns: [], pageSize: 10 }),
    saveHrEmployeePreferences: () => {},
    ...globals
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(transformed, sandbox, { filename: relativePath })
  return sandbox.module.exports
}

function loadEmployeeApi(request) {
  const source = employeeApi
    .replace(/import\s+request\s+from\s+["'][^"']+["']\s*/, "")
    .replace(/export const /g, "const ")
  const sandbox = { module: { exports: {} }, exports: {}, request }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(`${source}\nmodule.exports = { updateHrEmployee }`, sandbox, { filename: "employee.js" })
  return sandbox.module.exports
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => {
    resolve = ok
    reject = fail
  })
  return { promise, resolve, reject }
}

function bindMethods(component, target) {
  Object.entries(component.methods || {}).forEach(([name, method]) => {
    target[name] = method.bind(target)
  })
  Object.entries(component.computed || {}).forEach(([name, computed]) => {
    const getter = typeof computed === "function" ? computed : computed.get
    if (getter) Object.defineProperty(target, name, { configurable: true, get: getter.bind(target) })
  })
  return target
}

const flushPromises = () => new Promise(resolve => setImmediate(resolve))
const plain = value => JSON.parse(JSON.stringify(value))

async function testSensitiveRevealIgnoresStaleRequests() {
  const requests = []
  const component = loadSfcScript("src/views/hr/components/HrSensitiveFieldValue.vue", {
    revealHrEmployeeSensitiveField(employeeId, field) {
      const pending = deferred()
      requests.push({ employeeId, field, pending })
      return pending.promise
    }
  })
  const target = bindMethods(component, {
    ...component.data(),
    employeeId: 1,
    field: "idNumber",
    maskedValue: "3500**********0000"
  })

  target.reveal()
  target.employeeId = 2
  target.resetReveal()
  target.reveal()
  requests[0].pending.reject(new Error("stale failure"))
  await flushPromises()
  assert.strictEqual(target.errorMessage, "", "stale reveal rejection must not surface an error")
  assert.strictEqual(target.revealing, true, "stale reveal finally must not clear the active loading state")
  requests[1].pending.resolve({ data: { fieldKey: "idNumber", value: "CURRENT-ID" } })
  await flushPromises()
  assert.strictEqual(target.revealedValue, "CURRENT-ID")

  target.resetReveal()
  target.reveal()
  target.field = "bankAccount"
  target.resetReveal()
  target.reveal()
  requests[2].pending.resolve({ data: { fieldKey: "idNumber", value: "STALE-ID" } })
  await flushPromises()
  assert.strictEqual(target.revealedValue, undefined, "stale reveal success must not overwrite a new field")
  requests[3].pending.resolve({ data: { fieldKey: "bankAccount", value: "CURRENT-BANK" } })
  await flushPromises()
  assert.strictEqual(target.revealedValue, "CURRENT-BANK")
  assert.deepStrictEqual(requests.map(item => [item.employeeId, item.field]), [
    [1, "idNumber"], [2, "idNumber"], [2, "idNumber"], [2, "bankAccount"]
  ])
}

async function testEmployeeListIgnoresStaleListSummaryAndDetail() {
  const listFirst = deferred()
  const listSecond = deferred()
  const listThird = deferred()
  const listFourth = deferred()
  const summaryFirst = deferred()
  const summarySecond = deferred()
  const summaryThird = deferred()
  const summaryFourth = deferred()
  const detailFirst = deferred()
  const detailSecond = deferred()
  const detailThird = deferred()
  const detailFourth = deferred()
  const listRequests = [listFirst, listSecond, listThird, listFourth]
  const summaryRequests = [summaryFirst, summarySecond, summaryThird, summaryFourth]
  const detailRequests = [detailFirst, detailSecond, detailThird, detailFourth]
  const summaryParams = []
  const component = loadSfcScript("src/views/hr/components/HrEmployeeList.vue", {
    ExcelImportDialog: {},
    HrProfileDetailDrawer: {},
    HrProfileEditDrawer: {},
    COMPLETENESS_FIELDS: [],
    HR_TASKS: [],
    OPTIONAL_LIST_FIELDS: [],
    HR_EXPORT_ACTION: "employee/export",
    getHrEmployee: () => detailRequests.shift().promise,
    getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }),
    getHrEmployeeSummary: params => {
      summaryParams.push(plain(params))
      return summaryRequests.shift().promise
    },
    listHrEmployees: () => Promise.resolve({ rows: [], total: 0 }),
    updateHrEmployee: () => Promise.resolve(),
    listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 }),
    LEGACY_HR_EMPLOYEE_IMPORT_ACTION: "import",
    LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION: "template",
    confirmExportAction: () => Promise.resolve()
  })
  const base = { mode: "employee" }
  const target = bindMethods(component, {
    ...base,
    ...component.data.call(base),
    source: "employee",
    defaultEmployeeStatus: "",
    $set(object, key, value) { object[key] = value },
    $delete(object, key) { delete object[key] }
  })
  target.listRequest = () => listRequests.shift().promise
  target.queryParams.completenessStatus = "INCOMPLETE"
  target.queryParams.accountConfigurationStatus = "MISSING"

  target.getList()
  target.getList()
  assert.deepStrictEqual(summaryParams, [{}, {}], "summary cards must exclude task-specific completeness and account filters")
  listFirst.resolve({ rows: [{ userId: 1 }], total: 1 })
  summaryFirst.resolve({ data: { totalEmployeeCount: 1 } })
  await flushPromises()
  assert.strictEqual(target.rows.length, 0, "stale list success must not overwrite the latest request")
  assert.strictEqual(target.summary.totalEmployeeCount, 0, "stale summary success must be ignored")
  assert.strictEqual(target.loading, true, "stale list finally must not clear active loading")
  listSecond.resolve({ rows: [{ userId: 2 }], total: 1 })
  summarySecond.resolve({ data: { totalEmployeeCount: 2 } })
  await flushPromises()
  assert.strictEqual(target.rows[0].userId, 2)
  assert.strictEqual(target.summary.totalEmployeeCount, 2)
  assert.strictEqual(target.loading, false)

  target.getList()
  target.getList()
  listThird.reject(new Error("stale list failure"))
  summaryThird.reject(new Error("stale summary failure"))
  await flushPromises()
  assert.strictEqual(target.rows[0].userId, 2, "stale list catch must preserve current rows")
  assert.strictEqual(target.summary.totalEmployeeCount, 2, "stale summary catch must preserve current metrics")
  assert.strictEqual(target.loading, true, "stale list catch/finally must preserve active loading")
  listFourth.resolve({ rows: [{ userId: 4 }], total: 1 })
  summaryFourth.resolve({ data: { totalEmployeeCount: 4 } })
  await flushPromises()
  assert.strictEqual(target.rows[0].userId, 4)
  assert.strictEqual(target.summary.totalEmployeeCount, 4)

  target.openDetail({ userId: 1 })
  target.openDetail({ userId: 2 })
  detailFirst.resolve({ data: { userId: 1 } })
  await flushPromises()
  assert.strictEqual(target.detail, null, "stale detail success must not replace the current selection")
  assert.strictEqual(target.detailLoading, true, "stale detail finally must not clear current loading")
  detailSecond.resolve({ data: { userId: 2 } })
  await flushPromises()
  assert.strictEqual(target.detail.userId, "2")
  assert.strictEqual(target.detailLoading, false)

  target.openDetail({ userId: 3 })
  target.openDetail({ userId: 4 })
  detailThird.reject(new Error("stale detail failure"))
  await flushPromises()
  assert.strictEqual(target.detail.userId, "2", "stale detail catch must preserve current detail")
  assert.strictEqual(target.detailLoading, true, "stale detail catch/finally must preserve active loading")
  detailFourth.resolve({ data: { userId: 4 } })
  await flushPromises()
  assert.strictEqual(target.detail.userId, "4")
  assert.strictEqual(target.detailLoading, false)
}

async function testEmployeeListFailureIsRetryableAndNotAnEmptyBusinessResult() {
  let listCalls = 0
  const listComponent = loadSfcScript("src/views/hr/components/HrEmployeeList.vue", {
    ExcelImportDialog: {}, HrProfileDetailDrawer: {}, HrProfileEditDrawer: {}, COMPLETENESS_FIELDS: [], HR_TASKS: [],
    OPTIONAL_LIST_FIELDS: [], HR_EXPORT_ACTION: "employee/export", LEGACY_HR_EMPLOYEE_IMPORT_ACTION: "import",
    LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION: "template", confirmExportAction: () => Promise.resolve(),
    listHrEmployees: () => { listCalls += 1; return Promise.reject(new Error("network down")) },
    getHrEmployee: () => Promise.resolve({ data: {} }), getHrEmployeeSummary: () => Promise.resolve({ data: {} }),
    getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }), updateHrEmployee: () => Promise.resolve(),
    listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 }),
    loadHrEmployeePreferences: () => ({ advancedFilterOpen: true, selectedOptionalColumns: ["contractEndDate"], pageSize: 50 })
  })
  const base = { mode: "employee" }
  const target = bindMethods(listComponent, {
    ...base, ...listComponent.data.call(base), source: "employee", defaultEmployeeStatus: "",
    initialFilters: {}, initialEmployeeId: undefined
  })
  target.rows = [{ userId: 1, employeeName: "旧数据" }]

  await target.getList()

  assert.strictEqual(target.listError, "员工档案加载失败，请重试")
  assert.deepStrictEqual(plain(target.rows), [])
  assert.strictEqual(target.listEmptyText, "加载失败")
  target.queryParams.pageNum = 4
  await target.retryList()
  assert.strictEqual(target.queryParams.pageNum, 1)
  assert.strictEqual(listCalls, 2)
}

function testMissingOnlyEditorSelectsFirstActionableGroup() {
  const groups = [
    { title: "教育信息", fields: [["firstEducation", "第一学历"], ["highestEducation", "最高学历"]] },
    { title: "联系人与银行", fields: [["bankAccount", "银行卡号"], ["bankName", "开户银行"]] }
  ]
  const editComponent = loadSfcScript("src/views/hr/components/HrProfileEditDrawer.vue", {
    getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }), previewHrEmployeeDerived: () => Promise.resolve({ data: {} }),
    DETAIL_GROUPS: groups, EDIT_REQUIRED_FIELDS: [], READ_ONLY_DERIVED_FIELDS: [], SENSITIVE_FIELDS: ["bankAccount"],
    resolveMissingProfileFields: fieldConfig.resolveMissingProfileFields,
    profileFieldLabel: fieldConfig.profileFieldLabel,
    CONTRACT_TERM_OPTIONS: [], CONTRACT_TYPE_OPTIONS: [], SOCIAL_TYPE_OPTIONS: [], signingOptionsForKey: () => null
  })
  const base = {
    detail: { userId: 7, fields: {}, profile: {} },
    missingFields: ["firstEducation", "bankAccount"], visible: true
  }
  const target = bindMethods(editComponent, {
    ...base, ...editComponent.data.call(base),
    $set(object, key, value) { object[key] = value },
    $delete(object, key) { delete object[key] }
  })

  target.resetForm()

  assert.strictEqual(target.missingOnly, true)
  assert.strictEqual(target.groupMissingCount(groups[0]), 1)
  assert.strictEqual(target.groupMissingCount(groups[1]), 1)
  assert.strictEqual(target.activeTab, "教育信息")
  assert.deepStrictEqual(plain(target.visibleGroupFields(groups[0]).map(field => field.key)), ["firstEducation"])
}

async function testEmployeeApiOmitsRouteIdFromPatchBody() {
  let requestConfig
  const api = loadEmployeeApi(config => {
    requestConfig = config
    return Promise.resolve({})
  })
  await api.updateHrEmployee(7, { userId: 7, remark: "updated" })
  assert.strictEqual(requestConfig.url, "/system/hr/employee/7")
  assert.deepStrictEqual(plain(requestConfig.data), { remark: "updated" }, "PATCH body must omit the route userId")
}

async function testEmployeeDeepLinkLoadsScopedRowAndOpensDetail() {
  const page = loadSfcScript("src/views/hr/employee/index.vue", { HrEmployeeList: {} })
  const pageTarget = bindMethods(page, { $route: { query: {
    userId: LARGE_EMPLOYEE_ID, deptId: "20", contractDue: "true", offboardAccountOnly: "false"
  } } })
  assert.strictEqual(pageTarget.routeEmployeeId, LARGE_EMPLOYEE_ID)
  assert.deepStrictEqual(plain(pageTarget.routeFilters), {
    deptId: 20, contractDue: true
  }, "employee todo filters must accept only positive ids and literal true booleans")
  pageTarget.$route.query.userId = "//evil"
  assert.strictEqual(pageTarget.routeEmployeeId, undefined, "invalid route ids must be rejected")

  const listCalls = []
  let detailCalls = 0
  const listComponent = loadSfcScript("src/views/hr/components/HrEmployeeList.vue", {
    ExcelImportDialog: {}, HrProfileDetailDrawer: {}, HrProfileEditDrawer: {}, COMPLETENESS_FIELDS: [], HR_TASKS: [],
    OPTIONAL_LIST_FIELDS: [], HR_EXPORT_ACTION: "employee/export", LEGACY_HR_EMPLOYEE_IMPORT_ACTION: "import",
    LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION: "template", confirmExportAction: () => Promise.resolve(),
    listHrEmployees: params => { listCalls.push(plain(params)); return Promise.resolve({ rows: [{ userId: LARGE_EMPLOYEE_ID }], total: 1 }) },
    getHrEmployee: id => { detailCalls += 1; return Promise.resolve({ data: { userId: id } }) },
    getHrEmployeeSummary: () => Promise.resolve({ data: {} }), getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }),
    updateHrEmployee: () => Promise.resolve(), listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 })
  })
  const base = { mode: "employee" }
  const target = bindMethods(listComponent, {
    ...base, ...listComponent.data.call(base), source: "employee", defaultEmployeeStatus: "", initialEmployeeId: undefined,
    $set(object, key, value) { object[key] = value }, $delete(object, key) { delete object[key] }
  })

  await target.openRouteEmployee(LARGE_EMPLOYEE_ID)
  await flushPromises()
  assert.strictEqual(listCalls[0].userId, LARGE_EMPLOYEE_ID, "deep link must preserve Long ids in the scoped server list query")
  assert.strictEqual(detailCalls, 1)
  assert.strictEqual(target.detail.userId, LARGE_EMPLOYEE_ID)
}

async function testEmployeeQueueRouteFiltersReachTheFirstServerRequest() {
  const listCalls = []
  const listComponent = loadSfcScript("src/views/hr/components/HrEmployeeList.vue", {
    ExcelImportDialog: {}, HrProfileDetailDrawer: {}, HrProfileEditDrawer: {}, COMPLETENESS_FIELDS: [], HR_TASKS: [],
    OPTIONAL_LIST_FIELDS: [], HR_EXPORT_ACTION: "employee/export", LEGACY_HR_EMPLOYEE_IMPORT_ACTION: "import",
    LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION: "template", confirmExportAction: () => Promise.resolve(),
    listHrEmployees: params => { listCalls.push(plain(params)); return Promise.resolve({ rows: [], total: 0 }) },
    getHrEmployee: () => Promise.resolve({ data: {} }), getHrEmployeeSummary: () => Promise.resolve({ data: {} }),
    getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }), updateHrEmployee: () => Promise.resolve(),
    listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 }),
    loadHrEmployeePreferences: () => ({ advancedFilterOpen: true, selectedOptionalColumns: ["contractEndDate"], pageSize: 50 })
  })
  const base = {
    mode: "employee", source: "employee", defaultEmployeeStatus: "", initialEmployeeId: undefined,
    initialFilters: { deptId: 20, contractDue: true, offboardAccountOnly: undefined },
    $store: { getters: { id: 7, permissions: ["hr:employee:list"] } }
  }
  const target = bindMethods(listComponent, { ...base, ...listComponent.data.call(base) })

  listComponent.created.call(target)
  await flushPromises()

  assert.strictEqual(listCalls.length, 1)
  assert.strictEqual(listCalls[0].deptId, 20)
  assert.strictEqual(listCalls[0].contractDue, true)
  assert.strictEqual(listCalls[0].offboardAccountOnly, undefined)
  assert.strictEqual(listCalls[0].pageSize, 50, "the first request must use the restored safe page size")
}

function testCompletenessQueueRouteFiltersInitializeTheFirstQuery() {
  const page = loadSfcScript("src/views/hr/completeness/index.vue", {
    getHrCompletenessDepartments: () => Promise.resolve({ data: [] }),
    getHrCompletenessSummary: () => Promise.resolve({ data: {} }),
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 })
  })
  const state = page.data.call({
    $route: { query: { contextDeptId: "20", completenessStatus: "incomplete" } }
  })
  assert.strictEqual(state.query.deptId, 20)
  assert.strictEqual(state.query.completenessStatus, "INCOMPLETE")
}

async function testMissingEmployeeDeepLinkClosesAndInvalidatesPriorDetail() {
  const oldDetail = deferred()
  const missingList = deferred()
  const component = loadSfcScript("src/views/hr/components/HrEmployeeList.vue", {
    ExcelImportDialog: {}, HrProfileDetailDrawer: {}, HrProfileEditDrawer: {}, COMPLETENESS_FIELDS: [], HR_TASKS: [],
    OPTIONAL_LIST_FIELDS: [], HR_EXPORT_ACTION: "employee/export", LEGACY_HR_EMPLOYEE_IMPORT_ACTION: "import",
    LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION: "template", confirmExportAction: () => Promise.resolve(),
    listHrEmployees: () => missingList.promise, getHrEmployee: () => oldDetail.promise,
    getHrEmployeeSummary: () => Promise.resolve({ data: {} }), getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }),
    updateHrEmployee: () => Promise.resolve(), listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 })
  })
  const base = { mode: "employee" }
  const target = bindMethods(component, {
    ...base, ...component.data.call(base), source: "employee", defaultEmployeeStatus: "", initialEmployeeId: undefined,
    detail: { userId: 1 }, detailOpen: true, $set(object, key, value) { object[key] = value }, $delete(object, key) { delete object[key] }
  })
  target.openDetail({ userId: 1 })
  const routePending = target.openRouteEmployee(999)
  assert.strictEqual(target.detail, null, "route switch must immediately clear prior detail")
  assert.strictEqual(target.detailOpen, false, "route switch must immediately close the drawer")
  missingList.resolve({ rows: [], total: 0 })
  await routePending
  oldDetail.resolve({ data: { userId: 1 } })
  await flushPromises()
  assert.strictEqual(target.detail, null, "stale prior detail must not reopen after a missing route row")
  assert.strictEqual(target.detailOpen, false)
}

async function testEmployeeKeepAliveReappliesOnlyDivergedDeepLink() {
  let listCalls = 0
  const component = loadSfcScript("src/views/hr/components/HrEmployeeList.vue", {
    ExcelImportDialog: {}, HrProfileDetailDrawer: {}, HrProfileEditDrawer: {}, COMPLETENESS_FIELDS: [], HR_TASKS: [],
    OPTIONAL_LIST_FIELDS: [], HR_EXPORT_ACTION: "employee/export", LEGACY_HR_EMPLOYEE_IMPORT_ACTION: "import",
    LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION: "template", confirmExportAction: () => Promise.resolve(),
    listHrEmployees: () => { listCalls += 1; return Promise.resolve({ rows: [], total: 0 }) }, getHrEmployee: () => Promise.resolve({ data: {} }),
    getHrEmployeeSummary: () => Promise.resolve({ data: {} }), getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }),
    updateHrEmployee: () => Promise.resolve(), listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 })
  })
  const base = { mode: "employee" }
  const target = bindMethods(component, { ...base, ...component.data.call(base), source: "employee", defaultEmployeeStatus: "", initialEmployeeId: 7 })
  target.queryParams.userId = 7
  await component.activated.call(target)
  assert.strictEqual(listCalls, 0, "initial activation must not duplicate the created deep-link load")
  target.queryParams.userId = undefined
  await component.activated.call(target)
  assert.strictEqual(listCalls, 1, "keep-alive re-entry must restore a diverged deep-link query")

  const normal = bindMethods(component, { ...base, ...component.data.call(base), source: "employee", defaultEmployeeStatus: "", initialEmployeeId: undefined })
  normal.queryParams.userId = 5
  await component.activated.call(normal)
  assert.strictEqual(listCalls, 1, "no-deep-link activation must preserve normal manual filters")
}

function testEmployeePayloadRuntimeBoundaries() {
  const component = loadSfcScript("src/views/hr/components/HrProfileEditDrawer.vue", {
    getHrEmployeeFormOptions: () => Promise.resolve({ data: {} }),
    previewHrEmployeeDerived: () => Promise.resolve({ data: {} }),
    DETAIL_GROUPS: [{ title: "基础信息", fields: [] }],
    EDIT_REQUIRED_FIELDS: [],
    READ_ONLY_DERIVED_FIELDS: ["companyName"],
    SENSITIVE_FIELDS: ["phoneNumber", "idNumber", "bankAccount", "registeredResidence", "currentAddress"],
    resolveMissingProfileFields: fieldConfig.resolveMissingProfileFields,
    profileFieldLabel: fieldConfig.profileFieldLabel
  })
  const target = bindMethods(component, {
    form: {
      userId: 7,
      values: { employeeNo: "E007", remark: "unchanged", companyName: "Derived" },
      deptId: 20,
      postIds: [30],
      directSupervisorUserId: 40
    },
    initialForm: {
      userId: 7,
      values: { employeeNo: "E007", remark: "unchanged", companyName: "Derived" },
      deptId: 20,
      postIds: [30],
      directSupervisorUserId: 40
    },
    sensitiveValues: {},
    dirtySensitiveFields: {},
    fieldErrors: {},
    confirmedEmployee: true
  })
  assert.deepStrictEqual(plain(target.buildEmployeeUpdatePayload()), {}, "unchanged relations and sensitive values must be omitted")
  target.dirtySensitiveFields.idNumber = true
  target.sensitiveValues.idNumber = "350000199001010000"
  assert.deepStrictEqual(plain(target.buildEmployeeUpdatePayload()), { idNumber: "350000199001010000" }, "only the exact dirty sensitive field is submitted")
  target.sensitiveValues.idNumber = "上海市中·心路1号"
  assert.deepStrictEqual(plain(target.buildEmployeeUpdatePayload()), { idNumber: "上海市中·心路1号" }, "a legitimate single Chinese middle dot is not a mask")
  target.sensitiveValues.idNumber = "上海*"
  assert.strictEqual(target.buildEmployeeUpdatePayload(), null, "a single ASCII mask star must be rejected")
  target.sensitiveValues.idNumber = "上海•"
  assert.strictEqual(target.buildEmployeeUpdatePayload(), null, "a single mask bullet must be rejected")
  target.sensitiveValues.idNumber = "3500••••0000"
  assert.strictEqual(target.buildEmployeeUpdatePayload(), null, "repeated mask bullets must be rejected")

  const resetBase = { detail: { userId: 7, deptId: 20, postIds: [30], fields: {} } }
  const resetTarget = bindMethods(component, {
    ...resetBase,
    ...component.data.call(resetBase),
    $set(object, key, value) { object[key] = value },
    $delete(object, key) { delete object[key] }
  })
  resetTarget.resetForm()
  assert.strictEqual(resetTarget.form.deptId, 20, "current department selection must round-trip from detail DTO")
  assert.deepStrictEqual(plain(resetTarget.form.postIds), [30], "current post selections must round-trip from detail DTO")
  assert.deepStrictEqual(plain(resetTarget.buildEmployeeUpdatePayload()), {}, "round-tripped unchanged relations must be omitted")
}

;(async () => {
  await testSensitiveRevealIgnoresStaleRequests()
  await testEmployeeListIgnoresStaleListSummaryAndDetail()
  await testEmployeeListFailureIsRetryableAndNotAnEmptyBusinessResult()
  testMissingOnlyEditorSelectsFirstActionableGroup()
  testEmployeePayloadRuntimeBoundaries()
  await testEmployeeApiOmitsRouteIdFromPatchBody()
  await testEmployeeDeepLinkLoadsScopedRowAndOpensDetail()
  await testEmployeeQueueRouteFiltersReachTheFirstServerRequest()
  testCompletenessQueueRouteFiltersInitializeTheFirstQuery()
  await testMissingEmployeeDeepLinkClosesAndInvalidatesPriorDetail()
  await testEmployeeKeepAliveReappliesOnlyDivergedDeepLink()
  console.log("hrEmployeeMasterFields tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
