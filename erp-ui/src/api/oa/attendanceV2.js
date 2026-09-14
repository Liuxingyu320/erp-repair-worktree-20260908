import request from '@/utils/request'

const BASE_URL = '/oa/attendance-v2'

export function listAttendanceShifts(params, options) {
  return request({ url: `${BASE_URL}/shifts`, method: 'get', params, silentError: options && options.silentError === true })
}

export function createAttendanceShift(data) {
  return request({ url: `${BASE_URL}/shifts`, method: 'post', data, silentError: true })
}

export function updateAttendanceShift(shiftId, data) {
  return request({ url: `${BASE_URL}/shifts/${shiftId}`, method: 'put', data, silentError: true })
}

export function changeAttendanceShiftStatus(shiftId, data) {
  return request({ url: `${BASE_URL}/shifts/${shiftId}/status`, method: 'put', data, silentError: true })
}

export function deleteAttendanceShift(shiftId, rowVersion) {
  return request({ url: `${BASE_URL}/shifts/${shiftId}`, method: 'delete', params: { rowVersion }, silentError: true })
}

export function listAttendanceSites(params) {
  return request({ url: `${BASE_URL}/sites`, method: 'get', params, silentError: true })
}

export function getAttendanceSite(siteId) {
  return request({ url: `${BASE_URL}/sites/${siteId}`, method: 'get', silentError: true })
}

export function createAttendanceSite(data) {
  return request({ url: `${BASE_URL}/sites`, method: 'post', data, silentError: true })
}

export function updateAttendanceSite(siteId, data) {
  return request({ url: `${BASE_URL}/sites/${siteId}`, method: 'put', data, silentError: true })
}

export function changeAttendanceSiteStatus(siteId, data) {
  return request({ url: `${BASE_URL}/sites/${siteId}/status`, method: 'put', data, silentError: true })
}

export function deleteAttendanceSite(siteId, rowVersion) {
  return request({ url: `${BASE_URL}/sites/${siteId}`, method: 'delete', params: { rowVersion }, silentError: true })
}

export function listAttendanceSchedules(params, options) {
  return request({ url: `${BASE_URL}/schedules`, method: 'get', params, silentError: options && options.silentError === true })
}

export function listAttendanceEmployeeOptions(params) {
  return request({ url: `${BASE_URL}/employee-options`, method: 'get', params, silentError: true })
}

export function saveAttendanceScheduleBatch(data) {
  return request({ url: `${BASE_URL}/schedules/batch`, method: 'post', data, silentError: true })
}

export function publishAttendanceSchedules(data) {
  return request({ url: `${BASE_URL}/schedules/publish`, method: 'post', data, silentError: true })
}

export function deleteAttendanceSchedule(scheduleId, rowVersion) {
  return request({
    url: `${BASE_URL}/schedules/${scheduleId}`,
    method: 'delete',
    params: { rowVersion },
    silentError: true
  })
}

export function getTodayAttendanceContext() {
  return request({ url: `${BASE_URL}/today`, method: 'get', silentError: true })
}

export function createPunchChallenge(data) {
  return request({ url: `${BASE_URL}/punch/challenge`, method: 'post', data, silentError: true })
}

export function submitAttendancePunch(payload, onUploadProgress) {
  const data = new FormData()
  data.append('challengeToken', payload.challengeToken)
  data.append('punchType', payload.punchType)
  if (payload.punchSlotKey !== undefined && payload.punchSlotKey !== null && String(payload.punchSlotKey).trim()) {
    data.append('punchSlotKey', String(payload.punchSlotKey))
  }
  data.append('latitude', String(payload.latitude))
  data.append('longitude', String(payload.longitude))
  data.append('accuracyMeters', String(payload.accuracyMeters))
  data.append('clientCoordinateSystem', String(payload.clientCoordinateSystem))
  data.append('clientCaptureTime', String(payload.clientCaptureTime))
  data.append('photo', payload.photo)
  ;['clientRequestId', 'deviceId', 'appVersion'].forEach(key => {
    if (payload[key] !== undefined && payload[key] !== null && String(payload[key]).trim()) {
      data.append(key, String(payload[key]))
    }
  })
  return request({
    url: `${BASE_URL}/punch`,
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data,
    timeout: 60000,
    silentError: true,
    onUploadProgress
  })
}

