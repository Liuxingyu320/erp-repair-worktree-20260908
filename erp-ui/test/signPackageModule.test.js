const assert = require("assert")
const fs = require("fs")
const path = require("path")

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, "..", relativePath), "utf8")
}

const apiSource = source("src/api/oa/signPackage.js")
const desktopSource = source("src/views/oa/signPackage/index.vue")
const companySealSource = source("src/views/oa/signPackage/CompanySealManagement.vue")
const mobileSource = source("src/views/mobile/signPackage/index.vue")
const mobilePolicySource = source("src/views/mobile/signPackage/mobileSignPackagePolicy.js")
const mobileRouteSource = source("src/views/mobile/mobileRouteDefinitions.js")
const { MOBILE_ROUTES } = require("../src/views/mobile/mobileNavigation")
const signTemplateCatalog = require("../src/views/oa/signPackage/signTemplateCatalog")
const mobileSignPackagePolicy = require("../src/views/mobile/signPackage/mobileSignPackagePolicy")

function exportFunctionSource(fileSource, functionName) {
  const start = fileSource.indexOf(`export function ${functionName}`)
  assert.ok(start >= 0, `${functionName} should be exported`)
  const next = fileSource.indexOf("\nexport function ", start + 1)
  return fileSource.slice(start, next < 0 ? fileSource.length : next)
}

function exportFunctionSource(fileSource, functionName) {
  const start = fileSource.indexOf(`export function ${functionName}`)
  assert.ok(start >= 0, `${functionName} should be exported`)
  const next = fileSource.indexOf("\nexport function ", start + 1)
  return fileSource.slice(start, next < 0 ? fileSource.length : next)
}

