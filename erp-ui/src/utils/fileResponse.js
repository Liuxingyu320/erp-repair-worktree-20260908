const ERROR_BODY_LIMIT = 256 * 1024

const STATUS_MESSAGES = {
  400: "请求参数不正确",
  401: "无效的会话，或者会话已过期，请重新登录。",
  403: "当前操作没有权限",
  404: "访问资源不存在",
  409: "当前数据状态已发生变化，请刷新后重试",
  413: "文件大小超过服务端限制",
  429: "请求过于频繁，请稍后重试",
  500: "服务器内部错误，请稍后重试",
  502: "上游服务暂时不可用，请稍后重试",
  503: "服务暂时不可用，请稍后重试",
  504: "上游服务响应超时，请稍后重试"
}

const JSON_MIME_PATTERN = /^(?:application|text)\/(?:[\w.+-]*\+)?json$/
const TEXT_MIME_PATTERN = /^(?:text\/|application\/(?:problem\+json|xml|xhtml\+xml))/

function normalizeMime(value) {
  return String(value || "").trim().toLowerCase().split(";", 1)[0]
}

function headerValue(headers, name) {
  if (!headers) return ""
  if (typeof headers.get === "function") {
    return headers.get(name) || headers.get(String(name).toLowerCase()) || ""
  }
  const expected = String(name).toLowerCase()
  const key = Object.keys(headers).find(item => String(item).toLowerCase() === expected)
  return key ? headers[key] : ""
}

function responseTypeOf(response) {
  return String(
    response && response.config && response.config.responseType ||
    response && response.request && response.request.responseType ||
    ""
  ).trim().toLowerCase()
}

function isFileResponse(response) {
  const responseType = responseTypeOf(response)
  return responseType === "blob" || responseType === "arraybuffer"
}

function isBlobLike(value) {
  return !!value &&
    typeof value.size === "number" &&
    typeof value.slice === "function"
}

function isArrayBufferLike(value) {
  return typeof ArrayBuffer !== "undefined" && (
    value instanceof ArrayBuffer ||
    (typeof ArrayBuffer.isView === "function" && ArrayBuffer.isView(value))
  )
}

async function readPrefixBytes(value, limit = ERROR_BODY_LIMIT) {
  if (isBlobLike(value)) {
    const slice = value.slice(0, Math.min(value.size, limit))
    if (slice && typeof slice.arrayBuffer === "function") {
      return new Uint8Array(await slice.arrayBuffer())
    }
    if (typeof FileReader === "undefined") {
      throw new TypeError("Blob content cannot be read")
    }
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(new Uint8Array(reader.result))
      reader.onerror = () => reject(reader.error || new TypeError("Blob content read failed"))
      reader.readAsArrayBuffer(slice)
    })
  }
  if (isArrayBufferLike(value)) {
    const view = value instanceof ArrayBuffer
      ? new Uint8Array(value)
      : new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
    return view.slice(0, Math.min(view.byteLength, limit))
  }
  if (typeof value === "string") {
    if (typeof TextEncoder !== "undefined") {
      return new TextEncoder().encode(value).slice(0, limit)
    }
    return Uint8Array.from(value.slice(0, limit), character => character.charCodeAt(0) & 0xff)
  }
  return new Uint8Array()
}

function decodeBytes(bytes) {
  if (!bytes || bytes.length === 0) return ""
  if (typeof TextDecoder !== "undefined") {
    return new TextDecoder("utf-8", { fatal: false }).decode(bytes)
  }
  let value = ""
  for (let index = 0; index < bytes.length; index += 1) {
    value += String.fromCharCode(bytes[index])
  }
  try {
    return decodeURIComponent(escape(value))
  } catch (_) {
    return value
  }
}

function hasPrefix(bytes, expected) {
  return bytes.length >= expected.length &&
    expected.every((value, index) => bytes[index] === value)
}

function hasAsciiPrefix(bytes, expected) {
  const normalized = String(expected)
  if (bytes.length < normalized.length) return false
  for (let index = 0; index < normalized.length; index += 1) {
    if (bytes[index] !== normalized.charCodeAt(index)) return false
  }
  return true
}

function hasKnownBinarySignature(bytes) {
  return hasAsciiPrefix(bytes, "%PDF-") ||
    hasPrefix(bytes, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]) ||
    hasPrefix(bytes, [0xff, 0xd8, 0xff]) ||
    hasAsciiPrefix(bytes, "GIF87a") ||
    hasAsciiPrefix(bytes, "GIF89a") ||
    (hasAsciiPrefix(bytes, "RIFF") && bytes.length >= 12 &&
      String.fromCharCode(...bytes.slice(8, 12)) === "WEBP") ||
    hasPrefix(bytes, [0x50, 0x4b, 0x03, 0x04]) ||
    hasPrefix(bytes, [0x50, 0x4b, 0x05, 0x06]) ||
    hasPrefix(bytes, [0x50, 0x4b, 0x07, 0x08]) ||
    hasPrefix(bytes, [0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1]) ||
    hasPrefix(bytes, [0x1f, 0x8b]) ||
    hasPrefix(bytes, [0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c]) ||
    hasAsciiPrefix(bytes, "Rar!")
}

function looksLikeHtml(value) {
  const source = String(value || "").replace(/^\uFEFF/, "").trimStart().toLowerCase()
  return source.startsWith("<!doctype html") ||
    source.startsWith("<html") ||
    source.startsWith("<head") ||
    source.startsWith("<body")
}

