const assert = require("assert")
const fs = require("fs")
const path = require("path")
const babel = require("@babel/core")

const rootDir = path.resolve(__dirname, "..")
const permissionStorePath = path.join(rootDir, "src/store/modules/permission.js")
const guardPath = path.join(rootDir, "src/permission.js")

function transformModule(file) {
  return babel.transformSync(fs.readFileSync(file, "utf8"), {
    babelrc: false,
    configFile: false,
    plugins: ["@babel/plugin-transform-modules-commonjs"]
  }).code
}

function executeModule(code, mockRequire) {
  const module = { exports: {} }
  new Function("require", "module", "exports", code)(mockRequire, module, module.exports)
  return module.exports
}

function loadPermissionStore(getRouters, router) {
  const code = transformModule(permissionStorePath)
  return executeModule(code, id => {
    if (id === "@/plugins/auth") {
      return { __esModule: true, default: { hasPermiOr: () => false, hasRoleOr: () => false } }
    }
    if (id === "@/router") {
      return {
        __esModule: true,
        default: router,
        constantRoutes: [{ path: "/login" }],
        dynamicRoutes: []
      }
    }
    if (id === "@/api/menu") return { getRouters }
    if (id === "@/components/ParentView" || id === "@/layout/components/InnerLink") {
      return { __esModule: true, default: {} }
    }
    throw new Error(`Unexpected permission store import: ${id}`)
  }).default
}

function createGuardHarness(options = {}) {
  let beforeGuard
  let afterGuard
  const nextCalls = []
  const dispatchCalls = []
  const addedRoutes = []
  const progress = { starts: 0, dones: 0 }
  const messages = { errors: [], warnings: [] }
  let confirmCalls = 0
  const isRelogin = { show: false }
  const generateResults = (options.generateResults || []).slice()
  const confirmResults = (options.confirmResults || []).slice()
  const getters = {
    roles: (options.roles || ["admin"]).slice(),
    isLock: false,
    credentialState: options.credentialState || "ACTIVE",
    permissions: [],
    businessFeatures: {},
    driveEnabled: false,
    profileCompletionRequired: false
  }
  const router = {
    beforeEach(handler) { beforeGuard = handler },
    afterEach(handler) { afterGuard = handler },
    addRoutes(routes) { addedRoutes.push(routes) }
  }
  const store = {
    getters,
    dispatch(action) {
      dispatchCalls.push(action)
      if (action === "GetInfo") {
        if (options.getInfoError) return Promise.reject(options.getInfoError)
        getters.roles = ["admin"]
        if (options.getInfoCredentialState) {
          getters.credentialState = options.getInfoCredentialState
        }
        return Promise.resolve()
      }
      if (action === "GenerateRoutes") {
        const result = generateResults.shift()
        return result instanceof Error ? Promise.reject(result) : Promise.resolve(result || [])
      }
      if (action === "FedLogOut") return Promise.resolve()
      return Promise.resolve()
    }
  }
  const nprogress = {
    configure() {},
    start() { progress.starts += 1 },
    done() { progress.dones += 1 }
  }
  const MessageBox = {
    confirm() {
      confirmCalls += 1
      const result = confirmResults.shift()
      return result === true ? Promise.resolve() : Promise.reject(new Error("cancelled"))
    }
  }

  executeModule(transformModule(guardPath), id => {
    if (id === "@/services/lazyPushRegistration") {
      return { __esModule: true, default: { resumeNavigation: () => Promise.resolve() } }
    }
    if (id === "./router") return { __esModule: true, default: router }
    if (id === "./store") return { __esModule: true, default: store }
    if (id === "@/plugins/element-services") {
      return {
        Message: {
          error(error) { messages.errors.push(error) },
          warning(message) { messages.warnings.push(message) }
        },
        MessageBox
      }
    }
    if (id === "nprogress") return { __esModule: true, default: nprogress }
    if (id === "nprogress/nprogress.css") return {}
    if (id === "@/utils/auth") return { getToken: () => "token" }
    if (id === "@/utils/sessionMode") {
      return {
        getWebSessionStatus: () => "anonymous",
        hasSessionCandidate: token => !!token,
        isCookiePreferredSession: () => false
      }
    }
    if (id === "@/utils/todoContextLease") {
      return { clearTodoContextLease() {}, restoreTodoContextLeaseForRoute() {} }
    }
    if (id === "@/utils/shopContext") {
      return {
        clearSelectedDept() {},
        getSelectedDeptContext: () => ({ deptId: "1", deptType: "STORE" }),
        getSelectedDeptType: () => "STORE",
        hasSelectedDeptContext: () => true,
        hasValidatedSelectedDeptContext: () => true,
        setSelectedDept() {}
      }
    }
    if (id === "@/views/mobile/mobileNavigation") {
      return {
        getMobileRouteAccessInfo: path => ({ redirect: path }),
        getMobileHomePath: () => "/mobile/home",
        getMobileRouteFeature: () => ({ requiresBusinessContext: false }),
        isMobileContextOptionalPath: () => true,
        resolveMobileNavigationRedirect: payload => ({ redirect: payload.path })
      }
    }
    if (id === "@/utils/validate") return { isPathMatch: (pattern, target) => pattern === target }
    if (id === "@/utils/request") return { isRelogin }
    if (id === "@/utils/desktopContextPolicy") return { requiresInventoryContext: () => false }
    if (id === "@/utils/clientPlatform") {
      return {
        isMobileClient: () => options.mobileClient === true,
        isMobileRoutePath: path => String(path || "").startsWith("/mobile/")
      }
    }
    if (id === "@/utils/profileCompletion") {
      return {
        PROFILE_COMPLETION_PATH: "/profile-completion",
        isProfileCompletionPath: () => false,
        resolveProfileCompletionRedirect: () => null
      }
    }
    throw new Error(`Unexpected route guard import: ${id}`)
  })

  assert.strictEqual(typeof beforeGuard, "function", "permission module should register a before guard")
  return {
    getters,
    dispatchCalls,
    addedRoutes,
    progress,
    messages,
    isRelogin,
    get confirmCalls() { return confirmCalls },
    async navigate(path = "/inventory/stock") {
      const to = { path, fullPath: path, query: {}, meta: {} }
      let navigationResult
      await beforeGuard(to, {}, value => {
        navigationResult = value
        nextCalls.push(value)
      })
      return navigationResult
    },
    completeNavigation(path = "/inventory/stock") {
      afterGuard({ path, fullPath: path, query: {}, meta: {} })
    }
  }
}

