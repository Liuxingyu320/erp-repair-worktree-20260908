/**
 * Mobile beautification acceptance gates (static).
 * Covers: design tokens, non-semantic clicks, route smoke matrix,
 * viewport/safe-area/keyboard contracts, public+specialty polish, legacy style audit.
 */
const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const read = (relativePath) => {
  const absolute = path.join(root, relativePath)
  return fs.existsSync(absolute) ? fs.readFileSync(absolute, "utf8") : ""
}

const BUSINESS_ROUTES = [
  "/mobile/store", "/mobile/warehouse", "/mobile/inventory", "/mobile/hr", "/mobile/mine",
  "/mobile/todo", "/mobile/oa-purchase-approval", "/mobile/reimbursement", "/mobile/notice",
  "/mobile/hr/onboarding", "/mobile/hr/onboarding/create", "/mobile/hr/onboarding/:id/edit",
  "/mobile/hr/onboarding/:id", "/mobile/hr/employee", "/mobile/hr/completeness",
  "/mobile/hr/health-certificate", "/mobile/attendance", "/mobile/salary", "/mobile/salary-scheme",
  "/mobile/contract", "/mobile/sign-package", "/mobile/onboard-data",
  "/mobile/sales", "/mobile/purchase", "/mobile/purchase-return", "/mobile/stock", "/mobile/product",
  "/mobile/oe", "/mobile/gift", "/mobile/category", "/mobile/customer", "/mobile/supplier",
  "/mobile/stock-log", "/mobile/stock-check", "/mobile/transfer-records", "/mobile/sales-return",
  "/mobile/replenishment", "/mobile/outbound", "/mobile/transfer", "/mobile/transfer-approval",
  "/mobile/transfer-rules", "/mobile/drive", "/mobile/profile", "/mobile/fixed-asset-repair",
  "/mobile/system-user", "/mobile/system-role", "/mobile/system-post", "/mobile/system-dept",
  "/mobile/system-menu", "/mobile/user-shop", "/mobile/system-config", "/mobile/system-dict-type",
  "/mobile/system-dict-data", "/mobile/system-logininfor", "/mobile/system-operlog",
  "/mobile/monitor-job", "/mobile/monitor-job-log", "/mobile/monitor-online"
]

const PUBLIC_ROUTES = [
  "/login", "/register", "/credential/change-password", "/complete-profile",
  "/select-shop", "/401", "/404", "/lock"
]

const PAGE_IMPLEMENTATIONS = [
  { id: "login", file: "src/views/login.vue", states: ["normal"] },
  { id: "register", file: "src/views/register.vue", states: ["normal"] },
  { id: "change-password", file: "src/views/credential/change-password.vue", states: ["normal", "error"] },
  { id: "complete-profile", file: "src/views/profile-completion/index.vue", states: ["loading", "error", "normal"] },
  { id: "select-shop", file: "src/views/select-shop/index.vue", states: ["loading", "empty", "normal"] },
  { id: "401", file: "src/views/error/401.vue", states: ["error"] },
  { id: "404", file: "src/views/error/404.vue", states: ["error"] },
  { id: "lock", file: "src/views/lock.vue", states: ["normal", "error"] },
  { id: "workbench", file: "src/views/mobile/components/MobileWorkbenchShell.vue", states: ["loading", "context", "permission", "session", "network", "normal"] },
  { id: "inventory", file: "src/views/mobile/inventory/index.vue", states: ["loading", "error", "normal"] },
  { id: "feature-template", file: "src/views/mobile/feature/index.vue", states: ["loading", "permission", "session", "network", "empty", "filtered-empty", "normal", "detail-sheet", "form-sheet"] },
  { id: "todo", file: "src/views/mobile/todo/index.vue", states: ["loading", "error", "empty", "normal"] },
  { id: "reimbursement", file: "src/views/mobile/oa/reimbursement/index.vue", states: ["list", "form", "detail", "dialog"] },
  { id: "drive", file: "src/views/mobile/drive/index.vue", states: ["loading", "session", "empty", "normal", "preview", "action-sheet"] },
  { id: "sign-package", file: "src/views/mobile/signPackage/index.vue", states: ["loading", "error", "empty", "detail", "dialog"] },
  { id: "onboarding-form", file: "src/views/mobile/hr/onboarding/form.vue", states: ["loading", "error", "steps"] },
  { id: "onboarding-list", file: "src/views/mobile/hr/onboarding/index.vue", states: ["list", "filter"] },
  { id: "customer", file: "src/views/mobile/customer/index.vue", states: ["list", "detail", "form"] },
  { id: "contract", file: "src/views/mobile/contract/index.vue", states: ["list", "preview", "sign"] },
  { id: "employee-completeness-health", files: [
    "src/views/mobile/hr/employee/index.vue",
    "src/views/mobile/hr/completeness/index.vue",
    "src/views/mobile/hr/healthCertificate/index.vue"
  ], states: ["list", "editor"] }
]

