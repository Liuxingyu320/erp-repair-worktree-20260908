const assert = require("assert")
const fs = require("fs")
const os = require("os")
const path = require("path")

const {
  apiPathMatches,
  isNonProductionApiPath,
  validateProductionDist
} = require("../../scripts/mobile-ios-prod-static-server.cjs")

const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), "erp-lan-dist-"))

function writeDist(name, apiPrefix, options = {}) {
  const dist = path.join(tempRoot, name)
  const scripts = path.join(dist, "static/js")
  fs.mkdirSync(scripts, { recursive: true })
  const scriptTag = options.unquotedScript
    ? '<script defer src=/static/js/app.js></script>'
    : '<script src="/static/js/app.js"></script>'
  fs.writeFileSync(path.join(dist, "index.html"), `<!doctype html>${scriptTag}`)
  fs.writeFileSync(path.join(scripts, "app.js"), `const apiBase = ${JSON.stringify(apiPrefix)}`)
  if (options.releaseInfo !== false) {
    fs.writeFileSync(path.join(dist, "release-info.json"), JSON.stringify({
      commit: "0123456789abcdef",
      buildTime: "2026-08-05T00:00:00Z"
    }))
  }
  return dist
}

try {
  const productionDist = writeDist("production", "/prod-api")
  const validation = validateProductionDist(productionDist)
  assert.strictEqual(validation.releaseInfo.commit, "0123456789abcdef")

  const minifiedProductionDist = writeDist("production-unquoted", "/prod-api", { unquotedScript: true })
  assert.strictEqual(
    validateProductionDist(minifiedProductionDist).scriptFiles.length,
    1,
    "minified production HTML may use an unquoted script src"
  )

  const stagingDist = writeDist("staging", "/stage-api")
  assert.throws(
    () => validateProductionDist(stagingDist),
    /non-production API prefix detected in dist: \/stage-api/
  )

  const missingReleaseDist = writeDist("missing-release", "/prod-api", { releaseInfo: false })
  assert.throws(
    () => validateProductionDist(missingReleaseDist),
    /missing release-info\.json/
  )

  assert.strictEqual(apiPathMatches("/prod-api/code?refresh=1", "/prod-api"), true)
  assert.strictEqual(apiPathMatches("/prod-api-v2/code", "/prod-api"), false)
  assert.strictEqual(isNonProductionApiPath("/stage-api/code"), true)
  assert.strictEqual(isNonProductionApiPath("/dev-api/code"), true)
  assert.strictEqual(isNonProductionApiPath("/prod-api/code"), false)

  console.log("LAN production static-server guards passed")
} finally {
  fs.rmSync(tempRoot, { recursive: true, force: true })
}
