const test = require('node:test'), assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const Vue = require('vue'), compiler = require('vue-template-compiler'), babel = require('@babel/core')
const scopeModule = require('../src/utils/uiOperationScope')
const root = path.resolve(__dirname, '..')
const tick = async () => { for (let i = 0; i < 5; i++) await Vue.nextTick() }
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function loadComponent(file, imports) {
  const script = compiler.parseComponent(fs.readFileSync(path.join(root, file), 'utf8')).script.content
  const code = babel.transformSync(script, { babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, console, window: { addEventListener() {}, removeEventListener() {} }, require: id => { if (!Object.hasOwn(imports, id)) throw Error(id); return imports[id] } })
  return module.exports.default
}
function makeAPI() {
  const calls = []
  const api = new Proxy({}, { get(_, name) { return (...args) => { const c = deferred(); calls.push({ name, args, ...c }); return c.promise } } })
  const take = name => { const c = calls.find(c => c.name === name && !c.used); assert.ok(c, 'Expected ' + name); c.used = true; return c }
  return { calls, api, take }
}
function setup(file) {
  const h = makeAPI(), env = { dept: '9', confirm: () => Promise.resolve(), all: true }, messages = [], downloads = []
  const imports = { '@/utils/uiOperationScope': scopeModule, '@/utils/shopContext': { getSelectedDeptId: () => env.dept }, '@/api/oa/salary': h.api, '@/api/oa/attendanceV2': h.api, '@/api/system/salaryConfig': h.api, '@/utils/exportConfirm': {}, '@/views/hr/components/LegacySalaryNotice': {}, '@/utils/businessEmptyState': { getBusinessEmptyText: () => 'empty' }, '@/views/mobile/attendance/attendancePunchPolicy': { dataOf: response => response.data } }
  const c = loadComponent(file, imports), store = Vue.observable({ getters: { id: '1', permissions: [] }, state: { user: { sessionRevision: 1 } } })
  const p = new Vue({ ...c, created: [], propsData: { shopContext: { deptId: '9', isStore: true } }, beforeCreate() { this.$store = store; this.$route = { path: '/page' }; this.$auth = { hasPermi: () => env.all }; this.$modal = { confirm: (...args) => env.confirm(...args), msgSuccess: m => messages.push(m), msgWarning: m => messages.push(m), msgError: m => messages.push(m) } } })
  p.download = (...args) => { downloads.push(args); return Promise.resolve() }; p.exportFileName = name => name
  return { ...h, p, env, store, messages, downloads }
}
for (const [file, api, rows, busy] of [
  ['CorrectionManagement', 'listShopAttendanceCorrections', 'rows', 'loading'],
  ['LeaveManagement', 'listShopAttendanceLeaves', 'requests', 'requestLoading']
]) {
  test('ROOT-06 ' + file + ' filters trigger fresh read and old response/finally never own B', async () => {
    const h = setup('src/views/oa/attendance/components/' + file + '.vue'), p = h.p
    p.query.status = 'PENDING'; await tick(); const a = h.take(api)
    p.query.status = 'APPROVED'; await tick(); const b = h.take(api)
    assert.equal(a.args[0].status, 'PENDING'); assert.equal(b.args[0].status, 'APPROVED')
    a.reject({ code: 403, message: 'old unauthorized' }); await tick(); assert.equal(p[busy], true); assert.equal(p.readError, '')
    b.resolve({ data: [{ id: 'B' }] }); await tick(); assert.equal(p[rows][0].id, 'B'); assert.equal(p[busy], false)
    p.query.status = 'RETURNED'; await tick(); const bad = h.take(api); bad.reject(Error('offline')); await tick()
    assert.equal(p[rows].length, 0); assert.match(p.readError, /offline/); p.$destroy()
  })
  test('ROOT-06 ' + file + ' account/organization changes clear old rows and prevent old detail resurrection', async () => {
    const h = setup('src/views/oa/attendance/components/' + file + '.vue'), p = h.p
    const action = p.openDetail('101'), detailApi = file === 'LeaveManagement' ? 'getAttendanceLeave' : 'getAttendanceCorrection', old = h.take(detailApi)
    h.store.state.user.sessionRevision++; await tick(); old.resolve({ data: { [file === 'LeaveManagement' ? 'leaveRequestId' : 'correctionRequestId']: '101' } }); await action
    assert.equal(p.detail, null); assert.equal(p.detailOpen, false)
    h.take(api).resolve({ data: [] }); await tick()
    p.shopContext = { deptId: '10', isStore: true }; await tick(); const fresh = h.take(api); assert.equal(fresh.args[0].shopId, '10'); fresh.resolve({ data: [] }); await tick(); p.$destroy()
  })
}
test('ROOT-06 historical salary month A/B includes total and scope and drops old error/finally', async () => {
  const h = setup('src/views/oa/salary/index.vue'), p = h.p
  p.salaryMonth = '2026-08'; await tick(); const a = h.take('listMySalary')
  p.salaryMonth = '2026-09'; await tick(); const b = h.take('listMySalary')
  assert.equal(a.args[0].salaryMonth, '2026-08'); assert.equal(b.args[0].salaryMonth, '2026-09')
  a.reject(Error('old')); await tick(); assert.equal(p.loading, true); assert.equal(p.listError, '')
  b.resolve({ rows: [{ salaryId: 'B' }], total: 2 }); await tick(); assert.equal(p.total, 2)
  p.viewScope = 'all'; const all = p.getList(); h.take('listAllSalary').resolve({ rows: [{ salaryId: 'all' }], total: 9 }); await all; assert.equal(p.list[0].salaryId, 'all')
  p.$options.deactivated.forEach(fn => fn.call(p)); assert.equal(p.list.length, 0)
  p.$options.activated.forEach(fn => fn.call(p)); h.take('listAllSalary').resolve({ rows: [], total: 0 }); await tick(); assert.equal(p.total, 0); p.$destroy()
})
test('ROOT-06 salary scheme A/B tier responses stay bound to selected scheme', async () => {
  const h = setup('src/views/system/salary/index.vue'), p = h.p
  p.selectScheme({ schemeId: '101' }); const a = h.take('listSalaryItems')
  p.selectScheme({ schemeId: '102' }); const b = h.take('listSalaryItems')
  a.reject(Error('old A')); await tick(); assert.equal(p.itemLoading, true); assert.equal(p.itemLoadError, '')
  b.resolve({ data: [{ itemId: 'B', schemeId: '102' }] }); await tick(); assert.equal(p.schemeItems[0].itemId, 'B'); assert.equal(p.itemLoadedSchemeId, '102')
  const old = p.loadSchemeItems(), o = h.take('listSalaryItems'), current = p.loadSchemeItems(), n = h.take('listSalaryItems')
  n.resolve({ data: [{ itemId: 'new', schemeId: '102' }] }); await current; o.resolve({ data: [{ itemId: 'old' }] }); await old
  assert.equal(p.schemeItems[0].itemId, 'new'); p.$destroy()
})
test('ROOT-06 salary scheme list late replies cannot replace the new filter or start obsolete tier reads', async () => {
  const h = setup('src/views/system/salary/index.vue'), p = h.p
  p.schemeQueryParams.schemeName = 'A'; const a = p.getSchemeList(), ra = h.take('listSalaryScheme')
  p.schemeQueryParams.schemeName = 'B'; const b = p.getSchemeList(), rb = h.take('listSalaryScheme')
  rb.resolve({ rows: [{ schemeId: '102', schemeName: 'B' }], total: 1 }); await tick(); const tiers = h.take('listSalaryItems')
  ra.resolve({ rows: [{ schemeId: '101' }], total: 20 }); await a
  assert.equal(p.selectedScheme.schemeId, '102'); assert.equal(p.schemeTotal, 1); assert.equal(p.itemLoading, true)
  tiers.resolve({ data: [] }); await b; assert.equal(p.itemLoadedSchemeId, '102'); p.$destroy()
})
test('ROOT-07 my/all export uses a fixed explicit endpoint and strips only pagination', async () => {
  const h = setup('src/views/oa/salary/index.vue'), p = h.p
  p.salaryMonth = '2026-09'; await tick(); h.take('listMySalary').resolve({ rows: [], total: 0 }); await tick()
  p.queryParams.pageNum = 3; p.queryParams.pageSize = 20; p.queryParams.keyword = 'kept'
  await p.handleExport(); assert.equal(h.downloads[0][0], 'oa/salary/export/my'); assert.equal(h.downloads[0][1].pageNum, undefined); assert.equal(h.downloads[0][1].keyword, 'kept')
  p.viewScope = 'all'; await p.handleExport(); assert.equal(h.downloads[1][0], 'oa/salary/export')
  h.env.all = false; await p.handleExport(); assert.equal(h.downloads[2][0], 'oa/salary/export/my'); p.$destroy()
})
test('ROOT-07 changing month/session while confirm is pending or cancelling never downloads', async () => {
  const h = setup('src/views/oa/salary/index.vue'), p = h.p
  const prompt = deferred(); h.env.confirm = () => prompt.promise; const exporting = p.handleExport()
  p.salaryMonth = '2026-10'; await tick(); prompt.resolve(); await exporting; assert.equal(h.downloads.length, 0)
  h.take('listMySalary').resolve({ rows: [], total: 0 }); await tick()
  const cancelled = deferred(); h.env.confirm = () => cancelled.promise; const second = p.handleExport(); cancelled.reject('cancel'); await second; assert.equal(h.downloads.length, 0)
  const stale = deferred(); h.env.confirm = () => stale.promise; const third = p.handleExport(); h.store.state.user.sessionRevision++; await tick(); stale.resolve(); await third; assert.equal(h.downloads.length, 0); p.$destroy()
})
test('ROOT-06/07 all four templates compile', () => {
  for (const file of ['src/views/oa/attendance/components/CorrectionManagement.vue','src/views/oa/attendance/components/LeaveManagement.vue','src/views/oa/salary/index.vue','src/views/system/salary/index.vue']) {
    assert.deepEqual(compiler.compile(compiler.parseComponent(fs.readFileSync(path.join(root, file), 'utf8')).template.content).errors, [])
  }
})
