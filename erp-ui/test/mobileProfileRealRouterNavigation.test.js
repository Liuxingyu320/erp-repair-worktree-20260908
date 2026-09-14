const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const Vue = require('vue')
const Router = require('vue-router')
Vue.use(Router)

const filename = path.resolve(__dirname, '../src/views/mobile/profile/index.vue')
const code = babel.transformSync(fs.readFileSync(filename, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1], {
  filename, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')]
}).code
const flush = async () => { for (let i = 0; i < 3; i++) { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() } }
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
async function mount(options = {}) {
  const pending = [], calls = [], reads = [], readCalls = [], store = { getters: { id: 7, permissions: [] }, commit() {}, dispatch: () => Promise.resolve() }
  const fixture = { userId: 7, userName: 'synthetic-user', nickName: '原昵称', phonenumber: '13800000000', email: 'test@example.com', sex: '2', profile: { currentAddress: '合成地址' } }
  const api = {
    getUserProfile: (...args) => {
      readCalls.push(args)
      if (!options.deferReads) return Promise.resolve({ data: fixture })
      const result = deferred(); reads.push(result); return result.promise
    },
    updateUserProfile: (...args) => { const result = deferred(); calls.push(args); pending.push(result); return result.promise },
    updateUserPwd: () => {
      if (!options.deferPasswords) return Promise.resolve()
      const result = deferred(); pending.push(result); return result.promise
    }, uploadAvatar: () => Promise.resolve()
  }
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, Object, Array, String, Number, console,
    window: { addEventListener() {}, removeEventListener() {} }, location: { href: 'unchanged' },
    require(id) {
      if (id === '@/api/system/user') return api
      if (id === '@/plugins/cache') return { session: { get: () => '0' } }
      if (id === '@/assets/images/profile.jpg') return 'synthetic.jpg'
      if (id === '@/utils/shopContext') return { getSelectedDeptContext: () => ({ deptId: 9, deptType: 'STORE' }) }
      if (id === '@/utils/passwordResetReminder') return { resetPasswordResetReminderState() {} }
      if (id === '@/utils/profileDisplayDate') return require('../src/utils/profileDisplayDate')
      if (id === '../mobileNavigation') return { getMobileBottomNav: () => [], getMobileHomePath: () => '/first', isMobileBottomNavItemActive: () => false }
      if (id === '../mobileViewport') return { startMobileViewportSync() {}, stopMobileViewportSync() {} }
      if (id === './mobileProfileValidation') return require('../src/views/mobile/profile/mobileProfileValidation')
      if (id === '../mobileErrorMessage') return require('../src/views/mobile/mobileErrorMessage')
      throw new Error(id)
    }
  }, { filename })
  const definition = module.exports.default
  const router = new Router({ mode: 'abstract', routes: [
    { path: '/mobile/profile', component: definition },
    { path: '/first', component: { render: h => h('div') } },
    { path: '/second', component: { render: h => h('div') } }
  ] })
  const root = new Vue({ router })
  await router.push('/mobile/profile')
  const page = new Vue(Object.assign({}, definition, { parent: root, beforeCreate() { this.$store = store } }))
  page.$confirm = () => Promise.resolve()
  // A rendered router-view registers the same instance here. This registration
  // lets the real Router execute component leave guards without a browser DOM.
  router.currentRoute.matched[0].instances.default = page
  await flush()
  return { page, router, pending, calls, reads, readCalls, fixture,
    lifecycle(name) {
      // Invoke the actual SFC lifecycle handlers as a keep-alive host does. No
      // browser DOM is claimed; navigation guards above run in real Vue Router.
      const hooks = page.$options[name] || []
      for (const hook of Array.isArray(hooks) ? hooks : [hooks]) hook.call(page)
    },
    push(target) { return router.push(target).then(() => ({ completed: true }), error => ({ completed: false, type: error.type })) },
    destroy() { page.$destroy(); root.$destroy() } }
}
const cases = []
const test = (name, run, options) => cases.push({ name, run, options })

