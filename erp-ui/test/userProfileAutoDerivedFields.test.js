const assert = require("assert")
const fs = require("fs")
const path = require("path")

const apiSource = fs.readFileSync(path.resolve(__dirname, "../src/api/system/user.js"), "utf8")
const userSource = fs.readFileSync(path.resolve(__dirname, "../src/views/system/user/index.vue"), "utf8")

assert.ok(
  apiSource.includes("export function previewUserDerivedProfile") &&
    apiSource.includes("/system/user/derived-preview") &&
    apiSource.includes('method: "post"'),
  "user API should expose the read-only derived profile preview endpoint"
)

assert.ok(
  userSource.includes("previewUserDerivedProfile") &&
    userSource.includes("scheduleDerivedPreview") &&
    userSource.includes("loadDerivedPreview") &&
    userSource.includes("derivedPreviewSequence"),
  "user form should debounce derived previews and track request ordering"
)

;[
  "companyName",
  "deptLevel1Name",
  "deptLevel2Name",
  "deptLevel3Name",
  "storeName",
  "positionNames",
  "departmentSupervisor",
  "workYears",
  "companyYears"
].forEach(field => {
  assert.ok(
    userSource.includes(`data-derived-field="${field}" :disabled="true"`),
    `${field} should be rendered as a disabled derived field`
  )
})

assert.ok(
  userSource.includes("根据归属部门自动匹配") &&
    userSource.includes("根据已选岗位自动匹配") &&
    userSource.includes("根据日期自动计算"),
  "derived fields should explain their authoritative source"
)

assert.ok(
  userSource.includes("derivedProfileWarning") &&
    userSource.includes("form.profile.derivedWarnings"),
  "derived warnings should be visible in the edit dialog"
)

assert.ok(
  userSource.includes("clearTimeout(this.derivedPreviewTimer)") &&
    userSource.includes("requestSequence !== this.derivedPreviewSequence"),
  "preview lifecycle should cancel debounce timers and discard stale responses"
)

console.log("userProfileAutoDerivedFields tests passed")
