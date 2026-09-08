const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const todoStorePath = path.resolve(__dirname, "../src/store/modules/todo.js")
const refreshEventsPath = path.resolve(__dirname, "../src/utils/todoRefreshEvents.js")
const TODO_PROVIDER_COUNT = 4
assert.ok(fs.existsSync(todoStorePath), "the namespaced resilient todo store must exist")
assert.ok(fs.existsSync(refreshEventsPath), "the todo refresh lifecycle helper must exist")

function createEventTarget(extra = {}) {
  const listeners = new Map()
  return Object.assign({
    addEventListener(type, listener) {
      if (!listeners.has(type)) listeners.set(type, new Set())
      listeners.get(type).add(listener)
    },
    removeEventListener(type, listener) {
      const values = listeners.get(type)
      if (values) values.delete(listener)
    },
    dispatchEvent(event) {
      Array.from(listeners.get(event.type) || []).forEach(listener => listener(event))
      return true
    },
    listenerCount(type) {
      return (listeners.get(type) || new Set()).size
    }
  }, extra)
}

function createClock() {
  let id = 0
  const timers = new Map()
  return {
    setInterval(callback, delay) {
      const timerId = ++id
      timers.set(timerId, { callback, delay })
      return timerId
    },
    clearInterval(timerId) {
      timers.delete(timerId)
    },
    tick(delay) {
      Array.from(timers.values()).filter(timer => timer.delay <= delay).forEach(timer => timer.callback())
    },
    size() {
      return timers.size
    }
  }
}

function loadTodoStore(overrides) {
  let source = fs.readFileSync(todoStorePath, "utf8")
  source = source
    .replace(/import\s+\{[\s\S]*?\}\s+from\s+['"][^'"]+['"]\s*/g, "")
    .replace(/export\s+(const|function)\s+/g, "$1 ")
    .replace(/export default todo\s*$/, "module.exports = { todo, createTodoState }")
  const aggregate = require("../src/utils/todoAggregator")
  const context = {
    module: { exports: {} }, exports: {}, Promise, Date, Error, JSON, Object, Array, Set, Number, String,
    fetchTodoSummary: overrides.fetchTodoSummary,
    fetchTodoList: overrides.fetchTodoList || (async () => ({ rows: [], total: 0 })),
    aggregateSummaries: aggregate.aggregateSummaries,
    fetchExactTodoPage: aggregate.fetchExactTodoPage,
    getSelectedDeptContext: overrides.getSelectedDeptContext,
    hasValidInventoryDeptContext: context => {
      const source = context || {}
      const hasDeptId = source.deptId !== undefined && source.deptId !== null && String(source.deptId).trim() !== ""
      const deptType = source.deptType ? String(source.deptType).trim().toUpperCase() : ""
      return hasDeptId && (deptType === "STORE" || deptType === "WAREHOUSE")
    },
    addTodoRefreshListener: overrides.addTodoRefreshListener,
    removeTodoRefreshListener: overrides.removeTodoRefreshListener,
    document: overrides.document,
    setInterval: overrides.clock.setInterval,
    clearInterval: overrides.clock.clearInterval
  }
  vm.runInNewContext(source, context, { filename: todoStorePath })
  return context.module.exports
}

function createHarness(loaded) {
  const { todo, createTodoState } = loaded
  const state = createTodoState()
  const context = {
    state,
    commit(type, payload) {
      return todo.mutations[type](state, payload)
    },
    dispatch(type, payload) {
      const localType = type.startsWith("todo/") ? type.slice(5) : type
      return todo.actions[localType](context, payload)
    },
    getters: {}, rootState: {}
  }
  return { todo, state, context }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((onResolve, onReject) => {
    resolve = onResolve
    reject = onReject
  })
  return { promise, resolve, reject }
}

const flush = () => new Promise(resolve => setImmediate(resolve))

