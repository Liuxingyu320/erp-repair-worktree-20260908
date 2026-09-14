const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const compiler = require('vue-template-compiler')
const file = path.resolve(__dirname, '../src/views/oa/signPackage/index.vue')
const descriptor = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const imports = []
const script = descriptor.script.content.replace(/import\s+([\s\S]*?)\s+from\s+["'][^"']+["']\s*/g, (_, names) => {
  imports.push(...names.replace(/[{}]/g, '').split(',').map(name => name.trim()).filter(Boolean))
  return ''
}).replace('export default', 'return')
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function preview(id, overrides = {}) {
  return { data: { planId: id, previewToken: `preview-${id}`, action: 'RESTORED', targetVersionId: '11',
    targetVersionNo: 1, restoreVersionId: '11', activeVersions: [{ versionId: '12', versionNo: 2 }], ...overrides } }
}
function harness(handlers = {}) {
  const state = { deptId: '8' }
  const calls = { publish: [], success: [], refresh: [] }
  const get = handlers.get || (async id => ({ data: { planId: id } }))
  const loadPreview = handlers.preview || (async id => preview(id))
  const globals = Object.fromEntries(imports.map(name => [name, () => {}]))
  Object.assign(globals, {
    getSelectedSignScopeDeptId: () => state.deptId,
    getSignPlan: get, previewPublishSignPlan: loadPreview,
    publishSignPlan: async (id, request) => { calls.publish.push({ id, request }); return handlers.publish
      ? handlers.publish(id, request) : { data: { planId: id, versionId: '11', versionNo: 1, matchingStatus: 'ENABLED', action: 'RESTORED' } } }
  })
  function localRequire(id) {
    return require(id.startsWith('@/') ? path.resolve(__dirname, '../src', id.slice(2))
      : id.startsWith('./') ? path.resolve(path.dirname(file), id) : id)
  }
  const definition = new Function('require', ...Object.keys(globals), script)(localRequire, ...Object.values(globals))
  const model = { $store: { state: { user: { id: '9', sessionRevision: 1 } } },
    $modal: { msgSuccess: value => calls.success.push(value) },
    publishOpen: false, publishTargetId: '', publishDetail: null, publishPreview: null,
    publishError: '', publishPreviewLoading: false, publishing: false, publishBlockingReasons: [],
    getPlans: () => calls.refresh.push(true) }
  for (const name of ['publishOperationScope', 'handlePublishPlan', 'confirmPublishPlan']) model[name] = definition.methods[name].bind(model)
  return { model, state, calls, definition }
}
test('restoration uses one preview and sends its exact version; double click emits one write', async () => {
  const response = deferred()
  const { model, calls } = harness({ publish: () => response.promise })
  await model.handlePublishPlan({ planId: '9007199254740993' })
  const pending = model.confirmPublishPlan()
  await model.confirmPublishPlan()
  assert.deepEqual(calls.publish, [{ id: '9007199254740993', request: { previewToken: 'preview-9007199254740993', restoreVersionId: '11' } }])
  response.resolve({ data: { planId: '9007199254740993', versionId: '11', versionNo: 1, matchingStatus: 'ENABLED', action: 'RESTORED' } })
  await pending
  assert.equal(model.publishOpen, false)
  assert.equal(calls.success.length, 1)
  assert.equal(calls.refresh.length, 1)
})
for (const failure of [false, true]) test(`old preview ${failure ? 'failure' : 'success'} after A-B-A cannot overwrite later loading`, async () => {
  const pending = []
  const { model } = harness({ preview: id => { const task = deferred(); pending.push({ id, task }); return task.promise } })
  const first = model.handlePublishPlan({ planId: '1' })
  const second = model.handlePublishPlan({ planId: '2' })
  const third = model.handlePublishPlan({ planId: '1' })
  if (failure) pending[0].task.reject(new Error('old failure')); else pending[0].task.resolve(preview('1'))
  await first
  assert.equal(model.publishPreview, null)
  assert.equal(model.publishError, '')
  assert.equal(model.publishPreviewLoading, true)
  pending[1].task.resolve(preview('2')); await second
  assert.equal(model.publishPreviewLoading, true)
  pending[2].task.resolve(preview('1', { previewToken: 'latest' })); await third
  assert.equal(model.publishPreview.previewToken, 'latest')
})
for (const boundary of ['session', 'deactivate', 'close', 'org']) test(`publication late reply is inert after ${boundary}`, async () => {
  const pending = deferred()
  const { model, calls, state, definition } = harness({ publish: () => pending.promise })
  await model.handlePublishPlan({ planId: '1' })
  const write = model.confirmPublishPlan()
  if (boundary === 'session') { model.$store.state.user.sessionRevision++; definition.watch['$store.state.user.sessionRevision'].call(model) }
  if (boundary === 'deactivate') definition.deactivated.call(model)
  if (boundary === 'close') { model.publishOpen = false; definition.watch.publishOpen.call(model, false) }
  if (boundary === 'org') state.deptId = '10'
  model.publishing = false
  pending.resolve({ data: { planId: '1', versionId: '11', versionNo: 1, matchingStatus: 'ENABLED', action: 'RESTORED' } })
  await write
  assert.deepEqual(calls.success, [])
  assert.deepEqual(calls.refresh, [])
  assert.equal(model.publishError, '')
})
for (const receipt of [{}, { planId: '2', matchingStatus: 'ENABLED', action: 'RESTORED' },
  { planId: '1', versionId: '11', matchingStatus: 'DISABLED', action: 'RESTORED' },
  { planId: '1', versionId: '12', matchingStatus: 'ENABLED', action: 'RESTORED' }]) {
  test(`ambiguous receipt remains in the same confirmation with recovery: ${JSON.stringify(receipt)}`, async () => {
    const { model, calls } = harness({ publish: async () => ({ data: receipt }) })
    await model.handlePublishPlan({ planId: '1' })
    await model.confirmPublishPlan()
    assert.equal(model.publishOpen, true)
    assert.equal(model.publishing, false)
    assert.match(model.publishError, /暂未确认/)
    assert.deepEqual(calls.success, [])
  })
}
test('failed write preserves preview and inputs for explicit refresh', async () => {
  const { model } = harness({ publish: async () => { throw new Error('预览已过期，请刷新') } })
  await model.handlePublishPlan({ planId: '1' })
  const detail = model.publishDetail
  await model.confirmPublishPlan()
  assert.equal(model.publishDetail, detail)
  assert.equal(model.publishOpen, true)
  assert.equal(model.publishing, false)
  assert.match(model.publishError, /过期/)
})
