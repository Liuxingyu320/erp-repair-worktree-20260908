const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")
const compiler = require("../node_modules/vue-template-compiler")

const root = path.resolve(__dirname, "../src/views/mobile/hr")
const helperPath = path.join(root, "mobileOnboardingOwnerPicker.js")
const componentPath = path.join(root, "components/MobileOnboardingOwnerPicker.vue")
const read = file => fs.readFileSync(file, "utf8")

function transformModule(source, filename) {
  return babel.transformSync(source, {
    filename,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
}

function loadModule(file) {
  const module = { exports: {} }
  vm.runInNewContext(transformModule(read(file), file), {
    module,
    exports: module.exports,
    require(request) {
      if (request === "./mobileHrError") return require("../src/views/mobile/hr/mobileHrError")
      return require(request)
    }
  }, { filename: file })
  return module.exports
}

function loadComponent(api, helper, environment = {}) {
  const descriptor = compiler.parseComponent(read(componentPath))
  const compiled = compiler.compile(descriptor.template.content)
  assert.deepStrictEqual(compiled.errors, [], "owner picker template should compile")
  const module = { exports: {} }
  vm.runInNewContext(transformModule(descriptor.script.content, `${componentPath}.js`), {
    module,
    exports: module.exports,
    window: environment.window,
    document: environment.document,
    setTimeout,
    clearTimeout,
    require(request) {
      if (request === "@/api/hr/onboarding") return api
      if (request === "../mobileOnboardingOwnerPicker") return helper
      throw new Error(`unexpected dependency ${request}`)
    }
  }, { filename: componentPath })
  return module.exports.default || module.exports
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function flush() { return new Promise(resolve => setImmediate(resolve)) }
function plain(value) { return JSON.parse(JSON.stringify(value)) }

function bind(component, additions) {
  const target = Object.assign({}, component.data.call({}), additions)
  target.$nextTick = callback => callback && callback()
  target.$emit = () => {}
  Object.keys(component.methods).forEach(name => {
    target[name] = (...args) => component.methods[name].apply(target, args)
  })
  return target
}

const helper = loadModule(helperPath)
assert.strictEqual(helper.OWNER_PAGE_SIZE, 20)
assert.strictEqual(helper.OWNER_KEYWORD_MAX_LENGTH, 64)

const sanitized = helper.normalizeOwnerOption({
  userId: 7,
  label: "林晓",
  deptId: 20,
  deptName: "招聘部",
  deptPath: "集团 / 招聘部",
  employeeNo: "E007",
  phoneMasked: "138****8000",
  phonenumber: "13800138000",
  userName: "linxiao",
  roles: ["admin"]
})
assert.deepStrictEqual(plain(sanitized), {
  userId: 7,
  label: "林晓",
  deptId: 20,
  deptName: "招聘部",
  deptPath: "集团 / 招聘部",
  employeeNo: "E007",
  phoneMasked: "138****8000"
}, "client normalization must keep only the minimal masked projection")

assert.deepStrictEqual(
  plain(helper.mergeOwnerOptions([{ userId: 7, label: "林晓" }], [
    { userId: 7, label: "重复项" },
    { userId: 8, label: "周宁" }
  ]).map(item => item.userId)),
  [7, 8],
  "pagination should de-duplicate users without replacing the selected row"
)
assert.strictEqual(helper.ownerPickerError({ response: { status: 403 } }).type, "forbidden")
assert.strictEqual(
  helper.ownerPickerError({ response: { status: 401, data: { msg: "令牌不能为空" } } }).message,
  "登录状态已失效，请重新登录"
)
assert.ok(helper.ownerOptionMeta(sanitized).includes("E007"))
assert.ok(helper.ownerOptionMeta(sanitized).includes("138****8000"))

const storageValues = new Map()
const storage = {
  getItem(key) { return storageValues.has(key) ? storageValues.get(key) : null },
  setItem(key, value) { storageValues.set(key, value) }
}
const recentKey = helper.ownerRecentStorageKey(3, 20)
assert.ok(recentKey)
assert.notStrictEqual(recentKey, helper.ownerRecentStorageKey(4, 20), "recent choices must be account isolated")
assert.notStrictEqual(recentKey, helper.ownerRecentStorageKey(3, 21), "recent choices must be organization isolated")
const recentNow = 2000000000000
for (let userId = 1; userId <= 6; userId += 1) {
  helper.rememberRecentOwner(storage, recentKey, userId, recentNow + userId)
}
const storedRecent = JSON.parse(storage.getItem(recentKey))
assert.strictEqual(storedRecent.length, 5)
assert.deepStrictEqual(Object.keys(storedRecent[0]).sort(), ["timestamp", "userId"],
  "recent storage may contain only userId and timestamp")
storage.setItem(recentKey, JSON.stringify([
  { userId: 8, timestamp: recentNow - helper.OWNER_RECENT_TTL_MS - 1 },
  { userId: 9, timestamp: recentNow }
]))
assert.deepStrictEqual(plain(helper.readRecentOwners(storage, recentKey, recentNow).map(item => item.userId)), [9],
  "recent choices must expire after 30 days")

const componentSource = read(componentPath)
for (const fragment of [
  'role="dialog"',
  'role="combobox"',
  'role="listbox"',
  'role="option"',
  'aria-modal="true"',
  'aria-activedescendant',
  'requestSequence',
  'window.addEventListener("popstate"',
  'window.history.pushState',
  'window.history.back()',
  'pageSize: OWNER_PAGE_SIZE',
  '加载更多',
  '当前组织暂无人员',
  '无权查看',
  'event.key === "Escape"',
  'event.key !== "Tab"',
  'min-height: 44px'
]) assert.ok(componentSource.includes(fragment), `owner picker missing ${fragment}`)
assert.ok(read(helperPath).includes("你没有查看入职负责人的权限"), "403 needs a dedicated permission message")
assert.ok(!componentSource.includes("MobileEntityPicker"), "HR owner picker must not couple to the inventory picker")

async function run() {
  const older = deferred()
  const latest = deferred()
  let calls = 0
  const component = loadComponent({
    getHrOnboardingOwnerOptions() {
      calls += 1
      return calls === 1 ? older.promise : latest.promise
    }
  }, helper)
  const target = bind(component, {
    open: true,
    value: 7,
    valueLabel: "旧负责人",
    targetDeptId: 20
  })

  const oldRequest = target.reload()
  const newRequest = target.reload()
  latest.resolve({ rows: [{ userId: 8, label: "最后搜索" }], total: 1 })
  await newRequest
  older.resolve({ rows: [{ userId: 7, label: "过期搜索" }], total: 1 })
  await oldRequest
  assert.deepStrictEqual(plain(target.rows.map(item => item.label)), ["最后搜索"],
    "a stale response must not overwrite the latest owner search")

  const debounceOld = deferred()
  const debounceLatest = deferred()
  let debounceCalls = 0
  const debounceComponent = loadComponent({
    getHrOnboardingOwnerOptions() {
      debounceCalls += 1
      return debounceCalls === 1 ? debounceOld.promise : debounceLatest.promise
    }
  }, helper)
  const debounceTarget = bind(debounceComponent, {
    open: true,
    value: null,
    valueLabel: "",
    targetDeptId: 20
  })
  const inFlight = debounceTarget.reload()
  debounceTarget.keyword = "新关键词"
  debounceTarget.scheduleSearch()
  assert.strictEqual(debounceTarget.loading, true, "typing should immediately enter the pending-search loading state")
  debounceOld.resolve({ rows: [{ userId: 1, label: "旧结果" }], total: 1 })
  await inFlight
  assert.deepStrictEqual(plain(debounceTarget.rows), [],
    "a request invalidated during the debounce window must not publish old results")
  await new Promise(resolve => setTimeout(resolve, 275))
  assert.strictEqual(debounceCalls, 2)
  debounceLatest.resolve({ rows: [{ userId: 2, label: "新结果" }], total: 1 })
  await flush()
  assert.deepStrictEqual(plain(debounceTarget.rows.map(item => item.label)), ["新结果"])
  assert.strictEqual(debounceTarget.loading, false)

  const historyListeners = {}
  const historyActions = []
  const historyWindow = {
    location: { href: "https://erp.test/mobile/hr/onboarding/create" },
    history: {
      state: { route: "form" },
      pushState(state) { this.state = state; historyActions.push("push") },
      back() { historyActions.push("back") }
    },
    addEventListener(name, listener) { historyListeners[name] = listener },
    removeEventListener(name) { delete historyListeners[name] }
  }
  const historyDocument = { activeElement: null }
  const historyComponent = loadComponent({
    getHrOnboardingOwnerOptions() { return Promise.resolve({ rows: [], total: 0 }) }
  }, helper, { window: historyWindow, document: historyDocument })
  const historyTarget = bind(historyComponent, {
    open: true,
    value: null,
    valueLabel: "",
    targetDeptId: 20,
    $refs: {}
  })
  historyTarget.installHistoryGuard()
  assert.deepStrictEqual(historyActions, ["push"])
  historyListeners.popstate()
  assert.strictEqual(historyTarget.open, false, "system/browser back should close the owner overlay")
  assert.deepStrictEqual(historyActions, ["push"], "system back must not trigger a second navigation")

  historyTarget.open = true
  historyTarget.installHistoryGuard()
  historyTarget.closePicker()
  assert.deepStrictEqual(historyActions, ["push", "push", "back"],
    "button close should consume its temporary same-URL history guard")
  historyListeners.popstate()
  assert.strictEqual(historyTarget.suppressNextPopstate, false)

  const deniedComponent = loadComponent({
    getHrOnboardingOwnerOptions() {
      return Promise.reject({ response: { status: 403 } })
    }
  }, helper)
  const denied = bind(deniedComponent, {
    open: true,
    value: null,
    valueLabel: "",
    targetDeptId: 20
  })
  await denied.reload()
  assert.strictEqual(denied.errorType, "forbidden")
  assert.ok(denied.initialError.includes("权限"))

  const pageFailureComponent = loadComponent({
    getHrOnboardingOwnerOptions() {
      return Promise.reject(new Error("offline"))
    }
  }, helper)
  const existing = { userId: 9, label: "已选负责人" }
  const pageFailure = bind(pageFailureComponent, {
    open: true,
    value: 9,
    valueLabel: "已选负责人",
    targetDeptId: 20,
    rows: [existing],
    total: 40,
    pageNum: 1
  })
  await pageFailure.fetchPage(true)
  await flush()
  assert.deepStrictEqual(plain(pageFailure.rows), [existing], "pagination failure must preserve loaded and selected rows")
  assert.ok(pageFailure.pageError)

  const hydrationComponent = loadComponent({
    getHrOnboardingOwnerOptions(params) {
      assert.strictEqual(params.userIds, "11,12,13", "current and recent IDs must be sent for server re-authorization")
      return Promise.resolve({
        rows: [
          { userId: 11, label: "当前值" },
          { userId: 12, label: "仍有权限" }
        ],
        total: 2
      })
    }
  }, helper)
  const hydrationTarget = bind(hydrationComponent, {
    open: true,
    value: 11,
    valueLabel: "当前值",
    targetDeptId: 20,
    draftValue: 11,
    recentEntries: [
      { userId: 12, timestamp: recentNow },
      { userId: 13, timestamp: recentNow - 1 }
    ],
    recentStorageKey: recentKey,
    storage() { return storage }
  })
  await hydrationTarget.reload()
  assert.deepStrictEqual(plain(hydrationTarget.recentEntries.map(item => item.userId)), [12],
    "recent IDs absent from the authorized hydration response must be pruned")

  console.log("mobileHrOnboardingOwnerPicker tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
