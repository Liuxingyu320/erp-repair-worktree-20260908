import request from '@/utils/request'

export function getSalary(salaryId) {
  return request({ url: '/oa/salary/' + salaryId, method: 'get' })
}

export function listMySalary(params) {
  return request({ url: '/oa/salary/my', method: 'get', params })
}

export function listAllSalary(params) {
  return request({ url: '/oa/salary/list', method: 'get', params })
}

export function calculateSalary(data) {
  return request({ url: '/oa/salary/calculate', method: 'post', data })
}

export function preflightSalaryAttendance(params) {
  return request({ url: '/oa/salary/attendance-preflight', method: 'get', params })
}

export function getSalaryConfig(params) {
  return request({ url: '/oa/salary/config', method: 'get', params })
}

export function saveSalaryConfig(data) {
  return request({ url: '/oa/salary/config', method: 'put', data })
}

export function exportSalary(params) {
  return request({ url: '/oa/salary/export', method: 'post', params, responseType: 'blob' })
}
