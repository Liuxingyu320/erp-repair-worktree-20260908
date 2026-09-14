const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("@babel/core")
const userPiiPatch = require("../src/utils/userPiiPatch")

function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

const timers = []
function fakeSetTimeout(fn) {
  const id = timers.length + 1
  timers.push({ id, fn })
  return id
}
function fakeClearTimeout(id) {
  const index = timers.findIndex(item => item && item.id === id)
  if (index >= 0) timers[index] = null
}
function flushTimers() {
  const pending = timers.filter(Boolean)
  timers.length = 0
  pending.forEach(item => item.fn())
}

const runtimeRef = { current: null }
const userApi = {
  getUser(...args) { return runtimeRef.current.getUser(...args) },
  getUserPii(...args) { return runtimeRef.current.getUserPii(...args) },
  updateUser(...args) { return runtimeRef.current.updateUser(...args) },
  addUser(...args) { return runtimeRef.current.addUser(...args) },
  updateUserPii(...args) { return runtimeRef.current.updateUserPii(...args) },
  previewUserDerivedProfile(...args) { return runtimeRef.current.previewUserDerivedProfile(...args) },
  listUser() { return Promise.resolve({ rows: [], total: 0 }) },
  getUserSetupSummary() { return Promise.resolve({ data: {} }) },
  deptTreeSelect() { return Promise.resolve({ data: [] }) },
  delUser() { return Promise.resolve() },
  resetUserPwd() { return Promise.resolve() },
  changeUserStatus() { return Promise.resolve() }
}

function loadComponent(api) {
  const file = path.resolve(__dirname, "../src/views/system/user/index.vue")
  const script = fs.readFileSync(file, "utf8").match(/<script>([\s\S]*?)<\/script>/)[1]
  const code = babel.transformSync(script, {
    filename: file,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
  }).code
  const testModule = { exports: {} }
  vm.runInNewContext(code, {
    module: testModule,
    exports: testModule.exports,
    process: { env: {} },
    Promise,
    setTimeout: fakeSetTimeout,
    clearTimeout: fakeClearTimeout,
    require(name) {
      if (name === "@/api/system/user") return api
      if (name === "@/utils/userPiiPatch") return userPiiPatch
      if (name === "@/plugins/cache") {
        return { local: { getJSON() { return null }, setJSON() {}, remove() {} } }
      }
      if (name === "@/views/hr/components/signingProfileOptions") {
        return {
          CONTRACT_TERM_OPTIONS: [],
          CONTRACT_TYPE_OPTIONS: [],
          SOCIAL_TYPE_OPTIONS: [],
          signingOptionsForKey() { return null },
          signingProfileLabel(_options, value) { return value }
        }
      }
      if (name === "@/utils/exportConfirm") return { confirmExportAction: () => Promise.resolve() }
      return {}
    }
  }, { filename: file })
  return testModule.exports.default
}

const component = loadComponent(userApi)
assert.ok(component && component.methods && component.methods.persistUserForm, "user page component must load before scenarios run")

