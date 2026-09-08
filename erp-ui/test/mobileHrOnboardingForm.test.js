const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")
const compiler = require("../node_modules/vue-template-compiler")

const ROOT = path.resolve(__dirname, "../src/views/mobile/hr")
const MODULE_PATH = path.join(ROOT, "mobileOnboardingForm.js")
const COMPONENT_PATH = path.join(ROOT, "components/MobileOnboardingStepForm.vue")
const PAGE_PATH = path.join(ROOT, "onboarding/form.vue")
const HR_ERROR_PATH = path.join(ROOT, "mobileHrError.js")

function source(file) {
  assert.ok(fs.existsSync(file), `${path.relative(__dirname, file)} should exist`)
  return fs.readFileSync(file, "utf8")
}

function loadJsModule(file) {
  const transformed = babel.transformSync(source(file), {
    filename: file,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  })
  const module = { exports: {} }
  vm.runInNewContext(transformed.code, { module, exports: module.exports, require }, { filename: file })
  return module.exports
}

function loadSfc(file, dependencies) {
  const descriptor = compiler.parseComponent(source(file))
  assert.ok(descriptor.script && descriptor.script.content, `${file} should have a script block`)
  const compiledTemplate = compiler.compile(descriptor.template.content)
  assert.deepStrictEqual(compiledTemplate.errors, [], `${file} template should compile`)
  const transformed = babel.transformSync(descriptor.script.content, {
    filename: `${file}.js`,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  })
  const module = { exports: {} }
  const sandbox = {
    module,
    exports: module.exports,
    window: dependencies && dependencies.__window,
    document: dependencies && dependencies.__document,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(dependencies || {}, request)) return dependencies[request]
      throw new Error(`unexpected component dependency: ${request}`)
    }
  }
  vm.runInNewContext(transformed.code, sandbox, { filename: file })
  return module.exports.default || module.exports
}

function plain(value) { return JSON.parse(JSON.stringify(value)) }
function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}
function flush() { return new Promise(resolve => setImmediate(resolve)) }

function bind(component, additions) {
  const target = Object.assign({}, component.data ? component.data.call({}) : {}, additions || {})
  target.emitted = []
  if (!target.$emit) target.$emit = (name, value) => target.emitted.push({ name, value })
  if (!target.$set) target.$set = (object, key, value) => { object[key] = value }
  if (!target.$delete) target.$delete = (object, key) => { delete object[key] }
  if (!target.$nextTick) target.$nextTick = callback => callback()
  Object.keys(component.methods || {}).forEach(name => {
    target[name] = (...args) => component.methods[name].apply(target, args)
  })
  return target
}

const form = loadJsModule(MODULE_PATH)
const hrError = require(HR_ERROR_PATH)
const componentSource = source(COMPONENT_PATH)
const pageSource = source(PAGE_PATH)

assert.strictEqual(
  hrError.mobileHrErrorMessage({ response: { status: 401, data: { msg: "令牌不能为空" } } }, "加载失败"),
  "登录状态已失效，请重新登录"
)
assert.strictEqual(
  hrError.mobileHrErrorMessage(new Error("java.lang.IllegalStateException at com.erp.HrService.java:42"), "加载失败"),
  "加载失败"
)

assert.deepStrictEqual(plain(form.STEP_KEYS), ["basic", "organization", "identityContact", "employment"])
assert.deepStrictEqual(plain(form.STEP_LABELS), ["基础", "任职", "资料", "确认"])
assert.strictEqual(form.fieldStep("idNumber"), "identityContact")
assert.strictEqual(form.fieldStep("contractType"), "employment")
assert.strictEqual(form.fieldStep("targetPostId"), "organization")
assert.strictEqual(form.fieldControl("expectedEntryDate"), "date-picker")
assert.strictEqual(form.fieldControl("directSupervisorUserId"), "owner-picker")
assert.strictEqual(form.fieldControl("ownerUserId"), "owner-picker")
assert.strictEqual(form.fieldStep("ownerUserId"), "organization")
assert.strictEqual(form.fieldOptionKey("targetPostId"), "posts")
assert.strictEqual(form.fieldOptionKey("contractType"), "contractTypes")
assert.strictEqual(form.normalizePayload({ version: "0" }).version, 0)
assert.strictEqual(form.isMaskPlaceholder("已填写"), true, "the safe filled sentinel is still a placeholder when submitted")
assert.strictEqual(form.isMaskPlaceholder("未填写"), true, "the safe empty sentinel is still a placeholder when submitted")

const createPayload = form.normalizePayload({
  employeeName: "林晓", phoneNumber: "13800138000", expectedEntryDate: "2026-07-20",
  targetDeptId: 11, targetPostId: 12, employeeCategory: "FULL_TIME", ownerUserId: 13,
  targetDeptName: "不可提交", positionName: "不可提交", idNumber: "110101199001010000"
}, { mode: "create" })
assert.deepStrictEqual(plain(createPayload), {
  employeeName: "林晓", phoneNumber: "13800138000", expectedEntryDate: "2026-07-20",
  targetDeptId: 11, targetPostId: 12, employeeCategory: "FULL_TIME", ownerUserId: 13
}, "create must send exactly the seven-field backend contract")

