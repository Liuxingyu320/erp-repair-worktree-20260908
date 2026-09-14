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
function setupUser() {
  const h = makeAPI(), env = { dept: '9', pii: true }, messages = []
  const component = loadComponent('src/views/system/user/view.vue', { '@/api/system/user': h.api, '@/utils/shopContext': { getSelectedDeptId: () => env.dept }, '@/utils/uiOperationScope': scopeModule, '@/views/hr/components/signingProfileOptions': {} })
  const store = Vue.observable({ getters: { id: '1', permissions: ['system:user:pii:read'] }, state: { user: { sessionRevision: 1 } } })
  const p = new Vue({ ...component, created: [], beforeCreate() { this.$store = store; this.$auth = { hasPermi: () => env.pii }; this.$message = { error: m => messages.push(m) } } })
  const base = id => ({ data: { userId: id, nickName: '用户' + id, dept: { deptName: '部门' + id }, profile: { employeeNo: 'E' + id } }, posts: [], roles: [] })
  return { ...h, p, env, store, messages, base }
}

test('PLAT-UI01 late A PII cannot merge into B public details or unlock B spinner', async () => {
  const h = setupUser(), p = h.p
  const a = p.open('101'); h.take('getUser').resolve(h.base('101')); await tick(); const pa = h.take('getUserPii')
  p.handleClose(); assert.deepEqual(JSON.parse(JSON.stringify(p.info)), {}); assert.equal(p.piiVisible, false)
  const b = p.open('102'); h.take('getUser').resolve(h.base('102')); await tick(); const pb = h.take('getUserPii')
  pa.resolve({ data: { userId: '101', nickName: 'A sensitive name', idNumber: 'A-secret' } }); await a
  assert.equal(p.info.userId, '102'); assert.equal(p.info.profile.employeeNo, 'E102'); assert.equal(p.loading, true); assert.equal(p.piiVisible, false)
  pb.resolve({ data: { userId: '102', phonenumber: 'B-phone', idNumber: 'B-secret', employeeNo: 'WRONG', roleIds: ['999'] } }); await b
  assert.equal(p.info.nickName, '用户102'); assert.equal(p.info.profile.idNumber, 'B-secret'); assert.equal(p.info.profile.employeeNo, 'E102'); assert.equal(p.loading, false)
  p.$destroy()
})

test('PLAT-UI01 old PII 403 is quiet, current PII 403 preserves only public data and supports retry', async () => {
  const h = setupUser(), p = h.p
  const a = p.open('101'); h.take('getUser').resolve(h.base('101')); await tick(); const pa = h.take('getUserPii')
  const b = p.open('102'); h.take('getUser').resolve(h.base('102')); await tick(); const pb = h.take('getUserPii')
  pa.reject({ code: 403 }); await a; assert.equal(p.piiError, ''); assert.equal(p.loading, true)
  pb.reject({ response: { status: 403 } }); await b
  assert.equal(p.piiError, '无权查看敏感资料'); assert.equal(p.info.userId, '102'); assert.equal(p.piiVisible, false)
  const retry = p.retryDetail(); h.take('getUser').resolve(h.base('102')); await tick(); h.take('getUserPii').resolve({ data: { userId: '102', bankAccount: 'B-bank' } }); await retry
  assert.equal(p.piiVisible, true); assert.equal(p.piiError, ''); p.$destroy()
})

test('PLAT-UI01 rejects mismatched public/PII targets and clears on session/organization boundaries', async () => {
  const h = setupUser(), p = h.p
  const mismatch = p.open('101'); h.take('getUser').resolve(h.base('102')); await mismatch
  assert.match(p.loadError, /已变化/); assert.equal(h.calls.some(c => c.name === 'getUserPii'), false)
  const read = p.open('101'); h.take('getUser').resolve(h.base('101')); await tick(); const pii = h.take('getUserPii')
  h.store.state.user.sessionRevision++; await tick(); pii.resolve({ data: { userId: '101', idNumber: 'secret' } }); await read
  assert.equal(p.visible, false); assert.equal(p.info.userId, undefined); assert.equal(p.piiVisible, false)
  h.env.pii = false; const publicOnly = p.open('102'); h.take('getUser').resolve(h.base('102')); await publicOnly
  assert.equal(p.info.userId, '102'); assert.equal(p.piiVisible, false); p.handleClose(); assert.equal(p.info.userId, undefined); p.$destroy()
})

