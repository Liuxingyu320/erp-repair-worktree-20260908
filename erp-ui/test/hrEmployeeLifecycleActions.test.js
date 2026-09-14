const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '..')
const tick = async () => { for(let i=0;i<8;i++) await Vue.nextTick() }
function deferred(){ let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject} }
function harness(sharedStorage) {
  const calls=[],events=[],storage=sharedStorage || new Map(),listeners=new Set()
  let dept='20',storageBlocked=false
  const route=Vue.observable({fullPath:'/hr/employee'}),store=Vue.observable({getters:{id:'88'}})
  const browser={addEventListener(name,fn){listeners.add(fn)},removeEventListener(name,fn){listeners.delete(fn)},
    sessionStorage:{getItem(key){if(storageBlocked)throw Error('storage blocked');return storage.get(key)||null},setItem(key,value){if(storageBlocked)throw Error('storage blocked');storage.set(key,value)},removeItem(key){storage.delete(key)}}}
  function load(relative,imports) {
    const filename=path.join(root,relative),source=fs.readFileSync(filename,'utf8')
    const code=babel.transformSync(relative.endsWith('.vue')?compiler.parseComponent(source).script.content:source,{filename,babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
    const module={exports:{}};vm.runInNewContext(code,{module,exports:module.exports,require(id){if(!(id in imports))throw Error('Unexpected import '+id);return imports[id]},window:browser,Date,Math,Promise,JSON},{filename})
    return relative.endsWith('.vue')?module.exports.default:module.exports
  }
  const api=load('src/api/hr/employee.js',{'@/utils/request':config=>{const d=deferred();calls.push({config,...d});return d.promise}})
  const options=load('src/views/hr/components/HrEmployeeLifecycleDialog.vue',{'@/api/hr/employee':api,'@/utils/shopContext':{getSelectedDeptId:()=>dept}})
  const c=new Vue({...options,propsData:{visible:false,employeeId:'9',scenario:'REGULARIZE'},beforeCreate(){this.$route=route;this.$store=store;this.$router={push:value=>events.push({route:value})}}})
  c.$on('update:visible',value=>{c.visible=value});c.$on('confirmed',value=>events.push(value))
  return {c,calls,events,storage,route,store,listeners,options,load,blockStorage(){storageBlocked=true},changeDept(value){dept=value;listeners.forEach(fn=>fn())},async open(scenario='REGULARIZE',id='9'){c.scenario=scenario;c.employeeId=id;c.visible=true;await tick()}}
}
function context(scenario='REGULARIZE',id='9') { return { userId:id,scenario,employeeName:'测试员工',employeeNo:'E09',departmentName:'部门',employeeStatus:'试用',postName:'岗位',eligible:true,blockedReason:null,businessDate:'2026-09-12',entryDate:'2026-05-01',probationStartDate:'2026-05-01',contractStartDate:'2025-10-01',contractEndDate:'2026-09-30',contractTypeCode:'LABOR_CONTRACT',contractTermCode:'FIXED_TERM',legalEntityId:'9007199254740993',legalEntityCode:'LE',legalEntityName:'主体',cycleKey:id+':2026-09-30:2',history:[] } }
async function ready(h,scenario='REGULARIZE',id='9'){await h.open(scenario,id);h.calls.at(-1).resolve({data:context(scenario,id)});await tick()}
Vue.config.warnHandler=message=>{if(!message.startsWith('Avoid mutating a prop'))throw Error(message)}

test('real SFC/API date-only request contains no payroll or position and success remains non-submittable',async()=>{
 const h=harness();await ready(h);assert.equal(h.calls[0].config.url,'/system/hr/employee/9/regularize/context')
 const submitting=h.c.submit();assert.equal(h.calls[1].config.url,'/system/hr/employee/9/regularize')
 assert.deepEqual(Object.keys(h.calls[1].config.data).sort(),['actualRegularizationDate','preservePositionSalary','requestId']);assert.equal(h.calls[1].config.data.preservePositionSalary,true)
 h.calls[1].resolve({data:{actionId:'9223372036854775807'}});await submitting
 assert.equal(h.events[0].actionId,'9223372036854775807');assert.equal(h.storage.size,0);assert.equal(h.c.canSubmit,false)
})
test('renewal freezes old cycle and contract identity while accepting only dates',async()=>{
 const h=harness();await ready(h,'RENEWAL','9223372036854775807');h.c.form.contractStartDate='2026-10-01';h.c.form.contractEndDate='2027-09-30'
 const pending=h.c.submit();const call=h.calls.at(-1)
 assert.equal(call.config.url,'/system/hr/employee/9223372036854775807/renewal/confirm');assert.equal(call.config.data.expectedCycleKey,'9223372036854775807:2026-09-30:2');assert.equal(call.config.data.legalEntityId,'9007199254740993')
 call.resolve({data:{actionId:'41'}});await pending
})
test('read failure or wrong employee never enables a prior employee context',async()=>{
 const h=harness();await ready(h);h.c.employeeId='10';await tick();assert.equal(h.c.context,null);assert.equal(h.c.canSubmit,false)
 h.calls.at(-1).resolve({data:context('REGULARIZE','9')});await tick();assert.equal(h.c.context,null);assert.match(h.c.error,/不一致/)
 const refresh=h.c.initialize();h.calls.at(-1).reject(Error('offline'));await refresh;assert.equal(h.c.canSubmit,false)
})
test('late read responses cannot cross employee or account scopes',async()=>{
 const h=harness();await h.open();const old=h.calls[0];h.c.employeeId='10';await tick();const current=h.calls.at(-1)
 old.resolve({data:context()});await tick();assert.equal(h.c.context,null)
 current.resolve({data:context('REGULARIZE','10')});await tick();assert.equal(h.c.context.userId,'10')
 h.store.getters.id='99';assert.equal(h.c.canSubmit,false);await tick();assert.equal(h.c.visible,false)
})
test('same tick department change and route departure prevent writes',async()=>{
 const h=harness();await ready(h);h.changeDept('30');await h.c.submit();assert.equal(h.calls.length,1);assert.equal(h.c.visible,false)
 const r=harness();await ready(r);r.route.fullPath='/other';await r.c.submit();assert.equal(r.calls.length,1)
})
test('unknown result replay is immutable and singleflight, then recoverable after closing',async()=>{
 const h=harness();await ready(h);const first=h.c.submit();const body=JSON.stringify(h.calls[1].config.data)
 h.c.submit();assert.equal(h.calls.length,2);h.c.form.actualRegularizationDate='2026-09-01'
 h.calls[1].reject(Error('connection lost'));await first;assert(h.c.pending);h.c.close();await tick()
 await h.open();h.calls.at(-1).resolve({data:{...context(),eligible:false,blockedReason:'已转正'}});await tick();assert.equal(h.c.canSubmit,true)
 const replay=h.c.submit();assert.equal(JSON.stringify(h.calls.at(-1).config.data),body);h.calls.at(-1).resolve({data:{actionId:'42'}});await replay;assert.equal(h.storage.size,0)
})
test('same-tab refreshed component restores original request instead of creating another',async()=>{
 const h=harness();await ready(h);const first=h.c.submit();const body=JSON.stringify(h.calls[1].config.data);h.calls[1].reject(Error('timeout'));await first;h.c.$destroy()
 const next=harness(h.storage);await ready(next);const replay=next.c.submit();assert.equal(JSON.stringify(next.calls[1].config.data),body);next.calls[1].resolve({data:{actionId:'42'}});await replay
})
test('definite rejection clears request but an HTTP500 unknown outcome stays frozen',async()=>{
 const h=harness();await ready(h);let pending=h.c.submit();h.calls.at(-1).reject({message:'规则不满足',response:{status:200,data:{code:500}}});await pending;assert.equal(h.c.pending,null);assert.equal(h.storage.size,0)
 pending=h.c.submit();h.calls.at(-1).reject({message:'gateway',response:{status:502}});await pending;assert(h.c.pending);assert.equal(h.storage.size,1)
})
test('invalid input and unavailable persistence do not send a business request',async()=>{
 const h=harness();await ready(h);h.c.form.actualRegularizationDate='2027-01-01';await h.c.submit();assert.equal(h.calls.length,1)
 h.c.form.actualRegularizationDate='2026-09-12';h.blockStorage();await h.c.submit();assert.equal(h.calls.length,1)
 const r=harness();await ready(r,'RENEWAL');r.c.form.contractStartDate='2026-09-30';r.c.form.contractEndDate='2027-09-30';await r.c.submit();assert.equal(r.calls.length,1)
})
test('server eligibility and invalid IDs are enforced before submission',async()=>{
 const h=harness();await h.open();h.calls.at(-1).resolve({data:{...context(),eligible:false,blockedReason:'无待决策'}});await tick();await h.c.submit();assert.equal(h.calls.length,1)
 const invalid=harness();await invalid.open('REGULARIZE','9223372036854775808');assert.equal(invalid.calls.length,0)
 const noActor=harness();noActor.store.getters.id=undefined;await noActor.open();assert.equal(noActor.calls.length,0)
})
test('old submission completion never overwrites another employee dialog',async()=>{
 const h=harness();await ready(h);const old=h.c.submit();const call=h.calls[1];h.c.employeeId='10';await tick();h.calls.at(-1).resolve({data:context('REGULARIZE','10')});await tick()
 call.resolve({data:{actionId:'42'}});await old;assert.equal(h.events.length,0);assert.equal(h.c.context.userId,'10');assert.equal(h.c.success,'')
})
test('unknown renewal recovery displays the original contract while separately showing current history',async()=>{
 const h=harness();await ready(h,'RENEWAL');h.c.form.contractStartDate='2026-10-01';h.c.form.contractEndDate='2027-09-30'
 const first=h.c.submit();h.calls.at(-1).reject(Error('timeout'));await first;h.c.close();await tick();await h.open('RENEWAL')
 h.calls.at(-1).resolve({data:{...context('RENEWAL'),contractEndDate:'2027-09-30',eligible:false,history:[{actionId:'42',actionType:'RENEWAL_CONFIRMED'}]}});await tick()
 assert.equal(h.c.displayContext.contractEndDate,'2026-09-30');assert.equal(h.c.context.history[0].actionId,'42');assert.equal(h.c.form.contractEndDate,'2027-09-30')
})
test('keep-alive departure clears visible state and stale result cannot clear a newer stored request',async()=>{
 const h=harness();await ready(h);const pending=h.c.submit();const key=h.c.storageKey();const call=h.calls[1]
 const newer=JSON.parse(h.storage.get(key));newer.body.requestId='newer-request';h.storage.set(key,JSON.stringify(newer))
 h.options.deactivated.call(h.c);await tick();assert.equal(h.c.visible,false)
 call.resolve({data:{actionId:'42'}});await pending;assert.equal(JSON.parse(h.storage.get(key)).body.requestId,'newer-request');assert.equal(h.events.length,0)
})
test('actual PC/H5 entry methods retain exact IDs and templates expose independent permissions',()=>{
 for(const relative of ['src/views/hr/components/HrEmployeeList.vue','src/views/mobile/hr/employee/index.vue']) {
   const source=fs.readFileSync(path.join(root,relative),'utf8'),script=compiler.parseComponent(source).script.content
   const method=script.match(/    openLifecycle\(row, scenario\) \{([\s\S]*?)\n    \},/)[1]
   const call=new Function('row','scenario','normalizePositiveDecimalId',method)
   const page={};call.call(page,{userId:'9223372036854775807'},'RENEWAL',value=>value)
   assert.equal(page.lifecycleEmployeeId,'9223372036854775807');assert.equal(page.lifecycleScenario,'RENEWAL');assert.equal(page.lifecycleOpen,true)
 }
 for(const relative of ['src/views/hr/components/HrProfileDetailDrawer.vue','src/views/mobile/hr/employee/index.vue','src/views/hr/components/HrEmployeeLifecycleDialog.vue','src/views/hr/components/HrEmployeeList.vue']) {
   const template=compiler.parseComponent(fs.readFileSync(path.join(root,relative),'utf8')).template.content
   assert.deepEqual(compiler.compile(template).errors,[],relative)
 }
})
