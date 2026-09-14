const test=require('node:test')
const assert=require('node:assert/strict')
const {harness,flush,type}=require('./helpers/leaveBalanceHarness')
const FILE='src/views/mobile/attendance/MobileAttendanceLeave.vue'
async function ready(unitMode='HALF_DAY'){
 const h=harness(FILE);h.resolve('/leave/types',[type('1',{unitMode})]);h.resolve('/leave/my',[]);await flush();h.c.openNew();Object.assign(h.c.form,{leaveTypeId:'1',startTime:'2026-09-13T09:00',endTime:'2026-09-14T18:00',reason:'申请原因'});return h
}
const detail=(h,overrides={})=>({leaveRequestId:'31',clientRequestId:h.c.form.clientRequestId,rowVersion:'1',status:'DRAFT',leaveTypeId:'1',startTime:'2026-09-13T09:00',endTime:'2026-09-14T18:00',reason:'申请原因',requestedDays:'1.5',attachments:[],...overrides})
test('DAY and HALF_DAY require employee entered days while legacy MINUTE stays optional',async()=>{
 for(const mode of ['DAY','HALF_DAY','MINUTE']){const h=await ready(mode);assert.equal(h.c.validateForm(),mode==='MINUTE'?'':'请填写申请天数');h.c.form.requestedDays='0';assert.match(h.c.validateForm(),/正数/);h.c.form.requestedDays=mode==='DAY'?'1':'0.5';assert.equal(h.c.validateForm(),'');h.c.$destroy()}
})
test('requested days are submitted independently of natural start/end span',async()=>{
 const h=await ready();h.c.form.requestedDays='1.5';const action=h.c.saveDraft(false),r=h.take('/leave/drafts','post');assert.equal(r.options.data.requestedDays,'1.5');assert.equal(r.options.data.startTime,'2026-09-13T09:00');assert.equal(r.options.data.endTime,'2026-09-14T18:00');r.resolve({data:detail(h)});await flush();h.resolve('/leave/my',[]);await action;assert.equal(h.c.form.requestedDays,'1.5');h.c.$destroy()
})
test('days changed while save is pending remain unsaved and block automatic submit',async()=>{
 const h=await ready();h.c.form.requestedDays='1.5';const action=h.c.saveDraft(true),r=h.take('/leave/drafts','post');h.c.form.requestedDays='2';r.resolve({data:detail(h)});await action;assert.equal(h.c.form.requestedDays,'2');assert.equal(h.c.unsavedChanges,true);assert.equal(h.requests.filter(r=>r.options.url.endsWith('/submit')).length,0);h.c.$destroy()
})
test('missing or altered server days cannot be accepted as saved request',async()=>{
 const h=await ready();const identity={leaveRequestId:'31',payload:{requestedDays:'1.5'}}
 assert.throws(()=>h.c.requireLeaveDetail(detail(h,{requestedDays:null}),identity),/天数/)
 assert.throws(()=>h.c.requireLeaveDetail(detail(h,{requestedDays:'2'}),identity),/天数/)
 assert.equal(h.c.requireLeaveDetail(detail(h,{requestedDays:1.5}),identity).requestedDays,1.5)
 h.c.$destroy()
})
test('six-decimal numeric response is compared exactly instead of scientific notation mismatch',async()=>{
 const h=await ready('MIXED');h.c.form.requestedDays='0.000001';const operation=h.c.editorOperation();const result=detail(h,{requestedDays:0.000001});assert.doesNotThrow(()=>h.c.requireLeaveDetail(result,operation));h.c.$destroy()
})
test('same-type edit echoes deep frozen policy snapshot and does not infer region',async()=>{
 const h=await ready();const policy={schemaVersion:1,leaveTypeId:'1',ruleId:'41',ruleVersion:2,mappingId:'51',mappingVersion:'3',minutesPerDay:'480',amountUnit:'DAYS'}
 const edit=h.c.editRow({leaveRequestId:'31'});h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:JSON.parse(JSON.stringify(policy))}));await edit
 const save=h.c.saveDraft(false),r=h.take('/leave/31/draft','put');assert.deepEqual(JSON.parse(JSON.stringify(r.options.data.quotaPolicySnapshot)),policy);assert.equal(Object.hasOwn(r.options.data,'province'),false);h.c.form.quotaPolicySnapshot.ruleVersion=9;assert.equal(r.options.data.quotaPolicySnapshot.ruleVersion,2);r.resolve({data:detail(h,{rowVersion:'2',quotaPolicySnapshot:policy})});await flush();h.resolve('/leave/my',[]);await save;h.c.$destroy()
})
test('changing leave type omits old policy and lets backend match the new policy',async()=>{
 const h=await ready();const edit=h.c.editRow({leaveRequestId:'31'});h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:{ruleId:'41',ruleVersion:2}}));await edit
 h.c.form.leaveTypeId='2';const save=h.c.saveDraft(false),r=h.take('/leave/31/draft','put');assert.equal(Object.hasOwn(r.options.data,'quotaPolicySnapshot'),false);r.resolve({data:detail(h,{rowVersion:'2',leaveTypeId:'2'})});await flush();h.resolve('/leave/my',[]);await save;h.c.$destroy()
})
test('legacy minute draft omits empty requested days and accepts old response shape',async()=>{
 const h=await ready('MINUTE'),action=h.c.saveDraft(false),r=h.take('/leave/drafts','post');assert.equal(Object.hasOwn(r.options.data,'requestedDays'),false);const response=detail(h);delete response.requestedDays;r.resolve({data:response});await flush();h.resolve('/leave/my',[]);await action;assert.equal(h.c.unsavedChanges,false);h.c.$destroy()
})