function parseJson(value) {
  const source = String(value || "").replace(/^\uFEFF/, "").trim()
  if (!source || (source[0] !== "{" && source[0] !== "[")) return null
  try {
    return JSON.parse(source)
  } catch (_) {
    return null
  }
}

function safeMessage(value) {
  const message = String(value || "")
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
  if (!message || /<[^>]+>/.test(message)) return ""
  return message.slice(0, 500)
}

function numericCode(value) {
  if (value === null || value === undefined || value === "") return null
  const number = Number(value)
  return Number.isFinite(number) ? number : value
}

function statusMessage(status, fallback) {
  const code = Number(status)
  return safeMessage(fallback) || STATUS_MESSAGES[code] ||
    (code ? `系统接口 ${code} 异常` : "系统接口请求失败")
}

function errorDetails(payload, status, fallbackMessage, forceInvalidFile) {
  const source = payload && typeof payload === "object" && !Array.isArray(payload)
    ? payload
    : {}
  const httpStatus = Number(status) || 0
  let code = numericCode(source.code)
  if (code === null) code = numericCode(source.status)
  if (code === null || (Number(code) === 200 && (httpStatus >= 400 || forceInvalidFile))) {
    code = httpStatus >= 400 ? httpStatus : "FILE_RESPONSE_INVALID"
  }
  const businessCode = source.businessCode == null ? undefined : String(source.businessCode)
  const message = safeMessage(source.msg || source.message || fallbackMessage) ||
    statusMessage(code === "FILE_RESPONSE_INVALID" ? httpStatus : code)
  return { code, businessCode, message, payload: source, httpStatus }
}

class FileResponseError extends Error {
  constructor(details) {
    super(details.message)
    this.name = "FileResponseError"
    this.code = details.code
    this.businessCode = details.businessCode
    this.payload = details.payload
    this.httpStatus = details.httpStatus
    this.isFileResponseError = true
  }
}

function createFileResponseError(payload, status, fallbackMessage, forceInvalidFile) {
  return new FileResponseError(errorDetails(payload, status, fallbackMessage, forceInvalidFile))
}

async function normalizeFileResponse(data, metadata = {}) {
  const status = Number(metadata.status) || 200
  const headers = metadata.headers || {}
  const mime = normalizeMime(
    metadata.contentType ||
    headerValue(headers, "content-type") ||
    data && data.type
  )
  const disposition = String(
    metadata.contentDisposition ||
    headerValue(headers, "content-disposition") ||
    ""
  ).toLowerCase()
  const bytes = await readPrefixBytes(data)
  const knownBinary = hasKnownBinarySignature(bytes)
  const text = knownBinary ? "" : decodeBytes(bytes)
  const payload = knownBinary ? null : parseJson(text)
  const html = !knownBinary && looksLikeHtml(text)
  const jsonMime = JSON_MIME_PATTERN.test(mime)
  const textMime = TEXT_MIME_PATTERN.test(mime)
  const attachment = disposition.includes("attachment")

  if (status < 200 || status >= 300) {
    throw createFileResponseError(
      payload,
      status,
      html ? statusMessage(status) : safeMessage(text),
      false
    )
  }

  if (knownBinary) return data

  if (payload) {
    throw createFileResponseError(
      payload,
      status,
      "文件接口返回了业务错误",
      true
    )
  }

  if (html) {
    throw createFileResponseError(
      {},
      status,
      "文件服务返回了网页错误，未生成有效文件",
      true
    )
  }

  if (jsonMime) {
    throw createFileResponseError(
      {},
      status,
      "文件接口返回了无法识别的 JSON 内容",
      true
    )
  }

  // text/plain 也可能是合法的文本下载。只有能识别为 JSON/HTML、
  // HTTP 非 2xx，或明确的非附件错误媒体类型时才拦截，避免误判文本下载。
  if (textMime && !attachment && mime !== "text/plain" && mime !== "text/csv") {
    throw createFileResponseError(
      {},
      status,
      "文件接口返回了文本错误，未生成有效文件",
      true
    )
  }

  return data
}

function normalizeTransportFailure(error) {
  const status = Number(error && error.response && error.response.status) || 0
  const sourceMessage = safeMessage(error && error.message)
  const code = error && error.code
  if (code === "ECONNABORTED" || code === "ETIMEDOUT" || /timeout/i.test(sourceMessage)) {
    return { code: status || "TIMEOUT", message: "系统接口请求超时", httpStatus: status }
  }
  if (code === "ERR_CANCELED" || /cancell?ed|aborted/i.test(sourceMessage)) {
    return { code: status || "REQUEST_CANCELED", message: "请求已取消", httpStatus: status }
  }
  if (!status && (!sourceMessage || /network error|failed to fetch|load failed/i.test(sourceMessage))) {
    return { code: "NETWORK_ERROR", message: "后端接口连接异常", httpStatus: 0 }
  }
  return {
    code: status || code || "REQUEST_FAILED",
    message: statusMessage(status, status ? "" : sourceMessage),
    httpStatus: status
  }
}

module.exports = {
  ERROR_BODY_LIMIT,
  FileResponseError,
  createFileResponseError,
  hasKnownBinarySignature,
  isFileResponse,
  normalizeFileResponse,
  normalizeTransportFailure,
  responseTypeOf,
  statusMessage
}
