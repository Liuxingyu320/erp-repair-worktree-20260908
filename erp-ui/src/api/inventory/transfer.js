import request from '@/utils/request'

function withConfig(base, config = {}) {
  return Object.assign({}, base, config)
}

// 调拨单列表
export function listTransfer(query) {
  return request({ url: '/inventory/transfer/list', method: 'get', params: query })
}

// 调拨处理中列表
export function listTransferProcessing(query) {
  return request({ url: '/inventory/transfer/processing', method: 'get', params: query })
}

export function getTransferOpsSummary() {
  return request({ url: '/inventory/transfer/ops-summary', method: 'get' })
}

// 调拨记录列表
export function listTransferRecords(query) {
  return request({ url: '/inventory/transfer/records', method: 'get', params: query })
}

// 调拨单详情
export function getTransferDetail(transferId, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/' + transferId, method: 'get' }, config))
}

// 调拨审批进度（含历史轮次）
export function getTransferApprovalTrack(transferId) {
  return request({ url: '/inventory/transfer/' + transferId + '/approval-track', method: 'get' })
}

// 保存草稿
export function saveTransfer(data) {
  return request({ url: '/inventory/transfer/save', method: 'post', data: data, silentError: true })
}

// 提交调拨单
export function submitTransfer(data) {
  return request({ url: '/inventory/transfer/submit', method: 'post', data: data, silentError: true })
}

// 审批调拨单
export function approveTransfer(data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/approve', method: 'post', data, silentError: true }, config))
}

// 发货
export function deliverTransfer(transferId, data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/deliver/' + transferId, method: 'post', data, silentError: true }, config))
}

// 异店调货由调出店逐行确认可调数量
export function confirmTransferSource(transferId, data) {
  return request({ url: '/inventory/transfer/source-confirm/' + transferId, method: 'post', data, silentError: true })
}

// 确认出库：兼容旧页面命名
export function outTransfer(transferId, data, config = {}) {
  return deliverTransfer(transferId, data, config)
}

// 确认收货
export function receiveTransfer(transferId, data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/receive/' + transferId, method: 'post', data, silentError: true }, config))
}

// 按发货批次确认收货
export function receiveShipment(shipmentId, data) {
  return request({ url: '/inventory/transfer/shipment/receive/' + shipmentId, method: 'post', data, silentError: true })
}

// 确认入库：兼容旧页面命名
export function inTransfer(transferId, data, config = {}) {
  return receiveTransfer(transferId, data, config)
}

// 取消调拨单
export function cancelTransfer(transferId) {
  return request({ url: '/inventory/transfer/' + transferId, method: 'delete', silentError: true })
}

// 申请人撤回统一审批；业务状态由审批回调异步更新
export function withdrawTransfer(transferId) {
  return request({ url: '/inventory/transfer/' + transferId + '/withdraw', method: 'post', silentError: true })
}

// 永久删除草稿调拨单
export function deleteTransferDraft(transferId) {
  return request({ url: '/inventory/transfer/delete/' + transferId, method: 'delete', silentError: true })
}

export function listTransferDiscrepancies(transferId) {
  return request({ url: '/inventory/transfer/' + transferId + '/discrepancies', method: 'get' })
}

export function getTransferDiscrepancy(discrepancyId) {
  return request({ url: '/inventory/transfer/discrepancies/' + discrepancyId, method: 'get' })
}

export function resolveTransferDiscrepancy(discrepancyId, data) {
  return request({ url: '/inventory/transfer/discrepancies/' + discrepancyId + '/resolve', method: 'post', data, silentError: true })
}
