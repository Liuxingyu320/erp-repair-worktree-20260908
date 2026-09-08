const fs = require("fs")
const path = require("path")
const {
  DEFAULT_ENTRY_LIMIT_BYTES,
  measureSharedColdPath,
  summarizeStats,
  summarizeEntrypointGzipJs,
  assertWithinBudget
} = require("./bundle-budget.cjs")

const root = path.resolve(__dirname, "..")
const reportPath = path.join(root, "dist", "report.json")
const compactReportPath = path.join(root, "dist", "build-budget.json")

if (!fs.existsSync(reportPath)) {
  console.error("production bundle budget failed: dist/report.json 不存在")
  process.exit(1)
}

let summary
try {
  const stats = JSON.parse(fs.readFileSync(reportPath, "utf8"))
  const sharedColdPath = measureSharedColdPath(stats, path.join(root, "dist"))
  summary = summarizeStats(
    stats,
    DEFAULT_ENTRY_LIMIT_BYTES,
    undefined,
    undefined,
    sharedColdPath
  )
  summary.entrypointGzipJs = summarizeEntrypointGzipJs(summary, assetName => {
    const assetPath = path.join(root, "dist", assetName)
    if (!fs.existsSync(assetPath)) {
      throw new Error(`gzip 入口资源不存在: ${assetName}`)
    }
    return fs.statSync(assetPath).size
  })
  fs.writeFileSync(compactReportPath, `${JSON.stringify(summary, null, 2)}\n`)
  assertWithinBudget(summary)
} catch (error) {
  console.error(`production bundle budget failed: ${error.message}`)
  process.exit(1)
}

if (process.env.KEEP_WEBPACK_REPORT !== "true") {
  fs.unlinkSync(reportPath)
}

console.log(
  `production bundle budget passed: ${summary.mib} MiB / ` +
    `${(summary.limitBytes / 1024 / 1024).toFixed(3)} MiB; ` +
    `${summary.sharedColdPath.gzipKib} KiB gzip / ` +
    `${(summary.sharedColdPath.limitBytes / 1024).toFixed(0)} KiB; ` +
    `${summary.sharedColdPath.requests}/${summary.sharedColdPath.requestLimit} requests; ` +
    `gzip JS ${summary.entrypointGzipJs.kib} KiB / ` +
    `${(summary.entrypointGzipJs.limitBytes / 1024).toFixed(0)} KiB`
)
