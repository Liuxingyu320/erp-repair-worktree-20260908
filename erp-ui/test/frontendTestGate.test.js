const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const packageJson = JSON.parse(fs.readFileSync(path.join(rootDir, "package.json"), "utf8"))
const packageLockJson = JSON.parse(fs.readFileSync(path.join(rootDir, "package-lock.json"), "utf8"))
const uiGitignore = fs.readFileSync(path.join(rootDir, ".gitignore"), "utf8")
const frontendToolchainPath = path.resolve(rootDir, "../docs/frontend-toolchain.md")
const vue2RiskDocumentPath = path.resolve(rootDir, "../docs/security/frontend-vue2-low-risk-advisory.md")
const dashboardChartFiles = [
  "src/views/dashboard/BarChart.vue",
  "src/views/dashboard/LineChart.vue",
  "src/views/dashboard/PieChart.vue",
  "src/views/dashboard/RaddarChart.vue"
]
const dashboardChartSources = dashboardChartFiles.map(file => [
  file,
  fs.readFileSync(path.join(rootDir, file), "utf8")
])

function listNumberedCopies(directory, relativeDirectory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const relativePath = path.join(relativeDirectory, entry.name)
    if (entry.isDirectory()) {
      return listNumberedCopies(path.join(directory, entry.name), relativePath)
    }
    return / [2-9]\.[^/]+$/.test(entry.name) ? [relativePath] : []
  })
}

const rootNumberedCopies = fs.readdirSync(rootDir, { withFileTypes: true })
  .filter(entry => entry.isFile() && / [2-9]\.[^/]+$/.test(entry.name))
  .map(entry => entry.name)
const nonNativeNumberedCopies = rootNumberedCopies
  .concat(listNumberedCopies(path.join(rootDir, "src"), "src"))
  .concat(listNumberedCopies(path.join(rootDir, "test"), "test"))
  .sort()

assert.strictEqual(
  packageJson.scripts.test,
  "node scripts/run-node-tests.cjs",
  "npm test should run the full committed Node test suite"
)

assert.strictEqual(
  packageJson.scripts["app:verify"],
  "node scripts/harden-capacitor-native-config.cjs && npm run test && node scripts/harden-capacitor-native-config.cjs && node scripts/verify-capacitor-sync.cjs",
  "app verification should clean native copies around the full test gate and native asset sync verification"
)

assert.strictEqual(
  packageJson.scripts["audit:prod"],
  "npm audit --omit=dev --audit-level=moderate --registry=https://registry.npmjs.org",
  "production dependency audit should fail on moderate or higher vulnerabilities"
)

assert.strictEqual(
  packageJson.dependencies.echarts,
  "6.1.0",
  "ECharts should be upgraded past the vulnerable 5.x line"
)

assert.strictEqual(
  packageJson.engines.node,
  ">=22 <25",
  "front-end toolchain should pin the supported Node range around Capacitor 8 and Vue CLI 4"
)

assert.strictEqual(
  fs.readFileSync(path.join(rootDir, ".nvmrc"), "utf8").trim(),
  "22.23.1",
  "front-end workspace should pin the verified Node 22 LTS patch release"
)

assert.strictEqual(
  packageJson.packageManager,
  "npm@10.9.8",
  "front-end workspace should pin the npm version paired with the default Node toolchain"
)

assert.ok(
  fs.existsSync(frontendToolchainPath) &&
    fs.readFileSync(frontendToolchainPath, "utf8").includes("@achrinza/node-ipc"),
  "front-end toolchain document should explain the remaining Vue CLI transitive engine warning"
)

assert.strictEqual(
  packageLockJson.packages[""].dependencies.echarts,
  "6.1.0",
  "package-lock should pin ECharts to the audited version"
)

assert.ok(
  fs.existsSync(path.join(rootDir, "scripts/run-node-tests.cjs")),
  "the full test gate runner should exist"
)

assert.ok(
  fs.existsSync(path.join(rootDir, "package-lock.json")) &&
    !uiGitignore.split(/\r?\n/).some(line => line.trim() === "package-lock.json"),
  "front-end dependency lockfile should be available for version control"
)

assert.ok(
  fs.existsSync(path.join(rootDir, "src/utils/echarts.js")),
  "dashboard charts should share a tree-shakeable ECharts registration module"
)

assert.ok(
  fs.existsSync(vue2RiskDocumentPath) &&
    fs.readFileSync(vue2RiskDocumentPath, "utf8").includes("GHSA-5j4c-8p2g-v4jx"),
  "remaining Vue 2 low-severity advisory should be documented as a migration-track risk"
)

dashboardChartSources.forEach(([file, source]) => {
  assert.ok(
    source.includes('import echarts from "@/utils/echarts"') &&
      !source.includes("import * as echarts from 'echarts'") &&
      !source.includes('import * as echarts from "echarts"') &&
      !source.includes("echarts/theme/macarons"),
    `${file} should use the shared tree-shakeable ECharts module`
  )
})

assert.deepStrictEqual(
  nonNativeNumberedCopies,
  [],
  "numbered Finder-style copies should not remain in web source, tests, or root files"
)

console.log("frontendTestGate tests passed")