const apiContracts = [
  ["listSignPackages", "url: '/oa/signPackage/list'", "method: 'get'"],
  ["getSignPackage", "url: '/oa/signPackage/' + packageId", "method: 'get'"],
  ["verifySignPackage", "url: '/oa/signPackage/' + packageId + '/verify'", "method: 'get'"],
  ["createSignPackage", "url: '/oa/signPackage'", "method: 'post'"],
  ["sendSignPackage", "url: '/oa/signPackage/' + packageId + '/send'", "method: 'post'"],
  ["voidSignPackage", "url: '/oa/signPackage/' + packageId + '/void'", "method: 'post'"],
  ["listSignTemplates", "url: '/oa/signPackage/template/list'", "method: 'get'"],
  ["previewSignTemplateFile", "url: '/oa/signPackage/template/' + templateId + '/preview'", "responseType: 'blob'"],
  ["downloadSignTemplateFile", "url: '/oa/signPackage/template/' + templateId + '/file'", "responseType: 'blob'"],
  ["listSignTemplateTypes", "url: '/oa/signPackage/template/types'", "method: 'get'"],
  ["saveSignTemplate", "url: '/oa/signPackage/template'", "method: 'post'"],
  ["listSignPlans", "url: '/oa/signPackage/plan/list'", "method: 'get'"],
  ["getSignPlan", "url: '/oa/signPackage/plan/' + planId", "method: 'get'"],
  ["saveSignPlan", "url: '/oa/signPackage/plan'", "method: 'post'"],
  ["updateSignPlan", "url: '/oa/signPackage/plan'", "method: 'put'"],
  ["publishSignPlan", "url: '/oa/signPackage/plan/' + planId + '/publish'", "method: 'post'"],
  ["updateSignPackage", "url: '/oa/signPackage/' + packageId", "method: 'put'"],
  ["previewSignPackageBatch", "url: '/oa/signPackage/batch/preview'", "method: 'post'"],
  ["createSignPackageDrafts", "url: '/oa/signPackage/batch/createDrafts'", "method: 'post'"],
  ["listMySignPackages", "url: '/oa/signPackage/mobile/list'", "method: 'get'"],
  ["getMySignPackage", "url: '/oa/signPackage/mobile/' + packageId", "method: 'get'"],
  ["confirmSignPackageDocumentRead", "url: '/oa/signPackage/mobile/' + packageId + '/read/' + documentId", "method: 'post'"],
  ["confirmSignPackageFinalDocumentRead", "url: '/oa/signPackage/mobile/' + packageId + '/final-read/' + documentId", "method: 'post'"],
  ["signMySignPackage", "url: '/oa/signPackage/mobile/' + packageId + '/sign'", "method: 'post'"],
  ["refuseMySignPackage", "url: '/oa/signPackage/mobile/' + packageId + '/refuse'", "method: 'post'"],
  ["downloadMySignPackageDocument", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/file'", "responseType: 'blob'"],
  ["getMySignPackageDocumentPreview", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/preview'", "params: { kind: kind || 'review' }"],
  ["downloadMySignPackageDocumentPreviewPage", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/preview/' + pageNumber", "responseType: 'blob'"],
  ["downloadMySignedSignPackageDocument", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/signed-file'", "responseType: 'blob'"],
  ["downloadMyFinalSignPackageDocumentExport", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/final-file/export'", "responseType: 'blob'"],
  ["downloadMySignPackageCertificate", "url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/certificate'", "responseType: 'blob'"],
  ["downloadSignPackageDocument", "url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/file'", "responseType: 'blob'"],
  ["downloadSignedSignPackageDocument", "url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/signed-file'", "responseType: 'blob'"],
  ["downloadFinalSignPackageDocumentExport", "url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/final-file/export'", "responseType: 'blob'"],
  ["downloadSignPackageCertificate", "url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/certificate'", "responseType: 'blob'"]
]

for (const [fnName, urlSnippet, methodSnippet] of apiContracts) {
  assert.ok(apiSource.includes(`export function ${fnName}`), `${fnName} should be exported by sign package API`)
  assert.ok(apiSource.includes(urlSnippet), `${fnName} should call ${urlSnippet}`)
  assert.ok(apiSource.includes(methodSnippet), `${fnName} should use ${methodSnippet}`)
}

assert.ok(apiSource.includes("const PDF_OPERATION_TIMEOUT = 300000"),
  "synchronous PDF writes should have a bounded five-minute client timeout")
for (const functionName of [
  "sendSignPackage",
  "finalizeSignPackage",
  "signMySignPackage",
  "confirmMyFinalSignPackage"
]) {
  assert.ok(exportFunctionSource(apiSource, functionName).includes("timeout: PDF_OPERATION_TIMEOUT"),
    `${functionName} should allow synchronous PDF rendering to finish`)
}
for (const functionName of [
  "createSignPackage",
  "updateSignPackage",
  "createSignPackageDrafts",
  "confirmSignPackageFinalDocumentRead"
]) {
  assert.ok(!exportFunctionSource(apiSource, functionName).includes("timeout: PDF_OPERATION_TIMEOUT"),
    `${functionName} should retain the ordinary shared request timeout`)
}

const previewMetadataApiStart = apiSource.indexOf("export function getMySignPackageDocumentPreview")
const previewPageApiStart = apiSource.indexOf("export function downloadMySignPackageDocumentPreviewPage")
const previewMetadataApiSource = apiSource.slice(previewMetadataApiStart, previewPageApiStart)
const previewPageApiSource = apiSource.slice(
  previewPageApiStart,
  apiSource.indexOf("export function downloadMySignedSignPackageDocument", previewPageApiStart)
)
assert.ok(
  previewMetadataApiSource.includes("return request({") &&
    previewMetadataApiSource.includes("method: 'get'") &&
    previewMetadataApiSource.includes("params: { kind: kind || 'review' }") &&
    !previewMetadataApiSource.includes("responseType: 'blob'"),
  "preview metadata API should return authenticated JSON for the requested review/final kind"
)
assert.ok(
  previewPageApiSource.includes("return request({") &&
    previewPageApiSource.includes("method: 'get'") &&
    previewPageApiSource.includes("params: { kind: kind || 'review' }") &&
    previewPageApiSource.includes("responseType: 'blob'"),
  "preview page API should download an authenticated binary PNG for the requested page and kind"
)

assert.strictEqual(MOBILE_ROUTES.signPackage, "/mobile/sign-package", "mobile sign package route should be stable")
assert.ok(
  mobileRouteSource.includes("path: '/mobile/sign-package'") &&
    mobileRouteSource.includes("import('@/views/mobile/signPackage/index')"),
  "mobile route definitions should register the employee sign package page"
)
assert.ok(
  mobileRouteSource.includes("{ label: '我的签约', icon: 'document', path: '/mobile/sign-package' }"),
  "mobile mine actions should expose the employee signing package entry"
)
assert.ok(
  mobileRouteSource.includes("path: '/mobile/onboard-data'") &&
    mobileRouteSource.includes("redirect: route => ({ path: '/mobile/sign-package', query: route.query })") &&
    !mobileRouteSource.includes("{ label: '合同资料补全', icon: 'user', path: '/mobile/onboard-data' }"),
  "legacy onboarding-data links should redirect into the single My Signings entry"
)
assert.ok(
  !mobileRouteSource.includes("{ label: '劳动合同', icon: 'document', path: '/mobile/contract' }"),
  "mobile mine actions should not expose the old labor contract signing entry"
)

;[
  "oa:signPackage:query",
  "oa:signPackage:add",
  "oa:signPackage:send",
  "oa:signPackage:void",
  "oa:signPackage:template"
].forEach(permission => {
  assert.ok(
    desktopSource.includes(`v-hasPermi="['${permission}']"`),
    `desktop sign package view should keep ${permission} controls behind v-hasPermi`
  )
})

assert.ok(
  desktopSource.includes('handleDetail(scope.row)" v-hasPermi="[\'oa:signPackage:query\']"'),
  "desktop detail action should use the same query permission required by the backend detail endpoint"
)

assert.ok(
  desktopSource.includes("模板管理") &&
    desktopSource.includes("签约方案") &&
    desktopSource.includes("签约包") &&
    desktopSource.includes("签约合同"),
  "desktop sign package view should separate packages, plans, templates, and contracts"
)
assert.ok(
  desktopSource.includes("批量按岗位生成") &&
    desktopSource.includes("batchPreviewRows") &&
    desktopSource.includes("missingFields") &&
    desktopSource.includes("handleEditPackage"),
  "desktop sign package view should support plan-based batch draft creation and draft editing"
)
assert.ok(
  desktopSource.includes("VUE_APP_SIGN_EMERGENCY_CREATE_ENABLED") &&
    desktopSource.includes('v-if="emergencyCreateEnabled"') &&
    desktopSource.includes("应急新建签约包") &&
    desktopSource.includes("应急批量建包") &&
    desktopSource.includes("非生命周期任务来源") &&
    desktopSource.includes("emergencyReason: this.batchForm.emergencyReason"),
  "free and batch package creation should be hidden by default and require an audited emergency reason"
)
assert.ok(
  desktopSource.includes("packageMatchName") &&
    desktopSource.includes("employmentType") &&
    desktopSource.includes("socialType") &&
    desktopSource.includes("postLevelSnapshot") &&
    desktopSource.includes("entryDate") &&
    desktopSource.includes("contractStartDate") &&
    desktopSource.includes("contractEndDate") &&
    desktopSource.includes("匹配结果") &&
    desktopSource.includes("合同/社保") &&
    desktopSource.includes("入职/合同日期"),
  "batch preview table should show automatic package match and auto-filled contract readiness fields"
)
assert.ok(
  desktopSource.includes('row.skipReason === "资料不完整"') &&
    desktopSource.includes('row.skipReason = ""'),
  "batch preview row refresh should clear incomplete skip reason after required fields are completed"
)
assert.ok(
  desktopSource.includes("batchPlanOptions") &&
    desktopSource.includes('listSignPlans({ status: "0" })'),
  "batch draft creation should use an independent enabled-plan option list"
)
assert.ok(
  desktopSource.includes('handleAddPlan" v-hasPermi="[\'oa:signPackage:template\']"') &&
    desktopSource.includes('handleEditPlan(scope.row)" v-hasPermi="[\'oa:signPackage:template\']"') &&
    desktopSource.includes('submitPlan" v-hasPermi="[\'oa:signPackage:template\']"'),
  "sign plan maintenance controls should align with template-list permission"
)
assert.ok(
  !desktopSource.includes("批量直接发送"),
  "desktop sign package view must not expose batch direct send wording or flow"
)
assert.ok(
    desktopSource.includes("templateTypeOptions") &&
    desktopSource.includes("requiredPlaceholders") &&
    [
      "ONBOARD_LABOR_CONTRACT",
      "ONBOARD_HANDBOOK",
      "ONBOARD_OFFER_NOTICE",
      "ONBOARD_CONFIDENTIAL_NONCOMPETE"
    ].every(code => signTemplateCatalog.DEFAULT_TEMPLATE_TYPE_OPTIONS
      .some(option => option.code === code)) &&
    desktopSource.includes("内部归档材料不会出现在员工端"),
  "desktop template management should be type-based and explain placeholder validation"
)
assert.ok(
  desktopSource.includes("在线预览") &&
    desktopSource.includes("下载") &&
    desktopSource.includes("handlePreviewTemplate(scope.row)") &&
    desktopSource.includes("handleDownloadTemplate(scope.row)") &&
    desktopSource.includes("this.openBlobFile(") &&
    desktopSource.includes("previewSignTemplateFile(row.templateId)") &&
    desktopSource.includes("validatePdfBlob(blob)") &&
    desktopSource.includes("downloadSignTemplateFile(row.templateId)") &&
    desktopSource.includes("blobValidate(blob)") &&
    desktopSource.includes("this.$download.printErrMsg(blob)") &&
    desktopSource.includes("this.$download.saveAs(fileBlob, this.templateDownloadFileName(row))"),
  "desktop template management should validate authenticated PDF previews and download blobs before opening or saving"
)
assert.ok(
  !desktopSource.includes(':href="normalizeFileUrl(scope.row.fileUrl)"') &&
    !desktopSource.includes("normalizeFileUrl(url)"),
  "desktop template file actions should not expose raw storage URLs"
)
assert.ok(
  [
    "RENEWAL_LABOR_CONTRACT",
    "RENEWAL_SERVICE_CONTRACT",
    "RENEWAL_SALARY_CONFIRM"
  ].every(code => signTemplateCatalog.DEFAULT_TEMPLATE_TYPE_OPTIONS
    .some(option => option.code === code)),
  "desktop template fallback catalog should expose dedicated renewal files"
)
assert.ok(
  desktopSource.includes('v-for="template in filteredPlanTemplateOptions"') &&
    desktopSource.includes("prunePlanTemplateSelection") &&
    desktopSource.includes("仅显示当前场景可绑定的模板"),
  "plan maintenance should filter templates by scenario and remove stale incompatible selections"
)
assert.ok(
  desktopSource.includes('v-model="planForm.reminderEnabled"') &&
    desktopSource.includes('v-model="planForm.reminderDaysBefore"') &&
    desktopSource.includes('JSON.stringify({ daysBefore: reminderDays })') &&
    desktopSource.includes('payload.reminderPolicyJson = payload.reminderEnabled') &&
    desktopSource.includes('空策略不会推断默认提醒') &&
    desktopSource.includes('reminderDayOptions()') &&
    desktopSource.includes('Math.min(30, deadlineDays)') &&
    desktopSource.includes('提前提醒天数不能超过签署期限'),
  "plan maintenance should persist only explicit frozen offsets within the signing deadline"
)
assert.ok(
  desktopSource.includes("发布方案版本前检查") &&
    desktopSource.includes("publishBlockingReasons") &&
    desktopSource.includes("publishSignPlan(this.publishDetail.planId)") &&
    desktopSource.includes("公司确定方式") &&
    desktopSource.includes("首次签名后按部门识别") &&
    desktopSource.includes('label="适用范围">全部签约组织') &&
    desktopSource.includes("HR 统一方案库，适用于全部签约组织") &&
    desktopSource.includes("delete payload.shopDeptId") &&
    desktopSource.includes("员工可见 {{ publishSummary.employeeVisible }}") &&
    desktopSource.includes("服务端还会校验模板场景、文件哈希、必填占位符"),
  "plan publication should show a readable scope/file summary before the immutable server publish"
)
assert.ok(
  desktopSource.indexOf("<sign-package-exception-panel") >
    desktopSource.indexOf('<el-drawer title="签约包详情"') &&
    !desktopSource.slice(
      desktopSource.indexOf('<el-dialog title="发布方案版本前检查"'),
      desktopSource.indexOf('<el-dialog :title="templateTitle"')
    ).includes("<sign-package-exception-panel"),
  "plan publishing dialog must not render package exception state from a null detail object"
)
assert.ok(
  desktopSource.includes("previousContractEndDate") &&
    desktopSource.includes("previousRenewalCount") &&
    desktopSource.includes("previousLegalEntityIdSnapshot") &&
    desktopSource.includes("续签次数必须在原次数基础上加1") === false &&
    desktopSource.includes("必须等于原续签次数 + 1"),
  "renewal package editing should expose the frozen previous contract and count fields"
)
assert.ok(
  desktopSource.includes("employeeAddressSnapshot") &&
    desktopSource.includes("probationStartDate") &&
    desktopSource.includes("probationEndDate") &&
    desktopSource.includes("servicePersonType") &&
    desktopSource.includes("insuranceType") &&
    desktopSource.includes("fieldAllowance") &&
    desktopSource.includes("综合驻外补贴"),
  "desktop sign package creation should collect the fields required by the onboarding contract files"
)
assert.ok(
  desktopSource.includes("user.postName || user.postNames || \"\""),
  "desktop employee selection should populate post from the system user list response"
)
assert.deepStrictEqual(
  signTemplateCatalog.DEFAULT_TEMPLATE_TYPE_OPTIONS
    .find(option => option.code === "ONBOARD_SALARY_CONFIRM")
    .requiredPlaceholders,
  [
    "employeeName",
    "employeeIdCard",
    "baseSalary",
    "postSalary",
    "fieldAllowance",
    "performanceSalary",
    "salaryTotal",
    "signDate"
  ],
  "salary confirmation placeholders should match the imported salary structure files"
)
assert.ok(
  desktopSource.includes('socialType: "有社保"') &&
    desktopSource.includes('salaryVersion: "B"') &&
    desktopSource.includes('handlePlanSocialTypeChange') &&
    desktopSource.includes('syncOnboardSalaryVersion') &&
    desktopSource.includes('有社保 B 版，无社保 A 版') &&
    !desktopSource.includes('薪酬版本由 HR 人工选择'),
  "onboarding salary confirmation must be derived from social insurance type instead of selected directly"
)
assert.ok(
  desktopSource.includes("matchTemplates") &&
    desktopSource.includes("文件清单") &&
    desktopSource.includes("handleSendPackage"),
  "desktop sign package creation should expose matched document list before sending"
)
assert.ok(
  desktopSource.includes("activeTab(newValue)") &&
    desktopSource.includes('newValue === "template"') &&
    !desktopSource.includes("this.getTemplates()\n    this.loadEmployees()"),
  "desktop sign package page should lazy-load template management data instead of requiring template permission on initial package page load"
)

assert.ok(
  mobileSource.includes("listMySignPackages") &&
    mobileSource.includes("getMySignPackage") &&
    mobileSource.includes("getMySignPackageDocumentPreview") &&
    mobileSource.includes("downloadMySignPackageDocumentPreviewPage") &&
    mobileSource.includes("signMySignPackage"),
  "mobile sign package page should use authenticated mobile signing and server-rendered preview APIs"
)
assert.ok(
  !mobileSource.includes("formatDateTime(item.sentTime || item.createTime)") &&
    !mobileSource.includes("<dt>发送</dt>"),
  "mobile package list and detail should not expose either visible send-time field"
)
assert.ok(
  desktopSource.includes("signPackageStatusLabel") &&
    mobileSource.includes("signPackageStatusLabel") &&
    source("src/utils/signDictionary.js").includes("SIGN_PACKAGE_STATUS_LABELS") &&
    source("src/utils/signDictionary.js").includes("refused: '已拒签'") &&
    source("src/utils/signDictionary.js").includes("expired: '已过期'"),
  "desktop and mobile package views should share one status dictionary including terminal states"
)
assert.ok(
  source("src/utils/signDictionary.js").includes("SOCIAL_INSURED: 'B'") &&
    source("src/utils/signDictionary.js").includes("SOCIAL_UNINSURED: 'A'") &&
    source("src/utils/signDictionary.js").includes("NO_SOCIAL: 'A'"),
  "employee signing pages should display insured as B and uninsured as A"
)
assert.ok(
  desktopSource.includes('SIGN_PACKAGE_STATUS_LABELS') &&
    desktopSource.includes('SignPackageRecordPanel') &&
    desktopSource.includes('SignPackageExceptionPanel') &&
    source('src/utils/signDictionary.js').includes('SIGN_PACKAGE_EVENT_LABELS'),
  "desktop status filters, signing records, exception evidence and event labels should use shared components/dictionaries"
)
assert.ok(
  mobileSource.includes("statusTabs") &&
    mobileSource.includes("activeStatus") &&
    mobileSource.includes("filteredPackages") &&
    ["待处理", "进行中", "已完成", "异常/撤回"].every(label =>
      mobileSignPackagePolicy.createStatusTabs().some(tab => tab.label === label)),
    "mobile sign package list should provide status segments for待处理/进行中/已完成/异常或撤回"
)
assert.ok(
  mobileSource.includes("listMyOnboardSignDataRequests") &&
    mobileSource.includes("packageDataRequestsById") &&
    mobileSource.includes("legacyDataRequests") &&
    mobileSource.includes("filteredDataRequests") &&
    mobileSource.includes("<mobile-onboard-data-request") &&
    mobileSource.includes('embedded') &&
    mobileSource.includes("唯一一次签名已完成") &&
    mobileSource.includes("isSignatureFirstEmployeeStage") &&
    mobileSource.includes("showEmbeddedOnboardDataStage") &&
    mobileSource.includes("openDataRequestDeepLink"),
  "My Signings must bind the signature-first employee stage into one real package while preserving legacy requests"
)
assert.ok(
  mobilePolicySource.includes("document.readConfirmed") &&
    mobileSource.includes("confirmSignPackageDocumentRead") &&
    mobileSource.includes("requiredReadDocuments") &&
    mobileSource.includes("全部需确认文件阅读后才能签署"),
  "mobile signing should require per-document read confirmation before package signing"
)
assert.ok(
  mobileSource.includes("confirmSignPackageFinalDocumentRead") &&
    mobileSource.includes("document.finalReadConfirmed") &&
    mobileSource.includes("allFinalDocumentsConfirmed") &&
    mobileSource.includes("待员工最终确认") &&
    mobileSource.includes("pending-final-watermark"),
  "pending-final confirmation should be server-recorded per opened file and visibly watermarked"
)
assert.ok(
  mobileSource.includes("isCompanyFirstSequence ? '手写签名并最终确认'") &&
    mobileSource.includes('status === "signed"') &&
    mobileSource.includes("手写签名并最终确认完成") &&
    mobileSource.includes("首次手写签名已提交，请等待公司与印章补充"),
  "company-first signing should complete in one employee action while signature-first keeps its waiting state"
)
assert.ok(
  desktopSource.includes("employeeVisible") &&
    desktopSource.includes("readConfirmationRequired") &&
    desktopSource.includes("仅人力资源内部") &&
    mobileSource.includes("requiresReadConfirmation") &&
    mobilePolicySource.includes("只读文件"),
  "template management and employee signing should distinguish visibility, read, and sign policies"
)
assert.ok(
  desktopSource.includes('v-model="templateForm.companySealRequired"') &&
    desktopSource.includes('v-model.trim="templateForm.companySealPositionJson"') &&
    desktopSource.includes("normalizeTemplatePlacementPolicy") &&
    desktopSource.includes("isValidCompanySealPlacement") &&
    desktopSource.includes("系统不会根据坐标猜测是否盖章") &&
    desktopSource.includes("APPENDED_CONFIRMATION_PAGE 会把员工签名、企业章和校验证据放在同一张追加确认页") &&
    desktopSource.includes("companySealRequired === \"Y\"") &&
    desktopSource.includes("缺少有效的印章定位"),
  "template management should require an explicit per-document company seal policy"
)
assert.ok(
  desktopSource.includes("finalizeRequiresSeal") &&
    desktopSource.includes("options.companySealRequired === false ? null") &&
    desktopSource.includes("finalizeRequiresSeal && !finalizeForm.sealId") &&
    desktopSource.includes("当前文件快照均明确为无需盖章"),
  "final contract generation should request a seal only when the frozen documents require one"
)
assert.ok(
  desktopSource.includes("recommendedLegalEntityName") &&
    desktopSource.includes("recommendedLegalRepresentative") &&
    desktopSource.includes("recommendedRegisteredAddress") &&
    desktopSource.includes("excelCompanyRecommendationMismatch") &&
    desktopSource.includes("不会自动新建、修改或切换公司"),
  "finalization must show Excel company facts as a read-only recommendation and warn on mismatch"
)
assert.ok(
  desktopSource.includes('v-if="canViewCompanySealManagement"') &&
    desktopSource.includes('checkPermi(["oa:signCompany:list"])') &&
    companySealSource.includes("v-hasPermi=\"['oa:signCompany:edit']\"") &&
    companySealSource.includes("v-hasPermi=\"['oa:signSeal:list']\"") &&
    companySealSource.includes("v-hasPermi=\"['oa:signSeal:edit']\"") &&
    !companySealSource.includes("v-hasPermi=\"['oa:signPackage:template']\""),
  "company and seal management should use independent read/write permissions"
)
assert.ok(
  mobileSource.includes("sticky-sign-bar") &&
    mobileSource.includes("signPanelOpen") &&
    mobileSource.includes("openSignPanel") &&
    mobileSource.includes("签署面板") &&
    mobileSource.includes("signConfirmText") &&
    mobileSource.includes("signatureCanvas") &&
    mobileSource.includes("signatureDataUrl") &&
    mobileSource.includes("certificateFileUrl") &&
    mobileSource.includes("本人确认签署本签约包"),
  "mobile signing should provide a sticky one-time package sign confirmation with handwritten signature"
)
assert.ok(
  mobileSource.includes("@pointerdown") &&
    mobileSource.includes("@pointermove") &&
    mobileSource.includes("@pointerup") &&
    mobileSource.includes("safe-area-inset-bottom") &&
    mobileSource.includes("preview-fallback"),
  "mobile signing should include Android/iOS pointer, safe-area, and file-preview fallback handling"
)
assert.ok(
  mobileSource.includes("getMySignPackageDocumentPreview") &&
    mobileSource.includes("downloadMySignPackageDocumentPreviewPage") &&
    mobileSource.includes("primaryPreviewPageUrl") &&
    mobileSource.includes("validatePreviewImageBlob") &&
    mobileSource.includes('type: "image/png"') &&
    mobileSource.includes("URL.createObjectURL") &&
    mobileSource.includes("URL.revokeObjectURL") &&
    mobileSource.includes('class="document-preview-image"') &&
    !mobileSource.includes("<iframe"),
  "mobile signing should render authenticated server-generated PNG pages without an iframe PDF plugin"
)
assert.ok(
  mobileSource.includes("previewPageCount") &&
    mobileSource.includes("previewPageNumber") &&
    mobileSource.includes("showPreviousPreviewPage") &&
    mobileSource.includes("showNextPreviewPage") &&
    mobileSource.includes("openPrimaryDocumentViewer") &&
    mobileSource.includes("closePrimaryDocumentViewer") &&
    mobileSource.includes("pdf-viewer-overlay") &&
    mobileSource.includes('@click="openPrimaryDocumentViewer"') &&
    !mobileSource.includes(':href="primaryDocumentFileUrl" target="_blank"'),
  "mobile preview should provide bounded pagination and an in-page fullscreen open-file viewer"
)
assert.ok(
  desktopSource.includes("downloadSignPackageDocument") &&
    desktopSource.includes("downloadSignPackageCertificate"),
  "desktop signing package detail should open generated files through authenticated blob downloads"
)
assert.ok(
  desktopSource.includes("canExportFinalDocument") &&
    desktopSource.includes("exportFinalDocument") &&
    desktopSource.includes("finalExportingDocumentId") &&
    desktopSource.includes("validatePdfBlob(fileBlob)") &&
    desktopSource.includes("await this.$download.saveAs(fileBlob, this.finalExportFileName(document))") &&
    desktopSource.includes("导出签章展示版") &&
    desktopSource.includes("签章展示版用于查看签名和盖章位置，原最终归档不变") &&
    desktopSource.includes("-签章展示版.pdf") &&
    desktopSource.includes("最终合同导出失败，请稍后重试") &&
    apiSource.includes("/final-file/export"),
  "desktop final archive export should be gated, PDF-validated, single-document guarded, safely named, and retryable"
)
assert.ok(
  mobileSource.includes("canExportFinalDocument(activeDocument)") &&
    mobileSource.includes("exportFinalDocument(document)") &&
    mobileSource.includes("finalExportingDocumentId") &&
    mobileSource.includes("validatePdfBlob(fileBlob)") &&
    mobileSource.includes("await this.$download.saveAs(fileBlob, this.finalExportFileName(document))") &&
    mobileSource.includes("导出签章展示版") &&
    mobileSource.includes("签章展示版用于查看签名和盖章位置，原最终归档不变") &&
    mobileSource.includes("-签章展示版.pdf") &&
    mobileSource.includes("最终合同导出失败，请稍后重试") &&
    apiSource.includes("/final-file/export"),
  "mobile final archive export should be capability-gated, PDF-validated, single-document guarded, safely named, and retryable"
)
assert.ok(
  /\.back-button,\s*\n\.refresh-button\s*\{[^}]*min-height:\s*44px;/s.test(mobileSource) &&
    /\.back-button\s*\{[^}]*width:\s*44px;[^}]*height:\s*44px;/s.test(mobileSource),
  "mobile signing header controls should provide 44px touch targets"
)
assert.ok(
  /\.read-button,\s*\n\.primary-button\s*\{[^}]*min-height:\s*44px;/s.test(mobileSource) &&
    /\.ghost-button\s*\{[^}]*min-height:\s*44px;/s.test(mobileSource) &&
    /\.confirm-text-row input\s*\{[^}]*height:\s*44px;/s.test(mobileSource),
  "mobile signing actions and confirmation input should provide 44px touch targets"
)
