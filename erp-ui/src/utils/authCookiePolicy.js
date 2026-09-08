function currentProtocol() {
  if (typeof window === "undefined" || !window.location) return ""
  return String(window.location.protocol || "").toLowerCase()
}

function authCookieOptions(protocol = currentProtocol()) {
  const options = {
    path: "/",
    sameSite: "Lax"
  }
  if (String(protocol).toLowerCase() === "https:") {
    options.secure = true
  }
  return options
}

module.exports = {
  authCookieOptions
}
