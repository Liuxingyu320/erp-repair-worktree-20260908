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

function rejection(id, status = 422) { return { response: { status, data: { code:500, businessCode:'DRIVE_FILE_TYPE_REJECTED', msg:'当前文件类型不允许上传', uploadAttempt: { operationId:id, state:'REJECTED_BEFORE_CLAIM' } } } } }
for (const surface of ['pc','mobile']) {
 test(`P03 ${surface}: first matching explicit pre-claim rejection releases only this candidate and shows the reason`, async()=>{
  const h=setup(surface), running=h.start(), id=h.state().operationId
  h.take('uploadDriveFile').reject(rejection(id));await running
  assert.equal(h.state().status,'rejected');assert.match(h.state().error,/文件类型不允许上传/)
  assert.equal(h.calls.some(c=>c.name==='getDriveUploadReceipt'),false)
  if(surface==='pc') {h.page.removeUpload(h.state().id);assert.equal(h.page.uploadItems.length,0);h.page.handleSelectFiles([{name:'allowed.pdf',size:30}]);await tick()}
  else {h.page.handleFileSelection({target:{files:[{name:'allowed.pdf',size:30}],value:'file'}},false)}
  const next=h.take('uploadDriveFile');assert.notEqual(next.args[5],id)
  next.resolve({data:{operationId:next.args[5],status:'SUCCEEDED',nodeId:'8'}});await tick()
  assert.equal(h.state().status,'done')
 })
 test(`P03 ${surface}: wrong-ID marker and ordinary HTTP rejection remain unknown until original receipt is read`,async()=>{
  for(const responseError of [rejection('wrong-operation'),{response:{status:422,data:{businessCode:'DRIVE_FILE_TYPE_REJECTED'}}},Error('timeout')]) {
   const h=setup(surface),running=h.start(),id=h.state().operationId
   h.take('uploadDriveFile').reject(responseError);await tick()
   h.take('getDriveUploadReceipt').resolve({data:{operationId:id,status:'NOT_OBSERVED'}});await running
   assert.equal(h.state().status,'pending');assert.equal(h.calls.filter(c=>c.name==='uploadDriveFile').length,1)
  }
 })
 test(`P03 ${surface}: stopped waiting cannot use a late pre-claim marker to discard an unknown command`,async()=>{
  const h=setup(surface),running=h.start(),id=h.state().operationId,post=h.take('uploadDriveFile')
  surface==='pc'?h.page.cancelUpload(h.state().id):h.page.cancelUpload()
  post.reject(rejection(id));await tick();h.take('getDriveUploadReceipt').resolve({data:{operationId:id,status:'SUCCEEDED',nodeId:'9'}});await running
  assert.equal(h.state().status,'done');assert.equal(h.state().nodeId,'9')
 })
 test(`P03 ${surface}: a safe retry is not the first attempt and preserves the original command on pre-claim rejection`,async()=>{
  const h=setup(surface),running=h.start(),id=h.state().operationId
  h.take('uploadDriveFile').resolve({data:{operationId:id,status:'FAILED_SAFE'}});await running
  surface==='pc'?h.page.retryUpload(h.state().id):h.page.retryUpload()
  h.take('uploadDriveFile').reject(rejection(id));await tick()
  const query=h.take('getDriveUploadReceipt');assert.equal(query.args[0],id)
  query.resolve({data:{operationId:id,status:'PROCESSING'}});await tick()
  assert.equal(h.state().status,'pending');assert.equal(h.state().operationId,id)
 })
 test(`P03 ${surface}: account ABA suppresses late explicit-rejection feedback`,async()=>{
  const h=setup(surface),running=h.start(),id=h.state().operationId,post=h.take('uploadDriveFile')
  h.store.state.user.id='31';h.store.state.user.sessionRevision++;await tick()
  h.store.state.user.id='30';h.store.state.user.sessionRevision++;await tick()
  post.reject(rejection(id));await running
  assert.equal(h.state(),surface==='pc'?undefined:null);assert.equal(h.calls.some(c=>c.name==='getDriveUploadReceipt'),false)
 })
}
test('P03 restored metadata never confers first-attempt authority; malformed markers and known outcomes are not rejected',()=>{
 const map=new Map(),storage={setItem:(k,v)=>map.set(k,v),getItem:k=>map.get(k)}
 const item={...desktop.createUploadItem(file,'8','0','root'),status:'failed'}
 receipt.persistUploadReceipts('30','pc',[item],storage)
 const restored=receipt.restoreUploadReceipts('30','pc',storage)[0]
 assert.equal(restored.freshUpload,false);assert.equal(restored.resultWasUnknown,true)
 assert.equal(receipt.applyPreClaimRejection({...restored,status:'uploading'},rejection(item.operationId),true),null)
 for(const error of [rejection(item.operationId,200),rejection(item.operationId,NaN),{uploadAttempt:{operationId:item.operationId,state:'REJECTED_BEFORE_CLAIM'}}]) {
  assert.equal(receipt.applyPreClaimRejection({...item,status:'uploading'},error,true),null)
 }
 assert.equal(receipt.applyPreClaimRejection({...item,status:'done'},rejection(item.operationId),true),null)
})
