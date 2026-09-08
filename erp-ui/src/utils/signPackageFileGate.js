function normalize(value) {
  if (value === undefined || value === null) return ""
  return String(value).trim()
}

function packageDocuments(signPackage) {
  return signPackage && Array.isArray(signPackage.documents) ? signPackage.documents : []
}

function requiresReadConfirmation(document) {
  return !!document && (document.employeeSignRequired === "Y" ||
    document.readConfirmationRequired === "Y")
}

function documentLoadKey(signPackage, document, kind) {
  if (!signPackage || !document) return ""
  const packageId = normalize(signPackage.packageId)
  const documentId = normalize(document.documentId)
  const isFinal = kind === "final"
  const version = normalize(isFinal ? signPackage.finalDocumentVersion : signPackage.documentVersion)
  const hash = normalize(isFinal ? document.finalPdfHash : document.reviewPdfHash)
  if (!packageId || !documentId || !version || !hash) return ""
  return [isFinal ? "final" : "review", packageId, documentId, version, hash].join(":")
}

function isDocumentLoaded(loadedKeys, signPackage, document, kind) {
  const key = documentLoadKey(signPackage, document, kind)
  return !!key && !!(loadedKeys && loadedKeys[key])
}

function previewPageLoadKey(signPackage, document, kind, pageNumber) {
  const loadKey = documentLoadKey(signPackage, document, kind)
  const page = Number(pageNumber)
  if (!loadKey || !Number.isSafeInteger(page) || page < 1) return ""
  return `${loadKey}:page:${page}`
}

function allPreviewPagesLoaded(loadedPageKeys, signPackage, document, kind, pageCount) {
  const total = Number(pageCount)
  if (!Number.isSafeInteger(total) || total < 1) return false
  for (let page = 1; page <= total; page += 1) {
    const key = previewPageLoadKey(signPackage, document, kind, page)
    if (!key || !(loadedPageKeys && loadedPageKeys[key])) return false
  }
  return true
}

function requiredInitialDocuments(signPackage) {
  return packageDocuments(signPackage).filter(requiresReadConfirmation)
}

function allInitialDocumentsLoaded(loadedKeys, signPackage) {
  const documents = requiredInitialDocuments(signPackage)
  return documents.length > 0 && documents.every(document =>
    isDocumentLoaded(loadedKeys, signPackage, document, "review"))
}

function hasCompleteFinalMetadata(signPackage) {
  const documents = packageDocuments(signPackage)
  return !!(signPackage && normalize(signPackage.finalDocumentVersion) &&
    normalize(signPackage.finalDocumentRootHash) && documents.length > 0 &&
    documents.every(document => normalize(document.finalPdfHash)))
}

function allFinalDocumentsLoaded(loadedKeys, signPackage) {
  if (!hasCompleteFinalMetadata(signPackage)) return false
  return packageDocuments(signPackage).every(document =>
    isDocumentLoaded(loadedKeys, signPackage, document, "final"))
}

const PDF_HEADER = [0x25, 0x50, 0x44, 0x46, 0x2d]
const PDF_EOF = [0x25, 0x25, 0x45, 0x4f, 0x46]
const PDF_TAIL_SCAN_BYTES = 4096
const PDF_ALLOWED_TRAILING_BYTES = 1024

function hasAllowedPdfMimeType(blob) {
  if (!blob || typeof blob.size !== "number" || blob.size <= 0) return false
  const contentType = normalize(blob.type).toLowerCase().split(";", 1)[0]
  return contentType === "application/pdf" ||
    contentType === "application/octet-stream" ||
    contentType === "binary/octet-stream"
}

function bytesMatch(bytes, offset, expected) {
  if (!bytes || offset < 0 || offset + expected.length > bytes.length) return false
  return expected.every((value, index) => bytes[offset + index] === value)
}

function lastBytesIndexOf(bytes, expected) {
  if (!bytes || bytes.length < expected.length) return -1
  for (let offset = bytes.length - expected.length; offset >= 0; offset -= 1) {
    if (bytesMatch(bytes, offset, expected)) return offset
  }
  return -1
}

