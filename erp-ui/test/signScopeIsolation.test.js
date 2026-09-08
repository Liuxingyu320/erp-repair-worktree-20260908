const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = relative => fs.readFileSync(path.join(root, relative), "utf8")

const requestSource = read("src/utils/request.js")
const signScopeSource = read("src/utils/signScopeContext.js")
const shopContextSource = read("src/utils/shopContext.js")
const packagePage = read("src/views/oa/signPackage/index.vue")
const taskPage = read("src/views/oa/signTask/index.vue")
const onboardDialog = read("src/views/hr/components/HrSignDataImportDialog.vue")
const signPackageApi = read("src/api/oa/signPackage.js")
const signScopeSelector = read("src/components/SignScopeSelector/index.vue")

assert.ok(signScopeSource.includes('SIGN_SCOPE_HEADER = "Sign-Scope-Dept-Id"'))
assert.ok(signScopeSource.includes('SIGN_SCOPE_TYPES = ["STORE", "COMPANY"]'))
assert.ok(requestSource.includes("/^\\/oa\\/sign(?:Package|Task)(?:\\/|$)/"))
assert.ok(requestSource.includes("config.headers[SIGN_SCOPE_HEADER]"))
assert.ok(requestSource.includes("isGlobalSignPlanRequest") &&
  requestSource.includes("!isGlobalSignPlanRequest"),
"global plan requests must not inherit the selected signing organization header")
assert.ok(signPackageApi.includes("export function listSignScopeOptions()") &&
  signPackageApi.includes("/oa/signPackage/scope/options"))
assert.ok(!shopContextSource.includes("Sign-Scope-Dept-Id"),
  "inventory context must remain independent of signing scope")
assert.ok(packagePage.includes("<sign-scope-selector") &&
  taskPage.includes("<sign-scope-selector") &&
  onboardDialog.includes("<sign-scope-selector"),
"every HR signing entry point must expose the dedicated signing organization")
assert.ok(signScopeSelector.includes("this.rootCompanyOptions(options)") &&
  signScopeSelector.includes('toUpperCase() === "COMPANY"') &&
  signScopeSelector.includes("!visibleDeptIds.has") &&
  signScopeSelector.includes('return `公司：${option.deptName || option.deptId}`'),
"signing organization selectors must only expose root company options")
assert.ok(signScopeSelector.includes('v-if="!loading && loadError"') &&
  signScopeSelector.includes("error && error.message") &&
  signScopeSelector.includes("签约组织加载失败，请稍后重试"),
"signing organization selector must surface authorization or request failures instead of reporting an empty scope")
assert.ok(packagePage.includes("activeTab === 'plan'") &&
  packagePage.includes("HR 统一方案库，适用于全部签约组织") &&
  packagePage.includes("delete payload.shopDeptId") &&
  packagePage.includes("delete payload.shopDeptName"),
"plan maintenance must be global and must not persist a client organization")
assert.ok(packagePage.includes("this.detail = null") &&
  packagePage.includes("this.verificationResult = null") &&
  packagePage.includes('this.activeTab === "record"'),
"changing signing organization must clear cached package records")

const stored = new Map([["selected_dept_id", "1185"], ["selected_dept_type", "STORE"]])
const contextModule = { exports: {} }
const transformedContext = signScopeSource
  .replace(/export const /g, "const ")
  .replace(/export function /g, "function ")
  .concat(`
module.exports = {
  getSelectedSignScopeContext,
  getSelectedSignScopeDeptId,
  setSelectedSignScope,
  clearSelectedSignScope
}`)
vm.runInNewContext(transformedContext, {
  module: contextModule,
  exports: contextModule.exports,
  sessionStorage: {
    getItem: key => stored.has(key) ? stored.get(key) : null,
    setItem: (key, value) => stored.set(key, String(value)),
    removeItem: key => stored.delete(key)
  }
})

const signScope = contextModule.exports
assert.strictEqual(signScope.setSelectedSignScope({
  deptId: "1157", deptName: "西安区域运营", deptType: "COMPANY"
}), true)
assert.strictEqual(signScope.getSelectedSignScopeDeptId(), "1157")
assert.strictEqual(signScope.getSelectedSignScopeContext().deptType, "COMPANY")
assert.strictEqual(stored.get("selected_dept_id"), "1185",
  "selecting company signing scope must not mutate inventory scope")
assert.strictEqual(signScope.setSelectedSignScope({
  deptId: "1200", deptName: "仓库", deptType: "WAREHOUSE"
}), false, "warehouse must not be accepted as a signing scope")
assert.strictEqual(signScope.getSelectedSignScopeDeptId(), "1157")
signScope.clearSelectedSignScope()
assert.strictEqual(signScope.getSelectedSignScopeDeptId(), null)
assert.strictEqual(stored.get("selected_dept_id"), "1185")

function createBroadcastChannelClass() {
  const channels = []
  return class FakeBroadcastChannel {
    constructor(name) {
      this.name = name
      this.onmessage = null
      channels.push(this)
    }
    postMessage(data) {
      channels
        .filter(channel => channel !== this && channel.name === this.name)
        .forEach(channel => {
          if (typeof channel.onmessage === "function") channel.onmessage({ data })
        })
    }
  }
}

