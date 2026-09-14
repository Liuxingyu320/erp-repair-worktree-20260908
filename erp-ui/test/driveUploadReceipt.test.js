const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const Vue = require('vue'), compiler = require('vue-template-compiler'), babel = require('@babel/core')
const receipt = require('../src/views/drive/uploadReceipt')
const desktop = require('../src/views/drive/driveState')
const mobile = require('../src/views/mobile/drive/mobileDriveState')
const root = path.resolve(__dirname, '..')
const file = { name: 'receipt.pdf', size: 12 }
const tick = async () => { for (let i = 0; i < 3; i++) await Vue.nextTick() }
function deferred() { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b}); return {promise,resolve,reject} }
function setup(surface) {
  const calls=[], messages=[]
  const api = new Proxy({}, {get(_, name) {return (...args) => {const d=deferred();calls.push({name,args,...d});return d.promise}}})
  const filename = 'src/views/' + (surface === 'pc' ? 'drive' : 'mobile/drive') + '/index.vue'
  const part = compiler.parseComponent(fs.readFileSync(path.join(root, filename),'utf8'))
  const module = {exports:{}}
  const code = babel.transformSync(part.script.content,{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
  vm.runInNewContext(code,{module,exports:module.exports,require:name=>{
    if(name==='@/api/drive') return api
    if(name.endsWith('uploadReceipt')) return receipt
    if(name.endsWith('mobileDriveState')) return mobile
    if(name.endsWith('driveState')) return desktop
    if(name.endsWith('.vue')||name==='file-saver') return {}
    throw Error(name)
  },AbortController,Promise,console,clearTimeout,setTimeout})
  const store = Vue.observable({state:{user:{id:'30',sessionRevision:1}}})
  const page=new Vue({...module.exports.default,created:[],beforeCreate(){this.$store=store;this.$message={info:m=>messages.push(m)}}})
  page.activeSpaceId='8';page.parentId='0';page.spaces=[{spaceId:'8',canWrite:true}]
  const loadSpaces = page.loadSpaces.bind(page)
  page.loadSpaces=async()=>{};page.loadNodes=async()=>{}
  const take=name=>{const call=calls.find(c=>c.name===name&&!c.taken);assert.ok(call,'missing '+name);call.taken=true;return call}
  const start=()=>{
    if(surface==='pc') {const item=desktop.createUploadItem(file,'8','0','root');page.uploadItems=[item];return page.startUpload(item)}
    return page.uploadSelectedFile(file)
  }
  const state=()=>surface==='pc'?page.uploadItems[0]:page.uploadState
  return {page,store,calls,take,start,state,messages,loadSpaces}
}
test('P03 receipts preserve exact IDs, never turn unknown/in-flight responses into retry permission',()=>{
 const item={operationId:receipt.newUploadOperationId(),file,status:'uploading'}
 for(const status of ['PROCESSING','CLEANING','NOT_OBSERVED','REVIEW_REQUIRED']) {
  assert.equal(receipt.applyUploadReceipt(item,{operationId:item.operationId,status}).status,'pending')
 }
 assert.equal(receipt.applyUploadReceipt(item,{operationId:'different',status:'SUCCEEDED',nodeId:'1'}).status,'pending')
 assert.equal(receipt.applyUploadReceipt(item,{operationId:item.operationId,status:'SUCCEEDED',nodeId:'9007199254740997'}).nodeId,'9007199254740997')
 assert.equal(receipt.applyUploadReceipt(item,{operationId:item.operationId,status:'FAILED_SAFE'}).status,'failed')
})
test('P03 reload retains receipt identity and destination without file bytes or credentials, scoped by actor',()=>{
 const map=new Map(),storage={setItem:(k,v)=>map.set(k,v),getItem:k=>map.get(k)}
 const item={...desktop.createUploadItem(file,'9007199254740997','0','root'),status:'uploading',token:'secret',owner:'secret',storageKey:'secret'}
 receipt.persistUploadReceipts('30','pc',[item],storage)
 const json=[...map.values()][0];assert.doesNotMatch(json,/secret|storageKey|token|owner/)
 const restored=receipt.restoreUploadReceipts('30','pc',storage)[0]
 assert.equal(restored.operationId,item.operationId);assert.equal(restored.status,'pending');assert.equal(restored.file,null)
 assert.equal(restored.targetSpaceId,'9007199254740997');assert.deepEqual(receipt.restoreUploadReceipts('31','pc',storage),[])
})
for(const surface of ['pc','mobile']) {
 test(`P03 ${surface}: querying a committed upload refreshes quota and its current folder without another POST`,async()=>{
  const h=setup(surface),running=h.start(),request=h.take('uploadDriveFile'),id=h.state().operationId
  request.resolve({data:{operationId:id,status:'PROCESSING'}});await running
  const refreshed=[]
  h.page.loadSpaces=async()=>{refreshed.push('quota')}
  h.page.loadNodes=async()=>{refreshed.push('folder')}
  const query=surface==='pc'?h.page.queryUpload(h.state().id):h.page.queryUpload()
  h.take('getDriveUploadReceipt').resolve({data:{operationId:id,status:'SUCCEEDED',nodeId:'7'}})
  await query
  assert.deepEqual(refreshed,['quota','folder'])
  assert.equal(h.state().status,'done')
  assert.equal(h.calls.filter(c=>c.name==='uploadDriveFile').length,1)
 })
 test(`P03 ${surface}: unavailable browser storage exposes a keep-page warning until the original result is known`,async()=>{
  const h=setup(surface), running=h.start(), request=h.take('uploadDriveFile'), id=h.state().operationId
  assert.equal(h.page.uploadReceiptStorageUnavailable,true)
  request.resolve({data:{operationId:id,status:'SUCCEEDED',nodeId:'7'}});await running
  assert.equal(h.page.uploadReceiptStorageUnavailable,false)
 })
 test(`P03 ${surface}: space refresh after upload cannot repopulate a changed account`,async()=>{
  const h=setup(surface), first=h.loadSpaces(), old=h.take('listDriveSpaces')
  h.store.state.user.id='31';h.store.state.user.sessionRevision++;await tick()
  const next=h.loadSpaces(), fresh=h.take('listDriveSpaces')
  fresh.resolve({data:[{spaceId:'31',spaceName:'current account'}]});await next
  old.resolve({data:[{spaceId:'8',spaceName:'old account'}]});await first
  assert.equal(h.page.spaces[0].spaceId,'31');assert.equal(h.page.activeSpaceId,'31')
 })
 test(`P03 ${surface}: cancel only stops waiting; original POST success still resolves the same receipt`,async()=>{
  const h=setup(surface),running=h.start(),request=h.take('uploadDriveFile'),id=h.state().operationId
  assert.equal(request.args[5],id)
  surface==='pc'?h.page.cancelUpload(h.state().id):h.page.cancelUpload()
  assert.equal(h.state().status,'pending');assert.equal(request.args[4].aborted,true)
  request.resolve({data:{operationId:id,status:'SUCCEEDED',nodeId:'9007199254740997'}});await running
  assert.equal(h.state().status,'done');assert.equal(h.state().nodeId,'9007199254740997')
 })
 test(`P03 ${surface}: lost reply queries receipt and never sends a duplicate POST`,async()=>{
  const h=setup(surface),running=h.start(),request=h.take('uploadDriveFile'),id=h.state().operationId
  request.reject(Error('network disconnected'));await tick()
  const query=h.take('getDriveUploadReceipt');assert.equal(query.args[0],id)
  query.resolve({data:{operationId:id,status:'NOT_OBSERVED'}});await running
  assert.equal(h.state().status,'pending');assert.equal(h.calls.filter(c=>c.name==='uploadDriveFile').length,1)
 })
 test(`P03 ${surface}: safe retry reuses original operation and destination`,async()=>{
  const h=setup(surface),running=h.start(),request=h.take('uploadDriveFile'),id=h.state().operationId
  request.resolve({data:{operationId:id,status:'FAILED_SAFE'}});await running;assert.equal(h.state().status,'failed')
  h.page.activeSpaceId='99'
  surface==='pc'?h.page.retryUpload(h.state().id):h.page.retryUpload()
  const retry=h.take('uploadDriveFile');assert.equal(retry.args[5],id);assert.equal(String(retry.args[1]),'8')
  retry.resolve({data:{operationId:id,status:'SUCCEEDED',nodeId:'7'}});await tick()
 })
 test(`P03 ${surface}: old response cannot write after an account ABA change`,async()=>{
  const h=setup(surface),running=h.start(),request=h.take('uploadDriveFile'),id=h.state().operationId
  h.store.state.user.id='31';h.store.state.user.sessionRevision++;await tick()
  h.store.state.user.id='30';h.store.state.user.sessionRevision++;await tick()
  request.reject(Error('old account failure'));await running
  assert.equal(h.calls.some(c=>c.name==='getDriveUploadReceipt'),false)
  assert.equal(h.state(),surface==='pc'?undefined:null)
 })
}
test('P03 PC/H5 and queue templates compile with visible result-query controls',()=>{
 for(const name of ['drive/index.vue','drive/components/DriveUploadQueue.vue','mobile/drive/index.vue']) {
  const part=compiler.parseComponent(fs.readFileSync(path.join(root,'src/views',name),'utf8'))
  assert.deepEqual(compiler.compile(part.template.content).errors,[])
  if(name!=='drive/index.vue') assert.match(part.template.content,/查询结果/)
 }
})
test('P03 PC late first-attempt reply cannot replace a safely retried upload or clear its progress',async()=>{
 const h=setup('pc'),running=h.start(),first=h.take('uploadDriveFile'),item=h.state(),id=item.operationId
 h.page.uploadItems=[{...item,status:'failed'}]
 h.page.retryUpload(item.id);const second=h.take('uploadDriveFile')
 first.reject(Error('late old attempt'));await running
 assert.equal(h.state().status,'uploading');assert.equal(h.calls.some(c=>c.name==='getDriveUploadReceipt'),false)
 second.resolve({data:{operationId:id,status:'SUCCEEDED',nodeId:'8'}});await tick();assert.equal(h.state().nodeId,'8')
})
