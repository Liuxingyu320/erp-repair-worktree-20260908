const assert = require("assert")
const fs = require("fs")
const path = require("path")

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, "..", relativePath), "utf8")
}

const apiSource = source("src/api/oa/laborContract.js")
const viewSource = source("src/views/oa/laborContract/index.vue")
const mobileViewSource = source("src/views/mobile/contract/index.vue")
const routerSource = source("src/router/index.js")
const mobileRouteSource = source("src/views/mobile/mobileRouteDefinitions.js")
const { MOBILE_ROUTES } = require("../src/views/mobile/mobileNavigation")

const apiContracts = [
  ["listLaborContract", "url: '/oa/laborContract/list'", "method: 'get'"],
  ["getLaborContract", "url: '/oa/laborContract/' + contractId", "method: 'get'"],
  ["saveLaborContract", "url: '/oa/laborContract/save'", "method: 'post'"],
  ["sendLaborContract", "url: '/oa/laborContract/send'", "method: 'post'"],
  ["voidLaborContract", "url: '/oa/laborContract/' + contractId + '/void'", "method: 'post'"],
  ["previewLaborContract", "url: '/oa/laborContract/' + contractId + '/preview'", "method: 'get'"],
  ["downloadLaborContractFile", "url: '/oa/laborContract/download/' + contractId + '/' + kind", "responseType: 'blob'"],
  ["verifyLaborContractHash", "url: '/oa/laborContract/verify'", "method: 'post'"],
  ["listLaborContractTemplate", "url: '/oa/laborContract/template/list'", "method: 'get'"],
  ["saveLaborContractTemplate", "url: '/oa/laborContract/template'", "method: 'post'"],
  ["getLaborContractSeal", "url: '/oa/laborContract/seal'", "method: 'get'"],
  ["saveLaborContractSeal", "url: '/oa/laborContract/seal'", "method: 'post'"],
  ["listMyLaborContracts", "url: '/oa/laborContract/mobile/my'", "method: 'get'"],
  ["getMyLaborContract", "url: '/oa/laborContract/mobile/' + contractId", "method: 'get'"],
  ["signMyLaborContract", "url: '/oa/laborContract/mobile/' + contractId + '/sign'", "method: 'post'"]
]

for (const [fnName, urlSnippet, methodSnippet] of apiContracts) {
  assert.ok(apiSource.includes(`export function ${fnName}`), `${fnName} should be exported by the labor contract API`)
  assert.ok(apiSource.includes(urlSnippet), `${fnName} should call ${urlSnippet}`)
  assert.ok(apiSource.includes(methodSnippet), `${fnName} should use ${methodSnippet}`)
}

assert.strictEqual(MOBILE_ROUTES.contract, "/mobile/contract", "mobile contract route should stay stable")
assert.ok(
  mobileRouteSource.includes("path: '/mobile/contract'") &&
    mobileRouteSource.includes("import('@/views/mobile/contract/index')"),
  "router should keep the mobile labor contract page addressable for historical deep links"
)
assert.ok(
  !mobileRouteSource.includes("{ label: '劳动合同', icon: 'document', path: '/mobile/contract' }") &&
    mobileRouteSource.includes("{ label: '我的签约', icon: 'document', path: '/mobile/sign-package' }"),
  "mobile mine actions should expose only 我的签约 as the employee signing entry"
)
assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)
assert.ok(
  mobileViewSource.includes("signMyLaborContract") &&
    mobileViewSource.includes("signatureCanvas") &&
    mobileViewSource.includes("contract-frame"),
  "mobile labor contract page should keep contract preview and handwritten signing flow wired"
)
assert.ok(
  mobileViewSource.includes("downloadLaborContractFile") &&
    mobileViewSource.includes("URL.createObjectURL") &&
    mobileViewSource.includes("revokeFileObjectUrl"),
  "mobile labor contract file preview should load protected files through authenticated blob requests"
)
assert.ok(
  /documentVersion:\s*this\.selectedContract\.documentVersion/.test(mobileViewSource) &&
    /previewFileHash:\s*this\.selectedContract\.previewFileHash/.test(mobileViewSource),
  "mobile labor contract signing should submit the frozen document version and preview hash"
)
assert.ok(
  mobileViewSource.includes("signConfirmText") &&
    /signConfirmText:\s*this\.signConfirmText/.test(mobileViewSource) &&
    mobileViewSource.includes("本人确认签署"),
  "mobile labor contract signing should require and submit the second confirmation text"
)
assert.ok(
  mobileViewSource.includes("selectedContract.certificateFileUrl") &&
    mobileViewSource.includes("selectedContract.archiveFileHash") &&
    mobileViewSource.includes("selectedContract.certificateFileHash"),
  "mobile labor contract evidence panel should expose archive and certificate evidence after signing"
)

assert.ok(
  /\.section-head a\s*\{[^}]*min-width:\s*44px[^}]*min-height:\s*44px/s.test(mobileViewSource) &&
    /\.certificate-link\s*\{[^}]*min-height:\s*44px/s.test(mobileViewSource) &&
    /\.confirm-text-row input\s*\{[^}]*min-height:\s*44px/s.test(mobileViewSource),
  "mobile labor contract links and form controls should preserve 44px touch targets"
)

