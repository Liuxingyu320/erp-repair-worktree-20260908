const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const parser = require('@babel/parser'), generate = require('@babel/generator').default, compiler = require('vue-template-compiler')
const { createUiOperationScope } = require('../src/utils/uiOperationScope')
const { createInventoryDraftRecovery } = require('../src/utils/inventoryDraftRecovery')
const upload = require('../src/views/drive/uploadReceipt')
const deferred = () => { let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject} }
const flush = async () => { for(let i=0;i<20;i++) await Promise.resolve() }
function page(file, globals={}, props={}) {
  const s=compiler.parseComponent(fs.readFileSync(path.join(__dirname,'../src',file),'utf8')).script.content
  const ast=parser.parse(s,{sourceType:'module'}).program.body.find(x=>x.type==='ExportDefaultDeclaration').declaration
  const selected={...ast,properties:ast.properties.filter(x=>['data','methods','computed','watch'].includes(x.key.name)).map(x=>x.key.name==='methods'?{...x,value:{...x.value,properties:x.value.properties.filter(y=>y.type==='ObjectMethod')}}:x)}
  const options=vm.runInNewContext('('+generate(selected).code+')',{Promise,AbortController,createUiOperationScope,getSelectedDeptId:()=> '20',...upload,...globals})
  const x={$store:{getters:{id:'7'},state:{user:{id:'7',sessionRevision:1}}},$route:{path:'/test',fullPath:'/test'},$modal:{msgSuccess(){},msgWarning(){},confirm:()=>Promise.resolve()},$nextTick:cb=>cb(),$refs:{},$emit(){},...options.data(),...props}
  for(const [k,v]of Object.entries(options.methods))x[k]=v.bind(x)
  for(const [k,v]of Object.entries(options.computed||{}))if(!(k in props))Object.defineProperty(x,k,{get:()=>v.call(x)})
  x._testWatch=options.watch||{}
  return x
}
const asset='views/oa/fixedAsset/config/index.vue',health='views/hr/healthCertificate/index.vue',purchase='views/oa/purchase/index.vue',picker='views/drive/components/DriveAttachmentPicker.vue'
function store() {
  const rows=new Map();return {rows,async read(k){return rows.get(k)},async all(){return [...rows.values()]},async reserve(k,c,h){const old=rows.get(k);if((old?old.requestId:null)===h&&(!old||old.phase==='ACKED'))rows.set(k,{key:k,...c});return rows.get(k)},async settle(k,id,result){const r=rows.get(k);if(r.requestId===id&&r.phase==='ACTIVE')rows.set(k,{...r,phase:'SETTLED',result})},async acknowledge(k,id,guard,opts){if(guard)guard();const r=rows.get(k);if(r.requestId===id&&r.phase==='SETTLED')rows.set(k,{...r,phase:'ACKED',...(opts&&opts.redact?{payload:null,result:null,fingerprint:null}:{})})}}
}
test('all three desktop draft pages render and register the recovery component',()=>{
  for(const name of ['purchase','purchaseReturn','salesReturn']){
    const source=fs.readFileSync(path.join(__dirname,'../src/views/inventory',name,'index.vue'),'utf8'), parsed=compiler.parseComponent(source)
    assert.match(parsed.template.content,new RegExp('<inventory-draft-recovery feature="'+name+'" @recovered="onDraftRecovered"'))
    assert.match(parsed.script.content,/components:\s*\{\s*InventoryDraftRecovery/)
  }
})
test('real draft title rules reject whitespace before reserving a durable command',async()=>{
  const Schema=require('async-validator').default||require('async-validator')
  for(const [name,field]of [['purchase','orderTitle'],['purchaseReturn','returnTitle'],['salesReturn','returnTitle']]){
    const x=page('views/inventory/'+name+'/index.vue');const schema=new Schema({[field]:x.rules[field]})
    const errors=await new Promise(resolve=>schema.validate({[field]:'   '},resolve));assert.ok(errors&&errors.length,name)
  }
})
test('pre-execution rejection releases a draft, while an unknown failure retains it',async()=>{
  for(const marker of [true,false]){
    const s=store();let id=0,calls=0
    const api=createInventoryDraftRecovery({storage:s,context:()=>({actor:'7',dept:'20'}),createId:()=> 'r'+ ++id,transport:async()=>{calls++;if(calls===1)throw Object.assign(Error('invalid'),{data:{code:500,...(marker?{draftOutcome:'REJECTED'}:{})}});return{data:{orderId:'42',version:'0'}}}})
    await assert.rejects(api.submit('purchase','save',{orderTitle:'bad'}));assert.equal((await api.pending('purchase')).length,marker?0:1)
    if(marker)assert.equal((await api.submit('purchase','save',{orderTitle:'corrected'})).data.orderId,'42')
    else {await assert.rejects(api.submit('purchase','save',{orderTitle:'corrected'}),/尚未确认/);assert.equal(calls,1)}
  }
})
test('atomic acknowledgement failure leaves one recoverable receipt without a second business write',async()=>{
  const s=store(),ack=s.acknowledge;let fail=true,calls=0,id=0
  s.redact=()=>{throw Error('separate cleanup must not run')}
  s.acknowledge=async(...args)=>{assert.equal(args[3].redact,true);if(fail){fail=false;throw Error('transaction aborted')}return ack(...args)}
  const api=createInventoryDraftRecovery({storage:s,context:()=>({actor:'7',dept:'20'}),createId:()=> 'r'+ ++id,transport:async()=>{calls++;return{data:{orderId:'42',version:'0'}}}})
  await assert.rejects(api.submit('purchase','save',{}),/aborted/);const pending=await api.pending('purchase');assert.equal(pending[0].phase,'SETTLED')
  assert.equal((await api.recover(pending[0])).data.orderId,'42');assert.equal(calls,1);assert.equal((await api.pending('purchase')).length,0)
  const row=[...s.rows.values()][0];assert.equal(row.payload,null);assert.equal(row.result,null)
})
test('asset list drops old response and finally after filter and session changes',async()=>{
  const calls=[];const x=page(asset,{listFixedAssetStores:q=>{const d=deferred();calls.push(d);return d.promise},getFixedAssetQuota:async()=>({data:{availableQuotaAmount:1}})})
  x.queryParams.shopDeptId='A';const a=x.getList();x.queryParams.shopDeptId='B';const b=x.getList()
  calls[0].reject(Error('stale error'));await a;assert.equal(x.loading,true);assert.equal(x.listError,'')
  calls[1].resolve({rows:[{shopDeptId:'B'}],total:1});await b;assert.equal(x.storeConfigRows[0].shopDeptId,'B')
  const c=x.getList();x.$store.state.user.sessionRevision++;calls[2].resolve({rows:[{shopDeptId:'old'}],total:99});await c;assert.equal(x.storeConfigRows.length,0)
})
test('asset store paging preserves server total and requests quotas only for the current page',async()=>{
  const queries=[],quotaCalls=[];const x=page(asset,{listFixedAssetStores:async q=>{queries.push(q);return{rows:[{shopDeptId:'later-store',detailCount:5001,assetTotalAmount:5001}],total:81}},getFixedAssetQuota:async q=>{quotaCalls.push(q);return{data:{availableQuotaAmount:0,annualRepairRatio:0}}}})
  x.queryParams.pageNum=9;await x.getList();await flush();assert.equal(queries[0].pageNum,9);assert.equal(queries[0].pageSize,10);assert.equal(x.total,81);assert.equal(x.storeConfigRows[0].detailCount,5001);assert.equal(quotaCalls.length,1)
})
test('quota failure remains unknown and row retry restores a real zero',async()=>{
  let fail=true;const x=page(asset,{getFixedAssetQuota:async()=>{if(fail)throw Error('network');return{data:{availableQuotaAmount:0,annualRepairRatio:0}}}}),row={shopDeptId:'20'}
  x.storeConfigRows=[row];await x.loadRowQuota(row);assert.equal(row.availableQuotaAmount,null);assert.equal(row.quotaError,'暂时无法读取')
  fail=false;await x.loadRowQuota(row);assert.equal(row.availableQuotaAmount,0);assert.equal(row.quotaError,'')
})
test('old quota and asset detail cannot overwrite a reopened store',async()=>{
  const q=deferred(),a=deferred(),b=deferred();let n=0;const x=page(asset,{getFixedAssetQuota:()=>q.promise,getFixedAssetStoreDetails:()=>++n===1?a.promise:b.promise}),row={shopDeptId:'A'}
  x.storeConfigRows=[row];const quota=x.loadRowQuota(row);x.invalidateOverview();q.resolve({data:{availableQuotaAmount:123}});await quota;assert.equal(row.availableQuotaAmount,null)
  const first=x.openDetailDrawer({shopDeptId:'A'}),second=x.openDetailDrawer({shopDeptId:'B'});b.resolve({data:[{configId:'B'}]});await second;a.resolve({data:[{configId:'A'}]});await first;assert.equal(x.activeStoreConfig.details[0].configId,'B')
})
test('purchase capability guards the entry and offers save only when drafts are enabled',async()=>{
  for(const canSave of [false,true])for(const canSubmit of [false,true]){
    const x=page(purchase,{getPurchaseAvailability:async()=>({data:{canSave,canSubmit,reason:'暂未开放'}})})
    await x.loadPurchaseAvailability();assert.equal(x.purchaseDraftAvailable,canSave);assert.equal(x.purchaseSubmissionAvailable,canSave&&canSubmit)
    if(!canSave){await x.openForm();assert.equal(x.open,false)}
  }
})
test('purchase close offers explicit choices and does not discard on cancel',()=>{
  const x=page(purchase,{}, {open:true,formSnapshot:'old',form:{title:'new'},purchaseDraftAvailable:true,formReady:true})
  x.requestCloseForm();assert.equal(x.closeChoiceOpen,true);assert.equal(x.open,true)
  x.closeChoiceOpen=false;assert.equal(x.form.title,'new');x.requestCloseForm();let saved=false;x.save=submit=>{assert.equal(submit,false);saved=true};x.saveAndClose();assert.equal(saved,true);assert.equal(x.open,true)
})
test('health personal list discards old refresh and old error',async()=>{
  const calls=[];const x=page(health,{getMyHealthCertificates:()=>{const q=deferred();calls.push(q);return q.promise}})
  const a=x.loadMine(),b=x.loadMine();calls[1].resolve({data:[{certificateId:'new'}]});await b;calls[0].resolve({data:[{certificateId:'old'}]});await a;assert.equal(x.mineRows[0].certificateId,'new')
  const c=x.loadMine();x.resetHealthContext();calls[2].reject(Error('old'));await c;assert.equal(x.mineError,'');assert.equal(x.mineRows.length,0)
})
test('health delayed validation cannot save after context invalidation and old save cannot close a new editor',async()=>{
  const validation=deferred(),saved=deferred();let writes=0
  const x=page(health,{saveMyHealthCertificateDraft:()=>{writes++;return saved.promise}},{intakeEnabled:true,$refs:{mineForm:{validate:cb=>validation.promise.then(cb),clearValidate(){}}}})
  x.openMineForm({certificateId:'A'});const a=x.saveMine();assert.equal(x.saving,true);assert.equal(await x.requestMineClose(),false);x.openMineForm({certificateId:'B'});assert.equal(x.mineForm.certificateId,'A')
  x.invalidateMineEditor();x.openMineForm({certificateId:'B'});validation.resolve(true);await a;assert.equal(writes,0);assert.equal(x.mineDialog,true)
  x.$refs.mineForm.validate=cb=>cb(true);const b=x.saveMine();await flush();assert.equal(writes,1);x.resetHealthContext();x.openMineForm({certificateId:'C'});x.saving=true;saved.resolve({});await b;assert.equal(x.mineDialog,true);assert.equal(x.mineForm.certificateId,'C');assert.equal(x.saving,true)
})
test('health save freezes the payload and successful save refreshes the list',async()=>{
  const saved=deferred();let sent,refreshes=0
  const x=page(health,{saveMyHealthCertificateDraft:body=>{sent=body;return saved.promise}},{intakeEnabled:true,$refs:{mineForm:{validate:cb=>cb(true),clearValidate(){}}}})
  x.refreshTodo=()=>{};x.loadMine=async()=>{refreshes++};x.openMineForm({certificateId:'A',issuerName:'original'});const p=x.saveMine();x.mineForm.issuerName='changed';await flush();assert.equal(sent.issuerName,'original');saved.resolve({});await p;assert.equal(x.mineDialog,false);assert.equal(refreshes,1)
})
test('attachment search uses server paging beyond recent files and isolates old folder responses',async()=>{
  const calls=[];const x=page(picker,{listDriveNodes:q=>{const d=deferred();calls.push({q,...d});return d.promise}},{spaceId:'5',pickerOpen:true})
  x.page=6;const a=x.loadFiles();assert.equal(calls[0].q.pageNum,6);x.keyword='older document';const b=x.search();calls[1].resolve({rows:[{nodeId:'60',nodeType:'FILE',contentType:'application/pdf',nodeName:'older document'}],total:61});await b;calls[0].resolve({rows:[{nodeId:'old'}],total:1});await a;assert.equal(x.rows[0].nodeId,'60');assert.equal(x.total,61)
  const emitted=[];x.$emit=(...v)=>emitted.push(v);x.selectNode(x.rows[0]);assert.deepEqual(emitted[0],['input','60'])
})
test('attachment selection excludes SVG and late upload receipt cannot bind across context',async()=>{
  const q=deferred(),events=[];const x=page(picker,{getDriveNode:()=>q.promise},{$emit:(...args)=>events.push(args)})
  assert.equal(x.supported({nodeType:'FILE',contentType:'image/svg+xml'}),false);assert.equal(x.supported({nodeType:'FILE',contentType:'application/pdf'}),true)
  const p=x.selectReceipt({status:'done',nodeId:'40'});x.$store.state.user.sessionRevision++;q.resolve({data:{nodeId:'40',nodeType:'FILE',contentType:'application/pdf'}});await p;assert.equal(events.length,0)
})
test('attachment upload persists before sending and unknown result blocks a fresh upload',async()=>{
  let calls=0,persisted=false;const events=[];const x=page(picker,{newUploadOperationId:()=> 'upload_01234567890123456789012345678901',persistUploadReceipts:()=>{persisted=true;return true},uploadDriveFile:async()=>{assert.equal(persisted,true);calls++;throw Error('lost')}},{spaceId:'5',$emit:(...v)=>events.push(v)})
  const event=()=>({target:{files:[{name:'proof.pdf',type:'application/pdf',size:10}],value:'file'}})
  await x.chooseUpload(event());assert.equal(x.receipts[0].status,'pending');await x.chooseUpload(event());assert.equal(calls,1);assert.equal(x.uploading,false)
})
test('attachment completed upload binds the controlled node in the same editor',async()=>{
  const events=[];const id='upload_01234567890123456789012345678901';const x=page(picker,{newUploadOperationId:()=>id,persistUploadReceipts:()=>true,uploadDriveFile:async()=>({data:{operationId:id,status:'SUCCEEDED',nodeId:'50'}}),getDriveNode:async()=>({data:{nodeId:'50',nodeType:'FILE',contentType:'application/pdf',nodeName:'proof.pdf'}})},{spaceId:'5',$emit:(...v)=>events.push(v)})
  await x.chooseUpload({target:{files:[{name:'proof.pdf',type:'application/pdf',size:10}],value:'file'}});assert(events.some(x=>x[0]==='input'&&x[1]==='50'));assert.equal(x.uploading,false)
})

test('pending purchase capability resumes only the latest open intent in the same session',async()=>{
  const availability=deferred(),details=[]
  const x=page(purchase,{getPurchaseAvailability:()=>availability.promise,getPurchaseDetail:async id=>{details.push(id);return{data:{purchaseId:id}}}})
  x.loadPurchaseAvailability();const a=x.openForm({purchaseId:'11'}),b=x.openForm({purchaseId:'12'})
  availability.resolve({data:{canSave:true,canSubmit:false}});await Promise.all([a,b]);assert.deepEqual(details,['12']);assert.equal(x.form.purchaseId,'12')
  const late=deferred();const y=page(purchase,{getPurchaseAvailability:()=>late.promise,getPurchaseDetail:async()=>{throw Error('must not open')}})
  const old=y.openForm({purchaseId:'13'});y.handlePurchaseContextChanged(false);late.resolve({data:{canSave:true}});await old;assert.equal(y.open,false)
})
test('asset queued filter watcher preserves a newly started query and invalidates unsent changes',async()=>{
  const pending=deferred();const x=page(asset,{listFixedAssetStores:()=>pending.promise,getFixedAssetQuota:async()=>({data:{}})})
  x.queryParams.shopDeptId='20';const request=x.getList();x._testWatch.queryParams.handler.call(x)
  assert.equal(x.loading,true);assert.equal(x.overviewQueryChanged,false)
  pending.resolve({rows:[{shopDeptId:'20'}],total:1});await request;assert.equal(x.storeConfigRows.length,1)
  x.queryParams.oeItemName='new filter';x._testWatch.queryParams.handler.call(x);assert.equal(x.storeConfigRows.length,0);assert.equal(x.overviewQueryChanged,true)
})

test('unavailable purchase empty state avoids suggesting a disabled create action',async()=>{
 const x=page(purchase,{getPurchaseAvailability:async()=>({data:{canSave:false,canSubmit:false,reason:'采购申请暂未开放'}})})
 assert.match(x.oaPurchaseEmptyText,/正在检查/);await x.loadPurchaseAvailability();assert.match(x.oaPurchaseEmptyText,/暂未开放/);assert.doesNotMatch(x.oaPurchaseEmptyText,/新建/)
})
test('open asset details observe quota completion and retry instead of keeping a copied unknown',async()=>{
 const q=deferred();const x=page(asset,{getFixedAssetQuota:()=>q.promise,getFixedAssetStoreDetails:async()=>({data:[]})}),row={shopDeptId:'20'}
 x.storeConfigRows=[row];const quota=x.loadRowQuota(row);await x.openDetailDrawer(row);assert.equal(x.activeStoreQuota.quotaLoading,true)
 q.resolve({data:{availableQuotaAmount:0}});await quota;assert.equal(x.activeStoreQuota.availableQuotaAmount,0);assert.equal(x.activeStoreQuota.quotaLoading,false)
})

test('late receipt lookup cannot replace a manual selection or mutate a saving form',async()=>{
 for(const action of ['choose','clear','saving']){
  const pending=deferred(),events=[];const x=page(picker,{getDriveNode:()=>pending.promise},{$emit:(...args)=>events.push(args)})
  const lookup=x.selectReceipt({status:'done',nodeId:'40'})
  if(action==='choose')x.selectNode({nodeId:'41',nodeType:'FILE',nodeName:'new.pdf',contentType:'application/pdf'})
  else if(action==='clear')x.clear()
  else {x.disabled=true;x._testWatch.disabled.call(x,true);x.disabled=false}
  pending.resolve({data:{nodeId:'40',nodeType:'FILE',contentType:'application/pdf'}});await lookup
  assert.equal(events.filter(e=>e[0]==='input'&&e[1]==='40').length,0,action)
 }
})

test('an empty filtered asset query does not claim the entire asset baseline is missing',()=>{
 const x=page(asset);x.queryParams.oeItemName='no match';assert.equal(x.hasAssetFilters,true);assert.match(x.fixedAssetConfigEmptyText,/调整条件/)
 x.queryParams.oeItemName='';x.queryParams.status='0';assert.equal(x.hasAssetFilters,true)
})

test('health route change renews a pending capability read and ignores the old response',async()=>{
 const calls=[];const x=page(health,{getHealthCertificateCapability:()=>{const d=deferred();calls.push(d);return d.promise}})
 x.applyTodoRoute=()=>{};x.refreshAll=()=>{};const a=x.loadCapability();x._testWatch['$route.fullPath'].call(x)
 calls[0].resolve({data:{intakeEnabled:true}});await a;assert.equal(x.capabilityLoaded,false)
 calls[1].resolve({data:{intakeEnabled:false,reason:'新范围尚未开放'}});await flush();assert.equal(x.capabilityLoaded,true);assert.equal(x.capability.intakeEnabled,false)
})
