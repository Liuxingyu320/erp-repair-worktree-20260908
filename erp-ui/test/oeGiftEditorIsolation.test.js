const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const { imageUrls } = require("../src/utils/imageGallery")

const uiRoot = path.resolve(__dirname, "..")
const kinds = ["oe", "gift"]
const imagesA = ["https://cdn.example.com/a1.png", "https://cdn.example.com/a2.png"]
const imagesB = ["https://cdn.example.com/b1.png", "https://cdn.example.com/b2.png", "https://cdn.example.com/b3.png"]

function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

async function tick() {
  for (let i = 0; i < 8; i++) await Promise.resolve()
}

function catalog(kind) {
  if (kind === "oe") {
    return {
      name: "OE",
      file: "src/views/inventory/oe/index.vue",
      idKey: "oeItemId",
      descKey: "itemDescription",
      nameKey: "oeItemName",
      detailKey: "detailOe"
    }
  }
  return {
    name: "gift",
    file: "src/views/inventory/gift/index.vue",
    idKey: "giftId",
    descKey: "productDescription",
    nameKey: "giftName",
    detailKey: "detailGift"
  }
}

function item(kind, letter, images) {
  const meta = catalog(kind)
  const row = {
    [meta.idKey]: letter === "A" ? 1 : 2,
    [meta.nameKey]: letter,
    [meta.descKey]: "desc-" + letter,
    imageUrls: images.slice()
  }
  if (kind === "oe") {
    row.supplierName = "Supplier" + letter
    row.supplierPhone = letter === "A" ? "111" : "222"
  }
  return row
}

function loadSfc(relative, globals) {
  const file = path.join(uiRoot, relative)
  const source = fs.readFileSync(file, "utf8")
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, relative + " must expose a script block")
  const transformed = script[1]
    .replace(/^import .*$/gm, "")
    .replace("export default", "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    Promise,
    require(name) {
      if (name === "@/utils/imageGallery") return require(path.join(uiRoot, "src/utils/imageGallery"))
      if (name === "@/utils/supplierOptionState") return require(path.join(uiRoot, "src/utils/supplierOptionState"))
      throw new Error("unexpected require: " + name)
    },
    ...globals
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(transformed, sandbox, { filename: relative })
  return sandbox.module.exports
}

function loadPage(kind, apis) {
  const resolved = () => Promise.resolve({ rows: [], total: 0, data: {} })
  if (kind === "oe") {
    return loadSfc(catalog(kind).file, {
      ImageGallery: {},
      listOe: resolved,
      getOe: id => apis.get(id),
      addOe: payload => apis.add(payload),
      updateOe: payload => apis.update(payload),
      delOe: resolved,
      importOeData: resolved,
      oeCategoryTree: resolved,
      getOePurchaseReferencePolicy: () => Promise.resolve({ data: { configured: true, allowedHosts: [] } }),
      listSupplier: () => Promise.resolve({ rows: [] })
    })
  }
  return loadSfc(catalog(kind).file, {
    ImageGallery: {},
    listGift: resolved,
    getGift: id => apis.get(id),
    addGift: payload => apis.add(payload),
    updateGift: payload => apis.update(payload),
    delGift: resolved,
    importGiftData: resolved,
    giftCategoryTree: resolved
  })
}

function harness(component) {
  const nextTicks = []
  const messages = []
  const instance = {
    $refs: {},
    $route: { query: {} },
    $modal: {
      confirm: () => Promise.resolve(),
      msgSuccess(message) { messages.push(message) },
      msgError() {},
      msgWarning() {}
    },
    $nextTick(callback) {
      if (typeof callback === "function") nextTicks.push(callback)
      return Promise.resolve()
    }
  }
  Object.entries(component.methods || {}).forEach(([name, method]) => {
    if (typeof method === "function") instance[name] = method.bind(instance)
  })
  Object.assign(instance, component.data.call(instance))
  Object.entries(component.computed || {}).forEach(([name, computed]) => {
    const getter = typeof computed === "function" ? computed : computed.get
    Object.defineProperty(instance, name, { configurable: true, get: getter.bind(instance) })
  })
  instance.flushNextTick = () => {
    const queued = nextTicks.splice(0)
    queued.forEach(callback => callback())
  }
  instance.messages = messages
  return instance
}

