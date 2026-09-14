const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const compiler = require('vue-template-compiler')
const file = path.resolve(__dirname, '../src/views/mobile/feature/components/MobileEntityPicker.vue')
const descriptor = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const script = descriptor.script.content.replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, '').replace('export default', 'return')
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness(entity = 'employee', field = {}) {
  const calls = [], events = []
  const definition = new Function('require', 'MobileQuickCustomerForm', 'fetchMobileEntityOptions', script)(
    id => require(path.resolve(path.dirname(file), id)), {}, (kind, params) => { const pending = deferred(); calls.push({ kind, params, ...pending }); return pending.promise })
  const model = { ...definition.data(), entity, field, context: { selectedDeptId: '10' }, formData: {}, value: '',
    $store: { state: { user: { id: '7', sessionRevision: 1 } } }, $emit: (...args) => events.push(args) }
  Object.entries(definition.methods).forEach(([name, fn]) => { model[name] = fn.bind(model) })
  Object.entries(definition.computed).forEach(([name, fn]) => Object.defineProperty(model, name, { get: fn.bind(model) }))
  return { model, calls, events, definition }
}
for (const rejected of [false, true]) test(`editing keyword invalidates old query ${rejected ? 'failure' : 'success'}`, async () => {
  const h = harness(); h.model.keyword = 'A'; const old = h.model.searchOptions()
  h.model.keyword = 'B'; h.model.handleKeywordInput(); const current = h.model.searchOptions()
  if (rejected) h.calls[0].reject(Error('old')); else h.calls[0].resolve([{ value: '1', label: 'A' }])
  await old
  assert.equal(h.model.loading, true)
  assert.equal(h.model.loadError, '')
  assert.deepEqual(h.model.options, [])
  const option = { value: '2', label: 'B' }; h.calls[1].resolve([option]); await current
  h.model.selectOption(option)
  assert.ok(h.events.some(event => event[0] === 'input' && event[1] === '2'))
})
test('a changed dependency or replaced form prevents stale option selection', async () => {
  const h = harness('employee', { dependsOn: 'department' }); h.model.formData = { department: '10' }
  const pending = h.model.searchOptions(), option = { value: '7', label: 'Employee' }
  h.calls[0].resolve([option]); await pending
  h.model.formData.department = '11'; h.model.selectOption(option)
  assert.equal(h.events.length, 0)
  h.model.formData = { department: '10' }; h.model.selectOption(option)
  assert.equal(h.events.length, 0)
})
test('closing during a search cannot reopen the option panel', async () => {
  const h = harness(); h.model.open = true; const pending = h.model.searchOptions()
  h.model.open = false; h.definition.watch.open.call(h.model, false)
  h.calls[0].resolve([{ value: '7', label: 'Employee' }]); await pending
  assert.equal(h.model.open, false)
  assert.deepEqual(h.model.options, [])
})
test('session revision and deactivation retire pending responses', async () => {
  const h = harness(), pending = h.model.searchOptions()
  h.model.$store.state.user.sessionRevision++
  h.calls[0].resolve([{ value: '7', label: 'Employee' }]); await pending
  assert.deepEqual(h.model.options, [])
  const second = h.model.searchOptions(); h.definition.deactivated.call(h.model)
  h.calls[1].resolve([{ value: '8', label: 'Other' }]); await second
  assert.deepEqual(h.model.options, [])
})
test('typing clears selected value while retaining the new keyword', () => {
  const h = harness(); h.model.value = '7'; h.model.selectedOptionLabel = 'Employee'; h.model.keyword = 'New'
  h.model.handleKeywordInput()
  assert.ok(h.events.some(event => event[0] === 'selection-cleared'))
  assert.equal(h.model.keyword, 'New')
})
test('warehouse default applies only to the unfiltered initial candidate set', async () => {
  const h = harness('warehouse', { salesWarehouseDefault: true })
  h.model.keyword = 'narrow search'; const filtered = h.model.searchOptions()
  h.calls[0].resolve([{ value: '20', label: 'Matched' }]); await filtered
  assert.equal(h.events.length, 0)
  h.model.keyword = ''; const initial = h.model.searchOptions()
  h.calls[1].resolve([{ value: '20', label: 'Only available' }]); await initial
  assert.ok(h.events.some(event => event[0] === 'input' && event[1] === '20'))
})