export function getAttendancePunchStatus(data) {
  return request({
    url: `${BASE_URL}/punch/status`,
    method: 'post',
    data,
    silentError: true
  })
}

export function getPunchEvidenceContent(evidenceId) {
  return request({
    url: `${BASE_URL}/evidence/${evidenceId}/content`,
    method: 'get',
    responseType: 'blob',
    timeout: 20000,
    silentError: true
  })
}

export function listMyAttendanceDayResults(params) {
  return request({ url: `${BASE_URL}/day-results/my`, method: 'get', params, silentError: true })
}

export function listAttendanceDayResults(params) {
  return request({ url: `${BASE_URL}/day-results`, method: 'get', params, silentError: true })
}

export function getAttendanceDayResultPreflight(params) {
  return request({ url: `${BASE_URL}/day-results/preflight`, method: 'get', params, silentError: true })
}

export function countUnfinalizedAttendanceDayResults(params) {
  return request({ url: `${BASE_URL}/day-results/unfinalized`, method: 'get', params, silentError: true })
}

export function settleAttendanceDayResults(data) {
  return request({ url: `${BASE_URL}/day-results/settle`, method: 'post', data, silentError: true })
}

export function getAttendanceTimeCreditContext(targetDayResultId) {
  return request({
    url: `${BASE_URL}/time-credits/context/${targetDayResultId}`,
    method: 'get',
    silentError: true
  })
}

export function applyAttendanceTimeCredit(data) {
  return request({
    url: `${BASE_URL}/time-credits/adjustments`,
    method: 'post',
    data,
    silentError: true
  })
}

export function reverseAttendanceTimeCredit(adjustmentId, data) {
  return request({
    url: `${BASE_URL}/time-credits/adjustments/${adjustmentId}/reverse`,
    method: 'post',
    data,
    silentError: true
  })
}

export function listAttendanceRemainingWorkIntervals(scheduleId) {
  return request({
    url: `${BASE_URL}/remaining-work/schedules/${scheduleId}/intervals`,
    method: 'get',
    silentError: true
  })
}

export function listAttendanceRemainingWorkHistory(scheduleId) {
  return request({
    url: `${BASE_URL}/remaining-work/schedules/${scheduleId}/history`,
    method: 'get',
    silentError: true
  })
}

export function confirmAttendanceRemainingWork(data) {
  return request({
    url: `${BASE_URL}/remaining-work/confirmations`,
    method: 'post',
    data,
    silentError: true
  })
}

export function uploadAttendanceRemainingWorkAttachment(confirmationId, file, onUploadProgress) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: `${BASE_URL}/remaining-work/confirmations/${confirmationId}/attachments`,
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data,
    timeout: 0,
    silentError: true,
    onUploadProgress
  })
}

export function getAttendanceRemainingWorkAttachmentContent(confirmationId, attachmentId) {
  return request({
    url: `${BASE_URL}/remaining-work/confirmations/${confirmationId}/attachments/${attachmentId}/content`,
    method: 'get',
    responseType: 'blob',
    timeout: 0,
    silentError: true
  })
}

export function listAttendanceLeaveTypes(params) {
  return request({ url: `${BASE_URL}/leave/types`, method: 'get', params, silentError: true })
}

export function createAttendanceLeaveType(data) {
  return request({ url: `${BASE_URL}/leave/types`, method: 'post', data, silentError: true })
}

export function updateAttendanceLeaveType(leaveTypeId, data) {
  return request({ url: `${BASE_URL}/leave/types/${leaveTypeId}`, method: 'put', data, silentError: true })
}

export function changeAttendanceLeaveTypeStatus(leaveTypeId, data) {
  return request({ url: `${BASE_URL}/leave/types/${leaveTypeId}/status`, method: 'put', data, silentError: true })
}

export function listMyAttendanceLeaves(params) {
  return request({ url: `${BASE_URL}/leave/my`, method: 'get', params, silentError: true })
}

export function listShopAttendanceLeaves(params) {
  return request({ url: `${BASE_URL}/leave/shop`, method: 'get', params, silentError: true })
}

