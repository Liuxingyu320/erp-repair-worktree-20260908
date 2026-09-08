const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const desktopSystem = read("src/assets/styles/desktop-system.scss")
const elementTheme = read("src/assets/styles/element-variables.scss")
const layout = read("src/layout/index.vue")
const navbar = read("src/layout/components/Navbar.vue")
const settings = read("src/store/modules/settings.js")
const themePicker = read("src/components/ThemePicker/index.vue")
const home = read("src/views/index.vue")
const login = read("src/views/login.vue")
const selectShop = read("src/views/select-shop/index.vue")
const drive = read("src/views/drive/index.vue")
const driveSidebar = read("src/views/drive/components/DriveSpaceSidebar.vue")
const employeeList = read("src/views/hr/components/HrEmployeeList.vue")
const employeeDrawer = read("src/views/hr/components/HrProfileDetailDrawer.vue")
const warehouseSystem = read("src/styles/warehouse-management-page.scss")
const packageJson = JSON.parse(read("package.json"))

function section(source, start, end) {
  const startIndex = source.indexOf(start)
  assert.ok(startIndex >= 0, `expected section start: ${start}`)
  const endIndex = source.indexOf(end, startIndex)
  assert.ok(endIndex > startIndex, `expected section end: ${end}`)
  return source.slice(startIndex, endIndex)
}

;[
  "--erp-primary: #252421",
  "--erp-canvas: #f3f0ea",
  "--erp-canvas-top: #faf8f4",
  "--erp-surface: #fffdf9",
  "--erp-text: #242320",
  "--erp-shadow-card-hover:",
  "--erp-focus-ring: 0 0 0 3px rgba(37, 36, 33, 0.15)",
  "@media (prefers-contrast: more)"
].forEach(contract => {
  assert.ok(desktopSystem.includes(contract), `desktop system should define ${contract}`)
})

assert.ok(
  /@media screen and \(min-width:\s*992px\)/.test(desktopSystem) &&
    desktopSystem.includes(".app-wrapper:not(.mobile)"),
  "desktop visual rules should remain isolated from the mobile application shell"
)

;[
  ".app-container",
  ".search-card",
  ".table-card",
  ".el-form-item__label",
  ".el-button--primary",
  ".el-table",
  ".el-tabs__active-bar",
  ".pagination-container",
  ".el-dialog",
  ".el-drawer",
  ".el-empty"
].forEach(selector => {
  assert.ok(desktopSystem.includes(selector), `desktop visual system should style ${selector}`)
})

assert.ok(
  /@media \(prefers-reduced-motion:\s*reduce\)/.test(desktopSystem),
  "desktop visual system should preserve its reduced-motion fallback"
)

assert.ok(
  elementTheme.includes("$--color-primary: #0b6b53") &&
    elementTheme.includes("$--border-color-light: #dde2de"),
  "Element UI should compile from the desktop tea-green and warm-neutral foundation"
)

assert.ok(
  settings.includes("theme: storageSetting.theme || '#0B6B53'") &&
    themePicker.includes("const ORIGINAL_THEME = '#0B6B53'"),
  "runtime theme defaults and the picker should agree with the compiled primary color"
)

;[
  "desktop-system.scss",
  "system-management.scss",
  "desktop-inventory.scss",
  "oa-workspace.scss"
].forEach(file => {
  assert.ok(layout.includes(file), `desktop layout should load ${file}`)
})

assert.ok(
  navbar.includes("<strong>BossERP</strong>") &&
    navbar.includes("background: rgba(255, 253, 249, 0.98)") &&
    navbar.includes("color: var(--erp-text, #242320)") &&
    !navbar.includes("#61dafb"),
  "the persistent desktop chrome should carry the warm-neutral BossERP login language"
)

assert.ok(
  !home.includes("radial-gradient") &&
    !home.includes("backdrop-filter") &&
    !home.includes("transform: translateY(-1px)") &&
    home.includes("url(\"~@/assets/images/desktop-login-tea-room-bg.jpg\")") &&
    home.includes("--home-primary: #252421") &&
    home.includes(".summary-card::before { display: none") &&
    home.includes("background-size: 30px 30px") &&
    home.includes(".summary-card:hover .summary-arrow"),
  "the dashboard should reuse the login atmosphere with restrained neutral feedback"
)

assert.ok(
  desktopSystem.includes(".app-main::before") && desktopSystem.includes("background-size: 32px 32px"),
  "desktop business pages should share the restrained grid-backed canvas"
)

const desktopLogin = section(login, ".login.is-desktop-web", ".mobile-auth-shell")
assert.ok(
  !desktopLogin.includes("radial-gradient") &&
    !desktopLogin.includes("backdrop-filter") &&
    !desktopLogin.includes("transform: translateY(-1px)") &&
    desktopLogin.includes("background: #252421") &&
    desktopLogin.includes("desktop-login-workspace-neutral-v3.jpg") &&
    !desktopLogin.includes("#0b6b53") &&
    login.includes("desktop-operations-grid") &&
    login.includes("@keyframes desktop-form-enter") &&
    login.includes("@media (prefers-reduced-motion: reduce)"),
  "desktop login should keep the approved neutral split entry without the rejected green treatment"
)

const desktopShop = section(selectShop, ".select-shop-page.is-desktop-web", ".mobile-shop-shell")
assert.ok(
  !desktopShop.includes("radial-gradient") &&
    !desktopShop.includes("backdrop-filter") &&
    !desktopShop.includes("transform: translateY(-1px)") &&
    desktopShop.includes("background: #252421") &&
    desktopShop.includes("rgba(255, 253, 249, 0.97)"),
  "desktop organization selection should continue the warm-neutral login atmosphere"
)

for (const [name, source] of [
  ["cloud drive", drive + driveSidebar],
  ["HR employee workspace", employeeList],
  ["HR employee drawer", employeeDrawer],
  ["warehouse management", warehouseSystem]
]) {
  assert.ok(!source.includes("radial-gradient"), `${name} should not use decorative radial backgrounds`)
  assert.ok(!source.includes("backdrop-filter"), `${name} should not require backdrop blur`)
  assert.ok(!source.includes("transform: translateY(-1px)"), `${name} should not lift high-frequency controls on hover`)
}

assert.ok(
  !Object.prototype.hasOwnProperty.call(packageJson.dependencies || {}, "gsap") &&
    !Object.prototype.hasOwnProperty.call(packageJson.devDependencies || {}, "gsap"),
  "the ERP should stay CSS-first until a real timeline or scroll choreography requires GSAP"
)

console.log("desktop visual system tests passed")
