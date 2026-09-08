#!/usr/bin/env node

const fs = require("fs")
const path = require("path")
const zlib = require("zlib")

const KIB = 1024
const DEFAULT_TARGET_KIB = 1600
const DEFAULT_HARD_LIMIT_KIB = 1700

function fail(message) {
  throw new Error(message)
}

function parsePositiveNumber(value, label) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    fail(`${label} must be a positive number`)
  }
  return parsed
}

function parseArgs(argv) {
  const options = {
    reportPath: process.env.ENTRYPOINT_BUDGET_REPORT || path.join("dist", "report.json"),
    entryName: process.env.ENTRYPOINT_BUDGET_ENTRY || "app",
    targetKiB: parsePositiveNumber(
      process.env.ENTRYPOINT_BUDGET_TARGET_KIB || DEFAULT_TARGET_KIB,
      "target KiB"
    ),
    hardLimitKiB: parsePositiveNumber(
      process.env.ENTRYPOINT_BUDGET_HARD_LIMIT_KIB || DEFAULT_HARD_LIMIT_KIB,
      "hard-limit KiB"
    ),
    summaryPath: process.env.ENTRYPOINT_BUDGET_SUMMARY || ""
  }

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index]
    const nextValue = () => {
      index += 1
      if (index >= argv.length || argv[index].startsWith("--")) {
        fail(`${argument} requires a value`)
      }
      return argv[index]
    }

    if (argument === "--report") {
      options.reportPath = nextValue()
    } else if (argument === "--entry") {
      options.entryName = nextValue()
    } else if (argument === "--target-kib") {
      options.targetKiB = parsePositiveNumber(nextValue(), "target KiB")
    } else if (argument === "--max-kib" || argument === "--hard-limit-kib") {
      options.hardLimitKiB = parsePositiveNumber(nextValue(), "hard-limit KiB")
    } else if (argument === "--summary") {
      options.summaryPath = nextValue()
    } else {
      fail(`unknown argument: ${argument}`)
    }
  }

  if (!options.entryName || /[\\/]/.test(options.entryName)) {
    fail("entry name must be a non-empty webpack entrypoint name")
  }
  if (options.targetKiB > options.hardLimitKiB) {
    fail("target KiB cannot exceed the hard-limit KiB")
  }
  return options
}

function normalizeAssetName(asset) {
  const value = typeof asset === "string" ? asset : asset && asset.name
  if (typeof value !== "string" || value.length === 0) {
    return null
  }
  return value.replace(/^\/+/, "").split("?")[0]
}

function entryAssetNames(stats, entryName) {
  const entry = stats && stats.entrypoints && stats.entrypoints[entryName]
  if (!entry || !Array.isArray(entry.assets)) {
    fail(`webpack report does not contain entrypoints.${entryName}.assets`)
  }
  return Array.from(new Set(entry.assets
    .map(normalizeAssetName)
    .filter(Boolean)
    .filter(name => /\.(?:js|css)$/i.test(name))))
}

function safeAssetPath(distDir, assetName) {
  const root = path.resolve(distDir)
  const resolved = path.resolve(root, assetName)
  if (resolved !== root && !resolved.startsWith(`${root}${path.sep}`)) {
    fail(`entrypoint asset escapes the build directory: ${assetName}`)
  }
  return resolved
}

function findInlineRuntime(distDir) {
  const indexPath = path.join(distDir, "index.html")
  if (!fs.existsSync(indexPath)) {
    fail("webpack runtime asset is inlined but dist/index.html is missing")
  }
  const html = fs.readFileSync(indexPath, "utf8")
  const candidates = []
  const scriptPattern = /<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi
  let match
  while ((match = scriptPattern.exec(html)) !== null) {
    const source = match[1].trim()
    if (source.includes("webpackJsonp") || source.includes("__webpack_require__")) {
      candidates.push(source)
    }
  }
  if (candidates.length === 0) {
    fail("could not locate the inlined webpack runtime in dist/index.html")
  }
  return Buffer.from(candidates.sort((left, right) => right.length - left.length)[0], "utf8")
}

function gzipSize(buffer, gzipPath) {
  if (gzipPath && fs.existsSync(gzipPath)) {
    return fs.statSync(gzipPath).size
  }
  return zlib.gzipSync(buffer, { level: zlib.constants.Z_BEST_COMPRESSION }).length
}

