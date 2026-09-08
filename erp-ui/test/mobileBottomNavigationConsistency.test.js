const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const readFile = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const sharedStyles = readFile("src/views/mobile/styles/mobileSystem.scss")
const workbenchSource = readFile("src/views/mobile/components/MobileWorkbenchShell.vue")
const navigationPages = [
  "src/views/mobile/components/MobileWorkbenchShell.vue",
  "src/views/mobile/feature/index.vue",
  "src/views/mobile/inventory/index.vue",
  "src/views/mobile/profile/index.vue",
  "src/views/mobile/hr/components/MobileHrShell.vue"
]

navigationPages.forEach(relativePath => {
  assert.ok(
    readFile(relativePath).includes("mobile-system-bottom-nav"),
    `${relativePath} should use the shared mobile bottom-navigation contract`
  )
})

assert.ok(
  sharedStyles.includes("html body .mobile-system-page .mobile-system-bottom-nav {") &&
    sharedStyles.includes("grid-template-columns: repeat(auto-fit, minmax(0, 1fr));") &&
    sharedStyles.includes("min-height: var(--mobile-bottom-nav-total);") &&
    sharedStyles.includes("font-size: var(--mobile-font-label);") &&
    sharedStyles.includes("flex: 0 0 23px;") &&
    sharedStyles.includes("background: transparent;") &&
    sharedStyles.includes("html body.mobile-task-mode .mobile-system-page .mobile-system-bottom-nav"),
  "all mobile shells should inherit one navigation width, button typography, icon size and active treatment"
)

const { getMobileBottomNav, getMobileHrBottomNav } = require("../src/views/mobile/mobileNavigation")
const navigationIcons = new Set([
  ...getMobileBottomNav("STORE", ["*:*:*"]).map(item => item.icon),
  ...getMobileBottomNav("WAREHOUSE", ["*:*:*"]).map(item => item.icon),
  ...getMobileHrBottomNav(["*:*:*"]).map(item => item.icon)
])

navigationIcons.forEach(icon => {
  assert.ok(
    workbenchSource.includes(`${icon}: \"`),
    `the workbench icon registry should render the ${icon} navigation icon without falling back to inventory`
  )
})

console.log("mobile bottom navigation consistency tests passed")
