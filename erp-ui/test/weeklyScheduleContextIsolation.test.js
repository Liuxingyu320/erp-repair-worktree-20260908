const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')

const WEEK_A = '2026-09-07'
const WEEK_B = '2026-09-14'
const RANGE_A = { dateFrom: '2026-09-07', dateTo: '2026-09-13' }

function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

function transform(file) {
  return babel.transformSync(fs.readFileSync(file, 'utf8'), {
    filename: file,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')]
  }).code
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
  const module = { exports: {} }
  vm.runInNewContext(code, {
    module,
    exports: module.exports,
    process: { env: {} },
    Promise,
    require(name) {
      if (name === '@/api/oa/attendanceV2') return api
      if (name === '@/utils/permission') return { checkPermi: () => true }
      return {}
    }
  }, { filename: file })
  return module.exports.default
}

function loadAttendanceApi() {
  const file = path.resolve(__dirname, '../src/api/oa/attendanceV2.js')
  const captured = []
  const module = { exports: {} }
  vm.runInNewContext(transform(file), {
    module,
    exports: module.exports,
    require(name) {
      if (name === '@/utils/request') {
        return config => {
          captured.push(config)
          return Promise.resolve({ data: [] })
        }
      }
      throw new Error(`unexpected require: ${name}`)
    }
  }, { filename: file })
  return { api: module.exports, captured }
}