function measureEntrypoint({ stats, distDir, entryName }) {
  let inlineRuntime
  const assets = entryAssetNames(stats, entryName).map(assetName => {
    const assetPath = safeAssetPath(distDir, assetName)
    let source
    let sourceType = "file"

    if (fs.existsSync(assetPath)) {
      source = fs.readFileSync(assetPath)
    } else if (/^static\/js\/runtime\.[^/]+\.js$/i.test(assetName)) {
      inlineRuntime = inlineRuntime || findInlineRuntime(distDir)
      source = inlineRuntime
      sourceType = "inline-runtime"
    } else {
      fail(`entrypoint asset is missing from the build output: ${assetName}`)
    }

    return {
      name: assetName,
      type: sourceType,
      rawBytes: source.length,
      gzipBytes: gzipSize(source, sourceType === "file" ? `${assetPath}.gz` : "")
    }
  })

  return {
    assets,
    rawBytes: assets.reduce((total, asset) => total + asset.rawBytes, 0),
    gzipBytes: assets.reduce((total, asset) => total + asset.gzipBytes, 0)
  }
}

function evaluateBudget(rawBytes, targetBytes, hardLimitBytes) {
  if (rawBytes > hardLimitBytes) {
    return { passed: false, status: "hard_limit_exceeded" }
  }
  if (rawBytes > targetBytes) {
    return { passed: false, status: "target_exceeded" }
  }
  return { passed: true, status: "pass" }
}

function formatKiB(bytes) {
  return (bytes / KIB).toFixed(2)
}

function createSummary({ reportPath, entryName, targetBytes, hardLimitBytes, measurement, result }) {
  return {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    entry: entryName,
    report: path.relative(process.cwd(), reportPath) || path.basename(reportPath),
    thresholds: {
      targetBytes,
      hardLimitBytes,
      targetKiB: Number(formatKiB(targetBytes)),
      hardLimitKiB: Number(formatKiB(hardLimitBytes))
    },
    totals: {
      rawBytes: measurement.rawBytes,
      gzipBytes: measurement.gzipBytes,
      rawKiB: Number(formatKiB(measurement.rawBytes)),
      gzipKiB: Number(formatKiB(measurement.gzipBytes))
    },
    status: result.status,
    assets: measurement.assets
  }
}

function run(argv = process.argv.slice(2)) {
  const options = parseArgs(argv)
  const reportPath = path.resolve(options.reportPath)
  if (!fs.existsSync(reportPath)) {
    fail(`webpack report does not exist: ${options.reportPath}`)
  }
  const stats = JSON.parse(fs.readFileSync(reportPath, "utf8"))
  const targetBytes = Math.round(options.targetKiB * KIB)
  const hardLimitBytes = Math.round(options.hardLimitKiB * KIB)
  const measurement = measureEntrypoint({
    stats,
    distDir: path.dirname(reportPath),
    entryName: options.entryName
  })
  const result = evaluateBudget(measurement.rawBytes, targetBytes, hardLimitBytes)
  const summary = createSummary({
    reportPath,
    entryName: options.entryName,
    targetBytes,
    hardLimitBytes,
    measurement,
    result
  })

  console.log(
    `[entry-budget] ${options.entryName} raw=${formatKiB(measurement.rawBytes)} KiB ` +
    `gzip=${formatKiB(measurement.gzipBytes)} KiB target=${options.targetKiB} KiB ` +
    `hard-limit=${options.hardLimitKiB} KiB status=${result.status}`
  )
  measurement.assets.forEach(asset => {
    console.log(
      `[entry-budget] ${asset.name} raw=${formatKiB(asset.rawBytes)} KiB ` +
      `gzip=${formatKiB(asset.gzipBytes)} KiB type=${asset.type}`
    )
  })

  if (options.summaryPath) {
    const summaryPath = path.resolve(options.summaryPath)
    fs.mkdirSync(path.dirname(summaryPath), { recursive: true })
    fs.writeFileSync(summaryPath, `${JSON.stringify(summary, null, 2)}\n`, { mode: 0o600 })
  }
  if (!result.passed) {
    process.exitCode = 1
  }
  return summary
}

if (require.main === module) {
  try {
    run()
  } catch (error) {
    console.error(`[entry-budget] ERROR: ${error.message}`)
    process.exitCode = 1
  }
}

module.exports = {
  DEFAULT_HARD_LIMIT_KIB,
  DEFAULT_TARGET_KIB,
  entryAssetNames,
  evaluateBudget,
  formatKiB,
  measureEntrypoint,
  parseArgs,
  run
}
