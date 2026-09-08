const assert = require("assert")
const fs = require("fs")
const os = require("os")
const path = require("path")
const { spawnSync } = require("child_process")

const rootDir = path.resolve(__dirname, "..")
const checkerPath = path.join(rootDir, "scripts/check-production-entrypoint-budget.cjs")
const {
  DEFAULT_HARD_LIMIT_KIB,
  DEFAULT_TARGET_KIB,
  entryAssetNames,
  evaluateBudget,
  measureEntrypoint
} = require(checkerPath)

assert.strictEqual(DEFAULT_TARGET_KIB, 1600, "the release target should retain at least 100 KiB of headroom")
assert.strictEqual(DEFAULT_HARD_LIMIT_KIB, 1700, "the configured webpack hard limit must not be raised")
assert.deepStrictEqual(
  entryAssetNames({ entrypoints: { app: { assets: ["app.js", { name: "app.css" }, "app.js", "logo.png"] } } }, "app"),
  ["app.js", "app.css"],
  "entrypoint assets should be normalized, deduplicated, and limited to JavaScript/CSS"
)
assert.deepStrictEqual(evaluateBudget(1600, 1600, 1700), { passed: true, status: "pass" })
assert.deepStrictEqual(evaluateBudget(1601, 1600, 1700), { passed: false, status: "target_exceeded" })
assert.deepStrictEqual(evaluateBudget(1701, 1600, 1700), { passed: false, status: "hard_limit_exceeded" })

const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), "erp-entry-budget-"))
try {
  const distDir = path.join(tempRoot, "dist")
  fs.mkdirSync(path.join(distDir, "static/js"), { recursive: true })
  fs.mkdirSync(path.join(distDir, "static/css"), { recursive: true })
  fs.writeFileSync(path.join(distDir, "static/js/app.test.js"), "a".repeat(512))
  fs.writeFileSync(path.join(distDir, "static/css/app.test.css"), "b".repeat(256))
  fs.writeFileSync(
    path.join(distDir, "index.html"),
    "<html><body><script>window.location.href='/legacy'</script>" +
      "<script>window.webpackJsonp=[];" + "r".repeat(128) + "</script></body></html>"
  )
  const report = {
    entrypoints: {
      app: {
        assets: [
          "static/js/runtime.test.js",
          "static/js/app.test.js",
          { name: "static/css/app.test.css" }
        ]
      }
    }
  }
  const reportPath = path.join(distDir, "report.json")
  fs.writeFileSync(reportPath, JSON.stringify(report))

  const measurement = measureEntrypoint({ stats: report, distDir, entryName: "app" })
  assert.strictEqual(measurement.assets.length, 3, "the inlined runtime must remain part of the measured entrypoint")
  assert.strictEqual(measurement.assets[0].type, "inline-runtime")
  assert.strictEqual(measurement.rawBytes, 512 + 256 + Buffer.byteLength(`window.webpackJsonp=[];${"r".repeat(128)}`))
  assert.ok(measurement.gzipBytes > 0 && measurement.gzipBytes < measurement.rawBytes)

  const hardFailure = spawnSync(process.execPath, [
    checkerPath,
    "--report", reportPath,
    "--target-kib", "0.5",
    "--max-kib", "0.75"
  ], { encoding: "utf8" })
  assert.notStrictEqual(hardFailure.status, 0, "the checker must fail closed above the 1700-KiB-equivalent hard limit")
  assert.ok(hardFailure.stdout.includes("hard_limit_exceeded"))

  const passingRun = spawnSync(process.execPath, [
    checkerPath,
    "--report", reportPath,
    "--target-kib", "2",
    "--max-kib", "3"
  ], { encoding: "utf8" })
  assert.strictEqual(passingRun.status, 0, passingRun.stderr)
  assert.ok(passingRun.stdout.includes("status=pass"))
} finally {
  fs.rmSync(tempRoot, { recursive: true, force: true })
}

const mainSource = fs.readFileSync(path.join(rootDir, "src/main.js"), "utf8")
const elementPluginSource = fs.readFileSync(path.join(rootDir, "src/plugins/element-ui.js"), "utf8")
const sourceFilesWithRootElementImport = []

function visitDirectory(directory) {
  fs.readdirSync(directory, { withFileTypes: true }).forEach(entry => {
    const fullPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      visitDirectory(fullPath)
    } else if (/\.(?:js|vue)$/.test(entry.name)) {
      const source = fs.readFileSync(fullPath, "utf8")
      if (/from\s+["']element-ui["']/.test(source) || /require\(["']element-ui["']\)/.test(source)) {
        sourceFilesWithRootElementImport.push(path.relative(rootDir, fullPath))
      }
    }
  })
}

visitDirectory(path.join(rootDir, "src"))
assert.deepStrictEqual(
  sourceFilesWithRootElementImport,
  [],
  "root element-ui imports pull the full prebundled library into the production entrypoint"
)
assert.ok(mainSource.includes("installElementUI(Vue"), "the curated Element component installer should run before app mount")
assert.ok(
  elementPluginSource.includes("chunk-element-form-advanced") &&
    elementPluginSource.includes("chunk-element-data") &&
    elementPluginSource.includes("ElDatePicker") &&
    elementPluginSource.includes("ElCascader"),
  "large feature-only Element controls should retain explicit async form and data chunks"
)

console.log("productionEntrypointBudget tests passed")
