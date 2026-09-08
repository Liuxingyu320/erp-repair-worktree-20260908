import request from '@/utils/request'

export function listLegalEntityOptions() {
  return request({
    url: '/system/legalEntity/options',
    method: 'get'
  })
}

export function listLegalEntities(params) {
  return request({
    url: '/system/legalEntity/list',
    method: 'get',
    params
  })
}

export function getLegalEntity(legalEntityId) {
  return request({
    url: '/system/legalEntity/' + legalEntityId,
    method: 'get'
  })
}

export function createLegalEntity(data) {
  return request({
    url: '/system/legalEntity',
    method: 'post',
    data
  })
}

export function updateLegalEntity(data) {
  return request({
    url: '/system/legalEntity',
    method: 'put',
    data
  })
}
