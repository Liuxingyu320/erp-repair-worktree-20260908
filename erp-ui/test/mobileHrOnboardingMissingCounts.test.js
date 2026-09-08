const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")
const compiler = require("../node_modules/vue-template-compiler")

const componentPath = path.resolve(__dirname, "../src/views/mobile/hr/onboarding/detail.vue")
const source = fs.readFileSync(componentPath, "utf8")
const descriptor = compiler.parseComponent(source)
const compiled = compiler.compile(descriptor.template.content)
assert.deepStrictEqual(compiled.errors, [], "mobile onboarding detail template should compile")

const transformed = babel.transformSync(descriptor.script.content, {
  filename: `${componentPath}.js`,
  babelrc: false,
  configFile: false,
  sourceType: "module",
  plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
})
const moduleRef = { exports: {} }
vm.runInNewContext(transformed.code, {
  module: moduleRef,
  exports: moduleRef.exports,
  require(request) {
    if (request === "@/api/hr/onboarding") return {}
    if (request === "@/utils/permission") return { checkPermi: () => true }
    if (request === "@/api/hr/employee") return {}
    if (request === "@/views/hr/onboarding/onboardingFieldConfig") return { presentOnboardingLog: value => value }
    if (request === "../components/MobileHrShell") return {}
    if (request === "../mobileHrError") return require("../src/views/mobile/hr/mobileHrError")
    throw new Error(`unexpected component dependency: ${request}`)
  }
}, { filename: componentPath })

const component = moduleRef.exports.default || moduleRef.exports

function missingState(detail) {
  const onboardingMissingGroups = component.computed.onboardingMissingGroups.call({ detail })
  const profileMissingGroups = component.computed.profileMissingGroups.call({ detail, onboardingMissingGroups })
  const missingGroups = component.computed.missingGroups.call({ onboardingMissingGroups, profileMissingGroups })
  return {
    onboardingMissingGroups,
    profileMissingGroups,
    missingGroups,
    onboardingMissingCount: component.computed.onboardingMissingCount.call({ onboardingMissingGroups }),
    profileMissingCount: component.computed.profileMissingCount.call({ profileMissingGroups })
  }
}

const splitCounts = missingState({
  missingOnboardingFields: {
    IDENTITY: [{ key: "idNumber", label: "身份证号" }, { key: "phone", label: "手机号" }],
    ORGANIZATION: [{ key: "targetPostId", label: "岗位" }]
  },
  missingProfileFields: {
    IDENTITY: [{ key: "idNumber", label: "身份证号" }, { key: "bankAccount", label: "银行卡号" }],
    EMPLOYMENT: [{ key: "socialSecurity", label: "社保信息" }, { key: "bankAccount", label: "银行卡号" }]
  }
})
assert.strictEqual(splitCounts.onboardingMissingCount, 3, "current onboarding fields should have an independent count")
assert.strictEqual(splitCounts.profileMissingCount, 2, "post-entry fields should exclude current and duplicate fields")
assert.strictEqual(splitCounts.missingGroups.length, 4, "both non-empty kinds should retain their detail groups")
assert.ok(splitCounts.onboardingMissingGroups.every(group => group.label.startsWith("当前入职需补")))
assert.ok(splitCounts.profileMissingGroups.every(group => group.label.startsWith("入职后待补")))

const currentOnly = missingState({
  missingOnboardingFields: { IDENTITY: [{ key: "idNumber", label: "身份证号" }] },
  missingProfileFields: {}
})
assert.deepStrictEqual(
  [currentOnly.onboardingMissingCount, currentOnly.profileMissingCount, currentOnly.missingGroups.length],
  [1, 0, 1],
  "an empty post-entry group should not hide current missing details"
)

const profileOnly = missingState({
  missingOnboardingFields: {},
  missingProfileFields: { EMPLOYMENT: [{ key: "contract", label: "合同" }] }
})
assert.deepStrictEqual(
  [profileOnly.onboardingMissingCount, profileOnly.profileMissingCount, profileOnly.missingGroups.length],
  [0, 1, 1],
  "an empty current group should not hide post-entry missing details"
)

const complete = missingState({ missingOnboardingFields: {}, missingProfileFields: {} })
assert.deepStrictEqual(
  [complete.onboardingMissingCount, complete.profileMissingCount, complete.missingGroups.length],
  [0, 0, 0],
  "two empty groups should render the complete state"
)

assert.ok(source.includes("当前入职必填缺 {{ onboardingMissingCount }} 项"))
assert.ok(source.includes("另有 {{ profileMissingCount }} 项可后续完善（不影响确认入职）"))
assert.ok(!source.includes("<h2 id=\"missing-title\">缺失资料</h2>"))
assert.ok(!source.includes("missingFieldCount"))

console.log("mobile HR onboarding missing-count tests passed")
