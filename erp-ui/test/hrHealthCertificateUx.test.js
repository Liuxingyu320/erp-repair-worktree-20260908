const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const api = readUi("src/api/hr/healthCertificate.js")
const page = readUi("src/views/hr/healthCertificate/index.vue")
const attachmentPicker = readUi("src/views/drive/components/DriveAttachmentPicker.vue")
const employeeList = readUi("src/views/hr/components/HrEmployeeList.vue")
const employeeDetail = readUi("src/views/hr/components/HrProfileDetailDrawer.vue")
const mobileRoutes = readUi("src/views/mobile/mobileRouteDefinitions.js")
const todoRoutes = readUi("src/utils/todoRouteResolver.js")
const controller = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/controller/HrHealthCertificateController.java")
const service = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateServiceImpl.java")
const migration = readRepo("sql/erp_hr_health_certificate_20260713.sql")

assert.ok(
  api.includes("/system/hr/health-certificate/me/draft") &&
    api.includes("/system/hr/health-certificate/me/submit") &&
    api.includes("responseType: 'blob'"),
  "health certificate API should expose self-service draft, submit, and controlled attachment streaming"
)
assert.ok(
  page.includes("办理日期") && page.includes("到期日期") &&
    page.includes("续证保留历史") && page.includes("reviewHealthCertificate"),
  "health certificate page should preserve history and expose the review lifecycle"
)
assert.ok(
  page.includes("getHealthCertificateAttachment") &&
    page.includes("URL.createObjectURL") &&
    page.includes("<drive-attachment-picker") &&
    attachmentPicker.includes("type !== 'image/svg+xml'") &&
    attachmentPicker.includes("type === 'application/pdf'"),
  "health certificate attachments should use the authorized blob endpoint and reject SVG choices in the UI"
)
assert.ok(
  employeeList.includes("healthCertificateExpiresOn") &&
    employeeList.includes("healthCertificateStatus") &&
    employeeDetail.includes("healthCertificateIssuedDate") &&
    employeeDetail.includes("healthCertificateAttachmentPresent"),
  "employee list and detail should expose the current health certificate projection"
)
assert.ok(
  mobileRoutes.includes("/mobile/hr/health-certificate") &&
    todoRoutes.includes("HR_HEALTH_CERT_DUE") &&
    todoRoutes.includes("HR_HEALTH_CERT_REVIEW") &&
    todoRoutes.includes("HR_HEALTH_CERT_RETURNED") &&
    todoRoutes.includes("/mobile/hr/health-certificate"),
  "mobile navigation and unified todos should route to health certificate handling"
)
assert.ok(
  page.includes("healthCertificateView") &&
    page.includes("EXPIRING_OR_EXPIRED") &&
    page.includes("focusCertificateId") &&
    page.includes("todo/invalidateAfterMutation"),
  "todo links should open the correct health-certificate tab/filter and mutations should invalidate reminders"
)
assert.ok(
  controller.includes("@RequiresLogin") &&
    controller.includes("resolveAttachmentNode") &&
    controller.includes('headers.setCacheControl("no-store")') &&
    controller.includes('headers.set("X-Content-Type-Options", "nosniff")'),
  "attachment streaming must authorize the business record and disable caching/sniffing"
)
assert.ok(
  service.includes("validateDriveBusinessFile") &&
    service.includes('"HEALTH_CERTIFICATE"') &&
    service.includes("selectCurrentProjection") &&
    service.includes("clearCurrentByUserId"),
  "health certificate writes should validate controlled files and maintain a single current projection"
)
assert.ok(
  migration.includes("UNIQUE KEY uk_hr_health_user_current (user_id, current_flag)") &&
    migration.includes("todo.health-certificate.warning-days") &&
    migration.includes("30,15,7"),
  "migration should enforce one current certificate and configure expiry reminder thresholds"
)

console.log("hrHealthCertificateUx tests passed")
