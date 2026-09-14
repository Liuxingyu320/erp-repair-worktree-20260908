const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const compiler = require('vue-template-compiler')
const recoveryPath = path.resolve(__dirname, '../src/utils/correctionRequestRecovery.js')
const scope = require('../src/utils/uiOperationScope')
const exact = require('../src/utils/positiveDecimalId')
const balance = require('../src/utils/leaveBalanceUi')
const policy = require('../src/views/mobile/attendance/attendancePunchPolicy')
function deferred() { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); return { promise, resolve, reject } }
const tick = () => new Promise(resolve => setImmediate(resolve))
function harness(storage = new Map()) {
  const browser = { crypto: require('node:crypto').webcrypto, sessionStorage: { getItem: key => storage.get(key), setItem: (key,value) => storage.set(key,value), removeItem: key => storage.delete(key) }, addEventListener() {}, removeEventListener() {} }
  const recoveryModule={exports:{}};vm.runInNewContext(fs.readFileSync(recoveryPath,'utf8'),{module:recoveryModule,window:browser,TextEncoder,Uint8Array})
  const recovery=recoveryModule.exports
  const descriptor=compiler.parseComponent(fs.readFileSync(path.resolve(__dirname,'../src/views/mobile/attendance/MobileAttendanceCorrection.vue'),'utf8'))
  assert.deepEqual(compiler.compile(descriptor.template.content).errors,[])
  const names=[];const source=descriptor.script.content.replace(/import\s+([\s\S]*?)\s+from\s+["'][^"']+["']\s*/g,(_,x)=>{names.push(...x.replace(/[{}]/g,'').split(',').map(n=>n.trim()).filter(Boolean));return ''}).replace('export default','return')
  const calls=[],shop={deptId:'10',isStore:true}
  const api=name=>(...args)=>{const d=deferred();calls.push({name,args,...d});return d.promise}
  const globals=Object.fromEntries(names.map(n=>[n,api(n)]));globals.checkPermi=()=>true;globals.getSelectedDeptContext=()=>shop
  const def=new Function('require','window',...names,source)(id=>id.includes('correctionRequestRecovery')?recovery:id.includes('uiOperationScope')?scope:id.includes('positiveDecimalId')?exact:id.includes('leaveBalanceUi')?balance:policy,browser,...names.map(n=>globals[n]))
  const model={...def.data(),$store:{state:{user:{id:'7',sessionRevision:1}},getters:{id:'7'}},$route:{fullPath:'/mobile/attendance'},todoBusinessId:''}
  for(const [n,f] of Object.entries(def.methods)) model[n]=f.bind(model)
  for(const [n,f] of Object.entries(def.computed)) Object.defineProperty(model,n,{get:()=>f.call(model),configurable:true})
  model.loadRows=async()=>{};model.showForm=true;Object.assign(model.form,{scheduleId:'11',correctionType:'MISSING_PUNCH',targetPunchType:'IN',requestedPunchTime:'2026-09-13T09:00',reason:'private correction reason'})
  model.schedules=[{scheduleId:'11'}]
  const take=async name=>{let c; for(let i=0;i<100&&!c;i++){c=calls.find(x=>x.name===name&&!x.used);if(!c) await tick()}assert.ok(c,`missing ${name}: ${calls.map(x=>x.name)}`);c.used=true;return c}
  const row=(state='DRAFT',id='101',input=model.form)=>({...input,correctionRequestId:id,status:state,rowVersion:2,userId:'7',shopId:'10'})
  return {model,def,calls,take,row,shop,storage,recovery}
}
test('lost submit response recovers PENDING without another update or submit',async()=>{
  const h=harness(),p=h.model.saveDraft(true);await tick();const c=(await h.take('createAttendanceCorrectionDraft'));c.resolve({data:h.row('DRAFT','101',c.args[0])});await tick()
  ;(await h.take('submitAttendanceCorrection')).reject(Error('network'));await tick();(await h.take('getAttendanceCorrection')).resolve({data:h.row('PENDING')});await p
  assert.equal(h.model.recoveryAttempt,null);assert.equal(h.model.showForm,false);assert.match(h.model.success,/审批中/)
  assert.equal(h.calls.filter(c=>c.name==='updateAttendanceCorrectionDraft').length,0)
})
test('unknown create retains exact original key and body until explicit NOT_FOUND replay',async()=>{
  const h=harness(),p=h.model.saveDraft(false);await tick();const c=(await h.take('createAttendanceCorrectionDraft'));const original=JSON.parse(JSON.stringify(c.args[0]));c.reject(Error('lost'));await tick();(await h.take('getAttendanceCorrectionByClientRequest')).reject(Error('offline'));await p
  assert.ok(h.model.recoveryAttempt);assert.equal(h.model.showForm,true)
  h.model.form.reason='later unsent edit';const again=h.model.reconcileCorrection(true);(await h.take('getAttendanceCorrectionByClientRequest')).reject(Error('CORRECTION_CLIENT_REQUEST_NOT_FOUND'));await tick()
  const replay=(await h.take('createAttendanceCorrectionDraft'));assert.deepEqual(JSON.parse(JSON.stringify(replay.args[0])),original)
  replay.resolve({data:h.row('DRAFT','102',original)});await again;assert.equal(h.model.form.correctionRequestId,'102')
})
test('uncertain lookup never creates another request or rotates identity',async()=>{
  const h=harness(),p=h.model.saveDraft(false);await tick();const c=(await h.take('createAttendanceCorrectionDraft'));c.reject(Error('lost'));await tick();(await h.take('getAttendanceCorrectionByClientRequest')).reject(Error('403'));await p
  const key=h.model.recoveryAttempt.clientRequestId;h.model.openNew();assert.equal(h.model.recoveryAttempt.clientRequestId,key)
  const r=h.model.reconcileCorrection(true);(await h.take('getAttendanceCorrectionByClientRequest')).reject(Error('503'));await r
  assert.equal(h.calls.filter(c=>c.name==='createAttendanceCorrectionDraft').length,1);assert.equal(h.model.recoveryAttempt.clientRequestId,key)
})
test('same-employee original SUBMITTING retry reads then submits and never edits draft',async()=>{
  const h=harness();h.model.showForm=false;const p=h.model.retrySubmitting(h.row('SUBMITTING'));(await h.take('getAttendanceCorrection')).resolve({data:h.row('SUBMITTING')});await tick()
  const submit=await h.take('submitAttendanceCorrection');assert.equal(h.model.busy,true,'retry must stay frozen through the submit promise');await h.model.retrySubmitting(h.row('SUBMITTING'));assert.equal(h.calls.filter(c=>c.name==='submitAttendanceCorrection').length,1);submit.resolve({data:h.row('PENDING')});await p
  assert.equal(h.model.busy,false);assert.equal(h.calls.filter(c=>c.name==='updateAttendanceCorrectionDraft').length,0);assert.match(h.model.success,/审批中/)
})
test('A-B-A organization and session changes discard old save success and failure',async()=>{
  for(const rejected of [false,true]){
    const h=harness(),p=h.model.saveDraft(false);await tick();const c=(await h.take('createAttendanceCorrectionDraft'))
    h.shop.deptId='20';h.model.invalidateCorrectionEditor();h.shop.deptId='10';h.model.invalidateCorrectionEditor();h.model.showForm=true;h.model.form.reason='new draft';h.model.busy=true
    if(rejected)c.reject(Error('old failure'));else c.resolve({data:h.row('DRAFT','101',c.args[0])});await p
    assert.equal(h.model.form.reason,'new draft');assert.equal(h.model.showForm,true);assert.equal(h.model.busy,true);assert.equal(h.model.success,'')
  }
})
test('session recovery stores digest and identifiers without personal fields; reload reads only',async()=>{
  const h=harness(),p=h.model.saveDraft(false);await tick();const c=(await h.take('createAttendanceCorrectionDraft'));c.reject(Error('lost'));await tick();(await h.take('getAttendanceCorrectionByClientRequest')).reject(Error('offline'));await p
  const saved=[...h.storage.values()][0];assert.ok(saved);assert.ok(!saved.includes('private correction reason'));assert.ok(!saved.includes('requestedPunchTime'));assert.match(JSON.parse(saved).payloadHash,/^[a-f0-9]{64}$/)
  const restored=harness(h.storage);restored.model.showForm=false;restored.model.restoreRecovery();const r=restored.model.reconcileCorrection(true)
  ;(await restored.take('getAttendanceCorrectionByClientRequest')).resolve({data:h.row('DRAFT','101',c.args[0])});await r
  assert.equal(restored.model.form.correctionRequestId,'101');assert.equal(restored.calls.filter(c=>/create|update|submit/i.test(c.name)).length,0)
})
test('unexpected server identity or changed fields keeps unresolved operation',async()=>{
  const h=harness(),p=h.model.saveDraft(false);await tick();const c=(await h.take('createAttendanceCorrectionDraft'));c.reject(Error('lost'));await tick()
  ;(await h.take('getAttendanceCorrectionByClientRequest')).resolve({data:h.row('DRAFT','101',{...c.args[0],clientRequestId:'correction:someoneelse'})});await p
  assert.ok(h.model.recoveryAttempt);assert.equal(h.model.showForm,true);assert.equal(h.model.form.correctionRequestId,null)
})
test('unknown draft update only replays original payload when the untouched base and version match',async()=>{
  const h=harness();h.model.form.correctionRequestId='101';h.model.form.rowVersion=2;h.model.savedBusiness=h.recovery.businessOf({...h.model.form,reason:'base'})
  const p=h.model.saveDraft(false);await tick();const update=(await h.take('updateAttendanceCorrectionDraft'));update.reject(Error('lost'));await tick()
  ;(await h.take('getAttendanceCorrection')).resolve({data:h.row('DRAFT','101',{...h.model.form,reason:'base'})});await p
  assert.ok(h.model.recoveryAttempt);const r=h.model.reconcileCorrection(true);(await h.take('getAttendanceCorrection')).resolve({data:h.row('DRAFT','101',{...h.model.form,reason:'base'})});await tick()
  const replay=(await h.take('updateAttendanceCorrectionDraft'));assert.deepEqual(replay.args,update.args);replay.resolve({data:h.row()});await r;assert.equal(h.model.recoveryAttempt,null)
})
test('unknown update cannot overwrite a changed server version even when old text happens to match',async()=>{
  const h=harness();h.model.form.correctionRequestId='101';h.model.form.rowVersion=2;h.model.savedBusiness=h.recovery.businessOf({...h.model.form,reason:'base'})
  const p=h.model.saveDraft(false);await tick();(await h.take('updateAttendanceCorrectionDraft')).reject(Error('lost'));await tick()
  ;(await h.take('getAttendanceCorrection')).resolve({data:{...h.row('DRAFT','101',{...h.model.form,reason:'base'}),rowVersion:3}});await p
  assert.ok(h.model.recoveryAttempt);assert.equal(h.calls.filter(c=>c.name==='updateAttendanceCorrectionDraft').length,1)
})
test('after reload NOT_FOUND requires the original digest before replay and keeps the same key',async()=>{
  const h=harness(),p=h.model.saveDraft(false);await tick();const c=(await h.take('createAttendanceCorrectionDraft'));c.reject(Error('lost'));await tick();(await h.take('getAttendanceCorrectionByClientRequest')).reject(Error('offline'));await p
  const r=harness(h.storage);r.model.showForm=false;r.model.restoreRecovery();const first=r.model.reconcileCorrection(true);(await r.take('getAttendanceCorrectionByClientRequest')).reject(Error('CORRECTION_CLIENT_REQUEST_NOT_FOUND'));await first
  assert.equal(r.model.restoredInputRequired,true);Object.assign(r.model.form,c.args[0],{reason:'different'})
  await r.model.reconcileCorrection(true);assert.equal(r.calls.filter(c=>c.name==='createAttendanceCorrectionDraft').length,0)
  Object.assign(r.model.form,c.args[0]);const retry=r.model.reconcileCorrection(true);await tick();(await r.take('getAttendanceCorrectionByClientRequest')).reject(Error('CORRECTION_CLIENT_REQUEST_NOT_FOUND'));await tick()
  const replay=(await r.take('createAttendanceCorrectionDraft'));assert.equal(replay.args[0].clientRequestId,c.args[0].clientRequestId);replay.resolve({data:r.row('DRAFT','101',replay.args[0])});await retry
  assert.equal(r.model.recoveryAttempt,null)
})

test('blocked browser storage retains the original in-memory recovery and visibly reports the limitation',async()=>{
  const broken={get:()=>null,set:()=>{throw Error('storage denied')},delete:()=>{throw Error('storage denied')}}
  const h=harness(broken),p=h.model.saveDraft(false);const create=await h.take('createAttendanceCorrectionDraft')
  assert.equal(h.model.recoveryStorageUnavailable,true)
  create.reject(Error('lost'));(await h.take('getAttendanceCorrectionByClientRequest')).reject(Error('offline'));await p
  assert.equal(h.model.recoveryAttempt.clientRequestId,create.args[0].clientRequestId);assert.equal(h.model.showForm,true)
  const source=fs.readFileSync(path.resolve(__dirname,'../src/views/mobile/attendance/MobileAttendanceCorrection.vue'),'utf8')
  assert.ok(source.includes('浏览器未能保存恢复标记'));assert.ok(source.includes('recoveryAttempt.clientRequestId'))
})
