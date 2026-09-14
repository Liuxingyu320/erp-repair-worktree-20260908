const test = require('node:test')
const assert = require('node:assert/strict')
const { harness,flush,type,bucket,balance } = require('./helpers/leaveBalanceHarness')
const util = require('../src/utils/leaveBalanceUi')
const SUMMARY='src/views/oa/attendance/components/LeaveBalanceSummary.vue'
const MANAGEMENT='src/views/oa/attendance/components/LeaveBalanceManagement.vue'
async function summary(props) { const h=harness(SUMMARY,props);h.resolve('/options/types',[type()]);await flush();return h }
test('exact unit formatting preserves large integers and does not invent unconfigured zero',()=>{
  assert.equal(util.exactId('9223372036854775807'),'9223372036854775807')
  assert.throws(()=>util.exactId(9007199254740992))
  assert.equal(util.formatUnits('480000000','DAYS','480'),'1 天')
  assert.equal(util.formatUnits('60000000','HOURS',null),'1 小时')
  assert.equal(util.formatUnits(null,'DAYS','480'),'待核对')
  assert.equal(util.formatUnits('1000000','DAYS',null),'待核对')
  assert.equal(util.formatUnits('9007199254740993','MINUTES',null),'9007199254.740993 分钟')
})
test('self balance reads actual self API and renders sources with server unit conversion',async()=>{
  const h=await summary();const r=h.take('/balance/my');assert.equal(r.options.params.leaveTypeId,'1');r.resolve({data:balance()});await flush()
  assert.equal(h.c.display(h.c.balance.availableUnits),'10 天');assert.ok(h.render());h.c.$destroy()
})
test('unknown policy stays unknown instead of rendering an available zero',async()=>{
  const h=await summary();h.resolve('/balance/my',balance('7',{status:'POLICY_NOT_CONFIGURED',availableUnits:null,reason:'缺工作地映射'}));await flush()
  assert.equal(h.c.balance.reason,'缺工作地映射');assert.equal(h.c.display(h.c.balance.availableUnits),'待核对');h.c.$destroy()
})
test('self response for another account is rejected',async()=>{
  const h=await summary();h.resolve('/balance/my',balance('8'));await flush();assert.equal(h.c.balance,null);assert.match(h.c.error,/不匹配/);h.c.$destroy()
})
test('late balance response after silent organization switch cannot replace current context',async()=>{
  const h=await summary();const r=h.take('/balance/my');h.state.dept='200';r.resolve({data:balance()});await flush();assert.equal(h.c.balance,null);h.c.$destroy()
})
test('HR balance uses exact employee ID and separate authorized endpoint',async()=>{
  const h=await summary({userId:'9007199254741001'});h.resolve('/employees/9007199254741001',balance('9007199254741001'));await flush();assert.equal(h.c.balance.userId,'9007199254741001');h.c.$destroy()
})
test('read permission alone does not recalculate or create write requests',async()=>{
  const h=harness(SUMMARY,{userId:'17'},['read']);h.resolve('/options/types',[type()]);await flush();h.resolve('/employees/17',balance('17'));await flush();assert.equal(h.c.canRecalculate,false);await h.c.recalculate();assert.equal(h.requests.length,0);h.c.$destroy()
})
test('employee search uses scoped server pagination and literal keyword payload',async()=>{
  const h=harness(MANAGEMENT);h.c.keyword='%甲_';const action=h.c.search(2),r=h.take('/options/employees');assert.deepEqual({...r.options.params},{ownerDeptId:'100',keyword:'%甲_',pageNum:2,pageSize:20})
  r.resolve({data:{rows:[{userId:'9007199254741001',userName:'甲',account:'jia',deptName:'仓'}],total:21}});await action;h.c.selectEmployee(h.c.employees[0]);assert.equal(h.c.userId,'9007199254741001');h.c.$destroy()
})
test('stale employee candidates cannot be selected before department watcher runs',async()=>{
  const h=harness(MANAGEMENT),action=h.c.search(1);h.resolve('/options/employees',{rows:[{userId:'17',userName:'甲'}],total:1});await action;h.state.dept='200';h.c.selectEmployee(h.c.employees[0]);assert.equal(h.c.userId,'');h.c.$destroy()
})
function adjustment() {const h=harness(MANAGEMENT);h.c.userId='17';h.c.selectedName='甲';h.c.receivedBalance(balance('17'));h.c.openAdjustment(h.c.balance.buckets[0]);h.c.amount='1.250000';h.c.reason='HR核对';return h}
test('adjustment echoes exact reviewed historical bucket and rule unit snapshot',async()=>{
  const h=adjustment();h.c.balance.ruleVersion=9
  const action=h.c.confirmAdjustment();assert.equal(h.confirms.length,1);h.confirms.shift().resolve();await flush()
  const r=h.take('/employees/17/adjustments');assert.equal(r.options.data.bucketId,'91');assert.equal(r.options.data.bucketVersion,'3');assert.equal(r.options.data.ruleVersion,2);assert.equal(r.options.data.displayUnit,'DAYS');assert.equal(r.options.data.minutesPerDay,'480');assert.equal(r.options.data.amount,'1.250000')
  r.resolve({data:{commandId:'901',bucketId:'91',commandKey:'ADJUST|7|'+r.options.data.clientRequestId,resultUnits:'600000000'}});await action;assert.equal(h.c.attempt,null);assert.equal(h.storage.size,0);h.c.$destroy()
})
test('confirmation frozen before account change never posts adjustment',async()=>{
  const h=adjustment(),action=h.c.confirmAdjustment();h.store.getters.id='8';h.confirms.shift().resolve();await action;assert.equal(h.requests.length,0);h.c.$destroy()
})
test('unknown adjustment retains original body and replays same request exactly',async()=>{
  const h=adjustment(),action=h.c.confirmAdjustment();h.confirms.shift().resolve();await flush();const first=h.take('/employees/17/adjustments'),body=JSON.stringify(first.options.data);first.reject(Error('timeout'));await action
  h.c.amount='88';const retry=h.c.sendAdjustment(),second=h.take('/employees/17/adjustments');assert.equal(JSON.stringify(second.options.data),body);second.resolve({data:{commandId:'901',bucketId:'91',commandKey:'ADJUST|7|'+second.options.data.clientRequestId}});await retry;assert.equal(h.c.attempt,null);h.c.$destroy()
})
test('business rejection permits correcting adjustment while generic failure stays unknown',async()=>{
  const h=adjustment(),action=h.c.confirmAdjustment();h.confirms.shift().resolve();await flush();const error=Object.assign(Error('余额不足'),{response:{status:200,data:{code:500,msg:'余额不足'}}});h.take('/employees/17/adjustments').reject(error);await action;assert.equal(h.c.attempt,null);assert.equal(h.storage.size,0)
  assert.equal(util.definiteRejection({response:{status:200,data:{code:500,msg:'系统处理失败，请稍后重试或联系管理员'}}}),false);h.c.$destroy()
})
test('confirmed overtime source cannot be manually adjusted through HR balance control',()=>{
  const h=adjustment();h.c.adjustOpen=false;h.c.balance.buckets=[bucket({sourceType:'OVERTIME'})];h.c.openAdjustment(h.c.balance.buckets[0]);assert.equal(h.c.adjustOpen,false);h.c.$destroy()
})
test('storage failure prevents sending an unrecorded adjustment',async()=>{
  const h=adjustment();h.state.storageFails=true;const action=h.c.confirmAdjustment();h.confirms.shift().resolve();await action;assert.equal(h.requests.length,0);assert.match(h.c.error,/storage/);h.c.$destroy()
})
test('HR ledger pagination never merges rows from a different selected employee',async()=>{
  const h=adjustment(),action=h.c.loadLedger(false);const r=h.take('/employees/17/ledger');h.c.userId='18';r.resolve({data:[{ledgerId:'99',action:'GRANT'}]});await action;assert.equal(h.c.ledger.length,0);h.c.$destroy()
})

