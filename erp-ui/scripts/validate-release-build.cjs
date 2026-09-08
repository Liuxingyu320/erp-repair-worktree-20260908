const fs = require("fs")
const path = require("path")

const commit = process.env.VUE_APP_BUILD_COMMIT || ""
const buildTime = process.env.VUE_APP_BUILD_TIME || ""

function productionEnvValue(name) {
  if (process.env[name] !== undefined) return String(process.env[name]).trim()
  const envPath = path.resolve(__dirname, "../.env.production")
  const source = fs.readFileSync(envPath, "utf8")
  const match = new RegExp(`^\\s*${name}\\s*=\\s*([^#\\r\\n]+)`, "m").exec(source)
  return match ? match[1].trim().replace(/^['"]|['"]$/g, "") : ""
}

function validateBooleanFlag(name) {
  const value = productionEnvValue(name)
  if (value !== "true" && value !== "false") {
    console.error(`${name} must be explicitly set to true or false`)
    process.exit(1)
  }
  return value === "true"
}

if (!/^[0-9a-f]{40}$/.test(commit)) {
  console.error("VUE_APP_BUILD_COMMIT must be a full 40-character lowercase Git SHA")
  process.exit(1)
}

if (!buildTime || Number.isNaN(Date.parse(buildTime))) {
  console.error("VUE_APP_BUILD_TIME must be an ISO-8601 timestamp")
  process.exit(1)
}

const quickApproveEnabled = validateBooleanFlag("VUE_APP_TODO_QUICK_APPROVE_ENABLED")
const batchApproveEnabled = validateBooleanFlag("VUE_APP_TODO_BATCH_APPROVE_ENABLED")
if (batchApproveEnabled && !quickApproveEnabled) {
  console.error("VUE_APP_TODO_BATCH_APPROVE_ENABLED cannot be true while quick approval is disabled")
  process.exit(1)
}

console.log(`release build metadata validated: commit=${commit}, todoQuick=${quickApproveEnabled}, todoBatch=${batchApproveEnabled}`)