async function testStoreRejectsMenuFailures() {
  const apiError = new Error("menu service unavailable")
  const commits = []
  const routeAdds = []
  const rejectedStore = loadPermissionStore(
    () => Promise.reject(apiError),
    { addRoutes: routes => routeAdds.push(routes) }
  )

  await assert.rejects(
    rejectedStore.actions.GenerateRoutes({ commit: (...args) => commits.push(args) }),
    error => error === apiError,
    "GenerateRoutes should preserve the menu API rejection"
  )
  assert.deepStrictEqual(commits, [], "failed menu requests must not publish partial route state")
  assert.deepStrictEqual(routeAdds, [], "failed menu requests must not register partial routes")

  const malformedStore = loadPermissionStore(
    () => Promise.resolve({ data: { path: "/not-an-array" } }),
    { addRoutes: routes => routeAdds.push(routes) }
  )
  await assert.rejects(
    malformedStore.actions.GenerateRoutes({ commit: (...args) => commits.push(args) }),
    error => error instanceof TypeError && error.message.includes("菜单响应格式无效"),
    "GenerateRoutes should reject malformed menu payloads with a recoverable error"
  )
  assert.deepStrictEqual(commits, [], "malformed menu payloads must not mutate route state")

  const successCommits = []
  const successAdds = []
  const successStore = loadPermissionStore(
    () => Promise.resolve({ data: [] }),
    { addRoutes: routes => successAdds.push(routes) }
  )
  const generated = await successStore.actions.GenerateRoutes({ commit: (...args) => successCommits.push(args) })
  assert.deepStrictEqual(generated, [{ path: "*", redirect: "/404", hidden: true }])
  assert.deepStrictEqual(successAdds, [[]], "successful generation should retain static fallback route registration")
  assert.deepStrictEqual(successCommits.map(call => call[0]), [
    "SET_ROUTES",
    "SET_SIDEBAR_ROUTERS",
    "SET_DEFAULT_ROUTES",
    "SET_TOPBAR_ROUTES"
  ], "successful generation should publish every existing route view")
}

