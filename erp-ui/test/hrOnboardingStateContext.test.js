const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const compiler = require('vue-template-compiler')
const descriptor = compiler.parseComponent(fs.readFileSync(path.resolve(__dirname, '../src/views/hr/onboarding/index.vue'), 'utf8'))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const imports = []
const script = descriptor.script.content.replace(/import\s+([\s\S]*?)\s+from\s+["'][^"']+["']\s*/g, (_, names) => {
  imports.push(...names.replace(/[{}]/g, '').split(',').map(n => n.trim()).filter(Boolean)); return ''
}).replace('export default', 'return')
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness() {
  const calls = [], messages = [], env = { dept: '10', prompt: () => Promise.resolve({ value: '取消原因' }) }
  const globals = Object.fromEntries(imports.map(name => [name, () => {}]))
  const request = name => (...args) => { const pending = deferred(); calls.push({ name, args, ...pending }); return pending.promise }
  Object.assign(globals, { checkPermi: () => true, getSelectedDeptId: () => env.dept,
    isOnboardingVersionConflict: error => error.code === 'CONFLICT',
    listHrOnboarding: request('list'), getHrOnboarding: request('detail'),
    markHrOnboardingReady: request('ready'), cancelHrOnboarding: request('cancel') })
  const definition = new Function('require', ...Object.keys(globals), script)(id => require(path.resolve(__dirname, '../src', id.slice(2))), ...Object.values(globals))
  const model = { $route: { query: {}, fullPath: '/hr/onboarding' }, $store: { state: { user: { id: '7', sessionRevision: 1 } } },
    $message: { success: value => messages.push(value), warning: value => messages.push(value), error: value => messages.push(value) },
    $prompt: (...args) => env.prompt(...args), canQuery: true, canSelect: true }
  Object.assign(model, definition.data.call(model))
  Object.entries(definition.methods).forEach(([name, fn]) => { model[name] = fn.bind(model) })
  const record = (id, version = 1) => ({ onboardingId: id, version, allowedActions: ['MARK_READY', 'CANCEL'] })
  model.selectedOnboardingId = '1'; model.detail = record('1'); model.rows = [record('1'), record('2')]
  const take = name => { const call = calls.find(c => c.name === name && !c.used); assert.ok(call, name); call.used = true; return call }
  const select = async id => { const read = model.selectOnboarding(record(id)); take('detail').resolve({ data: record(id) }); await read }
  return { model, calls, messages, env, definition, record, take, select }
}
for (const outcome of ['success', 'failure', 'conflict']) test(`A state ${outcome} after selecting B cannot replace B or refresh it back to A`, async () => {
  const h = harness(), action = h.model.handleAction('MARK_READY'), old = h.take('ready')
  await h.select('2')
  if (outcome === 'success') old.resolve({ data: h.record('1', 2) })
  else old.reject(outcome === 'conflict' ? { code: 'CONFLICT' } : Error('old failure'))
  await action
  assert.equal(h.model.selectedOnboardingId, '2')
  assert.equal(h.model.detail.onboardingId, '2')
  assert.equal(h.calls.filter(c => c.name === 'list').length, 0)
  assert.deepEqual(h.messages, [])
})
test('old A finally cannot unlock a new B state action', async () => {
  const h = harness(), first = h.model.handleAction('MARK_READY'), old = h.take('ready')
  await h.select('2')
  const second = h.model.handleAction('MARK_READY'), current = h.take('ready')
  old.reject(Error('old failure')); await first
  assert.equal(h.model.actionLoading, true)
  current.reject(Error('B failure')); await second
  assert.equal(h.model.actionLoading, false)
  assert.deepEqual(h.messages, ['B failure'])
})
test('cancel confirmation cannot submit after A-B-A selection changes', async () => {
  const h = harness(), prompt = deferred(); h.env.prompt = () => prompt.promise
  const pending = h.model.handleAction('CANCEL')
  await h.select('2'); await h.select('1')
  prompt.resolve({ value: 'old reason' }); await pending
  assert.equal(h.calls.filter(c => c.name === 'cancel').length, 0)
})
test('current version conflict refreshes only the still-selected original record', async () => {
  const h = harness(), pending = h.model.handleAction('MARK_READY')
  h.take('ready').reject({ code: 'CONFLICT' })
  await Promise.resolve(); await Promise.resolve()
  h.take('list').resolve({ rows: [h.record('1', 2), h.record('2')], total: 2 })
  for (let i = 0; i < 12; i++) await Promise.resolve()
  h.take('detail').resolve({ data: h.record('1', 2) })
  await pending
  assert.equal(h.model.detail.version, 2)
  assert.equal(h.model.actionLoading, false)
})
test('session change makes an in-flight state reply inert', async () => {
  const h = harness(), pending = h.model.handleAction('MARK_READY'), call = h.take('ready')
  h.model.$store.state.user.sessionRevision++
  call.resolve({ data: h.record('1', 2) }); await pending
  assert.equal(h.model.detail.version, 1)
  assert.deepEqual(h.messages, [])
})
