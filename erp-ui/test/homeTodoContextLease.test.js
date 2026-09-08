const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const homePath = path.resolve(__dirname, "../src/views/index.vue")
const homeSource = fs.readFileSync(homePath, "utf8")
const { navigateTodo } = require("../src/utils/todoNavigator")

assert.ok(
  homeSource.includes("beginContextLease: beginTodoContextLease") &&
    homeSource.includes("rollbackContextLease") &&
    homeSource.includes("getReturnRoute: () => this.$route") &&
    homeSource.includes("clearSelectedDept") &&
    homeSource.includes("formatTodoContextSwitchNotice"),
  "home recommendations must use the same rollback-capable context lease as HeaderTodo"
)

function loadHomeComponent(overrides) {
  const scriptMatch = homeSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "home component script should exist")
  const script = scriptMatch[1]
    .replace(/^import(?:[\s\S]*?)\s+from\s+['"][^'"]+['"]\s*$/gm, "")
    .replace(/export default/, "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    mapGetters: () => ({}),
    constantRoutes: [],
    buildAvailableRouteSet: () => new Set(),
    visibleDashboardEntries: entries => entries,
    window: { location: { reload() {} } },
    Promise,
    Object,
    Array,
    Set,
    String,
    Number,
    Date,
    Math,
    ...overrides
  }
  vm.runInNewContext(script, sandbox, { filename: homePath })
  return sandbox.module.exports
}

function todoItem(businessId) {
  return {
    source: "inventory",
    type: "INV_TRANSFER_APPROVAL",
    businessId,
    contextDeptId: "warehouse-2",
    contextDeptName: "新仓库",
    contextDeptType: "WAREHOUSE"
  }
}

function createHarness({ rejectPush = false } = {}) {
  const originContext = { deptId: "store-1", deptName: "旧门店", deptType: "STORE" }
  let currentContext = { ...originContext }
  let leasePayload = null
  let rollbackCount = 0
  const pushes = []
  const notices = []
  const errors = []
  const dispatches = []
  const route = { path: "/index", fullPath: "/index?from=home" }

  const component = loadHomeComponent({
    navigateTodo,
    getSelectedDeptContext: () => currentContext,
    setSelectedDept: (deptId, deptName, deptType) => {
      currentContext = { deptId: String(deptId), deptName, deptType }
    },
    clearSelectedDept: () => {
      currentContext = {}
    },
    beginTodoContextLease: payload => {
      leasePayload = payload
      return { ok: true }
    },
    rollbackTodoContextLease: options => {
      rollbackCount += 1
      if (leasePayload && leasePayload.originContext && leasePayload.originContext.deptId) {
        const origin = leasePayload.originContext
        options.setSelectedDept(origin.deptId, origin.deptName, origin.deptType)
      } else {
        options.clearSelectedDept()
      }
      return { ok: true, restored: true }
    },
    formatTodoContextSwitchNotice: payload => `临时切换到${payload.targetContext.deptName}`
  })
  const context = {
    permissions: [],
    todoAvailableRouteSet: new Set(["/inventory/transfer"]),
    $route: route,
    $router: {
      push(location) {
        pushes.push(location)
        return rejectPush
          ? Promise.reject(new Error("router push rejected"))
          : Promise.resolve()
      }
    },
    $message: {
      info(message) { notices.push(message) },
      error(message) { errors.push(message) }
    },
    $store: {
      dispatch(action) {
        dispatches.push(action)
        return Promise.resolve()
      }
    }
  }

  return {
    component,
    context,
    dispatches,
    errors,
    getCurrentContext: () => currentContext,
    getLeasePayload: () => leasePayload,
    getRollbackCount: () => rollbackCount,
    notices,
    originContext,
    pushes,
    route
  }
}

;(async () => {
  const success = createHarness()
  const successResult = await success.component.methods.handleGuide.call(
    success.context,
    { todo: todoItem(81) }
  )
  assert.strictEqual(successResult.ok, true)
  assert.strictEqual(success.pushes.length, 1, "successful recommendation should push once")
  assert.strictEqual(success.pushes[0].path, "/inventory/transfer")
  assert.strictEqual(success.getCurrentContext().deptId, "warehouse-2",
    "successful navigation should keep the temporary target context until returning")
  assert.strictEqual(success.getLeasePayload().returnRoute, success.route,
    "the home route should be captured as the lease return route")
  assert.strictEqual(success.notices.length, 1, "successful cross-organization navigation should explain the switch")
  assert.ok(success.notices[0].includes("新仓库"))
  assert.strictEqual(success.getRollbackCount(), 0)
  assert.deepStrictEqual(success.dispatches, [])

  const failed = createHarness({ rejectPush: true })
  const failedResult = await failed.component.methods.handleGuide.call(
    failed.context,
    { todo: todoItem(82) }
  )
  assert.deepStrictEqual(failedResult, { ok: false, reason: "route_missing" })
  assert.strictEqual(failed.getRollbackCount(), 1, "a rejected push should roll back the context lease")
  assert.strictEqual(failed.getCurrentContext().deptId, failed.originContext.deptId,
    "a rejected push must restore the source organization")
  assert.strictEqual(failed.notices.length, 0, "a failed navigation must not announce a successful switch")
  assert.strictEqual(failed.errors.length, 1)
  assert.deepStrictEqual(failed.dispatches, ["todo/refreshSummaries"])

  console.log("home todo context lease tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
