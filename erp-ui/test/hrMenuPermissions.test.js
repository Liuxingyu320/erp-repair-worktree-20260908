const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = relative => fs.readFileSync(path.join(root, relative), "utf8")
const onboardingApi = read("src/api/hr/onboarding.js")
const completeness = read("src/views/hr/completeness/index.vue")
const onboarding = read("src/views/hr/onboarding/index.vue")
const sql = read("../sql/erp_user_hr_onboarding_20260710.sql")
const dockerSql = read("../docker/mysql/db/erp_user_hr_onboarding_20260710.sql")
const positionPath = path.join(root, "src/views/hr/positionConfig/index.vue")
assert.ok(fs.existsSync(positionPath), "position config page must exist")
const position = fs.existsSync(positionPath) ? fs.readFileSync(positionPath, "utf8") : ""

for (const fragment of ["/summary", "/employees", "/departments"]) {
  assert.ok(completeness.includes(fragment) || read("src/api/hr/completeness.js").includes(fragment), `completeness flow needs ${fragment}`)
}
for (const field of [
  "onboardingCompletionPercent", "profileCompletionPercent", "postEntryDueDate", "postEntryOverdue",
  "accountConfigurationRiskCodes", "accountConfigurationStatus", "onboardingDetailUrl", "employeeDetailUrl"
]) assert.ok(completeness.includes(field), `completeness page must render ${field}`)
assert.ok(completeness.includes('accountConfigurationStatus: "MISSING"'))
assert.ok(completeness.includes("账号待配置"))
assert.ok(!completeness.includes("filteredRows"))
for (const permission of ["hr:employee:query", "hr:employee:list", "hr:onboarding:query", "hr:onboarding:list", "hr:onboarding:config"]) {
  assert.ok(completeness.includes(permission), `completeness links must be guarded by ${permission}`)
}

for (const fragment of [
  "/system/hr/onboarding/config/list", "/system/hr/onboarding/config/options",
  "/system/hr/onboarding/config/{id}", "/system/hr/onboarding/config/{id}/disable"
]) assert.ok(onboardingApi.includes(fragment), `position config API missing ${fragment}`)
for (const field of [
  "postId", "employeeCategory", "roleIds", "dataScopeStrategy", "contractTypeMode",
  "defaultContractType", "socialTypeMode", "defaultSocialType", "probationPeriodMode",
  "defaultProbationPeriod", "jobGrade", "accountEnabled", "status", "version"
]) assert.ok(position.includes(field), `position config page must support ${field}`)
for (const mode of ["REQUIRED", "OPTIONAL", "NOT_APPLICABLE"]) assert.ok(position.includes(mode), `rule mode ${mode} must be explicit`)
assert.ok(position.includes("POSITION_CONFIG_VERSION_CONFLICT"), "version conflicts must be handled")
assert.ok(position.includes("getHrOnboardingPositionConfigOptions"), "page must use onboarding config options")
assert.ok(!position.includes("@/api/system/"), "position config must not depend on system-management APIs")
assert.ok(!position.includes("system:"), "position config must not depend on system-management permissions")
assert.ok(position.includes("hr:onboarding:config"), "position config actions must use the onboarding config permission")
for (const binding of [':close-on-click-modal="!saving"', ':close-on-press-escape="!saving"', ':show-close="!saving"']) {
  assert.ok(position.includes(binding), `write dialog must lock ${binding} while saving`)
}

assert.ok(sql.includes("'hr/positionConfig/index', 'HrPositionConfig'"))
assert.ok(sql.includes("'hr:onboarding:config'"))
assert.strictEqual(sql, dockerSql, "menu SQL mirrors must remain identical")
const permissions = onboarding.match(/["']hr:[^"']+["']/g) || []
assert.ok(permissions.length, "onboarding page should declare onboarding permissions")
assert.ok(permissions.every(token => token.includes("hr:onboarding:") || token === '"hr:employee:query"'),
  "onboarding page may use only onboarding permissions plus optional linked-employee coverage read")
assert.ok(permissions.includes('"hr:employee:query"'), "linked employee coverage must require its exact read permission")

function loadApi(request) {
  const source = onboardingApi
    .replace(/import request from ["'][^"']+["']\s*/, "")
    .replace(/export const /g, "const ")
  const sandbox = { module: { exports: {} }, request, require: name => { assert.strictEqual(name, "@/utils/positiveDecimalId"); return require("../src/utils/positiveDecimalId") } }
  vm.runInNewContext(`${source}\nmodule.exports = { listHrOnboardingPositionConfigs, getHrOnboardingPositionConfig, createHrOnboardingPositionConfig, updateHrOnboardingPositionConfig, disableHrOnboardingPositionConfig, getHrOnboardingPositionConfigOptions }`, sandbox)
  return sandbox.module.exports
}