function isPdfTrailingWhitespace(value) {
  return value === 0x00 || value === 0x09 || value === 0x0a ||
    value === 0x0c || value === 0x0d || value === 0x20
}

function blobSliceBytes(blob, start, end) {
  const slice = blob.slice(start, end)
  if (slice && typeof slice.arrayBuffer === "function") {
    return slice.arrayBuffer().then(buffer => new Uint8Array(buffer))
  }
  if (typeof FileReader === "undefined") {
    return Promise.reject(new TypeError("Blob content cannot be read in this environment"))
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(new Uint8Array(reader.result))
    reader.onerror = () => reject(reader.error || new TypeError("Blob content read failed"))
    reader.readAsArrayBuffer(slice)
  })
}

async function validatePdfBlob(blob) {
  if (!hasAllowedPdfMimeType(blob) || typeof blob.slice !== "function") return false
  if (blob.size < PDF_HEADER.length + PDF_EOF.length) return false
  try {
    const header = await blobSliceBytes(blob, 0, Math.min(blob.size, 8))
    if (!bytesMatch(header, 0, PDF_HEADER)) return false

    const tailStart = Math.max(0, blob.size - PDF_TAIL_SCAN_BYTES)
    const tail = await blobSliceBytes(blob, tailStart, blob.size)
    const eofOffset = lastBytesIndexOf(tail, PDF_EOF)
    if (eofOffset < 0) return false

    const eofEnd = eofOffset + PDF_EOF.length
    if (blob.size - (tailStart + eofEnd) > PDF_ALLOWED_TRAILING_BYTES) return false
    for (let offset = eofEnd; offset < tail.length; offset += 1) {
      if (!isPdfTrailingWhitespace(tail[offset])) return false
    }
    return true
  } catch (_) {
    return false
  }
}

function createDocumentFrameLoadContext(requestSequence, signPackage, document, kind, objectUrl, pageNumber = 1) {
  const loadKey = documentLoadKey(signPackage, document, kind)
  const normalizedUrl = normalize(objectUrl)
  const sequence = Number(requestSequence)
  const page = Number(pageNumber)
  if (!Number.isSafeInteger(sequence) || sequence < 1 || !loadKey || !normalizedUrl ||
    !Number.isSafeInteger(page) || page < 1) return null
  const token = ["sign-pdf", sequence, loadKey, page, normalizedUrl].join("|")
  return {
    requestSequence: sequence,
    packageId: normalize(signPackage.packageId),
    documentId: normalize(document.documentId),
    kind: kind === "final" ? "final" : "review",
    loadKey,
    pageNumber: page,
    objectUrl: normalizedUrl,
    token
  }
}

function matchesDocumentFrameLoadContext(context, state) {
  if (!context || !state || !state.signPackage || !state.document) return false
  const kind = state.kind === "final" ? "final" : "review"
  const page = state.pageNumber == null ? 1 : Number(state.pageNumber)
  return Number(state.requestSequence) === context.requestSequence &&
    normalize(state.signPackage.packageId) === context.packageId &&
    normalize(state.document.documentId) === context.documentId &&
    kind === context.kind &&
    page === context.pageNumber &&
    documentLoadKey(state.signPackage, state.document, kind) === context.loadKey &&
    normalize(state.objectUrl) === context.objectUrl &&
    normalize(state.eventToken) === context.token &&
    normalize(state.eventObjectUrl) === context.objectUrl
}

module.exports = {
  allFinalDocumentsLoaded,
  allInitialDocumentsLoaded,
  allPreviewPagesLoaded,
  createDocumentFrameLoadContext,
  documentLoadKey,
  hasCompleteFinalMetadata,
  isDocumentLoaded,
  matchesDocumentFrameLoadContext,
  previewPageLoadKey,
  validatePdfBlob,
  requiresReadConfirmation
}