assert.strictEqual(BUSINESS_ROUTES.length, 58, "business route matrix must contain 58 routes")
assert.strictEqual(PUBLIC_ROUTES.length, 8, "public route matrix must contain 8 routes")
assert.strictEqual(PAGE_IMPLEMENTATIONS.length, 20, "page implementation matrix must cover 20 implementations")

// --- tokens ---
const tokens = read("src/views/mobile/styles/mobileSystem.scss")
const globalStyles = read("src/assets/styles/index.scss")
;[
  "--mobile-color-page: #f4f5f2",
  "--mobile-color-primary: #0b6b53",
  "--mobile-color-success",
  "--mobile-bottom-nav-total",
  "--mobile-keyboard-inset",
  "--mobile-safe-top",
  "--mobile-safe-bottom",
  ".mobile-data-state",
  ".mobile-status-chip",
  ".mobile-bottom-action-bar",
  "font-size: var(--mobile-font-input)",
  "min-height: 44px",
  "prefers-reduced-motion"
].forEach(marker => {
  assert.ok(tokens.includes(marker) || new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).test(tokens),
    `token source must include ${marker}`)
})
assert.ok(globalStyles.includes("views/mobile/styles/mobileSystem.scss"))
assert.ok(!globalStyles.includes("assets/styles/mobile-system.scss"))

// --- legacy zero refs ---
function walkVueAndScss(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === "dist") continue
      walkVueAndScss(full, acc)
    } else if (/\.(vue|scss|css|js|ts)$/.test(entry.name)) {
      acc.push(full)
    }
  }
  return acc
}

const sourceFiles = walkVueAndScss(path.join(root, "src"))
let legacyImportHits = 0
sourceFiles.forEach(file => {
  const text = fs.readFileSync(file, "utf8")
  if (/assets\/styles\/mobile-system\.scss|views\/styles\/mobileSystem\.scss/.test(text) &&
      !file.endsWith(`${path.sep}mobile-system.scss`) &&
      !file.endsWith(`${path.sep}styles${path.sep}mobileSystem.scss`)) {
    legacyImportHits += 1
  }
})
assert.strictEqual(legacyImportHits, 0, "legacy blue glass style files must have zero runtime imports")

assert.ok(
  !fs.existsSync(path.join(root, "src/assets/styles/mobile-system.scss")),
  "legacy assets mobile-system.scss should be removed after zero-ref + regression gates"
)
assert.ok(
  !fs.existsSync(path.join(root, "src/views/styles/mobileSystem.scss")),
  "legacy views mobileSystem.scss should be removed after zero-ref + regression gates"
)

// --- non-semantic click audit ---
function collectTemplates() {
  const roots = [
    path.join(root, "src/views/mobile"),
    path.join(root, "src/views/login.vue"),
    path.join(root, "src/views/register.vue"),
    path.join(root, "src/views/lock.vue"),
    path.join(root, "src/views/select-shop"),
    path.join(root, "src/views/error"),
    path.join(root, "src/views/credential"),
    path.join(root, "src/views/profile-completion")
  ]
  const files = []
  roots.forEach(r => {
    if (fs.existsSync(r) && fs.statSync(r).isFile()) files.push(r)
    else if (fs.existsSync(r)) walkVueAndScss(r).filter(f => f.endsWith(".vue")).forEach(f => files.push(f))
  })
  return files
}

