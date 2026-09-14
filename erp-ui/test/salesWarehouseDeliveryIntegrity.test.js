const assert=require('node:assert/strict'),test=require('node:test'),fs=require('fs'),path=require('path'),vm=require('vm'),babel=require('@babel/core'),Vue=require('vue'),compiler=require('vue-template-compiler'),root=path.resolve(__dirname,'..')
const utilities=require('../src/utils/salesWarehouse'),tick=async()=>{await new Promise(r=>setImmediate(r));await Vue.nextTick()},deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}}
function harness(page){
 const env={dept:5},store=Vue.observable({getters:{id:7,token:'session-a'}}),calls=[],messages=[],confirms=[]
 const api=new Proxy({},{get(_,name){if(name==='__esModule')return false;return(...args)=>{const d=deferred();calls.push({name,args,...d});return d.promise}}})
 const parsed=compiler.parseComponent(fs.readFileSync(path.join(root,'src/views/inventory',page),'utf8'));assert.deepEqual(compiler.compile(parsed.template.content).errors,[])
 const code=babel.transformSync(parsed.script.content,{filename:page,babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code,module={exports:{}}
 vm.runInNewContext(code,{module,exports:module.exports,console,Promise,Set,Map,Array,Object,String,Number,JSON,document:{},window:{addEventListener(){},removeEventListener(){}},require(id){
 if(id.startsWith('@/api/'))return api
 if(id==='@/utils/shopContext')return{getSelectedDeptContext:()=>({deptId:env.dept,isStore:true,deptName:'门店'}),isSelectedStore:()=>true,isSelectedWarehouse:()=>false,getSelectedDeptId:()=>env.dept,getSelectedDeptName:()=> 'Store'}
 if(id==='@/utils/uiOperationScope')return require('../src/utils/uiOperationScope')
 if(id==='@/utils/salesWarehouse')return utilities
 if(id==='@/utils/supplierOptionState')return require('../src/utils/supplierOptionState')
 if(id==='@/utils/imageGallery')return require('../src/utils/imageGallery')
 if(id==='@/utils/common')return{parseTime:()=> '2026-09-13'}
 if(id==='@/mixins/todoBusinessFocus')return{createTodoBusinessFocusMixin:()=>({})}
 return{}}})
 const c=new Vue({...module.exports.default,created:[],beforeDestroy:[],mixins:[],beforeCreate(){this.$route={query:{},path:"/inventory"};this.$store=store;this.$modal={msgSuccess:m=>messages.push(m),msgError:m=>messages.push(m),msgWarning:m=>messages.push(m),confirm:m=>{const d=deferred();confirms.push({...d,message:m});return d.promise}}}})
 const actualGetList=c.getList && c.getList.bind(c);c.getList=()=>Promise.resolve();c.addDateRange=q=>q;return{c,env,store,calls,confirms,messages,actualGetList}
}
const row=(id,warehouse,quantity=4)=>({detailId:id,warehouseId:warehouse,productName:'P'+id,noticeQty:quantity,deliveredQty:0})
async function delivery(){const h=harness('deliveryNotice/index.vue'),p=h.c.openDeliver({noticeId:11});h.calls.at(-1).resolve({data:{noticeId:11,noticeNo:'N11',shopDeptId:5,status:'pending',warehouseId:null,details:[row(1,20),row(2,30)]}});await p;await tick();h.c.onDeliverWarehousesLoaded([{deptId:20,deptName:'W20'},{deptId:30,deptName:'W30'}]);return h}
test('authorized current then sole default, never arbitrary first; preserve filled rows',()=>{const options=[{deptId:20},{deptId:30}];assert.equal(utilities.defaultSalesWarehouse(options,30),30);assert.equal(utilities.defaultSalesWarehouse(options,5),undefined);assert.equal(utilities.defaultSalesWarehouse([options[0]],5),20);assert.deepEqual(utilities.fillEmptySalesWarehouses([{warehouseId:30},{warehouseId:null}],20),[{warehouseId:30},{warehouseId:20}])})
test('actual sales editor fills only empty warehouse rows and defaults new row',async()=>{const h=harness('sales/index.vue');h.c.open=true;await tick();h.c.form.details=[{warehouseId:30},{warehouseId:null}];h.c.onWarehousesLoaded([{deptId:20,deptName:'W20'}]);assert.equal(h.c.defaultWarehouseId,20);assert.deepEqual(h.c.form.details.map(r=>r.warehouseId),[30,20]);h.c.addDetail();assert.equal(h.c.form.details[2].warehouseId,20)})
test('late sales detail cannot replace a newer editor',async()=>{const h=harness('sales/index.vue'),a=h.c.openForm({orderId:1}),first=h.calls.at(-1),b=h.c.openForm({orderId:2}),second=h.calls.at(-1);second.resolve({data:{orderId:2,details:[]}});await b;first.resolve({data:{orderId:1,details:[]}});await a;assert.equal(h.c.form.orderId,2)})
test('sales validation after organization change cannot send save',async()=>{const h=harness('sales/index.vue');h.c.open=true;await tick();const validation=deferred();h.c.validateEditorForm=()=>validation.promise;const p=h.c.doSave(true);h.env.dept=6;h.c.handleContextChanged();validation.resolve(true);await p;assert.equal(h.calls.filter(c=>c.name==='submitSales').length,0)})
test('one multiwarehouse notice submits current group; partial refresh preserves other group inputs',async()=>{const h=await delivery();assert.equal(h.c.deliverForm.warehouseId,undefined);h.c.deliverForm.warehouseId=20;h.c.deliverForm.items[0].deliverQuantity=2;h.c.deliverForm.items[1].deliverQuantity=3;const p=h.c.submitDeliver();assert.equal(h.c.submitLoading,true);h.confirms[0].resolve();await tick();const sent=h.calls.at(-1);assert.equal(sent.name,'deliverDeliveryNotice');assert.equal(sent.args[0],11);assert.deepEqual(JSON.parse(JSON.stringify(sent.args[1])),{warehouseId:20,items:[{detailId:1,deliverQuantity:2}]});sent.resolve({});await p;await tick();h.calls.at(-1).resolve({data:{noticeId:11,shopDeptId:5,status:'delivering',details:[{...row(1,20),deliveredQty:2},row(2,30)]}});await tick();assert.equal(h.c.deliverOpen,true);assert.deepEqual(h.c.deliverForm.items.map(r=>r.deliverQuantity),[0,3]);assert.equal(h.c.submitLoading,false)})
test('reopening during delivery confirmation cannot send for a new target',async()=>{const h=await delivery();h.c.deliverForm.warehouseId=20;const p=h.c.submitDeliver();h.c.invalidateDelivery();h.c.deliverDetail={noticeId:12};h.confirms[0].resolve();await p;assert.equal(h.calls.filter(c=>c.name==='deliverDeliveryNotice').length,0)})
test('uncertain result blocks repeat until explicit refresh; attempted quantities reset',async()=>{const h=await delivery();h.c.deliverForm.warehouseId=20;const p=h.c.submitDeliver();h.confirms[0].resolve();await tick();h.calls.at(-1).reject(Error('network'));await p;assert.equal(h.c.deliveryUncertain,true);await h.c.submitDeliver();assert.equal(h.confirms.length,1);const refresh=h.c.refreshDeliverState();h.calls.at(-1).resolve({data:{noticeId:11,status:'delivering',details:[{...row(1,20),deliveredQty:4},row(2,30)]}});await refresh;assert.equal(h.c.deliveryUncertain,false);assert.equal(h.c.deliverForm.items[0].deliverQuantity,0)})
test('warehouse selector supersedes pending old scope and permits retry',async()=>{const h=harness('components/WarehouseSelect.vue');h.c.$props.autoload=true;h.c.$props.scopeDeptId=5;await tick();const old=h.calls.at(-1);h.c.$props.scopeDeptId=6;await tick();const fresh=h.calls.at(-1);assert.notEqual(old,fresh);fresh.resolve({data:[{deptId:60,deptType:'WAREHOUSE',status:'0'}]});await tick();old.resolve({data:[{deptId:50,deptType:'WAREHOUSE',status:'0'}]});await tick();assert.equal(h.c.warehouses[0].deptId,60);const retry=h.c.reloadWarehouses();h.calls.at(-1).reject(Error('offline'));await retry;assert.equal(h.c.loaded,false);const ok=h.c.loadWarehouses();h.calls.at(-1).resolve({data:[{deptId:60,deptType:'WAREHOUSE'}]});await ok;assert.equal(h.c.loaded,true)})

test('OE selected supplier placeholder never counts as loaded catalog; real options supersede history and retry',async()=>{
 const h=harness('oe/index.vue');h.c.form.supplierName='旧供应商';h.c.form.supplierPhone='111';h.c.ensureSelectedSupplier();assert.equal(h.c.supplierOptions.length,1)
 const first=h.c.loadSuppliers();assert.equal(h.calls.at(-1).name,'listSupplier');assert.equal(h.c.loadSuppliers(),first)
 h.calls.at(-1).reject(Error('offline'));await first;assert.equal(h.c.supplierLoaded,false);assert.equal(h.c.form.supplierName,'旧供应商');assert.ok(h.c.supplierLoadError)
 const second=h.c.loadSuppliers();h.calls.at(-1).resolve({rows:[{supplierId:2,supplierName:'旧供应商',contactPhone:'222'},{supplierId:3,supplierName:'新供应商'}]});await second
 assert.equal(h.c.supplierLoaded,true);assert.equal(h.c.supplierOptions.length,2);assert.equal(h.c.supplierOptions[0]._historical,undefined);h.c.handleSupplierChange('旧供应商');assert.equal(h.c.form.supplierPhone,'222')
})
test('supplier response from an invalidated organization cannot replace current options or form',async()=>{
 const h=harness('oe/index.vue');h.c.form.supplierName='保留';h.c.ensureSelectedSupplier();const first=h.c.loadSuppliers(),a=h.calls.at(-1);h.env.dept=6;h.c.invalidateSupplierCache();const second=h.c.loadSuppliers(),b=h.calls.at(-1)
 b.resolve({rows:[{supplierId:3,supplierName:'B'}]});await second;a.resolve({rows:[{supplierId:2,supplierName:'A'}]});await first;assert.equal(h.c.form.supplierName,'保留');assert.deepEqual(h.c.supplierOptions.map(x=>x.supplierName),['保留','B'])
})
test('H5 sales keeps per-line warehouses and version; STORE id is never a warehouse fallback',()=>{
 const configs=require('../src/views/mobile/feature/mobileFormConfigs'),payloads=require('../src/views/mobile/feature/mobileFormPayloads')
 const config=configs.getMobileFormConfig('sales'),source={orderId:1,version:4,customerId:3,details:[{itemType:'product',itemId:1,quantity:2,unitPrice:5,warehouseId:20},{itemType:'gift',itemId:2,quantity:1,unitPrice:8,warehouseId:30}]}
 const form=payloads.createMobileFormData(config,source,{selectedDeptId:5,selectedDeptType:'STORE'});assert.equal(form.warehouseId,'');assert.equal(form.version,4);form.warehouseId=40
 const payload=payloads.buildMobileFormPayload(config,form);assert.deepEqual(payload.details.map(x=>x.warehouseId),[20,30]);assert.equal(payload.version,4)
 assert.equal(payloads.createMobileFormData(config,null,{selectedDeptId:5,selectedDeptType:'STORE'}).warehouseId,'')
})

test('inventory category IDs are typed; OE/gift trees load separately and late OE cannot replace gift',async()=>{
 const h=harness('stock/index.vue');h.c.loadStockData=()=>{};h.c.queryParams.itemType='oe';const a=h.c.loadCategories(),old=h.calls.at(-1);assert.equal(old.name,'oeCategoryTree')
 h.c.queryParams.itemType='gift';h.c.queryParams.categoryId=1;h.c.handleItemTypeQueryChange();const fresh=h.calls.at(-1);assert.equal(fresh.name,'giftCategoryTree');assert.equal(h.c.queryParams.categoryId,undefined)
 fresh.resolve({data:[{categoryId:1,categoryName:'Gift'}]});await tick();old.resolve({data:[{categoryId:1,categoryName:'OE'}]});await a
 assert.equal(h.c.categoryOptions[0].categoryKey,'gift:1');assert.equal(h.c.categoryLoadedType,'gift');h.c.handleCategoryClick({categoryId:1,itemType:'oe'});assert.equal(h.c.queryParams.categoryId,undefined)
 h.c.handleCategoryClick(h.c.categoryOptions[0]);assert.equal(h.c.queryParams.categoryId,1)
 const before=h.calls.length;h.c.queryParams.itemType='';h.c.handleItemTypeQueryChange();assert.equal(h.calls.length,before);assert.equal(h.c.queryParams.categoryId,undefined);assert.deepEqual(h.c.categoryOptions,[])
})
test('failed type category load permits retry and never falls back to product',async()=>{
 const h=harness('stock/index.vue');h.c.queryParams.itemType='oe';const p=h.c.loadCategories();h.calls.at(-1).reject(Error('denied'));await p
 assert.equal(h.c.categoryLoadedType,'');assert.ok(h.c.categoryLoadError);assert.equal(h.c.categoryLoading,false)
 const retry=h.c.loadCategories();assert.equal(h.calls.at(-1).name,'oeCategoryTree');h.calls.at(-1).resolve({data:[{categoryId:1,categoryName:'OE'}]});await retry;assert.equal(h.c.categoryLoadedType,'oe')
})
test('stock list late success/failure cannot replace a newer item type query',async()=>{
 const h=harness('stock/index.vue');h.c.ensureEntryContext=()=>true;h.c.queryParams.itemType='oe';const a=h.actualGetList(),old=h.calls.at(-1)
 h.c.queryParams.itemType='gift';const b=h.actualGetList(),fresh=h.calls.at(-1);fresh.resolve({rows:[{itemType:'gift',itemId:1,itemName:'Gift'}],total:1});await b
 old.reject(Error('old network error'));await a;assert.equal(h.c.list[0].itemType,'gift');assert.equal(h.c.stockLoadError,'');assert.equal(h.c.loading,false)
})

test('notice cancellation freezes its ID and organization across confirmation and late response',async()=>{
 const h=harness('deliveryNotice/index.vue'),row={noticeId:11,noticeNo:'N11'};const stale=h.c.handleCancel(row);h.env.dept=6;h.c.handleDeliveryContextChanged();h.confirms[0].resolve();await stale;assert.equal(h.calls.filter(c=>c.name==='cancelDeliveryNotice').length,0);
 const current=h.c.handleCancel(row);h.confirms[1].resolve();await tick();const call=h.calls.at(-1);assert.equal(call.name,'cancelDeliveryNotice');assert.equal(call.args[0],'11');assert.equal(call.args[1].silentError,true);
 h.env.dept=5;h.c.handleDeliveryContextChanged();call.reject(Error('obsolete network error'));await current;assert.equal(h.messages.length,0);
});
test('notice export never substitutes filters edited after the confirmation opened',async()=>{
 const h=harness('deliveryNotice/index.vue'),downloads=[];h.c.download=(...args)=>downloads.push(args);h.c.exportFileName=()=> 'notice.xlsx';const p=h.c.handleExport();h.c.queryParams.salesOrderNo='new-filter';h.confirms[0].resolve();await p;assert.equal(downloads.length,0);
});

async function historicalRepair(){
 const h=harness('sales/index.vue'),p=h.c.openWarehouseRepair({orderId:91});h.calls.at(-1).resolve({data:{orderId:91,orderNo:'H91',status:'submitted',version:3,details:[{detailId:1,productName:'One',quantity:2,warehouseId:null},{detailId:2,productName:'Two',quantity:3,warehouseId:null},{detailId:3,warehouseId:30}]}});await p;await tick();h.c.onRepairWarehousesLoaded([{deptId:20,deptName:'W20'}]);return h
}
test('controlled historical repair never auto maps even a sole warehouse and freezes reviewed payload',async()=>{
 const h=await historicalRepair();assert.equal(h.c.repairForm.details.length,2);assert.ok(h.c.repairForm.details.every(d=>!d.warehouseId));await h.c.submitWarehouseRepair();assert.equal(h.confirms.length,0);
 h.c.repairDefaultWarehouseId=20;h.c.applyRepairDefault();const p=h.c.submitWarehouseRepair();h.c.repairForm.details[0].warehouseId=30;h.confirms[0].resolve();await tick();const call=h.calls.at(-1);assert.equal(call.name,'repairSalesWarehouses');assert.deepEqual(JSON.parse(JSON.stringify(call.args)),[91,{version:3,assignments:[{detailId:1,warehouseId:20},{detailId:2,warehouseId:20}]},{silentError:true}]);call.resolve({});await p;assert.equal(h.c.repairOpen,false);
});
test('historical unknown result blocks repeat, failed read retains mappings, fresh read removes already-filled lines',async()=>{
 const h=await historicalRepair();h.c.repairDefaultWarehouseId=20;h.c.applyRepairDefault();const p=h.c.submitWarehouseRepair();h.confirms[0].resolve();await tick();h.calls.at(-1).reject(Error('lost response'));await p;assert.equal(h.c.repairUncertain,true);await h.c.submitWarehouseRepair();assert.equal(h.confirms.length,1);
 const failed=h.c.refreshWarehouseRepair();h.calls.at(-1).reject(Error('offline'));await failed;assert.equal(h.c.repairUncertain,true);assert.deepEqual(h.c.repairForm.details.map(d=>d.warehouseId),[20,20]);
 const refresh=h.c.refreshWarehouseRepair();h.calls.at(-1).resolve({data:{orderId:91,orderNo:'H91',status:'submitted',version:4,details:[{detailId:1,warehouseId:30},{detailId:2,productName:'Two',warehouseId:null}]}});await refresh;assert.equal(h.c.repairUncertain,false);assert.equal(h.c.repairForm.version,4);assert.deepEqual(h.c.repairForm.details.map(d=>[d.detailId,d.warehouseId]),[[2,20]]);
});
test('closing historical repair during confirmation discards request and old load failure stays silent',async()=>{
 const h=await historicalRepair();h.c.repairDefaultWarehouseId=20;h.c.applyRepairDefault();const p=h.c.submitWarehouseRepair();h.c.repairOpen=false;await tick();h.confirms[0].resolve();await p;assert.equal(h.calls.filter(c=>c.name==='repairSalesWarehouses').length,0);
 const open=h.c.openWarehouseRepair({orderId:92}),call=h.calls.at(-1);h.c.repairRevision+=1;h.c.operationScope().invalidate('repair');call.reject(Error('obsolete'));await open;assert.equal(h.messages.length,0);
});
