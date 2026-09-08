const assert = require("assert")

const policy = require("../src/views/mobile/signPackage/mobileSignPackagePolicy")

assert.deepStrictEqual(Array.from(policy.EMPLOYEE_ACTION_STATUSES), [
  "pending_sign",
  "part_viewed",
  "pending_final_confirm"
])
assert.deepStrictEqual(Array.from(policy.TERMINAL_PACKAGE_STATUSES), ["refused", "expired"])
assert.deepStrictEqual(
  policy.REFUSAL_REASON_OPTIONS.map(option => option.value),
  [
    "CONTRACT_CONTENT_DISPUTE",
    "PERSONAL_INFORMATION_ERROR",
    "COMPANY_INFORMATION_ERROR",
    "SIGNING_NOT_INTENDED",
    "OTHER"
  ]
)

assert.strictEqual(policy.normalizeNumericBusinessId(" 42 "), "42")
assert.strictEqual(policy.normalizeNumericBusinessId(9007199254740993n), "9007199254740993")
assert.strictEqual(policy.normalizeNumericBusinessId("0"), "")
assert.strictEqual(policy.normalizeNumericBusinessId("01"), "")
assert.strictEqual(policy.normalizeNumericBusinessId("12345678901234567890"), "")
assert.strictEqual(policy.normalizeNumericBusinessId("1e3"), "")

const deadline = "2026-07-31T08:00:00+08:00"
assert.strictEqual(policy.deadlinePassed({ signDeadline: deadline }, Date.parse(deadline) - 1), false)
assert.strictEqual(policy.deadlinePassed({ signDeadline: deadline }, Date.parse(deadline)), true)
assert.strictEqual(policy.deadlinePassed({ signDeadline: "" }, Date.parse(deadline)), false)

assert.strictEqual(policy.deadlinePolicyMissing({
  status: "pending_sign",
  signDeadline: deadline,
  deadlinePolicySource: "PLAN_VERSION",
  deadlineDaysSnapshot: 7
}), false)
assert.strictEqual(policy.deadlinePolicyMissing({
  status: "pending_sign",
  signDeadline: deadline,
  deadlinePolicySource: "",
  deadlineDaysSnapshot: 7
}), true)
assert.strictEqual(policy.deadlinePolicyMissing({
  status: "pending_company",
  signDeadline: "",
  deadlinePolicySource: "",
  deadlineDaysSnapshot: null
}), false, "the company stage should not be blocked by the paused employee deadline")

const originalPayload = {
  requestId: "sign-1",
  documentHashes: [{ documentId: "1", pdfHash: "ABC" }],
  finalDocumentHashes: [{ documentId: "2", pdfHash: "DEF" }]
}
const frozenMutation = policy.freezeMutationEnvelope("91", originalPayload)
originalPayload.documentHashes[0].pdfHash = "MUTATED"
originalPayload.documentHashes.push({ documentId: "3", pdfHash: "NEW" })
assert.strictEqual(Object.isFrozen(frozenMutation), true)
assert.strictEqual(Object.isFrozen(frozenMutation.payload), true)
assert.strictEqual(Object.isFrozen(frozenMutation.payload.documentHashes), true)
assert.strictEqual(Object.isFrozen(frozenMutation.payload.documentHashes[0]), true)
assert.deepStrictEqual(JSON.parse(JSON.stringify(frozenMutation)), {
  packageId: "91",
  payload: {
    requestId: "sign-1",
    documentHashes: [{ documentId: "1", pdfHash: "ABC" }],
    finalDocumentHashes: [{ documentId: "2", pdfHash: "DEF" }]
  }
}, "retries must retain a detached snapshot of every submitted file hash")

const readMutation = policy.freezeDocumentReadMutationEnvelope("91", "7", {
  requestId: "read-1",
  expectedVersion: 3
})
assert.strictEqual(policy.matchesDocumentReadMutation(readMutation, 91, 7), true)
assert.strictEqual(policy.matchesDocumentReadMutation(readMutation, 91, 8), false)
assert.strictEqual(Object.isFrozen(readMutation.payload), true)

