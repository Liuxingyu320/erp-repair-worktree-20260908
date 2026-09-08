const assert = require("assert")
const fs = require("fs")
const path = require("path")

const apiPath = path.resolve(__dirname, "../src/api/inventory/mobile.js")
const apiSource = fs.readFileSync(apiPath, "utf8")

assert.ok(
  apiSource.includes("export function listTransferApprovalTodos"),
  "mobile inventory API should export transfer approval todo list used by featureService"
)

console.log("mobileInventoryApiExports tests passed")
