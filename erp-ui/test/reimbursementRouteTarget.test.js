const test=require('node:test'),assert=require('node:assert/strict'),Vue=require('vue')
const setup=require('./helpers/oaApprovalPageHarness')
const tick=async()=>{for(let i=0;i<8;i++)await Vue.nextTick()}
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
function fixture(){const h=setup('oa/reimbursement'),p=h.p;p.showDetail=h.component.methods.showDetail.bind(p);p.detail=null;p.approvalDetail=null;p.detailVisible=false;p.loadedRouteTargetKey='';return h}
const detail=id=>({data:{reimbursementId:id,approvalInstanceId:'3'+id,approvalRound:1,status:'pending'}})
const approval=id=>({data:{instance:{instanceId:'3'+id,businessCode:'OA_REIMBURSEMENT',businessId:id,status:'RUNNING'},tasks:[{taskId:'2'+id,taskStatus:'PENDING'}]}})
test('A14 same component route A to B reloads B and old A cannot fill details or clear B spinner',async()=>{
 const h=fixture(),p=h.p;await tick();h.route.query={businessId:'101',approvalInstanceId:'3101',approvalTaskId:'2101'};await tick();const a=h.take('getReimbursement');h.route.query={businessId:'102',approvalInstanceId:'3102',approvalTaskId:'2102'};await tick();const b=h.take('getReimbursement');a.reject(Error('A unavailable'));await tick();assert.equal(p.detailError,'');assert.equal(p.detailLoading,true);b.resolve(detail('102'));await tick();h.take('getApprovalInstance').resolve(approval('102'));await tick();assert.equal(p.detail.reimbursementId,'102');assert.equal(p.canAct,true);p.$destroy()
})
test('A14 list failure does not block explicit detail read and changing only approval instance reloads',async()=>{
 const h=fixture(),p=h.p;h.route.query={businessId:'101',approvalInstanceId:'3101',approvalTaskId:'2101'};await tick();h.take('getReimbursement').resolve(detail('101'));await tick();h.take('getApprovalInstance').resolve(approval('101'));await tick();h.route.query={businessId:'101',approvalInstanceId:'999',approvalTaskId:'888'};await tick();h.take('getReimbursement').resolve(detail('101'));await tick();assert.match(p.detailError,/审批轮次已变化/);assert.equal(p.canAct,false);p.loadList=()=>Promise.reject(Error('list unavailable'));p.loadedRouteTargetKey='';h.component.created.call(p);assert.ok(h.take('getReimbursement'));p.$destroy()
})
test('A14 removed/forbidden target clears previous approval and keepalive activation reopens current target',async()=>{
 const h=fixture(),p=h.p;h.route.query={businessId:'102',approvalInstanceId:'3102',approvalTaskId:'2102'};await tick();h.take('getReimbursement').reject({response:{status:403,data:{msg:'B无权'}}});await tick();assert.equal(p.detail,null);assert.equal(p.canAct,false);assert.equal(p.detailError,'B无权');p.pageInactive=true;h.component.activated.call(p);const read=h.take('getReimbursement');read.resolve(detail('102'));await tick();h.take('getApprovalInstance').resolve(approval('102'));await tick();h.route.query={};await tick();assert.equal(p.detail,null);assert.equal(p.detailVisible,false);p.$destroy()
})
test('A14 dirty navigation cancellation preserves A and stale confirmation cannot approve B after C navigation',async()=>{
 const h=fixture(),p=h.p;p.formVisible=true;p.form={reimbursementId:'101',title:'A',purpose:'changed',items:[]};p.formBaseline='different';const b=deferred(),c=deferred(),results=[];h.env.ask=()=>b.promise;h.component.beforeRouteUpdate.call(p,{}, {},v=>results.push(['B',v]));h.env.ask=()=>c.promise;h.component.beforeRouteUpdate.call(p,{}, {},v=>results.push(['C',v]));b.resolve();await tick();assert.deepEqual(results,[['B',false]]);assert.equal(p.form.title,'A');c.reject('cancel');await tick();assert.deepEqual(results,[['B',false],['C',false]]);assert.equal(p.formVisible,true);p.$destroy()
})
test('A14 explicit next button only returns through todo navigation, never posts approval',async()=>{
 const h=fixture(),paths=[];h.p.$router={push:path=>{paths.push(path);return Promise.resolve()},replace:path=>{paths.push(path);return Promise.resolve()}};await h.p.returnToNextTodo();assert.deepEqual(paths,['/workbench/todo']);assert.equal(h.calls.some(c=>/ApprovalTask/.test(c.name)),false);h.p.$destroy()
})