function createRuntime() {
  const queues = {}
  const log = { users: [], piiReads: [], updates: [], creates: [], piiWrites: [], previews: [] }
  function enqueue(name) {
    const item = deferred()
    queues[name] = queues[name] || []
    queues[name].push(item)
    return item.promise
  }
  function settle(name, value, isError) {
    const list = queues[name] || []
    const item = list.shift()
    if (!item) throw new Error("no pending " + name)
    if (isError) item.reject(value)
    else item.resolve(value)
  }
  const runtime = {
    log,
    pending() {
      return Object.keys(queues).filter(key => queues[key] && queues[key].length)
    },
    resolve(name, value) { settle(name, value, false) },
    reject(name, error) { settle(name, error, true) },
    getUser(id, config) {
      const key = id == null || id === "" ? "user:new" : "user:" + id
      log.users.push({ id, silentError: !!(config && config.silentError === true) })
      return enqueue(key)
    },
    getUserPii(id, reason, config) {
      log.piiReads.push({ id, reason, silentError: !!(config && config.silentError === true) })
      return enqueue("pii:" + id)
    },
    updateUser(payload, config) {
      log.updates.push(payload)
      log.updateSilent = !!(config && config.silentError === true)
      return enqueue("update:" + payload.userId)
    },
    addUser(payload, config) {
      log.creates.push(payload)
      log.createSilent = !!(config && config.silentError === true)
      return enqueue("add")
    },
    updateUserPii(id, data, reason, afterCreate, config) {
      log.piiWrites.push({
        id,
        data,
        reason,
        afterCreate: Boolean(afterCreate),
        silentError: !!(config && config.silentError === true)
      })
      return enqueue("piiWrite:" + id + ":" + (afterCreate ? "create" : "edit"))
    },
    previewUserDerivedProfile(payload, config) {
      log.previews.push(payload)
      log.previewSilent = !!(config && config.silentError === true)
      return enqueue("preview:" + log.previews.length)
    }
  }
  runtimeRef.current = runtime
  return runtime
}

function harness() {
  const instance = {
    $refs: {},
    $modal: { confirm() { return Promise.resolve() }, msgSuccess() {}, msgError() {}, msgWarning() {} },
    resetForm() {},
    $nextTick() { return Promise.resolve() },
    $set(obj, key, val) { if (obj) obj[key] = val },
    $createElement(tag, data, children) { return { tag, data, children } },
    $confirm() { return Promise.resolve() },
    $store: { state: { user: { businessFeatures: {} } }, getters: {} },
    $auth: {
      hasPermi() { return true },
      hasPermiAnd() { return true }
    }
  }
  Object.entries(component.methods).forEach(([key, fn]) => {
    if (typeof fn === "function") instance[key] = fn.bind(instance)
  })
  Object.assign(instance, component.data.call(instance))
  Object.entries(component.computed || {}).forEach(([key, fn]) => {
    if (typeof fn === "function") Object.defineProperty(instance, key, { get: fn.bind(instance), configurable: true })
  })
  return instance
}

function fireWatch(page, key, value) {
  const spec = component.watch[key]
  if (!spec) return
  const handler = typeof spec === "function" ? spec : spec.handler
  handler.call(page, value)
}

function mount(options = {}) {
  timers.length = 0
  const runtime = createRuntime()
  const messages = { success: [], error: [], warning: [] }
  const shopConfirms = []
  const page = harness()
  page.$store = {
    state: { user: { businessFeatures: { systemManagementUxV2: options.v2 === true } } },
    getters: {}
  }
  page.$confirm = options.confirm || (() => Promise.resolve())
  page.$modal = {
    msgSuccess(text) { messages.success.push(text) },
    msgError(text) { messages.error.push(text) },
    msgWarning(text) { messages.warning.push(text) },
    confirm() { return Promise.resolve() }
  }
  page.$refs.form = { validate: fn => fn(true) }
  page.getList = () => { page.listReloads = (page.listReloads || 0) + 1 }
  page.getDeptTree = () => {}
  page.getPostOptions = () => {}
  page.loadEmployeeStatusOptions = () => {}
  page.confirmShopScopeAfterCreate = user => { shopConfirms.push(user) }
  page.messages = messages
  page.shopConfirms = shopConfirms
  component.created.call(page)
  return { page, runtime }
}

const tick = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }

function userResponse(id, extra = {}) {
  return {
    data: {
      userId: id,
      userName: extra.userName || ("user" + id),
      nickName: extra.nickName || ("姓名" + id),
      deptId: extra.deptId || 10,
      status: "0",
      remark: extra.remark || "",
      profile: extra.profile || { emergencyContact: "联系人" + id, bankAccount: "bank-" + id }
    },
    posts: [{ postId: 1, postName: "岗位" }],
    roles: [{ roleId: 2, roleName: "角色" }],
    postIds: extra.postIds || [1],
    roleIds: extra.roleIds || [2]
  }
}