export function getAttendanceLeave(leaveRequestId) {
  return request({ url: `${BASE_URL}/leave/${leaveRequestId}`, method: 'get', silentError: true })
}

export function getAttendanceLeaveByClientRequest(clientRequestId) {
  return request({
    url: `${BASE_URL}/leave/by-client-request/${encodeURIComponent(clientRequestId)}`,
    method: 'get',
    silentError: true
  })
}

export function createAttendanceLeaveDraft(data) {
  return request({ url: `${BASE_URL}/leave/drafts`, method: 'post', data, silentError: true })
}

export function updateAttendanceLeaveDraft(leaveRequestId, data) {
  return request({ url: `${BASE_URL}/leave/${leaveRequestId}/draft`, method: 'put', data, silentError: true })
}

export function previewAttendanceLeavePolicy(leaveRequestId, data) {
  return request({ url: `${BASE_URL}/leave/${leaveRequestId}/policy-preview`, method: 'post', data, silentError: true })
}

export function submitAttendanceLeave(leaveRequestId, rowVersion) {
  return request({
    url: `${BASE_URL}/leave/${leaveRequestId}/submit`,
    method: 'post',
    data: { rowVersion },
    silentError: true
  })
}

export function withdrawAttendanceLeave(leaveRequestId, data) {
  return request({ url: `${BASE_URL}/leave/${leaveRequestId}/withdraw`, method: 'post', data, silentError: true })
}

export function uploadAttendanceLeaveAttachment(leaveRequestId, rowVersion, file, onUploadProgress) {
  const data = new FormData()
  data.append('rowVersion', String(rowVersion))
  data.append('file', file)
  return request({
    url: `${BASE_URL}/leave/${leaveRequestId}/attachments`,
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data,
    timeout: 0,
    silentError: true,
    onUploadProgress
  })
}

export function deleteAttendanceLeaveAttachment(leaveRequestId, attachmentId, rowVersion) {
  return request({
    url: `${BASE_URL}/leave/${leaveRequestId}/attachments/${attachmentId}`,
    method: 'delete',
    data: { rowVersion },
    silentError: true
  })
}

export function getAttendanceLeaveAttachmentContent(leaveRequestId, attachmentId) {
  return request({
    url: `${BASE_URL}/leave/${leaveRequestId}/attachments/${attachmentId}/content`,
    method: 'get',
    responseType: 'blob',
    timeout: 0,
    silentError: true
  })
}

export function listAttendanceCorrectionEligibleSchedules(params) {
  return request({ url: `${BASE_URL}/corrections/eligible-schedules`, method: 'get', params, silentError: true })
}

export function listAttendanceCorrectionEligiblePunchEvents(scheduleId) {
  return request({ url: `${BASE_URL}/corrections/eligible-schedules/${scheduleId}/punch-events`, method: 'get', silentError: true })
}

export function listMyAttendanceCorrections(params) {
  return request({ url: `${BASE_URL}/corrections/my`, method: 'get', params, silentError: true })
}

export function listShopAttendanceCorrections(params) {
  return request({ url: `${BASE_URL}/corrections/shop`, method: 'get', params, silentError: true })
}

export function getAttendanceCorrection(correctionRequestId) {
  return request({ url: `${BASE_URL}/corrections/${correctionRequestId}`, method: 'get', silentError: true })
}

export function getAttendanceCorrectionByClientRequest(clientRequestId) {
  return request({ url: `${BASE_URL}/corrections/by-client-request/${encodeURIComponent(clientRequestId)}`, method: 'get', silentError: true })
}

export function createAttendanceCorrectionDraft(data) {
  return request({ url: `${BASE_URL}/corrections/drafts`, method: 'post', data, silentError: true })
}

export function updateAttendanceCorrectionDraft(correctionRequestId, data) {
  return request({ url: `${BASE_URL}/corrections/${correctionRequestId}/draft`, method: 'put', data, silentError: true })
}

export function submitAttendanceCorrection(correctionRequestId, rowVersion) {
  return request({
    url: `${BASE_URL}/corrections/${correctionRequestId}/submit`,
    method: 'post',
    data: { rowVersion },
    silentError: true
  })
}

export { BASE_URL as ATTENDANCE_V2_BASE_URL }
