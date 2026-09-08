const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function read(relativePath, root = uiRoot) {
  return fs.readFileSync(path.join(root, relativePath), "utf8")
}

const scenarios = require("../src/utils/signScenario")
const templateCatalog = require("../src/views/oa/signPackage/signTemplateCatalog")
assert.strictEqual(scenarios.normalizeTaskScenario("renew"), "RENEWAL")
assert.strictEqual(scenarios.normalizePackageScenario("RENEWAL"), "renewal")
assert.strictEqual(scenarios.signScenarioLabel("renewal"), "续签")
assert.ok(scenarios.SIGN_TASK_SCENARIO_OPTIONS.some(item => item.value === "RENEWAL"))
assert.ok(scenarios.SIGN_PACKAGE_SCENARIO_OPTIONS.some(item => item.value === "renewal"))
assert.strictEqual(scenarios.packageScenarioHasEntryDate("renewal"), false)
assert.strictEqual(scenarios.packageScenarioHasContractDates("renewal"), true)
assert.strictEqual(scenarios.packageScenarioHasProbationDates("renewal"), false)
assert.strictEqual(scenarios.packageScenarioHasSalary("renewal"), true)

const taskPage = read("src/views/oa/signTask/index.vue")
assert.ok(taskPage.includes(":aria-pressed=\"activeMetricKey === metric.key\""),
  "metric cards should expose their selected state")
assert.ok(taskPage.includes("dueSoon: true") && taskPage.includes("completedMonth: true"),
  "due-soon and completed-this-month cards should filter the task list")
assert.ok(taskPage.includes("employeeName"),
  "task center should support finding and identifying an employee by name")
assert.ok(taskPage.includes("taskNumberLabel(scope.row)") &&
  taskPage.includes("businessText(scope.row.failureDetail"),
  "task center should not expose internal English task numbers or failure codes")

const taskDetail = read("src/views/oa/signTask/SignTaskDetailDrawer.vue")
const previewStart = taskDetail.indexOf("previewDocument(document)")
const popupStart = taskDetail.indexOf("window.open('about:blank', '_blank')", previewStart)
const downloadStart = taskDetail.indexOf("downloadSignPackageDocument", previewStart)
assert.ok(previewStart > -1 && popupStart > previewStart && popupStart < downloadStart,
  "task document preview should reserve a window before the asynchronous protected download")
assert.ok(!taskDetail.includes("SignTaskConfirmDialog") &&
  !read("src/api/oa/signTask.js").includes("/confirm"),
"contract tasks should not expose the removed review action")

const packagePage = read("src/views/oa/signPackage/index.vue")
const packageRecordPanel = read("src/views/oa/signPackage/SignPackageRecordPanel.vue")
assert.ok(packagePage.includes('status: "1"') && packagePage.includes("新模板首次保存固定为停用"),
  "new templates should default to disabled and explain the deliberate review step")
assert.ok(packagePage.includes("SIGN_PACKAGE_SCENARIO_OPTIONS"),
  "desktop signing screens should share the canonical scenario options")
assert.ok(packagePage.includes("showPackageProbationFields") && packagePage.includes("showPlanContractFields"),
  "renewal forms should keep contract and salary fields while hiding onboarding-only fields")
assert.ok(packagePage.includes("SignPackageRecordPanel") &&
  packageRecordPanel.includes("请先回到“签约包”页签"),
  "record empty state should explain how to select a signing package")
;[
  "ONBOARD_OFFER_NOTICE",
  "ONBOARD_COMMITMENT",
  "ONBOARD_POST_DUTY",
  "ONBOARD_HANDBOOK_RECEIPT",
  "ONBOARD_SALARY_CONFIRM",
  "ONBOARD_CONFIDENTIAL_NONCOMPETE",
  "RENEWAL_LABOR_CONTRACT"
].forEach(code => assert.strictEqual(templateCatalog.isLaborEmploymentTemplateType(code), true))
assert.ok(packagePage.includes("isLaborEmploymentTemplateType(templateType)"),
  "labor-only onboarding and renewal templates should be fixed to labor-contract matching in the editor")
assert.ok(templateCatalog.DEFAULT_TEMPLATE_TYPE_OPTIONS
  .find(option => option.code === "ONBOARD_LABOR_CONTRACT")
  .requiredPlaceholders.includes("baseSalary"),
  "labor-contract templates should require the base salary used by the reviewed file")
assert.ok(packagePage.includes("scope.row.documentCount") &&
  packagePage.includes("displaySignDateTime(scope.row.sentTime)") &&
  packagePage.includes("scope.row.initialSignedTime || scope.row.signatureSampleTime || scope.row.signedTime") &&
  !packagePage.includes("parseTime(scope.row.sentTime)"),
  "desktop package rows should use the deterministic signing formatter and the initial-sign timestamp")
assert.ok(packagePage.includes("getSelectedSignScopeDeptId()") &&
  packagePage.includes("新建签约包前，请先选择公司或门店签约组织") &&
  packagePage.includes("getSelectedSignScopeContext()") &&
  packagePage.includes("shopDeptName: shopContext.deptName"),
"manual package creation should require its dedicated company-or-store signing scope and carry its display name")
assert.ok(packagePage.includes("signPlaceholderToken") && packagePage.includes("scenarioLabel(templateForm.scenario)"),
  "template configuration should show Chinese scenario and placeholder names")

