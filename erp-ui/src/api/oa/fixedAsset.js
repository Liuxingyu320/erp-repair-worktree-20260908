import request from '@/utils/request'

export function listFixedAssetConfigs(params) {
  return request({
    url: '/oa/fixedAsset/config/list',
    method: 'get',
    params
  })
}

export function getFixedAssetConfig(configId) {
  return request({
    url: '/oa/fixedAsset/config/' + configId,
    method: 'get'
  })
}

export function saveFixedAssetConfig(data) {
  return request({
    url: '/oa/fixedAsset/config/save',
    method: 'post',
    data
  })
}

export function deleteFixedAssetConfig(configId) {
  return request({
    url: '/oa/fixedAsset/config/' + configId,
    method: 'delete'
  })
}

export function getFixedAssetQuota(params) {
  return request({
    url: '/oa/fixedAsset/config/quota',
    method: 'get',
    params
  })
}

export function listFixedAssetRepairs(params) {
  return request({
    url: '/oa/fixedAsset/repair/list',
    method: 'get',
    params
  })
}

export function getFixedAssetRepair(repairId) {
  return request({
    url: '/oa/fixedAsset/repair/' + repairId,
    method: 'get'
  })
}

export function submitFixedAssetRepair(data) {
  return request({
    url: '/oa/fixedAsset/repair/submit',
    method: 'post',
    data
  })
}

export function precheckFixedAssetRepair(data) {
  return request({
    url: '/oa/fixedAsset/repair/precheck',
    method: 'post',
    data
  })
}

export function submitFixedAssetRepairBatch(data) {
  return request({
    url: '/oa/fixedAsset/repair/submit/batch',
    method: 'post',
    data
  })
}
