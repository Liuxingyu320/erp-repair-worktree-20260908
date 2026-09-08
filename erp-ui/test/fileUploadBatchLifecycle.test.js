const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const componentPath = path.resolve(__dirname, "../src/components/FileUpload/index.vue")

function loadComponent() {
  const source = fs.readFileSync(componentPath, "utf8")
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, "FileUpload must expose a script block")
  const transformed = babel.transformSync(script[1], {
    filename: componentPath,
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
      if (request === "@/utils/auth") return { getToken() { return "token" } }
      if (request === "@/utils/sessionMode") {
        return {
          buildSessionAuthHeaders(token) { return token ? { Authorization: `Bearer ${token}` } : {} },
          applySessionAuthHeaders(headers, token) {
            delete headers.Authorization
            if (token) headers.Authorization = `Bearer ${token}`
            return headers
          },
          shouldUseSessionCredentials() { return false }
        }
      }
      if (request === "@/api/system/file") return { deleteFile() { return Promise.resolve() } }
      if (request === "@/utils/urlSecurity") {
        return { sanitizeFileUrl(value) { return /^javascript:/i.test(String(value || "")) ? "" : String(value || "") } }
      }
      if (request === "@/utils/requestSecurity") {
        return { safeTrustedApiUrl(base, action) { return String(base || "") + String(action || "") } }
      }
      if (request === "sortablejs") return { __esModule: true, default: { create() {} } }
      throw new Error(`unexpected dependency: ${request}`)
    },
    process: { env: { VUE_APP_BASE_API: "" } },
    Promise,
    Object,
    Array,
    String,
    Date
  }, { filename: componentPath })
  return module.exports.default || module.exports
}

function createHarness(component, existing = [], options = {}) {
  const events = []
  const errors = []
  const removed = []
  const loading = { opens: 0, closes: 0 }
  const props = {
    action: "/file/upload",
    fileType: ["pdf"],
    fileSize: 5,
    limit: 5,
    disabled: false,
    drag: false,
    isShowTip: true
  }
  const instance = Object.assign(component.data.call(props), props, {
    fileList: existing.map(item => Object.assign({}, item)),
    $set(target, key, value) { target[key] = value },
    $emit(type, value) { events.push({ type, value }) },
    $modal: {
      loading() { loading.opens += 1 },
      closeLoading() { loading.closes += 1 },
      msgError(message) { errors.push(message) }
    },
    $refs: {
      fileUpload: {
        uploadFiles: options.uploadFiles || [],
        handleRemove(file) {
          removed.push(file && file.uid)
          if (options.elementSpliceRemove) {
            this.uploadFiles.splice(this.uploadFiles.indexOf(file), 1)
          }
        }
      }
    }
  })
  Object.keys(component.methods).forEach(name => {
    instance[name] = component.methods[name].bind(instance)
  })
  return { instance, events, errors, removed, loading }
}

function file(uid, name = `${uid}.pdf`, size = 1024) {
  return { uid, name, size }
}

function success(url) {
  return { code: 200, data: { url } }
}

function urls(instance) {
  return Array.from(instance.fileList, item => item.url)
}

function register(harness, ...files) {
  files.forEach(item => assert.strictEqual(harness.instance.handleBeforeUpload(item), true))
}

