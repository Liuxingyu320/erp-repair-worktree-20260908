const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB']
const PREVIEW_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'txt', 'csv'])
const VIEWS = new Set(['files', 'recent', 'trash'])
const SORT_FIELDS = new Set(['name', 'size', 'updated'])
const SORT_DIRECTIONS = new Set(['asc', 'desc'])

const DRIVE_ERROR_MESSAGES = {
  DRIVE_SESSION_EXPIRED: '登录状态已失效，请重新登录',
  DRIVE_DISABLED: '云盘功能尚未开启',
  DRIVE_ACCESS_DENIED: '无权执行此操作',
  DRIVE_SPACE_NOT_FOUND: '云盘空间不存在',
  DRIVE_NODE_NOT_FOUND: '文件或文件夹不存在',
  DRIVE_NAME_CONFLICT: '同一位置已存在同名文件',
  DRIVE_INVALID_MOVE: '无法移动到该位置',
  DRIVE_QUOTA_EXCEEDED: '云盘空间不足',
  DRIVE_CAPACITY_EXCEEDED: '全局云盘容量池不足',
  DRIVE_ORG_BUDGET_EXCEEDED: '已超出上级组织树预算',
  DRIVE_POLICY_INVALID: '额度配置参数无效',
  DRIVE_POLICY_NOT_FOUND: '额度策略不存在',
  DRIVE_ORG_CONFIG_NOT_FOUND: '组织盘配置不存在',
  DRIVE_IMPACT_STALE: '数据已变化，请重新预览影响',
  DRIVE_FILE_TOO_LARGE: '文件大小超过限制',
  DRIVE_FILE_TYPE_REJECTED: '不支持该文件类型',
  DRIVE_PREVIEW_UNSUPPORTED: '此文件暂不支持预览，请下载查看',
  DRIVE_CONCURRENT_MODIFICATION: '文件已被其他操作修改，请刷新后重试',
  DRIVE_STORAGE_UNAVAILABLE: '文件服务暂不可用，请稍后重试',
  DRIVE_STORAGE_OBJECT_MISSING: '文件内容不存在'
}

let uploadSequence = 0

