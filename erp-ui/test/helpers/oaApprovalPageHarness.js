const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm')
const Vue=require('vue'),compiler=require('vue-template-compiler'),babel=require('@babel/core')
const scope=require('../../src/utils/uiOperationScope')
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
function setup(folder){
 const calls=[],messages=[],prompts=[],env={dept:'10',ask:()=>Promise.resolve()};const api=new Proxy({}, {get(_,name){return (...args)=>{const c=deferred();calls.push({name,args,...c});return c.promise}}})
 const file=path.resolve(__dirname,'../../src/views',folder,'index.vue'),script=compiler.parseComponent(fs.readFileSync(file,'utf8')).script.content
 const code=babel.transformSync(script,{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
 const mixin=require('./approvalRecoveryHarness')(api,env),module={exports:{}}
 vm.runInNewContext(code,{module,exports:module.exports,Promise,console,window:{addEventListener(){},removeEventListener(){}},require:id=>{
  if(id==='@/mixins/reimbursementExportRecovery')return {default:{},__esModule:true}
  if(id==='@/mixins/approvalCommandRecovery')return mixin
  if(id==='@/mixins/reimbursementWithdrawRecovery')return require('./withdrawRecoveryHarness')(api,env)
  if(id.startsWith('@/api/'))return api
  if(id==='@/utils/approvalCommandRecovery')return require('../../src/utils/approvalCommandRecovery')
  if(id==='@/views/approval/manage/components/approvalUi')return {statusLabel:x=>x}
  if(id==='@/utils/uiOperationScope')return scope
  if(id==='@/utils/shopContext')return {getSelectedDeptId:()=>env.dept,hasValidatedSelectedDeptContext:()=>true}
  if(id==='@/utils/oaPurchaseContext')return require('../../src/utils/oaPurchaseContext')
  if(id==='@/utils/reimbursementSmartFill')return require('../../src/utils/reimbursementSmartFill')
  if(id==='@/utils/todoRouteParams')return require('../../src/utils/todoRouteParams')
  if(id==='@/utils/todoActionReturn')return require('../../src/utils/todoActionReturn')
  if(id==='@/utils/todoBusinessFocus')return require('../../src/utils/todoBusinessFocus')
  if(id==='./mobileReimbursementState')return require('../../src/views/mobile/oa/reimbursement/mobileReimbursementState')
  if(id==='@/mixins/todoBusinessFocus')return {createTodoBusinessFocusMixin:()=>({})}
  return {}
 }})
 const store=Vue.observable({getters:{id:'7'},state:{user:{sessionRevision:1}},dispatch:()=>Promise.resolve()})
 const mobile=folder.startsWith('mobile'),purchase=folder.includes('purchase'),route=Vue.observable({path:mobile?'/mobile/'+(purchase?'oa-purchase-approval':'reimbursement'):'/oa/'+(purchase?'purchase':'reimbursement'),query:{businessId:'101',approvalTaskId:'201',approvalInstanceId:'301'}})
 const p=new Vue({...module.exports.default,created:[],beforeCreate(){this.$store=store;this.$route=route;this.$modal={confirm:()=>env.ask(),msgSuccess:m=>messages.push(m),msgError:m=>messages.push(m),msgWarning:m=>messages.push(m)};this.$prompt=(...args)=>{prompts.push(args);return env.ask().then(()=>({value:'保留意见'}))}}})
 const businessCode=purchase?'OA_PURCHASE':'OA_REIMBURSEMENT',target={businessId:'101',taskId:'201',instanceId:'301',businessCode}
 const read={instance:{instanceId:'301',businessId:'101',businessCode,status:'RUNNING'},tasks:[{taskId:'201',instanceId:'301',taskStatus:'PENDING'}],actions:[]}
 const detail={reimbursementId:'101',purchaseId:'101',approvalInstanceId:'301',approvalRound:1,status:'pending',items:[]}
 p.detail=detail;p.approvalDetail=read;p.detailVisible=true;p.detailLoading=false;p.loading=false;p.mode='detail'
 if(purchase){if(mobile){p.purchase=detail;p.loadedTarget=p.routeTarget;p._purchaseReadToken=p.purchaseScope().begin('read',p.routeTarget)}else{p.detailTarget={purchaseId:'101',taskId:'201',instanceId:'301'};p._purchaseDetailToken=p.purchaseScope().begin('detail',p.detailTarget)}}
 const take=name=>{const c=calls.find(c=>c.name===name&&!c.used);assert.ok(c,'missing '+name+' calls '+calls.map(c=>c.name));c.used=true;return c}
 const run=action=>p[purchase?(mobile?'executeAction':'handleApprovalAction'):'approvalAction'](action)
 p.getList=()=>Promise.resolve();p.loadAvailability=()=>Promise.resolve();p.loadTodoBusinessList=loader=>loader();p.showDetail=()=>Promise.resolve();p.openDetail=()=>Promise.resolve();p.load=()=>Promise.resolve();p.loadList=()=>Promise.resolve();p.refreshTodo=()=>Promise.resolve()
 return {p,store,route,api,calls,messages,prompts,env,take,run,read,target,component:module.exports.default}
}

module.exports=setup
