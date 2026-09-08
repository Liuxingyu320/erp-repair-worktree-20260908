let commandSequence = 0

const PERSISTENT_TRANSFER_POST = /^\/inventory\/transfer\/(?:save|submit|approve|deliver\/[^/]+|[^/]+\/shipments|shipments\/[^/]+\/receipts|receive\/[^/]+|shipment\/receive\/[^/]+|discrepancies\/[^/]+\/(?:adjudications|resolve)|[^/]+\/withdraw)$/
const PERSISTENT_TRANSFER_DELETE = /^\/inventory\/transfer\/(?:delete\/)?[^/]+$/

function isPersistentTransferCommand(config) {
  const method = String(config && config.method || "get").toLowerCase()
  const requestPath = String(config && config.url || "").split("?")[0]
  return method === "post"
    ? PERSISTENT_TRANSFER_POST.test(requestPath)
    : method === "delete" && PERSISTENT_TRANSFER_DELETE.test(requestPath)
}

function createPersistentCommandRequestId() {
  const browserCrypto = typeof globalThis !== "undefined" && globalThis.crypto
    ? globalThis.crypto
    : (typeof window !== "undefined" ? window.crypto : null)
  if (browserCrypto && typeof browserCrypto.randomUUID === "function") {
    return `transfer:${browserCrypto.randomUUID()}`
  }

  commandSequence = (commandSequence + 1) % 0x1000000
  return [
    "transfer",
    Date.now().toString(36),
    commandSequence.toString(36),
    Math.random().toString(36).slice(2) || "0"
  ].join(":")
}

function applyPersistentCommandRequestId(config) {
  const target = config || {}
  const headers = Object.assign({}, target.headers || {})
  let requestId = ""

  Object.keys(headers).forEach(key => {
    if (key.toLowerCase() === "x-request-id") {
      if (!requestId && headers[key] !== undefined && headers[key] !== null) {
        requestId = String(headers[key]).trim()
      }
      delete headers[key]
    }
  })
  headers["X-Request-Id"] = requestId || createPersistentCommandRequestId()
  target.headers = headers
  return target
}

module.exports = {
  applyPersistentCommandRequestId,
  createPersistentCommandRequestId,
  isPersistentTransferCommand
}