assert.strictEqual(policy.refusalDocumentVersion({
  finalDocumentVersion: " final-v2 ",
  documentVersion: "initial-v1"
}), "final-v2")
assert.strictEqual(policy.refusalDocumentVersion({ documentVersion: " initial-v1 " }), "initial-v1")
assert.strictEqual(policy.refusalDocumentVersion(null), "")

assert.strictEqual(policy.validateRefusal({
  canRefuse: false,
  reasonCode: "OTHER",
  reasonDetail: "原因",
  documentVersion: "v1"
}), "当前签约包已不能拒签，请刷新后查看")
assert.strictEqual(policy.validateRefusal({
  canRefuse: true,
  reasonCode: "UNKNOWN",
  reasonDetail: "原因",
  documentVersion: "v1"
}), "请选择拒签原因分类")
assert.strictEqual(policy.validateRefusal({
  canRefuse: true,
  reasonCode: "OTHER",
  reasonDetail: " ",
  documentVersion: "v1"
}), "请填写拒签原因说明")
assert.strictEqual(policy.validateRefusal({
  canRefuse: true,
  reasonCode: "OTHER",
  reasonDetail: "a".repeat(501),
  documentVersion: "v1"
}), "拒签原因说明不能超过500个字符")
assert.strictEqual(policy.validateRefusal({
  canRefuse: true,
  reasonCode: "OTHER",
  reasonDetail: "原因",
  documentVersion: ""
}), "签约文档版本不完整，请刷新后重试")
assert.strictEqual(policy.validateRefusal({
  canRefuse: true,
  reasonCode: "OTHER",
  reasonDetail: " 原因 ",
  documentVersion: "v1"
}), "")

assert.strictEqual(policy.requestErrorMessage({
  response: { data: { msg: "服务端错误" } },
  message: "网络错误"
}, "兜底"), "服务端错误")
assert.strictEqual(policy.requestErrorMessage({ message: "网络错误" }, "兜底"), "网络错误")
assert.strictEqual(policy.requestErrorMessage(null, "兜底"), "兜底")
assert.strictEqual(policy.requestErrorMessage({
  response: { status: 401, data: { msg: "令牌不能为空" } }
}, "兜底"), "登录状态已失效，请重新登录")

assert.deepStrictEqual(policy.createStatusTabs().map(tab => tab.value), [
  "todo",
  "progress",
  "done",
  "voided"
])
assert.deepStrictEqual(policy.createProgressSteps().map(step => step.value), [
  "pending_sign",
  "pending_company",
  "pending_final_confirm",
  "signed"
])
assert.strictEqual(policy.statusTabValue("pending_final_confirm"), "todo")
assert.strictEqual(policy.statusTabValue("pending_company"), "progress")
assert.strictEqual(policy.statusTabValue("signed"), "done")
assert.strictEqual(policy.statusTabValue("failed"), "voided")
assert.strictEqual(policy.statusTabValue("unknown"), "")

assert.strictEqual(policy.dataRequestRawStatus({ reviewStatus: " submitted " }), "SUBMITTED")
assert.strictEqual(policy.dataRequestStatusTab({ status: "REJECTED" }), "todo")
assert.strictEqual(policy.dataRequestStatusTab({ status: "COMPLETED" }), "progress")
assert.strictEqual(policy.dataRequestStatusTab({ status: "CANCELLED" }), "voided")
assert.strictEqual(policy.dataRequestStatusLabel({
  status: "REJECTED",
  signatureCaptured: true
}), "待更正事实（唯一签名已保留）")
assert.strictEqual(policy.dataRequestStatusLabel({ status: "COMPLETED" }), "等待公司与印章")
assert.strictEqual(policy.dataRequestChipClass({ status: "PROFILE_SYNC_FAILED" }), "pending_company")
assert.strictEqual(policy.dataRequestChipClass({ status: "REJECTED" }), "refused")
assert.strictEqual(policy.dataRequestTitle({ signingSequence: "signature_first" }), "入职签约包")
assert.strictEqual(policy.dataRequestTitle({ signingSequence: "company_first" }), "入职合同资料核对")
assert.strictEqual(policy.dataRequestSummary({
  factSnapshot: { employeeName: " 张三 ", employeePost: " 店长 " }
}), "张三 · 店长")
assert.strictEqual(policy.dataRequestSummary({ factSnapshot: {} }), "本人")
assert.ok(policy.dataRequestProgressSummary({ status: "COMPLETED" }).includes("唯一一次签名已完成"))
assert.ok(policy.dataRequestProgressSummary({
  status: "REJECTED",
  signatureCaptured: true
}).includes("不会再次签名"))