test('timed-out days update can replay original payload only after recovery matches authoritative old draft',async()=>{
 const h=await ready(),edit=h.c.editRow({leaveRequestId:'31'});h.resolve('/leave/31',detail(h));await edit;h.c.form.requestedDays='2';const first=h.c.saveDraft(false),r=h.take('/leave/31/draft','put'),original=JSON.parse(JSON.stringify(r.options.data));r.reject(Error('timeout'));await flush();h.resolve('/leave/31',detail(h));await first;assert.ok(h.c.saveAttempt);assert.equal(h.c.form.requestedDays,'2');assert.equal(h.requests.length,0);
 const retry=h.c.saveDraft(false);h.resolve('/leave/31',detail(h,{rowVersion:'2'}));await flush();const replay=h.take('/leave/31/draft','put');assert.deepEqual(JSON.parse(JSON.stringify(replay.options.data)),{...original,rowVersion:'2'});replay.resolve({data:detail(h,{rowVersion:'3',requestedDays:'2'})});await flush();h.resolve('/leave/my',[]);await retry;assert.equal(h.c.saveAttempt,null);assert.equal(h.c.unsavedChanges,false);h.c.$destroy()
})
test('recovery never overwrites days changed independently on server',async()=>{
 const h=await ready(),edit=h.c.editRow({leaveRequestId:'31'});h.resolve('/leave/31',detail(h));await edit;h.c.form.requestedDays='2';const first=h.c.saveDraft(false);h.take('/leave/31/draft','put').reject(Error('timeout'));await flush();h.resolve('/leave/31',detail(h,{requestedDays:'3'}));await first;const retry=h.c.saveDraft(false);h.resolve('/leave/31',detail(h,{requestedDays:'3'}));await retry;assert.ok(h.c.saveAttempt);assert.equal(h.c.form.requestedDays,'2');assert.equal(h.requests.length,0);assert.match(h.c.error,/已变化/);h.c.$destroy()
})

test('configured whole-day and half-day steps are enforced before any save while MIXED keeps exact decimals',async()=>{
 for(const [mode,invalid,valid] of [['DAY','1.5','2.000000'],['HALF_DAY','1.25','1.500000'],['MIXED',null,'0.123456']]){const h=await ready(mode);if(invalid){h.c.form.requestedDays=invalid;assert.match(h.c.validateForm(),/须按/);await h.c.saveDraft(false);assert.equal(h.requests.length,0)}h.c.form.requestedDays=valid;assert.equal(h.c.validateForm(),'');h.c.$destroy()}
})

