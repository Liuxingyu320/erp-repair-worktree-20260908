const fs = require("fs")
const path = require("path")
const zlib = require("zlib")

const DEFAULT_ENTRY_LIMIT_BYTES = 1536 * 1024
const DEFAULT_ENTRY_GZIP_JS_LIMIT_BYTES = 350 * 1024
const DEFAULT_ASYNC_CHUNK_LIMIT_BYTES = 192 * 1024
const DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES = 425 * 1024
const DEFAULT_INITIAL_REQUEST_LIMIT = 8
const CRITICAL_ASYNC_ROUTES = Object.freeze([
  { id: "health-certificate", module: "./src/views/hr/healthCertificate/index.vue" },
  { id: "customer-service-card", module: "./src/views/inventory/customer/index.vue" },
  { id: "transfer-workbench", module: "./src/views/inventory/transfer/index.vue" },
  { id: "mobile-customer-service-card", module: "./src/views/mobile/customer/index.vue" },
  { id: "mobile-health-certificate", module: "./src/views/mobile/hr/healthCertificate/index.vue" }
])

function messageText(value) {
  if (typeof value === "string") return value
  if (value && typeof value.message === "string") return value.message
  return JSON.stringify(value)
}

function assetName(value) {
  return typeof value === "string" ? value : value && value.name
}

function gzipSize(filePath) {
  const precompressedPath = `${filePath}.gz`
  if (fs.existsSync(precompressedPath)) {
    return fs.statSync(precompressedPath).size
  }
  return zlib.gzipSync(fs.readFileSync(filePath), { level: 9 }).length
}

function measureSharedColdPath(stats, distRoot) {
  const entrypoint = stats && stats.entrypoints && stats.entrypoints.app
  if (!entrypoint) throw new Error("webpack stats 缺少 app entrypoint")

  const assets = []
  ;(entrypoint.assets || []).forEach(value => {
    const name = assetName(value)
    if (!name || !/\.(?:css|js)$/.test(name)) return
    const filePath = path.join(distRoot, name)
    // Runtime can be inlined into index.html by ScriptExtHtmlWebpackPlugin.
    if (!fs.existsSync(filePath)) return
    assets.push({ name, gzipBytes: gzipSize(filePath) })
  })

  const htmlPath = path.join(distRoot, "index.html")
  if (!fs.existsSync(htmlPath)) {
    throw new Error("生产产物缺少 index.html")
  }
  const htmlGzipBytes = gzipSize(htmlPath)
  const gzipBytes = assets.reduce((total, asset) => total + asset.gzipBytes, htmlGzipBytes)

  return {
    gzipBytes,
    gzipKib: Number((gzipBytes / 1024).toFixed(1)),
    limitBytes: DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES,
    withinBudget: gzipBytes <= DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES,
    requests: assets.length + 1,
    requestLimit: DEFAULT_INITIAL_REQUEST_LIMIT,
    requestsWithinBudget: assets.length + 1 <= DEFAULT_INITIAL_REQUEST_LIMIT,
    htmlGzipBytes,
    assets
  }
}

function summarizeCriticalRoute(stats, route, limitBytes) {
  const matchingModules = (stats.modules || []).filter(module =>
    String(module.name || "").includes(route.module)
  )
  if (!matchingModules.length) {
    throw new Error(`webpack stats 缺少关键页面模块: ${route.module}`)
  }

  const chunks = new Set()
  matchingModules.forEach(module => {
    ;(module.chunks || []).forEach(chunk => chunks.add(String(chunk)))
  })
  if (!chunks.size) {
    throw new Error(`关键页面未生成异步分块: ${route.id}`)
  }

  const assetsByName = new Map()
  ;(stats.assets || []).forEach(asset => {
    const name = assetName(asset)
    const belongsToRoute = (asset.chunks || []).some(chunk => chunks.has(String(chunk)))
    if (name && /\.(?:css|js)$/.test(name) && belongsToRoute) {
      assetsByName.set(name, asset.size || 0)
    }
  })
  if (!assetsByName.size) {
    throw new Error(`webpack stats 缺少关键页面产物: ${route.id}`)
  }

  const assets = [...assetsByName].map(([name, size]) => ({ name, size }))
  const bytes = assets.reduce((total, asset) => total + asset.size, 0)
  return {
    id: route.id,
    module: route.module,
    limitBytes,
    bytes,
    kib: Number((bytes / 1024).toFixed(1)),
    withinBudget: bytes <= limitBytes,
    chunks: [...chunks].sort(),
    assets
  }
}