assert.strictEqual(
  policy.summarizeDeadline(
    { status: "pending_sign", signDeadline: deadline },
    Date.parse("2026-07-31T06:30:00+08:00")
  ),
  "剩余 1小时30分钟 · 2026-07-31 08:00"
)
assert.strictEqual(
  policy.summarizeDeadline(
    { status: "pending_company", signDeadline: deadline },
    Date.parse("2026-07-31T09:00:00+08:00")
  ),
  "员工签署计时已暂停 · 2026-07-31 08:00"
)
assert.strictEqual(policy.summarizeDeadline({ status: "pending_sign", signDeadline: "" }, 0),
  "签署截止：未设置")
assert.strictEqual(policy.isTerminalPackageStatus("refused"), true)
assert.strictEqual(policy.isTerminalPackageStatus("signed"), false)
assert.strictEqual(policy.refusalReasonLabel("SIGN_DEADLINE_ELAPSED"), "超过签署截止时间")
assert.strictEqual(policy.refusalReasonLabel("OTHER"), "其他原因")
assert.strictEqual(policy.refusalReasonLabel("UNKNOWN"), "其他已记录原因")
assert.strictEqual(policy.resolutionStatusLabel("REISSUED"), "已创建替代版本")
assert.strictEqual(policy.resolutionStatusLabel("UNKNOWN"), "待经办人确认")
assert.strictEqual(policy.isProgressStepActive("pending_final_confirm", "pending_company"), true)
assert.strictEqual(policy.isProgressStepActive("pending_sign", "pending_company"), false)

const documentFixtures = [{
  documentId: "doc-sign",
  employeeSignRequired: "Y",
  readConfirmationRequired: "Y",
  readConfirmed: "Y",
  finalReadConfirmed: "N",
  finalFileAvailable: true,
  signedFileAvailable: false,
  finalPdfHash: "final-sign-hash"
}, {
  documentId: "doc-read",
  employeeSignRequired: "N",
  readConfirmationRequired: "Y",
  readConfirmed: "N",
  finalReadConfirmed: "Y",
  finalFileAvailable: false,
  signedFileAvailable: true,
  finalPdfHash: "final-read-hash"
}, {
  documentId: "doc-view",
  employeeSignRequired: "N",
  readConfirmationRequired: "N",
  readConfirmed: "N",
  finalReadConfirmed: "N",
  finalFileAvailable: false,
  signedFileAvailable: false,
  finalPdfHash: "final-view-hash"
}]
const signatureFirstPackage = {
  packageId: "pkg-policy",
  status: "pending_sign",
  signingSequence: "SIGNATURE_FIRST",
  finalDocumentVersion: "final-v1",
  finalDocumentRootHash: "final-root",
  documents: documentFixtures
}

assert.deepStrictEqual(policy.packageDocuments(null), [])
assert.deepStrictEqual(policy.packageDocuments({ documents: {} }), [])
assert.strictEqual(policy.packageDocuments(signatureFirstPackage), documentFixtures)
assert.strictEqual(policy.isCompanyFirstSequence(signatureFirstPackage), false)
assert.strictEqual(policy.isCompanyFirstSequence({
  signingSequence: "COMPANY_FIRST"
}), true)
assert.strictEqual(policy.isSignatureFirstEmployeeStage(signatureFirstPackage), true)
assert.strictEqual(policy.isSignatureFirstEmployeeStage({
  signingSequence: "signature_first",
  status: "part_viewed"
}), true)
assert.strictEqual(policy.isSignatureFirstEmployeeStage({
  signingSequence: "SIGNATURE_FIRST",
  status: "pending_company"
}), false)
assert.strictEqual(policy.isSignatureFirstWaitingCompany({
  signingSequence: "signature_first",
  status: "PENDING_COMPANY"
}), true)
assert.strictEqual(policy.isSignatureFirstWaitingCompany(signatureFirstPackage), false)

