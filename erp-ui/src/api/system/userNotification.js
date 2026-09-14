import request from '@/utils/request'

export function listUserNotifications(params) {
  return request({
    url: params ? '/system/user-notification/page' : '/system/user-notification/list',
    params,
    method: 'get',
    silentError: true
  })
}

export function markAllUserNotificationsRead(snapshotMaxId) {
  return request({
    url: '/system/user-notification/read-all',
    method: 'post',
    data: { snapshotMaxId },
    silentError: true
  })
}

export function getUserNotificationUnreadCount() {
  return request({
    url: '/system/user-notification/unread-count',
    method: 'get',
    silentError: true
  })
}

export function markUserNotificationRead(notificationId) {
  return request({
    url: '/system/user-notification/' + notificationId + '/read',
    method: 'post',
    silentError: true
  })
}

function authorizedOptions(requestOptions) {
  const config = {}
  if (Object.prototype.hasOwnProperty.call(requestOptions, 'authToken')) {
    config.headers = { isToken: false }
    if (requestOptions.authToken) {
      config.headers.Authorization = `Bearer ${requestOptions.authToken}`
    }
  }
  if (requestOptions.signal) config.signal = requestOptions.signal
  return config
}

export function registerUserDeviceToken(data, options = {}) {
  return request({
    url: '/system/user-notification/device-token',
    method: 'post',
    data,
    silentError: true,
    ...authorizedOptions(options)
  })
}

export function disableUserDeviceToken(data, options = {}) {
  return request({
    url: '/system/user-notification/device-token',
    method: 'delete',
    data,
    silentError: true,
    ...authorizedOptions(options)
  })
}
