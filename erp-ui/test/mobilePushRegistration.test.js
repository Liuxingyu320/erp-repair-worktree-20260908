const assert = require("assert")
const fs = require("fs")
const path = require("path")
const babel = require("@babel/core")

const rootDir = path.resolve(__dirname, "..")

function readFile(relativePath) {
  return fs.readFileSync(path.join(rootDir, relativePath), "utf8")
}

function assertFile(relativePath) {
  assert.ok(fs.existsSync(path.join(rootDir, relativePath)), `${relativePath} should exist`)
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function flushPromises() {
  return new Promise(resolve => setImmediate(resolve))
}

function createFakeClock() {
  let now = 0
  let nextId = 0
  const timers = new Map()
  const delays = []
  return {
    delays,
    setTimeout(callback, delay) {
      const id = ++nextId
      timers.set(id, { callback, at: now + delay })
      delays.push(delay)
      return id
    },
    clearTimeout(id) {
      timers.delete(id)
    },
    advance(milliseconds) {
      const target = now + milliseconds
      while (true) {
        const due = Array.from(timers.entries())
          .filter(([, timer]) => timer.at <= target)
          .sort((left, right) => left[1].at - right[1].at || left[0] - right[0])
        if (due.length === 0) break
        const [id, timer] = due[0]
        timers.delete(id)
        now = timer.at
        timer.callback()
      }
      now = target
    },
    activeCount() {
      return timers.size
    }
  }
}

function loadPushModule() {
  const routeSource = readFile("src/services/pushRoute.js")
  const transformedRoute = babel.transformSync(routeSource, {
    babelrc: false,
    configFile: false,
    plugins: ["@babel/plugin-transform-modules-commonjs"]
  }).code
  const routeModule = { exports: {} }
  new Function("require", "module", "exports", transformedRoute)(
    () => { throw new Error("pushRoute should not import runtime dependencies") },
    routeModule,
    routeModule.exports
  )

  const source = readFile("src/services/pushRegistrationService.js")
  const transformed = babel.transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ["@babel/plugin-transform-modules-commonjs"]
  }).code
  const module = { exports: {} }
  const mockRequire = id => {
    if (id === "@capacitor/core") return { Capacitor: {} }
    if (id === "@capacitor/push-notifications") return { PushNotifications: {} }
    if (id === "@/utils/auth") return { getToken: () => "default-auth-token" }
    if (id === "./pushRoute") return routeModule.exports
    if (id === "@/api/system/userNotification") {
      return {
        registerUserDeviceToken: () => Promise.resolve(),
        disableUserDeviceToken: () => Promise.resolve()
      }
    }
    throw new Error(`Unexpected import: ${id}`)
  }
  new Function("require", "module", "exports", transformed)(mockRequire, module, module.exports)
  return {
    ...module.exports,
    resolvePushRoute: routeModule.exports.resolvePushRoute
  }
}

async function assertWebPushFacade() {
  const source = readFile("src/services/pushRegistration.js")
  const transformed = babel.transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ["@babel/plugin-transform-modules-commonjs"]
  }).code
  const module = { exports: {} }
  const previousWindow = global.window
  global.window = {}
  try {
    new Function("require", "module", "exports", transformed)(
      id => {
        if (id === "./pushRoute") return { resolvePushRoute: () => null }
        throw new Error(`Unexpected import: ${id}`)
      },
      module,
      module.exports
    )
    const facade = module.exports.default
    const router = { push: () => Promise.resolve() }
    facade.setRouter(router)
    assert.deepStrictEqual(await facade.initialize(201), {
      registered: false,
      reason: "web"
    })
    assert.deepStrictEqual(await facade.disable("auth-token"), {
      disabled: true,
      reason: "web"
    })
  } finally {
    if (previousWindow === undefined) delete global.window
    else global.window = previousWindow
  }
}

function loadNotificationApi(request) {
  const source = readFile("src/api/system/userNotification.js")
  const transformed = babel.transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ["@babel/plugin-transform-modules-commonjs"]
  }).code
  const module = { exports: {} }
  const mockRequire = id => {
    if (id === "@/utils/request") return { __esModule: true, default: request }
    throw new Error(`Unexpected import: ${id}`)
  }
  new Function("require", "module", "exports", transformed)(mockRequire, module, module.exports)
  return module.exports
}

