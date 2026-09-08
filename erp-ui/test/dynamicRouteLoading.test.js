const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const permissionSource = fs.readFileSync(path.join(uiRoot, "src/store/modules/permission.js"), "utf8")
const babelConfigSource = fs.readFileSync(path.join(uiRoot, "babel.config.js"), "utf8")

assert.ok(
  permissionSource.includes("process.env.NODE_ENV === 'development'") &&
    permissionSource.includes("require([`@/views/${view}`], resolve)"),
  "development dynamic routes should use async require so webpack can resolve view modules"
)

assert.ok(
  permissionSource.includes("return () => import(`@/views/${view}`)"),
  "production dynamic routes should keep lazy import for route chunk splitting"
)

assert.ok(
  babelConfigSource.includes("development") &&
    babelConfigSource.includes("dynamic-import-node"),
  "development babel config should preserve dynamic-import-node for vue-cli route loading"
)

console.log("dynamicRouteLoading tests passed")