const calls = []
const api = loadApi(config => { calls.push(config); return config })
api.listHrOnboardingPositionConfigs({ pageNum: 2 })
api.getHrOnboardingPositionConfig(9)
api.createHrOnboardingPositionConfig({ postId: 3 })
api.updateHrOnboardingPositionConfig(9, { version: 4 })
api.disableHrOnboardingPositionConfig(9, 4)
api.getHrOnboardingPositionConfigOptions()
assert.deepStrictEqual(JSON.parse(JSON.stringify(calls)), [
  { url: "/system/hr/onboarding/config/list", method: "get", params: { pageNum: 2 } },
  { url: "/system/hr/onboarding/config/9", method: "get" },
  { url: "/system/hr/onboarding/config", method: "post", data: { postId: 3 }, silentError: true },
  { url: "/system/hr/onboarding/config/9", method: "put", data: { version: 4 }, silentError: true },
  { url: "/system/hr/onboarding/config/9/disable", method: "post", data: { version: 4 }, silentError: true },
  { url: "/system/hr/onboarding/config/options", method: "get" }
])

function loadSfc(relative, globals) {
  const match = read(relative).match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match)
  const source = match[1]
    .replace(/import\s+{[\s\S]*?}\s+from\s+["'][^"']+["']\s*/g, "")
    .replace(/import\s+[\w]+\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = { module: { exports: {} }, setTimeout, clearTimeout, ...globals }
  vm.runInNewContext(source, sandbox, { filename: relative })
  return sandbox.module.exports
}

function bind(component, target) {
  Object.entries(component.methods || {}).forEach(([name, fn]) => { target[name] = fn.bind(target) })
  Object.entries(component.computed || {}).forEach(([name, fn]) => {
    Object.defineProperty(target, name, { configurable: true, get: fn.bind(target) })
  })
  return target
}

const completenessCalls = []
const completenessComponent = loadSfc("src/views/hr/completeness/index.vue", {
  getHrCompletenessSummary: params => { completenessCalls.push(["summary", params]); return Promise.resolve({ data: {} }) },
  listHrCompletenessEmployees: params => { completenessCalls.push(["employees", params]); return Promise.resolve({ rows: [], total: 0 }) },
  getHrCompletenessDepartments: params => { completenessCalls.push(["departments", params]); return Promise.resolve({ data: [] }) }
})
const completenessTarget = bind(completenessComponent, {
  ...completenessComponent.data(),
  $router: { push() {} }
})
completenessTarget.activeTab = "accountRisk"
completenessTarget.loadData()
assert.strictEqual(completenessCalls[0][1].accountConfigurationStatus, "MISSING", "risk summary query must be server-scoped")
assert.strictEqual(completenessCalls[1][1].accountConfigurationStatus, "MISSING", "risk rows query must be server-scoped")

const configCalls = []
const latest = { configId: 9, version: 5, contractTypeMode: "OPTIONAL", socialTypeMode: "NOT_APPLICABLE", probationPeriodMode: "REQUIRED" }
const positionComponent = loadSfc("src/views/hr/positionConfig/index.vue", {
  listHrOnboardingPositionConfigs: () => Promise.resolve({ rows: [], total: 0 }),
  getHrOnboardingPositionConfig: id => { configCalls.push(["detail", id]); return Promise.resolve({ data: latest }) },
  createHrOnboardingPositionConfig: payload => { configCalls.push(["create", payload]); return Promise.resolve({ data: payload }) },
  updateHrOnboardingPositionConfig: (id, payload) => {
    configCalls.push(["update", id, payload])
    return Promise.reject({ response: { data: { errorCode: "POSITION_CONFIG_VERSION_CONFLICT" } } })
  },
  disableHrOnboardingPositionConfig: () => Promise.resolve(),
  getHrOnboardingPositionConfigOptions: () => Promise.resolve({ data: {} })
})
const base = { $message: { success() {}, warning() {}, error() {} }, $refs: { form: { validate(done) { done(true) } } } }
const positionTarget = bind(positionComponent, { ...base, ...positionComponent.data.call(base) })
positionTarget.dialogVisible = true
positionTarget.form = {
  configId: 9, postId: 3, employeeCategory: "FULL_TIME", roleIds: [2], dataScopeStrategy: "TARGET_DEPT",
  contractTypeMode: "OPTIONAL", defaultContractType: "FIXED", socialTypeMode: "NOT_APPLICABLE",
  defaultSocialType: "SHOULD_BE_CLEARED", probationPeriodMode: "REQUIRED", defaultProbationPeriod: "THREE_MONTHS",
  jobGrade: "P5", accountEnabled: true, status: "0", version: 4, remark: ""
}
const payload = positionTarget.buildConfigPayload()
assert.strictEqual(payload.contractTypeMode, "OPTIONAL")
assert.strictEqual(payload.socialTypeMode, "NOT_APPLICABLE")
assert.strictEqual(payload.defaultSocialType, null, "not-applicable mode must be persisted and clear only its default")
assert.strictEqual(payload.probationPeriodMode, "REQUIRED")
assert.strictEqual(payload.version, 4)
function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

