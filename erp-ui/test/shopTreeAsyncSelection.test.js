const test = require('node:test'), assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
// A tiny local DOM allows Vue 2 to run its real delayed-component mount hooks.
class Node {
  constructor(type, tag, text = '') { this.nodeType = type; this.tagName = tag; this.data = text; this.childNodes = []; this.parentNode = null; this.attrs = {} }
  appendChild(n) { if (n.parentNode) n.parentNode.removeChild(n); this.childNodes.push(n); n.parentNode = this; return n }
  insertBefore(n, ref) { if (!ref) return this.appendChild(n); if (n.parentNode) n.parentNode.removeChild(n); this.childNodes.splice(this.childNodes.indexOf(ref), 0, n); n.parentNode = this; return n }
  removeChild(n) { this.childNodes.splice(this.childNodes.indexOf(n), 1); n.parentNode = null; return n }
  setAttribute(k, v) { this.attrs[k] = v }
  removeAttribute(k) { delete this.attrs[k] }
  hasAttribute(k) { return Object.hasOwn(this.attrs, k) }
  get firstChild() { return this.childNodes[0] }
  get nextSibling() { return this.parentNode && this.parentNode.childNodes[this.parentNode.childNodes.indexOf(this) + 1] }
  get textContent() { return this.nodeType === 3 ? this.data : this.childNodes.map(n => n.textContent).join('') }
  set textContent(v) { this.childNodes = []; this.data = v }
  querySelector() { return null }
}
global.window = { navigator: { userAgent: 'test' }, addEventListener() {}, removeEventListener() {} }
global.document = { createElement: tag => new Node(1, tag.toUpperCase()), createElementNS: (_, tag) => new Node(1, tag.toUpperCase()),
  createTextNode: text => new Node(3, undefined, text), createComment: text => new Node(8, undefined, text),
  createEvent: () => ({ timeStamp: 0 }), querySelector: () => null }
