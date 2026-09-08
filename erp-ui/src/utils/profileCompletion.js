const PROFILE_COMPLETION_PATH = "/complete-profile"

function isProfileCompletionPath(path) {
  return String(path || "").split("?")[0] === PROFILE_COMPLETION_PATH
}

function resolveProfileCompletionRedirect(fullPath, required) {
  if (!required || isProfileCompletionPath(fullPath)) return ""
  return `${PROFILE_COMPLETION_PATH}?redirect=${encodeURIComponent(fullPath || "/")}`
}

function responseData(error) {
  return (error && error.response && error.response.data) || (error && error.data) || error || {}
}

function isProfileCompletionRequiredError(error) {
  return responseData(error).businessCode === "PROFILE_COMPLETION_REQUIRED"
}

function getProfileCompletionErrorFields(error) {
  const fields = responseData(error).profileMissingFields
  return Array.isArray(fields) ? fields : []
}

module.exports = {
  PROFILE_COMPLETION_PATH,
  isProfileCompletionPath,
  resolveProfileCompletionRedirect,
  isProfileCompletionRequiredError,
  getProfileCompletionErrorFields
}