function createHarness(createPushRegistrationService, options = {}) {
  const listeners = {}
  const removed = []
  const uploaded = []
  const uploadedRequests = []
  const disabled = []
  const disabledRequests = []
  const routed = []
  const calls = { check: 0, request: 0, register: 0, unregister: 0 }
  const permissions = (options.permissions || ["granted"]).slice()
  const requestResult = options.requestResult || "granted"
  const platform = options.platform || "android"
  const currentPlatform = () => options.getPlatform ? options.getPlatform() : platform
  const pushNotifications = {
    async addListener(name, callback) {
      listeners[name] = callback
      if (options.onAddListener) return options.onAddListener(name, callback)
      return {
        remove: async () => {
          removed.push(name)
          return options.onRemove ? options.onRemove(name) : undefined
        }
      }
    },
    async checkPermissions() {
      calls.check += 1
      if (options.onCheck) return options.onCheck()
      if (options.checkError) throw options.checkError
      return { receive: permissions.length > 1 ? permissions.shift() : permissions[0] }
    },
    async requestPermissions() {
      calls.request += 1
      if (options.onRequest) return options.onRequest()
      return { receive: requestResult }
    },
    async register() {
      calls.register += 1
      if (options.onNativeRegister) return options.onNativeRegister()
    },
    async unregister() {
      calls.unregister += 1
      if (options.onUnregister) return options.onUnregister()
    }
  }
  const service = createPushRegistrationService({
    capacitor: {
      getPlatform: currentPlatform,
      isNativePlatform: () => currentPlatform() === "android" || currentPlatform() === "ios"
    },
    pushNotifications,
    router: {
      push(target) {
        routed.push(target)
        return Promise.resolve()
      }
    },
    registerDeviceToken(payload, requestOptions) {
      uploaded.push(payload)
      uploadedRequests.push({ payload, options: requestOptions })
      return options.onRegister ? options.onRegister(payload, requestOptions) : Promise.resolve()
    },
    disableDeviceToken(payload, requestOptions) {
      disabled.push(payload)
      disabledRequests.push({ payload, options: requestOptions })
      return options.onDisable ? options.onDisable(payload, requestOptions) : Promise.resolve()
    },
    getAuthToken: options.getAuthToken || (() => options.authToken || "auth-token"),
    AbortController: options.AbortController,
    navigatorObject: { userAgent: "ERP-Test/" + "x".repeat(250) },
    setTimeout: options.setTimeout,
    clearTimeout: options.clearTimeout
  })
  return {
    service,
    listeners,
    removed,
    uploaded,
    uploadedRequests,
    disabled,
    disabledRequests,
    routed,
    calls
  }
}