function piiBody(id, extra = {}) {
  return {
    email: extra.email || ("u" + id + "@example.com"),
    phonenumber: extra.phonenumber || "15900000000",
    sex: extra.sex || "1",
    emergencyContact: extra.emergencyContact || ("联系人" + id),
    bankAccount: extra.bankAccount || ("bank-" + id)
  }
}

async function openExisting(page, runtime, id, extra) {
  const pending = page.handleUpdate({ userId: id })
  await tick()
  runtime.resolve("user:" + id, userResponse(id, extra))
  await tick()
  runtime.resolve("pii:" + id, { data: piiBody(id, extra && extra.pii) })
  await pending
  await tick()
  timers.length = 0
}

async function openCreate(page, runtime) {
  page.handleAdd()
  await tick()
  runtime.resolve("user:new", {
    posts: [{ postId: 1, postName: "岗位" }],
    roles: [{ roleId: 2, roleName: "角色" }]
  })
  await tick()
  timers.length = 0
}

function fillCreateForm(page) {
  page.form.userName = "new-a"
  page.form.nickName = "新建A"
  page.form.deptId = 10
  page.form.roleIds = [2]
  page.form.postIds = [1]
  page.form.phonenumber = "15900000001"
  page.form.profile.emergencyContact = "家属A"
  page.form.profile.birthDate = "1998-01-02"
}

function asList(value) {
  assert.ok(Array.isArray(value), "list must be an array; a missing field is not an empty list")
  return Array.from(value)
}

function assertSameList(actual, expected, message) {
  assert.deepStrictEqual(asList(actual), asList(expected), message)
}

function assertSameRecord(actual, expected, message) {
  const label = message || "record"
  assert.ok(actual && typeof actual === "object" && !Array.isArray(actual), label + " actual must be a record")
  assert.ok(expected && typeof expected === "object" && !Array.isArray(expected), label + " expected must be a record")
  assertSameList(Object.keys(actual).sort(), Object.keys(expected).sort(), label + " keys")
  Object.keys(expected).forEach(key => {
    const actualValue = actual[key]
    const expectedValue = expected[key]
    if (Array.isArray(actualValue) || Array.isArray(expectedValue)) {
      assertSameList(actualValue, expectedValue, label + " " + key)
      return
    }
    if (
      actualValue !== null && expectedValue !== null &&
      typeof actualValue === "object" && typeof expectedValue === "object"
    ) {
      assertSameRecord(actualValue, expectedValue, label + " " + key)
      return
    }
    assert.strictEqual(actualValue, expectedValue, label + " " + key)
  })
}

function assertSettled(runtime, name) {
  assertSameList(runtime.pending(), [], name + " left pending requests")
}

function loadUserApi() {
  const calls = []
  const file = path.resolve(__dirname, "../src/api/system/user.js")
  const code = babel.transformSync(fs.readFileSync(file, "utf8"), {
    filename: file,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
  }).code
  const testModule = { exports: {} }
  vm.runInNewContext(code, {
    module: testModule,
    exports: testModule.exports,
    require(name) {
      if (name === "@/utils/request") {
        return function request(config) {
          calls.push(config)
          return Promise.resolve({ data: {} })
        }
      }
      if (name === "@/utils/common") {
        return {
          parseStrEmpty(value) {
            if (!value || value == "undefined" || value == "null") return ""
            return value
          }
        }
      }
      return {}
    }
  }, { filename: file })
  return { api: testModule.exports, calls }
}