assert.strictEqual(policy.shouldUseFinalDocument({
  status: "pending_final_confirm"
}, documentFixtures[0]), true)
assert.strictEqual(policy.shouldUseFinalDocument({
  status: "pending_sign",
  signingSequence: "COMPANY_FIRST"
}, documentFixtures[0]), true)
assert.strictEqual(policy.shouldUseFinalDocument({
  status: "part_viewed",
  signingSequence: "COMPANY_FIRST"
}, documentFixtures[0]), true)
assert.strictEqual(policy.shouldUseFinalDocument({
  status: "signed"
}, documentFixtures[0]), true)
assert.strictEqual(policy.shouldUseFinalDocument({
  status: "signed"
}, documentFixtures[1]), false)
assert.strictEqual(policy.shouldUseFinalDocument({
  status: "refused",
  signingSequence: "COMPANY_FIRST"
}, documentFixtures[0]), false)
assert.strictEqual(policy.shouldUseFinalDocument(null, documentFixtures[0]), false)

const signatureProgress = policy.createDocumentProgressSummary(signatureFirstPackage)
assert.strictEqual(signatureProgress.documents, documentFixtures)
assert.deepStrictEqual(signatureProgress.requiredDocuments, [documentFixtures[0]])
assert.deepStrictEqual(signatureProgress.requiredReadDocuments, documentFixtures.slice(0, 2))
assert.strictEqual(signatureProgress.requiredDocumentCount, 2)
assert.strictEqual(signatureProgress.requiredReadCount, 1)
assert.strictEqual(signatureProgress.finalArchiveAvailableCount, 1)
assert.strictEqual(signatureProgress.signedFileAvailableCount, 1)
assert.strictEqual(signatureProgress.signedArchiveOrFileAvailableCount, 2)
assert.strictEqual(signatureProgress.finalReadConfirmedCount, 1)
assert.strictEqual(signatureProgress.fileListProgressCount, 1)
assert.strictEqual(signatureProgress.fileListProgressTotal, 2)
assert.strictEqual(signatureProgress.allFinalDocumentsConfirmed, false)

const companyFirstProgress = policy.createDocumentProgressSummary(Object.assign(
  {}, signatureFirstPackage, { signingSequence: "COMPANY_FIRST" }
))
assert.strictEqual(companyFirstProgress.requiredDocumentCount, 3)
assert.strictEqual(companyFirstProgress.requiredReadCount, 1,
  "company-first progress is based on opening every pre-generated final document")

const pendingFinalProgress = policy.createDocumentProgressSummary(Object.assign(
  {}, signatureFirstPackage, { status: "pending_final_confirm" }
))
assert.strictEqual(pendingFinalProgress.fileListProgressCount, 1)
assert.strictEqual(pendingFinalProgress.fileListProgressTotal, 3)

const signedProgress = policy.createDocumentProgressSummary(Object.assign(
  {}, signatureFirstPackage, { status: "signed" }
))
assert.strictEqual(signedProgress.fileListProgressCount, 2,
  "signed packages count either the final archive or the compatible signed-file capability")
assert.strictEqual(signedProgress.fileListProgressTotal, 3)

const confirmedDocuments = documentFixtures.map(document => Object.assign(
  {}, document, { finalReadConfirmed: "Y" }
))
assert.strictEqual(policy.createDocumentProgressSummary(Object.assign(
  {}, signatureFirstPackage, { documents: confirmedDocuments }
)).allFinalDocumentsConfirmed, true)
assert.strictEqual(policy.createDocumentProgressSummary(Object.assign(
  {}, signatureFirstPackage, { documents: [] }
)).allFinalDocumentsConfirmed, false)

