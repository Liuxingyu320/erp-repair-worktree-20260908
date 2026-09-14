const assert = require('assert')
const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')

const api = read('src/api/oa/attendanceV2.js')
const mobileRouteDefinitions = read('src/views/mobile/mobileRouteDefinitions.js')
const mobilePage = read('src/views/mobile/attendance/index.vue')
const mobileTemplate = mobilePage.split('<script>')[0]
const punchPolicy = read('src/views/mobile/attendance/attendancePunchPolicy.js')
const punchAttempt = read('src/views/mobile/attendance/mobileAttendancePunchAttempt.js')
const liveCamera = read('src/views/mobile/attendance/mobileAttendanceLiveCamera.js')
const mobileLeave = read('src/views/mobile/attendance/MobileAttendanceLeave.vue')
const mobileCorrection = read('src/views/mobile/attendance/MobileAttendanceCorrection.vue')
const androidManifest = read('android/app/src/main/AndroidManifest.xml')
const iosInfoPlist = read('ios/App/App/Info.plist')
const packageJson = read('package.json')
const vueConfig = read('vue.config.js')
const legacyRuntime = read('src/views/mobile/feature/featureActionRuntime.js')
const legacyActions = read('src/views/mobile/feature/featureActions.js')
const legacyService = read('src/views/mobile/feature/featureActionService.js')
const desktopPage = read('src/views/oa/attendance/index.vue')
const siteManagement = read('src/views/oa/attendance/components/AttendanceSiteManagement.vue')
const shiftManagement = read('src/views/oa/attendance/components/ShiftManagement.vue')
const weeklySchedule = read('src/views/oa/attendance/components/WeeklySchedule.vue')
const dayResultPage = read('src/views/oa/attendance/components/DayResultManagement.vue')
const desktopLeave = read('src/views/oa/attendance/components/LeaveManagement.vue')
const desktopCorrection = read('src/views/oa/attendance/components/CorrectionManagement.vue')

assert.ok(api.includes("const BASE_URL = '/oa/attendance-v2'"), 'V2 API must use the OA gateway route')
;[
  '/shifts', '/sites', '/schedules', '/schedules/batch', '/schedules/publish',
  '/employee-options', '/schedules/${scheduleId}', '/today', '/punch/challenge', '/punch/status', '/punch', '/evidence/'
].forEach(fragment => assert.ok(api.includes(fragment), `V2 API must include ${fragment}`))
;['/leave/types', '/leave/my', '/leave/shop', '/leave/drafts', '/leave/by-client-request/${encodeURIComponent(clientRequestId)}', '/leave/${leaveRequestId}/submit', '/corrections/eligible-schedules', '/corrections/my', '/corrections/shop', '/corrections/drafts', '/corrections/${correctionRequestId}/submit']
  .forEach(fragment => assert.ok(api.includes(fragment), `V2 request API must include ${fragment}`))
;['listAttendanceDayResults', 'getAttendanceDayResultPreflight', 'countUnfinalizedAttendanceDayResults', 'settleAttendanceDayResults']
  .forEach(name => assert.ok(api.includes(`function ${name}`), `V2 day-result API must export ${name}`))
;['/day-results`', '/day-results/preflight', '/day-results/unfinalized', '/day-results/settle']
  .forEach(fragment => assert.ok(api.includes(fragment), `V2 day-result API must include ${fragment}`))
assert.ok(api.includes("data.append('challengeToken'"), 'multipart punch must send challengeToken')
assert.ok(api.includes("data.append('latitude'") && api.includes("data.append('longitude'"), 'multipart punch must send audit coordinates without rendering them')
assert.ok(api.includes("data.append('accuracyMeters'"), 'multipart punch must send accuracyMeters')
assert.ok(api.includes("data.append('clientCoordinateSystem'"), 'multipart punch must identify navigator coordinates as WGS84')
assert.ok(api.includes("data.append('clientCaptureTime'"), 'multipart punch must send the fresh local capture time')
assert.ok(api.includes("data.append('punchSlotKey'"), 'segmented punches must submit the server-issued punch slot key')
assert.ok(/function deleteAttendanceShift[\s\S]*?method: 'delete'[\s\S]*?rowVersion/.test(api), 'disabled unused shifts must use the versioned delete contract')
;['listAttendanceSites', 'getAttendanceSite', 'createAttendanceSite', 'updateAttendanceSite', 'changeAttendanceSiteStatus', 'deleteAttendanceSite']
  .forEach(name => assert.ok(api.includes(`function ${name}`), `V2 site API must export ${name}`))
