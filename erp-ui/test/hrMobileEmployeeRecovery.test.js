const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')

// Run the shipped SFC methods and API module together. Only the HTTP transport,
// router/store and browser events are fixtures; no requests leave this process.
const root = path.resolve(__dirname, '..')
const tests = []
const test = (name, run) => tests.push({ name, run })
const tick = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }
function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}
function load(relative, imports, override) {
  const filename = override || path.join(root, 'src', relative)
  const source = fs.readFileSync(filename, 'utf8')
  const script = relative.endsWith('.vue') ? source.match(/<script>([\s\S]*?)<\/script>/)[1] : source
  const code = babel.transformSync(script, { filename, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, require(name) {
    if (name === "@/utils/positiveDecimalId") return require("../src/utils/positiveDecimalId")
    if (!(name in imports)) throw new Error(`Unexpected import ${name}`)
    return imports[name]
  } }, { filename })
  return relative.endsWith('.vue') ? module.exports.default : module.exports
}
function harness(query = {}) {
  const calls = [], summaries = []
  const env = { deptId: '10' }
  const request = config => {
    const next = deferred()
    calls.push({ config, ...next })
    return next.promise
  }
  const api = load('api/hr/employee.js', { '@/utils/request': request })
  const component = load('views/mobile/hr/employee/index.vue', {
    '@/api/hr/employee': api,
    '@/api/system/user': { changeUserStatus: () => Promise.resolve() },
    '@/utils/shopContext': { getSelectedDeptId: () => env.deptId, getSelectedDeptContext: () => ({ deptName: '测试组织' }) },
    '../components/MobileHrProfileEditor': {},
    '../mobileHrError': { mobileHrErrorMessage: (error, fallback) => error.message || fallback }
  }, process.env.HR_MOBILE_EMPLOYEE_SOURCE)
  const page = { $route: { query, fullPath: '/mobile/hr/employee' },
    $store: { dispatch: name => { summaries.push(name); return Promise.resolve() } },
    $message: { success() {} }, $modal: { confirm: () => Promise.resolve() } }
  Object.assign(page, component.data.call(page))
  Object.entries(component.methods).forEach(([name, fn]) => { page[name] = fn.bind(page) })
  Object.entries(component.computed).forEach(([name, fn]) => {
    Object.defineProperty(page, name, { get: fn.bind(page) })
  })
  return { page, component, env, calls, summaries }
}
const employees = Array.from({ length: 30 }, (_, i) => ({ userId: String(i + 1), employeeName: `员工${i + 1}` }))
function listReply(call) {
  const params = call.config.params
  const matches = params.userId ? employees.filter(row => row.userId === params.userId) : employees
  const start = (params.pageNum - 1) * params.pageSize
  call.resolve({ rows: matches.slice(start, start + params.pageSize), total: matches.length })
}

test('incomplete-profile entry locates employee 15 through the actual scoped list and detail APIs', async () => {
  const h = harness({ todoType: 'HR_PROFILE_INCOMPLETE', userId: '00015', deptId: '10' })
  const loading = h.page.reload()
  assert.equal(h.calls[0].config.url, '/system/hr/employee/list')
  assert.equal(h.calls[0].config.params.userId, '15')
  assert.equal(h.calls[0].config.params.deptId, '10')
  listReply(h.calls[0]); await tick()
  assert.equal(h.calls[1].config.url, '/system/hr/employee/15')
  h.calls[1].resolve({ data: { userId: '15', employeeName: '员工15' } }); await loading
  assert(h.page.editorOpen); assert.equal(h.page.editing.userId, '15')
  assert.equal(h.page.rows.length, 1); assert.equal(h.page.message, '')
  assert.equal(h.summaries.length, 0)
  assert(h.calls.every(call => call.config.silentError === true))
})