const mobileLaborContract = read("src/views/mobile/contract/index.vue")
assert.ok(mobileLaborContract.includes("手机浏览器无法直接预览") && mobileLaborContract.includes("下载 Word 合同"),
  "legacy Word contracts should show an explicit download fallback instead of a blank iframe")

const legacyContract = read("src/views/oa/laborContract/index.vue")
assert.ok(legacyContract.includes("合同文件验真") && legacyContract.includes("SHA-256 哈希"),
  "legacy verification should use clear business-facing labels")

const mobilePackage = read("src/views/mobile/signPackage/index.vue")
const mobilePackageFileGate = read("src/utils/signPackageFileGate.js")
assert.ok(mobilePackage.includes("signScenarioLabel"),
  "employee signing should render canonical Chinese scenario labels")
assert.ok(mobilePackage.includes("currentDocument.reviewPdfHash"),
  "read confirmation should submit the freshly revalidated document hash")
assert.ok(mobilePackage.includes("fileRequestSequence"),
  "file preview responses should not overwrite a newer document selection")
assert.ok(mobilePackage.includes("requiredReadDocuments") && mobilePackageFileGate.includes("readConfirmationRequired"),
  "employee signing should include read-only confirmations such as the employee handbook")
assert.ok(mobilePackage.includes("documentCount(item)") &&
  mobilePackage.includes("deadlineSummary(item)") &&
  !mobilePackage.includes("formatDateTime(item.sentTime"),
"employee package rows should show the real document count and signing deadline without exposing send time")

const displayText = require("../src/utils/signDisplayText")
assert.strictEqual(displayText.signVersionLabel("20260714-v3-draft"), "20260714-第3版-草稿")
assert.strictEqual(displayText.signTaskNumberLabel({ taskId: 9, taskNo: "ST-ABC" }), "合同任务9")
assert.ok(!/[A-Za-z]/.test(displayText.signBusinessText("TEMPLATE_HASH_MISMATCH")),
  "contract business messages should render in Chinese")

const mobileNavigation = read("src/views/mobile/mobileNavigation.js")
assert.ok(mobileNavigation.includes("MOBILE_ROUTES.signPackage"),
  "personal signing should not require selecting a store or warehouse context")

const packageDomain = read("erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java", repoRoot)
const packageMapper = read("erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml", repoRoot)
const packageService = read("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java", repoRoot)
assert.ok(packageDomain.includes("private Integer documentCount") &&
  packageMapper.includes("as document_count") && packageMapper.includes("employee_visible = 'Y'"),
"package list APIs should return an HR count and an employee-visible count without loading every document")
assert.ok(packageService.includes("setShopDeptName(shopScopeService.resolveShopDeptName(shopDeptId))"),
  "the server should persist the canonical shop name instead of trusting a client snapshot")
assert.ok(packageDomain.includes("private String legalEntityNameSnapshot") &&
  packageDomain.includes("private String previousContractEndDate") &&
  packageDomain.includes("private Integer previousRenewalCount") &&
  packageMapper.includes("legal_entity_name_snapshot") &&
  packageMapper.includes("previous_contract_end_date") &&
  packageMapper.includes("previous_renewal_count"),
"packages should persist legal-entity and renewal-safety snapshots instead of hiding them in remarks")

const taskDomain = read("erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java", repoRoot)
assert.ok(taskDomain.includes("private String employeeName") && taskDomain.includes("private Boolean dueSoon") &&
  taskDomain.includes("private Boolean completedMonth"),
"task list domain should carry the usability-only name and metric filters")

const taskMapper = read("erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml", repoRoot)
assert.ok(taskMapper.includes("employee_name_snapshot") && taskMapper.includes("dueSoon") &&
  taskMapper.includes("completedMonth") && taskMapper.includes("upper(trim(scenario)) = 'RENEW'"),
"task mapper should implement employee-name and metric-card filtering")

assert.ok(packageService.includes("OaSignScenarioCodes.normalizePackageScenario"),
  "manual and task-derived packages should persist the canonical package scenario")
assert.ok(packageService.includes("attachEmployeeChildren") && packageService.includes("assertEmployeeVisibleDocument"),
  "employee package detail and file endpoints should exclude internal HR documents")

const preflightValidator = read("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java", repoRoot)
assert.ok(preflightValidator.includes("offerNotice") && preflightValidator.includes("handbookReceipt") &&
  preflightValidator.includes("劳务合同与劳务合同签收单必须成套发送"),
"service packages should reject labor-only materials and require the service agreement pair")
const documentService = read("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java", repoRoot)
assert.ok(/values\.put\("companyName",[\s\S]{0,160}signPackage\.getLegalEntityNameSnapshot\(\)/.test(documentService) &&
  !documentService.includes('values.put("companyName", value(signPackage.getShopDeptName()))'),
"companyName must render the frozen legal entity, never the store name")

console.log("contract usability audit tests passed")
