const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const componentPath = path.resolve(__dirname, "../src/views/mobile/contract/index.vue")

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function createPopup(options = {}) {
  const listeners = {}
  let href = "about:blank"
  const popup = {
    closed: false,
    closeCalls: 0,
    removeCalls: 0,
    addEventListener(type, listener) {
      if (!listeners[type]) listeners[type] = new Set()
      listeners[type].add(listener)
    },
    removeEventListener(type, listener) {
      this.removeCalls += 1
      if (listeners[type]) listeners[type].delete(listener)
    },
    close() {
      this.closed = true
      this.closeCalls += 1
    },
    fire(type) {
      if (listeners[type]) Array.from(listeners[type]).forEach(listener => listener())
    },
    listenerCount(type) {
      return listeners[type] ? listeners[type].size : 0
    }
  }
  popup.location = {}
  Object.defineProperty(popup.location, "href", {
    get() { return href },
    set(value) {
      if (options.navigationThrows) throw new Error("navigation denied")
      href = value
    }
  })
  return popup
}

function createRuntime() {
  let urlId = 0
  let timerId = 0
  let currentTime = 0
  const timers = new Map()
  const runtime = {
    downloads: [],
    createdUrls: [],
    revokedUrls: [],
    popupQueue: [],
    warnings: [],
    instances: [],
    timerDelays: [],
    setTimeout(callback, delay) {
      const id = ++timerId
      runtime.timerDelays.push(delay)
      timers.set(id, { callback, dueAt: currentTime + delay })
      return id
    },
    clearTimeout(id) { timers.delete(id) },
    advanceTime(milliseconds) {
      currentTime += milliseconds
      const due = Array.from(timers.entries()).filter(([, timer]) => timer.dueAt <= currentTime)
      due.forEach(([id, timer]) => {
        timers.delete(id)
        timer.callback()
      })
    }
  }
  runtime.URL = {
    createObjectURL() {
      const target = `blob:contract-${++urlId}`
      runtime.createdUrls.push(target)
      return target
    },
    revokeObjectURL(target) { runtime.revokedUrls.push(target) }
  }
  runtime.window = {
    devicePixelRatio: 1,
    addEventListener() {},
    removeEventListener() {},
    open() {
      return runtime.popupQueue.length ? runtime.popupQueue.shift() : null
    }
  }
  return runtime
}

