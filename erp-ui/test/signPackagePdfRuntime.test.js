const assert = require("assert")
const fs = require("fs")
const path = require("path")

const dockerfile = fs.readFileSync(
  path.resolve(__dirname, "../../docker/erp/modules/oa/dockerfile"),
  "utf8"
)

assert.ok(
  dockerfile.startsWith("FROM eclipse-temurin:17-jre-jammy"),
  "OA runtime should use a maintained Ubuntu-based Java 17 image so PDF packages remain installable"
)

;["libreoffice-writer", "libreoffice-calc", "fonts-noto-cjk", "fontconfig"].forEach(runtimePackage => {
  assert.ok(
    dockerfile.includes(runtimePackage),
    `OA runtime image should install ${runtimePackage} for faithful Chinese PDF conversion`
  )
})
assert.ok(
  dockerfile.includes("fc-cache -f"),
  "OA runtime image should build the CJK font cache before contract conversion"
)

console.log("sign package PDF runtime tests passed")
