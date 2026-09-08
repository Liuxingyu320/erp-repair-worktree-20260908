const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const optionsPath = path.join(uiRoot, "src/views/hr/components/signingProfileOptions.js")

assert.ok(fs.existsSync(optionsPath), "HR and system user pages should share one signing profile option source")

const optionsSource = fs.readFileSync(optionsPath, "utf8")
const hrEditSource = fs.readFileSync(path.join(uiRoot, "src/views/hr/components/HrProfileEditDrawer.vue"), "utf8")
const hrDetailSource = fs.readFileSync(path.join(uiRoot, "src/views/hr/components/HrProfileDetailDrawer.vue"), "utf8")
const userSource = fs.readFileSync(path.join(uiRoot, "src/views/system/user/index.vue"), "utf8")
const userViewSource = fs.readFileSync(path.join(uiRoot, "src/views/system/user/view.vue"), "utf8")

;[
  'value: "LABOR_CONTRACT", label: "劳动合同"',
  'value: "SERVICE_CONTRACT", label: "劳务协议"',
  'value: "FIXED_TERM", label: "固定期限"',
  'value: "OPEN_ENDED", label: "无固定期限"',
  'value: "SOCIAL_INSURED", label: "缴纳社保"',
  'value: "SOCIAL_UNINSURED", label: "无需缴纳"',
  'value: "DISPATCHED", label: "劳务派遣"',
  'value: "PENDING_CONFIRMATION", label: "待确认"',
  "signingProfileLabel"
].forEach(needle => assert.ok(optionsSource.includes(needle), `shared signing options should include ${needle}`))

assert.ok(
  hrEditSource.includes("CONTRACT_TYPE_OPTIONS") &&
    hrEditSource.includes("CONTRACT_TERM_OPTIONS") &&
    hrEditSource.includes("SOCIAL_TYPE_OPTIONS") &&
    !hrEditSource.includes('contractType: ["固定期限劳动合同"'),
  "HR profile editing should use shared machine-code options"
)

assert.ok(
  hrDetailSource.includes("signingProfileLabel") &&
    hrDetailSource.includes("displayProfileValue"),
  "HR profile details should translate signing codes back to business labels"
)

assert.ok(
  userSource.includes("CONTRACT_TYPE_OPTIONS") &&
    userSource.includes("CONTRACT_TERM_OPTIONS") &&
    userSource.includes("SOCIAL_TYPE_OPTIONS") &&
    userSource.includes(":key=\"item.value\"") &&
    userSource.includes(":label=\"item.label\"") &&
    userSource.includes(":value=\"item.value\""),
  "system user editing should submit the same shared machine codes"
)

assert.ok(
  userViewSource.includes("signingProfileLabel") &&
    userViewSource.includes("signingOptionsForKey"),
  "system user details should display shared business labels"
)

console.log("signingProfileDictionary tests passed")
