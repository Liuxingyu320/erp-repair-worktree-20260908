const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const userStorePath = path.resolve(__dirname, "../src/store/modules/user.js")

function esm(defaultValue, named = {}) {
  return Object.assign({ __esModule: true, default: defaultValue }, named)
}

function createRuntime() {
  let timerId = 0
  const timers = new Map()
  const externalResult = behavior => {
    if (behavior === "throw") throw new Error("synchronous cleanup failure")
    if (behavior === "pending") return new Promise(() => {})
    if (behavior === "reject") return Promise.reject(new Error("cleanup failed"))
    return Promise.resolve()
  }
  const runtime = {
    clearSignScopeCalls: 0,
    clearSignScopeBehavior: "resolve",
    clearDeptCalls: 0,
    clearDeptBehavior: "resolve",
    selectedDeptId: null,
    loginRequestDeptIds: [],
    removedTokenCalls: 0,
    removedExpiresInCalls: 0,
    authToken: "old-token",
    authExpiresIn: 7200,
    cookiePreferred: false,
    webStatusUpdates: [],
    sessionStatusListener: null,
    alertCalls: [],
    reloadCalls: 0,
    timerDelays: [],
    loginResponse: { data: { access_token: "new-token", expires_in: 3600 } },
    loginBehavior: "resolve",
    getInfoResponses: [],
    logoutBehavior: "resolve",
    logoutTokens: [],
    todoStopBehavior: "resolve",
    pushInitializeBehavior: "resolve",
    pushInitializeCalls: 0,
    pushDisableBehavior: "resolve",
    pushDisableTokens: [],
    dispatches: [],
    router: {
      currentRoute: { path: "/" },
      replaceCalls: [],
      replace(target) {
        this.replaceCalls.push(target)
        return Promise.resolve()
      },
      push() { return Promise.resolve() }
    },
    store: {
      dispatch(type, payload) {
        runtime.dispatches.push(type)
        if (type === "SyncWebSessionStatus" && typeof runtime.syncWebSessionStatus === "function") {
          return runtime.syncWebSessionStatus(payload)
        }
        if (type === "todo/stop") return externalResult(runtime.todoStopBehavior)
        return Promise.resolve()
      }
    },
    pushRegistration: {
      initialize() {
        runtime.pushInitializeCalls += 1
        return externalResult(runtime.pushInitializeBehavior)
      },
      disable(token) {
        runtime.pushDisableTokens.push(token)
        return externalResult(runtime.pushDisableBehavior)
      }
    },
    setTimeout(callback, delay) {
      const id = ++timerId
      timers.set(id, { callback, delay })
      runtime.timerDelays.push(delay)
      return id
    },
    clearTimeout(id) { timers.delete(id) },
    flushTimers() {
      const callbacks = Array.from(timers.values())
      timers.clear()
      callbacks.forEach(timer => timer.callback())
    }
  }
  return runtime
}

