const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const Vue = require('vue')
const compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '..')
const tick = async () => { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
const copy = value => JSON.parse(JSON.stringify(value))
function load(file, mock, cache = new Map()) {
  file = path.resolve(file)
  if (cache.has(file)) return cache.get(file).exports
  const module = { exports: {} }; cache.set(file, module)
  let source = fs.readFileSync(file, 'utf8')
  if (file.endsWith('.vue')) {
    const parsed = compiler.parseComponent(source), result = compiler.compile(parsed.template.content)
    assert.deepStrictEqual(result.errors, [], file + ' template')
    source = parsed.script.content
  }
  const code = babel.transformSync(source, { filename: file, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  vm.runInNewContext(code, { module, exports: module.exports, console, Promise, Set, Map, Object, Array, String, Number, JSON,
    window: {addEventListener(){},removeEventListener(){}}, document: { childNodes: [] }, process: { env: {} }, require(id) {
      if (id === 'vue') return Vue
      if (id === '@/utils/uiOperationScope') return require('../src/utils/uiOperationScope')
      if (id === '@/utils/approvalCommandRecovery') return require('../src/utils/approvalCommandRecovery')
      if (mock(id) !== undefined) return mock(id)
      if (id.startsWith('element-ui/')) return load(path.join(root, 'node_modules', id + '.js'), mock, cache)
      if (id.startsWith('.')) return load(path.resolve(path.dirname(file), id + '.js'), mock, cache)
      if (id.startsWith('@/mixins/')) return load(path.join(root, 'src', id.slice(2) + '.js'), mock, cache)
      return {}
    }
  }, { filename: file })
  return module.exports
}
const Store = load(path.join(root, 'node_modules/element-ui/packages/table/src/store/index.js'), () => undefined).default
function setup(relative, params = {}) {
  const calls = [], notices = [], closed = [], events = []
  const env = { dept: 10 }, route = Vue.observable({ params, fullPath: '/' + relative }), store = { getters: Vue.observable({ id: 7 }), state: Vue.observable({user:{sessionRevision:1}}) }
  const api = new Proxy({}, { get(_, name) { if (name === '__esModule') return false; return (...args) => {
    let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); calls.push({ name, args, resolve, reject }); return promise
  } } })
  const component = load(path.join(root, 'src/views/system', relative), id => {
    if (id.startsWith('@/api/')) return api
    if (id === '@/utils/shopContext') return { getSelectedDeptId: () => env.dept }
    if (id === '@/utils/exportConfirm') return { confirmExportAction() {} }
    if (id === './selectUser' || id === './detail') return {}
  }).default
  const page = new Vue({ ...component, propsData: { roleId: params.roleId || 5 },
    beforeCreate() { this.$route = route; this.$store = store; this.$tab = { closeOpenPage: x => closed.push(x) }; this.$modal = { msgSuccess: x => notices.push(x), msgError: x => notices.push(x) }; this.addDateRange = q => q; this.resetForm = () => {} }
  })
  const table = { $emit(name, selection) { if (name === 'selection-change') page.handleSelectionChange(selection) }, updateScrollY() {}, debouncedUpdateLayout() {} }
  const tableStore = new Store(); tableStore.table = table
  tableStore.states.rowKey = row => String(row.userId == null ? row.roleId : row.userId)
  tableStore.states.reserveSelection = true
  page.$refs.table = {
    clearSelection: () => tableStore.clearSelection(),
    toggleRowSelection: (row, selected) => tableStore.toggleRowSelection(row, selected)
  }
  const dataKey = relative === 'role/selectUser.vue' ? 'userList' : relative === 'user/authRole.vue' ? 'roles' : null
  if (dataKey) page.$watch(dataKey, rows => tableStore.commit('setData', rows))
  page.$on('ok', () => events.push('ok'))
  const take = name => { const call = calls.find(call => call.name === name && !call.used); assert(call, 'missing ' + name); call.used = true; return call }
  return { page, component, route, store, tableStore, calls, env, notices, closed, events, take,
    count: name => calls.filter(call => call.name === name).length, dispose() { page.$destroy(); tableStore.$destroy() } }
}
const cases = []
const test = (name, run) => cases.push({ name, run })
for (const [relative, api, field, params] of [
  ['post/index.vue', 'listPost', 'postList', {}], ['dict/index.vue', 'listType', 'typeList', {}],
  ['role/authUser.vue', 'allocatedUserList', 'userList', { roleId: '5' }]
]) {
  test(relative + ' failure releases spinner; retry and reversed responses preserve current result', async () => {
    const h = setup(relative, params), p = h.page
    h.take(api).reject(new Error('network fail')); await tick()
    assert.equal(p.loading, false); assert.equal(p.loadError, 'network fail')
    const a = p.getList(), b = p.getList(), ra = h.take(api), rb = h.take(api)
    rb.resolve({ rows: [{ name: 'current' }], total: 1 }); await b
    ra.reject(new Error('obsolete')); await a
    assert.equal(p[field][0].name, 'current'); assert.equal(p.loadError, ''); assert.equal(p.loading, false)
    assert(h.calls.filter(x => x.name === api).every(x => x.args[1].silentError === true)); h.dispose()
  })
  test(relative + ' context change and keepalive discard old reads without a stuck spinner', async () => {
    const h = setup(relative, params), p = h.page, old = h.take(api); h.env.dept = 11
    old.resolve({ rows: [{ name: 'wrong shop' }], total: 1 }); await tick()
    assert.equal(p.loading, false); assert(p.loadError); assert.equal(p[field].length, 0)
    const retry = p.getList(), pending = h.take(api)
    for (const fn of p.$options.deactivated) fn.call(p)
    pending.reject(new Error('old')); await retry
    for (const fn of p.$options.activated) fn.call(p)
    h.take(api).resolve({ rows: [{ name: 'reentered' }], total: 1 }); await tick()
    assert.equal(p[field][0].name, 'reentered'); assert.equal(p.loading, false); h.dispose()
  })
}
test('dictionary initial type/options failure offers retry; no unscoped list query', async () => {
  const h = setup('dict/data.vue', { dictId: '8' }), p = h.page
  h.take('getType').reject(new Error('type failed')); h.take('optionselect').resolve({ data: [] }); await tick()
  assert.equal(p.loading, false); assert(p.loadError); assert.equal(h.count('listData'), 0)
  const retry = p.retryList()
  h.take('getType').resolve({ data: { dictType: 'kind' } }); h.take('optionselect').resolve({ data: [{ dictType: 'kind' }] }); await tick()
  const list = h.take('listData'); assert.equal(list.args[0].dictType, 'kind')
  list.resolve({ rows: [{ dictCode: 1 }], total: 1 }); await retry
  assert.equal(p.dataList[0].dictCode, 1); assert.equal(p.loading, false); assert.equal(p.loadError, ''); h.dispose()
})
const user = id => ({ userId: id, userName: 'user' + id, nickName: '员工' + id, dept: { deptName: '测试' }, status: '0' })
test('dictionary route changes stop obsolete bootstrap before another list API is started', async () => {
  const h = setup('dict/data.vue', { dictId: '8' }), p = h.page
  const oldType = h.take('getType'), oldOptions = h.take('optionselect')
  h.route.params.dictId = '9'; h.route.fullPath = '/dict/9'; await tick()
  oldType.resolve({ data: { dictType: 'old-type' } }); oldOptions.resolve({ data: [] }); await tick()
  assert.equal(h.count('listData'), 0)
  h.take('getType').resolve({ data: { dictType: 'new-type' } }); h.take('optionselect').resolve({ data: [] }); await tick()
  const current = h.take('listData'); assert.equal(current.args[0].dictType, 'new-type')
  current.resolve({ rows: [], total: 0 }); await tick(); assert.equal(p.queryParams.dictType, 'new-type'); h.dispose()
})
test('actual Element UI store preserves cross-page and searched users, removes one, submits all once', async () => {
  const h = setup('role/selectUser.vue', { roleId: 5 }), p = h.page
  const open = p.show(); h.take('unallocatedUserList').resolve({ rows: [user(1), user(2)], total: 4 }); await open; await tick()
  p.clickRow(p.userList[0]); p.clickRow(p.userList[1]); assert.equal(p.selectedUsers.length, 2)
  p.queryParams.pageNum = 2
  const next = p.getList(); h.take('unallocatedUserList').resolve({ rows: [user(3), user(4)], total: 4 }); await next; await tick()
  assert.deepStrictEqual(copy(p.userIds), [1, 2]); p.clickRow(p.userList[0])
  p.queryParams.userName = 'user4'; const search = p.handleQuery(); h.take('unallocatedUserList').resolve({ rows: [user(4)], total: 1 }); await search; await tick()
  assert.deepStrictEqual(copy(p.userIds), [1, 2, 3])
  p.removeSelectedUser(p.selectedUsers[1]); assert.deepStrictEqual(copy(p.userIds), [1, 3])
  const save = p.handleSelectUser(); await p.handleSelectUser(); const request = h.take('authUserSelectAll')
  assert.deepStrictEqual(copy(request.args[0]), { roleId: 5, userIds: '1,3' }); assert.equal(h.count('authUserSelectAll'), 1)
  request.reject(new Error('save failed')); await save; assert.equal(p.selectedUsers.length, 2); assert(p.submitError)
  const save2 = p.handleSelectUser(); h.take('authUserSelectAll').resolve({ msg: 'saved' }); await save2; assert.equal(p.visible, false); assert.equal(h.events.length, 1); h.dispose()
})
test('role picker reopen and role change clear prior selection and invalidate old read/write', async () => {
  const h = setup('role/selectUser.vue', { roleId: 5 }), p = h.page
  const a = p.show(), old = h.take('unallocatedUserList')
  const b = p.show(); h.take('unallocatedUserList').resolve({ rows: [user(2)], total: 1 }); await b; await tick()
  old.resolve({ rows: [user(1)], total: 1 }); await a; assert.equal(p.userList[0].userId, 2)
  p.clickRow(p.userList[0]); const save = p.handleSelectUser(), pending = h.take('authUserSelectAll')
  p.roleId = 6; await tick(); h.take('unallocatedUserList').resolve({ rows: [user(3)], total: 1 }); await tick()
  pending.resolve({ msg: 'old' }); await save
  assert.equal(p.visible, true); assert.equal(p.userIds.length, 0); assert.equal(h.events.length, 0); assert.equal(p.saving, false); h.dispose()
})
const roles = () => [{ roleId: 1, roleName: '正常', status: '0', flag: true }, { roleId: 2, roleName: '已关联停用', status: '1', flag: true }, { roleId: 3, roleName: '未关联停用', status: '1', flag: false }]
test('malformed picker result retains selected users and blocks submission until retry succeeds', async () => {
  const h = setup('role/selectUser.vue', { roleId: 5 }), p = h.page
  const open = p.show(); h.take('unallocatedUserList').resolve({ rows: [user(1)], total: 1 }); await open; await tick(); p.clickRow(p.userList[0])
  const failed = p.getList(); h.take('unallocatedUserList').resolve({ rows: [user(2), user(2)], total: 2 }); await failed; await p.handleSelectUser()
  assert(p.loadError); assert.equal(p.loading, false); assert.equal(h.count('authUserSelectAll'), 0); assert.deepStrictEqual(copy(p.userIds), [1])
  const retry = p.getList(); h.take('unallocatedUserList').resolve({ rows: [user(2)], total: 1 }); await retry; await tick()
  assert.deepStrictEqual(copy(p.userIds), [1]); assert.equal(p.loadError, ''); h.dispose()
})
test('user roles failed read cannot submit empty grants; retry preselects active and disabled originals', async () => {
  const h = setup('user/authRole.vue', { userId: '7' }), p = h.page
  h.take('getAuthRole').reject(new Error('no read')); await tick(); await p.submitForm()
  assert.equal(p.loading, false); assert.equal(h.count('updateAuthRole'), 0)
  const retry = p.getList(); h.take('getAuthRole').resolve({ user: { userId: 7 }, roles: roles() }); await retry; await tick()
  assert.deepStrictEqual(copy(p.roleIds), [1, 2]); assert(p.checkSelectable(p.roles[1])); assert.equal(p.checkSelectable(p.roles[2]), false)
  p.clickRow(p.roles[1]); assert.deepStrictEqual(copy(p.roleIds), [1])
  p.clickRow(p.roles[2]); assert.deepStrictEqual(copy(p.roleIds), [1])
  p.clickRow(p.roles[1]); assert.deepStrictEqual(copy(p.roleIds), [1, 2])
  p.clickRow(p.roles[1]); const save = p.submitForm(); await p.submitForm()
  const request = h.take('updateAuthRole'); assert.deepStrictEqual(copy(request.args[0]), { userId: 7, roleIds: '1' }); assert.equal(h.count('updateAuthRole'), 1)
  request.reject(new Error('write failed')); await save; assert.equal(p.saving, false); assert(p.submitError); assert.equal(h.closed.length, 0); assert.deepStrictEqual(copy(p.roleIds), [1]); h.dispose()
})
test('user role route A to B rejects late A and sends only B with string identity intact', async () => {
  const h = setup('user/authRole.vue', { userId: '7' }), p = h.page, old = h.take('getAuthRole')
  h.route.params.userId = '9223372036854775000'; h.route.fullPath = '/other'; await tick()
  h.take('getAuthRole').resolve({ user: { userId: '9223372036854775000' }, roles: roles() }); await tick()
  old.resolve({ user: { userId: 7 }, roles: roles() }); await tick()
  assert.equal(p.form.userId, '9223372036854775000')
  const save = p.submitForm(); const request = h.take('updateAuthRole'); assert.equal(request.args[0].userId, '9223372036854775000')
  request.resolve({}); await save; assert.equal(h.closed.length, 1); h.dispose()
})
test('all changed read APIs preserve URL, method, query and default error behavior', async () => {
  for (const [file, name, args, url] of [
    ['post', 'listPost', [{ pageNum: 2 }], '/system/post/list'],
    ['dict/type', 'listType', [{ status: '0' }], '/system/dict/type/list'],
    ['dict/type', 'getType', [8], '/system/dict/type/8'],
    ['dict/type', 'optionselect', [], '/system/dict/type/optionselect'],
    ['dict/data', 'listData', [{ dictType: 'kind' }], '/system/dict/data/list'],
    ['role', 'allocatedUserList', [{ roleId: 5 }], '/system/role/authUser/allocatedList'],
    ['role', 'unallocatedUserList', [{ roleId: 5 }], '/system/role/authUser/unallocatedList'],
    ['user', 'getAuthRole', ['9223372036854775000'], '/system/user/authRole/9223372036854775000']
  ]) {
    const requests = []
    const api = load(path.join(root, 'src/api/system', file + '.js'), id => id === '@/utils/request' ? (options => { requests.push(options); return Promise.resolve() }) : undefined)
    for (const options of [undefined, {}, { silentError: 'true' }, { silentError: false }, { silentError: true, method: 'delete', url: '/bad', params: { injected: true } }]) {
      await api[name](...args, options)
      const actual = requests.at(-1)
      assert.equal(actual.url, url); assert.equal(actual.method, 'get'); assert.equal(actual.silentError, Boolean(options && options.silentError === true))
      if (args[0] && typeof args[0] === 'object') assert.deepStrictEqual(copy(actual.params), args[0])
      else assert.equal(actual.params, undefined)
      assert.equal(actual.data, undefined)
    }
  }
})
for (const picker of [false, true]) {
  const relative = picker ? 'role/selectUser.vue' : 'user/authRole.vue'
  const readName = picker ? 'unallocatedUserList' : 'getAuthRole'
  const writeName = picker ? 'authUserSelectAll' : 'updateAuthRole'
  const response = () => picker ? { rows: [user(1)], total: 1 } : { user: { userId: 7 }, roles: roles() }
  const submit = page => picker ? page.handleSelectUser() : page.submitForm()
  test(relative + ' idle account/organization change cannot submit the previously loaded selection', async () => {
    const h = setup(relative, picker ? { roleId: 5 } : { userId: '7' }), p = h.page
    if (picker) p.show()
    h.take(readName).resolve(response()); await tick()
    if (picker) p.clickRow(p.userList[0])
    h.store.getters.id = 8; h.env.dept = 20
    await submit(p)
    assert.equal(h.count(writeName), 0); assert(p.loadError)
    assert.equal(picker ? p.canSelectUser() : p.checkSelectable(p.roles[0]), false)
    const reload = p.getList(); h.take(readName).resolve(response()); await reload; await tick()
    if (picker) { assert.equal(p.userIds.length, 0); p.clickRow(p.userList[0]) }
    const write = submit(p); h.take(writeName).resolve({ msg: 'saved' }); await write
    assert.equal(h.count(writeName), 1); h.dispose()
  })
  test(relative + ' deactivation during save releases old busy state and reentry reloads without old navigation', async () => {
    const h = setup(relative, picker ? { roleId: 5 } : { userId: '7' }), p = h.page
    if (picker) p.show()
    h.take(readName).resolve(response()); await tick()
    if (picker) p.clickRow(p.userList[0])
    const write = submit(p), pending = h.take(writeName)
    assert.equal(p.saving, true)
    for (const fn of p.$options.deactivated) fn.call(p)
    assert.equal(p.saving, false)
    for (const fn of p.$options.activated) fn.call(p)
    h.take(readName).resolve(response()); await tick()
    pending.resolve({ msg: 'old ack' }); await write
    assert.equal(p.saving, false); assert.equal(p.loading, false); assert.equal(h.closed.length + h.events.length, 0)
    if (picker) assert.equal(p.visible, true)
    else assert.equal(p.loaded, true)
    h.dispose()
  })
}
async function run() {
  for (const c of cases) { await c.run(); console.log('PASS ' + c.name) }
  console.log('systemListAndRoleRecovery: ' + cases.length + ' real Vue/Element UI scenarios passed; 0 failed; 0 skipped')
}
const timer = setTimeout(() => { console.error('Unfinished async test'); process.exit(1) }, 20000)
run().then(() => clearTimeout(timer)).catch(error => { clearTimeout(timer); console.error(error); process.exitCode = 1 })