function setup(kind, overrides = {}) {
  const getCalls = []
  const saved = { add: [], update: [] }
  const listReloads = { count: 0 }
  const apis = {
    get(id) {
      const request = deferred()
      getCalls.push({ id, request })
      return request.promise
    },
    add(payload) {
      saved.add.push(payload)
      return Promise.resolve({})
    },
    update(payload) {
      saved.update.push(payload)
      return Promise.resolve({})
    },
    ...overrides
  }
  const instance = harness(loadPage(kind, apis))
  instance.getList = () => { listReloads.count += 1 }
  instance.$refs.formRef = { validate: callback => callback(true) }
  return { meta: catalog(kind), instance, getCalls, saved, listReloads, apis }
}

function resolveGet(getCalls, index, data) {
  getCalls[index].request.resolve({ data })
}

function asList(value) {
  assert.ok(Array.isArray(value), "image list must be an array; a missing field is not an empty list")
  return Array.from(value)
}

function assertSameList(actual, expected, message) {
  assert.deepStrictEqual(asList(actual), asList(expected), message)
}

function assertSourceContracts() {
  for (const kind of kinds) {
    const source = fs.readFileSync(path.join(uiRoot, catalog(kind).file), "utf8")
    const label = catalog(kind).name
    assert.ok(source.includes('@close="handleEditorClose"'), label + " must invalidate the editor when close starts")
    assert.ok(!source.includes('@closed="handleEditorClose"'), label + " must not wait for close animation to invalidate the editor")
    assert.ok(source.includes('@close="handleDetailClose"'), label + " must invalidate detail when close starts")
    assert.ok(!source.includes('@closed="handleDetailClose"'), label + " must not wait for detail close animation")
    assert.ok(source.includes('v-model="form.imageUrls"'), label + " must keep click-to-upload imageUrls")
    assert.ok(source.includes(':delete-on-remove="false"'), label + " must keep the existing image upload contract")
    assert.ok(!source.includes('<el-input v-model="form.imageUrl"'), label + " must not expose a raw image URL input")
    assert.ok(source.includes('v-else-if="formReady"'), label + " must render the editor only after detail is ready")
    assert.ok(source.includes('retryEditorLoad'), label + " must expose in-drawer reload")
    assert.ok(source.includes('formLoadError'), label + " must bind a load-failure state")
    assert.ok(source.includes('role="alert"') && source.includes("重新加载"), label + " must show an in-window retry on load failure")
    assert.ok(source.includes("资料加载中"), label + " must show loading status before the form is editable")
  }
}

async function assertLateEditDoesNotClobberCurrent(kind) {
  const { meta, instance, getCalls, saved } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  const rowB = item(kind, "B", imagesB)
  instance.openForm(rowA)
  instance.openForm(rowB)
  resolveGet(getCalls, 1, Object.assign({}, rowB, { [meta.descKey]: "server-B", imageUrls: imagesB }))
  await tick()
  instance.form[meta.descKey] = "edited-B"
  instance.form.imageUrls = imagesB.slice()
  resolveGet(getCalls, 0, Object.assign({}, rowA, { [meta.descKey]: "server-A", imageUrls: imagesA }))
  await tick()
  assert.strictEqual(instance.form[meta.idKey], rowB[meta.idKey], meta.name + " late A must not replace B's id")
  assert.strictEqual(instance.form[meta.descKey], "edited-B", meta.name + " late A must not replace B's description")
  assertSameList(instance.form.imageUrls, imagesB, meta.name + " late A must not replace B's images")
  instance.doSave()
  await tick()
  assert.strictEqual(saved.update.length, 1)
  assert.strictEqual(saved.add.length, 0)
  assert.strictEqual(saved.update[0][meta.idKey], rowB[meta.idKey], meta.name + " save must still send B")
  assertSameList(saved.update[0].imageUrls, imagesB, meta.name + " save must still send B's images")
}