function summarizeStats(
  stats,
  limitBytes = DEFAULT_ENTRY_LIMIT_BYTES,
  asyncLimitBytes = DEFAULT_ASYNC_CHUNK_LIMIT_BYTES,
  criticalRoutes = CRITICAL_ASYNC_ROUTES,
  sharedColdPath = null
) {
  const entrypoint = stats && stats.entrypoints && stats.entrypoints.app
  if (!entrypoint) throw new Error("webpack stats 缺少 app entrypoint")

  const sizes = new Map((stats.assets || []).map(asset => [asset.name, asset.size || 0]))
  const assets = (entrypoint.assets || []).map(value => {
    const name = assetName(value)
    return { name, size: sizes.get(name) || 0 }
  })
  const bytes = assets.reduce((total, asset) => total + asset.size, 0)
  const errors = (stats.errors || []).map(messageText)
  const warnings = (stats.warnings || []).map(messageText)
  const performanceWarnings = warnings.filter(message =>
    /entrypoint size limit|asset size limit|exceeds the recommended limit/i.test(message)
  )
  const criticalAsyncRoutes = criticalRoutes.map(route =>
    summarizeCriticalRoute(stats, route, asyncLimitBytes)
  )

  return {
    entrypoint: "app",
    limitBytes,
    bytes,
    mib: Number((bytes / 1024 / 1024).toFixed(3)),
    withinBudget: bytes <= limitBytes,
    assets,
    criticalAsyncLimitBytes: asyncLimitBytes,
    criticalAsyncWithinBudget: criticalAsyncRoutes.every(route => route.withinBudget),
    criticalAsyncRoutes,
    sharedColdPath,
    errors,
    warnings,
    performanceWarnings
  }
}

function summarizeEntrypointGzipJs(
  summary,
  assetSize,
  limitBytes = DEFAULT_ENTRY_GZIP_JS_LIMIT_BYTES
) {
  if (typeof assetSize !== "function") {
    throw new Error("gzip 入口统计缺少资源大小读取器")
  }
  const assets = (summary.assets || [])
    .filter(asset => asset.size > 0 && /\.js$/.test(asset.name))
    .map(asset => {
      const gzipName = `${asset.name}.gz`
      const gzipBytes = Number(assetSize(gzipName))
      if (!Number.isFinite(gzipBytes) || gzipBytes < 0) {
        throw new Error(`gzip 入口资源大小无效: ${gzipName}`)
      }
      return {
        name: asset.name,
        gzipName,
        rawBytes: asset.size,
        gzipBytes
      }
    })
  if (!assets.length) {
    throw new Error("app 入口缺少可统计的外部 JavaScript")
  }
  const bytes = assets.reduce((total, asset) => total + asset.gzipBytes, 0)
  return {
    limitBytes,
    bytes,
    kib: Number((bytes / 1024).toFixed(1)),
    withinBudget: bytes <= limitBytes,
    assets
  }
}

function assertWithinBudget(summary) {
  if (summary.errors.length) {
    throw new Error(`webpack 构建包含 ${summary.errors.length} 个错误`)
  }
  if (!summary.withinBudget) {
    throw new Error(
      `app 入口 ${summary.mib} MiB 超过 ${(summary.limitBytes / 1024 / 1024).toFixed(3)} MiB 预算`
    )
  }
  if (!summary.criticalAsyncWithinBudget) {
    const oversized = summary.criticalAsyncRoutes
      .filter(route => !route.withinBudget)
      .map(route => `${route.id}=${route.kib} KiB`)
      .join(", ")
    throw new Error(
      `关键异步分块超过 ${(summary.criticalAsyncLimitBytes / 1024).toFixed(0)} KiB 预算: ${oversized}`
    )
  }
  if (summary.sharedColdPath && !summary.sharedColdPath.withinBudget) {
    throw new Error(
      `共享首启 ${summary.sharedColdPath.gzipKib} KiB gzip 超过 ` +
      `${(summary.sharedColdPath.limitBytes / 1024).toFixed(0)} KiB 预算`
    )
  }
  if (summary.sharedColdPath && !summary.sharedColdPath.requestsWithinBudget) {
    throw new Error(
      `共享首启 ${summary.sharedColdPath.requests} 个请求超过 ` +
      `${summary.sharedColdPath.requestLimit} 个请求预算`
    )
  }
  if (summary.entrypointGzipJs && !summary.entrypointGzipJs.withinBudget) {
    throw new Error(
      `app 入口 gzip JavaScript ${summary.entrypointGzipJs.kib} KiB 超过 ` +
        `${(summary.entrypointGzipJs.limitBytes / 1024).toFixed(0)} KiB 预算`
    )
  }
  if (summary.performanceWarnings.length) {
    throw new Error(`webpack 仍存在 ${summary.performanceWarnings.length} 条体积警告`)
  }
  return summary
}

module.exports = {
  DEFAULT_ENTRY_LIMIT_BYTES,
  DEFAULT_ENTRY_GZIP_JS_LIMIT_BYTES,
  DEFAULT_ASYNC_CHUNK_LIMIT_BYTES,
  DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES,
  DEFAULT_INITIAL_REQUEST_LIMIT,
  CRITICAL_ASYNC_ROUTES,
  measureSharedColdPath,
  summarizeStats,
  summarizeEntrypointGzipJs,
  assertWithinBudget
}
