const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const compiler = require('vue-template-compiler')
const descriptor = compiler.parseComponent(fs.readFileSync(path.resolve(__dirname, '../src/views/oa/fixedAsset/repair/index.vue'), 'utf8'))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const script = descriptor.script.content.replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, '').replace('export default', 'return')
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness() {
  const calls = [], messages = [], env = { shop: '10', validate: callback => callback(true) }
  const api = name => (...args) => { const pending = deferred(); calls.push({ name, args, ...pending }); return pending.promise }
  const definition = new Function('require', 'getSelectedDeptContext', 'ImageUpload', 'ImageGallery',
    'submitFixedAssetRepairBatch', 'getFixedAssetQuota', 'listFixedAssetConfigs', script)(
    id => id.includes('todoBusinessFocus') ? { createTodoBusinessFocusMixin: () => ({}) } : require('../src/utils/uiOperationScope'),
    () => ({ isStore: true, deptId: env.shop }), {}, {}, api('submit'), api('quota'), api('assets'))
  const model = { ...definition.data(), $store: { state: { user: { id: '7', sessionRevision: 1 } } },
    $modal: { msgSuccess: value => messages.push(value), msgWarning: value => messages.push(value) },
    $refs: { repairForm: { validate: callback => env.validate(callback) } },
    currentStoreDeptId: '10', repairActionDisabled: false, repairQuotaExceeded: false }
  Object.entries(definition.methods).forEach(([name, fn]) => { model[name] = fn.bind(model) })
  model.getList = () => { calls.push({ name: 'list' }); return Promise.resolve() }
  const prepare = (description = 'A') => {
    model.repairOpen = true; model.form = { shopDeptId: env.shop, faultDescription: description, imageUrls: 'image-A' }
    model.repairAssetRows = [{ oeItemId: '7', repairQuantity: 1, assetQuantity: 2 }]
  }
  const take = name => { const call = calls.find(c => c.name === name && !c.used); assert.ok(call, name); call.used = true; return call }
  prepare()
  return { model, calls, messages, env, definition, prepare, take }
}
test('submit freezes input and duplicate submit/new button cannot replace the active draft', async () => {
  const h = harness(), first = h.model.submitRepair(), call = h.take('submit')
  await h.model.submitRepair(); h.model.openRepairForm()
  assert.equal(h.calls.filter(c => c.name === 'submit').length, 1)
  assert.equal(h.model.form.faultDescription, 'A')
  h.model.form.faultDescription = 'edited after send'
  assert.equal(call.args[0].faultDescription, 'A')
  call.reject(Error('network')); await first
  assert.equal(h.model.repairOpen, true)
  assert.equal(h.model.form.imageUrls, 'image-A')
  assert.match(h.model.repairError, /待核对/)
})
for (const rejected of [false, true]) test(`old ${rejected ? 'failure' : 'success'} cannot close a programmatically opened new draft`, async () => {
  const h = harness(), first = h.model.submitRepair(), old = h.take('submit')
  h.definition.watch.repairOpen.call(h.model, false); h.prepare('B')
  const second = h.model.submitRepair(), current = h.take('submit')
  if (rejected) old.reject(Error('old')); else old.resolve({ data: 1 }); await first
  assert.equal(h.model.repairOpen, true)
  assert.equal(h.model.saving, true)
  assert.equal(h.model.form.faultDescription, 'B')
  assert.deepEqual(h.messages, [])
  current.reject(Error('new failure')); await second
  assert.equal(h.model.saving, false)
})
test('validation callback after scope departure sends no request', async () => {
  const h = harness(); let finish; h.env.validate = callback => { finish = callback }
  const pending = h.model.submitRepair()
  h.model.invalidateRepairContext(); finish(true); await pending
  assert.equal(h.calls.filter(c => c.name === 'submit').length, 0)
})
test('quota conflict uses frozen store and old quota response cannot overwrite later draft quota', async () => {
  const h = harness(), pending = h.model.submitRepair()
  h.take('submit').reject({ response: { data: { errorCode: 'FIXED_ASSET_QUOTA_EXCEEDED', precheck: { message: '额度变化' } } } })
  await pending
  const quota = h.take('quota')
  assert.equal(quota.args[0].shopDeptId, '10')
  h.definition.watch.repairOpen.call(h.model, false); h.prepare('B'); h.model.quota = { availableQuotaAmount: 99 }
  quota.resolve({ data: { availableQuotaAmount: 1 } }); await Promise.resolve(); await Promise.resolve()
  assert.equal(h.model.quota.availableQuotaAmount, 99)
})