async function testCancelledMenuLoadKeepsSessionAndCanRecover() {
  const harness = createGuardHarness({
    generateResults: [new Error("temporary menu failure"), [{ path: "/inventory" }]],
    confirmResults: [false]
  })

  const firstResult = await harness.navigate()
  assert.strictEqual(firstResult, false, "cancelling retry should abort only the current navigation")
  assert.strictEqual(harness.dispatchCalls.includes("FedLogOut"), false, "menu failures must not clear the user session")
  assert.strictEqual(harness.progress.dones, 1, "aborted route loading should close the progress indicator")
  assert.strictEqual(harness.isRelogin.show, false, "aborted route loading should restore the relogin guard state")
  assert.ok(harness.messages.warnings[0].includes("登录状态已保留"), "the recovery message should explain that login is preserved")

  const secondResult = await harness.navigate()
  assert.strictEqual(secondResult.path, "/inventory/stock", "a later navigation should retry and continue to the requested route")
  assert.strictEqual(secondResult.replace, true, "successful dynamic route registration should replace the pending navigation")
  assert.strictEqual(harness.dispatchCalls.filter(action => action === "GenerateRoutes").length, 2,
    "a cancelled failure must leave the route bootstrap eligible for retry")
  assert.deepStrictEqual(harness.addedRoutes, [[{ path: "/inventory" }]], "recovered routes should be registered once")
  harness.completeNavigation()
  assert.strictEqual(harness.progress.dones, 2, "successful navigation should finish progress through afterEach")
}

async function testInlineRetryWaitsForSuccess() {
  const harness = createGuardHarness({
    generateResults: [new Error("temporary menu failure"), [{ path: "/oa" }]],
    confirmResults: [true]
  })

  const result = await harness.navigate("/oa/index")
  assert.strictEqual(result.path, "/oa/index")
  assert.strictEqual(harness.dispatchCalls.filter(action => action === "GenerateRoutes").length, 2,
    "the route guard should await the explicit retry before completing navigation")
  assert.strictEqual(harness.dispatchCalls.includes("FedLogOut"), false)
  assert.strictEqual(harness.isRelogin.show, false)
}

async function testConcurrentNavigationSharesRouteBootstrap() {
  let resolveRoutes
  const pendingRoutes = new Promise(resolve => { resolveRoutes = resolve })
  const harness = createGuardHarness({ generateResults: [pendingRoutes] })

  const firstNavigation = harness.navigate("/inventory/stock")
  const secondNavigation = harness.navigate("/oa/index")
  await Promise.resolve()
  assert.strictEqual(harness.dispatchCalls.filter(action => action === "GenerateRoutes").length, 1,
    "concurrent guards should share one menu request and one recovery dialog")

  resolveRoutes([{ path: "/business" }])
  const [firstResult, secondResult] = await Promise.all([firstNavigation, secondNavigation])
  assert.strictEqual(firstResult.path, "/inventory/stock")
  assert.strictEqual(secondResult.path, "/oa/index")
  assert.deepStrictEqual(harness.addedRoutes, [[{ path: "/business" }]],
    "shared bootstrap should register the recovered routes only once")
}

async function testExpiredSessionStaysWithGlobalAuthenticationRecovery() {
  const sessionError = Object.assign(new Error("session expired"), { code: 401 })
  const harness = createGuardHarness({ generateResults: [sessionError] })
  const result = await harness.navigate()

  assert.strictEqual(result, false)
  assert.strictEqual(harness.confirmCalls, 0, "401 should not open a competing menu retry dialog")
  assert.deepStrictEqual(harness.messages.warnings, [], "401 should keep the global session-expiry guidance")
  assert.strictEqual(harness.dispatchCalls.includes("FedLogOut"), false,
    "the route guard should leave session-expiry decisions to the global authentication flow")
  assert.strictEqual(harness.progress.dones, 1)
}

async function testIdentityFailureStillUsesAuthenticationRecovery() {
  const identityError = new Error("session invalid")
  const harness = createGuardHarness({ roles: [], getInfoError: identityError })
  const result = await harness.navigate()

  assert.deepStrictEqual(result, { path: "/" })
  assert.deepStrictEqual(harness.dispatchCalls, ["GetInfo", "FedLogOut"])
  assert.strictEqual(harness.messages.errors[0], identityError)
  assert.strictEqual(harness.progress.dones, 1)
  assert.strictEqual(harness.isRelogin.show, false)
}

async function testCredentialPreconditionFromGetInfoKeepsSessionAndRedirects() {
  const credentialError = Object.assign(new Error("password change required"), {
    code: 428,
    businessCode: "CREDENTIAL_CHANGE_REQUIRED"
  })
  const harness = createGuardHarness({ roles: [], getInfoError: credentialError })
  const result = await harness.navigate("/mobile/store")

  assert.deepStrictEqual(result, {
    path: "/credential/change-password",
    replace: true
  })
  assert.deepStrictEqual(harness.dispatchCalls, ["GetInfo"],
    "credential preconditions must preserve the authenticated session")
  assert.deepStrictEqual(harness.messages.errors, [],
    "credential preconditions are an expected navigation state, not an identity failure")
  assert.strictEqual(harness.confirmCalls, 0)
  assert.strictEqual(harness.progress.dones, 1)
  assert.strictEqual(harness.isRelogin.show, false)
}