async function testLockAndQueuedContextRefresh() {
  const clock = createClock()
  const fakeDocument = createEventTarget({ visibilityState: "visible" })
  const fakeWindow = createEventTarget()
  let deptId = "10"
  let calls = 0
  const firstRound = Array.from({ length: TODO_PROVIDER_COUNT }, () => deferred())
  const loaded = loadTodoStore({
    clock,
    document: fakeDocument,
    getSelectedDeptContext: () => ({ deptId }),
    addTodoRefreshListener: listener => fakeWindow.addEventListener("erp:dept-changed", listener),
    removeTodoRefreshListener: listener => fakeWindow.removeEventListener("erp:dept-changed", listener),
    fetchTodoSummary: async source => {
      const call = calls++
      if (call < TODO_PROVIDER_COUNT) await firstRound[call].promise
      return { data: { approval: deptId === "10" ? 1 : 9, execution: 0, returned: 0, risk: 0, personal: 0, recent: [], typeCounts: {}, source } }
    }
  })
  const { state, context } = createHarness(loaded)

  const first = context.dispatch("start")
  const duplicate = context.dispatch("refreshSummaries")
  assert.strictEqual(duplicate, first, "a normal duplicate must return the one in-flight promise")
  assert.strictEqual(calls, TODO_PROVIDER_COUNT)

  deptId = "20"
  context.dispatch("contextChanged")
  context.dispatch("contextChanged")
  assert.strictEqual(state.refreshQueued, true)
  assert.strictEqual(state.contextVersion, 2)
  firstRound.forEach(gate => gate.resolve())
  await first
  assert.strictEqual(calls, TODO_PROVIDER_COUNT * 2,
    "many context events during one request must queue exactly one latest-context batch")
  assert.strictEqual(state.providers.inventory.summary.approval, 9, "the old-context response must not commit")
  assert.strictEqual(state.refreshQueued, false)
  await context.dispatch("stop")
}

async function testVisibilityPollingEventsAndStop() {
  const clock = createClock()
  const fakeDocument = createEventTarget({ visibilityState: "visible" })
  const fakeWindow = createEventTarget()
  let calls = 0
  const loaded = loadTodoStore({
    clock,
    document: fakeDocument,
    getSelectedDeptContext: () => ({ deptId: "10" }),
    addTodoRefreshListener: listener => fakeWindow.addEventListener("erp:dept-changed", listener),
    removeTodoRefreshListener: listener => fakeWindow.removeEventListener("erp:dept-changed", listener),
    fetchTodoSummary: async () => {
      calls += 1
      return { data: { approval: 1, execution: 0, returned: 0, risk: 0, personal: 0, recent: [], typeCounts: {} } }
    }
  })
  const { state, context } = createHarness(loaded)
  await context.dispatch("start")
  await context.dispatch("start")
  assert.strictEqual(calls, TODO_PROVIDER_COUNT, "start must be idempotent")
  assert.strictEqual(clock.size(), 1)
  assert.strictEqual(fakeDocument.listenerCount("visibilitychange"), 1)
  assert.strictEqual(fakeWindow.listenerCount("erp:dept-changed"), 1)

  clock.tick(60000)
  await flush()
  assert.strictEqual(calls, TODO_PROVIDER_COUNT * 2, "visible pages poll every 60 seconds")

  fakeDocument.visibilityState = "hidden"
  fakeDocument.dispatchEvent({ type: "visibilitychange" })
  assert.strictEqual(clock.size(), 0)
  clock.tick(60000)
  await flush()
  assert.strictEqual(calls, TODO_PROVIDER_COUNT * 2, "hidden pages do not poll")

  fakeDocument.visibilityState = "visible"
  fakeDocument.dispatchEvent({ type: "visibilitychange" })
  await flush()
  assert.strictEqual(calls, TODO_PROVIDER_COUNT * 3, "visibility restoration refreshes immediately")
  assert.strictEqual(clock.size(), 1)

  fakeWindow.dispatchEvent({ type: "erp:dept-changed", detail: { deptId: "20" } })
  await flush()
  assert.strictEqual(calls, TODO_PROVIDER_COUNT * 4, "organization changes refresh immediately")

  state.listCache.example = { rows: [{}], total: 1 }
  await context.dispatch("stop")
  assert.strictEqual(clock.size(), 0)
  assert.strictEqual(fakeDocument.listenerCount("visibilitychange"), 0)
  assert.strictEqual(fakeWindow.listenerCount("erp:dept-changed"), 0)
  assert.strictEqual(state.started, false)
  assert.strictEqual(Object.prototype.hasOwnProperty.call(state, "refreshPromise"), false)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(state.listCache)), {})
  assert.ok(Object.values(state.providers).every(provider => provider.summary === null))
}

