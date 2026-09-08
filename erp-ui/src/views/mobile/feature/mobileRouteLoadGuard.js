function createMobileRouteLoadGuard() {
  let version = 0

  return {
    begin(featureKey) {
      version += 1
      return {
        version,
        featureKey: String(featureKey || "")
      }
    },
    invalidate() {
      version += 1
    },
    isCurrent(token, featureKey) {
      return !!token &&
        token.version === version &&
        token.featureKey === String(featureKey || "")
    }
  }
}

module.exports = {
  createMobileRouteLoadGuard
}
