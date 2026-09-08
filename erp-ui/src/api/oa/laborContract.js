import request from '@/utils/request'

export function listLaborContract(params) {
  return request({
    url: '/oa/laborContract/list',
    method: 'get',
    params,
    silentError: true
  })
}

export function getLaborContract(contractId) {
  return request({
    url: '/oa/laborContract/' + contractId,
    method: 'get',
    silentError: true
  })
}

export function saveLaborContract(data) {
  return request({
    url: '/oa/laborContract/save',
    method: 'post',
    data
  })
}

export function sendLaborContract(contractId) {
  return request({
    url: '/oa/laborContract/send',
    method: 'post',
    data: { contractId }
  })
}

export function voidLaborContract(contractId) {
  return request({
    url: '/oa/laborContract/' + contractId + '/void',
    method: 'post'
  })
}

export function previewLaborContract(contractId) {
  return request({
    url: '/oa/laborContract/' + contractId + '/preview',
    method: 'get',
    silentError: true
  })
}

export function downloadLaborContractFile(contractId, kind) {
  return request({
    url: '/oa/laborContract/download/' + contractId + '/' + kind,
    method: 'get',
    responseType: 'blob'
  })
}

export function verifyLaborContractHash(data) {
  return request({
    url: '/oa/laborContract/verify',
    method: 'post',
    data,
    silentError: true
  })
}

export function listLaborContractTemplate(params) {
  return request({
    url: '/oa/laborContract/template/list',
    method: 'get',
    params,
    silentError: true
  })
}

export function saveLaborContractTemplate(data) {
  return request({
    url: '/oa/laborContract/template',
    method: 'post',
    data
  })
}

export function getLaborContractSeal() {
  return request({
    url: '/oa/laborContract/seal',
    method: 'get',
    silentError: true
  })
}

export function saveLaborContractSeal(data) {
  return request({
    url: '/oa/laborContract/seal',
    method: 'post',
    data
  })
}

export function listMyLaborContracts(params) {
  return request({
    url: '/oa/laborContract/mobile/my',
    method: 'get',
    params
  })
}

export function getMyLaborContract(contractId) {
  return request({
    url: '/oa/laborContract/mobile/' + contractId,
    method: 'get'
  })
}

export function signMyLaborContract(contractId, data) {
  return request({
    url: '/oa/laborContract/mobile/' + contractId + '/sign',
    method: 'post',
    data
  })
}
