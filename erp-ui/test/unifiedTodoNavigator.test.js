const assert = require("assert")

const { navigateTodo } = require("../src/utils/todoNavigator")

const routes = new Set([
  "/inventory/transfer", "/inventory/stockCheck", "/mobile/contract"
])

async function run() {
  const crossOrgCases = [
    ["inventory", "INV_TRANSFER_APPROVAL", "/inventory/transfer"],
    ["inventory", "INV_STOCK_CHECK_APPROVAL", "/inventory/stockCheck"]
  ]

  for (const [source, type, expectedPath] of crossOrgCases) {
    let context = { deptId: 1, deptName: "旧门店", deptType: "STORE" }
    let switches = 0
    let pushed = null
    let permissionsReads = 0
    let routeReads = 0
    let leaseStarts = 0
    let notices = 0
    const result = await navigateTodo({
      source,
      type,
      businessId: 88,
      requiredPermission: "todo:approve",
      contextDeptId: 2,
      contextDeptName: "新仓库",
      contextDeptType: "WAREHOUSE"
    }, {
      platform: "desktop",
      getPermissions: () => { permissionsReads += 1; return ["todo:approve"] },
      getAvailableRouteSet: () => { routeReads += 1; return routes },
      getCurrentContext: () => context,
      setSelectedDept: (deptId, deptName, deptType) => {
        switches += 1
        context = { deptId, deptName, deptType }
      },
      getReturnRoute: () => ({ path: "/workbench/todo", fullPath: "/workbench/todo" }),
      beginContextLease: () => { leaseStarts += 1; return { ok: true } },
      rollbackContextLease: () => { throw new Error("success must not roll back") },
      showContextNotice: () => { notices += 1 },
      router: { push: location => { pushed = location } },
      refreshSummaries: () => { throw new Error("success must not refresh summaries") }
    })
    assert.strictEqual(result.ok, true)
    assert.strictEqual(pushed.path, expectedPath)
    assert.strictEqual(switches, 1, `${type} should switch context exactly once`)
    assert.strictEqual(leaseStarts, 1, `${type} should preserve the source organization before switching`)
    assert.strictEqual(notices, 1, `${type} should explain that the organization switch is temporary`)
    assert.ok(permissionsReads >= 2, "permissions must be checked again after context switch")
    assert.ok(routeReads >= 2, "available routes must be checked again after context switch")
  }

  let switches = 0
  await navigateTodo({
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 1,
    contextDeptId: 2, contextDeptName: "当前仓", contextDeptType: "WAREHOUSE"
  }, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    currentContext: { deptId: "2", deptName: "当前仓", deptType: "WAREHOUSE" },
    setSelectedDept: () => { switches += 1 }, router: { push: () => {} }
  })
  assert.strictEqual(switches, 0, "same context must not switch")

  await navigateTodo({
    source: "oa", type: "OA_LABOR_CONTRACT_SIGN", businessId: 1
  }, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    currentContext: { deptId: 1, deptName: "当前门店", deptType: "STORE" },
    setSelectedDept: () => { switches += 1 }, router: { push: () => {} }
  })
  assert.strictEqual(switches, 0, "personal signing must not switch")

  let refreshed = 0
  let message = ""
  let pushed = 0
  const denied = await navigateTodo({
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 1,
    requiredPermission: "todo:approve",
    contextDeptId: 1, contextDeptName: "门店", contextDeptType: "STORE"
  }, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    currentContext: { deptId: 1, deptName: "门店", deptType: "STORE" },
    router: { push: () => { pushed += 1 } },
    showError: text => { message = text },
    refreshSummaries: () => { refreshed += 1 }
  })
  assert.deepStrictEqual(denied, { ok: false, reason: "permission_changed" })
  assert.strictEqual(pushed, 0)
  assert.strictEqual(refreshed, 1)
  assert.ok(message.includes("权限"))

  let routeRead = 0
  const staleRoute = await navigateTodo({
    source: "inventory", type: "INV_STOCK_CHECK_APPROVAL", businessId: 2,
    contextDeptId: 2, contextDeptName: "新仓", contextDeptType: "WAREHOUSE"
  }, {
    platform: "desktop", permissions: ["todo:approve"],
    getAvailableRouteSet: () => { routeRead += 1; return routeRead === 1 ? routes : new Set() },
    getCurrentContext: () => ({ deptId: 2, deptName: "新仓", deptType: "WAREHOUSE" }),
    router: { push: () => { pushed += 1 } },
    refreshSummaries: () => { refreshed += 1 }
  })
  assert.deepStrictEqual(staleRoute, { ok: false, reason: "route_missing" })
  assert.strictEqual(refreshed, 2)

  let resolvedErrorRefreshes = 0
  let resolvedErrorMessage = ""
  const resolvedPushError = await navigateTodo({
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 3,
    contextDeptId: 1, contextDeptName: "门店", contextDeptType: "STORE"
  }, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    currentContext: { deptId: 1, deptName: "门店", deptType: "STORE" },
    router: { push: () => Promise.resolve(new Error("wrapped router failure")) },
    showError: text => { resolvedErrorMessage = text },
    refreshSummaries: () => { resolvedErrorRefreshes += 1 }
  })
  assert.deepStrictEqual(resolvedPushError, { ok: false, reason: "route_missing" })
  assert.strictEqual(resolvedErrorRefreshes, 1)
  assert.ok(resolvedErrorMessage.includes("不可用"))

  let setterFailureRefreshes = 0
  const setterFailureOptions = {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    getCurrentContext: () => ({ deptId: 1, deptName: "旧门店", deptType: "STORE" }),
    getReturnRoute: () => ({ path: "/workbench/todo", fullPath: "/workbench/todo" }),
    beginContextLease: () => ({ ok: true }),
    rollbackContextLease: () => ({ ok: true }),
    router: { push: () => { throw new Error("must not push") } },
    refreshSummaries: () => { setterFailureRefreshes += 1 }
  }
  const crossOrgItem = {
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 8,
    contextDeptId: 2, contextDeptName: "新仓", contextDeptType: "WAREHOUSE"
  }
  const thrownSetter = await navigateTodo(crossOrgItem, {
    ...setterFailureOptions,
    setSelectedDept: () => { throw new Error("storage unavailable") }
  })
  assert.deepStrictEqual(thrownSetter, { ok: false, reason: "organization_required" })
  assert.strictEqual(setterFailureRefreshes, 1)

  let asyncContext = { deptId: 1, deptName: "旧门店", deptType: "STORE" }
  const asyncSetter = await navigateTodo(crossOrgItem, {
    ...setterFailureOptions,
    getCurrentContext: () => asyncContext,
    setSelectedDept: async (deptId, deptName, deptType) => {
      asyncContext = { deptId, deptName, deptType }
    }
  })
  assert.deepStrictEqual(asyncSetter, { ok: false, reason: "organization_required" }, "async context setters are not navigation-safe")

  const staleGetter = await navigateTodo(crossOrgItem, {
    ...setterFailureOptions,
    setSelectedDept: () => {}
  })
  assert.deepStrictEqual(staleGetter, { ok: false, reason: "organization_required" }, "no-op context switch must be detected")

  let unsafeSwitches = 0
  const unsafeLease = await navigateTodo(crossOrgItem, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    getCurrentContext: () => ({ deptId: 1, deptName: "旧门店", deptType: "STORE" }),
    setSelectedDept: () => { unsafeSwitches += 1 },
    router: { push: () => { throw new Error("must not push") } }
  })
  assert.deepStrictEqual(unsafeLease, { ok: false, reason: "context_lease_unavailable" })
  assert.strictEqual(unsafeSwitches, 0, "cross-organization navigation must not switch without rollback-capable lease hooks")

  let rejectedLeaseSwitches = 0
  const rejectedLease = await navigateTodo(crossOrgItem, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    getCurrentContext: () => ({ deptId: 1, deptName: "旧门店", deptType: "STORE" }),
    getReturnRoute: () => ({ path: "/workbench/todo", fullPath: "/workbench/todo" }),
    beginContextLease: () => ({ ok: false, reason: "storage_unavailable" }),
    rollbackContextLease: () => ({ ok: true }),
    setSelectedDept: () => { rejectedLeaseSwitches += 1 },
    router: { push: () => { throw new Error("must not push") } }
  })
  assert.deepStrictEqual(rejectedLease, { ok: false, reason: "context_lease_unavailable" })
  assert.strictEqual(rejectedLeaseSwitches, 0)

  let rollbackContext = { deptId: 1, deptName: "旧门店", deptType: "STORE" }
  let rollbacks = 0
  const failedAfterSwitch = await navigateTodo(crossOrgItem, {
    platform: "desktop", permissions: [],
    getAvailableRouteSet: (() => {
      let reads = 0
      return () => { reads += 1; return reads === 1 ? routes : new Set() }
    })(),
    getCurrentContext: () => rollbackContext,
    getReturnRoute: () => ({ path: "/workbench/todo", fullPath: "/workbench/todo" }),
    beginContextLease: () => ({ ok: true }),
    rollbackContextLease: () => {
      rollbacks += 1
      rollbackContext = { deptId: 1, deptName: "旧门店", deptType: "STORE" }
      return { ok: true }
    },
    setSelectedDept: (deptId, deptName, deptType) => {
      rollbackContext = { deptId, deptName, deptType }
    },
    router: { push: () => { throw new Error("must not push after route recheck") } }
  })
  assert.deepStrictEqual(failedAfterSwitch, { ok: false, reason: "route_missing" })
  assert.strictEqual(rollbacks, 1)
  assert.strictEqual(rollbackContext.deptId, 1, "a post-switch route failure should restore the source organization")

  let rejectedPushContext = { deptId: 1, deptName: "旧门店", deptType: "STORE" }
  let rejectedPushRollbacks = 0
  const rejectedPush = await navigateTodo({ ...crossOrgItem, businessId: 81 }, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    getCurrentContext: () => rejectedPushContext,
    getReturnRoute: () => ({ path: "/workbench/todo", fullPath: "/workbench/todo" }),
    beginContextLease: () => ({ ok: true }),
    rollbackContextLease: () => {
      rejectedPushRollbacks += 1
      rejectedPushContext = { deptId: 1, deptName: "旧门店", deptType: "STORE" }
      return { ok: true }
    },
    setSelectedDept: (deptId, deptName, deptType) => {
      rejectedPushContext = { deptId, deptName, deptType }
    },
    router: { push: () => Promise.reject(new Error("router rejected")) }
  })
  assert.deepStrictEqual(rejectedPush, { ok: false, reason: "route_missing" })
  assert.strictEqual(rejectedPushRollbacks, 1)
  assert.strictEqual(rejectedPushContext.deptId, 1,
    "a rejected router transition should not leave the target organization selected")

  const refreshReject = await navigateTodo({
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 9,
    requiredPermission: "oa:approve",
    contextDeptId: 1, contextDeptName: "门店", contextDeptType: "STORE"
  }, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    currentContext: { deptId: 1, deptName: "门店", deptType: "STORE" },
    showError: () => { throw new Error("message layer unavailable") },
    refreshSummaries: () => Promise.reject(new Error("refresh unavailable")),
    router: { push: () => {} }
  })
  assert.deepStrictEqual(refreshReject, { ok: false, reason: "permission_changed" }, "failure reporting must preserve its original reason")

  let duplicatedRefreshes = 0
  const navigationDuplicated = new Error("Avoided redundant navigation")
  navigationDuplicated.name = "NavigationDuplicated"
  const duplicateNavigation = await navigateTodo({
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 3,
    contextDeptId: 1, contextDeptName: "门店", contextDeptType: "STORE"
  }, {
    platform: "desktop", permissions: [], availableRouteSet: routes,
    currentContext: { deptId: 1, deptName: "门店", deptType: "STORE" },
    router: { push: () => Promise.resolve(navigationDuplicated) },
    refreshSummaries: () => { duplicatedRefreshes += 1 }
  })
  assert.strictEqual(duplicateNavigation.ok, true, "resolved NavigationDuplicated is a successful no-op")
  assert.strictEqual(duplicatedRefreshes, 0)

  let duplicateReports = 0
  let duplicateRefreshes = 0
  let releaseRefresh
  const pendingRefresh = new Promise(resolve => { releaseRefresh = resolve })
  const unavailableItem = {
    todoKey: "inventory:approval:duplicate",
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 10,
    contextDeptId: 1, contextDeptName: "门店", contextDeptType: "STORE"
  }
  const unavailableOptions = {
    platform: "desktop", permissions: [], availableRouteSet: new Set(),
    currentContext: { deptId: 1, deptName: "门店", deptType: "STORE" },
    router: { push: () => { throw new Error("unavailable routes must not push") } },
    showError: () => { duplicateReports += 1 },
    refreshSummaries: () => {
      duplicateRefreshes += 1
      return pendingRefresh
    }
  }
  const firstUnavailable = navigateTodo(unavailableItem, unavailableOptions)
  const secondUnavailable = navigateTodo(unavailableItem, unavailableOptions)
  await Promise.resolve()
  assert.strictEqual(duplicateReports, 1, "concurrent clicks for one todo must report one navigation failure")
  assert.strictEqual(duplicateRefreshes, 1, "concurrent clicks for one todo must request one summary refresh")
  releaseRefresh()
  const unavailableResults = await Promise.all([firstUnavailable, secondUnavailable])
  assert.deepStrictEqual(unavailableResults, [
    { ok: false, reason: "route_missing" },
    { ok: false, reason: "route_missing" }
  ])

  let concurrentContext = { deptId: 1, deptName: "原门店", deptType: "STORE" }
  let activeLeaseId = null
  let leaseSequence = 0
  const pendingPushes = new Map()
  const concurrentRouter = {
    push: location => new Promise((resolve, reject) => {
      pendingPushes.set(String(location.query.businessId), { resolve, reject })
    })
  }
  const concurrentOptions = {
    platform: "desktop",
    permissions: [],
    availableRouteSet: routes,
    getCurrentContext: () => concurrentContext,
    getReturnRoute: () => ({ path: "/workbench/todo", fullPath: "/workbench/todo" }),
    beginContextLease: payload => {
      activeLeaseId = `lease-${++leaseSequence}`
      return { ok: true, lease: { leaseId: activeLeaseId, target: payload.targetContext } }
    },
    rollbackContextLease: payload => {
      const leaseId = payload && payload.contextLease && payload.contextLease.leaseId
      if (!leaseId || leaseId === activeLeaseId) {
        concurrentContext = { deptId: 1, deptName: "原门店", deptType: "STORE" }
        activeLeaseId = null
      }
      return { ok: true }
    },
    setSelectedDept: (deptId, deptName, deptType) => {
      concurrentContext = { deptId, deptName, deptType }
    },
    router: concurrentRouter
  }
  const firstCrossOrg = navigateTodo({
    todoKey: "inventory:INV_TRANSFER_APPROVAL:101:approve",
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 101,
    contextDeptId: 2, contextDeptName: "门店A", contextDeptType: "STORE"
  }, concurrentOptions)
  await Promise.resolve()
  const secondCrossOrg = navigateTodo({
    todoKey: "inventory:INV_TRANSFER_APPROVAL:202:approve",
    source: "inventory", type: "INV_TRANSFER_APPROVAL", businessId: 202,
    contextDeptId: 3, contextDeptName: "门店B", contextDeptType: "STORE"
  }, concurrentOptions)
  await Promise.resolve()
  pendingPushes.get("101").reject(new Error("superseded by the second route"))
  const firstCrossOrgResult = await firstCrossOrg
  assert.deepStrictEqual(firstCrossOrgResult, { ok: false, reason: "route_missing" })
  pendingPushes.get("202").resolve()
  const secondCrossOrgResult = await secondCrossOrg
  assert.strictEqual(secondCrossOrgResult.ok, true)
  assert.strictEqual(concurrentContext.deptId, 3,
    "a cancelled navigation must not roll back the newer navigation's organization lease")
}

run().then(() => {
  console.log("unifiedTodoNavigator tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
