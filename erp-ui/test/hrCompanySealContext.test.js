const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const compiler = require('vue-template-compiler')
const descriptor = compiler.parseComponent(fs.readFileSync(path.resolve(__dirname, '../src/views/oa/signPackage/CompanySealManagement.vue'), 'utf8'))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const script = descriptor.script.content.replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, '').replace('export default', 'return')
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness() {
  const calls = [], messages = [], env = { validate: callback => callback(true) }
  const api = name => (...args) => { const pending = deferred(); calls.push({ name, args, ...pending }); return pending.promise }
  const definition = new Function('require', 'getSelectedSignScopeDeptId', 'listCompanySeals', 'saveCompanySeal', script)(
    id => require(path.resolve(__dirname, '../src', id.slice(2))), () => '10', api('list'), api('save'))
  const model = { ...definition.data(), $store: { state: { user: { id: '7', sessionRevision: 1 } } },
    $refs: { sealForm: { validate: callback => env.validate(callback), clearValidate() {} } },
    $nextTick: callback => callback(), $modal: { msgSuccess: message => messages.push(message) } }
  Object.entries(definition.methods).forEach(([name, fn]) => { model[name] = fn.bind(model) })
  const company = id => ({ legalEntityId: id, legalEntityName: `Company ${id}` })
  const seal = id => ({ legalEntityId: id, sealId: `${id}0`, sealName: `Seal ${id}` })
  const take = name => { const call = calls.find(c => c.name === name && !c.used); assert.ok(call, name); call.used = true; return call }
  const open = async id => { const pending = model.openSeals(company(id)); take('list').resolve({ data: [seal(id)] }); await pending }
  return { model, calls, messages, env, definition, company, seal, take, open }
}
for (const rejected of [false, true]) test(`old company list ${rejected ? 'failure' : 'success'} cannot overwrite next company or loading`, async () => {
  const h = harness(), a = h.model.openSeals(h.company('1')), old = h.take('list')
  const b = h.model.openSeals(h.company('2')), current = h.take('list')
  if (rejected) old.reject(Error('old')); else old.resolve({ data: [h.seal('1')] })
  await a
  assert.deepEqual(h.model.seals, [])
  assert.equal(h.model.sealLoading, true)
  assert.equal(h.model.sealListError, '')
  current.resolve({ data: [h.seal('2')] }); await b
  assert.equal(h.model.seals[0].legalEntityId, '2')
})
test('wrong-company response and stale row cannot open an editable seal', async () => {
  const h = harness(), pending = h.model.openSeals(h.company('2'))
  h.take('list').resolve({ data: [h.seal('1')] }); await pending
  assert.deepEqual(h.model.seals, [])
  h.model.openSeal(h.seal('1'))
  assert.equal(h.model.sealOpen, false)
  await h.open('2')
  h.model.openSeal(h.seal('1'))
  assert.equal(h.model.sealOpen, false)
})
test('changing company during validation cannot submit old seal against new company', async () => {
  const h = harness(); await h.open('1'); h.model.openSeal(h.seal('1'))
  let validate; h.env.validate = callback => { validate = callback }
  const pending = h.model.saveSeal()
  await h.open('2'); validate(true); await pending
  assert.equal(h.calls.filter(c => c.name === 'save').length, 0)
})
for (const rejected of [false, true]) test(`old save ${rejected ? 'failure' : 'success'} cannot close or unlock the new seal editor`, async () => {
  const h = harness(); await h.open('1'); h.model.openSeal(h.seal('1'))
  const first = h.model.saveSeal(), old = h.take('save')
  assert.equal(old.args[0].legalEntityId, '1')
  await h.open('2'); h.model.openSeal(h.seal('2'))
  const second = h.model.saveSeal(), current = h.take('save')
  if (rejected) old.reject(Error('old')); else old.resolve({ data: true }); await first
  assert.equal(h.model.sealOpen, true)
  assert.equal(h.model.sealSaving, true)
  assert.equal(h.model.sealForm.legalEntityId, '2')
  assert.equal(h.model.sealError, '')
  assert.deepEqual(h.messages, [])
  current.reject(Error('current failure')); await second
  assert.equal(h.model.sealSaving, false)
  assert.equal(h.model.sealError, 'current failure')
})
test('duplicate save is suppressed and frozen company title cannot be overwritten by row data', async () => {
  const h = harness(); await h.open('1'); h.model.openSeal({ ...h.seal('1'), legalEntityName: 'stale title' })
  assert.equal(h.model.sealForm.legalEntityName, 'Company 1')
  const pending = h.model.saveSeal(), write = h.take('save'); await h.model.saveSeal()
  assert.equal(h.calls.filter(c => c.name === 'save').length, 1)
  write.reject(Error('retry')); await pending
  assert.equal(h.model.sealOpen, true)
  assert.equal(h.model.sealForm.sealName, 'Seal 1')
})
