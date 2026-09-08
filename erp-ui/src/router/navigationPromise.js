/**
 * Keep duplicate route clicks as successful no-ops without masking genuine
 * navigation failures from callers that need to render a retry/error state.
 */
function settleNavigation(promise, isDuplicated) {
  return Promise.resolve(promise).catch(error => {
    if (typeof isDuplicated === "function" && isDuplicated(error)) {
      return error
    }
    return Promise.reject(error)
  })
}

module.exports = { settleNavigation }
