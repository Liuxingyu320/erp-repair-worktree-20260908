const NUMERIC_ID_PATTERN = /^[1-9]\d{0,18}$/

export function normalizePushId(value) {
  if (value === undefined || value === null) return null
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return null
  const normalized = String(value)
  return NUMERIC_ID_PATTERN.test(normalized) ? normalized : null
}

export function resolvePushRoute(data, options = {}) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return null
  const mobile = options.mobile === true
  if (data.routeType === 'OA_SIGN_PACKAGE_SIGN') {
    const packageId = normalizePushId(data.packageId)
    return packageId ? { path: options.mobile === false ? '/sign-package-handoff' : '/mobile/sign-package', query: { packageId } } : null
  }
  if (data.routeType === 'OA_SIGN_HR_TASK') {
    const taskId = normalizePushId(data.taskId)
    // HR signing task maintenance currently has no phone detail view.
    return taskId ? mobile
      ? { path: '/mobile/messages' }
      : { path: '/oa/sign-task', query: { taskId } } : null
  }
  if (data.routeType === 'HR_HEALTH_CERT_DUE') {
    const employeeId = normalizePushId(data.employeeId || data.userId)
    if (!employeeId) return null
    const recipientUserId = normalizePushId(data.recipientUserId)
    // Managers also receive expiry reminders; their own certificate is not the subject.
    if (recipientUserId !== employeeId) {
      return { path: mobile ? '/mobile/messages' : '/system/user-notification' }
    }
    const query = { employeeId, healthCertificateView: 'mine' }
    const certificateId = normalizePushId(data.certificateId)
    if (certificateId) query.certificateId = certificateId
    return { path: mobile ? '/mobile/hr/health-certificate' : '/hr/healthCertificate', query }
  }
  if (data.routeType === 'USER_NOTIFICATION') {
    return { path: mobile ? '/mobile/messages' : '/system/user-notification' }
  }
  return null
}
