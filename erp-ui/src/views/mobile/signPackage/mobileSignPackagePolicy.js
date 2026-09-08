const { dateTimeValue, formatSignDateTime } = require("../../../utils/signDateTime")
const { mobileErrorMessage } = require("../mobileErrorMessage")
const {
  hasCompleteFinalMetadata,
  requiresReadConfirmation: requiresDocumentRead
} = require("../../../utils/signPackageFileGate")

const EMPLOYEE_ACTION_STATUSES = Object.freeze([
  "pending_sign",
  "part_viewed",
  "pending_final_confirm"
])
const TERMINAL_PACKAGE_STATUSES = Object.freeze(["refused", "expired"])
const NUMERIC_BUSINESS_ID_PATTERN = /^[1-9]\d{0,18}$/
const REFUSAL_REASON_OPTIONS = Object.freeze([
  { value: "CONTRACT_CONTENT_DISPUTE", label: "合同内容与沟通不一致" },
  { value: "PERSONAL_INFORMATION_ERROR", label: "个人或岗位信息有误" },
  { value: "COMPANY_INFORMATION_ERROR", label: "公司或印章信息有误" },
  { value: "SIGNING_NOT_INTENDED", label: "本人不同意本次签约" },
  { value: "OTHER", label: "其他原因" }
])

function requestErrorMessage(error, fallback) {
  return mobileErrorMessage(error, fallback)
}

function normalizeNumericBusinessId(value) {
  const normalized = String(value == null ? "" : value).trim()
  return NUMERIC_BUSINESS_ID_PATTERN.test(normalized) ? normalized : ""
}

function deadlinePassed(signPackage, now = Date.now()) {
  const deadline = dateTimeValue(signPackage && signPackage.signDeadline)
  return Number.isFinite(deadline) && deadline <= now
}

function deadlinePolicyMissing(signPackage) {
  if (!signPackage || !EMPLOYEE_ACTION_STATUSES.includes(signPackage.status)) return false
  const days = Number(signPackage.deadlineDaysSnapshot)
  return !Number.isFinite(dateTimeValue(signPackage.signDeadline)) ||
    !String(signPackage.deadlinePolicySource || "").trim() ||
    !Number.isInteger(days) || days < 1
}

function freezeMutationEnvelope(packageId, payload) {
  const frozenPayload = Object.assign({}, payload)
  if (Array.isArray(payload.documentHashes)) {
    frozenPayload.documentHashes = Object.freeze(payload.documentHashes.map(item =>
      Object.freeze(Object.assign({}, item))
    ))
  }
  if (Array.isArray(payload.finalDocumentHashes)) {
    frozenPayload.finalDocumentHashes = Object.freeze(payload.finalDocumentHashes.map(item =>
      Object.freeze(Object.assign({}, item))
    ))
  }
  return Object.freeze({
    packageId,
    payload: Object.freeze(frozenPayload)
  })
}

function freezeDocumentReadMutationEnvelope(packageId, documentId, payload) {
  return Object.freeze({
    packageId,
    documentId,
    payload: Object.freeze(Object.assign({}, payload))
  })
}

function matchesDocumentReadMutation(mutation, packageId, documentId) {
  return !!(mutation &&
    String(mutation.packageId) === String(packageId) &&
    String(mutation.documentId) === String(documentId))
}

function refusalDocumentVersion(signPackage) {
  if (!signPackage) return ""
  return String(signPackage.finalDocumentVersion || signPackage.documentVersion || "").trim()
}

function validateRefusal({ canRefuse, reasonCode, reasonDetail, documentVersion }) {
  if (!canRefuse) return "当前签约包已不能拒签，请刷新后查看"
  if (!REFUSAL_REASON_OPTIONS.some(option => option.value === reasonCode)) {
    return "请选择拒签原因分类"
  }
  const detail = String(reasonDetail || "").trim()
  if (!detail) return "请填写拒签原因说明"
  if (detail.length > 500) return "拒签原因说明不能超过500个字符"
  if (!documentVersion) return "签约文档版本不完整，请刷新后重试"
  return ""
}

function createStatusTabs() {
  return [
    { value: "todo", label: "待处理", statuses: ["pending_sign", "pending_final_confirm"] },
    { value: "progress", label: "进行中", statuses: ["part_viewed", "pending_company"] },
    { value: "done", label: "已完成", statuses: ["signed"] },
    { value: "voided", label: "异常/撤回", statuses: ["refused", "expired", "voided", "failed"] }
  ]
}

