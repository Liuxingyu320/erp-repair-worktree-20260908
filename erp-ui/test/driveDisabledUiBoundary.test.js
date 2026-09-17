const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")
const compiler = require("../node_modules/vue-template-compiler")
const Vue = require("../node_modules/vue")

const uiRoot = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")

const customerPath = "src/views/inventory/customer/index.vue"
const healthPath = "src/views/hr/healthCertificate/index.vue"
const customerSource = read(customerPath)
const healthSource = read(healthPath)

function sourceFiles(directory, result = []) {
  fs.readdirSync(directory, { withFileTypes: true }).forEach(entry => {
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      sourceFiles(absolute, result)
    } else if (/\.(?:js|vue|scss)$/.test(entry.name)) {
      result.push(absolute)
    }
  })
  return result
}

function isDriveImplementation(relativePath) {
  return relativePath.startsWith("src/views/drive/") ||
    relativePath.startsWith("src/views/mobile/drive/") ||
    relativePath.startsWith("src/api/drive/")
}

function auditExternalDriveReferences() {
  const files = sourceFiles(path.join(uiRoot, "src"))
  const visibleReferences = []
  const driveApiConsumers = []
  files.forEach(absolute => {
    const relative = path.relative(uiRoot, absolute).split(path.sep).join("/")
    if (isDriveImplementation(relative)) return
    const source = fs.readFileSync(absolute, "utf8")
    if (/云盘|网盘/.test(source)) visibleReferences.push(relative)
    if (/@\/api\/drive/.test(source)) driveApiConsumers.push(relative)
  })
  assert.deepStrictEqual(visibleReferences.sort(), [
    "src/components/TransferEvidencePicker.vue",
    "src/views/hr/healthCertificate/index.vue",
    "src/views/inventory/customer/index.vue",
    "src/views/mobile/mobileNavigation.js",
    "src/views/mobile/mobileRouteDefinitions.js"
  ], "drive-external user-visible references must stay within the audited protected files")
  assert.deepStrictEqual(driveApiConsumers.sort(), [
    "src/components/TransferEvidencePicker.vue",
    "src/views/inventory/customer/index.vue"
  ], "only the explicitly gated business forms and controlled transfer evidence picker may consume drive APIs")
}

function esm(defaultValue, named = {}) {
  return Object.assign({ __esModule: true, default: defaultValue }, named)
}

function loadComponent(relativePath, dependencies) {
  const source = read(relativePath)
  const descriptor = compiler.parseComponent(source)
  assert.ok(descriptor.script && descriptor.script.content, `${relativePath} should contain a script block`)
  const transformed = babel.transformSync(descriptor.script.content, {
    filename: path.resolve(uiRoot, `${relativePath}.js`),
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const module = { exports: {} }
  vm.runInNewContext(transformed, {
    module,
    exports: module.exports,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(dependencies, request)) return dependencies[request]
      throw new Error(`unexpected dependency in ${relativePath}: ${request}`)
    },
    Promise,
    Date,
    Math,
    Object,
    Array,
    String,
    Number,
    URL,
    setTimeout,
    clearTimeout
  }, { filename: relativePath })
  return module.exports.default || module.exports
}

function bindMethods(component, target) {
  Object.entries(component.methods || {}).forEach(([name, method]) => {
    target[name] = method.bind(target)
  })
  Object.entries(component.computed || {}).forEach(([name, getter]) => {
    Object.defineProperty(target, name, { configurable: true, get: () => getter.call(target) })
  })
  return target
}

function extractFormItem(source, label) {
  const descriptor = compiler.parseComponent(source)
  const escapedLabel = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  const match = descriptor.template.content.match(
    new RegExp(`<el-form-item[^>]*label=["']${escapedLabel}["'][\\s\\S]*?<\\/el-form-item>`)
  )
  assert.ok(match, `${label} form item should exist`)
  return match[0]
}

function countRenderedFormItems(fragment, label, driveEnabled) {
  const compiled = compiler.compile(`<div>${fragment}</div>`)
  assert.deepStrictEqual(compiled.errors, [], `${label} fragment should compile`)
  const instance = new Vue({
    data() {
      return {
        driveEnabled, mineDialog: false,
        form: {},
        mineForm: {},
        recentImages: [],
        recentFiles: []
      }
    },
    render: new Function(compiled.render),
    staticRenderFns: compiled.staticRenderFns.map(source => new Function(source))
  })
  const root = instance._render()
  let count = 0
  const visit = node => {
    if (!node) return
    if (node.data && node.data.attrs && node.data.attrs.label === label) count += 1
    ;(node.children || []).forEach(visit)
    if (node.componentOptions) (node.componentOptions.children || []).forEach(visit)
  }
  visit(root)
  return count
}

function flushPromises() {
  return new Promise(resolve => setImmediate(resolve))
}

