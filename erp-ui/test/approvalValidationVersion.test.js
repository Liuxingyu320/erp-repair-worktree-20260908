const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const Vue = require('vue'), compiler = require('vue-template-compiler'), babel = require('@babel/core')
const scopes = require('../src/utils/uiOperationScope')
const root = path.resolve(__dirname, '..')
const file = 'src/views/approval/manage/components/ValidationPanel.vue'
const tick = async () => { for (let i = 0; i < 4; i++) await Vue.nextTick() }
function deferred() { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b}); return {promise,resolve,reject} }
function load(source, importer) {
  const module = {exports:{}}
  const code = babel.transformSync(source, {babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
  vm.runInNewContext(code, {module,exports:module.exports,require:importer,console,Promise,window:{addEventListener(){},removeEventListener(){}}})
  return module.exports
}
const ui = load(fs.readFileSync(path.join(root,'src/views/approval/manage/components/approvalUi.js'),'utf8'), () => {throw Error('unexpected import')})
function setup() {
  const calls=[],messages=[],env={dept:'8'}
  const api = new Proxy({}, {get(_,name) { return (...args) => { const d=deferred();calls.push({name,args,...d});return d.promise } }})
  const component=load(compiler.parseComponent(fs.readFileSync(path.join(root,file),'utf8')).script.content, name => {
    if(name.startsWith('@/api/approval/')) return api
    if(name==='@/utils/uiOperationScope') return scopes
    if(name==='@/utils/shopContext') return {getSelectedDeptId:()=>env.dept}
    if(name==='./approvalUi') return ui
    if(name==='./ApprovalLoadError') return {}
    throw Error(name)
  }).default
  const store=Vue.observable({getters:{id:'30'},state:{user:{sessionRevision:1}}})
  const page=new Vue({...component,created:[],propsData:{templates:[{templateId:'1',templateName:'采购'}]},beforeCreate(){this.$store=store;this.$modal={msgError:m=>messages.push(['error',m]),msgSuccess:m=>messages.push(['success',m])}}})
  page.$refs.runForm={validate:cb=>cb(true),clearValidate(){}}
  const take=name=>{const c=calls.find(c=>c.name===name&&!c.taken);assert.ok(c,'missing '+name);c.taken=true;return c}
  const ready=async()=>{
    const pending=page.openRunDialog({templateId:'1'})
    take('getApprovalTemplate').resolve({data:{rules:[{ruleId:'11',ruleName:'采购规则'}]}});await tick()
    take('getApprovalRule').resolve({data:{rule:{ruleId:'11',templateId:'1'},versions:[{versionId:'9007199254740997',ruleId:'11',versionNo:3,versionStatus:'DRAFT'}]}})
    await pending;await tick()
  }
  return {page,calls,messages,take,ready,env,store}
}
test('P07 template compiler accepts all changed approval management views',()=>{
  for(const f of [file,'src/views/approval/manage/index.vue','src/views/approval/manage/components/FlowConfiguration.vue','src/views/approval/manage/components/RuleWizard.vue']){
    const part=compiler.parseComponent(fs.readFileSync(path.join(root,f),'utf8'))
    assert.deepEqual(compiler.compile(part.template.content).errors,[])
  }
})
test('P07 one available rule/version is visibly selected; exact long version is submitted',async()=>{
  const h=setup();await h.ready();assert.equal(h.page.runForm.versionId,'9007199254740997');assert.match(h.page.selectedVersionLabel,/V3/)
  const run=h.page.submitRun();await tick();const c=h.take('runApprovalValidation')
  assert.deepEqual(JSON.parse(JSON.stringify(c.args[0])),{versionId:'9007199254740997',validationType:'MANUAL'})
  c.resolve({data:{runId:'77',runStatus:'PASSED'}});await run
  assert.equal(h.messages[0][0],'success');assert.equal(h.page.runVisible,false)
})
test('P07 multiple versions require selection; incoming version preselects only an exact match',async()=>{
  const h=setup(),p=h.page
  const open=p.openRunDialog({templateId:'1',ruleId:'11',versionId:'102'})
  h.take('getApprovalTemplate').resolve({data:{rules:[{ruleId:'11'},{ruleId:'12'}]}});await tick()
  h.take('getApprovalRule').resolve({data:{rule:{ruleId:'11',templateId:'1'},versions:[{versionId:'101',ruleId:'11',versionNo:1},{versionId:'102',ruleId:'11',versionNo:2}]}})
  await open;assert.equal(p.runForm.versionId,'102')
  const reload=p.selectRule();h.take('getApprovalRule').resolve({data:{rule:{ruleId:'11',templateId:'1'},versions:[{versionId:'101',ruleId:'11'},{versionId:'102',ruleId:'11'}]}})
  await reload;assert.equal(p.runForm.versionId,'')
})
test('P07 API filters use templateId/runStatus and stale list responses do not replace later query',async()=>{
  const h=setup(),p=h.page;p.query.templateId='1';p.query.runStatus='FAILED';const a=p.load(),ra=h.take('listApprovalValidationRuns')
  assert.deepEqual(JSON.parse(JSON.stringify(ra.args[0])),{pageNum:1,pageSize:10,templateId:'1',runStatus:'FAILED'})
  p.query.runStatus='PASSED';const b=p.load(),rb=h.take('listApprovalValidationRuns');ra.reject(Error('old'));await a
  assert.equal(p.loading,true);assert.equal(p.validationLoadError,null);rb.resolve({rows:[{runId:'new'}],total:1});await b;assert.equal(p.rows[0].runId,'new')
})
test('P07 changing template rejects delayed rule data and delayed finally',async()=>{
  const h=setup(),p=h.page;const a=p.openRunDialog({templateId:'1'}),ra=h.take('getApprovalTemplate')
  p.runForm.templateId='2';const b=p.selectTemplate(),rb=h.take('getApprovalTemplate');ra.resolve({data:{rules:[{ruleId:'old'}]}});await a
  assert.equal(p.choicesLoading,true);assert.equal(p.rules.length,0);rb.resolve({data:{rules:[]}});await b;assert.match(p.choicesError,/没有审批规则/)
})
test('P07 validation callback after closing/reopening never submits the newer form',async()=>{
  const h=setup();await h.ready();let validate;h.page.$refs.runForm.validate=cb=>{validate=cb}
  const run=h.page.submitRun();h.page.closeRun();h.page.runForm={templateId:'2',ruleId:'22',versionId:'222'}
  validate(true);await run;assert.equal(h.calls.some(c=>c.name==='runApprovalValidation'),false)
})
test('P07 late failed run cannot show an error or stop a new context spinner',async()=>{
  const h=setup();await h.ready();const first=h.page.submitRun();await tick();const request=h.take('runApprovalValidation')
  h.page.closeRun();h.page.runForm.versionId='second';h.page.runLoading=true;request.reject(Error('old failed'));await first
  assert.equal(h.messages.length,0);assert.equal(h.page.runLoading,true)
})
test('P07 ordinary failure retains exact selection and reports one plain message',async()=>{
  const h=setup();await h.ready();const pending=h.page.submitRun();await tick();h.take('runApprovalValidation').reject(Error('暂时不可用'));await pending
  assert.equal(h.page.runVisible,true);assert.equal(h.page.runForm.versionId,'9007199254740997');assert.equal(h.page.runLoading,false)
  assert.deepEqual(h.messages,[['error','暂时不可用']])
})
test('P07 actor/session and organization ABA invalidation prevent old work from returning',async()=>{
  const h=setup();await h.ready();const pending=h.page.submitRun();await tick();const c=h.take('runApprovalValidation')
  h.env.dept='9';h.page.contextChanged();h.env.dept='8';h.page.contextChanged();c.resolve({data:{runId:'stale'}});await pending
  assert.equal(h.messages.length,0);assert.equal(h.page.detailVisible,false)
})
test('P07 detail A late failure cannot stop detail B or leave A issues visible',async()=>{
  const h=setup(),p=h.page;p.detail={issues:[{message:'old'}]};const a=p.openDetail({runId:'1'}),ra=h.take('getApprovalValidationRun')
  const b=p.openDetail({runId:'2'}),rb=h.take('getApprovalValidationRun');ra.reject(Error('old'));await a;assert.equal(p.detailLoading,true);assert.equal(p.detail,null)
  rb.resolve({data:{run:{runId:'2'},issues:[]}});await b;assert.equal(p.detail.runId,'2');assert.equal(p.detailError,'')
})