function run() {
  const component = loadComponent()

  const networkLast = createHarness(component, [{ name: "/existing.pdf", url: "/existing.pdf" }])
  const networkA = file("network-a")
  const networkB = file("network-b")
  register(networkLast, networkA, networkB)
  networkLast.instance.handleUploadSuccess(success("/network-a.pdf"), networkA)
  assert.deepStrictEqual(networkLast.loading, { opens: 1, closes: 0 },
    "one settled file must not close loading while another uid remains pending")
  networkLast.instance.handleUploadError(new Error("offline"), networkB)
  assert.deepStrictEqual(urls(networkLast.instance), ["/existing.pdf", "/network-a.pdf"])
  assert.deepStrictEqual(networkLast.removed, [],
    "Element has already removed network failures before invoking on-error")
  assert.strictEqual(networkLast.events.length, 1)
  assert.deepStrictEqual(networkLast.loading, { opens: 1, closes: 1 })

  const elementNetworkA = file("element-network-a")
  const elementNetworkB = file("element-network-b")
  const elementUploadFiles = [elementNetworkA, elementNetworkB]
  const elementNetwork = createHarness(component, [], {
    uploadFiles: elementUploadFiles,
    elementSpliceRemove: true
  })
  register(elementNetwork, elementNetworkA, elementNetworkB)
  elementUploadFiles.splice(elementUploadFiles.indexOf(elementNetworkA), 1)
  elementNetwork.instance.handleUploadError(new Error("network"), elementNetworkA)
  assert.deepStrictEqual(elementUploadFiles.map(item => item.uid), ["element-network-b"],
    "on-error must not call handleRemove after Element has already removed the failed file")
  assert.deepStrictEqual(elementNetwork.removed, [])
  assert.strictEqual(elementNetwork.loading.closes, 0)
  elementNetwork.instance.handleUploadSuccess(success("/element-network-b.pdf"), elementNetworkB)
  assert.deepStrictEqual(urls(elementNetwork.instance), ["/element-network-b.pdf"])
  assert.deepStrictEqual(elementNetwork.loading, { opens: 1, closes: 1 })

  const businessFirst = createHarness(component)
  const businessA = file("business-a")
  const businessB = file("business-b")
  register(businessFirst, businessA, businessB)
  businessFirst.instance.handleUploadSuccess({ code: 500, msg: "业务失败" }, businessA)
  assert.strictEqual(businessFirst.loading.closes, 0)
  businessFirst.instance.handleUploadSuccess(success("/business-b.pdf"), businessB)
  assert.deepStrictEqual(urls(businessFirst.instance), ["/business-b.pdf"])
  assert.deepStrictEqual(businessFirst.removed, ["business-a"])
  assert.strictEqual(businessFirst.events.length, 1)
  assert.deepStrictEqual(businessFirst.loading, { opens: 1, closes: 1 })

  const allFailed = createHarness(component, [{ name: "/kept.pdf", url: "/kept.pdf" }])
  const failedA = file("failed-a")
  const failedB = file("failed-b")
  register(allFailed, failedA, failedB)
  allFailed.instance.handleUploadError(new Error("offline"), failedB)
  allFailed.instance.handleUploadSuccess({ code: 400, msg: "invalid" }, failedA)
  assert.deepStrictEqual(urls(allFailed.instance), ["/kept.pdf"])
  assert.strictEqual(allFailed.events.length, 0)
  assert.deepStrictEqual(allFailed.removed, ["failed-a"])
  assert.deepStrictEqual(allFailed.loading, { opens: 1, closes: 1 })

  const reverseSuccess = createHarness(component)
  const selectedFirst = file("selected-first")
  const selectedSecond = file("selected-second")
  register(reverseSuccess, selectedFirst, selectedSecond)
  reverseSuccess.instance.handleUploadSuccess(success("/second.pdf"), selectedSecond)
  reverseSuccess.instance.handleUploadSuccess(success("/first.pdf"), selectedFirst)
  assert.deepStrictEqual(urls(reverseSuccess.instance), ["/first.pdf", "/second.pdf"],
    "successful files must be committed in registration order, not callback order")
  assert.strictEqual(reverseSuccess.events.length, 1)

  const validation = createHarness(component)
  const invalid = file("invalid", "invalid.exe")
  const valid = file("valid", "VALID.PDF")
  assert.strictEqual(validation.instance.handleBeforeUpload(invalid), false)
  assert.deepStrictEqual(validation.loading, { opens: 0, closes: 0 })
  assert.strictEqual(Object.keys(validation.instance.uploadOperations).length, 0)
  register(validation, valid)
  validation.instance.handleUploadSuccess(success("/valid.pdf"), valid)
  assert.deepStrictEqual(validation.loading, { opens: 1, closes: 1 })

  const duplicateCallbacks = createHarness(component)
  const stable = file("stable")
  register(duplicateCallbacks, stable)
  duplicateCallbacks.instance.handleUploadSuccess(success("/stable.pdf"), stable)
  const duplicateSnapshot = {
    urls: urls(duplicateCallbacks.instance),
    emits: duplicateCallbacks.events.length,
    removes: duplicateCallbacks.removed.length,
    closes: duplicateCallbacks.loading.closes,
    errors: duplicateCallbacks.errors.length
  }
  duplicateCallbacks.instance.handleUploadSuccess(success("/contradictory.pdf"), stable)
  duplicateCallbacks.instance.handleUploadError(new Error("late failure"), stable)
  duplicateCallbacks.instance.handleUploadSuccess(success("/unknown.pdf"), file("unknown"))
  assert.deepStrictEqual({
    urls: urls(duplicateCallbacks.instance),
    emits: duplicateCallbacks.events.length,
    removes: duplicateCallbacks.removed.length,
    closes: duplicateCallbacks.loading.closes,
    errors: duplicateCallbacks.errors.length
  }, duplicateSnapshot, "duplicate, contradictory, and unknown callbacks must be no-ops")

  const missingUrl = createHarness(component)
  const missing = file("missing-url")
  register(missingUrl, missing)
  missingUrl.instance.handleUploadSuccess({ code: 200, data: {} }, missing)
  missingUrl.instance.handleUploadError(new Error("duplicate failure"), missing)
  assert.deepStrictEqual(missingUrl.removed, ["missing-url"])
  assert.strictEqual(missingUrl.events.length, 0)
  assert.strictEqual(missingUrl.errors.length, 1)
  assert.deepStrictEqual(missingUrl.loading, { opens: 1, closes: 1 })

  const overlapping = createHarness(component)
  const overlapA = file("overlap-a")
  const overlapB = file("overlap-b")
  const overlapC = file("overlap-c")
  register(overlapping, overlapA, overlapB)
  overlapping.instance.handleUploadSuccess(success("/overlap-a.pdf"), overlapA)
  register(overlapping, overlapC)
  overlapping.instance.handleUploadSuccess(success("/overlap-c.pdf"), overlapC)
  assert.strictEqual(overlapping.loading.closes, 0)
  overlapping.instance.handleUploadSuccess(success("/overlap-b.pdf"), overlapB)
  assert.deepStrictEqual(urls(overlapping.instance), [
    "/overlap-a.pdf", "/overlap-b.pdf", "/overlap-c.pdf"
  ])
  assert.strictEqual(overlapping.events.length, 1)
  assert.deepStrictEqual(overlapping.loading, { opens: 1, closes: 1 },
    "overlapping selections in one continuous busy period must share one loading and emit")

  const consecutive = createHarness(component)
  const roundOne = file("round-one")
  register(consecutive, roundOne)
  consecutive.instance.handleUploadSuccess(success("/round-one.pdf"), roundOne)
  const roundTwo = file("round-two")
  register(consecutive, roundTwo)
  consecutive.instance.handleUploadSuccess(success("/round-two.pdf"), roundTwo)
  assert.deepStrictEqual(consecutive.loading, { opens: 2, closes: 2 })
  assert.strictEqual(consecutive.events.length, 2)
  consecutive.instance.handleUploadError(new Error("obsolete"), roundOne)
  assert.deepStrictEqual(consecutive.loading, { opens: 2, closes: 2 },
    "callbacks from an old busy period must not affect a newer completed period")

  const retry = createHarness(component)
  const retryFailed = file("retry-failed")
  register(retry, retryFailed)
  retry.instance.handleUploadError(new Error("offline"), retryFailed)
  const retryNewUid = file("retry-new")
  register(retry, retryNewUid)
  retry.instance.handleUploadSuccess(success("/retried.pdf"), retryNewUid)
  assert.deepStrictEqual(urls(retry.instance), ["/retried.pdf"])
  assert.deepStrictEqual(retry.removed, [])
  assert.deepStrictEqual(retry.loading, { opens: 2, closes: 2 })

  const sameUidRetry = createHarness(component)
  const oldAttempt = file("same-uid", "old.pdf")
  register(sameUidRetry, oldAttempt)
  sameUidRetry.instance.handleUploadError(new Error("old failed"), oldAttempt)
  assert.strictEqual(sameUidRetry.instance.handleBeforeUpload(oldAttempt), false,
    "the exact retired file object must be rejected because its old callback is indistinguishable")
  const newAttempt = file("same-uid", "new.pdf")
  register(sameUidRetry, newAttempt)
  sameUidRetry.instance.handleUploadSuccess(success("/late-old.pdf"), oldAttempt)
  assert.strictEqual(sameUidRetry.instance.pendingUploadCount(), 1,
    "a late callback from the old object must not settle a same-uid new attempt")
  sameUidRetry.instance.handleUploadSuccess(success("/new-attempt.pdf"), newAttempt)
  assert.deepStrictEqual(urls(sameUidRetry.instance), ["/new-attempt.pdf"])
  assert.deepStrictEqual(sameUidRetry.removed, [])
  assert.deepStrictEqual(sameUidRetry.loading, { opens: 2, closes: 2 })

  const boundedLedger = createHarness(component)
  const firstBoundedFile = file("bounded-0")
  for (let index = 0; index < 101; index += 1) {
    const item = index === 0 ? firstBoundedFile : file(`bounded-${index}`)
    register(boundedLedger, item)
    boundedLedger.instance.handleUploadSuccess(success(`/bounded-${index}.pdf`), item)
  }
  assert.strictEqual(Object.keys(boundedLedger.instance.uploadOperations).length, 0,
    "terminal operations must be removed from the active ledger")
  assert.ok(boundedLedger.instance.uploadTombstones.length <= 32,
    "strong tombstones must remain bounded after many busy periods")
  assert.strictEqual(boundedLedger.instance.handleBeforeUpload(firstBoundedFile), false,
    "weak retired identity tracking must keep an exact old object from being reused")

  const anchored = createHarness(component)
  const anchor = file("anchor")
  const oldSameUid = file("anchored-retry")
  register(anchored, anchor, oldSameUid)
  anchored.instance.handleUploadError(new Error("first attempt failed"), oldSameUid)
  const newSameUid = file("anchored-retry")
  register(anchored, newSameUid)
  anchored.instance.handleUploadSuccess(success("/late-old.pdf"), oldSameUid)
  assert.strictEqual(anchored.instance.pendingUploadCount(), 2)
  anchored.instance.handleUploadSuccess(success("/new-same-uid.pdf"), newSameUid)
  for (let index = 0; index < 101; index += 1) {
    const failed = file(`anchored-failure-${index}`)
    register(anchored, failed)
    anchored.instance.handleUploadError(new Error("expected failure"), failed)
    assert.deepStrictEqual(Object.keys(anchored.instance.uploadOperations), ["anchor"],
      "each terminal failure must leave only the long-running anchor active")
  }
  assert.strictEqual(anchored.instance.uploadPeriodResults[anchored.instance.uploadBusyPeriodId].successes.length, 1,
    "failed attempts must not accumulate in the per-period success buffer")
  assert.ok(anchored.instance.uploadTombstones.length <= 32)
  anchored.instance.handleUploadSuccess(success("/anchor.pdf"), anchor)
  assert.deepStrictEqual(urls(anchored.instance), ["/anchor.pdf", "/new-same-uid.pdf"])
  assert.strictEqual(Object.keys(anchored.instance.uploadOperations).length, 0)
  assert.strictEqual(Object.keys(anchored.instance.uploadPeriodResults).length, 0,
    "the per-period success buffer must be released as soon as the busy period settles")
  assert.deepStrictEqual(anchored.loading, { opens: 1, closes: 1 })
  assert.strictEqual(anchored.events.length, 1)

  const destroyed = createHarness(component)
  const destroyedA = file("destroyed-a")
  const destroyedB = file("destroyed-b")
  register(destroyed, destroyedA, destroyedB)
  destroyed.instance.handleUploadSuccess(success("/buffered-before-destroy.pdf"), destroyedA)
  component.beforeDestroy.call(destroyed.instance)
  component.beforeDestroy.call(destroyed.instance)
  assert.deepStrictEqual(destroyed.loading, { opens: 1, closes: 1 },
    "destroy must close only this component's owned loading and remain idempotent")
  assert.strictEqual(Object.keys(destroyed.instance.uploadOperations).length, 0)
  assert.strictEqual(Object.keys(destroyed.instance.uploadPeriodResults).length, 0)
  const destroyedSnapshot = {
    emits: destroyed.events.length,
    removes: destroyed.removed.length,
    errors: destroyed.errors.length
  }
  destroyed.instance.handleUploadSuccess(success("/too-late.pdf"), destroyedA)
  destroyed.instance.handleUploadError(new Error("too late"), destroyedB)
  assert.deepStrictEqual({
    emits: destroyed.events.length,
    removes: destroyed.removed.length,
    errors: destroyed.errors.length
  }, destroyedSnapshot, "callbacks after destroy must not emit, remove, or toast")

  const parentObject = { name: "/object.pdf", url: "/object.pdf" }
  const objectWatcher = { fileList: [], value: "must-not-be-used" }
  component.watch.value.handler.call(objectWatcher, parentObject)
  assert.strictEqual(objectWatcher.fileList.length, 1)
  assert.strictEqual(objectWatcher.fileList[0].url, "/object.pdf")
  assert.ok(objectWatcher.fileList[0].uid)
  assert.notStrictEqual(objectWatcher.fileList[0], parentObject)
  assert.strictEqual(parentObject.uid, undefined,
    "single-object value normalization must not mutate the parent-owned object")

  const parentArrayObject = { name: "/array.pdf", url: "/array.pdf" }
  const parentArray = [parentArrayObject, "/string.pdf"]
  const arrayWatcher = { fileList: [], value: "must-not-be-used" }
  component.watch.value.handler.call(arrayWatcher, parentArray)
  assert.deepStrictEqual(
    Array.from(arrayWatcher.fileList, item => item.url),
    ["/array.pdf", "/string.pdf"]
  )
  assert.notStrictEqual(arrayWatcher.fileList[0], parentArrayObject)
  assert.strictEqual(parentArrayObject.uid, undefined)
  assert.deepStrictEqual(parentArray, [parentArrayObject, "/string.pdf"],
    "array normalization must preserve the parent array and its objects")

  const unsafeResponse = createHarness(component)
  const unsafeFile = file("unsafe-response")
  register(unsafeResponse, unsafeFile)
  unsafeResponse.instance.handleUploadSuccess(success("javascript:alert(1)"), unsafeFile)
  assert.deepStrictEqual(urls(unsafeResponse.instance), [])
  assert.deepStrictEqual(unsafeResponse.removed, ["unsafe-response"])
  assert.ok(unsafeResponse.errors.some(message => message.includes("不安全的文件地址")))

  const allInvalid = createHarness(component)
  assert.strictEqual(allInvalid.instance.handleBeforeUpload(file("bad-type", "bad.exe")), false)
  assert.strictEqual(allInvalid.instance.handleBeforeUpload(file("bad-name", "bad,name.pdf")), false)
  assert.strictEqual(allInvalid.instance.handleBeforeUpload(
    file("bad-size", "large.pdf", 5 * 1024 * 1024)
  ), false)
  assert.strictEqual(Object.keys(allInvalid.instance.uploadOperations).length, 0)
  assert.deepStrictEqual(allInvalid.loading, { opens: 0, closes: 0 })
  assert.strictEqual(allInvalid.events.length, 0)

  console.log("file upload batch lifecycle tests passed")
}

try {
  run()
} catch (error) {
  console.error(error)
  process.exit(1)
}