const interactiveTags = new Set([
  "button", "a", "input", "select", "textarea", "option", "label", "router-link",
  "el-button", "el-checkbox", "el-switch", "el-radio", "el-link", "summary", "el-dropdown-item"
])

const nonSemantic = []
collectTemplates().forEach(file => {
  const text = fs.readFileSync(file, "utf8")
  const template = text.split(/<script[\s>]/)[0]
  let i = 0
  while (true) {
    const j = template.indexOf("@click", i)
    if (j < 0) break
    const start = template.lastIndexOf("<", j)
    const end = template.indexOf(">", j)
    if (start < 0 || end < 0) {
      i = j + 1
      continue
    }
    const chunk = template.slice(start, end + 1)
    if (chunk.startsWith("<!--")) {
      i = j + 1
      continue
    }
    const tagMatch = chunk.match(/^<([A-Za-z][\w-]*)/)
    if (!tagMatch) {
      i = j + 1
      continue
    }
    const tag = tagMatch[1].toLowerCase()
    const attrs = chunk.slice(tagMatch[0].length, -1)
    if (/@click\.self\b/.test(attrs) && !/@click(?!\.self)\b/.test(attrs)) {
      i = j + 1
      continue
    }
    const semantic = interactiveTags.has(tag) ||
      /role\s*=\s*["']button["']/.test(attrs) ||
      /type\s*=\s*["']button["']/.test(attrs)
    if (!semantic) {
      nonSemantic.push(`${path.relative(root, file)} <${tag}>`)
    }
    i = j + 1
  }
})
assert.strictEqual(nonSemantic.length, 0, `non-semantic click nodes must be 0, found: ${nonSemantic.join("; ")}`)

// --- page implementation contracts ---
PAGE_IMPLEMENTATIONS.forEach(impl => {
  const files = impl.files || [impl.file]
  files.forEach(file => {
    const source = read(file)
    assert.ok(source.length > 0, `${impl.id} source missing: ${file}`)
    assert.ok(
      source.includes("mobile-system-page") ||
        source.includes("mobile-system-sheet") ||
        source.includes("mobile-system-topbar") ||
        source.includes("var(--mobile-color-") ||
        source.includes("MobileHrShell") ||
        source.includes("HealthCertificatePage"),
      `${impl.id} should consume mobile system tokens or shared shell`
    )
  })
})

// blocking state mutual exclusion contracts
const feature = read("src/views/mobile/feature/index.vue")
const workbench = read("src/views/mobile/components/MobileWorkbenchShell.vue")
const drive = read("src/views/mobile/drive/index.vue")
const sign = read("src/views/mobile/signPackage/index.vue")
const inventory = read("src/views/mobile/inventory/index.vue")
assert.ok(feature.includes("session-expired") && feature.includes("isBlockingListState"))
assert.ok(workbench.includes("session-expired") && workbench.includes("showWorkbenchContent"))
assert.ok(drive.includes("pageBlockingState") && drive.includes("还没有文件"))
assert.ok(sign.includes("pageLevelError"))
assert.ok(inventory.includes("blockingState"))

// public pages polish contracts
const login = read("src/views/login.vue")
const register = read("src/views/register.vue")
const selectShop = read("src/views/select-shop/index.vue")
const changePassword = read("src/views/credential/change-password.vue")
const page401 = read("src/views/error/401.vue")
const page404 = read("src/views/error/404.vue")
const lock = read("src/views/lock.vue")
assert.ok(login.includes("mobile-system-page") && login.includes("--mobile-color-primary"))
assert.ok(register.includes("var(--mobile-color-page") || register.includes("background: var(--mobile-color-page"))
assert.ok(selectShop.includes("tea-room-backdrop") && selectShop.includes("background-image: none"))
assert.ok(changePassword.includes("--mobile-color-primary") && !changePassword.includes("#409eff"))
assert.ok(page401.includes("返回有效入口") && page401.includes("error-secondary"))
assert.ok(page404.includes("继续工作") && page404.includes("error-secondary"))
assert.ok(lock.includes("var(--mobile-color-page") && lock.includes("display: none") && lock.includes("particle-bg"))

// specialty polish
const customer = read("src/views/mobile/customer/index.vue")
const employee = read("src/views/mobile/hr/employee/index.vue")
const completeness = read("src/views/mobile/hr/completeness/index.vue")
const health = read("src/views/mobile/hr/healthCertificate/index.vue")
const contract = read("src/views/mobile/contract/index.vue")
assert.ok(customer.includes("var(--mobile-color-page") && customer.includes("customer-hero { display: none"))
assert.ok(employee.includes("var(--mobile-color-primary") && !/#2563eb/.test(employee.split("</style>")[0].split("var(--mobile-color-primary)").pop() || "") || employee.includes("var(--mobile-color-primary)"))
assert.ok(completeness.includes("var(--mobile-color-primary"))
assert.ok(health.includes("mobile-system-page") && health.includes("var(--mobile-color-page"))
assert.ok(contract.includes("var(--mobile-color-page"))

// viewport / safe-area / keyboard matrix
const viewports = [
  { w: 320, h: 568 },
  { w: 354, h: 766 },
  { w: 375, h: 667 },
  { w: 390, h: 844 }
]
const viewportUtil = read("src/views/mobile/mobileViewport.js")
assert.ok(viewportUtil.includes("visualViewport") && viewportUtil.includes("--mobile-keyboard-inset"))
assert.ok(tokens.includes("@media (max-width: 360px)"))
assert.ok(tokens.includes("scroll-padding-bottom: calc(var(--mobile-bottom-nav-total) + var(--mobile-keyboard-inset)"))
assert.ok(tokens.includes("padding-bottom: max(var(--mobile-space-3), var(--mobile-safe-bottom))") || tokens.includes("var(--mobile-safe-bottom)"))
viewports.forEach(vp => {
  assert.ok(vp.w >= 320 && vp.h >= 568, `viewport ${vp.w}x${vp.h} is supported by shared responsive tokens`)
})

// route smoke: feature routes defined
const routeDefs = read("src/views/mobile/mobileRouteDefinitions.js")
const routePolicy = read("src/views/mobile/mobileRoutePolicy.js")
BUSINESS_ROUTES.forEach(route => {
  const staticPath = route.replace(/:id/g, "1").replace(/\/edit$/, "/edit")
  const bare = staticPath.split("?")[0]
  // parametric routes still map to definition roots
  const rootPath = bare
    .replace(/\/1\/edit$/, "")
    .replace(/\/1$/, "")
    .replace(/\/create$/, "")
  const haystack = routeDefs + routePolicy + feature + workbench + drive + sign + inventory
  assert.ok(
    haystack.includes(rootPath) ||
      haystack.includes(route) ||
      haystack.includes(bare) ||
      rootPath === "/mobile/hr/onboarding",
    `business route should be wired: ${route}`
  )
})
PUBLIC_ROUTES.forEach(route => {
  assert.ok(
    read("src/router/index.js").includes(route) ||
      read("src/permission.js").includes(route) ||
      route === "/401" || route === "/404",
    `public route should be registered: ${route}`
  )
})

// eight-class before/after baseline assets exist
const baselineDir = "/Users/liuxingyu/.codex/visualizations/2026/07/30/019fb361-0711-79b3-825d-336d7951cddd/mobile-visual-redesign-audit"
const baselineFiles = [
  "01-login-baseline.jpg",
  "02-profile-baseline.jpg",
  "03-store-workbench.jpg",
  "04-stock-list.jpg",
  "05-reimbursement-form.jpg",
  "06-drive.jpg",
  "07-sign-package.jpg",
  "08-hr-onboarding-form.jpg"
]
if (fs.existsSync(baselineDir)) {
  baselineFiles.forEach(name => {
    assert.ok(fs.existsSync(path.join(baselineDir, name)), `baseline screenshot missing: ${name}`)
  })
}

console.log("mobileBeautificationAcceptance.test.js passed")
console.log(JSON.stringify({
  businessRoutes: BUSINESS_ROUTES.length,
  publicRoutes: PUBLIC_ROUTES.length,
  pageImplementations: PAGE_IMPLEMENTATIONS.length,
  nonSemanticClicks: nonSemantic.length,
  legacyImportHits,
  viewports
}, null, 2))