async function testCredentialPreconditionCanEnterExistingChangeRoute() {
  const credentialError = Object.assign(new Error("password change required"), {
    code: 428
  })
  const harness = createGuardHarness({ roles: [], getInfoError: credentialError })
  const result = await harness.navigate("/credential/change-password")

  assert.strictEqual(result, undefined,
    "an old backend returning 428 from getInfo must not create a same-route redirect loop")
  assert.deepStrictEqual(harness.dispatchCalls, ["GetInfo"])
  assert.deepStrictEqual(harness.messages.errors, [])
  assert.strictEqual(harness.confirmCalls, 0)
  assert.strictEqual(harness.progress.dones, 1)
}

async function testCredentialRestrictionFromMenuSkipsRetryDialogAndRedirects() {
  const credentialError = Object.assign(new Error("password change required"), {
    code: 409,
    businessCode: "CREDENTIAL_CHANGE_REQUIRED"
  })
  const harness = createGuardHarness({
    roles: [],
    generateResults: [credentialError]
  })
  const result = await harness.navigate("/mobile/store")

  assert.deepStrictEqual(result, {
    path: "/credential/change-password",
    replace: true
  })
  assert.deepStrictEqual(harness.dispatchCalls, ["GetInfo", "GenerateRoutes"])
  assert.strictEqual(harness.dispatchCalls.includes("FedLogOut"), false)
  assert.strictEqual(harness.confirmCalls, 0,
    "credential restrictions must never be presented as menu or network failures")
  assert.deepStrictEqual(harness.messages.warnings, [])
  assert.strictEqual(harness.progress.dones, 1)
}

async function testTemporaryCredentialRedirectsBeforeMenuLoad() {
  const harness = createGuardHarness({
    roles: [],
    getInfoCredentialState: "TEMPORARY"
  })

  const result = await harness.navigate("/mobile/store")

  assert.deepStrictEqual(result, {
    path: "/credential/change-password",
    replace: true
  })
  assert.deepStrictEqual(harness.dispatchCalls, ["GetInfo"],
    "temporary credentials must redirect to password change before requesting business menus")
  assert.strictEqual(harness.confirmCalls, 0,
    "credential-restricted users must not see the business-menu retry dialog")
  assert.strictEqual(harness.progress.dones, 1)
}

async function testTemporaryCredentialCanEnterChangePageWithoutBusinessRoutes() {
  const harness = createGuardHarness({
    roles: [],
    getInfoCredentialState: "TEMPORARY"
  })

  const result = await harness.navigate("/credential/change-password")

  assert.strictEqual(result, undefined)
  assert.deepStrictEqual(harness.dispatchCalls, ["GetInfo"],
    "the mandatory password page must not bootstrap business menus")
  assert.strictEqual(harness.confirmCalls, 0)
  assert.strictEqual(harness.progress.dones, 1)
}

async function testDesktopClientRejectsDirectMobileRoute() {
  const harness = createGuardHarness()
  const result = await harness.navigate("/mobile/store")

  assert.deepStrictEqual(result, { path: "/", replace: true })
}

async function run() {
  const guardSource = fs.readFileSync(guardPath, "utf8")
  assert.ok(guardSource.includes("router.beforeEach(async (to, from, next)"), "route bootstrap should be fully awaitable")
  assert.ok(guardSource.includes("await generateRoutesWithRetry()"), "the guard should wait for dynamic route recovery")
  assert.ok(guardSource.includes("return next(false)"), "cancelled recovery should explicitly abort navigation")

  await testStoreRejectsMenuFailures()
  await testCancelledMenuLoadKeepsSessionAndCanRecover()
  await testInlineRetryWaitsForSuccess()
  await testConcurrentNavigationSharesRouteBootstrap()
  await testExpiredSessionStaysWithGlobalAuthenticationRecovery()
  await testIdentityFailureStillUsesAuthenticationRecovery()
  await testCredentialPreconditionFromGetInfoKeepsSessionAndRedirects()
  await testCredentialPreconditionCanEnterExistingChangeRoute()
  await testCredentialRestrictionFromMenuSkipsRetryDialogAndRedirects()
  await testTemporaryCredentialRedirectsBeforeMenuLoad()
  await testTemporaryCredentialCanEnterChangePageWithoutBusinessRoutes()
  await testDesktopClientRejectsDirectMobileRoute()
  console.log("dynamicRouteRecovery tests passed")
}

run().catch(error => {
  console.error(error)
  process.exitCode = 1
})
