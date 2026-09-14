const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const Vue = require('vue'), Vuex = require('vuex'), babel = require('@babel/core'), compiler = require('vue-template-compiler')
Vue.use(Vuex)
const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const tick = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await Vue.nextTick() } }
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b}); return {promise,resolve,reject} }
function load(source, imports, timers) {
  const module = {exports:{}}
  const code = babel.transformSync(source,{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]}).code
  vm.runInNewContext(code,{module,exports:module.exports,require(name){if (!(name in imports)) throw Error(name);return imports[name]},
    Promise,Object,Array,String,Number,Error,console,window:{removeEventListener(){},location:{reload(){}}},
    setTimeout:timers.set,clearTimeout:timers.clear,clearInterval(){},cancelAnimationFrame(){}})
  return module.exports.default
}
function setup(cookie = true) {
  const requests=[],loginRequests=[],alerts=[],routes=[],unlockRequests=[],dispatches=[],timersMap=new Map(),cleanup=deferred()
  let timerId=0,store,clearCount=0,pendingCleanup=false
  const timers={set(fn,delay){const id=++timerId;timersMap.set(id,{fn,delay});return id},clear(id){timersMap.delete(id)}}
  const router={currentRoute:{path:'/lock'},replace(target){routes.push(target);return Promise.resolve()},push(){return Promise.resolve()}}
  const proxy={dispatch(type,payload){dispatches.push(type);return store.dispatch(type,payload)}}
  const imports={
    '@/store':proxy,'@/router':router,'@/plugins/cache':{session:{set(){}}},
    '@/plugins/element-services':{MessageBox:{alert(message){alerts.push(message);return Promise.resolve()},confirm(){return Promise.reject(Error('dismissed'))}}},
    '@/api/login':{logout(token){const request=deferred();requests.push({token,...request});return request.promise},login(){const request=deferred();loginRequests.push(request);return request.promise},getInfo(){return Promise.resolve()},refreshToken(){return Promise.resolve()}},
    '@/utils/auth':{getToken:()=>cookie?'':'old-token',setToken(){},setExpiresIn(){},removeToken(){clearCount++},removeExpiresIn(){}},
    '@/utils/shopContext':{clearSelectedDept(){}},'@/utils/signScopeContext':{clearSelectedSignScope(){}},
    '@/utils/passwordResetReminder':{getPasswordResetRoute:()=>'/profile',resetPasswordResetReminderState(){},setPendingPasswordResetReminder(){},showPendingPasswordResetReminderIfReady(){}},
    '@/utils/validate':{isEmpty:v=>v==null||v===''},'@/utils/mobileHrQueueState':{clearMobileHrQueueStateCache(){}},
    '@/assets/images/profile.jpg':'avatar.jpg','@/services/lazyPushRegistration':{disable(){return pendingCleanup?cleanup.promise:Promise.resolve()},initialize(){return Promise.resolve()}},
    '@/utils/sessionMode':{isCookiePreferredSession:()=>cookie,setWebSessionStatus(){},subscribeWebSessionStatus:()=>()=>{}},
    '@/utils/safeStorage':{getSafeLocalStorage:()=>({}),readJsonStorageValue:(_s,_k,f)=>f,readStringStorageValue:(_s,_k,f)=>f,safeSetStorageValue(){}}
  }
  const user=load(read('src/store/modules/user.js'),imports,timers),lock=load(read('src/store/modules/lock.js'),imports,timers)
  store=new Vuex.Store({modules:{user,lock,todo:{namespaced:true,actions:{stop(){return pendingCleanup?cleanup.promise:Promise.resolve()}}}},
    getters:{avatar:s=>s.user.avatar,nickName:s=>s.user.nickName,lockPath:s=>s.lock.lockPath,isLock:s=>s.lock.isLock}})
  store.commit('SET_ID','101');store.commit('SET_NAME','current-user');store.dispatch('lock/lockScreen','/inventory/sales')
  const component=load(compiler.parseComponent(read('src/views/lock.vue')).script.content,{
    vuex:Vuex,'@/api/login':{unlockScreen(password){unlockRequests.push(password);return Promise.resolve()}},'@/assets/images/profile.jpg':'avatar.jpg'
  },timers)
  const page=new Vue({...component,mounted:[],beforeCreate(){this.$store=store;this.$router=router}})
  const aba=()=>{store.commit('SET_TOKEN','');store.commit('SET_ID','');store.commit('SET_ID','101');if(!cookie)store.commit('SET_TOKEN','replacement-token');store.commit('SET_NAME','later-user');store.dispatch('lock/lockScreen','/later')}
  const flushTimeout=delay=>{for(const [id,value] of Array.from(timersMap)){if(value.delay===delay){timersMap.delete(id);value.fn()}}}
  return {page,store,requests,loginRequests,alerts,routes,dispatches,unlockRequests,aba,flushTimeout,clearCount:()=>clearCount,cleanup,
    setPendingCleanup(value){pendingCleanup=value},destroy(){page.$destroy()}}
}
function assertStillLocked(h){assert.equal(h.store.state.lock.isLock,true);assert.equal(h.store.state.user.id,'101');assert.equal(h.routes.length,0)}
test('PLAT-UI02 actual lock template compiles with disabled unlock and logout buttons',()=>{
  const part=compiler.parseComponent(read('src/views/lock.vue'));assert.deepEqual(compiler.compile(part.template.content).errors,[])
  assert.match(part.template.content,/:disabled="loading \|\| logoutLoading"/)
  assert.match(part.template.content,/<div v-if="errorMsg"[^>]+role="alert">\{\{ errorMsg \}\}<\/div>/)
  assert.doesNotMatch(part.template.content,/<a[^>]+href="\/login"/)
})
test('Cookie 503 keeps actual page/store locked; inline alert without modal and retry succeeds',async()=>{
  const h=setup(),pending=h.page.goLogin();await tick();assertStillLocked(h);assert.equal(h.page.logoutLoading,true)
  h.requests[0].reject(Object.assign(Error('503'),{response:{status:503}}));await pending;await tick()
  assertStillLocked(h);assert.equal(h.alerts.length,0);assert.match(h.page.errorMsg,/暂时无法确认服务器已完成退出/);assert.equal(h.page.logoutLoading,false);assert.equal(h.clearCount(),0)
  const retry=h.page.goLogin();await tick();h.requests[1].resolve();await retry;assert.deepEqual(h.routes,['/login']);assert.equal(h.store.state.lock.isLock,false);h.destroy()
})
for(const response of ['success','401'])test(`Cookie ${response} unlocks and navigates using the post-clear revision`,async()=>{
  const h=setup(),revision=h.store.state.user.sessionRevision,pending=h.page.goLogin();await tick()
  if(response==='success')h.requests[0].resolve();else h.requests[0].reject(Object.assign(Error('expired'),{response:{status:401}}))
  await pending;await tick();assert.equal(h.store.state.user.id,'');assert.equal(h.store.state.user.sessionRevision,revision+1)
  assert.equal(h.store.state.lock.isLock,false);assert.deepEqual(h.routes,['/login']);assert.equal(h.alerts.length,0);h.destroy()
})
test('Cookie timeout stays locked and a later completion cannot silently unlock',async()=>{
  const h=setup(),pending=h.page.goLogin();await tick();h.flushTimeout(12000);await pending;assertStillLocked(h)
  assert.equal(h.page.logoutLoading,false);assert.equal(h.alerts.length,0);assert.match(h.page.errorMsg,/退出请求超时/);h.requests[0].resolve();await tick();assertStillLocked(h);h.destroy()
})
test('double click and password Enter while logout is pending issue one logout and no unlock request',async()=>{
  const h=setup(),first=h.page.goLogin(),second=h.page.goLogin();h.page.password='secret';await h.page.handleUnlock();await tick()
  assert.equal(h.requests.length,1);assert.equal(h.unlockRequests.length,0);h.requests[0].resolve();await Promise.all([first,second]);assert.deepEqual(h.routes,['/login']);h.destroy()
})
test('destroyed lock component does not navigate after its confirmed logout',async()=>{
  const h=setup(),pending=h.page.goLogin();await tick();h.destroy();h.requests[0].resolve();await pending
  assert.equal(h.store.state.user.id,'');assert.equal(h.routes.length,0);assert.equal(h.page.logoutLoading,false)
})
test('deactivated page ignores late logout, and activation permits a fresh attempt',async()=>{
  const h=setup(),pending=h.page.goLogin();await tick();h.page.$options.deactivated[0].call(h.page)
  h.requests[0].reject(Error('network'));await pending;assertStillLocked(h);assert.equal(h.page.logoutLoading,false)
  h.page.$options.activated[0].call(h.page);const retry=h.page.goLogin();await tick();h.requests[1].resolve();await retry;assert.deepEqual(h.routes,['/login']);h.destroy()
})
for(const outcome of ['success','401','failure','timeout'])test(`old Cookie ${outcome} cannot clear a newer same-account session or emit stale errors`,async()=>{
  const h=setup(),pending=h.store.dispatch('LogOut').then(v=>({v}),e=>({e}));await tick();h.aba();await tick()
  if(outcome==='success')h.requests[0].resolve()
  if(outcome==='401')h.requests[0].reject(Object.assign(Error('expired'),{code:401}))
  if(outcome==='failure')h.requests[0].reject(Error('network'))
  if(outcome==='timeout')h.flushTimeout(12000)
  const result=await pending;assert.equal(result.e.code,'LOGOUT_SESSION_CHANGED');assertStillLocked(h)
  assert.equal(h.store.state.user.name,'later-user');assert.equal(h.clearCount(),0);assert.equal(h.alerts.length,0);h.destroy()
})
test('page ABA permits a new attempt; old success/finally cannot stop the newer spinner',async()=>{
  const h=setup(),first=h.page.goLogin();await tick();h.aba();await tick();assert.equal(h.page.logoutLoading,false)
  const second=h.page.goLogin();await tick();assert.equal(h.requests.length,2);h.requests[0].resolve();await first;await tick()
  assertStillLocked(h);assert.equal(h.page.logoutLoading,true);h.requests[1].resolve();await second;assert.deepEqual(h.routes,['/login']);h.destroy()
})
test('session change before dispatch prevents the queued page logout request',async()=>{
  const h=setup(),pending=h.page.goLogin();h.aba();await pending;await tick();assert.equal(h.requests.length,0);assertStillLocked(h);h.destroy()
})
for(const cookie of [true,false])test(`${cookie?'Cookie':'Bearer'} newer login during optional cleanup cannot receive an old completion permit`,async()=>{
  const h=setup(cookie);h.setPendingCleanup(true);const pending=h.page.goLogin();await tick()
  if(cookie){h.requests[0].resolve();await tick()}
  assert.equal(h.store.state.user.id,'');h.aba();await tick();h.cleanup.resolve();await pending;await tick();assertStillLocked(h)
  assert.equal(h.store.state.user.name,'later-user');assert.equal(h.alerts.length,0);h.destroy()
})
test('Bearer keeps best-effort remote failure behavior and clears old lock locally',async()=>{
  const h=setup(false),pending=h.page.goLogin();await tick();assert.equal(h.store.state.user.id,'');await pending
  assert.equal(h.store.state.lock.isLock,false);assert.deepEqual(h.routes,['/login']);assert.equal(h.requests[0].token,'old-token')
  h.requests[0].reject(Error('server unreachable'));await tick();assert.equal(h.alerts.length,0);h.destroy()
})
test('lock page renders its own inline feedback even when error was previously marked notified, without a modal',async()=>{
  const h=setup(),pending=h.page.goLogin();await tick();h.requests[0].reject(Object.assign(Error('already shown'),{notified:true}));await pending
  assertStillLocked(h);assert.equal(h.alerts.length,0);assert.match(h.page.errorMsg,/暂时无法确认服务器已完成退出/);h.destroy()
})
test('ordinary logout caller retains the existing global failure feedback',async()=>{
  const h=setup(),pending=h.store.dispatch('LogOut').catch(error=>error);await tick();h.requests[0].reject(Object.assign(Error('503'),{response:{status:503}}))
  const error=await pending;assertStillLocked(h);assert.equal(h.alerts.length,1);assert.equal(error.notified,true);assert.equal(error.inlineMessage,undefined);h.destroy()
})
for(const loginStage of ['pending','completed-before-GetInfo'])test(`old logout completion cannot navigate after a new empty-identity Cookie Login is ${loginStage}`,async()=>{
  const h=setup();h.setPendingCleanup(true);const old=h.page.goLogin();await tick();h.requests[0].resolve();await tick()
  assert.equal(h.store.state.user.id,'');assert.equal(h.store.state.user.token,'')
  const login=h.store.dispatch('Login',{username:'next-user',password:'secret',code:'x',uuid:'y'});await tick()
  if(loginStage==='completed-before-GetInfo'){h.loginRequests[0].resolve({data:{expires_in:3600}});await login;await tick()}
  assert.equal(h.store.state.user.id,'');assert.equal(h.store.state.user.token,'')
  h.cleanup.resolve();await old;await tick();assert.deepEqual(h.routes,[]);assert.equal(h.alerts.length,0)
  if(loginStage==='pending'){h.loginRequests[0].resolve({data:{expires_in:3600}});await login}
  h.destroy()
})
