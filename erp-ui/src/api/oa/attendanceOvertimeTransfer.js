import request from '@/utils/request'

const base = '/oa/attendance-v2/leave/balance/overtime-transfers'
export function getOvertimeTransferContext(sourceDayResultId, leaveTypeId, options) {
  const silent = options && options.silentError === true ? { silentError: true } : {}
  return request({ url: `${base}/context/${sourceDayResultId}`, method: 'get', params: { leaveTypeId }, ...silent })
}
export function confirmOvertimeTransfer(data) {
  return request({ url: base, method: 'post', data })
}
export function reverseOvertimeTransfer(transferId, data) {
  return request({ url: `${base}/${transferId}/reverse`, method: 'post', data })
}