const Vue = require('vue'), compiler = require('vue-template-compiler'), babel = require('@babel/core')
Vue.config.productionTip = false; Vue.config.devtools = false
const source = fs.readFileSync(path.resolve(__dirname, '../src/views/select-shop/index.vue'), 'utf8')
const compile = text => babel.transformSync(text, { babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
function moduleFrom(code, imports, globals = {}) {
  const module = { exports: {} }
  vm.runInNewContext(compile(code), { module, exports: module.exports, require: id => { if (!Object.hasOwn(imports, id)) throw Error(id); return imports[id] }, window, console, ...globals })
  return module.exports
}
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const flush = async () => { await new Promise(r => setImmediate(r)); await Vue.nextTick() }
function setup(factory) {
  const calls = [], warnings = [], routes = [], highlights = [], cache = { id: '83', type: 'STORE', clears: 0, writes: [] }
  const storage = { getItem: key => ({ selected_dept_id: cache.id, selected_dept_type: cache.type })[key] || null }
  const shop = moduleFrom(fs.readFileSync(path.resolve(__dirname, '../src/utils/shopContext.js'), 'utf8'), {}, { sessionStorage: storage })
  Object.assign(shop, { clearSelectedDept() { cache.clears++; cache.id = null }, setSelectedDept(...args) { cache.writes.push(args); cache.id = String(args[0]) } })
  const component = moduleFrom(compiler.parseComponent(source).script.content, {
    '@/api/system/dept': { listShopTree: () => { const call = deferred(); calls.push(call); return call.promise } },
    '@/plugins/element-services': { MessageBox: {} }, '@/utils/desktopContextPolicy': { requiresInventoryContext: () => true },
    '@/utils/shopContext': shop, '@/utils/passwordResetReminder': { consumePendingPasswordResetReminder: () => null },
    '@/views/mobile/mobileNavigation': {}, '@/utils/clientPlatform': { isMobileClient: () => false },
    '@/utils/uiOperationScope': require('../src/utils/uiOperationScope'), './shopEntryRouting': { resolveNoBusinessContextRedirect: () => '', resolveShopEntryRedirect: () => '/index' },
    '@/utils/profileCompletion': { isProfileCompletionRequiredError: () => false }
  }).default
  const store = Vue.observable({ getters: { id: '7', permissions: [] }, state: { user: { sessionRevision: 1 } } })
  const tree = { name: 'TestTree', methods: { filter() {}, setCurrentKey(id) { highlights.push(id) }, getNode: id => ({ data: { deptId: id }, level: 1 }) }, render: h => h('div') }
  const page = new Vue({ ...component, beforeCreate() { this.$store = store; this.$route = { query: {}, fullPath: '/select-shop' }; this.$router = { push: p => { routes.push(p); return Promise.resolve() } }; this.$message = { warning: m => warnings.push(m), info() {}, success() {} } },
    render(h) { return h('div', this.deptTree.length ? [h(factory || tree, { ref: 'deptTree', on: { 'hook:mounted': this.handleTreeReady } })] : []) }
  }).$mount()
  const data = [{ deptId: '1', deptName: '公司', deptType: 'COMPANY', children: [{ deptId: 83, deptName: '已授权门店', deptType: 'STORE' }] }]
  return { page, calls, warnings, routes, cache, highlights, tree, data, store }
}

test('MOB-N01 cold and warm async tree retain authorized cached organization and replay only visual state', async () => {
  let resolveTree
  const factory = () => new Promise(r => { resolveTree = r })
  const h = setup(factory); h.calls[0].resolve({ data: h.data }); await flush()
  assert.equal(h.page.$refs.deptTree, undefined); assert.equal(h.cache.clears, 0); assert.equal(h.warnings.length, 0)
  assert.equal(h.page.selectedDeptId, 83); assert.equal(h.page.selectedDeptPath, '公司 / 已授权门店'); assert.equal(h.page.treeReady, true)
  resolveTree(h.tree); await flush(); assert.ok(h.page.$refs.deptTree); assert.equal(h.highlights.at(-1), 83)
  h.page.$destroy()
  const warm = setup(factory); warm.calls[0].resolve({ data: warm.data }); await flush()
  assert.equal(warm.cache.clears, 0); assert.equal(warm.page.selectedDeptId, 83); assert.ok(warm.page.$refs.deptTree); warm.page.$destroy()
})

test('MOB-N01 failed authorization read preserves cache and blocks entering until successful retry', async () => {
  const h = setup(); h.calls[0].reject(Error('offline')); await flush()
  assert.equal(h.cache.clears, 0); assert.equal(h.page.selectedDeptId, '83'); assert.equal(h.page.treeReady, false)
  h.page.handleConfirm(); assert.equal(h.routes.length, 0); assert.match(h.page.treeError, /已保留原选择/)
  const read = h.page.fetchDeptTree(); h.calls[1].resolve({ data: h.data }); await read; await flush()
  h.page.handleConfirm(); assert.equal(h.routes.length, 1); assert.equal(h.cache.clears, 0); h.page.$destroy()
})

test('MOB-N01 only confirmed missing or invalid business organization clears cached selection', async () => {
  for (const type of ['COMPANY', 'MISSING']) {
    const h = setup(); h.calls[0].resolve({ data: type === 'MISSING' ? [] : [{ deptId: 83, deptName: '公司', deptType: type }] }); await flush()
    assert.equal(h.cache.clears, 1); assert.equal(h.page.selectedDeptId, ''); assert.equal(h.warnings.length, 1); h.page.$destroy()
  }
})

test('MOB-N01 account change drops old authorization success/failure/finally and mount callbacks', async () => {
  const h = setup(); const old = h.calls[0]
  h.store.getters.id = '8'; h.store.state.user.sessionRevision++; await flush()
  old.resolve({ data: [] }); await flush(); assert.equal(h.cache.clears, 0); assert.equal(h.page.loading, true)
  h.calls[1].resolve({ data: h.data }); await flush(); assert.equal(h.page.selectedDeptId, 83); assert.equal(h.page.loading, false)
  const first = h.page.fetchDeptTree(), prior = h.calls[2]; const second = h.page.fetchDeptTree(), latest = h.calls[3]
  prior.reject(Error('old')); await first; assert.equal(h.page.loading, true); assert.equal(h.page.treeError, '')
  latest.resolve({ data: h.data }); await second; h.page.$destroy()
})

test('MOB-N01 unmount before delayed tree resolves cannot alter cached selection', async () => {
  let resolveTree; const h = setup(() => new Promise(r => { resolveTree = r }))
  h.calls[0].resolve({ data: h.data }); await flush(); h.page.$destroy(); resolveTree(h.tree); await flush()
  assert.equal(h.cache.clears, 0); assert.equal(h.highlights.length, 0)
})

test('MOB-N01 desktop and mobile entry templates bind async ready hook and compile', () => {
  assert.equal((source.match(/@hook:mounted="handleTreeReady"/g) || []).length, 2)
  assert.deepEqual(compiler.compile(compiler.parseComponent(source).template.content).errors, [])
})
