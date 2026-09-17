import request from '@/utils/request'

export function listFixedAssetConfigs(params, options) {
  return request({
    url: '/oa/fixedAsset/config/list',
    method: 'get', silentError: options && options.silentError === true,
    params
  })
}

export function listFixedAssetStores(params) {
  return request({ url: '/oa/fixedAsset/config/stores', method: 'get', params, silentError: true })
}
export function getFixedAssetStoreDetails(params) {
  return request({ url: '/oa/fixedAsset/config/store-details', method: 'get', params, silentError: true })
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

export function getFixedAssetQuota(params, options) {
  return request({
    url: '/oa/fixedAsset/config/quota',
    method: 'get', silentError: options && options.silentError === true,
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

// Full shop snapshot and durable atomic configuration command.
export function getFixedAssetConfigSnapshot(shopDeptId) {
  return request({ url: '/oa/fixedAsset/config/snapshot', method: 'get', params: { shopDeptId }, silentError: true })
}
export function saveFixedAssetConfigBatch(data) {
  return request({ url: '/oa/fixedAsset/config/batch-save', method: 'post', data, silentError: true })
}
export function getFixedAssetConfigBatchCommand(requestId, shopDeptId) {
  return request({ url: '/oa/fixedAsset/config/batch-commands/' + encodeURIComponent(requestId), method: 'get', params: { shopDeptId }, silentError: true })
}
