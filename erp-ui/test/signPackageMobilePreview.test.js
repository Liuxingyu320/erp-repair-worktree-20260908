const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const componentPath = path.resolve(__dirname, "../src/views/mobile/signPackage/index.vue")
const componentSource = fs.readFileSync(componentPath, "utf8")
const fileGate = require("../src/utils/signPackageFileGate")
const mobileSignPackagePolicy = require("../src/views/mobile/signPackage/mobileSignPackagePolicy")

assert.ok(componentSource.includes("导出签章展示版") &&
  componentSource.includes("签章展示版用于查看签名和盖章位置，原最终归档不变"),
"mobile export UI should identify a display derivative and preserve the immutable archive wording")

function pngBlob(contentType = "image/png") {
  return new Blob([
    Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10])
  ], { type: contentType })
}

function pdfBlob() {
  return new Blob(["%PDF-1.7\n%%EOF\n"], { type: "application/pdf" })
}

function loadComponent() {
  const scriptMatch = componentSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "mobile sign package component should expose a script block")
  const script = scriptMatch[1]
    .replace(/^import(?:[\s\S]*?)\s+from\s+['"][^'"]+['"]\s*$/gm, "")
    .replace(/export default/, "module.exports =")

  const calls = {
    metadata: [],
    page: [],
    finalRead: [],
    finalExport: [],
    savedFiles: [],
    failFinalExport: false,
    primaryPdf: [],
    createdObjectUrls: [],
    revokedObjectUrls: []
  }
  let pageCount = 3
  let objectUrlSequence = 0
  let pageResponse = () => pngBlob()
  const noopPdf = () => Promise.resolve(pdfBlob())

  const sandbox = {
    module: { exports: {} },
    exports: {},
    confirmSignPackageDocumentRead: () => Promise.resolve({ data: {} }),
    confirmSignPackageFinalDocumentRead(packageId, documentId, data) {
      calls.finalRead.push([packageId, documentId, data])
      return Promise.resolve({
        data: {
          packageId,
          status: "pending_final_confirm",
          finalDocumentVersion: data.finalDocumentVersion,
          finalDocumentRootHash: "b".repeat(64),
          documents: [{
            documentId,
            documentName: "劳动合同",
            employeeSignRequired: "Y",
            readConfirmationRequired: "Y",
            finalPdfUrl: "/pending-final.pdf",
            finalPdfHash: data.finalPdfHash,
            finalReadConfirmed: "Y"
          }]
        }
      })
    },
    confirmMyFinalSignPackage: () => Promise.resolve({ data: {} }),
    downloadMySignPackageCertificate: noopPdf,
    downloadMySignPackageDocument(packageId, documentId) {
      calls.primaryPdf.push([packageId, documentId])
      return noopPdf()
    },
    downloadMySignPackageDocumentPreviewPage(packageId, documentId, pageNumber, kind) {
      calls.page.push([packageId, documentId, pageNumber, kind])
      return Promise.resolve(pageResponse(pageNumber, kind))
    },
    downloadMySignedSignPackageDocument: noopPdf,
    downloadMyFinalSignPackageDocumentExport(packageId, documentId) {
      calls.finalExport.push([packageId, documentId])
      if (calls.failFinalExport) return Promise.reject(new Error("network"))
      return noopPdf()
    },
    getMySignPackageDocumentPreview(packageId, documentId, kind) {
      calls.metadata.push([packageId, documentId, kind])
      return Promise.resolve({ data: { pageCount } })
    },
    getMySignPackage: () => Promise.resolve({ data: {} }),
    listMySignPackages: () => Promise.resolve({ rows: [] }),
    refuseMySignPackage: () => Promise.resolve({ data: {} }),
    signMySignPackage: () => Promise.resolve({ data: {} }),
    signDictionaryLabel: value => value,
    signPackageStatusLabel: value => value,
    require(request) {
      if (request === "@/mixins/todoBusinessFocus") {
        return { createTodoPersonalFocusMixin: () => ({}) }
      }
      if (request === "@/utils/signDateTime") {
        return {
          dateTimeValue: value => Number(value),
          formatSignDateTime: value => String(value == null ? "-" : value)
        }
      }
      if (request === "../mobileViewport") return {}
      if (request === "@/utils/signScenario") return { signScenarioLabel: value => value }
      if (request === "@/utils/signPackageFileGate") return fileGate
      if (request === "./mobileSignPackagePolicy") return mobileSignPackagePolicy
      throw new Error(`Unexpected require: ${request}`)
    },
    window: {
      crypto: { randomUUID: () => "generated-request-id" },
      addEventListener() {},
      removeEventListener() {},
      setInterval() { return 1 },
      clearInterval() {},
      devicePixelRatio: 1
    },
    URL: {
      createObjectURL(blob) {
        const url = `blob:preview-page-${++objectUrlSequence}`
        calls.createdObjectUrls.push({ url, type: blob.type, size: blob.size })
        return url
      },
      revokeObjectURL(url) {
        calls.revokedObjectUrls.push(url)
      }
    },
    Blob,
    Uint8Array,
    Promise,
    Object,
    Array,
    String,
    Number,
    Boolean,
    Math,
    RegExp,
    TypeError,
    Date,
    process: { env: { VUE_APP_BASE_API: "" } }
  }

  vm.runInNewContext(script, sandbox, { filename: componentPath })
  return {
    component: sandbox.module.exports,
    calls,
    setPageCount(value) { pageCount = value },
    setPageResponse(value) { pageResponse = value }
  }
}