const fullPolicy=(overrides={})=>({schemaVersion:1,leaveTypeId:'1',leaveTypeVersion:'4',ruleId:'41',ruleVersion:2,mappingId:'51',mappingVersion:'3',legalEntityId:'20',unitMode:'HALF_DAY',amountUnit:'DAYS',displayUnit:'DAYS',balanceRequired:true,minutesPerDay:'480',...overrides})
async function oldPolicyDraft(){const h=await ready();const edit=h.c.editRow({leaveRequestId:'31'});h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:fullPolicy(),quotaUnits:'720000000'}));await edit;return h}
const preview=(h,overrides={})=>({...detail(h),userId:'7',shopId:'100',totalMinutes:1980,quotaUnits:'630000000',quotaPolicySnapshot:fullPolicy({ruleVersion:3,minutesPerDay:'420'}),...overrides})
test('current policy preview freezes form and requires explicit review before same draft save echoes new policy',async()=>{
 const h=await oldPolicyDraft(),read=h.c.reviewCurrentPolicy(),r=h.take('/leave/31/policy-preview','post');assert.equal(r.options.data.rowVersion,'1');assert.equal(r.options.data.requestedDays,'1.5');assert.equal(h.c.busy,true);assert.equal(h.c.submitLocked,true);assert.equal(r.options.data.quotaPolicySnapshot,undefined);r.resolve({data:preview(h)});await read;assert.equal(h.c.form.quotaPolicySnapshot.ruleVersion,2);assert.equal(h.c.busy,true);assert.ok(h.render());await h.c.saveDraft(true);assert.equal(h.requests.length,0)
 h.c.confirmPolicyReview();assert.equal(h.c.form.quotaPolicySnapshot.ruleVersion,3);assert.equal(h.c.unsavedChanges,true);assert.equal(h.requests.length,0);const save=h.c.saveDraft(false),write=h.take('/leave/31/draft','put');assert.equal(write.options.data.rowVersion,'1');assert.equal(write.options.data.quotaPolicySnapshot.ruleVersion,3);write.resolve({data:detail(h,{rowVersion:'2',quotaPolicySnapshot:preview(h).quotaPolicySnapshot})});await flush();h.resolve('/leave/my',[]);await save;assert.equal(h.c.reviewedPolicy,null);assert.equal(h.c.unsavedChanges,false);h.c.$destroy()
})
test('cancelled or failed policy preview never replaces original policy or writes draft',async()=>{
 for(const fail of [false,true]){const h=await oldPolicyDraft(),read=h.c.reviewCurrentPolicy(),r=h.take('/leave/31/policy-preview');if(fail)r.reject(Error('timeout'));else r.resolve({data:preview(h)});await read;if(!fail)h.c.cancelPolicyReview();assert.equal(h.c.form.quotaPolicySnapshot.ruleVersion,2);assert.equal(h.c.reviewedPolicy,null);assert.equal(h.c.busy,false);assert.equal(h.c.submitLocked,false);assert.equal(h.requests.length,0);h.c.$destroy()}
})
test('policy preview rejects wrong identity amount version malformed policy and late context',async()=>{
 for(const drift of ['leaveRequestId','userId','shopId','leaveTypeId','rowVersion','requestedDays','reason','policy','scope']){const h=await oldPolicyDraft(),read=h.c.reviewCurrentPolicy(),r=h.take('/leave/31/policy-preview'),reply=preview(h);if(drift==='policy')reply.quotaPolicySnapshot.leaveTypeVersion=9007199254740992;else if(drift==='scope')h.state.dept='200';else reply[drift]='99';r.resolve({data:reply});await read;assert.equal(h.c.policyReview,null,drift);assert.equal(h.c.form.quotaPolicySnapshot.ruleVersion,2,drift);assert.equal(h.requests.length,0);h.c.$destroy()}
})
test('policy confirmation rechecks version and complete business input even before UI context watchers',async()=>{
 for(const drift of ['requestedDays','reason','rowVersion','scope']){const h=await oldPolicyDraft(),read=h.c.reviewCurrentPolicy();h.resolve('/leave/31/policy-preview',preview(h));await read;if(drift==='scope')h.state.dept='200';else h.c.form[drift]='99';h.c.confirmPolicyReview();assert.equal(h.c.form.quotaPolicySnapshot.ruleVersion,2);assert.equal(h.c.reviewedPolicy,null);assert.equal(h.requests.length,0);h.c.$destroy()}
})
test('editing input after confirmed policy requires another bound preview before save',async()=>{
 const h=await oldPolicyDraft(),read=h.c.reviewCurrentPolicy();h.resolve('/leave/31/policy-preview',preview(h));await read;h.c.confirmPolicyReview();h.c.form.requestedDays='2';await h.c.saveDraft(false);assert.equal(h.requests.length,0);assert.match(h.c.error,/重新核对/);h.c.$destroy()
})
test('unknown policy save cannot accept unchanged business with old snapshot and replays exact policy only after authoritative old base',async()=>{
 const h=await oldPolicyDraft(),read=h.c.reviewCurrentPolicy();h.resolve('/leave/31/policy-preview',preview(h));await read;h.c.confirmPolicyReview();const save=h.c.saveDraft(false),r=h.take('/leave/31/draft');r.reject(Error('timeout'));await flush();h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:fullPolicy()}));await save;assert.ok(h.c.saveAttempt);assert.equal(h.c.form.quotaPolicySnapshot.ruleVersion,3);const retry=h.c.saveDraft(false);h.resolve('/leave/31',detail(h,{rowVersion:'2',quotaPolicySnapshot:fullPolicy()}));await flush();const replay=h.take('/leave/31/draft');assert.equal(replay.options.data.rowVersion,'2');assert.equal(replay.options.data.quotaPolicySnapshot.ruleVersion,3);replay.resolve({data:detail(h,{rowVersion:'3',quotaPolicySnapshot:preview(h).quotaPolicySnapshot})});await flush();h.resolve('/leave/my',[]);await retry;assert.equal(h.c.saveAttempt,null);h.c.$destroy()
})
test('definite policy change rejection unlocks preview only after reread confirms exact original draft',async()=>{
 const h=await oldPolicyDraft(),save=h.c.saveDraft(false);h.take('/leave/31/draft').reject(Object.assign(Error('政策已变化，请重新核对'),{response:{status:200,data:{code:500,msg:'政策已变化，请重新核对'}}}));await flush();h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:fullPolicy()}));await save;assert.equal(h.c.saveAttempt,null);assert.match(h.c.error,/政策已变化/);const read=h.c.reviewCurrentPolicy();h.resolve('/leave/31/policy-preview',preview(h));await read;assert.ok(h.c.policyReview);h.c.cancelPolicyReview();h.c.$destroy()
})

