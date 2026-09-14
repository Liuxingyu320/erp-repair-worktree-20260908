const test=require('node:test')
const assert=require('node:assert/strict')
const {harness,flush,type}=require('./helpers/leaveBalanceHarness')
const FILE='src/views/oa/attendance/components/LeaveBalanceRuleManagement.vue'
const company={legalEntityId:'20',legalEntityName:'测试公司',legalEntityCode:'TEST'}
const config=()=>({leaveCategory:'ANNUAL',calculation:'FIXED',unit:'DAYS',minutesPerDay:'480',tenureBasis:'ENTRY',tenureAt:'PERIOD_START',proration:'NONE',grantTiming:'UPFRONT',rounding:'DOWN',grantStepMinutes:'1',expiryMonthsAfterYear:0,carryLimit:'0',carryExpiryMonths:0,amount:'5'})
const rule=(overrides={})=>({ruleId:'41',rowVersion:'2',familyId:'41',ownerDeptId:'100',legalEntityId:'20',leaveTypeId:'1',name:'公司配置',locationCode:'WORK-A',effectiveFrom:'2026-01-01',effectiveTo:'2026-12-31',priority:1,version:1,status:'DRAFT',config:config(),tiers:[],...overrides})
function reads(h,rules=[],locations=[]) {h.resolve('/options/companies',[company]);h.resolve('/options/types',[type()]);h.resolve('/balance/rules',rules,'get');h.resolve('/balance/locations',locations,'get')}
async function ready(rules=[]) {const h=harness(FILE);reads(h,rules);await flush();return h}
function populate(h,overrides={}) {h.c.newRule();Object.assign(h.c.form,rule({ruleId:'',rowVersion:'',...overrides}))}
test('new policy starts with no invented quota, daily minutes or expiry numbers',async()=>{const h=await ready();h.c.newRule();assert.equal(h.c.form.config.amount,'');assert.equal(h.c.form.config.minutesPerDay,'');assert.equal(h.c.form.config.expiryMonthsAfterYear,'');assert.throws(()=>h.c.rulePayload());assert.ok(h.render());h.c.$destroy()})
test('structured policy form builds exact decimals and explicit choices',async()=>{const h=await ready();populate(h);h.c.form.config.amount='5.125000';const body=h.c.rulePayload();assert.equal(body.ownerDeptId,'100');assert.equal(body.legalEntityId,'20');assert.equal(body.config.amount,'5.125000');assert.equal(body.config.minutesPerDay,'480');assert.equal(body.config.expiryMonthsAfterYear,0);h.c.$destroy()})
test('compensatory rule cannot be submitted with fixed or tenure grants',async()=>{const h=await ready();populate(h);h.c.form.config.leaveCategory='COMPENSATORY';assert.throws(()=>h.c.rulePayload(),/主管核定/);h.c.changedSetting('leaveCategory');assert.equal(h.c.form.config.calculation,'SOURCE_ONLY');assert.equal(h.c.form.config.amount,'');assert.equal(h.c.rulePayload().config.amount,undefined);h.c.$destroy()})
test('same-tick organization change blocks saving a form from previous context',async()=>{const h=await ready();populate(h);h.state.dept='200';await h.c.saveRule();assert.equal(h.requests.length,0);h.c.$destroy()})
test('draft save never publishes and adopts server ID and version',async()=>{
 const h=await ready();populate(h);const action=h.c.saveRule(),r=h.take('/balance/rules','post');assert.equal(r.options.data.previousRuleId,undefined);r.resolve({data:rule()});await flush();reads(h,[rule()]);await action;assert.equal(h.c.ruleOpen,false);assert.equal(h.c.form.ruleId,'41');assert.equal(h.storage.size,0);assert.equal(h.requests.length,0);h.c.$destroy()
})
test('editing published rule creates a next version without changing original ID',async()=>{
 const published=rule({status:'PUBLISHED'}),h=await ready([published]);const action=h.c.editRule(h.c.rules[0]);h.resolve('/rules/41',published);await action;assert.equal(h.c.form.ruleId,'');assert.equal(h.c.form.previousRuleId,'41');const body=h.c.rulePayload();assert.equal(body.previousRuleId,'41');assert.equal(h.c.rules[0].status,'PUBLISHED');h.c.$destroy()
})
test('publish reads current rule then shows all amounts and expiry settings before write',async()=>{
 const h=await ready([rule()]);const action=h.c.publish(h.c.rules[0]);h.resolve('/rules/41',rule());await flush();assert.equal(h.requests.length,0);assert.equal(h.confirms.length,1);const prompt=h.confirms.shift();assert.match(prompt.args[0],/固定额度：5 天/);assert.match(prompt.args[0],/年末后保留月数.*0/);assert.match(prompt.args[0],/每天折合分钟.*480/);prompt.resolve();await flush();const r=h.take('/rules/41/publish');assert.equal(r.options.data.rowVersion,'2');r.resolve({data:rule({status:'PUBLISHED',rowVersion:'3'})});await flush();reads(h,[rule({status:'PUBLISHED',rowVersion:'3'})]);await action;h.c.$destroy()
})
test('changed rule version or account during publish confirmation blocks post',async()=>{
 const h=await ready([rule()]);const first=h.c.publish(h.c.rules[0]);h.resolve('/rules/41',rule({rowVersion:'3'}));await first;assert.equal(h.confirms.length,0);assert.match(h.c.error,/变化/)
 const second=h.c.publish(h.c.rules[0]);h.resolve('/rules/41',rule());await flush();h.store.getters.id='8';h.confirms.shift().resolve();await second;assert.equal(h.requests.filter(r=>r.options.method!=='get').length,0);h.c.$destroy()
})
test('unknown creation retains request evidence and blocks duplicate creation',async()=>{
 const h=await ready();populate(h);const action=h.c.saveRule();h.take('/balance/rules','post').reject(Error('timeout'));await action;assert.equal(h.c.unknownWrite,'规则保存');assert.equal(h.storage.size,1);await h.c.saveRule();assert.equal(h.requests.length,0);h.c.$destroy()
})
test('unknown create is not mistaken for a pre-existing identical rule',async()=>{
 const existing=rule(),h=await ready([existing]);populate(h);const action=h.c.saveRule();h.take('/balance/rules','post').reject(Error('timeout'));await action;const reload=h.c.load();reads(h,[existing]);await reload;assert.ok(h.c.unknownWrite);assert.equal(h.storage.size,1);h.c.$destroy()
})
test('unknown create resolves only to a newly observed matching complete configuration',async()=>{
 const h=await ready();populate(h);const action=h.c.saveRule();h.take('/balance/rules','post').reject(Error('timeout'));await action
 const wrong=h.c.load();reads(h,[rule({config:{...config(),amount:'9'}})]);await wrong;assert.ok(h.c.unknownWrite)
 const correct=h.c.load();reads(h,[rule()]);await correct;assert.equal(h.c.unknownWrite,'');assert.equal(h.storage.size,0);h.c.$destroy()
})
test('storage failure prevents rule creation while keeping form editable',async()=>{const h=await ready();populate(h);h.state.storageFails=true;await h.c.saveRule();assert.equal(h.requests.length,0);assert.equal(h.c.unknownWrite,'');assert.equal(h.c.ruleOpen,true);h.c.$destroy()})
test('mapping edit sends prior row version and keeps organization fixed',async()=>{
 const mapping={mappingId:'51',rowVersion:'7',ownerDeptId:'100',legalEntityId:'20',workLocation:'某工作地',locationCode:'WORK-A',reason:'原映射'},h=harness(FILE);reads(h,[],[mapping]);await flush();h.c.editLocation(h.c.locations[0]);h.c.locationForm.reason='HR复核';const action=h.c.saveLocation(),r=h.take('/locations/51');assert.equal(r.options.data.rowVersion,'7');assert.equal(r.options.data.ownerDeptId,'100');assert.equal(r.options.data.reason,'HR复核');r.resolve({data:{...mapping,rowVersion:'8',reason:'HR复核'}});await flush();reads(h,[],[{...mapping,rowVersion:'8',reason:'HR复核'}]);await action;assert.equal(h.c.locationOpen,false);h.c.$destroy()
})
test('late policy list response after silent scope change does not populate new context',async()=>{const h=harness(FILE);h.state.dept='200';reads(h,[rule()]);await flush();assert.equal(h.c.rules.length,0);assert.equal(h.c.companies.length,0);h.c.$destroy()})

