import request from '@/utils/request'

const base = '/oa/attendance-v2/leave/balance/options'
export function listBalanceTypeOptions() { return request({ url: `${base}/types`, method: 'get', silentError: true }) }
export function listBalanceCompanyOptions(ownerDeptId) { return request({ url: `${base}/companies`, method: 'get', params: { ownerDeptId }, silentError: true }) }
export function listBalanceEmployeeOptions(params) { return request({ url: `${base}/employees`, method: 'get', params, silentError: true }) }
export function listBalanceOvertimeSources(params) { return request({ url: `${base}/overtime-sources`, method: 'get', params, silentError: true }) }