async function assertCloseOrCreateDropsPendingEdit(kind) {
  const { meta, instance, getCalls, saved } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  instance.openForm(rowA)
  assert.strictEqual(instance.drawerOpen, true)
  assert.strictEqual(instance.formReady, false)
  instance.$refs.formRef.validate = () => { throw new Error(meta.name + " must not validate before the edit payload is ready") }
  instance.doSave()
  assert.strictEqual(saved.update.length, 0)
  assert.strictEqual(saved.add.length, 0, meta.name + " pending edit must not create a new record")

  instance.closeEditor()
  assert.strictEqual(instance.drawerOpen, false)
  resolveGet(getCalls, 0, rowA)
  await tick()
  assert.strictEqual(instance.drawerOpen, false, meta.name + " stale edit must not reopen the drawer")

  const create = setup(kind)
  create.instance.openForm(item(kind, "A", imagesA))
  create.instance.openForm()
  assert.strictEqual(create.instance.editorMode, "create")
  assert.strictEqual(create.instance.form[meta.idKey], undefined)
  assert.strictEqual(create.instance.formReady, true)
  resolveGet(create.getCalls, 0, item(kind, "A", imagesA))
  await tick()
  assert.strictEqual(create.instance.form[meta.idKey], undefined, meta.name + " stale edit must not fill create with A's id")
  assertSameList(create.instance.form.imageUrls, [], meta.name + " create must keep empty images after a stale edit")
  create.instance.doSave()
  await tick()
  assert.strictEqual(create.saved.update.length, 0)
  assert.strictEqual(create.saved.add.length, 1)
  assert.strictEqual(create.saved.add[0][meta.idKey], undefined)
}

async function assertDetailLatestWins(kind) {
  const { meta, instance, getCalls } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  const rowB = item(kind, "B", imagesB)
  instance.openDetail(rowA)
  instance.openDetail(rowB)
  resolveGet(getCalls, 1, Object.assign({}, rowB, { [meta.descKey]: "server-B", imageUrls: imagesB }))
  resolveGet(getCalls, 0, Object.assign({}, rowA, { [meta.descKey]: "server-A", imageUrls: imagesA }))
  await tick()
  const detail = instance[meta.detailKey]
  assert.strictEqual(detail[meta.idKey], rowB[meta.idKey], meta.name + " detail must finish on B")
  assert.strictEqual(detail[meta.descKey], "server-B")
  assertSameList(imageUrls(detail), imagesB, meta.name + " detail must keep B's image order")
}

function pendingUpdates() {
  const saves = []
  const saved = { update: [] }
  return {
    saved,
    update(payload) {
      saved.update.push(payload)
      const request = deferred()
      saves.push(request)
      return request.promise
    },
    at(index) {
      return saves[index]
    }
  }
}

async function assertStaleSaveDoesNotTouchLaterEditor(kind) {
  const writes = pendingUpdates()
  const { meta, instance, getCalls } = setup(kind, { update: writes.update })
  const rowA = item(kind, "A", imagesA)
  const rowB = item(kind, "B", imagesB)
  instance.openForm(rowA)
  resolveGet(getCalls, 0, rowA)
  await tick()
  instance.doSave()
  assert.strictEqual(instance.saving, true)
  instance.openForm(rowB)
  assert.strictEqual(instance.saving, false, meta.name + " opening B must not inherit A's save spinner")
  assert.strictEqual(instance.formLoading, true)
  writes.at(0).resolve({})
  await tick()
  assert.strictEqual(instance.drawerOpen, true, meta.name + " A's success must not close B")
  assert.strictEqual(instance.form[meta.idKey], rowB[meta.idKey], meta.name + " A's success must not replace B")
  assert.strictEqual(instance.formLoading, true, meta.name + " A's success must not clear B's loading")
  assert.strictEqual(instance.messages.length, 0)
}

