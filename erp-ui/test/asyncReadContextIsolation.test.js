const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')

// Execute the actual SFC methods; explicitly dispatch real hooks/watchers because
// this harness does not mount Vue or automatically schedule its watchers.
let scenarios = 0
const tests = []
const test = (name, run) => tests.push({ name, run })
const tick = async () => { for (let n = 0; n < 10; n++) await Promise.resolve() }
const plain = value => JSON.parse(JSON.stringify(value))
function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}
function queue() {
  const calls = []
  const call = (...args) => { const d = deferred(); calls.push({ ...d, args }); return d.promise }
  return { calls, call }
}
function environment() {
  const listeners = new Map()
  const env = { dept: 10, listeners, window: {
    addEventListener(name, fn) { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(fn) },
    removeEventListener(name, fn) { if (listeners.has(name)) listeners.get(name).delete(fn) },
    history: { length: 1 }
  } }
  env.emit = name => { for (const fn of listeners.get(name) || []) fn() }
  return env
}
function load(relative, mocks = {}, env = environment()) {
  const file = path.resolve(__dirname, '../src', relative)
  const source = fs.readFileSync(file, 'utf8')
  const script = relative.endsWith('.vue') ? source.match(/<script>([\s\S]*?)<\/script>/)[1] : source
  const code = babel.transformSync(script, { filename: file, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, process: { env: {} }, Promise, window: env.window,
    require(name) {
      if (mocks[name]) return mocks[name]
      if (name === '@/mixins/reimbursementExportRecovery') return { default: {}, __esModule: true }
      if (name === '@/mixins/approvalCommandRecovery') return { createApprovalCommandRecovery: () => ({}) }
      if (name === '@/mixins/reimbursementWithdrawRecovery') return { createReimbursementWithdrawRecovery: () => ({}) }
      if (name === '@/utils/uiOperationScope') return require('../src/utils/uiOperationScope')
      if (name === '@/utils/approvalCommandRecovery') return require('../src/utils/approvalCommandRecovery')
      if (name === '@/utils/todoRouteParams') return require('../src/utils/todoRouteParams')
      if (name === '@/utils/todoActionReturn') return require('../src/utils/todoActionReturn')
      if (name === "@/utils/positiveDecimalId") return require("../src/utils/positiveDecimalId")
      if (name === '@/utils/shopContext') return { getSelectedDeptId: () => env.dept,
        getSelectedDeptName: () => `组织${env.dept}`, getSelectedDeptContext: () => ({ deptId: env.dept, isWarehouse: true }),
        isSelectedWarehouse: () => !!env.dept, hasValidatedSelectedDeptContext: () => !!env.dept }
      if (name === '@/utils/reimbursementSmartFill') return require('../src/utils/reimbursementSmartFill')
      if (name === './mobileReimbursementState') return require('../src/views/mobile/oa/reimbursement/mobileReimbursementState')
      if (name === '../mobileHrError') return { mobileHrErrorMessage: (error, fallback) => error.message || fallback }
      if (name === '@/utils/sessionMode') return { buildSessionAuthHeaders: () => ({}), shouldUseSessionCredentials: () => false }
      if (name === '@/utils/urlSecurity') return { sanitizeFileUrl: value => value }
      if (name === '@/utils/requestSecurity') return { safeTrustedApiUrl: () => '/file/upload' }
      if (name === '@/utils/common') return { parseTime: () => '2026-09-12' }
      if (name === '@/utils/auth') return { getToken: () => '' }
      if (name === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
      return {}
    }
  }, { filename: file })
  return relative.endsWith('.vue') ? module.exports.default : module.exports
}
function harness(component) {
  const errors = []
  const page = { errors, $refs: {}, $route: { fullPath: '/test', query: {} },
    $store: { dispatch: () => Promise.resolve(), getters: { id: '1' }, state: { user: { sessionRevision: 1 } } },
    $modal: { confirm: () => Promise.resolve(), msgSuccess() {}, msgError: message => errors.push(message), msgWarning() {} },
    $message: { success() {}, warning() {} }, $auth: { hasPermi: () => true },
    $nextTick: fn => Promise.resolve().then(() => fn && fn()),
    addDateRange(query, range) { return { ...query, params: { beginTime: range[0], endTime: range[1] } } }
  }
  Object.entries(component.methods || {}).forEach(([name, fn]) => { if (typeof fn === 'function') page[name] = fn.bind(page) })
  Object.assign(page, component.data.call(page))
  Object.entries(component.computed || {}).forEach(([name, fn]) => {
    if (typeof fn === 'function') Object.defineProperty(page, name, { get: fn.bind(page), configurable: true })
  })
  return page
}
function watch(component, page, name, ...args) {
  const handler = component.watch[name]
  return (typeof handler === 'function' ? handler : handler.handler).apply(page, args)
}
const record = (id, version = 1) => ({ reimbursementId: id, rowVersion: version, title: `remote-${id}`,
  purpose: 'remote purpose', items: [{ itemId: 1, claimedAmount: '10' }], invoices: [{ invoiceId: version }] })
function hr() {
  const env = environment(), read = queue(), lists = queue()
  const component = load('views/mobile/hr/employee/index.vue', { '@/api/hr/employee': { getHrEmployee: read.call, listHrEmployees: lists.call } }, env)
  return { env, read, lists, component, page: harness(component) }
}
function reimbursement(mobile) {
  const env = environment(), read = queue(), lists = queue()
  const component = load(`views/${mobile ? 'mobile/' : ''}oa/reimbursement/index.vue`, {
    '@/api/oa/reimbursement': { getReimbursement: read.call, listMyReimbursements: lists.call }
  }, env)
  const page = harness(component)
  return { env, read, lists, component, page,
    open: id => page.openForm(mobile ? id : id ? { reimbursementId: id } : undefined),
    refresh: () => mobile ? page.refreshForm() : page.reloadFormDetail(),
    detail: id => mobile ? page.openDetail(id) : page.showDetail(id),
    loading: () => mobile ? page.loading : page.formLoading,
    dirty: () => mobile ? page.isFormDirty : page.formDirty }
}
function purchase() {
  const env = environment(), product = queue(), oe = queue(), gift = queue()
  const component = load('views/inventory/purchase/index.vue', {
    '@/api/inventory/purchase': { listPurchaseProducts: product.call, listPurchaseOeItems: oe.call, listPurchaseGifts: gift.call },
    '@/api/inventory/product': { listProduct: product.call }, '@/api/inventory/oe': { listOe: oe.call },
    '@/api/inventory/gift': { listGift: gift.call }
  }, env)
  const page = harness(component)
  page.form = { orderId: 5, supplierId: 1, supplierName: 'Supplier', details: [] }
  page.openProductSelector()
  const start = type => { page.selectedItemType = type; return page.getProductList() }
  return { env, component, page, product, oe, gift, start }
}
const catalog = (type, id) => ({ status: '0', supplierName: 'Supplier',
  ...(type === 'product' ? { productId: id, productName: `P${id}` } : type === 'oe' ? { oeItemId: id, oeItemName: `O${id}` } : { giftId: id, giftName: `G${id}` }) })
function report() {
  const env = environment(), summary = queue(), warning = queue()
  const component = load('views/inventory/report/index.vue', {
    '@/api/inventory/report': { getReportSummary: summary.call, listStockWarning: warning.call }
  }, env)
  return { env, summary, warning, component, page: harness(component) }
}

test('six real read APIs keep defaults and whitelist silentError without request-parameter override', () => {
  const requests = []
  const mock = { '@/utils/request': value => { requests.push(value); return Promise.resolve() } }
  const reimbursementApi = load('api/oa/reimbursement.js', mock), hrApi = load('api/hr/employee.js', mock)
  const reportApi = load('api/inventory/report.js', mock), query = { pageNum: 2, keyword: 'original' }
  const cases = [
    [reimbursementApi.getReimbursement, 7, '/oa/reimbursement/7'],
    [reimbursementApi.listMyReimbursements, query, '/oa/reimbursement/my'],
    [hrApi.getHrEmployee, 7, '/system/hr/employee/7'],
    [hrApi.listHrEmployees, query, '/system/hr/employee/list'],
    [reportApi.getReportSummary, query, '/inventory/report/summary'],
    [reportApi.listStockWarning, query, '/inventory/report/stock-warning']
  ]
  for (const [call, argument, url] of cases) {
    for (const option of [undefined, {}, { silentError: false }, { silentError: 'true' },
      { silentError: true, url: '/hijacked', method: 'post', params: { pageNum: 99 }, data: 'unexpected' }]) call(argument, option)
    const batch = requests.splice(0)
    assert.deepStrictEqual(batch.map(c => c.silentError === true), [false, false, false, false, true])
    for (const c of batch) {
      assert.equal(c.method, 'get'); assert.equal(c.url, url); assert.equal(c.data, undefined)
      if (argument === query) assert.strictEqual(c.params, query)
    }
  }
})
test('HR A/B reverse preserves newer edited identity and ignores old failure', async () => {
  const h = hr(), p = h.page
  const a = p.openEditor({ userId: 1 }), b = p.openEditor({ userId: 2 })
  h.read.calls[1].resolve({ data: { userId: 2, employeeName: 'B' } }); await b
  p.editing.employeeName = 'typed B'
  h.read.calls[0].resolve({ data: { userId: 1 } }); await a
  assert.equal(p.editing.userId, 2); assert.equal(p.editing.employeeName, 'typed B'); assert(p.editorOpen)
  const old = p.openEditor({ userId: 1 }), current = p.openEditor({ userId: 2 })
  h.read.calls[3].reject(new Error('current failure')); await current
  h.read.calls[2].reject(new Error('obsolete failure')); await old
  assert.equal(p.message, 'current failure')
  const retry = p.openEditor({ userId: 2 }); h.read.calls[4].resolve({ data: { userId: 2 } }); await retry
  assert.equal(p.message, '')
  assert(h.read.calls.every(c => c.args[1].silentError === true))
})
test('HR close/reopen same employee, route, keep-alive and organization events', async () => {
  const h = hr(), p = h.page
  const old = p.openEditor({ userId: 1 }); p.closeEditor()
  const current = p.openEditor({ userId: 1 }); h.read.calls[1].resolve({ data: { userId: 1, marker: 'new' } }); await current
  h.read.calls[0].resolve({ data: { userId: 1, marker: 'old' } }); await old
  assert.equal(p.editing.marker, 'new')
  const route = p.openEditor({ userId: 2 }); watch(h.component, p, '$route.fullPath')
  h.read.calls[2].resolve({ data: { userId: 2 } }); await route; assert.equal(p.editorOpen, false)
  const pending = p.openEditor({ userId: 3 }); h.component.deactivated.call(p)
  h.component.activated.call(p)
  h.read.calls[3].resolve({ data: { userId: 3 } }); await pending; assert.equal(p.editing.marker, 'new')
  const org = p.openEditor({ userId: 4 }); p.bindDeptListener(); h.env.dept = 20; h.env.emit('erp:dept-changed')
  h.read.calls[4].reject(new Error('old dept')); await org; assert.notEqual(p.message, 'old dept')
  h.component.beforeDestroy.call(p)
  assert.equal(h.env.listeners.get('erp:dept-changed').size, 0)
  const count = h.read.calls.length; await p.openEditor({ userId: 5 }); assert.equal(h.read.calls.length, count)
  assert(h.lists.calls.every(c => c.args[1].silentError === true))
  h.lists.calls.forEach(c => c.resolve({ rows: [], total: 0 })); await tick()
})
test('HR pending route-focus list cannot replace editor explicitly chosen by user', async () => {
  const h = hr(), p = h.page; p.$route.query.userId = '1'
  const list = p.reload(), edit = p.openEditor({ userId: 2 })
  h.read.calls[0].resolve({ data: { userId: 2 } }); await edit
  h.lists.calls[0].resolve({ rows: [{ userId: 1 }] }); await list
  assert.equal(h.read.calls.length, 1); assert.equal(p.editing.userId, 2)
})
for (const mobile of [false, true]) {
  const label = mobile ? 'H5' : 'PC'
  test(`${label} reimbursement A/B reverse, current loading and silent obsolete error`, async () => {
    const h = reimbursement(mobile), p = h.page
    const a = h.open(1), b = h.open(2)
    h.read.calls[0].reject(new Error('obsolete')); await a; assert(h.loading()); assert.equal(p.errors.length, 0); if (mobile) assert.equal(p.error, '')
    h.read.calls[1].resolve({ data: record(2, 2) }); await b
    const baseline = p.formBaseline; p.form.title = 'typed'; assert(h.dirty())
    assert.equal(p.form.reimbursementId, 2); assert.equal(p.form.rowVersion, 2); assert.equal(p.form.invoices[0].invoiceId, 2)
    assert.equal(p.formBaseline, baseline); assert.equal(h.loading(), false)
    assert(h.read.calls.every(c => c.args[1].silentError === true))
    const c = h.open(3), d = h.open(4)
    h.read.calls[3].resolve({ data: record(4, 4) }); await d; p.form.title = 'latest edit'; const latestBaseline = p.formBaseline
    h.read.calls[2].resolve({ data: record(3) }); await c
    assert.equal(p.form.title, 'latest edit'); assert.equal(p.form.reimbursementId, 4); assert.equal(p.formBaseline, latestBaseline); assert(h.dirty())
  })
  test(`${label} same-document reload order preserves local edits, versions and invoice ownership`, async () => {
    const h = reimbursement(mobile), p = h.page
    const initial = h.open(1); h.read.calls[0].resolve({ data: record(1) }); await initial
    const a = h.refresh(), b = h.refresh(); p.form.title = 'local title'; p.form.purpose = 'local purpose'; p.form.items[0].claimedAmount = '99'
    h.read.calls[2].resolve({ data: record(1, 3) }); await b
    const baseline = p.formBaseline
    h.read.calls[1].resolve({ data: record(1, 2) }); await a
    assert.equal(p.form.rowVersion, 3); assert.equal(p.form.invoices[0].invoiceId, 3)
    assert.equal(p.form.title, 'local title'); assert.equal(p.form.purpose, 'local purpose'); assert.equal(p.form.items[0].claimedAmount, '99')
    assert.equal(p.formBaseline, baseline); assert(h.dirty())
    const c = h.refresh(), d = h.refresh(); h.read.calls[4].resolve({ data: record(1, 5) }); await d
    h.read.calls[3].reject(new Error('stale reload failure')); await c; assert.equal(p.errors.length, 0)
    const currentFailure = h.refresh(); h.read.calls[5].reject(new Error('current reload failure'))
    await assert.rejects(currentFailure, /current reload failure/)
  })
  test(`${label} close/reopen same id and new unsaved draft invalidate older reads`, async () => {
    const h = reimbursement(mobile), p = h.page
    const a = h.open(1)
    if (mobile) { const list = p.loadList(); h.lists.calls[0].resolve({ rows: [] }); await list }
    else { p.formVisible = false; p.handleFormClosed() }
    const b = h.open(1); h.read.calls[1].resolve({ data: record(1, 2) }); await b
    h.read.calls[0].resolve({ data: record(1) }); await a; assert.equal(p.form.rowVersion, 2)
    const c = h.refresh(); h.open(); p.form.title = 'new unsaved'; const baseline = p.formBaseline
    h.read.calls[2].resolve({ data: record(1, 3) }); await c
    assert(!p.form.reimbursementId); assert.equal(p.form.title, 'new unsaved'); assert.equal(p.formBaseline, baseline); assert(h.dirty())
    const count = h.read.calls.length; await h.refresh(); assert.equal(h.read.calls.length, count, 'new drafts must not GET undefined')
    if (!mobile) { p.handleFormClosed(); assert.equal(p.form.title, 'new unsaved', 'late dialog closed hook must not reset reopened form') }
  })
  test(`${label} form/detail transitions reject old response and old finally`, async () => {
    const h = reimbursement(mobile), p = h.page
    const a = h.open(1), detail = h.detail(2)
    h.read.calls[0].resolve({ data: record(1) }); await a
    assert(mobile ? p.loading : p.detailLoading)
    const c = h.open(3); h.read.calls[1].reject(new Error('old detail')); await detail
    assert(h.loading()); assert.equal(p.errors.length, 0); if (mobile) assert.equal(p.error, '')
    h.read.calls[2].resolve({ data: record(3) }); await c; assert.equal(p.form.reimbursementId, 3)
    const goodDetail = h.detail(4); h.read.calls[3].resolve({ data: record(4) }); await goodDetail
    assert.equal(p.detail.reimbursementId, 4); assert.equal(mobile ? p.loading : p.detailLoading, false)
  })
  test(`${label} keep-alive, live organization check, listener cleanup and retry`, async () => {
    const h = reimbursement(mobile), p = h.page
    const a = h.open(1); h.component.deactivated.call(p); h.component.activated.call(p)
    // H5 activation performs a fresh load of the active form.
    if (mobile) { h.read.calls[1].resolve({ data: record(1, 2) }); await tick() }
    h.read.calls[0].resolve({ data: record(1) }); await a
    assert.notEqual(p.form.rowVersion, 1)
    const b = h.open(2); h.env.dept = 20
    h.read.calls.at(-1).reject(new Error('old dept without event')); await b
    assert.equal(p.errors.length, 0); if (mobile) assert.equal(p.error, '')
    h.env.emit('erp:dept-changed'); assert.equal(h.loading(), false)
    const failed = h.open(3); h.read.calls.at(-1).reject(new Error('current open failure')); await failed
    assert(mobile ? p.error.includes('current open failure') : p.errors.at(-1).includes('current open failure'))
    const retry = h.open(3); h.read.calls.at(-1).resolve({ data: record(3) }); await retry
    assert.equal(p.form.reimbursementId, 3); assert.equal(h.loading(), false)
    if (mobile) { p.cancelInvoiceBatch = () => {}; p.releaseInvoicePreviewUrl = () => {} }
    else p.clearPreview = () => {}
    h.component.beforeDestroy.call(p); assert.equal(h.env.listeners.get('erp:dept-changed').size, 0)
    const count = h.read.calls.length; h.open(9); assert.equal(h.read.calls.length, count)
  })
}
for (const mobile of [false, true]) {
  test(`${mobile ? 'H5' : 'PC'} refresh superseding pending open owns and releases loading`, async () => {
    const h = reimbursement(mobile), p = h.page, open = h.open(1), refresh = h.refresh()
    h.read.calls[0].resolve({ data: record(1) }); await open; assert(h.loading())
    h.read.calls[1].resolve({ data: record(1, 2) }); await refresh
    assert.equal(p.form.rowVersion, 2); assert.equal(h.loading(), false)
  })
}
test('H5 keep-alive activation retains saved and no-id unsaved drafts', async () => {
  const h = reimbursement(true), p = h.page
  const initial = h.open(1); h.read.calls[0].resolve({ data: record(1) }); await initial
  p.form.title = 'unsaved saved-document title'; h.component.deactivated.call(p); h.component.activated.call(p)
  h.read.calls[1].resolve({ data: record(1, 2) }); await tick()
  assert.equal(p.form.title, 'unsaved saved-document title'); assert.equal(p.form.rowVersion, 2); assert(h.dirty()); assert.equal(h.loading(), false)
  h.open(); p.form.title = 'unsaved no-id draft'; const baseline = p.formBaseline
  h.component.deactivated.call(p); h.component.activated.call(p); await tick()
  assert.equal(h.read.calls.length, 2); assert.equal(h.lists.calls.length, 0)
  assert.equal(p.mode, 'form'); assert.equal(p.form.title, 'unsaved no-id draft'); assert.equal(p.formBaseline, baseline); assert(h.dirty())
})
test('H5 activation refresh failure keeps dirty form visible and a later refresh preserves it', async () => {
  const h = reimbursement(true), p = h.page, initial = h.open(1)
  h.read.calls[0].resolve({ data: record(1) }); await initial; p.form.title = 'retain me'
  h.component.deactivated.call(p); h.component.activated.call(p)
  h.read.calls[1].reject(new Error('activation failure')); await tick()
  assert.equal(p.form.title, 'retain me'); assert.equal(p.error, ''); assert(p.errors.at(-1).includes('activation failure')); assert(h.dirty())
  const retry = h.refresh(); h.read.calls[2].resolve({ data: record(1, 3) }); await retry
  assert.equal(p.form.title, 'retain me'); assert.equal(p.form.rowVersion, 3); assert(h.dirty())
})
test('PC route detail starts independently of list and late reads cannot overwrite a draft or keep-alive refresh', async () => {
  for (const action of ['draft', 'leave']) {
    const h = reimbursement(false), p = h.page, list = deferred()
    p.$route.query.reimbursementId = '9'; p.loadAvailability = () => {}; p.loadList = () => list.promise
    h.component.created.call(p)
    assert.equal(h.read.calls.length, 1)
    if (action === 'draft') { h.open(); p.form.title = 'draft' }
    else { h.component.deactivated.call(p); h.component.activated.call(p); assert.equal(h.read.calls.length, 2) }
    h.read.calls[0].resolve({ data: record(9) }); await tick()
    if (action === 'draft') assert.equal(p.form.title, 'draft')
    else { assert.equal(p.detailLoading, true); h.read.calls[1].resolve({ data: record(9, 2) }); await tick(); assert.equal(p.detail.rowVersion, 2) }
    list.resolve(); await tick(); assert.equal(h.read.calls.length, action === 'draft' ? 1 : 2)
  }
})
test('PC route change and H5 actual back-to-list leave current form reads obsolete', async () => {
  const pc = reimbursement(false), a = pc.open(1)
  watch(pc.component, pc.page, '$route.fullPath'); pc.read.calls[0].resolve({ data: record(1) }); await a
  assert.notEqual(pc.page.form.rowVersion, 1)
  const h = reimbursement(true), b = h.open(1)
  await h.page.goBack(); h.lists.calls[0].resolve({ rows: [{ reimbursementId: 8 }], total: 1 }); await tick()
  h.read.calls[0].reject(new Error('old form')); await b
  assert(h.lists.calls.every(c => c.args[1].silentError === true))
  assert.equal(h.page.mode, 'list'); assert.equal(h.page.rows[0].reimbursementId, 8); assert.equal(h.page.error, '')
})

test('purchase product→OE and OE→gift reversed replies retain captured item identity', async () => {
  for (const [oldType, newType] of [['product', 'oe'], ['oe', 'gift']]) {
    const h = purchase(), p = h.page
    const a = h.start(oldType), b = h.start(newType)
    h[newType].calls[0].resolve({ rows: [catalog(newType, 20)], total: 1 }); await b
    p.handleProductSelectionChange([p.productList[0]])
    h[oldType].calls[0].resolve({ rows: [catalog(oldType, 10)], total: 99 }); await a
    assert.equal(p.productList[0].itemType, newType); assert.equal(p.productList[0].itemId, 20)
    assert.equal(p.productTotal, 1); assert.equal(p.productSelection[0].itemId, 20); assert.equal(p.productLoading, false)
    p.confirmProductSelection(); assert.equal(p.form.details[0].itemType, newType); assert.equal(p.form.details[0].itemId, 20)
  }
})
test('purchase normal product add and same-type search/page reverse with old failure', async () => {
  const h = purchase(), p = h.page
  const a = h.start('product'); p.productQuery.productName = 'new'; p.productQuery.pageNum = 2
  const b = p.getProductList(); watch(h.component, p, 'productQuery')
  h.product.calls[0].reject(new Error('obsolete')); await a; assert(p.productLoading); assert.equal(p.errors.length, 0)
  h.product.calls[1].resolve({ rows: [catalog('product', 22)], total: 25 }); await b
  assert.equal(p.productList[0].itemId, 22); assert.equal(h.product.calls[1].args[0].keyword, 'new'); assert.equal(h.product.calls[1].args[0].pageNum, 2)
  p.handleProductSelectionChange([p.productList[0]]); p.confirmProductSelection(); assert.equal(p.form.details[0].productId, 22)
  assert(h.product.calls.every(c => c.args[1].silentError === true))
})
test('purchase canceled type, supplier, order, close/reopen, route and live organization invalidate reads', async () => {
  for (const action of ['type', 'supplier', 'order', 'close', 'route', 'dept', 'silentDept', 'leave']) {
    const h = purchase(), p = h.page, old = h.start('product')
    if (action === 'type') { p.selectedItemType = null; p.handleItemTypeChange() }
    if (action === 'supplier') { p.form.supplierId = 2; p.supplierOptions = [{ supplierId: 2, supplierName: 'Other' }]; p.onSupplierChange(2) }
    if (action === 'order') { p.loadSuppliers = () => {}; p.openForm(null) }
    if (action === 'close') { p.productDialogOpen = false; p.handleProductSelectorClose(); p.openProductSelector() }
    if (action === 'route') watch(h.component, p, '$route.fullPath')
    if (action === 'dept') { p.bindProductDeptListener(); h.env.dept = 20; h.env.emit('erp:dept-changed') }
    if (action === 'silentDept') h.env.dept = 20
    if (action === 'leave') { h.component.deactivated.call(p); h.component.activated.call(p) }
    h.product.calls[0].resolve({ rows: [catalog('product', 1)], total: 100 }); await old
    assert.equal(p.productList.length, 0, action); assert.equal(p.productTotal, 0, action)
    if (action !== 'silentDept') assert.equal(p.productLoading, false, action)
  }
})
test('purchase current error is visible, retry works and listener removed at destroy', async () => {
  const h = purchase(), p = h.page
  p.bindProductDeptListener(); const a = h.start('product'); h.product.calls[0].reject(new Error('failure')); await a
  assert.equal(p.errors.length, 1); assert.equal(p.productLoading, false)
  const retry = p.getProductList(); h.product.calls[1].resolve({ rows: [catalog('product', 2)], total: 1 }); await retry
  assert.equal(p.productList[0].itemId, 2)
  h.component.beforeDestroy.call(p); assert.equal(h.env.listeners.get('erp:dept-changed').size, 0)
})

test('report Aug/Sept reverse isolates values, totals and old finally', async () => {
  const h = report(), p = h.page
  p.dateRange = ['2026-08-01', '2026-08-31']; const a = p.loadData()
  p.dateRange = ['2026-09-01', '2026-09-30']; watch(h.component, p, 'dateRange')
  const b = p.loadData()
  h.summary.calls[0].resolve({ data: { salesAmount: 8 } }); h.warning.calls[0].reject(new Error('obsolete')); await a
  assert(p.summaryLoading); assert(p.warningLoading); assert.equal(p.errors.length, 0)
  h.summary.calls[1].resolve({ data: { salesAmount: 9 } }); h.warning.calls[1].resolve({ rows: [{ productId: 9 }], total: 90 }); await b
  assert.equal(p.summary.salesAmount, 9); assert.equal(p.warningTotal, 90); assert.equal(p.warningList[0].productId, 9)
  assert.equal(h.summary.calls[1].args[0].params.beginTime, '2026-09-01')
  assert([...h.summary.calls, ...h.warning.calls].every(c => c.args[1].silentError === true))
})
test('report late old success after new result cannot overwrite; page change leaves summary alive', async () => {
  const h = report(), p = h.page
  const a = p.loadData(); p.queryParams.productId = 9; const b = p.loadData()
  h.summary.calls[1].resolve({ data: { salesAmount: 9 } }); h.warning.calls[1].resolve({ rows: [{ productId: 9 }], total: 9 }); await b
  h.summary.calls[0].resolve({ data: { salesAmount: 1 } }); h.warning.calls[0].resolve({ rows: [{ productId: 1 }], total: 1 }); await a
  assert.equal(p.summary.salesAmount, 9); assert.equal(p.warningTotal, 9)
  const summary = p.getSummary(), firstPage = p.getWarningList()
  p.queryParams.pageNum = 2; watch(h.component, p, 'queryParams'); const nextPage = p.getWarningList()
  h.warning.calls[2].resolve({ rows: [{ productId: 1 }], total: 1 }); await firstPage; assert(p.warningLoading)
  h.summary.calls[2].resolve({ data: { salesAmount: 20 } }); await summary
  h.warning.calls[3].resolve({ rows: [{ productId: 2 }], total: 20 }); await nextPage
  assert.equal(p.summary.salesAmount, 20); assert.equal(p.warningList[0].productId, 2)
})
test('report unsubmitted filters clear misleading old values; status, reset, organization and lifecycle work', async () => {
  const h = report(), p = h.page
  const first = p.loadData(); h.summary.calls[0].resolve({ data: { salesAmount: 1 } }); h.warning.calls[0].resolve({ rows: [{ productId: 1 }], total: 1 }); await first
  p.queryParams.stockStatus = 'low'; watch(h.component, p, 'queryParams')
  assert.deepStrictEqual(plain(p.summary), {}); assert.equal(p.warningList.length, 0)
  const pending = p.loadData(); p.resetQuery(); watch(h.component, p, 'queryParams')
  h.summary.calls[1].resolve({ data: { salesAmount: 1 } }); h.warning.calls[1].resolve({ rows: [{ productId: 1 }], total: 1 }); await pending
  assert(p.summaryLoading); assert(p.warningLoading)
  p.bindDeptListener(); h.env.dept = 20; h.env.emit('erp:dept-changed')
  assert.equal(p.currentDeptId, 20); assert(p.currentDeptLabel.includes('20'))
  h.summary.calls[2].reject(new Error('old reset')); h.warning.calls[2].reject(new Error('old reset')); await tick()
  assert.equal(p.errors.length, 0)
  h.summary.calls[3].resolve({ data: { salesAmount: 20 } }); h.warning.calls[3].resolve({ rows: [{ productId: 20 }], total: 20 }); await tick()
  assert.equal(p.summary.salesAmount, 20)
  const old = p.loadData(); h.component.deactivated.call(p); h.component.activated.call(p)
  h.summary.calls[4].resolve({ data: { salesAmount: 40 } }); h.warning.calls[4].reject(new Error('old life')); await old
  assert(p.summaryLoading); assert(p.warningLoading); assert.equal(p.errors.length, 0)
  h.summary.calls[5].resolve({ data: { salesAmount: 50 } }); h.warning.calls[5].resolve({ rows: [{ productId: 50 }], total: 50 }); await tick()
  h.component.beforeDestroy.call(p); assert.equal(h.env.listeners.get('erp:dept-changed').size, 0)
})
test('report live organization check rejects a response even without event; current failure permits retry', async () => {
  const h = report(), p = h.page
  const old = p.loadData(); h.env.dept = 20
  h.summary.calls[0].resolve({ data: { salesAmount: 10 } }); h.warning.calls[0].reject(new Error('old org')); await old
  assert.deepStrictEqual(plain(p.summary), {}); assert.equal(p.errors.length, 0)
  const failed = p.loadData(); h.summary.calls[1].reject(new Error('summary')); h.warning.calls[1].reject(new Error('warning')); await failed
  assert.equal(p.errors.length, 2); assert.equal(p.summaryLoading, false); assert.equal(p.warningLoading, false)
  const retry = p.loadData(); h.summary.calls[2].resolve({ data: { salesAmount: 30 } }); h.warning.calls[2].resolve({ rows: [], total: 0 }); await retry
  assert.equal(p.summary.salesAmount, 30)
  const count = h.summary.calls.length; h.env.dept = null; await p.loadData(); assert.equal(h.summary.calls.length, count); assert.deepStrictEqual(plain(p.summary), {})
})

async function run() {
  for (const { name, run } of tests) {
    await run(); scenarios++; console.log(`PASS ${name}`)
  }
  console.log(`asyncReadContextIsolation: ${scenarios} real-component/API scenarios passed`)
}
const timeout = setTimeout(() => { console.error('FAIL unresolved behavior test'); process.exit(1) }, 10000)
run().then(() => clearTimeout(timeout)).catch(error => { clearTimeout(timeout); console.error(error); process.exitCode = 1 })
