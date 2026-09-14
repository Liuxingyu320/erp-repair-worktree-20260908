const test=require('node:test')
const assert=require('node:assert/strict')
const {harness,flush,type,source,context}=require('./helpers/leaveBalanceHarness')
const FILE='src/views/oa/attendance/components/OvertimeTransferDialog.vue'
async function ready(overrides={}) {const h=harness(FILE,{open:true,source:source()});h.resolve('/options/types',[type('1',{typeCode:'COMPENSATORY',typeName:'调休'})]);await flush();h.resolve('/context/9007199254741001',context(overrides));await flush();return h}
test('loads precise settled source and authoritative free minutes',async()=>{const h=await ready();assert.equal(h.c.context.availableMinutes,90);assert.equal(h.c.sourceId,'9007199254741001');assert.ok(h.render());h.c.$destroy()})
test('unsafe legacy day ID falls back to scoped paged candidates without rounding',async()=>{
 const h=harness(FILE,{open:true,source:source({dayResultId:9007199254740992})});h.resolve('/options/types',[type('1',{typeCode:'COMPENSATORY'})]);await flush();const r=h.take('/options/overtime-sources');assert.equal(r.options.params.ownerDeptId,'100');assert.equal(r.options.params.dateFrom,'2026-09-12');r.resolve({data:{rows:[source()],total:1}});await flush();assert.equal(h.c.sourceId,'');h.c.chooseSource(h.c.sources[0]);h.resolve('/context/9007199254741001',context());await flush();assert.equal(h.c.context.source.dayResultId,'9007199254741001');h.c.$destroy()
})
test('custom enabled type remains eligible for server matched compensatory policy',async()=>{
 const h=harness(FILE,{open:true,source:source()});h.resolve('/options/types',[type('2',{typeCode:'CUSTOM_COMP'}),type('1',{typeCode:'COMPENSATORY'})]);await flush();h.resolve('/context/9007199254741001',context());await flush();assert.equal(h.c.types.length,2);h.c.typeId='2';const action=h.c.loadContext(),r=h.take('/context/9007199254741001');assert.equal(r.options.params.leaveTypeId,'2');r.resolve({data:context()});await action;h.c.$destroy()
})
test('transfer freezes authoritative source version and exact request body before confirmation',async()=>{
 const h=await ready();h.c.minutes='40';h.c.reason='主管核定';const action=h.c.confirmTransfer();h.c.minutes='80';h.c.reason='后改';h.confirms.shift().resolve();await flush();const r=h.take('/overtime-transfers','post');assert.equal(r.options.data.sourceVersion,'8');assert.equal(r.options.data.transferMinutes,40);assert.equal(r.options.data.reason,'主管核定');r.resolve({data:{transferId:'901',sourceDayResultId:'9007199254741001',clientRequestId:r.options.data.clientRequestId}});await flush();h.resolve('/context/9007199254741001',context({transferredMinutes:50,availableMinutes:50}));await action;assert.equal(h.c.attempt,null);h.c.$destroy()
})
test('same-context double click cannot create a second transfer',async()=>{
 const h=await ready();h.c.minutes='40';h.c.reason='确认';const first=h.c.confirmTransfer();await h.c.confirmTransfer();assert.equal(h.confirms.length,1);h.confirms.shift().resolve();await flush();const r=h.take('/overtime-transfers','post');await h.c.confirmTransfer();assert.equal(h.requests.length,0);r.reject(Error('timeout'));await first;h.c.$destroy()
})
test('silent organization drift before click cannot submit old loaded source',async()=>{const h=await ready();h.c.minutes='40';h.c.reason='确认';h.state.dept='200';await h.c.confirmTransfer();assert.equal(h.confirms.length,0);assert.equal(h.requests.length,0);h.c.$destroy()})
test('department change during confirmation prevents all writes',async()=>{const h=await ready();h.c.minutes='40';h.c.reason='确认';const action=h.c.confirmTransfer();h.state.dept='200';h.confirms.shift().resolve();await action;assert.equal(h.requests.length,0);h.c.$destroy()})
test('late source context cannot overwrite a different selected department',async()=>{
 const h=harness(FILE,{open:true,source:source()});h.resolve('/options/types',[type()]);await flush();const r=h.take('/context/9007199254741001');h.state.dept='200';r.resolve({data:context()});await flush();assert.equal(h.c.context,null);h.c.$destroy()
})
test('unconfigured policy and salary lock block confirmation',async()=>{for(const status of ['RULE_REVIEW','SALARY_LOCKED','SOURCE_REVIEW']){const h=await ready({status,availableMinutes:null,reason:'待核对'});h.c.minutes='40';h.c.reason='确认';await h.c.confirmTransfer();assert.equal(h.confirms.length,0);assert.equal(h.requests.length,0);h.c.$destroy()}})
test('unknown transfer retries original identity after current form values change',async()=>{
 const h=await ready();h.c.minutes='40';h.c.reason='确认';const first=h.c.confirmTransfer();h.confirms.shift().resolve();await flush();const r=h.take('/overtime-transfers','post'),body=JSON.stringify(r.options.data);r.reject(Error('timeout'));await first;h.c.minutes='10';const retry=h.c.sendAttempt(),second=h.take('/overtime-transfers','post');assert.equal(JSON.stringify(second.options.data),body);second.reject(Error('timeout'));await retry;assert.ok(h.c.attempt);assert.equal(h.storage.size,1);h.c.$destroy()
})
test('reversal sends original transfer ID with a frozen reason and same-request retry',async()=>{
 const item={transferId:'902',action:'APPLY',reversed:false,transferMinutes:30};const h=await ready({history:[item]});const action=h.c.reverse(h.c.context.history[0]);h.prompts.shift().resolve({value:'核对原核定'});await flush();const r=h.take('/902/reverse'),body=JSON.stringify(r.options.data);r.reject(Error('timeout'));await action;const retry=h.c.sendAttempt(),second=h.take('/902/reverse');assert.equal(JSON.stringify(second.options.data),body);second.resolve({data:{transferId:'903',sourceDayResultId:'9007199254741001',clientRequestId:second.options.data.clientRequestId}});await flush();h.resolve('/context/9007199254741001',context());await retry;assert.equal(h.c.attempt,null);h.c.$destroy()
})
test('malformed success response keeps attempt pending and emits no completion',async()=>{
 const h=await ready();let changed=0;h.c.$on('changed',()=>changed++);h.c.minutes='40';h.c.reason='确认';const action=h.c.confirmTransfer();h.confirms.shift().resolve();await flush();h.take('/overtime-transfers','post').resolve({data:{transferId:'1'}});await action;assert.ok(h.c.attempt);assert.equal(changed,0);h.c.$destroy()
})