test('unknown adjustment retry remains bound to original actor and organization',async()=>{
 for(const drift of ['dept','actor']){const h=adjustment(),action=h.c.confirmAdjustment();h.confirms.shift().resolve();await flush();h.take('/employees/17/adjustments').reject(Error('timeout'));await action;const saved=Array.from(h.storage.entries());if(drift==='dept')h.state.dept='200';else h.store.getters.id='8';await h.c.sendAdjustment();assert.equal(h.requests.filter(r=>r.options.method==='post').length,0);assert.deepEqual(Array.from(h.storage.entries()),saved);h.c.$destroy()}
})
test('secure randomValues supports browsers without randomUUID and never falls back to Math.random',()=>{
 const h=adjustment(),u=h.load('src/utils/leaveBalanceUi.js');delete h.window.crypto.randomUUID;let count=0;h.window.crypto.getRandomValues=bytes=>{for(let i=0;i<bytes.length;i++)bytes[i]=++count;return bytes};const a=u.requestId(),b=u.requestId();assert.match(a,/^leave-balance:[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);assert.notEqual(a,b);delete h.window.crypto.getRandomValues;assert.throws(()=>u.requestId(),/安全请求标识/);h.c.$destroy()
})

test('specialist attendance entry lands on the available balance action without requiring day or shift permission',()=>{
 for(const [permission,tab] of [['convert','compensatory'],['rule','balanceRules'],['read','balance']]){const h=harness('src/views/oa/attendance/index.vue',{},[permission],{shallow:true});assert.equal(h.c.activeTab,tab);assert.equal(h.c.can('oa:attendance:day:list'),false);assert.equal(h.requests.length,0);h.c.$destroy()}
})
test('standard leave types enforce HR approval and annual/compensatory balance while preserving old custom options',async()=>{
 const h=harness('src/views/oa/attendance/components/LeaveManagement.vue',{},[]);for(const code of ['PERSONAL','SICK','ANNUAL','COMPENSATORY','MARRIAGE','BEREAVEMENT','MATERNITY','PATERNITY','CHILDCARE']){h.c.openTypeEdit({typeCode:code,balanceRequired:false,approvalRequired:false});assert.equal(h.c.typeForm.approvalRequired,true);assert.equal(h.c.typeForm.balanceRequired,['ANNUAL','COMPENSATORY'].includes(code))}
 h.c.openTypeEdit({typeCode:'CUSTOM_LEGACY',balanceRequired:false,approvalRequired:false});assert.equal(h.c.typeForm.approvalRequired,false);assert.equal(h.c.typeForm.minutesPerDay,null);h.c.$destroy()
})
test('ledger quantities use their own historical bucket unit instead of the current rule unit',()=>{
 const h=adjustment();h.c.balance.displayUnit='HOURS';h.c.balance.minutesPerDay='420';assert.equal(h.c.ledgerAmount({bucketId:'91',units:'480000000'}),'1 天');assert.equal(h.c.ledgerAmount({bucketId:'missing',units:'480000000'}),'待核对');h.c.$destroy()
})
