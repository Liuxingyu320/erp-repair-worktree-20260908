import request from '@/utils/request'

export function listDriveSpaces() {
  return request({
    url: '/file/drive/spaces',
    method: 'get',
    silentError: true
  })
}

export function listDriveNodes(query) {
  return request({
    url: '/file/drive/nodes',
    method: 'get',
    params: query,
    silentError: true
  })
}

export function getDriveNode(nodeId) {
  return request({
    url: '/file/drive/nodes/' + nodeId,
    method: 'get',
    silentError: true
  })
}

export function createDriveFolder(data) {
  return request({
    url: '/file/drive/folders',
    method: 'post',
    data,
    silentError: true
  })
}

export function uploadDriveFile(file, spaceId, parentId, onUploadProgress, signal, operationId) {
  const data = new FormData()
  data.append('file', file)
  if (operationId) data.append('operationId', operationId)
  data.append('spaceId', spaceId)
  data.append('parentId', parentId == null ? 0 : parentId)
  return request({
    url: '/file/drive/files',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 0,
    silentError: true,
    onUploadProgress,
    signal
  })
}

export function getDriveUploadReceipt(operationId) {
  return request({ url: '/file/drive/uploads/' + encodeURIComponent(operationId), method: 'get', silentError: true })
}

export function renameDriveNode(nodeId, data) {
  return request({
    url: '/file/drive/nodes/' + nodeId + '/name',
    method: 'put',
    data,
    silentError: true
  })
}

export function moveDriveNode(nodeId, data) {
  return request({
    url: '/file/drive/nodes/' + nodeId + '/move',
    method: 'put',
    data,
    silentError: true
  })
}

export function trashDriveNode(nodeId, version) {
  return request({
    url: '/file/drive/nodes/' + nodeId,
    method: 'delete',
    params: { version },
    silentError: true
  })
}

export function listRecentDriveNodes(limit) {
  return request({
    url: '/file/drive/recent',
    method: 'get',
    params: limit == null ? undefined : { limit },
    silentError: true
  })
}

export function listDriveTrash(query) {
  return request({
    url: '/file/drive/trash',
    method: 'get',
    params: query,
    silentError: true
  })
}

export function restoreDriveNode(nodeId) {
  return request({
    url: '/file/drive/trash/' + nodeId + '/restore',
    method: 'post',
    silentError: true
  })
}

export function purgeDriveNode(nodeId) {
  return request({
    url: '/file/drive/trash/' + nodeId,
    method: 'delete',
    silentError: true
  })
}

export function emptyDriveTrash(spaceId) {
  return request({
    url: '/file/drive/trash',
    method: 'delete',
    params: { spaceId },
    silentError: true
  })
}

export function getDriveContent(nodeId, mode) {
  return request({
    url: '/file/drive/nodes/' + nodeId + '/content',
    method: 'get',
    params: { mode },
    responseType: 'blob',
    timeout: 0,
    silentError: true
  })
}

export function updateDriveQuota(spaceId, data) {
  return request({
    url: '/file/drive/spaces/' + spaceId + '/quota',
    method: 'put',
    data,
    silentError: true
  })
}