const editPayload = form.normalizePayload({
  version: "4", employeeName: "林晓", targetDeptId: "11", targetDeptName: "华东区",
  positionName: "店长", phoneNumberMasked: "138****8000", idNumberMasked: "1101**********0000"
}, {
  mode: "edit",
  originalModel: { version: 4, employeeName: "林晓", targetDeptId: 11 },
  dirtySensitiveFields: {}
})
assert.deepStrictEqual(plain(editPayload), { version: 4 },
  "an unchanged edit must send only the version after payload normalization")

const changedPayload = form.normalizePayload({
  version: "4", employeeName: "新姓名", targetDeptId: "11", targetStoreId: "",
  directSupervisorUserId: null, birthDate: ""
}, {
  mode: "edit",
  originalModel: {
    version: 4, employeeName: "原姓名", targetDeptId: 11, targetStoreId: 21,
    directSupervisorUserId: 13, birthDate: "1990-01-01"
  },
  dirtySensitiveFields: {}
})
assert.deepStrictEqual(plain(changedPayload), {
  version: 4,
  employeeName: "新姓名",
  targetStoreId: "",
  directSupervisorUserId: null,
  birthDate: ""
}, "edit must preserve real changes and explicit clears while ignoring equivalent normalized values")

const dirtyPayload = form.normalizePayload({
  version: 4, phoneNumber: "13900139000", idNumber: "110101199001010000"
}, {
  mode: "edit",
  originalModel: { version: 4, phoneNumber: "13900139000", idNumber: "110101199001010000" },
  dirtySensitiveFields: { phoneNumber: true, idNumber: true }
})
assert.deepStrictEqual(plain(dirtyPayload), { version: 4, phoneNumber: "13900139000", idNumber: "110101199001010000" })
assert.throws(() => form.normalizePayload(
  { version: 4, idNumber: "1101**********0000" },
  { mode: "edit", dirtySensitiveFields: { idNumber: true } }
), error => Boolean(error && error.fieldErrors && error.fieldErrors.idNumber), "mask placeholders must be rejected locally")

assert.deepStrictEqual(plain(form.errorsByStep({ idNumber: "证件错误", targetPostId: "岗位错误", contractType: "合同错误" })), {
  basic: {}, organization: { targetPostId: "岗位错误" }, identityContact: { idNumber: "证件错误" }, employment: { contractType: "合同错误" }
})
assert.strictEqual(form.firstErrorLocation({ contractType: "合同错误", employeeName: "姓名错误" }).stepKey, "basic")
assert.deepStrictEqual(plain(form.missingCountsByStep({
  basic: [{ key: "employeeName" }], identity: [{ key: "idNumber" }, { key: "currentAddress" }],
  contract: [{ key: "contractType" }]
})), { basic: 1, organization: 0, identityContact: 2, employment: 1 })