function loadSignScopeTab(tabStorage, BroadcastChannel, extras = {}) {
  const tabModule = { exports: {} }
  const context = Object.assign({
    module: tabModule,
    exports: tabModule.exports,
    sessionStorage: {
      getItem: key => tabStorage.has(key) ? tabStorage.get(key) : null,
      setItem: (key, value) => tabStorage.set(key, String(value)),
      removeItem: key => tabStorage.delete(key)
    }
  }, extras)
  if (BroadcastChannel) context.BroadcastChannel = BroadcastChannel
  vm.runInNewContext(transformedContext, context)
  return tabModule.exports
}

const FakeBroadcastChannel = createBroadcastChannelClass()
const firstTabStorage = new Map()
const secondTabStorage = new Map()
const firstTabScope = loadSignScopeTab(firstTabStorage, FakeBroadcastChannel)
const secondTabScope = loadSignScopeTab(secondTabStorage, FakeBroadcastChannel)
assert.strictEqual(firstTabScope.setSelectedSignScope({
  deptId: "1157", deptName: "第一标签公司", deptType: "COMPANY"
}), true)
assert.strictEqual(secondTabScope.setSelectedSignScope({
  deptId: "1201", deptName: "第二标签公司", deptType: "COMPANY"
}), true)
firstTabScope.clearSelectedSignScope()
assert.strictEqual(firstTabScope.getSelectedSignScopeDeptId(), null)
assert.strictEqual(secondTabScope.getSelectedSignScopeDeptId(), null,
  "clearing an authentication boundary in one tab must clear session-scoped signing context in another tab")

const deniedStorage = {
  getItem() { throw new Error("SecurityError: getItem denied") },
  setItem() { throw new Error("SecurityError: setItem denied") },
  removeItem() { throw new Error("SecurityError: removeItem denied") }
}
const deniedModule = { exports: {} }
vm.runInNewContext(transformedContext, {
  module: deniedModule,
  exports: deniedModule.exports,
  sessionStorage: deniedStorage
})
assert.deepStrictEqual(Object.assign({}, deniedModule.exports.getSelectedSignScopeContext()), {
  deptId: "", deptName: "", deptType: "", valid: false
}, "SecurityError from getItem must produce an empty signing context")
assert.strictEqual(deniedModule.exports.setSelectedSignScope({
  deptId: "1157", deptName: "受限存储", deptType: "COMPANY"
}), false, "SecurityError from setItem must be contained")
assert.doesNotThrow(() => deniedModule.exports.clearSelectedSignScope(),
  "SecurityError from removeItem must not block authentication teardown")

function createStorageEventHub() {
  const values = new Map()
  const tabs = []
  let writes = 0
  return {
    getWriteCount() { return writes },
    createTab() {
      const listeners = []
      const tab = {
        window: {
          addEventListener(type, listener) {
            if (type === "storage") listeners.push(listener)
          },
          dispatchEvent() {}
        },
        localStorage: {
          getItem: key => values.has(key) ? values.get(key) : null,
          setItem(key, value) {
            writes += 1
            values.set(key, String(value))
            tabs.filter(candidate => candidate !== tab).forEach(candidate => {
              candidate.listeners.forEach(listener => listener({ key, newValue: String(value) }))
            })
          },
          removeItem: key => values.delete(key)
        },
        listeners
      }
      tabs.push(tab)
      return tab
    }
  }
}

function verifyStorageFallback(label, BroadcastChannel) {
  const hub = createStorageEventHub()
  const first = hub.createTab()
  const second = hub.createTab()
  const firstStorage = new Map()
  const secondStorage = new Map()
  const firstScope = loadSignScopeTab(firstStorage, BroadcastChannel, first)
  const secondScope = loadSignScopeTab(secondStorage, BroadcastChannel, second)
  firstScope.setSelectedSignScope({
    deptId: "1157", deptName: "第一标签公司", deptType: "COMPANY"
  })
  secondScope.setSelectedSignScope({
    deptId: "1201", deptName: "第二标签公司", deptType: "COMPANY"
  })
  firstScope.clearSelectedSignScope()
  assert.strictEqual(firstScope.getSelectedSignScopeDeptId(), null)
  assert.strictEqual(secondScope.getSelectedSignScopeDeptId(), null, label)
  assert.strictEqual(hub.getWriteCount(), 1,
    "a received storage clear must not be rebroadcast into a loop")
}

verifyStorageFallback(
  "localStorage storage events must propagate clears when BroadcastChannel is unavailable",
  undefined
)
verifyStorageFallback(
  "localStorage storage events must propagate clears when BroadcastChannel construction fails",
  class ConstructorFailureChannel {
    constructor() { throw new Error("BroadcastChannel constructor failed") }
  }
)
verifyStorageFallback(
  "localStorage storage events must propagate clears when BroadcastChannel postMessage fails",
  class PostFailureChannel {
    postMessage() { throw new Error("BroadcastChannel post failed") }
  }
)

console.log("sign scope isolation tests passed")
