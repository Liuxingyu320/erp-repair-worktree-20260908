const test = require('node:test'), assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const Vue = require('vue'), compiler = require('vue-template-compiler'), babel = require('@babel/core')
const state = require('../src/views/monitor/job/deletionState')
const scopes = require('../src/utils/uiOperationScope')
const file = path.resolve(__dirname, '../src/views/monitor/job/index.vue')
const tick = async () => { for (let i = 0; i < 3; i++) await Vue.nextTick() }
function deferred() { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b}); return {promise,resolve,reject} }
function setup() {
 const calls=[],messages=[],confirmation=deferred()
 const api = new Proxy({}, {get(_,name) {return (...args) => {const d=deferred();calls.push({name,args,...d});return d.promise}}})
 const module={exports:{}},script=compiler.parseComponent(fs.readFileSync(file,'utf8')).script.content
 const code=babel.transformSync(script,{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
 vm.runInNewContext(code,{module,exports:module.exports,require:name=>{
  if(name==='@/api/monitor/job')return api
  if(name==='@/utils/uiOperationScope')return scopes
  if(name==='./deletionState')return state
  if(name==='@/utils/cronExpression')return require('../src/utils/cronExpression')
  return {}
 },console,Promise})
 const store=Vue.observable({state:{user:{id:'30',sessionRevision:1}}})
 const page=new Vue({...module.exports.default,created:[],beforeCreate(){this.$store=store;this.$modal={confirm:()=>confirmation.promise,msgError:m=>messages.push(m)}}})
 page.loading=false;let reloads=0;page.getList=async()=>{reloads++}
 const take=name=>{const c=calls.find(c=>c.name===name&&!c.taken);assert.ok(c,'missing '+name);c.taken=true;return c}
 return {page,store,calls,messages,confirmation,take,reloads:()=>reloads}
}
test('P04 selection snapshot and exact revisions survive edits while confirmation is open',async()=>{
 const h=setup(),p=h.page;p.handleSelectionChange([{jobId:'9007199254740997',revision:'first'}])
 const run=p.handleDelete();p.handleSelectionChange([{jobId:'8',revision:'new'}]);h.confirmation.resolve();await tick()
 const request=h.take('deleteJobSnapshot');assert.deepEqual(JSON.parse(JSON.stringify(request.args[0].jobs)),[{jobId:'9007199254740997',revision:'first'}])
 request.resolve({data:{batchId:request.args[0].batchId,status:'PENDING',items:[{jobId:'9007199254740997',status:'PENDING'}]}});await run
 assert.equal(p.deletionReceipt.status,'PENDING');assert.match(p.deletionMessage,/正在同步/);assert.equal(p.actionLoading,false)
})
test('P04 double click cannot submit two deletions; rejected precondition allows refreshed selection',async()=>{
 const h=setup(),p=h.page,run=p.handleDelete({jobId:'7',revision:'r1'});p.handleDelete({jobId:'8',revision:'r2'})
 h.confirmation.resolve();await tick();const request=h.take('deleteJobSnapshot');request.reject(Object.assign(Error('版本已变化'),{code:409}));await run
 assert.equal(h.calls.filter(c=>c.name==='deleteJobSnapshot').length,1);assert.equal(p.deletionReceipt.status,'REJECTED');assert.equal(p.deletionPending,false)
 assert.deepEqual(h.messages,['版本已变化']);assert.equal(h.reloads(),1)
})
test('P04 lost reply queries the same batch and never replays or calls a new delete',async()=>{
 const h=setup(),p=h.page,run=p.handleDelete({jobId:'7',revision:'r1'});h.confirmation.resolve();await tick()
 const request=h.take('deleteJobSnapshot');request.reject(Error('connection reset'));await tick();const query=h.take('getJobDeletionReceipt')
 assert.equal(query.args[0],request.args[0].batchId);query.resolve({data:{batchId:query.args[0],status:'NOT_OBSERVED',items:[]}});await run
 assert.equal(p.deletionPending,true);assert.match(p.deletionMessage,/原请求可能/);await p.handleDelete({jobId:'7',revision:'r1'})
 assert.equal(h.calls.filter(c=>c.name==='deleteJobSnapshot').length,1)
})
test('P04 retrying scheduler state is visible and query-only completion unlocks future deletions',async()=>{
 const h=setup(),p=h.page,id=state.newDeletionBatchId();p.deletionReceipt={batchId:id,status:'RETRYING',items:[]}
 assert.match(p.deletionMessage,/系统将继续重试同步/);const query=p.queryDeletion();h.take('getJobDeletionReceipt').resolve({data:{batchId:id,status:'COMPLETED',items:[]}});await query
 assert.equal(p.deletionPending,false);assert.match(p.deletionMessage,/当前调度器已同步/);assert.equal(p.deletionQuerying,false)
})
test('P04 account ABA invalidates confirmation and old failure/finally cannot touch a new account',async()=>{
 const h=setup(),p=h.page,run=p.handleDelete({jobId:'7',revision:'r1'})
 h.store.state.user.id='31';h.store.state.user.sessionRevision++;await tick();h.store.state.user.id='30';h.store.state.user.sessionRevision++;await tick()
 h.confirmation.resolve();await run;assert.equal(h.calls.length,0)
 const b=setup(),old=b.page.handleDelete({jobId:'7',revision:'r1'});b.confirmation.resolve();await tick();const request=b.take('deleteJobSnapshot')
 b.store.state.user.id='31';b.store.state.user.sessionRevision++;await tick();b.page.actionLoading=true
 request.reject(Error('old error'));await old;assert.equal(b.page.actionLoading,true);assert.deepEqual(b.messages,[])
})
test('P04 refresh restores only this actor deletion receipt, never credentials or an automatic retry',()=>{
 const map=new Map(),storage={setItem:(k,v)=>map.set(k,v),getItem:k=>map.get(k)},id=state.newDeletionBatchId()
 state.saveDeletionReceipt('30',{batchId:id,status:'PENDING',items:[]},storage)
 assert.equal(state.loadDeletionReceipt('30',storage).batchId,id);assert.equal(state.loadDeletionReceipt('31',storage),null)
 assert.deepEqual(compiler.compile(compiler.parseComponent(fs.readFileSync(file,'utf8')).template.content).errors,[])
})
