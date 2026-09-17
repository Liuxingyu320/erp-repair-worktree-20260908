const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const parser = require('@babel/parser'), generate = require('@babel/generator').default
const { validInventoryQuantity } = require('../src/utils/inventoryQuantity')
const { createUiOperationScope } = require('../src/utils/uiOperationScope')
const { createInventoryDraftRecovery } = require('../src/utils/inventoryDraftRecovery')
function methods(name, globals = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/views', name.endsWith('.vue') ? name : name + '/index.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const ast = parser.parse(source, { sourceType: 'module' })
  const object = ast.program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const property = object.properties.find(node => node.key.name === 'methods').value
  return vm.runInNewContext('(' + generate({ ...property, properties: property.properties.filter(node => node.type === 'ObjectMethod') }).code + ')', { Promise, validInventoryQuantity, ...globals })
}
function instance(m, props = {}) {
  const result = { $modal: { msgError() {}, msgSuccess() {}, msgWarning() {} }, $store: { getters: { id: '7' }, state: { user: { sessionRevision: 1 } } }, $route: { fullPath: '/test' }, $nextTick: cb => cb(), $set: (obj, key, value) => { obj[key] = value }, ...props }
  for (const [name, method] of Object.entries(m)) result[name] = method.bind(result)
  return result
}
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a;reject=b }); return { promise, resolve, reject } }
const flush = async () => { for (let i=0;i<12;i++) await Promise.resolve() }
const ctx = { getSelectedDeptId: () => '20' }
test('sales return guards both writes before delayed form validation', async () => {
  const validation = deferred(), transport = deferred(), calls = []
  const x=instance(methods('inventory/salesReturn',{ ...ctx, saveSalesReturn: body => { calls.push(body); return transport.promise }, submitSalesReturn: () => { throw Error('duplicate submit') } }), { dialogOpen: true, formEpoch: 1, form: {}, submitLoading: false, isStoreContext: true, $refs: { formRef: { validate: cb => validation.promise.then(cb) } } })
  x.buildPayload = () => ({ details: [{ quantity: 1 }] }); x.getList = () => Promise.resolve()
  const a=x.doSave(); await x.doSubmit(); assert.equal(calls.length,0); validation.resolve(true);await flush();assert.equal(calls.length,1)
  transport.resolve({ data:{ returnId:'42',version:'0' } });await a;assert.equal(x.form.returnId,'42');assert.equal(x.submitLoading,false)
})
test('return detail A-B-A, close and old errors never overwrite current detail', async () => {
  const calls=[]; const x=instance(methods('inventory/salesReturn',{ ...ctx, getSalesReturn: id => { const q=deferred();calls.push({id,...q});return q.promise } }),{ detailSequence:0,pageInactive:false })
  const a=x.openDetail({returnId:'A'}),b=x.openDetail({returnId:'B'}),c=x.openDetail({returnId:'A'})
  calls[2].resolve({data:{returnId:'A',returnNo:'latest'}});await c;calls[0].resolve({data:{returnId:'A',returnNo:'old'}});await a;calls[1].reject(Error('old error'));await b
  assert.equal(x.detailForm.returnNo,'latest');assert.equal(x.detailError,'');assert.equal(x.detailLoading,false)
  const d=x.openDetail({returnId:'C'});x.detailOpen=false;calls[3].resolve({data:{returnId:'C'}});await d;assert.equal(x.detailForm.returnId,undefined)
})
for (const page of ['inventory/salesReturn','oa/reimbursement']) test(page + ' drops stale success, failure and finally',async()=>{
  const calls=[],loader=q=>{const d=deferred();calls.push(d);return d.promise}
  const x=instance(methods(page,{...ctx,listSalesReturn:loader,listMyReimbursements:loader}),{pageInactive:false,listSequence:0,isStoreContext:true,queryParams:{status:'draft'},query:{status:'draft'},financeMode:false,loadTodoBusinessList:f=>f(),handleTodoFocusRows(){}})
  x.sameDeptId=()=>true;const load=page.includes('reimbursement')?x.loadList:x.getList
  const a=load();x.query.status=x.queryParams.status='approved';const b=load();calls[0].reject(Error('stale'));await a;assert.equal(x.loading,true)
  calls[1].resolve({rows:[{status:'approved'}],total:1});await b;assert.equal((x.rows||x.list)[0].status,'approved');assert.equal(x.loading,false)
})
test('purchase opens only the newest editor and blocks opening during save',async()=>{
  const calls=[];const x=instance(methods('inventory/purchase',{...ctx,getPurchaseDraft:id=>{const q=deferred();calls.push({id,...q});return q.promise}}),{$refs:{},editorEpoch:0,formSubmitting:false})
  x.ensureWarehouseContext=()=>true;x.closeProductSelector=()=>{};x.loadSuppliers=()=>{};x.defaultOrderDate=()=> '2026-09-14'
  const a=x.openForm({orderId:'A'}),b=x.openForm({orderId:'B'});calls[1].resolve({data:{orderId:'B',details:[]}});await b;calls[0].resolve({data:{orderId:'A',details:[]}});await a;assert.equal(x.form.orderId,'B')
  x.formSubmitting=true;await x.openForm({orderId:'C'});assert.equal(calls.length,2)
})
test('late purchase save does not close a reopened editor or release its busy state',async()=>{
  const q=deferred();const x=instance(methods('inventory/purchase',{...ctx,savePurchase:()=>q.promise}),{editorEpoch:1,form:{supplierId:1,supplierName:'A',details:[{itemId:1,itemType:'product',quantity:'0.5',unitPrice:1}]},open:true})
  x.onSupplierChange=()=>{};x.buildPurchasePayload=()=>({});x.getList=()=>{};const a=x.submitValidatedForm(false)
  x.invalidateEditor();x.open=true;x.form={orderId:'B'};x.formSubmitting=true;q.resolve({data:{orderId:'A',version:'1'}});await a;assert.equal(x.open,true);assert.equal(x.form.orderId,'B');assert.equal(x.formSubmitting,true)
})
test('asset search reaches page 2 and retains selected rows over queries',async()=>{
  const calls=[];const x=instance(methods('oa/fixedAsset/repair',{listFixedAssetConfigs:query=>{calls.push(query);return Promise.resolve({rows:query.pageNum===1?Array.from({length:100},(_,i)=>({oeItemId:String(i+1)})):[{oeItemId:'101'}],total:101})}}),{form:{shopDeptId:'20'},repairOpen:true,draftGeneration:1,assetOptions:[],repairAssetRows:[{oeItemId:'101'}]})
  const scope=createUiOperationScope(()=> '20');x.operationScope=()=>scope
  await x.loadAssets('20');await x.loadAssets('20','',2);assert.equal(x.assetOptions.length,101);await x.searchAssets('末页');assert.ok(x.assetOptions.some(row=>row.oeItemId==='101'));assert.equal(calls.at(-1).oeItemName,'末页')
})
test('quantity validation preserves legal fractions and rejects invalid text without clamping',()=>{
  for(const value of ['0.01','0.5','1','1.23'])assert.equal(validInventoryQuantity(value),true,value)
  for(const value of ['0','-1','0.0001','1.2345','1.23456','NaN','','1e2','100000000000000'])assert.equal(validInventoryQuantity(value),false,value)
})
test('sales close retains incomplete input, restores by scope, and manual title is never replaced',()=>{
  const x=instance(methods('inventory/sales'),{formSubmitting:false,formDirty:true,open:true,choiceScope:'7:20',form:{orderTitle:'手工标题',customerName:'客户A',orderDate:'2026-09-14',details:[]},formBaseline:'{}',customerOptions:[],titleEdited:true})
  x.closeForm();assert.equal(x.closeChoiceOpen,true);assert.equal(x.open,true);x.updateAutomaticTitle();assert.equal(x.form.orderTitle,'手工标题')
  x.finishCloseChoice('keep');assert.equal(x.open,false);assert.equal(x.retainedDraft.form.orderTitle,'手工标题');assert.equal(x.retainedDraft.scope,'7:20')
  x.form.orderTitle='new';assert.equal(x.retainedDraft.form.orderTitle,'手工标题');x.finishCloseChoice('discard');assert.equal(x.retainedDraft,null)
})
function memoryStore() {
  const records = new Map(), copy = value => value ? JSON.parse(JSON.stringify(value)) : null
  return { records, async read(key){return copy(records.get(key))},async all(){return [...records.values()].map(copy)},
    async reserve(key,candidate,head){const old=records.get(key);if((old?old.requestId:null)===head&&(!old||old.phase==='ACKED'))records.set(key,{key,...copy(candidate)});return copy(records.get(key))},
    async settle(key,id,result){const old=records.get(key);if(old.requestId===id&&old.phase==='ACTIVE')records.set(key,{...old,phase:'SETTLED',result:copy(result)})},
    async acknowledge(key,id,guard){if(guard)guard();const old=records.get(key);if(old.requestId===id&&old.phase==='SETTLED')records.set(key,{...old,phase:'ACKED'})}
  }
}
function recoveryFixture() {
  const storage=memoryStore(),state={actor:'7',dept:'20'},receipts=new Map(),calls=[];let id=0,lose=false
  const deps={storage,context:()=>state,createId:()=> 'draft-test-'+ ++id,transport:async record=>{calls.push(record);if(!receipts.has(record.requestId))receipts.set(record.requestId,{data:{orderId:'100',version:'0',status:'draft'}});if(lose){lose=false;throw Error('response lost')}return receipts.get(record.requestId)}}
  return {storage,state,receipts,calls,deps,service:()=>createInventoryDraftRecovery(deps),lose(){lose=true}}
}
test('unknown draft outcome survives reopen and reuses the same command across tabs',async()=>{
  const h=recoveryFixture();h.lose();const payload={orderTitle:'草稿',details:[{quantity:.5}]}
  await assert.rejects(h.service().submit('purchase','save',payload),/lost/)
  const reopened=h.service();const pending=await reopened.pending('purchase');assert.equal(pending.length,1)
  await assert.rejects(reopened.submit('purchase','submit',{...payload,orderTitle:'改动'}),/尚未确认/)
  const [a,b]=await Promise.all([reopened.recover(pending[0]),h.service().submit('purchase','save',payload)])
  assert.equal(a.data.orderId,b.data.orderId);assert.equal(h.receipts.size,1);assert.equal(new Set(h.calls.map(row=>row.requestId)).size,1);assert.equal((await reopened.pending('purchase')).length,0)
})
test('context switch and storage failure cannot issue a new write',async()=>{
  const h=recoveryFixture();h.lose();await assert.rejects(h.service().submit('purchase','save',{}));const saved=(await h.service().pending('purchase'))[0]
  h.state.actor='8';await assert.rejects(h.service().recover(saved),/变化/);assert.equal(h.calls.length,1)
  h.deps.storage={read:async()=>{throw Error('disk failure')}};await assert.rejects(h.service().submit('purchase','save',{}),/disk/);assert.equal(h.calls.length,1)
})
test('definite transaction rejection releases the operation so corrected input can be saved',async()=>{
  const h=recoveryFixture();h.deps.transport=async()=>{throw Object.assign(Error('invalid'),{data:{draftOutcome:'REJECTED'}})}
  await assert.rejects(h.service().submit('purchase','save',{orderTitle:'invalid'}));assert.equal((await h.service().pending('purchase')).length,0)
})

