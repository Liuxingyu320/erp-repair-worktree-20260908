const test=require('node:test'),assert=require('node:assert/strict'),Vue=require('vue'),fs=require('node:fs'),compiler=require('vue-template-compiler')
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
const tick=async()=>{for(let i=0;i<5;i++)await Vue.nextTick()}
function fixture(){
 const calls=[],messages=[],downloads=[],env={dept:'10',ask:()=>Promise.resolve()},store=Vue.observable({getters:{id:'7'},state:{user:{sessionRevision:1}}})
 const api=new Proxy({}, {get:(_,name)=>(...args)=>{const d=deferred();calls.push({name,args,...d});return d.promise}})
 const mixin=require('./helpers/exportRecoveryHarness')(api,env).default
 const p=new Vue({mixins:[mixin],created:[],data:()=>({selectedRows:[{reimbursementId:'101'},{reimbursementId:'102'}],exporting:false,financeMode:true}),beforeCreate(){this.$store=store;this.$modal={confirm:()=>env.ask(),msgSuccess:m=>messages.push(m),msgError:m=>messages.push(m)};this.$download={saveAs:(...args)=>downloads.push(args)}},methods:{loadList:()=>Promise.resolve()}})
 const take=name=>{const c=calls.find(c=>!c.used&&c.name===name);assert.ok(c,'missing '+name);c.used=true;return c}
 return {p,calls,messages,downloads,env,store,take,mixin}
}
const batch=command=>({batchId:'601',batchNo:'EXP601',requestId:command.requestId,archiveName:'original.zip',reimbursementCount:2,archiveStatus:'AVAILABLE'})
async function unknown(h){const a=h.p.exportSelected();await tick();const post=h.take('createReimbursementExport');post.reject({message:'timeout'});await a;return post}
test('A12 accepted batch survives download failure; original GET retry never creates another batch',async()=>{
 const h=fixture(),p=h.p,ask=deferred();h.env.ask=()=>ask.promise;const a=p.exportSelected();p.selectedRows=[{reimbursementId:'999'}];ask.resolve();await tick();const post=h.take('createReimbursementExport');assert.deepEqual(Array.from(post.args[0]),['101','102']);const receipt=batch(p.pendingExportCommand);post.resolve({data:receipt});await tick();assert.equal(p.exportReceipts[0].batchId,'601');assert.equal(p.pendingExportCommand,null);h.take('downloadReimbursementExport').reject({message:'connection lost'});await a;assert.match(p.exportRecoveryError,/下载未完成/);const retry=p.downloadOriginalExport(receipt);h.take('downloadReimbursementExport').resolve({valid:true});await retry;assert.equal(h.downloads[0][1],'original.zip');assert.equal(h.calls.filter(c=>c.name==='createReimbursementExport').length,1);assert.match(h.messages.at(-1),/已发起.*浏览器/);p.$destroy()
})
test('A12 unknown result checks durable receipt without automatic POST or automatic download',async()=>{
 const h=fixture(),p=h.p;await unknown(h);assert.match(p.exportRecoveryError,/结果待核对/);p.retryExportCommand();assert.equal(h.calls.length,1);const check=p.checkExportCommand();h.take('getReimbursementExportCommand').resolve({data:{state:'SUCCEEDED',batch:batch(p.pendingExportCommand)}});await check;assert.equal(p.pendingExportCommand,null);assert.equal(p.exportReceipts.length,1);assert.equal(h.calls.length,2);p.$destroy()
})
test('A12 NOT_OBSERVED enables only explicit identical request and IDs; malformed ACK remains unresolved',async()=>{
 const h=fixture(),p=h.p,first=await unknown(h),request=JSON.stringify(first.args);const check=p.checkExportCommand();h.take('getReimbursementExportCommand').resolve({data:{state:'NOT_OBSERVED'}});await check;assert.match(p.exportRecoveryError,/不能证明未生成/);assert.equal(h.calls.filter(c=>c.name==='createReimbursementExport').length,1);p.selectedRows=[{reimbursementId:'999'}];const retry=p.retryExportCommand();const post=h.take('createReimbursementExport');assert.equal(JSON.stringify(post.args),request);post.resolve({data:{batchId:'601',requestId:'unrelated'}});await retry;assert.ok(p.pendingExportCommand);assert.equal(p.exportReceipts.length,0);assert.equal(p.exportCommandChecked,false);p.$destroy()
})
test('A12 cancellation is silent and stale identity success/finally cannot overwrite new operation',async()=>{
 const h=fixture(),p=h.p;h.env.ask=()=>Promise.reject('cancel');await p.exportSelected();assert.equal(h.calls.length,0);assert.equal(p.exportRecoveryError,'');h.env.ask=()=>Promise.resolve();const old=p.exportSelected();await tick();const post=h.take('createReimbursementExport'),receipt=batch(p.pendingExportCommand);h.store.state.user.sessionRevision++;await tick();p.exporting=true;p.exportRecoveryError='B';post.resolve({data:receipt});await old;assert.equal(p.exporting,true);assert.equal(p.exportRecoveryError,'B');assert.equal(p.exportReceipts.length,0);assert.equal(h.calls.length,1);p.$destroy()
})
test('A12 history is owner-only default, newest request wins and late errors/finally cannot replace it',async()=>{
 const h=fixture(),p=h.p;const a=p.openExportHistory(),old=h.take('listReimbursementExports');assert.equal(old.args[0].allCreators,undefined);p.exportHistoryPage=2;const b=p.loadExportHistory(),fresh=h.take('listReimbursementExports');fresh.resolve({rows:[{batchId:'9',archiveStatus:'UNAVAILABLE'}],total:11});await b;old.reject(Error('old'));await a;assert.equal(p.exportHistoryRows[0].batchId,'9');assert.equal(p.exportHistoryTotal,11);assert.equal(p.exportHistoryError,'');assert.equal(p.exportHistoryLoading,false);p.$destroy()
})
test('A12 template keeps recovery/history entry beside filtered list and guards duplicate export',()=>{
 const source=fs.readFileSync(require('node:path').join(__dirname,'../src/views/oa/reimbursement/index.vue'),'utf8'),template=compiler.parseComponent(source).template.content
 assert.deepEqual(compiler.compile(template).errors,[]);assert.match(template,/@click="openExportHistory"/);assert.match(template,/@click="downloadOriginalExport\(batch\)"/);assert.match(template,/:disabled="!selectedRows.length \|\| !!pendingExportCommand"/)
 assert.match(template,/同意并打开下一条/);assert.match(source,/approveAndOpenNext/);assert.match(source,/returnToNextTodo\(\)/)
})