function formatBytes(bytes) {
  let value = Number(bytes)
  if (!Number.isFinite(value) || value <= 0) return '0 B'

  let unitIndex = 0
  while (value >= 1024 && unitIndex < BYTE_UNITS.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  const rounded = Math.round(value * 100) / 100
  return `${rounded} ${BYTE_UNITS[unitIndex]}`
}

function formatDriveDateTime(value, options) {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return '—'
  const config = options && typeof options === 'object' ? options : {}
  const parts = new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    ...(config.timeZone ? { timeZone: config.timeZone } : {})
  }).formatToParts(date)
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}`
}

function positiveInteger(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function nonNegativeInteger(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0
}

function normalizeRouteState(query) {
  const source = query && typeof query === 'object' ? query : {}
  return {
    spaceId: positiveInteger(source.space),
    parentId: nonNegativeInteger(source.parent),
    view: VIEWS.has(source.view) ? source.view : 'files',
    keyword: typeof source.keyword === 'string' ? source.keyword : ''
  }
}

function textValue(value) {
  return value == null ? '' : String(value)
}

function numericValue(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function updatedValue(node) {
  const parsed = Date.parse(node && (node.updateTime || node.createTime))
  return Number.isFinite(parsed) ? parsed : 0
}

function compareNodeValue(left, right, field) {
  if (field === 'size') {
    return numericValue(left && left.sizeBytes) - numericValue(right && right.sizeBytes)
  }
  if (field === 'updated') {
    return updatedValue(left) - updatedValue(right)
  }
  return textValue(left && left.nodeName).localeCompare(
    textValue(right && right.nodeName),
    'zh-CN',
    { numeric: true, sensitivity: 'base' }
  )
}

function sortNodes(nodes, sortField, sortDirection) {
  const field = SORT_FIELDS.has(sortField) ? sortField : 'updated'
  const direction = SORT_DIRECTIONS.has(sortDirection) ? sortDirection : 'desc'
  const multiplier = direction === 'asc' ? 1 : -1

  return (Array.isArray(nodes) ? nodes.slice() : []).sort((left, right) => {
    const leftFolder = left && left.nodeType === 'FOLDER'
    const rightFolder = right && right.nodeType === 'FOLDER'
    if (leftFolder !== rightFolder) return leftFolder ? -1 : 1

    const compared = compareNodeValue(left, right, field)
    if (compared !== 0) return compared * multiplier

    const nameCompared = compareNodeValue(left, right, 'name')
    if (nameCompared !== 0) return nameCompared
    return numericValue(left && left.nodeId) - numericValue(right && right.nodeId)
  })
}

function preserveRecentOrder(nodes) {
  return Array.isArray(nodes) ? nodes.slice() : []
}

function isDriveLoadCurrent(request, current) {
  if (!request || !current) return false
  return Number(request.sequence) === Number(current.sequence) &&
    Number(request.spaceId) === Number(current.spaceId) &&
    Number(request.parentId) === Number(current.parentId) &&
    textValue(request.view) === textValue(current.view) &&
    textValue(request.keyword) === textValue(current.keyword) &&
    textValue(request.sortField) === textValue(current.sortField) &&
    textValue(request.sortDirection) === textValue(current.sortDirection) &&
    Number(request.pageNum) === Number(current.pageNum) &&
    Number(request.pageSize) === Number(current.pageSize)
}

function isMoveFolderLoadCurrent(request, current) {
  if (!request || !current) return false
  return Number(request.sequence) === Number(current.sequence) &&
    request.visible === true && current.visible === true &&
    Number(request.nodeId || 0) === Number(current.nodeId || 0) &&
    Number(request.spaceId) === Number(current.spaceId) &&
    Number(request.parentId) === Number(current.parentId) &&
    Number(request.pageNum) === Number(current.pageNum) &&
    Number(request.pageSize) === Number(current.pageSize)
}

function createUploadItem(file, spaceId, parentId, logicalPath) {
  uploadSequence += 1
  return {
    id: `${Date.now()}-${uploadSequence}`,
    file,
    name: file && file.name ? file.name : '',
    size: file && Number.isFinite(Number(file.size)) ? Number(file.size) : 0,
    targetSpaceId: spaceId,
    targetParentId: parentId == null ? 0 : parentId,
    targetPath: logicalPath || '',
    progress: 0,
    status: 'queued',
    error: '',
    controller: null
  }
}

function updateUploadProgress(item, progress) {
  const numeric = Number(progress)
  const next = Number.isFinite(numeric) ? Math.round(numeric) : 0
  return { ...item, progress: Math.max(0, Math.min(100, next)) }
}

function markUploadFailed(item, error) {
  return { ...item, status: 'failed', error: error || '上传失败', controller: null }
}

function markUploadCanceled(item) {
  return { ...item, status: 'canceled', error: '', controller: null }
}

function markUploadDone(item) {
  return { ...item, progress: 100, status: 'done', error: '', controller: null }
}

function selectUploadStartCandidates(items, maxConcurrent) {
  const queue = Array.isArray(items) ? items : []
  const limit = Number.isInteger(Number(maxConcurrent)) && Number(maxConcurrent) > 0
    ? Number(maxConcurrent)
    : 1
  const running = queue.filter(item => item && item.status === 'uploading').length
  const slots = Math.max(0, limit - running)
  return queue.filter(item => item && item.status === 'queued').slice(0, slots)
}

function releaseDriveObjectUrl(objectUrl, urlApi) {
  if (objectUrl && urlApi && typeof urlApi.revokeObjectURL === 'function') {
    urlApi.revokeObjectURL(objectUrl)
  }
  return ''
}

function clearDriveTimer(timer, clearTimer) {
  if (timer !== null && timer !== undefined && typeof clearTimer === 'function') {
    clearTimer(timer)
  }
  return null
}

function isPreviewable(node) {
  if (!node || !node.canPreview) return false
  const extension = textValue(node.extension).replace(/^\./, '').toLowerCase()
  return PREVIEW_EXTENSIONS.has(extension)
}

function driveErrorMessage(code, serverMessage) {
  if (code && DRIVE_ERROR_MESSAGES[code]) return DRIVE_ERROR_MESSAGES[code]
  if (typeof serverMessage === 'string' && serverMessage.trim()) return serverMessage.trim()
  return '操作失败，请稍后重试'
}

async function parseDriveBlobError(error) {
  const data = error && error.response && error.response.data
  const status = Number(error && error.response && error.response.status) || 0
  const isBlob = typeof Blob !== 'undefined' && data instanceof Blob
  if (isBlob && /json/i.test(data.type || '')) {
    try {
      const payload = JSON.parse(await data.text())
      return {
        code: normalizeDriveErrorCode(payload.businessCode || payload.code, status),
        message: payload.msg || payload.message || ''
      }
    } catch (_) {
      return { code: '', message: '' }
    }
  }
  if (data && typeof data === 'object' && !isBlob) {
    return {
      code: normalizeDriveErrorCode(data.businessCode || data.code, status),
      message: data.msg || data.message || ''
    }
  }
  return {
    code: normalizeDriveErrorCode(error && (error.businessCode || error.code), status),
    message: error && error.message
  }
}

function normalizeDriveErrorCode(code, status) {
  const value = code == null ? '' : String(code)
  if (value.startsWith('DRIVE_')) return value
  if (Number(status) === 401 || Number(value) === 401) return 'DRIVE_SESSION_EXPIRED'
  if (Number(status) === 403 || Number(value) === 403) return 'DRIVE_ACCESS_DENIED'
  if (Number(status) >= 500 || Number(value) >= 500) return 'DRIVE_STORAGE_UNAVAILABLE'
  return value
}

function isDriveRequestCanceled(error) {
  return Boolean(error && (
    error.code === 'ERR_CANCELED' ||
    error.name === 'CanceledError' ||
    error.name === 'AbortError' ||
    error.__CANCEL__ === true
  ))
}

module.exports = {
  formatBytes,
  formatDriveDateTime,
  isDriveLoadCurrent,
  isMoveFolderLoadCurrent,
  normalizeRouteState,
  preserveRecentOrder,
  sortNodes,
  createUploadItem,
  clearDriveTimer,
  updateUploadProgress,
  markUploadCanceled,
  markUploadFailed,
  markUploadDone,
  releaseDriveObjectUrl,
  selectUploadStartCandidates,
  isPreviewable,
  driveErrorMessage,
  isDriveRequestCanceled,
  parseDriveBlobError
}
