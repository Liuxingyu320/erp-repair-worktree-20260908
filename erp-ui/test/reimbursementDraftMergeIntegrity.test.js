const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const smartFill = require('../src/utils/reimbursementSmartFill')
const clone = value => JSON.parse(JSON.stringify(value))
const tick = async () => { for (let i = 0; i < 16; i++) await Promise.resolve() }
function queue() {
  const calls = []
  return { calls, call(...args) { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); calls.push({ args, resolve, reject }); return promise } }
}
function expense(id, amount, source, sortNo) {
  return { itemId: id, sortNo, expenseType: '交通费', expenseDate: '2026-09-12', merchantName: '商户', description: `费用${id || '新增'}`, claimedAmount: amount, sourceInvoiceId: source == null ? null : source }
}
function fixture() {
  return { reimbursementId: 1, rowVersion: 2, status: 'draft', title: '出差报销', purpose: '出差',
    items: [expense(10, 100, 50, 1), expense(11, 30, null, 2)],
    invoices: [{ invoiceId: 50, itemId: 10, originalName: 'invoice.pdf', recognitionStatus: 'corrected', invoiceDate: '2026-09-12', invoiceTotalAmount: 100, sellerName: '商户', commoditySummary: '出租车' }] }
}
function saved(payload, offset = 20) {
  const remote = clone(payload)
  remote.reimbursementId = payload.reimbursementId || 1
  remote.rowVersion = payload.rowVersion == null ? 0 : payload.rowVersion + 1
  remote.items.forEach((row, index) => { row.itemId = offset + index; row.sortNo = index + 1; row.reimbursementId = remote.reimbursementId })
  remote.invoices = (remote.invoices || []).map(invoice => ({ ...invoice, itemId: (remote.items.find(row => String(row.sourceInvoiceId) === String(invoice.invoiceId)) || {}).itemId || null }))
  return remote
}
function deleted(remote) {
  const value = clone(remote); value.rowVersion++; const invoice = value.invoices.find(row => row.invoiceId === 50)
  value.items = value.items.filter(row => !invoice || row.itemId !== invoice.itemId); value.invoices = value.invoices.filter(row => row.invoiceId !== 50)
  return value
}
function load(mobile, apis, env) {
  const file = path.resolve(__dirname, `../src/views/${mobile ? 'mobile/' : ''}oa/reimbursement/index.vue`)
  const script = fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const code = babel.transformSync(script, { filename: file, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, AbortController, process: { env: {} }, window: { addEventListener() {}, removeEventListener() {}, history: { length: 1 } },
    require(name) {
      if (name === '@/mixins/reimbursementWithdrawRecovery') return { createReimbursementWithdrawRecovery: () => ({}) }
      if (name === '@/mixins/reimbursementExportRecovery') return { default: {}, __esModule: true }
      if (name === '@/mixins/approvalCommandRecovery') return { createApprovalCommandRecovery: () => ({}) }
      if (name === '@/utils/approvalCommandRecovery') return require('../src/utils/approvalCommandRecovery')
      if (name === '@/utils/uiOperationScope') return require('../src/utils/uiOperationScope')
      if (name === '@/utils/todoRouteParams') return require('../src/utils/todoRouteParams')
      if (name === '@/utils/todoActionReturn') return require('../src/utils/todoActionReturn')
      if (name === '@/api/oa/reimbursement') return apis
      if (name === '@/utils/reimbursementSmartFill') return smartFill
      if (name === './mobileReimbursementState') return require('../src/views/mobile/oa/reimbursement/mobileReimbursementState')
      if (name === '@/utils/shopContext') return { getSelectedDeptId: () => env.dept, hasValidatedSelectedDeptContext: () => true }
      if (name === '@/utils/sessionMode') return { buildSessionAuthHeaders: () => ({}), shouldUseSessionCredentials: () => false }
      if (name === '@/utils/requestSecurity') return { safeTrustedApiUrl: () => '/file/upload' }
      if (name === '@/utils/auth') return { getToken: () => '' }
      if (name === '@/utils/urlSecurity') return { sanitizeFileUrl: value => value }
      return {}
    }
  }, { filename: file })
  return module.exports.default
}
function setup(mobile, initial = fixture()) {
  const saves = queue(), deletes = queue(), reads = queue(), submits = queue(), uploads = queue(), env = { dept: 10 }
  const component = load(mobile, { saveReimbursement: saves.call, deleteReimbursementInvoice: deletes.call,
    getReimbursement: reads.call, submitReimbursement: submits.call, uploadReimbursementInvoice: uploads.call }, env)
  const notices = []
  const page = { $route: { query: {} }, $refs: { form: { validate: fn => fn(true), clearValidate() {} } },
    $store: { getters: {}, dispatch: () => Promise.resolve() },
    $modal: { confirm: () => Promise.resolve(), msgError: text => notices.push(['error', text]), msgWarning: text => notices.push(['warning', text]), msgSuccess: text => notices.push(['success', text]) },
    $nextTick: fn => Promise.resolve().then(() => fn && fn()), $set: (object, key, value) => { object[key] = value }, $delete: (object, key) => { delete object[key] } }
  Object.entries(component.methods).forEach(([key, fn]) => { if (typeof fn === 'function') page[key] = fn.bind(page) })
  Object.assign(page, component.data.call(page))
  Object.entries(component.computed || {}).forEach(([key, fn]) => { if (typeof fn === 'function') Object.defineProperty(page, key, { get: fn.bind(page) }) })
  page.mode = 'form'; page.formVisible = true; page.form = page.normalizeForm(clone(initial)); page.captureFormBaseline(); page.submissionAvailable = true
  page.loadList = () => Promise.resolve(); page.refreshTodo = () => {}
  return { mobile, page, component, env, saves, deletes, reads, submits, uploads, notices,
    save: () => mobile ? page.saveDraftOnly() : page.saveDraft(),
    submit: () => mobile ? page.submitForm() : page.saveAndSubmit(),
    remove: invoice => mobile ? page.deleteInvoice(invoice) : page.removeInvoice(invoice),
    refresh: () => mobile ? page.refreshForm() : page.reloadFormDetail(),
    dirty: () => mobile ? page.isFormDirty : page.formDirty,
    open: id => page.openForm(mobile ? id : { reimbursementId: id }) }
}
const cases = []
const test = (name, run) => cases.push({ name, run })
for (const mobile of [false, true]) {
  const label = mobile ? 'H5' : 'PC'
  test(`${label}: save-title-edit → new expense identities → delete invoice → save has no resurrected expense`, async () => {
    const h = setup(mobile), p = h.page, a = h.save()
    p.form.title = '保存期间的新标题'; const remote = saved(h.saves.calls[0].args[0]); h.saves.calls[0].resolve({ data: remote }); await a
    assert.equal(p.form.title, '保存期间的新标题'); assert.equal(p.form.items[0].itemId, 20); assert.equal(p.form.invoices[0].itemId, 20); assert(h.dirty())
    const remove = h.remove(p.form.invoices[0]); await tick(); h.deletes.calls[0].resolve({ data: deleted(remote) }); await remove
    assert.equal(p.form.items.length, 1); assert.equal(p.form.items[0].itemId, 21); assert.equal(p.form.title, '保存期间的新标题')
    const b = h.save(), payload = h.saves.calls[1].args[0]
    assert(!payload.items.some(row => row.itemId === 10 || Number(row.claimedAmount) === 100)); assert.equal(payload.items[0].claimedAmount, mobile ? '30.00' : 30)
    h.saves.calls[1].resolve({ data: saved(payload, 40) }); await b; assert.equal(p.form.items[0].itemId, 40); assert.equal(p.formConflict, null)
  })
  test(`${label}: frozen payload, reorder/add/remove/amount edits map original rows without mutable indices`, async () => {
    const h = setup(mobile), p = h.page, original = p.form.items.slice(), a = h.save()
    p.form.items[1].claimedAmount = 99
    p.form.items = [original[1], expense(null, 7, null, null)]
    p.form.title = '新标题'
    const payload = h.saves.calls[0].args[0]
    assert.equal(Number(payload.items[1].claimedAmount), 30); assert.equal(payload.items.length, 2); assert.equal(payload.title, '出差报销')
    h.saves.calls[0].resolve({ data: saved(payload) }); await a
    assert.equal(p.form.items[0].itemId, 21); assert.equal(Number(p.form.items[0].claimedAmount), 99); assert.equal(p.form.items[1].itemId, null)
    assert(!p.form.items.some(row => row.itemId === 20)); assert(h.dirty())
    const b = h.save(); assert.equal(h.saves.calls[1].args[0].items[0].itemId, 21); assert.equal(h.saves.calls[1].args[0].items[1].claimedAmount, 7)
    h.saves.calls[1].resolve({ data: saved(h.saves.calls[1].args[0], 40) }); await b
    assert.deepStrictEqual(Array.from(p.form.items, row => row.itemId), [40, 41])
  })
  test(`${label}: two unsaved manual rows reordered during save retain their own server IDs`, async () => {
    const initial = fixture(); initial.items = [expense(null, 10, null, null), expense(null, 20, null, null)]; initial.invoices = []
    const h = setup(mobile, initial), p = h.page, a = h.save()
    p.form.items.reverse(); h.saves.calls[0].resolve({ data: saved(h.saves.calls[0].args[0]) }); await a
    assert.deepStrictEqual(Array.from(p.form.items, row => row.itemId), [21, 20]); assert(h.dirty())
  })
  test(`${label}: malformed or ambiguous save response preserves draft and blocks another write`, async () => {
    for (const corrupt of [r => { delete r.items[0].sortNo }, r => { r.items[1].sortNo = 1 }, r => { r.invoices[0].itemId = 999 }]) {
      const h = setup(mobile), p = h.page, a = h.save(), before = clone(p.form), remote = saved(h.saves.calls[0].args[0])
      corrupt(remote); h.saves.calls[0].resolve({ data: remote }); await a
      assert(p.formConflict); assert.equal(p.form.rowVersion, before.rowVersion); assert.equal(p.form.items[0].itemId, 10)
      await h.save(); await h.submit(); assert.equal(h.saves.calls.length, 1); assert.equal(h.submits.calls.length, 0)
    }
  })
  test(`${label}: explicit delete version conflict preserves 100/version2 and blocks save/submit until explicit reload`, async () => {
    const h = setup(mobile), p = h.page, remove = h.remove(p.form.invoices[0]); await tick()
    h.deletes.calls[0].reject(new Error('报销申请已变化，请刷新后重试')); await tick()
    const remote = saved(fixture()); remote.items[0].claimedAmount = 200
    h.reads.calls[0].resolve({ data: remote }); await remove
    assert.equal(p.form.rowVersion, 2); assert.equal(Number(p.form.items[0].claimedAmount), 100); assert.equal(p.formConflict.remote.rowVersion, 3)
    await h.save(); await h.submit(); assert.equal(h.saves.calls.length, 0); assert.equal(h.submits.calls.length, 0)
    const resolve = p.useLatestConflictRecord(); await tick(); h.reads.calls[1].resolve({ data: remote }); await resolve
    assert.equal(p.formConflict, null); assert.equal(p.form.rowVersion, 3); assert.equal(Number(p.form.items[0].claimedAmount), 200)
    const retry = h.save(); assert.equal(Number(h.saves.calls[0].args[0].items[0].claimedAmount), 200); assert.equal(h.saves.calls[0].args[0].rowVersion, 3)
    h.saves.calls[0].resolve({ data: saved(h.saves.calls[0].args[0], 40) }); await retry
  })
  test(`${label}: delete timeout with exact authoritative deletion safely recovers unrelated local edits`, async () => {
    const h = setup(mobile), p = h.page; p.form.items[1].claimedAmount = 99; const remove = h.remove(p.form.invoices[0]); await tick()
    h.deletes.calls[0].reject(new Error('network timeout')); await tick(); h.reads.calls[0].resolve({ data: deleted(fixture()) }); await remove
    assert.equal(p.formConflict, null); assert.equal(p.form.rowVersion, 3); assert.equal(p.form.items.length, 1); assert.equal(Number(p.form.items[0].claimedAmount), 99)
    assert(h.notices.some(([kind, message]) => kind === 'success' && message.includes('已核对')))
  })
  test(`${label}: absent invoice plus concurrent changed expense remains unresolved and never adopts newer version`, async () => {
    const h = setup(mobile), p = h.page, remove = h.remove(p.form.invoices[0]); await tick()
    h.deletes.calls[0].reject(new Error('network timeout')); await tick()
    const remote = deleted(fixture()); remote.items[0].claimedAmount = 200; remote.rowVersion = 4
    h.reads.calls[0].resolve({ data: remote }); await remove
    assert.equal(p.form.rowVersion, 2); assert.equal(p.form.items.length, 2); assert.equal(p.formConflict.kind, 'deleteUnknown'); assert.equal(p.formConflict.remote.rowVersion, 4)
    await h.save(); await h.submit(); assert.equal(h.saves.calls.length, 0); assert.equal(h.submits.calls.length, 0)
    assert(!h.notices.some(([kind]) => kind === 'success'))
  })
  test(`${label}: delete result and authority read both unknown retain recoverable draft/version`, async () => {
    const h = setup(mobile), p = h.page, before = clone(p.form), remove = h.remove(p.form.invoices[0]); await tick()
    h.deletes.calls[0].reject(new Error('network timeout')); await tick(); h.reads.calls[0].reject(new Error('network timeout')); await remove
    assert.deepStrictEqual(clone(p.form), before); assert.equal(p.formConflict.kind, 'deleteUnknown'); assert.equal(p.formConflict.remote, null)
    await h.save(); assert.equal(h.saves.calls.length, 0)
    const recheck = p.reviewFormConflict(); h.reads.calls[1].resolve({ data: deleted(fixture()) }); await recheck
    assert.equal(p.formConflict, null); assert.equal(p.form.items.length, 1)
  })
  test(`${label}: cancel issues no delete; rapid clicks issue one; obsolete callback changes neither new draft nor notices`, async () => {
    const h = setup(mobile), p = h.page
    p.$modal.confirm = () => Promise.reject(new Error('cancel')); await h.remove(p.form.invoices[0]); assert.equal(h.deletes.calls.length, 0)
    p.$modal.confirm = () => Promise.resolve(); const remove = h.remove(p.form.invoices[0]); await tick(); await h.remove(p.form.invoices[0]); assert.equal(h.deletes.calls.length, 1)
    const open = h.open(2); const other = fixture(); other.reimbursementId = 2; other.title = 'new record'; h.reads.calls[0].resolve({ data: other }); await open
    p.form.title = 'typed in new record'; const snapshot = clone(p.form), baseline = p.formBaseline, notices = h.notices.length
    h.deletes.calls[0].reject(new Error('old version conflict')); await remove
    assert.deepStrictEqual(clone(p.form), snapshot); assert.equal(p.formBaseline, baseline); assert.equal(h.notices.length, notices); assert.equal(p.formConflict, null)
  })
  test(`${label}: pending save cannot rewrite another form or finish its newer saving state`, async () => {
    const h = setup(mobile), p = h.page, old = h.save(), oldPayload = clone(h.saves.calls[0].args[0])
    const open = h.open(2); const other = fixture(); other.reimbursementId = 2; h.reads.calls[0].resolve({ data: other }); await open
    const newer = h.save(); h.saves.calls[0].resolve({ data: saved(oldPayload) }); await old
    assert.equal(p.form.reimbursementId, 2); assert.equal(p.saving, true)
    h.saves.calls[1].resolve({ data: saved(h.saves.calls[1].args[0], 40) }); await newer; assert.equal(p.saving, false)
  })
  test(`${label}: submit waits for stable saved content; edits during save cause zero approval calls`, async () => {
    const h = setup(mobile), p = h.page, operation = h.submit(); await tick()
    p.form.title = 'new unsaved title'; h.saves.calls[0].resolve({ data: saved(h.saves.calls[0].args[0]) }); await operation
    assert.equal(h.submits.calls.length, 0); assert.equal(p.form.title, 'new unsaved title'); assert(h.dirty())
    const retry = h.submit(); await tick(); const response = saved(h.saves.calls[1].args[0], 40); h.saves.calls[1].resolve({ data: response }); await tick()
    assert.equal(h.submits.calls.length, 1); assert.equal(h.submits.calls[0].args[0].title, 'new unsaved title'); assert.equal(h.submits.calls[0].args[0].rowVersion, response.rowVersion)
    h.submits.calls[0].resolve({}); await retry
  })
  test(`${label}: normal save failure retains inputs and retry; concurrent saves issue one request`, async () => {
    const h = setup(mobile), p = h.page, before = clone(p.form), failed = h.save(); await h.save(); assert.equal(h.saves.calls.length, 1)
    h.saves.calls[0].reject(Object.assign(new Error('validation failure'), { response: { status: 400 } })); await failed
    assert.deepStrictEqual(clone(p.form), before); assert.equal(p.formConflict, null)
    const retry = h.save(); h.saves.calls[1].resolve({ data: saved(h.saves.calls[1].args[0]) }); await retry; assert.equal(p.form.rowVersion, 3)
  })
  test(`${label}: refresh cannot give dirty old expenses a concurrently updated version`, async () => {
    const h = setup(mobile), p = h.page; p.form.title = 'local'; const refresh = h.refresh(), remote = saved(fixture()); remote.items[0].claimedAmount = 200
    h.reads.calls[0].resolve({ data: remote }); await assert.rejects(refresh, /费用已变化/)
    assert.equal(p.form.rowVersion, 2); assert.equal(Number(p.form.items[0].claimedAmount), 100); assert(p.formConflict)
    await h.save(); assert.equal(h.saves.calls.length, 0)
  })
  test(`${label}: upload/autofill save and later refresh retain new identities while keeping late title input`, async () => {
    const initial = fixture(); initial.items = []; initial.invoices = []
    const h = setup(mobile, initial), p = h.page, file = { name: 'invoice.pdf', size: 20 }
    const operation = mobile ? p.uploadInvoiceFile(file) : p.processInvoiceUpload(file); await tick()
    const invoice = fixture().invoices[0]; invoice.itemId = null
    h.uploads.calls[0].resolve({ data: invoice }); await tick()
    const uploaded = clone(initial); uploaded.rowVersion = 3; uploaded.invoices = [invoice]
    h.reads.calls[0].resolve({ data: uploaded }); await tick()
    assert.equal(h.saves.calls.length, 1); assert.equal(h.saves.calls[0].args[0].items[0].sourceInvoiceId, 50)
    p.form.title = 'upload期间的新标题'; const remote = saved(h.saves.calls[0].args[0]); h.saves.calls[0].resolve({ data: remote }); await operation
    assert.equal(p.form.items[0].itemId, 20); assert.equal(p.form.invoices[0].itemId, 20); assert.equal(p.form.title, 'upload期间的新标题')
    const refresh = h.refresh(); h.reads.calls[1].resolve({ data: remote }); await refresh; assert.equal(p.form.items[0].itemId, 20)
    const remove = h.remove(p.form.invoices[0]); await tick(); h.deletes.calls[0].resolve({ data: deleted(remote) }); await remove
    assert.equal(p.form.items.length, 0)
    const save = h.save(); assert.equal(h.saves.calls[1].args[0].items.length, 0); h.saves.calls[1].resolve({ data: saved(h.saves.calls[1].args[0]) }); await save
    assert.equal(p.formConflict, null)
  })
  for (const field of ['title', 'purpose']) {
    test(`${label}: remote ${field}-only change cannot grant a dirty local draft the newer version`, async () => {
      const h = setup(mobile), p = h.page
      p.form.items[0].claimedAmount = 125
      const before = clone(p.form), refresh = h.refresh(), remote = fixture()
      remote.rowVersion = 3
      remote[field] = '另一窗口已保存的内容'
      h.reads.calls[0].resolve({ data: remote })
      await assert.rejects(refresh, /标题或事由已变化/)
      assert.deepStrictEqual(clone(p.form), before)
      assert(p.formConflict)
      await h.save(); await h.submit()
      assert.equal(h.saves.calls.length, 0); assert.equal(h.submits.calls.length, 0)
      const reload = p.useLatestConflictRecord(); await tick()
      h.reads.calls[1].resolve({ data: remote }); await reload
      assert.equal(p.form[field], remote[field]); assert.equal(p.form.rowVersion, 3)
      assert.equal(Number(p.form.items[0].claimedAmount), 100)
      const save = h.save()
      assert.equal(h.saves.calls[0].args[0][field], remote[field])
      h.saves.calls[0].resolve({ data: saved(h.saves.calls[0].args[0]) }); await save
    })
  }
}
for (const mobile of [false, true]) {
  const label = mobile ? 'H5' : 'PC'
  test(`${label}: late upload cannot refresh, autofill, or save a different reimbursement`, async () => {
    const h = setup(mobile), p = h.page, file = { name: 'old.pdf', size: 20 }
    const upload = mobile ? p.uploadInvoiceFile(file) : p.processInvoiceUpload(file); await tick()
    const open = h.open(2), other = fixture(); other.reimbursementId = 2
    h.reads.calls[0].resolve({ data: other }); await open; p.form.title = 'new draft title'
    const before = clone(p.form), notices = h.notices.length
    h.uploads.calls[0].resolve({ data: fixture().invoices[0] }); await upload
    assert.deepStrictEqual(clone(p.form), before); assert.equal(h.reads.calls.length, 1); assert.equal(h.saves.calls.length, 0); assert.equal(h.notices.length, notices)
  })
  test(`${label}: save timeout blocks blind retry and preserves draft/version`, async () => {
    const h = setup(mobile), p = h.page, original = clone(p.form), save = h.save()
    h.saves.calls[0].reject(new Error('network timeout')); await save
    assert.equal(p.formConflict.kind, 'saveUnknown'); assert.deepStrictEqual(clone(p.form), original)
    await h.save(); await h.submit(); assert.equal(h.saves.calls.length, 1); assert.equal(h.submits.calls.length, 0)
  })
  test(`${label}: obsolete conflict authority response cannot affect another draft`, async () => {
    const h = setup(mobile), p = h.page, remove = h.remove(p.form.invoices[0]); await tick()
    h.deletes.calls[0].reject(new Error('version conflict')); await tick()
    const open = h.open(2), other = fixture(); other.reimbursementId = 2; h.reads.calls[1].resolve({ data: other }); await open
    p.form.title = 'new local'; const baseline = p.formBaseline, notices = h.notices.length
    h.reads.calls[0].resolve({ data: saved(fixture()) }); await remove
    assert.equal(p.form.reimbursementId, 2); assert.equal(p.form.title, 'new local'); assert.equal(p.formBaseline, baseline); assert.equal(p.formConflict, null); assert.equal(h.notices.length, notices)
  })
  test(`${label}: result without detail requires authority proof; canceled recovery leaves writes blocked`, async () => {
    const h = setup(mobile), p = h.page, remove = h.remove(p.form.invoices[0]); await tick()
    h.deletes.calls[0].resolve({}); await tick(); const remote = fixture(); remote.rowVersion = 3
    h.reads.calls[0].resolve({ data: remote }); await remove
    assert(p.formConflict); assert.equal(p.form.rowVersion, 2); assert.equal(p.form.items.length, 2)
    p.$modal.confirm = () => Promise.reject(new Error('cancel')); await p.useLatestConflictRecord(); await h.save()
    assert.equal(h.saves.calls.length, 0); assert.equal(p.form.rowVersion, 2)
  })
}
for (const mobile of [false, true]) {
  test(`${mobile ? 'H5' : 'PC'}: new edits during explicit conflict reload are retained for another review`, async () => {
    const h = setup(mobile), p = h.page
    p.setFormConflict('version', '版本冲突', saved(fixture()))
    const reload = p.useLatestConflictRecord(); await tick(); p.form.title = 'typed after confirmation'
    h.reads.calls[0].resolve({ data: saved(fixture()) }); await reload
    assert.equal(p.form.title, 'typed after confirmation'); assert.equal(p.form.rowVersion, 2); assert(p.formConflict)
    await h.save(); assert.equal(h.saves.calls.length, 0)
  })
}
test('PC queued uploads are bound before waiting and cannot start against a reopened form', async () => {
  const h = setup(false), p = h.page
  let release; p.uploadQueue = new Promise(resolve => { release = resolve })
  const queued = p.uploadInvoice({ file: { name: 'queued.pdf', size: 20 } })
  const open = h.open(2), other = fixture(); other.reimbursementId = 2; h.reads.calls[0].resolve({ data: other }); await open
  release(); await queued
  assert.equal(h.uploads.calls.length, 0); assert.equal(h.saves.calls.length, 0); assert.equal(p.form.reimbursementId, 2); assert.equal(p.uploading, false)
})

async function run() {
  for (const entry of cases) { await entry.run(); console.log(`PASS ${entry.name}`) }
  console.log(`reimbursementDraftMergeIntegrity: ${cases.length} dynamic real-component scenarios passed; 0 failed, 0 skipped`)
}
const timeout = setTimeout(() => { console.error('FAIL unresolved async scenario'); process.exit(1) }, 15000)
run().then(() => clearTimeout(timeout)).catch(error => { clearTimeout(timeout); console.error(error); process.exitCode = 1 })