async function assertStaleSaveDoesNotClearLaterSaving(kind, settle) {
  const writes = pendingUpdates()
  const { meta, instance, getCalls } = setup(kind, { update: writes.update })
  const rowA = item(kind, "A", imagesA)
  const rowB = item(kind, "B", imagesB)
  instance.openForm(rowA)
  resolveGet(getCalls, 0, rowA)
  await tick()
  instance.doSave()
  instance.openForm(rowB)
  resolveGet(getCalls, 1, rowB)
  await tick()
  instance.doSave()
  assert.strictEqual(instance.saving, true)
  assert.strictEqual(writes.saved.update.length, 2)
  settle(writes.at(0))
  await tick()
  assert.strictEqual(instance.drawerOpen, true, meta.name + " A's settled save must not close B")
  assert.strictEqual(instance.form[meta.idKey], rowB[meta.idKey], meta.name + " A's settled save must not replace B")
  assert.strictEqual(instance.saving, true, meta.name + " A's settled save must not clear B's saving flag")
  assert.strictEqual(instance.messages.length, 0)
}

async function assertValidateBindsClickSession(kind) {
  const { meta, instance, getCalls, saved } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  const rowB = item(kind, "B", imagesB)
  instance.openForm(rowA)
  resolveGet(getCalls, 0, rowA)
  await tick()
  let validate
  instance.$refs.formRef = { validate(callback) { validate = callback } }
  instance.doSave()
  instance.openForm(rowB)
  resolveGet(getCalls, 1, rowB)
  await tick()
  validate(true)
  await tick()
  assert.strictEqual(saved.update.length, 0, meta.name + " stale validate must not save the later form")
  assert.strictEqual(instance.form[meta.idKey], rowB[meta.idKey])
  instance.$refs.formRef = { validate: callback => callback(true) }
  instance.doSave()
  await tick()
  assert.strictEqual(saved.update.length, 1)
  assert.strictEqual(saved.update[0][meta.idKey], rowB[meta.idKey])
}

async function assertCloseStartDoesNotHitReopenedSameRecord(kind) {
  const { meta, instance, getCalls } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  instance.openForm(rowA)
  instance.closeEditor()
  instance.openForm(rowA)
  const session = instance.editorSession
  instance.handleEditorClose()
  instance.handleDetailClose()
  assert.strictEqual(instance.drawerOpen, true, meta.name + " delayed close must not close a reopened drawer")
  assert.strictEqual(instance.editorSession, session, meta.name + " delayed close must not invalidate the new session")
  resolveGet(getCalls, 0, Object.assign({}, rowA, { [meta.descKey]: "stale-A" }))
  resolveGet(getCalls, 1, Object.assign({}, rowA, { [meta.descKey]: "fresh-A", imageUrls: imagesA }))
  await tick()
  assert.strictEqual(instance.form[meta.descKey], "fresh-A", meta.name + " only the reopened session may fill the form")
  assertSameList(instance.form.imageUrls, imagesA)
}

async function assertNormalSaveAndRetry(kind) {
  const firstSave = deferred()
  const retrySave = deferred()
  let updateCalls = 0
  const { meta, instance, getCalls, saved, listReloads } = setup(kind, {
    update(payload) {
      saved.update.push(payload)
      updateCalls += 1
      return updateCalls === 1 ? firstSave.promise : retrySave.promise
    }
  })
  const rowA = item(kind, "A", imagesA)
  instance.openForm(rowA)
  resolveGet(getCalls, 0, Object.assign({}, rowA, { imageUrls: imagesA }))
  await tick()
  assertSameList(instance.form.imageUrls, imagesA, meta.name + " edit must preserve image order")
  instance.doSave()
  firstSave.reject(new Error("save failed"))
  await tick()
  assert.strictEqual(instance.drawerOpen, true, meta.name + " failed save must stay open for retry")
  assert.strictEqual(instance.saving, false)
  instance.doSave()
  retrySave.resolve({})
  await tick()
  assert.strictEqual(instance.drawerOpen, false)
  assert.strictEqual(listReloads.count, 1)
  assert.strictEqual(saved.update.length, 2)
  assert.strictEqual(saved.update[1][meta.idKey], rowA[meta.idKey])
}