assert.ok(
  viewSource.includes('label="历史劳动合同"') &&
    viewSource.includes("合同验真") &&
    viewSource.includes("handleVerifyHash") &&
    viewSource.includes("handleDetail(scope.row)") &&
    viewSource.includes("handlePreview(scope.row)"),
  "desktop labor contract page should become a historical contract and hash verification module"
)
;[
  '@click="handleAdd"',
  '@click="handleEdit(scope.row)"',
  '@click="handleSend(scope.row)"',
  '@click="handleVoid(scope.row)"',
  '@click="handleAddTemplate"',
  '@click="handleEditTemplate(scope.row)"',
  '@click="sealOpen = true"'
].forEach(snippet => {
  assert.ok(
    !viewSource.includes(snippet),
    `desktop historical labor contract page should not expose ${snippet}`
  )
})
assert.ok(
  !viewSource.includes('label="模板与企业章"'),
  "desktop historical labor contract page should not expose template and company seal maintenance"
)

assert.ok(
  viewSource.includes("getSelectedDeptContext") &&
    viewSource.includes("hasValidatedSelectedDeptContext"),
  "desktop labor contract employee picker should read the explicit selected shop context"
)
assert.ok(
  /loadEmployees\(\)\s*\{\s*if \(!this\.ensureShopContextForEmployeeOptions\(false\)\)/.test(viewSource) &&
    viewSource.indexOf("ensureShopContextForEmployeeOptions") < viewSource.indexOf("listUser({ pageNum: 1, pageSize: 200, status: \"0\" }, { silentError: true })"),
  "desktop labor contract employee loading should verify selected shop context before calling system user list"
)
assert.ok(
  /handleAdd\(\)\s*\{\s*if \(!this\.ensureShopContextForEmployeeOptions\(\)\)/.test(viewSource),
  "starting a labor contract should stop before opening the form when selected shop context is missing"
)
assert.ok(
  !viewSource.includes('label="模板与企业章"'),
  "desktop historical labor contract page should not render template/seal maintenance tabs"
)
assert.ok(
  viewSource.includes("bootstrapLaborContractPage()") &&
    viewSource.includes("safeLoadLaborContractResource"),
  "desktop historical labor contract page should bootstrap only query/detail/hash resources"
)
assert.ok(
  !/created\(\)\s*\{\s*this\.getList\(\)\s*this\.getTemplates\(\)\s*this\.loadEmployees\(\)\s*this\.loadSchemes\(\)\s*this\.loadSeal\(\)\s*\}/.test(viewSource),
  "desktop labor contract created hook should not unconditionally call every labor-contract API"
)

const handlePreviewIndex = viewSource.indexOf("handlePreview(row)")
const previewRequestIndex = viewSource.indexOf("previewLaborContract(row.contractId)", handlePreviewIndex)
const popupIndex = viewSource.indexOf("window.open(\"about:blank\", \"_blank\")", handlePreviewIndex)
assert.ok(
  handlePreviewIndex > -1 && popupIndex > handlePreviewIndex && popupIndex < previewRequestIndex,
  "desktop labor contract file preview should open a placeholder tab synchronously before async preview lookup"
)
assert.ok(
  /openFile\(url,\s*previewWindow,\s*data\.contractId \|\| row\.contractId\)/.test(viewSource) &&
    viewSource.includes("downloadLaborContractFile(contractId, kind)") &&
    viewSource.includes("URL.createObjectURL"),
  "desktop labor contract file preview should reuse the placeholder tab with an authenticated blob URL"
)
assert.ok(
  viewSource.includes("verifyLaborContractHash") &&
    viewSource.includes("verifyOpen") &&
    viewSource.includes("handleVerifyHash"),
  "desktop labor contract view should provide a hash verification dialog"
)
assert.ok(
  viewSource.includes("data.pdfFileUrl || data.archiveFileUrl || data.previewFileUrl") &&
    viewSource.includes("签署版本") &&
    viewSource.includes("事件哈希"),
  "desktop labor contract detail should prefer PDF files and show document version plus audit hash chain fields"
)

assert.ok(
  viewSource.includes('label="合同标题" prop="contractTitle"'),
  "desktop labor contract form should expose the contractTitle field so saved contracts have a readable title"
)
assert.ok(
  !/el-table-column label="手机号" prop="employeePhone"/.test(viewSource) &&
    !/el-table-column label="工资合计" prop="totalSalary"/.test(viewSource),
  "desktop labor contract list should not expose phone numbers or salary totals in the bulk table"
)
assert.ok(
  !/v-if="scope\.row\.status === 'draft' \|\| scope\.row\.status === 'pending_sign'"/.test(viewSource),
  "desktop historical labor contract page should not expose ordinary void actions"
)
assert.ok(
  !viewSource.includes(":label=\"employeeOptionLabel(user)\"") &&
    viewSource.includes("contractOptionLabel") &&
    viewSource.includes("employeeOptionLabel(user)") &&
    viewSource.includes("user.dept.deptName"),
  "desktop labor contract employee picker should use precomputed option labels instead of calling a hot-update-sensitive render method"
)
assert.ok(
  viewSource.includes("模板必须包含员工姓名、身份证号、手机号、合同期限和岗位占位符"),
  "desktop labor contract template upload should warn that ordinary blank-line Word files cannot auto-fill"
)
assert.ok(
  mobileViewSource.includes("本人已阅读并确认签署此版本合同"),
  "mobile labor contract signing copy should bind the confirmation to the frozen document version"
)
assert.ok(
  mobileViewSource.includes("合同文件尚未加载") &&
    /if \(!this\.selectedContract\.documentVersion \|\| !this\.selectedContract\.previewFileHash\)/.test(mobileViewSource),
  "mobile labor contract signing should block submission until the protected file and frozen version/hash are loaded"
)
assert.ok(
  /\.back-button,\s*\n\.refresh-button,[\s\S]*?min-height:\s*44px;/.test(mobileViewSource) &&
    /\.back-button\s*\{[^}]*width:\s*44px;/s.test(mobileViewSource),
  "mobile labor contract header controls should provide 44px touch targets"
)