assert.ok(!api.includes("data.append('challengeId'"), 'multipart punch must not send obsolete challengeId')
assert.ok(!api.includes("data.append('accuracy'"), 'multipart punch must not send obsolete accuracy')

assert.ok(
  /path:\s*['"]\/mobile\/attendance['"][\s\S]*?component:\s*\(\)\s*=>\s*import\(['"]@\/views\/mobile\/attendance\/index['"]\)/.test(mobileRouteDefinitions),
  '/mobile/attendance must use the dedicated attendance page'
)
assert.ok(mobileRouteDefinitions.includes("'oa:attendance:leave:approve'") && mobileRouteDefinitions.includes("'oa:attendance:correction:approve'"), 'mobile approval todos must be able to register the dedicated attendance route')
assert.ok(!legacyRuntime.includes('checkInAttendance') && !legacyRuntime.includes('checkOutAttendance'), 'generic mobile runtime must not expose legacy attendance actions')
assert.ok(!legacyActions.includes('checkInAttendance') && !legacyActions.includes('checkOutAttendance'), 'generic action catalog must not expose legacy attendance actions')
assert.ok(!legacyService.includes('@/api/oa/attendance'), 'generic mobile action service must not import the legacy attendance API')
assert.ok(!legacyRuntime.includes('checkInLocation: "MOBILE"') && !legacyRuntime.includes('checkOutLocation: "MOBILE"'), 'literal MOBILE punches must be removed')

const challengeCall = mobilePage.indexOf('createPunchChallenge({')
const locationCall = mobilePage.indexOf('return this.getHighAccuracyPosition()', challengeCall)
const cameraCapture = mobilePage.indexOf('ref="liveCameraVideo"')
const multipartCall = mobilePage.indexOf('submitAttendancePunch({')
assert.ok(challengeCall > -1 && locationCall > challengeCall, 'mobile flow must obtain a server challenge before geolocation')
assert.ok(cameraCapture > -1 && mobilePage.includes('getUserMedia(') && mobilePage.includes('navigator.geolocation.getCurrentPosition'), 'mobile flow must require high-accuracy geolocation and a live browser camera stream')
assert.ok(!mobileTemplate.includes('type="file"') && !mobileTemplate.includes('capture="environment"'), 'browser attendance must not fall back to gallery-capable file input capture')
assert.ok(liveCamera.includes("facingMode: { exact: 'environment' }") && liveCamera.includes('requireEnvironmentStream'), 'browser attendance must fail closed unless the returned live stream is confirmed as rear-facing')
assert.ok(mobilePage.includes('window.isSecureContext === false') && mobilePage.includes('BROWSER_LOCATION_REQUIRES_HTTPS'), 'browser attendance must explain that the native location prompt requires HTTPS')
assert.ok(mobilePage.includes('浏览器会直接询问定位权限') && mobilePage.includes('“位置”改为“询问”'), 'browser attendance must describe the direct prompt and browser-specific recovery path')
assert.ok(vueConfig.includes('LOCAL_MOBILE_HTTPS_KEY_FILE') && vueConfig.includes('LOCAL_MOBILE_HTTPS_CERT_FILE') && vueConfig.includes('https: localHttps'), 'local mobile browser testing must support an explicit HTTPS certificate pair')
assert.ok(packageJson.includes('@capacitor/camera') && packageJson.includes('@capacitor/geolocation'), 'installed apps must bundle native camera and geolocation plugins')
assert.ok(mobilePage.includes('Camera.takePhoto({') && mobilePage.includes('source: CameraSource.Camera') && mobilePage.includes('Geolocation.getCurrentPosition({'), 'installed apps must force native camera-only capture and native high-accuracy location')
assert.ok(androidManifest.includes('android.permission.CAMERA') && androidManifest.includes('android.permission.ACCESS_FINE_LOCATION'), 'Android must declare camera and precise location permissions')
assert.ok(iosInfoPlist.includes('NSCameraUsageDescription') && iosInfoPlist.includes('NSLocationWhenInUseUsageDescription'), 'iOS must declare camera and location purpose strings')
assert.ok(multipartCall > locationCall, 'multipart punch submission must happen only after challenge and location stages')
assert.ok(mobilePage.includes('result.success === true'), 'UI must require explicit server punch success')
assert.ok(mobilePage.includes('result.event && String(result.event.resolvedAddress'), 'UI must require the server-resolved readable address before showing success')
assert.ok(mobilePage.includes('Boolean(result.evidenceId)'), 'UI must require server watermark evidence before showing success')
assert.ok(punchAttempt.indexOf('writeVerified(attempt') < punchAttempt.indexOf('dependencies.execute(attempt)'), 'a stable punch attempt must be durably verified before network dispatch')
assert.ok(punchAttempt.includes("value.status === 'settled'") && punchAttempt.includes("status: 'pending', outcome: ''"), 'local settlement hints must be re-confirmed from an authoritative endpoint after reload')
assert.ok(mobilePage.includes("window.addEventListener('erp:dept-changed'") && mobilePage.includes('attendanceContextEpoch'), 'attendance async work must be fenced across store changes including A→B→A')
assert.ok(mobilePage.includes('getPunchEvidenceContent(evidenceId)'), 'UI must render evidence through the authenticated content endpoint')
assert.ok(mobilePage.includes('restoreLatestEvidence(this.context.latestEvidenceId,'), 'today refresh must restore the latest private evidence from the server context')
assert.ok(/restoreLatestEvidence\(latestEvidenceId, options\) \{[\s\S]*?loadEvidence\(latestEvidenceId[\s\S]*?evidenceLoadError[\s\S]*?return false/.test(mobilePage), 'latest evidence loading must fail independently with a retryable state')
assert.ok(!/restoreLatestEvidence\(latestEvidenceId, options\) \{[\s\S]*?this\.serverAccepted\s*=\s*true[\s\S]*?\n\s*\},/.test(mobilePage), 'restoring historical evidence must never mark the current punch flow as accepted')
assert.ok(mobilePage.includes("serverAccepted ? '打卡已由服务端受理' : '最新打卡证据暂未显示'") && mobilePage.includes('@click="retryEvidence"'), 'failed historical evidence must expose a retry action without hiding today data')
assert.ok(mobilePage.indexOf('context.latestPunch.serverPunchTime') < mobilePage.indexOf('context.latestPunch.serverTime'), 'latest punch must prefer the authoritative serverPunchTime field')
assert.ok(mobilePage.includes('@click="openEvidencePreview"') && mobilePage.includes('aria-label="全屏查看服务端水印打卡证据"'), 'watermark evidence thumbnail must be an accessible full-screen preview trigger')
assert.ok(mobilePage.includes('role="dialog"') && mobilePage.includes('aria-modal="true"') && mobilePage.includes('@click="closeEvidencePreview"'), 'private evidence must open in a closable in-app modal')
assert.ok(mobilePage.includes('@click.self="closeEvidencePreview"') && mobilePage.includes('@keydown.esc.stop.prevent="closeEvidencePreview"'), 'evidence preview must support explicit, backdrop and keyboard close paths')
assert.ok(mobilePage.includes('height: 100dvh') && mobilePage.includes('env(safe-area-inset-top)') && mobilePage.includes('-webkit-overflow-scrolling: touch'), 'evidence preview must account for iPhone Safari viewport, safe area and scrolling')
assert.ok(mobilePage.includes('touch-action: pan-x pan-y pinch-zoom'), 'full-screen evidence must allow touch inspection without navigating to a public URL')
assert.ok(!mobilePage.includes('window.open(') && !mobilePage.includes(':href="evidenceUrl"'), 'private evidence must never be exposed through a new window or public link')
assert.ok(/beforeDestroy\(\) \{[\s\S]*?closeEvidencePreview\(\)[\s\S]*?revokeUrl\('evidenceUrl'\)/.test(mobilePage), 'component teardown must close the preview before revoking its Blob URL')
assert.ok(/revokeUrl\(key\) \{[\s\S]*?key === 'evidenceUrl'[\s\S]*?closeEvidencePreview\(\)[\s\S]*?URL\.revokeObjectURL\(value\)/.test(mobilePage), 'replacing private evidence must close the preview before revoking the active Blob URL')
assert.ok(mobilePage.includes("body.style.position = 'fixed'") && mobilePage.includes('restoreEvidencePreviewScroll()'), 'iPhone evidence preview must lock and restore background page scrolling')
assert.ok(mobilePage.includes('员工、公司到店铺的完整组织路径、班次名称、权威时间、打卡类型、可读地址和凭证编号'), 'mobile review must explain the authoritative server watermark contents')
assert.ok(mobilePage.includes('分段班次·每个工作段上下班都需打卡') && mobilePage.includes('segmentTimeline'), 'mobile today view must render each work segment and its punch checkpoints')
assert.ok(mobilePage.includes("punchSlotKey: this.gate.punchSlotKey || undefined") && mobilePage.includes("punchSlotKey: attempt.punchSlotKey || undefined"), 'challenge and punch command must stay bound to the same server slot')
assert.ok(!/\{\{[^}]*resolvedAddress/.test(mobileTemplate), 'readable address text must appear only inside the server-generated evidence image')
;['考勤地点', '围栏规则', 'distanceMeters', 'radiusText', 'siteName']
  .forEach(fragment => assert.ok(!mobilePage.includes(fragment), `mobile attendance must not expose obsolete site detail: ${fragment}`))
assert.ok(mobilePage.includes('<mobile-attendance-leave') && mobilePage.includes('<mobile-attendance-correction'), 'mobile attendance must use real leave and correction panels')
assert.ok(mobileLeave.includes('createAttendanceLeaveDraft(payload)') && mobileLeave.includes('submitAttendanceLeave(draft.leaveRequestId, draft.rowVersion)'), 'mobile leave must persist a server draft before submit')
assert.ok(mobileLeave.includes('clientRequestId: newClientRequestId()') && mobileLeave.includes('payload.clientRequestId = operation.clientRequestId') && mobileLeave.includes('this.saveAttempt = attempt') && mobileLeave.includes('createAttendanceLeaveDraft(attempt.payload)'), 'mobile leave retries must reuse one stable clientRequestId until the server draft identity is returned')
assert.ok(mobileLeave.includes('.catch(error => this.recoverSubmittedLeave(draft, error, operation))') && mobileLeave.includes("['DRAFT', 'RETURNED'].includes(status)") && mobileLeave.includes('getAttendanceLeaveByClientRequest(clientRequestId)'), 'ambiguous leave submit failures must reconcile authoritative state before retrying a draft update')
assert.ok(mobileLeave.includes("detail.status || '').toUpperCase() === 'CANCELLED'") && mobileLeave.includes('撤回请求已提交，等待审批服务确认'), 'withdraw UI must not claim cancellation before the approval callback changes server status')
assert.ok(mobileLeave.includes('uploadAttendanceLeaveAttachment') && mobileLeave.includes('getAttendanceLeaveAttachmentContent'), 'mobile leave must use private attachment APIs')
assert.ok(mobileCorrection.includes('listAttendanceCorrectionEligibleSchedules') && mobileCorrection.includes('listAttendanceCorrectionEligiblePunchEvents'), 'correction forms must select immutable server schedules/events')
assert.ok(mobileCorrection.includes('createAttendanceCorrectionDraft(payload)') && mobileCorrection.includes('submitAttendanceCorrection(attempt.id, row.rowVersion)') && mobileCorrection.includes('this.mergeCorrection(row)') && mobileCorrection.includes('getAttendanceCorrectionByClientRequest(attempt.clientRequestId)'), 'mobile correction must persist a server draft before submit')
assert.ok(mobileCorrection.includes('v-model="form.targetPunchSlotKey"') && mobileCorrection.includes('targetPunchSlotKey: this.usesSlotTargeting ? this.form.targetPunchSlotKey : null') && mobileCorrection.includes('targetScheduleSegmentSnapshotId: this.usesSlotTargeting ? this.form.targetScheduleSegmentSnapshotId : null'), 'segmented correction must submit both immutable punch-slot identifiers')
assert.ok(mobileCorrection.includes('v-else v-model="form.targetPunchType"'), 'continuous shifts must retain the legacy IN/OUT correction choice')
assert.ok(mobileCorrection.includes('this.form.targetPunchSlotKey && this.form.targetScheduleSegmentSnapshotId') && mobileCorrection.includes('targetSegmentLabelSnapshot'), 'editing an old segmented draft must preserve its immutable slot even outside the eligible schedule range')
assert.ok(mobileCorrection.includes("status === 'MISSING'") && mobileCorrection.includes('eligibleForMissingPunch === false'), 'missing-punch selection must exclude punched, corrected, and approved-leave slots')
assert.ok(mobileCorrection.includes('this.form.correctionRequestId && String(slot.punchSlotKey) === String(this.form.targetPunchSlotKey)'), 'an existing segmented draft must keep its own pending slot selectable while editing')
assert.ok(!mobileCorrection.includes('<option value="OTHER">'), 'unsupported catch-all corrections must not be offered because they can bypass slot evidence rules')
assert.ok(mobileCorrection.includes('correctionChangeText(row)') && mobileCorrection.includes('原始：') && mobileCorrection.includes('→ 申请：') && mobileCorrection.includes('原事件 #'), 'correction approval detail must show original type/time to requested type/time and the immutable event id')
assert.ok(mobileCorrection.includes('preserveOriginalEvent(detail)'), 'editing a correction must retain its occupied original event even when the eligible-event API excludes it')
const correctionScheduleLoader = mobileCorrection.slice(
  mobileCorrection.indexOf('loadEligibleSchedules() {'),
  mobileCorrection.indexOf('loadRows() {')
)
assert.ok(correctionScheduleLoader.includes('this.scheduleError'), 'eligible-schedule failures must use their own warning state')
assert.ok(!correctionScheduleLoader.includes('this.error ='), 'eligible-schedule failures must not race with history-list errors')
assert.ok(mobileCorrection.includes('Promise.all([schedules, this.loadRows()])'), 'correction refresh must retry schedules and history independently')
assert.ok(mobileCorrection.includes('this.canSelf && !this.approvalFocus'), 'manager-only and approval-detail refreshes must not request employee eligible schedules')
assert.ok(mobileCorrection.includes('历史补卡记录仍可查看'), 'schedule failures must explain that correction history remains available')

const {
  attendanceErrorText,
  normalizeTodayContext,
  punchSlotLabel,
  resolvePunchGate,
  restMessage,
  validateLocation,
  validatePhoto
} = require('../src/views/mobile/attendance/attendancePunchPolicy')

assert.strictEqual(
  attendanceErrorText(new Error('FEATURE_DISABLED: feature.oa.attendance.v2.enabled'), '加载失败'),
  '考勤功能尚未启用',
  'disabled attendance must show a user-facing message instead of an internal feature key'
)
assert.strictEqual(
  attendanceErrorText(new Error('EMPLOYEE_NOT_ACTIVE_IN_SHOP'), '加载失败'),
  '当前账号不在有效考勤员工范围内',
  'inactive employees must see a user-facing explanation instead of an internal code'
)
assert.strictEqual(
  attendanceErrorText(new Error('ATTENDANCE_ADDRESS_RESOLVER_UNAVAILABLE'), '加载失败'),
  '定位地址服务暂不可用，本次未打卡，请稍后重试。',
  'unavailable address resolution must be localized and must not claim punch success'
)
assert.match(attendanceErrorText(new Error('ATTENDANCE_ADDRESS_RESOLUTION_FAILED'), '加载失败'), /可读地址.*未打卡/)
assert.match(attendanceErrorText(new Error('ATTENDANCE_ADDRESS_INVALID'), '加载失败'), /地址结果无效.*未打卡/)
assert.match(
  attendanceErrorText(new Error('CORRECTION_TARGET_REQUIRES_REMAINING_WORK_CONFIRMATION'), '补卡失败'),
  /不能补录原卡.*管理者核验/,
  'leave-covered original slots must route users to remaining-work review instead of exposing an internal code'
)
assert.match(
  attendanceErrorText(new Error('PUNCH_DAY_RESULT_ALREADY_SETTLED'), '打卡失败'),
  /已完成结算.*本次未打卡.*显式重算/,
  'a settled day must fail closed with a localized message'
)
assert.match(
  attendanceErrorText(new Error('LEAVE_NO_SCHEDULED_WORK'), '请假提交失败'),
  /只覆盖排班休息段.*没有需要请假的工作时段/,
  'leave submission must explain that rest-only intervals have no effective scheduled work'
)

const noSchedule = normalizeTodayContext({ data: {
  state: 'LOCKED_NO_SCHEDULE', canPunch: false, lockReason: 'NO_PUBLISHED_SCHEDULE', schedule: null,
  latestEvidenceId: 'evidence-100'
} })
assert.deepStrictEqual(resolvePunchGate(noSchedule).ready, false, 'no published schedule must remain locked')
assert.match(resolvePunchGate(noSchedule).reason, /没有已发布排班/)
assert.strictEqual(noSchedule.latestEvidenceId, 'evidence-100', 'today normalization must preserve latestEvidenceId for page reload recovery')

const ready = normalizeTodayContext({ data: {
  state: 'READY_IN', canPunch: true, allowedPunchType: 'IN', punchModeSnapshot: 'SHIFT_BOUNDARY',
  schedule: { scheduleId: '88' }
} })
assert.deepStrictEqual(resolvePunchGate(ready), { ready: true, reason: '', scheduleId: '88', punchType: 'IN' })

const splitReady = normalizeTodayContext({ data: {
  state: 'READY_IN', canPunch: true, punchModeSnapshot: 'PER_WORK_SEGMENT',
  schedule: { scheduleId: '99', punchModeSnapshot: 'PER_WORK_SEGMENT' },
  nextPunchSlot: { punchSlotKey: 'SEGMENT-1-IN', punchType: 'IN', segmentOrder: 1, segmentLabel: '上午', opensAt: '2026-08-21T07:45:00' },
  punchSlots: [
    { punchSlotKey: 'SEGMENT-1-IN', punchType: 'IN', segmentOrder: 1, segmentLabel: '上午' },
    { punchSlotKey: 'SEGMENT-1-OUT', punchType: 'OUT', segmentOrder: 1, segmentLabel: '上午' },
    { punchSlotKey: 'SEGMENT-2-IN', punchType: 'IN', segmentOrder: 2, segmentLabel: '下午' },
    { punchSlotKey: 'SEGMENT-2-OUT', punchType: 'OUT', segmentOrder: 2, segmentLabel: '下午' }
  ]
} })
assert.deepStrictEqual(resolvePunchGate(splitReady), {
  ready: true,
  reason: '',
  scheduleId: '99',
  punchType: 'IN',
  punchSlotKey: 'SEGMENT-1-IN',
  segmentOrder: 1,
  segmentLabel: '上午',
  opensAt: '2026-08-21T07:45:00',
  closesAt: ''
})
assert.strictEqual(punchSlotLabel(splitReady.nextPunchSlot), '上午上班卡')

const middayBreak = normalizeTodayContext({ data: {
  state: 'BETWEEN_SEGMENTS', canPunch: false, lockReason: 'BETWEEN_SEGMENTS', punchModeSnapshot: 'PER_WORK_SEGMENT',
  schedule: { scheduleId: '99' },
  nextPunchSlot: { punchSlotKey: 'SEGMENT-2-IN', punchType: 'IN', segmentOrder: 2, segmentLabel: '下午', opensAt: '2026-08-21T14:00:00' }
} })
assert.strictEqual(restMessage(middayBreak), '休息中，14:00继续上班')
assert.strictEqual(resolvePunchGate(middayBreak).reason, '休息中，14:00继续上班')

const invalidSplit = normalizeTodayContext({ data: {
  state: 'READY_IN', canPunch: true, allowedPunchType: 'IN', punchModeSnapshot: 'PER_WORK_SEGMENT',
  schedule: { scheduleId: '100' }
} })
assert.match(resolvePunchGate(invalidSplit).reason, /分段班次.*打卡时段/, 'segmented punch must fail closed when the server slot is absent')
assert.strictEqual(validateLocation({ latitude: 31, longitude: 121, accuracy: 30 }).ok, true)
assert.strictEqual(validateLocation({ latitude: 31, longitude: 121, accuracy: 0 }).ok, false, 'invalid location must fail before camera')
assert.ok(!punchPolicy.includes('haversineMeters') && !punchPolicy.includes('distanceMeters') && !punchPolicy.includes('radiusMeters'), 'frontend punch policy must not calculate a site fence')
assert.strictEqual(validatePhoto({ type: 'image/jpeg', size: 1024 }).ok, true)
assert.strictEqual(validatePhoto({ type: 'text/plain', size: 1024 }).ok, false)
assert.strictEqual(validatePhoto({ type: 'image/webp', size: 1024 }).ok, false, 'WebP must be rejected because the server accepts only JPEG/PNG')

assert.ok(desktopPage.includes('<shift-management') && desktopPage.includes('<weekly-schedule'), 'desktop attendance center must expose shift and weekly scheduling')
assert.ok(desktopPage.includes('<attendance-site-management') && desktopPage.includes('AttendanceSiteManagement'), 'desktop attendance center must expose scoped attendance site configuration')
assert.ok(siteManagement.includes('listAttendanceSites') && siteManagement.includes('createAttendanceSite') && siteManagement.includes('updateAttendanceSite'), 'site management must support scoped list/create/update operations')
assert.ok(siteManagement.includes('changeAttendanceSiteStatus') && siteManagement.includes('deleteAttendanceSite'), 'site management must support versioned enable/disable and delete operations')
assert.ok(siteManagement.includes("v-hasPermi=\"['oa:attendance:site:add']\"") && siteManagement.includes("v-hasPermi=\"['oa:attendance:site:remove']\""), 'site management mutations must remain permission-gated')
assert.ok(desktopPage.includes('<day-result-management'), 'desktop attendance center must replace the day-result placeholder with the real manager page')
assert.ok(desktopPage.includes('<leave-management') && desktopPage.includes('<correction-management'), 'desktop attendance center must expose real leave and correction lists')
assert.ok(desktopLeave.includes('listShopAttendanceLeaves') && desktopCorrection.includes('listShopAttendanceCorrections'), 'desktop request lists must read the V2 scoped APIs')
assert.ok(!desktopPage.includes('handleCheckIn') && !desktopPage.includes('handleCheckOut'), 'desktop center must not retain WEB punch buttons')
assert.ok(shiftManagement.includes("const CONTINUOUS_PUNCH_MODE = 'SHIFT_BOUNDARY'") && shiftManagement.includes("const SEGMENT_PUNCH_MODE = 'PER_WORK_SEGMENT'"), 'shift management must expose compatible boundary and per-work-segment punch modes')
assert.ok(!shiftManagement.includes('this.form.punchMode = SEGMENT_PUNCH_MODE'), 'adding a work segment must not auto-select PER_WORK_SEGMENT')
assert.ok(shiftManagement.includes('derivedSegmentPunchWindows') && shiftManagement.includes('derivedPunchWindowPreview'), 'PER_WORK_SEGMENT must show midpoint-clamped effective windows before save')
assert.ok(
  /v-if="form.punchMode === segmentPunchMode"[\s\S]*derived-window-panel/.test(shiftManagement),
  'per-segment effective windows must be gated on PER_WORK_SEGMENT and hidden for SHIFT_BOUNDARY'
)
assert.ok(shiftManagement.includes('每工作段打卡（分段班）') && shiftManagement.includes('@click="addWorkSegment"') && shiftManagement.includes('@click="moveWorkSegment(index, -1)"'), 'shift management must add and reorder split work periods')
assert.ok(shiftManagement.includes("segmentType: 'WORK'") && shiftManagement.includes("segmentType: 'BREAK'") && shiftManagement.includes('startMinuteOffset') && shiftManagement.includes('endMinuteOffset'), 'shift payload must generate ordered minute-offset WORK and automatic BREAK segments')
assert.ok(shiftManagement.includes('standardMinutes') && shiftManagement.includes('standardMinutes += end - start') && shiftManagement.includes('delete payload.workSegments'), 'shift payload must derive standard minutes and omit editor-only state')
assert.ok(shiftManagement.includes('workPeriodsText(scope.row)') && shiftManagement.includes(".join(' / ')"), 'shift list must render every work period')
assert.ok(weeklySchedule.includes('businessDate: day.date') && weeklySchedule.includes('shopId: this.shopContext.deptId'), 'weekly scheduling must use final shopId/businessDate contract')
assert.ok(weeklySchedule.includes('shiftPeriodsText(row)') && weeklySchedule.includes('cellShiftPeriods') && weeklySchedule.includes('cellPunchModeText'), 'weekly scheduling must show all work periods and punch mode in options and cells')
assert.ok(weeklySchedule.includes('siteId: value.siteId') && weeklySchedule.includes('listAttendanceSites({ shopId, status: \'ENABLED\' })'), 'weekly scheduling must read enabled sites and submit the selected siteId')
assert.ok(weeklySchedule.includes('未选择考勤地点') && weeklySchedule.includes('必选考勤地点'), 'weekly scheduling must fail closed until every draft has a selected attendance site')
assert.ok(weeklySchedule.includes('listAttendanceEmployeeOptions({ shopId,') && weeklySchedule.includes('keyword: this.employeeKeyword'), 'weekly scheduling must use the searchable shop-scoped attendance employee option endpoint')
assert.ok(!weeklySchedule.includes('@/api/system/user'), 'attendance scheduling must not depend on system user-management permissions')
assert.ok(weeklySchedule.includes("String(row.status || 'DRAFT').toUpperCase() === 'DRAFT'"), 'publishing must exclude schedules that are already immutable/published')
assert.ok(weeklySchedule.includes('scheduleId: existing.scheduleId') && weeklySchedule.includes('rowVersion: existing.rowVersion') && weeklySchedule.includes('deleteAttendanceSchedule(deletion.scheduleId, deletion.rowVersion)'), 'clearing a draft cell must persist through the captured draft-only ID and version delete contract')
assert.ok(dayResultPage.includes('listAttendanceDayResults(params)') && dayResultPage.includes('getAttendanceDayResultPreflight'), 'day-result management must query real scoped results and preflight')
assert.ok(dayResultPage.includes('this.refreshPreflight().then') && dayResultPage.includes('settleAttendanceDayResults({'), 'day settlement must refresh preflight before invoking the settlement command')
assert.ok(dayResultPage.includes("v-hasPermi=\"['oa:attendance:day:settle']\"") && dayResultPage.includes('recalculate: Boolean(recalculate)'), 'settlement and explicit recalculation must remain permission-gated and distinct')
assert.ok(dayResultPage.includes(".then(() => this.$modal.confirm("), 'recalculating settled results must require a second explicit confirmation')
;['scheduledMinutes', 'workedMinutes', 'paidLeaveMinutes', 'unpaidLeaveMinutes', 'absenceMinutes', 'lateMinutes', 'earlyLeaveMinutes', 'issueCodes']
  .forEach(field => assert.ok(dayResultPage.includes(field), `day-result management must expose ${field}`))
;['LEAVE_REQUEST_REVIEW_REQUIRED', 'CORRECTION_REQUEST_REVIEW_REQUIRED', 'LEAVE_DECISION_CHANGED', 'CORRECTION_DECISION_CHANGED', 'APPROVED_LEAVE_CHANGED', 'APPROVED_CORRECTION_CHANGED']
  .forEach(code => assert.ok(dayResultPage.includes(code), `day-result management must localize ${code}`))
assert.ok(dayResultPage.includes('salaryMonth') && dayResultPage.includes('expectedShopId') && dayResultPage.includes('exceptionOnly'), 'salary exception deep links must focus month, shop and exception-only state')

const { resolveTodoRoute } = require('../src/utils/todoRouteResolver')
const context = { deptId: 9, deptName: '一号店', deptType: 'STORE' }
const availableRouteSet = new Set(['/oa/attendance-v2', '/mobile/attendance'])
;[
  ['OA_ATTENDANCE_LEAVE_APPROVAL', 'leave'],
  ['OA_ATTENDANCE_CORRECTION_APPROVAL', 'correction']
].forEach(([type, tab]) => {
  const item = {
    type,
    businessId: '1001',
    contextDeptId: 9,
    contextDeptName: '一号店',
    contextDeptType: 'STORE',
    routeParams: { businessId: '1001' }
  }
  const desktop = resolveTodoRoute(item, { platform: 'desktop', permissions: [], availableRouteSet, currentContext: context })
  const mobile = resolveTodoRoute(item, { platform: 'mobile', permissions: [], availableRouteSet, currentContext: context })
  assert.strictEqual(desktop.ok, true)
  assert.strictEqual(desktop.location.path, '/oa/attendance-v2')
  assert.strictEqual(desktop.location.query.tab, tab)
  assert.strictEqual(desktop.location.query.businessId, '1001')
  assert.strictEqual(mobile.ok, true)
  assert.strictEqual(mobile.location.path, '/mobile/attendance')
  assert.strictEqual(mobile.location.query.tab, tab)
  assert.strictEqual(mobile.location.query.businessId, '1001')
})



// A stalled upload/evidence connection must eventually hand control back to
// the pending-result/retry UI. Exercise the exported API configuration.
{
  const vm = require('vm')
  const apiRuntime = {
    request: config => config,
    FormData: class { append() {} }
  }
  vm.runInNewContext(api.replace(/import[^\n]+\n/, '').replace(/export function/g, 'function').replace(/export\s*\{[^}]+\}/g, ''), apiRuntime)
  const upload = apiRuntime.submitAttendancePunch({})
  const evidence = apiRuntime.getPunchEvidenceContent(1)
  assert.ok(upload.timeout > 0 && upload.timeout < 180000, 'upload must time out before a new challenge window is needed')
  assert.ok(evidence.timeout > 0 && evidence.timeout < 180000, 'private evidence must not hold page loading forever')
}

console.log('attendance V2 frontend contract tests passed')
