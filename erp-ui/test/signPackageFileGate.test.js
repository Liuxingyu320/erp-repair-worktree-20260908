const assert = require("assert")
const fs = require("fs")
const path = require("path")

const gate = require("../src/utils/signPackageFileGate")

function signPackage(overrides) {
  return Object.assign({
    packageId: 21,
    status: "pending_sign",
    documentVersion: "review-v1",
    finalDocumentVersion: "final-v1",
    finalDocumentRootHash: "root-v1",
    documents: [
      {
        documentId: 101,
        employeeSignRequired: "Y",
        readConfirmationRequired: "Y",
        reviewPdfHash: "review-hash-101",
        finalPdfHash: "final-hash-101"
      },
      {
        documentId: 102,
        employeeSignRequired: "N",
        readConfirmationRequired: "Y",
        reviewPdfHash: "review-hash-102",
        finalPdfHash: "final-hash-102"
      }
    ]
  }, overrides)
}

function pdfBlob(contentType, suffix) {
  const tail = suffix === undefined ? "%%EOF\n" : suffix
  return new Blob([
    "%PDF-1.7\n",
    "1 0 obj\n<< /Type /Catalog >>\nendobj\n",
    "startxref\n0\n",
    tail
  ], { type: contentType })
}

;(async () => {
const current = signPackage()
const review101 = gate.documentLoadKey(current, current.documents[0], "review")
const review102 = gate.documentLoadKey(current, current.documents[1], "review")
assert.ok(review101 && review102)
assert.strictEqual(gate.allInitialDocumentsLoaded({}, current), false,
  "a failed or not-yet-started download must keep signing gated")
assert.strictEqual(gate.allInitialDocumentsLoaded({ [review101]: true }, current), false,
  "loading only one required file must keep package signing gated")
assert.strictEqual(gate.allInitialDocumentsLoaded({ [review101]: true, [review102]: true }, current), true,
  "all required review files from the current version should unlock the file gate")

const review101Page1 = gate.previewPageLoadKey(current, current.documents[0], "review", 1)
const review101Page2 = gate.previewPageLoadKey(current, current.documents[0], "review", 2)
assert.strictEqual(gate.allPreviewPagesLoaded({ [review101Page1]: true },
  current, current.documents[0], "review", 2), false,
"one loaded page must not represent a complete multi-page contract")
assert.strictEqual(gate.allPreviewPagesLoaded({ [review101Page1]: true, [review101Page2]: true },
  current, current.documents[0], "review", 2), true,
"every authenticated preview page must load before the document may unlock")

const refreshed = signPackage({ documentVersion: "review-v2" })
assert.strictEqual(
  gate.allInitialDocumentsLoaded({ [review101]: true, [review102]: true }, refreshed),
  false,
  "loaded state from an older package version must not unlock a refreshed package"
)

const final101 = gate.documentLoadKey(current, current.documents[0], "final")
const final102 = gate.documentLoadKey(current, current.documents[1], "final")
assert.strictEqual(gate.hasCompleteFinalMetadata(current), true)
assert.strictEqual(current.documents.some(document => document.finalPdfUrl), false,
  "employee-visible final metadata must not depend on a server-side storage path")
assert.strictEqual(gate.allFinalDocumentsLoaded({ [final101]: true }, current), false,
  "final confirmation must wait until every visible final document loads")
assert.strictEqual(gate.allFinalDocumentsLoaded({ [final101]: true, [final102]: true }, current), true)

const incompleteFinal = signPackage({
  documents: current.documents.map((document, index) =>
    index === 1 ? Object.assign({}, document, { finalPdfHash: "" }) : document)
})
assert.strictEqual(gate.hasCompleteFinalMetadata(incompleteFinal), false)
assert.strictEqual(gate.allFinalDocumentsLoaded({ [final101]: true, [final102]: true }, incompleteFinal), false,
  "missing final metadata must fail closed even when stale loaded keys exist")
assert.strictEqual(await gate.validatePdfBlob(pdfBlob("application/pdf")), true,
  "a PDF response with a valid header and EOF marker should pass content validation")
assert.strictEqual(await gate.validatePdfBlob(pdfBlob("application/octet-stream")), true,
  "an octet-stream response should remain compatible when its bytes are a valid PDF")
assert.strictEqual(await gate.validatePdfBlob(new Blob([
  "<html><body>gateway error</body></html>"
], { type: "application/pdf" })), false,
"an error page mislabeled as application/pdf must fail closed")
assert.strictEqual(await gate.validatePdfBlob(new Blob([
  "not a pdf"
], { type: "application/octet-stream" })), false,
"an octet-stream MIME type alone must not be enough to unlock signing")
assert.strictEqual(await gate.validatePdfBlob(pdfBlob("application/pdf", "")), false,
  "a truncated PDF without a terminal EOF marker must fail closed")
assert.strictEqual(await gate.validatePdfBlob(pdfBlob("application/pdf", "%%EOF\ncorrupt")), false,
  "non-whitespace bytes after the terminal EOF marker must fail closed")
assert.strictEqual(await gate.validatePdfBlob(new Blob([""], { type: "application/pdf" })), false,
  "an empty PDF response must not unlock the document gate")
assert.strictEqual(await gate.validatePdfBlob(pdfBlob("text/html")), false,
  "unexpected response MIME types must fail closed even if they contain PDF-like bytes")

const frameContext = gate.createDocumentFrameLoadContext(
  7,
  current,
  current.documents[0],
  "review",
  "blob:preview-page-1"
)
const currentFrameState = {
  requestSequence: 7,
  signPackage: current,
  document: current.documents[0],
  kind: "review",
  objectUrl: "blob:preview-page-1",
  eventToken: frameContext.token,
  eventObjectUrl: "blob:preview-page-1"
}
assert.strictEqual(gate.matchesDocumentFrameLoadContext(frameContext, currentFrameState), true,
  "the current preview image load may unlock its exact package, document, version, hash, request, and URL")
assert.strictEqual(gate.matchesDocumentFrameLoadContext(frameContext,
  Object.assign({}, currentFrameState, { requestSequence: 6 })), false,
"a stale preview load or error from an earlier request must be ignored")
assert.strictEqual(gate.matchesDocumentFrameLoadContext(frameContext,
  Object.assign({}, currentFrameState, { eventToken: "stale-token" })), false,
"a stale preview image node must not affect the current file gate")
assert.strictEqual(gate.matchesDocumentFrameLoadContext(frameContext,
  Object.assign({}, currentFrameState, { objectUrl: "blob:preview-page-2" })), false,
"a stale image event for a revoked preview page must not affect the current gate")
assert.strictEqual(gate.matchesDocumentFrameLoadContext(frameContext,
  Object.assign({}, currentFrameState, { signPackage: refreshed })), false,
"an image event for an older document version must not unlock the refreshed package")
assert.strictEqual(gate.matchesDocumentFrameLoadContext(frameContext,
  Object.assign({}, currentFrameState, { document: current.documents[1] })), false,
"an image event for another document must not unlock the active document")

const mobileSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/signPackage/index.vue"),
  "utf8"
)
const mobilePolicySource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/signPackage/mobileSignPackagePolicy.js"),
  "utf8"
)
const objectUrlMethodMatches = mobileSource.match(/^    (?:async\s+)?applyDocumentObjectUrl\s*\(/gm) || []
assert.strictEqual(objectUrlMethodMatches.length, 1,
  "mobile signing must expose exactly one document-object-URL implementation")
const objectUrlMethodStart = mobileSource.indexOf("async applyDocumentObjectUrl(")
const objectUrlMethodEnd = mobileSource.indexOf("    isCurrentPrimaryFrameEvent(", objectUrlMethodStart)
const objectUrlMethodSource = mobileSource.slice(objectUrlMethodStart, objectUrlMethodEnd)
assert.ok(
  objectUrlMethodStart >= 0 && objectUrlMethodEnd > objectUrlMethodStart &&
    objectUrlMethodSource.indexOf("await validatePdfBlob(fileBlob)") < objectUrlMethodSource.indexOf("URL.createObjectURL(fileBlob)") &&
    objectUrlMethodSource.includes("if (!this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) return false") &&
    objectUrlMethodSource.includes("URL.revokeObjectURL(objectUrl)") &&
    objectUrlMethodSource.includes("return true"),
  "the only mobile document-object-URL implementation must validate PDF bytes and reject stale responses before exposing the blob"
)
assert.ok(
  mobileSource.includes('v-else-if="fileError"') &&
    mobileSource.includes("retryActiveDocumentFiles") &&
    mobileSource.includes("签约文件加载失败") &&
    mobileSource.includes("待确认文件加载失败"),
  "mobile signing should expose explicit primary-file failure and retry states"
)
assert.ok(
  mobileSource.includes("currentDocumentPrimaryLoaded") &&
    mobileSource.includes("allSigningFilesLoaded") &&
    mobileSource.includes("allFinalFilesLoaded") &&
    mobileSource.includes("clearDocumentLoaded(signPackage, document, primaryKind)") &&
    mobilePolicySource.includes("本次已加载") &&
    mobileSource.includes("文件尚未成功加载，请重新加载后再确认阅读"),
  "read, sign, and final-confirm actions should all use current successful-download gates"
)
assert.ok(
  mobileSource.includes("fileRequestSequence") &&
    mobileSource.includes("isCurrentDocumentRequest") &&
    mobileSource.includes("文件版本已更新，请重新加载并阅读最新文件"),
  "stale responses and refreshed document versions must not unlock actions"
)
const primaryPreviewMethodSource = mobileSource.slice(
  mobileSource.indexOf("loadPrimaryDocumentPreview(requestSequence"),
  mobileSource.indexOf("    retryActiveDocumentFiles()", mobileSource.indexOf("loadPrimaryDocumentPreview(requestSequence"))
)
assert.ok(
  primaryPreviewMethodSource.includes("getMySignPackageDocumentPreview(packageId, documentId, kind)") &&
    primaryPreviewMethodSource.includes("downloadMySignPackageDocumentPreviewPage(packageId, documentId, targetPage, kind)") &&
    primaryPreviewMethodSource.includes("pageCount < 1 || pageCount > 200") &&
    primaryPreviewMethodSource.includes("validatePreviewImageBlob(imageBlob)") &&
    primaryPreviewMethodSource.includes("URL.createObjectURL(imageBlob)") &&
    primaryPreviewMethodSource.includes("isCurrentDocumentRequest(requestSequence, packageId, documentId)") &&
    !primaryPreviewMethodSource.includes("markDocumentLoaded"),
  "metadata and a validated authenticated PNG page must still wait for the current image load event before unlocking"
)
assert.ok(
  mobileSource.includes('class="document-preview-image"') &&
    mobileSource.includes('@load="handlePrimaryDocumentFrameLoad"') &&
    mobileSource.includes('@error="handlePrimaryDocumentFrameError"') &&
    !mobileSource.includes("<iframe"),
  "the primary mobile preview should use image load/error events instead of an iframe PDF plugin"
)
const frameLoadHandlerSource = mobileSource.slice(
  mobileSource.indexOf("handlePrimaryDocumentFrameLoad(event)"),
  mobileSource.indexOf("handlePrimaryDocumentFrameError(event)")
)
const frameErrorHandlerSource = mobileSource.slice(
  mobileSource.indexOf("handlePrimaryDocumentFrameError(event)"),
  mobileSource.indexOf("    initSignatureCanvas() {", mobileSource.indexOf("handlePrimaryDocumentFrameError(event)"))
)
assert.ok(
  mobileSource.includes("matchesDocumentFrameLoadContext") &&
    frameLoadHandlerSource.includes("if (!this.isCurrentPrimaryFrameEvent(event)) return false") &&
    frameLoadHandlerSource.includes("this.markPreviewPageLoaded") &&
    frameErrorHandlerSource.includes("if (!this.isCurrentPrimaryFrameEvent(event)) return false") &&
    frameErrorHandlerSource.includes("this.clearPreviewPageLoaded") &&
    frameErrorHandlerSource.includes("this.clearDocumentLoaded") &&
    frameErrorHandlerSource.includes("预览加载失败"),
  "current image errors must clear the gate while stale preview load/error events are ignored"
)
const auxiliaryMethodStart = mobileSource.indexOf("loadAuxiliaryDocumentFile(label")
const auxiliaryMethodSource = mobileSource.slice(
  auxiliaryMethodStart,
  mobileSource.indexOf("shouldUseFinalDocument(document)", auxiliaryMethodStart)
)
assert.ok(
  mobileSource.includes("loadAuxiliaryDocumentFile") &&
    auxiliaryMethodSource.includes("this.applyDocumentObjectUrl(field, blob") &&
    auxiliaryMethodSource.includes("validatePdfBlob") === false &&
    !auxiliaryMethodSource.includes("markDocumentLoaded"),
  "auxiliary downloads must remain outside the primary signing gate"
)
assert.ok(
  mobileSource.includes("showPreviousPreviewPage") &&
    mobileSource.includes("showNextPreviewPage") &&
    mobileSource.includes("previewPageNumber <= 1") &&
    mobileSource.includes("previewPageNumber >= this.previewPageCount") &&
    mobileSource.includes("openPrimaryDocumentViewer") &&
    mobileSource.includes("closePrimaryDocumentViewer") &&
    mobileSource.includes("zoomPreviewIn") &&
    mobileSource.includes("zoomPreviewOut") &&
    mobileSource.includes("pdf-viewer-overlay"),
  "the mobile image preview should keep pagination bounded and provide a zoomable fullscreen overlay"
)

console.log("sign package file gate tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
