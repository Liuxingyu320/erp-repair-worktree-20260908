function newDeletionBatchId(cryptoApi = globalThis.crypto) {
  if (!cryptoApi || !cryptoApi.getRandomValues) throw Error('无法建立可靠的删除编号')
  const bytes = new Uint8Array(16); cryptoApi.getRandomValues(bytes)
  return 'delete_' + Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
}
function normalizeDeletionReceipt(batchId, data) {
  if (!data || data.batchId !== batchId || !['COMPLETED', 'PENDING', 'RETRYING', 'NOT_OBSERVED', 'REJECTED'].includes(data.status)) {
    return { batchId, status: 'UNKNOWN', items: [] }
  }
  return { batchId, status: data.status, items: Array.isArray(data.items) ? data.items : [] }
}
function deletionLabel(receipt) {
  if (!receipt) return ''
  return { COMPLETED: '任务定义已删除，当前调度器已同步', PENDING: '任务定义已删除，正在同步调度器',
    RETRYING: '任务定义已删除，调度器暂不可用，系统将继续重试同步',
    NOT_OBSERVED: '尚未查到删除回执，原请求可能仍在处理，请继续查询',
    REJECTED: '删除请求未通过，任务没有被本次请求删除，请刷新后重新选择',
    UNKNOWN: '删除结果待核对，请查询原删除结果' }[receipt.status] || '删除结果待核对'
}
function receiptStorage() { try {return typeof sessionStorage === 'undefined' ? null : sessionStorage} catch (_) {return null} }
function saveDeletionReceipt(actor, receipt, storage = receiptStorage()) {
  if (!actor || !storage) return
  try {storage.setItem('job.deletion.'+actor,JSON.stringify(receipt))} catch (_) { /* pending state remains visible in this page */ }
}
function loadDeletionReceipt(actor, storage = receiptStorage()) {
  if (!actor || !storage) return null
  try {const value=JSON.parse(storage.getItem('job.deletion.'+actor));return value && /^delete_[a-f0-9]{32}$/.test(value.batchId) ? normalizeDeletionReceipt(value.batchId,value) : null} catch (_) {return null}
}
module.exports = {newDeletionBatchId, normalizeDeletionReceipt, deletionLabel, saveDeletionReceipt, loadDeletionReceipt}
