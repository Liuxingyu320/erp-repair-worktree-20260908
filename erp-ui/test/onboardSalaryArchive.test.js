const assert = require('assert')
const fs = require('fs')
const path = require('path')
const read = name => fs.readFileSync(path.join(__dirname, '../src', name), 'utf8')
const dialog = read('views/hr/components/HrSignDataImportDialog.vue')
const company = read('views/oa/signTask/SignOnboardCompanyWorkDialog.vue')
const api = read('api/oa/signTask.js')
function method(source, start, end, args, globals) {
  const body = source.slice(source.indexOf(start) + start.length, source.indexOf(end, source.indexOf(start)))
  assert.ok(body.length > 0)
  return new Function(...Object.keys(globals), `return function(${args}) {${body}}`)(...Object.values(globals))
}
async function main() {
  let sent
  const archive = method(api, 'export function archiveOnboardSignSalary(batchId, rows) {', '\n}', 'batchId, rows', {
    request: config => { sent = config; return Promise.resolve() }
  })
  await archive(3, [{ rowId: 10, version: 2, employeeId: 11, salaryTotal: 999999 }])
  assert.deepStrictEqual(sent.data, { batchId: 3, rows: [{ rowId: 10, version: 2 }] }, 'browser cannot supply wage amounts')

  const events = []
  let rejectArchive = false
  const generate = method(dialog, '    generateRows(targetRows) {', '\n    },\n    sendRows', 'targetRows', {
    archiveOnboardSignSalary: async (id, rows) => { events.push(['archive', id, rows[0].version]); if (rejectArchive) throw Error('archive unavailable') },
    generateOnboardSignImport: async () => { events.push(['generate']); return { code: 200 } }
  })
  const target = {
    batchId: 3, batch: { version: 1 }, confirmation: {},
    isReadyToGenerate: () => true, generationConfirmedFor: () => true,
    targetRowsRequireHistoricalSupplement: () => false, targetRowsRequireWarningReason: () => false,
    createRequestId: () => 'fixed-generation-request', errorMessage: error => error.message,
    $modal: { msgSuccess() {} }, refreshBatch: () => Promise.resolve()
  }
  const rows = [{ rowId: 10, employeeId: 11, version: 2 }]
  await generate.call(target, rows)
  assert.deepStrictEqual(events.map(item => item[0]), ['archive', 'generate'])
  assert.strictEqual(events[0][2], 2)
  events.length = 0; rejectArchive = true
  await generate.call(target, rows)
  assert.deepStrictEqual(events.map(item => item[0]), ['archive'], 'failed archive prevents generation')
  assert.strictEqual(target.operationError, 'archive unavailable')
  assert.strictEqual(target.generating, false)

  events.length = 0; rejectArchive = false
  const execute = method(company, '    execute() {', '\n    },\n    applyResults', '', {
    archiveOnboardSignSalary: async id => { events.push(['archive', id]); if (rejectArchive) throw Error('unavailable') },
    executeOnboardSignCompanyWork: async () => { events.push(['generate']); return { data: {} } },
    signBusinessText: message => message
  })
  const companyTarget = { rows: [{ batchId: 3, rowId: 10, version: 2 }, { batchId: 4, rowId: 20, version: 1 }],
    normalizeId: Number, payload: () => ({}), applyResults() {}, successCount: 0, $message: { error() {} } }
  await execute.call(companyTarget)
  assert.deepStrictEqual(events, [['archive', 3], ['archive', 4], ['generate']])
  events.length = 0; rejectArchive = true
  await execute.call(companyTarget)
  assert.deepStrictEqual(events, [['archive', 3]])
  assert.strictEqual(companyTarget.executing, false)
  console.log('onboarding salary archive: payload, order, failure and company-work tests passed')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
