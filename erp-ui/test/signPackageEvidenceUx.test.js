const assert = require("assert")
const fs = require("fs")
const path = require("path")

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, "..", relativePath), "utf8")
}

const apiSource = source("src/api/oa/signPackage.js")
const desktopSource = source("src/views/oa/signPackage/index.vue")
const mobileSource = source("src/views/mobile/signPackage/index.vue")
const mobilePolicySource = source("src/views/mobile/signPackage/mobileSignPackagePolicy.js")
const dictionarySource = source("src/utils/signDictionary.js")

assert.ok(
  apiSource.includes("export function verifySignPackage") &&
    apiSource.includes("'/verify'") &&
    apiSource.includes("includeTechnical"),
  "HR should call the scoped business verification endpoint and opt in to technical evidence explicitly"
)

;[
  "文件完整，验真通过",
  "文件与签署记录不一致",
  "文件缺失，暂时无法验证",
  "历史合同，仅支持旧版验真"
].forEach(message => {
  assert.ok(desktopSource.includes(message), `desktop verification UI should contain fixed message: ${message}`)
})

assert.ok(
  desktopSource.includes("验证文件") &&
    desktopSource.includes("verifySignPackage") &&
    desktopSource.includes("verificationResult") &&
    desktopSource.includes("verificationMessage"),
  "HR detail should expose business-language file verification"
)
assert.ok(
  desktopSource.includes('v-if="canViewTechnicalEvidence"') &&
    desktopSource.includes("oa:signTask:technicalEvidence") &&
    desktopSource.includes("technicalEvidence"),
  "raw evidence should only render behind the technical evidence permission"
)
assert.ok(
  !desktopSource.includes('<el-table-column label="文件哈希" prop="documentHash"') &&
    !desktopSource.includes('<el-table-column label="事件哈希" prop="eventHash"'),
  "normal HR signing records should not render hash columns directly"
)

assert.ok(
  mobileSource.includes('this.signConfirmText !== "本人确认签署本签约包"'),
  "employee confirmation must be an exact phrase rather than a checkbox"
)
assert.ok(
    mobileSource.includes("documentVersion: current.documentVersion") &&
    mobileSource.includes("payload.documentHashes = this.requiredDocuments.map") &&
    mobileSource.includes("reviewPdfHash: document.reviewPdfHash") &&
    mobileSource.includes("payload.finalDocumentVersion = current.finalDocumentVersion") &&
    mobileSource.includes("payload.finalDocumentRootHash = current.finalDocumentRootHash") &&
    mobileSource.includes("payload.finalDocumentHashes = this.documents.map") &&
    mobileSource.includes("finalPdfHash: document.finalPdfHash") &&
    mobileSource.includes("requestId: this.signRequestId"),
  "employee signing payload should bind review evidence or the exact pre-generated final version, root, and per-document hashes"
)
assert.ok(
  mobileSource.includes('signRequestId: ""') &&
    mobileSource.includes("this.signRequestId = this.createRequestId()") &&
    mobileSource.includes("crypto.randomUUID"),
  "one UUID should be reused when an uncertain network result is retried"
)
assert.ok(
  mobileSource.includes("confirmSignPackageDocumentRead(") &&
    mobileSource.includes("documentVersion: current.documentVersion") &&
    mobileSource.includes("reviewPdfHash: currentDocument.reviewPdfHash"),
  "read confirmation should send the version and hash returned by the server"
)
assert.ok(
    mobileSource.includes("首次发送文件") &&
    mobileSource.includes("首次签名文件") &&
    mobileSource.includes("最终归档文件") &&
    mobileSource.includes("最终归档 · 已完成") &&
    mobileSource.includes("document.signedFileAvailable") &&
    mobileSource.includes("document.certificateAvailable") &&
    mobileSource.includes("document.finalFileAvailable") &&
    mobileSource.includes("downloadMyFinalSignPackageDocument") &&
    mobileSource.includes("downloadMySignedSignPackageDocument"),
  "mobile UI should distinguish evidence files through server capabilities rather than storage URLs"
)
assert.ok(
  mobileSource.includes("核对待确认文件") &&
    mobileSource.includes("待确认文件已打开并记录") &&
    mobileSource.includes('this.$modal.msgSuccess("本次文件阅读已记录")') &&
    !mobileSource.includes('this.$modal.msgSuccess("阅读确认已完成")') &&
    mobilePolicySource.includes('document.signed === "Y") return "已留签名"') &&
    !mobilePolicySource.includes('document.signed === "Y") return "已签"') &&
    mobilePolicySource.includes('document.finalReadConfirmed === "Y" ? "已打开" : "待打开"') &&
    !mobileSource.includes("<h2>确认最终合同</h2>") &&
    !mobilePolicySource.includes('document.finalReadConfirmed === "Y" ? "已确认最终合同" : "待确认最终合同"'),
  "pending-final UI must use pending/open semantics until the package is signed"
)
assert.ok(
  mobilePolicySource.includes("唯一一次签名已留存") &&
    mobileSource.includes("documentProgressLabel(document)") &&
    mobilePolicySource.includes("待打开待确认文件") &&
    mobilePolicySource.includes("已打开待确认文件"),
  "signature-first final confirmation must say the one signature is retained and track the final file open state"
)
assert.ok(
  mobileSource.includes("signPackage.initialSignedTime || signPackage.signatureSampleTime || signPackage.signedTime") &&
    desktopSource.includes("scope.row.initialSignedTime || scope.row.signatureSampleTime || scope.row.signedTime"),
  "signature-first onboarding should display its package-scoped signature sample time"
)
assert.ok(
  desktopSource.includes('SIGNATURE_SAMPLE: "本任务手写签名样本"'),
  "package verification should label the one-time package-level signature sample"
)
assert.ok(
  dictionarySource.includes("pending_final_confirm: '待确认文件'") &&
    dictionarySource.includes("PENDING_FINAL_CONFIRM: '待确认文件'") &&
    dictionarySource.includes("signed: '已完成'"),
  "status dictionaries must reserve completed semantics for signed packages"
)
assert.ok(
  mobileSource.includes('if (useFinalFile && signPackage.status === "signed")'),
  "pending-final preview must not proactively request the raw /file download"
)
assert.ok(
  !mobileSource.includes("document.signedPdfUrl") &&
    !mobileSource.includes("document.certificateFileUrl") &&
    !mobileSource.includes("document.finalPdfUrl"),
  "employee UI must not depend on leaked storage paths"
)
assert.ok(
  desktopSource.includes("阅读原文") &&
    desktopSource.includes("已签文件") &&
    desktopSource.includes("scope.row.reviewPdfUrl") &&
    desktopSource.includes("scope.row.signedPdfUrl") &&
    desktopSource.includes("downloadSignedSignPackageDocument"),
  "HR detail should provide explicit review and signed PDF actions"
)

console.log("sign package evidence UX tests passed")