async function testStoppedSummaryFinallyCannotClearNewRuntime() {
  const clock = createClock()
  const fakeDocument = createEventTarget({ visibilityState: "visible" })
  const fakeWindow = createEventTarget()
  const gates = Array.from({ length: TODO_PROVIDER_COUNT * 2 }, () => deferred())
  let calls = 0
  const loaded = loadTodoStore({
    clock,
    document: fakeDocument,
    getSelectedDeptContext: () => ({ deptId: "10" }),
    addTodoRefreshListener: listener => fakeWindow.addEventListener("erp:dept-changed", listener),
    removeTodoRefreshListener: listener => fakeWindow.removeEventListener("erp:dept-changed", listener),
    fetchTodoSummary: async () => {
      const current = calls++
      await gates[current].promise
      return { data: { approval: current < TODO_PROVIDER_COUNT ? 1 : 9, execution: 0, returned: 0, risk: 0, personal: 0, recent: [], typeCounts: {} } }
    }
  })
  const { state, context } = createHarness(loaded)
  const stoppedRequest = context.dispatch("start")
  await context.dispatch("stop")
  const latestRequest = context.dispatch("start")
  assert.strictEqual(calls, TODO_PROVIDER_COUNT * 2)

  gates.slice(0, TODO_PROVIDER_COUNT).forEach(gate => gate.resolve())
  await stoppedRequest
  assert.strictEqual(state.summaryLoading, true, "an old runtime finally must not clear the new runtime loading flag")
  assert.strictEqual(state.providers.inventory.summary, null)

  gates.slice(TODO_PROVIDER_COUNT).forEach(gate => gate.resolve())
  await latestRequest
  assert.strictEqual(state.summaryLoading, false)
  assert.strictEqual(state.providers.inventory.summary.approval, 9)
  await context.dispatch("stop")
}

function testShopContextEmitsOneSafeEvent() {
  const shopPath = path.resolve(__dirname, "../src/utils/shopContext.js")
  let source = fs.readFileSync(shopPath, "utf8").replace(/export\s+/g, "")
  source += "; return { setSelectedDept, clearSelectedDept }"
  const values = new Map()
  const storage = {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key)
  }
  const fakeWindow = createEventTarget()
  const events = []
  fakeWindow.addEventListener("erp:dept-changed", event => events.push(event.detail))
  function FakeCustomEvent(type, init) {
    this.type = type
    this.detail = init.detail
  }
  const api = new Function("sessionStorage", "window", "CustomEvent", source)(storage, fakeWindow, FakeCustomEvent)
  api.setSelectedDept(88, "总仓", "WAREHOUSE")
  assert.strictEqual(events.length, 1)
  assert.deepStrictEqual(events[0], { deptId: "88", name: "总仓", type: "WAREHOUSE" })
  api.clearSelectedDept()
  assert.strictEqual(events.length, 2)
  assert.deepStrictEqual(events[1], { deptId: null, name: "", type: "" })

  const noWindowApi = new Function("sessionStorage", source)(storage)
  assert.doesNotThrow(() => noWindowApi.setSelectedDept(89, "二号仓", "WAREHOUSE"), "shop context must be safe without window")
}

function loadUserModule(options) {
  const dispatch = options.dispatch || (() => Promise.resolve())
  const getInfo = options.getInfo || (async () => ({}))
  const userPath = path.resolve(__dirname, "../src/store/modules/user.js")
  let source = fs.readFileSync(userPath, "utf8")
  source = source
    .replace(/import[\s\S]*?from\s+['"][^'"]+['"]\s*/g, "")
    .replace(/export default user\s*$/, "module.exports = user")
  const context = {
    module: { exports: {} }, exports: {}, Promise, Object, Array, String, Error,
    store: { dispatch },
    router: {
      currentRoute: { path: "/" },
      push: () => Promise.resolve(),
      replace: () => Promise.resolve()
    },
    cache: { session: { set: () => {} } },
    MessageBox: { confirm: () => Promise.resolve() },
    login: async () => ({}),
    logout: options.logout || (async () => {}),
    getInfo,
    refreshToken: async () => ({}),
    getToken: () => "token",
    setToken: () => {},
    setExpiresIn: () => {},
    removeToken: options.removeToken || (() => {}),
    removeExpiresIn: options.removeExpiresIn || (() => {}),
    isCookiePreferredSession: () => false,
    setWebSessionStatus: () => {},
    subscribeWebSessionStatus: () => () => {},
    clearSelectedDept: () => {},
    clearSelectedSignScope: options.clearSelectedSignScope || (() => {}),
    getPasswordResetRoute: () => "/profile",
    resetPasswordResetReminderState: () => {},
    setPendingPasswordResetReminder: () => {},
    showPendingPasswordResetReminderIfReady: () => {},
    isEmpty: value => value === undefined || value === null || value === "",
    clearMobileHrQueueStateCache: options.clearMobileHrQueueStateCache || (() => {}),
    defAva: "default-avatar",
    pushRegistration: {
      initialize: options.pushInitialize || (() => Promise.resolve()),
      disable: options.pushDisable || (() => Promise.resolve())
    },
    setTimeout: options.setTimeout || setTimeout,
    clearTimeout: options.clearTimeout || clearTimeout
  }
  vm.runInNewContext(source, context, { filename: userPath })
  return context.module.exports
}