test('missing scoped target and failed list never claim the todo was completed', async () => {
  const h = harness({ todoType: 'HR_PROFILE_INCOMPLETE', userId: '15', deptId: '10' })
  const missing = h.page.reload(); h.calls[0].resolve({ rows: [], total: 0 }); await missing
  assert.equal(h.calls.length, 1); assert(!h.page.editorOpen)
  assert(h.page.message.includes('未找到')); assert(!h.page.emptyText.includes('事项已处理'))
  assert.equal(h.summaries.length, 0); assert.equal(h.page.routeFocusHandled, false)
  const failed = h.page.reload(); h.calls[1].reject(new Error('network')); await failed
  assert(h.page.message.includes('加载失败')); assert(!h.page.emptyText.includes('事项已处理'))
  assert.equal(h.summaries.length, 0)
})

test('failed automatic detail lookup can be retried by refresh', async () => {
  const h = harness({ todoType: 'HR_PROFILE_INCOMPLETE', userId: '15' })
  const first = h.page.reload(); listReply(h.calls[0]); await tick()
  h.calls[1].reject(new Error('详情暂不可用')); await first
  assert.equal(h.page.routeFocusHandled, false); assert.equal(h.page.message, '详情暂不可用')
  const retry = h.page.reload(); listReply(h.calls[2]); await tick()
  h.calls[3].resolve({ data: { userId: '15' } }); await retry
  assert(h.page.editorOpen); assert.equal(h.page.message, '')
})

test('empty detail response stays unresolved and refresh retries the focused employee', async () => {
  const h = harness({ userId: '15' }), p = h.page
  const first = p.reload(); listReply(h.calls[0]); await tick()
  h.calls[1].resolve({ data: null }); await first
  assert(!p.editorOpen); assert.equal(p.routeFocusHandled, false)
  assert(p.message.includes('未找到')); assert.equal(h.summaries.length, 0)
  const retry = p.reload(); listReply(h.calls[2]); await tick()
  h.calls[3].resolve({ data: { userId: '15' } }); await retry
  assert(p.editorOpen)
})

test('page 2 failure retries page 2 and appends every employee exactly once', async () => {
  const h = harness(), p = h.page
  let loading = p.reload(); listReply(h.calls[0]); await loading
  loading = p.loadMore(); assert.equal(p.pageNum, 1)
  p.loadMore(); assert.equal(h.calls.length, 2, 'duplicate click is ignored while loading')
  h.calls[1].reject(new Error('offline')); await loading
  assert.equal(p.pageNum, 1); assert.equal(p.rows.length, 10); assert(p.hasMore)
  loading = p.loadMore(); listReply(h.calls[2]); await loading
  assert.equal(p.pageNum, 2); assert.equal(p.message, '')
  loading = p.loadMore(); listReply(h.calls[3]); await loading
  assert.deepStrictEqual(h.calls.map(call => call.config.params.pageNum), [1, 2, 2, 3])
  assert.deepStrictEqual(Array.from(p.rows, row => row.userId), employees.map(row => row.userId))
  assert.equal(p.pageNum, 3); assert.equal(p.hasMore, false)
})

test('stale page append cannot change rows, page number or a newer loading state', async () => {
  const h = harness(), p = h.page
  let loading = p.reload(); listReply(h.calls[0]); await loading
  const stale = p.loadMore()
  p.keyword = 'new'; const current = p.reload()
  listReply(h.calls[1]); await stale
  assert.equal(p.pageNum, 1); assert.equal(p.rows.length, 0); assert(p.loading)
  h.calls[2].resolve({ rows: [{ userId: '22' }], total: 1 }); await current
  assert.equal(p.rows[0].userId, '22'); assert.equal(p.pageNum, 1); assert(!p.loading)
})

test('route and organization changes retire pending focused results', async () => {
  const h = harness({ userId: '15', deptId: '10' }), p = h.page
  const old = p.reload()
  p.$route.query = { userId: '16', deptId: '20' }; h.env.deptId = '20'
  h.component.watch['$route.fullPath'].call(p)
  listReply(h.calls[0]); await old
  assert.equal(h.calls.length, 2); assert.equal(p.rows.length, 0)
  listReply(h.calls[1]); await tick()
  assert.equal(h.calls[2].config.url, '/system/hr/employee/16')
  h.calls[2].resolve({ data: { userId: '16' } }); await tick()
  assert.equal(p.editing.userId, '16'); assert(p.editorOpen)
})