function createProgressSteps() {
  return [
    { value: "pending_sign", label: "签约包已发送" },
    { value: "pending_company", label: "唯一一次签名" },
    { value: "pending_final_confirm", label: "最终文件已发送" },
    { value: "signed", label: "员工已确认" }
  ]
}

function statusTabValue(status) {
  if (["pending_sign", "pending_final_confirm"].includes(status)) return "todo"
  if (["part_viewed", "pending_company"].includes(status)) return "progress"
  if (status === "signed") return "done"
  if (["refused", "expired", "voided", "failed"].includes(status)) return "voided"
  return ""
}

function dataRequestRawStatus(item) {
  return String(item && (item.status || item.reviewStatus) || "").trim().toUpperCase()
}

function dataRequestStatusTab(item) {
  const status = dataRequestRawStatus(item)
  if (["PENDING_EMPLOYEE", "REJECTED"].includes(status)) return "todo"
  if (["SUBMITTED", "APPROVED", "PROFILE_SYNC_FAILED", "COMPLETED"].includes(status)) return "progress"
  if (status === "CANCELLED") return "voided"
  return "todo"
}

function dataRequestStatusLabel(item) {
  const status = dataRequestRawStatus(item)
  if (status === "REJECTED" && item && item.signatureCaptured) {
    return "待更正事实（唯一签名已保留）"
  }
  return {
    PENDING_EMPLOYEE: "待核对并签名",
    REJECTED: "待修改并重新提交",
    SUBMITTED: "待 HR 审核",
    APPROVED: "资料同步中",
    PROFILE_SYNC_FAILED: "等待 HR 处理",
    COMPLETED: "等待公司与印章",
    CANCELLED: "已取消"
  }[status] || "待处理"
}

function dataRequestChipClass(item) {
  const status = dataRequestRawStatus(item)
  if (status === "REJECTED") return "refused"
  if (["COMPLETED", "APPROVED", "SUBMITTED", "PROFILE_SYNC_FAILED"].includes(status)) {
    return "pending_company"
  }
  if (status === "CANCELLED") return "voided"
  return "pending_sign"
}

function dataRequestTitle(item) {
  return String(item && item.signingSequence || "").toUpperCase() === "SIGNATURE_FIRST"
    ? "入职签约包" : "入职合同资料核对"
}

function dataRequestSummary(item) {
  const facts = item && item.factSnapshot && typeof item.factSnapshot === "object"
    ? item.factSnapshot : {}
  const employee = String(facts.employeeName || "本人").trim()
  const post = String(facts.employeePost || "").trim()
  return post ? `${employee} · ${post}` : employee
}

function dataRequestProgressSummary(item) {
  const status = dataRequestRawStatus(item)
  if (status === "COMPLETED") return "唯一一次签名已完成；请等待 HR 选择公司与印章"
  if (["SUBMITTED", "APPROVED"].includes(status)) {
    return "事实和个人资料已提交，HR 核验后继续处理合同"
  }
  if (status === "PROFILE_SYNC_FAILED") return "已保留本次提交，请等待 HR 处理档案同步"
  if (status === "REJECTED") {
    return item && item.signatureCaptured
      ? "请按 HR 意见更正事实后重新提交；不会再次签名"
      : "请按 HR 意见修改后重新提交本任务"
  }
  return String(item && item.signingSequence || "").toUpperCase() === "SIGNATURE_FIRST"
    ? "核对签约包并完成唯一一次手写签名"
    : "补全签约所需的个人事实"
}

function packageDocuments(signPackage) {
  return signPackage && Array.isArray(signPackage.documents) ? signPackage.documents : []
}

function isCompanyFirstSequence(signPackage) {
  return !!signPackage && signPackage.signingSequence === "COMPANY_FIRST"
}

function isSignatureFirstEmployeeStage(signPackage) {
  if (!signPackage) return false
  if (String(signPackage.signingSequence || "").toUpperCase() !== "SIGNATURE_FIRST") return false
  return ["pending_sign", "part_viewed"].includes(String(signPackage.status || ""))
}

function isSignatureFirstWaitingCompany(signPackage) {
  return !!signPackage &&
    String(signPackage.signingSequence || "").toUpperCase() === "SIGNATURE_FIRST" &&
    String(signPackage.status || "").toLowerCase() === "pending_company"
}

