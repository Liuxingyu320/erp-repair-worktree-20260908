const assert = require('assert')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const file = path.resolve(__dirname, '../src/api/oa/attendanceOvertimeTransfer.js')
const code = babel.transformSync(fs.readFileSync(file, 'utf8'), { filename: file, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
const captured = [], mod = { exports: {} }
vm.runInNewContext(code, { module: mod, exports: mod.exports, require(id) { assert.equal(id, '@/utils/request'); return config => { captured.push(config); return Promise.resolve({ data: {} }) } } })
const api = mod.exports, base = '/oa/attendance-v2/leave/balance/overtime-transfers'
api.getOvertimeTransferContext('9007199254740993', '2'); assert.equal(captured.at(-1).silentError, undefined)
api.getOvertimeTransferContext('9007199254740993', '2', { silentError: true, url: '/wrong', method: 'delete', params: { userId: 99 }, data: {} })
let call = captured.at(-1); assert.equal(call.url, base + '/context/9007199254740993'); assert.equal(call.method, 'get'); assert.equal(call.silentError, true); assert.deepEqual(JSON.parse(JSON.stringify(call.params)), { leaveTypeId: '2' }); assert.equal(call.data, undefined)
const data = { sourceDayResultId: '9007199254740993', sourceVersion: '9007199254740995', leaveTypeId: '2', transferMinutes: 60, reason: '合成核定', clientRequestId: 'request-001' }
api.confirmOvertimeTransfer(data, { silentError: true }); call = captured.at(-1); assert.equal(call.url, base); assert.equal(call.method, 'post'); assert.strictEqual(call.data, data); assert.equal(call.data.sourceVersion, '9007199254740995'); assert.equal(call.silentError, undefined)
api.reverseOvertimeTransfer('9007199254740997', { reason: '合成撤销', clientRequestId: 'reverse-001' }); call = captured.at(-1); assert.equal(call.url, base + '/9007199254740997/reverse'); assert.equal(call.method, 'post'); assert.equal(call.silentError, undefined)
assert.deepEqual(Object.keys(api).sort(), ['confirmOvertimeTransfer', 'getOvertimeTransferContext', 'reverseOvertimeTransfer'])
console.log('attendanceOvertimeTransferApi: 3 actual method contracts passed; 0 failed; 0 skipped')