async function assertCreateKeepsImages(kind) {
  const { meta, instance, saved } = setup(kind)
  instance.openForm()
  instance.form[meta.nameKey] = "new-item"
  instance.form.imageUrls = imagesB.slice()
  instance.doSave()
  await tick()
  assert.strictEqual(saved.add.length, 1)
  assert.strictEqual(saved.update.length, 0)
  assertSameList(saved.add[0].imageUrls, imagesB)
}

async function assertLoadFailureRetryStaysOnCurrentRecord(kind) {
  const { meta, instance, getCalls, saved } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  instance.openForm(rowA)
  assert.strictEqual(instance.formReady, false)
  assert.strictEqual(instance.formLoading, true)
  assert.strictEqual(instance.formLoadError, "")
  instance.form[meta.descKey] = "typed-during-load"
  getCalls[0].request.reject(new Error("offline"))
  await tick()
  assert.strictEqual(instance.formReady, false, meta.name + " failed load must not open the editor")
  assert.strictEqual(instance.formLoading, false)
  assert.ok(String(instance.formLoadError).includes("失败"), meta.name + " failed load must expose in-window retry")
  instance.doSave()
  assert.strictEqual(saved.update.length, 0)
  assert.strictEqual(saved.add.length, 0)
  const failedSession = instance.editorSession
  instance.retryEditorLoad()
  assert.notStrictEqual(instance.editorSession, failedSession, meta.name + " retry must start a new session")
  assert.strictEqual(instance.formLoading, true)
  assert.strictEqual(instance.formReady, false)
  assert.strictEqual(instance.formLoadError, "")
  assert.strictEqual(getCalls.length, 2)
  assert.strictEqual(getCalls[1].id, rowA[meta.idKey])
  resolveGet(getCalls, 1, Object.assign({}, rowA, { [meta.descKey]: "server-A", imageUrls: imagesA }))
  await tick()
  assert.strictEqual(instance.formReady, true)
  assert.strictEqual(instance.form[meta.idKey], rowA[meta.idKey])
  assert.strictEqual(instance.form[meta.descKey], "server-A")
  assertSameList(instance.form.imageUrls, imagesA, meta.name + " retry success must keep image order")
}

async function assertStaleLoadSuccessDoesNotCoverRetry(kind) {
  const { meta, instance, getCalls } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  instance.openForm(rowA)
  instance.retryEditorLoad()
  assert.strictEqual(getCalls.length, 2, meta.name + " retry must leave the first request pending")
  resolveGet(getCalls, 0, Object.assign({}, rowA, { [meta.descKey]: "stale-success", imageUrls: imagesA }))
  await tick()
  assert.notStrictEqual(instance.form[meta.descKey], "stale-success", meta.name + " old success must not fill a retried editor")
  assert.strictEqual(instance.formReady, false)
  assert.strictEqual(instance.formLoading, true)
  assert.strictEqual(instance.formLoadError, "")
  resolveGet(getCalls, 1, Object.assign({}, rowA, { [meta.descKey]: "retry-success", imageUrls: imagesA }))
  await tick()
  assert.strictEqual(instance.formReady, true)
  assert.strictEqual(instance.form[meta.descKey], "retry-success")
  assertSameList(instance.form.imageUrls, imagesA)
}