function shouldUseFinalDocument(signPackage, document) {
  if (!signPackage || !document) return false
  if (signPackage.status === "pending_final_confirm") return true
  if (signPackage.signingSequence === "COMPANY_FIRST" &&
    ["pending_sign", "part_viewed"].includes(signPackage.status)) return true
  return signPackage.status === "signed" && !!document.finalFileAvailable
}

function createDocumentProgressSummary(signPackage) {
  const documents = packageDocuments(signPackage)
  const requiredDocuments = documents.filter(document => document.employeeSignRequired === "Y")
  const requiredReadDocuments = documents.filter(requiresDocumentRead)
  const companyFirst = isCompanyFirstSequence(signPackage)
  const requiredDocumentCount = companyFirst ? documents.length : requiredReadDocuments.length
  const requiredReadCount = companyFirst
    ? documents.filter(document => document.finalReadConfirmed === "Y").length
    : requiredReadDocuments.filter(document => document.readConfirmed === "Y").length
  const finalArchiveAvailableCount = documents.filter(
    document => document.finalFileAvailable === true
  ).length
  const signedFileAvailableCount = documents.filter(
    document => document.signedFileAvailable === true
  ).length
  const signedArchiveOrFileAvailableCount = documents.filter(document =>
    document.finalFileAvailable === true || document.signedFileAvailable === true
  ).length
  const finalReadConfirmedCount = documents.filter(
    document => document.finalReadConfirmed === "Y"
  ).length
  const status = signPackage && signPackage.status
  const fileListProgressCount = status === "pending_final_confirm"
    ? finalReadConfirmedCount
    : status === "signed" ? signedArchiveOrFileAvailableCount : requiredReadCount
  const fileListProgressTotal = ["pending_final_confirm", "signed"].includes(status)
    ? documents.length
    : requiredDocumentCount

  return {
    documents,
    requiredDocuments,
    requiredReadDocuments,
    requiredDocumentCount,
    requiredReadCount,
    finalArchiveAvailableCount,
    signedFileAvailableCount,
    signedArchiveOrFileAvailableCount,
    finalReadConfirmedCount,
    fileListProgressCount,
    fileListProgressTotal,
    allFinalDocumentsConfirmed: hasCompleteFinalMetadata(signPackage) &&
      documents.length > 0 &&
      documents.every(document => document.finalReadConfirmed === "Y")
  }
}

function documentPolicyLabel(signPackage, document) {
  const status = signPackage && signPackage.status
  const signatureFirst = signPackage && signPackage.signingSequence === "SIGNATURE_FIRST"
  if (document && document.employeeSignRequired === "Y" && signatureFirst &&
    ["pending_company", "pending_final_confirm"].includes(status)) {
    return "唯一一次签名已留存"
  }
  if (document && document.employeeSignRequired === "Y" && status === "signed") {
    return "签名已归档"
  }
  if (document && document.employeeSignRequired === "Y") return "需签署"
  if (requiresDocumentRead(document)) return "需阅读"
  return "只读文件"
}

function documentProgressLabel(signPackage, document) {
  const status = signPackage && signPackage.status
  if (status === "pending_final_confirm") {
    return document && document.finalReadConfirmed === "Y"
      ? "已打开待确认文件"
      : "待打开待确认文件"
  }
  if (status === "signed" && shouldUseFinalDocument(signPackage, document)) {
    return "最终文件已归档"
  }
  if (shouldUseFinalDocument(signPackage, document)) {
    return document && document.finalReadConfirmed === "Y" ? "已打开完整文件" : "待打开完整文件"
  }
  if (requiresDocumentRead(document)) {
    return document.readConfirmed === "Y" ? "已确认" : "未确认"
  }
  return "无需确认"
}

function documentLoadStateLabel(signPackage, document, loaded) {
  const kind = shouldUseFinalDocument(signPackage, document) ? "final" : "review"
  if (kind === "final" && signPackage && signPackage.status === "pending_final_confirm") {
    return document && document.finalReadConfirmed === "Y" ? "已打开" : "待打开"
  }
  return loaded ? "本次已加载" : "待加载"
}

function documentStateLabel(signPackage, document) {
  if (signPackage && signPackage.status === "pending_final_confirm" &&
    document && document.finalPdfHash) {
    return document.finalReadConfirmed === "Y" ? "已打开" : "待打开"
  }
  if (signPackage && signPackage.status === "signed" &&
    document && document.finalFileAvailable) {
    return "最终归档 · 已完成"
  }
  if (document && document.signed === "Y") return "已留签名"
  if (!requiresDocumentRead(document)) return "可查看"
  return document.readConfirmed === "Y" ? "已读" : "待读"
}