function loadUserStore(runtime) {
  const source = fs.readFileSync(userStorePath, "utf8")
  const transformed = babel.transformSync(source, {
    filename: userStorePath,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const module = { exports: {} }
  const modules = {
    "@/store": esm(runtime.store),
    "@/router": esm(runtime.router),
    "@/plugins/cache": esm({ session: { set() {} } }),
    "@/plugins/element-services": esm(undefined, {
      MessageBox: {
        confirm() { return Promise.reject(new Error("dismissed")) },
        alert(message) {
          runtime.alertCalls.push(message)
          return Promise.resolve()
        }
      }
    }),
    "@/api/login": esm(undefined, {
      login() {
        runtime.loginRequestDeptIds.push(runtime.selectedDeptId)
        return runtime.loginBehavior === "reject"
          ? Promise.reject(new Error("login failed"))
          : Promise.resolve(runtime.loginResponse)
      },
      logout(token) {
        runtime.logoutTokens.push(token)
        return runtime.logoutBehavior === "throw"
        ? (() => { throw new Error("synchronous logout failure") })()
        : runtime.logoutBehavior === "pending"
          ? new Promise(() => {})
          : runtime.logoutBehavior === "reject"
            ? Promise.reject(new Error("logout failed"))
            : Promise.resolve() },
      getInfo() { return Promise.resolve(runtime.getInfoResponses.shift()) },
      refreshToken() { return Promise.resolve({ data: 3600 }) }
    }),
    "@/utils/auth": esm(undefined, {
      getToken() { return runtime.authToken },
      setToken(token) { runtime.authToken = token },
      setExpiresIn(expiresIn) { runtime.authExpiresIn = expiresIn },
      removeToken() {
        runtime.authToken = ""
        runtime.removedTokenCalls += 1
      },
      removeExpiresIn() {
        runtime.authExpiresIn = ""
        runtime.removedExpiresInCalls += 1
      }
    }),
    "@/utils/shopContext": esm(undefined, {
      clearSelectedDept() {
        runtime.clearDeptCalls += 1
        if (runtime.clearDeptBehavior === "throw") throw new Error("department storage denied")
        runtime.selectedDeptId = null
      }
    }),
    "@/utils/signScopeContext": esm(undefined, {
      clearSelectedSignScope() {
        runtime.clearSignScopeCalls += 1
        if (runtime.clearSignScopeBehavior === "throw") throw new Error("scope storage denied")
      }
    }),
    "@/utils/passwordResetReminder": esm(undefined, {
      getPasswordResetRoute() { return "/profile" },
      resetPasswordResetReminderState() {},
      setPendingPasswordResetReminder() {},
      showPendingPasswordResetReminderIfReady() {}
    }),
    "@/utils/validate": esm(undefined, {
      isEmpty(value) { return value === undefined || value === null || value === "" }
    }),
    "@/utils/mobileHrQueueState": esm(undefined, {
      clearMobileHrQueueStateCache() {}
    }),
    "@/assets/images/profile.jpg": esm("profile.jpg"),
    "@/services/lazyPushRegistration": esm(runtime.pushRegistration),
    "@/utils/sessionMode": esm(undefined, {
      isCookiePreferredSession() { return runtime.cookiePreferred },
      setWebSessionStatus(status, broadcast) {
        runtime.webStatusUpdates.push({ status, broadcast })
      },
      subscribeWebSessionStatus(listener) {
        runtime.sessionStatusListener = listener
        return () => {}
      }
    })
  }
  vm.runInNewContext(transformed, {
    module,
    exports: module.exports,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(modules, request)) return modules[request]
      throw new Error(`unexpected user-store dependency: ${request}`)
    },
    Promise,
    Object,
    Array,
    String,
    Error,
    window: { location: { reload() { runtime.reloadCalls += 1 } } },
    setTimeout: runtime.setTimeout,
    clearTimeout: runtime.clearTimeout
  }, { filename: userStorePath })
  return module.exports.default || module.exports
}

function createHarness(userModule) {
  const state = JSON.parse(JSON.stringify(userModule.state))
  const context = {
    state,
    commit(type, payload) {
      userModule.mutations[type](state, payload)
    }
  }
  return { state, context }
}

function userInfoResponse(overrides = {}) {
  return Object.assign({
    user: {
      userId: 7,
      deptId: 11,
      userName: "tester",
      nickName: "Tester",
      avatar: ""
    },
    roles: ["manager"],
    permissions: ["hr:onboarding:list"],
    credentialState: "ACTIVE",
    profileMissingFields: []
  }, overrides)
}

function seedSensitiveSession(state, runtime, token) {
  Object.assign(state, {
    token,
    expires_in: 7200,
    id: 7,
    deptId: 11,
    name: "old-user",
    nickName: "Old User",
    avatar: "old-avatar.png",
    roles: ["previous-role"],
    permissions: ["previous:permission"],
    driveEnabled: true,
    businessFeatures: { healthCertificate: true },
    profileCompletionRequired: true,
    profileMissingFields: ["mobile"],
    credentialState: "TEMPORARY",
    temporaryPasswordExpiresAt: "2026-08-01",
    credentialBusinessCode: "CREDENTIAL_CHANGE_REQUIRED",
    systemBuild: { commit: "old", buildTime: "old", version: "old" }
  })
  runtime.authToken = token
  runtime.authExpiresIn = 7200
}

function currentRequestHeaders(runtime) {
  return runtime.selectedDeptId
    ? { "Dept-NumId": String(runtime.selectedDeptId) }
    : {}
}