test('clean route leaves normally and does not open a draft prompt', async h => {
  const result = await h.push('/first')
  assert.equal(result.completed, true); assert.equal(h.router.currentRoute.path, '/first'); assert.equal(h.page.leavePromptOpen, false)
})
test('dirty transition aborts immediately; second target cannot steal first target', async h => {
  h.page.profileForm.nickName = '待保存'
  let firstFinished = false
  const first = h.push({ path: '/first', query: { origin: 'first' } }).then(value => { firstFinished = true; return value })
  await flush(); assert.equal(firstFinished, true, 'the original router transition must be settled before asking the user')
  assert.equal((await first).type, 4); assert.equal(h.page.leavePromptOpen, true)
  const second = await h.push('/second'); assert.equal(second.completed, false)
  h.page.discardAndLeave(); await flush()
  assert.equal(h.router.currentRoute.path, '/first'); assert.equal(h.router.currentRoute.query.origin, 'first')
  assert.equal(h.page.profileForm.nickName, '原昵称'); assert.equal(h.page.profileDirty, false)
})
test('repeated identical targets still produce one effective decision and actual navigation', async h => {
  h.page.profileForm.nickName = '临时'
  const a = h.push('/first'); const b = h.push('/first'); await flush()
  h.page.discardAndLeave(); await Promise.all([a, b]); await flush()
  assert.equal(h.router.currentRoute.path, '/first'); assert.equal(h.page.leavePromptOpen, false)
})
test('cancel retains input and retry can choose a different target', async h => {
  h.page.profileForm.nickName = '不能丢'
  const first = h.push('/first'); await flush(); h.page.cancelLeave(); await first; await flush()
  assert.equal(h.router.currentRoute.path, '/mobile/profile'); assert.equal(h.page.profileForm.nickName, '不能丢')
  const retry = h.push('/second'); await flush(); h.page.discardAndLeave(); await retry; await flush()
  assert.equal(h.router.currentRoute.path, '/second')
})
test('save and leave waits for real save and then reaches the original target', async h => {
  h.page.profileForm.nickName = '已保存'
  const first = h.push('/first'); await flush(); const choosing = h.page.saveAndLeave(); await flush()
  assert.equal(h.calls.length, 1); assert.equal(h.router.currentRoute.path, '/mobile/profile')
  h.pending.shift().resolve({}); await choosing; await first; await flush()
  assert.equal(h.router.currentRoute.path, '/first'); assert.equal(h.page.profileDirty, false)
})
test('save failure keeps the route and input; cancel clears navigation intent', async h => {
  h.page.profileForm.nickName = '重试输入'
  const first = h.push('/first'); await flush(); const choosing = h.page.saveAndLeave()
  h.pending.shift().reject(new Error('network failed')); await choosing; await first; await flush()
  assert.equal(h.router.currentRoute.path, '/mobile/profile'); assert.equal(h.page.profileForm.nickName, '重试输入'); assert.equal(h.page.leavePromptOpen, true)
  h.page.cancelLeave(); await flush(); assert.equal(h.page.leavePromptOpen, false)
  const retry = h.push('/second'); await flush(); h.page.discardAndLeave(); await retry; await flush(); assert.equal(h.router.currentRoute.path, '/second')
})
test('new inputs during save prevent route replay until the next explicit decision', async h => {
  h.page.profileForm.nickName = '第一版'
  const first = h.push('/first'); await flush(); const choosing = h.page.saveAndLeave()
  h.page.profileForm.nickName = '第二版'; h.pending.shift().resolve({}); await choosing; await first; await flush()
  assert.equal(h.router.currentRoute.path, '/mobile/profile'); assert.equal(h.page.profileForm.nickName, '第二版'); assert.equal(h.page.profileDirty, true)
  h.page.cancelLeave(); await flush()
})
test('new input between discard and replay is not bypassed by an approval flag', async h => {
  h.page.profileForm.nickName = '旧草稿'
  const first = h.push('/first'); await flush(); h.page.discardAndLeave(); h.page.profileForm.nickName = '放弃之后的新输入'
  await first; await flush()
  assert.equal(h.router.currentRoute.path, '/mobile/profile'); assert.equal(h.page.profileDirty, true); assert.equal(h.page.profileForm.nickName, '放弃之后的新输入')
  if (h.page.leavePromptOpen) h.page.cancelLeave()
})
test('same-record query tab switch preserves draft without a leave prompt', async h => {
  h.page.profileForm.nickName = '标签草稿'
  await h.router.replace({ path: '/mobile/profile', query: { mode: 'hr-profile' } }); await flush()
  assert.equal(h.page.activeSection, 'hr-profile'); assert.equal(h.page.profileForm.nickName, '标签草稿'); assert.equal(h.page.leavePromptOpen, false)
  await h.router.replace('/mobile/profile'); await flush(); assert.equal(h.page.profileForm.nickName, '标签草稿')
})
test('leaving before initial load and reactivating starts a fresh read; old success stays isolated', async h => {
  assert.equal(h.readCalls.length, 1); assert.equal(h.page.profileBaseline, null)
  await h.push('/first'); h.lifecycle('deactivated')
  await h.push('/mobile/profile'); h.lifecycle('activated')
  assert.equal(h.readCalls.length, 2); assert.equal(h.page.loading, true)
  h.reads.shift().resolve({ data: Object.assign({}, h.fixture, { nickName: '过期昵称' }) }); await flush()
  assert.equal(h.page.profileBaseline, null); assert.equal(h.page.loading, true); assert.equal(h.page.loadError, '')
  h.reads.shift().resolve({ data: Object.assign({}, h.fixture, { nickName: '重新进入后的昵称' }) }); await flush()
  assert.equal(h.page.profileForm.nickName, '重新进入后的昵称'); assert.equal(h.page.profileBaseline.nickName, '重新进入后的昵称'); assert.equal(h.page.loading, false)
  h.page.profileForm.nickName = '重新进入后可保存'; const saving = h.page.saveProfile()
  assert.equal(h.calls.length, 1); h.pending.shift().resolve({}); assert.equal(await saving, true)
}, { deferReads: true })
test('old initial read failure after reactivation cannot hide fresh loading or report stale error', async h => {
  await h.push('/first'); h.lifecycle('deactivated')
  await h.push('/mobile/profile'); h.lifecycle('activated')
  h.reads.shift().reject(new Error('old request failed')); await flush()
  assert.equal(h.page.loadError, ''); assert.equal(h.page.loading, true)
  h.reads.shift().resolve({ data: h.fixture }); await flush()
  assert.equal(h.page.profileBaseline.nickName, '原昵称'); assert.equal(h.page.loading, false)
}, { deferReads: true })
test('reactivating an existing dirty profile retains its baseline and does not overwrite input', async h => {
  h.page.profileForm.nickName = '保留草稿'
  h.lifecycle('deactivated'); h.lifecycle('activated'); await flush()
  assert.equal(h.readCalls.length, 1); assert.equal(h.page.profileForm.nickName, '保留草稿')
  assert.equal(h.page.profileBaseline.nickName, '原昵称'); assert.equal(h.page.profileDirty, true)
})
test('hard refresh warns while save is pending even when new input equals the old baseline', async h => {
  h.page.profileForm.nickName = '请求提交值'; const saving = h.page.saveProfile()
  h.page.profileForm.nickName = '原昵称'
  assert.equal(h.page.profileDirty, false); assert.equal(h.page.profileSaving, true)
  const event = { prevented: false, preventDefault() { this.prevented = true } }
  h.page.warnBeforeUnload(event); assert.equal(event.prevented, true); assert.equal(event.returnValue, '')
  h.pending.shift().resolve({}); await saving
  assert.equal(h.page.profileForm.nickName, '原昵称'); assert.equal(h.page.user.nickName, '请求提交值'); assert.equal(h.page.profileDirty, true)
  const latest = h.page.saveProfile(); h.pending.shift().resolve({}); await latest
  const clean = { prevented: false, preventDefault() { this.prevented = true } }
  h.page.warnBeforeUnload(clean); assert.equal(clean.prevented, false)
})
test('hard refresh warns about a pending password write after its input fields are cleared', async h => {
  Object.assign(h.page.passwordForm, { oldPassword: 'synthetic-old', newPassword: 'synthetic-new', confirmPassword: 'synthetic-new' })
  const saving = h.page.savePassword()
  Object.assign(h.page.passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  assert.equal(h.page.passwordDirty, false); assert.equal(h.page.passwordSaving, true)
  const event = { prevented: false, preventDefault() { this.prevented = true } }
  h.page.warnBeforeUnload(event); assert.equal(event.prevented, true); assert.equal(event.returnValue, '')
  h.pending.shift().resolve({}); await saving
  const clean = { prevented: false, preventDefault() { this.prevented = true } }
  h.page.warnBeforeUnload(clean); assert.equal(clean.prevented, false)
}, { deferPasswords: true })
async function run() {
  let passed = 0
  for (const entry of cases) {
    const h = await mount(entry.options); let timer
    try {
      await Promise.race([entry.run(h), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('unfinished real-router scenario')), 4000) })])
      await flush(); assert.equal(h.pending.length, 0); assert.equal(h.reads.length, 0); passed++; console.log('PASS ' + entry.name)
    } catch (error) { error.message = entry.name + ': ' + error.message; throw error }
    finally { clearTimeout(timer); h.destroy() }
  }
  console.log(`mobileProfileRealRouterNavigation: ${passed} real Router/component lifecycle scenarios passed; 0 failed, 0 skipped (no browser DOM)`)
}
run().catch(error => { console.error(error); process.exitCode = 1 })