function harness(component) {
  const instance = {
    $refs: {},
    $set(target, key, value) { target[key] = value },
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
  const log = { shifts: [], sites: [], schedules: [], employees: [], saves: [], publishes: [] }
  const api = {
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
    deleteAttendanceSchedule() { return Promise.resolve() }
  }
  return { api, log }
}

function draftRow(overrides) {
  return Object.assign({
    scheduleId: 101,
    userId: 1,
    nickName: '张三',
    businessDate: WEEK_A,
    shiftId: 8,
    siteId: 5,
    status: 'DRAFT',
    rowVersion: 3
  }, overrides)
}


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
    shifts: [{ shiftId: 8, shiftName: '早班', status: 'ENABLED' }],
    sites: [{ siteId: shopId, siteName: `地点${shopId}`, status: 'ENABLED', address: `${shopId}号店` }],
    employees: employees || [{ userId: 1, nickName: '张三' }],
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

function setup(shopId, week) {
  const { api, log } = createApi()
  const component = loadComponent(api)
  const page = harness(component)
  const errors = []
  const warnings = []
  const successes = []
  let confirm = deferred()
  page.shopContext = { isStore: true, deptId: shopId, deptName: `门店${shopId}` }
  page.weekAnchor = week
  page.$modal.msgError = message => errors.push(message)
  page.$modal.msgWarning = message => warnings.push(message)
  page.$modal.msgSuccess = message => successes.push(message)
  page.$modal.confirm = () => confirm.promise
  return { page, component, log, errors, warnings, successes, confirm: () => confirm, resetConfirm() { confirm = deferred() } }
}

const tick = async () => { await new Promise(resolve => setImmediate(resolve)) }

async function mount(shopId, week, schedules, employees) {
  const ctx = setup(shopId, week)
  callHook(ctx.component, ctx.page, 'created')
  const load = snapshotLoad(ctx.log)
  resolveLoad(load, loadPayload(shopId, schedules, employees))
  await tick()
  return ctx
}

function assertSilentRead(entry, label) {
  assert.strictEqual(entry.options && entry.options.silentError, true, `${label} must pass silentError`)
}

async function run() {
  let completed = 0
  const { api, captured } = loadAttendanceApi()
  api.listAttendanceShifts({ status: 'ENABLED' })
  assert.notStrictEqual(captured[0].silentError, true, 'shift list stays noisy by default for other pages')
  api.listAttendanceSchedules({ shopId: 10, dateFrom: WEEK_A, dateTo: RANGE_A.dateTo })
  assert.notStrictEqual(captured[1].silentError, true, 'schedule list stays noisy by default for other pages')
  api.listAttendanceShifts({ status: 'ENABLED' }, { silentError: true })
  api.listAttendanceSchedules({ shopId: 10 }, { silentError: true })
  assert.strictEqual(captured[2].silentError, true)
  assert.strictEqual(captured[3].silentError, true)

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    assertSilentRead(ctx.log.shifts[0], 'weekly shift read')
    assertSilentRead(ctx.log.schedules[0], 'weekly schedule read')
    const done = ctx.page.handlePublish()
    ctx.page.moveWeek(7)
    assert.equal(ctx.page.weekAnchor, WEEK_A, 'confirm click must freeze the week')
    ctx.confirm().resolve()
    await tick()
    ctx.page.moveWeek(7)
    assert.equal(ctx.page.weekAnchor, WEEK_A, 'week switch during save must restore the locked week')
    assert.equal(ctx.log.shifts.length, 1, 'locked week must not load 9/14')
    ctx.page.schedules = [draftRow({ scheduleId: 202, businessDate: WEEK_B })]
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    const query = latest(ctx.log.schedules)
    assert.equal(query.params.shopId, 10)
    assert.equal(query.params.dateFrom, RANGE_A.dateFrom)
    assert.equal(query.params.dateTo, RANGE_A.dateTo)
    assertSilentRead(query, 'publish collection query')
    query.resolve({ data: [draftRow(), draftRow({ scheduleId: 202, businessDate: WEEK_B, status: 'PUBLISHED' })] })
    await tick()
    assert.equal(ctx.log.publishes.length, 1)
    assert.deepStrictEqual(ctx.log.publishes[0].scheduleIds, [101])
    assert.equal(ctx.log.publishes[0].shopId, 10)
    assert.ok(!ctx.log.publishes[0].scheduleIds.includes(202), 'must never publish 9/14 id 202')
    const trailing = snapshotLoad(ctx.log)
    resolveLoad(trailing, loadPayload(10, [draftRow({ status: 'PUBLISHED' })]))
    await done
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    const done = ctx.page.handlePublish()
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    assert.equal(ctx.page.sites.length, 0, 'shop switch must drop the old table immediately')
    assert.equal(ctx.page.schedules.length, 0)
    ctx.page.weekAnchor = WEEK_B
    ctx.confirm().resolve()
    await tick()
    await done
    assert.equal(ctx.log.publishes.length, 0, 'confirm after shop switch must not publish')
    assert.equal(ctx.log.saves.length, 0, 'invalidated confirm must not save the new shop')
    completed += 1
  }

  {
    const ctx = setup(10, WEEK_A)
    callHook(ctx.component, ctx.page, 'created')
    const shop10 = snapshotLoad(ctx.log)
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    const shop20 = snapshotLoad(ctx.log)
    assert.equal(ctx.page.employees.length, 0)
    assert.equal(ctx.page.loading, true)
    resolveLoad(shop20, loadPayload(20, [draftRow({ scheduleId: 220, userId: 20, nickName: '李四', siteId: 20 })], [{ userId: 20, nickName: '李四' }]))
    await tick()
    shop10.shifts.reject(new Error('门店10班次失败'))
    await tick()
    assert.equal(ctx.page.sites[0].siteId, 20)
    assert.equal(ctx.page.employees[0].userId, 20)
    assert.equal(ctx.page.schedules[0].scheduleId, 220)
    assert.equal(ctx.page.cell({ userId: 20 }, WEEK_A).scheduleId, 220)
    assert.equal(ctx.errors.length, 0, 'stale shop errors must stay silent')
    assert.equal(ctx.page.loading, false)
    completed += 1
  }

  {
    const ctx = setup(10, WEEK_A)
    callHook(ctx.component, ctx.page, 'created')
    const first = snapshotLoad(ctx.log)
    ctx.page.shopContext = { isStore: true, deptId: 20, deptName: '门店20' }
    ctx.page.handleShopContextChange()
    first.schedules.reject(new Error('旧店失败'))
    await tick()
    assert.equal(ctx.page.loading, true, 'stale finally must not clear the new loading flag')
    assert.equal(ctx.errors.length, 0)
    resolveLoad(snapshotLoad(ctx.log), loadPayload(20, []))
    await tick()
    assert.equal(ctx.page.loading, false)
    assert.equal(ctx.page.sites[0].siteId, 20)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    ctx.page.schedules.push(draftRow({ scheduleId: 202, businessDate: WEEK_B }))
    const done = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    latest(ctx.log.saves).reject(new Error('保存失败'))
    await tick()
    const recoveryRead = latest(ctx.log.schedules)
    assert.equal(recoveryRead.params.shopId, 10)
    assert.equal(recoveryRead.params.dateFrom, WEEK_A)
    assert.equal(recoveryRead.params.dateTo, RANGE_A.dateTo)
    assertSilentRead(recoveryRead, 'unknown save recovery read')
    recoveryRead.resolve({ data: [draftRow()] })
    await done
    assert.equal(ctx.log.publishes.length, 0, 'save failure must not publish leftover schedules')
    assert.equal(ctx.log.schedules.length, 2, 'only initial load and authority recovery are allowed; no publish collection')
    assert.ok(ctx.errors[0].includes('第 1 批保存未完成'))
    assert.equal(ctx.page.cell({ userId: 1 }, WEEK_A).shiftId, draftRow().shiftId)
    assert.equal(ctx.page.cell({ userId: 1 }, WEEK_A).rowVersion, 3)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    ctx.page.schedules.push(draftRow({ scheduleId: 202, businessDate: WEEK_B }))
    const done = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    latest(ctx.log.schedules).reject(new Error('整周查询失败'))
    await tick()
    await done
    assert.equal(ctx.log.publishes.length, 0, 'collection query failure must not publish leftover id 202')
    assert.equal(ctx.errors[0], '整周查询失败')
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    const done = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    latest(ctx.log.schedules).resolve({
      data: [
        draftRow(),
        draftRow({ scheduleId: 303, userId: 2, nickName: '隐藏草稿', rowVersion: 4 }),
        draftRow({ scheduleId: 404, status: 'PUBLISHED' })
      ]
    })
    await tick()
    assert.deepStrictEqual(ctx.log.publishes[0].scheduleIds, [101, 303])
    assert.equal(ctx.log.saves[0].data.items[0].rowVersion, 3)
    assert.equal(ctx.log.saves[0].data.items[0].businessDate, WEEK_A)
    const trailing = snapshotLoad(ctx.log)
    resolveLoad(trailing, loadPayload(10, [draftRow({ status: 'PUBLISHED' })]))
    await done
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [
      draftRow(),
      draftRow({ scheduleId: 303, userId: 2, nickName: '王五' })
    ], [{ userId: 1, nickName: '张三' }])
    ctx.page.employeeKeyword = '张'
    ctx.page.employees = [{ userId: 1, nickName: '张三' }]
    const done = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    assert.equal(ctx.log.saves[0].data.items.length, 1)
    assert.equal(ctx.log.saves[0].data.items[0].userId, 1)
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    latest(ctx.log.schedules).resolve({
      data: [draftRow(), draftRow({ scheduleId: 303, userId: 2, nickName: '王五' })]
    })
    await tick()
    assert.deepStrictEqual(ctx.log.publishes[0].scheduleIds, [101, 303], 'filtered view must still publish the whole week')
    const trailing = snapshotLoad(ctx.log)
    resolveLoad(trailing, loadPayload(10, []))
    await done
    completed += 1
  }

  {
    const ctx = setup(10, WEEK_A)
    callHook(ctx.component, ctx.page, 'created')
    const firstA = snapshotLoad(ctx.log)
    ctx.page.moveWeek(7)
    assert.equal(ctx.page.weekAnchor, WEEK_B)
    const weekB = snapshotLoad(ctx.log)
    ctx.page.moveWeek(-7)
    assert.equal(ctx.page.weekAnchor, WEEK_A)
    const secondA = snapshotLoad(ctx.log)
    resolveLoad(firstA, loadPayload(10, [draftRow({ scheduleId: 101 })]))
    await tick()
    assert.equal(ctx.page.schedules.length, 0, 'A→B→A must not accept the first A-week response')
    resolveLoad(weekB, loadPayload(10, [draftRow({ scheduleId: 202, businessDate: WEEK_B })]))
    await tick()
    assert.equal(ctx.page.schedules.length, 0, 'returning to A-week must not accept the B-week response')
    resolveLoad(secondA, loadPayload(10, [draftRow({ scheduleId: 303 })]))
    await tick()
    assert.equal(ctx.page.schedules[0].scheduleId, 303)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    const done = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    ctx.page.refresh()
    const internal = snapshotLoad(ctx.log)
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    latest(ctx.log.schedules).resolve({ data: [draftRow()] })
    await tick()
    assert.equal(ctx.log.publishes.length, 1, 'internal refresh must not cancel the frozen publish')
    resolveLoad(internal, loadPayload(10, [draftRow({ scheduleId: 202, businessDate: WEEK_B })]))
    const trailing = snapshotLoad(ctx.log)
    resolveLoad(trailing, loadPayload(10, [draftRow()]))
    await done
    assert.deepStrictEqual(ctx.log.publishes[0].scheduleIds, [101])
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    const pendingPublish = ctx.page.handlePublish()
    ctx.page.handleSave()
    assert.equal(ctx.log.saves.length, 0, 'save must refuse an in-flight publish confirmation')
    ctx.confirm().resolve()
    await tick()
    assert.equal(ctx.page.saving, false)
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    latest(ctx.log.schedules).resolve({ data: [draftRow()] })
    await tick()
    const trailing = snapshotLoad(ctx.log)
    resolveLoad(trailing, loadPayload(10, []))
    await pendingPublish
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    let confirmCalls = 0
    ctx.page.$modal.confirm = () => { confirmCalls += 1; return Promise.resolve() }
    const saving = ctx.page.handleSave()
    ctx.page.moveWeek(7)
    assert.equal(ctx.page.weekAnchor, WEEK_A, 'save must lock and restore the current week')
    ctx.page.handlePublish()
    assert.equal(confirmCalls, 0, 'publish must refuse an in-flight save')
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    const afterSave = snapshotLoad(ctx.log)
    resolveLoad(afterSave, loadPayload(10, [draftRow()]))
    await saving
    assert.equal(ctx.log.publishes.length, 0)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    callHook(ctx.component, ctx.page, 'activated')
    assert.equal(ctx.log.shifts.length, 1, 'first activated must not reload')
    const done = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    callHook(ctx.component, ctx.page, 'deactivated')
    assert.equal(ctx.page.scheduleDestroyed, false)
    assert.equal(ctx.page.scheduleInactive, true)
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    await done
    assert.equal(ctx.log.publishes.length, 0, 'leaving keep-alive must drop unsent publish stages')
    assert.equal(ctx.log.schedules.length, 1, 'leave during save must not query the frozen week for publish')
    callHook(ctx.component, ctx.page, 'activated')
    assert.equal(ctx.page.scheduleDestroyed, false)
    assert.equal(ctx.page.scheduleInactive, false)
    const reentered = snapshotLoad(ctx.log)
    resolveLoad(reentered, loadPayload(10, [draftRow()]))
    await tick()
    ctx.resetConfirm()
    const again = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    latest(ctx.log.schedules).resolve({ data: [draftRow()] })
    await tick()
    assert.equal(ctx.log.publishes.length, 1, 're-entry must allow a new publish')
    const trailing = snapshotLoad(ctx.log)
    resolveLoad(trailing, loadPayload(10, []))
    await again
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    const done = ctx.page.handlePublish()
    ctx.confirm().resolve()
    await tick()
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    await tick()
    callHook(ctx.component, ctx.page, 'deactivated')
    latest(ctx.log.schedules).resolve({ data: [draftRow(), draftRow({ scheduleId: 202 })] })
    await tick()
    await done
    assert.equal(ctx.log.publishes.length, 0, 'leave during week query must not publish')
    callHook(ctx.component, ctx.page, 'activated')
    resolveLoad(snapshotLoad(ctx.log), loadPayload(10, [draftRow()]))
    await tick()
    assert.equal(ctx.page.loading, false)
    assert.equal(ctx.page.scheduleDestroyed, false)
    completed += 1
  }

  {
    const ctx = setup(10, WEEK_A)
    callHook(ctx.component, ctx.page, 'created')
    snapshotLoad(ctx.log).schedules.reject(new Error('当前周加载失败'))
    await tick()
    assert.equal(ctx.errors[0], '当前周加载失败', 'the active read must still surface its own error')
    assert.equal(ctx.page.loading, false)
    completed += 1
  }

  {
    const ctx = await mount(10, WEEK_A, [draftRow()])
    const pending = ctx.page.persistDrafts()
    assert.equal(ctx.log.saves[0].data.shopId, 10)
    latest(ctx.log.saves).resolve(completeSavedBatch(ctx, [draftRow()]))
    assert.equal(await pending, true)
    completed += 1
  }

  assert.equal(completed, 16, 'all original context scenarios must finish')
  console.log('weeklyScheduleContextIsolation: real component methods passed')
}

const timeout = setTimeout(() => { console.error('weeklyScheduleContextIsolation did not finish'); process.exit(1) }, 10000)
run().then(() => clearTimeout(timeout)).catch(error => { clearTimeout(timeout); console.error(error); process.exitCode = 1 })
