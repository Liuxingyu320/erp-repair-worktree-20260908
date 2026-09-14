const assert = require('node:assert/strict'), test = require('node:test')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler'), root = path.resolve(__dirname, '..')
const { getMobileFormConfig } = require('../src/views/mobile/feature/mobileFormConfigs')
const { buildMobileFormPayload } = require('../src/views/mobile/feature/mobileFormPayloads')
const { getMobileFormValidationError } = require('../src/views/mobile/feature/mobileValidation')
const diagnostics = []; Vue.config.errorHandler = e => diagnostics.push(e)
Vue.config.warnHandler = e => { if (!e.startsWith('Avoid mutating a prop directly')) diagnostics.push(Error(e)) }
test.afterEach(() => assert.deepEqual(diagnostics.splice(0), []))
const tick = async () => { await new Promise(r => setImmediate(r)); await Vue.nextTick() }
function setup(relative = 'src/views/inventory/components/SalesReturnSourcePicker.vue', props = {}) {
  const focusSpecs = []
  const env = { dept: 10 }, calls = [], errors = [], store = Vue.observable({ getters: { id: 7 } }), route = Vue.observable({ fullPath: '/salesReturn' })
  const api = new Proxy({}, { get(_, name) { if (name === '__esModule') return false; return (...args) => {
    let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); calls.push({ name, args, resolve, reject }); return promise
  } } })
  const file = path.join(root, relative), source = fs.readFileSync(file, 'utf8')
  const code = babel.transformSync(compiler.parseComponent(source).script.content, { filename: file, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, console, Promise, require(id) {
    if (id.startsWith('@/api/')) return api
    if (id === '@/utils/shopContext') return { getSelectedDeptId: () => env.dept, isSelectedStore: () => true }
    if (id === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: spec => { focusSpecs.push(spec); return {} } }
    if (id === '@/utils/returnSelection') return require('../src/utils/returnSelection')
    if (id === '../mobileQuickCustomer') return { createCustomerDraft: () => ({}), normalizeCustomerServiceCardContext: () => ({contextKey:''}), isCustomerQuickCreateAllowed: () => false }
    if (id === '@/utils/businessEmptyState') return { getBusinessEmptyText: () => '' }
    return {}
  } }, { filename: file })
  const definition = module.exports.default, emitted = []
  const c = new Vue({ ...definition, mixins: [], created: [], mounted: [], propsData: { contextKey: 'form1', active: true, ...props },
    beforeCreate() { this.$store = store; this.$route = route; this.$modal = { msgError: m => errors.push(m), msgWarning: m => errors.push(m), msgSuccess() {} } }
  })
  c.$on('select', x => emitted.push(x))
  return { c, definition, env, calls, focusSpecs, errors, store, route, emitted, take: () => calls.at(-1),
    async page(rows, total=rows.length) { const pending=c.loadPage(); calls.at(-1).resolve({rows,total}); await pending; await tick() },
    dispose() { c.$destroy() } }
}
const order = id => ({ orderId: id, orderNo: 'SO'+id, customerName: '客户'+id, orderDate: '2026-09-10' })
test('shared picker sends customer/order/date to server and can page beyond the original 20 orders', async () => {
  const h=setup(); Object.assign(h.c.query,{customerName:'客户A',orderNo:'SO',startDate:'2026-01-01',endDate:'2026-09-12'})
  await h.page([order('30')],41); assert.equal(h.take().name,'listSalesReturnSourceOrders')
  assert.deepEqual({...h.take().args[0]},{pageNum:1,pageSize:20,customerName:'客户A',orderNo:'SO',startDate:'2026-01-01',endDate:'2026-09-12'})
  const next=h.c.changePage(2); h.take().resolve({rows:[order('1')],total:41}); await next
  assert.equal(h.take().args[0].pageNum,2); h.c.choose(h.c.rows[0]); assert.equal(h.emitted[0].orderId,'1'); h.c.choose(h.c.rows[0]); assert.equal(h.emitted.length,1); h.dispose()
})
test('failed page retries the same page and retains filters', async () => {
  const h=setup(); h.c.query.customerName='A'; await h.page([order(1)],40)
  const next=h.c.changePage(2);h.take().reject(Error('offline'));await next
  assert.equal(h.c.query.pageNum,2); assert.equal(h.c.loading,false); assert.equal(h.c.rows.length,0)
  const retry=h.c.loadPage();assert.equal(h.take().args[0].pageNum,2);assert.equal(h.take().args[0].customerName,'A')
  h.take().resolve({rows:[order(2)],total:40});await retry;assert.equal(h.c.rows[0].orderId,'2');h.dispose()
})
test('search resets pagination; invalid/reversed dates make no request', async () => {
  const h=setup();h.c.query.pageNum=3;h.c.query.startDate='2026-09-20';h.c.query.endDate='2026-09-01'
  await h.c.search();assert.equal(h.c.query.pageNum,1);assert.equal(h.calls.length,0);assert.ok(h.c.loadError)
  const reset=h.c.reset();h.take().resolve({rows:[],total:0});await reset;assert.equal(h.take().args[0].startDate,'');h.dispose()
})
test('reversed list replies never overwrite the newer filters or loading state', async () => {
  const h=setup();const first=h.c.loadPage(), old=h.take();h.c.query.customerName='new';const second=h.c.search(), fresh=h.take()
  fresh.resolve({rows:[order(2)],total:1});await second;old.resolve({rows:[order(1)],total:99});await first
  assert.equal(h.c.rows[0].orderId,'2');assert.equal(h.c.total,1);assert.equal(h.c.loading,false);h.dispose()
})
for(const change of ['query','context','actor','silent-dept','route','inactive']) test('picker rejects stale selection after '+change+' changes, before watchers flush', async()=>{
  const h=setup();await h.page([order('9223372036854775807')]);const row=h.c.rows[0]
  if(change==='query')h.c.query.customerName='changed'
  if(change==='context')h.c.contextKey='form2'
  if(change==='actor')h.store.getters.id=8
  if(change==='silent-dept')h.env.dept=11
  if(change==='route')h.route.fullPath='/else'
  if(change==='inactive')h.c.active=false
  h.c.choose(row);assert.equal(h.emitted.length,0);await tick();h.dispose()
})
test('picker rejects malformed IDs/duplicates/counts and preserves exact Long strings', async()=>{
  for(const result of [{rows:[order(9007199254740992)],total:1},{rows:[order('1'),order('1')],total:2},{rows:[order(1)],total:null},{rows:[order('1e3')],total:1}]){
    const h=setup();const p=h.c.loadPage();h.take().resolve(result);await p;assert.ok(h.c.loadError);assert.equal(h.c.rows.length,0);h.dispose()
  }
  const h=setup();await h.page([order('9223372036854775807')]);h.c.choose(h.c.rows[0]);assert.equal(h.emitted[0].orderId,'9223372036854775807');h.dispose()
})
test('empty out-of-range page clamps to last page under the same filters',async()=>{
  const h=setup();h.c.query.pageNum=3;const p=h.c.loadPage();h.take().resolve({rows:[],total:21});await tick()
  assert.equal(h.calls.length,2);assert.equal(h.take().args[0].pageNum,2);h.take().resolve({rows:[order(21)],total:21});await p;assert.equal(h.c.rows[0].orderId,'21');h.dispose()
})
test('closing shared picker discards a late API reply',async()=>{
  const h=setup();const p=h.c.loadPage(),r=h.take();h.dispose();r.resolve({rows:[order(1)],total:1});await p;assert.equal(h.c.rows.length,0);assert.equal(h.c.loading,false)
})
function desktop(){return setup('src/views/inventory/salesReturn/index.vue')}
const detail=id=>({detailId:id,itemType:'product',itemId:id,productId:id,productName:'商品'+id,returnableQuantity:5,unitPrice:10})
test('PC source uses dedicated exact source API and discards old A after B',async()=>{
  const h=desktop(),c=h.c;c.dialogOpen=true;c.form.salesOrderId='1';const a=c.loadSalesOrder('1'),old=h.take()
  c.form.salesOrderId='2';const b=c.loadSalesOrder('2'),fresh=h.take()
  assert.equal(fresh.name,'getSalesReturnSourceOrder');fresh.resolve({data:{...order('2'),details:[detail(20)]}});await b
  old.resolve({data:{...order('1'),details:[detail(10)]}});await a
  assert.equal(c.form.salesOrderNo,'SO2');assert.equal(c.form.details[0].salesDetailId,20);assert.equal(c.form.details[0].returnSelected,false);h.dispose()
})
test('PC wrong source response and changed account cannot populate the editor',async()=>{
  for(const drift of ['wrong-id','actor','same-id-form']){
    const h=desktop(),c=h.c;c.dialogOpen=true;c.form.salesOrderId='1';const p=c.loadSalesOrder('1'),r=h.take()
    if(drift==='actor')h.store.getters.id=8
    if(drift==='same-id-form')c.form={...c.form,details:[]}
    r.resolve({data:{...order(drift==='wrong-id'?'2':'1'),details:[detail(10)]}});await p
    assert.equal(c.form.details.length,0);h.dispose()
  }
})
test('PC selection ignores zero unchecked rows, validates selected rows and strips UI flags',()=>{
  const h=desktop(),c=h.c;c.form.details=c.buildReturnDetails([detail(1),detail(2)])
  assert.equal(c.buildPayload(),null);c.form.details[0].returnSelected=true;c.form.details[0].quantity=2
  let out=c.buildPayload();assert.equal(out.details.length,1);assert.equal(out.details[0].quantity,2);assert.equal('returnSelected' in out.details[0],false)
  c.form.details[0].quantity=6;assert.equal(c.buildPayload(),null);c.fillAllReturnable();assert.equal(c.buildPayload().details.length,2)
  c.clearReturnSelection();assert.equal(c.buildPayload(),null);h.dispose()
})
test('PC submit existing draft calls ID-only specialized API without fetching generic detail',async()=>{
  const h=desktop();h.c.$modal.confirm=()=>Promise.resolve();h.c.getList=()=>Promise.resolve()
  const p=h.c.handleSubmit({returnId:'9223372036854775807'});await tick();assert.equal(h.take().name,'submitSalesReturnDraft');assert.equal(h.take().args[0],'9223372036854775807')
  h.take().resolve({code:200});await p;assert.equal(h.calls.length,1);h.dispose()
})
test('H5 uses shared source picker and preserves the selected source payload identity',()=>{
  const h=setup('src/views/mobile/feature/components/MobileEntityPicker.vue',{entity:'sales',formData:{returnId:undefined,shopDeptId:10},context:{selectedDeptId:10}})
  h.c.toggleSalesSource();h.c.selectSalesSource(order('123'));assert.equal(h.emitted.length,1);assert.equal(h.emitted[0].value,'123');assert.equal(h.c.open,false)
  h.c.toggleSalesSource();h.c.formData={shopDeptId:10};h.c.selectSalesSource(order('124'));assert.equal(h.emitted.length,1);h.dispose()
})
test('H5 D05 validation/payload considers selected sales lines only and preserves source prices',()=>{
  const config=getMobileFormConfig('salesReturn'),data={salesOrderId:'1',returnTitle:'退货',customerName:'客户',shopDeptId:10,details:[
    {salesDetailId:11,itemType:'product',itemId:1,productName:'商品',quantity:2,maxReturnQuantity:5,unitPrice:10,returnSelected:true},
    {salesDetailId:12,itemType:'gift',itemId:1,quantity:0,maxReturnQuantity:3,unitPrice:99,returnSelected:false}]}
  assert.equal(getMobileFormValidationError(config,data),null)
  const p=buildMobileFormPayload(config,data,{selectedDeptId:10,selectedDeptType:'STORE'})
  assert.equal(p.details.length,1);assert.equal(p.details[0].salesDetailId,11);assert.equal(p.details[0].amount,20)
})
test('three actual templates compile and non-sales H5 picker remains its existing branch',()=>{
  for(const rel of ['src/views/inventory/components/SalesReturnSourcePicker.vue','src/views/inventory/salesReturn/index.vue','src/views/mobile/feature/components/MobileEntityPicker.vue']){
    const s=fs.readFileSync(path.join(root,rel),'utf8'),r=compiler.compile(compiler.parseComponent(s).template.content)
    assert.deepEqual(r.errors,[]);assert.deepEqual(r.tips,[])
  }
})


test('PC specialist todo focus reads minimal action context instead of generic query detail',async()=>{
  const h=desktop();assert.equal(h.focusSpecs.length,1)
  const pending=h.focusSpecs[0].loadFocusedRow('9223372036854775807');assert.equal(h.take().name,'getSalesReturnActionContext');assert.equal(h.take().args[0],'9223372036854775807');assert.equal(h.take().args[1].silentError,true)
  h.take().resolve({data:{returnId:'9223372036854775807',status:'draft',_specialistSummaryOnly:true}});await pending;assert.equal(h.calls.length,1);h.dispose()
})
