const test=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),vm=require('vm'),babel=require('@babel/core'),Vue=require('vue'),compiler=require('vue-template-compiler')
const root=path.resolve(__dirname,'..'),tick=async()=>{await new Promise(r=>setImmediate(r));await Vue.nextTick()},deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}}
function harness(relative){
 const calls=[],env={deptId:'10',deptType:'STORE'},events=new Map(),store=Vue.observable({getters:{id:'7',token:'a',permissions:['inv:report:list'],permission_routes:[]}}),route=Vue.observable({query:{packageId:'9223372036854775807'}})
 const window={location:{origin:'https://erp.example'},addEventListener:(n,f)=>events.set(n,f),removeEventListener:n=>events.delete(n)}
 const api=new Proxy({},{get(_,name){if(name==='__esModule')return false;return(...args)=>{const d=deferred();calls.push({name,args,...d});return d.promise}}})
 const filename=path.join(root,'src/views',relative),parsed=compiler.parseComponent(fs.readFileSync(filename,'utf8'));assert.deepEqual(compiler.compile(parsed.template.content).errors,[])
 const code=babel.transformSync(parsed.script.content,{filename,babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code,module={exports:{}}
 vm.runInNewContext(code,{module,exports:module.exports,window,navigator:{},console,URL,Promise,require(id){
 if(id.startsWith('@/api/'))return api
 if(id==='@/utils/shopContext')return{getSelectedDeptContext:()=>({...env}),getSelectedDeptId:()=>env.deptId}
 if(id==='@/utils/uiOperationScope')return require('../src/utils/uiOperationScope')
 if(id==='@/utils/signPackageHandoff')return require('../src/utils/signPackageHandoff')
 if(id==='@/utils/signDictionary')return{signPackageStatusLabel:status=>status}
 if(id==='qrcode-generator')return require('qrcode-generator')
 if(id==='vuex')return require('vuex')
 if(id.startsWith('./')||id.startsWith('../'))return require(path.resolve(path.dirname(filename),id))
 return{}
 }})
 const c=new Vue({...module.exports.default,propsData:relative.includes("Workbench")?{contextType:"STORE"}:{},created:undefined,activated:undefined,beforeCreate(){this.$store=store;this.$route=route;this.$router={back(){}}}})
 return{c,calls,env,store,route,events,definition:module.exports.default}
}
test('workbench A-B-A invalidates old success and failure and preserves newest loading',async()=>{
 const h=harness('mobile/components/MobileWorkbenchShell.vue'),a=h.c.loadWorkbenchSummary(),old=h.calls.at(-1)
 h.env.deptId='20';h.c.reloadWorkbenchContext();const b=h.calls.findLast(x=>x.name==='getMobileWorkbenchSummary')
 h.env.deptId='10';h.c.reloadWorkbenchContext();const fresh=h.calls.findLast(x=>x.name==='getMobileWorkbenchSummary')
 old.resolve({data:{selectedDeptId:'10',selectedDeptType:'STORE',todaySalesCount:99}});await a
 b.reject(Error('old organization'));await tick();assert.equal(h.c.summaryLoading,true);assert.equal(h.c.workbenchSummary,null)
 fresh.resolve({data:{selectedDeptId:'10',selectedDeptType:'STORE',todaySalesCount:4}});await tick();assert.equal(h.c.workbenchSummary.todaySalesCount,4);assert.equal(h.c.summaryLoading,false)
})
test('workbench missing/failed data shows unknown and organization mismatch cannot populate counts',async()=>{
 const h=harness('mobile/components/MobileWorkbenchShell.vue');assert.ok(h.c.profile.metrics.every(x=>x.value==='—'))
 const p=h.c.loadWorkbenchSummary();h.calls.at(-1).resolve({data:{selectedDeptId:'999',selectedDeptType:'STORE',todaySalesCount:2}});await p
 assert.equal(h.c.workbenchSummary,null);assert.ok(h.c.summaryError);assert.ok(h.c.profile.metrics.every(x=>x.value==='—'))
 h.c.$props.contextType='WAREHOUSE';assert.ok(!h.c.profile.metrics.some(x=>x.key==='todaySalesCount'))
})
test('handoff produces a local QR with only protected business URL and exact long ID',async()=>{
 const h=harness('signPackageHandoff/index.vue'),p=h.c.loadPackage();assert.equal(h.calls[0].name,'getMySignPackage');assert.equal(h.calls[0].args[0],'9223372036854775807')
 h.calls[0].resolve({data:{packageId:'9223372036854775807',status:'pending_sign',packageNo:'P01',documents:[{privateUrl:'/private/file'}],token:'secret'}});await p
 assert.equal(h.c.mobileUrl,'https://erp.example/mobile/sign-package?packageId=9223372036854775807');assert.ok(h.c.qrImage.startsWith('data:image/gif;base64,'));assert.deepEqual(Object.keys(h.c.signPackage).sort(),['packageId','packageNo','status'])
 await h.c.copyLink();assert.match(h.c.copyMessage,/复制/)
})
test('handoff package and account changes obsolete old results, failure retries, terminal states never promise signing',async()=>{
 const h=harness('signPackageHandoff/index.vue'),a=h.c.loadPackage(),old=h.calls[0];h.route.query.packageId='42';await tick();const fresh=h.calls.at(-1)
 old.resolve({data:{packageId:'9223372036854775807',status:'pending_sign'}});await a;assert.equal(h.c.signPackage,null);assert.equal(h.c.loading,true)
 fresh.reject(Error('forbidden'));await tick();assert.ok(h.c.error);assert.equal(h.c.qrImage,'')
 const retry=h.c.loadPackage();h.calls.at(-1).resolve({data:{packageId:'42',status:'voided'}});await retry;assert.equal(h.c.canContinue,false);assert.equal(h.c.mobileUrl,'');assert.equal(h.c.qrImage,'')
 const late=h.c.loadPackage(),request=h.calls.at(-1);h.store.getters.id='8';await tick();request.resolve({data:{packageId:'42',status:'pending_sign'}});await late;assert.equal(h.c.signPackage,null)
})
test('handoff URL rejects malformed identities, foreign paths and credentials',()=>{
 const {createHandoffUrl,normalizePackageId}=require('../src/utils/signPackageHandoff')
 for(const id of ['1e2','0','-1','9223372036854775808',9007199254740992,{},['1']])assert.equal(normalizePackageId(id),'')
 for(const origin of ['https://user:pass@erp.example','javascript:alert(1)','https://erp.example/other','https://erp.example?token=secret'])assert.equal(createHandoffUrl(origin,'1'),'')
})
