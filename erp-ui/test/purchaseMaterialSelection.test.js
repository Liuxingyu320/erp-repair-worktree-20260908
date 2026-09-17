const assert = require('node:assert/strict'), test = require('node:test')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler'), root = path.resolve(__dirname, '..')
const diagnostics = []; Vue.config.errorHandler = e => diagnostics.push(e); Vue.config.warnHandler = e => diagnostics.push(Error(e))
test.afterEach(() => assert.deepEqual(diagnostics.splice(0), []))
const tick = async () => { await new Promise(r => setImmediate(r)); await Vue.nextTick() }
function load(file, mock, cache = new Map()) {
  file = path.resolve(file); if (cache.has(file)) return cache.get(file).exports
  const module = { exports: {} }; cache.set(file, module)
  let source = fs.readFileSync(file, 'utf8')
  if (file.endsWith('.vue')) source = compiler.parseComponent(source).script.content
  const code = babel.transformSync(source, { filename: file, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  vm.runInNewContext(code, { module, exports: module.exports, console, Promise, Set, Map, Object, Array, String, Number, JSON,
    document: { childNodes: [] }, process: { env: {} }, require(id) {
      if (id === 'vue') return Vue
      const overridden = mock(id); if (overridden !== undefined) return overridden
      if (id.startsWith('element-ui/')) return load(path.join(root, 'node_modules', id + '.js'), mock, cache)
      if (id.startsWith('.')) return load(path.resolve(path.dirname(file), id + '.js'), mock, cache)
      return {}
    } }, { filename: file })
  return module.exports
}
const Store = load(path.join(root, 'node_modules/element-ui/packages/table/src/store/index.js'), () => undefined).default
const catalog = (type, id) => ({ status: '0', supplierName: '供应商A', ...(type === 'product' ? { productId: id, productName: 'P'+id } :
  type === 'oe' ? { oeItemId: id, oeItemName: 'O'+id } : { giftId: id, giftName: 'G'+id }) })
function setup() {
  const env = { dept: 10 }, store = Vue.observable({ getters: { id: 7 } }), route = Vue.observable({ fullPath: '/purchase' })
  const calls = [], errors = [], api = new Proxy({}, { get(_, id) { if (id === '__esModule') return false; return (...args) => {
    let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); calls.push({ id, args, resolve, reject }); return promise
  } } })
  const definition = load(path.join(root, 'src/views/inventory/purchase/index.vue'), id => {
    if (id === '@/utils/inventoryQuantity') return require('../src/utils/inventoryQuantity')
      if (id.startsWith('@/api/')) return api
    if (id === '@/utils/shopContext') return { getSelectedDeptId: () => env.dept, isSelectedWarehouse: () => true }
    if (id === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
    if (id.startsWith('@/') || id.startsWith('./')) return {}
  }).default
  const c = new Vue({ ...definition, created: [], mixins: [], beforeDestroy: [],
    beforeCreate() { this.$store = store; this.$route = route; this.$modal = { msgError: x => errors.push(x) } }
  })
  c.refreshReceiveRecovery = () => {}; c.invalidateReceiveSession = () => {}
  c.form = { orderId: '30', supplierId: '40', supplierName: '供应商A', details: [] }
  const ts = new Store(); ts.table = { updateScrollY() {}, debouncedUpdateLayout() {},
    $emit(name, rows, row) { if (name === 'select') c.handleProductRowSelect(rows, row); if (name === 'select-all') c.handleProductSelectionChange(rows) } }
  ts.states.rowKey = c.productSelectionKey; ts.states.reserveSelection = false
  c.$refs.productTable = { clearSelection: () => ts.clearSelection(), toggleRowSelection: (row, value) => ts.toggleRowSelection(row, value, false) }
  c.$watch('productList', rows => ts.commit('setData', rows))
  c.openProductSelector()
  async function page(type, ids, n = 1) {
    c.selectedItemType = type; c.productQuery.pageNum = n
    const pending = c.getProductList(); calls.at(-1).resolve({ rows: ids.map(id => catalog(type,id)), total: 50 })
    await pending; await tick()
  }
  return { c, definition, env, store, route, calls, errors, ts, page,
    select: index => ts.toggleRowSelection(c.productList[index]),
    all: () => ts._toggleAllSelection(),
    dispose() { c.$destroy(); ts.$destroy() } }
}
test('actual Element store preserves chosen items over page/search/type changes with same numeric IDs', async () => {
  const h = setup(); await h.page('product',[1,2]); h.select(0); h.select(1)
  await h.page('product',[3],2); assert.equal(h.c.productSelection.length,2); h.select(0)
  h.c.productQuery.productName = '搜索'; await h.page('oe',[1]); h.select(0)
  await h.page('gift',[1]); h.select(0); assert.equal(h.c.productSelection.length,5)
  h.c.confirmProductSelection(); assert.deepEqual(h.c.form.details.map(x => x.itemType+':'+x.itemId),['product:1','product:2','product:3','oe:1','gift:1'])
  h.c.confirmProductSelection(); assert.equal(h.c.form.details.length,5); h.dispose()
})
test('returning to a page restores checked rows; deselect all only removes that page', async () => {
  const h = setup(); await h.page('product',[1,2]); h.all(); await h.page('product',[3],2); h.select(0)
  await h.page('product',[1,2]); assert.equal(h.ts.states.selection.length,2); h.all()
  assert.deepEqual(h.c.productSelection.map(x=>x.itemId),[3]); h.dispose()
})
test('chosen list removes off-page items and clear empties widgets without losing the order draft', async () => {
  const h = setup(); await h.page('product',[1]); h.select(0); await h.page('oe',[2]); h.select(0)
  h.c.removeProductSelection(h.c.productSelection[0]); await tick(); assert.equal(h.c.productSelection.length,1)
  h.c.clearProductSelection(); await tick(); assert.equal(h.c.productSelection.length,0); assert.equal(h.ts.states.selection.length,0)
  assert.equal(h.c.form.details.length,0); h.dispose()
})
test('query edits and failed loads retain the chosen list; retry restores checks', async () => {
  const h = setup(); await h.page('product',[1]); h.select(0)
  h.c.productQuery.productName = '不匹配'; await tick(); assert.equal(h.c.productSelection.length,1)
  const pending = h.c.getProductList(); h.calls.at(-1).reject(Error('network')); await pending
  assert.equal(h.c.productSelection.length,1); assert.equal(h.c.productLoading,false)
  await h.page('product',[1]); assert.equal(h.ts.states.selection.length,1); h.dispose()
})
test('out-of-order catalog responses cannot change selections or decode as a new type', async () => {
  const h = setup(); await h.page('product',[1]); h.select(0)
  h.c.selectedItemType = 'oe'; const old = h.c.getProductList(), a = h.calls.at(-1)
  await h.page('gift',[2]); h.select(0); a.resolve({ rows:[catalog('oe',9)], total:1 }); await old
  assert.equal(h.c.productList[0].itemType,'gift'); assert.deepEqual(h.c.productSelection.map(x=>x.itemType),['product','gift']); h.dispose()
})
for (const change of ['supplier','order','same-id-form','actor','silent-dept','route','close']) test(change + ' change cannot add stale chosen items', async () => {
  const h = setup(); await h.page('product',[1]); h.select(0)
  if(change==='supplier') h.c.form.supplierId='41'
  if(change==='order') h.c.form.orderId='31'
  if(change==='same-id-form') h.c.form={...h.c.form,details:[]}
  if(change==='actor') h.store.getters.id=8
  if(change==='silent-dept') h.env.dept=11
  if(change==='route') h.route.fullPath='/else'
  if(change==='close') h.c.closeProductSelector()
  h.c.confirmProductSelection(); assert.equal(h.c.form.details.length,0); await tick()
  assert.equal(h.c.productSelection.length,0); h.dispose()
})
test('selecting an existing detail increments it once and preserves its entered quantity and price otherwise', async () => {
  const h=setup(); h.c.form.details=[{itemType:'product',itemId:1,quantity:5,unitPrice:12}]
  await h.page('product',[1,2]); h.all(); h.c.confirmProductSelection()
  assert.equal(h.c.form.details.length,2); assert.equal(h.c.form.details[0].quantity,6); assert.equal(h.c.form.details[0].unitPrice,12); h.dispose()
})
test('supplier mismatch blocks the entire selected set without partial additions', async () => {
  const h=setup(); await h.page('product',[1,2]); h.all(); h.c.productSelection[1].supplierName='另一供应商'
  h.c.confirmProductSelection(); assert.equal(h.c.form.details.length,0); assert.equal(h.c.productSelection.length,2); assert.ok(h.errors.length); h.dispose()
})
test('source template uses explicit Element user selection events and shows chosen count/removal', () => {
  const source=fs.readFileSync(path.join(root,'src/views/inventory/purchase/index.vue'),'utf8')
  const compiled=compiler.compile(compiler.parseComponent(source).template.content)
  assert.deepEqual(compiled.errors,[]); assert.deepEqual(compiled.tips,[])
  assert.ok(source.includes('@select="handleProductRowSelect"')); assert.ok(!source.includes('@selection-change="handleProductSelectionChange"'))
  assert.ok(source.includes('aria-label="已选商品"')); assert.ok(source.includes(':row-key="productSelectionKey"'))
})