async function assertStaleLoadFailureDoesNotCoverRetry(kind) {
  const { meta, instance, getCalls } = setup(kind)
  const rowA = item(kind, "A", imagesA)
  const rowB = item(kind, "B", imagesB)
  instance.openForm(rowA)
  instance.retryEditorLoad()
  assert.strictEqual(getCalls.length, 2, meta.name + " retry must leave the first request pending")
  getCalls[0].request.reject(new Error("stale fail after retry"))
  await tick()
  assert.strictEqual(instance.formLoadError, "", meta.name + " old failure must not mark the retried request as failed")
  assert.strictEqual(instance.formLoading, true)
  assert.strictEqual(instance.formReady, false)
  resolveGet(getCalls, 1, Object.assign({}, rowA, { [meta.descKey]: "retry-after-fail", imageUrls: imagesA }))
  await tick()
  assert.strictEqual(instance.formReady, true)
  assert.strictEqual(instance.form[meta.descKey], "retry-after-fail")
  assertSameList(instance.form.imageUrls, imagesA)

  const switched = setup(kind)
  switched.instance.openForm(rowA)
  switched.instance.openForm(rowB)
  assert.strictEqual(switched.getCalls.length, 2, meta.name + " opening B must leave A's request pending")
  switched.getCalls[0].request.reject(new Error("A failed"))
  await tick()
  assert.strictEqual(switched.instance.formLoadError, "", meta.name + " A's load failure must not flag B")
  assert.strictEqual(switched.instance.formLoading, true)
  assert.strictEqual(switched.instance.editorTargetId, rowB[meta.idKey])
  resolveGet(switched.getCalls, 1, rowB)
  await tick()
  assert.strictEqual(switched.instance.formReady, true)
  assert.strictEqual(switched.instance.form[meta.idKey], rowB[meta.idKey])
  assertSameList(switched.instance.form.imageUrls, imagesB)
}

async function assertOeSupplierAndFocusStayIsolated() {
  const { instance, getCalls } = setup("oe")
  const rowA = item("oe", "A", imagesA)
  const rowB = item("oe", "B", imagesB)
  let focused = 0
  instance.$refs.purchaseReferenceUrlInput = { focus() { focused += 1 } }
  instance.openForm(rowA, true)
  instance.openForm(rowB)
  resolveGet(getCalls, 1, rowB)
  await tick()
  instance.flushNextTick()
  instance.form.supplierName = "SupplierB"
  instance.form.supplierPhone = "222"
  resolveGet(getCalls, 0, rowA)
  await tick()
  instance.flushNextTick()
  assert.strictEqual(focused, 0, "OE delayed focus from A must not steal B")
  assert.strictEqual(instance.form.supplierName, "SupplierB")
  assert.strictEqual(instance.form.supplierPhone, "222")
  assert.strictEqual(instance.form.oeItemId, 2)

  const create = setup("oe")
  let createFocused = 0
  create.instance.$refs.purchaseReferenceUrlInput = { focus() { createFocused += 1 } }
  create.instance.openForm(undefined, true)
  create.instance.openForm(rowB)
  create.instance.flushNextTick()
  assert.strictEqual(createFocused, 0, "OE create focus queued for A must not run after switching to B")
  resolveGet(create.getCalls, 0, rowB)
  await tick()
  create.instance.flushNextTick()
  assert.strictEqual(createFocused, 0)
  assert.strictEqual(create.instance.form.oeItemId, 2)

  const stay = setup("oe")
  let stayFocused = 0
  stay.instance.$refs.purchaseReferenceUrlInput = { focus() { stayFocused += 1 } }
  stay.instance.openForm(undefined, true)
  stay.instance.flushNextTick()
  assert.strictEqual(stayFocused, 1, "OE create focus must still work on the original session")
}

async function run() {
  assertSourceContracts()
  for (const kind of kinds) {
    await assertLateEditDoesNotClobberCurrent(kind)
    await assertCloseOrCreateDropsPendingEdit(kind)
    await assertDetailLatestWins(kind)
    await assertStaleSaveDoesNotTouchLaterEditor(kind)
    await assertStaleSaveDoesNotClearLaterSaving(kind, request => request.resolve({}))
    await assertStaleSaveDoesNotClearLaterSaving(kind, request => request.reject(new Error("save failed")))
    await assertValidateBindsClickSession(kind)
    await assertCloseStartDoesNotHitReopenedSameRecord(kind)
    await assertNormalSaveAndRetry(kind)
    await assertCreateKeepsImages(kind)
    await assertLoadFailureRetryStaysOnCurrentRecord(kind)
    await assertStaleLoadSuccessDoesNotCoverRetry(kind)
    await assertStaleLoadFailureDoesNotCoverRetry(kind)
  }
  await assertOeSupplierAndFocusStayIsolated()
  console.log("oeGiftEditorIsolation: real OE/gift editor, detail and save isolation passed")
}

run().catch(error => {
  console.error(error)
  process.exitCode = 1
})
