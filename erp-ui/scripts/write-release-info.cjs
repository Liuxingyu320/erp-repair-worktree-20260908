const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const dist = path.resolve(root, "dist")
const commit = process.env.VUE_APP_BUILD_COMMIT || ""
const buildTime = process.env.VUE_APP_BUILD_TIME || ""

function productionBoolean(name) {
  let value = process.env[name]
  if (value === undefined) {
    const envSource = fs.readFileSync(path.resolve(root, ".env.production"), "utf8")
    const match = new RegExp(`^\\s*${name}\\s*=\\s*([^#\\r\\n]+)`, "m").exec(envSource)
    value = match ? match[1].trim().replace(/^['"]|['"]$/g, "") : ""
  }
  value = String(value).trim().replace(/^['"]|['"]$/g, "")
  if (value !== "true" && value !== "false") {
    console.error(`${name} must be explicitly set to true or false`)
    process.exit(1)
  }
  return value === "true"
}

if (!/^[0-9a-f]{40}$/.test(commit) || !buildTime || Number.isNaN(Date.parse(buildTime))) {
  console.error("refusing to write release-info.json with invalid build metadata")
  process.exit(1)
}
if (!fs.existsSync(path.resolve(dist, "index.html"))) {
  console.error("refusing to write release-info.json before a frontend build exists")
  process.exit(1)
}

const todoQuickApproveEnabled = productionBoolean("VUE_APP_TODO_QUICK_APPROVE_ENABLED")
const todoBatchApproveEnabled = productionBoolean("VUE_APP_TODO_BATCH_APPROVE_ENABLED")
if (todoBatchApproveEnabled && !todoQuickApproveEnabled) {
  console.error("VUE_APP_TODO_BATCH_APPROVE_ENABLED cannot be true while quick approval is disabled")
  process.exit(1)
}

fs.writeFileSync(
  path.resolve(dist, "release-info.json"),
  `${JSON.stringify({ commit, buildTime, todoQuickApproveEnabled, todoBatchApproveEnabled }, null, 2)}\n`,
  { encoding: "utf8", mode: 0o644 }
)
console.log("frontend release-info.json written")
