const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')

const WEEK_A = '2026-09-07'
const WEEK_B = '2026-09-14'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

function loadComponent(api) {
  const file = path.resolve(__dirname, '../src/views/oa/attendance/components/WeeklySchedule.vue')
  const script = fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const code = babel.transformSync(script, {
    filename: file,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')]
  }).code
  const sandboxModule = { exports: {} }
  vm.runInNewContext(code, {
    module: sandboxModule,
    exports: sandboxModule.exports,
    process: { env: {} },
    Promise,
    require(name) {
      if (name === '@/api/oa/attendanceV2') return api
      if (name === '@/utils/permission') return { checkPermi: () => true }
      return {}
    }
  }, { filename: file })
  return sandboxModule.exports.default
}

function harness(component) {
  const instance = {
    $refs: {},
    $set(target, key, value) { target[key] = value },
    $delete(target, key) { delete target[key] },
    $modal: { confirm: () => Promise.resolve(), msgSuccess() {}, msgError() {}, msgWarning() {} },
    $nextTick: () => Promise.resolve()
  }
  Object.entries(component.methods).forEach(([key, fn]) => {
    if (typeof fn === 'function') instance[key] = fn.bind(instance)
  })
  Object.assign(instance, component.data.call(instance))
  Object.entries(component.computed || {}).forEach(([key, fn]) => {
    if (typeof fn === 'function') Object.defineProperty(instance, key, { get: fn.bind(instance), configurable: true })
  })
  return instance
}

function track(bucket) {
  return (params, options) => {
    const item = deferred()
    const entry = { params, options, ...item }
    entry.resolve = response => { entry.response = response; item.resolve(response) }
    bucket.push(entry)
    return item.promise
  }
}

function createApi() {
  const log = { shifts: [], sites: [], schedules: [], employees: [], saves: [], publishes: [], deletes: [] }
  return {
    log,
    api: {
      listAttendanceShifts: track(log.shifts),
      listAttendanceSites: track(log.sites),
      listAttendanceSchedules: track(log.schedules),
      listAttendanceEmployeeOptions: track(log.employees),
      saveAttendanceScheduleBatch(data) {
        const item = deferred()
        log.saves.push({ data, ...item })
        return item.promise
      },
      publishAttendanceSchedules(data) {
        log.publishes.push(data)
        const source = latest(log.schedules).response.data
        return Promise.resolve({ data: data.scheduleIds.map(id => {
          const row = source.find(value => String(value.scheduleId) === String(id))
          assert.ok(row, 'publish fixture requires the authoritative frozen row')
          return Object.assign({}, row, { shopId: data.shopId, status: 'PUBLISHED', rowVersion: (BigInt(row.rowVersion) + 1n).toString() })
        }) })
      },
      deleteAttendanceSchedule(scheduleId, rowVersion) {
        const item = deferred()
        log.deletes.push({ scheduleId, rowVersion, ...item })
        return item.promise
      }
    }
  }
}

function draftRow(overrides) {
  return Object.assign({
    scheduleId: 101,
    userId: 1,
    nickName: '甲',
    businessDate: WEEK_A,
    shiftId: 1,
    siteId: 5,
    status: 'DRAFT',
    rowVersion: 3
  }, overrides)
}

function employeeA() { return { userId: 1, nickName: '甲' } }
function employeeB() { return { userId: 2, nickName: '乙' } }


// Successful service responses contain every submitted row, including rows the
// scenario is not otherwise interested in. Business race values stay explicit;
// the service contract advances every existing version exactly once.
function completeSavedBatch(ctx, overrides = []) {
  const request = latest(ctx.log.saves).data
  return { data: Array.from(request.items).map(item => {
    const explicit = overrides.find(row => String(row.userId) === String(item.userId) && row.businessDate === item.businessDate)
    return Object.assign({}, item, { shopId: request.shopId, scheduleId: item.scheduleId || 20000 + Number(item.userId), status: 'DRAFT', rowVersion: item.rowVersion == null ? 0 : Number(item.rowVersion) + 1 }, explicit || {}, { rowVersion: item.rowVersion == null ? 0 : Number(item.rowVersion) + 1 })
  }) }
}