async function verifyCustomerDriveBoundary() {
  let recentCalls = 0
  const driveApi = {
    listRecentDriveNodes() {
      recentCalls += 1
      return Promise.resolve({
        rows: [
          { nodeId: 1, nodeType: "FILE", contentType: "image/jpeg" },
          { nodeId: 2, nodeType: "FILE", contentType: "image/svg+xml" },
          { nodeId: 3, nodeType: "FOLDER", contentType: "image/png" }
        ]
      })
    }
  }
  const customerApi = {
    getCustomerServiceCardCapabilities: () => Promise.resolve({ data: { writeEnabled: true } })
  }
  const component = loadComponent(customerPath, {
    "@/utils/shopContext": { getSelectedDeptId: () => "20" },
    "@/utils/uiOperationScope": require("../src/utils/uiOperationScope"),
    "@/views/inventory/components/CustomerServiceHistory.vue": esm({}),
    "@/api/drive": driveApi,
    "@/api/inventory/customer": customerApi,
    vue: esm({ component() {} }),
    "./components/CustomerOpsSummary": esm({})
  })

  const disabled = bindMethods(component, {
    $store: { getters: { driveEnabled: false, permissions: ["*:*:*"] } },
    writeEnabled: false,
    recentImages: [{ nodeId: 99 }]
  })
  assert.strictEqual(disabled.driveEnabled, false, "wildcard administrators must still observe the disabled flag")
  await disabled.loadCapabilities()
  assert.strictEqual(recentCalls, 0, "disabled customer forms must not request recent drive files")
  assert.strictEqual(disabled.recentImages.length, 0, "disabled customer forms must clear stale drive choices")

  const enabled = bindMethods(component, {
    $store: { getters: { driveEnabled: true, permissions: [] } },
    writeEnabled: false,
    recentImages: []
  })
  assert.strictEqual(enabled.driveEnabled, true)
  await enabled.loadCapabilities()
  assert.strictEqual(recentCalls, 1, "enabled customer forms should retain the existing recent-file capability")
  assert.deepStrictEqual(Array.from(enabled.recentImages, item => item.nodeId), [1], "enabled customer forms should retain safe image filtering")
}

async function verifyHealthDriveBoundary() {
  // The business form mounts the controlled picker only while the feature and editor are open.
  assert.ok(healthSource.includes('v-if="driveEnabled" label="云盘附件"'))
  assert.ok(healthSource.includes('<drive-attachment-picker v-if="mineDialog"'))
  assert.ok(!healthSource.includes('listRecentDriveNodes'), 'opening the health page must not preload drive files')
  const picker = read('src/views/drive/components/DriveAttachmentPicker.vue')
  assert.ok(picker.includes('restoreUploadReceipts'), 'the controlled picker must retain unknown upload receipts')
  assert.ok(picker.includes('listDriveNodes(query)'), 'existing files must be searchable with server paging')
}

async function run() {
  auditExternalDriveReferences()
  const customerItem = extractFormItem(customerSource, "客户照片")
  const healthItem = extractFormItem(healthSource, "云盘附件")
  assert.strictEqual(countRenderedFormItems(customerItem, "客户照片", false), 0)
  assert.strictEqual(countRenderedFormItems(customerItem, "客户照片", true), 1)
  assert.strictEqual(countRenderedFormItems(healthItem, "云盘附件", false), 0)
  assert.strictEqual(countRenderedFormItems(healthItem, "云盘附件", true), 1)

  await verifyCustomerDriveBoundary()
  await verifyHealthDriveBoundary()

  const navigation = require("../src/views/mobile/mobileNavigation")
  const disabledDecision = navigation.getMobileRouteAccessDecision(
    "/mobile/drive",
    "STORE",
    ["*:*:*"],
    { driveEnabled: false }
  )
  assert.notStrictEqual(disabledDecision.path, "/mobile/drive")
  assert.strictEqual(disabledDecision.reason, "feature-disabled")
  assert.ok(disabledDecision.message.includes("暂未开启"))
  assert.ok(
    !navigation.getMobileQuickActions("STORE", ["*:*:*"], { driveEnabled: false })
      .some(item => item.path === "/mobile/drive"),
    "disabled mobile navigation must hide drive even from wildcard administrators"
  )
  assert.ok(
    navigation.getMobileQuickActions("STORE", ["drive:access"], { driveEnabled: true })
      .some(item => item.path === "/mobile/drive"),
    "enabled mobile navigation should remain recoverable"
  )

  const desktopRoutes = read("src/router/index.js")
  assert.ok(
    !/path:\s*["']\/drive(?:[\/"'])/.test(desktopRoutes),
    "desktop drive must not have a static route that bypasses the filtered server menu"
  )

  console.log("drive-disabled UI boundary tests passed")
}

run().catch(error => {
  console.error(error)
  process.exitCode = 1
})