test('unknown SOURCE_ONLY save reconciles nullable persisted configuration without accepting different priority',async()=>{
 const h=await ready();populate(h);h.c.form.config.leaveCategory='COMPENSATORY';h.c.changedSetting('leaveCategory');const saved=h.c.rulePayload(),action=h.c.saveRule();h.take('/balance/rules','post').reject(Error('timeout'));await action;const server=rule({...saved,ruleId:'42',config:{...saved.config,ruleId:'42',amount:null},tiers:[]});const wrong=h.c.load();reads(h,[{...server,priority:2}]);await wrong;assert.ok(h.c.unknownWrite);const correct=h.c.load();reads(h,[server]);await correct;assert.equal(h.c.unknownWrite,'');assert.equal(h.storage.size,0);h.c.$destroy()
})

test('publish refuses a different rule identity even when version and status match',async()=>{
 const h=await ready([rule()]),action=h.c.publish(h.c.rules[0]);h.resolve('/rules/41',rule({ruleId:'42'}));await action;assert.equal(h.confirms.length,0);assert.equal(h.requests.length,0);assert.match(h.c.error,/变化/);h.c.$destroy()
})
test('unknown next-version save cannot reconcile a different rule family with identical policy',async()=>{
 const published=rule({status:'PUBLISHED'}),h=await ready([published]),edit=h.c.editRule(h.c.rules[0]);h.resolve('/rules/41',published);await edit;const save=h.c.saveRule();h.take('/balance/rules','post').reject(Error('timeout'));await save;assert.equal(h.c.unknownPayload.familyId,'41');const wrong=h.c.load();reads(h,[published,rule({ruleId:'52',familyId:'51',version:2})]);await wrong;assert.ok(h.c.unknownWrite);const correct=h.c.load();reads(h,[published,rule({ruleId:'52',familyId:'41',version:2})]);await correct;assert.equal(h.c.unknownWrite,'');h.c.$destroy()
})
