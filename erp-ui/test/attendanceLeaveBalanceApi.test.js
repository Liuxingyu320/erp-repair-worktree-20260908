const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const file = path.resolve(__dirname, '../src/api/oa/attendanceLeaveBalance.js')
const code = babel.transformSync(fs.readFileSync(file, 'utf8'), { filename: file, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
const captured = [], moduleValue = { exports: {} }
vm.runInNewContext(code, { module: moduleValue, exports: moduleValue.exports, require(id) { assert.equal(id, '@/utils/request'); return config => { captured.push(config); return Promise.resolve({ data: {} }) } } })
const api = moduleValue.exports
const base = '/oa/attendance-v2/leave/balance'
const options = { silentError: true, url: '/wrong', method: 'delete', params: { userId: 999 }, data: { amount: 999 } }
let cases = 0
for (const [name, args, url] of [
  ['getMyLeaveBalance', [1], '/my'], ['getEmployeeLeaveBalance', [11, 1], '/employees/11'],
  ['getEmployeeLeaveBalanceLedger', [11, 1, 99], '/employees/11/ledger'], ['listLeaveBalanceRules', [], '/rules'],
  ['getLeaveBalanceRule', [31], '/rules/31'], ['listLeaveBalanceLocations', [], '/locations']
]) {
  api[name](...args); let call = captured.at(-1); assert.notEqual(call.silentError, true)
  api[name](...args, options); call = captured.at(-1); assert.equal(call.silentError, true); assert.equal(call.method, 'get'); assert.equal(call.url, base + url)
  assert.notEqual(call.params && call.params.userId, 999); assert.equal(call.data, undefined); cases++
}
for (const [name, args, method, url] of [
  ['recalculateMyLeaveBalance', [1], 'post', '/my/recalculate'], ['recalculateEmployeeLeaveBalance', [11, 1], 'post', '/employees/11/recalculate'],
  ['adjustEmployeeLeaveBalance', [11, { leaveTypeId: 1, amount: 1 }], 'post', '/employees/11/adjustments'],
  ['createLeaveBalanceRule', [{}], 'post', '/rules'], ['updateLeaveBalanceRule', [31, {}], 'put', '/rules/31/draft'],
  ['publishLeaveBalanceRule', [31, '9007199254740993'], 'post', '/rules/31/publish'],
  ['createLeaveBalanceLocation', [{}], 'post', '/locations'], ['updateLeaveBalanceLocation', [41, {}], 'put', '/locations/41']
]) {
  api[name](...args, options); const call = captured.at(-1); assert.equal(call.method, method); assert.equal(call.url, base + url); assert.equal(call.silentError, undefined); cases++
}
api.recalculateMyLeaveBalance(1); assert.deepEqual(JSON.parse(JSON.stringify(captured.at(-1).data)), { leaveTypeId: 1 })
api.publishLeaveBalanceRule(31, '9007199254740993'); assert.equal(captured.at(-1).data.rowVersion, '9007199254740993')
assert.equal(cases, 14)
console.log(`attendanceLeaveBalanceApi: ${cases} actual API method contracts passed; 0 failed; 0 skipped`)
