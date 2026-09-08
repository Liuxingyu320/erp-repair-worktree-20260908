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
  api.includes('/remaining-work/schedules/${scheduleId}/intervals') &&
    api.includes('/remaining-work/confirmations'),
  'remaining-work review must use its independent server contract'
)
assert.ok(
  api.includes('/remaining-work/confirmations/${confirmationId}/attachments') &&
    api.includes("responseType: 'blob'"),
  'remaining-work evidence must stay private and use authenticated blob reads'
)
assert.ok(
  page.includes("v-hasPermi=\"['oa:attendance:remaining-work:manage']\"") &&
    page.includes('部分请假剩余工作核验'),
  'only authorized managers may open the remaining-work review'
)
assert.ok(
  page.includes('ATTENDED') && page.includes('ABSENT') &&
    page.includes('RETURN_FOR_EVIDENCE'),
  'review UI must expose attended, absent and return-for-evidence decisions'
)
assert.ok(
  page.includes('不会生成现场打卡槽') &&
    page.includes('每次提交都会追加保留审计记录'),
  'review UI must explain that confirmations are append-only and not punch slots'
)
assert.ok(
  page.includes('REMAINING_WORK_CONFIRMATION_REQUIRED') &&
    page.includes('REMAINING_WORK_EVIDENCE_REQUIRED') &&
    page.includes('REMAINING_WORK_CONFIRMATION_CHANGED'),
  'settlement blockers must route managers to the review UI'
)

console.log('attendance remaining-work management tests passed')
