const assert = require("assert")
const {
  DEFAULT_ENTRY_LIMIT_BYTES,
  DEFAULT_ENTRY_GZIP_JS_LIMIT_BYTES,
  DEFAULT_ASYNC_CHUNK_LIMIT_BYTES,
  DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES,
  DEFAULT_INITIAL_REQUEST_LIMIT,
  CRITICAL_ASYNC_ROUTES,
  summarizeStats,
  summarizeEntrypointGzipJs,
  assertWithinBudget
} = require("../scripts/bundle-budget.cjs")

function stats(size, warnings = []) {
  const routeAssets = CRITICAL_ASYNC_ROUTES.map((route, index) => ({
    name: `static/js/critical-${index}.js`,
    size: 2048,
    chunks: [`critical-${index}`]
  }))
  return {
    entrypoints: { app: { assets: ["static/js/app.js", "static/css/app.css"] } },
    assets: [
      { name: "static/js/app.js", size },
      { name: "static/css/app.css", size: 1024 },
      ...routeAssets
    ],
    modules: CRITICAL_ASYNC_ROUTES.map((route, index) => ({
      name: `${route.module} + test modules`,
      chunks: [`critical-${index}`]
    })),
    errors: [],
    warnings
  }
}

const passing = summarizeStats(stats(DEFAULT_ENTRY_LIMIT_BYTES - 1024))
passing.entrypointGzipJs = summarizeEntrypointGzipJs(
  passing,
  assetName => assetName.includes("app.js") ? DEFAULT_ENTRY_GZIP_JS_LIMIT_BYTES - 1 : 1
)
assert.strictEqual(passing.bytes, DEFAULT_ENTRY_LIMIT_BYTES)
assert.strictEqual(passing.withinBudget, true)
assert.strictEqual(passing.entrypointGzipJs.withinBudget, true)
assert.strictEqual(passing.criticalAsyncRoutes.length, CRITICAL_ASYNC_ROUTES.length)
assert.strictEqual(passing.criticalAsyncWithinBudget, true)
assert.doesNotThrow(() => assertWithinBudget(passing))

const passingColdPath = {
  gzipBytes: DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES,
  gzipKib: DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES / 1024,
  limitBytes: DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES,
  withinBudget: true,
  requests: DEFAULT_INITIAL_REQUEST_LIMIT,
  requestLimit: DEFAULT_INITIAL_REQUEST_LIMIT,
  requestsWithinBudget: true,
  htmlGzipBytes: 1024,
  assets: []
}
assert.doesNotThrow(() => assertWithinBudget(summarizeStats(
  stats(1024),
  DEFAULT_ENTRY_LIMIT_BYTES,
  DEFAULT_ASYNC_CHUNK_LIMIT_BYTES,
  CRITICAL_ASYNC_ROUTES,
  passingColdPath
)))

assert.throws(() => assertWithinBudget(summarizeStats(
  stats(1024),
  DEFAULT_ENTRY_LIMIT_BYTES,
  DEFAULT_ASYNC_CHUNK_LIMIT_BYTES,
  CRITICAL_ASYNC_ROUTES,
  { ...passingColdPath, gzipKib: 426, withinBudget: false }
)), /共享首启/)

assert.throws(() => assertWithinBudget(summarizeStats(
  stats(1024),
  DEFAULT_ENTRY_LIMIT_BYTES,
  DEFAULT_ASYNC_CHUNK_LIMIT_BYTES,
  CRITICAL_ASYNC_ROUTES,
  {
    ...passingColdPath,
    requests: DEFAULT_INITIAL_REQUEST_LIMIT + 1,
    requestsWithinBudget: false
  }
)), /请求/)

const oversizedGzip = summarizeStats(stats(1024))
oversizedGzip.entrypointGzipJs = summarizeEntrypointGzipJs(
  oversizedGzip,
  () => DEFAULT_ENTRY_GZIP_JS_LIMIT_BYTES + 1
)
assert.strictEqual(oversizedGzip.entrypointGzipJs.withinBudget, false)
assert.throws(() => assertWithinBudget(oversizedGzip), /gzip JavaScript/)

const oversized = summarizeStats(stats(DEFAULT_ENTRY_LIMIT_BYTES))
assert.strictEqual(oversized.withinBudget, false)
assert.throws(() => assertWithinBudget(oversized), /超过/)

const warned = summarizeStats(stats(1024, [
  "entrypoint size limit: app exceeds the recommended limit"
]))
assert.throws(() => assertWithinBudget(warned), /体积警告/)

const oversizedAsyncStats = stats(1024)
oversizedAsyncStats.assets.find(asset => asset.name === "static/js/critical-0.js").size =
  DEFAULT_ASYNC_CHUNK_LIMIT_BYTES + 1
const oversizedAsync = summarizeStats(oversizedAsyncStats)
assert.strictEqual(oversizedAsync.criticalAsyncWithinBudget, false)
assert.throws(() => assertWithinBudget(oversizedAsync), /关键异步分块/)

const missingRouteStats = stats(1024)
missingRouteStats.modules.pop()
assert.throws(() => summarizeStats(missingRouteStats), /缺少关键页面模块/)

console.log("production bundle budget checks passed")