function createContext(component, signPackage, document, calls) {
  return Object.assign(component.data(), component.methods, {
    selectedPackage: signPackage,
    activeDocument: document,
    fileRequestSequence: 7,
    $refs: {},
    $download: {
      saveAs(file, name) {
        if (!calls) return Promise.resolve()
        calls.savePending = {
          file,
          name,
          resolve() {
            calls.savedFiles.push({ file, name })
            calls.saveCompleted = true
          }
        }
        return new Promise(resolve => {
          calls.savePending.resolvePromise = resolve
        })
      }
    },
    $modal: {
      msgError(message) {
        if (calls) calls.error = message
      }
    },
    $set(target, key, value) { target[key] = value },
    $delete(target, key) { delete target[key] }
  })
}

function previewImageTarget(context) {
  return {
    dataset: {
      loadToken: context.primaryFrameLoadContext.token,
      objectUrl: context.primaryPreviewPageUrl
    }
  }
}

;(async () => {
  assert.ok(!componentSource.includes("formatDateTime(item.sentTime || item.createTime)"),
    "the mobile package list should not display send time")
  assert.ok(!componentSource.includes("<dt>发送</dt>"),
    "the mobile package detail should not display send time")
  assert.ok(componentSource.includes('class="document-preview-image"') &&
    !componentSource.includes("<iframe"),
  "the primary contract preview should be an image instead of an embedded PDF frame")
  assert.ok(componentSource.includes("pending-final-watermark") &&
    componentSource.includes("待员工最终确认"),
  "pending-final previews should carry a visible non-final watermark")
  assert.ok(componentSource.includes('!isSignatureFirstWaitingCompany() && selectedPackage.legalEntityNameSnapshot') &&
    componentSource.includes('!isSignatureFirstWaitingCompany() && selectedPackage.sealNameSnapshot'),
  "signature-first waiting-company details must not render an unsent company or seal")

  const harness = loadComponent()
  const document = {
    documentId: 101,
    documentName: "劳动合同",
    employeeSignRequired: "Y",
    readConfirmationRequired: "Y",
    reviewPdfHash: "review-hash-101"
  }
  const signPackage = {
    packageId: 21,
    status: "pending_sign",
    documentVersion: "review-v1",
    documents: [document]
  }
  const context = createContext(harness.component, signPackage, document)

  const preparedHarness = loadComponent()
  const preparedPackage = {
    packageId: 20,
    status: "pending_company",
    signingSequence: "SIGNATURE_FIRST",
    finalConfirmationStatus: "WAITING_COMPANY",
    documents: [document]
  }
  const preparedContext = createContext(preparedHarness.component, preparedPackage, document)
  assert.strictEqual(preparedContext.isSignatureFirstWaitingCompany(), true)
  const preparedLoads = await preparedContext.loadActiveDocumentFiles()
  assert.strictEqual(preparedLoads.length, 0,
    "signature-first waiting-company state must not start any employee file preview")
  assert.strictEqual(preparedHarness.calls.metadata.length, 0)
  assert.strictEqual(preparedHarness.calls.page.length, 0)

  assert.strictEqual(await context.validatePreviewImageBlob(pngBlob()), true,
    "an authenticated PNG preview response should pass its binary signature check")
  assert.strictEqual(await context.validatePreviewImageBlob(pngBlob("application/octet-stream")), true,
    "an authenticated octet-stream PNG should remain compatible after byte validation")
  assert.strictEqual(await context.validatePreviewImageBlob(pdfBlob()), false,
    "a PDF blob must not be accepted as a server-rendered preview page")

  const initialLoad = await context.loadPrimaryDocumentPreview(7, signPackage, document, "review")
  assert.strictEqual(initialLoad, true)
  assert.deepStrictEqual(harness.calls.metadata, [[21, 101, "review"]],
    "the preview should first request authenticated metadata for the selected version kind")
  assert.deepStrictEqual(harness.calls.page, [[21, 101, 1, "review"]],
    "metadata should be followed by the first authenticated PNG page request")
  assert.strictEqual(harness.calls.primaryPdf.length, 0,
    "the primary mobile preview must not download the original PDF blob")
  assert.deepStrictEqual(harness.calls.createdObjectUrls[0], {
    url: "blob:preview-page-1",
    type: "image/png",
    size: 8
  })
  assert.strictEqual(context.previewPageCount, 3)
  assert.strictEqual(context.previewPageNumber, 1)
  assert.strictEqual(context.primaryPreviewPageUrl, "blob:preview-page-1")
  assert.strictEqual(fileGate.isDocumentLoaded(
    context.loadedReviewDocumentKeys, signPackage, document, "review"), false,
  "downloading a PNG must not unlock signing before the current image loads")

  const firstImage = previewImageTarget(context)
  context.$refs.primaryDocumentFrame = firstImage
  assert.strictEqual(context.handlePrimaryDocumentFrameLoad({ currentTarget: firstImage }), true)
  assert.strictEqual(fileGate.isDocumentLoaded(
    context.loadedReviewDocumentKeys, signPackage, document, "review"), true,
  "opening the first successfully rendered page must mark the current document as opened")
  assert.strictEqual(context.previewPageViewedCount(signPackage, document, "review", 3), 1)

  assert.strictEqual(await context.showNextPreviewPage(), true)
  assert.deepStrictEqual(harness.calls.page[1], [21, 101, 2, "review"])
  assert.strictEqual(context.previewPageNumber, 2)
  assert.strictEqual(context.primaryPreviewPageUrl, "blob:preview-page-2")
  assert.ok(harness.calls.revokedObjectUrls.includes("blob:preview-page-1"),
    "changing pages should revoke the prior authenticated image URL")

  const secondImage = previewImageTarget(context)
  context.$refs.primaryDocumentFrame = secondImage
  assert.strictEqual(context.handlePrimaryDocumentFrameLoad({ currentTarget: firstImage }), false,
    "a stale image load from the previous page must be ignored")
  assert.strictEqual(context.handlePrimaryDocumentFrameError({ currentTarget: firstImage }), false,
    "a stale image error must not clear the current page or reading gate")
  assert.strictEqual(context.primaryPreviewPageUrl, "blob:preview-page-2")
  assert.strictEqual(context.handlePrimaryDocumentFrameLoad({ currentTarget: secondImage }), true)
  assert.strictEqual(fileGate.isDocumentLoaded(
    context.loadedReviewDocumentKeys, signPackage, document, "review"), true,
  "pagination remains optional after the document has been opened")

  assert.strictEqual(context.openPrimaryDocumentViewer(), true)
  assert.strictEqual(context.primaryViewerOpen, true,
    "open file should use the in-page fullscreen viewer")
  context.zoomPreviewIn()
  context.zoomPreviewIn()
  assert.strictEqual(context.previewZoom, 2,
    "the fullscreen viewer should provide explicit zoom for small mobile text")
  context.zoomPreviewOut()
  assert.strictEqual(context.previewZoom, 1.5)
  context.closePrimaryDocumentViewer()
  assert.strictEqual(context.primaryViewerOpen, false)
  assert.strictEqual(context.previewZoom, 1)

  assert.strictEqual(await context.showNextPreviewPage(), true)
  assert.strictEqual(context.previewPageNumber, 3)
  const thirdImage = previewImageTarget(context)
  context.$refs.primaryDocumentFrame = thirdImage
  assert.strictEqual(context.handlePrimaryDocumentFrameLoad({ currentTarget: thirdImage }), true)
  assert.strictEqual(fileGate.isDocumentLoaded(
    context.loadedReviewDocumentKeys, signPackage, document, "review"), true,
  "viewing additional pages must keep the opened-document gate satisfied")
  assert.strictEqual(context.previewPageViewedCount(signPackage, document, "review", 3), 3)
  const pageCallsAtEnd = harness.calls.page.length
  assert.strictEqual(await context.showNextPreviewPage(), false,
    "pagination should stop at the metadata page count")
  assert.strictEqual(harness.calls.page.length, pageCallsAtEnd)
  assert.strictEqual(await context.showPreviousPreviewPage(), true)
  assert.strictEqual(context.previewPageNumber, 2)

  const currentImage = previewImageTarget(context)
  context.$refs.primaryDocumentFrame = currentImage
  assert.strictEqual(context.handlePrimaryDocumentFrameError({ currentTarget: currentImage }), true)
  assert.strictEqual(context.primaryPreviewPageUrl, "")
  assert.strictEqual(fileGate.isDocumentLoaded(
    context.loadedReviewDocumentKeys, signPackage, document, "review"), false,
  "a current image error should close the reading gate")

  const finalHarness = loadComponent()
  const finalDocument = {
    documentId: 102,
    documentName: "劳动合同",
    employeeSignRequired: "Y",
    readConfirmationRequired: "Y",
    finalPdfUrl: "/pending-final.pdf",
    finalPdfHash: "a".repeat(64),
    finalReadConfirmed: "N",
    signed: "Y"
  }
  const finalPackage = {
    packageId: 22,
    status: "pending_final_confirm",
    signingSequence: "SIGNATURE_FIRST",
    finalDocumentVersion: "final-v2",
    finalDocumentRootHash: "b".repeat(64),
    documents: [finalDocument]
  }
  const finalContext = createContext(finalHarness.component, finalPackage, finalDocument)
  assert.strictEqual(finalContext.documentPolicyLabel(finalDocument), "唯一一次签名已留存",
    "signature-first final confirmation must not ask the employee to sign a second time")
  assert.strictEqual(finalContext.documentProgressLabel(finalDocument), "待打开待确认文件",
    "signature-first final confirmation must use the final-read state instead of the old review state")
  assert.strictEqual(await finalContext.loadPrimaryDocumentPreview(
    7, finalPackage, finalDocument, "final"), true)
  const finalImage = previewImageTarget(finalContext)
  finalContext.$refs.primaryDocumentFrame = finalImage
  assert.strictEqual(finalContext.handlePrimaryDocumentFrameLoad({ currentTarget: finalImage }), true)
  assert.strictEqual(fileGate.isDocumentLoaded(
    finalContext.loadedFinalDocumentKeys, finalPackage, finalDocument, "final"), true,
  "opening the first rendered page should mark a pending-final file as opened without forcing every page")
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.strictEqual(finalHarness.calls.finalRead.length, 1,
    "opening a pending-final file should write one server-side read event")
  assert.strictEqual(JSON.stringify(finalHarness.calls.finalRead[0]), JSON.stringify([22, 102, {
    finalDocumentVersion: "final-v2",
    finalPdfHash: "a".repeat(64),
    requestId: "generated-request-id"
  }]))
  assert.strictEqual(finalContext.activeDocument.finalReadConfirmed, "Y",
    "the returned server state should replace the client-only open marker")
  assert.strictEqual(finalContext.documentProgressLabel(finalContext.activeDocument), "已打开待确认文件")

  const companyFirstHarness = loadComponent()
  const companyFirstDocument = {
    documentId: 103,
    documentName: "劳动合同",
    employeeSignRequired: "Y",
    readConfirmationRequired: "Y",
    finalPdfHash: "c".repeat(64),
    finalReadConfirmed: "N"
  }
  const companyFirstPackage = {
    packageId: 23,
    status: "pending_sign",
    signingSequence: "COMPANY_FIRST",
    documentVersion: "review-v3",
    finalDocumentVersion: "final-v3",
    finalDocumentRootHash: "d".repeat(64),
    documents: [companyFirstDocument]
  }
  const companyFirstContext = createContext(companyFirstHarness.component,
    companyFirstPackage, companyFirstDocument)
  assert.strictEqual(companyFirstContext.shouldUseFinalDocument(companyFirstDocument), true,
    "company-first employees must open the pre-generated final candidate before signing")
  assert.strictEqual(companyFirstContext.documentPolicyLabel(companyFirstDocument), "需签署",
    "company-first pending-sign documents must retain their real signing requirement")
  assert.strictEqual(await companyFirstContext.loadPrimaryDocumentPreview(
    7, companyFirstPackage, companyFirstDocument, "final"), true)
  const companyFirstImage = previewImageTarget(companyFirstContext)
  companyFirstContext.$refs.primaryDocumentFrame = companyFirstImage
  assert.strictEqual(companyFirstContext.handlePrimaryDocumentFrameLoad({
    currentTarget: companyFirstImage
  }), true)
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.strictEqual(companyFirstHarness.calls.finalRead.length, 1,
    "company-first pending-sign preview must persist FINAL_DOCUMENT_OPENED")
  assert.strictEqual(JSON.stringify(companyFirstHarness.calls.finalRead[0]), JSON.stringify([
    23, 103, {
      finalDocumentVersion: "final-v3",
      finalPdfHash: "c".repeat(64),
      requestId: "generated-request-id"
    }
  ]))
  companyFirstContext.selectedPackage.status = "signed"
  companyFirstContext.activeDocument.signed = "Y"
  companyFirstContext.activeDocument.finalFileAvailable = true
  assert.strictEqual(companyFirstContext.documentPolicyLabel(companyFirstContext.activeDocument), "签名已归档",
    "a completed company-first package must not keep showing a signing requirement")

  const invalidMetadataHarness = loadComponent()
  invalidMetadataHarness.setPageCount(0)
  const invalidContext = createContext(invalidMetadataHarness.component, signPackage, document)
  await assert.rejects(
    invalidContext.loadPrimaryDocumentPreview(7, signPackage, document, "review"),
    /合同预览页数无效/,
    "invalid server preview metadata must fail closed"
  )
  assert.strictEqual(invalidMetadataHarness.calls.page.length, 0)

  const exportHarness = loadComponent()
  const exportPackage = {
    packageId: 24,
    packageNo: "SP/24\n",
    employeeNameSnapshot: "员工:甲",
    status: "signed",
    finalConfirmationStatus: "CONFIRMED"
  }
  const exportDocument = {
    documentId: 104,
    documentName: "劳动/合同",
    templateType: "ONBOARD_LABOR_CONTRACT",
    finalFileAvailable: true
  }
  const exportContext = createContext(exportHarness.component, exportPackage,
    exportDocument, exportHarness.calls)
  assert.strictEqual(exportContext.canExportFinalDocument(exportDocument), true,
    "a signed and confirmed document with an archive capability should expose export")
  assert.strictEqual(exportContext.canExportFinalDocument({
    ...exportDocument,
    templateType: "ONBOARD_COMMITMENT"
  }), false, "mobile commitment documents must not expose the labor display export")
  assert.strictEqual(exportContext.canExportFinalDocument({
    ...exportDocument,
    templateType: "ONBOARD_HANDBOOK_RECEIPT"
  }), false, "mobile handbook documents must not expose the labor display export")
  assert.strictEqual(exportContext.canExportFinalDocument({
    ...exportDocument,
    finalFileAvailable: false
  }), false, "mobile labor documents without a final archive must not expose export")
  exportContext.selectedPackage.finalConfirmationStatus = "PENDING"
  assert.strictEqual(exportContext.canExportFinalDocument(exportDocument), false,
    "mobile unconfirmed packages must not expose export")
  exportContext.selectedPackage.finalConfirmationStatus = "CONFIRMED"
  const firstExport = exportContext.exportFinalDocument(exportDocument)
  const duplicateExport = exportContext.exportFinalDocument(exportDocument)
  assert.strictEqual(await duplicateExport, false,
    "a second click for the same document must be ignored while export is in flight")
  await new Promise(resolve => {
    const waitForSave = () => exportHarness.calls.savePending ? resolve() : setTimeout(waitForSave, 0)
    waitForSave()
  })
  assert.strictEqual(exportContext.finalExportingDocumentId, "104",
    "export loading must remain active until the asynchronous save completes")
  exportHarness.calls.savePending.resolve()
  exportHarness.calls.savePending.resolvePromise()
  assert.strictEqual(await firstExport, true)
  assert.deepStrictEqual(exportHarness.calls.finalExport, [[24, 104]])
  assert.strictEqual(exportHarness.calls.savedFiles.length, 1)
  assert.strictEqual(exportHarness.calls.savedFiles[0].name,
    "SP_24-员工_甲-劳动_合同-签章展示版.pdf",
    "export file names should remove path, control, and line-break characters")

  exportHarness.calls.failFinalExport = true
  assert.strictEqual(await exportContext.exportFinalDocument(exportDocument), false,
    "a failed export should report a safe business error")
  assert.strictEqual(exportHarness.calls.error, "最终合同导出失败，请稍后重试")
  exportHarness.calls.failFinalExport = false
  exportHarness.calls.savePending = null
  const retryExport = exportContext.exportFinalDocument(exportDocument)
  await new Promise(resolve => {
    const waitForRetrySave = () => exportHarness.calls.savePending ? resolve() : setTimeout(waitForRetrySave, 0)
    waitForRetrySave()
  })
  exportHarness.calls.savePending.resolve()
  exportHarness.calls.savePending.resolvePromise()
  assert.strictEqual(await retryExport, true,
    "a failed export should be retryable after loading is released")

  console.log("mobile sign package PNG preview tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
