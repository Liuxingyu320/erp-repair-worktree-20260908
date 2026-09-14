const { test } = require('node:test')
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path')
const compiler = require('vue-template-compiler')
const scope = require('../src/utils/uiOperationScope')
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}}
function harness(){
  const d=compiler.parseComponent(fs.readFileSync(path.resolve(__dirname,'../src/views/hr/healthCertificate/index.vue'),'utf8'))
  assert.deepEqual(compiler.compile(d.template.content).errors,[])
  const imports=[];const script=d.script.content.replace(/import\s+([\s\S]*?)\s+from\s+["'][^"']+["']\s*/g,(_,x)=>{imports.push(...x.replace(/[{}]/g,'').split(',').map(n=>n.trim().split(/\s+as\s+/).pop()).filter(Boolean));return ''}).replace('export default','return')
  const calls=[],confirmations=[];const api=name=>(...args)=>{const pending=deferred();calls.push({name,args,...pending});return pending.promise}
  assert.match(d.script.content, /import \{ getSelectedDeptId \} from '@\/utils\/shopContext'/)
  const shopSource=fs.readFileSync(path.resolve(__dirname,'../src/utils/shopContext.js'),'utf8')
  const realShop=new Function('sessionStorage',shopSource.replace(/export /g,'')+';return {getSelectedDeptId}')({getItem:key=>key==='selected_dept_id'?'10':null})
  const globals=Object.fromEntries(imports.map(n=>[n,api(n)]));globals.getSelectedDeptId=realShop.getSelectedDeptId
  assert.equal(globals.getSelectedDeptId(),'10')
  const def=new Function('require',...imports,script)(()=>scope,...imports.map(n=>globals[n]))
  const model={...def.data(),$store:{state:{user:{id:'7',sessionRevision:1}},getters:{permissions:['hr:healthCertificate:list']}},$route:{fullPath:'/hr/healthCertificate'},$modal:{confirm:()=>{const d=deferred();confirmations.push(d);return d.promise},msgSuccess(){}},$nextTick:f=>f(),$refs:{}}
  for(const[n,f]of Object.entries(def.methods))model[n]=f.bind(model)
  for(const[n,f]of Object.entries(def.computed))Object.defineProperty(model,n,{get:()=>f.call(model),configurable:true})
  model.refreshAll=()=>{}
  const take=name=>{const c=calls.find(c=>c.name===name&&!c.used);assert.ok(c,name);c.used=true;return c}
  return{model,calls,take,confirmations,def}
}
test('each pending tile opens exactly its server queue at page one with same department',async()=>{
  for(const queue of ['PENDING_ALL','PENDING_REVIEW','APPROVAL_PENDING','APPROVAL_SUBMITTING','APPROVAL_START_FAILED']){
    const h=harness();h.model.query.currentDeptId='20';h.model.query.pageNum=4;h.model.query.certificateId='88'
    const p=h.model.openQueue(queue),r=h.take('listHealthCertificates');assert.equal(r.args[0].reviewStatus,queue);assert.equal(r.args[0].pageNum,1);assert.equal(r.args[0].currentDeptId,'20');assert.equal(r.args[0].certificateId,undefined)
    r.resolve({rows:[],total:0});await p;assert.equal(h.model.activeTab,'admin')
  }
})
test('failed summary is visibly unknown and retry retains organization scope',async()=>{
  const h=harness();h.model.query.currentDeptId='20';h.model.opsLoaded=true;h.model.opsSummary={pendingReviewCount:5}
  const p=h.model.loadOpsSummary();h.take('getHealthCertificateOpsSummary').reject(Error('offline'));await p
  assert.equal(h.model.opsValue('pendingReviewCount'),'—');assert.equal(h.model.opsSummary.pendingReviewCount,5);assert.match(h.model.opsError,/offline/)
  const r=h.model.loadOpsSummary();const read=h.take('getHealthCertificateOpsSummary');assert.equal(read.args[0].currentDeptId,'20');read.resolve({data:{pendingReviewCount:3}});await r;assert.equal(h.model.opsValue('pendingReviewCount'),3)
})
test('older summary and list replies cannot replace a later organization context',async()=>{
  const h=harness();const a=h.model.loadOpsSummary(),old=h.take('getHealthCertificateOpsSummary');h.model.healthDeptChanged();const b=h.model.loadOpsSummary(),next=h.take('getHealthCertificateOpsSummary')
  next.resolve({data:{pendingReviewCount:2}});await b;old.resolve({data:{pendingReviewCount:99}});await a;assert.equal(h.model.opsValue('pendingReviewCount'),2)
  const listA=h.model.openQueue('PENDING_ALL'),oldList=h.take('listHealthCertificates');const listB=h.model.openQueue('APPROVAL_PENDING'),newList=h.take('listHealthCertificates');newList.resolve({rows:[{certificateId:'2'}],total:1});await listB;oldList.reject(Error('old failure'));await listA;assert.equal(h.model.adminRows[0].certificateId,'2');assert.equal(h.model.adminError,'')
})
test('failed start replay uses the existing outbox version and must recheck after unknown result',async()=>{
  const h=harness(),p=h.model.openStartRecovery({certificateId:'88'});const record={outboxId:'9007199254740993',certificateId:'88',status:'FAILED',version:4};h.take('listHealthCertificateApprovalStartOutboxes').resolve({rows:[record]});await p
  const replay=h.model.replayStart(record);h.confirmations[0].resolve();await Promise.resolve();const call=h.take('replayHealthCertificateApprovalStart');assert.deepEqual(call.args,['9007199254740993',4]);call.reject(Error('lost'));await replay
  await h.model.replayStart(record);assert.equal(h.calls.filter(c=>c.name==='replayHealthCertificateApprovalStart').length,1);assert.equal(h.model.startRecoveryNeedsCheck,true)
})
test('closing recovery dialog during confirmation prevents a replay on another certificate',async()=>{
  const h=harness();h.model.startRecoveryOpen=true;h.model.startRecoveryCertificateId='88';const p=h.model.replayStart({outboxId:'1',certificateId:'88',status:'FAILED',version:0});h.model.startRecoveryOpen=false;h.model.closeStartRecovery();h.model.startRecoveryOpen=true;h.model.startRecoveryCertificateId='99';h.confirmations[0].resolve();await p
  assert.equal(h.calls.length,0);assert.equal(h.model.startRecoveryCertificateId,'99')
})
test('employee detail contains one complete current section and future summary; mobile labels match columns',()=>{
  const detail=fs.readFileSync(path.resolve(__dirname,'../src/views/hr/components/HrProfileDetailDrawer.vue'),'utf8')
  assert.equal((detail.match(/<h[34]>当前健康证<\/h[34]>/g)||[]).length,1);assert.ok(detail.includes('detail.healthCertificateNextValidFrom'))
  const mobile=fs.readFileSync(path.resolve(__dirname,'../src/views/mobile/hr/healthCertificate/index.vue'),'utf8')
  assert.match(mobile,/mine-health-table[^\n]+nth-child\(3\)[^\n]+生效日期/);assert.match(mobile,/admin-health-table[^\n]+nth-child\(5\)[^\n]+生效日期/)
})
