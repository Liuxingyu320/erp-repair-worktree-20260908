const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const vueConfig = require(path.join(rootDir, "vue.config.js"))
const routerSource = fs.readFileSync(path.join(rootDir, "src/router/index.js"), "utf8")
const routePermissionSource = fs.readFileSync(path.join(rootDir, "src/store/modules/permission.js"), "utf8")
const maxLoginBackgroundBytes = 244 * 1024
const maxCompressedAssetBytes = 244 * 1024
const maxRawAsyncAssetBytes = 800 * 1024
const maxRawEntrypointBytes = 1536 * 1024
const sourceLoginBackgrounds = [
  "src/assets/images/desktop-login-tea-room-bg.jpg",
  "src/assets/images/login-background.jpg"
]

assert.deepStrictEqual(
  vueConfig.configureWebpack.performance,
  {
    maxAssetSize: maxRawAsyncAssetBytes,
    maxEntrypointSize: maxRawEntrypointBytes
  },
  "production webpack performance hints should use the project raw budget while gzip assets are tested separately"
)

const budgetSource = fs.readFileSync(path.join(rootDir, "scripts/bundle-budget.cjs"), "utf8")
const buildBudgetCheckSource = fs.readFileSync(
  path.join(rootDir, "scripts/check-production-bundle.cjs"),
  "utf8"
)
assert.ok(
  budgetSource.includes("DEFAULT_SHARED_COLD_GZIP_LIMIT_BYTES") &&
    budgetSource.includes("DEFAULT_INITIAL_REQUEST_LIMIT") &&
    buildBudgetCheckSource.includes("measureSharedColdPath"),
  "production budget should enforce shared cold-path gzip bytes and initial request count"
)

assert.ok(
  routerSource.includes("const Layout = () => import(/* webpackChunkName: \"chunk-desktop-layout\" */ '@/layout')") &&
    !routerSource.includes("import Layout from '@/layout'"),
  "desktop layout should be lazy-loaded so login and mobile entry routes do not download desktop shell code up front"
)

assert.ok(
  routePermissionSource.includes("const Layout = () => import(/* webpackChunkName: \"chunk-desktop-layout\" */ '@/layout/index')") &&
    !routePermissionSource.includes("import Layout from '@/layout/index'"),
  "dynamic route conversion should preserve the lazy desktop layout boundary"
)

sourceLoginBackgrounds.forEach(relativePath => {
  const fullPath = path.join(rootDir, relativePath)
  const size = fs.statSync(fullPath).size
  assert.ok(
    size <= maxLoginBackgroundBytes,
    `${relativePath} should stay under the webpack asset warning budget (${size} bytes)`
  )
})

const distImageDir = path.join(rootDir, "dist/static/img")
if (fs.existsSync(distImageDir)) {
  const distLoginBackgrounds = fs.readdirSync(distImageDir)
    .filter(file => /^desktop-login-tea-room-bg\..*\.jpg$/.test(file) || /^login-background\..*\.jpg$/.test(file))

  distLoginBackgrounds.forEach(file => {
    const fullPath = path.join(distImageDir, file)
    const size = fs.statSync(fullPath).size
    assert.ok(
      size <= maxLoginBackgroundBytes,
      `${file} should stay under the webpack asset warning budget (${size} bytes)`
    )
  })

  fs.readdirSync(path.join(rootDir, "dist/static/js"))
    .filter(file => /\.js\.gz$/.test(file))
    .forEach(file => {
      const fullPath = path.join(rootDir, "dist/static/js", file)
      const size = fs.statSync(fullPath).size
      assert.ok(
        size <= maxCompressedAssetBytes,
        `${file} should stay under the compressed transfer budget (${size} bytes)`
      )
    })

  fs.readdirSync(path.join(rootDir, "dist/static/css"))
    .filter(file => /\.css\.gz$/.test(file))
    .forEach(file => {
      const fullPath = path.join(rootDir, "dist/static/css", file)
      const size = fs.statSync(fullPath).size
      assert.ok(
        size <= maxCompressedAssetBytes,
        `${file} should stay under the compressed transfer budget (${size} bytes)`
      )
    })
}

console.log("productionAssetBudget tests passed")