const rejectExport = (code,status=code)=>({code,response:{status,data:{code,msg:'所选集合不可导出'}}})
test('A12 first explicit 409/403 rejection preserves selection but releases the candidate so a corrected set can be exported',async()=>{
 for(const error of [rejectExport(409,200),rejectExport(403),rejectExport(400)]) {
  const h=fixture(),p=h.p,running=p.exportSelected();await tick();const post=h.take('createReimbursementExport'),oldId=post.args[1]
  post.reject(error);await running
  assert.equal(p.pendingExportCommand,null);assert.equal(p.exportCommandChecked,false);assert.equal(p.selectedRows.length,2);assert.match(p.exportRecoveryError,/调整选择/)
  p.selectedRows=[{reimbursementId:'103'}];const next=p.exportSelected();await tick();const fixed=h.take('createReimbursementExport')
  assert.deepEqual(Array.from(fixed.args[0]),['103']);assert.notEqual(fixed.args[1],oldId);fixed.reject(rejectExport(409));await next;p.$destroy()
 }
})
test('A12 generic body500 and HTTP5xx stay unknown even when a response arrived',async()=>{
 for(const error of [rejectExport(500,200),rejectExport(500),{message:'network disconnected'}]) {
  const h=fixture(),p=h.p,running=p.exportSelected();await tick();h.take('createReimbursementExport').reject(error);await running
  assert.ok(p.pendingExportCommand);assert.equal(p.exportCommandChecked,false);assert.match(p.exportRecoveryError,/待核对/);await p.exportSelected();assert.equal(h.calls.length,1);p.$destroy()
 }
})
test('A12 a later retry403/409 after unknown cannot release or change the original command and must query again',async()=>{
 for(const error of [rejectExport(403),rejectExport(409,200)]) {
  const h=fixture(),p=h.p,original=await unknown(h),command=p.pendingExportCommand;const check=p.checkExportCommand()
  h.take('getReimbursementExportCommand').resolve({data:{state:'NOT_OBSERVED'}});await check;p.selectedRows=[{reimbursementId:'999'}]
  const retry=p.retryExportCommand(),post=h.take('createReimbursementExport');assert.equal(JSON.stringify(post.args),JSON.stringify(original.args))
  post.reject(error);await retry;assert.equal(p.pendingExportCommand,command);assert.equal(p.exportCommandChecked,false);assert.match(p.exportRecoveryError,/不能据此认定/)
  await p.retryExportCommand();assert.equal(h.calls.filter(c=>c.name==='createReimbursementExport').length,2)
  const resolve=p.checkExportCommand();h.take('getReimbursementExportCommand').resolve({data:{state:'SUCCEEDED',batch:batch(command)}});await resolve
  assert.equal(p.pendingExportCommand,null);assert.equal(p.exportReceipts.length,1);p.$destroy()
 }
})
