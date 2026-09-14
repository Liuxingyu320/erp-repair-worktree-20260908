// Only public receipt metadata is persisted. Files, tokens and storage keys never enter this store.
function newUploadOperationId(cryptoApi = globalThis.crypto) {
  if (!cryptoApi || typeof cryptoApi.getRandomValues !== 'function') throw new Error('当前浏览器无法建立可靠的上传编号')
  const bytes = new Uint8Array(16)
  cryptoApi.getRandomValues(bytes)
  return 'upload_' + Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
}

function uploadActor(vm) {
  const user = vm && vm.$store && vm.$store.state && vm.$store.state.user || {}
  return String(user.id || '')
}

function uploadContext(vm) {
  const user = vm && vm.$store && vm.$store.state && vm.$store.state.user || {}
  return uploadActor(vm) + ':' + String(user.sessionRevision || 0)
}

function pendingUpload(item, message) {
  return { ...item, freshUpload: false, resultWasUnknown: true, status: 'pending', error: message || '上传结果待核对，请查询原上传结果', controller: null }
}

// Only the first in-memory attempt can use this request-local marker. Restored/replayed/aborted
// commands must continue reading their durable receipt; NOT_OBSERVED never authorizes a retry.
function applyPreClaimRejection(item, error, firstAttempt) {
  const response = error && error.response
  const payload = response && response.data
  const marker = payload && payload.uploadAttempt
  if (firstAttempt !== true || !item || item.resultWasUnknown || item.status !== 'uploading' ||
    !response || !Number.isFinite(Number(response.status)) || Number(response.status) < 400 || Number(response.status) >= 600 ||
    !marker || marker.operationId !== item.operationId || marker.state !== 'REJECTED_BEFORE_CLAIM') return null
  return { ...item, status: 'rejected', freshUpload: false, controller: null,
    error: (payload.msg || '上传条件不符合要求') + '；本次上传未被受理，可重新选择文件' }
}

function applyUploadReceipt(item, receipt) {
  if (!receipt || receipt.operationId !== item.operationId) return pendingUpload(item)
  if (receipt.status === 'SUCCEEDED' && receipt.nodeId != null) {
    return { ...item, status: 'done', progress: 100, nodeId: String(receipt.nodeId), error: '', controller: null }
  }
  if (receipt.status === 'FAILED_SAFE') {
    return { ...item, status: 'failed', error: item.file ? '上传未成功，可安全重试' : '上传未成功，请重新选择同一个文件继续', controller: null }
  }
  return pendingUpload(item, receipt.status === 'REVIEW_REQUIRED'
    ? '文件写入结果仍需核对，请勿重新上传；请联系管理员检查此上传编号：' + item.operationId
    : receipt.status === 'NOT_OBSERVED'
      ? '暂未查到上传回执，原请求可能仍在处理，请稍后再次查询'
      : '服务器仍在处理或核对，请稍后再次查询')
}

function storageForUploads() {
  try { return typeof localStorage === 'undefined' ? null : localStorage } catch (_) { return null }
}

function persistUploadReceipts(actor, surface, items, storage = storageForUploads()) {
  if (!actor || !storage) return false
  const records = (items || []).filter(item => item.operationId && !['queued', 'canceled'].includes(item.status))
    .map(item => ({
      id: item.id || item.operationId, operationId: item.operationId,
      name: item.name || item.displayName || item.file && item.file.name || '',
      size: Number(item.size == null ? item.file && item.file.size : item.size) || 0,
      targetSpaceId: String(item.targetSpaceId), targetParentId: String(item.targetParentId || 0),
      targetPath: item.targetPath || '', nodeId: item.nodeId == null ? null : String(item.nodeId),
      status: ['done', 'failed', 'rejected'].includes(item.status) ? item.status : 'pending'
    }))
  try { storage.setItem('drive.upload.receipts.' + surface + '.' + actor, JSON.stringify(records)); return true } catch (_) { return false }
}

function restoreUploadReceipts(actor, surface, storage = storageForUploads()) {
  if (!actor || !storage) return []
  try {
    const records = JSON.parse(storage.getItem('drive.upload.receipts.' + surface + '.' + actor) || '[]')
    if (!Array.isArray(records)) return []
    return records.filter(row => row && /^[A-Za-z0-9_-]{20,64}$/.test(row.operationId)
      && /^\d+$/.test(row.targetSpaceId) && /^\d+$/.test(row.targetParentId))
      .map(row => ({ ...row, freshUpload: false, resultWasUnknown: true, file: null, controller: null, progress: row.status === 'done' ? 100 : 0,
        displayName: row.name, error: row.status === 'done' ? '' : row.status === 'rejected'
          ? '该次上传未被受理，可重新选择文件' : row.status === 'failed'
          ? '上传未成功，请重新选择同一个文件继续' : '已恢复上传记录，请查询原上传结果' }))
  } catch (_) { return [] }
}

function matchesUploadFile(item, file, spaceId, parentId) {
  return item && file && item.name === file.name && Number(item.size) === Number(file.size)
    && String(item.targetSpaceId) === String(spaceId) && String(item.targetParentId || 0) === String(parentId || 0)
}

module.exports = { newUploadOperationId, uploadActor, uploadContext, pendingUpload, applyPreClaimRejection, applyUploadReceipt,
  persistUploadReceipts, restoreUploadReceipts, matchesUploadFile }
