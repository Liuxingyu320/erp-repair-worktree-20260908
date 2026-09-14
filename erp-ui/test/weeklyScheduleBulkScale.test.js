const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const Vue = require('vue')
const WEEK = '2026-09-07'
const clone = value => JSON.parse(JSON.stringify(value))
const advanceVersion = value => typeof value === 'string' ? (BigInt(value) + 1n).toString() : value + 1
const flush = async () => { for (let i = 0; i < 3; i++) { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() } }
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const employee = userId => ({ userId, nickName: `合成员工${userId}`, userName: `fixture-${userId}`, employeeNo: `E${userId}` })
const row = (userId, businessDate = WEEK, extra = {}) => Object.assign({ scheduleId: userId, shopId: 10, userId, userName: employee(userId).nickName, businessDate, shiftId: 1, siteId: 5, status: 'DRAFT', rowVersion: 1 }, extra)
const file = path.resolve(__dirname, '../src/views/oa/attendance/components/WeeklySchedule.vue')
const code = babel.transformSync(fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1], { filename: file, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
async function mount(options = {}) {
  const h = { behavior: {}, calls: { reads: [], employees: [], saves: [], publishes: [] }, notices: [], committedSaves: [], committedPublishes: [], schedules: clone(options.rows || []), roster: Array.from({ length: options.people || 3 }, (_, i) => employee(i + 1)) }
  let nextId = 10000
  const api = {
    listAttendanceShifts: () => Promise.resolve({ data: [{ shiftId: 1, shiftName: '早班', status: 'ENABLED' }, { shiftId: 2, shiftName: '晚班', status: 'ENABLED' }] }),
    listAttendanceSites: () => Promise.resolve({ data: [{ siteId: 5, siteName: '合成地点', status: 'ENABLED' }] }),
    listAttendanceSchedules(query, readOptions) {
      h.calls.reads.push({ query: clone(query), options: readOptions })
      const read = () => ({ data: clone(h.schedules.filter(r => r.shopId === query.shopId && r.businessDate >= query.dateFrom && r.businessDate <= query.dateTo)) })
      return Promise.resolve().then(() => h.behavior.read ? h.behavior.read(query, read, h.calls.reads.length) : read())
    },
    listAttendanceEmployeeOptions(query) {
      h.calls.employees.push(clone(query))
      const read = () => {
        const values = h.roster.filter(r => !query.keyword || r.nickName === query.keyword || r.employeeNo === query.keyword)
        return { data: { rows: clone(values.slice(((query.pageNum || 1) - 1) * 100, (query.pageNum || 1) * 100)), total: values.length, pageNum: query.pageNum || 1 } }
      }
      return Promise.resolve().then(() => h.behavior.employees ? h.behavior.employees(query, read) : read())
    },
    saveAttendanceScheduleBatch(payload) {
      const data = clone(payload); h.calls.saves.push(data)
      assert.ok(data.items.length > 0 && data.items.length <= 500, 'each actual save request must fit the service limit')
      const commit = () => {
        const next = clone(h.schedules), result = []
        for (const item of data.items) {
          let saved = next.find(r => r.userId === item.userId && r.businessDate === item.businessDate)
          if (saved) {
            assert.equal(saved.shopId, data.shopId)
            if (saved.status !== 'DRAFT' || item.rowVersion !== saved.rowVersion) throw new Error('SCHEDULE_VERSION_CONFLICT')
            Object.assign(saved, item, { rowVersion: advanceVersion(saved.rowVersion) })
          } else {
            saved = row(item.userId, item.businessDate, Object.assign({}, item, { shopId: data.shopId, scheduleId: nextId++, rowVersion: 0 }))
            next.push(saved)
          }
          result.push(clone(saved))
        }
        h.schedules = next; h.committedSaves.push(...clone(data.items))
        return { data: result }
      }
      return Promise.resolve().then(() => h.behavior.save ? h.behavior.save(data, commit, h.calls.saves.length) : commit())
    },
    publishAttendanceSchedules(payload) {
      const data = clone(payload); h.calls.publishes.push(data)
      assert.ok(data.scheduleIds.length > 0 && data.scheduleIds.length <= 500, 'each actual publish request must fit the service limit')
      const commit = () => {
        const selected = data.scheduleIds.map(id => h.schedules.find(r => r.scheduleId === id && r.shopId === data.shopId))
        assert.ok(selected.every(r => r && r.status === 'DRAFT'), 'never resend a published id')
        assert.deepEqual(Object.keys(data.scheduleVersions).sort(), data.scheduleIds.map(String).sort(), 'publish versions must cover exactly this batch')
        if (selected.some(r => data.scheduleVersions[String(r.scheduleId)] !== r.rowVersion)) throw new Error('SCHEDULE_VERSION_CONFLICT')
        selected.forEach(r => { r.status = 'PUBLISHED'; r.rowVersion = advanceVersion(r.rowVersion) })
        h.committedPublishes.push(...data.scheduleIds)
        return { data: clone(selected) }
      }
      return Promise.resolve().then(() => h.behavior.publish ? h.behavior.publish(data, commit, h.calls.publishes.length) : commit())
    },
    deleteAttendanceSchedule: () => Promise.reject(new Error('not used by bulk tests'))
  }
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, console,
    require(id) { if (id === '@/api/oa/attendanceV2') return api; if (id === '@/utils/permission') return { checkPermi: () => options.permission !== false }; throw new Error(id) }
  }, { filename: file })
  const definition = module.exports.default
  h.page = new Vue(Object.assign({}, definition, {
    propsData: { shopContext: { isStore: true, deptId: 10, deptName: '合成门店' } },
    data() { return Object.assign(definition.data.call(this), { weekAnchor: WEEK }) },
    beforeCreate() { this.$modal = { confirm: () => Promise.resolve(), msgSuccess: text => h.notices.push(['success', text]), msgWarning: text => h.notices.push(['warning', text]), msgError: text => h.notices.push(['error', text]) } }
  }))
  h.lifecycle = name => { for (const hook of h.page.$options[name] || []) hook.call(h.page) }
  h.fill = (people, days) => {
    h.page.clearEmployeeSelection()
    h.roster.slice(0, people).forEach(e => h.page.selectEmployee(e, true))
    h.page.bulkDates = h.page.weekDays.slice(0, days).map(d => d.date)
    h.page.bulkShiftId = 1; h.page.bulkSiteId = 5
    h.page.applyBulkSchedule()
  }
  await flush()
  return h
}
const cases = []
const test = (name, run, options) => cases.push({ name, run, options })
test('employee 101 and later are available; local pages and search keep same-shop selection', async h => {
  assert.equal(h.page.employeeTotal, 143); assert.equal(h.page.employeeQueryRows.length, 100); assert.equal(h.page.visibleEmployees.length, 20)
  h.page.selectVisibleEmployees(); h.page.employeePage = 2; h.page.selectVisibleEmployees(); assert.equal(h.page.selectedEmployees.length, 40)
  await h.page.loadMoreEmployees(); assert.equal(h.calls.employees[1].pageNum, 2); assert.equal(h.page.employees.length, 143)
  h.page.selectEmployee(h.page.employees.find(e => e.userId === 143), true)
  h.page.employeeKeyword = 'E143'; await h.page.searchEmployees(); assert.equal(h.page.employees.length, 1); assert.equal(h.page.selectedEmployees.length, 41)
  h.page.employeeKeyword = ''; await h.page.searchEmployees(); assert.equal(h.page.selectedEmployees.length, 41)
  h.page.shopContext.deptId = 20; await flush(); assert.equal(h.page.selectedEmployees.length, 0)
}, { people: 143 })
test('failed employee page retains cursor and retries that exact page', async h => {
  h.behavior.employees = () => { throw new Error('temporary read failure') }
  await h.page.loadMoreEmployees(); assert.equal(h.page.employeeApiPage, 1); assert.equal(h.page.employeeLoadingMore, false)
  delete h.behavior.employees; await h.page.loadMoreEmployees()
  assert.equal(h.calls.employees[1].pageNum, 2); assert.equal(h.calls.employees[2].pageNum, 2); assert.equal(h.page.employeeQueryRows.length, 143)
}, { people: 143 })
test('late employee page cannot replace a newer search', async h => {
  const gate = deferred(); h.behavior.employees = (q, read) => q.pageNum === 2 ? gate.promise.then(read) : read()
  const more = h.page.loadMoreEmployees(); await flush(); h.page.employeeKeyword = 'E3'; await h.page.searchEmployees()
  gate.resolve(); await more; assert.equal(h.page.employees.length, 1); assert.equal(h.page.employees[0].userId, 3); assert.equal(h.page.employeeLoadingMore, false)
}, { people: 143 })
test('bulk fill leaves both persisted and local drafts untouched and issues no write', async h => {
  h.page.setCell(employee(2), WEEK, 'shiftId', 2)
  h.fill(3, 1)
  assert.equal(h.page.cell(employee(1), WEEK).shiftId, 2); assert.equal(h.page.cell(employee(2), WEEK).shiftId, 2); assert.equal(h.page.cell(employee(3), WEEK).shiftId, 1)
  assert.equal(h.page.cell(employee(1), WEEK).rowVersion, 7); assert.equal(h.calls.saves.length, 0); assert.equal(h.calls.publishes.length, 0)
  assert.ok(h.page.bulkNotice.includes('跳过 2'))
}, { rows: [row(1, WEEK, { shiftId: 2, rowVersion: 7 })] })
test('bulk fill requires scope permission, people, date, shift and site', async h => {
  h.fill(3, 1); assert.equal(Object.keys(h.page.draftEdits).length, 0); assert.equal(h.calls.saves.length, 0)
}, { permission: false })
test('copy previous week creates target drafts only, skipping server and local occupied cells', async h => {
  h.page.setCell(employee(2), WEEK, 'shiftId', 2)
  await h.page.copyPreviousWeek()
  assert.equal(h.page.cell(employee(1), WEEK).shiftId, 2); assert.equal(h.page.cell(employee(2), WEEK).shiftId, 2)
  assert.equal(h.page.cell(employee(3), WEEK).shiftId, 1); assert.equal(h.page.cell(employee(3), WEEK).scheduleId, null)
  assert.equal(h.calls.saves.length, 0); assert.equal(h.calls.publishes.length, 0)
  assert.ok(h.page.bulkNotice.includes('2026-08-31')); assert.ok(h.page.bulkNotice.includes(WEEK)); assert.ok(h.page.bulkNotice.includes('跳过 2'))
  for (const read of h.calls.reads.slice(1)) assert.equal(read.options.silentError, true)
}, { rows: [row(1, WEEK, { shiftId: 2 }), row(1, '2026-08-31', { scheduleId: 11 }), row(2, '2026-08-31', { scheduleId: 12 }), row(3, '2026-08-31', { scheduleId: 13 })] })
test('copy skips disabled or unavailable source shift/site and never creates invalid target', async h => {
  await h.page.copyPreviousWeek(); assert.equal(Object.keys(h.page.draftEdits).length, 0); assert.ok(h.page.bulkNotice.includes('2 条班次或地点不可用'))
}, { rows: [row(1, '2026-08-31', { shiftId: 999 }), row(2, '2026-08-31', { siteId: 999 })] })
test('copy reads current target authority and avoids a schedule created since initial load', async h => {
  h.schedules.push(row(1, WEEK, { scheduleId: 44, shiftId: 2, rowVersion: 4 }))
  await h.page.copyPreviousWeek(); assert.equal(h.page.cell(employee(1), WEEK).scheduleId, 44); assert.equal(h.page.cell(employee(1), WEEK).shiftId, 2); assert.equal(Object.keys(h.page.draftEdits).length, 0)
}, { rows: [row(1, '2026-08-31')] })
test('copy response after a week switch cannot write the new week or resurrect old selection', async h => {
  const gate = deferred(); h.behavior.read = (q, read) => q.dateFrom === '2026-08-31' ? gate.promise.then(read) : read()
  const copying = h.page.copyPreviousWeek(); await flush(); h.page.moveWeek(7); await flush(); gate.resolve(); await copying
  assert.equal(h.page.weekAnchor, '2026-09-14'); assert.equal(Object.keys(h.page.draftEdits).length, 0); assert.equal(h.page.copying, false)
}, { rows: [row(1, '2026-08-31')] })
test('copy preserves edits and clearing a local draft made while source read is pending', async h => {
  h.page.setCell(employee(1), WEEK, 'shiftId', 2)
  const gate = deferred(); h.behavior.read = (q, read) => q.dateFrom === '2026-08-31' ? gate.promise.then(read) : read()
  const copying = h.page.copyPreviousWeek(); await flush(); h.page.setCell(employee(1), WEEK, 'shiftId', null); gate.resolve(); await copying
  assert.equal(h.page.cell(employee(1), WEEK).shiftId, null); assert.equal(Object.keys(h.page.draftEdits).length, 0); assert.ok(h.page.bulkNotice.includes('本次未复制'))
}, { rows: [row(1, '2026-08-31')] })
for (const [people, days, total, expected] of [[84, 6, 504, [500, 4]], [143, 7, 1001, [500, 500, 1]]]) {
  test(`${total} drafts save and publish in bounded sequential requests`, async h => {
    h.fill(people, days); assert.equal(h.page.draftItems().length, total)
    await h.page.handlePublish(); assert.deepEqual(h.calls.saves.map(c => c.items.length), expected); assert.deepEqual(h.calls.publishes.map(c => c.scheduleIds.length), expected)
    assert.equal(h.schedules.filter(r => r.status === 'PUBLISHED').length, total); assert.equal(new Set(h.committedPublishes).size, total)
    assert.equal(Object.keys(h.page.draftEdits).length, 0); assert.equal(h.page.batchProgress.completed, total)
  }, { people })
  test(`${total} second save batch failure retains remaining drafts; retry does not resend first batch`, async h => {
    h.fill(people, days); h.behavior.save = (data, commit, n) => { if (n === 2) throw new Error('second save failed'); return commit() }
    await h.page.handleSave(); assert.equal(h.calls.saves.length, 2); assert.equal(h.schedules.length, 500); assert.equal(Object.keys(h.page.draftEdits).length, total - 500)
    assert.equal(h.page.batchProgress.failed, true); assert.equal(h.page.batchProgress.completed, 500); assert.equal(h.notices.filter(n => n[0] === 'success').length, 0)
    delete h.behavior.save; await h.page.handleSave(); assert.equal(h.schedules.length, total); assert.equal(h.committedSaves.length, total)
    assert.ok(h.calls.saves.slice(2).every(call => call.items.every(item => !h.calls.saves[0].items.some(first => first.userId === item.userId && first.businessDate === item.businessDate))))
  }, { people })
  test(`${total} second publish batch failure stops and retries only unpublished ids`, async h => {
    h.fill(people, days); h.behavior.publish = (data, commit, n) => { if (n === 2) throw new Error('second publish failed'); return commit() }
    await h.page.handlePublish(); assert.equal(h.calls.publishes.length, 2); assert.equal(h.committedPublishes.length, 500); assert.equal(h.page.batchProgress.failed, true)
    assert.equal(h.notices.filter(n => n[0] === 'success' && n[1].includes('本周排班已发布')).length, 0)
    delete h.behavior.publish; await h.page.handlePublish(); assert.equal(h.committedPublishes.length, total); assert.equal(new Set(h.committedPublishes).size, total)
    const first = new Set(h.calls.publishes[0].scheduleIds); assert.ok(h.calls.publishes.slice(2).every(c => c.scheduleIds.every(id => !first.has(id))))
  }, { people })
}
test('unknown committed save batch requires authority comparison before remaining work can publish', async h => {
  h.fill(84, 6); h.behavior.save = (data, commit, n) => { const result = commit(); if (n === 2) throw new Error('response lost after commit'); return result }
  await h.page.handlePublish(); assert.equal(h.schedules.length, 504); assert.equal(h.calls.publishes.length, 0); assert.equal(h.page.hasUnresolvedConflicts(), true)
  const count = h.calls.saves.length; await h.page.handlePublish(); assert.equal(h.calls.saves.length, count); assert.equal(h.calls.publishes.length, 0)
  Object.values(h.page.draftEdits).filter(edit => edit.needsReview).forEach(edit => h.page.resolveConflictUseRemote(employee(edit.userId), edit.workDate))
  delete h.behavior.save; await h.page.handlePublish(); assert.equal(h.schedules.length, 504); assert.equal(h.committedPublishes.length, 504); assert.equal(h.committedSaves.length, 504)
}, { people: 84 })
test('unknown committed publish batch is read back and never blindly resent', async h => {
  h.fill(143, 7); h.behavior.publish = (data, commit, n) => { const result = commit(); if (n === 2) throw new Error('publish response lost'); return result }
  await h.page.handlePublish(); assert.equal(h.page.batchProgress.completed, 1000); assert.equal(h.page.batchProgress.failed, true); assert.equal(h.calls.publishes.length, 2)
  delete h.behavior.publish; await h.page.handlePublish(); assert.equal(h.committedPublishes.length, 1001); assert.equal(h.calls.publishes[2].scheduleIds.length, 1)
}, { people: 143 })
test('publish recovery read failure keeps uncertainty; next confirmation first rereads authority', async h => {
  h.fill(84, 6); let uncertain = false
  h.behavior.publish = (data, commit, n) => { const result = commit(); if (n === 2) { uncertain = true; throw new Error('response lost') } return result }
  h.behavior.read = (q, read) => { if (uncertain) throw new Error('authority unavailable'); return read() }
  await h.page.handlePublish(); assert.equal(h.page.batchProgress.unknown, true); assert.equal(h.calls.publishes.length, 2)
  uncertain = false; delete h.behavior.publish; await h.page.handlePublish(); assert.equal(h.calls.publishes.length, 2); assert.equal(h.committedPublishes.length, 504); assert.equal(h.page.batchProgress.failed, false); assert.equal(h.page.batchProgress.unknown, false)
}, { people: 84 })
test('store switch after first save commits stops every later batch and keeps old-store remaining draft keys', async h => {
  h.fill(143, 7); h.behavior.save = async (data, commit, n) => { const result = commit(); if (n === 1) { h.page.shopContext.deptId = 20; await Vue.nextTick() } return result }
  await h.page.handleSave(); await flush(); assert.equal(h.calls.saves.length, 1); assert.equal(h.page.shopContext.deptId, 20); assert.equal(h.page.batchProgress, null)
  assert.equal(Object.keys(h.page.draftEdits).filter(key => key.startsWith('10|')).length, 501); assert.equal(h.page.schedules.length, 0)
}, { people: 143 })
test('deactivation after first publish commits stops later batches and no old result rewrites new week', async h => {
  h.fill(84, 6); h.behavior.publish = async (data, commit, n) => { const result = commit(); if (n === 1) { h.lifecycle('deactivated'); h.page.weekAnchor = '2026-09-14'; h.lifecycle('activated'); await Vue.nextTick() } return result }
  await h.page.handlePublish(); await flush(); assert.equal(h.calls.publishes.length, 1); assert.equal(h.page.weekAnchor, '2026-09-14'); assert.equal(h.page.batchProgress, null); assert.equal(h.page.schedules.length, 0)
}, { people: 84 })
test('new edits while an earlier batch saves retain the latest value/version and prevent publish', async h => {
  h.fill(84, 6); h.behavior.save = (data, commit, n) => { if (n === 1) h.page.setCell(employee(1), WEEK, 'shiftId', 2); return commit() }
  await h.page.handlePublish(); const edit = h.page.draftEdits[`10|1|${WEEK}`]
  assert.equal(edit.shiftId, 2); assert.equal(edit.rowVersion, 0); assert.equal(h.page.cell(employee(1), WEEK).shiftId, 2); assert.equal(h.calls.publishes.length, 0)
  delete h.behavior.save; await h.page.handlePublish(); assert.equal(h.schedules.find(r => r.userId === 1 && r.businessDate === WEEK).shiftId, 2); assert.equal(h.committedPublishes.length, 504)
}, { people: 84 })
test('missing successful-save rows are unknown, do not clear stamps and never trigger publish', async h => {
  h.fill(3, 1)
  const stamps = Object.fromEntries(Object.entries(h.page.draftEdits).map(([key, edit]) => [key, edit.stamp]))
  h.behavior.save = (data, commit) => ({ data: commit().data.slice(0, 1) })
  await h.page.handlePublish()
  assert.equal(h.calls.publishes.length, 0); assert.equal(h.page.hasUnresolvedConflicts(), true)
  for (const [key, stamp] of Object.entries(stamps)) assert.equal(h.page.draftEdits[key].stamp, stamp)
  assert.equal(h.notices.filter(n => n[0] === 'success').length, 0)
})
test('save response with matching identity/version but wrong business content cannot erase new input', async h => {
  h.fill(3, 1)
  h.behavior.save = (data, commit) => {
    const result = commit(); result.data[0].shiftId = 2
    h.page.setCell(employee(3), WEEK, 'shiftId', 2)
    return result
  }
  await h.page.handlePublish()
  assert.equal(h.calls.publishes.length, 0); assert.equal(h.page.hasUnresolvedConflicts(), true)
  assert.equal(h.page.draftEdits[`10|1|${WEEK}`].shiftId, 1)
  assert.equal(h.page.draftEdits[`10|3|${WEEK}`].shiftId, 2)
})
test('new editing while publish collection is being read blocks all publishing', async h => {
  h.fill(3, 1)
  h.behavior.read = (query, read) => { h.page.setCell(employee(1), WEEK, 'shiftId', 2); return read() }
  await h.page.handlePublish()
  assert.equal(h.calls.publishes.length, 0); assert.equal(h.page.draftEdits[`10|1|${WEEK}`].shiftId, 2); assert.equal(h.page.batchProgress.failed, true)
})
test('a remote edit between publish batches cannot change the frozen content being approved', async h => {
  h.fill(84, 6)
  let changedId
  h.behavior.publish = (data, commit, n) => {
    const result = commit()
    if (n === 1) { const changed = h.schedules.find(r => r.status === 'DRAFT'); changedId = changed.scheduleId; changed.shiftId = 2; changed.rowVersion += 1 }
    return result
  }
  await h.page.handlePublish()
  assert.equal(h.committedPublishes.length, 500); assert.equal(h.calls.publishes.length, 2); assert.equal(h.page.hasUnresolvedConflicts(), true)
  const changed = h.schedules.find(r => r.scheduleId === changedId)
  const draft = h.page.draftEdits[`10|${changed.userId}|${changed.businessDate}`]
  assert.equal(draft.shiftId, 1); assert.equal(draft.remoteShiftId, 2)
  await h.page.handlePublish(); assert.equal(h.calls.publishes.length, 2)
  h.page.resolveConflictUseRemote(employee(changed.userId), changed.businessDate)
  delete h.behavior.publish; await h.page.handlePublish()
  assert.equal(h.committedPublishes.length, 504); assert.equal(h.schedules.find(r => r.scheduleId === changedId).shiftId, 2)
}, { people: 84 })
const badPublishReceipts = [
  ['empty', () => []],
  ['missing payload', () => undefined],
  ['missing row', rows => rows.slice(1)],
  ['duplicate row', rows => [rows[0], rows[0], rows[2]]],
  ['extra row', rows => rows.concat(row(99, WEEK, { status: 'PUBLISHED' }))],
  ...['scheduleId', 'userId', 'businessDate', 'shopId', 'shiftId', 'siteId', 'status', 'rowVersion'].map(field => [
    'wrong ' + field,
    rows => { rows[0][field] = field === 'businessDate' ? '2026-09-08' : field === 'status' ? 'DRAFT' : field === 'rowVersion' ? rows[0].rowVersion - 1 : 999; return rows }
  ])
]
for (const [label, corrupt] of badPublishReceipts) {
  test('invalid publish receipt (' + label + ') cannot manufacture published rows or continue', async h => {
    h.fill(3, 1)
    h.behavior.publish = data => {
      const rows = data.scheduleIds.map(id => Object.assign({}, h.schedules.find(r => r.scheduleId === id), { status: 'PUBLISHED', rowVersion: 1 }))
      return { data: corrupt(rows) }
    }
    await h.page.handlePublish()
    assert.equal(h.calls.publishes.length, 1); assert.equal(h.committedPublishes.length, 0)
    assert.equal(h.page.batchProgress.failed, true); assert.equal(h.page.batchProgress.completed, 0)
    assert.equal(h.page.schedules.filter(r => r.status === 'PUBLISHED').length, 0)
    assert.equal(h.notices.filter(n => n[0] === 'success').length, 0)
    assert.ok(h.calls.reads.length >= 3, 'invalid success receipt must trigger authoritative recovery')
    delete h.behavior.publish; await h.page.handlePublish(); assert.equal(h.committedPublishes.length, 3)
  })
}
for (const [label, value] of [['negative', -1], ['fraction', 1.5], ['empty', ''], ['exponent', '1e0'], ['null', null], ['unsafe number', 9007199254740992], ['past Long', '9223372036854775808'], ['not advanced', 7], ['skipped version', 9]]) {
  test('invalid save version (' + label + ') keeps submitted draft and requires authority recovery', async h => {
    h.page.setCell(employee(1), WEEK, 'shiftId', 2)
    const stamp = h.page.draftEdits[`10|1|${WEEK}`].stamp
    h.behavior.save = (data, commit) => { const response = commit(); response.data[0].rowVersion = value; return response }
    await h.page.handlePublish()
    assert.equal(h.calls.publishes.length, 0); assert.equal(h.page.hasUnresolvedConflicts(), true)
    assert.equal(h.page.draftEdits[`10|1|${WEEK}`].stamp, stamp)
    assert.equal(h.page.draftEdits[`10|1|${WEEK}`].shiftId, 2)
    assert.equal(h.notices.filter(n => n[0] === 'success').length, 0)
  }, { rows: [row(1, WEEK, { rowVersion: 7 })] })
}
test('Long string versions retain exact adjacent values through save and publish', async h => {
  h.page.setCell(employee(1), WEEK, 'shiftId', 2)
  await h.page.handlePublish()
  assert.equal(h.calls.saves[0].items[0].rowVersion, '9007199254740992')
  assert.equal(h.calls.publishes[0].scheduleVersions['1'], '9007199254740993')
  assert.equal(h.schedules[0].rowVersion, '9007199254740994')
  assert.equal(h.page.schedules[0].rowVersion, '9007199254740994')
  assert.equal(h.committedPublishes.length, 1)
}, { rows: [row(1, WEEK, { rowVersion: '9007199254740992' })] })
test('adjacent Long string publish mismatch is rejected without lossy Number comparison', async h => {
  h.page.setCell(employee(1), WEEK, 'shiftId', 2)
  h.behavior.publish = (data, commit) => { const result = commit(); result.data[0].rowVersion = '9007199254740993'; return result }
  await h.page.handlePublish()
  assert.equal(h.page.batchProgress.failed, true); assert.equal(h.notices.filter(n => n[0] === 'success').length, 0)
  assert.equal(h.calls.reads.length, 3)
  assert.equal(h.page.schedules[0].rowVersion, '9007199254740994')
}, { rows: [row(1, WEEK, { rowVersion: '9007199254740992' })] })
test('second committed publish with incomplete receipt stops remaining work and retries no confirmed id', async h => {
  h.fill(143, 7)
  h.behavior.publish = (data, commit, n) => { const result = commit(); return n === 2 ? { data: result.data.slice(1) } : result }
  await h.page.handlePublish()
  assert.equal(h.calls.publishes.length, 2); assert.equal(h.page.batchProgress.failed, true); assert.equal(h.page.batchProgress.completed, 1000)
  assert.equal(h.notices.filter(n => n[0] === 'success').length, 0)
  delete h.behavior.publish; await h.page.handlePublish()
  assert.equal(h.calls.publishes[2].scheduleIds.length, 1); assert.equal(h.committedPublishes.length, 1001); assert.equal(new Set(h.committedPublishes).size, 1001)
}, { people: 143 })

async function run() {
  let passed = 0
  for (const entry of cases) {
    const h = await mount(entry.options); let timer
    try { await Promise.race([entry.run(h), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('scenario did not finish')), 10000) })]); await flush(); passed++; console.log('PASS ' + entry.name) }
    catch (error) { error.message = entry.name + ': ' + error.message; throw error }
    finally { clearTimeout(timer); h.page.$destroy() }
  }
  console.log(`weeklyScheduleBulkScale: ${passed} actual Vue component scenarios passed; 0 failed; 0 skipped`)
}
const totalTimeout = setTimeout(() => { console.error('bulk scale suite did not finish'); process.exit(1) }, 120000)
run().then(() => clearTimeout(totalTimeout)).catch(error => { clearTimeout(totalTimeout); console.error(error); process.exitCode = 1 })