const quickCustomer = require('../src/views/mobile/feature/mobileQuickCustomer')
const recentChoices = require('../src/utils/salesRecentChoices')
test('automatic title follows customer/date until the operator edits it',()=>{
  const x=instance(methods('inventory/sales'),{form:{customerName:'客户A',orderDate:'2026-09-14'},titleEdited:false})
  x.updateAutomaticTitle();assert.equal(x.form.orderTitle,'客户A 2026-09-14')
  x.form.orderDate='2026-09-15';x.updateAutomaticTitle();assert.equal(x.form.orderTitle,'客户A 2026-09-15')
  x.titleEdited=true;x.form.orderTitle='指定主题';x.form.customerName='客户B';x.updateAutomaticTitle();assert.equal(x.form.orderTitle,'指定主题')
})
test('quick customer respects feature permission and preserves sale fields after creating and selecting',async()=>{
  const calls=[],q=deferred(),scope=createUiOperationScope(()=> '7:20')
  const x=instance(methods('inventory/sales',{...quickCustomer,createCustomerServiceCard:(...args)=>{calls.push(args);return q.promise}}),{canQuickCreateCustomer:false,quickCustomerSaving:false,quickCustomerDraft:{customerName:'新客户'},editorRevision:1,selectedDeptContext:{deptId:20},quickCustomerRequestKey:'desktop-fixed-request',quickCustomerOpen:true,customerOptions:[],form:{orderTitle:'保留的标题',orderDate:'2026-09-14',details:[{quantity:2}]},titleEdited:true})
  x.operationScope=()=>scope;await x.createQuickCustomer();assert.equal(calls.length,0)
  x.canQuickCreateCustomer=true;const save=x.createQuickCustomer();assert.equal(calls.length,1);assert.equal(calls[0][0].requestKey,'desktop-fixed-request');calls[0][1].assertContext()
  q.resolve({data:{customerId:'9',customerName:'新客户'}});await save;assert.equal(x.form.customerId,'9');assert.equal(x.form.orderTitle,'保留的标题');assert.equal(x.form.details[0].quantity,2)
  scope.invalidate();assert.throws(calls[0][1].assertContext,/变化/)
})
test('bulk sales items append once with current prices and leave existing warehouses and quantities intact',()=>{
  const x=instance(methods('inventory/sales'),{open:true,batchPickerOpen:true,formSubmitting:false,defaultWarehouseId:'20',form:{details:[{itemType:'product',itemId:'1',quantity:5,unitPrice:7,warehouseId:'30'}]}})
  x.addBatchMaterials([{itemType:'product',itemId:'1',salesPrice:99},{itemType:'gift',itemId:'1',itemName:'礼盒',salesPrice:12}])
  assert.equal(x.form.details.length,2);assert.equal(x.form.details[0].quantity,5);assert.equal(x.form.details[0].unitPrice,7);assert.equal(x.form.details[0].warehouseId,'30');assert.equal(x.form.details[1].unitPrice,12);assert.equal(x.form.details[1].warehouseId,'20')
})
test('recent choices isolate login/organization and retain no old prices or contact details',()=>{
  recentChoices.rememberSalesChoice('7:session1:20','customer',{id:'9',label:'客户',contactPhone:'private',price:100})
  assert.deepEqual(recentChoices.recentSalesChoices('7:session1:21','customer'),[])
  assert.deepEqual(recentChoices.recentSalesChoices('7:session2:20','customer'),[])
  assert.deepEqual(recentChoices.recentSalesChoices('7:session1:20','customer'),[{id:'9',type:'',label:'客户'}])
})
test('draft response after logout/login ABA remains recoverable and cannot acknowledge a later session',async()=>{
  const h=recoveryFixture(),pending=deferred();let revision=1;h.deps.contextRevision=()=>revision;h.deps.transport=()=>pending.promise
  const service=h.service(),save=service.submit('purchase','save',{});await flush();revision=3;pending.resolve({data:{orderId:'100',version:'0'}})
  await assert.rejects(save,/变化/);const records=await service.pending('purchase');assert.equal(records[0].phase,'SETTLED')
  const result=await service.recover(records[0]);assert.equal(result.data.orderId,'100')
})
test('queued receipt acknowledgment rechecks login before releasing the saved operation',async()=>{
  const h=recoveryFixture();let revision=1;h.deps.contextRevision=()=>revision
  const acknowledge=h.storage.acknowledge
  h.storage.acknowledge=async(...args)=>{revision=2;return acknowledge(...args)}
  const service=h.service();await assert.rejects(service.submit('purchase','save',{}),/变化/)
  const pending=await service.pending('purchase');assert.equal(pending[0].phase,'SETTLED')
  h.storage.acknowledge=acknowledge;assert.equal((await service.recover(pending[0])).data.orderId,'100')
})
