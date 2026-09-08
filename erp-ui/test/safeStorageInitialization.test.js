const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const root = path.resolve(__dirname, "..")
const safeStoragePath = path.join(root, "src/utils/safeStorage.js")
const settingsPath = path.join(root, "src/store/modules/settings.js")
const lockPath = path.join(root, "src/store/modules/lock.js")

function compileModule(filename, dependencies = {}, globals = {}, globalDescriptors = {}) {
  const source = fs.readFileSync(filename, "utf8")
  const transformed = babel.transformSync(source, {
    filename,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const module = { exports: {} }
  const context = Object.assign({
    module,
    exports: module.exports,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(dependencies, request)) {
        return dependencies[request]
      }
      throw new Error(`unexpected dependency: ${request}`)
    },
    Object,
    Array,
    String,
    Boolean,
    JSON
  }, globals)
  Object.defineProperties(context, globalDescriptors)
  vm.runInNewContext(transformed, context, { filename })
  return module.exports
}

function createStorage(initial = {}, failures = {}) {
  const values = new Map(Object.entries(initial))
  const removed = []
  const writes = []
  return {
    values,
    removed,
    writes,
    getItem(key) {
      if (failures.get) throw new Error("SecurityError: getItem denied")
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      if (failures.set) throw new Error("QuotaExceededError: setItem denied")
      const text = String(value)
      writes.push([key, text])
      values.set(key, text)
    },
    removeItem(key) {
      if (failures.remove) throw new Error("SecurityError: removeItem denied")
      removed.push(key)
      values.delete(key)
    }
  }
}

const defaultSettings = {
  sideTheme: "theme-dark",
  showSettings: true,
  tagsView: true,
  tagsViewPersist: false,
  tagsIcon: true,
  tagsViewStyle: "default",
  fixedHeader: false,
  sidebarLogo: true,
  dynamicTitle: true,
  footerVisible: true,
  footerContent: "ERP"
}

function loadSafeStorage(storage, options = {}) {
  const globals = {}
  const globalDescriptors = {}
  if (options.throwOnGlobalAccess) {
    globalDescriptors.localStorage = {
      configurable: true,
      enumerable: true,
      get() {
        throw new Error("SecurityError: localStorage getter denied")
      }
    }
  } else if (storage !== undefined) {
    globals.localStorage = storage
  }
  return compileModule(safeStoragePath, {}, globals, globalDescriptors)
}

function loadSettings(storage, options) {
  const safeStorage = loadSafeStorage(storage, options)
  const module = compileModule(settingsPath, {
    "@/settings": { __esModule: true, default: defaultSettings },
    "@/utils/dynamicTitle": { useDynamicTitle() {} },
    "@/utils/safeStorage": safeStorage
  })
  return module.default || module
}

function loadLock(storage, options) {
  const safeStorage = loadSafeStorage(storage, options)
  const module = compileModule(lockPath, {
    "@/utils/mobileHrQueueState": { clearMobileHrQueueStateCache() {} },
    "@/utils/safeStorage": safeStorage
  })
  return module.default || module
}

const corruptSettings = createStorage({ "layout-setting": "{broken" })
const settingsFromCorruptJson = loadSettings(corruptSettings)
assert.strictEqual(settingsFromCorruptJson.state.theme, "#0B6B53")
assert.strictEqual(settingsFromCorruptJson.state.sideTheme, defaultSettings.sideTheme)
assert.deepStrictEqual(corruptSettings.removed, ["layout-setting"],
  "corrupt layout JSON must be removed before falling back")

const wrongSettingsType = createStorage({ "layout-setting": "[]" })
const settingsFromWrongType = loadSettings(wrongSettingsType)
assert.strictEqual(settingsFromWrongType.state.tagsView, defaultSettings.tagsView)
assert.deepStrictEqual(wrongSettingsType.removed, ["layout-setting"],
  "valid JSON with the wrong top-level type must be removed")

const validSettings = createStorage({
  "layout-setting": JSON.stringify({
    theme: "#123456",
    sideTheme: "theme-light",
    tagsView: false,
    fixedHeader: true
  })
})
const settingsFromValidValue = loadSettings(validSettings)
assert.strictEqual(settingsFromValidValue.state.theme, "#123456")
assert.strictEqual(settingsFromValidValue.state.sideTheme, "theme-light")
assert.strictEqual(settingsFromValidValue.state.tagsView, false)
assert.strictEqual(settingsFromValidValue.state.fixedHeader, true)
assert.deepStrictEqual(validSettings.removed, [],
  "valid persisted layout settings must stay compatible")

const deniedSettings = loadSettings(createStorage({}, { get: true }))
assert.strictEqual(deniedSettings.state.theme, "#0B6B53")
assert.strictEqual(deniedSettings.state.tagsView, defaultSettings.tagsView)

const hostileStorage = new Proxy({}, {
  get() {
    throw new Error("SecurityError: storage method access denied")
  }
})
assert.doesNotThrow(() => {
  assert.strictEqual(loadSettings(hostileStorage).state.theme, "#0B6B53")
  assert.strictEqual(loadLock(hostileStorage).state.isLock, false)
}, "a hostile Storage proxy must not abort store initialization")

const inaccessibleSettings = loadSettings(undefined, { throwOnGlobalAccess: true })
assert.strictEqual(inaccessibleSettings.state.sideTheme, defaultSettings.sideTheme,
  "a throwing localStorage getter must not abort module initialization")

const corruptLock = createStorage({
  "screen-lock": "\"true\"",
  "screen-lock-path": ""
})
const lockFromCorruptValues = loadLock(corruptLock)
assert.strictEqual(lockFromCorruptValues.state.isLock, false)
assert.strictEqual(lockFromCorruptValues.state.lockPath, "/index")
assert.deepStrictEqual(corruptLock.removed.sort(), ["screen-lock", "screen-lock-path"],
  "wrong lock value types must be cleaned independently")

const validLock = createStorage({
  "screen-lock": "true",
  "screen-lock-path": "/mobile/hr/onboarding/42/edit"
})
const lockFromValidValues = loadLock(validLock)
assert.strictEqual(lockFromValidValues.state.isLock, true)
assert.strictEqual(lockFromValidValues.state.lockPath, "/mobile/hr/onboarding/42/edit")
assert.deepStrictEqual(validLock.removed, [])

const deniedLock = loadLock(createStorage({}, { get: true }))
assert.strictEqual(deniedLock.state.isLock, false)
assert.strictEqual(deniedLock.state.lockPath, "/index")

const cleanupDenied = createStorage({
  "screen-lock": "{broken",
  "screen-lock-path": ""
}, { remove: true })
assert.doesNotThrow(() => loadLock(cleanupDenied),
  "a removeItem failure while cleaning corrupt data must be contained")

const writeDeniedStorage = createStorage({}, { set: true })
const lockWithDeniedWrites = loadLock(writeDeniedStorage)
assert.doesNotThrow(() => {
  lockWithDeniedWrites.mutations.SET_LOCK(lockWithDeniedWrites.state, true)
  lockWithDeniedWrites.mutations.SET_LOCK_PATH(lockWithDeniedWrites.state, "/mobile")
})
assert.strictEqual(lockWithDeniedWrites.state.isLock, true)
assert.strictEqual(lockWithDeniedWrites.state.lockPath, "/mobile")

console.log("safe storage initialization tests passed")