const flush = () => new Promise(resolve => setImmediate(resolve))

async function testPositionAsyncBoundaries() {
  await positionTarget.submit()
  assert.ok(configCalls.some(call => call[0] === "detail" && call[1] === 9), "version conflict must refresh latest detail")
  assert.strictEqual(positionTarget.form.version, 5, "version conflict refresh must replace the stale version")

  const listOne = deferred(); const listTwo = deferred()
  const detailOne = deferred(); const detailTwo = deferred()
  const optionsOne = deferred(); const optionsTwo = deferred()
  const save = deferred()
  let listIndex = 0; let optionsIndex = 0; let saveCalls = 0
  const component = loadSfc("src/views/hr/positionConfig/index.vue", {
    listHrOnboardingPositionConfigs: () => [listOne, listTwo][listIndex++].promise,
    getHrOnboardingPositionConfig: id => (id === 1 ? detailOne : detailTwo).promise,
    createHrOnboardingPositionConfig: () => Promise.resolve(),
    updateHrOnboardingPositionConfig: () => { saveCalls += 1; return save.promise },
    disableHrOnboardingPositionConfig: () => Promise.resolve(),
    getHrOnboardingPositionConfigOptions: () => [optionsOne, optionsTwo][optionsIndex++].promise
  })
  const runtimeBase = { $message: { success() {}, warning() {}, error() {} }, $refs: { form: { validate(done) { done(true) } } } }
  const target = bind(component, { ...runtimeBase, ...component.data.call(runtimeBase) })

  target.loadList(); target.loadList()
  listTwo.resolve({ rows: [{ configId: 2 }], total: 1 }); await flush()
  listOne.resolve({ rows: [{ configId: 1 }], total: 1 }); await flush()
  assert.strictEqual(target.rows[0].configId, 2, "stale list responses must not replace the latest page")

  target.loadOptions(); target.loadOptions()
  optionsTwo.resolve({ data: { posts: [{ value: 2 }] } }); await flush()
  optionsOne.resolve({ data: { posts: [{ value: 1 }] } }); await flush()
  assert.strictEqual(target.options.posts[0].value, 2, "stale options responses must not replace current options")

  target.openDetail({ configId: 1 }); target.openDetail({ configId: 2 })
  detailTwo.resolve({ data: { configId: 2, version: 2 } }); await flush()
  detailOne.resolve({ data: { configId: 1, version: 1 } }); await flush()
  assert.strictEqual(target.detail.configId, 2, "stale detail responses must not replace the active detail")

  target.form = { ...target.form, configId: 2, version: 2, roleIds: [] }
  const firstSave = target.submit(); const secondSave = target.submit()
  await flush()
  assert.strictEqual(saveCalls, 1, "saving state must prevent duplicate writes")
  save.resolve({ data: {} }); await Promise.all([firstSave, secondSave])

  const pushed = []
  const linkTarget = bind(completenessComponent, {
    ...completenessComponent.data(), $store: { getters: { permissions: ["*:*:*"] } },
    $router: { push(url) { pushed.push(url) } }
  })
  for (const url of [
    "/hr/employee?userId=7", "/hr/onboarding?onboardingId=9", "/hr/position-config?postId=3",
    "//evil.example/hr/employee", "/system/user", "/hr/employee-evil?userId=7", "https://evil.example"
  ]) linkTarget.openSafeLink(url)
  assert.deepStrictEqual(pushed, [
    "/hr/employee?userId=7", "/hr/onboarding?onboardingId=9", "/hr/position-config?postId=3"
  ], "only exact expected HR route prefixes may be opened")

  const permissionTarget = permissions => bind(completenessComponent, {
    ...completenessComponent.data(), $store: { getters: { permissions } }, $router: { push(url) { pushed.push(url) } }
  })
  const employeeOrOnly = permissionTarget(["hr:employee:list"])
  assert.strictEqual(employeeOrOnly.canOpenEmployeeLink, false, "employee list permission alone must not expose the deep link")
  employeeOrOnly.openSafeLink("/hr/employee?userId=7")
  const onboardingOrOnly = permissionTarget(["hr:onboarding:query"])
  assert.strictEqual(onboardingOrOnly.canOpenOnboardingLink, false, "onboarding query permission alone must not expose the deep link")
  onboardingOrOnly.openSafeLink("/hr/onboarding?onboardingId=9")
  assert.strictEqual(pushed.length, 3, "OR-only permission holders must not activate protected links")
  const allPermissions = permissionTarget(["hr:employee:list", "hr:employee:query", "hr:onboarding:list", "hr:onboarding:query", "hr:onboarding:config"])
  assert.strictEqual(allPermissions.canOpenEmployeeLink, true)
  assert.strictEqual(allPermissions.canOpenOnboardingLink, true)
  assert.strictEqual(allPermissions.canOpenPositionConfigLink, true)
  allPermissions.openSafeLink("/hr/onboarding?onboardingId=10")
  assert.strictEqual(pushed[pushed.length - 1], "/hr/onboarding?onboardingId=10", "all required permissions must activate the link")
  const wildcard = permissionTarget(["*:*:*"])
  assert.strictEqual(wildcard.canOpenEmployeeLink, true)
  wildcard.openSafeLink("/hr/employee?userId=8")
  assert.strictEqual(pushed[pushed.length - 1], "/hr/employee?userId=8", "wildcard permission must pass all-permission gating")

  const completenessFailure = deferred()
  const failedCompleteness = loadSfc("src/views/hr/completeness/index.vue", {
    getHrCompletenessSummary: () => completenessFailure.promise,
    listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 }),
    getHrCompletenessDepartments: () => Promise.resolve({ data: [] })
  })
  const failedCompletenessTarget = bind(failedCompleteness, {
    ...failedCompleteness.data(), summary: { totalEmployeeCount: 99 }, rows: [{ userId: 1 }], total: 1,
    $message: { error() {} }, $router: { push() {} }
  })
  const completenessPending = failedCompletenessTarget.loadData()
  completenessFailure.reject(new Error("failed")); await completenessPending
  assert.deepStrictEqual(JSON.parse(JSON.stringify(failedCompletenessTarget.summary)), {}, "current completeness failure must clear stale summary")
  assert.strictEqual(failedCompletenessTarget.rows.length, 0); assert.strictEqual(failedCompletenessTarget.total, 0)

  let routeListCalls = 0
  const routeComponent = loadSfc("src/views/hr/positionConfig/index.vue", {
    listHrOnboardingPositionConfigs: () => { routeListCalls += 1; return Promise.resolve({ rows: [], total: 0 }) },
    getHrOnboardingPositionConfig: () => Promise.resolve({ data: {} }), createHrOnboardingPositionConfig: () => Promise.resolve(),
    updateHrOnboardingPositionConfig: () => Promise.resolve(), disableHrOnboardingPositionConfig: () => Promise.resolve(),
    getHrOnboardingPositionConfigOptions: () => Promise.resolve({ data: {} })
  })
  const routeBase = { $route: { query: { postId: "3", employeeCategory: "FULL_TIME" } }, $message: { error() {}, warning() {}, success() {} } }
  const routeTarget = bind(routeComponent, { ...routeBase, ...routeComponent.data.call(routeBase) })
  routeTarget.applyRouteFilters(); await routeTarget.loadList()
  routeComponent.activated.call(routeTarget)
  await flush()
  assert.strictEqual(routeListCalls, 1, "initial keep-alive activation must not double-load")
  routeTarget.$route = { query: { postId: "4", employeeCategory: "PART_TIME" } }
  await routeComponent.watch["$route.query"].handler.call(routeTarget)
  assert.strictEqual(routeTarget.query.pageNum, 1)
  assert.strictEqual(routeTarget.query.postId, 4); assert.strictEqual(routeTarget.query.employeeCategory, "PART_TIME")
  assert.strictEqual(routeListCalls, 2, "route reuse must reload once with validated filters")
  routeTarget.query.postId = undefined; routeTarget.query.employeeCategory = undefined
  await routeComponent.activated.call(routeTarget)
  assert.strictEqual(routeTarget.query.postId, 4); assert.strictEqual(routeTarget.query.employeeCategory, "PART_TIME")
  assert.strictEqual(routeListCalls, 3, "same deep-link activation must restore manually reset route filters")
  routeTarget.$route = { query: {} }; await routeComponent.watch["$route.query"].handler.call(routeTarget)
  routeTarget.query.postId = 99; routeTarget.query.employeeCategory = "MANUAL"
  await routeComponent.activated.call(routeTarget)
  assert.strictEqual(routeTarget.query.postId, 99); assert.strictEqual(routeTarget.query.employeeCategory, "MANUAL")
  assert.strictEqual(routeListCalls, 4, "no-deep-link activation must not override normal manual filters")

  const failureComponent = loadSfc("src/views/hr/positionConfig/index.vue", {
    listHrOnboardingPositionConfigs: () => Promise.reject(new Error("list failed")),
    getHrOnboardingPositionConfig: () => Promise.reject(new Error("detail failed")),
    createHrOnboardingPositionConfig: () => Promise.resolve(),
    updateHrOnboardingPositionConfig: () => Promise.reject({ response: { data: { errorCode: "POSITION_CONFIG_VERSION_CONFLICT" } } }),
    disableHrOnboardingPositionConfig: () => Promise.resolve(),
    getHrOnboardingPositionConfigOptions: () => Promise.reject(new Error("options failed"))
  })
  const messages = []
  const failureBase = { $route: { query: {} }, $message: { error(value) { messages.push(value) }, warning() {}, success() {} }, $refs: { form: { validate(done) { done(true) } } } }
  const failureTarget = bind(failureComponent, { ...failureBase, ...failureComponent.data.call(failureBase), rows: [{ configId: 1 }], total: 1, options: { posts: [1] }, detail: { configId: 1 } })
  await failureTarget.loadList(); await failureTarget.loadOptions(); await failureTarget.openDetail({ configId: 1 })
  assert.strictEqual(failureTarget.rows.length, 0); assert.strictEqual(failureTarget.total, 0)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(failureTarget.options)), {})
  assert.strictEqual(failureTarget.detail, null)
  failureTarget.form = { ...failureTarget.form, configId: 1, version: 1, roleIds: [] }
  await failureTarget.submit()
  assert.ok(failureTarget.conflictMessage.includes("刷新失败"), "version-conflict refresh GET failure needs a clear state")
  assert.ok(messages.length >= 2)

  let editCalls = 0
  const optionsFailure = deferred(); const optionsSuccess = deferred()
  let optionAttempt = 0
  const guardedComponent = loadSfc("src/views/hr/positionConfig/index.vue", {
    listHrOnboardingPositionConfigs: () => Promise.resolve({ rows: [], total: 0 }),
    getHrOnboardingPositionConfig: () => { editCalls += 1; return Promise.resolve({ data: {} }) },
    createHrOnboardingPositionConfig: () => Promise.resolve(), updateHrOnboardingPositionConfig: () => Promise.resolve(),
    disableHrOnboardingPositionConfig: () => Promise.resolve(),
    getHrOnboardingPositionConfigOptions: () => [optionsFailure, optionsSuccess][optionAttempt++].promise
  })
  const guardedBase = { $route: { query: {} }, $message: { error(value) { messages.push(value) }, warning(value) { messages.push(value) }, success() {} } }
  const guarded = bind(guardedComponent, { ...guardedBase, ...guardedComponent.data.call(guardedBase) })
  const failedOptionsRequest = guarded.loadOptions(); optionsFailure.reject(new Error("options failed")); await failedOptionsRequest
  assert.strictEqual(guarded.optionsReady, false); assert.ok(guarded.optionsError.includes("重试"))
  guarded.openCreate(); await guarded.openEdit({ configId: 1 })
  assert.strictEqual(guarded.dialogVisible, false); assert.strictEqual(editCalls, 0, "programmatic create/edit must be blocked before options succeed")
  const retry = guarded.loadOptions(); assert.strictEqual(guarded.optionsError, "", "retry must clear the stale options error")
  optionsSuccess.resolve({ data: { roles: [] } }); await retry
  assert.strictEqual(guarded.optionsReady, true, "an empty but successfully loaded role list is valid")
  guarded.openCreate(); assert.strictEqual(guarded.dialogVisible, true)
}

testPositionAsyncBoundaries().then(() => {
  console.log("hrMenuPermissions tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
