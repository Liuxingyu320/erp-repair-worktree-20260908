const assert = require('assert')
const fs = require('fs')
const path = require('path')
const read = name => fs.readFileSync(path.join(__dirname, '../src', name), 'utf8')
function loadSync(globals) {
  const source = read('utils/onboardSalarySync.js').replace(/^import .*$/gm, '').replace('export async function', 'async function')
  return new Function('crypto', ...Object.keys(globals), source + '\nreturn syncOnboardSalaryBeforeGenerate')(undefined, ...Object.values(globals))
}
function method(source, start, end, args, globals) {
  const body = source.slice(source.indexOf(start) + start.length, source.indexOf(end, source.indexOf(start)))
  assert.ok(body.length > 0)
  return new Function(...Object.keys(globals), `return function(${args}) {${body}}`)(...Object.values(globals))
}
const row = (batchId = '3', rowId = 10) => ({ batchId, rowId, version: 2, expectedSourceId: null, expectedProfileHash: 'a'.repeat(64), confirmed: false })
async function main() {
  const dialog = read('views/hr/components/HrSignDataImportDialog.vue')
  const company = read('views/oa/signTask/SignOnboardCompanyWorkDialog.vue')
  for (const source of [dialog, company]) assert.ok(!source.includes('hr-salary-confirm-dialog'), 'no extra salary form or confirmation step')
  let sent
  const api = new Function('request', read('api/oa/signTask.js').replace(/^import .*$/gm, '').replace(/export function/g, 'function') + '\nreturn { archiveOnboardSignSalary }')(config => { sent = config; return Promise.resolve() })
  await api.archiveOnboardSignSalary({ batchId: 3, rows: [{ ...row(), salaryTotal: 99999 }], salaryTotal: 99999, requestId: 'salary_test_1', effectiveDate: '2026-01-01', confirmed: true, reason: '自动同步合同工资' })
  assert.strictEqual(sent.data.salaryTotal, undefined)
  assert.deepStrictEqual(Object.keys(sent.data.rows[0]).sort(), ['expectedProfileHash', 'expectedSourceId', 'rowId', 'version'])

  const events = []
  let rejectSync = false
  const globals = { syncOnboardSalaryBeforeGenerate: async () => { events.push('sync'); if (rejectSync) throw Error('金额合计不符') },
    generateOnboardSignImport: async () => { events.push('generate'); return { code: 200 } } }
  const generate = method(dialog, '    generateRows(targetRows) {', '\n    },\n    sendRows', 'targetRows', globals)
  const target = { batchId: 3, batch: { version: 1 }, confirmation: {}, salarySyncState: {}, isReadyToGenerate: () => true,
    generationConfirmedFor: () => true, targetRowsRequireHistoricalSupplement: () => false, targetRowsRequireWarningReason: () => false,
    createRequestId: () => 'generation_test', errorMessage: e => e.message, $modal: { msgSuccess() {} }, refreshBatch: async () => {} }
  await generate.call(target, [row()]); assert.deepStrictEqual(events, ['sync', 'generate'])
  events.length = 0; rejectSync = true; await generate.call(target, [row()])
  assert.deepStrictEqual(events, ['sync']); assert.strictEqual(target.operationError, '金额合计不符')
  events.length = 0
  const execute = method(company, '    execute() {', '\n    },\n    applyResults', '', {
    syncOnboardSalaryBeforeGenerate: async () => events.push('sync'),
    executeOnboardSignCompanyWork: async () => { events.push('company'); return { data: {} } }, signBusinessText: v => v
  })
  await execute.call({ rows: [row()], salarySyncState: {}, payload: () => ({}), applyResults() {}, successCount: 0, $message: { error() {} } })
  assert.deepStrictEqual(events, ['sync', 'company'])

  let statusCalls = 0, writes = 0, original; const recovery = {}
  const sync = loadSync({
    previewOnboardSignSalary: async () => ({ data: [row()] }),
    getOnboardSignSalaryStatus: async data => { statusCalls++; if (statusCalls === 2) throw Error('连接中断'); return { data: { status: statusCalls === 1 ? 'NOT_FOUND' : 'CONFIRMED' } } },
    archiveOnboardSignSalary: async data => { writes++; original = JSON.stringify(data); throw Error('响应丢失') }
  })
  await assert.rejects(sync([row()], recovery), /连接中断/)
  assert.match(recovery.attempts[0].request.requestId, /^[A-Za-z0-9_-]{8,64}$/)
  await sync([row()], recovery)
  assert.strictEqual(writes, 1, 'generation retry recovers the previous write without duplicating it')
  assert.strictEqual(JSON.stringify(recovery.attempts[0].request), original)

  const ids = [], counts = {}, partial = {}; let secondFails = true
  const partialSync = loadSync({
    previewOnboardSignSalary: async (batchId, rows) => ({ data: rows }),
    getOnboardSignSalaryStatus: async () => ({ data: { status: 'NOT_FOUND' } }),
    archiveOnboardSignSalary: async data => { ids.push([data.batchId, data.requestId]); counts[data.batchId] = (counts[data.batchId] || 0) + 1; if (data.batchId === '4' && secondFails) throw Error('第二批冲突'); return { data: { status: 'CONFIRMED' } } }
  })
  await assert.rejects(partialSync([row(), row('4', 20)], partial), /第二批冲突/)
  secondFails = false; await partialSync([row(), row('4', 20)], partial)
  assert.deepStrictEqual(counts, { '3': 1, '4': 2 }); assert.strictEqual(ids[1][1], ids[2][1])

  const changed = {}; const selectedWrites = []; let failFirstStatus = true
  const changeSync = loadSync({ previewOnboardSignSalary: async (batchId, rows) => ({ data: rows }),
    getOnboardSignSalaryStatus: async () => { if (failFirstStatus) { failFirstStatus = false; throw Error('状态查询超时') }; return { data: { status: 'NOT_FOUND' } } },
    archiveOnboardSignSalary: async data => { selectedWrites.push(data.rows.map(row => row.rowId)); return { data: { status: 'CONFIRMED' } } } })
  await assert.rejects(changeSync([row('3', 10)], changed), /状态查询超时/)
  await changeSync([row('3', 20)], changed)
  assert.deepStrictEqual(selectedWrites, [[20]], 'changing selection cannot write salaries for the previous selection')

  const chunks = []
  const confirmedSync = loadSync({ previewOnboardSignSalary: async (batchId, rows) => { chunks.push(rows.length); return { data: rows.map(row => ({ ...row, confirmed: true })) } },
    getOnboardSignSalaryStatus: async () => { throw Error('already synchronized must not write') }, archiveOnboardSignSalary: async () => { throw Error('must not write') } })
  await confirmedSync(Array.from({ length: 201 }, (_, i) => row('3', i + 1)), {})
  assert.deepStrictEqual(chunks, [100, 100, 1])
  console.log('onboarding salary sync: 8 groups passed (one-click generation, invalid amount stop, server values, lost response, partial retry, changed selection, HTTP id and batch chunking)')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