assert.ok(!/from\s+["'][^"']*\/api\//.test(componentSource), "pure step form must not call APIs")
for (const prop of ["model", "step", "errors", "missingCounts", "options", "disabled"]) {
  assert.ok(new RegExp(`\\b${prop}:\\s*\\{`).test(componentSource), `step form should expose ${prop}`)
}
for (const event of ["input", "sensitive-dirty", "back", "next", "save"]) {
  assert.ok(componentSource.includes(`$emit(\"${event}\"`) || componentSource.includes(`$emit('${event}'`), `step form should emit ${event}`)
}
assert.ok(componentSource.includes('type="date"'), "dates should use a touch date picker")
assert.ok(componentSource.includes("<select"), "organization, post, supervisor and dictionaries should use native option sheets")
assert.ok(componentSource.includes("<mobile-onboarding-owner-picker"), "owner must use the dedicated mobile picker instead of the native select")
assert.ok(
  componentSource.includes(':picker-label="field.label"') &&
    componentSource.includes('@select="updatePerson(field, $event)"'),
  "owner and direct supervisor must share the paged authorized picker without loading a full user list"
)
assert.ok(componentSource.includes(':input-id="fieldId(field.key)"') &&
  componentSource.includes(':labelled-by="labelId(field.key)"'),
"owner trigger must keep the field label/id association")
assert.ok(componentSource.includes(":for=") && componentSource.includes(":aria-describedby="), "fields need real labels and associated errors")
assert.ok(/masked-control[\s\S]*?<button[^>]*:id="fieldId\(field\.key\)"/.test(componentSource), "protected sensitive labels and errors should target the modify control")
assert.ok(/min-height:\s*44px/.test(componentSource) && /min-width:\s*44px/.test(componentSource), "touch controls must be at least 44x44")
assert.ok(componentSource.includes("stepHeading") && componentSource.includes("focus"), "step changes should focus the heading")
assert.ok(componentSource.includes("safe-area-inset-bottom") && componentSource.includes("mobile-keyboard-offset"), "sticky footer should account for keyboard and safe area")
assert.ok(componentSource.includes("window.visualViewport") && componentSource.includes('addEventListener("resize"') && componentSource.includes('addEventListener("scroll"'), "the form should track real visual viewport keyboard changes")
assert.ok(componentSource.includes('removeEventListener("resize"') && componentSource.includes('removeEventListener("scroll"') && componentSource.includes(":style=\"keyboardStyle\""), "viewport listeners must be removed and the measured offset bound to the root")
assert.ok(componentSource.includes(':disabled="disabled"') && pageSource.includes(':disabled="formLocked"'), "the full form must remain inert while writing, navigating, or after a saved response")
assert.ok(
  /@media\s*\(max-width:\s*320px\)[\s\S]*?\.mobile-form-page\s*\{[^}]*margin:\s*-12px/.test(pageSource),
  "the form bleed must follow the shell's 12px padding at the 320px breakpoint"
)

assert.ok(pageSource.includes("createHrOnboarding") && pageSource.includes("updateHrOnboarding"), "page should use the existing write APIs")
assert.ok(pageSource.includes("getHrOnboarding") && pageSource.includes("getHrOnboardingFormOptions"), "edit should load masked detail and options")
assert.ok(pageSource.includes(':options-ready="!optionsLoading && !optionsError"'),
  "unavailable saved values must only be declared after the final options load succeeds")
assert.ok(componentSource.includes('v-if="hasUnavailableCurrentValue(field)"') &&
  componentSource.includes("（当前不可选）"),
"the option sheet must visibly preserve an unavailable saved value")
assert.ok(pageSource.includes("beforeRouteLeave") && pageSource.includes("beforeRouteUpdate") && pageSource.includes("submitting"), "leave and same-component route changes must be blocked during saves")
assert.ok(!pageSource.includes("localStorage") && !pageSource.includes("sessionStorage"), "form state must stay in memory")
assert.ok(!pageSource.includes("reveal"), "mobile form must never reveal sensitive data")
assert.ok(pageSource.includes("stateKey") && !pageSource.includes("employeeName:"), "success navigation should preserve only opaque queue state")
assert.ok(pageSource.includes("保存成功但跳转失败") && pageSource.includes("retryNavigation") && pageSource.includes("savedOnboardingId"), "post-save navigation failures need a terminal saved state and navigation-only retry")
assert.ok(pageSource.includes("提交结果未知") && pageSource.includes("createOutcomeUnknown") && pageSource.includes("openQueueForReconciliation"), "ambiguous create failures need a terminal reconciliation state instead of save retry")
assert.ok(pageSource.includes("/^qs_[a-z0-9_]{12,80}$/i") && !pageSource.includes("/^[a-z0-9_-]{1,80}$/i"), "stateKey validation must exactly match queue/detail")
assert.ok(
  ["idNumber", "bankAccount", "registeredResidence", "currentAddress"].every(key => form.SENSITIVE_FIELDS.includes(key)),
  "form source may name protected identifiers only so dirty edits can be normalized without rendering raw detail values"
)
assert.ok(
  !/\|\|\s*(?:model|detail)\.(?:idNumber|bankAccount|registeredResidence|currentAddress)\b/.test(componentSource + pageSource),
  "rendered form controls must never fall back from masked DTO fields to raw sensitive values"
)

const component = loadSfc(COMPONENT_PATH, {
  "../mobileOnboardingForm": form,
  "./MobileOnboardingOwnerPicker": {}
})
const shellStub = {}

function loadPage(api) {
  return loadSfc(PAGE_PATH, {
    "@/api/hr/onboarding": api,
    "../components/MobileHrShell": shellStub,
    "../components/MobileOnboardingStepForm": component,
    "../mobileOnboardingForm": form,
    "../mobileHrError": hrError
  })
}

async function run() {
  const sensitiveField = { key: "idNumber", sensitive: true }
  assert.strictEqual(
    component.methods.isDirtySensitive.call({ isEdit: false, model: { idNumber: "110101199001010000" } }, sensitiveField),
    false,
    "create mode must not present initialized sensitive fields as modified edit values"
  )
  assert.strictEqual(
    component.methods.isDirtySensitive.call({ isEdit: true, model: { idNumber: "110101199001010000" } }, sensitiveField),
    true,
    "edit mode should keep the explicit sensitive-field dirty state"
  )
  const viewportListeners = {}
  const removedViewportListeners = []
  const viewport = {
    height: 800,
    offsetTop: 0,
    addEventListener(name, listener) { viewportListeners[name] = listener },
    removeEventListener(name, listener) {
      removedViewportListeners.push([name, listener])
      if (viewportListeners[name] === listener) delete viewportListeners[name]
    }
  }
  const viewportWindow = { innerHeight: 800, visualViewport: viewport }
  const keyboardComponent = loadSfc(COMPONENT_PATH, {
    "../mobileOnboardingForm": form,
    "./MobileOnboardingOwnerPicker": {},
    __window: viewportWindow
  })
  const keyboardTarget = bind(keyboardComponent, { $refs: { stepHeading: { focus() {} } } })
  keyboardComponent.mounted.call(keyboardTarget)
  assert.strictEqual(keyboardTarget.keyboardOffset, 0)
  assert.strictEqual(keyboardComponent.computed.keyboardStyle.call(keyboardTarget)["--mobile-keyboard-offset"], "0px")
  viewport.height = 500
  viewport.offsetTop = 10
  viewportListeners.resize()
  assert.strictEqual(keyboardTarget.keyboardOffset, 290, "keyboard offset should follow the shrunken visual viewport")
  assert.strictEqual(keyboardComponent.computed.keyboardStyle.call(keyboardTarget)["--mobile-keyboard-offset"], "290px")
  viewport.height = 800
  viewport.offsetTop = 0
  viewportListeners.scroll()
  assert.strictEqual(keyboardTarget.keyboardOffset, 0, "keyboard offset should reset when the viewport is restored")
  viewport.height = 620
  keyboardTarget.handleFocusIn({ target: { tagName: "INPUT" } })
  assert.strictEqual(keyboardTarget.keyboardOffset, 180, "focusing a control should refresh the viewport measurement")
  keyboardComponent.beforeDestroy.call(keyboardTarget)
  assert.deepStrictEqual(removedViewportListeners.map(item => item[0]).sort(), ["resize", "scroll"], "all viewport listeners should be removed")
  assert.strictEqual(keyboardTarget.viewportListening, false)
  const ssrTarget = bind(component, { $refs: { stepHeading: { focus() {} } } })
  component.mounted.call(ssrTarget)
  assert.strictEqual(ssrTarget.keyboardOffset, 0, "SSR/no-window mounting should be a safe no-op")
  component.beforeDestroy.call(ssrTarget)

  const createCalls = []
  const createOptionParams = []
  const createPage = loadPage({
    createHrOnboarding(data) { createCalls.push(plain(data)); return Promise.resolve({ data: { onboardingId: 71 } }) },
    updateHrOnboarding() { throw new Error("unexpected update") },
    getHrOnboarding() { throw new Error("unexpected detail") },
    getHrOnboardingFormOptions(params) { createOptionParams.push(plain(params)); return Promise.resolve({ data: {} }) }
  })
  const createTarget = bind(createPage, {
    $route: { path: "/mobile/hr/onboarding/create", params: {}, query: { stateKey: "qs_queueopaque_123", employeeName: "forbidden" }, fullPath: "/mobile/hr/onboarding/create?stateKey=qs_queueopaque_123" },
    $router: { replace(target) { createTarget.replaced = target; return Promise.resolve() }, back() {} },
    $refs: {}
  })
  createTarget.initializeRoute()
  await flush()
  assert.deepStrictEqual(createOptionParams, [{ includeOwners: false, includeSupervisors: false }],
    "create form must not download either full people list")

  let blockedLeave
  createPage.beforeRouteLeave.call({ submitting: true, savedOnboardingId: null, successNavigationPending: false, successNavigationTarget: "" }, { path: "/mobile/hr" }, {}, value => { blockedLeave = value })
  assert.strictEqual(blockedLeave, false, "user navigation must be blocked while saving")
  let successfulLeave = "not-called"
  createPage.beforeRouteLeave.call({ submitting: true, savedOnboardingId: 71, successNavigationPending: true, successNavigationTarget: "/mobile/hr/onboarding/71" }, { path: "/mobile/hr/onboarding/71" }, {}, value => { successfulLeave = value })
  assert.strictEqual(successfulLeave, undefined, "the page's own successful detail navigation must be allowed")
  let unrelatedLeave
  createPage.beforeRouteLeave.call({ submitting: true, savedOnboardingId: 71, successNavigationPending: true, successNavigationTarget: "/mobile/hr/onboarding/71" }, { path: "/mobile/hr" }, {}, value => { unrelatedLeave = value })
  assert.strictEqual(unrelatedLeave, false, "an unrelated navigation must remain blocked while the success replace is pending")
  createTarget.model.employeeName = "旧姓名"
  createTarget.fieldErrors = { employeeName: "姓名错误", targetPostId: "岗位错误" }
  createTarget.handleModelInput({ ...createTarget.model, employeeName: "新姓名" })
  assert.deepStrictEqual(plain(createTarget.fieldErrors), { targetPostId: "岗位错误" }, "editing one field must not erase unrelated backend errors")
  Object.assign(createTarget.model, {
    employeeName: "林晓", phoneNumber: "13800138000", expectedEntryDate: "2026-07-20",
    targetDeptId: 11, targetPostId: 12, employeeCategory: "FULL_TIME", ownerUserId: 13,
    targetDeptName: "华东区"
  })
  const firstCreate = createTarget.submit()
  const duplicateCreate = createTarget.submit()
  await Promise.all([firstCreate, duplicateCreate])
  assert.strictEqual(createCalls.length, 1, "duplicate create submissions should make one request")
  assert.deepStrictEqual(createCalls[0], plain(createPayload))
  assert.deepStrictEqual(plain(createTarget.replaced), { path: "/mobile/hr/onboarding/71", query: { stateKey: "qs_queueopaque_123" } })

  for (const rejectedStateKey of ["13800138000", "arbitrary-token"]) {
    let rejectedTarget
    const strictStateTarget = bind(createPage, {
      $route: { path: "/mobile/hr/onboarding/create", params: {}, query: { stateKey: rejectedStateKey }, fullPath: `strict-${rejectedStateKey}` },
      $router: { replace(target) { rejectedTarget = target; return Promise.resolve() }, back() {} },
      $refs: {}
    })
    strictStateTarget.savedOnboardingId = 72
    await strictStateTarget.navigateToDetail(72)
    assert.deepStrictEqual(plain(rejectedTarget), { path: "/mobile/hr/onboarding/72" }, `${rejectedStateKey} must not propagate as queue state`)
  }

  const firstNavigation = deferred()
  const secondNavigation = deferred()
  const terminalCreateCalls = []
  let navigationAttempts = 0
  const terminalPage = loadPage({
    createHrOnboarding(data) { terminalCreateCalls.push(plain(data)); return Promise.resolve({ data: { onboardingId: 88 } }) },
    updateHrOnboarding() { throw new Error("unexpected update") },
    getHrOnboarding() { throw new Error("unexpected detail") },
    getHrOnboardingFormOptions() { return Promise.resolve({ data: {} }) }
  })
  const terminalTarget = bind(terminalPage, {
    $route: { path: "/mobile/hr/onboarding/create", params: {}, query: {}, fullPath: "terminal-create" },
    $router: {
      replace() {
        navigationAttempts += 1
        return navigationAttempts === 1 ? firstNavigation.promise : secondNavigation.promise
      },
      back() {}
    },
    $refs: {}
  })
  terminalTarget.initializeRoute()
  await flush()
  Object.assign(terminalTarget.model, plain(createPayload))
  const terminalSubmit = terminalTarget.submit()
  await flush()
  assert.strictEqual(terminalTarget.savedOnboardingId, 88, "the saved record id must become a terminal state before navigation settles")
  assert.strictEqual(terminalTarget.submitting, true, "save must remain busy while navigation is pending")
  terminalTarget.submit()
  assert.strictEqual(terminalCreateCalls.length, 1, "repeated save while navigation is pending must not reissue create")
  firstNavigation.reject(new Error("router unavailable"))
  await terminalSubmit
  assert.ok(terminalTarget.navigationError.includes("保存成功但跳转失败"))
  assert.strictEqual(terminalTarget.submitting, false)
  assert.strictEqual(terminalTarget.savedOnboardingId, 88)
  assert.strictEqual(terminalPage.computed.formLocked.call(terminalTarget), true, "save controls must remain locked after navigation failure")
  const navigationRetry = terminalTarget.retryNavigation()
  terminalTarget.submit()
  await flush()
  assert.strictEqual(terminalCreateCalls.length, 1, "save and navigation retry after a saved response must never reissue create")
  assert.strictEqual(navigationAttempts, 2, "navigation can be retried independently")
  secondNavigation.resolve()
  await navigationRetry
  assert.strictEqual(terminalTarget.navigationError, "")
  assert.strictEqual(terminalTarget.submitting, false)

  const oldOptions = deferred()
  const newOptions = deferred()
  let optionCalls = 0
  const optionsRacePage = loadPage({
    createHrOnboarding() {}, updateHrOnboarding() {}, getHrOnboarding() {},
    getHrOnboardingFormOptions() { optionCalls += 1; return optionCalls === 1 ? oldOptions.promise : newOptions.promise }
  })
  const optionsTarget = bind(optionsRacePage, {
    $route: { path: "/mobile/hr/onboarding/create", params: {}, query: {}, fullPath: "options-one" },
    $router: { replace() {}, back() {} }, $refs: {}
  })
  optionsTarget.initializeRoute()
  optionsTarget.$route = { path: "/mobile/hr/onboarding/create", params: {}, query: {}, fullPath: "options-two" }
  optionsTarget.initializeRoute()
  newOptions.resolve({ data: { posts: [{ label: "新岗位", value: 2 }] } })
  oldOptions.resolve({ data: { posts: [{ label: "旧岗位", value: 1 }] } })
  await flush()
  assert.deepStrictEqual(plain(optionsTarget.options.posts), [{ label: "新岗位", value: 2 }], "stale options must not replace the current authoritative list")

  const failedPage = loadPage({
    createHrOnboarding() { return Promise.reject({ response: { data: { fieldErrors: { targetPostId: "请选择岗位", idNumber: "证件错误" }, msg: "请完善" } } }) },
    updateHrOnboarding() { throw new Error("unexpected update") },
    getHrOnboarding() { throw new Error("unexpected detail") },
    getHrOnboardingFormOptions() { return Promise.resolve({ data: {} }) }
  })
  const failedTarget = bind(failedPage, {
    $route: { path: "/mobile/hr/onboarding/create", params: {}, query: {}, fullPath: "/mobile/hr/onboarding/create" },
    $router: { replace() {}, back() {} },
    $refs: { stepForm: { focusField(key) { failedTarget.focusedField = key } } }
  })
  failedTarget.initializeRoute()
  await flush()
  failedTarget.model.employeeName = "保留我"
  await failedTarget.submit()
  assert.strictEqual(failedTarget.model.employeeName, "保留我", "failed requests must retain the in-memory model")
  assert.strictEqual(failedTarget.activeStep, 1, "the first backend error step should activate")
  assert.strictEqual(failedTarget.focusedField, "targetPostId")
  assert.strictEqual(failedTarget.missingCounts.organization, 1)
  assert.strictEqual(failedTarget.missingCounts.identityContact, 1)
  assert.ok(failedTarget.submitError && typeof failedTarget.retrySubmit === "function", "failures should expose retry")
  assert.strictEqual(failedTarget.createOutcomeUnknown, false, "deterministic validation responses must remain retryable")

  const unknownCreateCalls = []
  const reconciliationTargets = []
  const unknownPage = loadPage({
    createHrOnboarding(data) {
      unknownCreateCalls.push(plain(data))
      const rawAxiosError = new Error("timeout after possible commit")
      rawAxiosError.name = "AxiosError"
      rawAxiosError.code = "ECONNABORTED"
      rawAxiosError.isAxiosError = true
      const normalizedError = new Error("系统接口请求超时")
      normalizedError.code = ""
      normalizedError.response = rawAxiosError
      return Promise.reject(normalizedError)
    },
    updateHrOnboarding() { throw new Error("unexpected update") },
    getHrOnboarding() { throw new Error("unexpected detail") },
    getHrOnboardingFormOptions() { return Promise.resolve({ data: {} }) }
  })
  const unknownTarget = bind(unknownPage, {
    $route: { path: "/mobile/hr/onboarding/create", params: {}, query: { stateKey: "qs_reconcile_12345" }, fullPath: "unknown-create" },
    $router: { replace(target) { reconciliationTargets.push(plain(target)); return Promise.resolve() }, back() {} },
    $refs: {}
  })
  unknownTarget.initializeRoute()
  await flush()
  Object.assign(unknownTarget.model, plain(createPayload))
  await unknownTarget.submit()
  assert.strictEqual(unknownCreateCalls.length, 1)
  assert.strictEqual(unknownTarget.model.employeeName, "林晓", "outcome-unknown create must retain the in-memory model")
  assert.strictEqual(unknownTarget.createOutcomeUnknown, true)
  assert.ok(unknownTarget.outcomeUnknownMessage.includes("提交结果未知"))
  assert.strictEqual(unknownPage.computed.formLocked.call(unknownTarget), true, "outcome-unknown create must permanently lock save controls")
  await unknownTarget.submit()
  await unknownTarget.retrySubmit()
  assert.strictEqual(unknownCreateCalls.length, 1, "save/retry after an ambiguous create must never POST again")
  assert.deepStrictEqual(reconciliationTargets, [
    { path: "/mobile/hr/onboarding", query: { stateKey: "qs_reconcile_12345" } },
    { path: "/mobile/hr/onboarding", query: { stateKey: "qs_reconcile_12345" } }
  ], "terminal actions may only navigate back to the queue for reconciliation")

  const ambiguousTransportCases = [
    { status: 502, data: "<html>Bad Gateway</html>", label: "502 HTML" },
    { status: 503, data: "", label: "503 empty" },
    { status: 504, data: "Gateway Timeout", label: "504 gateway" },
    { status: 408, data: "Request Timeout", label: "408 timeout" }
  ]
  for (const scenario of ambiguousTransportCases) {
    let calls = 0
    const page = loadPage({
      createHrOnboarding() {
        calls += 1
        const normalizedError = new Error(`系统接口${scenario.status}异常`)
        normalizedError.code = scenario.status
        normalizedError.response = { status: scenario.status, data: scenario.data }
        return Promise.reject(normalizedError)
      },
      updateHrOnboarding() { throw new Error("unexpected update") },
      getHrOnboarding() { throw new Error("unexpected detail") },
      getHrOnboardingFormOptions() { return Promise.resolve({ data: {} }) }
    })
    const target = bind(page, {
      $route: { path: "/mobile/hr/onboarding/create", params: {}, query: {}, fullPath: `transport-${scenario.status}` },
      $router: { replace() { return Promise.resolve() }, back() {} }, $refs: {}
    })
    target.initializeRoute()
    await flush()
    Object.assign(target.model, plain(createPayload))
    await target.submit()
    await target.submit()
    await target.retrySubmit()
    assert.strictEqual(calls, 1, `${scenario.label} must never reissue create`)
    assert.strictEqual(target.createOutcomeUnknown, true, `${scenario.label} must enter terminal reconciliation`)
  }

  const deterministicCreateCases = [
    {
      label: "structured backend validation",
      error: { code: 500, response: { status: 200, data: { code: 500, msg: "字段校验失败" } } }
    },
    {
      label: "structured field errors",
      error: { response: { status: 500, data: { errorCode: "ONBOARDING_VALIDATION_FAILED", fieldErrors: { employeeName: "姓名必填" } } } }
    },
    {
      label: "HTTP 400 validation",
      error: { code: 400, response: { status: 400, data: "Bad Request" } }
    }
  ]
  for (const scenario of deterministicCreateCases) {
    let calls = 0
    const page = loadPage({
      createHrOnboarding() { calls += 1; return Promise.reject(scenario.error) },
      updateHrOnboarding() { throw new Error("unexpected update") },
      getHrOnboarding() { throw new Error("unexpected detail") },
      getHrOnboardingFormOptions() { return Promise.resolve({ data: {} }) }
    })
    const target = bind(page, {
      $route: { path: "/mobile/hr/onboarding/create", params: {}, query: {}, fullPath: `deterministic-${calls}` },
      $router: { replace() { throw new Error("must not reconcile") }, back() {} }, $refs: {}
    })
    target.initializeRoute()
    await flush()
    Object.assign(target.model, plain(createPayload))
    await target.submit()
    assert.strictEqual(target.createOutcomeUnknown, false, `${scenario.label} should remain retryable`)
    await target.retrySubmit()
    assert.strictEqual(calls, 2, `${scenario.label} retry should reissue create`)
  }

  const detailRequest = deferred()
  const optionRequest = deferred()
  const editOptionParams = []
  const updates = []
  const editPage = loadPage({
    createHrOnboarding() { throw new Error("unexpected create") },
    updateHrOnboarding(id, data) { updates.push([id, plain(data)]); return Promise.resolve({ data: { onboardingId: id, version: 5 } }) },
    getHrOnboarding() { return detailRequest.promise },
    getHrOnboardingFormOptions(params) { editOptionParams.push(plain(params)); return optionRequest.promise }
  })
  const editTarget = bind(editPage, {
    $route: { path: "/mobile/hr/onboarding/8/edit", params: { id: "8" }, query: {}, fullPath: "/mobile/hr/onboarding/8/edit" },
    $router: { replace(target) { editTarget.replaced = target; return Promise.resolve() }, back() {} },
    $refs: {}
  })
  editTarget.initializeRoute()
  assert.deepStrictEqual(editOptionParams, [{ includeOwners: false, includeSupervisors: false }],
    "edit must hydrate owner and supervisor through the paged authorized picker, not a full user list")
  detailRequest.resolve({ data: {
    onboardingId: 8, version: 4, employeeName: "原姓名", phoneNumberMasked: "138****8000",
    idNumberMasked: "1101**********0000", bankAccountMasked: "6222020202021234",
    registeredResidenceMasked: "上海********", currentAddressMasked: "上海********",
    targetDeptId: 11, targetPostId: 12, employeeCategory: "INTERN", ownerUserId: 13,
    missingOnboardingFields: { organization: [{ key: "targetPostId" }] },
    missingProfileFields: { organization: [{ key: "targetPostId" }] }
  } })
  await flush()
  assert.strictEqual(editTarget.model.employeeCategory, "INTERN", "edit hydration must retain the existing employee category before options arrive")
  assert.strictEqual(editTarget.model.ownerUserId, 13, "edit hydration must retain the existing owner before options arrive")
  assert.deepStrictEqual(plain(form.normalizePayload(editTarget.model, {
    mode: "edit",
    originalModel: editTarget.originalModel,
    dirtySensitiveFields: {}
  })), { version: 4 }, "an untouched edit payload must send only the optimistic-lock version")

  const selectedValueDirective = component.directives && component.directives.selectedValue
  assert.ok(selectedValueDirective && typeof selectedValueDirective.componentUpdated === "function",
    "async option sheets need a post-options value synchronization directive")
  const employeeCategorySelect = {
    options: [{ value: "" }],
    selectedIndex: 0
  }
  selectedValueDirective.inserted(employeeCategorySelect, { value: editTarget.model.employeeCategory })
  assert.strictEqual(employeeCategorySelect.selectedIndex, -1,
    "a hydrated value cannot be selected before its async option exists")
  selectedValueDirective.componentUpdated(employeeCategorySelect, { value: "" })
  assert.strictEqual(employeeCategorySelect.selectedIndex, 0,
    "an empty model value must select the empty placeholder")

  optionRequest.resolve({ data: {
    posts: [{ label: "店长", value: 12 }],
    employeeCategories: [{ label: "实习生", value: "INTERN" }],
    owners: [{ label: "丑稚琼", value: 13 }]
  } })
  await flush()
  employeeCategorySelect.options = [{ value: "" }, { value: "INTERN" }]
  selectedValueDirective.componentUpdated(employeeCategorySelect, { value: editTarget.model.employeeCategory })
  assert.strictEqual(employeeCategorySelect.selectedIndex, 1,
    "the existing employee category must be selected after async options arrive")
  const ownerSelect = {
    options: [{ value: "" }, { value: "13", _value: 13 }],
    selectedIndex: 0
  }
  selectedValueDirective.componentUpdated(ownerSelect, { value: editTarget.model.ownerUserId })
  assert.strictEqual(ownerSelect.selectedIndex, 1,
    "numeric owner ids must match Vue's real numeric option _value")

  const ownerField = form.FORM_STEPS
    .flatMap(step => step.fields)
    .find(field => field.key === "ownerUserId")
  const optionStateTarget = bind(component, {
    model: { version: 4, ownerUserId: 13 },
    optionsReady: true,
    options: { owners: [{ label: "其他负责人", value: 14 }] }
  })
  optionStateTarget.optionsReady = false
  assert.strictEqual(optionStateTarget.hasUnavailableCurrentValue(ownerField), false,
    "a pending or failed options request must not prematurely label the saved value unavailable")
  optionStateTarget.optionsReady = true
  assert.strictEqual(optionStateTarget.hasUnavailableCurrentValue(ownerField), true,
    "a saved value removed from the final option set must be reported as unavailable")
  assert.strictEqual(optionStateTarget.unavailableCurrentLabel(ownerField), "已保存的入职负责人（当前不可选）")
  optionStateTarget.options.owners = [{ label: "丑稚琼", value: 13 }, { label: "其他负责人", value: 14 }]
  assert.strictEqual(optionStateTarget.hasUnavailableCurrentValue(ownerField), false,
    "a saved value must stop being reported as unavailable when the option returns")
  optionStateTarget.options.owners = [{ label: "其他负责人", value: 14 }]
  assert.strictEqual(optionStateTarget.hasUnavailableCurrentValue(ownerField), true,
    "removing the option again must restore the explicit unavailable state")
  optionStateTarget.model.ownerUserId = ""
  assert.strictEqual(optionStateTarget.hasUnavailableCurrentValue(ownerField), false,
    "an empty model value is not an unavailable saved value")

  optionStateTarget.model.ownerUserId = 13
  optionStateTarget.updateField("ownerUserId", "14")
  const ownerChange = optionStateTarget.emitted.find(event => event.name === "input")
  assert.strictEqual(ownerChange.value.ownerUserId, "14",
    "changing the owner must emit the newly selected value")
  ownerSelect.options = [
    { value: "" },
    { value: "13", _value: 13 },
    { value: "14", _value: 14 }
  ]
  selectedValueDirective.componentUpdated(ownerSelect, { value: ownerChange.value.ownerUserId })
  assert.strictEqual(ownerSelect.selectedIndex, 2,
    "the select must synchronize to the user change after a prior hydration sync")
  assert.strictEqual(editTarget.model.bankAccountMasked, "已填写", "an unexpectedly unmasked value in a masked DTO field must never be rendered raw")
  assert.strictEqual(editTarget.missingCounts.organization, 1, "duplicate backend missing keys must count once per step")
  editTarget.model.employeeName = "新姓名"
  await editTarget.submit()
  assert.strictEqual(updates.length, 1)
  assert.deepStrictEqual(updates[0], [8, {
    version: 4,
    employeeName: "新姓名"
  }],
    "edit must send version plus real changes and omit untouched hydrated fields")

  const unchangedUpdates = []
  const unchangedPage = loadPage({
    createHrOnboarding() { throw new Error("unexpected create") },
    updateHrOnboarding(id, data) {
      unchangedUpdates.push([id, plain(data)])
      return Promise.resolve({ data: { onboardingId: id, version: 5 } })
    },
    getHrOnboarding() { throw new Error("unexpected detail") },
    getHrOnboardingFormOptions() { throw new Error("unexpected options") }
  })
  const unchangedTarget = bind(unchangedPage, {
    mode: "edit",
    recordId: 8,
    model: { version: "4", employeeName: "原姓名", targetDeptId: "11" },
    originalModel: { version: 4, employeeName: "原姓名", targetDeptId: 11 },
    $route: { path: "/mobile/hr/onboarding/8/edit", params: { id: "8" }, query: {}, fullPath: "unchanged-edit" },
    $router: { replace() { return Promise.resolve() }, back() {} },
    $refs: {}
  })
  await unchangedTarget.submit()
  assert.deepStrictEqual(unchangedUpdates, [[8, { version: 4 }]],
    "an unchanged edit must still issue the update request with version only")

  const maskTarget = bind(editPage, {
    $route: { path: "/mobile/hr/onboarding/8/edit", params: { id: "8" }, query: {}, fullPath: "edit" },
    $router: { replace() {}, back() {} }, $refs: {}
  })
  maskTarget.mode = "edit"
  maskTarget.recordId = 8
  maskTarget.model = { version: 4, idNumber: "1101**********0000" }
  maskTarget.sensitiveDirty = { idNumber: true }
  await maskTarget.submit()
  assert.ok(maskTarget.fieldErrors.idNumber, "dirty masking placeholders should fail before the API")

  const firstDetail = deferred()
  const secondDetail = deferred()
  let detailCall = 0
  const racePage = loadPage({
    createHrOnboarding() {}, updateHrOnboarding() {},
    getHrOnboarding() { detailCall += 1; return detailCall === 1 ? firstDetail.promise : secondDetail.promise },
    getHrOnboardingFormOptions() { return Promise.resolve({ data: {} }) }
  })
  const raceTarget = bind(racePage, {
    $route: { path: "/mobile/hr/onboarding/1/edit", params: { id: "1" }, query: {}, fullPath: "one" },
    $router: { replace() {}, back() {} }, $refs: {}
  })
  raceTarget.initializeRoute()
  raceTarget.$route = { path: "/mobile/hr/onboarding/2/edit", params: { id: "2" }, query: {}, fullPath: "two" }
  raceTarget.initializeRoute()
  secondDetail.resolve({ data: { onboardingId: 2, version: 2, employeeName: "新记录" } })
  firstDetail.resolve({ data: { onboardingId: 1, version: 1, employeeName: "旧记录" } })
  await flush()
  assert.strictEqual(raceTarget.recordId, 2)
  assert.strictEqual(raceTarget.model.employeeName, "新记录", "stale route detail must not overwrite the current record")

  const conflictRefresh = deferred()
  let conflictGetCalls = 0
  const conflictPage = loadPage({
    createHrOnboarding() {},
    updateHrOnboarding() { return Promise.reject({ response: { data: { errorCode: "ONBOARDING_VERSION_CONFLICT" } } }) },
    getHrOnboarding() {
      conflictGetCalls += 1
      if (conflictGetCalls === 1) return Promise.resolve({ data: { onboardingId: 9, version: 3, employeeName: "服务端旧值", targetDeptId: 1 } })
      return conflictRefresh.promise
    },
    getHrOnboardingFormOptions() { return Promise.resolve({ data: {} }) }
  })
  const conflictTarget = bind(conflictPage, {
    $route: { path: "/mobile/hr/onboarding/9/edit", params: { id: "9" }, query: {}, fullPath: "conflict" },
    $router: { replace() {}, back() {} }, $refs: {}
  })
  conflictTarget.initializeRoute()
  await flush()
  conflictTarget.model.employeeName = "用户修改"
  const conflictSubmit = conflictTarget.submit()
  await flush()
  conflictTarget.model.employeeName = "用户后续修改"
  conflictRefresh.resolve({ data: { onboardingId: 9, version: 4, employeeName: "服务端新值", targetDeptId: 2 } })
  await conflictSubmit
  assert.strictEqual(conflictTarget.model.version, 4)
  assert.strictEqual(conflictTarget.model.employeeName, "用户后续修改", "version refresh should preserve edits made while the latest masked detail was pending")
  assert.strictEqual(conflictTarget.model.targetDeptId, 2, "untouched fields should refresh from the latest masked detail")
  assert.ok(conflictTarget.conflictNotice && !conflictTarget.submitting, "conflict should require review, never silently resubmit")

  console.log("mobile HR onboarding form tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
