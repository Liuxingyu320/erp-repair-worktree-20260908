import request from '@/utils/request'

// 查询公告列表
export function listNotice(query, config) {
  return request({
    silentError: !!(config && config.silentError),
    url: '/system/notice/list',
    method: 'get',
    params: query
  })
}

// 查询公告详细
export function getNotice(noticeId, config) {
  return request({
    silentError: !!(config && config.silentError),
    url: '/system/notice/' + noticeId,
    method: 'get'
  })
}

// 当前用户按接收人快照查询公告详情
export function getInboxNotice(noticeId) {
  return request({
    url: '/system/notice/inbox/' + noticeId,
    method: 'get'
  })
}

// 新增公告
export function addNotice(data, config) {
  return request({
    silentError: !!(config && config.silentError),
    url: '/system/notice',
    method: 'post',
    data: data
  })
}

// 修改公告
export function updateNotice(data, config) {
  return request({
    silentError: !!(config && config.silentError),
    url: '/system/notice',
    method: 'put',
    data: data
  })
}

// 预览公告受众去重人数
export function previewNoticeAudience(data, config) {
  return request({
    silentError: !!(config && config.silentError),
    url: '/system/notice/audience-preview',
    method: 'post',
    data
  })
}

// 公告受众选择器选项
export function getNoticeAudienceOptions(keyword, config) {
  return request({
    silentError: !!(config && config.silentError),
    url: '/system/notice/audience-options',
    method: 'get',
    params: { keyword }
  })
}

// 立即发布或计划发布
export function publishNotice(noticeId, data) {
  return request({
    url: `/system/notice/${noticeId}/publish`,
    method: 'post',
    data,
    silentError: true
  })
}

export function cancelNoticeSchedule(noticeId, version) {
  return request({
    url: `/system/notice/${noticeId}/cancel-schedule`,
    method: 'post',
    data: { version },
    silentError: true
  })
}

export function offlineNotice(noticeId, version) {
  return request({
    url: `/system/notice/${noticeId}/offline`,
    method: 'post',
    data: { version },
    silentError: true
  })
}

export function createNoticeVersion(noticeId, version) {
  return request({
    url: `/system/notice/${noticeId}/new-version`,
    method: 'post',
    data: { version },
    silentError: true
  })
}

// 删除公告
export function delNotice(noticeId) {
  return request({
    url: '/system/notice/' + noticeId,
    method: 'delete'
  })
}

// 首页顶部公告列表（带已读状态）
export function listNoticeTop() {
  return request({
    url: '/system/notice/listTop',
    method: 'get'
  })
}

// 标记公告已读
export function markNoticeRead(noticeId) {
  return request({
    url: '/system/notice/markRead',
    method: 'post',
    params: { noticeId }
  })
}

// 将当前用户的全部启用公告标记为已读
export function markNoticeReadAll() {
  return request({
    url: '/system/notice/markReadAll',
    method: 'post'
  })
}

// 查询公告已读用户列表
export function listNoticeReadUsers(query, config) {
  return request({
    url: '/system/notice/readUsers/list',
    method: 'get',
    params: query,
    silentError: !!(config && config.silentError)
  })
}
