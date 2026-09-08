const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const componentPath = path.resolve(__dirname, "../src/views/mobile/onboardData/index.vue")

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function loadComponent(runtime) {
  const source = fs.readFileSync(componentPath, "utf8")
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, "mobile onboard-data component must expose a script block")
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
      if (request === "../mobileErrorMessage") {
        return require("../src/views/mobile/mobileErrorMessage")
      }
      if (request !== "@/api/oa/signTask") throw new Error(`unexpected dependency: ${request}`)
      return {
        getOnboardSignDataRequest(requestId) {
          const request = deferred()
          runtime.detailCalls.push({ requestId: String(requestId), request })
          return request.promise
        },
        listMyOnboardSignDataRequests() {
          const request = deferred()
          runtime.listCalls.push({ request })
          return request.promise
        },
        submitOnboardSignDataRequest(requestId, payload) {
          const request = deferred()
          runtime.submitCalls.push({
            requestId: String(requestId),
            payload: JSON.parse(JSON.stringify(payload)),
            request
          })
          return request.promise
        }
      }
    },
    Promise,
    Object,
    Array,
    String,
    JSON,
    Math,
    Date
  }, { filename: componentPath })
  return module.exports.default || module.exports
}

function createInstance(component, options = {}) {
  const events = []
  const messages = []
  const nextTicks = []
  const instance = Object.assign(component.data(), {
    requestId: null,
    embedded: false,
    $route: { query: {}, params: {} },
    $refs: {},
    $set(target, key, value) { target[key] = value },
    $emit(type, payload) { events.push({ type, payload }) },
    $nextTick(callback) {
      if (options.deferNextTick) nextTicks.push(callback)
      else callback()
    },
    $modal: { msgSuccess(message) { messages.push(message) } }
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
  instance.initSignatureCanvas = () => { instance.signatureInitializations += 1 }
  instance.signatureInitializations = 0
  return {
    instance,
    events,
    messages,
    flushNextTicks() {
      const callbacks = nextTicks.splice(0)
      callbacks.forEach(callback => callback())
    }
  }
}

function detail(requestId, version, overrides = {}) {
  return Object.assign({
    requestId,
    version,
    status: "PENDING_EMPLOYEE",
    editableFields: ["currentAddress"],
    currentValues: { currentAddress: `address-${requestId}` }
  }, overrides)
}

async function run() {
  const runtime = { detailCalls: [], listCalls: [], submitCalls: [] }
  const component = loadComponent(runtime)

  const latest = createInstance(component)
  const loadA = latest.instance.loadDetail("101")
  const loadB = latest.instance.loadDetail("202")
  runtime.detailCalls[1].request.resolve({
    data: detail("202", 2, {
      signingSequence: "SIGNATURE_FIRST",
      factSnapshot: { employeeName: "B" },
      plannedDocumentNames: ["B合同"]
    })
  })
  await loadB
  runtime.detailCalls[0].request.resolve({ data: detail("101", 1) })
  await loadA
  assert.strictEqual(latest.instance.selectedRequest.requestId, "202")
  assert.strictEqual(latest.instance.formValues.currentAddress, "address-202")
  assert.strictEqual(latest.instance.errorMessage, "")
  assert.strictEqual(latest.instance.loading, false)
  assert.deepStrictEqual(latest.events.map(event => [event.type, event.payload.requestId]), [["loaded", "202"]])
  assert.strictEqual(latest.instance.signatureInitializations, 1,
    "only the latest detail request may initialize the signature canvas")

  const sameId = createInstance(component)
  const firstReload = sameId.instance.loadDetail("303")
  const secondReload = sameId.instance.loadDetail("303")
  runtime.detailCalls[3].request.resolve({ data: detail("303", 4) })
  await secondReload
  runtime.detailCalls[2].request.resolve({ data: detail("303", 3) })
  await firstReload
  assert.strictEqual(sameId.instance.selectedRequest.version, 4,
    "generation ownership must protect same-ID reloads from older responses")

  const oldFailure = createInstance(component)
  const failedA = oldFailure.instance.loadDetail("401")
  const successfulB = oldFailure.instance.loadDetail("402")
  runtime.detailCalls[5].request.resolve({ data: detail("402", 2) })
  await successfulB
  runtime.detailCalls[4].request.reject(new Error("old A failed"))
  await failedA
  assert.strictEqual(oldFailure.instance.selectedRequest.requestId, "402")
  assert.strictEqual(oldFailure.instance.errorMessage, "",
    "an obsolete detail failure must not clear or attach an error to the latest detail")

  const destroyed = createInstance(component)
  const leavingRequest = destroyed.instance.loadDetail("501")
  component.beforeDestroy.call(destroyed.instance)
  runtime.detailCalls[6].request.resolve({ data: detail("501", 1) })
  await leavingRequest
  assert.strictEqual(destroyed.instance.selectedRequest, null)
  assert.deepStrictEqual(destroyed.events, [])
  assert.strictEqual(destroyed.instance.signatureInitializations, 0,
    "a response arriving after destruction must not mutate or initialize the component")

  const submitSwitch = createInstance(component)
  const initialA = submitSwitch.instance.loadDetail("601")
  runtime.detailCalls[7].request.resolve({ data: detail("601", 7) })
  await initialA
  const submitA = submitSwitch.instance.submit()
  assert.strictEqual(runtime.submitCalls[0].requestId, "601")
  assert.strictEqual(runtime.submitCalls[0].payload.version, 7)
  const switchToB = submitSwitch.instance.loadDetail("602")
  runtime.detailCalls[8].request.resolve({ data: detail("602", 8) })
  await switchToB
  runtime.submitCalls[0].request.resolve({ data: true })
  await submitA
  assert.strictEqual(submitSwitch.instance.selectedRequest.requestId, "602")
  assert.strictEqual(runtime.detailCalls.length, 9,
    "a stale submit success must not refresh its former request")
  assert.deepStrictEqual(submitSwitch.events.map(event => event.type), ["loaded", "loaded"])
  assert.deepStrictEqual(submitSwitch.messages, [],
    "a stale submit success must not emit user feedback for the newly selected request")

  const listRace = createInstance(component)
  const firstList = listRace.instance.loadList()
  const firstListCall = runtime.listCalls[runtime.listCalls.length - 1]
  const secondList = listRace.instance.loadList()
  const secondListCall = runtime.listCalls[runtime.listCalls.length - 1]
  secondListCall.request.resolve({ data: [{ requestId: 702, title: "new list" }] })
  await secondList
  firstListCall.request.resolve({ data: [{ requestId: 701, title: "old list" }] })
  await firstList
  assert.deepStrictEqual(listRace.instance.requests.map(item => item.title), ["new list"],
    "only the latest list request may replace the visible request list")

  const listThenDetail = createInstance(component)
  const obsoleteList = listThenDetail.instance.loadList()
  const obsoleteListCall = runtime.listCalls[runtime.listCalls.length - 1]
  const currentDetail = listThenDetail.instance.loadDetail("703")
  const currentDetailCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  obsoleteListCall.request.reject(new Error("old list failed"))
  await obsoleteList
  assert.strictEqual(listThenDetail.instance.loading, true,
    "an obsolete list finally handler must not clear current detail loading")
  currentDetailCall.request.resolve({ data: detail("703", 1) })
  await currentDetail
  assert.strictEqual(listThenDetail.instance.selectedRequest.requestId, "703")
  assert.strictEqual(listThenDetail.instance.errorMessage, "")

  const destroyedList = createInstance(component)
  const pendingDestroyedList = destroyedList.instance.loadList()
  const destroyedListCall = runtime.listCalls[runtime.listCalls.length - 1]
  component.beforeDestroy.call(destroyedList.instance)
  destroyedListCall.request.resolve({ data: [{ requestId: 704, title: "too late" }] })
  await pendingDestroyedList
  assert.deepStrictEqual(Array.from(destroyedList.instance.requests), [],
    "a list response after destruction must not mutate component state")

  const deferredTick = createInstance(component, { deferNextTick: true })
  const tickA = deferredTick.instance.loadDetail("705")
  const tickACall = runtime.detailCalls[runtime.detailCalls.length - 1]
  tickACall.request.resolve({
    data: detail("705", 1, {
      signingSequence: "SIGNATURE_FIRST",
      factSnapshot: { employeeName: "A" },
      plannedDocumentNames: ["A合同"]
    })
  })
  await tickA
  const tickB = deferredTick.instance.loadDetail("706")
  const tickBCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  deferredTick.flushNextTicks()
  assert.strictEqual(deferredTick.instance.signatureInitializations, 0,
    "an async nextTick callback must recheck ownership after a newer load starts")
  tickBCall.request.resolve({
    data: detail("706", 2, {
      signingSequence: "SIGNATURE_FIRST",
      factSnapshot: { employeeName: "B" },
      plannedDocumentNames: ["B合同"]
    })
  })
  await tickB
  deferredTick.flushNextTicks()
  assert.strictEqual(deferredTick.instance.signatureInitializations, 1)

  const duplicateSubmit = createInstance(component)
  const duplicateDetail = duplicateSubmit.instance.loadDetail("707")
  const duplicateDetailCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  duplicateDetailCall.request.resolve({ data: detail("707", 3) })
  await duplicateDetail
  const submitCountBeforeDuplicate = runtime.submitCalls.length
  const firstSubmit = duplicateSubmit.instance.submit()
  const duplicateAttempt = duplicateSubmit.instance.submit()
  assert.strictEqual(runtime.submitCalls.length, submitCountBeforeDuplicate + 1,
    "a submitting request must reject duplicate submit attempts before writing the backend")
  await duplicateAttempt
  const firstSubmitCall = runtime.submitCalls[runtime.submitCalls.length - 1]
  firstSubmitCall.request.reject(new Error("current submit failed"))
  await firstSubmit
  assert.strictEqual(duplicateSubmit.instance.submitError, "current submit failed")
  assert.strictEqual(duplicateSubmit.instance.submitting, false)

  const staleSubmitFailure = createInstance(component)
  const staleSubmitA = staleSubmitFailure.instance.loadDetail("708")
  const staleSubmitADetail = runtime.detailCalls[runtime.detailCalls.length - 1]
  staleSubmitADetail.request.resolve({ data: detail("708", 4) })
  await staleSubmitA
  const submitOldA = staleSubmitFailure.instance.submit()
  const submitOldACall = runtime.submitCalls[runtime.submitCalls.length - 1]
  const loadSubmitB = staleSubmitFailure.instance.loadDetail("709")
  assert.strictEqual(staleSubmitFailure.instance.submitting, false,
    "loading a new request must immediately transfer submit ownership and unlock the new detail")
  const loadSubmitBCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  loadSubmitBCall.request.resolve({ data: detail("709", 5) })
  await loadSubmitB
  const submitCurrentB = staleSubmitFailure.instance.submit()
  const submitCurrentBCall = runtime.submitCalls[runtime.submitCalls.length - 1]
  submitOldACall.request.reject(new Error("obsolete submit failed"))
  await submitOldA
  assert.strictEqual(staleSubmitFailure.instance.submitting, true,
    "an obsolete submit finally handler must not unlock a newer submit")
  assert.strictEqual(staleSubmitFailure.instance.submitError, "",
    "an obsolete submit catch must not display an error on the new detail")
  submitCurrentBCall.request.resolve({ data: true })
  await Promise.resolve()
  const refreshedBCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  assert.strictEqual(refreshedBCall.requestId, "709")
  refreshedBCall.request.resolve({ data: detail("709", 6) })
  await submitCurrentB
  assert.strictEqual(staleSubmitFailure.instance.selectedRequest.version, 6)
  assert.deepStrictEqual(
    staleSubmitFailure.events.filter(event => event.type === "updated").map(event => event.payload.requestId),
    ["709"]
  )

  const refreshSwitch = createInstance(component)
  const refreshInitial = refreshSwitch.instance.loadDetail("710")
  const refreshInitialCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  refreshInitialCall.request.resolve({ data: detail("710", 7) })
  await refreshInitial
  const refreshSubmit = refreshSwitch.instance.submit()
  const refreshSubmitCall = runtime.submitCalls[runtime.submitCalls.length - 1]
  refreshSubmitCall.request.resolve({ data: true })
  await Promise.resolve()
  const obsoleteRefreshCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  assert.strictEqual(obsoleteRefreshCall.requestId, "710")
  const refreshSwitchB = refreshSwitch.instance.loadDetail("711")
  assert.strictEqual(refreshSwitch.instance.submitting, false,
    "an external detail switch must release the internal refresh submit owner")
  const refreshSwitchBCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  refreshSwitchBCall.request.resolve({ data: detail("711", 8) })
  await refreshSwitchB
  obsoleteRefreshCall.request.resolve({ data: detail("710", 9) })
  await refreshSubmit
  assert.strictEqual(refreshSwitch.instance.selectedRequest.requestId, "711")
  assert.strictEqual(refreshSwitch.instance.submitting, false)
  assert.deepStrictEqual(refreshSwitch.events.filter(event => event.type === "updated"), [],
    "switching during the success refresh must suppress stale updated events")

  const sameIdSubmitReload = createInstance(component)
  const sameSubmitInitial = sameIdSubmitReload.instance.loadDetail("712")
  const sameSubmitInitialCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  sameSubmitInitialCall.request.resolve({ data: detail("712", 10) })
  await sameSubmitInitial
  const sameIdOldSubmit = sameIdSubmitReload.instance.submit()
  const sameIdOldSubmitCall = runtime.submitCalls[runtime.submitCalls.length - 1]
  const sameIdNewLoad = sameIdSubmitReload.instance.loadDetail("712")
  const sameIdNewLoadCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  sameIdNewLoadCall.request.resolve({ data: detail("712", 11) })
  await sameIdNewLoad
  const detailCountBeforeOldSuccess = runtime.detailCalls.length
  sameIdOldSubmitCall.request.resolve({ data: true })
  await sameIdOldSubmit
  assert.strictEqual(runtime.detailCalls.length, detailCountBeforeOldSuccess,
    "same-ID reload must invalidate an older submit by generation and captured version")
  assert.strictEqual(sameIdSubmitReload.instance.selectedRequest.version, 11)

  const lockedRefresh = createInstance(component)
  const lockedRefreshInitial = lockedRefresh.instance.loadDetail("713")
  const lockedRefreshInitialCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  lockedRefreshInitialCall.request.resolve({ data: detail("713", 12) })
  await lockedRefreshInitial
  const lockedRefreshSubmit = lockedRefresh.instance.submit()
  const lockedRefreshSubmitCall = runtime.submitCalls[runtime.submitCalls.length - 1]
  lockedRefreshSubmitCall.request.resolve({ data: true })
  await Promise.resolve()
  const lockedRefreshCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  assert.strictEqual(lockedRefreshCall.requestId, "713")
  assert.strictEqual(lockedRefresh.instance.submitting, true,
    "an internal success refresh must retain the submit lock while pending")
  const submitCountDuringRefresh = runtime.submitCalls.length
  await lockedRefresh.instance.submit()
  assert.strictEqual(runtime.submitCalls.length, submitCountDuringRefresh,
    "submit during an internal refresh must not create another backend request")
  lockedRefreshCall.request.resolve({ data: detail("713", 13) })
  await lockedRefreshSubmit
  assert.strictEqual(lockedRefresh.instance.selectedRequest.version, 13)
  assert.strictEqual(lockedRefresh.instance.submitting, false,
    "a successful refresh must transfer ownership to the new version before unlocking")
  assert.deepStrictEqual(
    lockedRefresh.events.filter(event => event.type === "updated").map(event => event.payload.version),
    [13]
  )

  const rejectedRefresh = createInstance(component)
  const rejectedRefreshInitial = rejectedRefresh.instance.loadDetail("714")
  const rejectedRefreshInitialCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  rejectedRefreshInitialCall.request.resolve({ data: detail("714", 14) })
  await rejectedRefreshInitial
  const rejectedRefreshSubmit = rejectedRefresh.instance.submit()
  const rejectedRefreshSubmitCall = runtime.submitCalls[runtime.submitCalls.length - 1]
  rejectedRefreshSubmitCall.request.resolve({ data: true })
  await Promise.resolve()
  const rejectedRefreshCall = runtime.detailCalls[runtime.detailCalls.length - 1]
  rejectedRefreshCall.request.reject(new Error("refresh failed"))
  await rejectedRefreshSubmit
  assert.strictEqual(rejectedRefresh.instance.selectedRequest.requestId, "714",
    "an internal refresh failure must retain the submitted detail for safe owner settlement")
  assert.strictEqual(rejectedRefresh.instance.errorMessage, "refresh failed")
  assert.strictEqual(rejectedRefresh.instance.loading, false)
  assert.strictEqual(rejectedRefresh.instance.submitting, false)
  assert.deepStrictEqual(rejectedRefresh.events.filter(event => event.type === "updated"), [],
    "a failed refresh must not emit an updated detail")

  console.log("mobile onboard-data request ownership tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
