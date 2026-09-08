const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const source = fs.readFileSync(
  path.resolve(__dirname, '../src/views/oa/attendance/components/ShiftManagement.vue'),
  'utf8'
)
const helperStart = source.indexOf("const CONTINUOUS_PUNCH_MODE = 'SHIFT_BOUNDARY'")
const helperEnd = source.indexOf('\nconst emptyForm', helperStart)
assert.ok(helperStart >= 0 && helperEnd > helperStart, 'shift segment helpers must remain testable')

function runHelper(expression, extra) {
  const context = Object.assign({ result: null }, extra || {})
  vm.runInNewContext(`${source.slice(helperStart, helperEnd)}\nresult = ${expression}`, context)
  return JSON.parse(JSON.stringify(context.result))
}

function definition(workSegments) {
  return runHelper('buildShiftDefinition(input)', { input: workSegments })
}

function windows(workSegments, offsets) {
  return runHelper('derivedSegmentPunchWindows(workSegments, offsets)', {
    workSegments,
    offsets: offsets || {}
  })
}

assert.ok(
  !source.includes('this.form.punchMode = SEGMENT_PUNCH_MODE'),
  'adding a second work segment must not auto-switch punch mode'
)
assert.ok(
  source.includes("punchMode: CONTINUOUS_PUNCH_MODE"),
  'new shifts must default to SHIFT_BOUNDARY'
)
assert.ok(
  source.includes('v-if="form.punchMode === segmentPunchMode"') &&
    source.includes('derivedPunchWindowPreview') &&
    source.includes('derivedSegmentPunchWindows'),
  'PER_WORK_SEGMENT must preview derived facing windows'
)

const split = definition([
  { startTime: '08:00:00', endTime: '12:00:00' },
  { startTime: '14:00:00', endTime: '18:00:00' }
])
assert.deepStrictEqual(split, {
  startTime: '08:00:00',
  endTime: '18:00:00',
  crossDay: false,
  standardMinutes: 480,
  segments: [
    { segmentType: 'WORK', segmentOrder: 1, startMinuteOffset: 480, endMinuteOffset: 720, paid: true },
    { segmentType: 'BREAK', segmentOrder: 2, startMinuteOffset: 720, endMinuteOffset: 840, paid: false },
    { segmentType: 'WORK', segmentOrder: 3, startMinuteOffset: 840, endMinuteOffset: 1080, paid: true }
  ]
}, '08-12 / 14-18 must become two paid WORK segments separated by one automatic unpaid BREAK')

const overnight = definition([
  { startTime: '20:00:00', endTime: '23:00:00' },
  { startTime: '01:00:00', endTime: '05:00:00' }
])
assert.strictEqual(overnight.crossDay, true)
assert.strictEqual(overnight.standardMinutes, 420)
assert.deepStrictEqual(overnight.segments.map(segment => [
  segment.segmentType,
  segment.startMinuteOffset,
  segment.endMinuteOffset
]), [
  ['WORK', 1200, 1380],
  ['BREAK', 1380, 1500],
  ['WORK', 1500, 1740]
], 'ordered work periods must retain next-day minute offsets')

assert.throws(
  () => definition([{ startTime: '', endTime: '12:00:00' }]),
  /完整填写/,
  'incomplete periods must fail before the shift request is sent'
)

const lunch = windows([
  { startTime: '08:00:00', endTime: '12:00:00' },
  { startTime: '14:00:00', endTime: '18:00:00' }
], {
  checkInOpenMinutes: 120,
  checkInCloseMinutes: 120,
  checkOutOpenMinutes: 120,
  checkOutCloseMinutes: 120
})
assert.strictEqual(lunch.issues.length, 0, 'standard lunch split must keep usable windows after midpoint clamp')
assert.deepStrictEqual(lunch.slots.map(slot => [
  slot.punchType,
  slot.opensAt,
  slot.closesAt,
  slot.clamped
]), [
  ['IN', 21600, 36000, false],
  ['OUT', 36000, 46800, true],
  ['IN', 46800, 57600, true],
  ['OUT', 57600, 72000, false]
], 'adjacent OUT/IN must clamp to the 13:00 rest midpoint; first IN and last OUT stay unclamped')
assert.strictEqual(lunch.slots[1].rangeText, '10:00–13:00')
assert.strictEqual(lunch.slots[2].rangeText, '13:00–16:00')
assert.ok(lunch.slots[1].configuredRangeText === '10:00–14:00' && lunch.slots[1].clamped)

const night = windows([
  { startTime: '20:00:00', endTime: '23:00:00' },
  { startTime: '01:00:00', endTime: '05:00:00' }
], {
  checkInOpenMinutes: 120,
  checkInCloseMinutes: 120,
  checkOutOpenMinutes: 120,
  checkOutCloseMinutes: 120
})
assert.deepStrictEqual(night.slots.map(slot => [slot.opensAt, slot.closesAt, slot.clamped]), [
  [64800, 79200, false],
  [75600, 86400, true],
  [86400, 97200, true],
  [97200, 111600, false]
], 'overnight facing windows must clamp at business-date midnight using absolute offsets')
assert.strictEqual(night.slots[1].rangeText, '21:00–+1天 00:00')
assert.strictEqual(night.slots[2].rangeText, '+1天 00:00–+1天 03:00')

const oddGap = windows([
  { startTime: '08:00:00', endTime: '12:00:00' },
  { startTime: '12:01:00', endTime: '18:00:00' }
], {
  checkInOpenMinutes: 120,
  checkInCloseMinutes: 120,
  checkOutOpenMinutes: 120,
  checkOutCloseMinutes: 120
})
assert.strictEqual(oddGap.issues.length, 0, 'odd-minute rest must keep usable windows at the exact :30 midpoint')
assert.strictEqual(oddGap.slots[1].closesAt, 43230)
assert.strictEqual(oddGap.slots[2].opensAt, 43230)
assert.strictEqual(oddGap.slots[1].rangeText, '10:00–12:00:30')
assert.strictEqual(oddGap.slots[2].rangeText, '12:00:30–14:01')
assert.ok(oddGap.slots[1].clamped && oddGap.slots[2].clamped)

const emptyFacing = windows([
  { startTime: '08:00:00', endTime: '12:00:00' },
  { startTime: '12:00:00', endTime: '18:00:00' }
], {
  checkInOpenMinutes: 120,
  checkInCloseMinutes: 120,
  checkOutOpenMinutes: 0,
  checkOutCloseMinutes: 120
})
assert.ok(emptyFacing.issues.length > 0, 'contiguous WORK with no early-out window must fail after seam clamp')
assert.ok(
  emptyFacing.slots[1].valid === false
    && /上午工作段下班/.test(emptyFacing.slots[1].issue)
    && /不足 60 秒/.test(emptyFacing.slots[1].issue),
  'effective OUT window shorter than 60 seconds must block save'
)

const oddGapTooShort = windows([
  { startTime: '08:00:00', endTime: '12:00:00' },
  { startTime: '12:01:00', endTime: '18:00:00' }
], {
  checkInOpenMinutes: 120,
  checkInCloseMinutes: 120,
  checkOutOpenMinutes: 0,
  checkOutCloseMinutes: 120
})
assert.ok(
  oddGapTooShort.slots[1].closesAt === 43230
    && oddGapTooShort.slots[1].valid === false
    && /不足 60 秒/.test(oddGapTooShort.slots[1].issue),
  'odd-minute midpoint leaving a 30-second OUT window must be rejected'
)

console.log('attendance shift segment tests passed')