function loadComponent(runtime) {
  const source = fs.readFileSync(componentPath, "utf8")
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, "mobile labor-contract component must expose a script block")
  assert.ok(/v-else-if="fileError"[\s\S]*?@click="loadContractFile"[\s\S]*?>重试</.test(source),
    "network preview failures must render a dedicated retry state instead of the empty-file state")
  const transformed = babel.transformSync(script[1], {
    filename: componentPath,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const module = { exports: {} }
  class FakeBlob {
    constructor(parts, options) {
      this.parts = parts
      this.type = options && options.type
    }
  }
  vm.runInNewContext(transformed, {
    module,
    exports: module.exports,
    require(request) {
      if (request === "@/api/oa/laborContract") {
        return {
          downloadLaborContractFile(contractId, kind) {
            const request = deferred()
            runtime.downloads.push({ contractId: String(contractId), kind, request })
            return request.promise
          },
          getMyLaborContract() { return Promise.resolve({ data: null }) },
          listMyLaborContracts() { return Promise.resolve({ rows: [] }) },
          signMyLaborContract() { return Promise.resolve({ data: null }) }
        }
      }
      if (request === "@/mixins/todoBusinessFocus") {
        return { createTodoPersonalFocusMixin() { return {} } }
      }
      if (request === "@/utils/protectedFileBlob") {
        return { normalizeProtectedFileBlob(blob) { return blob } }
      }
      if (request === "../mobileViewport") {
        return { startMobileViewportSync() {}, stopMobileViewportSync() {} }
      }
      if (request === "../mobileErrorMessage") {
        return require("../src/views/mobile/mobileErrorMessage")
      }
      throw new Error(`unexpected dependency: ${request}`)
    },
    Blob: FakeBlob,
    URL: runtime.URL,
    window: runtime.window,
    setTimeout: runtime.setTimeout,
    clearTimeout: runtime.clearTimeout,
    process: { env: { VUE_APP_BASE_API: "" } },
    Promise,
    Object,
    Array,
    String,
    Number,
    Math
  }, { filename: componentPath })
  return { component: module.exports.default || module.exports, FakeBlob }
}

function createInstance(component, runtime) {
  const instance = Object.assign(component.data(), {
    $refs: {},
    $router: { back() {} },
    $nextTick(callback) { callback() },
    $modal: {
      msgWarning(message) { runtime.warnings.push(message) },
      msgSuccess() {}
    }
  })
  Object.keys(component.methods).forEach(name => {
    instance[name] = component.methods[name].bind(instance)
  })
  Object.keys(component.computed).forEach(name => {
    Object.defineProperty(instance, name, {
      configurable: true,
      get: component.computed[name].bind(instance)
    })
  })
  runtime.instances.push(instance)
  return instance
}

function contract(contractId, kind = "preview-pdf") {
  return {
    contractId,
    status: "pending_sign",
    previewFileUrl: `/laborContract/download/${contractId}/${kind}`
  }
}

function startInline(instance, runtime, selected) {
  instance.selectedContract = selected
  const promise = instance.loadContractFile()
  return { promise, call: runtime.downloads[runtime.downloads.length - 1] }
}

async function passProtectedFileValidationStage() {
  await Promise.resolve()
  await Promise.resolve()
}

async function run() {
  const runtime = createRuntime()
  const { component, FakeBlob } = loadComponent(runtime)

  const lateA = createInstance(component, runtime)
  const lateARequest = startInline(lateA, runtime, contract(101))
  const currentBRequest = startInline(lateA, runtime, contract(202))
  currentBRequest.call.request.resolve(new FakeBlob(["B"], { type: "application/pdf" }))
  await currentBRequest.promise
  lateARequest.call.request.resolve(new FakeBlob(["A"], { type: "application/pdf" }))
  await lateARequest.promise
  assert.strictEqual(lateA.fileObjectUrl, "blob:contract-1")
  assert.deepStrictEqual(runtime.createdUrls, ["blob:contract-1"],
    "a late obsolete blob should not create an object URL")

  const earlyA = createInstance(component, runtime)
  const obsoleteFirst = startInline(earlyA, runtime, contract(301))
  const pendingSecond = startInline(earlyA, runtime, contract(302))
  obsoleteFirst.call.request.resolve(new FakeBlob(["old"], { type: "application/pdf" }))
  await obsoleteFirst.promise
  assert.strictEqual(earlyA.fileLoading, true,
    "an obsolete finally handler must not clear the current request loading state")
  pendingSecond.call.request.resolve(new FakeBlob(["new"], { type: "application/pdf" }))
  await pendingSecond.promise
  assert.strictEqual(earlyA.fileObjectUrl, "blob:contract-2")

  const oldFailure = createInstance(component, runtime)
  const failedOld = startInline(oldFailure, runtime, contract(401))
  const successfulNew = startInline(oldFailure, runtime, contract(402))
  successfulNew.call.request.resolve(new FakeBlob(["new"], { type: "application/pdf" }))
  await successfulNew.promise
  failedOld.call.request.reject(new Error("old request failed"))
  await failedOld.promise
  assert.strictEqual(oldFailure.fileError, "")
  assert.strictEqual(oldFailure.fileObjectUrl, "blob:contract-3",
    "an obsolete failure must not clear the current preview")

  const sameIdRetry = createInstance(component, runtime)
  const sameIdOld = startInline(sameIdRetry, runtime, contract(501))
  const sameIdNew = startInline(sameIdRetry, runtime, contract(501))
  sameIdNew.call.request.resolve(new FakeBlob(["retry"], { type: "application/pdf" }))
  await sameIdNew.promise
  sameIdOld.call.request.resolve(new FakeBlob(["old"], { type: "application/pdf" }))
  await sameIdOld.promise
  assert.strictEqual(sameIdRetry.fileObjectUrl, "blob:contract-4",
    "same-ID retries must still be isolated by sequence")

  const switchedInline = createInstance(component, runtime)
  const loadedBeforeSwitch = startInline(switchedInline, runtime, contract(551))
  loadedBeforeSwitch.call.request.resolve(new FakeBlob(["first"], { type: "application/pdf" }))
  await loadedBeforeSwitch.promise
  const switchedUrl = switchedInline.fileObjectUrl
  const loadedAfterSwitch = startInline(switchedInline, runtime, contract(552))
  assert.ok(runtime.revokedUrls.includes(switchedUrl),
    "switching contracts must revoke the previous inline URL immediately")
  loadedAfterSwitch.call.request.resolve(new FakeBlob(["second"], { type: "application/pdf" }))
  await loadedAfterSwitch.promise

  const microtaskSwitch = createInstance(component, runtime)
  const microtaskOld = startInline(microtaskSwitch, runtime, contract(553))
  microtaskOld.call.request.resolve(new FakeBlob(["microtask-old"], { type: "application/pdf" }))
  await passProtectedFileValidationStage()
  const microtaskOldUrl = runtime.createdUrls[runtime.createdUrls.length - 1]
  const microtaskNew = startInline(microtaskSwitch, runtime, contract(554))
  await microtaskOld.promise
  assert.ok(runtime.revokedUrls.includes(microtaskOldUrl),
    "switching in the createObjectURL ownership microtask must revoke the stale target")
  microtaskNew.call.request.resolve(new FakeBlob(["microtask-new"], { type: "application/pdf" }))
  await microtaskNew.promise

  const microtaskBack = createInstance(component, runtime)
  const microtaskBackRequest = startInline(microtaskBack, runtime, contract(555))
  microtaskBackRequest.call.request.resolve(new FakeBlob(["microtask-back"], { type: "application/pdf" }))
  await passProtectedFileValidationStage()
  const microtaskBackUrl = runtime.createdUrls[runtime.createdUrls.length - 1]
  microtaskBack.goBack()
  await microtaskBackRequest.promise
  assert.ok(runtime.revokedUrls.includes(microtaskBackUrl),
    "returning in the createObjectURL ownership microtask must revoke the target")

  const microtaskDestroy = createInstance(component, runtime)
  const microtaskDestroyRequest = startInline(microtaskDestroy, runtime, contract(556))
  microtaskDestroyRequest.call.request.resolve(new FakeBlob(["microtask-destroy"], { type: "application/pdf" }))
  await passProtectedFileValidationStage()
  const microtaskDestroyUrl = runtime.createdUrls[runtime.createdUrls.length - 1]
  component.beforeDestroy.call(microtaskDestroy)
  await microtaskDestroyRequest.promise
  assert.ok(runtime.revokedUrls.includes(microtaskDestroyUrl),
    "destroying in the createObjectURL ownership microtask must revoke the target")

  const currentFailure = createInstance(component, runtime)
  const failedCurrent = startInline(currentFailure, runtime, contract(601))
  failedCurrent.call.request.reject(new Error("network unavailable"))
  await failedCurrent.promise
  assert.strictEqual(currentFailure.fileError, "network unavailable")
  assert.strictEqual(currentFailure.fileLoading, false)
  const retryCurrent = startInline(currentFailure, runtime, contract(601))
  retryCurrent.call.request.resolve(new FakeBlob(["retry"], { type: "application/pdf" }))
  await retryCurrent.promise
  assert.strictEqual(currentFailure.fileError, "")
  assert.ok(currentFailure.fileObjectUrl)

  const backInstance = createInstance(component, runtime)
  const backLoaded = startInline(backInstance, runtime, contract(701))
  backLoaded.call.request.resolve(new FakeBlob(["back"], { type: "application/pdf" }))
  await backLoaded.promise
  const backUrl = backInstance.fileObjectUrl
  backInstance.goBack()
  assert.strictEqual(backInstance.fileObjectUrl, "")
  assert.ok(runtime.revokedUrls.includes(backUrl), "returning to the list must revoke the inline URL")
  const lateAfterBack = startInline(backInstance, runtime, contract(702))
  backInstance.goBack()
  const createdBeforeLateBack = runtime.createdUrls.length
  lateAfterBack.call.request.resolve(new FakeBlob(["late"], { type: "application/pdf" }))
  await lateAfterBack.promise
  assert.strictEqual(runtime.createdUrls.length, createdBeforeLateBack,
    "returning before a blob arrives must avoid creating an obsolete URL")

  const destroyedInline = createInstance(component, runtime)
  const destroyLoaded = startInline(destroyedInline, runtime, contract(801))
  destroyLoaded.call.request.resolve(new FakeBlob(["loaded"], { type: "application/pdf" }))
  await destroyLoaded.promise
  const destroyLoadedUrl = destroyedInline.fileObjectUrl
  component.beforeDestroy.call(destroyedInline)
  assert.strictEqual(destroyedInline.fileObjectUrl, "")
  assert.ok(runtime.revokedUrls.includes(destroyLoadedUrl),
    "destroy must revoke the current inline URL exactly once")
  const destroyedPendingInline = createInstance(component, runtime)
  const destroyRequest = startInline(destroyedPendingInline, runtime, contract(802))
  component.beforeDestroy.call(destroyedPendingInline)
  const createdBeforeDestroyedInline = runtime.createdUrls.length
  destroyRequest.call.request.resolve(new FakeBlob(["destroyed"], { type: "application/pdf" }))
  await destroyRequest.promise
  assert.strictEqual(runtime.createdUrls.length, createdBeforeDestroyedInline)

  const blockedPopup = createInstance(component, runtime)
  blockedPopup.selectedContract = contract(901)
  const downloadsBeforeBlocked = runtime.downloads.length
  await blockedPopup.openProtectedFile(blockedPopup.selectedContract.previewFileUrl)
  assert.strictEqual(runtime.downloads.length, downloadsBeforeBlocked,
    "a blocked popup must not start a protected download")

  const failedPopup = createInstance(component, runtime)
  const failureWindow = createPopup()
  runtime.popupQueue.push(failureWindow)
  failedPopup.selectedContract = contract(902)
  const warningsBeforeFailure = runtime.warnings.length
  const popupFailure = failedPopup.openProtectedFile(failedPopup.selectedContract.previewFileUrl)
  runtime.downloads[runtime.downloads.length - 1].request.reject(new Error("popup failed"))
  await popupFailure
  assert.strictEqual(failureWindow.closed, true)
  assert.strictEqual(failedPopup.popupPreviews.length, 0)
  assert.strictEqual(runtime.warnings.length, warningsBeforeFailure + 1,
    "a popup download rejection must show exactly one user warning")

  const closedEarlyPopup = createInstance(component, runtime)
  const closedEarlyWindow = createPopup()
  runtime.popupQueue.push(closedEarlyWindow)
  closedEarlyPopup.selectedContract = contract(903)
  const warningsBeforeEarlyClose = runtime.warnings.length
  const closedEarly = closedEarlyPopup.openProtectedFile(closedEarlyPopup.selectedContract.previewFileUrl)
  const createdBeforeEarlyClose = runtime.createdUrls.length
  closedEarlyWindow.close()
  runtime.downloads[runtime.downloads.length - 1].request.resolve(new FakeBlob(["closed"], { type: "application/pdf" }))
  await closedEarly
  assert.strictEqual(runtime.createdUrls.length, createdBeforeEarlyClose,
    "a popup closed before download completion must not create an object URL")
  assert.strictEqual(runtime.warnings.length, warningsBeforeEarlyClose,
    "an actively closed popup must not report a download failure")

  const microtaskClosedPopup = createInstance(component, runtime)
  const microtaskClosedWindow = createPopup()
  runtime.popupQueue.push(microtaskClosedWindow)
  microtaskClosedPopup.selectedContract = contract(9031)
  const warningsBeforeMicrotaskClose = runtime.warnings.length
  const microtaskClosed = microtaskClosedPopup.openProtectedFile(microtaskClosedPopup.selectedContract.previewFileUrl)
  const createdBeforeMicrotaskClose = runtime.createdUrls.length
  runtime.downloads[runtime.downloads.length - 1].request.resolve(new FakeBlob(["microtask-close"], { type: "application/pdf" }))
  await Promise.resolve()
  microtaskClosedWindow.close()
  await microtaskClosed
  assert.ok(
    runtime.createdUrls.slice(createdBeforeMicrotaskClose).every(url => runtime.revokedUrls.includes(url)),
    "closing during validation must either avoid creating the object URL or revoke it"
  )
  assert.strictEqual(runtime.warnings.length, warningsBeforeMicrotaskClose)

  const navigationFailure = createInstance(component, runtime)
  const navigationFailureWindow = createPopup({ navigationThrows: true })
  runtime.popupQueue.push(navigationFailureWindow)
  navigationFailure.selectedContract = contract(904)
  const warningsBeforeNavigationFailure = runtime.warnings.length
  const badNavigation = navigationFailure.openProtectedFile(navigationFailure.selectedContract.previewFileUrl)
  runtime.downloads[runtime.downloads.length - 1].request.resolve(new FakeBlob(["nav"], { type: "application/pdf" }))
  await badNavigation
  const navigationUrl = runtime.createdUrls[runtime.createdUrls.length - 1]
  assert.ok(runtime.revokedUrls.includes(navigationUrl))
  assert.strictEqual(navigationFailureWindow.closed, true)
  assert.strictEqual(runtime.warnings.length, warningsBeforeNavigationFailure + 1,
    "a popup navigation exception must show exactly one user warning")

  const concurrentPopup = createInstance(component, runtime)
  const firstWindow = createPopup()
  const secondWindow = createPopup()
  runtime.popupQueue.push(firstWindow, secondWindow)
  concurrentPopup.selectedContract = contract(905)
  const firstPopup = concurrentPopup.openProtectedFile(concurrentPopup.selectedContract.previewFileUrl)
  concurrentPopup.selectedContract = contract(906, "certificate")
  const secondPopup = concurrentPopup.openProtectedFile(concurrentPopup.selectedContract.previewFileUrl)
  const firstPopupCall = runtime.downloads[runtime.downloads.length - 2]
  const secondPopupCall = runtime.downloads[runtime.downloads.length - 1]
  assert.deepStrictEqual(
    [[firstPopupCall.contractId, firstPopupCall.kind], [secondPopupCall.contractId, secondPopupCall.kind]],
    [["905", "preview-pdf"], ["906", "certificate"]],
    "concurrent popups must retain their own contract and file-kind snapshots"
  )
  firstPopupCall.request.resolve(new FakeBlob(["first"], { type: "application/pdf" }))
  secondPopupCall.request.resolve(new FakeBlob(["second"], { type: "application/pdf" }))
  await Promise.all([firstPopup, secondPopup])
  const concurrentUrls = runtime.createdUrls.slice(-2)
  firstWindow.fire("load")
  secondWindow.fire("load")
  assert.ok(concurrentUrls.every(url => runtime.revokedUrls.includes(url)))
  assert.strictEqual(firstWindow.closed, false)
  assert.strictEqual(secondWindow.closed, false,
    "successful popup release must not close navigated windows")
  assert.strictEqual(firstWindow.listenerCount("load"), 0)
  assert.strictEqual(secondWindow.listenerCount("load"), 0)
  assert.strictEqual(firstWindow.removeCalls, 1)
  assert.strictEqual(secondWindow.removeCalls, 1,
    "load release must remove listeners that capture the component")

  const timeoutPopup = createInstance(component, runtime)
  const timeoutWindow = createPopup()
  runtime.popupQueue.push(timeoutWindow)
  timeoutPopup.selectedContract = contract(907)
  const timeoutRequest = timeoutPopup.openProtectedFile(timeoutPopup.selectedContract.previewFileUrl)
  runtime.downloads[runtime.downloads.length - 1].request.resolve(new FakeBlob(["timeout"], { type: "application/pdf" }))
  await timeoutRequest
  const timeoutUrl = runtime.createdUrls[runtime.createdUrls.length - 1]
  timeoutWindow.close()
  runtime.advanceTime(29999)
  assert.ok(!runtime.revokedUrls.includes(timeoutUrl))
  runtime.advanceTime(1)
  assert.ok(runtime.revokedUrls.includes(timeoutUrl),
    "manual close must still be bounded by idempotent timeout release")
  assert.strictEqual(timeoutWindow.listenerCount("load"), 0)

  const destroyedPopup = createInstance(component, runtime)
  const pendingWindow = createPopup()
  runtime.popupQueue.push(pendingWindow)
  destroyedPopup.selectedContract = contract(908)
  const warningsBeforeDestroy = runtime.warnings.length
  const pendingPopup = destroyedPopup.openProtectedFile(destroyedPopup.selectedContract.previewFileUrl)
  component.beforeDestroy.call(destroyedPopup)
  const beforeDestroyedBlob = runtime.createdUrls.length
  runtime.downloads[runtime.downloads.length - 1].request.resolve(new FakeBlob(["destroy"], { type: "application/pdf" }))
  await pendingPopup
  assert.strictEqual(runtime.createdUrls.length, beforeDestroyedBlob)
  assert.strictEqual(pendingWindow.closed, true)
  assert.strictEqual(runtime.warnings.length, warningsBeforeDestroy,
    "component destruction must cancel popup ownership without a failure warning")

  const navigatedDestroy = createInstance(component, runtime)
  const navigatedWindow = createPopup()
  runtime.popupQueue.push(navigatedWindow)
  navigatedDestroy.selectedContract = contract(909)
  const navigatedPopup = navigatedDestroy.openProtectedFile(navigatedDestroy.selectedContract.previewFileUrl)
  runtime.downloads[runtime.downloads.length - 1].request.resolve(new FakeBlob(["navigated"], { type: "application/pdf" }))
  await navigatedPopup
  const navigatedUrl = runtime.createdUrls[runtime.createdUrls.length - 1]
  component.beforeDestroy.call(navigatedDestroy)
  assert.ok(runtime.revokedUrls.includes(navigatedUrl))
  assert.strictEqual(navigatedWindow.closed, false,
    "destroy must release the URL without force-closing an already navigated popup")
  assert.strictEqual(navigatedWindow.listenerCount("load"), 0)

  assert.ok(runtime.timerDelays.length > 0)
  assert.ok(runtime.timerDelays.every(delay => delay === 30000),
    "every successful popup must use the bounded 30000ms release contract")

  const ownedUrls = new Set(runtime.instances.flatMap(instance => {
    return instance.protectedFileOwners.map(owner => owner.objectUrl).filter(Boolean)
  }))
  runtime.createdUrls.forEach(url => {
    assert.ok(ownedUrls.has(url) || runtime.revokedUrls.includes(url),
      `created URL ${url} must remain owned or be revoked`)
  })

  assert.strictEqual(new Set(runtime.revokedUrls).size, runtime.revokedUrls.length,
    "every object URL must be revoked at most once")
  console.log("mobile labor contract preview lifecycle tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