function assertSensitiveSessionCleared(state, runtime) {
  assert.strictEqual(state.token, "")
  assert.strictEqual(state.expires_in, "")
  assert.strictEqual(state.id, "")
  assert.strictEqual(state.deptId, "")
  assert.strictEqual(state.name, "")
  assert.strictEqual(state.nickName, "")
  assert.strictEqual(state.avatar, "")
  assert.deepStrictEqual(Array.from(state.roles), [])
  assert.deepStrictEqual(Array.from(state.permissions), [])
  assert.strictEqual(state.driveEnabled, false)
  assert.strictEqual(state.profileCompletionRequired, false)
  assert.deepStrictEqual(Array.from(state.profileMissingFields), [])
  assert.strictEqual(state.credentialState, "ACTIVE")
  assert.strictEqual(state.temporaryPasswordExpiresAt, "")
  assert.strictEqual(state.credentialBusinessCode, "")
  assert.deepStrictEqual(Object.assign({}, state.systemBuild), {
    commit: "UNSET", buildTime: "UNSET", version: "UNSET"
  })
  assert.strictEqual(runtime.authToken, "")
  assert.strictEqual(runtime.authExpiresIn, "")
  assert.ok(Object.values(state.businessFeatures).every(value => value === false))
}

async function run() {
  const runtime = createRuntime()
  const userModule = loadUserStore(runtime)
  const { state, context } = createHarness(userModule)

  seedSensitiveSession(state, runtime, "old-token")
  runtime.selectedDeptId = "dept-account-a"
  await userModule.actions.Login(context, {
    username: " tester ",
    password: "secret",
    code: "1234",
    uuid: "captcha"
  })
  assert.strictEqual(runtime.clearSignScopeCalls, 1,
    "login start must clear the previous account's signing scope")
  assert.strictEqual(runtime.loginRequestDeptIds[0], null,
    "A→B login must clear account A's organization before the login request is sent")
  assert.deepStrictEqual(currentRequestHeaders(runtime), {},
    "account A's organization must not enter account B's subsequent requests")
  assert.deepStrictEqual(Array.from(state.roles), [])
  assert.deepStrictEqual(Array.from(state.permissions), [])
  assert.strictEqual(runtime.authToken, "new-token")
  assert.strictEqual(runtime.authExpiresIn, 3600)

  seedSensitiveSession(state, runtime, "stale-token")
  runtime.selectedDeptId = "dept-failed-account"
  runtime.loginBehavior = "reject"
  runtime.clearSignScopeBehavior = "throw"
  const beforeFailedLogin = runtime.clearSignScopeCalls
  await assert.rejects(() => userModule.actions.Login(context, {
    username: " failed ",
    password: "wrong",
    code: "0000",
    uuid: "captcha-failed"
  }), /login failed/)
  assert.strictEqual(runtime.clearSignScopeCalls, beforeFailedLogin + 1,
    "a failed login attempt must still clear the previous account's signing scope")
  assert.strictEqual(runtime.loginRequestDeptIds.at(-1), null,
    "the previous organization must be gone before a login request that later fails")
  assert.deepStrictEqual(currentRequestHeaders(runtime), {},
    "a failed login must leave no organization header candidate for later requests")
  assert.deepStrictEqual(Array.from(state.roles), [],
    "a failed login attempt must not retain previous roles")
  assert.deepStrictEqual(Array.from(state.permissions), [],
    "a failed login attempt must not retain previous permissions")
  assertSensitiveSessionCleared(state, runtime)
  assert.strictEqual(runtime.clearSignScopeCalls, beforeFailedLogin + 1,
    "scope cleanup exceptions must not block failed-login teardown")
  runtime.clearSignScopeBehavior = "resolve"
  runtime.loginBehavior = "resolve"

  const copiedInfo = userInfoResponse()
  runtime.getInfoResponses.push(
    copiedInfo,
    userInfoResponse({ roles: [], permissions: ["stale:permission"] }),
    userInfoResponse({ roles: ["manager"], permissions: { invalid: true } })
  )
  await userModule.actions.GetInfo(context)
  assert.deepStrictEqual(Array.from(state.roles), ["manager"])
  assert.deepStrictEqual(Array.from(state.permissions), ["hr:onboarding:list"])
  copiedInfo.roles.push("mutated-after-commit")
  copiedInfo.permissions.push("mutated:after:commit")
  assert.deepStrictEqual(Array.from(state.roles), ["manager"],
    "GetInfo must commit a copy of the roles array")
  assert.deepStrictEqual(Array.from(state.permissions), ["hr:onboarding:list"],
    "GetInfo must commit a copy of the permissions array")

  await userModule.actions.GetInfo(context)
  assert.deepStrictEqual(Array.from(state.roles), ["ROLE_DEFAULT"],
    "an empty role response should use the default authenticated role")
  assert.deepStrictEqual(Array.from(state.permissions), [],
    "an empty role response must clear old and contradictory permissions")

  await userModule.actions.GetInfo(context)
  assert.deepStrictEqual(Array.from(state.roles), ["manager"])
  assert.deepStrictEqual(Array.from(state.permissions), [],
    "a malformed permissions response must replace old permissions with an empty array")

  for (const behavior of ["pending", "throw", "reject"]) {
    runtime.pushInitializeBehavior = behavior
    runtime.getInfoResponses.push(userInfoResponse())
    const result = await Promise.race([
      userModule.actions.GetInfo(context),
      new Promise((resolve, reject) => setImmediate(() => reject(
        new Error(`GetInfo waited for push initialization behavior: ${behavior}`)
      )))
    ])
    assert.strictEqual(result.user.userId, 7)
  }
  runtime.pushInitializeBehavior = "resolve"

  const beforeRestrictedInfo = runtime.clearSignScopeCalls
  const beforeRestrictedPush = runtime.pushInitializeCalls
  runtime.getInfoResponses.push(userInfoResponse({ credentialState: "TEMPORARY" }))
  await userModule.actions.GetInfo(context)
  assert.strictEqual(runtime.clearSignScopeCalls, beforeRestrictedInfo + 1,
    "GetInfo must clear signing scope when the account is forced into credential change")
  assert.strictEqual(runtime.pushInitializeCalls, beforeRestrictedPush,
    "credential-restricted GetInfo must never initialize push")

  runtime.router.currentRoute.path = "/credential/change-password"
  const beforeEnforce = runtime.clearSignScopeCalls
  await userModule.actions.EnforceCredentialChange(context, {
    credentialState: "CHANGE_REQUIRED",
    businessCode: "CREDENTIAL_CHANGE_REQUIRED"
  })
  assert.strictEqual(runtime.clearSignScopeCalls, beforeEnforce + 1,
    "the explicit forced-credential boundary must clear signing scope")

  seedSensitiveSession(state, runtime, "expired-token")
  runtime.selectedDeptId = "dept-bearer-logout"
  runtime.logoutBehavior = "pending"
  runtime.todoStopBehavior = "throw"
  runtime.pushDisableBehavior = "pending"
  const beforeLogout = runtime.clearSignScopeCalls
  const logoutPromise = userModule.actions.LogOut(context)
  let logoutActionResolved = false
  logoutPromise.then(() => { logoutActionResolved = true })
  assert.strictEqual(runtime.clearSignScopeCalls, beforeLogout + 1,
    "logout must clear signing scope before external cleanup settles")
  assert.deepStrictEqual(currentRequestHeaders(runtime), {},
    "Bearer logout must clear the previous organization before later requests")
  assertSensitiveSessionCleared(state, runtime)
  runtime.flushTimers()
  await new Promise(resolve => setImmediate(resolve))
  assert.deepStrictEqual(runtime.pushDisableTokens.slice(-1), ["expired-token"])
  assert.deepStrictEqual(runtime.logoutTokens.slice(-1), ["expired-token"],
    "remote logout must start only after bounded push cleanup settles")
  assert.strictEqual(logoutActionResolved, true,
    "remote logout must not add a second cleanup deadline to the action")
  runtime.flushTimers()
  await logoutPromise

  seedSensitiveSession(state, runtime, "local-token")
  runtime.selectedDeptId = "dept-fed-logout"
  runtime.todoStopBehavior = "pending"
  runtime.pushDisableBehavior = "throw"
  const beforeFedLogout = runtime.clearSignScopeCalls
  const fedLogoutPromise = userModule.actions.FedLogOut(context)
  assert.strictEqual(runtime.clearSignScopeCalls, beforeFedLogout + 1,
    "front-end logout must clear signing scope")
  assert.deepStrictEqual(currentRequestHeaders(runtime), {},
    "FedLogOut must clear the previous organization")
  assertSensitiveSessionCleared(state, runtime)
  runtime.flushTimers()
  await fedLogoutPromise
  assert.deepStrictEqual(runtime.pushDisableTokens.slice(-1), ["local-token"])
  assert.strictEqual(runtime.removedTokenCalls, 4,
    "login starts and both logout paths must remove the local token")
  assert.strictEqual(runtime.removedExpiresInCalls, 4,
    "login starts and both logout paths must remove the expiry cookie")
  assert.ok(runtime.timerDelays.length > 0)
  assert.ok(runtime.timerDelays.every(delay => delay === 1500),
    "all bounded authentication cleanup timers must use the 1500ms contract")

  const cookieBroadcastRuntime = createRuntime()
  cookieBroadcastRuntime.cookiePreferred = true
  cookieBroadcastRuntime.loginResponse = { data: { expires_in: 3600 } }
  const cookieBroadcastModule = loadUserStore(cookieBroadcastRuntime)
  const cookieBroadcastHarness = createHarness(cookieBroadcastModule)
  await cookieBroadcastModule.actions.Login(cookieBroadcastHarness.context, {
    username: " cookie-user ",
    password: "secret",
    code: "1234",
    uuid: "cookie-captcha"
  })
  assert.deepStrictEqual(
    cookieBroadcastRuntime.webStatusUpdates.filter(update => update.status === "authenticated"),
    [{ status: "authenticated", broadcast: undefined }],
    "an explicit cookie login must notify peer tabs exactly once"
  )
  cookieBroadcastRuntime.webStatusUpdates = []
  cookieBroadcastRuntime.getInfoResponses.push(userInfoResponse())
  await cookieBroadcastModule.actions.GetInfo(cookieBroadcastHarness.context)
  assert.deepStrictEqual(cookieBroadcastRuntime.webStatusUpdates, [
    { status: "authenticated", broadcast: false }
  ], "an ordinary GetInfo probe must update local status without rebroadcasting")

  const cookieFailureRuntime = createRuntime()
  cookieFailureRuntime.cookiePreferred = true
  cookieFailureRuntime.logoutBehavior = "reject"
  const cookieFailureModule = loadUserStore(cookieFailureRuntime)
  const cookieFailureHarness = createHarness(cookieFailureModule)
  seedSensitiveSession(cookieFailureHarness.state, cookieFailureRuntime, "")
  cookieFailureHarness.state.name = "cookie-user"
  cookieFailureRuntime.selectedDeptId = "dept-cookie-session"
  await assert.rejects(
    () => cookieFailureModule.actions.LogOut(cookieFailureHarness.context),
    error => error && error.code === "COOKIE_LOGOUT_FAILED",
    "cookie logout must remain failed when the server did not revoke the HttpOnly session"
  )
  assert.strictEqual(cookieFailureHarness.state.name, "cookie-user",
    "cookie logout failure must retain local authenticated state so it cannot silently revive")
  assert.strictEqual(cookieFailureRuntime.selectedDeptId, "dept-cookie-session",
    "cookie logout failure must not clear local organization while the server session remains authenticated")
  assert.strictEqual(cookieFailureRuntime.removedTokenCalls, 0)
  assert.strictEqual(cookieFailureRuntime.alertCalls.length, 1,
    "cookie logout failure must offer a visible retry message")

  const cookieSuccessRuntime = createRuntime()
  cookieSuccessRuntime.cookiePreferred = true
  const cookieSuccessModule = loadUserStore(cookieSuccessRuntime)
  const cookieSuccessHarness = createHarness(cookieSuccessModule)
  seedSensitiveSession(cookieSuccessHarness.state, cookieSuccessRuntime, "")
  cookieSuccessRuntime.selectedDeptId = "dept-cookie-success"
  await cookieSuccessModule.actions.LogOut(cookieSuccessHarness.context)
  assertSensitiveSessionCleared(cookieSuccessHarness.state, cookieSuccessRuntime)
  assert.deepStrictEqual(currentRequestHeaders(cookieSuccessRuntime), {},
    "confirmed Cookie logout must clear the previous organization")
  assert.deepStrictEqual(cookieSuccessRuntime.logoutTokens, [undefined],
    "cookie logout must use the HttpOnly credential instead of passing a script token")

  const remoteRuntime = createRuntime()
  remoteRuntime.cookiePreferred = true
  const remoteModule = loadUserStore(remoteRuntime)
  const remoteHarness = createHarness(remoteModule)
  seedSensitiveSession(remoteHarness.state, remoteRuntime, "")
  remoteRuntime.selectedDeptId = "dept-remote-anonymous"
  await remoteModule.actions.SyncWebSessionStatus(remoteHarness.context, "anonymous")
  assertSensitiveSessionCleared(remoteHarness.state, remoteRuntime)
  assert.deepStrictEqual(currentRequestHeaders(remoteRuntime), {},
    "a cross-tab anonymous event must clear the receiving tab's organization")
  assert.deepStrictEqual(remoteRuntime.router.replaceCalls, ["/login"])
  assert.ok(remoteRuntime.webStatusUpdates.some(update =>
    update.status === "anonymous" && update.broadcast === false),
  "remote anonymous cleanup must not rebroadcast and form a loop")

  seedSensitiveSession(remoteHarness.state, remoteRuntime, "")
  remoteRuntime.selectedDeptId = "dept-remote-authenticated"
  await remoteModule.actions.SyncWebSessionStatus(remoteHarness.context, "authenticated")
  assert.strictEqual(remoteHarness.state.name, "")
  assert.deepStrictEqual(currentRequestHeaders(remoteRuntime), {},
    "a cross-tab authenticated event must clear the receiving tab's previous organization")
  assert.strictEqual(remoteRuntime.reloadCalls, 1,
    "remote login/user switch must clear stale data and force an authenticated identity re-probe")

  const tabARuntime = createRuntime()
  tabARuntime.cookiePreferred = true
  tabARuntime.selectedDeptId = "dept-new-account-a"
  const tabAModule = loadUserStore(tabARuntime)
  const tabAHarness = createHarness(tabAModule)
  tabARuntime.syncWebSessionStatus = status =>
    tabAModule.actions.SyncWebSessionStatus(tabAHarness.context, status)

  const tabBRuntime = createRuntime()
  tabBRuntime.cookiePreferred = true
  tabBRuntime.selectedDeptId = "dept-old-account-b"
  const tabBModule = loadUserStore(tabBRuntime)
  const tabBHarness = createHarness(tabBModule)
  seedSensitiveSession(tabBHarness.state, tabBRuntime, "")
  tabBRuntime.syncWebSessionStatus = status =>
    tabBModule.actions.SyncWebSessionStatus(tabBHarness.context, status)

  tabBRuntime.sessionStatusListener("authenticated")
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(tabARuntime.selectedDeptId, "dept-new-account-a",
    "the broadcasting login tab keeps its newly selected organization")
  assert.deepStrictEqual(currentRequestHeaders(tabBRuntime), {},
    "the peer tab must discard its old organization before re-probing the authenticated identity")
  assert.strictEqual(tabBRuntime.reloadCalls, 1)

  tabBRuntime.selectedDeptId = "dept-before-peer-logout"
  seedSensitiveSession(tabBHarness.state, tabBRuntime, "")
  tabBRuntime.sessionStatusListener("anonymous")
  await new Promise(resolve => setImmediate(resolve))
  assert.deepStrictEqual(currentRequestHeaders(tabBRuntime), {},
    "the peer tab must discard its organization after another tab logs out")
  assertSensitiveSessionCleared(tabBHarness.state, tabBRuntime)

  const deniedDeptRuntime = createRuntime()
  deniedDeptRuntime.clearDeptBehavior = "throw"
  deniedDeptRuntime.selectedDeptId = "inaccessible-dept"
  const deniedDeptModule = loadUserStore(deniedDeptRuntime)
  const deniedDeptHarness = createHarness(deniedDeptModule)
  await assert.doesNotReject(() => deniedDeptModule.actions.FedLogOut(deniedDeptHarness.context),
    "organization Storage failures must remain best-effort at a forced local logout boundary")

  const idempotentRuntime = createRuntime()
  idempotentRuntime.selectedDeptId = "dept-idempotent"
  const idempotentModule = loadUserStore(idempotentRuntime)
  const idempotentHarness = createHarness(idempotentModule)
  await idempotentModule.actions.FedLogOut(idempotentHarness.context)
  await idempotentModule.actions.FedLogOut(idempotentHarness.context)
  assert.deepStrictEqual(currentRequestHeaders(idempotentRuntime), {})
  assert.strictEqual(idempotentRuntime.clearDeptCalls, 2,
    "repeating the same local session boundary must remain safe and idempotent")

  idempotentHarness.context.commit("SET_ID", "101")
  const firstRevision = idempotentHarness.state.sessionRevision
  idempotentHarness.context.commit("SET_ID", 101)
  assert.strictEqual(idempotentHarness.state.sessionRevision, firstRevision,
    "refreshing the same identity must not invalidate active requests")
  await idempotentModule.actions.FedLogOut(idempotentHarness.context)
  idempotentHarness.context.commit("SET_ID", "101")
  assert.strictEqual(idempotentHarness.state.sessionRevision, firstRevision + 2,
    "signing back into the same account must invalidate old request replay permits")

  console.log("mobile session boundary tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
