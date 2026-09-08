const NUMERIC_ID_PATTERN = /^[1-9]\d{0,18}$/

function normalizeNumericId(value) {
  if (value === undefined || value === null) return null
  const normalized = String(value)
  return NUMERIC_ID_PATTERN.test(normalized) ? normalized : null
}

export function resolvePushRoute(data) {
  if (!data || typeof data !== 'object') return null
  if (data.routeType === 'OA_SIGN_PACKAGE_SIGN') {
    const packageId = normalizeNumericId(data.packageId)
    return packageId ? { path: '/mobile/sign-package', query: { packageId } } : null
  }
  if (data.routeType === 'OA_SIGN_HR_TASK') {
    const taskId = normalizeNumericId(data.taskId)
    return taskId ? { path: '/oa/sign-task', query: { taskId } } : null
  }
  return null
}
