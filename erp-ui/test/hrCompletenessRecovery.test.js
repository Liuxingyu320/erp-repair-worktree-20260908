const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs'), path = require('node:path')
const compiler = require('vue-template-compiler')
const scope = require('../src/utils/uiOperationScope')
const descriptor = compiler.parseComponent(fs.readFileSync(path.resolve(__dirname, '../src/views/mobile/hr/completeness/index.vue'), 'utf8'))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const script = descriptor.script.content.replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, '').replace('export default', 'return')
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness() {
  const calls = [], env = { dept: '8' }
  const definition = new Function('require', 'getSelectedDeptId', 'profileFieldLabel', 'listHrCompletenessEmployees', 'getHrMasterDataSummary', script)(
    () => scope, () => env.dept, x => x,
    params => { const pending = deferred(); calls.push({ params, ...pending }); return pending.promise }, async () => ({ data: {} }))
  const model = { ...definition.data(), $route: { fullPath: '/mobile/hr/completeness', query: {} },
    $store: { state: { user: { id: '7', sessionRevision: 1 } }, dispatch: async () => {} }, canViewMasterData: false }
  for (const [name, method] of Object.entries(definition.methods)) model[name] = method.bind(model)
  Object.defineProperty(model, 'hasMore', { get: () => definition.computed.hasMore.call(model) })
  return { model, calls, env, definition }
}
test('failed page 2 retries page 2 and keeps expanded fields and loaded rows', async () => {
  const { model, calls } = harness()
  const first = model.reload(); calls[0].resolve({ rows: [{ userId: '1' }], total: 3 }); await first
  model.expandedRows = { '1': true }
  const second = model.loadMore(); calls[1].reject(new Error('offline')); await second
  assert.equal(model.pageNum, 1)
  assert.deepEqual(model.rows, [{ userId: '1' }])
  assert.equal(model.expandedRows['1'], true)
  assert.match(model.loadError, /第2页/)
  const retry = model.loadMore(); calls[2].resolve({ rows: [{ userId: '2' }], total: 3 }); await retry
  assert.deepEqual(calls.map(c => c.params.pageNum), [1, 2, 2])
  assert.equal(model.pageNum, 2)
  assert.equal(model.loadError, '')
})
for (const rejected of [false, true]) test(`old page ${rejected ? 'failure' : 'success'} cannot finish new filter loading`, async () => {
  const { model, calls } = harness()
  const old = model.reload()
  model.$route = { fullPath: '/mobile/hr/completeness?deptId=9', query: { deptId: '9' } }
  const current = model.reload()
  if (rejected) calls[0].reject(Error('old')); else calls[0].resolve({ rows: [{ userId: 'old' }], total: 1 })
  await old
  assert.equal(model.loading, true)
  assert.equal(model.loadError, '')
  assert.deepEqual(model.rows, [])
  calls[1].resolve({ rows: [{ userId: 'new' }], total: 1 }); await current
  assert.deepEqual(model.rows, [{ userId: 'new' }])
})
test('organization ABA events invalidate earlier reads even when final ID matches', async () => {
  const { model, calls, env } = harness()
  const a = model.reload()
  env.dept = '9'; const b = model.handleDeptChanged()
  env.dept = '8'; const c = model.handleDeptChanged()
  calls[0].resolve({ rows: [{ userId: 'old' }], total: 1 }); await a
  calls[1].resolve({ rows: [], total: 0 }); await b
  assert.equal(model.loading, true)
  calls[2].resolve({ rows: [{ userId: 'fresh' }], total: 1 }); await c
  assert.deepEqual(model.rows, [{ userId: 'fresh' }])
})
test('deactivation prevents result rendering and activation retries current query', async () => {
  const { model, calls, definition } = harness()
  const read = model.reload()
  definition.deactivated.call(model)
  calls[0].resolve({ rows: [{ userId: 'stale' }], total: 1 }); await read
  assert.deepEqual(model.rows, [])
  definition.activated.call(model)
  assert.equal(calls.length, 2)
  calls[1].resolve({ rows: [], total: 0 })
})
