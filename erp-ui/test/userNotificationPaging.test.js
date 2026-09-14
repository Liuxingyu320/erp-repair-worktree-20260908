const assert = require('node:assert/strict'), test = require('node:test')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '..'), diagnostic = []
Vue.config.errorHandler = error => diagnostic.push(error)
Vue.config.warnHandler = message => diagnostic.push(Error(message))
test.afterEach(() => assert.deepEqual(diagnostic.splice(0), []))
const tick = async () => { for (let i = 0; i < 6; i++) await Vue.nextTick() }
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const MAX = '9223372036854775807', OLD = '9007199254740993'
function setup(values = new Map()) {
  const requests = [], navigations = [], events = [], listeners = new Map()
  let dept = '10'
  const store = Vue.observable({ getters: { id: '42' } })
  const window = { sessionStorage: { getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value) },
    addEventListener: (type, fn) => listeners.set(type, fn), removeEventListener: type => listeners.delete(type), dispatchEvent: event => events.push(event.type) }
  const cache = {}
  function load(relative) {
    if (cache[relative]) return cache[relative]
    const filename = path.join(root, relative), source = fs.readFileSync(filename, 'utf8')
    const script = relative.endsWith('.vue') ? compiler.parseComponent(source).script.content : source
    const code = babel.transformSync(script, { filename, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
    const module = { exports: {} }
    vm.runInNewContext(code, { module, exports: module.exports, Promise, console, window, document: {}, Event: class { constructor(type) { this.type = type } },
      require(id) {
        if (id === '@/utils/request') return config => { const d = deferred(); requests.push({ config, ...d }); return d.promise }
        if (id === '@/api/system/userNotification') return load('src/api/system/userNotification.js')
        if (id === '@/services/pushRoute') return load('src/services/pushRoute.js')
        if (id === './pushRoute') return load('src/services/pushRoute.js')
        if (id === '@/utils/clientPlatform') return { isMobileClient: () => false }
        if (id === '@/utils/shopContext') return { getSelectedDeptId: () => dept }
        throw Error('Unexpected dependency ' + id)
      }
    }, { filename })
    return (cache[relative] = relative.endsWith('.vue') ? module.exports.default : module.exports)
  }
  const sourcePage = fs.readFileSync(path.join(root, 'src/views/system/userNotification/index.vue'), 'utf8')
  const definition = load('src/views/system/userNotification/index.vue')
  const c = new Vue({ ...definition, beforeCreate() { this.$store = store; this.$router = { push(route) { navigations.push(route); return Promise.resolve() } } } })
  const row = (id = OLD, extra = {}) => ({ notificationId: id, title: '通知', body: '正文', readStatus: '0', routeType: 'OA_SIGN_HR_TASK', routeParams: JSON.stringify({ taskId: '123' }), ...extra })
  function respond(request = requests.at(-1), changes = {}) {
    const params = request.config.params || {}
    request.resolve({ data: { rows: [row()], total: 65, unreadCount: 65, routeTypes: ['OA_SIGN_HR_TASK', 'OA_SIGN_PACKAGE_SIGN'],
      snapshotMaxId: params.snapshotMaxId || MAX, pageNum: params.pageNum || 1, pageSize: params.pageSize || 20, ...changes } })
  }
  return { c, definition, load, sourcePage, requests, values, row, respond, store, events, navigations, listeners, window,
    changeDept(value) { dept = value; c.onScopeChanged() } }
}

test('native health reminders coexist with desktop signing handoff and exact long IDs', async () => {
  const e = setup(); e.respond(); await tick()
  e.c.$route = { path: '/system/user-notification' }
  const sign = e.row(OLD, { routeType: 'OA_SIGN_PACKAGE_SIGN', routeParams: { packageId: MAX } })
  assert.equal(e.c.parseRoute(sign).path, '/sign-package-handoff')
  assert.equal(e.c.parseRoute(sign).query.packageId, MAX)
  e.c.$route = { path: '/mobile/messages' }
  assert.equal(e.c.parseRoute(sign).path, '/mobile/sign-package')
  const health = e.row('123', { routeType: 'HR_HEALTH_CERT_DUE', routeParams: { employeeId: '42', certificateId: MAX } })
  assert.equal(e.c.parseRoute(health).path, '/mobile/hr/health-certificate')
  assert.equal(e.c.parseRoute(health).query.certificateId, MAX)
  health.routeParams.employeeId = '43'
  health.routeParams.recipientUserId = '43'
  assert.equal(e.c.parseRoute(health).path, '/mobile/messages', 'server route payload cannot change the signed-in recipient')
})

test('native notification opened refresh preserves filters and cannot revive a suspended list', async () => {
  const e = setup(); e.respond(); await tick()
  e.c.query.keyword = '合同'; e.c.query.pageNum = 2
  e.definition.mounted.call(e.c)
  e.listeners.get('user-notification-opened')()
  assert.equal(e.requests[1].config.params.keyword, '合同')
  assert.equal(e.requests[1].config.params.pageNum, 2)
  assert.equal(e.requests[1].config.params.snapshotMaxId, MAX)
  e.definition.deactivated.call(e.c)
  e.listeners.get('user-notification-opened')()
  assert.equal(e.requests.length, 2)
  e.respond(e.requests[1], { rows: [e.row('500')] }); await tick()
  assert.equal(e.c.messages[0].notificationId, OLD)
  e.definition.beforeDestroy.call(e.c)
  assert.equal(e.listeners.has('user-notification-opened'), false)
})

test('mobile fallback marks the message read without navigating to the same message page', async () => {
  const e = setup()
  e.c.$route = { path: '/mobile/messages' }
  e.respond(e.requests[0], { rows: [e.row(OLD, { routeType: 'USER_NOTIFICATION', routeParams: {} })] }); await tick()
  const action = e.c.openMessage(e.c.messages[0])
  e.requests[1].resolve({ code: 200 }); await action
  assert.equal(e.c.messages[0].readStatus, '1')
  assert.equal(e.navigations.length, 0)
})

test('initial query, keywords, types, status, date range and page use server filtering with stable snapshot', async () => {
  const e = setup(); assert.equal(e.requests[0].config.url, '/system/user-notification/page')
  e.respond(); await tick(); assert.equal(e.c.messages[0].notificationId, OLD)
  e.c.query.keyword = '张三'; e.c.query.routeType = 'OA_SIGN_HR_TASK'; e.c.query.readStatus = '0'
  e.c.handleDateRange(['2026-01-01', '2026-01-31']); await tick()
  const params = e.requests[1].config.params
  assert.deepEqual(JSON.parse(JSON.stringify(params)), { keyword: '张三', routeType: 'OA_SIGN_HR_TASK', readStatus: '0', startDate: '2026-01-01', endDate: '2026-01-31', pageNum: 1, pageSize: 20, snapshotMaxId: MAX })
  e.respond(); await tick(); e.c.changePage(2); assert.equal(e.requests[2].config.params.pageNum, 2)
  assert.equal(e.requests[2].config.params.snapshotMaxId, MAX)
  e.respond(); await tick(); e.c.changePageSize(50); assert.equal(e.requests[3].config.params.pageSize, 50); assert.equal(e.requests[3].config.params.pageNum, 1)
})
test('older list and failure responses cannot replace newer search result or clear loading', async () => {
  const e = setup(), first = e.requests[0]
  e.c.query.keyword = 'new'; e.c.handleQuery(); const second = e.requests[1]
  e.respond(second, { rows: [e.row('200')] }); await tick()
  first.reject(Error('late failure')); await tick()
  assert.equal(e.c.messages[0].notificationId, '200'); assert.equal(e.c.loadError, ''); assert.equal(e.c.loading, false)
  e.c.handleQuery(); const third = e.requests[2]; e.c.handleQuery(); const fourth = e.requests[3]
  e.respond(third, { rows: [e.row('100')] }); await tick(); assert.equal(e.c.loading, true)
  e.respond(fourth, { rows: [e.row('400')] }); await tick(); assert.equal(e.c.messages[0].notificationId, '400')
})
test('failed page retains page/filter for retry and shows a readable failure', async () => {
  const e = setup(); e.respond(); await tick(); e.c.changePage(3); e.requests[1].reject(Error('offline')); await tick()
  assert.equal(e.c.query.pageNum, 3); assert.equal(e.c.messages.length, 0); assert.match(e.c.loadError, /offline/)
  e.c.loadMessages(); assert.equal(e.requests[2].config.params.pageNum, 3); e.respond(); await tick(); assert.equal(e.c.loadError, '')
})
test('explicit refresh obtains a new cutoff and starts at page one', async () => {
  const e = setup(); e.respond(); await tick(); e.c.query.pageNum = 3; e.c.refreshMessages()
  assert.equal(e.requests[1].config.params.snapshotMaxId, undefined); assert.equal(e.requests[1].config.params.pageNum, 1)
})
for (const invalid of [{ rows: [{ notificationId: 9007199254740992 }] }, { rows: [{ notificationId: '9223372036854775808' }] },
  { rows: [{ notificationId: '0' }] }, { snapshotMaxId: '0', rows: [{ notificationId: '1' }] }, { pageNum: 2 },
  { total: -1 }, { total: null }, { total: false }, { rows: [{ notificationId: '1' }, { notificationId: '1' }] }]) {
  test('invalid page cannot enter message list: ' + JSON.stringify(invalid), async () => {
    const e = setup(); e.respond(e.requests[0], invalid); await tick(); assert.equal(e.c.messages.length, 0); assert.ok(e.c.loadError)
  })
}
test('bulk read is one POST for account cutoff, independent of selected filter/page', async () => {
  const e = setup(); e.respond(); await tick(); e.c.query.keyword = 'some'; e.c.query.readStatus = '0'; e.c.query.pageNum = 3
  const action = e.c.markAllRead(); await tick(); const request = e.requests[1]
  assert.equal(request.config.url, '/system/user-notification/read-all'); assert.equal(request.config.method, 'post')
  assert.deepEqual(JSON.parse(JSON.stringify(request.config.data)), { snapshotMaxId: MAX })
  await e.c.markAllRead(); assert.equal(e.requests.length, 2)
  request.resolve({ data: { changed: 65, snapshotMaxId: MAX, unreadCount: 1 } }); await tick()
  assert.equal(e.requests.length, 3); assert.equal(e.requests[2].config.params.keyword, 'some'); assert.equal(e.c.unreadCount, 1)
  e.respond(e.requests[2], { rows: [], total: 0, unreadCount: 1 }); await action; assert.equal(e.c.markingAll, false)
  assert.ok(e.events.includes('user-notification-changed'))
})
test('unknown bulk response keeps message status and cutoff; error is visible and retry idempotently uses same cutoff', async () => {
  const e = setup(); e.respond(); await tick(); const action = e.c.markAllRead()
  e.requests[1].reject(Error('timeout')); await action; assert.equal(e.c.messages[0].readStatus, '0'); assert.match(e.c.actionError, /暂未确认/)
  e.c.markAllRead(); assert.equal(e.requests[2].config.data.snapshotMaxId, MAX)
})
test('bulk response for the wrong cutoff cannot claim all messages read', async () => {
  const e = setup(); e.respond(); await tick(); const action = e.c.markAllRead()
  e.requests[1].resolve({ data: { snapshotMaxId: '12', unreadCount: 0 } }); await action
  assert.equal(e.c.messages[0].readStatus, '0'); assert.ok(e.c.actionError)
})
test('opening reuses strict route resolver, marks exact long ID once, and navigates after completion', async () => {
  const e = setup(); e.respond(); await tick(); const item = e.c.messages[0]
  const action = e.c.openMessage(item); e.c.openMessage(item); assert.equal(e.requests.length, 2)
  assert.equal(e.requests[1].config.url, '/system/user-notification/' + OLD + '/read'); assert.equal(e.navigations.length, 0)
  e.requests[1].resolve({ code: 200 }); await action
  assert.equal(item.readStatus, '1'); assert.equal(e.navigations[0].path, '/oa/sign-task'); assert.equal(e.navigations[0].query.taskId, '123')
  assert.equal(e.c.parseRoute(e.row('1', { routeType: 'URL', routeParams: '{"url":"https://evil.test"}' })), null)
  assert.equal(e.c.parseRoute(e.row('1', { routeParams: '{"taskId":"javascript:bad"}' })), null)
  assert.equal(e.c.parseRoute(e.row('1', { routeParams: '{"taskId":9007199254740993}' })), null)
})
test('failed single read can still navigate without pretending status was saved', async () => {
  const e = setup(); e.respond(); await tick(); const action = e.c.openMessage(e.c.messages[0])
  e.requests[1].reject(Error('offline')); await action
  assert.equal(e.c.messages[0].readStatus, '0'); assert.equal(e.navigations.length, 1); assert.ok(e.c.actionError)
})
test('account switch invalidates old read list, marking and navigation even before watcher flush', async () => {
  const e = setup(); e.respond(); await tick(); const open = e.c.openMessage(e.c.messages[0]), request = e.requests[1]
  e.store.getters.id = '43'; request.resolve({ code: 200 }); await open; await tick()
  assert.equal(e.navigations.length, 0); assert.equal(e.c.messages.length, 0); assert.equal(e.c.activeScope, '43:10')
  e.respond(); await tick(); const action = e.c.markAllRead(), bulk = e.requests.at(-1)
  e.changeDept('20'); bulk.resolve({ data: { snapshotMaxId: MAX, unreadCount: 0 } }); await action
  assert.equal(e.c.unreadCount, 0); assert.equal(e.c.messages.length, 0); assert.equal(e.c.activeScope, '43:20')
})
test('leaving while a read is in flight prevents stale result or navigation; returning reloads same query', async () => {
  const e = setup(); e.respond(); await tick(); e.c.query.pageNum = 2
  const action = e.c.openMessage(e.c.messages[0]), request = e.requests[1]
  let continued = false; e.definition.beforeRouteLeave.call(e.c, {}, {}, () => { continued = true })
  request.resolve({ code: 200 }); await action; assert.equal(continued, true); assert.equal(e.navigations.length, 0)
  e.definition.activated.call(e.c); assert.equal(e.requests.at(-1).config.params.pageNum, 2)
})
test('recreated page restores only scoped filters, page and browse anchor, never cached message bodies', async () => {
  const e = setup(); e.respond(); await tick(); e.c.query.keyword = 'saved'; e.c.query.pageNum = 3
  const surface = { scrollTop: 550, getBoundingClientRect: () => ({ top: 100 }) }
  const row = { dataset: { notificationId: OLD }, getBoundingClientRect: () => ({ top: 140, bottom: 200 }) }
  e.c.$el = { closest: () => surface, querySelectorAll: () => [row] }
  e.c.saveViewState(); const saved = JSON.parse(e.values.get('erp:message-view:v1:42:10'))
  assert.equal(saved.position.top, 550); assert.equal(saved.position.offset, 40); assert.equal(saved.messages, undefined)
  const next = setup(e.values); assert.equal(next.requests[0].config.params.keyword, 'saved'); assert.equal(next.requests[0].config.params.pageNum, 3)
  const nextSurface = { scrollTop: 10, getBoundingClientRect: () => ({ top: 100 }) }
  next.c.$el = { closest: () => nextSurface, querySelectorAll: () => [{ ...row, getBoundingClientRect: () => ({ top: 350, bottom: 400 }) }] }
  next.respond(); await tick(); assert.equal(nextSurface.scrollTop, 220)
})
test('corrupt saved view and unavailable session storage cannot break message reads', async () => {
  const values = new Map([['erp:message-view:v1:42:10', '{bad']]), e = setup(values)
  assert.equal(e.requests[0].config.params.pageNum, 1)
  e.window.sessionStorage.setItem = () => { throw Error('blocked') }
  e.respond(); await tick(); assert.equal(e.c.loadError, ''); assert.equal(e.c.messages.length, 1)
})
test('legacy API calls retain list/device/single-read routes and current page template compiles', async () => {
  const e = setup(), api = e.load('src/api/system/userNotification.js')
  api.listUserNotifications(); assert.equal(e.requests[1].config.url, '/system/user-notification/list')
  api.registerUserDeviceToken({ token: 'placeholder' }, { authToken: 'scoped' })
  assert.equal(e.requests[2].config.headers.Authorization, 'Bearer scoped')
  assert.equal(e.requests[2].config.url, '/system/user-notification/device-token')
  assert.equal(compiler.compile(compiler.parseComponent(e.sourcePage).template.content).errors.length, 0)
})

test('malformed bulk counters cannot optimistically mark messages read', async () => {
  const e = setup(); e.respond(); await tick(); const action = e.c.markAllRead()
  e.requests[1].resolve({ data: { snapshotMaxId: MAX, changed: null, unreadCount: 0 } }); await action
  assert.equal(e.c.messages[0].readStatus, '0'); assert.equal(e.c.unreadCount, 65); assert.ok(e.c.actionError)
})

test('when read filtering removes the last page, recover the last existing page with the same cutoff', async () => {
  const e = setup(); e.respond(); await tick(); e.c.changePage(4)
  e.respond(e.requests[1], { rows: [], total: 21 }); await tick()
  assert.equal(e.requests[2].config.params.pageNum, 2); assert.equal(e.requests[2].config.params.snapshotMaxId, MAX)
  e.respond(e.requests[2], { rows: [e.row('20')], total: 21 }); await tick()
  assert.equal(e.c.query.pageNum, 2); assert.equal(e.c.messages[0].notificationId, '20'); assert.equal(e.c.loading, false)
})

test('desktop signing navigation failure preserves exact package ID and retries without a second mark-read', async () => {
  const e = setup(); const item = e.row(OLD,{routeType:'OA_SIGN_PACKAGE_SIGN',routeParams:JSON.stringify({packageId:MAX})})
  e.respond(e.requests[0],{rows:[item]});await tick()
  let attempts=0;e.c.$router.push=route=>{e.navigations.push(route);return ++attempts===1?Promise.reject(Error('chunk')):Promise.resolve()}
  const opening=e.c.openMessage(e.c.messages[0]);e.requests[1].resolve({code:200});await opening
  assert.equal(e.navigations[0].path,'/sign-package-handoff');assert.equal(e.navigations[0].query.packageId,MAX)
  assert.ok(e.c.navigationRetry);assert.equal(e.c.messages[0].readStatus,'1')
  await e.c.retryBusinessNavigation();assert.equal(e.requests.length,2);assert.equal(e.navigations.length,2)
  e.changeDept('11');assert.equal(e.c.navigationRetry,null)
})
