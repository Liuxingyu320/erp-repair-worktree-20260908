const assert = require("assert")
const fs = require("fs")
const os = require("os")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repositoryRoot = path.resolve(uiRoot, "..")

function read(relativePath) {
  return fs.readFileSync(path.resolve(repositoryRoot, relativePath), "utf8")
}

const { resolveApiBaseUrl } = require("../src/utils/apiBaseUrl")

assert.strictEqual(
  resolveApiBaseUrl({
    basePath: "/prod-api",
    nativeOrigin: "",
    isNative: false,
    production: true
  }),
  "/prod-api",
  "production web builds must retain the reverse-proxy relative base path"
)

assert.strictEqual(
  resolveApiBaseUrl({
    basePath: "/prod-api",
    nativeOrigin: "https://erp-api.example.test/gateway",
    isNative: true,
    production: true
  }),
  "https://erp-api.example.test/prod-api",
  "production native builds must resolve the API path against the configured HTTPS origin"
)

assert.strictEqual(
  resolveApiBaseUrl({
    basePath: "/dev-api",
    nativeOrigin: "",
    isNative: true,
    production: false
  }),
  "/dev-api",
  "non-production native development may retain the configured relative proxy path"
)

for (const nativeOrigin of ["", "http://erp-api.example.test", "//erp-api.example.test", "not-a-url"]) {
  assert.throws(
    () => resolveApiBaseUrl({
      basePath: "/prod-api",
      nativeOrigin,
      isNative: true,
      production: true
    }),
    error => error && error.code === "NATIVE_API_ORIGIN_REQUIRED" && error.message === "NATIVE_API_ORIGIN_REQUIRED",
    `production native origin ${JSON.stringify(nativeOrigin)} must fail closed before a request is created`
  )
}

for (const basePath of ["http://other.example.test/prod-api", "//other.example.test/prod-api"]) {
  assert.throws(
    () => resolveApiBaseUrl({
      basePath,
      nativeOrigin: "https://erp-api.example.test",
      isNative: true,
      production: true
    }),
    error => error && error.code === "NATIVE_API_ORIGIN_REQUIRED",
    `production native base path ${basePath} must not escape the validated HTTPS origin`
  )
}