assert.strictEqual(policy.documentPolicyLabel({
  status: "pending_company",
  signingSequence: "SIGNATURE_FIRST"
}, documentFixtures[0]), "唯一一次签名已留存")
assert.strictEqual(policy.documentPolicyLabel({
  status: "signed",
  signingSequence: "SIGNATURE_FIRST"
}, documentFixtures[0]), "签名已归档")
assert.strictEqual(policy.documentPolicyLabel(signatureFirstPackage, documentFixtures[0]), "需签署")
assert.strictEqual(policy.documentPolicyLabel(signatureFirstPackage, documentFixtures[1]), "需阅读")
assert.strictEqual(policy.documentPolicyLabel(signatureFirstPackage, documentFixtures[2]), "只读文件")

assert.strictEqual(policy.documentProgressLabel({
  status: "pending_final_confirm"
}, documentFixtures[0]), "待打开待确认文件")
assert.strictEqual(policy.documentProgressLabel({
  status: "pending_final_confirm"
}, documentFixtures[1]), "已打开待确认文件")
assert.strictEqual(policy.documentProgressLabel({
  status: "signed"
}, documentFixtures[0]), "最终文件已归档")
assert.strictEqual(policy.documentProgressLabel({
  status: "pending_sign",
  signingSequence: "COMPANY_FIRST"
}, Object.assign({}, documentFixtures[0], {
  finalReadConfirmed: "Y"
})), "已打开完整文件")
assert.strictEqual(policy.documentProgressLabel(signatureFirstPackage, documentFixtures[0]), "已确认")
assert.strictEqual(policy.documentProgressLabel(signatureFirstPackage, documentFixtures[1]), "未确认")
assert.strictEqual(policy.documentProgressLabel(signatureFirstPackage, documentFixtures[2]), "无需确认")

assert.strictEqual(policy.documentLoadStateLabel({
  status: "pending_final_confirm"
}, documentFixtures[0], true), "待打开")
assert.strictEqual(policy.documentLoadStateLabel({
  status: "pending_final_confirm"
}, documentFixtures[1], false), "已打开")
assert.strictEqual(policy.documentLoadStateLabel(signatureFirstPackage, documentFixtures[0], true), "本次已加载")
assert.strictEqual(policy.documentLoadStateLabel(signatureFirstPackage, documentFixtures[0], false), "待加载")

assert.strictEqual(policy.documentStateLabel({
  status: "pending_final_confirm"
}, documentFixtures[0]), "待打开")
assert.strictEqual(policy.documentStateLabel({
  status: "signed"
}, documentFixtures[0]), "最终归档 · 已完成")
assert.strictEqual(policy.documentStateLabel(signatureFirstPackage, Object.assign(
  {}, documentFixtures[0], { signed: "Y" }
)), "已留签名")
assert.strictEqual(policy.documentStateLabel(signatureFirstPackage, documentFixtures[2]), "可查看")
assert.strictEqual(policy.documentStateLabel(signatureFirstPackage, documentFixtures[0]), "已读")
assert.strictEqual(policy.documentStateLabel(signatureFirstPackage, documentFixtures[1]), "待读")

assert.strictEqual(policy.readButtonLabel({ document: null }), "已阅读确认")
assert.strictEqual(policy.readButtonLabel({
  document: Object.assign({}, documentFixtures[0], { readConfirmed: "Y" })
}), "已阅读确认")
assert.strictEqual(policy.readButtonLabel({
  document: documentFixtures[1],
  readConfirmingDocumentId: "doc-read"
}), "确认中...")
assert.strictEqual(policy.readButtonLabel({
  document: documentFixtures[1],
  readActionError: "network",
  readRetryDocumentId: "doc-read"
}), "重试阅读确认")
assert.strictEqual(policy.readButtonLabel({
  document: documentFixtures[1],
  currentDocumentPrimaryLoaded: true
}), "阅读确认")
assert.strictEqual(policy.readButtonLabel({
  document: documentFixtures[1],
  previewPageCount: 5,
  currentPreviewLoadedPageCount: 3
}), "请翻阅全部 5 页（已查看 3 页）")
assert.strictEqual(policy.readButtonLabel({
  document: documentFixtures[1]
}), "文件加载成功后可确认")

console.log("mobile sign package policy tests passed")