test('unknown transfer retry cannot migrate to a changed organization or actor before watchers flush',async()=>{
 for(const drift of ['dept','actor']){const h=await ready();h.c.minutes='40';h.c.reason='确认';const first=h.c.confirmTransfer();h.confirms.shift().resolve();await flush();const r=h.take('/overtime-transfers','post');r.reject(Error('timeout'));await first;const saved=Array.from(h.storage.entries());if(drift==='dept')h.state.dept='200';else h.store.getters.id='8';await h.c.sendAttempt();assert.equal(h.requests.filter(r=>r.options.method==='post').length,0);assert.deepEqual(Array.from(h.storage.entries()),saved);h.c.$destroy()}
})
test('restored legacy request is bound to its original storage scope and retains the original request key',async()=>{
 const h=await ready();const u=h.load('src/utils/leaveBalanceUi.js'),scope=h.c.scope(),body={sourceDayResultId:'9007199254741001',sourceVersion:'8',leaveTypeId:'1',transferMinutes:20,clientRequestId:'original-request',reason:'主管核定'};u.persistAttempt(scope,'overtime',{action:'apply',sourceId:body.sourceDayResultId,typeId:'1',body});h.c.attempt=u.restoreAttempt(scope,'overtime');assert.equal(h.c.attempt.scope,scope);const retry=h.c.sendAttempt(),r=h.take('/overtime-transfers','post');assert.equal(r.options.data.clientRequestId,'original-request');r.reject(Error('timeout'));await retry;h.c.$destroy()
})
