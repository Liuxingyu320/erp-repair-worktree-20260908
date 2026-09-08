function nativeOriginRequired() {
  const error = new Error("NATIVE_API_ORIGIN_REQUIRED")
  error.code = "NATIVE_API_ORIGIN_REQUIRED"
  return error
}

function resolveApiBaseUrl({ basePath, nativeOrigin, isNative, production }) {
  if (!isNative || !production) {
    return basePath
  }

  let parsedOrigin
  try {
    parsedOrigin = new URL(nativeOrigin)
  } catch (error) {
    throw nativeOriginRequired()
  }

  if (
    parsedOrigin.protocol !== "https:" ||
    !parsedOrigin.hostname ||
    parsedOrigin.username ||
    parsedOrigin.password
  ) {
    throw nativeOriginRequired()
  }

  const resolved = new URL(basePath || "/", parsedOrigin.origin)
  if (resolved.protocol !== "https:" || resolved.origin !== parsedOrigin.origin) {
    throw nativeOriginRequired()
  }
  return resolved.toString()
}

module.exports = {
  resolveApiBaseUrl
}
