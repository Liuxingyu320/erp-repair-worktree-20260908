function runtimeOrigin() {
  if (typeof window === "undefined" || !window.location) return ""
  return String(window.location.origin || "")
}

function isHttpProtocol(protocol) {
  return protocol === "http:" || protocol === "https:"
}

function trustedApiOrigin(baseURL, origin) {
  const runtime = String(origin || runtimeOrigin()).trim()
  const base = String(baseURL || "").trim()
  try {
    if (/^https?:\/\//i.test(base)) {
      return new URL(base).origin
    }
    if (runtime) {
      return new URL(base || "/", runtime).origin
    }
  } catch (_) {
    return ""
  }
  return ""
}

function assertTrustedRequestUrl(url, baseURL, origin) {
  const value = String(url == null ? "" : url).trim()
  if (!value) return value
  if (/[\u0000-\u001f\u007f]/.test(value) || value.includes("\\") || value.startsWith("//")) {
    throw new TypeError("Blocked unsafe API request URL")
  }

  const hasScheme = /^[a-z][a-z\d+.-]*:/i.test(value)
  if (!hasScheme) return value

  let target
  try {
    target = new URL(value)
  } catch (_) {
    throw new TypeError("Blocked malformed API request URL")
  }
  if (!isHttpProtocol(target.protocol) || target.username || target.password) {
    throw new TypeError("Blocked unsafe API request URL")
  }

  const allowedOrigin = trustedApiOrigin(baseURL, origin)
  if (!allowedOrigin || target.origin !== allowedOrigin) {
    throw new TypeError("Blocked cross-origin authenticated API request")
  }
  return value
}

function assertTrustedRequestBaseUrl(baseURL, trustedBaseURL, origin) {
  const candidate = trustedApiOrigin(baseURL, origin)
  const trusted = trustedApiOrigin(trustedBaseURL, origin)
  if (!candidate || !trusted || candidate !== trusted) {
    throw new TypeError("Blocked cross-origin API base URL override")
  }
  return baseURL
}

function resolveTrustedApiUrl(baseURL, requestPath, origin) {
  const base = String(baseURL || "").trim()
  const path = assertTrustedRequestUrl(requestPath, base, origin)
  const combined = /^[a-z][a-z\d+.-]*:/i.test(path) ? path : base + path
  assertTrustedRequestUrl(combined, base, origin)
  return combined
}

function safeTrustedApiUrl(baseURL, requestPath, origin) {
  try {
    return resolveTrustedApiUrl(baseURL, requestPath, origin)
  } catch (_) {
    return ""
  }
}

module.exports = {
  assertTrustedRequestBaseUrl,
  assertTrustedRequestUrl,
  resolveTrustedApiUrl,
  safeTrustedApiUrl,
  trustedApiOrigin
}
