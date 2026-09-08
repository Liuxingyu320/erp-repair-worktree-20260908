const assert = require('assert')
const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(uiRoot, '..')
const readUi = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')
const readRepo = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8')

const api = readUi('src/api/oa/attendanceV2.js')
const attendancePage = readUi('src/views/oa/attendance/index.vue')
const sitePage = readUi('src/views/oa/attendance/components/AttendanceSiteManagement.vue')
const schedulePage = readUi('src/views/oa/attendance/components/WeeklySchedule.vue')
const controller = readRepo('erp-modules/erp-oa/src/main/java/com/erp/oa/attendance/controller/AttendanceV2Controller.java')

;[
  'listAttendanceSites',
  'getAttendanceSite',
  'createAttendanceSite',
  'updateAttendanceSite',
  'changeAttendanceSiteStatus',
  'deleteAttendanceSite'
].forEach(name => assert.ok(api.includes(`export function ${name}`), `attendance API must export ${name}`))

;[
  'oa:attendance:site:list',
  'oa:attendance:site:query',
  'oa:attendance:site:add',
  'oa:attendance:site:edit',
  'oa:attendance:site:remove'
].forEach(permission => assert.ok(controller.includes(`@RequiresPermissions("${permission}")`), `site endpoint must retain ${permission}`))

assert.ok(attendancePage.includes('<attendance-site-management :shop-context="shopContext"'), 'desktop attendance must mount site management with the selected shop context')
assert.ok(sitePage.includes('shopId: this.shopContext.deptId'), 'site list and writes must stay bound to the selected shop')
assert.ok(sitePage.includes('rowVersion: row.rowVersion'), 'status changes must use optimistic row versions')
assert.ok(sitePage.includes('deleteAttendanceSite(row.siteId, row.rowVersion)'), 'site deletion must use the versioned contract')
assert.ok(sitePage.includes("coordinateSystem: 'GCJ02'") && sitePage.includes('radiusMeters: 200') && sitePage.includes('maxAccuracyMeters: 100'), 'site editor must expose complete geofence defaults')

assert.ok(schedulePage.includes("listAttendanceSites({ shopId, status: 'ENABLED' })"), 'weekly scheduling must only offer enabled sites in the selected shop')
assert.ok(schedulePage.includes('if (!value.siteId) throw new Error'), 'draft validation must fail closed when siteId is missing')
assert.ok(schedulePage.includes('siteId: value.siteId'), 'draft payload must submit the selected siteId')
assert.ok(schedulePage.includes('siteNameSnapshot') && schedulePage.includes('addressSnapshot'), 'published schedules must render their frozen site snapshot')

console.log('attendance V2 site admin contract tests passed')