const scenarios = [
  ["api-silent-error-contract", async () => {
    const { api, calls } = loadUserApi()
    const hijack = { silentError: true, url: "/hacked", method: "delete", data: { x: 1 }, params: { reasonCode: "HACK" }, headers: { Authorization: "no" } }
    api.getUser(12)
    api.getUser(12, hijack)
    api.getUserPii(12, "DATA_CORRECTION")
    api.getUserPii(12, "DATA_CORRECTION", hijack)
    api.updateUser({ userId: 12, nickName: "甲" })
    api.updateUser({ userId: 12, nickName: "甲" }, hijack)
    api.addUser({ userName: "new-a" })
    api.addUser({ userName: "new-a" }, hijack)
    api.updateUserPii(12, { emergencyContact: "家属" }, "DATA_CORRECTION", false)
    api.updateUserPii(12, { emergencyContact: "家属" }, "DATA_CORRECTION", false, hijack)
    api.updateUserPii(12, { emergencyContact: "家属" }, "DATA_CORRECTION", true, hijack)
    api.previewUserDerivedProfile({ deptId: 10 })
    api.previewUserDerivedProfile({ deptId: 10 }, hijack)

    function assertCall(index, expected) {
      const actual = calls[index]
      assert.equal(actual.url, expected.url)
      assert.equal(actual.method, expected.method)
      assert.equal(actual.silentError, expected.silentError)
      if (Object.prototype.hasOwnProperty.call(expected, "data")) {
        assertSameRecord(actual.data, expected.data, expected.url + " data")
      }
      if (Object.prototype.hasOwnProperty.call(expected, "params")) {
        assertSameRecord(actual.params, expected.params, expected.url + " params")
      }
      assert.equal(actual.headers, undefined)
    }

    assert.equal(calls.length, 13)
    assertCall(0, { url: "/system/user/12", method: "get", silentError: false })
    assertCall(1, { url: "/system/user/12", method: "get", silentError: true })
    assertCall(2, { url: "/system/user/12/pii", method: "get", silentError: false, params: { reasonCode: "DATA_CORRECTION" } })
    assertCall(3, { url: "/system/user/12/pii", method: "get", silentError: true, params: { reasonCode: "DATA_CORRECTION" } })
    assertCall(4, { url: "/system/user", method: "put", silentError: false, data: { userId: 12, nickName: "甲" } })
    assertCall(5, { url: "/system/user", method: "put", silentError: true, data: { userId: 12, nickName: "甲" } })
    assertCall(6, { url: "/system/user", method: "post", silentError: false, data: { userName: "new-a" } })
    assertCall(7, { url: "/system/user", method: "post", silentError: true, data: { userName: "new-a" } })
    assertCall(8, { url: "/system/user/12/pii", method: "put", silentError: false, params: { reasonCode: "DATA_CORRECTION" }, data: { emergencyContact: "家属" } })
    assertCall(9, { url: "/system/user/12/pii", method: "put", silentError: true, params: { reasonCode: "DATA_CORRECTION" }, data: { emergencyContact: "家属" } })
    assertCall(10, { url: "/system/user/12/pii-after-create", method: "put", silentError: true, params: { reasonCode: "DATA_CORRECTION" }, data: { emergencyContact: "家属" } })
    assertCall(11, { url: "/system/user/derived-preview", method: "post", silentError: false, data: { deptId: 10 } })
    assertCall(12, { url: "/system/user/derived-preview", method: "post", silentError: true, data: { deptId: 10 } })
  }],

  ["save-a-then-switch-b", async () => {
    const { page, runtime } = mount()
    await openExisting(page, runtime, 1)
    assert.equal(runtime.log.users[0].silentError, true)
    assert.equal(runtime.log.piiReads[0].silentError, true)
    page.form.nickName = "甲改名"
    page.form.profile.emergencyContact = "甲紧急联系人"
    page.piiReasonCode = "DATA_CORRECTION"
    page.submitForm()
    await tick()
    assert.equal(runtime.log.updates.length, 1)
    assert.equal(runtime.log.updates[0].userId, 1)
    assertSameList(runtime.log.updates[0].roleIds, [2], "frozen save must keep A's roleIds")
    assertSameList(runtime.log.updates[0].postIds, [1], "frozen save must keep A's postIds")
    page.form.roleIds.push(99)
    page.form.profile.emergencyContact = "保存后改掉"
    page.form.userId = 999
    const switched = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2, { nickName: "乙原名" }))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2, { emergencyContact: "乙联系人" }) })
    await switched
    await tick()
    timers.length = 0
    page.form.nickName = "乙正在编辑"
    page.form.profile.emergencyContact = "乙输入中"
    runtime.resolve("update:1")
    await tick()
    assert.equal(runtime.log.piiWrites.length, 1, "base save of A must continue frozen PII")
    assert.equal(runtime.log.piiWrites[0].id, 1)
    assert.equal(runtime.log.piiWrites[0].afterCreate, false)
    assert.equal(runtime.log.piiWrites[0].reason, "DATA_CORRECTION")
    assert.equal(runtime.log.piiWrites[0].silentError, true)
    assert.equal(runtime.log.updateSilent, true)
    assertSameRecord(runtime.log.piiWrites[0].data, { emergencyContact: "甲紧急联系人" }, "frozen PII must keep A's submitted fields")
    assertSameList(runtime.log.updates[0].roleIds, [2], "mutating the live form must not change A's frozen roleIds")
    runtime.resolve("piiWrite:1:edit")
    await tick()
    assert.equal(page.open, true)
    assert.equal(page.form.userId, 2)
    assert.equal(page.form.nickName, "乙正在编辑")
    assert.equal(page.form.profile.emergencyContact, "乙输入中")
    assert.equal(page.messages.success.includes("修改成功"), false)
    assert.equal(page.piiLoaded, true)
    assertSettled(runtime, "save-a-then-switch-b")
  }],

  ["detail-pii-reverse-order", async () => {
    const { page, runtime } = mount()
    const first = page.handleUpdate({ userId: 1 })
    await tick()
    runtime.resolve("user:1", userResponse(1, { nickName: "甲" }))
    await tick()
    const second = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2, { nickName: "乙" }))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2, { emergencyContact: "乙PII" }) })
    await second
    await tick()
    runtime.resolve("pii:1", { data: piiBody(1, { emergencyContact: "甲迟到PII" }) })
    await first
    await tick()
    assert.equal(page.form.userId, 2)
    assert.equal(page.form.nickName, "乙")
    assert.equal(page.form.profile.emergencyContact, "乙PII")
    assert.equal(page.piiLoaded, true)
    assert.equal(page.piiLoadFailed, false)
    assert.equal(page.title, "修改用户")
    assertSettled(runtime, "detail-pii-reverse-order")
  }],

  ["stale-pii-failure-does-not-pollute-b", async () => {
    const { page, runtime } = mount()
    const first = page.handleUpdate({ userId: 1 })
    await tick()
    runtime.resolve("user:1", userResponse(1, { nickName: "甲" }))
    await tick()
    const second = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2, { nickName: "乙" }))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2, { emergencyContact: "乙PII" }) })
    await second
    await tick()
    page.form.nickName = "乙保持"
    runtime.reject("pii:1", new Error("stale pii read failed"))
    await first
    await tick()
    assert.equal(page.form.userId, 2)
    assert.equal(page.form.nickName, "乙保持")
    assert.equal(page.form.profile.emergencyContact, "乙PII")
    assert.equal(page.piiLoaded, true)
    assert.equal(page.piiLoadFailed, false)
    assert.equal(page.messages.warning.length, 0)
    assertSettled(runtime, "stale-pii-failure-does-not-pollute-b")
  }],

  ["stale-get-user-failure-does-not-pollute-b", async () => {
    const { page, runtime } = mount()
    const first = page.handleUpdate({ userId: 1 })
    await tick()
    const second = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.reject("user:1", new Error("stale getUser failed"))
    await first
    await tick()
    runtime.resolve("user:2", userResponse(2, { nickName: "乙" }))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2, { emergencyContact: "乙PII" }) })
    await second
    await tick()
    assert.equal(runtime.log.users[0].silentError, true)
    assert.equal(runtime.log.users[1].silentError, true)
    assert.equal(page.form.userId, 2)
    assert.equal(page.form.nickName, "乙")
    assert.equal(page.piiLoaded, true)
    assert.equal(page.messages.error.length, 0)
    assert.equal(page.messages.warning.length, 0)
    assertSettled(runtime, "stale-get-user-failure-does-not-pollute-b")
  }],

  ["current-get-user-failure-can-retry", async () => {
    const { page, runtime } = mount()
    const first = page.handleUpdate({ userId: 1 })
    await tick()
    runtime.reject("user:1", new Error("current getUser failed"))
    await first
    await tick()
    assert.ok(page.messages.error.some(text => text.indexOf("用户资料加载失败") >= 0))
    assert.equal(page.open, false)
    assert.equal(page.piiLoaded, false)
    assert.notEqual(page.form.userId, 1)
    const second = page.handleUpdate({ userId: 1 })
    await tick()
    runtime.resolve("user:1", userResponse(1, { nickName: "重试成功" }))
    await tick()
    runtime.resolve("pii:1", { data: piiBody(1, { emergencyContact: "重试PII" }) })
    await second
    await tick()
    assert.equal(page.form.userId, 1)
    assert.equal(page.form.nickName, "重试成功")
    assert.equal(page.form.profile.emergencyContact, "重试PII")
    assert.equal(page.piiLoaded, true)
    assert.equal(page.open, true)
    assertSettled(runtime, "current-get-user-failure-can-retry")
  }],

  ["reopen-same-user", async () => {
    const { page, runtime } = mount()
    const first = page.handleUpdate({ userId: 1 })
    await tick()
    runtime.resolve("user:1", userResponse(1, { nickName: "旧请求" }))
    await tick()
    const second = page.handleUpdate({ userId: 1 })
    await tick()
    runtime.resolve("user:1", userResponse(1, { nickName: "新请求" }))
    await tick()
    runtime.resolve("pii:1", { data: piiBody(1, { emergencyContact: "旧PII" }) })
    await first
    await tick()
    assert.equal(page.piiLoaded, false, "stale reopen must not mark the new session loaded")
    assert.notEqual(page.form.profile.emergencyContact, "旧PII")
    runtime.resolve("pii:1", { data: piiBody(1, { emergencyContact: "新PII" }) })
    await second
    await tick()
    assert.equal(page.form.nickName, "新请求")
    assert.equal(page.form.profile.emergencyContact, "新PII")
    assert.equal(page.piiLoaded, true)
    assertSettled(runtime, "reopen-same-user")
  }],

  ["create-pii-fail-retry", async () => {
    const { page, runtime } = mount()
    await openCreate(page, runtime)
    fillCreateForm(page)
    page.submitForm()
    await tick()
    assert.equal(runtime.log.creates.length, 1)
    runtime.resolve("add", {
      data: { temporaryCredential: { userId: 101, userName: "new-a", temporaryPassword: "once" } }
    })
    await tick()
    runtime.reject("piiWrite:101:create", new Error("pii failed"))
    await tick()
    assert.equal(runtime.log.creates.length, 1)
    assert.equal(page.piiRetryUserId, 101)
    assert.equal(page.form.userId, 101)
    assert.equal(page.piiSaveFailure, true)
    assert.equal(page.open, true)
    assert.ok(page.messages.error.some(text => text.indexOf("PII 未保存") >= 0))
    assert.equal(page.messages.success.length, 0)
    page.form.profile.emergencyContact = "重试家属"
    page.retryPiiAfterCreate()
    await tick()
    assert.equal(runtime.log.creates.length, 1, "retry must not create another account")
    assert.equal(runtime.log.piiWrites.length, 2)
    assert.equal(runtime.log.piiWrites[1].id, 101)
    assert.equal(runtime.log.piiWrites[1].afterCreate, true)
    assertSameRecord(runtime.log.piiWrites[1].data, {
      phonenumber: "15900000001",
      emergencyContact: "重试家属",
      birthDate: "1998-01-02"
    }, "retry PII must stay bound to A's snapshot")
    const switched = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2) })
    await switched
    await tick()
    runtime.resolve("piiWrite:101:create")
    await tick()
    assert.equal(page.open, true)
    assert.equal(page.form.userId, 2)
    assert.equal(page.shopConfirms.length, 0)
    assert.equal(page.messages.success.includes("个人信息保存成功"), false)
    assertSettled(runtime, "create-pii-fail-retry")
  }],

  ["create-pii-fail-after-switch", async () => {
    const { page, runtime } = mount()
    await openCreate(page, runtime)
    fillCreateForm(page)
    page.submitForm()
    await tick()
    runtime.resolve("add", {
      data: { temporaryCredential: { userId: 202, userName: "new-a", temporaryPassword: "once" } }
    })
    await tick()
    const switched = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2, { nickName: "乙保持" }))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2) })
    await switched
    await tick()
    page.form.nickName = "乙未污染"
    runtime.reject("piiWrite:202:create", new Error("pii failed"))
    await tick()
    assert.equal(runtime.log.creates.length, 1)
    assert.equal(page.form.userId, 2)
    assert.equal(page.form.nickName, "乙未污染")
    assert.equal(page.piiRetryUserId, null)
    assert.equal(page.piiSaveFailure, false)
    assert.equal(page.credentialVisible, false)
    assert.equal(page.title, "修改用户")
    assertSettled(runtime, "create-pii-fail-after-switch")
  }],

  ["validate-during-switch", async () => {
    const { page, runtime } = mount()
    await openExisting(page, runtime, 1)
    let validate
    page.$refs.form.validate = fn => { validate = fn }
    page.form.nickName = "甲待校验"
    page.submitForm()
    const switched = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2, { nickName: "乙" }))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2) })
    await switched
    await tick()
    validate(true)
    await tick()
    assert.equal(runtime.log.updates.length, 0)
    assert.equal(runtime.log.creates.length, 0)
    assert.equal(page.form.userId, 2)
    assert.equal(page.form.nickName, "乙")
    assertSettled(runtime, "validate-during-switch")
  }],

  ["confirm-during-switch", async () => {
    let confirm
    const { page, runtime } = mount({
      v2: true,
      confirm() { return new Promise(resolve => { confirm = resolve }) }
    })
    await openCreate(page, runtime)
    fillCreateForm(page)
    page.submitForm()
    await tick()
    assert.equal(typeof confirm, "function")
    const switched = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2) })
    await switched
    await tick()
    confirm()
    await tick()
    assert.equal(runtime.log.creates.length, 0)
    assert.equal(page.form.userId, 2)
    assertSettled(runtime, "confirm-during-switch")
  }],

  ["no-pii-change", async () => {
    const { page, runtime } = mount()
    await openExisting(page, runtime, 1)
    page.form.nickName = "只改姓名"
    page.form.roleIds = [2, 3]
    page.submitForm()
    await tick()
    runtime.resolve("update:1")
    await tick()
    assert.equal(runtime.log.piiWrites.length, 0, "role/name only edits must not call PII")
    assert.ok(page.messages.success.includes("修改成功"))
    assert.equal(page.open, false)
    assertSettled(runtime, "no-pii-change")
  }],

  ["pii-fail-not-full-success", async () => {
    const { page, runtime } = mount()
    await openExisting(page, runtime, 1)
    page.form.profile.emergencyContact = "失败联系人"
    page.submitForm()
    await tick()
    runtime.resolve("update:1")
    await tick()
    runtime.reject("piiWrite:1:edit", new Error("pii failed"))
    await tick()
    assert.equal(page.messages.success.includes("修改成功"), false)
    assert.ok(page.messages.error.some(text => text.indexOf("PII 未保存") >= 0))
    assert.equal(page.open, true)
    assert.equal(page.form.userId, 1)
    assertSettled(runtime, "pii-fail-not-full-success")
  }],

  ["base-save-failure-settles", async () => {
    const { page, runtime } = mount()
    await openExisting(page, runtime, 1)
    page.form.nickName = "基础保存失败"
    page.submitForm()
    await tick()
    runtime.reject("update:1", new Error("base failed"))
    await tick()
    assert.equal(page.messages.success.includes("修改成功"), false)
    assert.ok(page.messages.error.some(text => text.indexOf("账号基础信息保存失败") >= 0))
    assert.equal(page.open, true)
    assert.equal(page.form.userId, 1)
    assert.equal(runtime.log.piiWrites.length, 0)
    assert.equal(runtime.log.updateSilent, true)
    assertSettled(runtime, "base-save-failure-settles")
  }],

  ["confirm-persist-failure-settles", async () => {
    let confirm
    const { page, runtime } = mount({
      v2: true,
      confirm() { return new Promise(resolve => { confirm = resolve }) }
    })
    await openCreate(page, runtime)
    fillCreateForm(page)
    page.submitForm()
    await tick()
    confirm()
    await tick()
    assert.equal(runtime.log.creates.length, 1)
    runtime.reject("add", new Error("create failed"))
    await tick()
    assert.equal(page.messages.success.length, 0)
    assert.ok(page.messages.error.some(text => text.indexOf("账号创建失败") >= 0))
    assert.equal(page.open, true)
    assert.strictEqual(page.form.userId, undefined)
    assert.equal(page.credentialVisible, false)
    assert.equal(runtime.log.createSilent, true)
    assertSettled(runtime, "confirm-persist-failure-settles")
  }],

  ["happy-path", async () => {
    const { page, runtime } = mount()
    await openExisting(page, runtime, 1)
    page.form.profile.emergencyContact = "成功联系人"
    page.submitForm()
    await tick()
    runtime.resolve("update:1")
    await tick()
    runtime.resolve("piiWrite:1:edit")
    await tick()
    assert.ok(page.messages.success.includes("修改成功"))
    assert.equal(page.open, false)
    assert.equal(page.form.userId, 1)
    assertSettled(runtime, "happy-path")
  }],

  ["stale-preview-and-destroy", async () => {
    const { page, runtime } = mount()
    await openExisting(page, runtime, 1)
    fireWatch(page, "form.deptId")
    flushTimers()
    await tick()
    assert.equal(runtime.log.previews.length, 1)
    assert.equal(runtime.log.previewSilent, true)
    const switched = page.handleUpdate({ userId: 2 })
    await tick()
    runtime.resolve("user:2", userResponse(2, { profile: { emergencyContact: "乙", companyName: "乙公司" } }))
    await tick()
    runtime.resolve("pii:2", { data: piiBody(2) })
    await switched
    await tick()
    timers.length = 0
    runtime.resolve("preview:1", { data: { companyName: "甲派生公司" } })
    await tick()
    assert.notEqual(page.form.profile.companyName, "甲派生公司")
    fireWatch(page, "form.deptId")
    flushTimers()
    await tick()
    assert.equal(runtime.log.previews.length, 2)
    component.beforeDestroy.call(page)
    runtime.resolve("preview:2", { data: { companyName: "销毁后派生" } })
    await tick()
    assert.notEqual(page.form.profile.companyName, "销毁后派生")
    assertSettled(runtime, "stale-preview-and-destroy")
  }]
]

const EXPECTED_SCENARIOS = scenarios.map(item => item[0])

async function run() {
  const completed = []
  for (const [name, fn] of scenarios) {
    await fn()
    completed.push(name)
  }
  assertSameList(completed, EXPECTED_SCENARIOS, "every isolation scenario must actually run")
  assert.equal(timers.filter(Boolean).length, 0, "no leftover timers")
}

Promise.resolve()
  .then(run)
  .then(() => {
    console.log("userSaveTargetIsolation: real component methods passed (" + EXPECTED_SCENARIOS.length + " scenarios)")
  })
  .catch(error => {
    console.error(error && error.stack ? error.stack : error)
    process.exitCode = 1
  })
