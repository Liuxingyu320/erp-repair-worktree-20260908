const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const Vue = require('vue')
const { displayProfileDate } = require('../src/utils/profileDisplayDate')
const filename = path.resolve(__dirname, '../src/views/mobile/profile/index.vue')
const source = fs.readFileSync(filename, 'utf8')
const code = babel.transformSync(source.match(/<script>([\s\S]*?)<\/script>/)[1], { filename, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
const flush = async () => { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
const profile = (extra = {}) => Object.assign({ userId: 7, userName: 'local-test', nickName: '测试用户', phonenumber: '13800000000', email: 'test@example.com', sex: '2', profile: { currentAddress: '合成地址', emergencyContact: '测试联系人', bankAccountMasked: '****0123', companyName: 'TEST公司完整名字', positionNames: 'IT Operations 完整职位' } }, extra)
async function mount(options = {}) {
  const calls = [], pending = [], decisions = [], events = [], commits = []
  const route = Vue.observable({ path: '/mobile/profile', fullPath: '/mobile/profile', query: options.preview ? { preview: '1' } : {} })
  const location = { href: 'unchanged' }
  const api = Object.fromEntries(['getUserProfile', 'updateUserProfile', 'updateUserPwd', 'uploadAvatar'].map(name => [name, (...args) => {
    calls.push({ name, args })
    return new Promise((resolve, reject) => pending.push({ name, args, resolve, reject }))
  }]))
  const module = { exports: {} }
  const windowObject = { addEventListener(name, fn) { events.push(['add', name, fn]) }, removeEventListener(name, fn) { events.push(['remove', name, fn]) } }
  vm.runInNewContext(code, {
    module, exports: module.exports, Promise, Object, Array, String, Number, console, location, window: windowObject,
    require(id) {
      if (id === '@/api/system/user') return api
      if (id === '@/plugins/cache') return { session: { get: () => '0' } }
      if (id === '@/assets/images/profile.jpg') return 'default.jpg'
      if (id === '@/utils/shopContext') return { getSelectedDeptContext: () => ({ deptId: 9, deptType: 'STORE', deptName: '合成门店' }) }
      if (id === '@/utils/passwordResetReminder') return { resetPasswordResetReminderState() {} }
      if (id === '@/utils/profileDisplayDate') return { displayProfileDate }
      if (id === '../mobileNavigation') return { getMobileBottomNav: () => [], getMobileHomePath: () => '/workbench', isMobileBottomNavItemActive: () => false }
      if (id === '../mobileViewport') return { startMobileViewportSync() {}, stopMobileViewportSync() {} }
      if (id === './mobileProfileValidation') return require('../src/views/mobile/profile/mobileProfileValidation')
      if (id === '../mobileErrorMessage') return require('../src/views/mobile/mobileErrorMessage')
      throw new Error(id)
    }
  }, { filename })
  const definition = module.exports.default
  let page
  const router = {
    push(to) { return page.requestLeave().then(allowed => { decisions.push({ to, answer: allowed ? undefined : false }); if (!allowed) throw new Error('navigation aborted') }) },
    replace(target) { route.query = target.query; return Promise.resolve() }
  }
  const store = { getters: { id: 7, permissions: [] }, commit(...args) { commits.push(args) }, dispatch(name) { calls.push({ name, args: [] }); return Promise.resolve() } }
  page = new Vue(Object.assign({}, definition, { beforeCreate() { this.$route = route; this.$router = router; this.$store = store } }))
  page.$confirm = () => Promise.resolve()
  const take = name => { const index = pending.findIndex(x => x.name === name); assert.ok(index >= 0, 'missing ' + name); return pending.splice(index, 1)[0] }
  if (!options.preview) take('getUserProfile').resolve({ data: profile(), roleGroup: '测试角色' })
  await flush()
  return { page, route, definition, api, pending, calls, decisions, commits, location, store, events, take,
    resolve(name, response = {}) { take(name).resolve(response) }, fail(name, error = new Error('network failed')) { take(name).reject(error) },
    count(name) { return calls.filter(x => x.name === name).length }, last(name) { return calls.filter(x => x.name === name).slice(-1)[0] }
  }
}
const scenarios = []
const test = (name, run) => scenarios.push({ name, run })

test('保存快照保留新输入和新银行卡，公开资料没有明文', async h => {
  h.page.profileForm.nickName = '保存昵称'; h.page.profileForm.bankAccount = '6222000012345678'
  const p = h.page.saveProfile()
  h.page.profileForm.nickName = '保存期间新昵称'; h.page.profileForm.bankAccount = '6222000087654321'
  assert.equal(h.last('updateUserProfile').args[0].nickName, '保存昵称')
  assert.equal(h.last('updateUserProfile').args[1].silentError, true)
  h.resolve('updateUserProfile'); assert.equal(await p, false)
  assert.equal(h.page.profileForm.nickName, '保存期间新昵称'); assert.equal(h.page.profileForm.bankAccount, '6222000087654321')
  assert.equal(h.page.user.nickName, '保存昵称'); assert.equal(h.page.user.bankAccount, undefined); assert.equal(h.page.user.profile.bankAccount, undefined)
  assert.equal(h.page.profileData.bankAccountMasked, '****5678'); assert.equal(h.page.profileDirty, true)
  const p2 = h.page.saveProfile(); h.resolve('updateUserProfile'); assert.equal(await p2, true)
  assert.equal(h.page.profileForm.bankAccount, ''); assert.equal(h.page.profileDirty, false)
  assert.equal(h.page.profileData.bankAccountMasked, '****4321')
})

test('纯昵称保存不抹掉其他公开或HR字段，空银行卡不发送', async h => {
  h.page.profileForm.nickName = '新昵称'
  const p = h.page.saveProfile(); assert.equal(h.last('updateUserProfile').args[0].bankAccount, undefined)
  h.resolve('updateUserProfile'); await p
  assert.equal(h.page.profileData.emergencyContact, '测试联系人')
  assert.equal(h.page.profileData.positionNames, 'IT Operations 完整职位')
  assert.equal(h.page.profileData.bankAccountMasked, '****0123')
  assert.equal(h.page.profileDirty, false)
})

test('保存后离开等待真实成功，并复用进行中的保存', async h => {
  h.page.profileForm.nickName = '新昵称'
  const save = h.page.saveProfile()
  const navigation = h.page.$router.push('/target')
  assert.equal(h.page.leavePromptOpen, true); assert.equal(h.decisions.length, 0)
  const choosing = h.page.saveAndLeave()
  assert.equal(h.count('updateUserProfile'), 1); assert.equal(h.decisions.length, 0)
  h.resolve('updateUserProfile'); await save; await choosing; await navigation
  assert.equal(h.decisions[0].answer, undefined); assert.equal(h.page.leavePromptOpen, false)
})

test('保存失败不离开，保留输入并可继续编辑', async h => {
  h.page.profileForm.nickName = '不能丢的昵称'
  const navigation = h.page.$router.push('/target').catch(() => {})
  const choosing = h.page.saveAndLeave(); h.fail('updateUserProfile'); await choosing
  assert.equal(h.decisions.length, 0); assert.equal(h.page.leavePromptOpen, true); assert.ok(h.page.leaveError)
  assert.equal(h.page.profileForm.nickName, '不能丢的昵称')
  h.page.cancelLeave(); await navigation; assert.equal(h.decisions[0].answer, false)
})

test('保存期间又编辑不能用旧成功结果直接离开', async h => {
  h.page.profileForm.nickName = '第一版'
  const navigation = h.page.$router.push('/target').catch(() => {})
  const choosing = h.page.saveAndLeave(); h.page.profileForm.nickName = '第二版'
  h.resolve('updateUserProfile'); await choosing
  assert.equal(h.decisions.length, 0); assert.equal(h.page.profileDirty, true)
  h.page.cancelLeave(); await navigation
  assert.equal(h.page.profileForm.nickName, '第二版')
})

test('放弃修改只完成原导航，不触发保存', async h => {
  h.page.profileForm.nickName = '临时'; h.page.passwordForm.oldPassword = 'private'
  const navigation = h.page.$router.push('/target')
  h.page.discardAndLeave(); await navigation
  assert.equal(h.page.profileForm.nickName, '测试用户'); assert.equal(h.page.passwordForm.oldPassword, '')
  assert.equal(h.count('updateUserProfile'), 0); assert.equal(h.count('updateUserPwd'), 0)
})

test('重复导航不会覆盖第一次目的地和决策', async h => {
  h.page.profileForm.nickName = '临时'
  const first = h.page.$router.push('/first')
  await h.page.$router.push('/second').catch(() => {})
  assert.equal(h.decisions[0].to, '/second'); assert.equal(h.decisions[0].answer, false)
  h.page.discardAndLeave(); await first
  assert.equal(h.decisions[1].to, '/first'); assert.equal(h.decisions[1].answer, undefined)
})

test('返回工作台取消导航后不会用location绕过保护', async h => {
  h.page.profileForm.nickName = '临时'; h.page.goHome(); h.page.cancelLeave(); await flush()
  assert.equal(h.location.href, 'unchanged'); assert.equal(h.decisions[0].answer, false)
})

test('切HR档案和密码标签保留编辑，不出现无损切换确认', async h => {
  h.page.profileForm.nickName = '临时'
  h.page.setSection('hr-profile'); await flush()
  assert.equal(h.page.activeSection, 'hr-profile'); assert.equal(h.page.profileForm.nickName, '临时')
  h.page.setSection('reset-password'); await flush(); assert.equal(h.page.activeSection, 'reset-password')
  h.page.setSection('profile'); await flush(); assert.equal(h.page.profileForm.nickName, '临时')
  assert.equal(h.page.leavePromptOpen, false); assert.equal(h.count('updateUserProfile'), 0)
})

test('密码和资料均有修改时分别成功才能离开', async h => {
  h.page.profileForm.nickName = '新昵称'
  Object.assign(h.page.passwordForm, { oldPassword: 'old-password', newPassword: 'new-password', confirmPassword: 'new-password' })
  const navigation = h.page.$router.push('/target'); const choosing = h.page.saveAndLeave()
  h.resolve('updateUserProfile'); await flush(); assert.equal(h.count('updateUserPwd'), 1)
  assert.equal(h.decisions.length, 0)
  h.resolve('updateUserPwd'); await choosing; await navigation
  assert.equal(h.page.passwordDirty, false); assert.equal(h.page.profileDirty, false)
})

test('密码保存失败或验证失败会留页并保留密码输入', async h => {
  Object.assign(h.page.passwordForm, { oldPassword: 'old-password', newPassword: 'new-password', confirmPassword: 'new-password' })
  const navigation = h.page.$router.push('/target').catch(() => {}); const choosing = h.page.saveAndLeave()
  h.fail('updateUserPwd'); await choosing
  assert.equal(h.page.passwordForm.newPassword, 'new-password'); assert.equal(h.decisions.length, 0)
  h.page.cancelLeave(); await navigation
})

test('无修改可直接离页，刷新仅对未保存内容提醒', async h => {
  await h.page.$router.push('/target'); assert.equal(h.decisions[0].answer, undefined)
  const event = { prevented: false, preventDefault() { this.prevented = true } }
  h.page.warnBeforeUnload(event); assert.equal(event.prevented, false)
  h.page.profileForm.nickName = '临时'; h.page.warnBeforeUnload(event)
  assert.equal(event.prevented, true); assert.equal(event.returnValue, '')
})

test('旧保存成功或失败不能污染外部重载的新资料', async h => {
  h.page.profileForm.nickName = '旧保存'
  const p = h.page.saveProfile()
  h.page.applyLoadedProfile(profile({ nickName: '外部新值' }), '', '')
  h.resolve('updateUserProfile'); assert.equal(await p, false)
  assert.equal(h.page.profileForm.nickName, '外部新值'); assert.equal(h.page.user.nickName, '外部新值')
  assert.equal(h.page.profileSaving, false); assert.equal(h.page.profileMessage, '')
})

test('缓存退出后旧保存静默，重入保留草稿可再保存', async h => {
  h.page.profileForm.nickName = '离开前输入'; const p = h.page.saveProfile()
  h.definition.deactivated.call(h.page); h.fail('updateUserProfile'); await p
  assert.equal(h.page.profileForm.nickName, '离开前输入'); assert.equal(h.page.profileMessage, '')
  h.definition.activated.call(h.page)
  const p2 = h.page.saveProfile(); h.resolve('updateUserProfile'); assert.equal(await p2, true)
})

test('注销先处理草稿，取消时不dispatch登出', async h => {
  h.page.profileForm.nickName = '临时'; h.page.logoutMobile(); await flush()
  h.page.cancelLeave(); await flush()
  assert.equal(h.count('LogOut'), 0); assert.equal(h.location.href, 'unchanged')
  h.page.logoutMobile(); await flush(); h.page.discardAndLeave(); await flush()
  assert.equal(h.count('LogOut'), 1); assert.equal(h.location.href, '/index')
})

test('账号变化让旧成功无效，公开用户不被写入', async h => {
  h.page.profileForm.nickName = '原账号保存'; const p = h.page.saveProfile()
  h.store.getters.id = 8; h.resolve('updateUserProfile'); assert.equal(await p, false)
  assert.equal(h.page.user.nickName, '测试用户'); assert.equal(h.commits.length, 0)
  h.definition.deactivated.call(h.page)
})

async function run() {
  let passed = 0
  for (const scenario of scenarios) {
    const h = await mount(); let timer
    try {
      await Promise.race([scenario.run(h), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('unfinished scenario')), 4000) })])
      await flush(); assert.equal(h.pending.length, 0, 'unsettled API')
      passed++
    } catch (error) { error.message = scenario.name + ': ' + error.message; throw error }
    finally { clearTimeout(timer); h.page.$destroy() }
  }
  const preview = await mount({ preview: true })
  await preview.page.saveProfile(); await preview.page.savePassword()
  assert.equal(preview.calls.length, 0); preview.page.$destroy(); passed++
  for (const [value, expected] of [
    ['TEST COMPANY 长公司名', 'TEST COMPANY 长公司名'], ['IT Operations 职位全称', 'IT Operations 职位全称'],
    ['2026-09-12T00:00:00+08:00', '2026-09-12'], ['2026-09-12T23:59:59Z', '2026-09-12'],
    ['2024-02-29T12:00', '2024-02-29'], ['2026-02-29T12:00', '2026-02-29T12:00'],
    ['2026-09-12', '2026-09-12'], ['This is not a date', 'This is not a date'], ['', ''], [null, null], [5, 5]
  ]) assert.strictEqual(displayProfileDate(value), expected)
  passed++
  const apiFile = path.resolve(__dirname, '../src/api/system/user.js')
  const apiCode = babel.transformSync(fs.readFileSync(apiFile, 'utf8'), { filename: apiFile, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const apiModule = { exports: {} }, requests = []
  vm.runInNewContext(apiCode, { module: apiModule, exports: apiModule.exports, require(id) {
    if (id === '@/utils/request') return options => { requests.push(options); return Promise.resolve() }
    if (id === '@/utils/common') return { parseStrEmpty: value => value == null ? '' : String(value) }
    throw new Error(id)
  } })
  for (const options of [undefined, {}, { silentError: false }, { silentError: 'true' }, { silentError: true, url: '/wrong', method: 'delete', data: { injected: true } }]) {
    await apiModule.exports.getUserProfile(options)
    const actual = requests[requests.length - 1]
    assert.deepStrictEqual(JSON.parse(JSON.stringify(actual)), { url: '/system/user/profile', method: 'get', silentError: Boolean(options && options.silentError === true) })
  }
  passed++
  assert.ok(source.includes('form="mobile-profile-edit-form"') && source.includes('class="profile-save-bar"'))
  const edit = source.slice(source.indexOf('<form id="mobile-profile-edit-form"'), source.indexOf('<section v-else-if="activeSection === \'hr-profile\'"'))
  assert.ok(!edit.includes('identityItems') && !edit.includes('employmentItems') && !edit.includes('contractItems'))
  assert.ok(source.includes('保存后离开') && source.includes('放弃修改') && source.includes('继续编辑'))
  console.log(`mobileProfileDraftNavigation: ${passed} scenarios/groups passed; 0 failed; 0 skipped`)
}
run().catch(error => { console.error(error); process.exitCode = 1 })