function loadPayload(shopId, schedules, employees) {
  return {
    shifts: [
      { shiftId: 1, shiftName: '早班', status: 'ENABLED' },
      { shiftId: 4, shiftName: '中班', status: 'ENABLED' },
      { shiftId: 8, shiftName: '夜班', status: 'ENABLED' },
      { shiftId: 9, shiftName: '晚班', status: 'ENABLED' }
    ],
    sites: [
      { siteId: 5, siteName: `地点${shopId}A`, status: 'ENABLED' },
      { siteId: 7, siteName: `地点${shopId}B`, status: 'ENABLED' }
    ],
    employees: employees || [employeeA(), employeeB()],
    schedules: schedules || []
  }
}

function snapshotLoad(log) {
  return {
    shifts: log.shifts[log.shifts.length - 1],
    sites: log.sites[log.sites.length - 1],
    schedules: log.schedules[log.schedules.length - 1],
    employees: log.employees[log.employees.length - 1]
  }
}

function resolveLoad(group, payload) {
  group.shifts.resolve({ data: payload.shifts })
  group.sites.resolve({ data: payload.sites })
  group.schedules.resolve({ data: payload.schedules })
  group.employees.resolve({ data: payload.employees })
}

function latest(bucket) {
  return bucket[bucket.length - 1]
}

function callHook(component, page, name) {
  const hook = component[name]
  assert.equal(typeof hook, 'function', `${name} lifecycle must exist`)
  return hook.call(page)
}

function itemIds(items) {
  assert.ok(Array.isArray(items), 'save items must be an array')
  return Array.from(items).map(item => Number(item.userId))
}

function versionConflict() {
  const error = new Error('SCHEDULE_VERSION_CONFLICT')
  error.businessCode = 'SCHEDULE_VERSION_CONFLICT'
  return error
}

function setup(shopId, week) {
  const { api, log } = createApi()
  const component = loadComponent(api)
  const page = harness(component)
  const errors = []
  const warnings = []
  const successes = []
  page.shopContext = { isStore: true, deptId: shopId, deptName: `门店${shopId}` }
  page.weekAnchor = week
  page.$modal.msgError = message => errors.push(message)
  page.$modal.msgWarning = message => warnings.push(message)
  page.$modal.msgSuccess = message => successes.push(message)
  page.$modal.confirm = () => Promise.resolve()
  return { page, component, log, errors, warnings, successes }
}

const tick = async () => { await new Promise(resolve => setImmediate(resolve)) }

async function mount(shopId, week, schedules, employees) {
  const ctx = setup(shopId, week)
  callHook(ctx.component, ctx.page, 'created')
  resolveLoad(snapshotLoad(ctx.log), loadPayload(shopId, schedules, employees))
  await tick()
  return ctx
}

async function finishSaveRefresh(ctx, schedules, employees) {
  const trailing = snapshotLoad(ctx.log)
  resolveLoad(trailing, loadPayload(ctx.page.shopContext.deptId, schedules || [], employees || (ctx.page.employeeKeyword ? ctx.page.employees : [employeeA(), employeeB()])))
  await tick()
}

