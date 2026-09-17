import request from '@/utils/request'

export function savePurchase(data) {
  return request({
    url: '/oa/purchase/save',
    method: 'post', silentError: true,
    data
  })
}

export function submitPurchase(data) {
  return request({
    url: '/oa/purchase/submit',
    method: 'post',
    data
  })
}

export function getPurchaseAvailability() {
  return request({
    url: '/oa/purchase/availability',
    method: 'get',
    silentError: true
  })
}

export function closePurchase(purchaseId) {
  return request({
    url: '/oa/purchase/' + purchaseId + '/close',
    method: 'post'
  })
}

export function withdrawPurchase(purchaseId, data) {
  return request({
    url: '/oa/purchase/' + purchaseId + '/withdraw',
    method: 'post',
    data,
    silentError: true
  })
}

export function listMyPurchases(params, config = {}) {
  return request({
    url: '/oa/purchase/my',
    method: 'get',
    params,
    silentError: config.silentError === true
  })
}

export function getPurchaseDetail(purchaseId, config = {}) {
  return request({
    url: '/oa/purchase/' + purchaseId,
    method: 'get',
    silentError: config.silentError === true
  })
}
