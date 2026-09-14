const test=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),vm=require('vm'),babel=require('@babel/core'),Vue=require('vue'),compiler=require('vue-template-compiler')
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}},tick=async()=>{await new Promise(r=>setImmediate(r));await Vue.nextTick()}
function harness(){
 const calls=[],store=Vue.observable({getters:{id:'7',token:'a'}}),env={dept:'10'}
 const file=path.resolve(__dirname,'../src/views/inventory/components/CustomerServiceHistory.vue'),parsed=compiler.parseComponent(fs.readFileSync(file,'utf8'));assert.deepEqual(compiler.compile(parsed.template.content).errors,[])
 const code=babel.transformSync(parsed.script.content,{filename:file,babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code,module={exports:{}}
 vm.runInNewContext(code,{module,exports:module.exports,console,Promise,window:{addEventListener(){},removeEventListener(){}},require(id){
 if(id==='@/api/inventory/customer')return{listCustomerServiceRecords:(...args)=>{const d=deferred();calls.push({args,...d});return d.promise}}
 if(id==='@/utils/shopContext')return{getSelectedDeptId:()=>env.dept}
 if(id==='@/utils/uiOperationScope')return require('../src/utils/uiOperationScope')
 throw Error(id)
 }})
 const c=new Vue({...module.exports.default,created:undefined,propsData:{customerId:'1',active:true},beforeCreate(){this.$store=store}})
 return{c,calls,store,env}
}
function page(customerId='1',start=1,count=20,extra={}){return{data:{records:Array.from({length:count},(_,i)=>({customerId,recordId:String(start+i),serviceDate:'2026-09-13 10:00:00'})),total:101,hasMore:true,snapshotMaxRecordId:'101',nextServiceDate:'2026-09-13 10:00:00',nextRecordId:String(start+count-1),...extra}}}
test('older page failure preserves rows and exact snapshot cursor for retry',async()=>{
 const h=harness(),first=h.c.search();h.calls[0].resolve(page());await first
 const second=h.c.loadMore(),query=JSON.parse(JSON.stringify(h.calls[1].args));h.calls[1].reject(Error('offline'));await second
 assert.equal(h.c.records.length,20);assert.equal(h.c.cursor.beforeRecordId,'20');assert.ok(h.c.error)
 const retry=h.c.loadMore();assert.deepEqual(JSON.parse(JSON.stringify(h.calls[2].args)),query);h.calls[2].resolve(page('1',21,20));await retry
 assert.equal(h.c.records.length,40);assert.equal(h.c.cursor.beforeRecordId,'40');assert.equal(h.c.error,'')
})
test('customer, organization and filter changes cannot append old history into current view',async()=>{
 const h=harness(),a=h.c.search(),old=h.calls[0];h.c.$props.customerId='2';await tick();const fresh=h.calls.at(-1)
 fresh.resolve(page('2',1,2));await tick();old.resolve(page('1'));await a;assert.equal(h.c.records[0].customerId,'2')
 const obsolete=h.c.loadMore(),request=h.calls.at(-1);h.env.dept='20';h.c.keyword='绿茶';h.c.dateRange=['2026-01-01','2026-09-13'];const changed=h.c.search()
 request.reject(Error('old'));await obsolete;assert.equal(h.c.loading,true);const latest=h.calls.at(-1);assert.equal(latest.args[1].keyword,'绿茶');assert.equal(latest.args[1].beforeRecordId,undefined)
 latest.resolve(page('2',1,0,{total:0,hasMore:false}));await changed;assert.equal(h.c.total,0);assert.deepEqual(h.c.records,[])
})
test('deep history browsing survives appended-service refresh notification until explicit refresh',async()=>{
 const h=harness(),first=h.c.search();h.calls[0].resolve(page());await first;const more=h.c.loadMore();h.calls[1].resolve(page('1',21,20));await more
 h.c.$props.refreshToken=2;await tick();assert.equal(h.c.newAvailable,true);assert.equal(h.c.records.length,40);assert.equal(h.calls.length,2)
 const refresh=h.c.search();assert.equal(h.calls[2].args[1].snapshotMaxRecordId,undefined);h.calls[2].resolve(page());await refresh
 h.c.$props.active=false;await tick();assert.equal(h.c.records.length,0);assert.equal(h.c.loading,false)
})
test('both desktop and mobile customer templates use the same scoped history component',()=>{
 for(const relative of ['inventory/customer/index.vue','mobile/customer/index.vue']){
 const source=fs.readFileSync(path.resolve(__dirname,'../src/views',relative),'utf8'),parsed=compiler.parseComponent(source)
 assert.deepEqual(compiler.compile(parsed.template.content).errors,[]);assert.match(parsed.template.content,/<customer-service-history /)
 }
})
function parentHarness(mobile=false){
 const file=path.resolve(__dirname,'../src/views',mobile?'mobile/customer/index.vue':'inventory/customer/index.vue'),parsed=compiler.parseComponent(fs.readFileSync(file,'utf8'));
 const calls=[],messages=[],store=Vue.observable({getters:{id:'7',token:'a',permissions:['*:*:*']}}),env={dept:'10'},module={exports:{}};
 const code=babel.transformSync(parsed.script.content,{filename:file,babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code;
 vm.runInNewContext(code,{module,exports:module.exports,console,Promise,window:{addEventListener(){},removeEventListener(){}},require(id){
  if(id==='vue')return Vue;if(id==='@/utils/uiOperationScope')return require('../src/utils/uiOperationScope');
  if(id==='@/utils/shopContext')return{getSelectedDeptId:()=>env.dept,getSelectedDeptContext:()=>({deptId:env.dept,deptType:'STORE'})};
  if(id==='@/api/inventory/customer')return{addCustomerServiceRecord:(...args)=>{const d=deferred();calls.push({args,...d});return d.promise}};
  if(id.endsWith('mobileCustomerServiceRecord'))return{buildCustomerServiceRecordPayload:form=>({...form})};
  if(id.endsWith('mobileErrorMessage'))return{mobileErrorMessage:(e,f)=>e.message||f};return {};
 }});
 const c=new Vue({...module.exports.default,created:undefined,beforeCreate(){this.$store=store;this.$modal={msgSuccess:x=>messages.push(x),msgError:x=>messages.push(x)};this.parseTime=()=> '2026-09-13 10:00:00'}});
 for(const method of ['load','refresh','loadDetail','loadRows','loadCapabilities','releasePhoto','unlockBody'])c[method]=()=>{};
 c.writeEnabled=true;return{c,calls,messages,env,store};
}
for(const mobile of [false,true])test(`${mobile?'mobile':'desktop'} appended-service response cannot close or overwrite a later customer dialog`,async()=>{
 const h=parentHarness(mobile),a={customerId:'1',status:'0',version:1},b={customerId:'2',status:'0',version:2};h.c.selected=a;h.c.detail=a;h.c.detailOpen=true;
 if(mobile)h.c.openRecord();else h.c.openRecord(a);
 const first=mobile?h.c.submitRecord({serviceNote:'A',requestKey:'stable'}):h.c.saveRecord();
 h.c.selected=b;h.c.detail=b;if(mobile)h.c.openRecord();else h.c.openRecord(b);
 const second=mobile?h.c.submitRecord({serviceNote:'B'}):h.c.saveRecord();
 h.calls[0].resolve({data:{...a,version:2}});await first;
 assert.equal(String(h.c.selected.customerId),'2');assert.equal(String(h.c.detail.customerId),'2');assert.equal(mobile?h.c.recordDialog.open:h.c.recordOpen,true);assert.equal(mobile?h.c.recordDialog.saving:h.c.saving,true);assert.equal(h.messages.length,0);
 h.calls[1].reject(Error('B offline'));await second;assert.equal(mobile?h.c.recordDialog.saving:h.c.saving,false);assert.equal(mobile?h.c.recordDialog.open:h.c.recordOpen,true);
 if(mobile)assert.match(h.c.recordDialog.error,/B offline/);else assert.match(h.messages[0],/B offline/);
});
test('record submission retains its idempotency key on retry and rejects organization A-B-A completion',async()=>{
 const h=parentHarness(false),card={customerId:'1',status:'0'};h.c.openRecord(card);const key=h.c.record.requestKey;
 const fail=h.c.saveRecord();h.calls[0].reject(Error('timeout'));await fail;const retry=h.c.saveRecord();assert.equal(h.calls[1].args[1].requestKey,key);
 h.env.dept='20';h.c.resetRecordContext();h.env.dept='10';h.c.resetRecordContext();h.c.openRecord(card);
 h.calls[1].resolve({data:{...card,version:2}});await retry;assert.equal(h.c.recordOpen,true);assert.equal(h.messages.length,1);assert.equal(h.c.saving,false);
});
test('malformed or repeated next cursor cannot advance or lose current history rows',async()=>{
 const h=harness(),first=h.c.search();h.calls[0].resolve(page());await first;const before=JSON.parse(JSON.stringify(h.c.cursor));
 const repeated=h.c.loadMore();h.calls[1].resolve(page('1',21,20,{nextRecordId:'20'}));await repeated;assert.equal(h.c.records.length,20);assert.deepEqual(JSON.parse(JSON.stringify(h.c.cursor)),before);assert.ok(h.c.error);
 const retry=h.c.loadMore();h.calls[2].resolve(page('1',21,20,{nextServiceDate:null}));await retry;assert.equal(h.c.records.length,20);assert.ok(h.c.error);
});
