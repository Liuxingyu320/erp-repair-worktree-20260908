const assert = require("assert")
const fs = require("fs")
const path = require("path")

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")
}

function occurrenceCount(source, value) {
  return source.split(value).length - 1
}

function elementInputTag(source, modelExpression) {
  const tags = source.match(/<el-input\b[\s\S]*?>/g) || []
  const tag = tags.find(candidate => candidate.includes(`v-model="${modelExpression}"`))
  assert.ok(tag, `missing Element Input for ${modelExpression}`)
  return tag
}

function staticProp(tag, name) {
  const match = tag.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`))
  return match ? match[1] : ""
}

global.window = { navigator: { userAgent: "node" } }
global.document = {
  createElement() { return { style: {} } },
  createEvent() { return { timeStamp: 0 } }
}

const Vue = require("vue")
const elementInputExport = require("element-ui/lib/input")
const ElementInput = Vue.extend(elementInputExport.default || elementInputExport)
Vue.config.productionTip = false
Vue.config.devtools = false

function findVNode(node, tagName) {
  if (!node) return null
  if (node.tag === tagName) return node
  const children = node.children || []
  for (const child of children) {
    const match = findVNode(child, tagName)
    if (match) return match
  }
  return null
}

function assertRenderedElementInput(source, modelExpression, expectedLabel, expectedAutocomplete) {
  const tag = elementInputTag(source, modelExpression)
  const label = staticProp(tag, "label")
  const autocomplete = staticProp(tag, "autocomplete")

  assert.strictEqual(label, expectedLabel, `${modelExpression} should pass the Element Input label prop`)
  if (expectedAutocomplete) {
    assert.strictEqual(
      autocomplete,
      expectedAutocomplete,
      `${modelExpression} should pass the expected Element Input autocomplete prop`
    )
  }

  const propsData = expectedAutocomplete ? { label, autocomplete } : { label }
  const vm = new ElementInput({ propsData })
  const nativeInput = findVNode(vm._render(), "input")
  assert.ok(nativeInput, `${modelExpression} should render a native input`)
  assert.strictEqual(
    nativeInput.data.attrs["aria-label"],
    expectedLabel,
    `${modelExpression} should render its accessible name on the native input`
  )
  if (expectedAutocomplete) {
    assert.strictEqual(
      nativeInput.data.attrs.autocomplete,
      expectedAutocomplete,
      `${modelExpression} should render autocomplete on the native input`
    )
  }
  vm.$destroy()
}

const loginSource = readSource("../src/views/login.vue")
const registerSource = readSource("../src/views/register.vue")
const selectShopSource = readSource("../src/views/select-shop/index.vue")
const featureSource = readSource("../src/views/mobile/feature/index.vue")
const workbenchSource = readSource("../src/views/mobile/components/MobileWorkbenchShell.vue")
const mobileSheetSource = readSource("../src/views/mobile/feature/components/mobileSheet.scss")
const elementInputCss = readSource("../node_modules/element-ui/lib/theme-chalk/input.css")
const hrShellSource = readSource("../src/views/mobile/hr/components/MobileHrShell.vue")
const hrListSource = readSource("../src/views/mobile/hr/components/MobileOnboardingList.vue")
const hrQueueSource = readSource("../src/views/mobile/hr/onboarding/index.vue")
const hrDetailSource = readSource("../src/views/mobile/hr/onboarding/detail.vue")
const hrFormSource = readSource("../src/views/mobile/hr/components/MobileOnboardingStepForm.vue")
const hrQaSource = readSource("../../scripts/mobile-hr-onboarding-qa.cjs")

;[
  [loginSource, "loginForm.username", "账号", "username"],
  [loginSource, "loginForm.password", "密码", "current-password"],
  [loginSource, "loginForm.code", "验证码", "one-time-code"],
  [registerSource, "registerForm.username", "账号", "username"],
  [registerSource, "registerForm.password", "密码", "new-password"],
  [registerSource, "registerForm.confirmPassword", "确认密码", "new-password"],
  [registerSource, "registerForm.code", "验证码", "one-time-code"],
  [selectShopSource, "filterText", "搜索组织", null]
].forEach(args => assertRenderedElementInput(...args))

assert.strictEqual(
  occurrenceCount(loginSource, 'autocomplete="username"'),
  2,
  "desktop and mobile login usernames should expose username autocomplete"
)
assert.strictEqual(
  occurrenceCount(loginSource, 'autocomplete="current-password"'),
  2,
  "desktop and mobile login passwords should expose current-password autocomplete"
)
assert.strictEqual(
  occurrenceCount(loginSource, 'autocomplete="one-time-code"'),
  2,
  "desktop and mobile login captcha fields should expose one-time-code autocomplete"
)
assert.ok(
  !loginSource.includes('auto-complete="off"'),
  "login credential and captcha fields should not disable browser autocomplete"
)

assert.ok(
  registerSource.includes('autocomplete="username"') &&
    occurrenceCount(registerSource, 'autocomplete="new-password"') === 2 &&
    registerSource.includes('autocomplete="one-time-code"'),
  "registration fields should expose username, new-password, and one-time-code autocomplete"
)
assert.ok(
  !registerSource.includes('auto-complete="off"'),
  "registration fields should not disable browser autocomplete"
)

assert.strictEqual(
  occurrenceCount(loginSource, '<button type="button" aria-label="刷新验证码" @click="getCode"'),
  2,
  "desktop and mobile login captcha refreshers should be real non-submit buttons"
)
assert.strictEqual(
  occurrenceCount(loginSource, 'alt="验证码，点击可刷新"'),
  2,
  "both login captcha images should describe refresh behavior"
)
assert.ok(
  registerSource.includes('<button type="button" aria-label="刷新验证码" @click="getCode"') &&
    registerSource.includes('alt="验证码，点击可刷新"') &&
    registerSource.includes('v-if="codeUrl"') &&
    registerSource.includes('<span v-else>验证码</span>'),
  "registration captcha should remain operable and named even before its image loads"
)

assert.strictEqual(
  occurrenceCount(selectShopSource, 'label="搜索组织"'),
  2,
  "desktop and mobile organization search inputs should have accessible names"
)
assert.ok(
  /class="refresh-icon-btn mobile-refresh-btn"[^>]+aria-label="刷新组织列表"[^>]+title="刷新组织列表"/.test(selectShopSource),
  "the icon-only mobile organization refresh control should have a label and title"
)

assert.ok(
  registerSource.includes("width: min(400px, 100%);") &&
    registerSource.includes("box-sizing: border-box;") &&
    !registerSource.includes("width: 400px;"),
  "registration card width should shrink without overflowing a 320px viewport"
)
assert.ok(
  registerSource.includes("min-height: 100vh;") &&
    registerSource.includes("min-height: 100dvh;") &&
    registerSource.includes("safe-area-inset-bottom"),
  "registration should use viewport height and safe-area padding"
)
assert.ok(
  loginSource.includes("bottom: max(16px, calc(env(safe-area-inset-bottom) + 8px));") &&
    occurrenceCount(loginSource, "calc(76px + env(safe-area-inset-bottom))") >= 2 &&
    !/\.el-login-footer\s*\{[^}]*bottom:\s*16px;/s.test(loginSource),
  "login footer and auth stage should reserve fixed footer space plus the bottom safe area"
)
assert.ok(
  registerSource.includes("flex-direction: column;") &&
    registerSource.includes("calc(var(--mobile-safe-bottom, env(safe-area-inset-bottom, 0px)) + 8px)") &&
    /\.el-register-footer\s*\{[^}]*position:\s*static;[^}]*margin-top:\s*auto;/s.test(registerSource) &&
    !/\.el-register-footer\s*\{[^}]*position:\s*fixed;/s.test(registerSource),
  "registration footer should stay in document flow and preserve bottom safe-area spacing"
)
assert.ok(
  /\.el-input__inner\s*\{[^}]*height:\s*44px;[^}]*line-height:\s*44px;/s.test(registerSource) &&
    registerSource.includes("::v-deep .el-input__inner") &&
    !/\.el-input\s*\{[^}]*\binput\s*\{/s.test(registerSource),
  "registration inputs, captcha refresh, and primary action should meet the 44px target"
)
assert.ok(
  /\.el-input__inner\{[^}]*height:40px/.test(elementInputCss),
  "Element UI's native input baseline should remain 40px without a scoped deep override"
)

assert.ok(
  featureSource.includes(".list-row h3") &&
    featureSource.includes("overflow-wrap: anywhere;") &&
    featureSource.includes("white-space: normal;") &&
    featureSource.includes(".list-row .row-detail"),
  "feature list titles and details should be allowed to wrap naturally"
)
assert.ok(
  workbenchSource.includes(".selector-pill span:not(.selector-icon)") &&
    workbenchSource.includes(".todo-copy h3") &&
    workbenchSource.includes(".todo-copy p") &&
    occurrenceCount(workbenchSource, "overflow-wrap: anywhere;") >= 3,
  "workbench selector, todo, context, and hero copy should support enlarged wrapping text"
)
assert.ok(
  workbenchSource.includes("font-size: 13px;") &&
    !/\.context-card p\s*\{[^}]*font-size:\s*(?:11|12)px/s.test(workbenchSource) &&
    !/\.todo-status span\s*\{[^}]*font-size:\s*(?:11|12)px/s.test(workbenchSource),
  "key workbench secondary copy should remain at least 13px"
)
assert.ok(
  !/\.bottom-nav span\s*\{[^}]*font-size:\s*(?:11|12)px/s.test(featureSource) &&
    !/\.bottom-nav span\s*\{[^}]*font-size:\s*(?:11|12)px/s.test(workbenchSource),
  "bottom navigation labels should remain at least 13px"
)

assert.ok(
  featureSource.includes(".stock-scope-toggle button") &&
    /\.stock-scope-toggle button\s*\{[^}]*min-height:\s*44px/s.test(featureSource) &&
    /\.managed-store-select select\s*\{[^}]*height:\s*44px/s.test(featureSource) &&
    /\.search-row input\s*\{[^}]*height:\s*44px/s.test(featureSource),
  "feature stock scope, store selector, and search controls should be at least 44px"
)
assert.ok(
  /\.detail-head button\s*\{[^}]*width:\s*44px[^}]*height:\s*44px/s.test(mobileSheetSource) &&
    /\.detail-actions button\s*\{[^}]*min-height:\s*44px/s.test(mobileSheetSource) &&
    !mobileSheetSource.includes("min-height: 38px;"),
  "sheet close, action, and compact states should use 44px targets"
)

assert.ok(
  featureSource.includes("@media (max-width: 390px)") &&
    featureSource.includes("@media (max-width: 320px)") &&
    featureSource.includes("grid-template-columns: repeat(2, minmax(0, 1fr));"),
  "feature layout should preserve its two-column shortcut grid at 390px and 320px"
)
assert.ok(
  featureSource.includes("@media (max-width: 360px) and (max-height: 620px)") &&
    /@media \(max-width: 360px\) and \(max-height: 620px\)[\s\S]*?\.feature-stage\s*\{[^}]*padding-top:\s*12px;/s.test(featureSource) &&
    /@media \(max-width: 360px\) and \(max-height: 620px\)[\s\S]*?\.preview-lock-banner\s*\{[^}]*padding:\s*8px 12px;/s.test(featureSource) &&
    /@media \(max-width: 360px\) and \(max-height: 620px\)[\s\S]*?\.list-panel\s*\{[^}]*margin-top:\s*6px;[^}]*padding-top:\s*10px;/s.test(featureSource),
  "short 320px-class viewports should compact vertical chrome so the list heading clears the fixed navigation"
)
assert.ok(
  workbenchSource.includes("@media (max-width: 390px)") &&
    workbenchSource.includes("@media (max-width: 320px)") &&
    workbenchSource.includes("grid-template-columns: repeat(2, minmax(0, 1fr));"),
  "workbench hero, context, todos, and shortcuts should have 390px and 320px safeguards"
)

;[390, 375, 360, 320].forEach(width => {
  assert.ok(hrQaSource.includes(String(width)), `mobile HR visual QA should exercise ${width}px width`)
})
;[hrShellSource, hrQueueSource, hrDetailSource, hrFormSource].forEach(source => {
  assert.ok(source.includes("safe-area-inset-bottom"), "every fixed or scrollable HR surface should account for the bottom safe area")
})
assert.ok(hrShellSource.includes("@media (max-width: 320px)"), "HR shell should include a 320px compact layout")
assert.ok(hrListSource.includes("@media (max-width: 320px)"), "HR list rows should include a 320px compact layout inside the safe-area shell")
assert.ok(hrQueueSource.includes("@media (max-width: 360px)"), "HR queue filters should collapse by 360px")
assert.ok(hrDetailSource.includes("@media (max-width: 360px)"), "HR detail progress should stack by 360px")

console.log("mobileAccessibilityVisuals tests passed")