async function run() {
  const packageJson = JSON.parse(readFile("package.json"))
  assert.strictEqual(
    packageJson.dependencies["@capacitor/push-notifications"],
    "8.1.1",
    "Capacitor push plugin should be pinned to the latest published Capacitor 8 version"
  )
  assert.ok(packageJson.scripts["postapp:verify"].includes("verify-capacitor-sync.cjs"))
  assert.ok(packageJson.scripts["app:verify:release"].includes("--release"))

  ;[
    "src/api/system/userNotification.js",
    "src/services/pushRegistration.js",
    "src/services/pushRegistrationService.js",
    "src/services/pushRoute.js",
    "scripts/verify-capacitor-sync.cjs",
    "ios/App/App/App.entitlements"
  ].forEach(assertFile)

  const apiSource = readFile("src/api/system/userNotification.js")
  assert.ok(apiSource.includes("/system/user-notification/device-token"))
  assert.ok(apiSource.includes("method: 'post'") && apiSource.includes("method: 'delete'"))
  const notificationRequests = []
  const notificationApi = loadNotificationApi(config => {
    notificationRequests.push(config)
    return Promise.resolve()
  })
  const requestSignal = { test: true }
  await notificationApi.registerUserDeviceToken({ token: "default-token" })
  await notificationApi.disableUserDeviceToken({ token: "owned-token" }, {
    authToken: "account-a-token",
    signal: requestSignal
  })
  await notificationApi.disableUserDeviceToken({ token: "missing-token" }, { authToken: "" })
  assert.strictEqual(notificationRequests[0].headers, undefined,
    "default device-token API calls must remain backward compatible")
  assert.strictEqual(notificationRequests[1].headers.isToken, false)
  assert.strictEqual(notificationRequests[1].headers.Authorization, "Bearer account-a-token")
  assert.strictEqual(notificationRequests[1].signal, requestSignal)
  assert.strictEqual(notificationRequests[2].headers.isToken, false)
  assert.strictEqual(notificationRequests[2].headers.Authorization, undefined,
    "an explicitly missing owner token must disable interceptor fallback")

  const { createPushRegistrationService, resolvePushRoute } = loadPushModule()
  await assertWebPushFacade()

  const web = createHarness(createPushRegistrationService, { platform: "web" })
  const webResult = await web.service.initialize(201)
  assert.strictEqual(webResult.reason, "web")
  assert.deepStrictEqual(web.calls, { check: 0, request: 0, register: 0, unregister: 0 })
  assert.deepStrictEqual(Object.keys(web.listeners), [])

  const android = createHarness(createPushRegistrationService, {
    platform: "android",
    permissions: ["prompt", "granted"],
    requestResult: "granted"
  })
  await android.service.initialize(201)
  assert.strictEqual(android.calls.check, 1)
  assert.strictEqual(android.calls.request, 1, "Android 13 prompt should request permission")
  assert.strictEqual(android.calls.register, 1)
  await android.listeners.registration({ value: "fcm-secret-token" })
  assert.deepStrictEqual(android.uploaded, [{
    platform: "ANDROID",
    token: "fcm-secret-token",
    appId: "com.erp.mobile",
    deviceName: ("ERP-Test/" + "x".repeat(250)).slice(0, 200)
  }])

  await android.service.initialize(201)
  assert.ok(android.removed.includes("registration"), "repeat initialization should remove old listeners")

  const missingUser = createHarness(createPushRegistrationService)
  await missingUser.service.initialize(201)
  const missingResult = await missingUser.service.initialize(null)
  assert.strictEqual(missingResult.reason, "missing-user")
  assert.strictEqual(missingUser.removed.length, 3,
    "missing-user initialization must still hand off previous listeners")

  let switchablePlatform = "android"
  const nativeToWeb = createHarness(createPushRegistrationService, {
    getPlatform: () => switchablePlatform
  })
  await nativeToWeb.service.initialize(201)
  switchablePlatform = "web"
  const switchedToWeb = await nativeToWeb.service.initialize(201)
  assert.strictEqual(switchedToWeb.reason, "web")
  assert.strictEqual(nativeToWeb.removed.length, 3,
    "web initialization must still hand off previous native listeners")

  const concurrent = createHarness(createPushRegistrationService)
  await Promise.all([
    concurrent.service.initialize(201),
    concurrent.service.initialize(201)
  ])
  assert.strictEqual(concurrent.calls.register, 1,
    "a synchronously superseded initialization must not reach native registration")

  const denied = createHarness(createPushRegistrationService, {
    platform: "android",
    permissions: ["prompt", "prompt-with-rationale"],
    requestResult: "denied"
  })
  const firstDenied = await denied.service.initialize(201)
  const secondDenied = await denied.service.initialize(201)
  assert.strictEqual(firstDenied.reason, "permission-denied")
  assert.strictEqual(secondDenied.reason, "permission-denied")
  assert.strictEqual(denied.calls.request, 1, "denied permission must not trigger a prompt loop")
  assert.strictEqual(denied.calls.register, 0)

  const failed = createHarness(createPushRegistrationService, {
    checkError: new Error("native permission bridge unavailable")
  })
  const failedResult = await failed.service.initialize(201)
  assert.strictEqual(failedResult.reason, "permission-unavailable")
  assert.strictEqual(failed.removed.length, 3,
    "failed initialization should remove every listener it installed")

  const malformedListener = createHarness(createPushRegistrationService, {
    onAddListener() { return {} }
  })
  assert.strictEqual((await malformedListener.service.initialize(201)).reason, "listener-unavailable")
  assert.strictEqual(malformedListener.calls.register, 0,
    "a listener without a removable handle must invalidate initialization")

  for (const malformedPermission of [null, {}, { receive: 1 }]) {
    const malformed = createHarness(createPushRegistrationService, {
      onCheck() { return malformedPermission }
    })
    assert.strictEqual((await malformed.service.initialize(201)).reason, "permission-unavailable")
    assert.strictEqual(malformed.calls.register, 0)
    assert.strictEqual(malformed.removed.length, 3)
  }

  const timerClock = createFakeClock()
  const timed = createHarness(createPushRegistrationService, {
    setTimeout: timerClock.setTimeout,
    clearTimeout: timerClock.clearTimeout
  })
  await timed.service.initialize(203)
  assert.ok(timerClock.delays.includes(250), "listener handoff must be bounded to 250ms")
  assert.ok(timerClock.delays.includes(5000),
    "permission checks and ordinary native operations must be bounded to 5s")
  assert.ok(timerClock.delays.includes(20000), "initialization must have a 20s total bound")
  assert.strictEqual(timerClock.activeCount(), 0, "successful initialization must clear every timer")

  const listenerClock = createFakeClock()
  const lateListener = deferred()
  let lateListenerRemoved = 0
  const listenerTimeout = createHarness(createPushRegistrationService, {
    setTimeout: listenerClock.setTimeout,
    clearTimeout: listenerClock.clearTimeout,
    onAddListener(name) {
      if (name === "registration") return lateListener.promise
      return { remove() {} }
    }
  })
  const listenerTimeoutPromise = listenerTimeout.service.initialize(204)
  await flushPromises()
  listenerClock.advance(5000)
  const listenerTimeoutResult = await listenerTimeoutPromise
  assert.strictEqual(listenerTimeoutResult.reason, "listener-unavailable")
  lateListener.resolve({ remove() { lateListenerRemoved += 1 } })
  await flushPromises()
  assert.strictEqual(lateListenerRemoved, 1,
    "a listener handle arriving after timeout must be removed immediately")
  assert.strictEqual(listenerClock.activeCount(), 0, "late-listener cleanup must clear timers")

  const permissionClock = createFakeClock()
  const pendingPermission = deferred()
  let permissionCheckCount = 0
  const permissionTimeout = createHarness(createPushRegistrationService, {
    setTimeout: permissionClock.setTimeout,
    clearTimeout: permissionClock.clearTimeout,
    onCheck() {
      permissionCheckCount += 1
      return permissionCheckCount === 1
        ? pendingPermission.promise
        : { receive: "granted" }
    }
  })
  const permissionTimeoutPromise = permissionTimeout.service.initialize(205)
  await flushPromises()
  permissionClock.advance(5000)
  const permissionTimeoutResult = await permissionTimeoutPromise
  assert.strictEqual(permissionTimeoutResult.reason, "permission-unavailable")
  pendingPermission.resolve({ receive: "granted" })
  await flushPromises()
  assert.strictEqual(permissionTimeout.calls.register, 0,
    "a late permission result must not continue the expired initialization")
  const permissionRetry = await permissionTimeout.service.initialize(205)
  assert.strictEqual(permissionRetry.registered, true,
    "permission timeout must remain retryable")

  const requestClock = createFakeClock()
  const pendingRequest = deferred()
  let requestAttempt = 0
  const requestTimeout = createHarness(createPushRegistrationService, {
    permissions: ["prompt"],
    setTimeout: requestClock.setTimeout,
    clearTimeout: requestClock.clearTimeout,
    onRequest() {
      requestAttempt += 1
      return requestAttempt === 1 ? pendingRequest.promise : { receive: "granted" }
    }
  })
  const requestTimeoutPromise = requestTimeout.service.initialize(206)
  await flushPromises()
  requestClock.advance(15000)
  assert.strictEqual((await requestTimeoutPromise).reason, "permission-unavailable")
  assert.strictEqual((await requestTimeout.service.initialize(206)).registered, true)
  assert.strictEqual(requestTimeout.calls.request, 2,
    "a timed-out permission prompt must be retryable, unlike an explicit denial")
  assert.ok(requestClock.delays.includes(15000),
    "only the interactive permission request should use the 15s bound")
  pendingRequest.resolve({ receive: "denied" })
  await flushPromises()

  const malformedRequest = createHarness(createPushRegistrationService, {
    permissions: ["prompt"],
    onRequest() { return {} }
  })
  assert.strictEqual((await malformedRequest.service.initialize(206)).reason, "permission-unavailable")
  assert.strictEqual(malformedRequest.calls.register, 0)
  assert.strictEqual(malformedRequest.removed.length, 3)

  const registerClock = createFakeClock()
  const pendingNativeRegister = deferred()
  let nativeRegisterAttempt = 0
  const registerTimeout = createHarness(createPushRegistrationService, {
    setTimeout: registerClock.setTimeout,
    clearTimeout: registerClock.clearTimeout,
    onNativeRegister() {
      nativeRegisterAttempt += 1
      return nativeRegisterAttempt === 1 ? pendingNativeRegister.promise : undefined
    }
  })
  const registerTimeoutPromise = registerTimeout.service.initialize(207)
  await flushPromises()
  registerClock.advance(5000)
  assert.strictEqual((await registerTimeoutPromise).reason, "register-unavailable")
  assert.strictEqual((await registerTimeout.service.initialize(207)).registered, true,
    "native registration timeout must not poison the next attempt")
  pendingNativeRegister.resolve()
  await flushPromises()

  const handoffClock = createFakeClock()
  const neverRemoved = deferred()
  const boundedHandoff = createHarness(createPushRegistrationService, {
    setTimeout: handoffClock.setTimeout,
    clearTimeout: handoffClock.clearTimeout,
    onRemove() { return neverRemoved.promise }
  })
  await boundedHandoff.service.initialize(208)
  const secondHandoff = boundedHandoff.service.initialize(209)
  await flushPromises()
  handoffClock.advance(250)
  await flushPromises()
  assert.strictEqual((await secondHandoff).registered, true,
    "new-account initialization must not wait on old listener removal")

  const disableClock = createFakeClock()
  const pendingUnregister = deferred()
  const boundedDisable = createHarness(createPushRegistrationService, {
    setTimeout: disableClock.setTimeout,
    clearTimeout: disableClock.clearTimeout,
    onUnregister() { return pendingUnregister.promise }
  })
  await boundedDisable.service.initialize(209)
  const boundedDisablePromise = boundedDisable.service.disable()
  await flushPromises()
  disableClock.advance(1500)
  await boundedDisablePromise
  assert.strictEqual((await boundedDisable.service.initialize(210)).registered, true,
    "a pending unregister must not block the next account")
  disableClock.advance(3500)
  await flushPromises()
  assert.strictEqual(disableClock.activeCount(), 0,
    "timers retained only for late unregister cleanup must eventually clear")

  const nativeReconcileClock = createFakeClock()
  const oldUnregister = deferred()
  let unregisterAttempt = 0
  const nativeReconcile = createHarness(createPushRegistrationService, {
    setTimeout: nativeReconcileClock.setTimeout,
    clearTimeout: nativeReconcileClock.clearTimeout,
    onUnregister() {
      unregisterAttempt += 1
      return unregisterAttempt === 1 ? oldUnregister.promise : undefined
    }
  })
  await nativeReconcile.service.initialize(301)
  const oldDisable = nativeReconcile.service.disable("account-a-token")
  await flushPromises()
  nativeReconcileClock.advance(1500)
  await oldDisable
  assert.strictEqual((await nativeReconcile.service.initialize(302)).registered, true)
  const registerCountBeforeLateUnregister = nativeReconcile.calls.register
  oldUnregister.resolve()
  await flushPromises()
  assert.strictEqual(nativeReconcile.calls.register, registerCountBeforeLateUnregister + 1,
    "late A unregister must reconcile the current B native registration")

  const staleNativeRegister = deferred()
  let nativeRegisterCalls = 0
  const nativeLogoutReconcile = createHarness(createPushRegistrationService, {
    onNativeRegister() {
      nativeRegisterCalls += 1
      return nativeRegisterCalls === 1 ? staleNativeRegister.promise : undefined
    }
  })
  const staleInitialize = nativeLogoutReconcile.service.initialize(303)
  await flushPromises()
  await nativeLogoutReconcile.service.disable("account-a-token")
  const unregisterCountBeforeLateRegister = nativeLogoutReconcile.calls.unregister
  staleNativeRegister.resolve()
  await staleInitialize
  await flushPromises()
  assert.strictEqual(
    nativeLogoutReconcile.calls.unregister,
    unregisterCountBeforeLateRegister + 1,
    "a stale native register completing after logout must reconcile back to unregistered"
  )

  const disableApiClock = createFakeClock()
  const pendingDisableApi = deferred()
  const boundedDisableApi = createHarness(createPushRegistrationService, {
    setTimeout: disableApiClock.setTimeout,
    clearTimeout: disableApiClock.clearTimeout,
    onDisable() { return pendingDisableApi.promise }
  })
  await boundedDisableApi.service.initialize(210)
  await boundedDisableApi.listeners.registration({ value: "disable-api-token" })
  const disableApiPromise = boundedDisableApi.service.disable()
  await flushPromises()
  disableApiClock.advance(1500)
  await disableApiPromise
  assert.strictEqual((await boundedDisableApi.service.initialize(211)).registered, true,
    "a pending token-disable API call must not block the next account")
  disableApiClock.advance(3500)
  await flushPromises()
  assert.strictEqual(disableApiClock.activeCount(), 0)

  const preemptClock = createFakeClock()
  const oldAdd = deferred()
  let addAttempt = 0
  let staleHandleRemoved = 0
  const preempted = createHarness(createPushRegistrationService, {
    setTimeout: preemptClock.setTimeout,
    clearTimeout: preemptClock.clearTimeout,
    onAddListener(name) {
      addAttempt += 1
      if (addAttempt === 1) return oldAdd.promise
      return {
        remove() {
          preempted.removed.push(name)
        }
      }
    }
  })
  const oldInitialize = preempted.service.initialize(210)
  await flushPromises()
  await preempted.service.disable()
  const newInitialize = await preempted.service.initialize(211)
  assert.strictEqual(newInitialize.registered, true,
    "disable and the next user must preempt an old pending initialization")
  oldAdd.resolve({ remove() { staleHandleRemoved += 1 } })
  await flushPromises()
  assert.strictEqual(staleHandleRemoved, 1)
  assert.deepStrictEqual(preempted.removed, [],
    "late completion of the old add-listener call must not remove the new account's listeners")
  preemptClock.advance(20000)
  await oldInitialize

  const staleCallbacks = createHarness(createPushRegistrationService)
  await staleCallbacks.service.initialize(212)
  const oldRegistrationCallback = staleCallbacks.listeners.registration
  const oldActionCallback = staleCallbacks.listeners.pushNotificationActionPerformed
  await staleCallbacks.service.initialize(213)
  await oldRegistrationCallback({ value: "old-account-token" })
  await oldActionCallback({
    notification: { data: { routeType: "OA_SIGN_HR_TASK", taskId: "99" } }
  })
  assert.deepStrictEqual(staleCallbacks.uploaded, [],
    "an old account listener must not upload a registration token")
  assert.deepStrictEqual(staleCallbacks.routed, [],
    "an old account listener must not navigate")

  const uploadAbort = deferred()
  let authToken = "account-a-token"
  const abortOwnership = createHarness(createPushRegistrationService, {
    getAuthToken: () => authToken,
    onRegister() { return uploadAbort.promise }
  })
  await abortOwnership.service.initialize(401)
  const abortedUpload = abortOwnership.listeners.registration({ value: "owned-token" })
  await flushPromises()
  assert.strictEqual(abortOwnership.uploadedRequests[0].options.authToken, "account-a-token")
  const uploadSignal = abortOwnership.uploadedRequests[0].options.signal
  authToken = "account-b-token"
  const ownedDisable = abortOwnership.service.disable("account-a-token")
  assert.strictEqual(uploadSignal.aborted, true,
    "disable must synchronously abort the old account upload")
  await ownedDisable
  assert.strictEqual(abortOwnership.disabledRequests[0].options.authToken, "account-a-token",
    "pending-binding cleanup must retain the initiating account credential")
  uploadAbort.resolve()
  await abortedUpload

  let refreshedToken = "token-before-refresh"
  const refreshedOwnership = createHarness(createPushRegistrationService, {
    getAuthToken: () => refreshedToken
  })
  await refreshedOwnership.service.initialize(401)
  refreshedToken = "token-after-refresh"
  await refreshedOwnership.listeners.registration({ value: "refreshed-token" })
  assert.strictEqual(refreshedOwnership.uploadedRequests[0].options.authToken, "token-after-refresh",
    "registration must bind the token current at the callback/POST boundary")

  const missingAuth = createHarness(createPushRegistrationService, {
    getAuthToken: () => ""
  })
  await missingAuth.service.initialize(401)
  const missingAuthResult = await missingAuth.listeners.registration({ value: "must-not-upload" })
  assert.strictEqual(missingAuthResult.reason, "missing-auth-token")
  assert.strictEqual(missingAuth.uploaded.length, 0,
    "missing owner authentication must never fall back to a later account")

  let latestCleanupToken = "initial-binding-token"
  const latestCleanup = createHarness(createPushRegistrationService, {
    getAuthToken: () => latestCleanupToken
  })
  await latestCleanup.service.initialize(401)
  await latestCleanup.listeners.registration({ value: "active-token" })
  latestCleanupToken = ""
  const missingCleanupResult = await latestCleanup.service.disable("")
  assert.strictEqual(latestCleanup.disabled.length, 0,
    "explicitly missing logout authentication must not reuse the stale binding token")
  assert.strictEqual(missingCleanupResult.reason, "missing-auth-token")

  const timedUploadClock = createFakeClock()
  const rawTimedUpload = deferred()
  const timedUpload = createHarness(createPushRegistrationService, {
    setTimeout: timedUploadClock.setTimeout,
    clearTimeout: timedUploadClock.clearTimeout,
    authToken: "timed-account-token",
    onRegister() { return rawTimedUpload.promise }
  })
  await timedUpload.service.initialize(402)
  const timedUploadCallback = timedUpload.listeners.registration({ value: "timed-token" })
  await flushPromises()
  timedUploadClock.advance(5000)
  await timedUploadCallback
  const timedUploadSignal = timedUpload.uploadedRequests[0].options.signal
  const disableTimedUpload = timedUpload.service.disable("timed-account-token")
  assert.strictEqual(timedUploadSignal.aborted, true,
    "a raw upload retained after its wrapper timeout must still be aborted by disable")
  await flushPromises()
  timedUploadClock.advance(1500)
  await disableTimedUpload
  rawTimedUpload.resolve()
  await flushPromises()

  const phaseUpload = deferred()
  const phaseEvents = []
  let serverEnabled = false
  const twoPhaseCleanup = createHarness(createPushRegistrationService, {
    authToken: "two-phase-a-token",
    onRegister() {
      phaseEvents.push("POST-start")
      return phaseUpload.promise.then(() => {
        phaseEvents.push("POST-fulfilled")
        serverEnabled = true
      })
    },
    onDisable() {
      phaseEvents.push("DELETE")
      serverEnabled = false
      return Promise.resolve()
    }
  })
  await twoPhaseCleanup.service.initialize(402)
  const phaseCallback = twoPhaseCleanup.listeners.registration({ value: "two-phase-token" })
  await twoPhaseCleanup.service.disable("two-phase-a-token")
  assert.strictEqual(serverEnabled, false)
  phaseUpload.resolve()
  await phaseCallback
  await flushPromises()
  assert.deepStrictEqual(phaseEvents, ["POST-start", "DELETE", "POST-fulfilled", "DELETE"])
  assert.strictEqual(twoPhaseCleanup.disabledRequests.length, 2,
    "disable and late POST fulfillment must each issue exactly one owner-auth DELETE")
  assert.ok(twoPhaseCleanup.disabledRequests.every(request =>
    request.options.authToken === "two-phase-a-token"))
  assert.strictEqual(serverEnabled, false,
    "an observable late fulfillment should be followed by the modeled best-effort cleanup")

  const retentionClock = createFakeClock()
  const retention = createHarness(createPushRegistrationService, {
    setTimeout: retentionClock.setTimeout,
    clearTimeout: retentionClock.clearTimeout,
    authToken: "retention-token",
    onRegister() { return new Promise(() => {}) }
  })
  await retention.service.initialize(402)
  const retentionCallbacks = Array.from({ length: 101 }, (_, index) =>
    retention.listeners.registration({ value: `retention-${index}` }))
  await flushPromises()
  retentionClock.advance(5000)
  await Promise.all(retentionCallbacks)
  assert.ok(retention.uploadedRequests.every(request => request.options.signal.aborted),
    "every timed-out upload attempt must be aborted")
  const retentionDisable = retention.service.disable("retention-token")
  await flushPromises()
  retentionClock.advance(1500)
  await retentionDisable
  assert.strictEqual(retention.disabled.length, 0,
    "timed-out current uploads must be aborted and evicted instead of retained for disable")

  const staleUpload = deferred()
  let switchingAuthToken = "late-account-a-token"
  const lateUploadOwnership = createHarness(createPushRegistrationService, {
    getAuthToken: () => switchingAuthToken,
    onRegister() { return staleUpload.promise }
  })
  await lateUploadOwnership.service.initialize(403)
  const staleUploadCallback = lateUploadOwnership.listeners.registration({ value: "late-token" })
  switchingAuthToken = "account-b-token"
  await lateUploadOwnership.service.initialize(404)
  staleUpload.resolve()
  await staleUploadCallback
  assert.strictEqual(
    lateUploadOwnership.disabledRequests[0].options.authToken,
    "late-account-a-token",
    "late upload compensation must never fall through to the current B credential"
  )

  const crossAccountUpload = deferred()
  let crossAccountToken = "account-a-token"
  const crossAccountCleanup = createHarness(createPushRegistrationService, {
    getAuthToken: () => crossAccountToken,
    onRegister(payload) {
      return payload.token === "account-a-device"
        ? crossAccountUpload.promise
        : Promise.resolve()
    }
  })
  await crossAccountCleanup.service.initialize(501)
  const accountAUpload = crossAccountCleanup.listeners.registration({
    value: "account-a-device"
  })
  crossAccountToken = "account-b-token"
  await crossAccountCleanup.service.initialize(502)
  await crossAccountCleanup.listeners.registration({ value: "account-b-device" })
  await crossAccountCleanup.service.disable("account-b-latest-token")
  assert.deepStrictEqual(
    crossAccountCleanup.disabledRequests.slice(0, 2).map(request => ({
      token: request.payload.token,
      authToken: request.options.authToken
    })),
    [
      { token: "account-a-device", authToken: "account-a-token" },
      { token: "account-b-device", authToken: "account-b-latest-token" }
    ],
    "disable(B) must preserve A attempt auth while using B's latest token for B binding"
  )
  crossAccountUpload.reject(new Error("aborted account A upload"))
  await accountAUpload
  await flushPromises()
  assert.deepStrictEqual(
    crossAccountCleanup.disabledRequests.slice(2).map(request => ({
      token: request.payload.token,
      authToken: request.options.authToken
    })),
    [{ token: "account-a-device", authToken: "account-a-token" }],
    "an invalidated rejected attempt must run exactly one best-effort settlement cleanup"
  )

  const unhandled = []
  const onUnhandled = reason => unhandled.push(reason)
  process.on("unhandledRejection", onUnhandled)
  const rejectedUpload = createHarness(createPushRegistrationService, {
    onRegister() { return Promise.reject(new Error("upload rejected")) }
  })
  await rejectedUpload.service.initialize(405)
  await rejectedUpload.listeners.registration({ value: "rejected-token" })
  await flushPromises()
  process.removeListener("unhandledRejection", onUnhandled)
  assert.deepStrictEqual(unhandled, [], "rejected external operations must not leak unhandled rejections")

  assert.deepStrictEqual(resolvePushRoute({ routeType: "OA_SIGN_PACKAGE_SIGN", packageId: "90" }), {
    path: "/mobile/sign-package",
    query: { packageId: "90" }
  })
  assert.deepStrictEqual(resolvePushRoute({ routeType: "OA_SIGN_HR_TASK", taskId: 9 }), {
    path: "/oa/sign-task",
    query: { taskId: "9" }
  })
  assert.strictEqual(resolvePushRoute({ routeType: "https://evil.example", url: "https://evil.example" }), null)
  assert.strictEqual(resolvePushRoute({ routeType: "OA_SIGN_PACKAGE_SIGN", packageId: "1e2" }), null)

  await android.listeners.pushNotificationActionPerformed({
    notification: { data: { routeType: "OA_SIGN_PACKAGE_SIGN", packageId: "90" } }
  })
  await android.listeners.pushNotificationActionPerformed({
    notification: { data: { routeType: "OA_SIGN_HR_TASK", taskId: "9" } }
  })
  await android.listeners.pushNotificationActionPerformed({
    notification: { data: { routeType: "https://evil.example", url: "https://evil.example" } }
  })
  assert.deepStrictEqual(android.routed, [
    { path: "/mobile/sign-package", query: { packageId: "90" } },
    { path: "/oa/sign-task", query: { taskId: "9" } }
  ])

  await android.service.disable()
  assert.deepStrictEqual(android.disabled, [{ platform: "ANDROID", token: "fcm-secret-token" }])
  assert.strictEqual(android.calls.unregister, 1)

  let releaseUpload
  let uploadFinished = false
  const logoutRace = createHarness(createPushRegistrationService, {
    onRegister() {
      return new Promise(resolve => {
        releaseUpload = () => {
          uploadFinished = true
          resolve()
        }
      })
    },
    onDisable() { return Promise.resolve() }
  })
  await logoutRace.service.initialize(202)
  const uploadPromise = logoutRace.listeners.registration({ value: "rotating-token" })
  const disablePromise = logoutRace.service.disable()
  let disableResolved = false
  disablePromise.then(() => { disableResolved = true })
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(disableResolved, true,
    "disable must not wait without bound for an in-flight registration upload")
  assert.strictEqual((await logoutRace.service.initialize(203)).registered, true,
    "the next account must initialize while the old upload is still pending")
  releaseUpload()
  await Promise.all([uploadPromise, disablePromise])
  assert.deepStrictEqual(logoutRace.disabled, [{
    platform: "ANDROID",
    token: "rotating-token"
  }, {
    platform: "ANDROID",
    token: "rotating-token"
  }], "disable and late stale upload must run distinct cleanup phases")

  const mainSource = readFile("src/main.js")
  assert.ok(mainSource.includes("pushRegistration.setRouter(router)"))
  const pushFacadeSource = readFile("src/services/pushRegistration.js")
  assert.ok(
    pushFacadeSource.includes("webpackChunkName: \"chunk-native-push\"") &&
      !pushFacadeSource.includes("@capacitor/push-notifications"),
    "web startup should defer the native push implementation until a native bridge is present"
  )

  const manifest = readFile("android/app/src/main/AndroidManifest.xml")
  assert.ok(manifest.includes("android.permission.POST_NOTIFICATIONS"))
  const appDelegate = readFile("ios/App/App/AppDelegate.swift")
  assert.ok(appDelegate.includes("capacitorDidRegisterForRemoteNotifications"))
  assert.ok(appDelegate.includes("capacitorDidFailToRegisterForRemoteNotifications"))
  const entitlements = readFile("ios/App/App/App.entitlements")
  assert.ok(entitlements.includes("aps-environment"))
  const xcodeProject = readFile("ios/App/App.xcodeproj/project.pbxproj")
  assert.ok(xcodeProject.includes("CODE_SIGN_ENTITLEMENTS = App/App.entitlements"))
  assert.ok(xcodeProject.includes("com.apple.Push"))

  const capacitorConfig = readFile("capacitor.config.ts")
  assert.ok(capacitorConfig.includes("PushNotifications"))
  assert.ok(capacitorConfig.includes("presentationOptions"))

  const gitignore = readFile(".gitignore")
  ;["google-services.json", "GoogleService-Info.plist", "*.p8", "service-account"].forEach(pattern => {
    assert.ok(gitignore.includes(pattern), `${pattern} should be ignored`)
  })

  const verifySource = readFile("scripts/verify-capacitor-sync.cjs")
  assert.ok(verifySource.includes("--release"))
  assert.ok(verifySource.includes("google-services.json"))
  assert.ok(verifySource.includes("GoogleService-Info.plist"))
  console.log("mobile push registration tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
