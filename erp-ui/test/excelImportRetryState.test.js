const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const componentPath = path.resolve(__dirname, "../src/components/ExcelImportDialog/index.vue")

function loadComponent() {
  const source = fs.readFileSync(componentPath, "utf8")
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, "ExcelImportDialog must expose a script block")
  const transformed = babel.transformSync(script[1], {
    filename: componentPath,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const moduleRef = { exports: {} }
  vm.runInNewContext(transformed, {
    module: moduleRef,
    exports: moduleRef.exports,
    require(request) {
      if (request === "@/utils/importResult") {
        return {
          failureCsv() { return "" },
          credentialCsv() { return "" },
          parseLegacyImportResult() { return {} },
          parseImportResult() {
            return {
              committed: true,
              createdCount: 1,
              updatedCount: 0,
              successCount: 1,
              failureCount: 0,
              failures: [],
              temporaryCredentials: [],
              lines: []
            }
          }
        }
      }
      if (request === "@/utils/auth") return { getToken() { return "test-session" } }
      if (request === "@/utils/sessionMode") {
        return {
          applySessionAuthHeaders(headers, token) {
            delete headers.Authorization
            if (token) headers.Authorization = `Bearer ${token}`
            return headers
          },
          buildSessionAuthHeaders(token) {
            return token ? { Authorization: `Bearer ${token}` } : {}
          },
          shouldUseSessionCredentials() { return true }
        }
      }
      if (request === "@/utils/requestSecurity") {
        return {
          safeTrustedApiUrl(base, action) {
            return `${base || ""}${action || ""}`
          }
        }
      }
      throw new Error(`unexpected dependency: ${request}`)
    },
    process: { env: { VUE_APP_BASE_API: "/dev-api" } },
    Promise,
    Object,
    Array,
    String,
    Number,
    Math,
    Date,
    console
  }, { filename: componentPath })
  return moduleRef.exports.default || moduleRef.exports
}

function emptyResult() {
  return {
    committed: true,
    createdCount: 0,
    updatedCount: 0,
    successCount: 0,
    failureCount: 0,
    failures: [],
    temporaryCredentials: [],
    lines: []
  }
}

function createHarness(component, selectedFile) {
  const errors = []
  const events = []
  const uploadFiles = selectedFile ? [selectedFile] : []
  let submitCount = 0
  let clearCount = 0
  const props = {
    title: "导入",
    width: "400px",
    action: "/system/user/importData",
    templateAction: "",
    templateFileName: "template",
    updateSupportLabel: "覆盖"
  }
  const dataContext = Object.assign({}, props, {
    emptyImportResult
  })
  function emptyImportResult() {
    return emptyResult()
  }
  const instance = Object.assign(component.data.call(dataContext), props, {
    uploadUrl: "/dev-api/system/user/importData?updateSupport=0",
    visible: true,
    importResult: emptyResult(),
    $modal: {
      msgError(message) { errors.push(message) }
    },
    $emit(type) { events.push(type) },
    $refs: {
      uploadRef: {
        uploadFiles,
        submit() { submitCount += 1 },
        clearFiles() {
          clearCount += 1
          uploadFiles.splice(0, uploadFiles.length)
        }
      }
    }
  })
  Object.keys(component.methods).forEach(name => {
    instance[name] = component.methods[name].bind(instance)
  })
  return {
    instance,
    errors,
    events,
    uploadFiles,
    submitCount: () => submitCount,
    clearCount: () => clearCount
  }
}

function run() {
  const source = fs.readFileSync(componentPath, "utf8")
  assert.ok(source.includes(':on-error="handleUploadError"'))
  assert.ok(source.includes(':loading="isUploading"'))

  const component = loadComponent()
  const selected = {
    uid: "network-file",
    name: "employee.xlsx",
    status: "ready",
    percentage: 0,
    raw: {}
  }
  const network = createHarness(component, selected)

  network.instance.handleSubmit()
  assert.strictEqual(network.instance.isUploading, true)
  assert.strictEqual(network.submitCount(), 1)

  // Element UI removes a failed entry before invoking on-error.
  network.uploadFiles.splice(0, network.uploadFiles.length)
  network.instance.handleUploadError(new Error("Network Error"), selected)
  assert.strictEqual(network.instance.isUploading, false)
  assert.deepStrictEqual(network.uploadFiles, [selected])
  assert.strictEqual(selected.status, "ready")
  assert.strictEqual(network.errors.length, 1)
  assert.match(network.errors[0], /网络连接异常/)

  network.instance.handleUploadError(new Error("late duplicate"), selected)
  assert.strictEqual(network.errors.length, 1, "one failed attempt must produce only one toast")

  network.instance.handleSubmit()
  assert.strictEqual(network.instance.isUploading, true)
  assert.strictEqual(network.submitCount(), 2, "the same dialog and file must be retryable")
  network.instance.handleSuccess({ code: 200, data: { successCount: 1 } }, selected)
  assert.strictEqual(network.instance.isUploading, false)
  assert.strictEqual(network.instance.visible, false)
  assert.strictEqual(network.instance.resultVisible, true)
  assert.deepStrictEqual(network.events, ["success"])
  assert.strictEqual(network.uploadFiles.length, 0)

  const timeoutFile = { uid: "timeout", name: "timeout.xls", status: "ready", raw: {} }
  const timeout = createHarness(component, timeoutFile)
  timeout.instance.handleSubmit()
  timeout.uploadFiles.splice(0, timeout.uploadFiles.length)
  timeout.instance.handleUploadError(new Error("timeout of 10000ms exceeded"), timeoutFile)
  assert.strictEqual(timeout.instance.isUploading, false)
  assert.match(timeout.errors[0], /请求超时/)
  assert.deepStrictEqual(timeout.uploadFiles, [timeoutFile])

  const businessFile = { uid: "business", name: "business.xlsx", status: "ready", raw: {} }
  const business = createHarness(component, businessFile)
  business.instance.handleSubmit()
  business.instance.handleSuccess({ code: 500, msg: "表格内容校验失败" }, businessFile)
  assert.strictEqual(business.instance.isUploading, false)
  assert.strictEqual(business.errors.length, 1)
  assert.strictEqual(business.errors[0], "表格内容校验失败")
  assert.deepStrictEqual(business.uploadFiles, [businessFile])
  assert.strictEqual(businessFile.status, "ready")

  const synchronousFailureFile = {
    uid: "synchronous",
    name: "sync.xlsx",
    status: "ready",
    raw: {}
  }
  const synchronousFailure = createHarness(component, synchronousFailureFile)
  synchronousFailure.instance.$refs.uploadRef.submit = () => {
    throw new Error("offline")
  }
  assert.doesNotThrow(() => synchronousFailure.instance.handleSubmit())
  assert.strictEqual(synchronousFailure.instance.isUploading, false)
  assert.strictEqual(synchronousFailure.errors.length, 1)
  assert.deepStrictEqual(synchronousFailure.uploadFiles, [synchronousFailureFile])

  console.log("excel import retry state tests passed")
}

run()