async function run() {
  let completed = 0

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 7)
    ctx.page.employeeKeyword = '乙'
    const searching = ctx.page.searchEmployees()
    latest(ctx.log.employees).resolve({ data: [employeeB()] })
    await searching
    await tick()
    assert.equal(ctx.log.saves.length, 0, 'search must not implicitly save')
    assert.equal(ctx.page.employees.length, 1)
    assert.equal(ctx.page.employees[0].userId, 2)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 9)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).siteId, 7)
    ctx.page.employeeKeyword = ''
    const clearing = ctx.page.searchEmployees()
    latest(ctx.log.employees).resolve({ data: [employeeA(), employeeB()] })
    await clearing
    await tick()
    assert.equal(ctx.log.saves.length, 0, 'clearing search must not save')
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 9)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).siteId, 7)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 7)
    ctx.page.employeeKeyword = '乙'
    const searching = ctx.page.searchEmployees()
    latest(ctx.log.employees).resolve({ data: [employeeB()] })
    await searching
    const saving = ctx.page.handleSave()
    await tick()
    const payload = latest(ctx.log.saves).data
    assert.equal(payload.shopId, 10)
    assert.ok(itemIds(payload.items).indexOf(1) !== -1, 'hidden employee A edits must be saved')
    const savedA = Array.from(payload.items).find(item => Number(item.userId) === 1)
    assert.equal(savedA.shiftId, 9)
    assert.equal(savedA.siteId, 7)
    assert.equal(savedA.businessDate, WEEK_A)
    assert.equal(savedA.rowVersion, 3)
    assert.ok(Array.from(payload.items).every(item => String(item.businessDate) !== WEEK_B), 'other-week drafts must not mix in')
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow({ shiftId: 9, siteId: 7, rowVersion: 4 })]))
    await tick()
    await finishSaveRefresh(ctx, [draftRow({ shiftId: 9, siteId: 7, rowVersion: 4 })])
    await saving
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 7)
    const shop10Key = '10|1|' + WEEK_A
    assert.ok(ctx.page.draftEdits[shop10Key])
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(20, [], [employeeB()]))
    await tick()
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', 4)
    ctx.page.setCell(employeeB(), WEEK_A, 'siteId', 7)
    const shop20Save = ctx.page.handleSave()
    await tick()
    const shop20Items = latest(ctx.log.saves).data
    assert.equal(shop20Items.shopId, 20)
    assert.ok(itemIds(shop20Items.items).indexOf(1) === -1, 'shop 10 drafts must not mix into shop 20 save')
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, []))
    await tick()
    await finishSaveRefresh(ctx, [], [employeeB()])
    await shop20Save
    assert.ok(ctx.page.draftEdits[shop10Key], 'saving shop 20 must not prune shop 10 drafts')
    ctx.page.shopContext = { isStore: true, deptId: 10, deptName: '门店10' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(10, [draftRow({ shiftId: 1, siteId: 5, rowVersion: 3 })]))
    await tick()
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 9, 'returning to shop 10 must keep local draft over original server values')
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).rowVersion, 3)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', 4)
    ctx.page.setCell(employeeB(), WEEK_A, 'siteId', 5)
    const saving = ctx.page.handleSave()
    await tick()
    latest(ctx.log.saves).reject(versionConflict())
    await tick()
    latest(ctx.log.schedules).resolve({
      data: [
        draftRow({ shiftId: 8, siteId: 5, rowVersion: 4 }),
        draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
      ]
    })
    await saving
    await tick()
    assert.equal(ctx.successes.length, 0, 'rolled-back batch must not look partially successful')
    assert.equal(ctx.page.cell(employeeB(), WEEK_A).shiftId, 4)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 9)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).needsReview, true)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).remoteShiftId, 8)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).rowVersion, 3)
    const review = ctx.page.conflictReviewText(ctx.page.cell(employeeA(), WEEK_A))
    assert.ok(review.indexOf('本地') !== -1 && review.indexOf('远端') !== -1, 'conflict UI must show both local and remote values')
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 4)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).needsReview, true, 'ordinary edits must not clear unresolved review')
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    const savesBefore = ctx.log.saves.length
    const blocked = ctx.page.handleSave()
    await tick()
    if (blocked && typeof blocked.then === 'function') await blocked
    assert.equal(ctx.log.saves.length, savesBefore, 'unresolved conflict must not resend old values')
    ctx.page.resolveConflictKeepLocal(employeeA(), WEEK_A)
    assert.equal(ctx.page.draftEdits['10|1|' + WEEK_A].rowVersion, 4)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).rowVersion, 4)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).needsReview, false)
    const retry = ctx.page.handleSave()
    await tick()
    const retried = latest(ctx.log.saves).data
    const retriedA = Array.from(retried.items).find(item => Number(item.userId) === 1)
    assert.equal(retriedA.shiftId, 9)
    assert.equal(retriedA.rowVersion, 4, 'explicit review may bind local values to the latest version')
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow({ shiftId: 9, rowVersion: 5 })]))
    await tick()
    await finishSaveRefresh(ctx, [draftRow({ shiftId: 9, rowVersion: 5 })])
    await retry
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(20, [], [employeeB()]))
    await tick()
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', 4)
    ctx.page.setCell(employeeB(), WEEK_A, 'siteId', 7)
    ctx.page.shopContext = { isStore: true, deptId: 10, deptName: '门店10' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(10, [draftRow()]))
    await tick()
    const failedRead = ctx.page.handleSave()
    await tick()
    latest(ctx.log.saves).reject(versionConflict())
    await tick()
    latest(ctx.log.schedules).reject(new Error('重读失败'))
    await failedRead
    await tick()
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 9)
    assert.ok(ctx.page.conflictReadFailed)
    assert.equal(ctx.page.conflictReadFailed.shopId, 10)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).needsReview, true)
    assert.equal(Boolean(ctx.page.draftEdits['20|2|' + WEEK_A].needsReview), false, 'other-shop drafts must not be marked from a failed reread')
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(20, [], [employeeB()]))
    await tick()
    assert.equal(ctx.page.conflictNotice, '', 'conflict-read failure must stay scoped to the original shop/week')
    ctx.page.shopContext = { isStore: true, deptId: 10, deptName: '门店10' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(10, [draftRow()]))
    await tick()
    const retryRead = ctx.page.handleSave()
    await tick()
    latest(ctx.log.schedules).resolve({
      data: [draftRow({ scheduleId: 101, shiftId: 1, status: 'PUBLISHED', rowVersion: 9 })]
    })
    await retryRead
    await tick()
    assert.equal(ctx.page.conflictReadFailed, null)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).overwriteBlocked, true)
    assert.equal(ctx.page.isPublished(ctx.page.cell(employeeA(), WEEK_A)), true)
    const publishedReview = ctx.page.conflictReviewText(ctx.page.cell(employeeA(), WEEK_A))
    assert.ok(publishedReview.indexOf('晚班') !== -1 && publishedReview.indexOf('早班') !== -1, 'published review must keep local draft distinct from remote')
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 4)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 1, 'published remote must not stay an overwritable draft')
    ctx.page.resolveConflictUseRemote(employeeA(), WEEK_A)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).needsReview, false)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).overwriteBlocked, false)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).remoteUnavailable, false)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', 4)
    ctx.page.setCell(employeeB(), WEEK_A, 'siteId', 5)
    const created = ctx.page.handleSave()
    await tick()
    latest(ctx.log.saves).reject(versionConflict())
    await tick()
    latest(ctx.log.schedules).resolve({ data: [draftRow()] })
    await created
    await tick()
    assert.notEqual(ctx.page.cell(employeeB(), WEEK_A).remoteStatus, 'DELETED', 'new local rows must not be treated as remotely deleted')
    assert.equal(Boolean(ctx.page.cell(employeeB(), WEEK_A).overwriteBlocked), false)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    const saving = ctx.page.handleSave()
    await tick()
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 4)
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 4 })]))
    await tick()
    const persistedB = draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    await finishSaveRefresh(ctx, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 4 }), persistedB])
    await saving
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 4, 'edits after the save snapshot must remain')
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).rowVersion, 4, 'continued edits must rebase onto the confirmed save')
    assert.equal(ctx.page.cell(employeeB(), WEEK_A).scheduleId, 202)
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', 4)
    ctx.page.setCell(employeeB(), WEEK_A, 'siteId', 5)
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', null)
    await tick()
    assert.equal(ctx.log.deletes.length, 1, 'clearing a persisted draft must call delete')
    latest(ctx.log.deletes).resolve()
    await tick()
    const afterDelete = snapshotLoad(ctx.log)
    resolveLoad(afterDelete, loadPayload(10, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 4 })]))
    await tick()
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 4, 'refresh after deleting another draft must keep unsaved edits')
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    const refreshing = ctx.page.refresh()
    const duringRefresh = snapshotLoad(ctx.log)
    resolveLoad(duringRefresh, loadPayload(10, [draftRow({ shiftId: 8, siteId: 5, rowVersion: 4 })]))
    await refreshing
    await tick()
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).needsReview, true)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).rowVersion, 3)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).remoteRowVersion, 4)
    const savesBefore = ctx.log.saves.length
    const blocked = ctx.page.handleSave()
    await tick()
    if (blocked && typeof blocked.then === 'function') await blocked
    assert.equal(ctx.log.saves.length, savesBefore, 'refresh must not silently adopt a newer version for old local values')
    completed += 1
  }

  {
    const ctx = setup(10, WEEK_A)
    callHook(ctx.component, ctx.page, 'created')
    const initial = snapshotLoad(ctx.log)
    ctx.page.employeeKeyword = '乙'
    const searching = ctx.page.searchEmployees()
    latest(ctx.log.employees).resolve({ data: [employeeB()] })
    await searching
    await tick()
    assert.equal(ctx.page.employees[0].userId, 2)
    assert.equal(ctx.page.loading, true)
    resolveLoad(initial, loadPayload(10, [draftRow(), draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2 })]))
    await tick()
    assert.equal(ctx.page.schedules.length, 2, 'late refresh must still apply schedules')
    assert.equal(ctx.page.employees[0].userId, 2, 'late refresh must not restore stale employees')
    assert.equal(ctx.page.loading, false)

    const ctx2 = setup(10, WEEK_A)
    callHook(ctx2.component, ctx2.page, 'created')
    const first = snapshotLoad(ctx2.log)
    ctx2.page.employeeKeyword = '乙'
    const lateSearch = ctx2.page.searchEmployees()
    const searchCall = latest(ctx2.log.employees)
    resolveLoad(first, loadPayload(10, [draftRow()]))
    await tick()
    assert.equal(ctx2.page.loading, false)
    searchCall.resolve({ data: [employeeB()] })
    await lateSearch
    await tick()
    assert.equal(ctx2.page.employees[0].userId, 2)
    assert.equal(ctx2.page.schedules[0].scheduleId, 101)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', null)
    await tick()
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(20, [], [employeeB()]))
    await tick()
    latest(ctx.log.deletes).resolve()
    await tick()
    assert.equal(ctx.errors.length, 0, 'stale delete must not toast on the new shop')
    assert.ok(ctx.page.draftEdits['10|1|' + WEEK_A], 'deleting one cell must not drop the other shop/week draft')
    assert.equal(ctx.log.deletes[0].scheduleId, 202)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    let pendingConfirm = deferred()
    ctx.page.$modal.confirm = () => pendingConfirm.promise
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    ctx.page.setCell(employeeB(), WEEK_A, 'shiftId', null)
    await tick()
    assert.equal(ctx.log.deletes.length, 0, 'delete must wait for confirmation')
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    resolveLoad(snapshotLoad(ctx.log), loadPayload(20, [], [employeeB()]))
    await tick()
    pendingConfirm.resolve()
    await tick()
    assert.equal(ctx.log.deletes.length, 0, 'confirm after shop switch must not delete with the new Dept-NumId')
    assert.ok(ctx.page.draftEdits['10|1|' + WEEK_A])

    const ctxWeek = await mount(10, WEEK_A, [
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    pendingConfirm = deferred()
    ctxWeek.page.$modal.confirm = () => pendingConfirm.promise
    ctxWeek.page.setCell(employeeB(), WEEK_A, 'shiftId', null)
    await tick()
    ctxWeek.page.moveWeek(7)
    resolveLoad(snapshotLoad(ctxWeek.log), loadPayload(10, [], [employeeB()]))
    await tick()
    pendingConfirm.resolve()
    await tick()
    assert.equal(ctxWeek.log.deletes.length, 0, 'confirm after week switch must not send the original id')
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    const publishing = ctx.page.handlePublish()
    await tick()
    latest(ctx.log.saves).reject(versionConflict())
    await tick()
    latest(ctx.log.schedules).resolve({
      data: [draftRow({ shiftId: 8, rowVersion: 4 }), draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })]
    })
    await publishing
    await tick()
    assert.equal(ctx.log.publishes.length, 0, 'publish conflict must not publish')
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).needsReview, true)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
    ])
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    const saving = ctx.page.handleSave()
    await tick()
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 4 })]))
    await tick()
    await finishSaveRefresh(ctx, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 4 })])
    await saving
    assert.equal(ctx.successes[0], '本周排班草稿已保存')
    assert.equal(Boolean(ctx.page.draftEdits['10|1|' + WEEK_A]), false, 'confirmed save must drop the overlay so later reads are not covered')
    ctx.page.employeeKeyword = '甲'
    const searching = ctx.page.searchEmployees()
    latest(ctx.log.employees).resolve({ data: [employeeA()] })
    await searching
    const publishing = ctx.page.handlePublish()
    await tick()
    if (ctx.log.saves.length > 1) {
      latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 4 })]))
      await tick()
    }
    latest(ctx.log.schedules).resolve({
      data: [
        draftRow({ shiftId: 9, siteId: 5, rowVersion: 4 }),
        draftRow({ scheduleId: 202, userId: 2, nickName: '乙', shiftId: 2, rowVersion: 5 })
      ]
    })
    await tick()
    assert.ok(Array.isArray(ctx.log.publishes[0].scheduleIds))
    assert.deepStrictEqual(Array.from(ctx.log.publishes[0].scheduleIds), [101, 202])
    assert.equal(ctx.log.publishes[0].shopId, 10)
    await finishSaveRefresh(ctx, [
      draftRow({ shiftId: 9, status: 'PUBLISHED' }),
      draftRow({ scheduleId: 202, userId: 2, status: 'PUBLISHED' })
    ], [employeeA()])
    await publishing
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [])
    const key = '10|1|' + WEEK_A
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    const submittedStamp = ctx.page.draftEdits[key].stamp
    const saving = ctx.page.handleSave()
    await tick()
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', null)
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 4)
    ctx.page.setCell(employeeA(), WEEK_A, 'siteId', 5)
    assert.ok(ctx.page.draftEdits[key].stamp > submittedStamp, 'recreated edits must not reuse a submitted stamp')
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 0 })]))
    await tick()
    await finishSaveRefresh(ctx, [draftRow({ shiftId: 9, siteId: 5, rowVersion: 0 })])
    await saving
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).shiftId, 4)
    assert.equal(ctx.page.draftEdits[key].shiftId, 4)
    assert.equal(ctx.page.draftEdits[key].rowVersion, 0)
    const nextItems = ctx.page.draftItems()
    assert.equal(nextItems.find(item => item.userId === 1).shiftId, 4)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    const key = '10|1|' + WEEK_A
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 9)
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', null)
    await tick()
    assert.equal(ctx.log.deletes.length, 1)
    ctx.page.setCell(employeeA(), WEEK_A, 'shiftId', 4)
    latest(ctx.log.deletes).resolve()
    await tick()
    await finishSaveRefresh(ctx, [])
    assert.equal(ctx.page.draftEdits[key].shiftId, 4, 'deletion must preserve edits made after its snapshot')
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).overwriteBlocked, true)
    assert.equal(ctx.page.cell(employeeA(), WEEK_A).remoteStatus, 'DELETED')
    assert.equal(ctx.page.hasUnresolvedConflicts(), true)
    completed += 1
  }

  assert.equal(completed, 15, 'all draft-recovery behaviors must actually finish')
  console.log(`weeklyScheduleDraftRecovery: ${completed} scenarios passed`)
}

const timeout = setTimeout(() => { console.error('weeklyScheduleDraftRecovery did not finish all 15 scenarios'); process.exit(1) }, 10000)
run().then(() => clearTimeout(timeout)).catch(error => { clearTimeout(timeout); console.error(error); process.exitCode = 1 })