function createCleanupClock() {
  let timerId = 0
  const timers = new Map()
  return {
    setTimeout(callback, delay) {
      const id = ++timerId
      timers.set(id, { callback, delay })
      return id
    },
    clearTimeout(id) {
      timers.delete(id)
    },
    flush() {
      const callbacks = Array.from(timers.values(), timer => timer.callback)
      timers.clear()
      callbacks.forEach(callback => callback())
    }
  }
}

async function testLogoutLifecycleIsImmediateAndBounded() {
  for (const actionName of ["LogOut", "FedLogOut"]) {
    const clock = createCleanupClock()
    const stopGate = deferred()
    const pushGate = deferred()
    const backendGate = deferred()
    const calls = []
    let removeTokenCalls = 0
    let removeExpiresInCalls = 0
    const user = loadUserModule({
      dispatch(type) {
        calls.push(type)
        assert.strictEqual(type, "todo/stop")
        return stopGate.promise
      },
      logout(token) {
        calls.push(`logout:${token}`)
        return backendGate.promise
      },
      pushDisable() {
        calls.push("push:disable")
        return pushGate.promise
      },
      clearSelectedSignScope() {
        calls.push("sign-scope:clear")
      },
      removeToken() {
        removeTokenCalls += 1
      },
      removeExpiresIn() {
        removeExpiresInCalls += 1
      },
      setTimeout: clock.setTimeout,
      clearTimeout: clock.clearTimeout
    })
    const state = JSON.parse(JSON.stringify(user.state))
    Object.assign(state, {
      token: "active-token",
      expires_in: 7200,
      id: 7,
      deptId: 11,
      name: "old-user",
      roles: ["manager"],
      permissions: ["todo:list"],
      profileCompletionRequired: true,
      profileMissingFields: ["mobile"]
    })
    const context = {
      state,
      commit(type, payload) {
        user.mutations[type](state, payload)
      }
    }
    let settled = false
    const actionPromise = user.actions[actionName](context).then(() => {
      settled = true
    })

    assert.ok(calls.includes("todo/stop"), `${actionName} must start todo lifecycle teardown`)
    assert.ok(calls.includes("push:disable"), `${actionName} must start push teardown`)
    if (actionName === "LogOut") {
      assert.ok(!calls.some(call => call.startsWith("logout:")),
        "LogOut must not revoke the account token while push cleanup is pending")
    } else {
      assert.ok(!calls.some(call => call.startsWith("logout:")),
        "FedLogOut must remain a local-only authentication boundary")
    }
    assert.strictEqual(state.token, "", `${actionName} must clear credentials without awaiting external cleanup`)
    assert.strictEqual(state.expires_in, "")
    assert.strictEqual(state.id, "")
    assert.strictEqual(state.deptId, "")
    assert.strictEqual(state.name, "")
    assert.deepStrictEqual(Array.from(state.roles), [])
    assert.deepStrictEqual(Array.from(state.permissions), [])
    assert.strictEqual(state.profileCompletionRequired, false)
    assert.deepStrictEqual(Array.from(state.profileMissingFields), [])
    assert.strictEqual(removeTokenCalls, 1)
    assert.strictEqual(removeExpiresInCalls, 1)
    await flush()
    assert.strictEqual(settled, false,
      `${actionName} should keep best-effort tasks alive until they settle or reach the bound`)

    if (actionName === "LogOut") {
      pushGate.resolve()
      await flush()
      assert.ok(calls.includes("logout:active-token"),
        "LogOut must use the token captured before local teardown after push cleanup settles")
      assert.strictEqual(state.token, "",
        "starting remote logout must never restore locally cleared credentials")
    }
    clock.flush()
    await actionPromise
    assert.strictEqual(settled, true, `${actionName} must resolve after bounded cleanup expires`)
  }

  const timeoutClock = createCleanupClock()
  const timeoutCalls = []
  const timeoutUser = loadUserModule({
    dispatch: () => Promise.resolve(),
    logout(token) {
      timeoutCalls.push(`logout:${token}`)
      return Promise.reject(new Error("remote logout failed"))
    },
    pushDisable() {
      timeoutCalls.push("push:disable")
      return new Promise(() => {})
    },
    setTimeout: timeoutClock.setTimeout,
    clearTimeout: timeoutClock.clearTimeout
  })
  const timeoutState = JSON.parse(JSON.stringify(timeoutUser.state))
  timeoutState.token = "timeout-token"
  let timeoutActionSettled = false
  const timeoutAction = timeoutUser.actions.LogOut({
    state: timeoutState,
    commit(type, payload) {
      timeoutUser.mutations[type](timeoutState, payload)
    }
  }).then(() => {
    timeoutActionSettled = true
  })
  assert.deepStrictEqual(timeoutCalls, ["push:disable"])
  assert.strictEqual(timeoutState.token, "")
  timeoutClock.flush()
  await flush()
  assert.deepStrictEqual(timeoutCalls, ["push:disable", "logout:timeout-token"],
    "the 1.5s push bound must release remote logout with the captured token")
  assert.strictEqual(timeoutActionSettled, true,
    "remote logout must not add a second 1.5s phase to the logout action deadline")
  timeoutClock.flush()
  await timeoutAction
  assert.strictEqual(timeoutState.token, "",
    "remote logout failure must not roll back local teardown")
}