test('rule mode drift can preview without stale day requirements and confirmed current MINUTE mode controls save',async()=>{
 const h=await oldPolicyDraft();h.c.leaveTypes[0].unitMode='DAY';h.c.form.quotaPolicySnapshot.unitMode='DAY';h.c.form.requestedDays='';const read=h.c.reviewCurrentPolicy(),r=h.take('/leave/31/policy-preview');assert.equal(r.options.data.requestedDays,undefined);r.resolve({data:preview(h,{requestedDays:null,quotaUnits:'1980000000',quotaPolicySnapshot:fullPolicy({ruleVersion:3,unitMode:'MINUTE',amountUnit:'MINUTES',displayUnit:'MINUTES',minutesPerDay:null})})});await read;h.c.confirmPolicyReview();assert.equal(h.c.requestedDaysUnitMode,'MINUTE');assert.equal(h.c.validateForm(),'');const save=h.c.saveDraft(false),write=h.take('/leave/31/draft');assert.equal(write.options.data.requestedDays,undefined);write.resolve({data:detail(h,{rowVersion:'2',requestedDays:null,quotaPolicySnapshot:JSON.parse(JSON.stringify(h.c.form.quotaPolicySnapshot))})});await flush();h.resolve('/leave/my',[]);await save;assert.equal(h.c.requestedDaysUnitMode,'MINUTE');h.c.$destroy()
})
test('definite rejection on unknown-save replay rereads original base then permits a fresh policy preview',async()=>{
 const h=await oldPolicyDraft();h.c.form.requestedDays='2';const first=h.c.saveDraft(false);h.take('/leave/31/draft').reject(Error('timeout'));await flush();h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:fullPolicy()}));await first;assert.ok(h.c.saveAttempt);const retry=h.c.saveDraft(false);h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:fullPolicy()}));await flush();h.take('/leave/31/draft').reject(Object.assign(Error('政策已变化'),{response:{status:200,data:{code:500,msg:'政策已变化'}}}));await flush();assert.ok(h.c.saveAttempt);h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:fullPolicy()}));await retry;assert.equal(h.c.saveAttempt,null);assert.equal(h.c.form.requestedDays,'2');const read=h.c.reviewCurrentPolicy();h.resolve('/leave/31/policy-preview',preview(h,{requestedDays:'2',quotaUnits:'840000000'}));await read;assert.ok(h.c.policyReview);h.c.cancelPolicyReview();h.c.$destroy()
})

test('new invalid input cannot block reconciliation of a previously confirmed policy save and remains unsaved after recovery',async()=>{
 const h=await oldPolicyDraft(),read=h.c.reviewCurrentPolicy();h.resolve('/leave/31/policy-preview',preview(h));await read;h.c.confirmPolicyReview();const first=h.c.saveDraft(false);h.take('/leave/31/draft').reject(Error('timeout'));await flush();h.resolve('/leave/31',detail(h,{quotaPolicySnapshot:fullPolicy()}));await first;h.c.form.reason='';h.c.form.requestedDays='-1';const retry=h.c.saveDraft(true);h.resolve('/leave/31',detail(h,{rowVersion:'2',quotaPolicySnapshot:preview(h).quotaPolicySnapshot}));await retry;assert.equal(h.c.saveAttempt,null);assert.equal(h.c.form.reason,'');assert.equal(h.c.form.requestedDays,'-1');assert.equal(h.c.unsavedChanges,true);assert.equal(h.requests.length,0);h.c.$destroy()
})
