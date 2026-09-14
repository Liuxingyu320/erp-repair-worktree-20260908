import request from '@/utils/request'
import store from '@/store'
import { getSelectedInventoryDeptId } from '@/utils/shopContext'
import { MessageBox } from '@/plugins/element-services'
const { createPurchaseQualityRecovery } = require('@/utils/purchaseQualityRecovery')

const qualityRecovery = createPurchaseQualityRecovery({
  context: () => ({ actor: store.getters.id, dept: getSelectedInventoryDeptId() }),
  storage: () => typeof localStorage === 'undefined' ? null : localStorage,
  createId: () => {
    if (typeof crypto === 'undefined' || !crypto.getRandomValues) throw new Error('当前浏览器不支持安全质检操作标识')
    const bytes = crypto.getRandomValues(new Uint8Array(16))
    return 'qc-' + Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  },
  transport: (orderId, data, deptId, kind) => request({
    url: (kind === 'legacy' ? '/inventory/purchase/qc/' : '/inventory/purchase/qc/batch/') + orderId,
    method: 'post', data, inventoryDeptId: deptId
  }),
  confirmRecovery: () => MessageBox.confirm(
    '上次质检结果尚未确认，将按上次提交的内容核对结果。本次修改暂不提交。',
    '核对上次质检', { confirmButtonText: '核对结果', cancelButtonText: '返回', type: 'warning' }
  ).then(() => true, () => false)
})

// 保存草稿
export function savePurchase(data) {
  return request({ url: '/inventory/purchase/save', method: 'post', data: data })
}

// 提交采购单
export function submitPurchase(data) {
  return request({ url: '/inventory/purchase/submit', method: 'post', data: data })
}

// 采购单列表（全部）
export function listPurchase(query, config) {
  return request(Object.assign({ url: '/inventory/purchase/list', method: 'get', params: query }, config))
}

// 我的采购单
export function listMyPurchases(query) {
  return request({ url: '/inventory/purchase/my', method: 'get', params: query })
}

// 采购单详情
export function getPurchaseDetail(orderId) {
  return request({ url: '/inventory/purchase/' + orderId, method: 'get' })
}

// 收货入库
export function receivePurchase(orderId, data, requestId, scope) {
  const assertScope = () => {
    if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(requestId || '')) throw new Error('收货请求标识缺失，请重新打开收货窗口')
    if (!scope || String(store.getters.id) !== String(scope.actor) || String(getSelectedInventoryDeptId()) !== String(scope.dept))
      throw new Error('账号或业务组织已变化，请返回原收货操作核对')
  }
  try { assertScope() } catch (error) { return Promise.reject(error) }
  return request({
    url: '/inventory/purchase/receive/' + orderId, method: 'post', data,
    inventoryDeptId: scope.dept, silentError: true,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false, 'Content-Type': 'application/json' },
    // Recheck after Axios interceptors, immediately before serializing a write.
    transformRequest: [body => { assertScope(); return JSON.stringify(body) }]
  })
}

// 采购质检
export function qualityCheckPurchase(orderId, data) {
  return qualityRecovery.submit(orderId, data, 'legacy')
}

// 待检收货批次
export function listPendingReceiptBatches(orderId) {
  return qualityRecovery.recover(orderId).then(() => request({ url: '/inventory/purchase/' + orderId + '/receipt-batches/pending', method: 'get' }))
}

// 收货批次逐行质检
export function qualityCheckPurchaseBatch(orderId, data) {
  return qualityRecovery.submit(orderId, data)
}

// 取消采购单
export function cancelPurchase(orderId) {
  return request({ url: '/inventory/purchase/' + orderId, method: 'delete' })
}

// 删除草稿采购单
export function deleteDraftPurchase(orderId) {
  return request({ url: '/inventory/purchase/delete/' + orderId, method: 'delete' })
}

// 提交已保存草稿，仅要求采购提交权限。
export function submitPurchaseDraft(orderId) {
  return request({ url: '/inventory/purchase/submit/' + orderId, method: 'post' })
}

// Reads authorized by the task being performed; general query permission stays unchanged.
export function getPurchaseDraft(orderId, config) {
  return request(Object.assign({ url: '/inventory/purchase/draft/' + orderId, method: 'get' }, config && config.silentError === true ? { silentError: true } : {}))
}
export function getPurchaseReceiveContext(orderId, config) {
  return request(Object.assign({ url: '/inventory/purchase/receive-context/' + orderId, method: 'get' }, config && config.silentError === true ? { silentError: true } : {}))
}
export function listPurchaseSuppliers(query, config) {
  return request(Object.assign({ url: '/inventory/purchase/catalog/suppliers', method: 'get', params: query }, config && config.silentError === true ? { silentError: true } : {}))
}
export function listPurchaseProducts(query, config) {
  return request(Object.assign({ url: '/inventory/purchase/catalog/products', method: 'get', params: query }, config && config.silentError === true ? { silentError: true } : {}))
}
export function listPurchaseOeItems(query, config) {
  return request(Object.assign({ url: '/inventory/purchase/catalog/oe', method: 'get', params: query }, config && config.silentError === true ? { silentError: true } : {}))
}
export function listPurchaseGifts(query, config) {
  return request(Object.assign({ url: '/inventory/purchase/catalog/gifts', method: 'get', params: query }, config && config.silentError === true ? { silentError: true } : {}))
}

export function getPurchaseActionContext(id, config) {
  return request(Object.assign({ url: '/inventory/purchase/action-context/' + id, method: 'get' }, config && config.silentError === true ? { silentError: true } : {}))
}
