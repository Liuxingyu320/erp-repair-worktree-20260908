const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '..')
const plain = value => JSON.parse(JSON.stringify(value))
const tick = async () => { for (let n = 0; n < 4; n++) { await Vue.nextTick(); await Promise.resolve() } }
function deferred() { let resolve, reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail }); return { promise, resolve, reject } }
function storage() { const values = new Map(); return { values, getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) } }
function harness(sharedStorage = storage()) {
  const calls = [], confirmations = [], messages = [], events = []
  const env = { dept: '20', actor: Vue.observable({ getters: { id: '10' } }), window: {
    sessionStorage: sharedStorage, addEventListener() {}, removeEventListener() {}
  } }
  const request = config => { const d = deferred(); calls.push({ config, ...d }); return d.promise }
  function load(relative) {
    const filename = path.join(root, 'src', relative), source = fs.readFileSync(filename, 'utf8')
    const script = relative.endsWith('.vue') ? compiler.parseComponent(source).script.content : source
    const code = babel.transformSync(script, { filename, babelrc: false, configFile: false,
      plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
    const module = { exports: {} }
    vm.runInNewContext(code, { module, exports: module.exports, Promise, window: env.window, require(name) {
      if (name === '@/utils/request') return request
      if (name === '@/api/approval/monitor') return load('api/approval/monitor.js')
      if (name === '@/utils/shopContext') return { getSelectedDeptId: () => env.dept }
      if (name === './approvalUi') return load('views/approval/manage/components/approvalUi.js')
      return {}
    } }, { filename })
    return relative.endsWith('.vue') ? module.exports.default : module.exports
  }
  function component(name, propsData) {
    const options = load(`views/approval/manage/components/${name}.vue`)
    // Exercise real Vue reactivity/watchers and methods; avoid monitor's unrelated initial list loads.
    const page = new Vue({ ...options, created: [], propsData, beforeCreate() {
      this.$store = env.actor; this.$route = Vue.observable({ fullPath: '/approval/manage' })
      this.$confirm = (...args) => { const d = deferred(); confirmations.push({ args, ...d }); return d.promise }
      this.$modal = { msgWarning: message => messages.push(message), msgSuccess: message => messages.push(message) }
    } })
    if (name === 'AdminActionDialog') {
      page.$refs.form = { clearValidate() {}, validate(done) {
        const valid = page.operation.type !== 'reassign' || (!!page.selectedRecipient && page.recipientReady && page.selectedRecipient.userId === page.form.toUserId)
        done(valid && page.form.reason.length >= 2)
      } }
      page.$on('confirm', value => events.push(value))
      page.$on('update:visible', value => { page.visible = value })
    }
    return page
  }
  return { calls, confirmations, messages, events, env, component, storage: sharedStorage }
}
const task = (id = '31') => ({ taskId: id, instanceId: '88', nodeId: '4', taskStatus: 'PENDING', businessRound: 2,
  candidates: [{ candidateId: '45', userId: '6', userName: '原审批人', candidateStatus: 'PENDING', taskId: id }] })
const person = (id = '9007199254740993', account = 'zhangsan.a') => ({ userId: id, userName: '张三', deptName: '采购部', accountName: account })
function operation(target = task()) { return { type: 'reassign', target, candidates: target.candidates, contextKey: `10:20:${target.taskId}` } }
function reply(call, users = [person()], more = false) {
  call.resolve({ data: { rows: users, hasMore: more, taskId: call.config.url.match(/tasks\/(\d+)/)[1], instanceId: '88', fromCandidateId: call.config.params.fromCandidateId } })
}
async function dialog(h, op = operation()) {
  const d = h.component('AdminActionDialog', { visible: false, operation: op, loading: false })
  d.visible = true; await tick(); return d
}
async function selected(h, d, value = person()) {
  reply(h.calls[h.calls.length - 1], [value]); await tick()
  d.form.toUserId = value.userId; d.selectRecipient(value.userId); d.form.reason = '原审批人请假'; await tick()
}
function monitor(h, target = task()) {
  const m = h.component('RuntimeMonitor')
  m.detailEngineMode = 'NATIVE'; m.detail = { instance: { instanceId: '88', businessRound: 2, businessNo: 'PUR-88' }, candidates: target.candidates }
  m.load = () => Promise.resolve(); m.openAdminAction('reassign', target)
  return m
}
function payload(m, recipient = person()) { return { type: 'reassign', target: m.adminOperation.target,
  contextKey: m.adminOperation.contextKey, fromCandidateId: '45', toUserId: recipient.userId, recipient, reason: '原审批人请假' } }

test('actual dialog and API search by text, show same-name department/account, preserve long IDs', async () => {
  const h = harness(), d = await dialog(h)
  assert.equal(h.calls[0].config.url, '/approval/tasks/31/reassign-options')
  assert.equal(h.calls[0].config.params.fromCandidateId, '45'); assert.equal(h.calls[0].config.silentError, true)
  reply(h.calls[0], []); await tick()
  d.searchRecipients('采购部'); reply(h.calls[1], [person(), person('9007199254740994', 'zhangsan.b')]); await tick()
  assert.equal(h.calls[1].config.params.keyword, '采购部')
  assert(d.recipientLabel(d.recipientOptions[0]).includes('张三 · 采购部 · zhangsan.a'))
  assert(d.recipientLabel(d.recipientOptions[1]).includes('zhangsan.b'))
  d.form.toUserId = person().userId; d.selectRecipient(person().userId); d.form.reason = '轮班替换'
  const confirmation = d.confirm(); await tick()
  assert(h.confirmations[0].args[0].includes('张三 · 采购部 · zhangsan.a'))
  h.confirmations[0].resolve(); await confirmation
  assert.equal(h.events[0].toUserId, '9007199254740993'); d.$destroy()
})

test('candidate search reverse order and current failure cannot authorize an old selection', async () => {
  const h = harness(), d = await dialog(h)
  const newer = d.searchRecipients('财务'); reply(h.calls[1], [person('9', 'finance')]); await newer
  reply(h.calls[0], [person('8', 'old')]); await tick()
  assert.equal(d.recipientOptions[0].userId, '9')
  d.form.toUserId = '9'; d.selectRecipient('9'); d.form.reason = '人员调换'
  const failed = d.loadRecipients(2); h.calls[2].reject(new Error('查询失败')); await failed
  assert.equal(d.recipientReady, false); await d.confirm(); assert.equal(h.confirmations.length, 0)
  d.$destroy()
})

test('candidate page retry requests the same page and opening the same task searches again', async () => {
  const h = harness(), d = await dialog(h)
  reply(h.calls[0], [person('8')], true); await tick()
  const failed = d.loadRecipients(2); h.calls[1].reject(new Error('offline')); await failed
  const retry = d.loadRecipients(d.recipientPage + 1); reply(h.calls[2], [person('9')]); await retry
  assert.deepEqual(h.calls.map(c => c.config.params.pageNum), [1, 2, 2])
  assert.deepEqual(Array.from(d.recipientOptions, p => p.userId), ['8', '9'])
  d.visible = false; await tick(); d.visible = true; await tick()
  assert.equal(h.calls.length, 4); assert.equal(d.selectedRecipient, null); d.$destroy()
})

test('candidate response from a different approval instance stays unusable', async () => {
  const h = harness(), d = await dialog(h)
  h.calls[0].resolve({ data: { rows: [person()], hasMore: false, taskId: '31', instanceId: '89', fromCandidateId: '45' } })
  await tick(); assert.equal(d.recipientReady, false); assert.equal(d.recipientOptions.length, 0)
  assert(d.recipientError.includes('上下文不一致')); d.$destroy()
})

test('source candidate and task switches discard old candidates and confirmation', async () => {
  const h = harness(), d = await dialog(h); await selected(h, d)
  const confirm = d.confirm(); await tick()
  d.operation = operation(task('32')); await tick()
  h.confirmations[0].resolve(); await confirm; assert.equal(h.events.length, 0)
  assert.equal(d.selectedRecipient, null); assert.equal(h.calls.at(-1).config.url, '/approval/tasks/32/reassign-options')
  d.form.fromCandidateId = '46'; await tick()
  const latest = h.calls.at(-1); reply(latest, [person('10')]); await tick()
  reply(h.calls[h.calls.length - 2], [person('11')]); await tick()
  assert.equal(d.recipientOptions[0].userId, '10'); d.$destroy()
})

test('confirmation is single-flight and account, department or form changes cancel it', async () => {
  for (const change of [h => { h.env.actor.getters.id = '11' }, h => { h.env.dept = '99' }, (h, d) => { d.form.reason = '后来修改' }]) {
    const h = harness(), d = await dialog(h); await selected(h, d)
    const pending = d.confirm(); d.confirm(); await tick(); assert.equal(h.confirmations.length, 1)
    change(h, d); await tick(); h.confirmations[0].resolve(); await pending
    assert.equal(h.events.length, 0); d.$destroy()
  }
})

test('actual reassign API persists frozen body first and retries the same request after response loss', async () => {
  const h = harness(), m = monitor(h), p = payload(m)
  const first = m.submitAdminAction(p); m.submitAdminAction(p)
  assert.equal(h.calls.length, 1); assert.equal(h.storage.values.size, 1)
  const original = plain(h.calls[0].config.data)
  assert.equal(original.toUserId, '9007199254740993'); assert.equal(original.fromCandidateId, '45')
  h.calls[0].reject(new Error('response lost')); await first
  assert(m.adminOperation.recovery); assert(m.adminActionError.includes('暂未确认'))
  const retry = m.submitAdminAction(p); assert.deepEqual(plain(h.calls[1].config.data), original)
  h.calls[1].resolve({ data: { candidateId: '77' } }); await retry
  assert.equal(h.storage.values.size, 0); assert.equal(m.adminActionVisible, false); m.$destroy()
})

test('reload/reopen unknown command keeps original recipient and blocks payload changes', async () => {
  const shared = storage(), first = harness(shared), a = monitor(first), p = payload(a)
  const pending = a.submitAdminAction(p); first.calls[0].reject(new Error('timeout')); await pending
  const original = plain(first.calls[0].config.data); a.$destroy()
  const next = harness(shared), b = monitor(next)
  assert(b.adminOperation.recovery)
  const d = await dialog(next, b.adminOperation)
  assert.equal(next.calls.length, 0, 'unknown command retries without requiring a now-reassigned source to search again')
  assert.equal(d.selectedRecipient.accountName, 'zhangsan.a')
  await b.submitAdminAction({ ...payload(b), toUserId: '99' }); assert.equal(next.calls.length, 0)
  const retried = b.submitAdminAction(payload(b)); assert.deepEqual(plain(next.calls[0].config.data), original)
  next.calls[0].resolve({ data: {} }); await retried; d.$destroy(); b.$destroy()
})

test('task, round, account and organization context reject stale confirmation without writes', async () => {
  for (const change of [h => { h.env.actor.getters.id = '11' }, h => { h.env.dept = '99' }, (h, m) => { m.adminOperation.target.businessRound = 3 },
    (h, m) => { m.openAdminAction('reassign', task('32')) }]) {
    const h = harness(), m = monitor(h), old = payload(m); change(h, m); await tick()
    await m.submitAdminAction(old); assert.equal(h.calls.length, 0); assert.equal(h.storage.values.size, 0); m.$destroy()
  }
})

test('old request completion cannot close or change a newer task dialog', async () => {
  const h = harness(), m = monitor(h), old = m.submitAdminAction(payload(m))
  m.invalidateAdminContext(); m.detailEngineMode = 'NATIVE'; m.openAdminAction('reassign', task('32'))
  h.calls[0].resolve({ data: {} }); await old
  assert.equal(m.adminOperation.target.taskId, '32'); assert(m.adminActionVisible); assert.equal(h.messages.length, 0); m.$destroy()
})

test('storage failure prevents submission and definite rejection permits corrected selection', async () => {
  const blocked = storage(); blocked.setItem = () => { throw new Error('storage unavailable') }
  const h = harness(blocked), m = monitor(h); await m.submitAdminAction(payload(m))
  assert.equal(h.calls.length, 0); assert(m.adminActionError.includes('storage unavailable')); m.$destroy()
  const other = harness(), n = monitor(other); const rejected = n.submitAdminAction(payload(n))
  other.calls[0].reject(Object.assign(new Error('新审批人无业务审批权限'), { response: { status: 200, data: { code: 500 } } })); await rejected
  assert.equal(other.storage.values.size, 0); assert.equal(n.adminOperation.recovery, null)
  const corrected = n.submitAdminAction(payload(n, person('99')))
  assert.notEqual(other.calls[1].config.data.requestId, other.calls[0].config.data.requestId)
  other.calls[1].resolve({}); await corrected; n.$destroy()
})

test('corrupted recovered recipient cannot show one person while sending another ID', async () => {
  const shared = storage(), h = harness(shared), m = monitor(h), pending = m.submitAdminAction(payload(m))
  h.calls[0].reject(new Error('response lost')); await pending
  const key = [...shared.values.keys()][0], command = JSON.parse(shared.getItem(key))
  command.recipient.userId = '99'; shared.setItem(key, JSON.stringify(command)); m.$destroy()
  const next = harness(shared), reopened = monitor(next)
  assert.equal(reopened.adminActionVisible, false); assert(next.messages.some(message => message.includes('上下文不一致')))
  assert.equal(next.calls.length, 0); reopened.$destroy()
})

test('template compilation retains existing actions and responsive person selection', () => {
  for (const file of ['AdminActionDialog.vue', 'RuntimeMonitor.vue']) {
    const sfc = compiler.parseComponent(fs.readFileSync(path.join(root, 'src/views/approval/manage/components', file), 'utf8'))
    assert.deepEqual(compiler.compile(sfc.template.content).errors, [])
  }
})
