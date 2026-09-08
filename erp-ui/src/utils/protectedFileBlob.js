const { validatePdfBlob } = require("./signPackageFileGate")

const PDF_KINDS = new Set(["preview-pdf", "archive-pdf", "certificate", "sign-package-pdf"])
const DOCX_KINDS = new Set(["preview", "archive"])
const PNG_KINDS = new Set(["signature"])

const DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
const GENERIC_BINARY_MIMES = new Set(["", "application/octet-stream", "binary/octet-stream"])

function normalizedMime(blob) {
  return String(blob && blob.type || "").trim().toLowerCase().split(";", 1)[0]
}

function isBlobLike(value) {
  return !!value && typeof value.size === "number" && typeof value.slice === "function"
}

function asBlob(value, fallbackType) {
  if (isBlobLike(value)) return value
  if (typeof Blob === "undefined") {
    throw new TypeError("Blob is unavailable")
  }
  return new Blob([value], { type: fallbackType })
}

function blobSliceBytes(blob, length) {
  const slice = blob.slice(0, Math.min(blob.size, length))
  if (slice && typeof slice.arrayBuffer === "function") {
    return slice.arrayBuffer().then(buffer => new Uint8Array(buffer))
  }
  if (typeof FileReader === "undefined") {
    return Promise.reject(new TypeError("Blob content cannot be read"))
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(new Uint8Array(reader.result))
    reader.onerror = () => reject(reader.error || new TypeError("Blob content read failed"))
    reader.readAsArrayBuffer(slice)
  })
}

function hasPrefix(bytes, expected) {
  return expected.every((value, index) => bytes[index] === value)
}

function hasZipHeader(bytes) {
  return hasPrefix(bytes, [0x50, 0x4b, 0x03, 0x04]) ||
    hasPrefix(bytes, [0x50, 0x4b, 0x05, 0x06]) ||
    hasPrefix(bytes, [0x50, 0x4b, 0x07, 0x08])
}

function retypeBlob(blob, type) {
  if (normalizedMime(blob) === type) return blob
  return new Blob([blob], { type })
}

async function validateDocxBlob(blob) {
  const mime = normalizedMime(blob)
  if (!blob || blob.size < 4 || (!GENERIC_BINARY_MIMES.has(mime) && mime !== DOCX_MIME)) {
    return false
  }
  try {
    return hasZipHeader(await blobSliceBytes(blob, 4))
  } catch (_) {
    return false
  }
}

async function validatePngBlob(blob) {
  const mime = normalizedMime(blob)
  if (!blob || blob.size < 8 || (!GENERIC_BINARY_MIMES.has(mime) && mime !== "image/png")) {
    return false
  }
  try {
    return hasPrefix(await blobSliceBytes(blob, 8), [
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    ])
  } catch (_) {
    return false
  }
}

async function normalizeProtectedFileBlob(value, kind) {
  const normalizedKind = String(kind || "").trim().toLowerCase()
  if (PDF_KINDS.has(normalizedKind)) {
    const blob = asBlob(value, "application/pdf")
    if (!await validatePdfBlob(blob)) {
      throw new TypeError("文件内容不是有效的 PDF")
    }
    return retypeBlob(blob, "application/pdf")
  }
  if (DOCX_KINDS.has(normalizedKind)) {
    const blob = asBlob(value, DOCX_MIME)
    if (!await validateDocxBlob(blob)) {
      throw new TypeError("文件内容不是有效的 Word 文档")
    }
    return retypeBlob(blob, DOCX_MIME)
  }
  if (PNG_KINDS.has(normalizedKind)) {
    const blob = asBlob(value, "image/png")
    if (!await validatePngBlob(blob)) {
      throw new TypeError("文件内容不是有效的 PNG 图片")
    }
    return retypeBlob(blob, "image/png")
  }
  throw new TypeError("不支持的受保护文件类型")
}

module.exports = {
  normalizeProtectedFileBlob,
  validateDocxBlob,
  validatePngBlob
}