test('an explicit editor selection still wins over a pending route-focus result', async () => {
  const h = harness({ userId: '15' }), p = h.page
  const loading = p.reload(), chosen = p.openEditor({ userId: '16' })
  h.calls[1].resolve({ data: { userId: '16', employeeName: '已选员工' } }); await chosen
  p.editing.employeeName = '尚未保存'
  listReply(h.calls[0]); await loading
  assert.equal(h.calls.length, 2); assert.equal(p.editing.userId, '16')
  assert.equal(p.editing.employeeName, '尚未保存')
})

test('full positive Long range stays decimal text in list, detail and save URLs', async () => {
  for (const id of ['1', '9007199254740991', '9007199254740993', '9223372036854775807']) {
    const h = harness({ userId: id }), p = h.page
    assert.strictEqual(p.query().userId, id)
    const open = p.openEditor({ userId: id })
    assert.strictEqual(p.editorTargetId, id)
    assert.equal(h.calls[0].config.url, `/system/hr/employee/${id}`)
    h.calls[0].resolve({ data: { userId: id } }); await open
    const save = p.saveProfile({ userId: p.editing.userId, patch: { userId: 'wrong', employeeName: '已修改' } })
    assert.equal(h.calls[1].config.url, `/system/hr/employee/${id}`)
    assert.equal(h.calls[1].config.method, 'patch'); assert.equal(h.calls[1].config.data.userId, undefined)
    h.calls[1].resolve({ code: 200 }); await tick()
    h.calls[2].resolve({ rows: [{ userId: id }], total: 1 }); await save
  }
})

test('adjacent large IDs remain distinct and reverse responses preserve the later editor draft', async () => {
  const h = harness(), p = h.page
  const a = '9007199254740992', b = '9007199254740993'
  const older = p.openEditor({ userId: a }), newer = p.openEditor({ userId: b })
  assert(!p.isCurrentEditorRead(p.editorReadEpoch, a, h.env.deptId))
  h.calls[1].resolve({ data: { userId: b, employeeName: 'B' } }); await newer
  p.editing.employeeName = 'B的草稿'
  h.calls[0].resolve({ data: { userId: a, employeeName: 'A' } }); await older
  assert.equal(p.editing.userId, b); assert.equal(p.editing.employeeName, 'B的草稿')
})

test('invalid, overflow and already-rounded numeric IDs report an error without HTTP', async () => {
  for (const id of [null, '', '0', '-1', '1.5', '1e3', 'abc', '9223372036854775808', 9007199254740992, ['15']]) {
    const h = harness({ userId: id })
    await h.page.openEditor({ userId: id }); await h.page.reload()
    assert.equal(h.calls.length, 0, `no requests for ${JSON.stringify(id)}`)
    assert(h.page.message.includes('员工标识无效')); assert(!h.page.loading)
    assert(!h.page.emptyText.includes('事项已处理'))
  }
})

test('closing and keep-alive deactivation still invalidate pending long-ID reads', async () => {
  for (const retire of [p => p.closeEditor(), (p, c) => c.deactivated.call(p)]) {
    const h = harness(), p = h.page
    const pending = p.openEditor({ userId: '9223372036854775807' })
    retire(p, h.component)
    h.calls[0].resolve({ data: { userId: '9223372036854775807' } }); await pending
    assert(!p.editorOpen); assert.equal(p.editing, null)
  }
})

;(async () => {
  let passed = 0, failed = 0
  for (const { name, run } of tests) {
    let timeout
    try {
      await Promise.race([run(), new Promise((resolve, reject) => {
        timeout = setTimeout(() => reject(new Error('Scenario did not settle')), 1500)
      })])
      passed++; console.log(`PASS ${name}`)
    }
    catch (error) { failed++; console.error(`FAIL ${name}\n${error.stack}`) }
    finally { clearTimeout(timeout) }
  }
  console.log(JSON.stringify({ suite: 'hrMobileEmployeeRecovery', passed, failed, skipped: 0 }))
  if (failed) process.exitCode = 1
})()