async function testGetInfoDoesNotAwaitTodoProviders() {
  const response = {
    user: { userId: 7, deptId: 8, userName: "director", nickName: "运营总监", avatar: "" },
    roles: ["director"],
    permissions: ["inventory:todo:approve"],
    pwdChrtype: "N"
  }
  const commits = []
  const startGate = deferred()
  let commitsAtStart = []
  const user = loadUserModule({
    getInfo: async () => response,
    dispatch(type) {
      assert.strictEqual(type, "todo/start")
      commitsAtStart = commits.map(entry => entry.type)
      return startGate.promise
    }
  })
  let resolved = false
  const infoPromise = user.actions.GetInfo({
    state: {},
    commit(type, payload) { commits.push({ type, payload }) }
  }).then(value => {
    resolved = true
    return value
  })
  await flush()
  await flush()
  assert.strictEqual(resolved, true, "GetInfo must resolve without waiting for pending todo providers")
  assert.ok(commitsAtStart.includes("SET_ROLES") && commitsAtStart.includes("SET_PERMISSIONS"))
  assert.ok(commitsAtStart.includes("SET_ID") && commitsAtStart.includes("SET_AVATAR"), "user fields must commit before todo/start")
  assert.strictEqual((await infoPromise).user.userId, 7)

  const rejectingUser = loadUserModule({
    getInfo: async () => response,
    dispatch: () => Promise.reject(new Error("todo providers unavailable"))
  })
  await assert.doesNotReject(() => rejectingUser.actions.GetInfo({ state: {}, commit: () => {} }))

  const throwingUser = loadUserModule({
    getInfo: async () => response,
    dispatch: () => { throw new Error("todo runtime unavailable") }
  })
  await assert.doesNotReject(() => throwingUser.actions.GetInfo({ state: {}, commit: () => {} }))
}

Promise.resolve()
  .then(testGetInfoDoesNotAwaitTodoProviders)
  .then(testLockAndQueuedContextRefresh)
  .then(testVisibilityPollingEventsAndStop)
  .then(testStoppedSummaryFinallyCannotClearNewRuntime)
  .then(testShopContextEmitsOneSafeEvent)
  .then(testLogoutLifecycleIsImmediateAndBounded)
  .then(() => console.log("unifiedTodoLifecycle tests passed"))
  .catch(error => {
    console.error(error)
    process.exitCode = 1
  })