const requestSource = read("erp-ui/src/utils/request.js")
assert.ok(requestSource.includes("resolveApiBaseUrl"), "the request client should use the pure API-base resolver")
assert.ok(requestSource.includes("Capacitor.isNativePlatform()"), "the request client should pass the Capacitor runtime state")
assert.ok(requestSource.includes("process.env.VUE_APP_NATIVE_API_ORIGIN"), "the request client should use the dedicated native origin")
assert.ok(requestSource.includes("process.env.NODE_ENV === 'production'"), "the request client should explicitly identify production")
assert.ok(
  /baseURL:\s*resolveApiBaseUrl\(/.test(requestSource),
  "native-origin validation must run while the Axios client is created, before any request"
)

const qaSource = read("scripts/mobile-hr-onboarding-qa.cjs")
assert.ok(qaSource.includes("if (require.main === module)"), "QA helpers should be importable without starting a browser run")
assert.ok(qaSource.includes("module.exports"), "QA should export deterministic authentication and PII helpers for runtime tests")
for (const width of [390, 375, 360, 320]) {
  assert.ok(qaSource.includes(String(width)), `QA should cover a ${width}px viewport`)
}
for (const state of [
  "workbench", "DRAFT", "READY", "CONFIRMED", "CANCELLED", "detail", "create", "edit",
  "validation", "confirmation", "cancel", "empty", "loading", "error"
]) {
  assert.ok(qaSource.includes(state), `QA should capture ${state}`)
}
assert.ok(qaSource.includes("artifacts/mobile-hr-onboarding"), "QA output should use the dedicated artifact directory")
assert.ok(qaSource.includes("API_ORIGIN"), "QA should require an explicit API origin")
assert.ok(qaSource.includes("https:"), "QA should reject a non-HTTPS origin")
assert.ok(qaSource.includes("appOrigin !== apiOrigin") && qaSource.includes("route.continue({ url: target.toString() })"),
  "QA should route relative production requests to the explicitly validated real API origin")
assert.ok(qaSource.includes("HR_QA_SESSION_FILE"), "QA should accept a dedicated ephemeral authentication session")
assert.ok(qaSource.includes("HR_QA_ALLOW_MUTATIONS"), "QA writes should require an explicit opt-in")
assert.ok(qaSource.includes("HR_QA_FORBIDDEN_PII"), "QA should accept exact forbidden fixture values for address auditing")
assert.ok(qaSource.includes("element.value") && qaSource.includes('getAttribute("value")'),
  "QA should inspect live visible control values and rendered value attributes in addition to body text")
assert.ok(!/password\s*[:=]\s*["'][^"']+["']/i.test(qaSource), "QA source must not contain a hard-coded password")
assert.ok(!qaSource.includes("localStorage.setItem"), "QA must not persist credentials in local storage")

const qa = require(path.resolve(repositoryRoot, "scripts/mobile-hr-onboarding-qa.cjs"))
const safeMutationRule = {
  method: "POST",
  origin: "https://api.example.test",
  pathname: "/prod-api/system/hr/onboarding/77/cancel"
}
assert.strictEqual(qa.decideQaRequest({ method: "GET", url: "https://api.example.test/prod-api/system/hr/onboarding/list" }).allowed, true)
assert.strictEqual(qa.decideQaRequest({ method: "HEAD", url: "https://api.example.test/health" }).allowed, true)
assert.strictEqual(qa.decideQaRequest({ method: "OPTIONS", url: "https://api.example.test/prod-api/system/hr/onboarding" }).allowed, true)
assert.strictEqual(qa.decideQaRequest({ method: "POST", url: safeMutationRule.origin + safeMutationRule.pathname,
  mutationOptIn: false, allowlist: [safeMutationRule] }).allowed, false,
"mutation allowlist must not bypass the explicit opt-in")
assert.strictEqual(qa.decideQaRequest({ method: "POST", url: safeMutationRule.origin + safeMutationRule.pathname,
  mutationOptIn: true, allowlist: [] }).allowed, false,
"an empty real mutation allowlist must keep QA globally read-only")
assert.strictEqual(qa.decideQaRequest({ method: "POST", url: safeMutationRule.origin + safeMutationRule.pathname,
  mutationOptIn: true, allowlist: [safeMutationRule] }).allowed, true,
"only an exact method/origin/path rule may approve an opted-in fixture mutation")
assert.strictEqual(qa.decideQaRequest({ method: "POST", url: "https://api.example.test/prod-api/system/hr/onboarding/78/cancel",
  mutationOptIn: true, allowlist: [safeMutationRule] }).allowed, false,
"a different fixture id must not match the safe allowlist")
assert.deepStrictEqual(qa.parseMutationAllowlist(undefined, false), [], "read-only QA should not require a mutation allowlist")
assert.deepStrictEqual(
  qa.parseMutationAllowlist(JSON.stringify([safeMutationRule]), true),
  [safeMutationRule],
  "an opted-in mutation allowlist should preserve only exact HTTPS method/origin/path rules"
)
for (const value of [
  undefined,
  "[]",
  "{}",
  JSON.stringify([{ ...safeMutationRule, method: "GET" }]),
  JSON.stringify([{ ...safeMutationRule, origin: "http://api.example.test" }]),
  JSON.stringify([{ ...safeMutationRule, pathname: `${safeMutationRule.pathname}?unexpected=1` }])
]) {
  assert.throws(
    () => qa.parseMutationAllowlist(value, true),
    error => error && /^HR_QA_MUTATION_ALLOWLIST_/.test(error.code),
    "opted-in real mutations must have a non-empty exact HTTPS allowlist"
  )
}
const authFixtureDir = fs.mkdtempSync(path.join(os.tmpdir(), "mobile-hr-qa-auth-"))
function authFixture(name, value) {
  const filename = path.join(authFixtureDir, name)
  fs.writeFileSync(filename, value, { mode: 0o600 })
  return filename
}
try {
  for (const filename of [
    authFixture("text.json", "not-json"),
    authFixture("object.json", "{}"),
    authFixture("empty.json", JSON.stringify({ cookies: [], origins: [] })),
    authFixture("wrong-shape.json", JSON.stringify({ cookies: {}, origins: [] })),
    authFixture("empty-cookie.json", JSON.stringify({ cookies: [{ name: "", value: "" }], origins: [] })),
    authFixture("expired-cookie.json", JSON.stringify({ cookies: [{ name: "session", value: "expired", expires: 1 }], origins: [] }))
  ]) {
    assert.throws(
      () => qa.ensureAuthenticationInput(filename),
      error => error && error.code === "HR_QA_SESSION_STATE_REQUIRED",
      `${path.basename(filename)} should fail before the browser starts`
    )
  }
  const cookieState = authFixture("cookie.json", JSON.stringify({
    cookies: [{ name: "session", value: "opaque", domain: "app.example.test", path: "/" }],
    origins: []
  }))
  const localState = authFixture("local.json", JSON.stringify({
    cookies: [],
    origins: [{ origin: "https://app.example.test", localStorage: [{ name: "token", value: "opaque" }] }]
  }))
  assert.strictEqual(qa.ensureAuthenticationInput(cookieState), cookieState)
  assert.strictEqual(qa.ensureAuthenticationInput(localState), localState)
  if (process.platform !== "win32") {
    const publicState = authFixture("public.json", JSON.stringify({
      cookies: [{ name: "session", value: "opaque", domain: "app.example.test", path: "/" }], origins: []
    }))
    fs.chmodSync(publicState, 0o644)
    assert.throws(() => qa.ensureAuthenticationInput(publicState), error => error && error.code === "HR_QA_SESSION_FILE_PRIVATE_REQUIRED")
    fs.chmodSync(publicState, 0o600)
    assert.strictEqual(qa.ensureAuthenticationInput(publicState), publicState)
  }
} finally {
  fs.rmSync(authFixtureDir, { recursive: true, force: true })
}

if (process.platform !== "win32") {
  const privateArtifactRoot = fs.mkdtempSync(path.join(os.tmpdir(), "mobile-hr-qa-output-"))
  const privateOutputDir = path.join(privateArtifactRoot, "run")
  try {
    qa.preparePrivateOutputDir(privateOutputDir)
    assert.strictEqual(fs.statSync(privateOutputDir).mode & 0o777, 0o700, "QA run directories must be owner-only")
    const privateArtifact = path.join(privateOutputDir, "capture.png")
    qa.writePrivateFile(privateArtifact, Buffer.from("private"))
    assert.strictEqual(fs.statSync(privateArtifact).mode & 0o777, 0o600, "QA artifacts must be owner-only")
  } finally {
    fs.rmSync(privateArtifactRoot, { recursive: true, force: true })
  }
}

assert.deepStrictEqual(
  qa.findPiiCategories([
    "手机 138 0013 8000",
    "全角手机 １３８－００１３－８０００",
    "证件 110105 1949-12-31 002X",
    "旧证件 130503-670401-001",
    "账户 4539 1488\n0343-6467"
  ], []),
  ["bank-or-account", "chinese-id", "phone"],
  "visible text and control values should detect full phone, Chinese ID and bank/account patterns"
)
assert.deepStrictEqual(
  qa.findPiiCategories([
    "普通流水号 123456789012345678",
    "无效卡号 4539-1488-0343-6468",
    "无效证件 110105 1949-02-31 002X",
    "校验错误但碰巧满足Luhn的证件形状 110105194912310028"
  ], []),
  [],
  "arbitrary identifiers, invalid dates/checksums and failed Luhn values must not be classified as PII"
)
assert.deepStrictEqual(
  qa.findPiiCategories(["138****8000 1101**********123X 6222********0123 已填写"], []),
  [],
  "masked display values and filled sentinels should remain safe"
)
const exactForbidden = "某省某市某区某街道88号"
assert.deepStrictEqual(qa.findPiiCategories([`地址 ${exactForbidden}`], []), [],
  "a detailed address needs an exact fixture because it intentionally has no generic numeric PII shape")
for (const [value, expectedCode] of [
  [undefined, "HR_QA_FORBIDDEN_PII_REQUIRED"],
  ["", "HR_QA_FORBIDDEN_PII_REQUIRED"],
  ["   ", "HR_QA_FORBIDDEN_PII_REQUIRED"],
  ["[]", "HR_QA_FORBIDDEN_PII_REQUIRED"],
  ["{}", "HR_QA_FORBIDDEN_PII_INVALID"],
  ["42", "HR_QA_FORBIDDEN_PII_INVALID"],
  ["true", "HR_QA_FORBIDDEN_PII_INVALID"],
  ["not-json", "HR_QA_FORBIDDEN_PII_INVALID"],
  ['["valid", 42]', "HR_QA_FORBIDDEN_PII_INVALID"],
  ['["valid", ""]', "HR_QA_FORBIDDEN_PII_INVALID"],
  ['["valid", "   "]', "HR_QA_FORBIDDEN_PII_INVALID"]
]) {
  assert.throws(() => qa.parseForbiddenPii(value), error => error && error.code === expectedCode)
}
assert.deepStrictEqual(qa.parseForbiddenPii(JSON.stringify([exactForbidden, "另一个禁止值"])), [exactForbidden, "另一个禁止值"])
assert.deepStrictEqual(qa.findPiiCategories(["地址 某省某市\n某区 某街道88号"], [exactForbidden]), ["forbidden-exact"])
assert.throws(
  () => qa.assertRenderedContentIsMasked([exactForbidden], "detail", [exactForbidden]),
  error => error && error.code.includes("forbidden-exact") && !error.code.includes(exactForbidden),
  "PII failures should report categories without echoing exact fixture values"
)
const safeQaDiagnostic = {
  forbiddenPiiConfigured: qa.parseForbiddenPii(JSON.stringify([exactForbidden])).length > 0,
  failure: "UNMASKED_RENDERED_FIXTURE:detail:forbidden-exact"
}
assert.ok(!JSON.stringify(safeQaDiagnostic).includes(exactForbidden), "QA diagnostics and report metadata must never contain exact fixture values")

qa.validateKeyboardMeasurements({
  visualTop: 20,
  visualHeight: 540,
  viewportWidth: 320,
  bodyScrollWidth: 320,
  footer: { top: 490, bottom: 560 },
  control: { top: 230, bottom: 274 },
  controls: [{ width: 44, height: 44 }, { width: 280, height: 46 }]
})
for (const invalid of [
  { visualTop: 20, visualHeight: 540, viewportWidth: 320, bodyScrollWidth: 321, footer: { top: 490, bottom: 560 }, control: { top: 230, bottom: 274 }, controls: [{ width: 44, height: 44 }] },
  { visualTop: 20, visualHeight: 540, viewportWidth: 320, bodyScrollWidth: 320, footer: { top: 500, bottom: 570 }, control: { top: 230, bottom: 274 }, controls: [{ width: 44, height: 44 }] },
  { visualTop: 20, visualHeight: 540, viewportWidth: 320, bodyScrollWidth: 320, footer: { top: 490, bottom: 560 }, control: { top: 480, bottom: 510 }, controls: [{ width: 44, height: 44 }] },
  { visualTop: 20, visualHeight: 540, viewportWidth: 320, bodyScrollWidth: 320, footer: { top: 490, bottom: 560 }, control: { top: 230, bottom: 260 }, controls: [{ width: 40, height: 40 }] }
]) {
  assert.throws(() => qa.validateKeyboardMeasurements(invalid), error => error && /^HR_QA_/.test(error.code))
}
const keyboardStep = () => ({ targets: [
  { visualTop: 0, visualHeight: 540, viewportWidth: 320, bodyScrollWidth: 320, footer: { top: 470, bottom: 540 }, control: { top: 200, bottom: 244 }, controls: [{ width: 44, height: 44 }] },
  { visualTop: 0, visualHeight: 540, viewportWidth: 320, bodyScrollWidth: 320, footer: { top: 470, bottom: 540 }, control: { top: 250, bottom: 294 }, controls: [{ width: 44, height: 44 }] }
] })
qa.validateKeyboardCoverage([keyboardStep(), keyboardStep(), keyboardStep(), keyboardStep()])
assert.throws(() => qa.validateKeyboardCoverage([keyboardStep(), keyboardStep(), keyboardStep()]),
  error => error && error.code === "HR_QA_FORM_STEP_COVERAGE_INCOMPLETE")
assert.throws(() => qa.validateKeyboardCoverage([{ targets: [keyboardStep().targets[0]] }, keyboardStep(), keyboardStep(), keyboardStep()]),
  error => error && error.code === "HR_QA_FORM_CONTROL_COVERAGE_INCOMPLETE")

qa.validateConfirmationState({ loading: false, error: false, actualDateCount: 1, decisionCount: 2, decisionAreaCount: 1 })
for (const invalid of [
  { loading: true, error: false, actualDateCount: 1, decisionCount: 2, decisionAreaCount: 1 },
  { loading: false, error: true, actualDateCount: 1, decisionCount: 2, decisionAreaCount: 1 },
  { loading: false, error: false, actualDateCount: 0, decisionCount: 2, decisionAreaCount: 1 },
  { loading: false, error: false, actualDateCount: 1, decisionCount: 1, decisionAreaCount: 1 },
  { loading: false, error: false, actualDateCount: 1, decisionCount: 2, decisionAreaCount: 0 }
]) {
  assert.throws(() => qa.validateConfirmationState(invalid), error => error && /^HR_QA_CONFIRM_/.test(error.code))
}

for (const marker of [
  "HR_QA_AUTH_REDIRECT", "mobile-hr-shell", "mobile-hr-nav", "今日入职概览", "aria-selected",
  "onboarding-detail-title", "mobile-form-page", "confirm-title", "cancel-title", "is-empty",
  "create-keyboard", "edit-keyboard", "contenteditable", "input:not([type=\"password\"])",
  "unapprovedMutationAttempts", "context.route", "page.screenshot({ fullPage: true })", "chmodSync",
  "visualViewport.offsetTop", "step-progress", "HR_QA_CONFIRM_CONFLICT_ERROR"
]) {
  assert.ok(qaSource.includes(marker), `QA runtime should enforce ${marker}`)
}
assert.ok(qaSource.includes("forbiddenPiiConfigured") && qaSource.includes("Forbidden exact PII configured"),
  "QA report metadata should state only whether exact forbidden PII was configured")
assert.ok(!qaSource.includes("forbiddenPii:"), "QA report must never serialize exact forbidden PII values")
assert.ok(
  qaSource.indexOf("parseForbiddenPii(process.env.HR_QA_FORBIDDEN_PII)") < qaSource.indexOf("const { chromium } = loadPlaywright()"),
  "mandatory exact PII configuration should be validated before loading Playwright or starting a browser"
)

console.log("mobileNativeProductionHardening tests passed")