function setupConfig() {
  const h = makeAPI(), env = { dept: '9', confirm: () => Promise.resolve() }, messages = []
  const c = loadComponent('src/views/system/config/index.vue', { '@/api/system/config': h.api, '@/utils/exportConfirm': {}, '@/utils/shopContext': { getSelectedDeptId: () => env.dept }, '@/utils/uiOperationScope': scopeModule })
  const store = Vue.observable({ getters: { id: '1' }, state: { user: { sessionRevision: 1 } } })
  const p = new Vue({ ...c, created: [], beforeCreate() { this.$store = store; this.$route = { path: '/system/config' }; this.$modal = { confirm: () => env.confirm(), msgSuccess: m => messages.push(m), msgWarning: m => messages.push(m) } } })
  p.addDateRange = (params, range) => ({ ...params, params: { beginTime: range[0], endTime: range[1] } })
  return { ...h, p, env, store, messages }
}

test('S02 immutable A/B filters and late A failure/finally do not change B results or loading', async () => {
  const h = setupConfig(), p = h.p
  p.queryParams.configName = 'A'; const a = p.getList(), ra = h.take('listConfig')
  p.queryParams.configName = 'B'; const b = p.getList(), rb = h.take('listConfig')
  assert.equal(ra.args[0].configName, 'A'); assert.equal(rb.args[0].configName, 'B')
  ra.reject(Error('A error')); await a; assert.equal(p.loading, true); assert.equal(p.listError, '')
  rb.resolve({ rows: [{ configId: '2', configName: 'B' }], total: 1 }); await b
  assert.equal(p.configList[0].configName, 'B'); assert.equal(p.total, 1); assert.equal(p.loading, false)
  const old = p.getList(), ro = h.take('listConfig'); const latest = p.getList(), rl = h.take('listConfig')
  rl.resolve({ rows: [{ configId: '3' }], total: 3 }); await latest; ro.resolve({ rows: [{ configId: '4' }], total: 99 }); await old
  assert.equal(p.configList[0].configId, '3'); assert.equal(p.total, 3); p.$destroy()
})

test('S02 failure clears stale rows/selections, retains filters and permits same-filter retry', async () => {
  const h = setupConfig(), p = h.p
  p.queryParams.configName = '目标'; p.ids = ['123']; p.configList = [{ configId: '123' }]
  const read = p.getList(); h.take('listConfig').reject(Error('offline')); await read
  assert.equal(p.configList.length, 0); assert.equal(p.ids.length, 0); assert.equal(p.listReady, false); assert.match(p.listError, /offline/)
  p.handleDelete({ configId: '123' }); assert.equal(h.calls.some(c => c.name === 'delConfig'), false)
  const retry = p.getList(), rr = h.take('listConfig'); assert.equal(rr.args[0].configName, '目标'); rr.resolve({ rows: [], total: 0 }); await retry; assert.equal(p.listReady, true); p.$destroy()
})

test('S02 delete confirmation freezes selected IDs and drops after actor changes', async () => {
  const h = setupConfig(), p = h.p
  const read = p.getList(); h.take('listConfig').resolve({ rows: [{ configId: '1' }, { configId: '2' }], total: 2 }); await read
  p.handleSelectionChange([{ configId: '1' }]); const confirm = deferred(); h.env.confirm = () => confirm.promise
  const deletion = p.handleDelete({}); p.ids.splice(0, 1, '2'); confirm.resolve(); await tick()
  const write = h.take('delConfig'); assert.deepEqual(Array.from(write.args[0]), ['1']); write.resolve({}); await deletion
  h.take('listConfig').resolve({ rows: [], total: 0 }); await tick()
  p.handleSelectionChange([{ configId: '1' }]); const cancelled = deferred(); h.env.confirm = () => cancelled.promise
  const stale = p.handleDelete({}); h.store.getters.id = '2'; await tick(); cancelled.resolve(); await stale
  assert.equal(h.calls.filter(c => c.name === 'delConfig').length, 1); p.$destroy()
})

test('S02 deactivation drops pending reads and activation re-queries preserved filters', async () => {
  const h = setupConfig(), p = h.p
  p.queryParams.configName = '保留'; const read = p.getList(), r = h.take('listConfig')
  p.$options.deactivated.forEach(fn => fn.call(p)); r.resolve({ rows: [{ configId: 'old' }], total: 1 }); await read
  assert.equal(p.configList.length, 0); p.$options.activated.forEach(fn => fn.call(p))
  const fresh = h.take('listConfig'); assert.equal(fresh.args[0].configName, '保留'); fresh.resolve({ rows: [], total: 0 }); h.take('listConfigDescriptors').resolve({ data: [] }); await tick(); assert.equal(p.listReady, true); p.$destroy()
})

test('PLAT-UI01 and S02 templates compile', () => {
  for (const file of ['src/views/system/user/view.vue', 'src/views/system/config/index.vue']) {
    const text = fs.readFileSync(path.join(root, file), 'utf8')
    assert.deepEqual(compiler.compile(compiler.parseComponent(text).template.content).errors, [])
  }
})
