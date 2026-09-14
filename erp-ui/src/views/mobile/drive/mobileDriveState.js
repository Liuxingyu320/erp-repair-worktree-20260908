const { newUploadOperationId } = require('../../drive/uploadReceipt')
'use strict'

const {
  driveErrorMessage,
  formatBytes,
  isPreviewable
} = require('../../drive/driveState')

const CAPTURE_EXTENSIONS = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/heic': 'heic',
  'image/heif': 'heif'
}

const IMAGE_PREVIEW_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp'])
const TEXT_PREVIEW_EXTENSIONS = new Set(['txt', 'csv'])

function selectInitialDriveSpace(spaces) {
  if (!Array.isArray(spaces)) return null
  return spaces.find(space => space && space.canRead !== false) || null
}

function normalizeFolder(folder) {
  if (!folder || folder.nodeId == null) return null
  return {
    nodeId: Number(folder.nodeId),
    nodeName: folder.nodeName == null ? '' : String(folder.nodeName)
  }
}

function pushMobileFolder(stack, folder) {
  const next = normalizeFolder(folder)
  const current = Array.isArray(stack) ? stack.slice() : []
  return next ? current.concat([next]) : current
}

function popMobileFolder(stack) {
  return Array.isArray(stack) ? stack.slice(0, -1) : []
}

function getMobileFolderParentId(stack) {
  const folders = Array.isArray(stack) ? stack : []
  const current = folders[folders.length - 1]
  return current ? Number(current.nodeId) || 0 : 0
}

function createMobileSearchState(current, keyword) {
  const source = current && typeof current === 'object' ? current : {}
  return {
    parentId: Number(source.parentId) || 0,
    keyword: typeof keyword === 'string' ? keyword : '',
    pageNum: 1
  }
}

function formatMobileBytes(bytes) {
  return formatBytes(bytes)
}

function getMobileRemainingQuotaBytes(space) {
  const source = space && typeof space === 'object' ? space : {}
  const quotaBytes = Math.max(Number(source.quotaBytes) || 0, 0)
  const usedBytes = Math.max(Number(source.usedBytes) || 0, 0)
  return Math.max(quotaBytes - usedBytes, 0)
}

function isMobilePreviewable(node) {
  return isPreviewable(node)
}

function driveMobileErrorMessage(code, serverMessage) {
  return driveErrorMessage(code, serverMessage)
}

function createMobileUploadState(file, targetSpaceId, targetParentId) {
  return {
    file,
    name: file && file.name || '',
    size: Number(file && file.size) || 0,
    operationId: newUploadOperationId(),
    freshUpload: true,
    targetSpaceId,
    targetParentId: targetParentId == null ? 0 : targetParentId,
    progress: 0,
    status: 'uploading',
    error: ''
  }
}

function canStartMobileUpload(uploadState) {
  return !uploadState || uploadState.status !== 'uploading'
}

function updateMobileUploadProgress(uploadState, event) {
  const source = uploadState || {}
  const loaded = Number(event && event.loaded) || 0
  const total = Number(event && event.total) || Number(source.file && source.file.size) || 0
  const progress = total > 0 ? Math.round(loaded * 100 / total) : 0
  return { ...source, progress: Math.max(0, Math.min(100, progress)) }
}

function markMobileUploadFailed(uploadState, error) {
  return { ...(uploadState || {}), status: 'failed', error: error || '上传失败' }
}

function markMobileUploadCanceled(uploadState) {
  return { ...(uploadState || {}), status: 'canceled', error: '' }
}

function markMobileUploadDone(uploadState, uploadedNode) {
  const node = uploadedNode && typeof uploadedNode === 'object'
    ? uploadedNode
    : {}
  return {
    ...(uploadState || {}),
    nodeId: node.nodeId == null ? null : Number(node.nodeId),
    displayName: node.nodeName || uploadState && uploadState.file &&
      uploadState.file.name || '',
    progress: 100,
    status: 'done',
    error: ''
  }
}

function renameMobileUploadNode(uploadState, nodeId, name) {
  if (!uploadState || Number(uploadState.nodeId) !== Number(nodeId)) {
    return uploadState
  }
  return { ...uploadState, displayName: String(name || '') }
}

function clearMobileUploadForNode(uploadState, nodeId) {
  if (!uploadState || Number(uploadState.nodeId) !== Number(nodeId)) {
    return uploadState
  }
  return null
}

function isMobileUploadDestinationCurrent(uploadState, spaceId, parentId) {
  return !!uploadState && Number(uploadState.targetSpaceId) === Number(spaceId) &&
    Number(uploadState.targetParentId) === Number(parentId)
}

function hasFileExtension(fileName) {
  return /(^|\/)\.?[^/]+\.[A-Za-z0-9]{1,10}$/.test(String(fileName || ''))
}

function prepareCapturedFile(file, options) {
  if (!file || hasFileExtension(file.name)) return file
  const extension = CAPTURE_EXTENSIONS[String(file.type || '').toLowerCase()]
  if (!extension) return file
  const config = options && typeof options === 'object' ? options : {}
  const FileApi = config.File || (typeof File !== 'undefined' ? File : null)
  if (!FileApi) return file
  const now = typeof config.now === 'function' ? config.now() : Date.now()
  return new FileApi([file], `照片-${now}.${extension}`, {
    type: file.type,
    lastModified: file.lastModified || now
  })
}

function getMobilePreviewKind(node) {
  if (!node || !node.canPreview) return 'unsupported'
  const extension = String(node.extension || '').replace(/^\./, '').toLowerCase()
  if (IMAGE_PREVIEW_EXTENSIONS.has(extension)) return 'image'
  if (extension === 'pdf') return 'pdf'
  if (TEXT_PREVIEW_EXTENSIONS.has(extension)) return 'text'
  return 'unsupported'
}

async function saveMobileBlob(blob, nodeName, options) {
  const config = options && typeof options === 'object' ? options : {}
  const navigatorApi = config.navigator || (typeof navigator !== 'undefined' ? navigator : null)
  const FileApi = config.File || (typeof File !== 'undefined' ? File : null)
  const name = nodeName == null || String(nodeName).trim() === '' ? '下载文件' : String(nodeName)
  let file = null

  if (FileApi) {
    file = new FileApi([blob], name, { type: (blob && blob.type) || 'application/octet-stream' })
  }

  if (file && navigatorApi && typeof navigatorApi.canShare === 'function' &&
    typeof navigatorApi.share === 'function') {
    let canShare = false
    try {
      canShare = navigatorApi.canShare({ files: [file] }) === true
    } catch (_) {
      canShare = false
    }
    if (canShare) {
      try {
        await navigatorApi.share({ files: [file], title: name })
        return 'shared'
      } catch (error) {
        if (error && error.name === 'AbortError') return 'cancelled'
      }
    }
  }

  const saveAs = config.saveAs || require('file-saver').saveAs
  saveAs(file || blob, name)
  return 'downloaded'
}

module.exports = {
  canStartMobileUpload,
  createMobileSearchState,
  createMobileUploadState,
  driveMobileErrorMessage,
  formatMobileBytes,
  getMobilePreviewKind,
  getMobileRemainingQuotaBytes,
  getMobileFolderParentId,
  isMobileUploadDestinationCurrent,
  isMobilePreviewable,
  clearMobileUploadForNode,
  markMobileUploadCanceled,
  markMobileUploadDone,
  markMobileUploadFailed,
  popMobileFolder,
  prepareCapturedFile,
  pushMobileFolder,
  renameMobileUploadNode,
  saveMobileBlob,
  selectInitialDriveSpace,
  updateMobileUploadProgress
}