function readButtonLabel(options = {}) {
  const {
    document,
    readConfirmingDocumentId,
    readActionError,
    readRetryDocumentId,
    currentDocumentPrimaryLoaded,
    previewPageCount,
    currentPreviewLoadedPageCount
  } = options
  if (!document || document.readConfirmed === "Y") return "已阅读确认"
  if (String(readConfirmingDocumentId) === String(document.documentId)) return "确认中..."
  if (readActionError && String(readRetryDocumentId) === String(document.documentId)) {
    return "重试阅读确认"
  }
  if (currentDocumentPrimaryLoaded) return "阅读确认"
  if (previewPageCount > 0) {
    return `请翻阅全部 ${previewPageCount} 页（已查看 ${currentPreviewLoadedPageCount} 页）`
  }
  return "文件加载成功后可确认"
}

function summarizeDeadline(signPackage, now) {
  const deadline = dateTimeValue(signPackage && signPackage.signDeadline)
  if (!Number.isFinite(deadline)) return "签署截止：未设置"
  const formatted = formatSignDateTime(signPackage.signDeadline)
  if (signPackage.status === "expired") return `已于 ${formatted} 过期`
  if (signPackage.status === "pending_company") return `员工签署计时已暂停 · ${formatted}`
  if (["refused", "signed", "voided"].includes(signPackage.status)) return `原截止：${formatted}`
  const remaining = deadline - now
  if (remaining <= 0) return `已到期 · ${formatted}`
  const totalMinutes = Math.max(1, Math.ceil(remaining / 60000))
  const days = Math.floor(totalMinutes / 1440)
  const hours = Math.floor((totalMinutes % 1440) / 60)
  const minutes = totalMinutes % 60
  const countdown = days > 0
    ? `${days}天${hours}小时`
    : hours > 0 ? `${hours}小时${minutes}分钟` : `${minutes}分钟`
  return `剩余 ${countdown} · ${formatted}`
}

function isTerminalPackageStatus(status) {
  return TERMINAL_PACKAGE_STATUSES.includes(status)
}

function refusalReasonLabel(reasonCode) {
  if (!reasonCode) return "未记录"
  if (reasonCode === "SIGN_DEADLINE_ELAPSED") return "超过签署截止时间"
  const option = REFUSAL_REASON_OPTIONS.find(item => item.value === reasonCode)
  return option ? option.label : "其他已记录原因"
}

function resolutionStatusLabel(status) {
  return {
    OPEN: "经办人处理中",
    CLOSED: "已关闭",
    REISSUED: "已创建替代版本"
  }[status] || "待经办人确认"
}

function isProgressStepActive(currentStatus, stepStatus) {
  const order = {
    pending_sign: 1,
    part_viewed: 1,
    pending_company: 2,
    pending_final_confirm: 3,
    signed: 4
  }
  return (order[currentStatus] || 0) >= (order[stepStatus] || 0)
}

module.exports = {
  EMPLOYEE_ACTION_STATUSES,
  REFUSAL_REASON_OPTIONS,
  TERMINAL_PACKAGE_STATUSES,
  createProgressSteps,
  createStatusTabs,
  dataRequestChipClass,
  dataRequestProgressSummary,
  dataRequestRawStatus,
  dataRequestStatusLabel,
  dataRequestStatusTab,
  dataRequestSummary,
  dataRequestTitle,
  deadlinePassed,
  deadlinePolicyMissing,
  documentLoadStateLabel,
  documentPolicyLabel,
  documentProgressLabel,
  documentStateLabel,
  freezeDocumentReadMutationEnvelope,
  freezeMutationEnvelope,
  createDocumentProgressSummary,
  isCompanyFirstSequence,
  isProgressStepActive,
  isSignatureFirstEmployeeStage,
  isSignatureFirstWaitingCompany,
  isTerminalPackageStatus,
  matchesDocumentReadMutation,
  normalizeNumericBusinessId,
  packageDocuments,
  readButtonLabel,
  refusalReasonLabel,
  refusalDocumentVersion,
  requestErrorMessage,
  resolutionStatusLabel,
  shouldUseFinalDocument,
  statusTabValue,
  summarizeDeadline,
  validateRefusal
}
