const assert = require('assert')
const fs = require('fs')
const path = require('path')

const api = fs.readFileSync(
  path.resolve(__dirname, '../src/api/oa/attendanceV2.js'),
  'utf8'
)
const page = fs.readFileSync(
  path.resolve(__dirname, '../src/views/oa/attendance/components/DayResultManagement.vue'),
  'utf8'
)

assert.ok(
  api.includes('/time-credits/context/${targetDayResultId}') &&
    api.includes('/time-credits/adjustments') &&
    api.includes('/time-credits/adjustments/${adjustmentId}/reverse'),
  'time-credit UI must use the scoped context, apply and reverse contracts'
)
assert.ok(
  page.includes("v-hasPermi=\"['oa:attendance:time-credit:manage']\"") &&
    page.includes('店长调整：加班抵扣早退'),
  'only authorized store managers may open the time-credit adjustment'
)
assert.ok(
  page.includes('同一工资月') &&
    page.includes('抵扣部分不再计算加班费') &&
    page.includes('原打卡、照片、定位、原加班和原早退均保留'),
  'manager UI must explain the no-double-benefit and raw-evidence rules'
)
assert.ok(
  page.includes('该员工本月工资已生成') &&
    page.includes('原加班(分)') && page.includes('净加班(分)') &&
    page.includes('原早退(分)') && page.includes('净早退(分)'),
  'manager UI must expose payroll locking and raw/net minute projections'
)
assert.ok(
  page.includes('撤销只追加反向记录，不删除原抵扣') &&
    page.includes('timeCreditCommandRequestId') &&
    page.includes('timeCreditRequestIds[signature]') &&
    page.includes('clearTimeCreditCommandRequestId'),
  'reversal must be append-only and identical retries must keep one request id until success'
)
assert.ok(
  page.includes('timeCreditContext.ledgerInconsistent') &&
    page.includes('只能先撤销失效调整'),
  'recalculation conflicts must keep history visible while disabling new credits'
)

console.log('attendance time-credit management tests passed')
